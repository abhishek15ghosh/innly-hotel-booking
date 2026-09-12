import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  recordWebhookEvent,
  updateWebhookEventStatus,
  getWebhookEvent,
  TERMINAL_WEBHOOK_STATUSES
} from "../src/repositories/webhook.repository.js";

describe("Webhook Repository Unit Tests (Fake DB Client)", () => {
  it("1. Initial insert explicitly leaves processed_at NULL in query string", async () => {
    let executedQuery = null;
    let queryParams = null;

    const fakeClient = {
      query: async (sql, params) => {
        executedQuery = sql;
        queryParams = params;
        return {
          rows: [
            {
              id: "wh-uuid-1",
              eventId: params[0],
              eventType: params[1],
              status: params[2],
              failureReason: params[3],
              createdAt: "2026-08-05T15:00:00Z",
              processedAt: null
            }
          ]
        };
      }
    };

    const result = await recordWebhookEvent(fakeClient, {
      eventId: "evt_test_101",
      eventType: "payment.captured",
      status: "processing"
    });

    assert.ok(executedQuery);
    assert.match(executedQuery, /VALUES \(\$1, \$2, \$3, \$4, NULL\)/i);
    assert.equal(result.processedAt, null);
  });

  it("2. Every allowed terminal status can update and set processed_at", async () => {
    const allowedStatuses = ["processed", "failed", "reconciliation_required", "failed_logged", "ignored"];

    for (const status of allowedStatuses) {
      let executedQuery = null;
      let queryParams = null;

      const fakeClient = {
        query: async (sql, params) => {
          executedQuery = sql;
          queryParams = params;
          return { rowCount: 1 };
        }
      };

      await updateWebhookEventStatus(fakeClient, "evt_test_202", status, "Optional reason");

      assert.ok(executedQuery, `Query should execute for terminal status: ${status}`);
      assert.match(executedQuery, /processed_at = NOW\(\)/i);
      assert.equal(queryParams[0], "evt_test_202");
      assert.equal(queryParams[1], status);
      assert.equal(queryParams[2], "Optional reason");
    }
  });

  it("3. non-terminal statuses (received, processing, unknown) are rejected", async () => {
    const invalidStatuses = ["received", "processing", "unknown_status", "invalid_123", ""];

    for (const status of invalidStatuses) {
      const fakeClient = {
        query: async () => {
          throw new Error("Query should NOT be called for invalid status");
        }
      };

      await assert.rejects(
        async () => {
          await updateWebhookEventStatus(fakeClient, "evt_test_303", status);
        },
        (err) => {
          assert.equal(err.message, `Invalid terminal webhook status: ${status}`);
          return true;
        }
      );
    }
  });

  it("4. Rejected statuses execute no update query", async () => {
    let queryCallCount = 0;

    const fakeClient = {
      query: async () => {
        queryCallCount++;
        return { rowCount: 0 };
      }
    };

    await assert.rejects(
      async () => {
        await updateWebhookEventStatus(fakeClient, "evt_test_404", "processing");
      }
    );

    assert.equal(queryCallCount, 0, "Fake DB query function must be called 0 times for rejected non-terminal status");
  });

  it("5. TERMINAL_WEBHOOK_STATUSES set contains exactly the 5 documented terminal statuses", () => {
    assert.equal(TERMINAL_WEBHOOK_STATUSES.size, 5);
    assert.ok(TERMINAL_WEBHOOK_STATUSES.has("processed"));
    assert.ok(TERMINAL_WEBHOOK_STATUSES.has("failed"));
    assert.ok(TERMINAL_WEBHOOK_STATUSES.has("reconciliation_required"));
    assert.ok(TERMINAL_WEBHOOK_STATUSES.has("failed_logged"));
    assert.ok(TERMINAL_WEBHOOK_STATUSES.has("ignored"));
  });
});
