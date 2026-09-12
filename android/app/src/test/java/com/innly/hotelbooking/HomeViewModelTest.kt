package com.innly.hotelbooking

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
import com.innly.hotelbooking.presentation.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

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
        var shouldFail: Boolean = false,
        var failureException: Throwable = java.net.ConnectException("Failed to connect to /10.0.2.2:8080"),
        var hotels: List<Hotel> = listOf(
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
            ),
            Hotel(
                id = "h-2",
                name = "Seaside Retreat",
                description = "Beach resort in Goa",
                city = "Goa",
                address = "Calangute Beach",
                rating = 4.2,
                reviewCount = 85,
                startingPrice = 3200,
                thumbnailUrl = "https://example.com/h2.jpg",
                images = emptyList(),
                amenities = emptyList(),
                isFavorite = true,
            ),
        ),
    ) : HotelRepository {
        var loadCallCount = 0
        var favoriteToggleCalls = mutableListOf<Pair<String, Boolean>>()

        private val _favoriteState = MutableStateFlow(
            FavoriteState(
                favoriteIds = hotels.filter { it.isFavorite }.map { it.id }.toSet(),
                favoriteHotels = hotels.filter { it.isFavorite },
                isHydrated = true,
                activeSessionUid = "test-uid",
            )
        )
        override val favoriteState: StateFlow<FavoriteState> = _favoriteState
        override val favoriteIds: StateFlow<Set<String>> = MutableStateFlow(_favoriteState.value.favoriteIds)
        override val favoriteHotels: StateFlow<List<Hotel>> = MutableStateFlow(_favoriteState.value.favoriteHotels)
        override val inFlightFavoriteHotelIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())
        override val isFavoritesHydrated: StateFlow<Boolean> = MutableStateFlow(true)

        override suspend fun getHotels(filter: HotelSearchFilter): List<Hotel> {
            loadCallCount++
            if (shouldFail) throw failureException
            return hotels
        }

        override suspend fun getHotelDetail(hotelId: String): Hotel {
            return hotels.firstOrNull { it.id == hotelId } ?: throw NoSuchElementException("Hotel not found")
        }

        override suspend fun getFavorites(): List<Hotel> = hotels.filter { it.isFavorite }
        override suspend fun syncFavorites(): List<Hotel> = hotels.filter { it.isFavorite }

        override suspend fun toggleFavoriteOptimistic(hotel: Hotel): FavoriteMutationResult {
            val target = !_favoriteState.value.favoriteIds.contains(hotel.id)
            favoriteToggleCalls.add(hotel.id to target)
            hotels = hotels.map { if (it.id == hotel.id) it.copy(isFavorite = target) else it }
            val newIds = if (target) _favoriteState.value.favoriteIds + hotel.id else _favoriteState.value.favoriteIds - hotel.id
            val newHotels = if (target) _favoriteState.value.favoriteHotels + hotel.copy(isFavorite = true) else _favoriteState.value.favoriteHotels.filterNot { it.id == hotel.id }
            _favoriteState.value = _favoriteState.value.copy(
                favoriteIds = newIds,
                favoriteHotels = newHotels,
                knownFavoriteStates = _favoriteState.value.knownFavoriteStates + (hotel.id to target),
            )
            return FavoriteMutationResult.Success(target)
        }

        override suspend fun restoreFavoriteOptimistic(hotel: Hotel, originalIndex: Int): FavoriteMutationResult {
            favoriteToggleCalls.add(hotel.id to true)
            return FavoriteMutationResult.Success(true)
        }

        override suspend fun getAvailability(hotelId: String, roomId: String, checkIn: String, checkOut: String, rooms: Int): Availability = TODO()
        override suspend fun getHotelReviews(hotelId: String, page: Int, limit: Int): PublicReviewPage = TODO()
        override suspend fun getMyReview(hotelId: String): MyReviewState = TODO()
        override suspend fun submitReview(hotelId: String, bookingId: String, reviewId: String?, rating: Int, title: String, comment: String): PrivateReview = TODO()
    }

    @Test
    fun `1 Cold-start load failure sets friendly sanitized message and empty hotel list`() = runTest {
        val fakeRepo = FakeHotelRepository(shouldFail = true)
        val viewModel = HomeViewModel(GetHotelsUseCase(fakeRepo), fakeRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Unable to load hotels. Please try again.", state.errorMessage)
        assertTrue(state.hotels.isEmpty())
        assertEquals(1, fakeRepo.loadCallCount)
    }

    @Test
    fun `2 ConnectException containing slash 10 0 2 2 colon 8080 produces no host or port leakage`() = runTest {
        val connectException = java.net.ConnectException("Failed to connect to /10.0.2.2:8080")
        val fakeRepo = FakeHotelRepository(shouldFail = true, failureException = connectException)
        val viewModel = HomeViewModel(GetHotelsUseCase(fakeRepo), fakeRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertEquals("Unable to load hotels. Please try again.", state.errorMessage)
        assertFalse(state.errorMessage!!.contains("10.0.2.2"))
        assertFalse(state.errorMessage!!.contains("8080"))
    }

    @Test
    fun `3 Home initial error to Retry to success transitions correctly`() = runTest {
        val fakeRepo = FakeHotelRepository(shouldFail = true)
        val viewModel = HomeViewModel(GetHotelsUseCase(fakeRepo), fakeRepo)
        advanceUntilIdle()

        // Confirmed initial error state
        assertEquals("Unable to load hotels. Please try again.", viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.hotels.isEmpty())

        // Backend recovers
        fakeRepo.shouldFail = false

        // Tap Retry
        viewModel.loadHotels()
        advanceUntilIdle()

        // Succeeded: loading false, error cleared, hotels loaded
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(2, state.hotels.size)
        assertEquals("Grand Palace Hotel", state.hotels[0].name)
        assertEquals(2, fakeRepo.loadCallCount)
    }

    @Test
    fun `4 Rapid Retry taps are prevented by in-flight guard`() = runTest {
        val fakeRepo = FakeHotelRepository(shouldFail = false)
        val viewModel = HomeViewModel(GetHotelsUseCase(fakeRepo), fakeRepo)
        advanceUntilIdle()
        assertEquals(1, fakeRepo.loadCallCount)

        // Fire two rapid calls
        viewModel.loadHotels()
        viewModel.loadHotels()
        advanceUntilIdle()

        // In-flight guard must ensure only one additional execution occurred
        assertEquals(2, fakeRepo.loadCallCount)
    }

    @Test
    fun `5 Home refresh failure preserves loaded hotels`() = runTest {
        val fakeRepo = FakeHotelRepository(shouldFail = false)
        val viewModel = HomeViewModel(GetHotelsUseCase(fakeRepo), fakeRepo)
        advanceUntilIdle()

        // Hotels initially loaded successfully
        assertEquals(2, viewModel.uiState.value.hotels.size)
        assertNull(viewModel.uiState.value.errorMessage)

        // Subsequent background refresh fails
        fakeRepo.shouldFail = true
        fakeRepo.failureException = java.net.SocketTimeoutException("connect timed out")
        viewModel.loadHotels()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Unable to load hotels. Please try again.", state.errorMessage)
        // Loaded hotels MUST be preserved
        assertEquals(2, state.hotels.size)
        assertEquals("Grand Palace Hotel", state.hotels[0].name)
        assertEquals("Seaside Retreat", state.hotels[1].name)
    }

    @Test
    fun `6 Legitimate backend validation messages remain intact in HomeViewModel`() = runTest {
        val jsonMediaType = "application/json".toMediaType()
        val errorJson = """{"message":"Service temporarily under maintenance for region Delhi"}"""
        val response = Response.error<Any>(400, errorJson.toResponseBody(jsonMediaType))
        val httpException = HttpException(response)

        val fakeRepo = FakeHotelRepository(shouldFail = true, failureException = httpException)
        val viewModel = HomeViewModel(GetHotelsUseCase(fakeRepo), fakeRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Service temporarily under maintenance for region Delhi", state.errorMessage)
    }

    @Test
    fun `7 Toggle favorite updates hotel state`() = runTest {
        val fakeRepo = FakeHotelRepository(shouldFail = false)
        val viewModel = HomeViewModel(GetHotelsUseCase(fakeRepo), fakeRepo)
        advanceUntilIdle()

        val hotel = viewModel.uiState.value.hotels.first { it.id == "h-1" }
        assertFalse(hotel.isFavorite)

        viewModel.toggleFavorite(hotel)
        advanceUntilIdle()

        val updated = viewModel.uiState.value.hotels.first { it.id == "h-1" }
        assertTrue(updated.isFavorite)
        assertEquals(1, fakeRepo.favoriteToggleCalls.size)
    }
}
