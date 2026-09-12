package com.innly.hotelbooking

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.innly.hotelbooking.presentation.reviews.ReviewsScreen
import com.innly.hotelbooking.presentation.reviews.ReviewsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WriteReviewScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createFakeHotelRepository(): HotelRepository {
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
            ): PrivateReview {
                return PrivateReview(
                    id = "rev-1",
                    bookingId = bookingId,
                    rating = rating,
                    title = title,
                    comment = comment,
                    status = "pending",
                    createdAt = "2026-08-21",
                    updatedAt = "2026-08-21",
                    moderatedAt = null,
                )
            }
        }
    }

    @Test
    fun writeReviewScreen_rendersForm_validatesInput_andEnablesSubmit() {
        val fakeRepo = createFakeHotelRepository()
        val viewModel = ReviewsViewModel(fakeRepo)

        composeTestRule.setContent {
            MaterialTheme {
                ReviewsScreen(
                    hotelId = "hotel-1",
                    onBackClick = {},
                    viewModel = viewModel,
                )
            }
        }

        // Verify Star selector exists
        composeTestRule.onNodeWithTag("star_5").assertIsDisplayed()
        composeTestRule.onNodeWithTag("star_4").performClick()

        // Submit button initially disabled because title & comment are empty
        composeTestRule.onNodeWithTag("submit_button").assertIsNotEnabled()

        // Enter Title and Comment
        composeTestRule.onNodeWithTag("title_field").performTextInput("Memorable Stay")
        composeTestRule.onNodeWithTag("comment_field").performTextInput("Room was spotless and comfortable.")

        // Submit button should now be enabled
        composeTestRule.onNodeWithTag("submit_button").assertIsEnabled()
    }
}
