import { pool } from "../config/db.js";

export async function listHotels(filters, userId) {
  const values = [];
  const conditions = ["h.is_active = TRUE"];

  if (filters.city) {
    values.push(filters.city);
    conditions.push(`LOWER(h.city) = LOWER($${values.length})`);
  }

  if (filters.minPrice) {
    values.push(filters.minPrice);
    conditions.push(`r.base_price >= $${values.length}`);
  }

  if (filters.maxPrice) {
    values.push(filters.maxPrice);
    conditions.push(`r.base_price <= $${values.length}`);
  }

  if (filters.minRating) {
    values.push(filters.minRating);
    conditions.push(`h.avg_rating >= $${values.length}`);
  }

  if (filters.amenities?.length) {
    values.push(filters.amenities);
    conditions.push(`EXISTS (
      SELECT 1 FROM hotel_amenities ha
      WHERE ha.hotel_id = h.id
      AND ha.amenity = ANY($${values.length})
    )`);
  }

  values.push(filters.limit, filters.offset, userId || null);

  const result = await pool.query(
    `SELECT h.id, h.name, h.description, h.city, h.address,
            h.avg_rating AS rating, h.review_count AS "reviewCount",
            MIN(r.base_price) AS "startingPrice",
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
            EXISTS (
              SELECT 1 FROM favorites f
              WHERE f.hotel_id = h.id AND f.user_id = $${values.length}
            ) AS "isFavorite"
     FROM hotels h
     JOIN rooms r ON r.hotel_id = h.id AND r.is_active = TRUE
     WHERE ${conditions.join(" AND ")}
     GROUP BY h.id
     ORDER BY h.avg_rating DESC, h.created_at DESC
     LIMIT $${values.length - 2}
     OFFSET $${values.length - 1}`,
    values
  );

  return result.rows.map((row) => ({
    ...row,
    startingPrice: Number(row.startingPrice),
    rating: Number(row.rating),
    reviewCount: Number(row.reviewCount)
  }));
}

export async function getHotelById(hotelId, userId) {
  const hotelResult = await pool.query(
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
                SELECT json_agg(hi.image_url ORDER BY hi.position)
                FROM hotel_images hi
                WHERE hi.hotel_id = h.id
              ),
              '[]'::json
            ) AS images,
            COALESCE(
              (
                SELECT json_agg(ha.amenity ORDER BY ha.amenity)
                FROM hotel_amenities ha
                WHERE ha.hotel_id = h.id
              ),
              '[]'::json
            ) AS amenities,
            EXISTS (
              SELECT 1 FROM favorites f
              WHERE f.hotel_id = h.id AND f.user_id = $2
            ) AS "isFavorite"
     FROM hotels h
     WHERE h.id = $1 AND h.is_active = TRUE`,
    [hotelId, userId || null]
  );

  if (!hotelResult.rows[0]) {
    return null;
  }

  const roomsResult = await pool.query(
    `SELECT id, hotel_id AS "hotelId", name, description, capacity,
            bed_type AS "bedType", base_price AS "basePrice",
            COALESCE(
              (
                SELECT ri.image_url
                FROM room_images ri
                WHERE ri.room_id = rooms.id
                ORDER BY ri.position ASC
                LIMIT 1
              ),
              ''
            ) AS "thumbnailUrl",
            COALESCE(
              (
                SELECT json_agg(ri.image_url ORDER BY ri.position)
                FROM room_images ri
                WHERE ri.room_id = rooms.id
              ),
              '[]'::json
            ) AS images
     FROM rooms
     WHERE hotel_id = $1 AND is_active = TRUE
     ORDER BY base_price ASC`,
    [hotelId]
  );

  return {
    ...hotelResult.rows[0],
    startingPrice: Number(hotelResult.rows[0].startingPrice),
    rating: Number(hotelResult.rows[0].rating),
    reviewCount: Number(hotelResult.rows[0].reviewCount),
    rooms: roomsResult.rows.map((row) => ({
      ...row,
      capacity: Number(row.capacity),
      basePrice: Number(row.basePrice)
    }))
  };
}

export async function getRoomInventory(hotelId, roomId, checkIn, checkOut, client = pool) {
  const result = await client.query(
    `SELECT ri.room_id,
            ri.inventory_date::text AS inventory_date,
            COALESCE(ri.price_override, r.base_price) AS price,
            ri.total_inventory,
            ri.booked_inventory
     FROM room_inventory ri
     JOIN rooms r ON r.id = ri.room_id
     WHERE r.hotel_id = $1
       AND r.id = $2
       AND r.is_active = TRUE
       AND ri.inventory_date >= $3::date
       AND ri.inventory_date < $4::date
     ORDER BY ri.inventory_date ASC`,
    [hotelId, roomId, checkIn, checkOut]
  );
  return result.rows.map((row) => ({
    ...row,
    price: Number(row.price),
    total_inventory: Number(row.total_inventory),
    booked_inventory: Number(row.booked_inventory)
  }));
}
