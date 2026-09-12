package com.innly.hotelbooking

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.innly.hotelbooking.domain.model.Availability
import com.innly.hotelbooking.domain.model.Hotel
import com.innly.hotelbooking.domain.model.HotelSearchFilter
import com.innly.hotelbooking.domain.model.MyReviewState
import com.innly.hotelbooking.domain.model.PrivateReview
import com.innly.hotelbooking.domain.model.PublicReviewPage
import com.innly.hotelbooking.domain.repository.FavoriteMutationResult
import com.innly.hotelbooking.domain.repository.FavoriteState
import com.innly.hotelbooking.domain.repository.HotelRepository
import com.innly.hotelbooking.domain.usecase.GetHotelsUseCase
import com.innly.hotelbooking.presentation.home.HomeScreen
import com.innly.hotelbooking.presentation.home.HomeViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class FakeHotelRepository : HotelRepository {
        var shouldFail = false
        var failureException: Throwable = java.net.ConnectException("Failed to connect to /10.0.2.2:8080")
        var hotels = listOf(
            Hotel(
                id = "h-1",
                name = "Grand Palace Hotel",
                description = "Luxury hotel in Delhi",
                city = "New Delhi",
                address = "1 Connaught Place",
                rating = 4.8,
                reviewCount = 120,
                startingPrice = 4500,
                thumbnailUrl = "https://example.com/h1.jpg",
                images = emptyList(),
                amenities = emptyList(),
                isFavorite = false,
            )
        )

        private val _favoriteState = MutableStateFlow(
            FavoriteState(
                favoriteIds = emptySet(),
                favoriteHotels = emptyList(),
                isHydrated = true,
                activeSessionUid = "test-uid",
            )
        )
        override val favoriteState: StateFlow<FavoriteState> = _favoriteState
        override val favoriteIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())
        override val favoriteHotels: StateFlow<List<Hotel>> = MutableStateFlow(emptyList())
        override val inFlightFavoriteHotelIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())
        override val isFavoritesHydrated: StateFlow<Boolean> = MutableStateFlow(true)

        override suspend fun getHotels(filter: HotelSearchFilter): List<Hotel> {
            if (shouldFail) throw failureException
            return hotels
        }

        override suspend fun getHotelDetail(hotelId: String): Hotel = throw NotImplementedError()
        override suspend fun getFavorites(): List<Hotel> = emptyList()
        override suspend fun syncFavorites(): List<Hotel> = emptyList()
        override suspend fun toggleFavoriteOptimistic(hotel: Hotel): FavoriteMutationResult =
            FavoriteMutationResult.Success(true)
        override suspend fun restoreFavoriteOptimistic(hotel: Hotel, originalIndex: Int): FavoriteMutationResult =
            FavoriteMutationResult.Success(true)
        override suspend fun getAvailability(hotelId: String, roomId: String, checkIn: String, checkOut: String, rooms: Int): Availability =
            throw NotImplementedError()
        override suspend fun getHotelReviews(hotelId: String, page: Int, limit: Int): PublicReviewPage =
            throw NotImplementedError()
        override suspend fun getMyReview(hotelId: String): MyReviewState =
            throw NotImplementedError()
        override suspend fun submitReview(hotelId: String, bookingId: String, reviewId: String?, rating: Int, title: String, comment: String): PrivateReview =
            throw NotImplementedError()
    }

    @Test
    fun homeScreen_coldStartError_rendersFriendlyMessageAndRetryButton_andRecoversOnRetry() {
        val repo = FakeHotelRepository()
        repo.shouldFail = true

        val viewModel = HomeViewModel(GetHotelsUseCase(repo), repo)

        composeTestRule.setContent {
            MaterialTheme {
                HomeScreen(
                    onSearchClick = {},
                    onHotelClick = {},
                    onHistoryClick = {},
                    onFavoritesClick = {},
                    onProfileClick = {},
                    viewModel = viewModel,
                )
            }
        }

        // 1. Initial cold-start error state displays friendly sanitized message
        composeTestRule.onNodeWithText("Unable to load hotels. Please try again.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_retry_button").assertIsDisplayed().assertIsEnabled()

        // 2. Backend recovers
        repo.shouldFail = false

        // 3. Tap Retry
        composeTestRule.onNodeWithTag("home_retry_button").performClick()

        // 4. Hotel content is loaded and displayed
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            viewModel.uiState.value.hotels.isNotEmpty()
        }
        composeTestRule.onNodeWithText("Grand Palace Hotel").assertIsDisplayed()
    }

    @Test
    fun homeScreen_refreshFailure_preservesHotelsAndRendersInlineErrorCard() {
        val repo = FakeHotelRepository()
        repo.shouldFail = false

        val viewModel = HomeViewModel(GetHotelsUseCase(repo), repo)

        composeTestRule.setContent {
            MaterialTheme {
                HomeScreen(
                    onSearchClick = {},
                    onHotelClick = {},
                    onHistoryClick = {},
                    onFavoritesClick = {},
                    onProfileClick = {},
                    viewModel = viewModel,
                )
            }
        }

        // Hotels initially displayed
        composeTestRule.onNodeWithText("Grand Palace Hotel").assertIsDisplayed()

        // Subsequent refresh fails
        repo.shouldFail = true
        repo.failureException = java.net.ConnectException("Failed to connect to /10.0.2.2:8080")
        viewModel.loadHotels()

        // Hotels must remain visible on screen
        composeTestRule.onNodeWithText("Grand Palace Hotel").assertIsDisplayed()

        // Inline error card and inline retry button are displayed
        composeTestRule.onNodeWithTag("home_inline_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_inline_retry_button").assertIsDisplayed().assertIsEnabled()
    }
}
