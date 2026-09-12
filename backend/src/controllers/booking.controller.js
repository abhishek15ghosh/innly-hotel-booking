import * as bookingService from "../services/booking.service.js";

export async function createBooking(req, res) {
  const data = await bookingService.createBooking(req.user, req.validated.body);
  res.status(201).json({ success: true, data });
}

export async function getBookingHistory(req, res) {
  const data = await bookingService.getBookingHistory(req.user);
  res.json({ success: true, data });
}

export async function cancelBooking(req, res) {
  const data = await bookingService.cancelBooking(
    req.user,
    req.validated.params.bookingId,
    req.validated.body.reason
  );
  res.json({ success: true, data });
}
