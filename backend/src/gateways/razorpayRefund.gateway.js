import { env } from "../config/env.js";

export class RazorpayRefundGateway {
  constructor(deps = {}) {
    this.keyId = deps.keyId || env.RAZORPAY_KEY_ID;
    this.keySecret = deps.keySecret || env.RAZORPAY_KEY_SECRET;
    this.baseUrl = deps.baseUrl || "https://api.razorpay.com/v1";
    this.fetchFn = deps.fetchFn || globalThis.fetch;
    this.timeoutMs = deps.timeoutMs || 8000; // 8 seconds timeout (shorter than worker 2-min lease)
  }

  async createRefund(paymentId, params, options = {}) {
    if (!paymentId) throw new Error("paymentId is required for refund");
    const idempotencyKey = options.idempotencyKey || params.receipt;
    if (!idempotencyKey) throw new Error("idempotencyKey is required for refund");

    const url = `${this.baseUrl}/payments/${paymentId}/refund`;
    const authHeader = `Basic ${Buffer.from(`${this.keyId}:${this.keySecret}`).toString("base64")}`;

    const bodyData = {
      amount: params.amount,
      notes: params.notes || {},
      receipt: params.receipt || idempotencyKey
    };

    const controller = typeof AbortController !== "undefined" ? new AbortController() : null;
    const timeoutId = controller ? setTimeout(() => controller.abort(), this.timeoutMs) : null;

    let response;
    try {
      response = await this.fetchFn(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": authHeader,
          "X-Refund-Idempotency": idempotencyKey
        },
        body: JSON.stringify(bodyData),
        signal: controller ? controller.signal : undefined
      });
    } catch (err) {
      if (err.name === "AbortError") {
        const timeoutErr = new Error("Razorpay HTTP request timed out");
        timeoutErr.statusCode = 504;
        throw timeoutErr;
      }
      throw err;
    } finally {
      if (timeoutId) clearTimeout(timeoutId);
    }

    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
      const err = new Error(data?.error?.description || data?.error?.code || `Razorpay refund request failed with status ${response.status}`);
      err.statusCode = response.status;
      err.error = data?.error;
      throw err;
    }

    return data;
  }

  async fetchRefund(paymentId, refundId) {
    if (!refundId) throw new Error("refundId is required to fetch refund");
    const url = paymentId
      ? `${this.baseUrl}/payments/${paymentId}/refunds/${refundId}`
      : `${this.baseUrl}/refunds/${refundId}`;
    const authHeader = `Basic ${Buffer.from(`${this.keyId}:${this.keySecret}`).toString("base64")}`;

    const controller = typeof AbortController !== "undefined" ? new AbortController() : null;
    const timeoutId = controller ? setTimeout(() => controller.abort(), this.timeoutMs) : null;

    let response;
    try {
      response = await this.fetchFn(url, {
        method: "GET",
        headers: {
          "Authorization": authHeader,
          "Content-Type": "application/json"
        },
        signal: controller ? controller.signal : undefined
      });
    } catch (err) {
      if (err.name === "AbortError") {
        const timeoutErr = new Error("Razorpay HTTP request timed out");
        timeoutErr.statusCode = 504;
        throw timeoutErr;
      }
      throw err;
    } finally {
      if (timeoutId) clearTimeout(timeoutId);
    }

    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
      const err = new Error(data?.error?.description || `Razorpay fetch refund failed with status ${response.status}`);
      err.statusCode = response.status;
      err.error = data?.error;
      throw err;
    }

    return data;
  }
}

export const razorpayRefundGateway = new RazorpayRefundGateway();
