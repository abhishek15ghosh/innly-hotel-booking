package com.innly.hotelbooking

import com.innly.hotelbooking.core.network.parseErrorMessage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ErrorParsingTest {

    private val jsonMediaType = "application/json".toMediaType()

    @Test
    fun `parseErrorMessage extracts message from structured backend JSON error response`() {
        val jsonErrorBody = """{"success":false,"message":"Sync your profile before creating bookings"}"""
        val response = Response.error<Any>(403, jsonErrorBody.toResponseBody(jsonMediaType))
        val httpException = HttpException(response)

        val result = httpException.parseErrorMessage("Fallback")

        assertEquals("Sync your profile before creating bookings", result)
    }

    @Test
    fun `parseErrorMessage extracts validation message from 400 Bad Request error response`() {
        val jsonErrorBody = """{"success":false,"message":"Validation failed","details":{"fieldErrors":{}}}"""
        val response = Response.error<Any>(400, jsonErrorBody.toResponseBody(jsonMediaType))
        val httpException = HttpException(response)

        val result = httpException.parseErrorMessage("Fallback")

        assertEquals("Validation failed", result)
    }

    @Test
    fun `parseErrorMessage falls back to default when error body is not JSON or blank`() {
        val response = Response.error<Any>(502, "".toResponseBody(jsonMediaType))
        val httpException = HttpException(response)

        val result = httpException.parseErrorMessage("Unable to initiate payment")

        assertEquals("Unable to initiate payment", result)
    }

    @Test
    fun `parseErrorMessage sanitizes raw Kotlin reflection parameter exception and returns friendly fallback`() {
        val rawException = IllegalArgumentException(
            "Parameter specified as non-null is null: method com.innly.hotelbooking.domain.model.UserProfile.<init>, parameter displayName"
        )

        val result = rawException.parseErrorMessage("Unable to create booking. Please try again.")

        assertEquals("Unable to create booking. Please try again.", result)
    }

    @Test
    fun `parseErrorMessage sanitizes NullPointerException and internal class names`() {
        val npe = NullPointerException("null pointer at com.innly.hotelbooking.something")

        val result = npe.parseErrorMessage("An unexpected error occurred")

        assertEquals("An unexpected error occurred", result)
    }

    @Test
    fun `parseErrorMessage preserves clean non-technical business exception messages`() {
        val businessException = IllegalStateException("Selected room is sold out")

        val result = businessException.parseErrorMessage("Unable to create booking. Please try again.")

        assertEquals("Selected room is sold out", result)
    }

    @Test
    fun `ConnectException containing slash 10 0 2 2 colon 8080 produces generic message without host or port leakage`() {
        val connectException = java.net.ConnectException("Failed to connect to /10.0.2.2:8080")

        val result = connectException.parseErrorMessage()

        assertEquals("Unable to connect. Please check your connection and try again.", result)
        assert(!result.contains("10.0.2.2"))
        assert(!result.contains("8080"))
        assert(!result.contains("/"))
    }

    @Test
    fun `ConnectException with custom screen fallback produces screen-specific friendly message without leakage`() {
        val connectException = java.net.ConnectException("Failed to connect to /10.0.2.2:8080")

        val result = connectException.parseErrorMessage("Unable to load hotels. Please try again.")

        assertEquals("Unable to load hotels. Please try again.", result)
        assert(!result.contains("10.0.2.2"))
        assert(!result.contains("8080"))
    }

    @Test
    fun `raw exception containing IP address and port produces sanitized fallback`() {
        val rawException = RuntimeException("Connection refused at 192.168.1.100:8080")

        val result = rawException.parseErrorMessage("Unable to connect. Please check your connection and try again.")

        assertEquals("Unable to connect. Please check your connection and try again.", result)
    }

    @Test
    fun `raw exception containing URL produces sanitized fallback`() {
        val rawException = RuntimeException("Failed to reach http://10.0.2.2:8080/api/v1/hotels")

        val result = rawException.parseErrorMessage()

        assertEquals("Unable to connect. Please check your connection and try again.", result)
    }

    @Test
    fun `SocketTimeoutException and UnknownHostException produce friendly fallback`() {
        val timeoutException = java.net.SocketTimeoutException("connect timed out")
        val dnsException = java.net.UnknownHostException("api.innly.internal")

        assertEquals(
            "Unable to connect. Please check your connection and try again.",
            timeoutException.parseErrorMessage(),
        )
        assertEquals(
            "Unable to connect. Please check your connection and try again.",
            dnsException.parseErrorMessage(),
        )
    }

    @Test
    fun `legitimate backend validation messages remain intact across diverse business errors`() {
        val validationMessages = listOf(
            "Check-out date must be after check-in date",
            "Please provide a valid full name",
            "Room is no longer available for the selected dates",
            "Maximum 4 guests allowed for this room",
        )

        for (msg in validationMessages) {
            val json = """{"message":"$msg"}"""
            val response = Response.error<Any>(400, json.toResponseBody(jsonMediaType))
            val exception = HttpException(response)

            val parsed = exception.parseErrorMessage("Unable to complete request. Please try again.")
            assertEquals(msg, parsed)
        }
    }
}
