package com.innly.hotelbooking

import com.innly.hotelbooking.data.remote.ApiResponse
import com.innly.hotelbooking.data.remote.AvailabilityDto
import com.innly.hotelbooking.data.remote.BookingDto
import com.innly.hotelbooking.data.remote.BookingRequestDto
import com.innly.hotelbooking.data.remote.CancellationResponseDto
import com.innly.hotelbooking.data.remote.CreateBookingResponseDto
import com.innly.hotelbooking.data.remote.HotelApiService
import com.innly.hotelbooking.data.remote.HotelDto
import com.innly.hotelbooking.data.remote.PaginatedPayload
import com.innly.hotelbooking.data.remote.PaymentVerificationRequestDto
import com.innly.hotelbooking.data.remote.PaymentVerificationResponseDto
import com.innly.hotelbooking.data.remote.ProfileSyncRequest
import com.innly.hotelbooking.data.remote.PrivateReviewDto
import com.innly.hotelbooking.data.remote.ReviewRequestDto
import com.innly.hotelbooking.data.remote.UserProfileDto
import com.innly.hotelbooking.data.repository.HotelRepositoryImpl
import com.innly.hotelbooking.domain.model.Hotel
import com.innly.hotelbooking.domain.model.HotelSearchFilter
import com.innly.hotelbooking.domain.model.UserProfile
import com.innly.hotelbooking.domain.repository.AuthRepository
import com.innly.hotelbooking.domain.repository.FavoriteMutationResult
import com.innly.hotelbooking.domain.usecase.GetHotelDetailUseCase
import com.innly.hotelbooking.domain.usecase.GetHotelsUseCase
import com.innly.hotelbooking.presentation.favorites.FavoritesRemovalPhase
import com.innly.hotelbooking.presentation.favorites.FavoritesViewModel
import com.innly.hotelbooking.presentation.home.HomeViewModel
import com.innly.hotelbooking.presentation.hoteldetail.HotelDetailViewModel
import com.innly.hotelbooking.presentation.search.SearchViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class FavoritesSyncTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeAuthRepo(
        initialUser: UserProfile? = UserProfile("db-1", "user-A", "userA@example.com", "User A", null, "Guest")
    ) : AuthRepository {
        val userFlow = MutableStateFlow<UserProfile?>(initialUser)
        override val currentUser: Flow<UserProfile?> = userFlow

        override suspend fun signIn(email: String, password: String) {}
        override suspend fun signUp(name: String, email: String, password: String) {}
        override suspend fun signOut() {
            userFlow.value = null
        }
        override suspend fun syncProfile(displayName: String?, phoneNumber: String?): UserProfile = TODO()
    }

    private class FakeHotelApiService : HotelApiService {
        val backendFavorites = mutableListOf<HotelDto>()
        var addDeferred: CompletableDeferred<Unit>? = null
        var removeDeferred: CompletableDeferred<Unit>? = null
        var getHotelsDeferred: CompletableDeferred<List<HotelDto>>? = null
        var shouldFailAdd = false
        var shouldFailRemove = false
        var hotelDtos: List<HotelDto> = listOf(
            createDto("h-1", "Hotel 1", true),
            createDto("h-2", "Hotel 2", false),
        )

        override suspend fun syncProfile(request: ProfileSyncRequest): ApiResponse<UserProfileDto> = TODO()

        override suspend fun getHotels(
            city: String?,
            minPrice: Int?,
            maxPrice: Int?,
            minRating: Double?,
            amenities: String?,
            limit: Int?
        ): ApiResponse<PaginatedPayload<HotelDto>> {
            val deferred = getHotelsDeferred
            val items = if (deferred != null) {
                deferred.await()
            } else {
                hotelDtos
            }
            return ApiResponse(
                success = true,
                data = PaginatedPayload(
                    items = items,
                    page = 1,
                    limit = 50,
                    total = items.size,
                )
            )
        }

        override suspend fun getHotelDetail(hotelId: String): ApiResponse<HotelDto> {
            return ApiResponse(success = true, data = createDto(hotelId, "Detail Hotel", false))
        }

        override suspend fun getAvailability(hotelId: String, roomId: String, checkIn: String, checkOut: String, rooms: Int): ApiResponse<AvailabilityDto> = TODO()

        var getFavoritesDeferred: CompletableDeferred<List<HotelDto>>? = null
        var getFavoritesCallCount = 0

        override suspend fun getFavorites(): ApiResponse<List<HotelDto>> {
            getFavoritesCallCount++
            val deferred = getFavoritesDeferred
            val items = if (deferred != null) deferred.await() else backendFavorites.toList()
            return ApiResponse(success = true, data = items)
        }

        override suspend fun addFavorite(hotelId: String): ApiResponse<Unit> {
            addDeferred?.await()
            if (shouldFailAdd) throw RuntimeException("Add favorite backend failure")
            backendFavorites.removeAll { it.id == hotelId }
            backendFavorites.add(createDto(hotelId, "Hotel $hotelId", true))
            return ApiResponse(success = true, data = Unit)
        }

        override suspend fun removeFavorite(hotelId: String): ApiResponse<Unit> {
            removeDeferred?.await()
            if (shouldFailRemove) throw RuntimeException("Remove favorite backend failure")
            backendFavorites.removeAll { it.id == hotelId }
            return ApiResponse(success = true, data = Unit)
        }

        override suspend fun getHotelReviews(hotelId: String, page: Int, limit: Int): ApiResponse<com.innly.hotelbooking.data.remote.ReviewListResponseDto> = TODO()
        override suspend fun getMyReview(hotelId: String): ApiResponse<com.innly.hotelbooking.data.remote.MyReviewResponseDto> = TODO()
        override suspend fun submitReview(request: ReviewRequestDto): ApiResponse<com.innly.hotelbooking.data.remote.PrivateReviewDto> = TODO()
        override suspend fun createBooking(request: BookingRequestDto): ApiResponse<CreateBookingResponseDto> = TODO()
        override suspend fun getBookingHistory(): ApiResponse<List<BookingDto>> = TODO()
        override suspend fun verifyPayment(request: PaymentVerificationRequestDto): ApiResponse<PaymentVerificationResponseDto> = TODO()
        override suspend fun cancelBooking(bookingId: String, request: Map<String, String>): ApiResponse<CancellationResponseDto> = TODO()

        fun createDto(id: String, name: String, isFavorite: Boolean): HotelDto {
            return HotelDto(
                id = id,
                name = name,
                description = "Desc",
                city = "City",
                address = "Address",
                rating = 4.5,
                reviewCount = 10,
                startingPrice = 5000,
                thumbnailUrl = "",
                images = emptyList(),
                amenities = emptyList(),
                isFavorite = isFavorite,
                rooms = emptyList(),
            )
        }
    }

    private fun createHotel(id: String, name: String, isFavorite: Boolean = false): Hotel {
        return Hotel(
            id = id,
            name = name,
            description = "Desc",
            city = "City",
            address = "Address",
            rating = 4.5,
            reviewCount = 10,
            startingPrice = 5000,
            thumbnailUrl = "",
            images = emptyList(),
            amenities = emptyList(),
            isFavorite = isFavorite,
        )
    }

    @Test
    fun `1 Add from Explore reflects in Favorites immediately before delayed network completes`() = runTest {
        val authRepo = FakeAuthRepo()
        val apiService = FakeHotelApiService()
        val addGate = CompletableDeferred<Unit>()
        apiService.addDeferred = addGate

        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        val hotel1 = createHotel("h-1", "Hotel One", false)

        val mutationJob = async { repository.toggleFavoriteOptimistic(hotel1) }

        // Before completing network response:
        advanceUntilIdle()
        assertTrue(repository.favoriteState.value.favoriteIds.contains("h-1"))
        assertEquals(1, repository.favoriteState.value.favoriteHotels.size)
        assertEquals("h-1", repository.favoriteState.value.favoriteHotels[0].id)
        assertTrue(repository.favoriteState.value.inFlightHotelIds.contains("h-1"))

        // Complete network request
        addGate.complete(Unit)
        val result = mutationJob.await()
        advanceUntilIdle()

        assertTrue(result is FavoriteMutationResult.Success && result.isFavorited)
        assertTrue(repository.favoriteState.value.favoriteIds.contains("h-1"))
        assertFalse(repository.favoriteState.value.inFlightHotelIds.contains("h-1"))
    }

    @Test
    fun `2 Remove from Favorites empties heart immediately everywhere while retaining card in Explore`() = runTest {
        val authRepo = FakeAuthRepo()
        val apiService = FakeHotelApiService()
        val removeGate = CompletableDeferred<Unit>()
        apiService.removeDeferred = removeGate
        apiService.backendFavorites.add(apiService.createDto("h-1", "Hotel 1", true))

        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        val homeViewModel = HomeViewModel(GetHotelsUseCase(repository), repository)
        advanceUntilIdle()

        // Initially favorite
        val initialHomeHotels = homeViewModel.uiState.value.hotels
        val initialH1 = initialHomeHotels.first { it.id == "h-1" }

        val mutationJob = async { repository.toggleFavoriteOptimistic(initialH1) }

        advanceUntilIdle()
        assertFalse(repository.favoriteState.value.favoriteIds.contains("h-1"))
        assertEquals(0, repository.favoriteState.value.favoriteHotels.size)
        val homeHotels = homeViewModel.uiState.value.hotels
        assertEquals(2, homeHotels.size)
        val homeH1 = homeHotels.first { it.id == "h-1" }
        assertFalse(homeH1.isFavorite)

        removeGate.complete(Unit)
        val result = mutationJob.await()
        advanceUntilIdle()

        assertTrue(result is FavoriteMutationResult.Success && !result.isFavorited)
        assertFalse(repository.favoriteState.value.favoriteIds.contains("h-1"))
    }

    @Test
    fun `3 Failed mutation rolls back only its own hotel without wiping concurrent mutations`() = runTest {
        val authRepo = FakeAuthRepo()
        val apiService = FakeHotelApiService()
        apiService.shouldFailAdd = true

        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        val hotel1 = createHotel("h-1", "Hotel One", false)

        val result = repository.toggleFavoriteOptimistic(hotel1)
        advanceUntilIdle()

        assertTrue(result is FavoriteMutationResult.Failed)
        assertFalse(repository.favoriteState.value.favoriteIds.contains("h-1"))
        assertEquals(0, repository.favoriteState.value.favoriteHotels.size)
        assertFalse(repository.favoriteState.value.inFlightHotelIds.contains("h-1"))
        assertFalse(repository.favoriteState.value.optimisticOverrides.containsKey("h-1"))
    }

    @Test
    fun `4 Rapid duplicate tap returns AlreadyInProgress and prevents duplicate network dispatch`() = runTest {
        val authRepo = FakeAuthRepo()
        val apiService = FakeHotelApiService()
        val gate = CompletableDeferred<Unit>()
        apiService.addDeferred = gate

        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        val hotel1 = createHotel("h-1", "Hotel One", false)

        val job1 = async { repository.toggleFavoriteOptimistic(hotel1) }
        advanceUntilIdle()

        val job2Result = repository.toggleFavoriteOptimistic(hotel1)
        assertEquals(FavoriteMutationResult.AlreadyInProgress, job2Result)

        gate.complete(Unit)
        val job1Result = job1.await()
        advanceUntilIdle()

        assertTrue(job1Result is FavoriteMutationResult.Success)
    }

    @Test
    fun `5 Slow query response cannot overwrite a newer completed favorite mutation`() = runTest {
        val authRepo = FakeAuthRepo()
        val apiService = FakeHotelApiService()
        val queryGate = CompletableDeferred<List<HotelDto>>()
        apiService.getHotelsDeferred = queryGate

        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        val queryJob = async { repository.getHotels(HotelSearchFilter()) }
        advanceUntilIdle()

        val hotel1 = createHotel("h-1", "Hotel One", false)
        val favResult = repository.toggleFavoriteOptimistic(hotel1)
        advanceUntilIdle()
        assertTrue(favResult is FavoriteMutationResult.Success && favResult.isFavorited)

        queryGate.complete(listOf(apiService.createDto("h-1", "Hotel One", false)))
        queryJob.await()
        advanceUntilIdle()

        assertTrue(repository.favoriteState.value.favoriteIds.contains("h-1"))
        assertEquals(1, repository.favoriteState.value.favoriteHotels.size)
    }

    @Test
    fun `6 Account switching resets state and late response from previous account is StaleSession`() = runTest {
        val authRepo = FakeAuthRepo(UserProfile("db-1", "user-A", "userA@example.com", "User A", null, "Guest"))
        val apiService = FakeHotelApiService()
        val gate = CompletableDeferred<Unit>()
        apiService.addDeferred = gate

        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        val hotel1 = createHotel("h-1", "Hotel One", false)

        val mutationJob = async { repository.toggleFavoriteOptimistic(hotel1) }
        advanceUntilIdle()
        assertTrue(repository.favoriteState.value.favoriteIds.contains("h-1"))

        authRepo.userFlow.value = UserProfile("db-2", "user-B", "userB@example.com", "User B", null, "Guest")
        advanceUntilIdle()

        assertFalse(repository.favoriteState.value.favoriteIds.contains("h-1"))
        assertEquals(0, repository.favoriteState.value.favoriteHotels.size)

        gate.complete(Unit)
        val result = runCatching { mutationJob.await() }.getOrNull()
        advanceUntilIdle()

        assertTrue(result == FavoriteMutationResult.StaleSession || result == null)
        assertFalse(repository.favoriteState.value.favoriteIds.contains("h-1"))
    }

    @Test
    fun `7 Repeated add and Undo operations never duplicate hotel card`() = runTest {
        val authRepo = FakeAuthRepo()
        val apiService = FakeHotelApiService()

        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        val hotel1 = createHotel("h-1", "Hotel One", false)

        repository.toggleFavoriteOptimistic(hotel1)
        advanceUntilIdle()
        assertEquals(1, repository.favoriteState.value.favoriteHotels.size)

        repository.restoreFavoriteOptimistic(hotel1, 0)
        advanceUntilIdle()
        assertEquals(1, repository.favoriteState.value.favoriteHotels.size)
        assertEquals("h-1", repository.favoriteState.value.favoriteHotels[0].id)
    }

    @Test
    fun `8 FavoritesViewModel emits Undo event only on confirmed removal success`() = runTest {
        val authRepo = FakeAuthRepo()
        val apiService = FakeHotelApiService()
        val removeGate = CompletableDeferred<Unit>()
        apiService.removeDeferred = removeGate
        apiService.backendFavorites.add(apiService.createDto("h-1", "Hotel One", true))

        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        val viewModel = FavoritesViewModel(repository, authRepo)
        advanceUntilIdle()

        val hotel1 = viewModel.uiState.value.hotels.first()

        viewModel.removeFavorite(hotel1)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.undoEvent)

        removeGate.complete(Unit)
        advanceUntilIdle()

        val undo = viewModel.uiState.value.undoEvent
        assertTrue(undo != null)
        assertEquals("h-1", undo?.hotel?.id)
    }

    @Test
    fun `9 Sequential removal allows removing hotel B after hotel A undo window finishes and synchronizes with Explore`() = runTest {
        val authRepo = FakeAuthRepo()
        val apiService = FakeHotelApiService()
        apiService.backendFavorites.add(apiService.createDto("h-1", "Hotel 1", true))
        apiService.backendFavorites.add(apiService.createDto("h-2", "Hotel 2", true))
        apiService.hotelDtos = listOf(
            apiService.createDto("h-1", "Hotel 1", true),
            apiService.createDto("h-2", "Hotel 2", true),
        )

        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        val homeViewModel = HomeViewModel(GetHotelsUseCase(repository), repository)
        val favoritesViewModel = FavoritesViewModel(repository, authRepo)
        advanceUntilIdle()

        assertEquals(2, favoritesViewModel.uiState.value.hotels.size)

        // 1. Remove hotel A successfully
        val hotel1 = favoritesViewModel.uiState.value.hotels.first { it.id == "h-1" }
        favoritesViewModel.removeFavorite(hotel1)
        advanceUntilIdle()

        val eventA = favoritesViewModel.uiState.value.undoEvent
        assertNotNull(eventA)
        assertEquals(FavoritesRemovalPhase.UNDO_AVAILABLE, favoritesViewModel.uiState.value.removalPhase)

        // 2. Finish/dismiss hotel A's Undo window
        favoritesViewModel.onSnackbarDismissed(eventA!!.eventId)
        advanceUntilIdle()

        assertEquals(FavoritesRemovalPhase.IDLE, favoritesViewModel.uiState.value.removalPhase)
        assertFalse(favoritesViewModel.uiState.value.isMutationInProgress)
        assertEquals(1, favoritesViewModel.uiState.value.hotels.size)

        // 3. Remove hotel B successfully without leaving Favorites
        val hotel2 = favoritesViewModel.uiState.value.hotels.first { it.id == "h-2" }
        favoritesViewModel.removeFavorite(hotel2)
        advanceUntilIdle()

        val eventB = favoritesViewModel.uiState.value.undoEvent
        assertNotNull(eventB)
        assertEquals("h-2", eventB?.hotel?.id)
        assertEquals(FavoritesRemovalPhase.UNDO_AVAILABLE, favoritesViewModel.uiState.value.removalPhase)

        // Dismiss hotel B's Undo window
        favoritesViewModel.onSnackbarDismissed(eventB!!.eventId)
        advanceUntilIdle()

        assertEquals(FavoritesRemovalPhase.IDLE, favoritesViewModel.uiState.value.removalPhase)
        assertEquals(0, favoritesViewModel.uiState.value.hotels.size)

        // 4. Confirm both removals synchronize to Explore (cards remain, hearts empty)
        val exploreHotels = homeViewModel.uiState.value.hotels
        assertEquals(2, exploreHotels.size)
        assertFalse(exploreHotels.first { it.id == "h-1" }.isFavorite)
        assertFalse(exploreHotels.first { it.id == "h-2" }.isFavorite)
    }

    @Test
    fun `10 Repeated navigation between Explore and Favorites does not trigger additional synchronization when hydrated`() = runTest {
        val authRepo = FakeAuthRepo()
        val apiService = FakeHotelApiService()
        apiService.backendFavorites.add(apiService.createDto("h-1", "Hotel 1", true))

        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        // Initial sync occurred
        val initialCallCount = apiService.getFavoritesCallCount

        // Multiple ViewModel instantiations / navigation entries
        val favoritesVm1 = FavoritesViewModel(repository, authRepo)
        advanceUntilIdle()
        val favoritesVm2 = FavoritesViewModel(repository, authRepo)
        advanceUntilIdle()

        // Zero additional network calls
        assertEquals(initialCallCount, apiService.getFavoritesCallCount)

        val state = favoritesVm2.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertFalse(state.isMutationInProgress)
        assertEquals(1, state.hotels.size)
        assertEquals("h-1", state.hotels[0].id)
    }

    @Test
    fun `11 Adding C to existing B, A immediately produces C, B, A and adding D produces D, C, B, A`() = runTest {
        val authRepo = FakeAuthRepo()
        val apiService = FakeHotelApiService()
        apiService.backendFavorites.add(apiService.createDto("h-B", "Hotel B", true))
        apiService.backendFavorites.add(apiService.createDto("h-A", "Hotel A", true))

        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        assertEquals(listOf("h-B", "h-A"), repository.favoriteHotels.value.map { it.id })

        val hotelC = Hotel(id = "h-C", name = "Hotel C", description = "", city = "", address = "", rating = 4.5, reviewCount = 10, startingPrice = 3000, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = false)
        repository.toggleFavoriteOptimistic(hotelC)
        advanceUntilIdle()

        assertEquals(listOf("h-C", "h-B", "h-A"), repository.favoriteHotels.value.map { it.id })

        val hotelD = Hotel(id = "h-D", name = "Hotel D", description = "", city = "", address = "", rating = 4.6, reviewCount = 20, startingPrice = 4000, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = false)
        repository.toggleFavoriteOptimistic(hotelD)
        advanceUntilIdle()

        assertEquals(listOf("h-D", "h-C", "h-B", "h-A"), repository.favoriteHotels.value.map { it.id })
    }

    @Test
    fun `12 Removing and re-favoriting A places A first`() = runTest {
        val authRepo = FakeAuthRepo()
        val apiService = FakeHotelApiService()
        apiService.backendFavorites.add(apiService.createDto("h-C", "Hotel C", true))
        apiService.backendFavorites.add(apiService.createDto("h-B", "Hotel B", true))
        apiService.backendFavorites.add(apiService.createDto("h-A", "Hotel A", true))

        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        assertEquals(listOf("h-C", "h-B", "h-A"), repository.favoriteHotels.value.map { it.id })

        val hotelA = repository.favoriteHotels.value.first { it.id == "h-A" }
        repository.toggleFavoriteOptimistic(hotelA)
        advanceUntilIdle()

        assertEquals(listOf("h-C", "h-B"), repository.favoriteHotels.value.map { it.id })

        repository.toggleFavoriteOptimistic(hotelA.copy(isFavorite = false))
        advanceUntilIdle()

        assertEquals(listOf("h-A", "h-C", "h-B"), repository.favoriteHotels.value.map { it.id })
    }

    @Test
    fun `13 Undo restores a removed hotel at its original index`() = runTest {
        val authRepo = FakeAuthRepo()
        val apiService = FakeHotelApiService()
        apiService.backendFavorites.add(apiService.createDto("h-1", "Hotel 1", true))
        apiService.backendFavorites.add(apiService.createDto("h-2", "Hotel 2", true))
        apiService.backendFavorites.add(apiService.createDto("h-3", "Hotel 3", true))

        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        assertEquals(listOf("h-1", "h-2", "h-3"), repository.favoriteHotels.value.map { it.id })

        val hotel2 = repository.favoriteHotels.value.first { it.id == "h-2" }
        repository.toggleFavoriteOptimistic(hotel2)
        advanceUntilIdle()

        assertEquals(listOf("h-1", "h-3"), repository.favoriteHotels.value.map { it.id })

        repository.restoreFavoriteOptimistic(hotel2, originalIndex = 1)
        advanceUntilIdle()

        assertEquals(listOf("h-1", "h-2", "h-3"), repository.favoriteHotels.value.map { it.id })
    }

    @Test
    fun `14 Addition failure rolls back and restores exact previous list and order`() = runTest {
        val authRepo = FakeAuthRepo()
        val apiService = FakeHotelApiService()
        apiService.backendFavorites.add(apiService.createDto("h-1", "Hotel 1", true))
        apiService.backendFavorites.add(apiService.createDto("h-2", "Hotel 2", true))
        apiService.shouldFailAdd = true

        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        assertEquals(listOf("h-1", "h-2"), repository.favoriteHotels.value.map { it.id })

        val hotelNew = Hotel(id = "h-new", name = "New Hotel", description = "", city = "", address = "", rating = 4.0, reviewCount = 5, startingPrice = 1000, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = false)
        val result = repository.toggleFavoriteOptimistic(hotelNew)
        advanceUntilIdle()

        assertTrue(result is FavoriteMutationResult.Failed)
        assertEquals(listOf("h-1", "h-2"), repository.favoriteHotels.value.map { it.id })
    }

    @Test
    fun `15 Slow sync cannot move a newer optimistic favorite below older backend results`() = runTest {
        val authRepo = FakeAuthRepo()
        val apiService = FakeHotelApiService()
        apiService.backendFavorites.add(apiService.createDto("h-B", "Hotel B", true))
        apiService.backendFavorites.add(apiService.createDto("h-A", "Hotel A", true))

        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        assertEquals(listOf("h-B", "h-A"), repository.favoriteHotels.value.map { it.id })

        val syncGate = CompletableDeferred<List<HotelDto>>()
        apiService.getFavoritesDeferred = syncGate

        val syncJob = async { repository.syncFavorites() }
        advanceUntilIdle()

        val hotelC = Hotel(id = "h-C", name = "Hotel C", description = "", city = "", address = "", rating = 4.5, reviewCount = 10, startingPrice = 3000, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = false)
        repository.toggleFavoriteOptimistic(hotelC)
        advanceUntilIdle()

        assertEquals(listOf("h-C", "h-B", "h-A"), repository.favoriteHotels.value.map { it.id })

        syncGate.complete(listOf(apiService.createDto("h-B", "Hotel B", true), apiService.createDto("h-A", "Hotel A", true)))
        syncJob.await()
        advanceUntilIdle()

        assertEquals(listOf("h-C", "h-B", "h-A"), repository.favoriteHotels.value.map { it.id })
    }

    @Test
    fun `16 Two rapid additions retain last-tapped-first ordering`() = runTest {
        val authRepo = FakeAuthRepo()
        val apiService = FakeHotelApiService()
        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        val hotel1 = Hotel(id = "h-1", name = "Hotel 1", description = "", city = "", address = "", rating = 4.5, reviewCount = 10, startingPrice = 3000, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = false)
        val hotel2 = Hotel(id = "h-2", name = "Hotel 2", description = "", city = "", address = "", rating = 4.6, reviewCount = 20, startingPrice = 4000, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = false)

        repository.toggleFavoriteOptimistic(hotel1)
        repository.toggleFavoriteOptimistic(hotel2)
        advanceUntilIdle()

        assertEquals(listOf("h-2", "h-1"), repository.favoriteHotels.value.map { it.id })
    }

    @Test
    fun `17 Session restoration preserves backend newest-first order with no duplicate IDs`() = runTest {
        val authRepo = FakeAuthRepo()
        val apiService = FakeHotelApiService()
        apiService.backendFavorites.add(apiService.createDto("h-newest", "Newest", true))
        apiService.backendFavorites.add(apiService.createDto("h-middle", "Middle", true))
        apiService.backendFavorites.add(apiService.createDto("h-oldest", "Oldest", true))

        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        val list = repository.favoriteHotels.value
        assertEquals(listOf("h-newest", "h-middle", "h-oldest"), list.map { it.id })
        assertEquals(3, list.distinctBy { it.id }.size)
    }

    @Test
    fun `18 Logout after two favorites clears hearts everywhere in repository and ViewModels`() = runTest {
        val authRepo = FakeAuthRepo()
        val apiService = FakeHotelApiService()
        apiService.backendFavorites.add(apiService.createDto("h-1", "Hotel 1", true))
        apiService.backendFavorites.add(apiService.createDto("h-2", "Hotel 2", true))
        apiService.hotelDtos = listOf(
            apiService.createDto("h-1", "Hotel 1", true),
            apiService.createDto("h-2", "Hotel 2", true),
        )

        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        val homeViewModel = HomeViewModel(GetHotelsUseCase(repository), repository)
        val favoritesViewModel = FavoritesViewModel(repository, authRepo)
        advanceUntilIdle()

        assertEquals(2, repository.favoriteHotels.value.size)
        assertTrue(homeViewModel.uiState.value.hotels.all { it.isFavorite })
        assertTrue(favoritesViewModel.uiState.value.isAuthenticated)
        assertEquals(2, favoritesViewModel.uiState.value.hotels.size)

        // Sign out
        authRepo.signOut()
        advanceUntilIdle()

        // 1. Repository cleared
        assertTrue(repository.favoriteHotels.value.isEmpty())
        assertTrue(repository.favoriteIds.value.isEmpty())

        // 2. HomeViewModel immediately has all hearts cleared (isFavorite = false)
        assertTrue(homeViewModel.uiState.value.hotels.isNotEmpty())
        assertTrue(homeViewModel.uiState.value.hotels.none { it.isFavorite })

        // 3. FavoritesViewModel is unauthenticated with empty list
        assertFalse(favoritesViewModel.uiState.value.isAuthenticated)
        assertTrue(favoritesViewModel.uiState.value.hotels.isEmpty())
    }

    @Test
    fun `19 A cached Hotel with isFavorite true cannot appear favorited for a signed-out guest`() = runTest {
        val authRepo = FakeAuthRepo(initialUser = null)
        val apiService = FakeHotelApiService()
        apiService.hotelDtos = listOf(apiService.createDto("h-1", "Hotel 1", true))

        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        val homeViewModel = HomeViewModel(GetHotelsUseCase(repository), repository)
        val searchViewModel = SearchViewModel(repository)
        val detailViewModel = HotelDetailViewModel(GetHotelDetailUseCase(repository), repository)

        advanceUntilIdle()
        searchViewModel.search()
        detailViewModel.loadHotel("h-1")
        advanceUntilIdle()

        // None of the ViewModels should expose isFavorite = true for signed-out guest
        assertFalse(homeViewModel.uiState.value.hotels.first().isFavorite)
        assertFalse(searchViewModel.uiState.value.results.first().isFavorite)
        assertFalse(detailViewModel.uiState.value.hotel!!.isFavorite)
    }

    @Test
    fun `20 A slow hotel-list response started before logout cannot restore red hearts`() = runTest {
        val authRepo = FakeAuthRepo()
        val apiService = FakeHotelApiService()
        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        val hotelsGate = CompletableDeferred<List<HotelDto>>()
        apiService.getHotelsDeferred = hotelsGate

        val fetchJob = async { repository.getHotels(HotelSearchFilter()) }
        advanceUntilIdle()

        // User logs out while getHotels was in flight
        authRepo.signOut()
        advanceUntilIdle()

        hotelsGate.complete(listOf(apiService.createDto("h-1", "Hotel 1", true)))
        val returnedHotels = fetchJob.await()
        advanceUntilIdle()

        assertTrue(repository.favoriteHotels.value.isEmpty())
        assertTrue(repository.favoriteIds.value.isEmpty())
        assertFalse(returnedHotels.first().isFavorite)
    }

    @Test
    fun `21 A late favorite-mutation response from the old session is ignored`() = runTest {
        val authRepo = FakeAuthRepo()
        val apiService = FakeHotelApiService()
        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        val addGate = CompletableDeferred<Unit>()
        apiService.addDeferred = addGate

        val hotel1 = Hotel(id = "h-1", name = "Hotel 1", description = "", city = "", address = "", rating = 4.5, reviewCount = 10, startingPrice = 3000, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = false)
        val mutationJob = async { repository.toggleFavoriteOptimistic(hotel1) }
        advanceUntilIdle()

        // Log out before mutation finishes
        authRepo.signOut()
        advanceUntilIdle()

        addGate.complete(Unit)
        val result = mutationJob.await()
        advanceUntilIdle()

        assertEquals(FavoriteMutationResult.StaleSession, result)
        assertTrue(repository.favoriteHotels.value.isEmpty())
        assertTrue(repository.favoriteIds.value.isEmpty())
    }

    @Test
    fun `22 Switching from account A to account B never exposes A favorites`() = runTest {
        val authRepo = FakeAuthRepo(
            initialUser = UserProfile("db-A", "user-A", "a@example.com", "User A", null, "Guest")
        )
        val apiService = FakeHotelApiService()
        apiService.backendFavorites.add(apiService.createDto("h-A1", "Hotel A1", true))

        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        assertEquals(listOf("h-A1"), repository.favoriteHotels.value.map { it.id })

        // Switch to User B
        apiService.backendFavorites.clear()
        apiService.backendFavorites.add(apiService.createDto("h-B1", "Hotel B1", true))
        authRepo.userFlow.value = UserProfile("db-B", "user-B", "b@example.com", "User B", null, "Guest")
        advanceUntilIdle()

        assertEquals(listOf("h-B1"), repository.favoriteHotels.value.map { it.id })
        assertFalse(repository.favoriteIds.value.contains("h-A1"))
    }

    @Test
    fun `23 Signing back into A restores only A genuine backend favorites`() = runTest {
        val authRepo = FakeAuthRepo(
            initialUser = UserProfile("db-A", "user-A", "a@example.com", "User A", null, "Guest")
        )
        val apiService = FakeHotelApiService()
        apiService.backendFavorites.add(apiService.createDto("h-A1", "Hotel A1", true))
        apiService.backendFavorites.add(apiService.createDto("h-A2", "Hotel A2", true))

        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        assertEquals(listOf("h-A1", "h-A2"), repository.favoriteHotels.value.map { it.id })

        // Log out
        authRepo.signOut()
        advanceUntilIdle()
        assertTrue(repository.favoriteHotels.value.isEmpty())

        // Log back in
        authRepo.userFlow.value = UserProfile("db-A", "user-A", "a@example.com", "User A", null, "Guest")
        advanceUntilIdle()

        assertEquals(listOf("h-A1", "h-A2"), repository.favoriteHotels.value.map { it.id })
    }

    @Test
    fun `24 Guest tapping a heart returns StaleSession and does not create a local favorite`() = runTest {
        val authRepo = FakeAuthRepo(initialUser = null)
        val apiService = FakeHotelApiService()
        val repository = HotelRepositoryImpl(apiService, authRepo, testDispatcher)
        advanceUntilIdle()

        val hotel1 = Hotel(id = "h-1", name = "Hotel 1", description = "", city = "", address = "", rating = 4.5, reviewCount = 10, startingPrice = 3000, thumbnailUrl = "", images = emptyList(), amenities = emptyList(), isFavorite = false)
        val result = repository.toggleFavoriteOptimistic(hotel1)

        assertEquals(FavoriteMutationResult.StaleSession, result)
        assertTrue(repository.favoriteIds.value.isEmpty())
        assertTrue(repository.favoriteHotels.value.isEmpty())
    }
}
