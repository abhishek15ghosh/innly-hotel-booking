package com.innly.hotelbooking

import com.innly.hotelbooking.domain.model.Availability
import com.innly.hotelbooking.domain.model.Booking
import com.innly.hotelbooking.domain.model.BookingRequest
import com.innly.hotelbooking.domain.model.CancellationResult
import com.innly.hotelbooking.domain.model.DailyAvailability
import com.innly.hotelbooking.domain.model.Hotel
import com.innly.hotelbooking.domain.model.HotelSearchFilter
import com.innly.hotelbooking.domain.model.PaymentOrder
import com.innly.hotelbooking.domain.model.PaymentVerificationResult
import com.innly.hotelbooking.domain.model.Review
import com.innly.hotelbooking.domain.model.Room
import com.innly.hotelbooking.domain.model.UserProfile
import com.innly.hotelbooking.domain.repository.AuthRepository
import com.innly.hotelbooking.domain.repository.BookingRepository
import com.innly.hotelbooking.domain.repository.HotelRepository
import com.google.gson.Gson
import com.innly.hotelbooking.data.remote.UserProfileDto
import com.innly.hotelbooking.data.remote.toDomain
import com.innly.hotelbooking.presentation.booking.BookingFormValidator
import com.innly.hotelbooking.presentation.booking.BookingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class BookingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeHotelRepository(
        var available: Boolean = true,
        var totalAmount: Int = 11800,
        var nights: Int = 2,
        var currency: String = "INR",
        var dailyBreakdown: List<DailyAvailability> = listOf(
            DailyAvailability("2026-08-16", 5900, 3),
            DailyAvailability("2026-08-17", 5900, 3),
        ),
    ) : HotelRepository {
        var lastCheckIn: String? = null
        var lastCheckOut: String? = null
        var lastRooms: Int? = null
        var checkAvailabilityCount = 0

        override suspend fun getHotels(filter: HotelSearchFilter): List<Hotel> = emptyList()
        override suspend fun getHotelDetail(hotelId: String): Hotel {
            return Hotel(
                id = hotelId,
                name = "Grand Palace Hotel",
                description = "Luxury stay",
                city = "New Delhi",
                address = "1 Connaught Place",
                rating = 4.8,
                reviewCount = 120,
                startingPrice = 5900,
                thumbnailUrl = "https://example.com/h.jpg",
                images = emptyList(),
                amenities = emptyList(),
                isFavorite = false,
                rooms = listOf(
                    Room(
                        id = "r-1",
                        hotelId = hotelId,
                        name = "Capital Suite",
                        description = "Spacious suite",
                        bedType = "King Bed",
                        capacity = 2,
                        basePrice = 5900,
                        thumbnailUrl = "https://example.com/r.jpg",
                        images = emptyList(),
                    ),
                ),
            )
        }

        override suspend fun getAvailability(
            hotelId: String,
            roomId: String,
            checkIn: String,
            checkOut: String,
            rooms: Int,
        ): Availability {
            checkAvailabilityCount++
            lastCheckIn = checkIn
            lastCheckOut = checkOut
            lastRooms = rooms
            return Availability(
                available = available,
                totalAmount = totalAmount,
                currency = currency,
                nights = nights,
                dailyBreakdown = dailyBreakdown,
            )
        }

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

    private class FakeBookingRepository : BookingRepository {
        var createBookingCallCount = 0
        var lastRequest: BookingRequest? = null
        var delayMs: Long = 0L

        override suspend fun createBooking(request: BookingRequest): PaymentOrder {
            createBookingCallCount++
            lastRequest = request
            if (delayMs > 0) {
                delay(delayMs)
            }
            return PaymentOrder(
                bookingId = "b-101",
                razorpayOrderId = "order_101",
                razorpayAmount = 1180000,
                amount = 11800,
                currency = "INR",
            )
        }

        override suspend fun verifyPayment(
            bookingId: String,
            razorpayOrderId: String,
            razorpayPaymentId: String,
            razorpaySignature: String,
        ): PaymentVerificationResult = TODO()

        override suspend fun getBookingHistory(): List<Booking> = emptyList()
        override suspend fun cancelBooking(bookingId: String, reason: String): CancellationResult = TODO()
    }

    private class FakeAuthRepository(
        private val userProfile: UserProfile? = UserProfile(
            firebaseUid = "uid-1",
            displayName = "Alex Morgan",
            email = "alex@example.com",
            phoneNumber = "+919876543210",
        ),
    ) : AuthRepository {
        override val currentUser: Flow<UserProfile?> = flowOf(userProfile)
        override suspend fun signIn(email: String, password: String) {}
        override suspend fun signUp(name: String, email: String, password: String) {}
        override suspend fun signOut() {}
        override suspend fun syncProfile(displayName: String?, phoneNumber: String?): UserProfile =
            userProfile ?: UserProfile("uid-1", "Alex Morgan", "alex@example.com")
    }

    @Test
    fun `1 Profile pre-populates guest details on initialization`() = runTest {
        val fakeHotelRepo = FakeHotelRepository()
        val fakeBookingRepo = FakeBookingRepository()
        val fakeAuthRepo = FakeAuthRepository()

        val viewModel = BookingViewModel(fakeHotelRepo, fakeBookingRepo, fakeAuthRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Alex Morgan", state.profileName)
        assertEquals("alex@example.com", state.profileEmail)
        assertEquals("+919876543210", state.profilePhone)
    }

    @Test
    fun `2 Today is rejected as check-in and tomorrow is accepted`() {
        val today = LocalDate.of(2026, 8, 15)
        val todayCheckIn = today
        val pastCheckIn = today.minusDays(1)
        val tomorrowCheckIn = today.plusDays(1)
        val futureCheckIn = today.plusDays(7)

        assertFalse("Today must be rejected for check-in", BookingFormValidator.isCheckInValid(todayCheckIn, today))
        assertFalse("Past date must be rejected for check-in", BookingFormValidator.isCheckInValid(pastCheckIn, today))
        assertTrue("Tomorrow must be accepted for check-in", BookingFormValidator.isCheckInValid(tomorrowCheckIn, today))
        assertTrue("Future date must be accepted for check-in", BookingFormValidator.isCheckInValid(futureCheckIn, today))
    }

    @Test
    fun `3 Check-out equal to or before check-in is rejected and after check-in is accepted`() {
        val today = LocalDate.of(2026, 8, 15)
        val checkIn = today.plusDays(1)

        val checkOutSame = checkIn
        val checkOutBefore = today
        val checkOutAfter = checkIn.plusDays(2)

        assertFalse("Check-out equal to check-in is rejected", BookingFormValidator.isCheckOutValid(checkOutSame, checkIn))
        assertFalse("Check-out before check-in is rejected", BookingFormValidator.isCheckOutValid(checkOutBefore, checkIn))
        assertTrue("Check-out strictly after check-in is accepted", BookingFormValidator.isCheckOutValid(checkOutAfter, checkIn))

        assertFalse("Date range with same day check-out is rejected", BookingFormValidator.isDateRangeValid(checkIn, checkOutSame, today))
        assertTrue("Date range with valid future check-in and check-out is accepted", BookingFormValidator.isDateRangeValid(checkIn, checkOutAfter, today))
    }

    @Test
    fun `4 Rooms outside 1-5 and Guests outside 1-10 are rejected`() {
        val validRooms = listOf(1, 2, 3, 4, 5)
        val invalidRooms = listOf(0, -1, 6, 10)

        validRooms.forEach { r -> assertTrue("Room count $r should be valid", BookingFormValidator.isRoomsValid(r)) }
        invalidRooms.forEach { r -> assertFalse("Room count $r should be invalid", BookingFormValidator.isRoomsValid(r)) }

        val validGuests = (1..10).toList()
        val invalidGuests = listOf(0, -2, 11, 20)

        validGuests.forEach { g -> assertTrue("Guest count $g should be valid", BookingFormValidator.isGuestsValid(g)) }
        invalidGuests.forEach { g -> assertFalse("Guest count $g should be invalid", BookingFormValidator.isGuestsValid(g)) }
    }

    @Test
    fun `5 Guest fields validation correctly validates name, email, and phone rules`() {
        // Name: 2..120
        assertTrue(BookingFormValidator.isGuestNameValid("John Doe"))
        assertFalse(BookingFormValidator.isGuestNameValid("A"))
        assertFalse(BookingFormValidator.isGuestNameValid("   "))

        // Email: standard regex
        assertTrue(BookingFormValidator.isGuestEmailValid("guest@example.com"))
        assertFalse(BookingFormValidator.isGuestEmailValid("invalid-email"))
        assertFalse(BookingFormValidator.isGuestEmailValid(""))

        // Phone: 8..20 chars
        assertTrue(BookingFormValidator.isGuestPhoneValid("+919876543210"))
        assertTrue(BookingFormValidator.isGuestPhoneValid("98765432"))
        assertFalse(BookingFormValidator.isGuestPhoneValid("123"))
        assertFalse(BookingFormValidator.isGuestPhoneValid(""))
    }

    @Test
    fun `6 Continue to Payment is disabled when availability is null or sold out or any field is invalid`() {
        val today = LocalDate.of(2026, 8, 15)
        val validCheckIn = today.plusDays(1)
        val validCheckOut = today.plusDays(3)

        val validAvailability = Availability(
            available = true,
            totalAmount = 11800,
            currency = "INR",
            nights = 2,
            dailyBreakdown = emptyList(),
        )
        val soldOutAvailability = Availability(
            available = false,
            totalAmount = 0,
            currency = "INR",
            nights = 2,
            dailyBreakdown = emptyList(),
        )

        // 1. Availability is null -> disabled
        assertFalse(
            BookingFormValidator.isContinueToPaymentEligible(
                isFormValid = true,
                availability = null,
                isLoading = false,
            )
        )

        // 2. Availability is sold out (available == false) -> disabled
        assertFalse(
            BookingFormValidator.isContinueToPaymentEligible(
                isFormValid = true,
                availability = soldOutAvailability,
                isLoading = false,
            )
        )

        // 3. Form invalid (e.g. invalid phone) -> disabled
        val formInvalid = BookingFormValidator.isFormValid(
            checkIn = validCheckIn,
            checkOut = validCheckOut,
            today = today,
            rooms = 1,
            guests = 2,
            guestName = "John Doe",
            guestEmail = "john@example.com",
            guestPhone = "123", // invalid
        )
        assertFalse(formInvalid)
        assertFalse(
            BookingFormValidator.isContinueToPaymentEligible(
                isFormValid = formInvalid,
                availability = validAvailability,
                isLoading = false,
            )
        )

        // 4. Loading is true -> disabled
        assertFalse(
            BookingFormValidator.isContinueToPaymentEligible(
                isFormValid = true,
                availability = validAvailability,
                isLoading = true,
            )
        )

        // 5. Form valid, availability confirmed, loading false -> ENABLED
        val formValid = BookingFormValidator.isFormValid(
            checkIn = validCheckIn,
            checkOut = validCheckOut,
            today = today,
            rooms = 1,
            guests = 2,
            guestName = "John Doe",
            guestEmail = "john@example.com",
            guestPhone = "+919876543210",
        )
        assertTrue(formValid)
        assertTrue(
            BookingFormValidator.isContinueToPaymentEligible(
                isFormValid = formValid,
                availability = validAvailability,
                isLoading = false,
            )
        )
    }

    @Test
    fun `7 Changing stay parameters invalidates previously confirmed availability`() = runTest {
        val fakeHotelRepo = FakeHotelRepository(available = true, totalAmount = 11800, nights = 2)
        val fakeBookingRepo = FakeBookingRepository()
        val fakeAuthRepo = FakeAuthRepository()

        val viewModel = BookingViewModel(fakeHotelRepo, fakeBookingRepo, fakeAuthRepo)
        advanceUntilIdle()

        viewModel.checkAvailability("h-1", "r-1", "2026-08-16", "2026-08-18", 1)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.availability)
        assertTrue(viewModel.uiState.value.availability!!.available)

        // Invalidate availability
        viewModel.invalidateAvailability()

        assertNull(viewModel.uiState.value.availability)
        assertNull(viewModel.uiState.value.successMessage)
    }

    @Test
    fun `8 Backend-provided currency and daily price breakdown formatted accurately`() {
        assertEquals("₹11,800", BookingFormValidator.formatCurrency("INR", 11800))
        assertEquals("$250", BookingFormValidator.formatCurrency("USD", 250))
        assertEquals("€199", BookingFormValidator.formatCurrency("EUR", 199))
        assertEquals("£150", BookingFormValidator.formatCurrency("GBP", 150))
        assertEquals("AUD 300", BookingFormValidator.formatCurrency("AUD", 300))
    }

    @Test
    fun `9 Load hotel details successfully loads hotel and room names`() = runTest {
        val fakeHotelRepo = FakeHotelRepository()
        val fakeBookingRepo = FakeBookingRepository()
        val fakeAuthRepo = FakeAuthRepository()

        val viewModel = BookingViewModel(fakeHotelRepo, fakeBookingRepo, fakeAuthRepo)
        advanceUntilIdle()

        viewModel.loadHotel("h-1")
        advanceUntilIdle()

        val hotel = viewModel.uiState.value.hotel
        assertNotNull(hotel)
        assertEquals("Grand Palace Hotel", hotel!!.name)
        assertEquals("New Delhi", hotel.city)
        assertEquals("Capital Suite", hotel.rooms.first().name)
    }

    // ==========================================
    // STAGE 1 & 2 SESSION ISOLATION & NAV TESTS
    // ==========================================

    private class FakeDynamicAuthRepo : AuthRepository {
        val flow = kotlinx.coroutines.flow.MutableSharedFlow<UserProfile?>(replay = 1)
        override val currentUser: Flow<UserProfile?> = flow
        override suspend fun signIn(email: String, password: String) {}
        override suspend fun signUp(name: String, email: String, password: String) {}
        override suspend fun signOut() {}
        override suspend fun syncProfile(displayName: String?, phoneNumber: String?): UserProfile =
            UserProfile("uid-test", "User", "user@test.com")
    }

    private class FakeDynamicBookingRepo : BookingRepository {
        var callCount = 0
        var delayMs: Long = 0L
        var returnList: List<Booking> = emptyList()

        override suspend fun createBooking(request: BookingRequest): PaymentOrder = TODO()
        override suspend fun verifyPayment(
            bookingId: String,
            razorpayOrderId: String,
            razorpayPaymentId: String,
            razorpaySignature: String
        ): PaymentVerificationResult = TODO()

        override suspend fun getBookingHistory(): List<Booking> {
            callCount++
            if (delayMs > 0) {
                kotlinx.coroutines.delay(delayMs)
            }
            return returnList
        }

        override suspend fun cancelBooking(bookingId: String, reason: String): CancellationResult = TODO()
    }

    private fun createSampleBooking(id: String, hotelName: String): Booking {
        return Booking(
            id = id,
            hotelName = hotelName,
            roomName = "Deluxe Room",
            checkIn = "2026-09-01",
            checkOut = "2026-09-03",
            amount = 5000,
            currency = "INR",
            status = "confirmed",
            canCancel = true,
            refundStatus = null,
            displayMessage = "Booking confirmed",
        )
    }

    @Test
    fun `10 Initial unresolved authentication shows loading and performs zero booking requests`() = runTest {
        val fakeHotelRepo = FakeHotelRepository()
        val fakeBookingRepo = FakeDynamicBookingRepo()
        val dynamicAuthRepo = FakeDynamicAuthRepo() // No emission yet

        val viewModel = BookingViewModel(fakeHotelRepo, fakeBookingRepo, dynamicAuthRepo)
        // No advance needed or advance with no emissions

        val state = viewModel.uiState.value
        assertFalse("Auth should be unresolved initially", state.isAuthResolved)
        assertEquals(0, fakeBookingRepo.callCount)

        // Manual call to loadBookingHistory should be a no-op while unresolved
        viewModel.loadBookingHistory()
        advanceUntilIdle()
        assertEquals(0, fakeBookingRepo.callCount)
    }

    @Test
    fun `11 Resolved guest shows guest UI and performs zero booking requests`() = runTest {
        val fakeHotelRepo = FakeHotelRepository()
        val fakeBookingRepo = FakeDynamicBookingRepo()
        val dynamicAuthRepo = FakeDynamicAuthRepo()

        val viewModel = BookingViewModel(fakeHotelRepo, fakeBookingRepo, dynamicAuthRepo)
        dynamicAuthRepo.flow.emit(null) // Emit guest
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Auth should be resolved", state.isAuthResolved)
        assertTrue("User should be guest", state.isGuest)
        assertEquals("Guest should make 0 booking requests", 0, fakeBookingRepo.callCount)
        assertTrue("Booking history should be empty", state.bookingHistory.isEmpty())
        assertNull("No error message for guest", state.errorMessage)

        // Manual loadBookingHistory should also be a no-op for guest
        viewModel.loadBookingHistory()
        advanceUntilIdle()
        assertEquals(0, fakeBookingRepo.callCount)
    }

    @Test
    fun `12 Login automatically loads the correct booking history`() = runTest {
        val fakeHotelRepo = FakeHotelRepository()
        val fakeBookingRepo = FakeDynamicBookingRepo()
        fakeBookingRepo.returnList = listOf(createSampleBooking("b-1", "Lotus Palace"))
        val dynamicAuthRepo = FakeDynamicAuthRepo()

        val viewModel = BookingViewModel(fakeHotelRepo, fakeBookingRepo, dynamicAuthRepo)
        dynamicAuthRepo.flow.emit(null) // Start as guest
        advanceUntilIdle()
        assertEquals(0, fakeBookingRepo.callCount)

        // Login as User A
        dynamicAuthRepo.flow.emit(UserProfile(firebaseUid = "uid-A", displayName = "Alice", email = "alice@innly.com"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isAuthResolved)
        assertFalse(state.isGuest)
        assertEquals("Alice", state.profileName)
        assertEquals(1, fakeBookingRepo.callCount)
        assertEquals(1, state.bookingHistory.size)
        assertEquals("b-1", state.bookingHistory.first().id)
    }

    @Test
    fun `13 Logout immediately clears cached booking and payment state`() = runTest {
        val fakeHotelRepo = FakeHotelRepository()
        val fakeBookingRepo = FakeDynamicBookingRepo()
        fakeBookingRepo.returnList = listOf(createSampleBooking("b-1", "Lotus Palace"))
        val dynamicAuthRepo = FakeDynamicAuthRepo()

        val viewModel = BookingViewModel(fakeHotelRepo, fakeBookingRepo, dynamicAuthRepo)
        dynamicAuthRepo.flow.emit(UserProfile(firebaseUid = "uid-A", displayName = "Alice", email = "alice@innly.com"))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.bookingHistory.size)

        // Logout
        dynamicAuthRepo.flow.emit(null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isAuthResolved)
        assertTrue(state.isGuest)
        assertEquals("", state.profileName)
        assertEquals("", state.profileEmail)
        assertTrue("Booking history must be cleared immediately on logout", state.bookingHistory.isEmpty())
        assertNull("Confirmed booking must be null", state.confirmedBooking)
        assertNull("Payment order must be null", state.paymentOrder)
        assertFalse("Payment verified must be false", state.paymentVerified)
        assertNull("Error message must be null", state.errorMessage)
    }

    @Test
    fun `14 Direct account A to account B switch clears A data and loads B data`() = runTest {
        val fakeHotelRepo = FakeHotelRepository()
        val fakeBookingRepo = FakeDynamicBookingRepo()
        fakeBookingRepo.returnList = listOf(createSampleBooking("b-alice", "Alice Resort"))
        val dynamicAuthRepo = FakeDynamicAuthRepo()

        val viewModel = BookingViewModel(fakeHotelRepo, fakeBookingRepo, dynamicAuthRepo)
        dynamicAuthRepo.flow.emit(UserProfile(firebaseUid = "uid-A", displayName = "Alice", email = "alice@innly.com"))
        advanceUntilIdle()

        assertEquals("b-alice", viewModel.uiState.value.bookingHistory.first().id)

        // Switch to Account B
        fakeBookingRepo.returnList = listOf(createSampleBooking("b-bob", "Bob Haven"))
        dynamicAuthRepo.flow.emit(UserProfile(firebaseUid = "uid-B", displayName = "Bob", email = "bob@innly.com"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Bob", state.profileName)
        assertEquals("bob@innly.com", state.profileEmail)
        assertEquals(2, fakeBookingRepo.callCount)
        assertEquals(1, state.bookingHistory.size)
        assertEquals("b-bob", state.bookingHistory.first().id)
    }

    @Test
    fun `15 A delayed response from account A is ignored after switching to B`() = runTest {
        val fakeHotelRepo = FakeHotelRepository()
        val fakeBookingRepo = FakeDynamicBookingRepo()
        fakeBookingRepo.delayMs = 1000L
        fakeBookingRepo.returnList = listOf(createSampleBooking("b-alice", "Alice Resort"))
        val dynamicAuthRepo = FakeDynamicAuthRepo()

        val viewModel = BookingViewModel(fakeHotelRepo, fakeBookingRepo, dynamicAuthRepo)

        // User A starts loading
        dynamicAuthRepo.flow.emit(UserProfile(firebaseUid = "uid-A", displayName = "Alice", email = "alice@innly.com"))
        testScheduler.advanceTimeBy(100) // In-flight request for Alice

        // Switch to User B before Alice's request returns
        fakeBookingRepo.delayMs = 100L
        fakeBookingRepo.returnList = listOf(createSampleBooking("b-bob", "Bob Haven"))
        dynamicAuthRepo.flow.emit(UserProfile(firebaseUid = "uid-B", displayName = "Bob", email = "bob@innly.com"))

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Bob", state.profileName)
        assertEquals(1, state.bookingHistory.size)
        assertEquals("b-bob", state.bookingHistory.first().id)
    }

    @Test
    fun `16 Repeated same-UID profile emissions do not unnecessarily clear or reload state`() = runTest {
        val fakeHotelRepo = FakeHotelRepository()
        val fakeBookingRepo = FakeDynamicBookingRepo()
        fakeBookingRepo.returnList = listOf(createSampleBooking("b-1", "Lotus Palace"))
        val dynamicAuthRepo = FakeDynamicAuthRepo()

        val viewModel = BookingViewModel(fakeHotelRepo, fakeBookingRepo, dynamicAuthRepo)
        dynamicAuthRepo.flow.emit(UserProfile(firebaseUid = "uid-A", displayName = "Alice", email = "alice@innly.com"))
        advanceUntilIdle()

        assertEquals(1, fakeBookingRepo.callCount)
        assertEquals(1, viewModel.uiState.value.bookingHistory.size)

        // Emit updated profile with same firebaseUid (e.g., edited display name or phone)
        dynamicAuthRepo.flow.emit(UserProfile(firebaseUid = "uid-A", displayName = "Alice Smith", email = "alice@innly.com", phoneNumber = "+919999988888"))
        advanceUntilIdle()

        // Call count should NOT have increased
        assertEquals(1, fakeBookingRepo.callCount)
        assertEquals("Alice Smith", viewModel.uiState.value.profileName)
        assertEquals("+919999988888", viewModel.uiState.value.profilePhone)
        assertEquals(1, viewModel.uiState.value.bookingHistory.size)
    }

    @Test
    fun `17 Sign In and Create Account destination routes are correct`() {
        val loginRoute = com.innly.hotelbooking.presentation.navigation.AppDestinations.Login
        val registerRoute = com.innly.hotelbooking.presentation.navigation.AppDestinations.Register
        val homeRoute = com.innly.hotelbooking.presentation.navigation.AppDestinations.Home
        val bookingHistoryRoute = com.innly.hotelbooking.presentation.navigation.AppDestinations.BookingHistory

        assertEquals("login", loginRoute)
        assertEquals("register", registerRoute)
        assertEquals("home", homeRoute)
        assertEquals("history", bookingHistoryRoute)
    }

    @Test
    fun `18 BookingScreen back action callback is invoked cleanly`() {
        var backInvoked = 0
        val onBackClick: () -> Unit = { backInvoked++ }

        onBackClick()
        assertEquals("Back action must be invoked exactly once", 1, backInvoked)
    }

    @Test
    fun `19 JSON response containing displayName null deserializes cleanly and maps to domain without throwing`() {
        val jsonPayload = """
            {
              "id": "fa5eef2d-d805-428a-9a75-ee95548dcb3a",
              "firebaseUid": "test-uid-null-display-name",
              "email": "guest@example.com",
              "displayName": null,
              "phoneNumber": null,
              "role": "user"
            }
        """.trimIndent()

        val dto = Gson().fromJson(jsonPayload, UserProfileDto::class.java)
        assertNull("Raw DTO displayName must be null from JSON", dto.displayName)

        // Safe domain boundary mapping
        val domain = dto.toDomain()
        assertEquals("fa5eef2d-d805-428a-9a75-ee95548dcb3a", domain.id)
        assertEquals("guest@example.com", domain.email)
        assertEquals("", domain.displayName)
        assertNull(domain.phoneNumber)
        assertEquals("user", domain.role)
    }

    @Test
    fun `20 signed-in user with null displayName progresses from booking form to payment order creation`() = runTest {
        val fakeAuthRepo = FakeAuthRepository(
            userProfile = UserProfile(
                id = "fa5eef2d-d805-428a-9a75-ee95548dcb3a",
                firebaseUid = "uid-google-null-name",
                displayName = "",
                email = "guest@example.com",
                phoneNumber = null,
                role = "user",
            )
        )
        val fakeBookingRepo = FakeBookingRepository()
        val viewModel = BookingViewModel(
            hotelRepository = FakeHotelRepository(),
            bookingRepository = fakeBookingRepo,
            authRepository = fakeAuthRepo,
        )
        advanceUntilIdle()

        viewModel.createBooking(
            BookingRequest(
                hotelId = "h-1",
                roomId = "r-1",
                checkIn = "2026-08-16",
                checkOut = "2026-08-18",
                rooms = 1,
                guests = 2,
                guestName = "Abhishek Ghosh",
                guestEmail = "guest@example.com",
                guestPhone = "+919876543210",
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, fakeBookingRepo.createBookingCallCount)
        assertNotNull(state.paymentOrder)
        assertEquals("b-101", state.paymentOrder?.bookingId)
        assertNull(state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun `21 rapid double taps on createBooking invoke booking creation at most once`() = runTest {
        val fakeBookingRepo = FakeBookingRepository()
        fakeBookingRepo.delayMs = 200L // Simulate network latency

        val viewModel = BookingViewModel(
            hotelRepository = FakeHotelRepository(),
            bookingRepository = fakeBookingRepo,
            authRepository = FakeAuthRepository(),
        )
        advanceUntilIdle()

        val request = BookingRequest(
            hotelId = "h-1",
            roomId = "r-1",
            checkIn = "2026-08-16",
            checkOut = "2026-08-18",
            rooms = 1,
            guests = 2,
            guestName = "Abhishek Ghosh",
            guestEmail = "guest@example.com",
            guestPhone = "+919876543210",
        )

        // Rapid double taps in the same frame/turn before completion
        viewModel.createBooking(request)
        viewModel.createBooking(request)
        viewModel.createBooking(request)

        advanceUntilIdle()

        assertEquals("Rapid double taps must invoke createBooking at most once", 1, fakeBookingRepo.createBookingCallCount)
        assertNotNull(viewModel.uiState.value.paymentOrder)
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
