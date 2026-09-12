package com.innly.hotelbooking

import com.innly.hotelbooking.domain.model.Availability
import com.innly.hotelbooking.domain.model.Hotel
import com.innly.hotelbooking.domain.model.HotelSearchFilter
import com.innly.hotelbooking.domain.model.Review
import com.innly.hotelbooking.domain.repository.HotelRepository
import com.innly.hotelbooking.presentation.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class SearchViewModelTest {

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
        var failureMessage: String = "Network connection failed",
        var failureException: Throwable = RuntimeException(failureMessage),
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
        var lastFilterPassed: HotelSearchFilter? = null
        var searchCallCount = 0
        var favoriteToggleCalls = mutableListOf<Pair<String, Boolean>>()

        override suspend fun getHotels(filter: HotelSearchFilter): List<Hotel> {
            searchCallCount++
            lastFilterPassed = filter
            if (shouldFail) throw failureException

            return hotels.filter { hotel ->
                (filter.city.isBlank() || hotel.city.contains(filter.city, ignoreCase = true)) &&
                    (filter.minPrice == null || hotel.startingPrice >= filter.minPrice) &&
                    (filter.maxPrice == null || hotel.startingPrice <= filter.maxPrice) &&
                    (filter.minRating == null || hotel.rating >= filter.minRating)
            }
        }

        override suspend fun getHotelDetail(hotelId: String): Hotel {
            return hotels.firstOrNull { it.id == hotelId } ?: throw NoSuchElementException("Hotel not found")
        }

        private val _favoriteState = kotlinx.coroutines.flow.MutableStateFlow(
            com.innly.hotelbooking.domain.repository.FavoriteState(
                favoriteIds = hotels.filter { it.isFavorite }.map { it.id }.toSet(),
                favoriteHotels = hotels.filter { it.isFavorite },
                isHydrated = true,
                activeSessionUid = "test-uid",
            )
        )
        override val favoriteState: kotlinx.coroutines.flow.StateFlow<com.innly.hotelbooking.domain.repository.FavoriteState> = _favoriteState
        override val favoriteIds: kotlinx.coroutines.flow.StateFlow<Set<String>> = kotlinx.coroutines.flow.MutableStateFlow(_favoriteState.value.favoriteIds)
        override val favoriteHotels: kotlinx.coroutines.flow.StateFlow<List<Hotel>> = kotlinx.coroutines.flow.MutableStateFlow(_favoriteState.value.favoriteHotels)
        override val inFlightFavoriteHotelIds: kotlinx.coroutines.flow.StateFlow<Set<String>> = kotlinx.coroutines.flow.MutableStateFlow(emptySet())
        override val isFavoritesHydrated: kotlinx.coroutines.flow.StateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(true)

        override suspend fun getFavorites(): List<Hotel> = hotels.filter { it.isFavorite }
        override suspend fun syncFavorites(): List<Hotel> = hotels.filter { it.isFavorite }

        override suspend fun toggleFavoriteOptimistic(hotel: Hotel): com.innly.hotelbooking.domain.repository.FavoriteMutationResult {
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
            return com.innly.hotelbooking.domain.repository.FavoriteMutationResult.Success(target)
        }

        override suspend fun restoreFavoriteOptimistic(hotel: Hotel, originalIndex: Int): com.innly.hotelbooking.domain.repository.FavoriteMutationResult {
            favoriteToggleCalls.add(hotel.id to true)
            hotels = hotels.map { if (it.id == hotel.id) it.copy(isFavorite = true) else it }
            val newIds = _favoriteState.value.favoriteIds + hotel.id
            val newHotels = _favoriteState.value.favoriteHotels + hotel.copy(isFavorite = true)
            _favoriteState.value = _favoriteState.value.copy(
                favoriteIds = newIds,
                favoriteHotels = newHotels,
            )
            return com.innly.hotelbooking.domain.repository.FavoriteMutationResult.Success(true)
        }

        override suspend fun getAvailability(hotelId: String, roomId: String, checkIn: String, checkOut: String, rooms: Int): com.innly.hotelbooking.domain.model.Availability = TODO()
        override suspend fun getHotelReviews(hotelId: String, page: Int, limit: Int): com.innly.hotelbooking.domain.model.PublicReviewPage = TODO()
        override suspend fun getMyReview(hotelId: String): com.innly.hotelbooking.domain.model.MyReviewState = TODO()
        override suspend fun submitReview(hotelId: String, bookingId: String, reviewId: String?, rating: Int, title: String, comment: String): com.innly.hotelbooking.domain.model.PrivateReview = TODO()
    }

    @Test
    fun `1 Initial state executes search and loads all available hotels`() = runTest {
        val fakeRepo = FakeHotelRepository()
        val viewModel = SearchViewModel(fakeRepo)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertNull(state.validationError)
        assertEquals(2, state.results.size)
        assertEquals(1, fakeRepo.searchCallCount)
    }

    @Test
    fun `2 Valid search filters correctly by city, price range, and rating`() = runTest {
        val fakeRepo = FakeHotelRepository()
        val viewModel = SearchViewModel(fakeRepo)
        advanceUntilIdle()

        viewModel.updateCity("Goa")
        viewModel.updateMinPrice("3000")
        viewModel.updateMaxPrice("5000")
        viewModel.updateMinRating(4.0)

        viewModel.search()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Goa", fakeRepo.lastFilterPassed?.city)
        assertEquals(3000, fakeRepo.lastFilterPassed?.minPrice)
        assertEquals(5000, fakeRepo.lastFilterPassed?.maxPrice)
        assertEquals(4.0, fakeRepo.lastFilterPassed?.minRating ?: 0.0, 0.001)

        assertEquals(1, state.results.size)
        assertEquals("Seaside Retreat", state.results[0].name)
    }

    @Test
    fun `3 Minimum price greater than maximum price sets validation error and blocks search`() = runTest {
        val fakeRepo = FakeHotelRepository()
        val viewModel = SearchViewModel(fakeRepo)
        advanceUntilIdle()

        val initialCallCount = fakeRepo.searchCallCount

        viewModel.updateMinPrice("6000")
        viewModel.updateMaxPrice("4000")

        val state = viewModel.uiState.value
        assertEquals("Minimum price cannot be greater than maximum price.", state.validationError)

        viewModel.search()
        advanceUntilIdle()

        // Call count should NOT increment because search was blocked by validation
        assertEquals(initialCallCount, fakeRepo.searchCallCount)
    }

    @Test
    fun `4 Non-digit characters in price inputs are filtered out safely`() = runTest {
        val fakeRepo = FakeHotelRepository()
        val viewModel = SearchViewModel(fakeRepo)
        advanceUntilIdle()

        viewModel.updateMinPrice("abc 2500 xyz")
        viewModel.updateMaxPrice("₹9999")

        val state = viewModel.uiState.value
        assertEquals("2500", state.minPrice)
        assertEquals("9999", state.maxPrice)
        assertNull(state.validationError)
    }

    @Test
    fun `5 Minimum rating selection updates state correctly`() = runTest {
        val fakeRepo = FakeHotelRepository()
        val viewModel = SearchViewModel(fakeRepo)
        advanceUntilIdle()

        viewModel.updateMinRating(4.5)
        assertEquals(4.5, viewModel.uiState.value.minRating ?: 0.0, 0.001)

        viewModel.search()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.results.size)
        assertEquals("Grand Palace Hotel", state.results[0].name)
    }

    @Test
    fun `6 Clear filters resets criteria and re-executes default search`() = runTest {
        val fakeRepo = FakeHotelRepository()
        val viewModel = SearchViewModel(fakeRepo)
        advanceUntilIdle()

        viewModel.updateCity("Delhi")
        viewModel.updateMinPrice("4000")
        viewModel.updateMaxPrice("8000")
        viewModel.updateMinRating(4.5)
        viewModel.search()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.results.size)

        viewModel.clearFilters()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.city)
        assertEquals("", state.minPrice)
        assertEquals("", state.maxPrice)
        assertNull(state.minRating)
        assertNull(state.validationError)
        assertEquals(2, state.results.size)
    }

    @Test
    fun `7 Toggle favorite updates hotel state and invokes repository correctly`() = runTest {
        val fakeRepo = FakeHotelRepository()
        val viewModel = SearchViewModel(fakeRepo)
        advanceUntilIdle()

        val hotelToFavorite = viewModel.uiState.value.results.first { it.id == "h-1" }
        assertFalse(hotelToFavorite.isFavorite)

        viewModel.toggleFavorite(hotelToFavorite)
        advanceUntilIdle()

        val updatedHotel = viewModel.uiState.value.results.first { it.id == "h-1" }
        assertTrue(updatedHotel.isFavorite)
        assertEquals(1, fakeRepo.favoriteToggleCalls.size)
        assertEquals("h-1" to true, fakeRepo.favoriteToggleCalls[0])

        // Un-favorite
        viewModel.toggleFavorite(updatedHotel)
        advanceUntilIdle()

        val unFavoritedHotel = viewModel.uiState.value.results.first { it.id == "h-1" }
        assertFalse(unFavoritedHotel.isFavorite)
        assertEquals(2, fakeRepo.favoriteToggleCalls.size)
        assertEquals("h-1" to false, fakeRepo.favoriteToggleCalls[1])
    }

    @Test
    fun `8 Search error gracefully updates errorMessage without crashing`() = runTest {
        val failingRepo = FakeHotelRepository(shouldFail = true, failureMessage = "500 Server Error")
        val viewModel = SearchViewModel(failingRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
        assertEquals("500 Server Error", state.errorMessage)
    }

    @Test
    fun `9 In-flight guard prevents overlapping search requests on rapid taps`() = runTest {
        val fakeRepo = FakeHotelRepository()
        val viewModel = SearchViewModel(fakeRepo)
        advanceUntilIdle()
        assertEquals(1, fakeRepo.searchCallCount)

        // Trigger two rapid searches while one is starting
        viewModel.search()
        viewModel.search()
        advanceUntilIdle()

        // Should only execute once more, not twice
        assertEquals(2, fakeRepo.searchCallCount)
    }

    @Test
    fun `10 Same-query refresh failure preserves previously loaded search results`() = runTest {
        val repo = FakeHotelRepository()
        val viewModel = SearchViewModel(repo)
        advanceUntilIdle()

        // Initial search succeeded
        assertEquals(2, viewModel.uiState.value.results.size)
        assertNull(viewModel.uiState.value.errorMessage)

        // Now refresh same query with backend down
        repo.shouldFail = true
        repo.failureException = java.net.ConnectException("Failed to connect to /10.0.2.2:8080")
        viewModel.search()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Unable to complete hotel search. Please try again.", state.errorMessage)
        // Results are preserved!
        assertEquals(2, state.results.size)
        assertEquals("Grand Palace Hotel", state.results[0].name)
    }

    @Test
    fun `11 Different-query failure never mislabels stale results`() = runTest {
        val repo = FakeHotelRepository()
        val viewModel = SearchViewModel(repo)
        advanceUntilIdle()

        // Succeeded on initial default query
        assertEquals(2, viewModel.uiState.value.results.size)

        // User enters a DIFFERENT city query and searches, but backend fails
        viewModel.updateCity("Mumbai")
        repo.shouldFail = true
        repo.failureException = java.net.ConnectException("Failed to connect to /10.0.2.2:8080")
        viewModel.search()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Unable to complete hotel search. Please try again.", state.errorMessage)
        // Must NOT show old results as if they belong to "Mumbai"!
        assertTrue(state.results.isEmpty())
    }

    @Test
    fun `12 Technical ConnectException sanitizes host and port in SearchViewModel`() = runTest {
        val failingRepo = FakeHotelRepository(
            shouldFail = true,
            failureMessage = "Failed to connect to /10.0.2.2:8080",
        )
        val viewModel = SearchViewModel(failingRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertEquals("Unable to complete hotel search. Please try again.", state.errorMessage)
        assertFalse(state.errorMessage!!.contains("10.0.2.2"))
        assertFalse(state.errorMessage!!.contains("8080"))
    }
}
