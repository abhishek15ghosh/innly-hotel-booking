import { pool } from "../config/db.js";
import { getStayDates } from "../utils/bookingAvailability.js";

export async function getRoomForBooking(client, hotelId, roomId) {
  const result = await client.query(
    `SELECT id, hotel_id AS "hotelId", name, base_price AS "basePrice"
     FROM rooms
     WHERE id = $1 AND hotel_id = $2 AND is_active = TRUE`,
    [roomId, hotelId]
  );
  return result.rows[0] || null;
}

export async function lockInventoryRows(client, roomId, checkIn, checkOut) {
  const result = await client.query(
    `SELECT room_inventory.room_id,
            room_inventory.inventory_date::text AS inventory_date,
            COALESCE(room_inventory.price_override, r.base_price) AS price,
            room_inventory.total_inventory,
            room_inventory.booked_inventory
     FROM room_inventory
     JOIN rooms r ON r.id = room_inventory.room_id
     WHERE room_inventory.room_id = $1
       AND room_inventory.inventory_date >= $2::date
       AND room_inventory.inventory_date < $3::date
     ORDER BY room_inventory.inventory_date ASC
     FOR UPDATE`,
    [roomId, checkIn, checkOut]
  );
  return result.rows.map((row) => ({
    ...row,
    price: Number(row.price),
    total_inventory: Number(row.total_inventory),
    booked_inventory: Number(row.booked_inventory)
  }));
}

export async function createPendingBooking(client, payload) {
  const result = await client.query(
    `INSERT INTO bookings (
       user_id, hotel_id, status, check_in, check_out, rooms_booked, guests,
       guest_name, guest_email, guest_phone, total_amount, currency, payment_deadline
     )
     VALUES ($1, $2, 'payment_pending', $3, $4, $5, $6, $7, $8, $9, $10, 'INR', NOW() + INTERVAL '15 minutes')
     RETURNING id, status, total_amount AS "totalAmount", currency`,
    [
      payload.userId,
      payload.hotelId,
      payload.checkIn,
      payload.checkOut,
      payload.rooms,
      payload.guests,
      payload.guestName,
      payload.guestEmail,
      payload.guestPhone,
      payload.totalAmount
    ]
  );
  return result.rows[0];
}

export async function createBookingItem(client, payload) {
  await client.query(
    `INSERT INTO booking_items (booking_id, room_id, quantity, per_night_avg_price, subtotal_amount)
     VALUES ($1, $2, $3, $4, $5)`,
    [
      payload.bookingId,
      payload.roomId,
      payload.quantity,
      payload.perNightAveragePrice,
      payload.subtotalAmount
    ]
  );
}

export async function reserveInventory(client, roomId, stayDates, rooms) {
  await client.query(
    `UPDATE room_inventory
     SET booked_inventory = booked_inventory + $3
     WHERE room_id = $1
       AND inventory_date = ANY($2::date[])`,
    [roomId, stayDates, rooms]
  );
}

export async function releaseInventory(client, roomId, stayDates, rooms) {
  await client.query(
    `UPDATE room_inventory
     SET booked_inventory = GREATEST(0, booked_inventory - $3)
     WHERE room_id = $1
       AND inventory_date = ANY($2::date[])`,
    [roomId, stayDates, rooms]
  );
}

export async function createPaymentRecord(client, bookingId, amount) {
  const result = await client.query(
    `INSERT INTO payments (booking_id, amount, currency, status)
     VALUES ($1, $2, 'INR', 'pending')
     RETURNING id`,
    [bookingId, amount]
  );
  return result.rows[0];
}

export async function attachRazorpayOrder(client, bookingId, razorpayOrderId, metadata = {}) {
  await client.query(
    `UPDATE payments
     SET razorpay_order_id = $2,
         metadata = COALESCE(metadata, '{}'::jsonb) || $3::jsonb
     WHERE booking_id = $1`,
    [bookingId, razorpayOrderId, JSON.stringify(metadata)]
  );
}

export async function getBookingsForUser(userId) {
  const result = await pool.query(
    `SELECT b.id,
            h.name AS "hotelName",
            r.name AS "roomName",
            b.check_in::text AS "checkIn",
            b.check_out::text AS "checkOut",
            b.status,
            b.total_amount AS amount,
            b.currency,
            h.free_cancellation_hours AS "freeCancellationHours",
            h.cancellation_policy AS "cancellationPolicy",
            rf.status AS "refundStatus"
     FROM bookings b
     JOIN hotels h ON h.id = b.hotel_id
     JOIN booking_items bi ON bi.booking_id = b.id
     JOIN rooms r ON r.id = bi.room_id
     LEFT JOIN refunds rf ON rf.booking_id = b.id
     WHERE b.user_id = $1
     ORDER BY b.created_at DESC`,
    [userId]
  );
  const now = new Date();

  return result.rows.map((row) => {
    const cleanCheckIn = String(row.checkIn).slice(0, 10);
    const cleanCheckOut = String(row.checkOut).slice(0, 10);
    const checkInDate = new Date(`${cleanCheckIn}T00:00:00.000Z`);
    const freeHours = Number(row.freeCancellationHours) || 24;
    const deadlineMs = checkInDate.getTime() - freeHours * 60 * 60 * 1000;
    const deadlineDate = new Date(deadlineMs);
    const deadlineStr = deadlineDate.toISOString().split("T")[0];

    let canCancel = false;
    let displayMessage = "";

    if (row.status === "payment_pending") {
      canCancel = true;
      displayMessage = "Payment pending";
    } else if (row.status === "confirmed") {
      if (now.getTime() < deadlineMs) {
        canCancel = true;
        displayMessage = `Free cancellation available until ${deadlineStr}`;
      } else {
        canCancel = false;
        displayMessage = "Free cancellation period has ended";
      }
    } else if (row.status === "cancellation_pending") {
      canCancel = false;
      displayMessage = "Refund processing";
    } else if (row.status === "cancelled") {
      canCancel = false;
      displayMessage = row.refundStatus === "processed" ? "Refund completed" : "Booking cancelled";
    } else {
      canCancel = false;
      displayMessage = `Status: ${row.status}`;
    }

    return {
      ...row,
      amount: Number(row.amount),
      checkIn: cleanCheckIn,
      checkOut: cleanCheckOut,
      canCancel,
      cancellationDeadline: deadlineStr,
      refundStatus: row.refundStatus || null,
      displayMessage
    };
  });
}

export async function getBookingForCancellationDetails(client, bookingId, userId) {
  const result = await client.query(
    `SELECT b.id AS "bookingId",
            b.user_id AS "userId",
            b.status AS "bookingStatus",
            b.rooms_booked AS "roomsBooked",
            b.check_in::text AS "checkIn",
            b.check_out::text AS "checkOut",
            b.total_amount AS "bookingAmount",
            b.currency AS "bookingCurrency",
            bi.room_id AS "roomId",
            h.free_cancellation_hours AS "freeCancellationHours",
            h.cancellation_policy AS "cancellationPolicy",
            p.id AS "paymentId",
            p.razorpay_payment_id AS "razorpayPaymentId",
            p.amount AS "paymentAmount",
            p.currency AS "paymentCurrency",
            p.status AS "paymentStatus"
     FROM bookings b
     JOIN booking_items bi ON bi.booking_id = b.id
     JOIN hotels h ON h.id = b.hotel_id
     LEFT JOIN payments p ON p.booking_id = b.id
     WHERE b.id = $1 AND b.user_id = $2
     FOR UPDATE OF b`,
    [bookingId, userId]
  );
  if (!result.rows[0]) return null;
  const row = result.rows[0];
  return {
    ...row,
    roomsBooked: Number(row.roomsBooked),
    bookingAmount: Number(row.bookingAmount),
    paymentAmount: row.paymentAmount ? Number(row.paymentAmount) : null,
    freeCancellationHours: Number(row.freeCancellationHours) || 24,
    checkIn: String(row.checkIn).slice(0, 10),
    checkOut: String(row.checkOut).slice(0, 10)
  };
}

export async function getRefundByBookingId(client, bookingId) {
  const result = await client.query(
    `SELECT id, booking_id AS "bookingId", payment_id AS "paymentId", user_id AS "userId",
            razorpay_refund_id AS "razorpayRefundId", razorpay_payment_id AS "razorpayPaymentId",
            idempotency_key AS "idempotencyKey", amount, currency, status, reason, error_message AS "errorMessage"
     FROM refunds
     WHERE booking_id = $1`,
    [bookingId]
  );
  if (!result.rows[0]) return null;
  return {
    ...result.rows[0],
    amount: Number(result.rows[0].amount)
  };
}

export async function createRefundRecord(client, payload) {
  const result = await client.query(
    `INSERT INTO refunds (
       booking_id, payment_id, user_id, razorpay_refund_id, razorpay_payment_id,
       idempotency_key, amount, currency, status, reason
     )
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9::refund_status, $10)
     ON CONFLICT (idempotency_key) DO UPDATE
     SET updated_at = NOW()
     RETURNING id, booking_id AS "bookingId", status, razorpay_refund_id AS "razorpayRefundId"`,
    [
      payload.bookingId,
      payload.paymentId,
      payload.userId,
      payload.razorpayRefundId || null,
      payload.razorpayPaymentId,
      payload.idempotencyKey,
      payload.amount,
      payload.currency || "INR",
      payload.status || "pending",
      payload.reason
    ]
  );
  return result.rows[0];
}

export async function claimPendingRefundsForLease(client, workerId, limit = 5) {
  const result = await client.query(
    `UPDATE refunds
     SET lease_until = NOW() + INTERVAL '2 minutes',
         leased_by = $1,
         attempt_count = attempt_count + 1,
         last_attempt_at = NOW(),
         updated_at = NOW()
     WHERE id IN (
       SELECT id FROM refunds
       WHERE status = 'pending'
         AND (lease_until IS NULL OR lease_until < NOW())
         AND (next_attempt_at IS NULL OR next_attempt_at <= NOW())
       ORDER BY created_at ASC
       LIMIT $2
       FOR UPDATE SKIP LOCKED
     )
     RETURNING id AS "refundId",
               booking_id AS "bookingId",
               payment_id AS "paymentId",
               user_id AS "userId",
               razorpay_refund_id AS "razorpayRefundId",
               razorpay_payment_id AS "razorpayPaymentId",
               idempotency_key AS "idempotencyKey",
               amount,
               currency,
               status AS "refundStatus",
               attempt_count AS "attemptCount",
               reason`,
    [workerId, limit]
  );

  if (result.rows.length === 0) return [];

  const refundIds = result.rows.map((r) => r.refundId);
  const details = await client.query(
    `SELECT r.id AS "refundId",
            b.status AS "bookingStatus",
            b.rooms_booked AS "roomsBooked",
            b.check_in::text AS "checkIn",
            b.check_out::text AS "checkOut",
            bi.room_id AS "roomId"
     FROM refunds r
     JOIN bookings b ON b.id = r.booking_id
     JOIN booking_items bi ON bi.booking_id = b.id
     WHERE r.id = ANY($1)`,
    [refundIds]
  );

  const detailMap = new Map(details.rows.map((d) => [d.refundId, d]));

  return result.rows.map((row) => {
    const d = detailMap.get(row.refundId) || {};
    return {
      ...row,
      amount: Number(row.amount),
      roomsBooked: Number(d.roomsBooked || 1),
      checkIn: String(d.checkIn || "").slice(0, 10),
      checkOut: String(d.checkOut || "").slice(0, 10),
      bookingStatus: d.bookingStatus,
      roomId: d.roomId
    };
  });
}

export async function finalizeProcessedRefundAtomically(client, { bookingId, paymentId, refundId, razorpayRefundId }) {
  const lockResult = await client.query(
    `SELECT b.id AS "bookingId",
            b.status AS "bookingStatus",
            b.rooms_booked AS "roomsBooked",
            b.check_in::text AS "checkIn",
            b.check_out::text AS "checkOut",
            bi.room_id AS "roomId",
            r.id AS "refundId",
            r.status AS "refundStatus"
     FROM bookings b
     JOIN booking_items bi ON bi.booking_id = b.id
     JOIN refunds r ON r.booking_id = b.id
     WHERE b.id = $1 AND r.id = $2
     FOR UPDATE OF b, r`,
    [bookingId, refundId]
  );

  if (!lockResult.rows[0]) {
    return { success: false, alreadyFinalized: false, reason: "Booking or refund record not found" };
  }

  const row = lockResult.rows[0];

  if (row.refundStatus === "processed" || row.bookingStatus === "cancelled") {
    return {
      success: true,
      alreadyFinalized: true,
      bookingStatus: "cancelled",
      refundStatus: "processed",
      displayMessage: "Refund completed"
    };
  }

  if (row.refundStatus !== "pending" && row.refundStatus !== "cancellation_pending") {
    return { success: false, alreadyFinalized: false, reason: `Invalid refund status for finalization: ${row.refundStatus}` };
  }

  await client.query(
    `UPDATE refunds
     SET status = 'processed',
         razorpay_refund_id = COALESCE($2, razorpay_refund_id),
         lease_until = NULL,
         leased_by = NULL,
         updated_at = NOW()
     WHERE id = $1`,
    [refundId, razorpayRefundId || null]
  );

  if (paymentId) {
    await client.query(
      `UPDATE payments
       SET status = 'refunded',
           updated_at = NOW()
       WHERE id = $1`,
      [paymentId]
    );
  }

  await client.query(
    `UPDATE bookings
     SET status = 'cancelled',
         updated_at = NOW()
     WHERE id = $1`,
    [bookingId]
  );

  const stayDates = getStayDates(row.checkIn, row.checkOut);
  await releaseInventory(client, row.roomId, stayDates, Number(row.roomsBooked));

  return {
    success: true,
    alreadyFinalized: false,
    bookingStatus: "cancelled",
    refundStatus: "processed",
    displayMessage: "Refund completed"
  };
}

export async function releaseRefundLeaseAndScheduleRetry(client, refundId, attemptCount = 1, errorMessage = null, isAuthError = false) {
  const attempt = Number(attemptCount) || 1;
  let delayMinutes = Math.min(60, Math.pow(2, Math.min(attempt, 6)));

  let updatedError = errorMessage;
  if (isAuthError) {
    updatedError = `Manual reconciliation required: Authentication failed (401/403). ${errorMessage || ""}`;
    delayMinutes = 60;
  } else if (attempt >= 10) {
    updatedError = `Manual reconciliation required: Exceeded ${attempt} retry attempts. ${errorMessage || ""}`;
    delayMinutes = 60;
  }

  await client.query(
    `UPDATE refunds
     SET lease_until = NULL,
         leased_by = NULL,
         next_attempt_at = NOW() + ($2 || ' minutes')::INTERVAL,
         error_message = COALESCE($3, error_message),
         updated_at = NOW()
     WHERE id = $1`,
    [refundId, String(delayMinutes), updatedError]
  );
}

export async function getPendingRefundsForReconciliation(client, limit = 50) {
  const result = await client.query(
    `SELECT r.id AS "refundId",
            r.booking_id AS "bookingId",
            r.payment_id AS "paymentId",
            r.user_id AS "userId",
            r.razorpay_refund_id AS "razorpayRefundId",
            r.razorpay_payment_id AS "razorpayPaymentId",
            r.idempotency_key AS "idempotencyKey",
            r.amount,
            r.currency,
            r.status AS "refundStatus",
            b.status AS "bookingStatus",
            b.rooms_booked AS "roomsBooked",
            b.check_in::text AS "checkIn",
            b.check_out::text AS "checkOut",
            bi.room_id AS "roomId"
     FROM refunds r
     JOIN bookings b ON b.id = r.booking_id
     JOIN booking_items bi ON bi.booking_id = b.id
     WHERE r.status = 'pending'
     ORDER BY r.created_at ASC
     LIMIT $1
     FOR UPDATE OF r SKIP LOCKED`,
    [limit]
  );
  return result.rows.map((row) => ({
    ...row,
    amount: Number(row.amount),
    roomsBooked: Number(row.roomsBooked),
    checkIn: String(row.checkIn).slice(0, 10),
    checkOut: String(row.checkOut).slice(0, 10)
  }));
}

export async function revertBookingToConfirmed(client, bookingId, paymentId) {
  if (paymentId) {
    await client.query(
      `UPDATE payments
       SET status = 'captured',
           updated_at = NOW()
       WHERE id = $1 AND status = 'refund_pending'`,
      [paymentId]
    );
  }
  await client.query(
    `UPDATE bookings
     SET status = 'confirmed',
         updated_at = NOW()
     WHERE id = $1 AND status = 'cancellation_pending'`,
    [bookingId]
  );
}

export async function markBookingCancelledAndPaymentRefunded(client, bookingId, paymentId, refundId, razorpayRefundId) {
  if (refundId) {
    await client.query(
      `UPDATE refunds
       SET status = 'processed',
           razorpay_refund_id = COALESCE($2, razorpay_refund_id),
           updated_at = NOW()
       WHERE id = $1`,
      [refundId, razorpayRefundId]
    );
  }
  if (paymentId) {
    await client.query(
      `UPDATE payments
       SET status = 'refunded',
           updated_at = NOW()
       WHERE id = $1`,
      [paymentId]
    );
  }
  await client.query(
    `UPDATE bookings
     SET status = 'cancelled',
         updated_at = NOW()
     WHERE id = $1`,
    [bookingId]
  );
}

export async function markBookingCancellationPending(client, bookingId, paymentId, refundId, razorpayRefundId) {
  if (refundId) {
    await client.query(
      `UPDATE refunds
       SET status = 'pending',
           razorpay_refund_id = COALESCE($2, razorpay_refund_id),
           updated_at = NOW()
       WHERE id = $1`,
      [refundId, razorpayRefundId]
    );
  }
  if (paymentId) {
    await client.query(
      `UPDATE payments
       SET status = 'refund_pending',
           updated_at = NOW()
       WHERE id = $1`,
      [paymentId]
    );
  }
  await client.query(
    `UPDATE bookings
     SET status = 'cancellation_pending',
         updated_at = NOW()
     WHERE id = $1`,
    [bookingId]
  );
}

export async function markRefundFailed(client, refundId, errorMessage) {
  await client.query(
    `UPDATE refunds
     SET status = 'failed',
         error_message = $2,
         updated_at = NOW()
     WHERE id = $1`,
    [refundId, errorMessage]
  );
}

export async function cancelBookingRecord(client, bookingId, reason) {
  await client.query(
    `UPDATE bookings
     SET status = 'cancelled',
         cancellation_reason = $2,
         updated_at = NOW()
     WHERE id = $1`,
    [bookingId, reason]
  );
}

export async function getPaymentVerificationContext(client, bookingId, userId) {
  const result = await client.query(
    `SELECT b.id AS "bookingId",
            b.user_id AS "userId",
            b.status AS "bookingStatus",
            b.total_amount AS "bookingAmount",
            b.currency AS "bookingCurrency",
            b.rooms_booked AS "roomsBooked",
            b.check_in AS "checkIn",
            b.check_out AS "checkOut",
            bi.room_id AS "roomId",
            p.id AS "paymentId",
            p.razorpay_order_id AS "razorpayOrderId",
            p.razorpay_payment_id AS "storedRazorpayPaymentId",
            p.amount AS "paymentAmount",
            p.currency AS "paymentCurrency",
            p.status AS "paymentStatus"
     FROM bookings b
     JOIN booking_items bi ON bi.booking_id = b.id
     JOIN payments p ON p.booking_id = b.id
     WHERE b.id = $1 AND b.user_id = $2
     FOR UPDATE OF b, p`,
    [bookingId, userId]
  );
  if (!result.rows[0]) return null;
  const row = result.rows[0];
  return {
    ...row,
    bookingAmount: Number(row.bookingAmount),
    paymentAmount: Number(row.paymentAmount)
  };
}

export async function getPaymentContextByOrderId(client, orderId) {
  const result = await client.query(
    `SELECT b.id AS "bookingId",
            b.user_id AS "userId",
            b.status AS "bookingStatus",
            b.total_amount AS "bookingAmount",
            b.currency AS "bookingCurrency",
            b.rooms_booked AS "roomsBooked",
            b.check_in AS "checkIn",
            b.check_out AS "checkOut",
            bi.room_id AS "roomId",
            p.id AS "paymentId",
            p.razorpay_order_id AS "razorpayOrderId",
            p.razorpay_payment_id AS "storedRazorpayPaymentId",
            p.amount AS "paymentAmount",
            p.currency AS "paymentCurrency",
            p.status AS "paymentStatus"
     FROM bookings b
     JOIN booking_items bi ON bi.booking_id = b.id
     JOIN payments p ON p.booking_id = b.id
     WHERE p.razorpay_order_id = $1
     FOR UPDATE OF b, p`,
    [orderId]
  );
  if (!result.rows[0]) return null;
  const row = result.rows[0];
  return {
    ...row,
    bookingAmount: Number(row.bookingAmount),
    paymentAmount: Number(row.paymentAmount)
  };
}

export async function getExpiredPendingBookings(client, now = new Date(), limit = 100) {
  const result = await client.query(
    `SELECT b.id AS "bookingId",
            b.user_id AS "userId",
            b.status AS "bookingStatus",
            b.rooms_booked AS "roomsBooked",
            b.check_in::text AS "checkIn",
            b.check_out::text AS "checkOut",
            bi.room_id AS "roomId",
            p.status AS "paymentStatus"
     FROM bookings b
     JOIN booking_items bi ON bi.booking_id = b.id
     JOIN payments p ON p.booking_id = b.id
     WHERE b.status = 'payment_pending'
       AND p.status = 'pending'
       AND b.payment_deadline <= $1
     ORDER BY b.payment_deadline ASC
     LIMIT $2
     FOR UPDATE OF b, p SKIP LOCKED`,
    [now, limit]
  );
  return result.rows.map((row) => ({
    ...row,
    roomsBooked: Number(row.roomsBooked),
    checkIn: formatDateString(row.checkIn),
    checkOut: formatDateString(row.checkOut)
  }));
}

export async function markPaymentCaptured(client, bookingId, payload) {
  const paymentResult = await client.query(
    `UPDATE payments
     SET status = 'captured',
         razorpay_payment_id = $2,
         razorpay_signature = $3,
         captured_at = NOW(),
         metadata = COALESCE(metadata, '{}'::jsonb) || $4::jsonb
     WHERE booking_id = $1 AND status = 'pending'`,
    [
      bookingId,
      payload.razorpayPaymentId,
      payload.razorpaySignature,
      JSON.stringify({ source: "mobile_checkout" })
    ]
  );

  if (paymentResult.rowCount === 0) {
    throw new ApiError(409, "Payment state conflict: payment is not in pending status");
  }

  const bookingResult = await client.query(
    `UPDATE bookings
     SET status = 'confirmed',
         updated_at = NOW()
     WHERE id = $1 AND status = 'payment_pending'`,
    [bookingId]
  );

  if (bookingResult.rowCount === 0) {
    throw new ApiError(409, "Booking state conflict: booking is not in payment_pending status");
  }
}

export async function markPaymentCapturedFromWebhook(client, bookingId, payload) {
  const paymentResult = await client.query(
    `UPDATE payments
     SET status = 'captured',
         razorpay_payment_id = $2,
         captured_at = NOW(),
         metadata = COALESCE(metadata, '{}'::jsonb) || $3::jsonb
     WHERE booking_id = $1 AND status = 'pending'`,
    [
      bookingId,
      payload.razorpayPaymentId,
      JSON.stringify({ source: "razorpay_webhook", eventId: payload.eventId })
    ]
  );

  if (paymentResult.rowCount === 0) {
    throw new ApiError(409, "Payment state conflict: payment is not in pending status");
  }

  const bookingResult = await client.query(
    `UPDATE bookings
     SET status = 'confirmed',
         updated_at = NOW()
     WHERE id = $1 AND status = 'payment_pending'`,
    [bookingId]
  );

  if (bookingResult.rowCount === 0) {
    throw new ApiError(409, "Booking state conflict: booking is not in payment_pending status");
  }
}

export async function markPaymentFailed(client, bookingId, reason) {
  const paymentResult = await client.query(
    `UPDATE payments
     SET status = 'failed',
         metadata = COALESCE(metadata, '{}'::jsonb) || $2::jsonb
     WHERE booking_id = $1 AND status = 'pending'`,
    [bookingId, JSON.stringify({ failureReason: reason })]
  );

  if (paymentResult.rowCount === 0) {
    throw new ApiError(409, "Payment state conflict: payment is not in pending status");
  }

  const bookingResult = await client.query(
    `UPDATE bookings
     SET status = 'payment_failed',
         updated_at = NOW()
     WHERE id = $1 AND status = 'payment_pending'`,
    [bookingId]
  );

  if (bookingResult.rowCount === 0) {
    throw new ApiError(409, "Booking state conflict: booking is not in payment_pending status");
  }
}
