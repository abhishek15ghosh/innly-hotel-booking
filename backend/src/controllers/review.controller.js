import * as reviewService from "../services/review.service.js";

export async function createReview(req, res) {
  const data = await reviewService.createReview(req.user, req.validated.body);
  res.status(201).json({ success: true, data });
}

export async function listHotelReviews(req, res) {
  const data = await reviewService.listHotelReviews(
    req.validated.params.hotelId,
    req.validated.query || {}
  );
  res.json({ success: true, data });
}

export async function getMyReview(req, res) {
  const data = await reviewService.getMyReviewAndEligibility(
    req.user,
    req.validated.params.hotelId
  );
  res.json({ success: true, data });
}
