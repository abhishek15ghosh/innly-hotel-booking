package com.innly.hotelbooking

import com.innly.hotelbooking.domain.model.Availability
import com.innly.hotelbooking.domain.model.Booking
import com.innly.hotelbooking.domain.model.BookingRequest
import com.innly.hotelbooking.domain.model.Hotel
import com.innly.hotelbooking.domain.model.PaymentOrder
import com.innly.hotelbooking.domain.model.PaymentVerificationResult
import com.innly.hotelbooking.domain.model.Review
import com.innly.hotelbooking.domain.model.UserProfile
import com.innly.hotelbooking.domain.repository.AuthRepository
import com.innly.hotelbooking.domain.repository.BookingRepository
import com.innly.hotelbooking.domain.repository.HotelRepository
import com.innly.hotelbooking.presentation.booking.BookingFormValidator
import com.innly.hotelbooking.presentation.booking.BookingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class BookingVerificationTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeHotelRepository : HotelRepository {
        override suspend fun getHotels(filter: com.innly.hotelbooking.domain.model.HotelSearchFilter): List<com.innly.hotelbooking.domain.model.Hotel> = TODO()
        override suspend fun getHotelDetail(hotelId: String) = TODO()
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

        override suspend fun getFavorites(): List<Hotel> = TODO()
        override suspend fun syncFavorites(): List<Hotel> = TODO()
        override suspend fun toggleFavoriteOptimistic(hotel: Hotel): com.innly.hotelbooking.domain.repository.FavoriteMutationResult = TODO()
        override suspend fun restoreFavoriteOptimistic(hotel: Hotel, originalIndex: Int): com.innly.hotelbooking.domain.repository.FavoriteMutationResult = TODO()
        override suspend fun getHotelReviews(hotelId: String, page: Int, limit: Int): com.innly.hotelbooking.domain.model.PublicReviewPage = TODO()
        override suspend fun getMyReview(hotelId: String): com.innly.hotelbooking.domain.model.MyReviewState = TODO()
        override suspend fun submitReview(hotelId: String, bookingId: String, reviewId: String?, rating: Int, title: String, comment: String): com.innly.hotelbooking.domain.model.PrivateReview = TODO()
    }

    private class FakeAuthRepository : AuthRepository {
        override val currentUser: Flow<UserProfile?> = flowOf(
            UserProfile(id = "db-user-1", firebaseUid = "uid-1", email = "test@example.com")
        )
        override suspend fun signIn(email: String, password: String) {}
        override suspend fun signUp(name: String, email: String, password: String) {}
        override suspend fun signOut() {}
        override suspend fun syncProfile(displayName: String?, phoneNumber: String?): UserProfile {
            return UserProfile(id = "db-user-1", firebaseUid = "uid-1", email = "test@example.com")
        }
    }

    private class FakeBookingRepository(
        var verifyResult: PaymentVerificationResult? = null,
        var verifyError: Exception? = null,
        var bookingHistoryList: List<Booking> = emptyList(),
        var historyError: Exception? = null,
    ) : BookingRepository {
        override suspend fun createBooking(request: BookingRequest): PaymentOrder = TODO()

        override suspend fun verifyPayment(
            bookingId: String,
            razorpayOrderId: String,
            razorpayPaymentId: String,
            razorpaySignature: String,
        ): PaymentVerificationResult {
            verifyError?.let { throw it }
            return verifyResult ?: PaymentVerificationResult(
                bookingId = bookingId,
                bookingStatus = "confirmed",
                paymentStatus = "captured",
            )
        }

        override suspend fun getBookingHistory(): List<Booking> {
            historyError?.let { throw it }
            return bookingHistoryList
        }

        override suspend fun cancelBooking(bookingId: String, reason: String) = TODO()
    }

    @Test
    fun `1 3-point check matching bookingId plus confirmed plus captured sets paymentVerified true`() = runTest {
        val fakeBookingRepo = FakeBookingRepository(
            verifyResult = PaymentVerificationResult(
                bookingId = "b-101",
                bookingStatus = "confirmed",
                paymentStatus = "captured",
                message = "Payment verified. Booking confirmed.",
            )
        )
        val viewModel = BookingViewModel(
            hotelRepository = FakeHotelRepository(),
            bookingRepository = fakeBookingRepo,
            authRepository = FakeAuthRepository(),
        )

        viewModel.verifyPayment("b-101", "order-1", "pay-1", "sig-1")

        val state = viewModel.uiState.value
        assertTrue("paymentVerified must be true when bookingId, confirmed, and captured match", state.paymentVerified)
        assertEquals("Payment verified. Booking confirmed.", state.successMessage)
        assertEquals(null, state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun `2 mismatched bookingId in verification response is rejected and paymentVerified remains false`() = runTest {
        val fakeBookingRepo = FakeBookingRepository(
            verifyResult = PaymentVerificationResult(
                bookingId = "b-DIFFERENT-999",
                bookingStatus = "confirmed",
                paymentStatus = "captured",
                message = "Payment verified.",
            )
        )
        val viewModel = BookingViewModel(
            hotelRepository = FakeHotelRepository(),
            bookingRepository = fakeBookingRepo,
            authRepository = FakeAuthRepository(),
        )

        viewModel.verifyPayment("b-101", "order-1", "pay-1", "sig-1")

        val state = viewModel.uiState.value
        assertFalse("paymentVerified must remain false on mismatched bookingId", state.paymentVerified)
        assertEquals("Payment verification returned an invalid booking reference.", state.errorMessage)
        assertNull(state.successMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun `3 authorized plus payment_pending result keeps paymentVerified false and shows pending message`() = runTest {
        val fakeBookingRepo = FakeBookingRepository(
            verifyResult = PaymentVerificationResult(
                bookingId = "b-101",
                bookingStatus = "payment_pending",
                paymentStatus = "authorized",
                message = "Payment authorized but not captured yet.",
            )
        )
        val viewModel = BookingViewModel(
            hotelRepository = FakeHotelRepository(),
            bookingRepository = fakeBookingRepo,
            authRepository = FakeAuthRepository(),
        )

        viewModel.verifyPayment("b-101", "order-1", "pay-1", "sig-1")

        val state = viewModel.uiState.value
        assertFalse("paymentVerified must remain false for authorized status", state.paymentVerified)
        assertEquals("Payment authorized but not captured yet.", state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun `4 verification error keeps paymentVerified false and shows error on screen`() = runTest {
        val fakeBookingRepo = FakeBookingRepository(
            verifyError = RuntimeException("Razorpay signature verification failed")
        )
        val viewModel = BookingViewModel(
            hotelRepository = FakeHotelRepository(),
            bookingRepository = fakeBookingRepo,
            authRepository = FakeAuthRepository(),
        )

        viewModel.verifyPayment("b-101", "order-1", "pay-1", "sig-1")

        val state = viewModel.uiState.value
        assertFalse("paymentVerified must remain false on error", state.paymentVerified)
        assertEquals(null, state.successMessage)
        assertEquals("Razorpay signature verification failed", state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun `5 verification success event is consumed once and cannot trigger duplicate navigation`() = runTest {
        val fakeBookingRepo = FakeBookingRepository(
            verifyResult = PaymentVerificationResult(
                bookingId = "b-101",
                bookingStatus = "confirmed",
                paymentStatus = "captured",
            )
        )
        val viewModel = BookingViewModel(
            hotelRepository = FakeHotelRepository(),
            bookingRepository = fakeBookingRepo,
            authRepository = FakeAuthRepository(),
        )

        viewModel.verifyPayment("b-101", "order-1", "pay-1", "sig-1")
        assertTrue("paymentVerified should be true initially", viewModel.uiState.value.paymentVerified)

        // Consume event
        viewModel.consumePaymentVerifiedEvent()
        assertFalse("paymentVerified must be false after consumption", viewModel.uiState.value.paymentVerified)
    }

    @Test
    fun `6 raw Razorpay error JSON is mapped to friendly message`() = runTest {
        val viewModel = BookingViewModel(
            hotelRepository = FakeHotelRepository(),
            bookingRepository = FakeBookingRepository(),
            authRepository = FakeAuthRepository(),
        )
        val rawJsonError = """{"error":{"code":"BAD_REQUEST_ERROR","description":"Payment failed during authentication","source":"customer","step":"payment_authentication","reason":"payment_failed"}}"""

        viewModel.reportPaymentError(rawJsonError, 0)

        val state = viewModel.uiState.value
        assertEquals(
            "Payment was not completed. No money was charged. Please try another payment method.",
            state.errorMessage
        )
        assertFalse("errorMessage must not contain raw JSON braces", state.errorMessage!!.contains("{"))
        assertFalse("errorMessage must not contain raw JSON field code", state.errorMessage!!.contains("BAD_REQUEST_ERROR"))
    }

    @Test
    fun `7 reportPaymentError enables retry state and resets isOpeningCheckout`() = runTest {
        val viewModel = BookingViewModel(
            hotelRepository = FakeHotelRepository(),
            bookingRepository = FakeBookingRepository(),
            authRepository = FakeAuthRepository(),
        )

        viewModel.startCheckoutAttempt()
        assertTrue(viewModel.uiState.value.isOpeningCheckout)

        viewModel.reportPaymentError("Payment failed", 0)

        val state = viewModel.uiState.value
        assertFalse("isOpeningCheckout must reset to false after error", state.isOpeningCheckout)
        assertFalse("isLoading must be false", state.isLoading)
        assertTrue("errorMessage must be non-null so UI shows Retry Payment", state.errorMessage != null)
    }

    @Test
    fun `8 retry flow reuses existing PaymentOrder without invoking createBooking again`() = runTest {
        var createBookingCallCount = 0
        val fakeBookingRepo = object : BookingRepository {
            override suspend fun createBooking(request: BookingRequest): PaymentOrder {
                createBookingCallCount++
                return PaymentOrder("b-1", 100, "INR", "order-123", 10000)
            }
            override suspend fun verifyPayment(bookingId: String, razorpayOrderId: String, razorpayPaymentId: String, razorpaySignature: String): PaymentVerificationResult = TODO()
            override suspend fun getBookingHistory(): List<Booking> = TODO()
            override suspend fun cancelBooking(bookingId: String, reason: String) = TODO()
        }

        val viewModel = BookingViewModel(
            hotelRepository = FakeHotelRepository(),
            bookingRepository = fakeBookingRepo,
            authRepository = FakeAuthRepository(),
        )

        viewModel.createBooking(BookingRequest("h-1", "r-1", "2026-08-16", "2026-08-18", 1, 2, "Guest Name", "guest@example.com", "9999999999"))
        assertEquals(1, createBookingCallCount)
        val originalOrder = viewModel.uiState.value.paymentOrder

        // Simulate payment failure and retry
        viewModel.reportPaymentError("User cancelled payment", 0)
        viewModel.startCheckoutAttempt()

        assertEquals(1, createBookingCallCount) // Call count must remain 1!
        assertEquals(originalOrder, viewModel.uiState.value.paymentOrder) // Existing order reused!
    }

    @Test
    fun `9 startCheckoutAttempt sets isOpeningCheckout true to block rapid double-taps`() = runTest {
        val viewModel = BookingViewModel(
            hotelRepository = FakeHotelRepository(),
            bookingRepository = FakeBookingRepository(),
            authRepository = FakeAuthRepository(),
        )

        assertFalse(viewModel.uiState.value.isOpeningCheckout)
        viewModel.startCheckoutAttempt()
        assertTrue("isOpeningCheckout must be true during launch to prevent double taps", viewModel.uiState.value.isOpeningCheckout)
        assertFalse("button must be disabled when isOpeningCheckout is true", !viewModel.uiState.value.isLoading && !viewModel.uiState.value.isOpeningCheckout)
    }

    @Test
    fun `10 isRazorpayTestMode safely detects test keys without logging or leaking key contents`() {
        assertTrue("rzp_test_ key should be identified as test mode", BookingFormValidator.isRazorpayTestMode("rzp_test_abc12345"))
        assertFalse("rzp_live_ key should not be test mode", BookingFormValidator.isRazorpayTestMode("rzp_live_xyz67890"))
        assertFalse("Empty key should not be test mode", BookingFormValidator.isRazorpayTestMode(""))
    }

    @Test
    fun `11 Currency is formatted with thousands separator`() {
        assertEquals("₹13,400", BookingFormValidator.formatCurrency("INR", 13400))
        assertEquals("$1,250", BookingFormValidator.formatCurrency("USD", 1250))
        assertEquals("€5,900", BookingFormValidator.formatCurrency("EUR", 5900))
        assertEquals("AUD 2,500", BookingFormValidator.formatCurrency("AUD", 2500))
    }

    @Test
    fun `12 loadBookingDetails selects matching booking by verified bookingId from getBookingHistory`() = runTest {
        val matchingBooking = Booking(
            id = "b-101",
            hotelName = "Valley Crest Resort",
            roomName = "Valley Room",
            checkIn = "2026-08-16",
            checkOut = "2026-08-18",
            status = "confirmed",
            amount = 13400,
            currency = "INR",
        )
        val otherBooking = Booking(
            id = "b-999",
            hotelName = "Grand Lotus",
            roomName = "Deluxe",
            checkIn = "2026-09-01",
            checkOut = "2026-09-03",
            status = "confirmed",
            amount = 5900,
            currency = "INR",
        )

        val fakeBookingRepo = FakeBookingRepository(bookingHistoryList = listOf(otherBooking, matchingBooking))
        val viewModel = BookingViewModel(
            hotelRepository = FakeHotelRepository(),
            bookingRepository = fakeBookingRepo,
            authRepository = FakeAuthRepository(),
        )

        viewModel.loadBookingDetails("b-101")

        val state = viewModel.uiState.value
        assertFalse(state.isDetailsLoading)
        assertNull(state.detailsError)
        assertNotNull(state.confirmedBooking)
        assertEquals("b-101", state.confirmedBooking!!.id)
        assertEquals("Valley Crest Resort", state.confirmedBooking!!.hotelName)
    }

    @Test
    fun `13 loadBookingDetails sets confirmedBooking null for fallback when not in history`() = runTest {
        val fakeBookingRepo = FakeBookingRepository(bookingHistoryList = emptyList())
        val viewModel = BookingViewModel(
            hotelRepository = FakeHotelRepository(),
            bookingRepository = fakeBookingRepo,
            authRepository = FakeAuthRepository(),
        )

        viewModel.loadBookingDetails("b-delayed-101")

        val state = viewModel.uiState.value
        assertFalse(state.isDetailsLoading)
        assertNull(state.confirmedBooking)
        assertNull(state.detailsError)
    }

    @Test
    fun `14 loadBookingDetails handles repository error gracefully permitting retry`() = runTest {
        val fakeBookingRepo = FakeBookingRepository(historyError = RuntimeException("Network timeout"))
        val viewModel = BookingViewModel(
            hotelRepository = FakeHotelRepository(),
            bookingRepository = fakeBookingRepo,
            authRepository = FakeAuthRepository(),
        )

        viewModel.loadBookingDetails("b-101")

        val state = viewModel.uiState.value
        assertFalse(state.isDetailsLoading)
        assertNull(state.confirmedBooking)
        assertEquals("Network timeout", state.detailsError)
    }
}
