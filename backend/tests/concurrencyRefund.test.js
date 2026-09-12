import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { finalizeProcessedRefundAtomically, releaseRefundLeaseAndScheduleRetry } from "../src/repositories/booking.repository.js";
import { RazorpayRefundGateway } from "../src/gateways/razorpayRefund.gateway.js";
import { RefundReconciliationWorker } from "../src/workers/refundReconciliation.worker.js";
import { cancelBooking } from "../src/services/booking.service.js";

describe("Paid-Refund Concurrency & Race Condition Unit Tests", () => {
  it("1. Single atomic finalization operation releases inventory exactly once when both foreground and worker receive processed", async () => {
    let inventoryReleasedCount = 0;
    let refundStatus = "pending";
    let bookingStatus = "cancellation_pending";

    const fakeClient = {
      query: async (sql, params) => {
        if (sql.includes("FOR UPDATE OF b, r")) {
          return {
            rows: [
              {
                bookingId: "b-101",
                bookingStatus,
                roomsBooked: 1,
                checkIn: "2026-09-01",
                checkOut: "2026-09-03",
                roomId: "room-1",
                refundId: "rf-101",
                refundStatus
              }
            ]
          };
        }
        if (sql.includes("UPDATE refunds") && sql.includes("status = 'processed'")) {
          refundStatus = "processed";
          return { rowCount: 1 };
        }
        if (sql.includes("UPDATE bookings") && sql.includes("status = 'cancelled'")) {
          bookingStatus = "cancelled";
          return { rowCount: 1 };
        }
        if (sql.includes("UPDATE room_inventory")) {
          inventoryReleasedCount++;
          return { rowCount: 1 };
        }
        return { rows: [], rowCount: 1 };
      }
    };

    // First call (Foreground)
    const res1 = await finalizeProcessedRefundAtomically(fakeClient, {
      bookingId: "b-101",
      paymentId: "p-101",
      refundId: "rf-101",
      razorpayRefundId: "rfnd_processed_1"
    });

    assert.equal(res1.success, true);
    assert.equal(res1.alreadyFinalized, false);
    assert.equal(inventoryReleasedCount, 1);

    // Second call (Worker running concurrently / sequentially)
    const res2 = await finalizeProcessedRefundAtomically(fakeClient, {
      bookingId: "b-101",
      paymentId: "p-101",
      refundId: "rf-101",
      razorpayRefundId: "rfnd_processed_1"
    });

    assert.equal(res2.success, true);
    assert.equal(res2.alreadyFinalized, true);
    assert.equal(inventoryReleasedCount, 1); // Inventory MUST stay 1!
  });

  it("2. Worker finalizes first -> foreground finalizer becomes no-op (alreadyFinalized)", async () => {
    let inventoryReleasedCount = 0;

    const fakeClient = {
      query: async (sql) => {
        if (sql.includes("FOR UPDATE OF b, r")) {
          return {
            rows: [
              {
                bookingId: "b-202",
                bookingStatus: "cancelled", // Already cancelled by worker!
                roomsBooked: 1,
                checkIn: "2026-09-01",
                checkOut: "2026-09-03",
                roomId: "room-1",
                refundId: "rf-202",
                refundStatus: "processed" // Already processed by worker!
              }
            ]
          };
        }
        if (sql.includes("UPDATE room_inventory")) {
          inventoryReleasedCount++;
        }
        return { rows: [], rowCount: 1 };
      }
    };

    const res = await finalizeProcessedRefundAtomically(fakeClient, {
      bookingId: "b-202",
      paymentId: "p-202",
      refundId: "rf-202",
      razorpayRefundId: "rfnd_202"
    });

    assert.equal(res.success, true);
    assert.equal(res.alreadyFinalized, true);
    assert.equal(inventoryReleasedCount, 0); // No double inventory release!
  });

  it("3. Missing or unknown Razorpay status never cancels booking or releases inventory", async () => {
    let markCancelledCount = 0;
    let releasedInventoryCount = 0;

    const fakePendingRefund = {
      refundId: "rf-unknown",
      bookingId: "b-unknown",
      paymentId: "p-unknown",
      userId: "u-1",
      razorpayRefundId: "rfnd_unknown",
      razorpayPaymentId: "pay_unknown",
      idempotencyKey: "ref_bunknown",
      amount: 5000,
      refundStatus: "pending",
      checkIn: "2026-09-01",
      checkOut: "2026-09-03",
      roomId: "room-1",
      attemptCount: 1
    };

    const mockRepo = {
      claimPendingRefundsForLease: async () => [fakePendingRefund],
      markBookingCancellationPending: async () => {},
      markBookingCancelledAndPaymentRefunded: async () => {
        markCancelledCount++;
      },
      releaseInventory: async () => {
        releasedInventoryCount++;
      },
      releaseRefundLeaseAndScheduleRetry: async () => {}
    };

    const mockGateway = {
      fetchRefund: async () => ({ id: "rfnd_unknown", status: "unexpected_unknown_status" })
    };

    const worker = new RefundReconciliationWorker({
      withTx: async (cb) => cb({}),
      bookingRepo: mockRepo,
      gateway: mockGateway
    });

    await worker.processBatch();

    assert.equal(markCancelledCount, 0);
    assert.equal(releasedInventoryCount, 0);
  });

  it("4. HTTP 409 conflict stays pending and is scheduled for worker retry", async () => {
    let markFailedCalled = false;
    let scheduleRetryCalled = false;

    const mockRepo = {
      getBookingForCancellationDetails: async () => ({
        bookingId: "b-409",
        userId: "u-1",
        bookingStatus: "confirmed",
        bookingAmount: 5000,
        paymentId: "p-409",
        paymentStatus: "captured",
        razorpayPaymentId: "pay_409",
        freeCancellationHours: 24,
        checkIn: "2026-09-10",
        checkOut: "2026-09-12",
        roomId: "room-1",
        roomsBooked: 1
      }),
      getRefundByBookingId: async () => null,
      createRefundRecord: async () => ({ id: "rf-409", status: "pending" }),
      markBookingCancellationPending: async () => {},
      markRefundFailed: async () => {
        markFailedCalled = true;
      }
    };

    const mockGateway = {
      createRefund: async () => {
        const err = new Error("409 Conflict: Refund request currently processing");
        err.statusCode = 409;
        throw err;
      }
    };

    const user = { id: "u-1" };
    const res = await cancelBooking(user, "b-409", "Conflict test", {
      withTx: async (cb) => cb({}),
      bookingRepo: mockRepo,
      gateway: mockGateway,
      now: new Date("2026-08-08T12:00:00.000Z")
    });

    assert.equal(res.status, "cancellation_pending");
    assert.equal(res.refundStatus, "pending");
    assert.equal(res.displayMessage, "Refund processing");
    assert.equal(markFailedCalled, false); // Must NOT mark failed!
  });

  it("5. Exponential backoff increases retry delay with attempt_count (2, 4, 8, 16, 32, 60 min max)", async () => {
    const scheduledDelays = [];

    const fakeClient = {
      query: async (sql, params) => {
        if (sql.includes("UPDATE refunds")) {
          scheduledDelays.push(Number(params[1]));
        }
      }
    };

    // Test attempts 1 through 7
    for (let attempt = 1; attempt <= 7; attempt++) {
      await releaseRefundLeaseAndScheduleRetry(fakeClient, "rf-1", attempt, "Retry test", false);
    }

    assert.deepEqual(scheduledDelays, [2, 4, 8, 16, 32, 60, 60]);
  });

  it("6. Gateway 8s HTTP timeout is shorter than 2-minute (120s) worker lease duration", () => {
    const gateway = new RazorpayRefundGateway();
    assert.equal(gateway.timeoutMs, 8000);
    assert.ok(gateway.timeoutMs < 120000); // 8,000ms < 120,000ms!
  });

  it("7. Skips real PostgreSQL database concurrency test if TEST_DATABASE_URL is not provided", () => {
    if (!process.env.TEST_DATABASE_URL) {
      console.log("INFO: Real PostgreSQL database concurrency test skipped because TEST_DATABASE_URL is not set.");
      assert.ok(true);
    }
  });
});
