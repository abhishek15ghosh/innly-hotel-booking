import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { cancelBooking } from "../src/services/booking.service.js";

describe("Booking Cancellation & Razorpay Refund Safety Tests (In-Memory Isolation)", () => {
  const user = { id: "user-uuid-101", email: "user@example.com" };

  it("1. Rejects cancellation request when user is unauthenticated or missing user.id", async () => {
    await assert.rejects(
      async () => {
        await cancelBooking(null, "b-1", "Change of plans");
      },
      (err) => {
        assert.equal(err.statusCode, 403);
        return true;
      }
    );
  });

  it("2. Rejects cancellation request missing reason or short reason", async () => {
    await assert.rejects(
      async () => {
        await cancelBooking(user, "b-1", "no");
      },
      (err) => {
        assert.equal(err.statusCode, 400);
        assert.equal(err.message, "A valid cancellation reason (at least 3 characters) is required");
        return true;
      }
    );
  });

  it("3. Rejects cancellation attempt by non-owner (404)", async () => {
    const mockRepo = {
      getBookingForCancellationDetails: async (client, bookingId, userId) => {
        assert.equal(userId, "user-uuid-101");
        return null;
      }
    };

    await assert.rejects(
      async () => {
        await cancelBooking(user, "b-999", "Need to cancel", {
          withTx: async (cb) => cb({}),
          bookingRepo: mockRepo
        });
      },
      (err) => {
        assert.equal(err.statusCode, 404);
        assert.equal(err.message, "Booking not found");
        return true;
      }
    );
  });

  it("4. Cancels pending unpaid booking immediately and releases inventory exactly once", async () => {
    let releasedCount = 0;
    let cancelledRecordCount = 0;

    const mockRepo = {
      getBookingForCancellationDetails: async () => ({
        bookingId: "b-pending-1",
        userId: user.id,
        bookingStatus: "payment_pending",
        roomsBooked: 1,
        checkIn: "2026-09-01",
        checkOut: "2026-09-03",
        roomId: "room-101"
      }),
      releaseInventory: async (client, roomId, dates, rooms) => {
        releasedCount++;
        assert.equal(roomId, "room-101");
        assert.equal(rooms, 1);
      },
      cancelBookingRecord: async (client, bookingId, reason) => {
        cancelledRecordCount++;
        assert.equal(bookingId, "b-pending-1");
        assert.equal(reason, "Change of plans");
      }
    };

    const res = await cancelBooking(user, "b-pending-1", "Change of plans", {
      withTx: async (cb) => cb({}),
      bookingRepo: mockRepo
    });

    assert.equal(res.status, "cancelled");
    assert.equal(res.displayMessage, "Booking cancelled");
    assert.equal(releasedCount, 1);
    assert.equal(cancelledRecordCount, 1);
  });

  it("5. Makes repeated cancellation of an already cancelled booking idempotent", async () => {
    let releasedCount = 0;

    const mockRepo = {
      getBookingForCancellationDetails: async () => ({
        bookingId: "b-already-cancelled",
        userId: user.id,
        bookingStatus: "cancelled",
        checkIn: "2026-09-01",
        checkOut: "2026-09-03"
      }),
      getRefundByBookingId: async () => ({ status: "processed" }),
      releaseInventory: async () => {
        releasedCount++;
      }
    };

    const res = await cancelBooking(user, "b-already-cancelled", "Duplicate tap", {
      withTx: async (cb) => cb({}),
      bookingRepo: mockRepo
    });

    assert.equal(res.status, "cancelled");
    assert.equal(res.refundStatus, "processed");
    assert.equal(res.displayMessage, "Refund completed");
    assert.equal(releasedCount, 0); // Must NOT release inventory again!
  });

  it("6. Rejects cancellation when free-cancellation deadline has passed (409)", async () => {
    const pastCheckInBooking = {
      bookingId: "b-confirmed-expired",
      userId: user.id,
      bookingStatus: "confirmed",
      checkIn: "2026-08-01",
      checkOut: "2026-08-03",
      freeCancellationHours: 24,
      paymentStatus: "captured",
      razorpayPaymentId: "pay_captured_123"
    };

    const mockRepo = {
      getBookingForCancellationDetails: async () => pastCheckInBooking
    };

    const fakeNow = new Date("2026-08-08T12:00:00.000Z");

    await assert.rejects(
      async () => {
        await cancelBooking(user, "b-confirmed-expired", "Late cancellation", {
          withTx: async (cb) => cb({}),
          bookingRepo: mockRepo,
          now: fakeNow
        });
      },
      (err) => {
        assert.equal(err.statusCode, 409);
        assert.equal(err.message, "Free cancellation period has ended");
        return true;
      }
    );
  });

  it("7. Processed refund marks booking cancelled, payment refunded, and releases inventory once", async () => {
    let refundApiCalled = false;
    let refundRecordCreated = false;
    let bookingCancelledAndRefunded = false;
    let releasedInventoryCount = 0;

    const mockRepo = {
      getBookingForCancellationDetails: async () => ({
        bookingId: "b-confirmed-valid",
        userId: user.id,
        bookingStatus: "confirmed",
        bookingAmount: 11800,
        bookingCurrency: "INR",
        paymentId: "p-uuid-1",
        paymentAmount: 11800,
        paymentCurrency: "INR",
        paymentStatus: "captured",
        razorpayPaymentId: "pay_captured_888",
        freeCancellationHours: 24,
        checkIn: "2026-09-10",
        checkOut: "2026-09-12",
        roomId: "room-202",
        roomsBooked: 1
      }),
      getRefundByBookingId: async () => null,
      createRefundRecord: async (client, payload) => {
        refundRecordCreated = true;
        assert.equal(payload.idempotencyKey, "ref_bconfirmedvalid");
        return { id: "rf-uuid-1", status: "pending" };
      },
      markBookingCancellationPending: async () => {},
      finalizeProcessedRefundAtomically: async (client, params) => {
        bookingCancelledAndRefunded = true;
        releasedInventoryCount++;
        assert.equal(params.bookingId, "b-confirmed-valid");
        assert.equal(params.razorpayRefundId, "rfnd_razorpay_999");
        return { success: true, alreadyFinalized: false, displayMessage: "Refund completed" };
      },
      releaseInventory: async () => {
        releasedInventoryCount++;
      }
    };

    const mockGateway = {
      createRefund: async (paymentId, params, options) => {
        refundApiCalled = true;
        assert.equal(paymentId, "pay_captured_888");
        assert.equal(params.amount, 1180000);
        assert.equal(options.idempotencyKey, "ref_bconfirmedvalid");
        return { id: "rfnd_razorpay_999", status: "processed" };
      }
    };

    const fakeNow = new Date("2026-08-08T12:00:00.000Z");

    const res = await cancelBooking(user, "b-confirmed-valid", "Personal emergency", {
      withTx: async (cb) => cb({}),
      bookingRepo: mockRepo,
      gateway: mockGateway,
      now: fakeNow
    });

    assert.equal(res.status, "cancelled");
    assert.equal(res.refundStatus, "processed");
    assert.equal(res.displayMessage, "Refund completed");
    assert.equal(refundApiCalled, true);
    assert.equal(refundRecordCreated, true);
    assert.equal(bookingCancelledAndRefunded, true);
    assert.equal(releasedInventoryCount, 1);
  });

  it("8. Definitive Razorpay rejection leaves booking confirmed and reverts payment state (502)", async () => {
    let releasedInventoryCount = 0;
    let markFailedCalled = false;
    let revertedConfirmed = false;

    const mockRepo = {
      getBookingForCancellationDetails: async () => ({
        bookingId: "b-confirmed-fail",
        userId: user.id,
        bookingStatus: "confirmed",
        bookingAmount: 5000,
        paymentId: "p-uuid-2",
        paymentAmount: 5000,
        paymentStatus: "captured",
        razorpayPaymentId: "pay_captured_fail",
        freeCancellationHours: 24,
        checkIn: "2026-09-10",
        checkOut: "2026-09-12",
        roomId: "room-202",
        roomsBooked: 1
      }),
      getRefundByBookingId: async () => null,
      createRefundRecord: async () => ({ id: "rf-uuid-fail", status: "pending" }),
      markBookingCancellationPending: async () => {},
      markRefundFailed: async () => {
        markFailedCalled = true;
      },
      revertBookingToConfirmed: async () => {
        revertedConfirmed = true;
      },
      releaseInventory: async () => {
        releasedInventoryCount++;
      }
    };

    const mockGateway = {
      createRefund: async () => {
        const err = new Error("Payment rejected by bank");
        err.statusCode = 400;
        throw err;
      }
    };

    await assert.rejects(
      async () => {
        await cancelBooking(user, "b-confirmed-fail", "Emergency cancel", {
          withTx: async (cb) => cb({}),
          bookingRepo: mockRepo,
          gateway: mockGateway,
          now: new Date("2026-08-08T12:00:00.000Z")
        });
      },
      (err) => {
        assert.equal(err.statusCode, 502);
        assert.equal(err.message, "Unable to process Razorpay refund. Booking remains confirmed.");
        return true;
      }
    );

    assert.equal(markFailedCalled, true);
    assert.equal(revertedConfirmed, true);
    assert.equal(releasedInventoryCount, 0); // Inventory MUST stay locked!
  });

  it("9. Network timeout keeps booking in cancellation_pending for reconciliation worker", async () => {
    let releasedInventoryCount = 0;

    const mockRepo = {
      getBookingForCancellationDetails: async () => ({
        bookingId: "b-timeout-1",
        userId: user.id,
        bookingStatus: "confirmed",
        bookingAmount: 5000,
        paymentId: "p-uuid-3",
        paymentStatus: "captured",
        razorpayPaymentId: "pay_captured_timeout",
        freeCancellationHours: 24,
        checkIn: "2026-09-10",
        checkOut: "2026-09-12",
        roomId: "room-202",
        roomsBooked: 1
      }),
      getRefundByBookingId: async () => null,
      createRefundRecord: async () => ({ id: "rf-timeout-1", status: "pending" }),
      markBookingCancellationPending: async () => {},
      releaseInventory: async () => {
        releasedInventoryCount++;
      }
    };

    const mockGateway = {
      createRefund: async () => {
        const err = new Error("ETIMEDOUT");
        err.statusCode = 504;
        throw err;
      }
    };

    const res = await cancelBooking(user, "b-timeout-1", "Timeout test", {
      withTx: async (cb) => cb({}),
      bookingRepo: mockRepo,
      gateway: mockGateway,
      now: new Date("2026-08-08T12:00:00.000Z")
    });

    assert.equal(res.status, "cancellation_pending");
    assert.equal(res.refundStatus, "pending");
    assert.equal(res.displayMessage, "Refund processing");
    assert.equal(releasedInventoryCount, 0);
  });

  it("10. Repeated Cancel taps during cancellation_pending return REFUND_PENDING with zero additional gateway calls", async () => {
    let gatewayCalled = false;
    let releasedInventoryCount = 0;

    const mockRepo = {
      getBookingForCancellationDetails: async () => ({
        bookingId: "b-pending-tap-2",
        userId: user.id,
        bookingStatus: "cancellation_pending",
        checkIn: "2026-09-10",
        checkOut: "2026-09-12",
        freeCancellationHours: 24
      }),
      getRefundByBookingId: async () => ({ status: "pending" }),
      releaseInventory: async () => {
        releasedInventoryCount++;
      }
    };

    const mockGateway = {
      createRefund: async () => {
        gatewayCalled = true;
        return { id: "rfnd_should_not_call", status: "processed" };
      }
    };

    const res = await cancelBooking(user, "b-pending-tap-2", "Second tap reason", {
      withTx: async (cb) => cb({}),
      bookingRepo: mockRepo,
      gateway: mockGateway,
      now: new Date("2026-08-08T12:00:00.000Z")
    });

    assert.equal(res.status, "cancellation_pending");
    assert.equal(res.refundStatus, "pending");
    assert.equal(res.displayMessage, "Refund processing");
    assert.equal(gatewayCalled, false);
    assert.equal(releasedInventoryCount, 0);
  });
});
