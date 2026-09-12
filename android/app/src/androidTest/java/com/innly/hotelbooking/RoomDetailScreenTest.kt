package com.innly.hotelbooking

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.innly.hotelbooking.domain.model.Availability
import com.innly.hotelbooking.domain.model.EligibleBooking
import com.innly.hotelbooking.domain.model.Hotel
import com.innly.hotelbooking.domain.model.HotelSearchFilter
import com.innly.hotelbooking.domain.model.MyReviewState
import com.innly.hotelbooking.domain.model.PrivateReview
import com.innly.hotelbooking.domain.model.PublicReviewPage
import com.innly.hotelbooking.domain.model.Room
import com.innly.hotelbooking.domain.repository.FavoriteMutationResult
import com.innly.hotelbooking.domain.repository.FavoriteState
import com.innly.hotelbooking.domain.repository.HotelRepository
import com.innly.hotelbooking.domain.usecase.GetHotelDetailUseCase
import com.innly.hotelbooking.presentation.hoteldetail.HotelDetailViewModel
import com.innly.hotelbooking.presentation.roomdetail.RoomDetailScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class RoomDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createFakeHotelRepository(
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
                throw NoSuchElementException("Hotel not found")
            }

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
                MyReviewState(canWriteReview = false, eligibleBooking = null, myReviews = emptyList(), latestReview = null)
            override suspend fun submitReview(hotelId: String, bookingId: String, reviewId: String?, rating: Int, title: String, comment: String): PrivateReview =
                throw NotImplementedError()
        }
    }

    @Test
    fun roomDetail_fullScreenError_rendersRoomDetailsWordingAndBackAndRetry() {
        var backClicked = false
        var loadCalls = 0
        val failingRepo = createFakeHotelRepository(
            hotelDetailProvider = {
                loadCalls++
                Result.failure(java.io.IOException("Failed to connect to backend"))
            }
        )
        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(failingRepo), failingRepo)

        composeTestRule.setContent {
            MaterialTheme {
                RoomDetailScreen(
                    hotelId = "h-1",
                    roomId = "r-1",
                    onBookNow = { _, _ -> },
                    onBackClick = { backClicked = true },
                    viewModel = viewModel,
                )
            }
        }

        // Must display "Unable to load room details. Please try again."
        composeTestRule.onNodeWithText("Unable to load room details. Please try again.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("room_detail_error_back_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("room_detail_retry_button").assertIsDisplayed()

        // Verify Back click works
        composeTestRule.onNodeWithTag("room_detail_error_back_button").performClick()
        assertTrue(backClicked)

        // Verify Retry click triggers reload
        val previousCalls = loadCalls
        composeTestRule.onNodeWithTag("room_detail_retry_button").performClick()
        assertTrue(loadCalls > previousCalls)
    }

    @Test
    fun roomDetail_refreshFailure_preservesLoadedRoomAndRendersInlineErrorWithUpdateRoomDetails() {
        var callCount = 0
        val testRoom = Room(
            id = "r-deluxe",
            hotelId = "h-ocean",
            name = "Deluxe Ocean Suite",
            description = "Spacious suite with balcony",
            capacity = 2,
            bedType = "King Bed",
            basePrice = 8500,
            thumbnailUrl = "",
            images = emptyList(),
        )
        val testHotel = Hotel(
            id = "h-ocean",
            name = "Oceanfront Paradise Resort",
            description = "Beach resort",
            city = "Goa",
            address = "Calangute",
            rating = 4.7,
            reviewCount = 50,
            startingPrice = 8500,
            thumbnailUrl = "",
            images = emptyList(),
            amenities = listOf("wifi", "pool"),
            isFavorite = false,
            rooms = listOf(testRoom),
        )

        val repo = createFakeHotelRepository(
            hotelDetailProvider = {
                callCount++
                if (callCount == 1) {
                    Result.success(testHotel)
                } else {
                    Result.failure(java.net.SocketTimeoutException("Network timeout"))
                }
            }
        )
        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(repo), repo)

        composeTestRule.setContent {
            MaterialTheme {
                RoomDetailScreen(
                    hotelId = "h-ocean",
                    roomId = "r-deluxe",
                    onBookNow = { _, _ -> },
                    onBackClick = {},
                    viewModel = viewModel,
                )
            }
        }

        // Initially loaded successfully
        composeTestRule.onNodeWithText("Deluxe Ocean Suite").assertIsDisplayed()

        // Background refresh fails
        viewModel.loadHotel("h-ocean", fallbackErrorMessage = "Unable to load room details. Please try again.")

        // Room content remains visible on screen
        composeTestRule.onNodeWithText("Deluxe Ocean Suite").assertIsDisplayed()

        // Inline error banner shows "Unable to update room details. Please try again."
        composeTestRule.onNodeWithTag("room_detail_inline_error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Unable to update room details. Please try again.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("room_detail_inline_retry_button").assertIsDisplayed()
    }

    @Test
    fun roomDetail_legitimateBusinessError_rendersExactBusinessMessage() {
        val jsonMediaType = "application/json".toMediaType()
        val errorJson = """{"message":"Selected room is currently closed for renovation"}"""
        val response = Response.error<Any>(400, errorJson.toResponseBody(jsonMediaType))
        val httpException = HttpException(response)

        val repo = createFakeHotelRepository(
            hotelDetailProvider = {
                Result.failure(httpException)
            }
        )
        val viewModel = HotelDetailViewModel(GetHotelDetailUseCase(repo), repo)

        composeTestRule.setContent {
            MaterialTheme {
                RoomDetailScreen(
                    hotelId = "h-renovation",
                    roomId = "r-reno",
                    onBookNow = { _, _ -> },
                    onBackClick = {},
                    viewModel = viewModel,
                )
            }
        }

        // Exact business message must be rendered on screen without being overwritten
        composeTestRule.onNodeWithText("Selected room is currently closed for renovation").assertIsDisplayed()
    }
}
