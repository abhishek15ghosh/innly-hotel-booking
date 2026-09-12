import { pool } from "../config/db.js";

export async function listFavorites(userId) {
  const result = await pool.query(
    `SELECT h.id, h.name, h.description, h.city, h.address,
            h.avg_rating AS rating, h.review_count AS "reviewCount",
            COALESCE(
              (
                SELECT MIN(base_price)
                FROM rooms
                WHERE hotel_id = h.id AND is_active = TRUE
              ),
              0
            ) AS "startingPrice",
            COALESCE(
              (
                SELECT ri.image_url
                FROM rooms listed_room
                JOIN room_images ri ON ri.room_id = listed_room.id
                WHERE listed_room.hotel_id = h.id
                  AND listed_room.is_active = TRUE
                ORDER BY listed_room.base_price ASC, ri.position ASC
                LIMIT 1
              ),
              (
                SELECT hi.image_url
                FROM hotel_images hi
                WHERE hi.hotel_id = h.id
                ORDER BY hi.position ASC
                LIMIT 1
              ),
              ''
            ) AS "thumbnailUrl",
            COALESCE(
              (
                SELECT json_agg(ha.amenity ORDER BY ha.amenity)
                FROM hotel_amenities ha
                WHERE ha.hotel_id = h.id
              ),
              '[]'::json
            ) AS amenities,
            TRUE AS "isFavorite"
     FROM favorites f
     JOIN hotels h ON h.id = f.hotel_id
     WHERE f.user_id = $1
     ORDER BY f.created_at DESC`,
    [userId]
  );
  return result.rows.map((row) => ({
    ...row,
    startingPrice: Number(row.startingPrice),
    rating: Number(row.rating),
    reviewCount: Number(row.reviewCount)
  }));
}

export async function addFavorite(userId, hotelId) {
  await pool.query(
    `INSERT INTO favorites (user_id, hotel_id)
     VALUES ($1, $2)
     ON CONFLICT (user_id, hotel_id) DO NOTHING`,
    [userId, hotelId]
  );
}

export async function removeFavorite(userId, hotelId) {
  await pool.query(
    `DELETE FROM favorites
     WHERE user_id = $1 AND hotel_id = $2`,
    [userId, hotelId]
  );
}
