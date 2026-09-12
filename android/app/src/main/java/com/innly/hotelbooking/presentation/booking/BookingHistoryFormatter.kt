package com.innly.hotelbooking.presentation.booking

import com.innly.hotelbooking.core.ui.PresentationFormatters
import com.innly.hotelbooking.domain.model.Booking
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

enum class RefundPresentationType {
    PROCESSED,
    PENDING,
    FAILED,
    CANCELLED_NO_REFUND,
    POLICY_INFO,
    NEUTRAL_FALLBACK,
}

data class RefundDisplayInfo(
    val message: String,
    val type: RefundPresentationType,
)

enum class BookingStatusType {
    CONFIRMED,
    UPCOMING,
    CURRENTLY_STAYING,
    COMPLETED,
    PAYMENT_PENDING,
    CANCELLATION_PENDING,
    CANCELLED,
    UNKNOWN,
}

enum class BookingDisplayCategory {
    UPCOMING,
    CURRENTLY_STAYING,
    PAYMENT_PENDING,
    COMPLETED,
    CANCELLATION_PENDING,
    CANCELLED,
    UNKNOWN,
}

data class BookingStatusDisplayInfo(
    val label: String,
    val type: BookingStatusType,
    val category: BookingDisplayCategory = BookingDisplayCategory.UNKNOWN,
)

object BookingDateProvider {
    val ASIA_KOLKATA: ZoneId = ZoneId.of("Asia/Kolkata")

    fun getToday(clock: Clock = Clock.system(ASIA_KOLKATA)): LocalDate {
        return LocalDate.now(clock)
    }
}

object BookingHistoryFormatter {

    fun formatFriendlyDate(dateStr: String?): String = PresentationFormatters.formatFriendlyDate(dateStr)

    fun formatFriendlyDateRange(
        checkIn: String?,
        checkOut: String?,
        separator: String = " → ",
    ): String = PresentationFormatters.formatFriendlyDateRange(checkIn, checkOut, separator)

    fun parseStayDate(dateStr: String?): LocalDate? = PresentationFormatters.parseIsoDate(dateStr)

    fun getDisplayCategory(
        booking: Booking,
        today: LocalDate = BookingDateProvider.getToday(),
    ): BookingDisplayCategory {
        val cleanStatus = booking.status.trim().lowercase()
        return when (cleanStatus) {
            "cancelled" -> BookingDisplayCategory.CANCELLED
            "cancellation_pending" -> BookingDisplayCategory.CANCELLATION_PENDING
            "payment_pending" -> BookingDisplayCategory.PAYMENT_PENDING
            "completed" -> BookingDisplayCategory.COMPLETED
            "confirmed" -> {
                val checkInDate = parseStayDate(booking.checkIn)
                val checkOutDate = parseStayDate(booking.checkOut)
                if (checkInDate == null || checkOutDate == null || !checkOutDate.isAfter(checkInDate)) {
                    BookingDisplayCategory.UNKNOWN
                } else if (!today.isBefore(checkOutDate)) {
                    // today >= checkOutDate
                    BookingDisplayCategory.COMPLETED
                } else if (!today.isBefore(checkInDate) && today.isBefore(checkOutDate)) {
                    // checkInDate <= today < checkOutDate
                    BookingDisplayCategory.CURRENTLY_STAYING
                } else {
                    // today < checkInDate
                    BookingDisplayCategory.UPCOMING
                }
            }
            else -> BookingDisplayCategory.UNKNOWN
        }
    }

    fun shouldShowCancelAction(
        booking: Booking,
        category: BookingDisplayCategory = getDisplayCategory(booking),
    ): Boolean {
        if (!booking.canCancel) return false
        return when (category) {
            BookingDisplayCategory.UPCOMING,
            BookingDisplayCategory.CURRENTLY_STAYING,
            BookingDisplayCategory.PAYMENT_PENDING -> true
            BookingDisplayCategory.COMPLETED,
            BookingDisplayCategory.CANCELLED,
            BookingDisplayCategory.CANCELLATION_PENDING,
            BookingDisplayCategory.UNKNOWN -> false
        }
    }

    fun filterBookings(
        list: List<Booking>,
        tab: BookingFilterTab,
        today: LocalDate = BookingDateProvider.getToday(),
    ): List<Booking> {
        return when (tab) {
            BookingFilterTab.ALL -> list
            BookingFilterTab.ACTIVE -> list.filter {
                val cat = getDisplayCategory(it, today)
                cat == BookingDisplayCategory.UPCOMING ||
                        cat == BookingDisplayCategory.CURRENTLY_STAYING ||
                        cat == BookingDisplayCategory.PAYMENT_PENDING
            }
            BookingFilterTab.COMPLETED -> list.filter {
                getDisplayCategory(it, today) == BookingDisplayCategory.COMPLETED
            }
            BookingFilterTab.CANCELLED -> list.filter {
                val cat = getDisplayCategory(it, today)
                cat == BookingDisplayCategory.CANCELLED ||
                        cat == BookingDisplayCategory.CANCELLATION_PENDING
            }
        }
    }

    fun formatCompactBookingId(id: String): String {
        val trimmed = id.trim()
        if (trimmed.length > 16) {
            return "${trimmed.take(8)}…${trimmed.takeLast(4)}"
        }
        return trimmed
    }

    fun isCancellationReasonValid(reason: String): Boolean {
        return reason.trim().length >= 3
    }

    fun mapRefundStatus(
        refundStatus: String?,
        bookingStatus: String,
        displayMessage: String?,
        canCancel: Boolean,
        cancellationDeadline: String?,
    ): RefundDisplayInfo? {
        val cleanRefund = refundStatus?.trim()?.lowercase()
        val cleanBooking = bookingStatus.trim().lowercase()

        return when {
            cleanRefund == "processed" -> {
                RefundDisplayInfo("Refund completed", RefundPresentationType.PROCESSED)
            }
            cleanRefund == "pending" || cleanBooking == "cancellation_pending" -> {
                RefundDisplayInfo("Refund processing", RefundPresentationType.PENDING)
            }
            cleanRefund == "failed" -> {
                RefundDisplayInfo("Refund failed. Please contact support for assistance.", RefundPresentationType.FAILED)
            }
            cleanBooking == "cancelled" && cleanRefund == null -> {
                val text = if (!displayMessage.isNullOrBlank()) displayMessage.trim() else "Booking cancelled"
                RefundDisplayInfo(text, RefundPresentationType.CANCELLED_NO_REFUND)
            }
            cleanBooking == "confirmed" && canCancel && !cancellationDeadline.isNullOrBlank() -> {
                RefundDisplayInfo("Free cancellation until ${formatFriendlyDate(cancellationDeadline)}", RefundPresentationType.POLICY_INFO)
            }
            !cleanRefund.isNullOrBlank() -> {
                RefundDisplayInfo("Refund status: $refundStatus", RefundPresentationType.NEUTRAL_FALLBACK)
            }
            else -> null
        }
    }

    fun mapBookingStatus(
        status: String,
        checkIn: String? = null,
        checkOut: String? = null,
        today: LocalDate = BookingDateProvider.getToday(),
    ): BookingStatusDisplayInfo {
        val cleanStatus = status.trim().lowercase()
        return when (cleanStatus) {
            "cancelled" -> BookingStatusDisplayInfo("Cancelled", BookingStatusType.CANCELLED, BookingDisplayCategory.CANCELLED)
            "cancellation_pending" -> BookingStatusDisplayInfo("Cancelling", BookingStatusType.CANCELLATION_PENDING, BookingDisplayCategory.CANCELLATION_PENDING)
            "payment_pending" -> BookingStatusDisplayInfo("Payment Pending", BookingStatusType.PAYMENT_PENDING, BookingDisplayCategory.PAYMENT_PENDING)
            "completed" -> BookingStatusDisplayInfo("Completed", BookingStatusType.COMPLETED, BookingDisplayCategory.COMPLETED)
            "confirmed" -> {
                val checkInDate = parseStayDate(checkIn)
                val checkOutDate = parseStayDate(checkOut)
                if (checkInDate == null || checkOutDate == null || !checkOutDate.isAfter(checkInDate)) {
                    BookingStatusDisplayInfo("Confirmed", BookingStatusType.CONFIRMED, BookingDisplayCategory.UNKNOWN)
                } else if (!today.isBefore(checkOutDate)) {
                    BookingStatusDisplayInfo("Completed", BookingStatusType.COMPLETED, BookingDisplayCategory.COMPLETED)
                } else if (!today.isBefore(checkInDate) && today.isBefore(checkOutDate)) {
                    BookingStatusDisplayInfo("Currently Staying", BookingStatusType.CURRENTLY_STAYING, BookingDisplayCategory.CURRENTLY_STAYING)
                } else {
                    BookingStatusDisplayInfo("Confirmed", BookingStatusType.CONFIRMED, BookingDisplayCategory.UPCOMING)
                }
            }
            else -> {
                val formattedLabel = if (status.isNotBlank()) {
                    status.replaceFirstChar { it.uppercase() }
                } else {
                    "Unknown"
                }
                BookingStatusDisplayInfo(formattedLabel, BookingStatusType.UNKNOWN, BookingDisplayCategory.UNKNOWN)
            }
        }
    }

    fun mapBookingStatus(
        booking: Booking,
        today: LocalDate = BookingDateProvider.getToday(),
    ): BookingStatusDisplayInfo {
        return mapBookingStatus(booking.status, booking.checkIn, booking.checkOut, today)
    }
}
