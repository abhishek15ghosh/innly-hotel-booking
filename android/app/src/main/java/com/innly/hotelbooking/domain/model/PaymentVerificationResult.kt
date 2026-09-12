package com.innly.hotelbooking.domain.model

data class PaymentVerificationResult(
    val bookingId: String = "",
    val bookingStatus: String = "",
    val paymentStatus: String = "",
    val message: String? = null,
)
