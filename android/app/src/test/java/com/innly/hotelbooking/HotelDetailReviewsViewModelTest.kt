package com.innly.hotelbooking

import com.innly.hotelbooking.domain.model.Availability
import com.innly.hotelbooking.domain.model.EligibleBooking
import com.innly.hotelbooking.domain.model.Hotel
import com.innly.hotelbooking.domain.model.HotelSearchFilter
import com.innly.hotelbooking.domain.model.MyReviewState
import com.innly.hotelbooking.domain.model.PrivateReview
import com.innly.hotelbooking.domain.model.PublicReview
import com.innly.hotelbooking.domain.model.PublicReviewPage
import com.innly.hotelbooking.domain.model.RatingSummary
import com.innly.hotelbooking.domain.repository.FavoriteMutationResult
import com.innly.hotelbooking.domain.repository.FavoriteState
import com.innly.hotelbooking.domain.repository.HotelRepository
import com.innly.hotelbooking.domain.usecase.GetHotelDetailUseCase
import com.innly.hotelbooking.presentation.hoteldetail.HotelDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HotelDetailReviewsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createFakeHotelRepository(
        reviewsResult: Result<PublicReviewPage> = Result.success(
            PublicReviewPage(
                items = listOf(
                    PublicReview(
                        id = "rev-1",
                        rating = 5,
                        title = "Great Hotel",
                        comment = "Loved everything",
                        createdAt = "2026-08-19",
                        authorName = "Abhishek G.",
                        isVerifiedStay = true,
                    )
                ),
                page = 1,
                limit = 10,
                total = 1,
                ratingSummary = RatingSummary(
                    avgRating = 5.0,
                    reviewCount = 1,
                    ratingDistribution = mapOf("5" to 1, "4" to 0, "3" to 0, "2" to 0, "1" to 0),
                ),
            )
        ),
        myReviewResult: Result<MyReviewState> = Result.success(
            MyReviewState(
                canWriteReview = true,
                eligibleBooking = EligibleBooking(
                    bookingId = "b-100",
                    checkIn = "2026-08-10",
                    checkOut = "2026-08-12",
                    roomName = "Deluxe King",
                ),
                myReviews = emptyList(),
                latestReview = null,
            )
        ),
        hotelDetailProvider: ((String) -> Result<Hotel>)? = null,
    ): HotelRepository {
        val fakeFavoriteState = MutableStateFlow(FavoriteState())
        return object : HotelRepository {
            override val favoriteState: StateFlow<FavoriteState> = fakeFavoriteState.asStateFlow()
            override val favoriteIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())
            override val favoriteHotels: StateFlow<List<Hotel>> = MutableStateFlow(emptyList())
            override val inFlightFavoriteHotelIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())
            override val isFavoritesHydrated: StateFlow<Boolean> = MutableStateFlow(true)

            override suspend fun getHotels(filter: HotelSearchFilter): List<Hotel> = emptyList()
            override suspend fun getHotelDetail(hotelId: String): Hotel {
                if (hotelDetailProvider != null) {
                    return hotelDetailProvider(hotelId).getOrThrow()
                }
                return Hotel(
                    id = hotelId,
                    name = "The Marine Grand",
                    description = "Luxury stay",
                    city = "Mumbai",
                    address = "Marine Drive",
                    rating = 4.8,
                    reviewCount = 1,
                    startingPrice = 5000,
                    thumbnailUrl = "",
                    images = emptyList(),
                    amenities = listOf("wifi"),
                    isFavorite = false,
                    rooms = emptyList(),
                )
            }

            override suspend fun getAvailability(
                hotelId: String,
                roomId: String,
                checkIn: String,
                checkOut: String,
                rooms: Int,
            ): Availability = throw NotImplementedError()

            override suspend fun getFavorites(): List<Hotel> = emptyList()
            override suspend fun syncFavorites(): List<Hotel> = emptyList()
            override suspend fun toggleFavoriteOptimistic(hotel: Hotel): FavoriteMutationResult =
                FavoriteMutationResult.Success(true)

            override suspend fun restoreFavoriteOptimistic(hotel: Hotel, originalIndex: Int): FavoriteMutationResult =
                FavoriteMutationResult.Success(true)

            override suspend fun getHotelReviews(hotelId: String, page: Int, limit: Int): PublicReviewPage {
                return reviewsResult.getOrThrow()
            }

            override suspend fun getMyReview(hotelId: String): MyReviewState {
                return myReviewResult.getOrThrow()
            }

            override suspend fun submitReview(
                hotelId: String,
                bookingId: String,
                reviewId: String?,
                rating: Int,
                title: String,
                comment: String,
            ): PrivateReview = throw NotImplementedError()
        }
    }

    @Test
    fun loadHotel_populatesHotelReviewsAndMyReviewState() = runTest {
        val repo = createFakeHotelRepository()
        val useCase = GetHotelDetailUseCase(repo)
        val viewModel = HotelDetailViewModel(useCase, repo)

        viewModel.loadHotel("hotel-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isReviewsLoading)
        assertEquals("The Marine Grand", state.hotel?.name)
        assertEquals(1, state.reviews.size)
        assertEquals("rev-1", state.reviews[0].id)
        assertEquals(5.0, state.ratingSummary?.avgRating ?: 0.0, 0.01)
        assertTrue(state.myReviewState?.canWriteReview == true)
        assertEquals("b-100", state.myReviewState?.eligibleBooking?.bookingId)
    }

    @Test
    fun loadNextReviewPage_appendsDeduplicatedReviews() = runTest {
        val page1Reviews = PublicReviewPage(
            items = listOf(
                PublicReview("rev-1", 5, "Good", "Text", "2026-08-19", "User A", true)
            ),
            page = 1,
            limit = 1,
            total = 2,
            ratingSummary = RatingSummary(5.0, 2, mapOf("5" to 2)),
        )
        val page2Reviews = PublicReviewPage(
            items = listOf(
                PublicReview("rev-2", 4, "Nice", "Text", "2026-08-18", "User B", false)
            ),
            page = 2,
            limit = 1,
            total = 2,
            ratingSummary = RatingSummary(5.0, 2, mapOf("5" to 2)),
        )

        var requestedPage = 1
        val fakeRepo = object : HotelRepository by createFakeHotelRepository() {
            override suspend fun getHotelReviews(hotelId: String, page: Int, limit: Int): PublicReviewPage {
                requestedPage = page
                return if (page == 1) page1Reviews else page2Reviews
            }
        }

        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(fakeRepo), fakeRepo)
        viewModel.loadHotel("hotel-1")
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.reviews.size)
        assertTrue(viewModel.uiState.value.canLoadMoreReviews)

        viewModel.loadNextReviewPage()
        advanceUntilIdle()

        assertEquals(2, requestedPage)
        assertEquals(2, viewModel.uiState.value.reviews.size)
        assertEquals("rev-1", viewModel.uiState.value.reviews[0].id)
        assertEquals("rev-2", viewModel.uiState.value.reviews[1].id)
        assertFalse(viewModel.uiState.value.canLoadMoreReviews)
    }

    @Test
    fun repeatStay_exposesOlderReviewAndNewerEligibleBooking() = runTest {
        val repeatStayState = MyReviewState(
            canWriteReview = true,
            eligibleBooking = EligibleBooking("b-newer", "2026-08-15", "2026-08-18", "Deluxe"),
            myReviews = listOf(
                PrivateReview("rev-old", "b-older", 5, "July stay", "Great", "approved", "2026-07-20", "2026-07-20", null)
            ),
            latestReview = PrivateReview("rev-old", "b-older", 5, "July stay", "Great", "approved", "2026-07-20", "2026-07-20", null),
        )

        val repo = createFakeHotelRepository(myReviewResult = Result.success(repeatStayState))
        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(repo), repo)

        viewModel.loadHotel("hotel-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.myReviewState?.canWriteReview == true)
        assertEquals("b-newer", state.myReviewState?.eligibleBooking?.bookingId)
        assertEquals(1, state.myReviewState?.myReviews?.size)
        assertEquals("approved", state.myReviewState?.myReviews?.get(0)?.status)
    }

    @Test
    fun refreshReviewsAndAuthorState_updatesAuthorStateAndReviewsWithoutClearingHotel() = runTest {
        val initialState = MyReviewState(
            canWriteReview = true,
            eligibleBooking = EligibleBooking("b-100", "2026-08-10", "2026-08-12", "Deluxe King"),
            myReviews = emptyList(),
            latestReview = null,
        )
        val postSubmissionState = MyReviewState(
            canWriteReview = false,
            eligibleBooking = null,
            myReviews = listOf(
                PrivateReview("rev-new", "b-100", 5, "Lovely stay", "Loved it", "pending", "2026-08-21", "2026-08-21", null)
            ),
            latestReview = PrivateReview("rev-new", "b-100", 5, "Lovely stay", "Loved it", "pending", "2026-08-21", "2026-08-21", null),
        )

        var currentMyReviewState = initialState
        var myReviewFetchCount = 0
        var reviewsFetchCount = 0

        val fakeRepo = object : HotelRepository by createFakeHotelRepository() {
            override suspend fun getMyReview(hotelId: String): MyReviewState {
                myReviewFetchCount++
                return currentMyReviewState
            }

            override suspend fun getHotelReviews(hotelId: String, page: Int, limit: Int): PublicReviewPage {
                reviewsFetchCount++
                // Public reviews remain empty while review is pending
                return PublicReviewPage(
                    items = emptyList(),
                    page = 1,
                    limit = 10,
                    total = 0,
                    ratingSummary = RatingSummary(0.0, 0, emptyMap()),
                )
            }
        }

        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(fakeRepo), fakeRepo)
        viewModel.loadHotel("hotel-1")
        advanceUntilIdle()

        // Before submission
        assertEquals(1, myReviewFetchCount)
        assertEquals(1, reviewsFetchCount)
        assertTrue(viewModel.uiState.value.myReviewState?.canWriteReview == true)
        assertEquals("The Marine Grand", viewModel.uiState.value.hotel?.name)

        // Simulate review submission occurring on ReviewsScreen
        currentMyReviewState = postSubmissionState

        // One-shot refresh event triggered on returning to HotelDetailScreen
        viewModel.refreshReviewsAndAuthorState("hotel-1")
        advanceUntilIdle()

        // After refresh
        assertEquals(2, myReviewFetchCount)
        assertEquals(2, reviewsFetchCount)
        val updatedState = viewModel.uiState.value
        assertFalse(updatedState.myReviewState?.canWriteReview == true)
        assertNull(updatedState.myReviewState?.eligibleBooking)
        assertEquals(1, updatedState.myReviewState?.myReviews?.size)
        assertEquals("pending", updatedState.myReviewState?.myReviews?.first()?.status)
        // Public rating summary remains unchanged (0.0 / 0 reviews) while review is pending
        assertEquals(0.0, updatedState.ratingSummary?.avgRating ?: 0.0, 0.01)
        assertEquals(0, updatedState.ratingSummary?.reviewCount)
        assertEquals(0, updatedState.reviews.size)
        // Hotel details was preserved without reloading
        assertEquals("The Marine Grand", updatedState.hotel?.name)
    }

    @Test
    fun pendingReview_hidesWriteReview_andPreservesPendingStateAcrossReentry() = runTest {
        val pendingReviewState = MyReviewState(
            canWriteReview = false,
            eligibleBooking = null,
            myReviews = listOf(
                PrivateReview("rev-pending", "b-55", 4, "Nice Place", "Good stay", "pending", "2026-08-21", "2026-08-21", null)
            ),
            latestReview = PrivateReview("rev-pending", "b-55", 4, "Nice Place", "Good stay", "pending", "2026-08-21", "2026-08-21", null),
        )

        val repo = createFakeHotelRepository(myReviewResult = Result.success(pendingReviewState))
        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(repo), repo)

        viewModel.loadHotel("hotel-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.myReviewState?.canWriteReview == true)
        assertNull(state.myReviewState?.eligibleBooking)
        assertEquals("pending", state.myReviewState?.myReviews?.first()?.status)
    }

    @Test
    fun repeatStay_pendingReview_andSeparateEligibleStay_exposesBothInState() = runTest {
        val repeatStayWithPending = MyReviewState(
            canWriteReview = true,
            eligibleBooking = EligibleBooking(
                bookingId = "b-eligible-2",
                checkIn = "2026-08-09",
                checkOut = "2026-08-11",
                roomName = "Sea Suite",
            ),
            myReviews = listOf(
                PrivateReview("rev-pending-1", "b-pending-1", 5, "Grand Lotus Stay", "Loved it", "pending", "2026-08-14", "2026-08-14", null)
            ),
            latestReview = PrivateReview("rev-pending-1", "b-pending-1", 5, "Grand Lotus Stay", "Loved it", "pending", "2026-08-14", "2026-08-14", null),
        )

        val repo = createFakeHotelRepository(myReviewResult = Result.success(repeatStayWithPending))
        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(repo), repo)

        viewModel.loadHotel("hotel-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // 1. Author review state exposes the pending review
        assertEquals(1, state.myReviewState?.myReviews?.size)
        val pendingReview = state.myReviewState?.myReviews?.first()
        assertEquals("rev-pending-1", pendingReview?.id)
        assertEquals("b-pending-1", pendingReview?.bookingId)
        assertEquals("pending", pendingReview?.status)

        // 2. Author review state simultaneously exposes separate eligible booking
        assertTrue(state.myReviewState?.canWriteReview == true)
        assertEquals("b-eligible-2", state.myReviewState?.eligibleBooking?.bookingId)
        assertEquals("Sea Suite", state.myReviewState?.eligibleBooking?.roomName)
    }

    @Test
    fun pendingReview_doesNotAffectPublicRatingOrReviewCount() = runTest {
        val pendingReviewState = MyReviewState(
            canWriteReview = false,
            eligibleBooking = null,
            myReviews = listOf(
                PrivateReview("rev-pending-1", "b-pending-1", 5, "Amazing", "Awesome", "pending", "2026-08-14", "2026-08-14", null)
            ),
            latestReview = PrivateReview("rev-pending-1", "b-pending-1", 5, "Amazing", "Awesome", "pending", "2026-08-14", "2026-08-14", null),
        )

        // Public repository returns 0 approved reviews
        val publicPage = PublicReviewPage(
            items = emptyList(),
            page = 1,
            limit = 10,
            total = 0,
            ratingSummary = RatingSummary(0.0, 0, emptyMap()),
        )

        val repo = createFakeHotelRepository(
            reviewsResult = Result.success(publicPage),
            myReviewResult = Result.success(pendingReviewState),
        )
        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(repo), repo)

        viewModel.loadHotel("hotel-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Public reviews list is empty and ratings unaffected
        assertEquals(0, state.reviews.size)
        assertEquals(0.0, state.ratingSummary?.avgRating ?: 0.0, 0.01)
        assertEquals(0, state.ratingSummary?.reviewCount)
        // Private pending review is preserved
        assertEquals("pending", state.myReviewState?.myReviews?.first()?.status)
    }

    @Test
    fun pendingOnlyState_withoutEligibleBooking_hidesCanWriteReview() = runTest {
        val pendingOnlyState = MyReviewState(
            canWriteReview = false,
            eligibleBooking = null,
            myReviews = listOf(
                PrivateReview("rev-pending-1", "b-stay-1", 5, "Title", "Comment", "pending", "2026-08-14", "2026-08-14", null)
            ),
            latestReview = PrivateReview("rev-pending-1", "b-stay-1", 5, "Title", "Comment", "pending", "2026-08-14", "2026-08-14", null),
        )

        val repo = createFakeHotelRepository(myReviewResult = Result.success(pendingOnlyState))
        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(repo), repo)

        viewModel.loadHotel("hotel-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.myReviewState?.canWriteReview == true)
        assertNull(state.myReviewState?.eligibleBooking)
        assertEquals(1, state.myReviewState?.myReviews?.size)
    }

    @Test
    fun loadHotel_initialFailure_setsFriendlyErrorMessage_andNullHotel() = runTest {
        val failingRepo = createFakeHotelRepository(
            hotelDetailProvider = { Result.failure(java.io.IOException("Failed to connect to server")) }
        )
        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(failingRepo), failingRepo)

        viewModel.loadHotel("hotel-fail-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.hotel)
        assertNull(state.rawHotel)
        assertEquals("Unable to load hotel details. Please try again.", state.errorMessage)
    }

    @Test
    fun loadHotel_errorThenRetry_transitionsThroughLoading_andLoadsSameHotel() = runTest {
        var callCount = 0
        var requestedHotelId: String? = null
        val expectedHotel = Hotel(
            id = "hotel-retry-42",
            name = "Recovered Royal Palace",
            description = "Recovered after network reconnect",
            city = "Jaipur",
            address = "Palace Road",
            rating = 4.9,
            reviewCount = 12,
            startingPrice = 8500,
            thumbnailUrl = "",
            images = emptyList(),
            amenities = listOf("wifi", "pool"),
            isFavorite = false,
            rooms = emptyList(),
        )

        val recoveringRepo = createFakeHotelRepository(
            hotelDetailProvider = { hotelId ->
                callCount++
                requestedHotelId = hotelId
                if (callCount == 1) {
                    Result.failure(java.io.IOException("Failed to connect to host"))
                } else {
                    Result.success(expectedHotel)
                }
            }
        )
        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(recoveringRepo), recoveringRepo)

        // Step 1: Initial load fails
        viewModel.loadHotel("hotel-retry-42")
        advanceUntilIdle()

        assertEquals(1, callCount)
        assertEquals("hotel-retry-42", requestedHotelId)
        val errorState = viewModel.uiState.value
        assertFalse(errorState.isLoading)
        assertNull(errorState.hotel)
        assertEquals("Unable to load hotel details. Please try again.", errorState.errorMessage)

        // Step 2: Retry for the exact same hotel ID
        viewModel.loadHotel("hotel-retry-42")
        // Immediate synchronous check: in-flight state is loading and error message cleared
        val loadingState = viewModel.uiState.value
        assertTrue("State should transition to loading = true on retry", loadingState.isLoading)
        assertNull("Error message should be cleared when retry starts", loadingState.errorMessage)

        // Step 3: Advance coroutines to complete the retried call
        advanceUntilIdle()

        assertEquals(2, callCount)
        assertEquals("hotel-retry-42", requestedHotelId)
        val successState = viewModel.uiState.value
        assertFalse(successState.isLoading)
        assertNull(successState.errorMessage)
        assertNotNull(successState.hotel)
        assertEquals("hotel-retry-42", successState.hotel?.id)
        assertEquals("Recovered Royal Palace", successState.hotel?.name)
    }

    @Test
    fun loadHotel_whileLoading_inFlightGuardPreventsConcurrentRequests() = runTest {
        var invocations = 0
        val slowRepo = createFakeHotelRepository(
            hotelDetailProvider = { hotelId ->
                invocations++
                Result.success(
                    Hotel(
                        id = hotelId,
                        name = "Hotel Guarded",
                        description = "Guarded stay",
                        city = "Delhi",
                        address = "Ring Road",
                        rating = 4.5,
                        reviewCount = 2,
                        startingPrice = 4000,
                        thumbnailUrl = "",
                        images = emptyList(),
                        amenities = emptyList(),
                        isFavorite = false,
                        rooms = emptyList(),
                    )
                )
            }
        )
        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(slowRepo), slowRepo)

        // First call starts loading
        viewModel.loadHotel("hotel-guard-1")
        assertTrue("isLoading should be true after first call", viewModel.uiState.value.isLoading)

        // Second call while first call is in flight should be rejected by the guard
        viewModel.loadHotel("hotel-guard-1")
        // Third call for a different ID while in flight should also be rejected
        viewModel.loadHotel("hotel-guard-2")

        advanceUntilIdle()

        // Only exactly 1 request was dispatched
        assertEquals(1, invocations)
        assertEquals("hotel-guard-1", viewModel.uiState.value.hotel?.id)
    }

    @Test
    fun loadHotel_refreshFailure_preservesLoadedHotel_andSetsInlineErrorMessage() = runTest {
        var callCount = 0
        val initialHotel = Hotel(
            id = "hotel-preserve-1",
            name = "Heritage Haveli",
            description = "Preserved on refresh failure",
            city = "Udaipur",
            address = "Lake View",
            rating = 4.9,
            reviewCount = 50,
            startingPrice = 12000,
            thumbnailUrl = "",
            images = emptyList(),
            amenities = listOf("wifi", "spa"),
            isFavorite = true,
            rooms = emptyList(),
        )

        val repo = createFakeHotelRepository(
            hotelDetailProvider = {
                callCount++
                if (callCount == 1) {
                    Result.success(initialHotel)
                } else {
                    Result.failure(java.io.IOException("Failed to connect to backend"))
                }
            }
        )
        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(repo), repo)

        // Initial load succeeds
        viewModel.loadHotel("hotel-preserve-1")
        advanceUntilIdle()

        assertEquals(1, callCount)
        val populatedState = viewModel.uiState.value
        assertNotNull(populatedState.hotel)
        assertEquals("hotel-preserve-1", populatedState.hotel?.id)
        assertNull(populatedState.errorMessage)

        // Subsequent refresh fails
        viewModel.loadHotel("hotel-preserve-1")
        advanceUntilIdle()

        assertEquals(2, callCount)
        val refreshFailedState = viewModel.uiState.value
        assertFalse(refreshFailedState.isLoading)
        // Loaded hotel is preserved, never nulled out
        assertNotNull("Hotel content must remain visible when a later refresh fails", refreshFailedState.hotel)
        assertEquals("hotel-preserve-1", refreshFailedState.hotel?.id)
        assertEquals("Heritage Haveli", refreshFailedState.hotel?.name)
        // Inline friendly error message is displayed
        assertEquals("Unable to load hotel details. Please try again.", refreshFailedState.errorMessage)
    }

    @Test
    fun loadHotel_withDefaultFallback_producesHotelDetailsWordingOnNetworkFailure() = runTest {
        val repo = createFakeHotelRepository(
            hotelDetailProvider = {
                Result.failure(java.net.ConnectException("Failed to connect to /10.0.2.2:8080"))
            }
        )
        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(repo), repo)

        viewModel.loadHotel("hotel-fallback-test")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.hotel)
        assertEquals("Unable to load hotel details. Please try again.", state.errorMessage)
    }

    @Test
    fun loadHotel_withRoomFallback_producesRoomDetailsWordingOnNetworkFailure() = runTest {
        val repo = createFakeHotelRepository(
            hotelDetailProvider = {
                Result.failure(java.net.ConnectException("Failed to connect to /10.0.2.2:8080"))
            }
        )
        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(repo), repo)

        viewModel.loadHotel(
            hotelId = "hotel-fallback-test",
            fallbackErrorMessage = "Unable to load room details. Please try again.",
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.hotel)
        assertEquals("Unable to load room details. Please try again.", state.errorMessage)
    }

    @Test
    fun loadHotel_withLegitimateBusinessMessage_preservesBusinessMessageIntact() = runTest {
        val jsonMediaType = "application/json".toMediaType()
        val errorJson = """{"message":"Selected room is sold out for these dates"}"""
        val response = retrofit2.Response.error<Any>(400, errorJson.toResponseBody(jsonMediaType))
        val httpException = retrofit2.HttpException(response)

        val repo = createFakeHotelRepository(
            hotelDetailProvider = {
                Result.failure(httpException)
            }
        )
        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(repo), repo)

        // Even when Room Details requests room fallback wording, legitimate business error is preserved!
        viewModel.loadHotel(
            hotelId = "hotel-business-msg-test",
            fallbackErrorMessage = "Unable to load room details. Please try again.",
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Selected room is sold out for these dates", state.errorMessage)
    }
}
