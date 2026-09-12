import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { RazorpayRefundGateway } from "../src/gateways/razorpayRefund.gateway.js";

describe("RazorpayRefundGateway Unit Tests (Mocked Local Fetch)", () => {
  it("createRefund sends POST /v1/payments/{paymentId}/refund with X-Refund-Idempotency header, Basic Auth, and correct body", async () => {
    let capturedUrl = null;
    let capturedOptions = null;

    const mockFetch = async (url, options) => {
      capturedUrl = url;
      capturedOptions = options;
      return {
        ok: true,
        status: 200,
        json: async () => ({
          id: "rfnd_mock_12345",
          entity: "refund",
          amount: 1180000,
          currency: "INR",
          payment_id: "pay_mock_999",
          status: "processed"
        })
      };
    };

    const gateway = new RazorpayRefundGateway({
      keyId: "rzp_test_mock_key",
      keySecret: "mock_secret_123",
      baseUrl: "http://localhost:9999/v1",
      fetchFn: mockFetch
    });

    const paymentId = "pay_mock_999";
    const idempotencyKey = "ref_3d645817b48d45c9aaf1d5f83ec9b398";
    const params = {
      amount: 1180000,
      notes: { bookingId: "b-101", reason: "Change of plans" },
      receipt: idempotencyKey
    };

    const response = await gateway.createRefund(paymentId, params, { idempotencyKey });

    // 1. Verify URL endpoint format
    assert.equal(capturedUrl, "http://localhost:9999/v1/payments/pay_mock_999/refund");

    // 2. Verify HTTP method
    assert.equal(capturedOptions.method, "POST");

    // 3. Verify official X-Refund-Idempotency header
    assert.equal(capturedOptions.headers["X-Refund-Idempotency"], idempotencyKey);

    // 4. Verify Content-Type and HTTP Basic Auth
    assert.equal(capturedOptions.headers["Content-Type"], "application/json");
    const expectedAuth = `Basic ${Buffer.from("rzp_test_mock_key:mock_secret_123").toString("base64")}`;
    assert.equal(capturedOptions.headers["Authorization"], expectedAuth);

    // 5. Verify Request Body JSON payload
    const parsedBody = JSON.parse(capturedOptions.body);
    assert.equal(parsedBody.amount, 1180000);
    assert.deepEqual(parsedBody.notes, { bookingId: "b-101", reason: "Change of plans" });
    assert.equal(parsedBody.receipt, idempotencyKey);

    // 6. Verify Response returned
    assert.equal(response.id, "rfnd_mock_12345");
    assert.equal(response.status, "processed");
  });

  it("fetchRefund sends GET /v1/payments/{paymentId}/refunds/{refundId} with Basic Auth", async () => {
    let capturedUrl = null;
    let capturedOptions = null;

    const mockFetch = async (url, options) => {
      capturedUrl = url;
      capturedOptions = options;
      return {
        ok: true,
        status: 200,
        json: async () => ({
          id: "rfnd_mock_12345",
          status: "processed"
        })
      };
    };

    const gateway = new RazorpayRefundGateway({
      keyId: "rzp_test_mock_key",
      keySecret: "mock_secret_123",
      baseUrl: "http://localhost:9999/v1",
      fetchFn: mockFetch
    });

    const response = await gateway.fetchRefund("pay_mock_999", "rfnd_mock_12345");

    assert.equal(capturedUrl, "http://localhost:9999/v1/payments/pay_mock_999/refunds/rfnd_mock_12345");
    assert.equal(capturedOptions.method, "GET");
    assert.equal(response.status, "processed");
  });
});
