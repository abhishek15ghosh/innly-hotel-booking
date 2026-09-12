package com.innly.hotelbooking.core.network

import com.google.gson.JsonParser
import retrofit2.HttpException

private val IP_ADDRESS_REGEX = Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b""")
private val PORT_REGEX = Regex(""":\d{2,5}\b""")
private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

private val INTERNAL_ERROR_PATTERNS = listOf(
    "com.innly",
    "java.",
    "kotlin.",
    "exception",
    "nullpointer",
    "cannot be cast",
    "response.error",
    "failed to connect",
    "connection refused",
    "connect timed out",
    "unreachable",
    "no route to host",
    "unable to resolve host",
    "no address associated",
    "connection reset",
    "software caused connection abort",
    "broken pipe",
    "econnrefused",
    "ehostunreach",
    "enonetwork",
    "sslhandshake",
    "stacktrace",
)

fun Throwable.parseErrorMessage(
    defaultMessage: String = "Unable to connect. Please check your connection and try again.",
): String {
    // 1. Direct infrastructure & runtime technical exception types
    if (this is java.net.ConnectException ||
        this is java.net.SocketTimeoutException ||
        this is java.net.UnknownHostException ||
        this is java.net.SocketException ||
        this is java.net.NoRouteToHostException ||
        this is java.net.PortUnreachableException ||
        this is java.io.InterruptedIOException ||
        this is javax.net.ssl.SSLException ||
        this is NullPointerException ||
        this is ClassCastException
    ) {
        return defaultMessage
    }

    // 2. Retrofit HTTP Exceptions
    if (this is HttpException) {
        if (code() >= 500) {
            return defaultMessage
        }
        val bodyStr = runCatching { response()?.errorBody()?.string() }.getOrNull()
        if (!bodyStr.isNullOrBlank()) {
            val jsonMsg = runCatching {
                val elem = JsonParser.parseString(bodyStr)
                if (elem.isJsonObject) {
                    val obj = elem.asJsonObject
                    obj.get("message")?.asString ?: obj.get("error")?.asString
                } else null
            }.getOrNull()
            val candidateMsg = jsonMsg ?: bodyStr.trim()
            val sanitized = sanitizeMessage(candidateMsg)
            if (sanitized != null) {
                return sanitized
            }
        }
        return defaultMessage
    }

    // 3. Inspect raw message for technical / infrastructure leaks
    val rawMessage = message.orEmpty().trim()
    val sanitized = sanitizeMessage(rawMessage)
    return sanitized ?: defaultMessage
}

private fun sanitizeMessage(msg: String): String? {
    if (msg.isBlank()) return null
    if (IP_ADDRESS_REGEX.containsMatchIn(msg)) return null
    if (PORT_REGEX.containsMatchIn(msg)) return null
    if (URL_REGEX.containsMatchIn(msg)) return null
    if (INTERNAL_ERROR_PATTERNS.any { msg.contains(it, ignoreCase = true) }) return null
    return msg
}
