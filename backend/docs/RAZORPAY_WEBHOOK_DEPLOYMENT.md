# Razorpay Webhook Deployment & Staging Verification Runbook

## Overview
Innly includes an enterprise-grade, transactional Razorpay webhook handler designed to receive real-time payment and refund lifecycle updates with signature verification, raw-body HMAC SHA-256 validation, and database audit deduplication.

---

## 1. Webhook Endpoint Specification

- **Public Path**: `/api/v1/payments/webhook`
- **Full URL**: `https://<your-production-or-staging-domain>/api/v1/payments/webhook`
- **Method**: `POST`
- **Payload Format**: `application/json` (Processed by raw byte stream parser before standard body parsers)
- **Required Ingress Headers**:
  - `x-razorpay-signature`: HMAC SHA-256 hash generated with `RAZORPAY_WEBHOOK_SECRET`.
  - `x-razorpay-event-id`: Unique Razorpay event identifier used for deduplication.

---

## 2. Supported Event Subscriptions

In the Razorpay Dashboard (under **Settings → Webhooks → Add New Webhook**), subscribe to the following events:

| Event Name | Purpose | Action in Innly Backend |
| :--- | :--- | :--- |
| `payment.captured` | Payment captured at gateway | Transitions `bookings.status` to `confirmed` and `payments.status` to `captured`. Releases nothing. |
| `payment.failed` | Payment failed at gateway | Marks `payments.status` as `failed` with sanitized error code and releases reserved room inventory. |
| `refund.processed` | Gateway processed refund | Marks `refunds.status` as `processed` and `bookings.status` as `cancelled`. Releases inventory once. |
| `refund.failed` | Gateway rejected refund | Marks `refunds.status` as `failed` and reverts `bookings.status` to `confirmed`. |

---

## 3. Environment Configuration

Inject the webhook secret into the backend deployment environment:

```bash
# Generated in Razorpay Webhooks Dashboard
RAZORPAY_WEBHOOK_SECRET="your_strong_random_secret_here"
```

> [!CAUTION]
> - Never commit `RAZORPAY_WEBHOOK_SECRET` to source control or `.env.example`.
> - The webhook endpoint fails closed (returns HTTP 400/500) if the secret is missing or the signature does not match.

---

## 4. Staging & Local Tunnel Testing

To test webhooks in a local or staging environment behind a NAT/firewall:

1. **Start Local Backend**:
   ```bash
   npm start
   ```

2. **Open Secure Ingress Tunnel (e.g. ngrok)**:
   ```bash
   ngrok http 8080
   ```

3. **Configure Webhook in Razorpay Test Dashboard**:
   - Webhook URL: `https://<ngrok-id>.ngrok-free.app/api/v1/payments/webhook`
   - Secret: Matches `RAZORPAY_WEBHOOK_SECRET` in `.env`.
   - Alert Email: Your development team lead email.
   - Active Events: Check `payment.captured`, `payment.failed`, `refund.processed`, `refund.failed`.

---

## 5. Audit & Deduplication Verification

Every received webhook is audited in the `webhook_events` PostgreSQL table.

### Inspect Received Webhook Events
```sql
SELECT id, event_id, event_type, status, failure_reason, created_at, processed_at
FROM webhook_events
ORDER BY created_at DESC
LIMIT 10;
```

### Invariant Checks
1. **Idempotency**: Repeated delivery of the same `event_id` is deduplicated via the `idx_webhook_events_event_id` unique constraint and returns HTTP 200 without duplicate mutations.
2. **Raw Body Integrity**: The signature is verified directly against the raw byte buffer before JSON parsing to eliminate encoding discrepancies.
3. **Transaction Safety**: Permanent validation failures commit an audit record (`status = 'failed'`), while transient database disconnections roll back the transaction so Razorpay can retry delivery.
