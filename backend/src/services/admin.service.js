import { pool } from "../config/db.js";

export async function createHotel(payload) {
  const result = await pool.query(
    `INSERT INTO hotels (name, slug, description, city, address, star_rating, cancellation_policy)
     VALUES ($1, $2, $3, $4, $5, $6, $7)
     RETURNING id, name, slug`,
    [
      payload.name,
      payload.slug,
      payload.description,
      payload.city,
      payload.address,
      payload.starRating || 4,
      payload.cancellationPolicy
    ]
  );
  return result.rows[0];
}

export async function createRoom(payload) {
  const result = await pool.query(
    `INSERT INTO rooms (hotel_id, name, description, capacity, bed_type, base_price, total_inventory)
     VALUES ($1, $2, $3, $4, $5, $6, $7)
     RETURNING id, hotel_id AS "hotelId", name`,
    [
      payload.hotelId,
      payload.name,
      payload.description,
      payload.capacity,
      payload.bedType,
      payload.basePrice,
      payload.totalInventory
    ]
  );
  return result.rows[0];
}

export async function upsertRoomPricing(roomId, payload) {
  const result = await pool.query(
    `INSERT INTO room_inventory (room_id, inventory_date, total_inventory, booked_inventory, price_override)
     VALUES (
       $1,
       $2::date,
       COALESCE($3, (SELECT total_inventory FROM rooms WHERE id = $1)),
       0,
       $4
     )
     ON CONFLICT (room_id, inventory_date)
     DO UPDATE SET
       total_inventory = COALESCE(EXCLUDED.total_inventory, room_inventory.total_inventory),
       price_override = COALESCE(EXCLUDED.price_override, room_inventory.price_override)
     RETURNING room_id AS "roomId", inventory_date AS "date", total_inventory AS "totalInventory",
               price_override AS "priceOverride"`,
    [roomId, payload.date, payload.totalInventory || null, payload.priceOverride || null]
  );
  return result.rows[0];
}

export async function listAdminBookings() {
  const result = await pool.query(
    `SELECT b.id, b.status, b.check_in AS "checkIn", b.check_out AS "checkOut",
            b.total_amount AS amount, h.name AS "hotelName", u.email AS "userEmail"
     FROM bookings b
     JOIN hotels h ON h.id = b.hotel_id
     JOIN users u ON u.id = b.user_id
     ORDER BY b.created_at DESC`
  );
  return result.rows.map((row) => ({ ...row, amount: Number(row.amount) }));
}

export async function listAdminPayments() {
  const result = await pool.query(
    `SELECT p.id, p.booking_id AS "bookingId", p.razorpay_order_id AS "razorpayOrderId",
            p.razorpay_payment_id AS "razorpayPaymentId", p.amount, p.currency, p.status
     FROM payments p
     ORDER BY p.created_at DESC`
  );
  return result.rows.map((row) => ({ ...row, amount: Number(row.amount) }));
}
