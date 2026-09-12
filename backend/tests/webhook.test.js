import { describe, it } from "node:test";
import assert from "node:assert/strict";
import crypto from "crypto";
import { handleRazorpayWebhook, verifyWebhookSignature } from "../src/services/webhook.service.js";
import { createApp } from "../src/app.js";

describe("Razorpay Webhook Foundation Unit Tests (In-Memory Isolation & Rollback Simulation)", () => {
  const secret = "whsec_test_secret_key_999";
  const serverOrderId = "order_wh_101";
  const paymentId = "pay_wh_202";

  function createRealRazorpayPayload(eventType, orderId = serverOrderId, payId = paymentId, amount = 1180000, currency = "INR", orderEntity = null) {
    const obj = {
      entity: "event",
      account_id: "acc_test",
      event: eventType,
      contains: ["payment"],
      created_at: Math.floor(Date.now() / 1000),
      payload: {
        payment: {
          entity: {
            id: payId,
            entity: "payment",
            amount: amount,
            currency: currency,
            status: eventType === "payment.failed" ? "failed" : "captured",
            order_id: orderId,
            error_description: eventType === "payment.failed" ? "Raw sensitive error description" : undefined
          }
        },
        ...(orderEntity ? { order: { entity: orderEntity } } : {})
      }
    };
    return Buffer.from(JSON.stringify(obj), "utf8");
  }

  function signPayload(rawBuf, sec = secret) {
    return crypto
      .createHmac("sha256", sec)
      .update(rawBuf)
      .digest("hex");
  }

  function getBaseContext(status = "payment_pending") {
    return {
      bookingId: "booking-wh-1",
      userId: "user-wh-1",
      bookingStatus: status,
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
      paymentStatus: status === "confirmed" ? "captured" : "pending"
    };
  }

  // Transaction fake with explicit commit/rollback semantics
  function createTransactionFake() {
    const committedState = {
      webhookEvents: new Map(),
      payments: new Map(),
      bookings: new Map()
    };

    const withTxFake = async (cb) => {
      // Stage state changes in draft copies
      const draftEvents = new Map(committedState.webhookEvents);
      const draftPayments = new Map(committedState.payments);
      const draftBookings = new Map(committedState.bookings);

      const client = {
        _draftEvents: draftEvents,
        _draftPayments: draftPayments,
        _draftBookings: draftBookings
      };

      try {
        const res = await cb(client);
        // Callback resolved -> COMMIT staged changes!
        committedState.webhookEvents = draftEvents;
        committedState.payments = draftPayments;
        committedState.bookings = draftBookings;
        return res;
      } catch (err) {
        // Callback threw -> ROLLBACK! Staged changes discarded!
        throw err;
      }
    };

    const mockWebhookRepo = {
      recordWebhookEvent: async (client, { eventId, eventType, status, failureReason }) => {
        const map = client._draftEvents;
        if (map.has(eventId)) return null; // ON CONFLICT DO NOTHING
        const record = {
          id: `wh-${eventId}`,
          eventId,
          eventType,
          status,
          failureReason,
          createdAt: new Date().toISOString(),
          processedAt: null
        };
        map.set(eventId, record);
        return record;
      },
      updateWebhookEventStatus: async (client, eventId, status, failureReason = null) => {
        const map = client._draftEvents;
        const existing = map.get(eventId);
        if (existing) {
          existing.status = status;
          existing.failureReason = failureReason;
          existing.processedAt = new Date().toISOString();
        }
      },
      getWebhookEvent: async (client, eventId) => {
        return client._draftEvents.get(eventId) || null;
      }
    };

    return { withTxFake, committedState, mockWebhookRepo };
  }

  it("1. Permanent validation failure COMMITS status = failed before handler returns HTTP error", async () => {
    const { withTxFake, committedState, mockWebhookRepo } = createTransactionFake();
    const eventId = "evt_perm_fail_committed";

    // Order ID does not exist in DB -> permanent 404 validation failure!
    const rawBuf = createRealRazorpayPayload("payment.captured", "order_non_existent");
    const sig = signPayload(rawBuf);

    const mockRepo = {
      getPaymentContextByOrderId: async () => null // Order not found!
    };

    await assert.rejects(
      async () => {
        await handleRazorpayWebhook(rawBuf, sig, eventId, {
          withTx: withTxFake,
          bookingRepo: mockRepo,
          webhookRepo: mockWebhookRepo,
          webhookSecret: secret,
          webhookEnabled: true
        });
      },
      (err) => {
        assert.equal(err.statusCode, 404);
        assert.equal(err.message, "Payment order context not found");
        return true;
      }
    );

    // CRITICAL PROOF: Transaction COMMITTED the failed audit event!
    const committedEvent = committedState.webhookEvents.get(eventId);
    assert.ok(committedEvent, "Event record must survive transaction commit");
    assert.equal(committedEvent.status, "failed");
    assert.equal(committedEvent.failureReason, "Payment order context not found");
  });

  it("2. Transient DB exception ROLLS BACK transaction so event can be retried", async () => {
    const { withTxFake, committedState, mockWebhookRepo } = createTransactionFake();
    const eventId = "evt_transient_rollback";

    const rawBuf = createRealRazorpayPayload("payment.captured");
    const sig = signPayload(rawBuf);

    // DB query throws transient infrastructure exception
    const mockFailingRepo = {
      getPaymentContextByOrderId: async () => {
        throw new Error("PostgreSQL connection timeout / deadlock");
      }
    };

    await assert.rejects(
      async () => {
        await handleRazorpayWebhook(rawBuf, sig, eventId, {
          withTx: withTxFake,
          bookingRepo: mockFailingRepo,
          webhookRepo: mockWebhookRepo,
          webhookSecret: secret,
          webhookEnabled: true
        });
      },
      (err) => {
        assert.equal(err.message, "PostgreSQL connection timeout / deadlock");
        return true;
      }
    );

    // CRITICAL PROOF: Transaction ROLLED BACK and draft event was discarded!
    const committedEvent = committedState.webhookEvents.get(eventId);
    assert.equal(committedEvent, undefined, "Transient failure must roll back and leave 0 committed events for retry");
  });

  it("3. Proves old 'throw inside transaction' behavior would fail to persist failed audit status", async () => {
    const { withTxFake, committedState } = createTransactionFake();
    const eventId = "evt_old_bug_test";

    // Simulate old flawed pattern: updating status inside transaction and then throwing inside transaction
    const oldFlawedHandler = async () => {
      return withTxFake(async (client) => {
        client._draftEvents.set(eventId, { eventId, status: "failed", failureReason: "Order not found" });
        // Old code threw ApiError inside withTx callback!
        throw new Error("Simulated ApiError inside transaction");
      });
    };

    await assert.rejects(
      async () => { await oldFlawedHandler(); },
      (err) => {
        assert.equal(err.message, "Simulated ApiError inside transaction");
        return true;
      }
    );

    // PROOF OF OLD BUG: Status update was rolled back and lost because callback threw inside transaction!
    assert.equal(committedState.webhookEvents.get(eventId), undefined);
  });

  it("4. Validates x-razorpay-event-id header presence and length (1-255 chars)", async () => {
    const rawBuf = createRealRazorpayPayload("payment.captured");
    const sig = signPayload(rawBuf);

    // Missing header (null)
    await assert.rejects(
      async () => {
        await handleRazorpayWebhook(rawBuf, sig, null, { webhookSecret: secret, webhookEnabled: true });
      },
      (err) => {
        assert.equal(err.statusCode, 400);
        assert.equal(err.message, "Missing x-razorpay-event-id header");
        return true;
      }
    );

    // Empty/whitespace header
    await assert.rejects(
      async () => {
        await handleRazorpayWebhook(rawBuf, sig, "   ", { webhookSecret: secret, webhookEnabled: true });
      },
      (err) => {
        assert.equal(err.statusCode, 400);
        assert.equal(err.message, "Invalid x-razorpay-event-id header length");
        return true;
      }
    );

    // Header length > 255 chars
    const overlyLongHeader = "a".repeat(256);
    await assert.rejects(
      async () => {
        await handleRazorpayWebhook(rawBuf, sig, overlyLongHeader, { webhookSecret: secret, webhookEnabled: true });
      },
      (err) => {
        assert.equal(err.statusCode, 400);
        assert.equal(err.message, "Invalid x-razorpay-event-id header length");
        return true;
      }
    );
  });

  it("5. Stores controlled/sanitized code for payment.failed (no raw error_description)", async () => {
    const { withTxFake, committedState, mockWebhookRepo } = createTransactionFake();
    const eventId = "evt_failed_sanitized";

    const rawBuf = createRealRazorpayPayload("payment.failed");
    const sig = signPayload(rawBuf);

    const result = await handleRazorpayWebhook(rawBuf, sig, eventId, {
      withTx: withTxFake,
      bookingRepo: {},
      webhookRepo: mockWebhookRepo,
      webhookSecret: secret,
      webhookEnabled: true
    });

    assert.equal(result.success, true);
    assert.equal(result.status, "failed_logged");

    const record = committedState.webhookEvents.get(eventId);
    assert.equal(record.status, "failed_logged");
    assert.equal(record.failureReason, "payment_attempt_failed"); // Sanitized code!
  });

  it("6. Valid raw-body signature with x-razorpay-event-id header succeeds", async () => {
    const { withTxFake, committedState, mockWebhookRepo } = createTransactionFake();
    const eventId = "evt_valid_6";

    const rawBuf = createRealRazorpayPayload("payment.captured");
    const sig = signPayload(rawBuf);

    let capturedFromWebhookCalled = false;
    const mockRepo = {
      getPaymentContextByOrderId: async () => getBaseContext(),
      markPaymentCapturedFromWebhook: async (client, bookingId, payload) => {
        capturedFromWebhookCalled = true;
        assert.equal(payload.eventId, eventId);
        assert.equal(payload.razorpayPaymentId, paymentId);
      }
    };

    const result = await handleRazorpayWebhook(rawBuf, sig, eventId, {
      withTx: withTxFake,
      bookingRepo: mockRepo,
      webhookRepo: mockWebhookRepo,
      webhookSecret: secret,
      webhookEnabled: true
    });

    assert.equal(result.success, true);
    assert.equal(result.status, "confirmed");
    assert.equal(capturedFromWebhookCalled, true);
    assert.equal(committedState.webhookEvents.get(eventId).status, "processed");
  });

  it("7. Parsed/re-serialized body does not replace raw-body verification", async () => {
    const rawBuf = createRealRazorpayPayload("payment.captured");
    const sig = signPayload(rawBuf);

    const parsedObj = JSON.parse(rawBuf.toString("utf8"));
    const reSerializedBuf = Buffer.from(JSON.stringify(parsedObj, null, 2), "utf8");

    assert.throws(
      () => verifyWebhookSignature(reSerializedBuf, sig, secret),
      (err) => {
        assert.equal(err.statusCode, 400);
        assert.equal(err.message, "Invalid webhook signature");
        return true;
      }
    );
  });

  it("8. Order and payment entity cross-check validation", async () => {
    const { withTxFake, committedState, mockWebhookRepo } = createTransactionFake();
    const eventId = "evt_crosscheck_8";

    const mismatchedOrderEntity = { id: "order_mismatch_999", amount_paid: 1180000 };
    const rawBuf = createRealRazorpayPayload("order.paid", serverOrderId, paymentId, 1180000, "INR", mismatchedOrderEntity);
    const sig = signPayload(rawBuf);

    await assert.rejects(
      async () => {
        await handleRazorpayWebhook(rawBuf, sig, eventId, {
          withTx: withTxFake,
          bookingRepo: {},
          webhookRepo: mockWebhookRepo,
          webhookSecret: secret,
          webhookEnabled: true
        });
      },
      (err) => {
        assert.equal(err.statusCode, 400);
        assert.equal(err.message, "Order entity ID mismatch with payment entity order_id");
        return true;
      }
    );

    const record = committedState.webhookEvents.get(eventId);
    assert.equal(record.status, "failed");
    assert.equal(record.failureReason, "Order entity ID mismatch with payment entity order_id");
  });

  it("9. Webhook confirmation does not populate Checkout razorpay_signature column", async () => {
    const { withTxFake, mockWebhookRepo } = createTransactionFake();
    const eventId = "evt_sig_col_9";
    const rawBuf = createRealRazorpayPayload("payment.captured");
    const sig = signPayload(rawBuf);

    let capturedPayload = null;
    const mockRepo = {
      getPaymentContextByOrderId: async () => getBaseContext(),
      markPaymentCapturedFromWebhook: async (client, bookingId, payload) => {
        capturedPayload = payload;
      }
    };

    await handleRazorpayWebhook(rawBuf, sig, eventId, {
      withTx: withTxFake,
      bookingRepo: mockRepo,
      webhookRepo: mockWebhookRepo,
      webhookSecret: secret,
      webhookEnabled: true
    });

    assert.ok(capturedPayload);
    assert.equal(capturedPayload.razorpayPaymentId, paymentId);
    assert.equal(capturedPayload.eventId, eventId);
    assert.equal(capturedPayload.razorpaySignature, undefined); // Checkout signature column NOT populated!
  });

  it("10. Expired captured payment becomes reconciliation_required without inventory release", async () => {
    const { withTxFake, committedState, mockWebhookRepo } = createTransactionFake();
    const eventId = "evt_expired_10";

    const rawBuf = createRealRazorpayPayload("payment.captured");
    const sig = signPayload(rawBuf);

    let capturedCalled = false;
    let releaseInventoryCalled = false;
    const mockRepo = {
      getPaymentContextByOrderId: async () => getBaseContext("payment_failed"), // Expired!
      markPaymentCapturedFromWebhook: async () => { capturedCalled = true; },
      releaseInventory: async () => { releaseInventoryCalled = true; }
    };

    const result = await handleRazorpayWebhook(rawBuf, sig, eventId, {
      withTx: withTxFake,
      bookingRepo: mockRepo,
      webhookRepo: mockWebhookRepo,
      webhookSecret: secret,
      webhookEnabled: true
    });

    assert.equal(result.success, true);
    assert.equal(result.status, "reconciliation_required");
    assert.equal(committedState.webhookEvents.get(eventId).status, "reconciliation_required");
    assert.equal(capturedCalled, false);
    assert.equal(releaseInventoryCalled, false);
  });

  it("11. Malformed JSON sent to normal API route returns HTTP 400 before authentication", async () => {
    const app = createApp();
    const server = app.listen(0);
    const port = server.address().port;

    try {
      // Send malformed JSON to normal JSON route POST /api/v1/auth/sync
      const res = await fetch(`http://127.0.0.1:${port}/api/v1/auth/sync`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: "{ invalid json string: bad }"
      });

      // express.json() catches malformed JSON and returns HTTP 400 Bad Request BEFORE auth middleware runs!
      assert.equal(res.status, 400);
    } finally {
      server.close();
    }
  });

  it("12. Initial processing record has processedAt = null, while terminal status sets processedAt timestamp", async () => {
    const { withTxFake, committedState } = createTransactionFake();
    const eventId = "evt_timestamp_test_12";

    let recordedProcessedAt = undefined;

    const mockRepo = {
      getPaymentContextByOrderId: async () => getBaseContext(),
      markPaymentCapturedFromWebhook: async () => {}
    };

    const mockWebhookRepo = {
      recordWebhookEvent: async (client, { eventId, eventType, status }) => {
        const record = { id: `wh-${eventId}`, eventId, eventType, status, processedAt: null };
        client._draftEvents.set(eventId, record);
        recordedProcessedAt = record.processedAt; // Initially null!
        return record;
      },
      updateWebhookEventStatus: async (client, eventId, status, failureReason) => {
        const existing = client._draftEvents.get(eventId);
        if (existing) {
          existing.status = status;
          existing.processedAt = new Date().toISOString(); // Set on terminal status update!
        }
      }
    };

    const rawBuf = createRealRazorpayPayload("payment.captured");
    const sig = signPayload(rawBuf);

    await handleRazorpayWebhook(rawBuf, sig, eventId, {
      withTx: withTxFake,
      bookingRepo: mockRepo,
      webhookRepo: mockWebhookRepo,
      webhookSecret: secret,
      webhookEnabled: true
    });

    assert.equal(recordedProcessedAt, null, "Initial processing record must have processedAt = null");
    const committedEvent = committedState.webhookEvents.get(eventId);
    assert.equal(committedEvent.status, "processed");
    assert.ok(committedEvent.processedAt, "Terminal status must set processedAt timestamp");
  });

  it("13. Uses only documented webhook status values in event lifecycle", async () => {
    const allowedStatuses = new Set([
      "received",
      "processing",
      "processed",
      "failed",
      "reconciliation_required",
      "failed_logged",
      "ignored"
    ]);

    const { withTxFake, committedState, mockWebhookRepo } = createTransactionFake();
    const eventId = "evt_status_check_13";

    const rawBuf = createRealRazorpayPayload("payment.captured");
    const sig = signPayload(rawBuf);

    const mockRepo = {
      getPaymentContextByOrderId: async () => getBaseContext(),
      markPaymentCapturedFromWebhook: async () => {}
    };

    await handleRazorpayWebhook(rawBuf, sig, eventId, {
      withTx: withTxFake,
      bookingRepo: mockRepo,
      webhookRepo: mockWebhookRepo,
      webhookSecret: secret,
      webhookEnabled: true
    });

    const finalStatus = committedState.webhookEvents.get(eventId).status;
    assert.ok(allowedStatuses.has(finalStatus), `Status '${finalStatus}' must be one of documented allowed statuses`);
  });
});
