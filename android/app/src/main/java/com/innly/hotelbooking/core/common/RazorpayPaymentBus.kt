package com.innly.hotelbooking.core.common

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface RazorpayPaymentResult {
    data class Success(
        val paymentId: String,
        val orderId: String,
        val signature: String,
    ) : RazorpayPaymentResult

    data class Error(
        val code: Int,
        val message: String,
    ) : RazorpayPaymentResult
}

object RazorpayPaymentBus {
    private val _events = MutableSharedFlow<RazorpayPaymentResult>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun emit(result: RazorpayPaymentResult) {
        _events.tryEmit(result)
    }
}
