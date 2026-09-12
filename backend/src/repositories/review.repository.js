import { pool } from "../config/db.js";

export async function createReview(client, { userId, hotelId, bookingId, rating, title, comment }) {
  const result = await client.query(
    `INSERT INTO reviews (user_id, hotel_id, booking_id, rating, title, comment, status)
     VALUES ($1, $2, $3, $4, $5, $6, 'pending')
     RETURNING id, hotel_id AS "hotelId", booking_id AS "bookingId", rating, title, comment, status, created_at AS "createdAt", updated_at AS "updatedAt", moderated_at AS "moderatedAt"`,
    [userId, hotelId, bookingId, rating, title, comment]
  );
  return result.rows[0];
}

export async function getApprovedReviewsByHotelId(client = pool, hotelId, limit = 10, offset = 0) {
  const result = await client.query(
    `SELECT r.id,
            r.hotel_id AS "hotelId",
            r.rating,
            r.title,
            r.comment,
            r.created_at AS "createdAt",
            u.display_name AS "rawAuthorName",
            (
              r.booking_id IS NOT NULL
              AND b.user_id = r.user_id
              AND b.hotel_id = r.hotel_id
              AND p.status = 'captured'
              AND b.status IN ('confirmed', 'completed')
              AND b.check_out <= CURRENT_DATE
            ) AS "isVerifiedStay"
     FROM reviews r
     JOIN users u ON u.id = r.user_id
     LEFT JOIN bookings b ON b.id = r.booking_id
     LEFT JOIN payments p ON p.booking_id = b.id
     WHERE r.hotel_id = $1 AND r.status = 'approved'
     ORDER BY r.created_at DESC, r.id DESC
     LIMIT $2 OFFSET $3`,
    [hotelId, limit, offset]
  );
  return result.rows;
}

export async function getHotelRatingDistribution(client = pool, hotelId) {
  const result = await client.query(
    `SELECT
       COALESCE(ROUND(AVG(rating)::numeric, 2), 0)::float AS "avgRating",
       COUNT(*)::int AS "reviewCount",
       COUNT(*) FILTER (WHERE rating = 5)::int AS "star5",
       COUNT(*) FILTER (WHERE rating = 4)::int AS "star4",
       COUNT(*) FILTER (WHERE rating = 3)::int AS "star3",
       COUNT(*) FILTER (WHERE rating = 2)::int AS "star2",
       COUNT(*) FILTER (WHERE rating = 1)::int AS "star1"
     FROM reviews
     WHERE hotel_id = $1 AND status = 'approved'`,
    [hotelId]
  );
  const row = result.rows[0] || {};
  return {
    avgRating: Number(row.avgRating || 0),
    reviewCount: Number(row.reviewCount || 0),
    ratingDistribution: {
      "5": Number(row.star5 || 0),
      "4": Number(row.star4 || 0),
      "3": Number(row.star3 || 0),
      "2": Number(row.star2 || 0),
      "1": Number(row.star1 || 0)
    }
  };
}

export async function findEligibleBookingForUser(client = pool, userId, hotelId) {
  const result = await client.query(
    `SELECT b.id AS "bookingId",
            b.check_in::text AS "checkIn",
            b.check_out::text AS "checkOut",
            COALESCE(r.name, 'Room') AS "roomName"
     FROM bookings b
     JOIN payments p ON p.booking_id = b.id
     LEFT JOIN booking_items bi ON bi.booking_id = b.id
     LEFT JOIN rooms r ON r.id = bi.room_id
     WHERE b.user_id = $1
       AND b.hotel_id = $2
       AND p.status = 'captured'
       AND b.status IN ('confirmed', 'completed')
       AND b.check_out <= CURRENT_DATE
       AND NOT EXISTS (
         SELECT 1 FROM reviews rev WHERE rev.booking_id = b.id
       )
     ORDER BY b.check_out DESC, b.created_at DESC
     LIMIT 1`,
    [userId, hotelId]
  );
  if (!result.rows[0]) return null;
  const row = result.rows[0];
  return {
    bookingId: row.bookingId,
    checkIn: String(row.checkIn).slice(0, 10),
    checkOut: String(row.checkOut).slice(0, 10),
    roomName: row.roomName
  };
}

export async function getUserReviewsForHotel(client = pool, userId, hotelId) {
  const result = await client.query(
    `SELECT id,
            booking_id AS "bookingId",
            rating,
            title,
            comment,
            status,
            created_at AS "createdAt",
            updated_at AS "updatedAt",
            moderated_at AS "moderatedAt"
     FROM reviews
     WHERE user_id = $1 AND hotel_id = $2
     ORDER BY created_at DESC, id DESC`,
    [userId, hotelId]
  );
  return result.rows.map((row) => ({
    ...row,
    rating: Number(row.rating)
  }));
}

export async function getBookingForReviewValidation(client, bookingId, userId, hotelId) {
  const result = await client.query(
    `SELECT b.id AS "bookingId",
            b.user_id AS "userId",
            b.hotel_id AS "hotelId",
            b.status AS "bookingStatus",
            b.check_out::text AS "checkOut",
            p.status AS "paymentStatus"
     FROM bookings b
     LEFT JOIN payments p ON p.booking_id = b.id
     WHERE b.id = $1 AND b.user_id = $2 AND b.hotel_id = $3
     FOR UPDATE OF b`,
    [bookingId, userId, hotelId]
  );
  if (!result.rows[0]) return null;
  const row = result.rows[0];
  return {
    bookingId: row.bookingId,
    userId: row.userId,
    hotelId: row.hotelId,
    bookingStatus: row.bookingStatus,
    checkOut: String(row.checkOut).slice(0, 10),
    paymentStatus: row.paymentStatus
  };
}

export async function resubmitRejectedReview(client, { reviewId, userId, hotelId, bookingId, rating, title, comment }) {
  const result = await client.query(
    `UPDATE reviews
     SET rating = $1,
         title = $2,
         comment = $3,
         status = 'pending'::review_status,
         moderated_at = NULL,
         updated_at = NOW()
     WHERE id = $4
       AND user_id = $5
       AND hotel_id = $6
       AND booking_id = $7
       AND status = 'rejected'
     RETURNING id, hotel_id AS "hotelId", booking_id AS "bookingId", rating, title, comment, status, created_at AS "createdAt", updated_at AS "updatedAt", moderated_at AS "moderatedAt"`,
    [rating, title, comment, reviewId, userId, hotelId, bookingId]
  );
  return result.rows[0] || null;
}

export async function moderateReview(client = pool, reviewId, status) {
  const result = await client.query(
    `UPDATE reviews
     SET status = $2, moderated_at = NOW()
     WHERE id = $1
     RETURNING id, hotel_id AS "hotelId", status`,
    [reviewId, status]
  );
  return result.rows[0];
}

export async function refreshHotelRating(client = pool, hotelId) {
  await client.query(
    `UPDATE hotels
     SET avg_rating = COALESCE((
           SELECT ROUND(AVG(rating)::numeric, 2)
           FROM reviews
           WHERE hotel_id = $1 AND status = 'approved'
         ), 0),
         review_count = COALESCE((
           SELECT COUNT(*)::int
           FROM reviews
           WHERE hotel_id = $1 AND status = 'approved'
         ), 0),
         updated_at = NOW()
     WHERE id = $1`,
    [hotelId]
  );
}
