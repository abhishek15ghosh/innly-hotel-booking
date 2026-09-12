import crypto from "crypto";
import { env } from "../config/env.js";
import { withTransaction } from "../config/db.js";
import * as bookingRepository from "../repositories/booking.repository.js";
import * as webhookRepository from "../repositories/webhook.repository.js";
import { ApiError } from "../utils/apiError.js";

export function verifyWebhookSignature(rawBodyBuffer, signatureHeader, webhookSecret) {
  if (!webhookSecret) {
    throw new ApiError(500, "Webhook configuration error: secret missing");
  }

  if (!signatureHeader || !rawBodyBuffer || !Buffer.isBuffer(rawBodyBuffer)) {
    throw new ApiError(400, "Invalid webhook signature");
  }

  const expectedSignatureStr = crypto
    .createHmac("sha256", webhookSecret)
    .update(rawBodyBuffer)
    .digest("hex");

  const expectedBuf = Buffer.from(expectedSignatureStr, "utf8");
  const actualBuf = Buffer.from(signatureHeader, "utf8");

  if (expectedBuf.length !== actualBuf.length || !crypto.timingSafeEqual(expectedBuf, actualBuf)) {
    throw new ApiError(400, "Invalid webhook signature");
  }

  return true;
}

export async function handleRazorpayWebhook(rawBodyBuffer, signatureHeader, eventIdHeader, deps = {}) {
  const {
    withTx = withTransaction,
    bookingRepo = bookingRepository,
    webhookRepo = webhookRepository,
    webhookSecret = env.RAZORPAY_WEBHOOK_SECRET,
    webhookEnabled = env.RAZORPAY_WEBHOOK_ENABLED
  } = deps;

  // Disabled in development by default unless explicitly enabled
  if (!webhookEnabled) {
    return {
      success: true,
      message: "Webhook is currently disabled"
    };
  }

  // Fail closed if enabled without a secret
  if (!webhookSecret) {
    throw new ApiError(500, "Webhook configuration error: secret missing");
  }

  // Require x-razorpay-event-id header BEFORE signature verification or DB writes
  if (!eventIdHeader || typeof eventIdHeader !== "string") {
    throw new ApiError(400, "Missing x-razorpay-event-id header");
  }
  const trimmedEventId = eventIdHeader.trim();
  if (trimmedEventId.length < 1 || trimmedEventId.length > 255) {
    throw new ApiError(400, "Invalid x-razorpay-event-id header length");
  }
  const eventId = trimmedEventId;

  // Signature verification using untouched raw buffer (fails before DB writes)
  verifyWebhookSignature(rawBodyBuffer, signatureHeader, webhookSecret);

  let body;
  try {
    const rawStr = rawBodyBuffer.toString("utf8");
    body = JSON.parse(rawStr);
  } catch (err) {
    throw new ApiError(400, "Invalid JSON payload in webhook");
  }

  const eventType = body.event;
  if (!eventType) {
    throw new ApiError(400, "Missing event type in webhook payload");
  }

  const result = await withTx(async (client) => {
    // Durable idempotency check
    const record = await webhookRepo.recordWebhookEvent(client, {
      eventId,
      eventType,
      status: "processing"
    });

    if (!record) {
      const existing = await webhookRepo.getWebhookEvent(client, eventId);
      return {
        success: true,
        message: "Duplicate event acknowledged",
        duplicate: true,
        status: existing?.status || "processed"
      };
    }

    const returnPermanentError = async (statusCode, safeReason) => {
      await webhookRepo.updateWebhookEventStatus(client, eventId, "failed", safeReason);
      return {
        isPermanentError: true,
        statusCode,
        safeReason
      };
    };

    if (["payment.captured", "order.paid"].includes(eventType)) {
      const paymentEntity = body.payload?.payment?.entity;
      const orderEntity = body.payload?.order?.entity;

      // Explicitly require payload.payment.entity
      if (!paymentEntity) {
        return await returnPermanentError(400, "Missing payload.payment.entity in webhook");
      }

      // Read payment ID, order ID, amount, currency and status ONLY from payment entity
      const paymentId = paymentEntity.id;
      const orderId = paymentEntity.order_id;
      const amountPaise = paymentEntity.amount;
      const currency = paymentEntity.currency || "INR";
      const paymentStatus = paymentEntity.status;

      if (!paymentId || !orderId) {
        return await returnPermanentError(400, "Missing payment ID or order ID in payment entity");
      }

      // Require payment status captured
      if (paymentStatus !== "captured") {
        return await returnPermanentError(400, "Razorpay payment status is not captured");
      }

      // Cross-check order entity if present on order.paid
      if (orderEntity) {
        if (orderEntity.id && orderEntity.id !== orderId) {
          return await returnPermanentError(400, "Order entity ID mismatch with payment entity order_id");
        }
        if (orderEntity.amount_paid != null && Number(orderEntity.amount_paid) !== Number(amountPaise)) {
          return await returnPermanentError(400, "Order entity amount_paid mismatch with payment entity amount");
        }
      }

      const context = await bookingRepo.getPaymentContextByOrderId(client, orderId);
      if (!context) {
        return await returnPermanentError(404, "Payment order context not found");
      }

      if (context.storedRazorpayPaymentId && context.storedRazorpayPaymentId !== paymentId) {
        return await returnPermanentError(400, "Razorpay payment ID mismatch");
      }

      if (Number(amountPaise) !== Math.round(Number(context.paymentAmount) * 100)) {
        return await returnPermanentError(400, "Razorpay payment amount mismatch");
      }

      if (currency !== (context.paymentCurrency || "INR")) {
        return await returnPermanentError(400, "Razorpay payment currency mismatch");
      }

      // Idempotent duplicate event if already confirmed/captured
      if (context.bookingStatus === "confirmed" && context.paymentStatus === "captured") {
        await webhookRepo.updateWebhookEventStatus(client, eventId, "processed");
        return {
          success: true,
          status: "confirmed",
          duplicate: true
        };
      }

      // Late payment received after reservation expired (inventory released)
      if (["cancelled", "payment_failed"].includes(context.bookingStatus)) {
        const failureReason = "Payment captured after reservation expired. Support reconciliation required.";
        await webhookRepo.updateWebhookEventStatus(client, eventId, "reconciliation_required", failureReason);
        return {
          success: true,
          status: "reconciliation_required",
          message: failureReason
        };
      }

      if (context.bookingStatus === "payment_pending") {
        await bookingRepo.markPaymentCapturedFromWebhook(client, context.bookingId, {
          razorpayPaymentId: paymentId,
          eventId
        });
        await webhookRepo.updateWebhookEventStatus(client, eventId, "processed");
        return {
          success: true,
          status: "confirmed"
        };
      }
    }

    if (eventType === "payment.failed") {
      // Store ONLY controlled/sanitized code, NOT raw external error_description
      const safeReason = "payment_attempt_failed";
      await webhookRepo.updateWebhookEventStatus(client, eventId, "failed_logged", safeReason);
      return {
        success: true,
        status: "failed_logged",
        message: "Payment failure logged; reservation deadline remains active."
      };
    }

    // Unknown event types acknowledged safely as ignored
    await webhookRepo.updateWebhookEventStatus(client, eventId, "ignored");
    return {
      success: true,
      status: "ignored",
      message: "Event type not processed"
    };
  });

  if (result?.isPermanentError) {
    throw new ApiError(result.statusCode, result.safeReason);
  }

  return result;
}
