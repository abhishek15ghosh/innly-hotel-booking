package com.innly.hotelbooking

import com.innly.hotelbooking.domain.model.Booking
import com.innly.hotelbooking.presentation.booking.BookingDateProvider
import com.innly.hotelbooking.presentation.booking.BookingDisplayCategory
import com.innly.hotelbooking.presentation.booking.BookingFilterTab
import com.innly.hotelbooking.presentation.booking.BookingHistoryFormatter
import com.innly.hotelbooking.presentation.booking.BookingStatusType
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingHistoryFormatterTest {

    private val referenceToday = LocalDate.of(2026, 8, 23)

    @Test
    fun `1 upcoming confirmed stay is classified as UPCOMING and filtered into ACTIVE and ALL`() {
        val booking = Booking(
            id = "b-up",
            hotelName = "Grand Lotus Palace",
            roomName = "Deluxe Room",
            checkIn = "2026-08-25",
            checkOut = "2026-08-28",
            status = "confirmed",
            amount = 12000,
            currency = "INR",
            canCancel = true,
        )

        val category = BookingHistoryFormatter.getDisplayCategory(booking, referenceToday)
        assertEquals(BookingDisplayCategory.UPCOMING, category)

        val list = listOf(booking)
        assertTrue(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.ALL, referenceToday).contains(booking))
        assertTrue(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.ACTIVE, referenceToday).contains(booking))
        assertFalse(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.COMPLETED, referenceToday).contains(booking))
        assertFalse(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.CANCELLED, referenceToday).contains(booking))

        val statusInfo = BookingHistoryFormatter.mapBookingStatus(booking, referenceToday)
        assertEquals("Confirmed", statusInfo.label)
        assertEquals(BookingStatusType.CONFIRMED, statusInfo.type)
        assertEquals(BookingDisplayCategory.UPCOMING, statusInfo.category)
    }

    @Test
    fun `2 currently staying guest is classified as CURRENTLY_STAYING and filtered into ACTIVE and ALL`() {
        val booking = Booking(
            id = "b-staying",
            hotelName = "Heritage Haveli",
            roomName = "Royal Suite",
            checkIn = "2026-08-22",
            checkOut = "2026-08-25",
            status = "confirmed",
            amount = 18000,
            currency = "INR",
            canCancel = false,
        )

        val category = BookingHistoryFormatter.getDisplayCategory(booking, referenceToday)
        assertEquals(BookingDisplayCategory.CURRENTLY_STAYING, category)

        val list = listOf(booking)
        assertTrue(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.ALL, referenceToday).contains(booking))
        assertTrue(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.ACTIVE, referenceToday).contains(booking))
        assertFalse(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.COMPLETED, referenceToday).contains(booking))
        assertFalse(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.CANCELLED, referenceToday).contains(booking))

        val statusInfo = BookingHistoryFormatter.mapBookingStatus(booking, referenceToday)
        assertEquals("Currently Staying", statusInfo.label)
        assertEquals(BookingStatusType.CURRENTLY_STAYING, statusInfo.type)
        assertEquals(BookingDisplayCategory.CURRENTLY_STAYING, statusInfo.category)
    }

    @Test
    fun `3 checkout date equals today is classified as COMPLETED and filtered into COMPLETED and ALL`() {
        val booking = Booking(
            id = "b-today-checkout",
            hotelName = "Palm Resort",
            roomName = "Garden Villa",
            checkIn = "2026-08-20",
            checkOut = "2026-08-23", // Checkout is today
            status = "confirmed",
            amount = 9500,
            currency = "INR",
            canCancel = false,
        )

        val category = BookingHistoryFormatter.getDisplayCategory(booking, referenceToday)
        assertEquals(BookingDisplayCategory.COMPLETED, category)

        val list = listOf(booking)
        assertTrue(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.ALL, referenceToday).contains(booking))
        assertFalse(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.ACTIVE, referenceToday).contains(booking))
        assertTrue(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.COMPLETED, referenceToday).contains(booking))
        assertFalse(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.CANCELLED, referenceToday).contains(booking))

        val statusInfo = BookingHistoryFormatter.mapBookingStatus(booking, referenceToday)
        assertEquals("Completed", statusInfo.label)
        assertEquals(BookingStatusType.COMPLETED, statusInfo.type)
        assertEquals(BookingDisplayCategory.COMPLETED, statusInfo.category)
    }

    @Test
    fun `4 past confirmed stay is classified as COMPLETED and filtered into COMPLETED and ALL`() {
        val booking = Booking(
            id = "b-past",
            hotelName = "Grand Lotus Palace",
            roomName = "Premier Room",
            checkIn = "2026-08-16",
            checkOut = "2026-08-18",
            status = "confirmed",
            amount = 11800,
            currency = "INR",
            canCancel = false,
        )

        val category = BookingHistoryFormatter.getDisplayCategory(booking, referenceToday)
        assertEquals(BookingDisplayCategory.COMPLETED, category)

        val list = listOf(booking)
        assertTrue(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.ALL, referenceToday).contains(booking))
        assertFalse(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.ACTIVE, referenceToday).contains(booking))
        assertTrue(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.COMPLETED, referenceToday).contains(booking))
        assertFalse(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.CANCELLED, referenceToday).contains(booking))
    }

    @Test
    fun `5 backend completed status is classified as COMPLETED regardless of stay date parsing`() {
        val booking = Booking(
            id = "b-backend-completed",
            hotelName = "Grand Lotus Palace",
            roomName = "Suite",
            checkIn = "corrupted-date",
            checkOut = "corrupted-date",
            status = "completed",
            amount = 15000,
            currency = "INR",
            canCancel = false,
        )

        val category = BookingHistoryFormatter.getDisplayCategory(booking, referenceToday)
        assertEquals(BookingDisplayCategory.COMPLETED, category)

        val list = listOf(booking)
        assertTrue(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.COMPLETED, referenceToday).contains(booking))
    }

    @Test
    fun `6 payment pending status is classified as PAYMENT_PENDING and filtered into ACTIVE and ALL`() {
        val booking = Booking(
            id = "b-pending",
            hotelName = "Grand Lotus Palace",
            roomName = "Executive Room",
            checkIn = "2026-08-25",
            checkOut = "2026-08-27",
            status = "payment_pending",
            amount = 8000,
            currency = "INR",
            canCancel = true,
        )

        val category = BookingHistoryFormatter.getDisplayCategory(booking, referenceToday)
        assertEquals(BookingDisplayCategory.PAYMENT_PENDING, category)

        val list = listOf(booking)
        assertTrue(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.ACTIVE, referenceToday).contains(booking))
        assertFalse(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.COMPLETED, referenceToday).contains(booking))
        assertFalse(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.CANCELLED, referenceToday).contains(booking))

        val statusInfo = BookingHistoryFormatter.mapBookingStatus(booking, referenceToday)
        assertEquals("Payment Pending", statusInfo.label)
        assertEquals(BookingStatusType.PAYMENT_PENDING, statusInfo.type)
    }

    @Test
    fun `7 cancellation pending status is classified as CANCELLATION_PENDING and filtered into CANCELLED and ALL`() {
        val booking = Booking(
            id = "b-cancelling",
            hotelName = "Grand Lotus Palace",
            roomName = "Standard Room",
            checkIn = "2026-08-25",
            checkOut = "2026-08-27",
            status = "cancellation_pending",
            amount = 6000,
            currency = "INR",
            canCancel = false,
        )

        val category = BookingHistoryFormatter.getDisplayCategory(booking, referenceToday)
        assertEquals(BookingDisplayCategory.CANCELLATION_PENDING, category)

        val list = listOf(booking)
        assertTrue(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.CANCELLED, referenceToday).contains(booking))
        assertFalse(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.ACTIVE, referenceToday).contains(booking))
        assertFalse(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.COMPLETED, referenceToday).contains(booking))
    }

    @Test
    fun `8 cancelled past booking is classified as CANCELLED and filtered into CANCELLED and ALL (never COMPLETED)`() {
        val booking = Booking(
            id = "b-cancelled-past",
            hotelName = "Grand Lotus Palace",
            roomName = "Economy Room",
            checkIn = "2026-08-10",
            checkOut = "2026-08-12",
            status = "cancelled",
            amount = 5000,
            currency = "INR",
            canCancel = false,
        )

        val category = BookingHistoryFormatter.getDisplayCategory(booking, referenceToday)
        assertEquals(BookingDisplayCategory.CANCELLED, category)

        val list = listOf(booking)
        assertTrue(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.CANCELLED, referenceToday).contains(booking))
        assertFalse(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.COMPLETED, referenceToday).contains(booking))
        assertFalse(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.ACTIVE, referenceToday).contains(booking))
    }

    @Test
    fun `9 unknown status is classified as UNKNOWN and filtered only into ALL`() {
        val booking = Booking(
            id = "b-unknown",
            hotelName = "Grand Lotus Palace",
            roomName = "Villa",
            checkIn = "2026-08-25",
            checkOut = "2026-08-27",
            status = "archived",
            amount = 7000,
            currency = "INR",
            canCancel = false,
        )

        val category = BookingHistoryFormatter.getDisplayCategory(booking, referenceToday)
        assertEquals(BookingDisplayCategory.UNKNOWN, category)

        val list = listOf(booking)
        assertTrue(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.ALL, referenceToday).contains(booking))
        assertFalse(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.ACTIVE, referenceToday).contains(booking))
        assertFalse(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.COMPLETED, referenceToday).contains(booking))
        assertFalse(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.CANCELLED, referenceToday).contains(booking))

        val statusInfo = BookingHistoryFormatter.mapBookingStatus(booking, referenceToday)
        assertEquals("Archived", statusInfo.label)
        assertEquals(BookingStatusType.UNKNOWN, statusInfo.type)
        assertEquals(BookingDisplayCategory.UNKNOWN, statusInfo.category)
    }

    @Test
    fun `10 malformed date string is rejected safely without throwing and classified as UNKNOWN`() {
        assertNull(BookingHistoryFormatter.parseStayDate("2026-08-16Garbage"))
        assertNull(BookingHistoryFormatter.parseStayDate("invalid-date"))
        assertNull(BookingHistoryFormatter.parseStayDate("2026-02-31"))

        val booking = Booking(
            id = "b-malformed",
            hotelName = "Hotel",
            roomName = "Room",
            checkIn = "invalid-date",
            checkOut = "2026-08-28",
            status = "confirmed",
            amount = 1000,
            currency = "INR",
            canCancel = true,
        )

        val category = BookingHistoryFormatter.getDisplayCategory(booking, referenceToday)
        assertEquals(BookingDisplayCategory.UNKNOWN, category)

        val list = listOf(booking)
        assertTrue(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.ALL, referenceToday).contains(booking))
        assertFalse(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.ACTIVE, referenceToday).contains(booking))
        assertFalse(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.COMPLETED, referenceToday).contains(booking))
        assertFalse(BookingHistoryFormatter.filterBookings(list, BookingFilterTab.CANCELLED, referenceToday).contains(booking))
    }

    @Test
    fun `11 blank or null date strings are rejected safely and classified as UNKNOWN`() {
        assertNull(BookingHistoryFormatter.parseStayDate(""))
        assertNull(BookingHistoryFormatter.parseStayDate("   "))
        assertNull(BookingHistoryFormatter.parseStayDate(null))

        val booking = Booking(
            id = "b-blank",
            hotelName = "Hotel",
            roomName = "Room",
            checkIn = "",
            checkOut = "2026-08-28",
            status = "confirmed",
            amount = 1000,
            currency = "INR",
            canCancel = true,
        )

        assertEquals(BookingDisplayCategory.UNKNOWN, BookingHistoryFormatter.getDisplayCategory(booking, referenceToday))
    }

    @Test
    fun `12 logically invalid date range where checkIn greater or equal to checkOut is classified as UNKNOWN`() {
        val sameDayBooking = Booking(
            id = "b-sameday",
            hotelName = "Hotel",
            roomName = "Room",
            checkIn = "2026-08-25",
            checkOut = "2026-08-25",
            status = "confirmed",
            amount = 1000,
            currency = "INR",
            canCancel = true,
        )
        val invertedBooking = Booking(
            id = "b-inverted",
            hotelName = "Hotel",
            roomName = "Room",
            checkIn = "2026-08-28",
            checkOut = "2026-08-25",
            status = "confirmed",
            amount = 1000,
            currency = "INR",
            canCancel = true,
        )

        assertEquals(BookingDisplayCategory.UNKNOWN, BookingHistoryFormatter.getDisplayCategory(sameDayBooking, referenceToday))
        assertEquals(BookingDisplayCategory.UNKNOWN, BookingHistoryFormatter.getDisplayCategory(invertedBooking, referenceToday))
    }

    @Test
    fun `13 ISO timestamp date formats parse accurately`() {
        val parsedUtc = BookingHistoryFormatter.parseStayDate("2026-08-25T14:30:00.000Z")
        assertEquals(LocalDate.of(2026, 8, 25), parsedUtc)

        val parsedNoMillis = BookingHistoryFormatter.parseStayDate("2026-08-25T00:00:00")
        assertEquals(LocalDate.of(2026, 8, 25), parsedNoMillis)
    }

    @Test
    fun `14 space-separated timestamp date formats parse accurately`() {
        val parsedSpace = BookingHistoryFormatter.parseStayDate("2026-08-25 14:30:00")
        assertEquals(LocalDate.of(2026, 8, 25), parsedSpace)
    }

    @Test
    fun `15 Asia-Kolkata midnight boundary test with Clock transitions CURRENTLY_STAYING to COMPLETED`() {
        val zone = BookingDateProvider.ASIA_KOLKATA

        // Instant corresponding to 2026-08-23 23:59:59 IST (UTC 18:29:59)
        val beforeMidnightInstant = Instant.parse("2026-08-23T18:29:59Z")
        val clockBeforeMidnight = Clock.fixed(beforeMidnightInstant, zone)
        val todayBeforeMidnight = BookingDateProvider.getToday(clockBeforeMidnight)
        assertEquals(LocalDate.of(2026, 8, 23), todayBeforeMidnight)

        // Instant corresponding to 2026-08-24 00:00:00 IST (UTC 18:30:00)
        val afterMidnightInstant = Instant.parse("2026-08-23T18:30:00Z")
        val clockAfterMidnight = Clock.fixed(afterMidnightInstant, zone)
        val todayAfterMidnight = BookingDateProvider.getToday(clockAfterMidnight)
        assertEquals(LocalDate.of(2026, 8, 24), todayAfterMidnight)

        // Stay with checkOut on 2026-08-24
        val booking = Booking(
            id = "b-boundary",
            hotelName = "Hotel",
            roomName = "Room",
            checkIn = "2026-08-20",
            checkOut = "2026-08-24",
            status = "confirmed",
            amount = 10000,
            currency = "INR",
            canCancel = false,
        )

        // Before midnight IST (date is 2026-08-23): guest is currently staying
        assertEquals(BookingDisplayCategory.CURRENTLY_STAYING, BookingHistoryFormatter.getDisplayCategory(booking, todayBeforeMidnight))

        // After midnight IST (date is 2026-08-24): stay is completed (checkout date reached)
        assertEquals(BookingDisplayCategory.COMPLETED, BookingHistoryFormatter.getDisplayCategory(booking, todayAfterMidnight))
    }

    @Test
    fun `16 cancellation action button is visible only when canCancel is true AND category is active`() {
        val upcomingCanCancel = Booking("1", "H", "R", "2026-08-25", "2026-08-28", "confirmed", 1000, "INR", true)
        val upcomingCannotCancel = Booking("2", "H", "R", "2026-08-25", "2026-08-28", "confirmed", 1000, "INR", false)
        val stayingCanCancel = Booking("3", "H", "R", "2026-08-22", "2026-08-25", "confirmed", 1000, "INR", true)
        val completedCanCancel = Booking("4", "H", "R", "2026-08-16", "2026-08-18", "confirmed", 1000, "INR", true)
        val paymentPendingCanCancel = Booking("5", "H", "R", "2026-08-25", "2026-08-28", "payment_pending", 1000, "INR", true)
        val cancelledCanCancel = Booking("6", "H", "R", "2026-08-25", "2026-08-28", "cancelled", 1000, "INR", true)
        val unknownCanCancel = Booking("7", "H", "R", "2026-08-25", "2026-08-28", "archived", 1000, "INR", true)

        assertTrue(BookingHistoryFormatter.shouldShowCancelAction(upcomingCanCancel, BookingDisplayCategory.UPCOMING))
        assertFalse(BookingHistoryFormatter.shouldShowCancelAction(upcomingCannotCancel, BookingDisplayCategory.UPCOMING))
        assertTrue(BookingHistoryFormatter.shouldShowCancelAction(stayingCanCancel, BookingDisplayCategory.CURRENTLY_STAYING))
        assertFalse(BookingHistoryFormatter.shouldShowCancelAction(completedCanCancel, BookingDisplayCategory.COMPLETED))
        assertTrue(BookingHistoryFormatter.shouldShowCancelAction(paymentPendingCanCancel, BookingDisplayCategory.PAYMENT_PENDING))
        assertFalse(BookingHistoryFormatter.shouldShowCancelAction(cancelledCanCancel, BookingDisplayCategory.CANCELLED))
        assertFalse(BookingHistoryFormatter.shouldShowCancelAction(unknownCanCancel, BookingDisplayCategory.UNKNOWN))
    }

    @Test
    fun `17 pairwise disjoint and mutual exclusivity invariants hold across all categories`() {
        val bUpcoming = Booking("b-1", "H", "R", "2026-08-25", "2026-08-28", "confirmed", 1000, "INR", true)
        val bStaying = Booking("b-2", "H", "R", "2026-08-22", "2026-08-25", "confirmed", 2000, "INR", true)
        val bPayPending = Booking("b-3", "H", "R", "2026-08-26", "2026-08-27", "payment_pending", 3000, "INR", true)
        val bCompleted = Booking("b-4", "H", "R", "2026-08-16", "2026-08-18", "confirmed", 4000, "INR", false)
        val bCancelling = Booking("b-5", "H", "R", "2026-08-25", "2026-08-28", "cancellation_pending", 5000, "INR", false)
        val bCancelled = Booking("b-6", "H", "R", "2026-08-10", "2026-08-12", "cancelled", 6000, "INR", false)
        val bUnknown = Booking("b-7", "H", "R", "invalid", "invalid", "confirmed", 7000, "INR", false)

        val list = listOf(bUpcoming, bStaying, bPayPending, bCompleted, bCancelling, bCancelled, bUnknown)

        val all = BookingHistoryFormatter.filterBookings(list, BookingFilterTab.ALL, referenceToday)
        val active = BookingHistoryFormatter.filterBookings(list, BookingFilterTab.ACTIVE, referenceToday)
        val completed = BookingHistoryFormatter.filterBookings(list, BookingFilterTab.COMPLETED, referenceToday)
        val cancelled = BookingHistoryFormatter.filterBookings(list, BookingFilterTab.CANCELLED, referenceToday)

        // 1. All contains every item
        assertEquals(7, all.size)

        // 2. Active, Completed, Cancelled are pairwise disjoint
        val activeIds = active.map { it.id }.toSet()
        val completedIds = completed.map { it.id }.toSet()
        val cancelledIds = cancelled.map { it.id }.toSet()

        assertTrue(activeIds.intersect(completedIds).isEmpty())
        assertTrue(activeIds.intersect(cancelledIds).isEmpty())
        assertTrue(completedIds.intersect(cancelledIds).isEmpty())

        // 3. Known bookings appear in exactly one non-All tab
        assertEquals(setOf("b-1", "b-2", "b-3"), activeIds)
        assertEquals(setOf("b-4"), completedIds)
        assertEquals(setOf("b-5", "b-6"), cancelledIds)

        // 4. UNKNOWN booking appears only in All
        assertFalse(activeIds.contains("b-7"))
        assertFalse(completedIds.contains("b-7"))
        assertFalse(cancelledIds.contains("b-7"))

        // 5. Invariant when UNKNOWN exists: Active + Completed + Cancelled < All
        assertEquals(3 + 1 + 2, active.size + completed.size + cancelled.size)
        assertTrue((active.size + completed.size + cancelled.size) < all.size)

        // 6. Invariant when no UNKNOWN exists: Active + Completed + Cancelled == All
        val listWithoutUnknown = list.filter { it.id != "b-7" }
        val allKnown = BookingHistoryFormatter.filterBookings(listWithoutUnknown, BookingFilterTab.ALL, referenceToday)
        val activeKnown = BookingHistoryFormatter.filterBookings(listWithoutUnknown, BookingFilterTab.ACTIVE, referenceToday)
        val completedKnown = BookingHistoryFormatter.filterBookings(listWithoutUnknown, BookingFilterTab.COMPLETED, referenceToday)
        val cancelledKnown = BookingHistoryFormatter.filterBookings(listWithoutUnknown, BookingFilterTab.CANCELLED, referenceToday)
        assertEquals(allKnown.size, activeKnown.size + completedKnown.size + cancelledKnown.size)
    }
}
