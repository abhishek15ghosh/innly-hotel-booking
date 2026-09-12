import { withTransaction } from "../config/db.js";
import { razorpayRefundGateway } from "../gateways/razorpayRefund.gateway.js";
import * as bookingRepository from "../repositories/booking.repository.js";
import { getStayDates } from "../utils/bookingAvailability.js";

export class RefundReconciliationWorker {
  constructor(deps = {}) {
    this.withTx = deps.withTx || withTransaction;
    this.bookingRepo = deps.bookingRepo || bookingRepository;
    this.gateway = deps.gateway || razorpayRefundGateway;
    this.intervalMs = deps.intervalMs || 30000;
    this.workerId = deps.workerId || `worker-${process.pid}-${Math.random().toString(36).substring(2, 8)}`;
    this.logger = deps.logger || console;
    this.timer = null;
    this.isRunning = false;
  }

  start() {
    if (this.timer) return;
    this.logger.log(`Refund reconciliation worker started [ID: ${this.workerId}] (interval: ${this.intervalMs}ms)`);
    this.timer = setInterval(() => {
      this.processBatch().catch(() => {});
    }, this.intervalMs);
    if (this.timer.unref) {
      this.timer.unref();
    }
  }

  stop() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
      this.logger.log(`Refund reconciliation worker stopped [ID: ${this.workerId}]`);
    }
  }

  async processBatch() {
    if (this.isRunning) return;
    this.isRunning = true;

    try {
      // 1. Claim pending refunds atomically with multi-instance worker lease
      const claimedRefunds = await this.withTx(async (client) => {
        return this.bookingRepo.claimPendingRefundsForLease(client, this.workerId, 5);
      });

      if (!claimedRefunds || claimedRefunds.length === 0) {
        this.isRunning = false;
        return;
      }

      for (const item of claimedRefunds) {
        let razorpayRefund = null;
        let razorpayError = null;

        // 2. Perform out-of-transaction network call to Razorpay
        if (item.razorpayRefundId) {
          // If we already have a refund ID, fetch status
          try {
            razorpayRefund = await this.gateway.fetchRefund(item.razorpayPaymentId, item.razorpayRefundId);
          } catch (err) {
            razorpayError = err;
          }
        } else {
          // If creation had an unknown outcome (no refund ID), retry creation with exact same X-Refund-Idempotency header & body
          try {
            razorpayRefund = await this.gateway.createRefund(
              item.razorpayPaymentId,
              {
                amount: Math.round(item.amount * 100),
                notes: { bookingId: item.bookingId, reason: item.reason },
                receipt: item.idempotencyKey
              },
              { idempotencyKey: item.idempotencyKey }
            );
          } catch (err) {
            razorpayError = err;
          }
        }

        // 3. Finalize result transactionally
        if (razorpayError) {
          const isAuthError = razorpayError.statusCode === 401 || razorpayError.statusCode === 403;
          const isDefinitiveFailure =
            razorpayError.statusCode === 400 ||
            razorpayError.statusCode === 404 ||
            razorpayError.statusCode === 422 ||
            (razorpayError.message && razorpayError.message.includes("rejected"));

          if (isDefinitiveFailure) {
            await this.withTx(async (client) => {
              await this.bookingRepo.markRefundFailed(
                client,
                item.refundId,
                razorpayError.message || "Razorpay refund rejected"
              );
              await this.bookingRepo.revertBookingToConfirmed(client, item.bookingId, item.paymentId);
              await this.bookingRepo.releaseRefundLeaseAndScheduleRetry(client, item.refundId, item.attemptCount, null, false);
            });
          } else {
            // Transient error, HTTP 409 conflict, or network timeout: schedule exponential backoff
            await this.withTx(async (client) => {
              await this.bookingRepo.releaseRefundLeaseAndScheduleRetry(
                client,
                item.refundId,
                item.attemptCount,
                razorpayError.message || "Network timeout",
                isAuthError
              );
            });
          }
          continue;
        }

        const rStatus = razorpayRefund?.status;

        if (rStatus === "processed" || rStatus === "captured") {
          await this.withTx(async (client) => {
            await this.bookingRepo.finalizeProcessedRefundAtomically(client, {
              bookingId: item.bookingId,
              paymentId: item.paymentId,
              refundId: item.refundId,
              razorpayRefundId: razorpayRefund.id
            });
          });
        } else if (rStatus === "failed") {
          await this.withTx(async (client) => {
            await this.bookingRepo.markRefundFailed(
              client,
              item.refundId,
              razorpayRefund.error_description || "Razorpay reported refund failed"
            );
            await this.bookingRepo.revertBookingToConfirmed(client, item.bookingId, item.paymentId);
          });
        } else {
          // Status is missing, unknown, or pending: update razorpay_refund_id if available & schedule next exponential check
          await this.withTx(async (client) => {
            if (razorpayRefund?.id) {
              await this.bookingRepo.markBookingCancellationPending(
                client,
                item.bookingId,
                item.paymentId,
                item.refundId,
                razorpayRefund.id
              );
            }
            await this.bookingRepo.releaseRefundLeaseAndScheduleRetry(
              client,
              item.refundId,
              item.attemptCount,
              "Pending status check",
              false
            );
          });
        }
      }
    } finally {
      this.isRunning = false;
    }
  }
}
