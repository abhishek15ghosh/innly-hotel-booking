import { withTransaction } from "../config/db.js";
import * as bookingRepository from "../repositories/booking.repository.js";
import { getStayDates } from "../utils/bookingAvailability.js";

let expiryTimer = null;

export async function processExpiredBookings(deps = {}, limit = 100) {
  const {
    withTx = withTransaction,
    bookingRepo = bookingRepository,
    nowFn = () => new Date()
  } = deps;

  return withTx(async (client) => {
    const expiredBookings = await bookingRepo.getExpiredPendingBookings(client, nowFn(), limit);
    let count = 0;

    for (const booking of expiredBookings) {
      // Defensively skip anything captured or not pending
      if (booking.bookingStatus !== "payment_pending" || booking.paymentStatus !== "pending") {
        continue;
      }

      try {
        const stayDates = getStayDates(booking.checkIn, booking.checkOut);
        await bookingRepo.releaseInventory(client, booking.roomId, stayDates, booking.roomsBooked);
        await bookingRepo.markPaymentFailed(client, booking.bookingId, "payment_timeout");
        count++;
      } catch (err) {
        console.warn(`Booking expiry warning [Booking ID: ${booking.bookingId}]: ${err.message}`);
        // Continue processing remaining valid expired bookings without aborting batch or releasing inventory for invalid range
      }
    }

    return { processedCount: count };
  });
}

export function startExpiryWorker(intervalMs = 60000, deps = {}) {
  if (expiryTimer) return;

  const logger = deps.logger || console;
  logger.log(`Booking expiry worker started (interval: ${intervalMs}ms)`);

  processExpiredBookings(deps).catch((err) => {
    logger.error("Initial booking expiry worker error:", err.message);
  });

  expiryTimer = setInterval(() => {
    processExpiredBookings(deps).catch((err) => {
      logger.error("Periodic booking expiry worker error:", err.message);
    });
  }, intervalMs);

  if (expiryTimer.unref) {
    expiryTimer.unref();
  }
}

export function stopExpiryWorker(deps = {}) {
  if (expiryTimer) {
    clearInterval(expiryTimer);
    expiryTimer = null;
    const logger = deps.logger || console;
    logger.log("Booking expiry worker stopped");
  }
}
