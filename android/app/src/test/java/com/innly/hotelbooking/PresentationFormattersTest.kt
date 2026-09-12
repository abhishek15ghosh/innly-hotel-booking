package com.innly.hotelbooking

import com.innly.hotelbooking.core.ui.PresentationFormatters
import com.innly.hotelbooking.presentation.booking.BookingFormValidator
import com.innly.hotelbooking.presentation.booking.BookingHistoryFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests verifying consistent presentation-layer formatting across JVM and Android environments:
 * - Direct tests on PresentationFormatters.
 * - Compatibility delegate verification on BookingFormValidator and BookingHistoryFormatter.
 * - Indian currency grouping (0 -> ₹0, 5900 -> ₹5,900, 12200 -> ₹12,200, 125000 -> ₹1,25,000, 1250000 -> ₹12,50,000).
 * - Standard currency grouping for non-INR (USD, EUR, etc.).
 * - Friendly date formatting (2026-09-09 -> 9 Sep 2026, ISO date-time -> d MMM yyyy).
 * - Safe graceful fallback behavior for null, blank, or malformed inputs without exceptions.
 */
class PresentationFormattersTest {

    @Test
    fun `indian currency formatting conforms exactly to required test vectors`() {
        // Direct PresentationFormatters
        assertEquals("₹0", PresentationFormatters.formatInr(0))
        assertEquals("₹5,900", PresentationFormatters.formatInr(5900))
        assertEquals("₹12,200", PresentationFormatters.formatInr(12200))
        assertEquals("₹1,25,000", PresentationFormatters.formatInr(125000))
        assertEquals("₹12,50,000", PresentationFormatters.formatInr(1250000))

        assertEquals("₹0", PresentationFormatters.formatCurrency("INR", 0))
        assertEquals("₹5,900", PresentationFormatters.formatCurrency("INR", 5900))
        assertEquals("₹12,200", PresentationFormatters.formatCurrency("inr", 12200))
        assertEquals("₹1,25,000", PresentationFormatters.formatCurrency("INR", 125000))
        assertEquals("₹12,50,000", PresentationFormatters.formatCurrency("INR", 1250000))

        // Delegate compatibility on BookingFormValidator
        assertEquals("₹0", BookingFormValidator.formatInr(0))
        assertEquals("₹5,900", BookingFormValidator.formatInr(5900))
        assertEquals("₹12,200", BookingFormValidator.formatInr(12200))
        assertEquals("₹1,25,000", BookingFormValidator.formatInr(125000))
        assertEquals("₹12,50,000", BookingFormValidator.formatInr(1250000))
        assertEquals("₹1,25,000", BookingFormValidator.formatCurrency("INR", 125000))
    }

    @Test
    fun `formatIndianNumber formats edge cases and large numbers properly`() {
        assertEquals("0", PresentationFormatters.formatIndianNumber(0))
        assertEquals("5", PresentationFormatters.formatIndianNumber(5))
        assertEquals("99", PresentationFormatters.formatIndianNumber(99))
        assertEquals("999", PresentationFormatters.formatIndianNumber(999))
        assertEquals("1,000", PresentationFormatters.formatIndianNumber(1000))
        assertEquals("10,000", PresentationFormatters.formatIndianNumber(10000))
        assertEquals("1,00,000", PresentationFormatters.formatIndianNumber(100000))
        assertEquals("10,00,000", PresentationFormatters.formatIndianNumber(1000000))
        assertEquals("1,00,00,000", PresentationFormatters.formatIndianNumber(10000000))
        assertEquals("-1,25,000", PresentationFormatters.formatIndianNumber(-125000))

        // Delegate compatibility
        assertEquals("1,25,000", BookingFormValidator.formatIndianNumber(125000))
    }

    @Test
    fun `non-INR currencies retain standard international grouping`() {
        assertEquals("$5,900", PresentationFormatters.formatCurrency("USD", 5900))
        assertEquals("$125,000", PresentationFormatters.formatCurrency("USD", 125000))
        assertEquals("€125,000", PresentationFormatters.formatCurrency("EUR", 125000))

        // Delegate compatibility
        assertEquals("$125,000", BookingFormValidator.formatCurrency("USD", 125000))
    }

    @Test
    fun `formatFriendlyDate formats ISO dates and ISO date-times correctly`() {
        assertEquals("9 Sep 2026", PresentationFormatters.formatFriendlyDate("2026-09-09"))
        assertEquals("1 Jan 2026", PresentationFormatters.formatFriendlyDate("2026-01-01"))
        assertEquals("31 Dec 2026", PresentationFormatters.formatFriendlyDate("2026-12-31"))

        // ISO date-time with Z or offset or local
        assertEquals("25 Aug 2026", PresentationFormatters.formatFriendlyDate("2026-08-25T14:30:00Z"))
        assertEquals("25 Aug 2026", PresentationFormatters.formatFriendlyDate("2026-08-25T14:30:00+05:30"))
        assertEquals("25 Aug 2026", PresentationFormatters.formatFriendlyDate("2026-08-25T14:30:00"))

        // Delegate compatibility on BookingHistoryFormatter
        assertEquals("9 Sep 2026", BookingHistoryFormatter.formatFriendlyDate("2026-09-09"))
        assertEquals("25 Aug 2026", BookingHistoryFormatter.formatFriendlyDate("2026-08-25T14:30:00Z"))
    }

    @Test
    fun `formatFriendlyDate handles null, blank, and malformed dates safely without crashing`() {
        assertEquals("", PresentationFormatters.formatFriendlyDate(null))
        assertEquals("", PresentationFormatters.formatFriendlyDate(""))
        assertEquals("", PresentationFormatters.formatFriendlyDate("   "))
        assertEquals("not-a-date", PresentationFormatters.formatFriendlyDate("not-a-date"))
        assertEquals("2026/09/09", PresentationFormatters.formatFriendlyDate("2026/09/09"))

        // Delegate compatibility
        assertEquals("", BookingHistoryFormatter.formatFriendlyDate(null))
        assertEquals("not-a-date", BookingHistoryFormatter.formatFriendlyDate("not-a-date"))
    }

    @Test
    fun `formatFriendlyDateRange formats ranges with default and custom separators`() {
        assertEquals(
            "9 Sep 2026 → 12 Sep 2026",
            PresentationFormatters.formatFriendlyDateRange("2026-09-09", "2026-09-12")
        )
        assertEquals(
            "9 Sep 2026 to 12 Sep 2026",
            PresentationFormatters.formatFriendlyDateRange("2026-09-09", "2026-09-12", " to ")
        )
        assertEquals(
            "9 Sep 2026 - 12 Sep 2026",
            PresentationFormatters.formatFriendlyDateRange("2026-09-09", "2026-09-12", " - ")
        )

        // Delegate compatibility
        assertEquals(
            "9 Sep 2026 → 12 Sep 2026",
            BookingHistoryFormatter.formatFriendlyDateRange("2026-09-09", "2026-09-12")
        )
    }

    @Test
    fun `formatFriendlyDateRange handles partial or null dates safely`() {
        assertEquals(
            "9 Sep 2026",
            PresentationFormatters.formatFriendlyDateRange("2026-09-09", null)
        )
        assertEquals(
            "12 Sep 2026",
            PresentationFormatters.formatFriendlyDateRange(null, "2026-09-12")
        )
        assertEquals(
            "",
            PresentationFormatters.formatFriendlyDateRange(null, null)
        )
        assertEquals(
            "invalid-start → 12 Sep 2026",
            PresentationFormatters.formatFriendlyDateRange("invalid-start", "2026-09-12")
        )

        // Delegate compatibility
        assertEquals(
            "9 Sep 2026",
            BookingHistoryFormatter.formatFriendlyDateRange("2026-09-09", null)
        )
    }
}
