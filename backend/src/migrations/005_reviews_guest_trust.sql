-- Migration: 005_reviews_guest_trust.sql
BEGIN;

-- 1. Preflight duplicate check: Abort transactionally if duplicate non-null booking_ids exist
DO $$
BEGIN
  IF EXISTS (
    SELECT booking_id
    FROM reviews
    WHERE booking_id IS NOT NULL
    GROUP BY booking_id
    HAVING COUNT(*) > 1
  ) THEN
    RAISE EXCEPTION 'Migration 005 aborted: Duplicate non-null booking_id values exist in reviews table.';
  END IF;
END $$;

-- 2. Alter default status to 'pending'
ALTER TABLE reviews ALTER COLUMN status SET DEFAULT 'pending'::review_status;

-- 3. Create partial unique index on booking_id
CREATE UNIQUE INDEX IF NOT EXISTS idx_reviews_booking_id_unique
ON reviews (booking_id)
WHERE booking_id IS NOT NULL;

-- 4. Create composite index for deterministic approved review pagination
CREATE INDEX IF NOT EXISTS idx_reviews_hotel_approved_pagination
ON reviews (hotel_id, created_at DESC, id DESC)
WHERE status = 'approved';

-- 5. Create index for author lookups
CREATE INDEX IF NOT EXISTS idx_reviews_user_hotel
ON reviews (user_id, hotel_id, created_at DESC);

COMMIT;
