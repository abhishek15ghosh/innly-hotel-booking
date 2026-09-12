import { pool } from "../config/db.js";

export const TERMINAL_WEBHOOK_STATUSES = new Set([
  "processed",
  "failed",
  "reconciliation_required",
  "failed_logged",
  "ignored"
]);

export async function recordWebhookEvent(client, { eventId, eventType, status = "received", failureReason = null }) {
  const db = client || pool;
  const result = await db.query(
    `INSERT INTO webhook_events (event_id, event_type, status, failure_reason, processed_at)
     VALUES ($1, $2, $3, $4, NULL)
     ON CONFLICT (event_id) DO NOTHING
     RETURNING id, event_id AS "eventId", event_type AS "eventType", status, failure_reason AS "failureReason", created_at AS "createdAt", processed_at AS "processedAt"`,
    [eventId, eventType, status, failureReason]
  );
  return result.rows[0] || null;
}

export async function updateWebhookEventStatus(client, eventId, status, failureReason = null) {
  if (!TERMINAL_WEBHOOK_STATUSES.has(status)) {
    throw new Error(`Invalid terminal webhook status: ${status}`);
  }
  const db = client || pool;
  await db.query(
    `UPDATE webhook_events
     SET status = $2, failure_reason = $3, processed_at = NOW()
     WHERE event_id = $1`,
    [eventId, status, failureReason]
  );
}

export async function getWebhookEvent(client, eventId) {
  const db = client || pool;
  const result = await db.query(
    `SELECT id, event_id AS "eventId", event_type AS "eventType", status, failure_reason AS "failureReason", created_at AS "createdAt", processed_at AS "processedAt"
     FROM webhook_events
     WHERE event_id = $1`,
    [eventId]
  );
  return result.rows[0] || null;
}
