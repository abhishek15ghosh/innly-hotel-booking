import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { RefundReconciliationWorker } from "../src/workers/refundReconciliation.worker.js";

describe("Refund Reconciliation Worker Unit Tests", () => {
  it("worker start and stop lifecycle methods manage internal timer cleanly", () => {
    const worker = new RefundReconciliationWorker({ intervalMs: 60000 });

    assert.equal(worker.timer, null);
    worker.start();
    assert.ok(worker.timer !== null);

    const timerRef = worker.timer;
    worker.start();
    assert.equal(worker.timer, timerRef);

    worker.stop();
    assert.equal(worker.timer, null);
  });

  it("recovers pending refund with NO razorpayRefundId by retrying creation with X-Refund-Idempotency header & body", async () => {
    let createRefundCalled = false;
    let markCancelledCount = 0;
    let releasedInventoryCount = 0;

    const fakePendingRefundNoId = {
      refundId: "rf-no-id-1",
      bookingId: "b-no-id",
      paymentId: "p-no-id",
      userId: "u-1",
      razorpayRefundId: null, // NULL! Timeout occurred on initial creation
      razorpayPaymentId: "pay_captured_123",
      idempotencyKey: "ref_bnoid",
      amount: 10000,
      currency: "INR",
      refundStatus: "pending",
      bookingStatus: "cancellation_pending",
      roomsBooked: 1,
      checkIn: "2026-09-01",
      checkOut: "2026-09-03",
      roomId: "room-1",
      reason: "Timeout retry test"
    };

    const mockRepo = {
      claimPendingRefundsForLease: async () => [fakePendingRefundNoId],
      getBookingForCancellationDetails: async () => ({
        bookingId: "b-no-id",
        bookingStatus: "cancellation_pending",
        checkIn: "2026-09-01",
        checkOut: "2026-09-03",
        roomsBooked: 1,
        roomId: "room-1"
      }),
      finalizeProcessedRefundAtomically: async () => {
        markCancelledCount++;
        releasedInventoryCount++;
        return { success: true, alreadyFinalized: false, displayMessage: "Refund completed" };
      },
      releaseInventory: async () => {
        releasedInventoryCount++;
      },
      releaseRefundLeaseAndScheduleRetry: async () => {}
    };

    const mockGateway = {
      createRefund: async (paymentId, params, options) => {
        createRefundCalled = true;
        assert.equal(paymentId, "pay_captured_123");
        assert.equal(options.idempotencyKey, "ref_bnoid");
        assert.equal(params.amount, 1000000);
        return { id: "rfnd_retry_success", status: "processed" };
      }
    };

    const worker = new RefundReconciliationWorker({
      withTx: async (cb) => cb({}),
      bookingRepo: mockRepo,
      gateway: mockGateway
    });

    await worker.processBatch();

    assert.equal(createRefundCalled, true);
    assert.equal(markCancelledCount, 1);
    assert.equal(releasedInventoryCount, 1);
  });

  it("two workers cannot claim the same refund simultaneously", async () => {
    const unclaimedRefund = {
      refundId: "rf-shared",
      bookingId: "b-shared",
      paymentId: "p-shared",
      userId: "u-shared",
      razorpayRefundId: "rfnd_shared",
      razorpayPaymentId: "pay_shared",
      idempotencyKey: "ref_bshared",
      amount: 5000,
      currency: "INR",
      refundStatus: "pending",
      checkIn: "2026-09-01",
      checkOut: "2026-09-03",
      roomId: "room-1"
    };

    let isClaimed = false;

    const mockRepoWorker1 = {
      claimPendingRefundsForLease: async (client, workerId) => {
        assert.equal(workerId, "worker-1");
        if (isClaimed) return [];
        isClaimed = true;
        return [unclaimedRefund];
      },
      getBookingForCancellationDetails: async () => ({
        bookingId: "b-shared",
        bookingStatus: "cancellation_pending"
      }),
      finalizeProcessedRefundAtomically: async () => ({ success: true, alreadyFinalized: false, displayMessage: "Refund completed" }),
      releaseInventory: async () => {},
      releaseRefundLeaseAndScheduleRetry: async () => {}
    };

    const mockRepoWorker2 = {
      claimPendingRefundsForLease: async (client, workerId) => {
        assert.equal(workerId, "worker-2");
        if (isClaimed) return []; // Already claimed by worker-1!
        return [unclaimedRefund];
      }
    };

    const mockGateway = {
      fetchRefund: async () => ({ id: "rfnd_shared", status: "processed" })
    };

    const worker1 = new RefundReconciliationWorker({
      workerId: "worker-1",
      withTx: async (cb) => cb({}),
      bookingRepo: mockRepoWorker1,
      gateway: mockGateway
    });

    const worker2 = new RefundReconciliationWorker({
      workerId: "worker-2",
      withTx: async (cb) => cb({}),
      bookingRepo: mockRepoWorker2,
      gateway: mockGateway
    });

    await Promise.all([worker1.processBatch(), worker2.processBatch()]);

    assert.equal(isClaimed, true);
  });

  it("handles failed refund by marking refund failed and reverting booking to confirmed", async () => {
    let markFailedCount = 0;
    let revertConfirmedCount = 0;
    let releasedInventoryCount = 0;

    const fakePendingRefund = {
      refundId: "rf-2",
      bookingId: "b-2",
      paymentId: "p-2",
      userId: "u-2",
      razorpayRefundId: "rfnd_fail",
      razorpayPaymentId: "pay_2",
      refundStatus: "pending",
      bookingStatus: "cancellation_pending",
      roomsBooked: 1,
      checkIn: "2026-09-01",
      checkOut: "2026-09-03",
      roomId: "room-1"
    };

    const mockRepo = {
      claimPendingRefundsForLease: async () => [fakePendingRefund],
      markRefundFailed: async (client, refundId) => {
        markFailedCount++;
        assert.equal(refundId, "rf-2");
      },
      revertBookingToConfirmed: async (client, bookingId, paymentId) => {
        revertConfirmedCount++;
        assert.equal(bookingId, "b-2");
        assert.equal(paymentId, "p-2");
      },
      releaseInventory: async () => {
        releasedInventoryCount++;
      },
      releaseRefundLeaseAndScheduleRetry: async () => {}
    };

    const mockGateway = {
      fetchRefund: async () => ({ id: "rfnd_fail", status: "failed", error_description: "Bank declined" })
    };

    const worker = new RefundReconciliationWorker({
      withTx: async (cb) => cb({}),
      bookingRepo: mockRepo,
      gateway: mockGateway
    });

    await worker.processBatch();

    assert.equal(markFailedCount, 1);
    assert.equal(revertConfirmedCount, 1);
    assert.equal(releasedInventoryCount, 0); // Inventory MUST stay locked!
  });

  it("prevents double-finalization if booking status is already cancelled", async () => {
    let releaseInventoryCount = 0;

    const fakePendingRefund = {
      refundId: "rf-3",
      bookingId: "b-3",
      paymentId: "p-3",
      userId: "u-3",
      razorpayRefundId: "rfnd_double",
      razorpayPaymentId: "pay_3",
      refundStatus: "pending",
      bookingStatus: "cancelled",
      roomsBooked: 1,
      checkIn: "2026-09-01",
      checkOut: "2026-09-03",
      roomId: "room-1"
    };

    const mockRepo = {
      claimPendingRefundsForLease: async () => [fakePendingRefund],
      finalizeProcessedRefundAtomically: async () => ({ success: true, alreadyFinalized: true, displayMessage: "Refund completed" }),
      releaseInventory: async () => {
        releaseInventoryCount++;
      },
      releaseRefundLeaseAndScheduleRetry: async () => {}
    };

    const mockGateway = {
      fetchRefund: async () => ({ id: "rfnd_double", status: "processed" })
    };

    const worker = new RefundReconciliationWorker({
      withTx: async (cb) => cb({}),
      bookingRepo: mockRepo,
      gateway: mockGateway
    });

    await worker.processBatch();

    assert.equal(releaseInventoryCount, 0);
  });
});
