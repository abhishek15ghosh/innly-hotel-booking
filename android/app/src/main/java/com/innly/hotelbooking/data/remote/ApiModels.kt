package com.innly.hotelbooking.data.remote

import com.google.gson.annotations.SerializedName
import com.innly.hotelbooking.domain.model.Availability
import com.innly.hotelbooking.domain.model.Booking
import com.innly.hotelbooking.domain.model.CancellationResult
import com.innly.hotelbooking.domain.model.DailyAvailability
import com.innly.hotelbooking.domain.model.EligibleBooking
import com.innly.hotelbooking.domain.model.Hotel
import com.innly.hotelbooking.domain.model.MyReviewState
import com.innly.hotelbooking.domain.model.PaymentOrder
import com.innly.hotelbooking.domain.model.PaymentVerificationResult
import com.innly.hotelbooking.domain.model.PrivateReview
import com.innly.hotelbooking.domain.model.PublicReview
import com.innly.hotelbooking.domain.model.PublicReviewPage
import com.innly.hotelbooking.domain.model.RatingSummary
import com.innly.hotelbooking.domain.model.Review
import com.innly.hotelbooking.domain.model.Room
import com.innly.hotelbooking.domain.model.UserProfile

data class PaymentVerificationResponseDto(
    val bookingId: String,
    val bookingStatus: String,
    val paymentStatus: String,
    val message: String? = null,
)

fun PaymentVerificationResponseDto.toDomain() = PaymentVerificationResult(
    bookingId = bookingId,
    bookingStatus = bookingStatus,
    paymentStatus = paymentStatus,
    message = message,
)

data class ApiResponse<T>(
    val success: Boolean,
    val data: T,
)

data class PaginatedPayload<T>(
    val items: List<T>,
    val page: Int,
    val limit: Int,
    val total: Int,
)

data class UserProfileDto(
    val id: String,
    val firebaseUid: String,
    val email: String,
    val displayName: String?,
    val phoneNumber: String?,
    val role: String,
)

data class ProfileSyncRequest(
    val displayName: String? = null,
    val phoneNumber: String? = null,
)

data class HotelDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val city: String,
    val address: String? = null,
    val rating: Double,
    val reviewCount: Int = 0,
    val startingPrice: Int,
    val thumbnailUrl: String,
    val images: List<String>? = null,
    val amenities: List<String>? = null,
    val isFavorite: Boolean = false,
    val rooms: List<RoomDto>? = null,
)

data class RoomDto(
    val id: String,
    val hotelId: String,
    val name: String,
    val description: String,
    val capacity: Int,
    val bedType: String,
    val basePrice: Int,
    val thumbnailUrl: String,
    val images: List<String>? = null,
)

data class DailyAvailabilityDto(
    val date: String,
    val price: Int,
    val availableInventory: Int,
)

data class AvailabilityDto(
    val available: Boolean,
    val nights: Int,
    val totalAmount: Int,
    val currency: String,
    val dailyBreakdown: List<DailyAvailabilityDto>,
)

data class BookingDto(
    val id: String,
    val hotelName: String,
    val roomName: String,
    val checkIn: String,
    val checkOut: String,
    val status: String,
    val amount: Int,
    val currency: String,
    val canCancel: Boolean = false,
    val cancellationDeadline: String? = null,
    val refundStatus: String? = null,
    val displayMessage: String? = null,
)

fun BookingDto.toDomain() = Booking(
    id = id,
    hotelName = hotelName,
    roomName = roomName,
    checkIn = checkIn.split("T")[0],
    checkOut = checkOut.split("T")[0],
    status = status,
    amount = amount,
    currency = currency,
    canCancel = canCancel,
    cancellationDeadline = cancellationDeadline?.split("T")?.get(0),
    refundStatus = refundStatus,
    displayMessage = displayMessage,
)

data class BookingRequestDto(
    val hotelId: String,
    val roomId: String,
    val checkIn: String,
    val checkOut: String,
    val rooms: Int,
    val guests: Int,
    val guestName: String,
    val guestEmail: String,
    val guestPhone: String,
)

data class CreateBookingResponseDto(
    val bookingId: String,
    val status: String,
    val amount: Int,
    val currency: String,
    @SerializedName("razorpayOrder")
    val razorpayOrder: RazorpayOrderDto,
)

data class RazorpayOrderDto(
    val id: String,
    val amount: Int,
    val currency: String,
)

data class PaymentVerificationRequestDto(
    val bookingId: String,
    val razorpayOrderId: String,
    val razorpayPaymentId: String,
    val razorpaySignature: String,
)

data class PublicReviewDto(
    val id: String,
    val rating: Int,
    val title: String,
    val comment: String,
    val createdAt: String,
    val authorName: String,
    val isVerifiedStay: Boolean,
)

data class ReviewListResponseDto(
    val items: List<PublicReviewDto>,
    val page: Int,
    val limit: Int,
    val total: Int,
    val avgRating: Double,
    val reviewCount: Int,
    val ratingDistribution: Map<String, Int>,
)

data class EligibleBookingDto(
    val bookingId: String,
    val checkIn: String,
    val checkOut: String,
    val roomName: String,
)

data class PrivateReviewDto(
    val id: String,
    val bookingId: String?,
    val rating: Int,
    val title: String,
    val comment: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val moderatedAt: String? = null,
)

data class MyReviewResponseDto(
    val canWriteReview: Boolean,
    val eligibleBooking: EligibleBookingDto? = null,
    val myReviews: List<PrivateReviewDto> = emptyList(),
    val latestReview: PrivateReviewDto? = null,
)

data class ReviewRequestDto(
    val hotelId: String,
    val bookingId: String,
    val reviewId: String? = null,
    val rating: Int,
    val title: String,
    val comment: String,
)

fun UserProfileDto.toDomain() = UserProfile(
    id = id,
    firebaseUid = firebaseUid,
    displayName = displayName?.trim().orEmpty(),
    email = email,
    phoneNumber = phoneNumber,
    role = role,
)

fun RoomDto.toDomain() = Room(
    id = id,
    hotelId = hotelId,
    name = name,
    description = description,
    capacity = capacity,
    bedType = bedType,
    basePrice = basePrice,
    thumbnailUrl = thumbnailUrl,
    images = images.orEmpty(),
)

fun HotelDto.toDomain() = Hotel(
    id = id,
    name = name,
    description = description.orEmpty(),
    city = city,
    address = address.orEmpty(),
    rating = rating,
    reviewCount = reviewCount,
    startingPrice = startingPrice,
    thumbnailUrl = thumbnailUrl,
    images = images.orEmpty(),
    amenities = amenities.orEmpty(),
    isFavorite = isFavorite,
    rooms = rooms.orEmpty().map { it.toDomain() },
)

fun AvailabilityDto.toDomain() = Availability(
    available = available,
    nights = nights,
    totalAmount = totalAmount,
    currency = currency,
    dailyBreakdown = dailyBreakdown.map {
        DailyAvailability(
            date = it.date,
            price = it.price,
            availableInventory = it.availableInventory,
        )
    },
)



fun PublicReviewDto.toDomain() = PublicReview(
    id = id,
    rating = rating,
    title = title,
    comment = comment,
    createdAt = createdAt,
    authorName = authorName,
    isVerifiedStay = isVerifiedStay,
)

fun ReviewListResponseDto.toDomain() = PublicReviewPage(
    items = items.map { it.toDomain() },
    page = page,
    limit = limit,
    total = total,
    ratingSummary = RatingSummary(
        avgRating = avgRating,
        reviewCount = reviewCount,
        ratingDistribution = ratingDistribution,
    ),
)

fun EligibleBookingDto.toDomain() = EligibleBooking(
    bookingId = bookingId,
    checkIn = checkIn,
    checkOut = checkOut,
    roomName = roomName,
)

fun PrivateReviewDto.toDomain() = PrivateReview(
    id = id,
    bookingId = bookingId,
    rating = rating,
    title = title,
    comment = comment,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
    moderatedAt = moderatedAt,
)

fun MyReviewResponseDto.toDomain() = MyReviewState(
    canWriteReview = canWriteReview,
    eligibleBooking = eligibleBooking?.toDomain(),
    myReviews = myReviews.map { it.toDomain() },
    latestReview = latestReview?.toDomain(),
)

fun CreateBookingResponseDto.toDomain() = PaymentOrder(
    bookingId = bookingId,
    amount = amount,
    currency = currency,
    razorpayOrderId = razorpayOrder.id,
    razorpayAmount = razorpayOrder.amount,
)

data class CancellationResponseDto(
    val bookingId: String,
    val status: String,
    val canCancel: Boolean = false,
    val cancellationDeadline: String? = null,
    val refundStatus: String? = null,
    val displayMessage: String? = null,
)

fun CancellationResponseDto.toDomain() = CancellationResult(
    bookingId = bookingId,
    status = status,
    canCancel = canCancel,
    cancellationDeadline = cancellationDeadline?.split("T")?.get(0),
    refundStatus = refundStatus,
    displayMessage = displayMessage ?: "Cancellation processed",
)
