package com.innly.hotelbooking.core.ui

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Central neutral presentation utility for user-facing formatting:
 * - Indian and international currency display formatting.
 * - Friendly date and date-range formatting with safe fallbacks for ISO strings.
 */
object PresentationFormatters {

    private val DATE_FORMAT_REGEX = Regex("""^(\d{4}-\d{2}-\d{2})(?:[T\s].*)?$""")
    private val FRIENDLY_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    /**
     * Formats an integer amount into an Indian Rupee string with the ₹ symbol and Indian grouping.
     */
    fun formatInr(amount: Int): String = formatCurrency("INR", amount)

    /**
     * Pure Kotlin Indian numbering system algorithm (e.g. 125000 -> "1,25,000", 1250000 -> "12,50,000").
     * Guaranteed to produce deterministic Indian grouping across both JVM and Android environments.
     */
    fun formatIndianNumber(amount: Long): String {
        val isNegative = amount < 0
        val s = Math.abs(amount).toString()
        if (s.length <= 3) {
            return if (isNegative) "-$s" else s
        }
        val last3 = s.takeLast(3)
        var rest = s.dropLast(3)
        val groups = mutableListOf<String>()
        while (rest.length > 2) {
            groups.add(rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) {
            groups.add(rest)
        }
        groups.reverse()
        val formatted = groups.joinToString(",") + "," + last3
        return if (isNegative) "-$formatted" else formatted
    }

    /**
     * Formats currency with currency symbol and appropriate grouping:
     * - INR uses Indian grouping system (₹1,25,000)
     * - USD, EUR, GBP, etc. use standard 3-digit international grouping ($125,000)
     */
    fun formatCurrency(currency: String, amount: Int): String {
        val cleanCurrency = currency.trim().uppercase()
        if (cleanCurrency == "INR" || cleanCurrency.isEmpty()) {
            return "₹${formatIndianNumber(amount.toLong())}"
        }
        val symbol = when (cleanCurrency) {
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            else -> "$cleanCurrency "
        }
        val formattedAmount = NumberFormat.getNumberInstance(Locale.US).format(amount)
        return "$symbol$formattedAmount"
    }

    /**
     * Safely extracts and parses an ISO date (yyyy-MM-dd) from a date or timestamp string.
     */
    fun parseIsoDate(dateStr: String?): LocalDate? {
        if (dateStr.isNullOrBlank()) return null
        val trimmed = dateStr.trim()
        val match = DATE_FORMAT_REGEX.matchEntire(trimmed) ?: return null
        val ymd = match.groupValues[1]
        return runCatching {
            LocalDate.parse(ymd, DateTimeFormatter.ISO_LOCAL_DATE)
        }.getOrNull()
    }

    /**
     * Formats an ISO date or ISO date-time string to a friendly format (e.g., "2026-09-09" -> "9 Sep 2026").
     * Safely returns original string on malformed dates, and empty string on null/blank dates.
     */
    fun formatFriendlyDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        val localDate = parseIsoDate(dateStr) ?: return dateStr.trim()
        return runCatching { localDate.format(FRIENDLY_DATE_FORMATTER) }.getOrDefault(dateStr.trim())
    }

    /**
     * Formats a date range into friendly readable text (e.g. "9 Sep 2026 → 12 Sep 2026").
     * Safely handles partial ranges or malformed inputs without exceptions.
     */
    fun formatFriendlyDateRange(
        checkIn: String?,
        checkOut: String?,
        separator: String = " → ",
    ): String {
        val start = formatFriendlyDate(checkIn)
        val end = formatFriendlyDate(checkOut)
        return when {
            start.isNotBlank() && end.isNotBlank() -> "$start$separator$end"
            start.isNotBlank() -> start
            end.isNotBlank() -> end
            else -> ""
        }
    }
}
