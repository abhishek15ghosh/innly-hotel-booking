-- Migration 003: Add cancellation policies and refunds tracking table

-- 1. Add free_cancellation_hours to hotels table
ALTER TABLE hotels ADD COLUMN IF NOT EXISTS free_cancellation_hours INT NOT NULL DEFAULT 24;

UPDATE hotels SET free_cancellation_hours = 48 WHERE cancellation_policy ILIKE '%48%' AND free_cancellation_hours = 24;
UPDATE hotels SET free_cancellation_hours = 72 WHERE cancellation_policy ILIKE '%72%' AND free_cancellation_hours = 24;

-- 2. Add new values to booking_status and payment_status ENUMs
ALTER TYPE booking_status ADD VALUE IF NOT EXISTS 'cancellation_pending';
ALTER TYPE payment_status ADD VALUE IF NOT EXISTS 'refund_pending';

-- 3. Create refund_status ENUM and refunds table
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'refund_status') THEN
    CREATE TYPE refund_status AS ENUM ('pending', 'processed', 'failed');
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS refunds (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id UUID NOT NULL UNIQUE REFERENCES bookings(id) ON DELETE CASCADE,
  payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id),
  razorpay_refund_id TEXT UNIQUE,
  razorpay_payment_id TEXT NOT NULL,
  idempotency_key TEXT NOT NULL UNIQUE,
  amount INT NOT NULL CHECK (amount > 0),
  currency CHAR(3) NOT NULL DEFAULT 'INR',
  status refund_status NOT NULL DEFAULT 'pending',
  reason TEXT NOT NULL,
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refunds_booking ON refunds(booking_id);
CREATE INDEX IF NOT EXISTS idx_refunds_idempotency ON refunds(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_refunds_status ON refunds(status);
