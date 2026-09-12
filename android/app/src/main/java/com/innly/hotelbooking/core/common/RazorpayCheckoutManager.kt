package com.innly.hotelbooking.core.common

import android.app.Activity
import android.util.Log
import com.innly.hotelbooking.BuildConfig
import com.innly.hotelbooking.domain.model.PaymentOrder
import com.innly.hotelbooking.domain.model.UserProfile
import com.razorpay.Checkout
import org.json.JSONObject

object RazorpayCheckoutManager {
    fun openCheckout(
        activity: Activity,
        paymentOrder: PaymentOrder,
        profile: UserProfile?,
    ): Result<Unit> = runCatching {
        val checkout = Checkout().apply {
            setKeyID(BuildConfig.RAZORPAY_KEY_ID)
        }
        checkout.open(activity, buildOptions(paymentOrder, profile))
    }.onFailure { error ->
        Log.w("RazorpayCheckoutManager", "Failed to open Razorpay checkout: ${error.javaClass.simpleName}")
    }

    private fun buildOptions(
        paymentOrder: PaymentOrder,
        profile: UserProfile?,
    ): JSONObject {
        return JSONObject().apply {
            put("name", "Innly")
            put("description", "Hotel booking payment")
            put("currency", paymentOrder.currency)
            put("order_id", paymentOrder.razorpayOrderId)
            put("amount", paymentOrder.razorpayAmount)
            put(
                "prefill",
                JSONObject().apply {
                    put("email", profile?.email.orEmpty())
                    put("name", profile?.displayName.orEmpty())
                    put("contact", profile?.phoneNumber.orEmpty())
                },
            )
            put("theme", JSONObject().apply { put("color", "#005E5D") })
        }
    }
}
