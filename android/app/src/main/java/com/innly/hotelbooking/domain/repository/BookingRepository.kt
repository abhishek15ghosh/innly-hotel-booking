package com.innly.hotelbooking.domain.repository

import com.innly.hotelbooking.domain.model.Booking
import com.innly.hotelbooking.domain.model.BookingRequest
import com.innly.hotelbooking.domain.model.CancellationResult
import com.innly.hotelbooking.domain.model.PaymentOrder
import com.innly.hotelbooking.domain.model.PaymentVerificationResult

interface BookingRepository {
    suspend fun createBooking(request: BookingRequest): PaymentOrder
    suspend fun verifyPayment(
        bookingId: String,
        razorpayOrderId: String,
        razorpayPaymentId: String,
        razorpaySignature: String,
    ): PaymentVerificationResult
    suspend fun getBookingHistory(): List<Booking>
    suspend fun cancelBooking(bookingId: String, reason: String): CancellationResult
}
