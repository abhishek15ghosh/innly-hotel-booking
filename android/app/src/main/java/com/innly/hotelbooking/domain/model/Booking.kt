package com.innly.hotelbooking.domain.model

data class Booking(
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

data class BookingRequest(
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

data class PaymentOrder(
    val bookingId: String,
    val amount: Int,
    val currency: String,
    val razorpayOrderId: String,
    val razorpayAmount: Int,
)

data class CancellationResult(
    val bookingId: String,
    val status: String,
    val canCancel: Boolean = false,
    val cancellationDeadline: String? = null,
    val refundStatus: String? = null,
    val displayMessage: String,
)
