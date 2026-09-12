package com.innly.hotelbooking

import com.innly.hotelbooking.domain.model.Availability
import com.innly.hotelbooking.domain.model.EligibleBooking
import com.innly.hotelbooking.domain.model.Hotel
import com.innly.hotelbooking.domain.model.HotelSearchFilter
import com.innly.hotelbooking.domain.model.MyReviewState
import com.innly.hotelbooking.domain.model.PrivateReview
import com.innly.hotelbooking.domain.model.PublicReviewPage
import com.innly.hotelbooking.domain.repository.FavoriteMutationResult
import com.innly.hotelbooking.domain.repository.FavoriteState
import com.innly.hotelbooking.domain.repository.HotelRepository
import com.innly.hotelbooking.presentation.reviews.ReviewScreenMode
import com.innly.hotelbooking.presentation.reviews.ReviewsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class WriteReviewViewModelTest {

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
        submitResult: Result<PrivateReview> = Result.success(
            PrivateReview(
                id = "rev-new-1",
                bookingId = "b-100",
                rating = 5,
                title = "Excellent",
                comment = "Very clean and friendly",
                status = "pending",
                createdAt = "2026-08-21",
                updatedAt = "2026-08-21",
                moderatedAt = null,
            )
        ),
        myReviewResult: Result<MyReviewState> = Result.success(
            MyReviewState(
                canWriteReview = true,
                eligibleBooking = EligibleBooking("b-100", "2026-08-10", "2026-08-12", "Deluxe"),
                myReviews = emptyList(),
                latestReview = null,
            )
        ),
    ): HotelRepository {
        return object : HotelRepository {
            override val favoriteState: StateFlow<FavoriteState> = MutableStateFlow(FavoriteState())
            override val favoriteIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())
            override val favoriteHotels: StateFlow<List<Hotel>> = MutableStateFlow(emptyList())
            override val inFlightFavoriteHotelIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())
            override val isFavoritesHydrated: StateFlow<Boolean> = MutableStateFlow(true)

            override suspend fun getHotels(filter: HotelSearchFilter): List<Hotel> = emptyList()
            override suspend fun getHotelDetail(hotelId: String): Hotel = throw NotImplementedError()
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

            override suspend fun getHotelReviews(hotelId: String, page: Int, limit: Int): PublicReviewPage =
                throw NotImplementedError()

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
            ): PrivateReview {
                return submitResult.getOrThrow()
            }
        }
    }

    @Test
    fun ratingSelection_updatesUiState() {
        val repo = createFakeHotelRepository()
        val viewModel = ReviewsViewModel(repo)

        assertEquals(5, viewModel.uiState.value.rating)
        viewModel.onRatingChanged(4)
        assertEquals(4, viewModel.uiState.value.rating)
        viewModel.onRatingChanged(1)
        assertEquals(1, viewModel.uiState.value.rating)
        // Invalid rating out of range should be ignored
        viewModel.onRatingChanged(0)
        assertEquals(1, viewModel.uiState.value.rating)
        viewModel.onRatingChanged(6)
        assertEquals(1, viewModel.uiState.value.rating)
    }

    @Test
    fun validation_enforcesTitleAndCommentLengthBounds() {
        val repo = createFakeHotelRepository()
        val viewModel = ReviewsViewModel(repo)

        assertFalse(viewModel.uiState.value.isFormValid)

        // Title too short (<2 chars)
        viewModel.onTitleChanged("A")
        viewModel.onCommentChanged("Valid comment text here")
        assertFalse(viewModel.uiState.value.isFormValid)

        // Valid title and comment
        viewModel.onTitleChanged("Good Stay")
        viewModel.onCommentChanged("Clean room and great food")
        assertTrue(viewModel.uiState.value.isFormValid)

        // Comment too short (<5 chars)
        viewModel.onCommentChanged("Hi")
        assertFalse(viewModel.uiState.value.isFormValid)
    }

    @Test
    fun submitReview_success_updatesIsSuccess() = runTest {
        val repo = createFakeHotelRepository()
        val viewModel = ReviewsViewModel(repo)

        viewModel.loadReviewContext("hotel-1")
        advanceUntilIdle()

        viewModel.onTitleChanged("Awesome stay")
        viewModel.onCommentChanged("Loved the entire experience!")
        viewModel.submitReview(hotelId = "hotel-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isSuccess)
        assertNotNull(state.submittedReview)
        assertEquals("pending", state.submittedReview?.status)
        assertNull(state.errorMessage)
    }

    @Test
    fun repeatStay_newerEligibleStay_withOlderRejectedReview_opensCleanFormWithoutOldReviewId() = runTest {
        var submittedBookingId: String? = null
        var submittedReviewId: String? = null

        val repeatStayState = MyReviewState(
            canWriteReview = true,
            eligibleBooking = EligibleBooking("b-newer-200", "2026-08-15", "2026-08-18", "Suite"),
            myReviews = listOf(
                PrivateReview("rev-old-rej", "b-old-100", 2, "Old bad title", "Old bad comment", "rejected", "2026-08-01", "2026-08-01", null)
            ),
            latestReview = PrivateReview("rev-old-rej", "b-old-100", 2, "Old bad title", "Old bad comment", "rejected", "2026-08-01", "2026-08-01", null),
        )

        val fakeRepo = object : HotelRepository by createFakeHotelRepository(myReviewResult = Result.success(repeatStayState)) {
            override suspend fun submitReview(
                hotelId: String,
                bookingId: String,
                reviewId: String?,
                rating: Int,
                title: String,
                comment: String,
            ): PrivateReview {
                submittedBookingId = bookingId
                submittedReviewId = reviewId
                return PrivateReview("rev-new", bookingId, rating, title, comment, "pending", "2026-08-21", "2026-08-21", null)
            }
        }

        val viewModel = ReviewsViewModel(fakeRepo)
        viewModel.loadReviewContext("hotel-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ReviewScreenMode.WRITE_NEW, state.mode)
        assertFalse(state.isResubmission)
        assertEquals("b-newer-200", state.targetBookingId)
        assertNull(state.targetReviewId)
        // Fields should be clean and fresh
        assertEquals("", state.title)
        assertEquals("", state.comment)
        assertEquals(5, state.rating)

        // Submit new review
        viewModel.onTitleChanged("Brand new stay review")
        viewModel.onCommentChanged("Everything was great during August visit")
        viewModel.submitReview("hotel-1")
        advanceUntilIdle()

        assertEquals("b-newer-200", submittedBookingId)
        assertNull(submittedReviewId) // MUST NOT pass the old rejected review ID!
    }

    @Test
    fun rejectedReview_noNewerStay_opensResubmissionWithPrefillAndSameReviewId() = runTest {
        var submittedBookingId: String? = null
        var submittedReviewId: String? = null

        val rejectedState = MyReviewState(
            canWriteReview = false,
            eligibleBooking = null,
            myReviews = listOf(
                PrivateReview("rev-rejected-99", "b-stay-99", 2, "Previous title", "Previous comment", "rejected", "2026-08-01", "2026-08-01", "2026-08-02")
            ),
            latestReview = PrivateReview("rev-rejected-99", "b-stay-99", 2, "Previous title", "Previous comment", "rejected", "2026-08-01", "2026-08-01", "2026-08-02"),
        )

        val fakeRepo = object : HotelRepository by createFakeHotelRepository(myReviewResult = Result.success(rejectedState)) {
            override suspend fun submitReview(
                hotelId: String,
                bookingId: String,
                reviewId: String?,
                rating: Int,
                title: String,
                comment: String,
            ): PrivateReview {
                submittedBookingId = bookingId
                submittedReviewId = reviewId
                return PrivateReview(reviewId ?: "err", bookingId, rating, title, comment, "pending", "2026-08-01", "2026-08-21", null)
            }
        }

        val viewModel = ReviewsViewModel(fakeRepo)
        viewModel.loadReviewContext("hotel-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ReviewScreenMode.RESUBMIT_REJECTED, state.mode)
        assertTrue(state.isResubmission)
        assertEquals("b-stay-99", state.targetBookingId)
        assertEquals("rev-rejected-99", state.targetReviewId)
        // Fields should be pre-filled from rejected review
        assertEquals("Previous title", state.title)
        assertEquals("Previous comment", state.comment)
        assertEquals(2, state.rating)

        // Resubmit review
        viewModel.onTitleChanged("Updated title with more details")
        viewModel.submitReview("hotel-1")
        advanceUntilIdle()

        assertEquals("b-stay-99", submittedBookingId)
        assertEquals("rev-rejected-99", submittedReviewId) // Reuses the EXACT same review ID!
    }

    @Test
    fun pendingOrApprovedReview_noNewerStay_setsCannotSubmitModeAndDisablesSubmission() = runTest {
        val pendingState = MyReviewState(
            canWriteReview = false,
            eligibleBooking = null,
            myReviews = listOf(
                PrivateReview("rev-pending", "b-1", 5, "Good", "Comment", "pending", "2026-08-20", "2026-08-20", null)
            ),
            latestReview = PrivateReview("rev-pending", "b-1", 5, "Good", "Comment", "pending", "2026-08-20", "2026-08-20", null),
        )

        val repo = createFakeHotelRepository(myReviewResult = Result.success(pendingState))
        val viewModel = ReviewsViewModel(repo)

        viewModel.loadReviewContext("hotel-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ReviewScreenMode.CANNOT_SUBMIT, state.mode)
        assertFalse(state.canSubmit)
        assertNull(state.targetBookingId)
        assertNull(state.targetReviewId)
    }

    @Test
    fun repeatStay_withPendingReview_andSeparateEligibleStay_usesSeparateEligibleBookingId_andDisallowsReviewingPendingBooking() = runTest {
        var submittedBookingId: String? = null
        var submittedReviewId: String? = null

        val repeatStayState = MyReviewState(
            canWriteReview = true,
            eligibleBooking = EligibleBooking("b-eligible-202", "2026-08-09", "2026-08-11", "Ocean Suite"),
            myReviews = listOf(
                PrivateReview("rev-pending-101", "b-pending-101", 5, "Pending Title", "Pending comment", "pending", "2026-08-14", "2026-08-14", null)
            ),
            latestReview = PrivateReview("rev-pending-101", "b-pending-101", 5, "Pending Title", "Pending comment", "pending", "2026-08-14", "2026-08-14", null),
        )

        val fakeRepo = object : HotelRepository by createFakeHotelRepository(myReviewResult = Result.success(repeatStayState)) {
            override suspend fun submitReview(
                hotelId: String,
                bookingId: String,
                reviewId: String?,
                rating: Int,
                title: String,
                comment: String,
            ): PrivateReview {
                submittedBookingId = bookingId
                submittedReviewId = reviewId
                return PrivateReview("rev-new-202", bookingId, rating, title, comment, "pending", "2026-08-22", "2026-08-22", null)
            }
        }

        val viewModel = ReviewsViewModel(fakeRepo)
        viewModel.loadReviewContext("hotel-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ReviewScreenMode.WRITE_NEW, state.mode)
        assertFalse(state.isResubmission)
        // Must use the separate eligible booking ID, NEVER the pending review's booking ID
        assertEquals("b-eligible-202", state.targetBookingId)
        assertNull(state.targetReviewId)
        // Clean form
        assertEquals("", state.title)
        assertEquals("", state.comment)

        viewModel.onTitleChanged("Second stay review")
        viewModel.onCommentChanged("Another delightful experience at the hotel")
        viewModel.submitReview("hotel-1")
        advanceUntilIdle()

        assertEquals("b-eligible-202", submittedBookingId)
        assertNull(submittedReviewId)
    }
}
