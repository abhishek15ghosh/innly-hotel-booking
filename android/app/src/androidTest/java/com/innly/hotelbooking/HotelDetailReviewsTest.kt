package com.innly.hotelbooking

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.innly.hotelbooking.presentation.hoteldetail.HotelDetailScreen
import com.innly.hotelbooking.presentation.hoteldetail.HotelDetailViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HotelDetailReviewsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createFakeHotelRepository(
        hotelDetailProvider: ((String) -> Result<Hotel>)? = null,
    ): HotelRepository {
        return object : HotelRepository {
            override val favoriteState: StateFlow<FavoriteState> = MutableStateFlow(FavoriteState())
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
                    description = "Luxury sea-facing property",
                    city = "Mumbai",
                    address = "Marine Drive",
                    rating = 4.7,
                    reviewCount = 1,
                    startingPrice = 5400,
                    thumbnailUrl = "",
                    images = emptyList(),
                    amenities = listOf("wifi", "pool"),
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
                return PublicReviewPage(
                    items = listOf(
                        PublicReview(
                            id = "rev-1",
                            rating = 5,
                            title = "Exceptional View",
                            comment = "Waking up to the sea view was magnificent.",
                            createdAt = "2026-08-18",
                            authorName = "Abhishek G.",
                            isVerifiedStay = true,
                        )
                    ),
                    page = 1,
                    limit = 10,
                    total = 1,
                    ratingSummary = RatingSummary(
                        avgRating = 4.7,
                        reviewCount = 1,
                        ratingDistribution = mapOf("5" to 1, "4" to 0, "3" to 0, "2" to 0, "1" to 0),
                    ),
                )
            }

            override suspend fun getMyReview(hotelId: String): MyReviewState {
                return MyReviewState(
                    canWriteReview = true,
                    eligibleBooking = EligibleBooking("b-100", "2026-08-10", "2026-08-12", "Deluxe King"),
                    myReviews = emptyList(),
                    latestReview = null,
                )
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
    fun hotelDetail_rendersRatingBreakdown_andVerifiedStayBadge() {
        val repo = createFakeHotelRepository()
        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(repo), repo)

        composeTestRule.setContent {
            MaterialTheme {
                HotelDetailScreen(
                    hotelId = "hotel-1",
                    onBackClick = {},
                    onReviewsClick = {},
                    onRoomClick = { _, _ -> },
                    onBookRoom = { _, _ -> },
                    viewModel = viewModel,
                )
            }
        }

        // Verify Rating Breakdown Card is displayed
        composeTestRule.onNodeWithTag("rating_breakdown_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Guest Reviews & Ratings").assertIsDisplayed()

        // Scroll Review Card into full view
        composeTestRule.onNodeWithTag("review_card_rev-1").performScrollTo()
        composeTestRule.onRoot().performTouchInput { swipeUp() }

        // Verify Review Card and Verified Stay Badge
        composeTestRule.onNodeWithTag("review_card_rev-1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Abhishek G.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Verified Stay").assertIsDisplayed()
        composeTestRule.onNodeWithText("Exceptional View").assertIsDisplayed()
    }

    @Test
    fun hotelDetail_rendersPendingModerationCard_whenReviewPending() {
        val baseRepo = createFakeHotelRepository()
        val pendingRepo = object : HotelRepository by baseRepo {
            override suspend fun getMyReview(hotelId: String): MyReviewState {
                return MyReviewState(
                    canWriteReview = false,
                    eligibleBooking = null,
                    myReviews = listOf(
                        PrivateReview("rev-pending", "b-100", 5, "Great stay", "Loved it", "pending", "2026-08-21", "2026-08-21", null)
                    ),
                    latestReview = PrivateReview("rev-pending", "b-100", 5, "Great stay", "Loved it", "pending", "2026-08-21", "2026-08-21", null),
                )
            }
        }
        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(pendingRepo), pendingRepo)

        composeTestRule.setContent {
            MaterialTheme {
                HotelDetailScreen(
                    hotelId = "hotel-1",
                    onBackClick = {},
                    onReviewsClick = {},
                    onRoomClick = { _, _ -> },
                    onBookRoom = { _, _ -> },
                    viewModel = viewModel,
                )
            }
        }

        // Verify Pending Moderation Card is displayed and Write review is not displayed
        composeTestRule.onNodeWithTag("pending_moderation_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Pending Moderation").assertIsDisplayed()
        composeTestRule.onNodeWithText("Great stay").assertIsDisplayed()
    }

    @Test
    fun hotelDetail_rendersPendingReview_andSeparateEligibleStay_simultaneously() {
        val baseRepo = createFakeHotelRepository()
        val repeatStayRepo = object : HotelRepository by baseRepo {
            override suspend fun getMyReview(hotelId: String): MyReviewState {
                return MyReviewState(
                    canWriteReview = true,
                    eligibleBooking = EligibleBooking(
                        bookingId = "b-200",
                        checkIn = "2026-08-09",
                        checkOut = "2026-08-11",
                        roomName = "Sea View Deluxe",
                    ),
                    myReviews = listOf(
                        PrivateReview("rev-pending-1", "b-100", 5, "First Stay Review", "Wonderful", "pending", "2026-08-14", "2026-08-14", null)
                    ),
                    latestReview = PrivateReview("rev-pending-1", "b-100", 5, "First Stay Review", "Wonderful", "pending", "2026-08-14", "2026-08-14", null),
                )
            }
        }
        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(repeatStayRepo), repeatStayRepo)

        composeTestRule.setContent {
            MaterialTheme {
                HotelDetailScreen(
                    hotelId = "hotel-1",
                    onBackClick = {},
                    onReviewsClick = {},
                    onRoomClick = { _, _ -> },
                    onBookRoom = { _, _ -> },
                    viewModel = viewModel,
                )
            }
        }

        // 1. Verify Existing Review Card with Pending Moderation status & Title
        composeTestRule.onNodeWithTag("pending_moderation_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Pending Moderation").assertIsDisplayed()
        composeTestRule.onNodeWithText("First Stay Review").assertIsDisplayed()

        // 2. Verify Separate Eligible Booking Card for the second stay
        composeTestRule.onNodeWithTag("eligible_booking_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Review another completed stay").assertIsDisplayed()
        composeTestRule.onNodeWithText("Write review").assertIsDisplayed()
    }

    @Test
    fun hotelDetail_fullScreenError_verifiesBackCallback_andRetryCallback() {
        var backClicked = false
        var requestedHotelId: String? = null
        val failingRepo = createFakeHotelRepository(
            hotelDetailProvider = { hotelId ->
                requestedHotelId = hotelId
                Result.failure(java.io.IOException("Failed to connect to backend"))
            }
        )
        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(failingRepo), failingRepo)

        composeTestRule.setContent {
            MaterialTheme {
                HotelDetailScreen(
                    hotelId = "hotel-err-1",
                    onBackClick = { backClicked = true },
                    onReviewsClick = {},
                    onRoomClick = { _, _ -> },
                    onBookRoom = { _, _ -> },
                    viewModel = viewModel,
                )
            }
        }

        // Verify full screen error state elements
        composeTestRule.onNodeWithTag("hotel_detail_error_back_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Unable to load hotel details. Please try again.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hotel_detail_retry_button").assertIsDisplayed()

        // Verify Back callback is invoked
        composeTestRule.onNodeWithTag("hotel_detail_error_back_button").performClick()
        assertTrue("Back button should trigger onBackClick callback", backClicked)

        // Verify Retry button is clickable and triggers load for the same hotel ID
        requestedHotelId = null
        composeTestRule.onNodeWithTag("hotel_detail_retry_button").performClick()
        assertEquals("hotel-err-1", requestedHotelId)
    }

    @Test
    fun hotelDetail_errorRecovery_transitionsFromErrorToPopulated_withSingleBackButton() {
        var attempts = 0
        var retriedId: String? = null
        val recoveryHotel = Hotel(
            id = "hotel-retry-1",
            name = "Royal Heritage Palace",
            description = "Recovered hotel",
            city = "Jaipur",
            address = "Amer Road",
            rating = 4.8,
            reviewCount = 10,
            startingPrice = 7500,
            thumbnailUrl = "",
            images = emptyList(),
            amenities = listOf("wifi"),
            isFavorite = false,
            rooms = emptyList(),
        )

        val recoveringRepo = createFakeHotelRepository(
            hotelDetailProvider = { hotelId ->
                attempts++
                retriedId = hotelId
                if (attempts == 1) {
                    Result.failure(java.io.IOException("Failed to connect to backend"))
                } else {
                    Result.success(recoveryHotel)
                }
            }
        )
        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(recoveringRepo), recoveringRepo)

        composeTestRule.setContent {
            MaterialTheme {
                HotelDetailScreen(
                    hotelId = "hotel-retry-1",
                    onBackClick = {},
                    onReviewsClick = {},
                    onRoomClick = { _, _ -> },
                    onBookRoom = { _, _ -> },
                    viewModel = viewModel,
                )
            }
        }

        // State 1: Full-screen error state with exactly one back button
        composeTestRule.onNodeWithTag("hotel_detail_error_back_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hotel_detail_retry_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hotel_detail_back_button").assertDoesNotExist()

        // Action: Tap Retry
        composeTestRule.onNodeWithTag("hotel_detail_retry_button").performClick()

        // State 2: Populated state - error disappears, hotel appears, exactly one back button
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hotel_detail_error_back_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hotel_detail_retry_button").assertDoesNotExist()
        composeTestRule.onNodeWithText("Royal Heritage Palace").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hotel_detail_back_button").assertIsDisplayed()
        assertEquals("hotel-retry-1", retriedId)
    }

    @Test
    fun hotelDetail_refreshFailure_preservesLoadedHotel_andDisplaysInlineError() {
        var attempts = 0
        val loadedHotel = Hotel(
            id = "hotel-live-1",
            name = "The Marina Bay",
            description = "Oceanfront luxury",
            city = "Mumbai",
            address = "Marine Drive",
            rating = 4.9,
            reviewCount = 25,
            startingPrice = 9000,
            thumbnailUrl = "",
            images = emptyList(),
            amenities = listOf("wifi", "pool"),
            isFavorite = false,
            rooms = emptyList(),
        )

        val repo = createFakeHotelRepository(
            hotelDetailProvider = {
                attempts++
                if (attempts == 1) {
                    Result.success(loadedHotel)
                } else {
                    Result.failure(java.io.IOException("Failed to connect to backend"))
                }
            }
        )
        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(repo), repo)

        composeTestRule.setContent {
            MaterialTheme {
                HotelDetailScreen(
                    hotelId = "hotel-live-1",
                    onBackClick = {},
                    onReviewsClick = {},
                    onRoomClick = { _, _ -> },
                    onBookRoom = { _, _ -> },
                    viewModel = viewModel,
                )
            }
        }

        // Verify hotel initially loaded
        composeTestRule.onNodeWithText("The Marina Bay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hotel_detail_back_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hotel_detail_error_back_button").assertDoesNotExist()

        // Trigger a background refresh that fails
        viewModel.loadHotel("hotel-live-1")
        composeTestRule.waitForIdle()

        // Verify loaded hotel remains visible (not wiped out by refresh failure)
        composeTestRule.onNodeWithText("The Marina Bay").assertIsDisplayed()
        // Exactly one back button remains visible
        composeTestRule.onNodeWithTag("hotel_detail_back_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hotel_detail_error_back_button").assertDoesNotExist()
        // Inline recoverable error card is visible with Retry button
        composeTestRule.onNodeWithTag("hotel_detail_inline_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hotel_detail_inline_retry_button").assertIsDisplayed()
    }
}
