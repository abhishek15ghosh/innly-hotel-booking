-- Migration 002: Harden webhook_events schema with status CHECK constraint and cleanup redundant indexes

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'webhook_events_status_check'
    ) THEN
        ALTER TABLE webhook_events
        ADD CONSTRAINT webhook_events_status_check
        CHECK (status IN (
            'received',
            'processing',
            'processed',
            'failed',
            'reconciliation_required',
            'failed_logged',
            'ignored'
        ));
    END IF;
END $$;

-- Drop redundant btree index as unique constraint webhook_events_event_id_key already provides B-tree index on event_id
DROP INDEX IF EXISTS idx_webhook_events_event_id;
