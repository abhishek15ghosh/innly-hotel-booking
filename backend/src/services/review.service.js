import { ApiError } from "../utils/apiError.js";
import { withTransaction } from "../config/db.js";
import * as reviewRepository from "../repositories/review.repository.js";

export function formatSafeAuthorName(rawName) {
  if (!rawName || typeof rawName !== "string") {
    return "Innly Guest";
  }
  const trimmed = rawName.trim();
  if (!trimmed) {
    return "Innly Guest";
  }
  const parts = trimmed.split(/\s+/);
  if (parts.length === 1) {
    return parts[0];
  }
  const firstName = parts[0];
  const lastInitial = parts[parts.length - 1].charAt(0).toUpperCase();
  return `${firstName} ${lastInitial}.`;
}

export async function listHotelReviews(hotelId, { page = 1, limit = 10 } = {}, deps = {}) {
  const { reviewRepo = reviewRepository } = deps;

  const parsedPage = Math.max(1, parseInt(page, 10) || 1);
  const parsedLimit = Math.min(50, Math.max(1, parseInt(limit, 10) || 10));
  const offset = (parsedPage - 1) * parsedLimit;

  const [reviewRows, ratingSummary] = await Promise.all([
    reviewRepo.getApprovedReviewsByHotelId(
      undefined,
      hotelId,
      parsedLimit,
      offset
    ),
    reviewRepo.getHotelRatingDistribution(
      undefined,
      hotelId
    )
  ]);

  const sanitizedRows = (Array.isArray(reviewRows) ? reviewRows : []).map((r) => ({
    id: r.id,
    rating: Number(r.rating),
    title: r.title,
    comment: r.comment,
    createdAt: r.createdAt,
    authorName: formatSafeAuthorName(r.rawAuthorName ?? r.authorName),
    isVerifiedStay: r.isVerifiedStay === true
  }));

  return {
    items: sanitizedRows,
    page: parsedPage,
    limit: parsedLimit,
    total: ratingSummary.reviewCount,
    avgRating: ratingSummary.avgRating,
    reviewCount: ratingSummary.reviewCount,
    ratingDistribution: ratingSummary.ratingDistribution
  };
}

export async function getMyReviewAndEligibility(user, hotelId, deps = {}) {
  const { reviewRepo = reviewRepository } = deps;

  if (!user || !user.id) {
    throw new ApiError(401, "Authentication required");
  }

  const [eligibleBooking, myReviews] = await Promise.all([
    reviewRepo.findEligibleBookingForUser(
      undefined,
      user.id,
      hotelId
    ),
    reviewRepo.getUserReviewsForHotel(
      undefined,
      user.id,
      hotelId
    )
  ]);

  const latestReview = Array.isArray(myReviews) && myReviews.length > 0 ? myReviews[0] : null;

  return {
    canWriteReview: Boolean(eligibleBooking),
    eligibleBooking: eligibleBooking
      ? {
          bookingId: eligibleBooking.bookingId,
          checkIn: eligibleBooking.checkIn,
          checkOut: eligibleBooking.checkOut,
          roomName: eligibleBooking.roomName
        }
      : null,
    myReviews: Array.isArray(myReviews) ? myReviews : [],
    latestReview
  };
}

export async function createReview(user, payload, deps = {}) {
  const {
    withTx = withTransaction,
    reviewRepo = reviewRepository,
    now = new Date()
  } = deps;

  if (!user || !user.id) {
    throw new ApiError(403, "User profile required to submit reviews");
  }

  const todayStr = now.toISOString().slice(0, 10);

  return withTx(async (client) => {
    // Validate booking ownership, hotel match, payment capture, confirmed/completed status, and past checkout
    const booking = await reviewRepo.getBookingForReviewValidation(
      client,
      payload.bookingId,
      user.id,
      payload.hotelId
    );

    if (!booking) {
      throw new ApiError(404, "No matching booking found for this review");
    }

    if (booking.paymentStatus !== "captured") {
      throw new ApiError(400, "Reviews are only available for bookings with confirmed captured payments");
    }

    if (!["confirmed", "completed"].includes(booking.bookingStatus)) {
      throw new ApiError(400, "Reviews are only available for confirmed or completed stays");
    }

    if (booking.checkOut > todayStr) {
      throw new ApiError(400, "Reviews can only be submitted after completing your stay");
    }

    // 1. Resubmission of rejected review on same review ID
    if (payload.reviewId) {
      const updatedReview = await reviewRepo.resubmitRejectedReview(client, {
        reviewId: payload.reviewId,
        userId: user.id,
        hotelId: payload.hotelId,
        bookingId: payload.bookingId,
        rating: payload.rating,
        title: payload.title.trim(),
        comment: payload.comment.trim()
      });

      if (!updatedReview) {
        throw new ApiError(404, "Unable to resubmit review. Ensure the review belongs to you, matches this stay, and was rejected.");
      }

      return {
        ...updatedReview,
        userName: user.displayName ? formatSafeAuthorName(user.displayName) : "Innly Guest"
      };
    }

    // 2. New review submission
    let review;
    try {
      review = await reviewRepo.createReview(client, {
        userId: user.id,
        hotelId: payload.hotelId,
        bookingId: payload.bookingId,
        rating: payload.rating,
        title: payload.title.trim(),
        comment: payload.comment.trim()
      });
    } catch (err) {
      if (err?.code === "23505") {
        throw new ApiError(409, "A review has already been submitted for this stay");
      }
      throw err;
    }

    return {
      ...review,
      userName: user.displayName ? formatSafeAuthorName(user.displayName) : "Innly Guest"
    };
  });
}

export async function moderateReview(reviewId, status, deps = {}) {
  const { withTx = withTransaction, reviewRepo = reviewRepository } = deps;

  return withTx(async (client) => {
    const review = await reviewRepo.moderateReview(client, reviewId, status);
    if (!review) {
      throw new ApiError(404, "Review not found");
    }

    await reviewRepo.refreshHotelRating(client, review.hotelId);

    return review;
  });
}
