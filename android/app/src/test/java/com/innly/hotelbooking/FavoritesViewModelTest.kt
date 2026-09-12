package com.innly.hotelbooking

import com.innly.hotelbooking.domain.model.Availability
import com.innly.hotelbooking.domain.model.Hotel
import com.innly.hotelbooking.domain.model.HotelSearchFilter
import com.innly.hotelbooking.domain.model.Review
import com.innly.hotelbooking.domain.model.UserProfile
import com.innly.hotelbooking.domain.repository.AuthRepository
import com.innly.hotelbooking.domain.repository.FavoriteMutationResult
import com.innly.hotelbooking.domain.repository.FavoriteState
import com.innly.hotelbooking.domain.repository.HotelRepository
import com.innly.hotelbooking.presentation.favorites.FavoritesRemovalPhase
import com.innly.hotelbooking.presentation.favorites.FavoritesViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
class FavoritesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeAuthRepositoryForFavorites(
        private val authFlow: Flow<UserProfile?> = flowOf(
            UserProfile(
                id = "u-1",
                firebaseUid = "fb-1",
                displayName = "Abhishek Ghosh",
                email = "abhishek@example.com",
            )
        ),
    ) : AuthRepository {
        override val currentUser: Flow<UserProfile?> = authFlow
        override suspend fun signIn(email: String, password: String) {}
        override suspend fun signUp(name: String, email: String, password: String) {}
        override suspend fun signOut() {}
        override suspend fun syncProfile(displayName: String?, phoneNumber: String?): UserProfile = TODO()
    }

    private open class FakeHotelRepositoryForFavorites(
        var shouldFailLoad: Boolean = false,
        var shouldFailToggle: Boolean = false,
        var loadErrorMessage: String = "Server unavailable",
        var toggleErrorMessage: String = "Network failure during toggle",
        var deferredToggle: CompletableDeferred<Unit>? = null,
        var deferredLoad: CompletableDeferred<List<Hotel>>? = null,
        var isInitiallyHydrated: Boolean = false,
        var favorites: List<Hotel> = listOf(
            Hotel(
                id = "h-1",
                name = "Cedar Peak Retreat",
                description = "Luxury mountain stay",
                city = "Manali",
                address = "Old Manali Road",
                rating = 4.8,
                reviewCount = 120,
                startingPrice = 6100,
                thumbnailUrl = "",
                images = emptyList(),
                amenities = emptyList(),
                isFavorite = true,
            ),
            Hotel(
                id = "h-2",
                name = "The Grand Palace",
                description = "Heritage resort",
                city = "Udaipur",
                address = "Lake Pichola",
                rating = 4.9,
                reviewCount = 210,
                startingPrice = 9500,
                thumbnailUrl = "",
                images = emptyList(),
                amenities = emptyList(),
                isFavorite = true,
            ),
        ),
    ) : HotelRepository {
        var loadCallCount = 0
        val toggleCalls = mutableListOf<Pair<String, Boolean>>()

        private val _favoriteHotels = MutableStateFlow(if (shouldFailLoad || deferredLoad != null) emptyList() else favorites)
        override val favoriteHotels: StateFlow<List<Hotel>> = _favoriteHotels.asStateFlow()
        private val _favoriteIds = MutableStateFlow(if (shouldFailLoad || deferredLoad != null) emptySet() else favorites.map { it.id }.toSet())
        override val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()
        private val _favoriteState = MutableStateFlow(
            FavoriteState(
                favoriteIds = if (shouldFailLoad || deferredLoad != null) emptySet() else favorites.map { it.id }.toSet(),
                favoriteHotels = if (shouldFailLoad || deferredLoad != null) emptyList() else favorites,
                isHydrated = isInitiallyHydrated && !shouldFailLoad && deferredLoad == null,
            )
        )
        override val favoriteState: StateFlow<FavoriteState> = _favoriteState.asStateFlow()
        override val inFlightFavoriteHotelIds: StateFlow<Set<String>> = MutableStateFlow(emptySet<String>())
        private val _isHydrated = MutableStateFlow(isInitiallyHydrated && !shouldFailLoad && deferredLoad == null)
        override val isFavoritesHydrated: StateFlow<Boolean> = _isHydrated.asStateFlow()

        fun setHydrated(hydrated: Boolean) {
            _isHydrated.value = hydrated
            _favoriteState.value = _favoriteState.value.copy(isHydrated = hydrated)
        }

        override suspend fun getFavorites(): List<Hotel> {
            return syncFavorites()
        }

        override suspend fun syncFavorites(): List<Hotel> {
            loadCallCount++
            deferredLoad?.await()?.let {
                favorites = it
                _favoriteHotels.value = it
                _favoriteIds.value = it.map { h -> h.id }.toSet()
                _isHydrated.value = true
                _favoriteState.value = _favoriteState.value.copy(favoriteIds = _favoriteIds.value, favoriteHotels = it, isHydrated = true)
                return it
            }
            if (shouldFailLoad) throw RuntimeException(loadErrorMessage)
            _favoriteHotels.value = favorites
            _favoriteIds.value = favorites.map { it.id }.toSet()
            _isHydrated.value = true
            _favoriteState.value = _favoriteState.value.copy(favoriteIds = _favoriteIds.value, favoriteHotels = favorites, isHydrated = true)
            return favorites
        }

        override suspend fun toggleFavoriteOptimistic(hotel: Hotel): FavoriteMutationResult {
            val target = false
            toggleCalls.add(hotel.id to target)
            deferredToggle?.await()
            if (shouldFailToggle) {
                return FavoriteMutationResult.Failed(RuntimeException(toggleErrorMessage))
            }
            favorites = favorites.filterNot { it.id == hotel.id }
            _favoriteHotels.value = favorites
            _favoriteIds.value = favorites.map { it.id }.toSet()
            _favoriteState.value = _favoriteState.value.copy(
                favoriteIds = _favoriteIds.value,
                favoriteHotels = favorites,
            )
            return FavoriteMutationResult.Success(false)
        }

        override suspend fun restoreFavoriteOptimistic(hotel: Hotel, originalIndex: Int): FavoriteMutationResult {
            toggleCalls.add(hotel.id to true)
            deferredToggle?.await()
            if (shouldFailToggle) {
                return FavoriteMutationResult.Failed(RuntimeException(toggleErrorMessage))
            }
            val targetIndex = originalIndex.coerceIn(0, favorites.size)
            val updated = ArrayList(favorites).apply { add(targetIndex, hotel.copy(isFavorite = true)) }
            favorites = updated
            _favoriteHotels.value = favorites
            _favoriteIds.value = favorites.map { it.id }.toSet()
            _favoriteState.value = _favoriteState.value.copy(
                favoriteIds = _favoriteIds.value,
                favoriteHotels = favorites,
            )
            return FavoriteMutationResult.Success(true)
        }
        override suspend fun getHotels(filter: HotelSearchFilter): List<Hotel> = emptyList()
        override suspend fun getHotelDetail(hotelId: String): Hotel = TODO()
        override suspend fun getAvailability(hotelId: String, roomId: String, checkIn: String, checkOut: String, rooms: Int): Availability = TODO()
        override suspend fun getHotelReviews(hotelId: String, page: Int, limit: Int): com.innly.hotelbooking.domain.model.PublicReviewPage = TODO()
        override suspend fun getMyReview(hotelId: String): com.innly.hotelbooking.domain.model.MyReviewState = TODO()
        override suspend fun submitReview(hotelId: String, bookingId: String, reviewId: String?, rating: Int, title: String, comment: String): com.innly.hotelbooking.domain.model.PrivateReview = TODO()
    }

    @Test
    fun `1 initial auth unresolved state shows loading and does not trigger getFavorites`() = runTest {
        val authFlow = MutableSharedFlow<UserProfile?>()
        val fakeAuth = FakeAuthRepositoryForFavorites(authFlow = authFlow)
        val fakeHotel = FakeHotelRepositoryForFavorites()

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)

        assertFalse(viewModel.uiState.value.isAuthResolved)
        assertFalse(viewModel.uiState.value.isAuthenticated)
        assertEquals(0, fakeHotel.loadCallCount)
    }

    @Test
    fun `2 resolving to signed-out user shows guest state and does not call getFavorites`() = runTest {
        val authFlow = MutableSharedFlow<UserProfile?>(replay = 1)
        val fakeAuth = FakeAuthRepositoryForFavorites(authFlow = authFlow)
        val fakeHotel = FakeHotelRepositoryForFavorites()

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)

        authFlow.emit(null)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAuthResolved)
        assertFalse(viewModel.uiState.value.isAuthenticated)
        assertEquals(0, fakeHotel.loadCallCount)
        assertTrue(viewModel.uiState.value.hotels.isEmpty())
    }

    @Test
    fun `3 resolving to authenticated user automatically loads favorites`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites()

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAuthResolved)
        assertTrue(viewModel.uiState.value.isAuthenticated)
        assertEquals(1, fakeHotel.loadCallCount)
        assertEquals(2, viewModel.uiState.value.hotels.size)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `4 signing in after guest state triggers single favorites load`() = runTest {
        val authFlow = MutableSharedFlow<UserProfile?>(replay = 1)
        val fakeAuth = FakeAuthRepositoryForFavorites(authFlow = authFlow)
        val fakeHotel = FakeHotelRepositoryForFavorites()

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)

        // Guest resolution
        authFlow.emit(null)
        testScheduler.advanceUntilIdle()
        assertEquals(0, fakeHotel.loadCallCount)

        // Sign in
        authFlow.emit(UserProfile(id = "u-1", firebaseUid = "fb-1", displayName = "Abhishek", email = "abhishek@example.com"))
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAuthenticated)
        assertEquals(1, fakeHotel.loadCallCount)
        assertEquals(2, viewModel.uiState.value.hotels.size)
    }

    @Test
    fun `5 subsequent duplicate auth emission for same UID does not trigger redundant load`() = runTest {
        val authFlow = MutableSharedFlow<UserProfile?>(replay = 1)
        val fakeAuth = FakeAuthRepositoryForFavorites(authFlow = authFlow)
        val fakeHotel = FakeHotelRepositoryForFavorites()

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)

        // Initial emission (e.g. cached profile)
        authFlow.emit(UserProfile(id = "u-1", firebaseUid = "fb-1", displayName = "Abhishek", email = "abhishek@example.com"))
        testScheduler.advanceUntilIdle()
        assertEquals(1, fakeHotel.loadCallCount)

        // Duplicate emission (e.g. backend sync completes with identical firebaseUid)
        authFlow.emit(UserProfile(id = "u-1", firebaseUid = "fb-1", displayName = "Abhishek Ghosh", email = "abhishek@example.com"))
        testScheduler.advanceUntilIdle()
        assertEquals(1, fakeHotel.loadCallCount)
    }

    @Test
    fun `6 populated and empty list states represent actual backend data`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites(favorites = emptyList())

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.hotels.isEmpty())
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `7 loading failure sets friendly parsed error message from ErrorUtils without raw exceptions`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites(shouldFailLoad = true, loadErrorMessage = "HTTP 500 Internal Server Error")

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.hotels.isEmpty())
        assertEquals("HTTP 500 Internal Server Error", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `8 functional retry clears error and populates favorites on success`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites(
            shouldFailLoad = true,
            loadErrorMessage = "Initial failure",
            favorites = listOf(
                Hotel(id = "h-1", name = "Cedar Peak", description = "", city = "Manali", address = "", rating = 4.8, reviewCount = 10, startingPrice = 5000, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = true)
            )
        )

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        assertEquals("Initial failure", viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.hotels.isEmpty())

        fakeHotel.shouldFailLoad = false
        viewModel.loadFavorites(isRefresh = false)
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(1, viewModel.uiState.value.hotels.size)
        assertEquals("Cedar Peak", viewModel.uiState.value.hotels.first().name)
    }

    @Test
    fun `9 background refresh failure preserves existing loaded hotels`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val initialHotels = listOf(
            Hotel(id = "h-1", name = "Cedar Peak", description = "", city = "Manali", address = "", rating = 4.8, reviewCount = 10, startingPrice = 5000, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = true)
        )
        val fakeHotel = FakeHotelRepositoryForFavorites(
            favorites = initialHotels,
            shouldFailLoad = false,
            loadErrorMessage = "Refresh network dropped"
        )

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.hotels.size)

        fakeHotel.shouldFailLoad = true
        viewModel.loadFavorites(isRefresh = true)
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.hotels.size)
        assertEquals("Refresh network dropped", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `10 manual pull refresh initiates background refresh without clearing hotels`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites()

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        assertEquals(1, fakeHotel.loadCallCount)
        assertEquals(2, viewModel.uiState.value.hotels.size)

        viewModel.loadFavorites(isRefresh = true)
        testScheduler.advanceUntilIdle()

        assertEquals(2, fakeHotel.loadCallCount)
        assertEquals(2, viewModel.uiState.value.hotels.size)
    }

    @Test
    fun `11 manual refresh while mutation is in progress is strictly ignored`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites()

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        val targetHotel = viewModel.uiState.value.hotels.first()
        viewModel.removeFavorite(targetHotel)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isMutationInProgress)
        val loadCallsBefore = fakeHotel.loadCallCount

        viewModel.loadFavorites(isRefresh = false)
        testScheduler.advanceUntilIdle()

        assertEquals(loadCallsBefore, fakeHotel.loadCallCount)
    }

    @Test
    fun `12 removeFavorite is rejected while initial load is in progress`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val deferredFavorites = CompletableDeferred<List<Hotel>>()
        val sampleHotel = Hotel(id = "h-1", name = "Test", description = "", city = "", address = "", rating = 4.0, reviewCount = 1, startingPrice = 100, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = true)
        val fakeHotel = FakeHotelRepositoryForFavorites(deferredLoad = deferredFavorites)

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.removeFavorite(sampleHotel)

        assertEquals(FavoritesRemovalPhase.IDLE, viewModel.uiState.value.removalPhase)
        assertNull(viewModel.uiState.value.activeRemovalEventId)

        deferredFavorites.complete(listOf(sampleHotel))
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `13 removeFavorite is rejected while refresh is in progress`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val sampleHotel = Hotel(id = "h-1", name = "Test", description = "", city = "", address = "", rating = 4.0, reviewCount = 1, startingPrice = 100, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = true)
        val fakeHotel = FakeHotelRepositoryForFavorites(favorites = listOf(sampleHotel))

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        val deferredRefresh = CompletableDeferred<List<Hotel>>()
        fakeHotel.deferredLoad = deferredRefresh
        viewModel.loadFavorites(isRefresh = true)
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isRefreshing)

        viewModel.removeFavorite(sampleHotel)

        assertEquals(FavoritesRemovalPhase.IDLE, viewModel.uiState.value.removalPhase)
        assertNull(viewModel.uiState.value.activeRemovalEventId)

        deferredRefresh.complete(listOf(sampleHotel))
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `14 removal blocks duplicate clicks while mutation is in progress`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites()

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        val targetHotel = viewModel.uiState.value.hotels.first()
        viewModel.removeFavorite(targetHotel)

        // Try second click while mutation is in flight
        val secondHotel = viewModel.uiState.value.hotels.first()
        viewModel.removeFavorite(secondHotel)

        testScheduler.advanceUntilIdle()
        assertEquals(1, fakeHotel.toggleCalls.size)
    }

    @Test
    fun `15 successful removal emits one-shot Undo event with original position and message`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites()

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        val targetHotel = viewModel.uiState.value.hotels[0]
        viewModel.removeFavorite(targetHotel)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FavoritesRemovalPhase.UNDO_AVAILABLE, state.removalPhase)
        assertNotNull(state.undoEvent)
        assertEquals(targetHotel.id, state.undoEvent?.hotel?.id)
        assertEquals(0, state.undoEvent?.originalIndex)
        assertEquals("Removed ${targetHotel.name} from Favorites", state.undoEvent?.message)
    }

    @Test
    fun `16 consuming Undo event clears undoEvent property while keeping activeRemovalEventId`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites()

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        val targetHotel = viewModel.uiState.value.hotels[0]
        viewModel.removeFavorite(targetHotel)
        testScheduler.advanceUntilIdle()

        val eventId = viewModel.uiState.value.undoEvent!!.eventId
        viewModel.consumeUndoEvent(eventId)

        assertNull(viewModel.uiState.value.undoEvent)
        assertEquals(eventId, viewModel.uiState.value.activeRemovalEventId)
        assertEquals(FavoritesRemovalPhase.UNDO_AVAILABLE, viewModel.uiState.value.removalPhase)
    }

    @Test
    fun `17 consuming with mismatched eventId is ignored`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites()

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        val targetHotel = viewModel.uiState.value.hotels[0]
        viewModel.removeFavorite(targetHotel)
        testScheduler.advanceUntilIdle()

        viewModel.consumeUndoEvent("wrong-id")
        assertNotNull(viewModel.uiState.value.undoEvent)
    }

    @Test
    fun `18 onSnackbarDismissed with matching eventId unlocks removal lock and resets phase to IDLE`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites()

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        val targetHotel = viewModel.uiState.value.hotels[0]
        viewModel.removeFavorite(targetHotel)
        testScheduler.advanceUntilIdle()

        val eventId = viewModel.uiState.value.undoEvent!!.eventId
        viewModel.consumeUndoEvent(eventId)

        viewModel.onSnackbarDismissed(eventId)
        assertFalse(viewModel.uiState.value.isMutationInProgress)
        assertEquals(FavoritesRemovalPhase.IDLE, viewModel.uiState.value.removalPhase)
        assertNull(viewModel.uiState.value.activeRemovalEventId)
    }

    @Test
    fun `19 onSnackbarDismissed with mismatched eventId leaves state unchanged`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites()

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        val targetHotel = viewModel.uiState.value.hotels[0]
        viewModel.removeFavorite(targetHotel)
        testScheduler.advanceUntilIdle()

        val eventId = viewModel.uiState.value.undoEvent!!.eventId
        viewModel.onSnackbarDismissed("mismatched-event-id")

        assertTrue(viewModel.uiState.value.isMutationInProgress)
        assertEquals(FavoritesRemovalPhase.UNDO_AVAILABLE, viewModel.uiState.value.removalPhase)
        assertEquals(eventId, viewModel.uiState.value.activeRemovalEventId)
    }

    @Test
    fun `20 onSnackbarDismissed during REMOVING cannot unlock mutation prematurely`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val deferredToggle = CompletableDeferred<Unit>()
        val fakeHotel = FakeHotelRepositoryForFavorites(
            deferredToggle = deferredToggle,
            favorites = listOf(
                Hotel(id = "h-1", name = "Cedar Peak", description = "", city = "", address = "", rating = 4.0, reviewCount = 1, startingPrice = 100, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = true)
            )
        )

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        val targetHotel = viewModel.uiState.value.hotels.first()
        viewModel.removeFavorite(targetHotel)

        val activeId = viewModel.uiState.value.activeRemovalEventId!!
        viewModel.onSnackbarDismissed(activeId)

        assertEquals(FavoritesRemovalPhase.REMOVING, viewModel.uiState.value.removalPhase)
        assertEquals(activeId, viewModel.uiState.value.activeRemovalEventId)

        deferredToggle.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(FavoritesRemovalPhase.UNDO_AVAILABLE, viewModel.uiState.value.removalPhase)
    }

    @Test
    fun `21 failed removal restores hotel at original position and releases lock without Undo event`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites(shouldFailToggle = true, toggleErrorMessage = "HTTP 500 Removal Failure")

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        val targetHotel = viewModel.uiState.value.hotels[0]
        viewModel.removeFavorite(targetHotel)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isMutationInProgress)
        assertEquals(FavoritesRemovalPhase.IDLE, viewModel.uiState.value.removalPhase)
        assertNull(viewModel.uiState.value.undoEvent)
        assertNull(viewModel.uiState.value.activeRemovalEventId)
        assertEquals(2, viewModel.uiState.value.hotels.size)
        assertEquals("Cedar Peak Retreat", viewModel.uiState.value.hotels[0].name)
        assertEquals("HTTP 500 Removal Failure", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `22 undo successfully restores hotel at original index and releases removal lock`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites()

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        val targetHotel = viewModel.uiState.value.hotels[0]
        viewModel.removeFavorite(targetHotel)
        testScheduler.advanceUntilIdle()

        val undoEvent = viewModel.uiState.value.undoEvent!!
        viewModel.consumeUndoEvent(undoEvent.eventId)

        viewModel.undoRemoveFavorite(undoEvent.eventId, undoEvent.hotel, undoEvent.originalIndex)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isMutationInProgress)
        assertEquals(FavoritesRemovalPhase.IDLE, viewModel.uiState.value.removalPhase)
        assertNull(viewModel.uiState.value.activeRemovalEventId)
        assertEquals(2, viewModel.uiState.value.hotels.size)
        assertEquals("Cedar Peak Retreat", viewModel.uiState.value.hotels[0].name)
    }

    @Test
    fun `23 undo with mismatched eventId is ignored`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites()

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        val targetHotel = viewModel.uiState.value.hotels[0]
        viewModel.removeFavorite(targetHotel)
        testScheduler.advanceUntilIdle()

        val undoEvent = viewModel.uiState.value.undoEvent!!
        viewModel.undoRemoveFavorite("wrong-event-id", undoEvent.hotel, undoEvent.originalIndex)
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.hotels.size)
        assertEquals(FavoritesRemovalPhase.UNDO_AVAILABLE, viewModel.uiState.value.removalPhase)
    }

    @Test
    fun `24 failed undo keeps hotel removed and displays friendly error message`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites(
            favorites = listOf(
                Hotel(id = "h-1", name = "Cedar Peak", description = "", city = "Manali", address = "", rating = 4.8, reviewCount = 10, startingPrice = 5000, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = true),
                Hotel(id = "h-2", name = "Grand Palace", description = "", city = "Udaipur", address = "", rating = 4.9, reviewCount = 20, startingPrice = 9000, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = true),
            )
        )

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        val targetHotel = viewModel.uiState.value.hotels.first()
        viewModel.removeFavorite(targetHotel)
        testScheduler.advanceUntilIdle()

        fakeHotel.shouldFailToggle = true
        fakeHotel.toggleErrorMessage = "Undo toggle failed"
        val undoEvent = viewModel.uiState.value.undoEvent!!
        viewModel.consumeUndoEvent(undoEvent.eventId)

        viewModel.undoRemoveFavorite(undoEvent.eventId, undoEvent.hotel, undoEvent.originalIndex)
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.hotels.size)
        assertEquals("h-2", viewModel.uiState.value.hotels[0].id)
        assertEquals(FavoritesRemovalPhase.IDLE, viewModel.uiState.value.removalPhase)
        assertNull(viewModel.uiState.value.activeRemovalEventId)
        assertEquals("Undo toggle failed", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `25 screen disposal with matching eventId during UNDO_AVAILABLE resets removal phase to IDLE`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites()

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        val targetHotel = viewModel.uiState.value.hotels.first()
        viewModel.removeFavorite(targetHotel)
        testScheduler.advanceUntilIdle()

        val eventId = viewModel.uiState.value.undoEvent!!.eventId
        viewModel.onScreenDisposed(eventId)

        assertEquals(FavoritesRemovalPhase.IDLE, viewModel.uiState.value.removalPhase)
        assertNull(viewModel.uiState.value.activeRemovalEventId)
        assertNull(viewModel.uiState.value.undoEvent)
    }

    @Test
    fun `26 screen disposal during REMOVING does not unlock prematurely`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val deferredToggle = CompletableDeferred<Unit>()
        val fakeHotel = FakeHotelRepositoryForFavorites(
            deferredToggle = deferredToggle,
            favorites = listOf(
                Hotel(id = "h-1", name = "Cedar Peak", description = "", city = "", address = "", rating = 4.0, reviewCount = 1, startingPrice = 100, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = true)
            )
        )

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        val targetHotel = viewModel.uiState.value.hotels.first()
        viewModel.removeFavorite(targetHotel)

        val activeId = viewModel.uiState.value.activeRemovalEventId!!
        viewModel.onScreenDisposed(activeId)

        assertEquals(FavoritesRemovalPhase.REMOVING, viewModel.uiState.value.removalPhase)
        assertEquals(activeId, viewModel.uiState.value.activeRemovalEventId)

        deferredToggle.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(FavoritesRemovalPhase.UNDO_AVAILABLE, viewModel.uiState.value.removalPhase)
    }

    @Test
    fun `27 screen disposal during RESTORING does not unlock or discard the successful Undo result`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val deferredUndo = CompletableDeferred<Unit>()
        val fakeHotel = FakeHotelRepositoryForFavorites(
            favorites = listOf(
                Hotel(id = "h-1", name = "Cedar Peak", description = "", city = "", address = "", rating = 4.0, reviewCount = 1, startingPrice = 100, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = true)
            )
        )

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        val targetHotel = viewModel.uiState.value.hotels.first()
        viewModel.removeFavorite(targetHotel)
        testScheduler.advanceUntilIdle()

        val undoEvent = viewModel.uiState.value.undoEvent!!
        fakeHotel.deferredToggle = deferredUndo
        viewModel.undoRemoveFavorite(undoEvent.eventId, undoEvent.hotel, undoEvent.originalIndex)

        viewModel.onScreenDisposed(undoEvent.eventId)
        assertEquals(FavoritesRemovalPhase.RESTORING, viewModel.uiState.value.removalPhase)

        deferredUndo.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals(FavoritesRemovalPhase.IDLE, viewModel.uiState.value.removalPhase)
        assertNull(viewModel.uiState.value.activeRemovalEventId)
        assertEquals(1, viewModel.uiState.value.hotels.size)
        assertEquals("h-1", viewModel.uiState.value.hotels[0].id)
    }

    @Test
    fun `28 screen disposal with null or mismatched eventId is ignored`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites()

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        val targetHotel = viewModel.uiState.value.hotels.first()
        viewModel.removeFavorite(targetHotel)
        testScheduler.advanceUntilIdle()

        val realEventId = viewModel.uiState.value.activeRemovalEventId

        viewModel.onScreenDisposed(null)
        assertEquals(FavoritesRemovalPhase.UNDO_AVAILABLE, viewModel.uiState.value.removalPhase)

        viewModel.onScreenDisposed("mismatched-id")
        assertEquals(FavoritesRemovalPhase.UNDO_AVAILABLE, viewModel.uiState.value.removalPhase)
        assertEquals(realEventId, viewModel.uiState.value.activeRemovalEventId)
    }

    @Test
    fun `29 cancelling refresh to start a removal produces no user-visible error`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val deferredRefresh = CompletableDeferred<List<Hotel>>()
        val sampleHotel = Hotel(id = "h-1", name = "Test", description = "", city = "", address = "", rating = 4.0, reviewCount = 1, startingPrice = 100, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = true)
        val fakeHotel = FakeHotelRepositoryForFavorites(deferredLoad = deferredRefresh)

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)

        deferredRefresh.complete(listOf(sampleHotel))
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(1, viewModel.uiState.value.hotels.size)
    }

    @Test
    fun `30 cancellation during sign-out produces no late error or Undo event`() = runTest {
        val authFlow = MutableSharedFlow<UserProfile?>(replay = 1)
        val fakeAuth = FakeAuthRepositoryForFavorites(authFlow = authFlow)
        val deferredToggle = CompletableDeferred<Unit>()
        val fakeHotel = FakeHotelRepositoryForFavorites(
            deferredToggle = deferredToggle,
            favorites = listOf(
                Hotel(id = "h-1", name = "Cedar Peak", description = "", city = "", address = "", rating = 4.0, reviewCount = 1, startingPrice = 100, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = true)
            )
        )

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)

        authFlow.emit(UserProfile(id = "u-1", firebaseUid = "fb-1", displayName = "Abhishek", email = "a@test.com"))
        testScheduler.advanceUntilIdle()

        val targetHotel = viewModel.uiState.value.hotels.first()
        viewModel.removeFavorite(targetHotel)

        authFlow.emit(null)
        testScheduler.advanceUntilIdle()

        deferredToggle.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAuthenticated)
        assertTrue(viewModel.uiState.value.hotels.isEmpty())
        assertNull(viewModel.uiState.value.undoEvent)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(FavoritesRemovalPhase.IDLE, viewModel.uiState.value.removalPhase)
    }

    @Test
    fun `31 stale load response from an older request cannot overwrite newer user session state`() = runTest {
        val authFlow = MutableSharedFlow<UserProfile?>(replay = 1)
        val fakeAuth = FakeAuthRepositoryForFavorites(authFlow = authFlow)
        val deferredUser1Favorites = CompletableDeferred<List<Hotel>>()

        val fakeHotel = object : FakeHotelRepositoryForFavorites() {
            var call = 0
            override suspend fun syncFavorites(): List<Hotel> {
                call++
                return if (call == 1) {
                    deferredUser1Favorites.await()
                } else {
                    listOf(
                        Hotel(id = "h-user2", name = "User 2 Hotel", description = "", city = "", address = "", rating = 4.5, reviewCount = 5, startingPrice = 200, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = true)
                    )
                }
            }
        }

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)

        authFlow.emit(UserProfile(id = "u-1", firebaseUid = "fb-user-1", displayName = "User 1", email = "u1@test.com"))
        testScheduler.advanceUntilIdle()

        authFlow.emit(UserProfile(id = "u-2", firebaseUid = "fb-user-2", displayName = "User 2", email = "u2@test.com"))
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.hotels.size)
        assertEquals("h-user2", viewModel.uiState.value.hotels[0].id)

        deferredUser1Favorites.complete(listOf(
            Hotel(id = "h-user1", name = "User 1 Stale Hotel", description = "", city = "", address = "", rating = 4.0, reviewCount = 1, startingPrice = 100, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = true)
        ))
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.hotels.size)
        assertEquals("h-user2", viewModel.uiState.value.hotels[0].id)
    }

    @Test
    fun `32 initial authenticated session triggers synchronization when not hydrated`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites(isInitiallyHydrated = false)

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        assertEquals(1, fakeHotel.loadCallCount)
        assertEquals(2, viewModel.uiState.value.hotels.size)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `33 already hydrated repository does not trigger additional network synchronization on Favorites viewmodel init`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites(isInitiallyHydrated = true)

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        // No network call needed since repository is already hydrated
        assertEquals(0, fakeHotel.loadCallCount)
        assertEquals(2, viewModel.uiState.value.hotels.size)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `34 entering Favorites with loaded data keeps isLoading false and isRefreshing false`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites(isInitiallyHydrated = true)

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertFalse(state.isMutationInProgress)
        assertEquals(2, state.hotels.size)
        assertNull(state.errorMessage)
    }

    @Test
    fun `35 account switching triggers fresh synchronization`() = runTest {
        val authFlow = MutableSharedFlow<UserProfile?>(replay = 1)
        val fakeAuth = FakeAuthRepositoryForFavorites(authFlow = authFlow)
        val fakeHotel = FakeHotelRepositoryForFavorites(isInitiallyHydrated = false)

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)

        authFlow.emit(UserProfile(id = "u-1", firebaseUid = "fb-1", displayName = "User 1", email = "u1@test.com"))
        testScheduler.advanceUntilIdle()
        assertEquals(1, fakeHotel.loadCallCount)

        // Switch account
        fakeHotel.setHydrated(false)
        authFlow.emit(UserProfile(id = "u-2", firebaseUid = "fb-2", displayName = "User 2", email = "u2@test.com"))
        testScheduler.advanceUntilIdle()

        assertEquals(2, fakeHotel.loadCallCount)
    }

    @Test
    fun `36 initial load failure provides functional retry action that loads favorites`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val fakeHotel = FakeHotelRepositoryForFavorites(
            shouldFailLoad = true,
            loadErrorMessage = "Connection timed out",
            isInitiallyHydrated = false,
        )

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        assertEquals(1, fakeHotel.loadCallCount)
        assertEquals("Connection timed out", viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.hotels.isEmpty())

        // User taps Retry
        fakeHotel.shouldFailLoad = false
        viewModel.loadFavorites()
        testScheduler.advanceUntilIdle()

        assertEquals(2, fakeHotel.loadCallCount)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(2, viewModel.uiState.value.hotels.size)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `37 failure during retry leaves error message and populated list is preserved`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val initialHotels = listOf(
            Hotel(id = "h-1", name = "Cedar Peak", description = "", city = "Manali", address = "", rating = 4.8, reviewCount = 10, startingPrice = 5000, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = true)
        )
        val fakeHotel = FakeHotelRepositoryForFavorites(
            favorites = initialHotels,
            shouldFailLoad = false,
            loadErrorMessage = "Network down",
            isInitiallyHydrated = true,
        )

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.hotels.size)

        fakeHotel.shouldFailLoad = true
        viewModel.loadFavorites()
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.hotels.size)
        assertEquals("Network down", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `38 signed-out user does not query favorites endpoint`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites(authFlow = flowOf(null))
        val fakeHotel = FakeHotelRepositoryForFavorites(isInitiallyHydrated = false)

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        assertEquals(0, fakeHotel.loadCallCount)
        assertFalse(viewModel.uiState.value.isAuthenticated)
    }

    @Test
    fun `39 sequential removal allows removing hotel B after hotel A undo window finishes without restart`() = runTest {
        val fakeAuth = FakeAuthRepositoryForFavorites()
        val hotelA = Hotel(id = "h-1", name = "Hotel A", description = "", city = "Delhi", address = "", rating = 4.5, reviewCount = 10, startingPrice = 3000, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = true)
        val hotelB = Hotel(id = "h-2", name = "Hotel B", description = "", city = "Mumbai", address = "", rating = 4.6, reviewCount = 20, startingPrice = 4000, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = true)
        val fakeHotel = FakeHotelRepositoryForFavorites(favorites = listOf(hotelA, hotelB))

        val viewModel = FavoritesViewModel(hotelRepository = fakeHotel, authRepository = fakeAuth)
        testScheduler.advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.hotels.size)

        // 1. Remove hotel A successfully
        viewModel.removeFavorite(hotelA)
        testScheduler.advanceUntilIdle()

        val eventA = viewModel.uiState.value.undoEvent
        assertNotNull(eventA)
        assertEquals("h-1", eventA?.hotel?.id)
        assertEquals(FavoritesRemovalPhase.UNDO_AVAILABLE, viewModel.uiState.value.removalPhase)
        assertTrue(viewModel.uiState.value.isMutationInProgress)

        // 2. Finish/dismiss hotel A's Undo window
        viewModel.onSnackbarDismissed(eventA!!.eventId)
        testScheduler.advanceUntilIdle()

        // Confirm removalPhase == IDLE
        assertEquals(FavoritesRemovalPhase.IDLE, viewModel.uiState.value.removalPhase)
        assertFalse(viewModel.uiState.value.isMutationInProgress)
        assertNull(viewModel.uiState.value.activeRemovalEventId)
        assertNull(viewModel.uiState.value.undoEvent)
        assertEquals(1, viewModel.uiState.value.hotels.size)
        assertEquals("h-2", viewModel.uiState.value.hotels[0].id)

        // 3. Remove hotel B successfully without restarting or leaving Favorites
        viewModel.removeFavorite(hotelB)
        testScheduler.advanceUntilIdle()

        val eventB = viewModel.uiState.value.undoEvent
        assertNotNull(eventB)
        assertEquals("h-2", eventB?.hotel?.id)
        assertEquals(FavoritesRemovalPhase.UNDO_AVAILABLE, viewModel.uiState.value.removalPhase)

        // Confirm two separate backend removal calls occurred
        assertEquals(listOf("h-1" to false, "h-2" to false), fakeHotel.toggleCalls)

        // 4. Confirm Undo still restores the correct hotel only once
        viewModel.undoRemoveFavorite(eventB!!.eventId, hotelB, eventB.originalIndex)
        testScheduler.advanceUntilIdle()

        assertEquals(FavoritesRemovalPhase.IDLE, viewModel.uiState.value.removalPhase)
        assertNull(viewModel.uiState.value.activeRemovalEventId)
        assertNull(viewModel.uiState.value.undoEvent)
        assertEquals(1, viewModel.uiState.value.hotels.size)
        assertEquals("h-2", viewModel.uiState.value.hotels[0].id)

        // Second undo call with same event is safely ignored
        viewModel.undoRemoveFavorite(eventB.eventId, hotelB, eventB.originalIndex)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("h-1" to false, "h-2" to false, "h-2" to true), fakeHotel.toggleCalls)
    }
}
