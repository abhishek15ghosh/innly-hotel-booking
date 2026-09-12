package com.innly.hotelbooking.core.network

import java.net.URI

object EnvironmentConfigValidator {

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    private val SENSITIVE_HEADERS = setOf("authorization", "cookie", "set-cookie")

    fun isHeaderRedacted(headerName: String): Boolean {
        return SENSITIVE_HEADERS.contains(headerName.trim().lowercase())
    }

    /**
     * Escapes characters for safe inclusion in generated Java string literals (e.g. BuildConfig).
     * Backslashes, double quotes, line breaks, carriage returns, and tabs are escaped.
     * Dollar signs ($) are NOT escaped because '\$' is an illegal escape character in Java.
     */
    fun escapeJavaStringLiteral(value: String): String {
        val sb = StringBuilder()
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '\"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun validateApiUrl(url: String, isRelease: Boolean): ValidationResult {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            return ValidationResult.Invalid("API Base URL cannot be blank")
        }

        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            return ValidationResult.Invalid("API Base URL is malformed")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme == null) {
            return ValidationResult.Invalid("API Base URL is missing a scheme")
        }

        if (isRelease) {
            if (scheme != "https") {
                return ValidationResult.Invalid("Release API URL must use HTTPS protocol")
            }

            if (uri.rawUserInfo != null || uri.userInfo != null) {
                return ValidationResult.Invalid("Release API URL cannot contain user credentials / userInfo")
            }

            val host = uri.host?.lowercase()
            if (host.isNullOrBlank()) {
                return ValidationResult.Invalid("Release API URL is missing a valid host")
            }

            if (isForbiddenHost(host)) {
                return ValidationResult.Invalid("Release API URL cannot use loopback, emulator, or placeholder domains")
            }
        } else {
            if (scheme != "http" && scheme != "https") {
                return ValidationResult.Invalid("Debug API URL must use HTTP or HTTPS")
            }
        }

        return ValidationResult.Valid
    }

    private fun isForbiddenHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost")) return true
        if (host == "127.0.0.1" || host.startsWith("127.")) return true
        if (host == "10.0.2.2") return true
        if (host == "::1" || host == "[::1]" || host == "0.0.0.0") return true

        if (host == "example.com" || host.endsWith(".example.com")) return true
        if (host == "example.org" || host.endsWith(".example.org")) return true
        if (host == "example.net" || host.endsWith(".example.net")) return true

        return false
    }

    fun validateRazorpayKey(keyId: String, isProductionRelease: Boolean): ValidationResult {
        val trimmed = keyId.trim()
        if (trimmed.isBlank()) {
            return ValidationResult.Invalid("Razorpay Key ID cannot be blank")
        }

        if (isProductionRelease) {
            if (trimmed.startsWith("rzp_test_", ignoreCase = true) || trimmed.contains("placeholder", ignoreCase = true)) {
                return ValidationResult.Invalid("Production release cannot use test or placeholder Razorpay keys")
            }
            if (!trimmed.startsWith("rzp_live_", ignoreCase = true)) {
                return ValidationResult.Invalid("Production release must use a valid live Razorpay key starting with 'rzp_live_'")
            }
        }

        return ValidationResult.Valid
    }
}
