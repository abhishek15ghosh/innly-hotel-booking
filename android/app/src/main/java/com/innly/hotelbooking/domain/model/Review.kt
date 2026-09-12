package com.innly.hotelbooking.domain.model

data class PublicReview(
    val id: String,
    val rating: Int,
    val title: String,
    val comment: String,
    val createdAt: String,
    val authorName: String,
    val isVerifiedStay: Boolean,
)

data class RatingSummary(
    val avgRating: Double,
    val reviewCount: Int,
    val ratingDistribution: Map<String, Int>,
)

data class PublicReviewPage(
    val items: List<PublicReview>,
    val page: Int,
    val limit: Int,
    val total: Int,
    val ratingSummary: RatingSummary,
)

data class EligibleBooking(
    val bookingId: String,
    val checkIn: String,
    val checkOut: String,
    val roomName: String,
)

data class PrivateReview(
    val id: String,
    val bookingId: String?,
    val rating: Int,
    val title: String,
    val comment: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val moderatedAt: String?,
)

data class MyReviewState(
    val canWriteReview: Boolean,
    val eligibleBooking: EligibleBooking?,
    val myReviews: List<PrivateReview>,
    val latestReview: PrivateReview?,
)

// Legacy alias for compatibility
typealias Review = PrivateReview
