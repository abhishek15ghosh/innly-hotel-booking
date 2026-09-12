import assert from "node:assert/strict";
import crypto from "node:crypto";
import http from "node:http";
import test, { describe } from "node:test";
import { createApp } from "../src/app.js";

async function makeRequest(server, options, body = null) {
  const port = server.address().port;
  return new Promise((resolve, reject) => {
    const req = http.request(
      {
        hostname: "127.0.0.1",
        port,
        method: options.method || "GET",
        path: options.path,
        headers: options.headers || {}
      },
      (res) => {
        let data = "";
        res.on("data", (chunk) => (data += chunk));
        res.on("end", () => {
          resolve({
            statusCode: res.statusCode,
            headers: res.headers,
            body: data
          });
        });
      }
    );
    req.on("error", reject);
    if (body) {
      req.write(body);
    }
    req.end();
  });
}

describe("Security, Probes, CORS & Rate Limiting Unit Tests", () => {
  test("1. Liveness probe returns HTTP 200 with status: live", async () => {
    const app = createApp();
    const server = http.createServer(app);
    await new Promise((resolve) => server.listen(0, resolve));

    try {
      const res = await makeRequest(server, { path: "/api/v1/health/liveness" });
      assert.equal(res.statusCode, 200);
      const json = JSON.parse(res.body);
      assert.equal(json.status, "live");
    } finally {
      server.close();
    }
  });

  test("2. Readiness probe returns HTTP 200 with status: ready when database is healthy", async () => {
    const mockDbPool = {
      query: async () => ({ rowCount: 1, rows: [{ "?column?": 1 }] })
    };
    const app = createApp({ dbPool: mockDbPool });
    const server = http.createServer(app);
    await new Promise((resolve) => server.listen(0, resolve));

    try {
      const res = await makeRequest(server, { path: "/api/v1/health/readiness" });
      assert.equal(res.statusCode, 200);
      const json = JSON.parse(res.body);
      assert.equal(json.status, "ready");
      assert.equal(json.database_url, undefined);
      assert.equal(json.password, undefined);
      assert.equal(json.user, undefined);
    } finally {
      server.close();
    }
  });

  test("3. Readiness probe returns HTTP 503 with status: not_ready when database fails (sanitized response)", async () => {
    const mockFailingPool = {
      query: async () => {
        throw new Error("FATAL: connection to server on socket '/tmp/.s.PGSQL.5432' failed: password authentication failed for user 'secret_user'");
      }
    };
    const app = createApp({ dbPool: mockFailingPool });
    const server = http.createServer(app);
    await new Promise((resolve) => server.listen(0, resolve));

    try {
      const res = await makeRequest(server, { path: "/api/v1/health/readiness" });
      assert.equal(res.statusCode, 503);
      const json = JSON.parse(res.body);
      assert.equal(json.status, "not_ready");
      // Verify no sensitive credentials or internal error stack leaked
      assert.equal(json.error, undefined);
      assert.equal(json.stack, undefined);
      assert.equal(json.secret_user, undefined);
    } finally {
      server.close();
    }
  });

  test("4. CORS: Allowed browser origin receives HTTP 200 and Access-Control-Allow-Origin header", async () => {
    const app = createApp({
      corsAllowedOrigins: "https://admin.innly.com,https://dashboard.innly.com",
      nodeEnv: "production"
    });
    const server = http.createServer(app);
    await new Promise((resolve) => server.listen(0, resolve));

    try {
      const res = await makeRequest(server, {
        path: "/api/v1/health",
        headers: { Origin: "https://admin.innly.com" }
      });
      assert.equal(res.statusCode, 200);
      assert.equal(res.headers["access-control-allow-origin"], "https://admin.innly.com");
    } finally {
      server.close();
    }
  });

  test("5. CORS: Disallowed browser origin receives HTTP 403", async () => {
    const app = createApp({
      corsAllowedOrigins: "https://admin.innly.com",
      nodeEnv: "production"
    });
    const server = http.createServer(app);
    await new Promise((resolve) => server.listen(0, resolve));

    try {
      const res = await makeRequest(server, {
        path: "/api/v1/health",
        headers: { Origin: "https://malicious-site.com" }
      });
      assert.equal(res.statusCode, 403);
    } finally {
      server.close();
    }
  });

  test("6. CORS: Request with NO Origin header (Android apps, Razorpay webhooks) succeeds", async () => {
    const app = createApp({
      corsAllowedOrigins: "https://admin.innly.com",
      nodeEnv: "production"
    });
    const server = http.createServer(app);
    await new Promise((resolve) => server.listen(0, resolve));

    try {
      const res = await makeRequest(server, {
        path: "/api/v1/health",
        headers: {} // No Origin header
      });
      assert.equal(res.statusCode, 200);
    } finally {
      server.close();
    }
  });

  test("6a. CORS (Native Android Staging): Empty CORS_ALLOWED_ORIGINS in production blocks browser Origin header with HTTP 403", async () => {
    const app = createApp({
      corsAllowedOrigins: "",
      nodeEnv: "production"
    });
    const server = http.createServer(app);
    await new Promise((resolve) => server.listen(0, resolve));

    try {
      const res = await makeRequest(server, {
        path: "/api/v1/health",
        headers: { Origin: "https://some-browser-client.com" }
      });
      assert.equal(res.statusCode, 403);
    } finally {
      server.close();
    }
  });

  test("6b. CORS (Native Android Staging): Empty CORS_ALLOWED_ORIGINS in production allows native requests without Origin header", async () => {
    const app = createApp({
      corsAllowedOrigins: "",
      nodeEnv: "production"
    });
    const server = http.createServer(app);
    await new Promise((resolve) => server.listen(0, resolve));

    try {
      const res = await makeRequest(server, {
        path: "/api/v1/health",
        headers: {} // No Origin header
      });
      assert.equal(res.statusCode, 200);
    } finally {
      server.close();
    }
  });

  test("7. Rate Limiting: Public explore routes enforce threshold and return HTTP 429 when exceeded", async () => {
    const app = createApp({
      publicExploreRateLimit: 3, // Small test limit
      publicExploreWindowMs: 60 * 1000
    });
    const server = http.createServer(app);
    await new Promise((resolve) => server.listen(0, resolve));

    try {
      // 3 allowed requests
      const r1 = await makeRequest(server, { path: "/api/v1/hotels" });
      const r2 = await makeRequest(server, { path: "/api/v1/hotels" });
      const r3 = await makeRequest(server, { path: "/api/v1/hotels" });

      assert.notEqual(r1.statusCode, 429);
      assert.notEqual(r2.statusCode, 429);
      assert.notEqual(r3.statusCode, 429);

      // 4th request exceeds limit -> HTTP 429
      const r4 = await makeRequest(server, { path: "/api/v1/hotels" });
      assert.equal(r4.statusCode, 429);
    } finally {
      server.close();
    }
  });

  test("8. Rate Limiting: Webhook route uses independent rate limiter and preserves raw HMAC signature", async () => {
    const testSecret = "test_webhook_secret_key_8";
    const app = createApp({
      webhookRateLimit: 2,
      webhookWindowMs: 60 * 1000,
      webhookDeps: {
        webhookEnabled: true,
        webhookSecret: testSecret
      }
    });
    const server = http.createServer(app);
    await new Promise((resolve) => server.listen(0, resolve));

    try {
      const rawPayload = JSON.stringify({
        event: "payment.captured",
        payload: { payment: { entity: { id: "pay_test123", order_id: "order_123", amount: 50000, currency: "INR", status: "captured" } } }
      });

      // 1. Invalid signature returns 400
      const invalidRes = await makeRequest(
        server,
        {
          method: "POST",
          path: "/api/v1/payments/webhook",
          headers: {
            "Content-Type": "application/json",
            "x-razorpay-event-id": "evt_1",
            "x-razorpay-signature": "bad_signature_hex"
          }
        },
        rawPayload
      );
      assert.equal(invalidRes.statusCode, 400);

      // 2. Second request to webhook route (within threshold 2)
      const r2 = await makeRequest(
        server,
        {
          method: "POST",
          path: "/api/v1/payments/webhook",
          headers: {
            "Content-Type": "application/json",
            "x-razorpay-event-id": "evt_2",
            "x-razorpay-signature": "bad_signature_hex"
          }
        },
        rawPayload
      );
      assert.equal(r2.statusCode, 400);

      // 3. Third request exceeds webhook threshold 2 -> HTTP 429
      const r3 = await makeRequest(
        server,
        {
          method: "POST",
          path: "/api/v1/payments/webhook",
          headers: {
            "Content-Type": "application/json",
            "x-razorpay-event-id": "evt_3",
            "x-razorpay-signature": "bad_signature_hex"
          }
        },
        rawPayload
      );
      assert.equal(r3.statusCode, 429);
    } finally {
      server.close();
    }
  });

  test("9. Webhook raw-body integrity: Modified payload with valid signature for previous bytes is rejected", async () => {
    const secret = "test_webhook_secret_key_9";
    const app = createApp({
      webhookDeps: {
        webhookEnabled: true,
        webhookSecret: secret
      }
    });
    const server = http.createServer(app);
    await new Promise((resolve) => server.listen(0, resolve));

    try {
      const originalPayload = JSON.stringify({ event: "payment.captured", amount: 1000 });
      const tamperedPayload = JSON.stringify({ event: "payment.captured", amount: 9999 });

      const originalSignature = crypto
        .createHmac("sha256", secret)
        .update(Buffer.from(originalPayload, "utf8"))
        .digest("hex");

      // Send tampered payload with original signature
      const res = await makeRequest(
        server,
        {
          method: "POST",
          path: "/api/v1/payments/webhook",
          headers: {
            "Content-Type": "application/json",
            "x-razorpay-event-id": "evt_tampered_1",
            "x-razorpay-signature": originalSignature
          }
        },
        tamperedPayload
      );

      assert.equal(res.statusCode, 400);
      const json = JSON.parse(res.body);
      assert.match(json.message, /Invalid webhook signature/);
    } finally {
      server.close();
    }
  });

  test("10. Webhook raw-body valid HMAC signature reaches handler without stream corruption", async () => {
    const secret = "test_webhook_secret_key_10";
    let handlerReached = false;

    const mockWebhookRepo = {
      recordWebhookEvent: async () => ({ id: "wh_1", event_id: "evt_valid_10", status: "processing" }),
      getWebhookEvent: async () => null,
      updateWebhookEventStatus: async () => {}
    };

    const mockWithTx = async (callback) => {
      handlerReached = true;
      return callback({ query: async () => ({ rows: [] }) });
    };

    const app = createApp({
      webhookDeps: {
        webhookEnabled: true,
        webhookSecret: secret,
        webhookRepo: mockWebhookRepo,
        withTx: mockWithTx
      }
    });
    const server = http.createServer(app);
    await new Promise((resolve) => server.listen(0, resolve));

    try {
      const rawPayload = JSON.stringify({
        event: "unknown.unhandled.event",
        payload: {}
      });

      const validSignature = crypto
        .createHmac("sha256", secret)
        .update(Buffer.from(rawPayload, "utf8"))
        .digest("hex");

      const res = await makeRequest(
        server,
        {
          method: "POST",
          path: "/api/v1/payments/webhook",
          headers: {
            "Content-Type": "application/json",
            "x-razorpay-event-id": "evt_valid_10",
            "x-razorpay-signature": validSignature
          }
        },
        rawPayload
      );

      assert.equal(res.statusCode, 200);
      assert.equal(handlerReached, true);
    } finally {
      server.close();
    }
  });
});
