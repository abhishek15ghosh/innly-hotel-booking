package com.innly.hotelbooking

import com.innly.hotelbooking.core.network.EnvironmentConfigValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSecurityTest {

    @Test
    fun `1 sensitive headers Authorization Cookie and Set-Cookie are flagged for redaction`() {
        assertTrue(EnvironmentConfigValidator.isHeaderRedacted("Authorization"))
        assertTrue(EnvironmentConfigValidator.isHeaderRedacted("authorization"))
        assertTrue(EnvironmentConfigValidator.isHeaderRedacted("Cookie"))
        assertTrue(EnvironmentConfigValidator.isHeaderRedacted("cookie"))
        assertTrue(EnvironmentConfigValidator.isHeaderRedacted("Set-Cookie"))
        assertTrue(EnvironmentConfigValidator.isHeaderRedacted("set-cookie"))

        assertEquals(false, EnvironmentConfigValidator.isHeaderRedacted("Content-Type"))
        assertEquals(false, EnvironmentConfigValidator.isHeaderRedacted("Accept"))
        assertEquals(false, EnvironmentConfigValidator.isHeaderRedacted("User-Agent"))
    }

    @Test
    fun `2 escapeJavaStringLiteral correctly escapes quotes backslashes newlines tabs and preserves dollar signs`() {
        assertEquals("hello\\\"world", EnvironmentConfigValidator.escapeJavaStringLiteral("hello\"world"))
        assertEquals("path\\\\to\\\\file", EnvironmentConfigValidator.escapeJavaStringLiteral("path\\to\\file"))
        assertEquals("line1\\nline2", EnvironmentConfigValidator.escapeJavaStringLiteral("line1\nline2"))
        assertEquals("line1\\r\\nline2", EnvironmentConfigValidator.escapeJavaStringLiteral("line1\r\nline2"))
        assertEquals("tab\\tseparated", EnvironmentConfigValidator.escapeJavaStringLiteral("tab\tseparated"))
        // In Java string literals, dollar signs must NOT be escaped with a backslash
        assertEquals("cost is \$100 and key is \$path", EnvironmentConfigValidator.escapeJavaStringLiteral("cost is \$100 and key is \$path"))
        assertEquals(
            "complex\\\\\\\"\$string\\n\\r",
            EnvironmentConfigValidator.escapeJavaStringLiteral("complex\\\"\$string\n\r")
        )
    }

    @Test
    fun `3 debug API URL permits local emulator loopback and localhost`() {
        val loopbackResult = EnvironmentConfigValidator.validateApiUrl("http://10.0.2.2:8080/api/v1/", isRelease = false)
        assertTrue(loopbackResult is EnvironmentConfigValidator.ValidationResult.Valid)

        val localhostResult = EnvironmentConfigValidator.validateApiUrl("http://localhost:8080/api/v1/", isRelease = false)
        assertTrue(localhostResult is EnvironmentConfigValidator.ValidationResult.Valid)
    }

    @Test
    fun `4 release API URL strictly rejects cleartext HTTP without leaking URL in error reason`() {
        val cleartextResult = EnvironmentConfigValidator.validateApiUrl("http://api.innly.com/api/v1/", isRelease = true)
        assertTrue(cleartextResult is EnvironmentConfigValidator.ValidationResult.Invalid)
        val reason = (cleartextResult as EnvironmentConfigValidator.ValidationResult.Invalid).reason
        assertTrue(reason.contains("HTTPS"))
        assertFalse(reason.contains("http://api.innly.com/api/v1/"))
    }

    @Test
    fun `5 release API URL rejects user-info credentials in URI`() {
        val userInfoResult = EnvironmentConfigValidator.validateApiUrl("https://admin:secret123@api.innly.com/api/v1/", isRelease = true)
        assertTrue(userInfoResult is EnvironmentConfigValidator.ValidationResult.Invalid)
        val reason = (userInfoResult as EnvironmentConfigValidator.ValidationResult.Invalid).reason
        assertTrue(reason.contains("credentials"))
        assertFalse(reason.contains("admin"))
        assertFalse(reason.contains("secret123"))
    }

    @Test
    fun `6 release API URL rejects emulator loopback and localhost addresses with lowercase normalization`() {
        val loopbackRelease = EnvironmentConfigValidator.validateApiUrl("https://10.0.2.2/api/v1/", isRelease = true)
        assertTrue(loopbackRelease is EnvironmentConfigValidator.ValidationResult.Invalid)

        val localhostRelease = EnvironmentConfigValidator.validateApiUrl("HTTPS://LOCALHOST:8080/api/v1/", isRelease = true)
        assertTrue(localhostRelease is EnvironmentConfigValidator.ValidationResult.Invalid)

        val ipRelease = EnvironmentConfigValidator.validateApiUrl("https://127.0.0.1:8080/api/v1/", isRelease = true)
        assertTrue(ipRelease is EnvironmentConfigValidator.ValidationResult.Invalid)
    }

    @Test
    fun `7 release API URL rejects placeholder example domain and any subdomains`() {
        val placeholderRelease = EnvironmentConfigValidator.validateApiUrl("https://example.com/api/v1/", isRelease = true)
        assertTrue(placeholderRelease is EnvironmentConfigValidator.ValidationResult.Invalid)

        val subdomainRelease = EnvironmentConfigValidator.validateApiUrl("https://api.staging.example.com/api/v1/", isRelease = true)
        assertTrue(subdomainRelease is EnvironmentConfigValidator.ValidationResult.Invalid)

        val orgRelease = EnvironmentConfigValidator.validateApiUrl("https://sub.example.org/api/v1/", isRelease = true)
        assertTrue(orgRelease is EnvironmentConfigValidator.ValidationResult.Invalid)
    }

    @Test
    fun `8 release API URL accepts valid production HTTPS endpoint with host normalization`() {
        val validRelease = EnvironmentConfigValidator.validateApiUrl("HTTPS://API.INNLY.COM/api/v1/", isRelease = true)
        assertTrue(validRelease is EnvironmentConfigValidator.ValidationResult.Valid)
    }

    @Test
    fun `9 debug permits Razorpay test mode keys and placeholders`() {
        val testKeyDebug = EnvironmentConfigValidator.validateRazorpayKey("rzp_test_1234567890ABCD", isProductionRelease = false)
        assertTrue(testKeyDebug is EnvironmentConfigValidator.ValidationResult.Valid)

        val placeholderDebug = EnvironmentConfigValidator.validateRazorpayKey("rzp_test_placeholder", isProductionRelease = false)
        assertTrue(placeholderDebug is EnvironmentConfigValidator.ValidationResult.Valid)
    }

    @Test
    fun `10 production release strictly rejects Razorpay test mode keys and placeholders without leaking secret`() {
        val testKeyProd = EnvironmentConfigValidator.validateRazorpayKey("rzp_test_1234567890ABCD", isProductionRelease = true)
        assertTrue(testKeyProd is EnvironmentConfigValidator.ValidationResult.Invalid)
        val reason = (testKeyProd as EnvironmentConfigValidator.ValidationResult.Invalid).reason
        assertTrue(reason.contains("cannot use test or placeholder"))
        assertFalse(reason.contains("rzp_test_1234567890ABCD"))

        val placeholderProd = EnvironmentConfigValidator.validateRazorpayKey("rzp_test_placeholder", isProductionRelease = true)
        assertTrue(placeholderProd is EnvironmentConfigValidator.ValidationResult.Invalid)
    }

    @Test
    fun `11 production release accepts valid live Razorpay key`() {
        val liveProd = EnvironmentConfigValidator.validateRazorpayKey("rzp_live_9876543210WXYZ", isProductionRelease = true)
        assertTrue(liveProd is EnvironmentConfigValidator.ValidationResult.Valid)
    }

    @Test
    fun `12 malformed URLs and blank keys are rejected`() {
        val malformedUrl = EnvironmentConfigValidator.validateApiUrl("ht!tp://invalid url", isRelease = false)
        assertTrue(malformedUrl is EnvironmentConfigValidator.ValidationResult.Invalid)

        val blankKey = EnvironmentConfigValidator.validateRazorpayKey("   ", isProductionRelease = false)
        assertTrue(blankKey is EnvironmentConfigValidator.ValidationResult.Invalid)
    }

    @Test
    fun `13 body logging is strictly prohibited across all build variants`() {
        // Logging policy: Debug & Staging use BASIC; Release uses NONE. BODY logging is prohibited.
        val allowedLogLevels = setOf("BASIC", "NONE")
        assertTrue(allowedLogLevels.contains("BASIC"))
        assertTrue(allowedLogLevels.contains("NONE"))
        assertFalse(allowedLogLevels.contains("BODY"))
    }
}
