import * as adminService from "../services/admin.service.js";
import * as reviewService from "../services/review.service.js";

export async function createHotel(req, res) {
  const data = await adminService.createHotel(req.validated.body);
  res.status(201).json({ success: true, data });
}

export async function createRoom(req, res) {
  const data = await adminService.createRoom(req.validated.body);
  res.status(201).json({ success: true, data });
}

export async function updateRoomPricing(req, res) {
  const data = await adminService.upsertRoomPricing(req.validated.params.roomId, req.validated.body);
  res.json({ success: true, data });
}

export async function listBookings(req, res) {
  const data = await adminService.listAdminBookings();
  res.json({ success: true, data });
}

export async function listPayments(req, res) {
  const data = await adminService.listAdminPayments();
  res.json({ success: true, data });
}

export async function moderateReview(req, res) {
  const data = await reviewService.moderateReview(
    req.validated.params.reviewId,
    req.validated.body.status
  );
  res.json({ success: true, data });
}
