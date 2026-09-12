-- Rollback: 005_reviews_guest_trust_down.sql
BEGIN;

DROP INDEX IF EXISTS idx_reviews_user_hotel;
DROP INDEX IF EXISTS idx_reviews_hotel_approved_pagination;
DROP INDEX IF EXISTS idx_reviews_booking_id_unique;
ALTER TABLE reviews ALTER COLUMN status SET DEFAULT 'approved'::review_status;

COMMIT;
