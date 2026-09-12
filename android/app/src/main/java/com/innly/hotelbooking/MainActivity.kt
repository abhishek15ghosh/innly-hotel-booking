package com.innly.hotelbooking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.innly.hotelbooking.core.common.RazorpayPaymentBus
import com.innly.hotelbooking.core.common.RazorpayPaymentResult
import com.innly.hotelbooking.core.ui.InnlyTheme
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity(), PaymentResultWithDataListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Checkout.preload(applicationContext)
        setContent {
            InnlyTheme {
                Surface {
                    InnlyApp()
                }
            }
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        RazorpayPaymentBus.emit(
            RazorpayPaymentResult.Success(
                paymentId = paymentData?.paymentId ?: razorpayPaymentId.orEmpty(),
                orderId = paymentData?.orderId.orEmpty(),
                signature = paymentData?.signature.orEmpty(),
            ),
        )
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        RazorpayPaymentBus.emit(
            RazorpayPaymentResult.Error(
                code = code,
                message = response ?: "Payment failed",
            ),
        )
    }
}
