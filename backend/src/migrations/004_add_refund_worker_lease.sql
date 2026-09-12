-- Migration 004: Add durable worker lease and retry tracking columns to refunds table

ALTER TABLE refunds
  ADD COLUMN IF NOT EXISTS attempt_count INT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ DEFAULT NOW(),
  ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS leased_by VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_refunds_lease_claim ON refunds(status, lease_until, next_attempt_at);

/*
ROLLBACK PROCEDURE FOR MIGRATION 004:
DROP INDEX IF EXISTS idx_refunds_lease_claim;
ALTER TABLE refunds
  DROP COLUMN IF EXISTS attempt_count,
  DROP COLUMN IF EXISTS last_attempt_at,
  DROP COLUMN IF EXISTS next_attempt_at,
  DROP COLUMN IF EXISTS lease_until,
  DROP COLUMN IF EXISTS leased_by;
*/
