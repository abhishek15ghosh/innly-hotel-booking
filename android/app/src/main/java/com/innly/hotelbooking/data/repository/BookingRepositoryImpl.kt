package com.innly.hotelbooking.data.repository

import com.innly.hotelbooking.data.remote.BookingRequestDto
import com.innly.hotelbooking.data.remote.HotelApiService
import com.innly.hotelbooking.data.remote.PaymentVerificationRequestDto
import com.innly.hotelbooking.data.remote.toDomain
import com.innly.hotelbooking.domain.model.Booking
import com.innly.hotelbooking.domain.model.BookingRequest
import com.innly.hotelbooking.domain.model.CancellationResult
import com.innly.hotelbooking.domain.model.PaymentOrder
import com.innly.hotelbooking.domain.model.PaymentVerificationResult
import com.innly.hotelbooking.domain.repository.BookingRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookingRepositoryImpl @Inject constructor(
    private val apiService: HotelApiService,
) : BookingRepository {
    override suspend fun createBooking(request: BookingRequest): PaymentOrder {
        return apiService.createBooking(
            request = BookingRequestDto(
                hotelId = request.hotelId,
                roomId = request.roomId,
                checkIn = request.checkIn,
                checkOut = request.checkOut,
                rooms = request.rooms,
                guests = request.guests,
                guestName = request.guestName,
                guestEmail = request.guestEmail,
                guestPhone = request.guestPhone,
            ),
        ).data.toDomain()
    }

    override suspend fun verifyPayment(
        bookingId: String,
        razorpayOrderId: String,
        razorpayPaymentId: String,
        razorpaySignature: String,
    ): PaymentVerificationResult {
        return apiService.verifyPayment(
            request = PaymentVerificationRequestDto(
                bookingId = bookingId,
                razorpayOrderId = razorpayOrderId,
                razorpayPaymentId = razorpayPaymentId,
                razorpaySignature = razorpaySignature,
            ),
        ).data.toDomain()
    }

    override suspend fun getBookingHistory(): List<Booking> {
        return apiService.getBookingHistory().data.map { it.toDomain() }
    }

    override suspend fun cancelBooking(bookingId: String, reason: String): CancellationResult {
        val response = apiService.cancelBooking(
            bookingId = bookingId,
            request = mapOf("reason" to reason),
        )
        return response.data.toDomain()
    }
}
