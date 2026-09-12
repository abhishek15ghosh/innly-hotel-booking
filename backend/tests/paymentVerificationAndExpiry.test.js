import { describe, it } from "node:test";
import assert from "node:assert/strict";
import crypto from "crypto";
import { verifyPaymentSignature } from "../src/services/payment.service.js";
import { processExpiredBookings } from "../src/services/bookingExpiry.service.js";

describe("Pre-Payment Backend Safety & Expiry Unit Tests (In-Memory Isolation)", () => {
  const secret = "test_razorpay_secret_key_123";
  const userA = { id: "user-uuid-A", firebaseUid: "uid-A" };
  const userB = { id: "user-uuid-B", firebaseUid: "uid-B" };
  const validBookingId = "booking-uuid-101";
  const serverOrderId = "order_server_abc123";
  const paymentId = "pay_xyz789";

  function createValidSignature(orderId, payId, keySecret = secret) {
    return crypto
      .createHmac("sha256", keySecret)
      .update(`${orderId}|${payId}`)
      .digest("hex");
  }

  function getBaseContext() {
    return {
      bookingId: validBookingId,
      userId: userA.id,
      bookingStatus: "payment_pending",
      bookingAmount: 11800,
      bookingCurrency: "INR",
      roomsBooked: 1,
      checkIn: "2026-08-10",
      checkOut: "2026-08-12",
      roomId: "room-1",
      paymentId: "pay-db-1",
      razorpayOrderId: serverOrderId,
      storedRazorpayPaymentId: null,
      paymentAmount: 11800,
      paymentCurrency: "INR",
      paymentStatus: "pending"
    };
  }

  it("1. Production default cannot bypass Razorpay fetch (fails closed with 502)", async () => {
    const mockRepo = {
      getPaymentVerificationContext: async () => getBaseContext()
    };
    const mockWithTx = async (cb) => cb({});

    const payload = {
      bookingId: validBookingId,
      razorpayOrderId: serverOrderId,
      razorpayPaymentId: paymentId,
      razorpaySignature: createValidSignature(serverOrderId, paymentId)
    };

    // Razorpay client where payments.fetch throws a network error
    const mockFailingRazorpay = {
      payments: {
        fetch: async () => { throw new Error("Razorpay gateway unreachable"); }
      }
    };

    await assert.rejects(
      async () => {
        await verifyPaymentSignature(userA, payload, {
          withTx: mockWithTx,
          bookingRepo: mockRepo,
          razorpay: mockFailingRazorpay,
          razorpaySecret: secret
        });
      },
      (err) => {
        assert.equal(err.statusCode, 502);
        assert.equal(err.message, "Payment verification unavailable. Please try again.");
        return true;
      }
    );
  });

  it("2. Authorized status is not recorded as captured", async () => {
    let markCapturedCalled = false;
    const mockRepo = {
      getPaymentVerificationContext: async () => getBaseContext(),
      markPaymentCaptured: async () => { markCapturedCalled = true; }
    };
    const mockWithTx = async (cb) => cb({});

    const payload = {
      bookingId: validBookingId,
      razorpayOrderId: serverOrderId,
      razorpayPaymentId: paymentId,
      razorpaySignature: createValidSignature(serverOrderId, paymentId)
    };

    const mockRazorpayAuthorized = {
      payments: {
        fetch: async () => ({
          id: paymentId,
          order_id: serverOrderId,
          status: "authorized",
          amount: 1180000,
          currency: "INR"
        })
      }
    };

    const result = await verifyPaymentSignature(userA, payload, {
      withTx: mockWithTx,
      bookingRepo: mockRepo,
      razorpay: mockRazorpayAuthorized,
      razorpaySecret: secret
    });

    assert.equal(result.bookingStatus, "payment_pending");
    assert.equal(result.paymentStatus, "authorized");
    assert.equal(markCapturedCalled, false); // NOT recorded as captured!
  });

  it("3. Missing or non-functional Razorpay verification fails closed with 502", async () => {
    const mockRepo = {
      getPaymentVerificationContext: async () => getBaseContext()
    };
    const mockWithTx = async (cb) => cb({});

    const payload = {
      bookingId: validBookingId,
      razorpayOrderId: serverOrderId,
      razorpayPaymentId: paymentId,
      razorpaySignature: createValidSignature(serverOrderId, paymentId)
    };

    await assert.rejects(
      async () => {
        await verifyPaymentSignature(userA, payload, {
          withTx: mockWithTx,
          bookingRepo: mockRepo,
          razorpay: null, // missing client
          razorpaySecret: secret
        });
      },
      (err) => {
        assert.equal(err.statusCode, 502);
        assert.equal(err.message, "Payment verification unavailable. Please try again.");
        return true;
      }
    );
  });

  it("4. Wrong currency is explicitly tested and rejected", async () => {
    const mockRepo = {
      getPaymentVerificationContext: async () => getBaseContext()
    };
    const mockWithTx = async (cb) => cb({});

    const payload = {
      bookingId: validBookingId,
      razorpayOrderId: serverOrderId,
      razorpayPaymentId: paymentId,
      razorpaySignature: createValidSignature(serverOrderId, paymentId)
    };

    const mockRazorpayUSD = {
      payments: {
        fetch: async () => ({
          id: paymentId,
          order_id: serverOrderId,
          status: "captured",
          amount: 1180000,
          currency: "USD" // Currency mismatch!
        })
      }
    };

    await assert.rejects(
      async () => {
        await verifyPaymentSignature(userA, payload, {
          withTx: mockWithTx,
          bookingRepo: mockRepo,
          razorpay: mockRazorpayUSD,
          razorpaySecret: secret
        });
      },
      (err) => {
        assert.equal(err.statusCode, 400);
        assert.equal(err.message, "Razorpay payment currency mismatch");
        return true;
      }
    );
  });

  it("5. Duplicate verification with another payment ID fails", async () => {
    const mockRepo = {
      getPaymentVerificationContext: async () => ({
        ...getBaseContext(),
        bookingStatus: "confirmed",
        paymentStatus: "captured",
        storedRazorpayPaymentId: "pay_original_111"
      })
    };
    const mockWithTx = async (cb) => cb({});

    // Original payment ID succeeds idempotently
    const originalPayload = {
      bookingId: validBookingId,
      razorpayOrderId: serverOrderId,
      razorpayPaymentId: "pay_original_111",
      razorpaySignature: createValidSignature(serverOrderId, "pay_original_111")
    };

    const res = await verifyPaymentSignature(userA, originalPayload, {
      withTx: mockWithTx,
      bookingRepo: mockRepo,
      razorpaySecret: secret
    });
    assert.equal(res.bookingStatus, "confirmed");

    // Different payment ID submitted for same confirmed booking fails!
    const differentPayload = {
      bookingId: validBookingId,
      razorpayOrderId: serverOrderId,
      razorpayPaymentId: "pay_attacker_999",
      razorpaySignature: createValidSignature(serverOrderId, "pay_attacker_999")
    };

    await assert.rejects(
      async () => {
        await verifyPaymentSignature(userA, differentPayload, {
          withTx: mockWithTx,
          bookingRepo: mockRepo,
          razorpaySecret: secret
        });
      },
      (err) => {
        assert.equal(err.statusCode, 400);
        assert.equal(err.message, "Razorpay payment ID mismatch for confirmed booking");
        return true;
      }
    );
  });

  it("6. Captured payment cannot be expired even if booking state is inconsistent", async () => {
    let releaseCalled = false;

    // Simulated inconsistent state: booking is 'payment_pending' but payment is 'captured'
    const inconsistentBooking = {
      bookingId: "booking-inconsistent",
      userId: userA.id,
      bookingStatus: "payment_pending",
      paymentStatus: "captured", // Captured!
      roomsBooked: 1,
      checkIn: "2026-08-10",
      checkOut: "2026-08-12",
      roomId: "room-1"
    };

    const mockRepo = {
      getExpiredPendingBookings: async () => [inconsistentBooking],
      releaseInventory: async () => { releaseCalled = true; },
      markPaymentFailed: async () => { throw new Error("Should not mark captured payment failed"); }
    };
    const mockWithTx = async (cb) => cb({});

    const result = await processExpiredBookings({ withTx: mockWithTx, bookingRepo: mockRepo });
    assert.equal(result.processedCount, 0); // Defensive skip!
    assert.equal(releaseCalled, false);
  });

  it("7. Conditional-update conflict (rowCount = 0) is handled safely", async () => {
    const mockRepo = {
      getPaymentVerificationContext: async () => getBaseContext(),
      markPaymentCaptured: async () => {
        // Throw 409 ApiError as repository does when rowCount === 0
        const { ApiError } = await import("../src/utils/apiError.js");
        throw new ApiError(409, "Payment state conflict: payment is not in pending status");
      }
    };
    const mockWithTx = async (cb) => cb({});

    const payload = {
      bookingId: validBookingId,
      razorpayOrderId: serverOrderId,
      razorpayPaymentId: paymentId,
      razorpaySignature: createValidSignature(serverOrderId, paymentId)
    };

    const mockRazorpay = {
      payments: {
        fetch: async () => ({
          id: paymentId,
          order_id: serverOrderId,
          status: "captured",
          amount: 1180000,
          currency: "INR"
        })
      }
    };

    await assert.rejects(
      async () => {
        await verifyPaymentSignature(userA, payload, {
          withTx: mockWithTx,
          bookingRepo: mockRepo,
          razorpay: mockRazorpay,
          razorpaySecret: secret
        });
      },
      (err) => {
        assert.equal(err.statusCode, 409);
        assert.equal(err.message, "Payment state conflict: payment is not in pending status");
        return true;
      }
    );
  });

  it("8. Ownership verification prevents cross-user access without mutations", async () => {
    let mutationsOccurred = false;
    const mockRepo = {
      getPaymentVerificationContext: async (client, bookingId, userId) => {
        if (userId !== userA.id) return null;
        return getBaseContext();
      },
      markPaymentCaptured: async () => { mutationsOccurred = true; }
    };
    const mockWithTx = async (cb) => cb({});

    const payload = {
      bookingId: validBookingId,
      razorpayOrderId: serverOrderId,
      razorpayPaymentId: paymentId,
      razorpaySignature: createValidSignature(serverOrderId, paymentId)
    };

    await assert.rejects(
      async () => {
        await verifyPaymentSignature(userB, payload, {
          withTx: mockWithTx,
          bookingRepo: mockRepo,
          razorpaySecret: secret
        });
      },
      (err) => {
        assert.equal(err.statusCode, 404);
        return true;
      }
    );

    assert.equal(mutationsOccurred, false);
  });
});
