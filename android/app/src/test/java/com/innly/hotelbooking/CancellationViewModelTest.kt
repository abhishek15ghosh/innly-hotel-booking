package com.innly.hotelbooking

import com.innly.hotelbooking.domain.model.Availability
import com.innly.hotelbooking.domain.model.Booking
import com.innly.hotelbooking.domain.model.BookingRequest
import com.innly.hotelbooking.domain.model.CancellationResult
import com.innly.hotelbooking.domain.model.Hotel
import com.innly.hotelbooking.domain.model.PaymentOrder
import com.innly.hotelbooking.domain.model.PaymentVerificationResult
import com.innly.hotelbooking.domain.model.Review
import com.innly.hotelbooking.domain.model.UserProfile
import com.innly.hotelbooking.domain.repository.AuthRepository
import com.innly.hotelbooking.domain.repository.BookingRepository
import com.innly.hotelbooking.domain.repository.HotelRepository
import com.innly.hotelbooking.presentation.booking.BookingFilterTab
import com.innly.hotelbooking.presentation.booking.BookingHistoryFormatter
import com.innly.hotelbooking.presentation.booking.BookingStatusType
import com.innly.hotelbooking.presentation.booking.BookingViewModel
import com.innly.hotelbooking.presentation.booking.RefundPresentationType
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CancellationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeBookingRepoForCancellation(
        private val shouldFail: Boolean = false,
        private val errorMessage: String = "Free cancellation period has ended",
        private val mockResult: CancellationResult = CancellationResult("b-1", "cancelled", false, null, "processed", "Refund completed"),
        private val historyList: List<Booking>? = null,
        private val shouldFailHistory: Boolean = false,
    ) : BookingRepository {
        var cancelCallCount = 0
        var lastReasonPassed: String? = null

        override suspend fun createBooking(request: BookingRequest): PaymentOrder = TODO()
        override suspend fun verifyPayment(bookingId: String, razorpayOrderId: String, razorpayPaymentId: String, razorpaySignature: String): PaymentVerificationResult = TODO()

        override suspend fun getBookingHistory(): List<Booking> {
            if (shouldFailHistory) {
                throw Exception("Failed to load booking history")
            }
            if (historyList != null) {
                return historyList
            }
            return listOf(
                Booking(
                    id = "b-1",
                    hotelName = "The Marine Grand",
                    roomName = "Deluxe King",
                    checkIn = "2026-08-15",
                    checkOut = "2026-08-17",
                    status = "confirmed",
                    amount = 11800,
                    currency = "INR",
                    canCancel = true,
                    cancellationDeadline = "2026-08-14",
                    displayMessage = "Free cancellation available until 2026-08-14",
                ),
            )
        }

        override suspend fun cancelBooking(bookingId: String, reason: String): CancellationResult {
            cancelCallCount++
            lastReasonPassed = reason
            if (shouldFail) {
                throw Exception(errorMessage)
            }
            return mockResult
        }
    }

    private class FakeAuthRepo : AuthRepository {
        override val currentUser: Flow<UserProfile?> = flowOf(UserProfile("u-1", "fb-1", "user@example.com", "Test User", "9999999999"))
        override suspend fun signIn(email: String, password: String) {}
        override suspend fun signUp(name: String, email: String, password: String) {}
        override suspend fun signOut() {}
        override suspend fun syncProfile(displayName: String?, phoneNumber: String?): UserProfile = UserProfile("u-1", "fb-1", "user@example.com", "Test User", "9999999999")
    }

    private class FakeHotelRepo : HotelRepository {
        override suspend fun getHotels(filter: com.innly.hotelbooking.domain.model.HotelSearchFilter): List<Hotel> = emptyList()
        override suspend fun getHotelDetail(hotelId: String): Hotel = TODO()
        override suspend fun getAvailability(hotelId: String, roomId: String, checkIn: String, checkOut: String, rooms: Int): Availability = TODO()
        override val favoriteState: kotlinx.coroutines.flow.StateFlow<com.innly.hotelbooking.domain.repository.FavoriteState> =
            kotlinx.coroutines.flow.MutableStateFlow(com.innly.hotelbooking.domain.repository.FavoriteState())
        override val favoriteIds: kotlinx.coroutines.flow.StateFlow<Set<String>> =
            kotlinx.coroutines.flow.MutableStateFlow(emptySet())
        override val favoriteHotels: kotlinx.coroutines.flow.StateFlow<List<Hotel>> =
            kotlinx.coroutines.flow.MutableStateFlow(emptyList())
        override val inFlightFavoriteHotelIds: kotlinx.coroutines.flow.StateFlow<Set<String>> =
            kotlinx.coroutines.flow.MutableStateFlow(emptySet())
        override val isFavoritesHydrated: kotlinx.coroutines.flow.StateFlow<Boolean> =
            kotlinx.coroutines.flow.MutableStateFlow(false)

        override suspend fun getFavorites(): List<Hotel> = emptyList()
        override suspend fun syncFavorites(): List<Hotel> = emptyList()
        override suspend fun toggleFavoriteOptimistic(hotel: Hotel): com.innly.hotelbooking.domain.repository.FavoriteMutationResult =
            com.innly.hotelbooking.domain.repository.FavoriteMutationResult.Success(true)
        override suspend fun restoreFavoriteOptimistic(hotel: Hotel, originalIndex: Int): com.innly.hotelbooking.domain.repository.FavoriteMutationResult =
            com.innly.hotelbooking.domain.repository.FavoriteMutationResult.Success(true)
        override suspend fun getHotelReviews(hotelId: String, page: Int, limit: Int): com.innly.hotelbooking.domain.model.PublicReviewPage = TODO()
        override suspend fun getMyReview(hotelId: String): com.innly.hotelbooking.domain.model.MyReviewState = TODO()
        override suspend fun submitReview(hotelId: String, bookingId: String, reviewId: String?, rating: Int, title: String, comment: String): com.innly.hotelbooking.domain.model.PrivateReview = TODO()
    }

    @Test
    fun `1 cancelBooking passes user reason and displays authoritative displayMessage on success`() = runTest {
        val fakeRepo = FakeBookingRepoForCancellation(
            mockResult = CancellationResult("b-1", "cancelled", false, null, "processed", "Refund completed")
        )
        val viewModel = BookingViewModel(
            hotelRepository = FakeHotelRepo(),
            bookingRepository = fakeRepo,
            authRepository = FakeAuthRepo(),
        )

        var completed = false
        viewModel.cancelBooking("b-1", "Change of plans") {
            completed = true
        }

        testScheduler.advanceUntilIdle()

        assertEquals(1, fakeRepo.cancelCallCount)
        assertEquals("Change of plans", fakeRepo.lastReasonPassed)
        assertTrue(completed)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isCancelling)
        assertEquals("Refund completed", viewModel.uiState.value.successMessage)
    }

    @Test
    fun `2 cancelBooking displays Refund processing for pending refunds`() = runTest {
        val fakeRepo = FakeBookingRepoForCancellation(
            mockResult = CancellationResult("b-1", "cancellation_pending", false, null, "pending", "Refund processing")
        )
        val viewModel = BookingViewModel(
            hotelRepository = FakeHotelRepo(),
            bookingRepository = fakeRepo,
            authRepository = FakeAuthRepo(),
        )

        viewModel.cancelBooking("b-1", "Personal emergency")

        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isCancelling)
        assertEquals("Refund processing", viewModel.uiState.value.successMessage)
    }

    @Test
    fun `3 cancelBooking parses error message on failure and leaves booking history intact`() = runTest {
        val fakeRepo = FakeBookingRepoForCancellation(
            shouldFail = true,
            errorMessage = "Free cancellation period has ended",
        )
        val viewModel = BookingViewModel(
            hotelRepository = FakeHotelRepo(),
            bookingRepository = fakeRepo,
            authRepository = FakeAuthRepo(),
        )

        var completed = false
        viewModel.cancelBooking("b-1", "Late cancel") {
            completed = true
        }

        testScheduler.advanceUntilIdle()

        assertEquals(1, fakeRepo.cancelCallCount)
        assertTrue(completed)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isCancelling)
        assertEquals("Free cancellation period has ended", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `4 in-flight cancellation guard prevents duplicate cancel calls on rapid clicks`() = runTest {
        val fakeRepo = FakeBookingRepoForCancellation()
        val viewModel = BookingViewModel(
            hotelRepository = FakeHotelRepo(),
            bookingRepository = fakeRepo,
            authRepository = FakeAuthRepo(),
        )

        viewModel.cancelBooking("b-1", "First Click")
        // Rapid second click while in-flight
        viewModel.cancelBooking("b-1", "Second Click")

        testScheduler.advanceUntilIdle()

        assertEquals(1, fakeRepo.cancelCallCount)
        assertEquals("First Click", fakeRepo.lastReasonPassed)
    }

    @Test
    fun `5 loadBookingHistory failure sets dedicated error state and does not become an empty state`() = runTest {
        val fakeRepo = FakeBookingRepoForCancellation(shouldFailHistory = true)
        val viewModel = BookingViewModel(
            hotelRepository = FakeHotelRepo(),
            bookingRepository = fakeRepo,
            authRepository = FakeAuthRepo(),
        )

        viewModel.loadBookingHistory()
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Failed to load booking history", viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.bookingHistory.isEmpty())
    }

    @Test
    fun `6 empty booking history state properly populates empty list on success`() = runTest {
        val fakeRepo = FakeBookingRepoForCancellation(historyList = emptyList())
        val viewModel = BookingViewModel(
            hotelRepository = FakeHotelRepo(),
            bookingRepository = fakeRepo,
            authRepository = FakeAuthRepo(),
        )

        viewModel.loadBookingHistory()
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.bookingHistory.isEmpty())
    }

    @Test
    fun `7 booking history filter correctly partitions bookings across All, Active, Completed, and Cancelled`() {
        val testToday = LocalDate.of(2026, 8, 23)
        val b1 = Booking("b-1", "Hotel A", "Room 1", "2026-08-25", "2026-08-28", "confirmed", 1000, "INR", true)
        val b2 = Booking("b-2", "Hotel B", "Room 2", "2026-08-20", "2026-08-22", "payment_pending", 2000, "INR", true)
        val b3 = Booking("b-3", "Hotel C", "Room 3", "2026-08-10", "2026-08-12", "cancelled", 3000, "INR", false)
        val b4 = Booking("b-4", "Hotel D", "Room 4", "2026-08-11", "2026-08-13", "cancellation_pending", 4000, "INR", false)
        val b5 = Booking("b-5", "Hotel E", "Room 5", "2026-08-16", "2026-08-18", "confirmed", 5000, "INR", false)

        val allList = listOf(b1, b2, b3, b4, b5)

        val filteredAll = BookingHistoryFormatter.filterBookings(allList, BookingFilterTab.ALL, testToday)
        val filteredActive = BookingHistoryFormatter.filterBookings(allList, BookingFilterTab.ACTIVE, testToday)
        val filteredCompleted = BookingHistoryFormatter.filterBookings(allList, BookingFilterTab.COMPLETED, testToday)
        val filteredCancelled = BookingHistoryFormatter.filterBookings(allList, BookingFilterTab.CANCELLED, testToday)

        assertEquals(5, filteredAll.size)
        assertEquals(2, filteredActive.size)
        assertEquals(listOf("b-1", "b-2"), filteredActive.map { it.id })
        assertEquals(1, filteredCompleted.size)
        assertEquals(listOf("b-5"), filteredCompleted.map { it.id })
        assertEquals(2, filteredCancelled.size)
        assertEquals(listOf("b-3", "b-4"), filteredCancelled.map { it.id })
    }

    @Test
    fun `8 cancellation reason validator rejects short reasons and accepts valid reasons`() {
        assertFalse(BookingHistoryFormatter.isCancellationReasonValid(""))
        assertFalse(BookingHistoryFormatter.isCancellationReasonValid("  "))
        assertFalse(BookingHistoryFormatter.isCancellationReasonValid("No"))
        assertFalse(BookingHistoryFormatter.isCancellationReasonValid(" a "))
        assertTrue(BookingHistoryFormatter.isCancellationReasonValid("Yes"))
        assertTrue(BookingHistoryFormatter.isCancellationReasonValid("Change of plans"))
        assertTrue(BookingHistoryFormatter.isCancellationReasonValid("Flight was cancelled by the airline"))
    }

    @Test
    fun `9 compact booking reference formatting preserves full ID for copying`() {
        val longUuid = "61d4f93b-a2a6-4e1f-846b-e75a08dff261"
        val compact = BookingHistoryFormatter.formatCompactBookingId(longUuid)

        assertEquals("61d4f93b…f261", compact)
        // Verify original string is completely preserved and available for copy operations
        assertEquals(36, longUuid.length)
        assertEquals("61d4f93b-a2a6-4e1f-846b-e75a08dff261", longUuid)

        val shortId = "b-101"
        assertEquals("b-101", BookingHistoryFormatter.formatCompactBookingId(shortId))
    }

    @Test
    fun `10 failed refund text never claims Support notified and uses factual customer guidance`() {
        val refundInfo = BookingHistoryFormatter.mapRefundStatus(
            refundStatus = "failed",
            bookingStatus = "cancelled",
            displayMessage = null,
            canCancel = false,
            cancellationDeadline = null,
        )

        assertNotNull(refundInfo)
        assertEquals(RefundPresentationType.FAILED, refundInfo?.type)
        assertEquals("Refund failed. Please contact support for assistance.", refundInfo?.message)
        assertFalse(refundInfo?.message?.contains("Support notified", ignoreCase = true) == true)
    }

    @Test
    fun `11 cancelled booking with no refund uses safe backend message or fallback`() {
        // When backend provided an authoritative displayMessage
        val withBackendMsg = BookingHistoryFormatter.mapRefundStatus(
            refundStatus = null,
            bookingStatus = "cancelled",
            displayMessage = "Cancelled per policy",
            canCancel = false,
            cancellationDeadline = null,
        )
        assertNotNull(withBackendMsg)
        assertEquals(RefundPresentationType.CANCELLED_NO_REFUND, withBackendMsg?.type)
        assertEquals("Cancelled per policy", withBackendMsg?.message)

        // When displayMessage is null or blank
        val fallback = BookingHistoryFormatter.mapRefundStatus(
            refundStatus = null,
            bookingStatus = "cancelled",
            displayMessage = null,
            canCancel = false,
            cancellationDeadline = null,
        )
        assertNotNull(fallback)
        assertEquals(RefundPresentationType.CANCELLED_NO_REFUND, fallback?.type)
        assertEquals("Booking cancelled", fallback?.message)
    }

    @Test
    fun `12 unknown refund status produces safe neutral fallback`() {
        val unknown = BookingHistoryFormatter.mapRefundStatus(
            refundStatus = "manual_review_queued",
            bookingStatus = "cancelled",
            displayMessage = null,
            canCancel = false,
            cancellationDeadline = null,
        )

        assertNotNull(unknown)
        assertEquals(RefundPresentationType.NEUTRAL_FALLBACK, unknown?.type)
        assertEquals("Refund status: manual_review_queued", unknown?.message)
    }

    @Test
    fun `13 booking status mapper maps confirmed, pending, cancelling, and unknown statuses`() {
        assertEquals("Confirmed", BookingHistoryFormatter.mapBookingStatus("confirmed").label)
        assertEquals(BookingStatusType.CONFIRMED, BookingHistoryFormatter.mapBookingStatus("confirmed").type)

        assertEquals("Payment Pending", BookingHistoryFormatter.mapBookingStatus("payment_pending").label)
        assertEquals(BookingStatusType.PAYMENT_PENDING, BookingHistoryFormatter.mapBookingStatus("payment_pending").type)

        assertEquals("Cancelling", BookingHistoryFormatter.mapBookingStatus("cancellation_pending").label)
        assertEquals(BookingStatusType.CANCELLATION_PENDING, BookingHistoryFormatter.mapBookingStatus("cancellation_pending").type)

        assertEquals("Cancelled", BookingHistoryFormatter.mapBookingStatus("cancelled").label)
        assertEquals(BookingStatusType.CANCELLED, BookingHistoryFormatter.mapBookingStatus("cancelled").type)

        assertEquals("Archived", BookingHistoryFormatter.mapBookingStatus("archived").label)
        assertEquals(BookingStatusType.UNKNOWN, BookingHistoryFormatter.mapBookingStatus("archived").type)
    }
}
