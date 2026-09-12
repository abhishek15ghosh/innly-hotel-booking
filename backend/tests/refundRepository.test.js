import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { createRefundRecord } from "../src/repositories/booking.repository.js";

describe("Refund Repository Unit Tests (Fake DB Client)", () => {
  it("createRefundRecord uses exact 10 placeholders and passes exactly 10 matching arguments in correct column order", async () => {
    let capturedSql = null;
    let capturedParams = null;

    const fakeClient = {
      query: async (sql, params) => {
        capturedSql = sql;
        capturedParams = params;
        return { rows: [{ id: "refund-123", bookingId: "b-1", status: "pending", razorpayRefundId: null }] };
      }
    };

    const payload = {
      bookingId: "b-uuid-1",
      paymentId: "p-uuid-1",
      userId: "u-uuid-1",
      razorpayRefundId: "rfnd_123",
      razorpayPaymentId: "pay_123",
      idempotencyKey: "ref_b_uuid_1",
      amount: 5000,
      currency: "INR",
      status: "pending",
      reason: "Customer request"
    };

    const result = await createRefundRecord(fakeClient, payload);

    assert.equal(result.id, "refund-123");
    assert.ok(capturedSql.includes("INSERT INTO refunds"));

    // Verify SQL placeholders count ($1 through $10)
    const placeholders = capturedSql.match(/\$\d+/g);
    assert.equal(placeholders.length, 10);
    assert.deepEqual(placeholders, ["$1", "$2", "$3", "$4", "$5", "$6", "$7", "$8", "$9", "$10"]);

    // Verify exact 10 binding arguments passed
    assert.equal(capturedParams.length, 10);

    // Verify argument values match field definitions in exact column order:
    // booking_id ($1), payment_id ($2), user_id ($3), razorpay_refund_id ($4), razorpay_payment_id ($5),
    // idempotency_key ($6), amount ($7), currency ($8), status ($9), reason ($10)
    assert.equal(capturedParams[0], payload.bookingId);
    assert.equal(capturedParams[1], payload.paymentId);
    assert.equal(capturedParams[2], payload.userId);
    assert.equal(capturedParams[3], payload.razorpayRefundId);
    assert.equal(capturedParams[4], payload.razorpayPaymentId);
    assert.equal(capturedParams[5], payload.idempotencyKey);
    assert.equal(capturedParams[6], payload.amount);
    assert.equal(capturedParams[7], payload.currency);
    assert.equal(capturedParams[8], payload.status);
    assert.equal(capturedParams[9], payload.reason);
  });
});
