import { razorpayClient } from "../config/razorpay.js";
import { razorpayRefundGateway } from "../gateways/razorpayRefund.gateway.js";
import { withTransaction } from "../config/db.js";
import * as bookingRepository from "../repositories/booking.repository.js";
import { ApiError } from "../utils/apiError.js";
import { getStayDates, summarizeAvailability } from "../utils/bookingAvailability.js";

export async function createBooking(user, payload, deps = {}) {
  const {
    withTx = withTransaction,
    bookingRepo = bookingRepository,
    razorpay = razorpayClient
  } = deps;

  if (!user?.id) {
    throw new ApiError(403, "Sync your profile before creating bookings");
  }

  const stayDates = getStayDates(payload.checkIn, payload.checkOut);

  const pendingBooking = await withTx(async (client) => {
    const room = await bookingRepo.getRoomForBooking(client, payload.hotelId, payload.roomId);
    if (!room) {
      throw new ApiError(404, "Room not found");
    }

    const lockedInventory = await bookingRepo.lockInventoryRows(
      client,
      payload.roomId,
      payload.checkIn,
      payload.checkOut
    );
    const availability = summarizeAvailability(lockedInventory, stayDates, payload.rooms);

    if (!availability.available) {
      throw new ApiError(409, "Room inventory is not available for the selected stay");
    }

    const booking = await bookingRepo.createPendingBooking(client, {
      userId: user.id,
      hotelId: payload.hotelId,
      checkIn: payload.checkIn,
      checkOut: payload.checkOut,
      rooms: payload.rooms,
      guests: payload.guests,
      guestName: payload.guestName,
      guestEmail: payload.guestEmail,
      guestPhone: payload.guestPhone,
      totalAmount: availability.totalAmount
    });

    await bookingRepo.createBookingItem(client, {
      bookingId: booking.id,
      roomId: payload.roomId,
      quantity: payload.rooms,
      perNightAveragePrice: Math.round(availability.totalAmount / availability.nights / payload.rooms),
      subtotalAmount: availability.totalAmount
    });

    await bookingRepo.reserveInventory(client, payload.roomId, stayDates, payload.rooms);
    await bookingRepo.createPaymentRecord(client, booking.id, availability.totalAmount);

    return {
      bookingId: booking.id,
      amount: availability.totalAmount,
      currency: "INR"
    };
  });

  let razorpayOrder;
  try {
    razorpayOrder = await razorpay.orders.create({
      amount: pendingBooking.amount * 100,
      currency: pendingBooking.currency,
      receipt: pendingBooking.bookingId,
      notes: {
        bookingId: pendingBooking.bookingId,
        source: "innly_mobile_app"
      }
    });
  } catch (error) {
    await withTx(async (client) => {
      await bookingRepo.releaseInventory(
        client,
        payload.roomId,
        stayDates,
        payload.rooms
      );
      await bookingRepo.markPaymentFailed(
        client,
        pendingBooking.bookingId,
        "razorpay_order_creation_failed"
      );
    });

    throw new ApiError(
      502,
      "Unable to initiate payment. Please try again."
    );
  }

  await withTx(async (client) => {
    await bookingRepo.attachRazorpayOrder(client, pendingBooking.bookingId, razorpayOrder.id, {
      receipt: razorpayOrder.receipt
    });
  });

  return {
    bookingId: pendingBooking.bookingId,
    status: "payment_pending",
    amount: pendingBooking.amount,
    currency: pendingBooking.currency,
    razorpayOrder
  };
}

export async function getBookingHistory(user) {
  if (!user?.id) {
    throw new ApiError(403, "Sync your profile before viewing bookings");
  }
  return bookingRepository.getBookingsForUser(user.id);
}

export async function cancelBooking(user, bookingId, reason, deps = {}) {
  if (!user?.id) {
    throw new ApiError(403, "Sync your profile before cancelling bookings");
  }
  if (!reason || typeof reason !== "string" || reason.trim().length < 3) {
    throw new ApiError(400, "A valid cancellation reason (at least 3 characters) is required");
  }

  const {
    withTx = withTransaction,
    bookingRepo = bookingRepository,
    gateway = razorpayRefundGateway,
    now = new Date()
  } = deps;

  // --- PHASE 1: Transaction 1 (Lock, validate, establish pending refund attempt) ---
  const phase1Result = await withTx(async (client) => {
    const booking = await bookingRepo.getBookingForCancellationDetails(client, bookingId, user.id);
    if (!booking) {
      throw new ApiError(404, "Booking not found");
    }

    if (booking.bookingStatus === "cancelled") {
      const existingRefund = await bookingRepo.getRefundByBookingId(client, bookingId);
      return {
        type: "ALREADY_CANCELLED",
        result: {
          bookingId,
          status: "cancelled",
          canCancel: false,
          cancellationDeadline: null,
          refundStatus: existingRefund?.status || null,
          displayMessage: existingRefund?.status === "processed" ? "Refund completed" : "Booking cancelled"
        }
      };
    }

    const stayDates = getStayDates(booking.checkIn, booking.checkOut);

    if (booking.bookingStatus === "payment_pending") {
      await bookingRepo.releaseInventory(client, booking.roomId, stayDates, booking.roomsBooked);
      await bookingRepo.cancelBookingRecord(client, bookingId, reason);
      return {
        type: "UNPAID_CANCELLED",
        result: {
          bookingId,
          status: "cancelled",
          canCancel: false,
          cancellationDeadline: null,
          refundStatus: null,
          displayMessage: "Booking cancelled"
        }
      };
    }

    if (booking.bookingStatus === "cancellation_pending") {
      const existingRefund = await bookingRepo.getRefundByBookingId(client, bookingId);
      const checkInDate = new Date(`${booking.checkIn}T00:00:00.000Z`);
      const freeHours = booking.freeCancellationHours || 24;
      const deadlineMs = checkInDate.getTime() - freeHours * 60 * 60 * 1000;
      const deadlineDate = new Date(deadlineMs);
      const deadlineStr = deadlineDate.toISOString().split("T")[0];

      return {
        type: "REFUND_PENDING",
        result: {
          bookingId,
          status: "cancellation_pending",
          canCancel: false,
          cancellationDeadline: deadlineStr,
          refundStatus: existingRefund?.status || "pending",
          displayMessage: "Refund processing"
        }
      };
    }

    if (booking.bookingStatus === "confirmed") {
      const checkInDate = new Date(`${booking.checkIn}T00:00:00.000Z`);
      const freeHours = booking.freeCancellationHours || 24;
      const deadlineMs = checkInDate.getTime() - freeHours * 60 * 60 * 1000;
      const deadlineDate = new Date(deadlineMs);
      const deadlineStr = deadlineDate.toISOString().split("T")[0];

      if (now.getTime() >= deadlineMs) {
        throw new ApiError(409, "Free cancellation period has ended");
      }

      if (booking.paymentStatus !== "captured" || !booking.razorpayPaymentId) {
        throw new ApiError(409, "Booking payment is not in captured status");
      }

      // Deterministic idempotency key (max 40 chars)
      const cleanBookingId = bookingId.replace(/-/g, "");
      const idempotencyKey = `ref_${cleanBookingId}`;

      let refundRecord = await bookingRepo.getRefundByBookingId(client, bookingId);
      if (!refundRecord) {
        refundRecord = await bookingRepo.createRefundRecord(client, {
          bookingId,
          paymentId: booking.paymentId,
          userId: user.id,
          razorpayPaymentId: booking.razorpayPaymentId,
          idempotencyKey,
          amount: booking.bookingAmount,
          currency: booking.bookingCurrency || "INR",
          status: "pending",
          reason
        });
      }

      await bookingRepo.markBookingCancellationPending(
        client,
        bookingId,
        booking.paymentId,
        refundRecord.id,
        refundRecord.razorpayRefundId || null
      );

      return {
        type: "INITIATE_REFUND",
        booking,
        refundRecord,
        stayDates,
        deadlineStr,
        idempotencyKey
      };
    }

    throw new ApiError(409, "Booking cannot be cancelled in its current state");
  });

  if (phase1Result.type === "ALREADY_CANCELLED" || phase1Result.type === "UNPAID_CANCELLED" || phase1Result.type === "REFUND_PENDING") {
    return phase1Result.result;
  }

  const { booking, refundRecord, stayDates, deadlineStr, idempotencyKey } = phase1Result;

  // --- PHASE 2: Outside Transaction (Call Razorpay Refund Gateway with X-Refund-Idempotency Header) ---
  let razorpayRefund = null;
  let razorpayError = null;

  try {
    razorpayRefund = await gateway.createRefund(
      booking.razorpayPaymentId,
      {
        amount: Math.round(booking.bookingAmount * 100),
        notes: { bookingId, reason },
        receipt: idempotencyKey
      },
      { idempotencyKey }
    );
  } catch (err) {
    razorpayError = err;
  }

  // --- PHASE 3: Transaction 2 (Persist result & state transition atomically) ---
  return withTx(async (client) => {
    if (razorpayError) {
      const isDefinitiveFailure =
        razorpayError.statusCode === 400 ||
        razorpayError.statusCode === 404 ||
        razorpayError.statusCode === 422 ||
        (razorpayError.message && razorpayError.message.includes("rejected"));

      if (isDefinitiveFailure) {
        if (refundRecord?.id) {
          await bookingRepo.markRefundFailed(client, refundRecord.id, razorpayError.message || "Razorpay refund rejected");
        }
        await bookingRepo.revertBookingToConfirmed(client, bookingId, booking.paymentId);
        throw new ApiError(502, "Unable to process Razorpay refund. Booking remains confirmed.");
      }

      // Timeout, 409 Conflict or temporary network failure: keep pending for worker reconciliation
      return {
        bookingId,
        status: "cancellation_pending",
        canCancel: false,
        cancellationDeadline: deadlineStr || null,
        refundStatus: "pending",
        displayMessage: "Refund processing"
      };
    }

    const rStatus = razorpayRefund?.status;
    if (rStatus === "processed" || rStatus === "captured") {
      const finalRes = await bookingRepo.finalizeProcessedRefundAtomically(client, {
        bookingId,
        paymentId: booking.paymentId,
        refundId: refundRecord.id,
        razorpayRefundId: razorpayRefund?.id || null
      });

      return {
        bookingId,
        status: "cancelled",
        canCancel: false,
        cancellationDeadline: deadlineStr || null,
        refundStatus: "processed",
        displayMessage: finalRes.displayMessage || "Refund completed"
      };
    } else if (rStatus === "failed") {
      if (refundRecord?.id) {
        await bookingRepo.markRefundFailed(client, refundRecord.id, razorpayRefund?.error_description || "Razorpay refund failed");
      }
      await bookingRepo.revertBookingToConfirmed(client, bookingId, booking.paymentId);
      throw new ApiError(502, "Unable to process Razorpay refund. Booking remains confirmed.");
    } else {
      // Missing, unknown, or pending status: keep cancellation_pending without releasing inventory
      if (razorpayRefund?.id) {
        await bookingRepo.markBookingCancellationPending(
          client,
          bookingId,
          booking.paymentId,
          refundRecord.id,
          razorpayRefund.id
        );
      }
      return {
        bookingId,
        status: "cancellation_pending",
        canCancel: false,
        cancellationDeadline: deadlineStr || null,
        refundStatus: "pending",
        displayMessage: "Refund processing"
      };
    }
  });
}

export async function reconcileRefund(bookingId, deps = {}) {
  const {
    withTx = withTransaction,
    bookingRepo = bookingRepository,
    razorpay = razorpayClient
  } = deps;

  const refundRecord = await withTx(async (client) => {
    return bookingRepo.getRefundByBookingId(client, bookingId);
  });

  if (!refundRecord || refundRecord.status === "processed" || refundRecord.status === "failed") {
    return refundRecord;
  }

  let fetchedRefund = null;
  if (refundRecord.razorpayRefundId && razorpay && razorpay.refunds && typeof razorpay.refunds.fetch === "function") {
    try {
      fetchedRefund = await razorpay.refunds.fetch(refundRecord.razorpayRefundId);
    } catch (err) {
      fetchedRefund = null;
    }
  }

  if (fetchedRefund && (fetchedRefund.status === "processed" || fetchedRefund.status === "captured")) {
    await withTx(async (client) => {
      const booking = await bookingRepo.getBookingForCancellationDetails(client, bookingId, refundRecord.userId);
      if (booking && booking.bookingStatus !== "cancelled") {
        const stayDates = getStayDates(booking.checkIn, booking.checkOut);
        await bookingRepo.markBookingCancelledAndPaymentRefunded(
          client,
          bookingId,
          booking.paymentId,
          refundRecord.id,
          fetchedRefund.id
        );
        await bookingRepo.releaseInventory(client, booking.roomId, stayDates, booking.roomsBooked);
      }
    });
  } else if (fetchedRefund && fetchedRefund.status === "failed") {
    await withTx(async (client) => {
      await bookingRepo.markRefundFailed(client, refundRecord.id, fetchedRefund.error_description || "Refund failed");
      await bookingRepo.revertBookingToConfirmed(client, bookingId, refundRecord.paymentId);
    });
  }

  return withTx(async (client) => {
    return bookingRepo.getRefundByBookingId(client, bookingId);
  });
}
