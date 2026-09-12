package com.innly.hotelbooking.data.repository

import com.innly.hotelbooking.data.remote.HotelApiService
import com.innly.hotelbooking.data.remote.ReviewRequestDto
import com.innly.hotelbooking.data.remote.toDomain
import com.innly.hotelbooking.domain.model.Availability
import com.innly.hotelbooking.domain.model.Hotel
import com.innly.hotelbooking.domain.model.HotelSearchFilter
import com.innly.hotelbooking.domain.model.MyReviewState
import com.innly.hotelbooking.domain.model.PrivateReview
import com.innly.hotelbooking.domain.model.PublicReviewPage
import com.innly.hotelbooking.domain.model.Review
import com.innly.hotelbooking.domain.repository.AuthRepository
import com.innly.hotelbooking.domain.repository.FavoriteMutationResult
import com.innly.hotelbooking.domain.repository.FavoriteState
import com.innly.hotelbooking.domain.repository.HotelRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private fun List<Hotel>.upsertHotel(hotel: Hotel, targetIndex: Int? = null): List<Hotel> {
    val existingIndex = indexOfFirst { it.id == hotel.id }
    val updated = ArrayList(this)
    if (existingIndex != -1) {
        if (targetIndex != null && targetIndex != existingIndex) {
            updated.removeAt(existingIndex)
            val boundedIndex = targetIndex.coerceIn(0, updated.size)
            updated.add(boundedIndex, hotel)
        } else {
            updated[existingIndex] = hotel
        }
    } else if (targetIndex != null) {
        val boundedIndex = targetIndex.coerceIn(0, updated.size)
        updated.add(boundedIndex, hotel)
    } else {
        updated.add(hotel)
    }
    return updated
}

private fun List<Hotel>.removeHotel(hotelId: String): List<Hotel> {
    return filterNot { it.id == hotelId }
}

private data class MutationContext(
    val sessionGen: Long,
    val uid: String?,
    val mutationId: String,
    val targetFavorite: Boolean,
    val previousIndex: Int,
    val previousKnown: Boolean?,
)

@Singleton
class HotelRepositoryImpl(
    private val apiService: HotelApiService,
    private val authRepository: AuthRepository,
    coroutineDispatcher: CoroutineDispatcher,
) : HotelRepository {

    @Inject
    constructor(
        apiService: HotelApiService,
        authRepository: AuthRepository,
    ) : this(apiService, authRepository, Dispatchers.IO)

    private val repositoryScope = CoroutineScope(SupervisorJob() + coroutineDispatcher)
    private val mutex = Mutex()
    private val activeMutationJobs = ConcurrentHashMap<String, Job>()

    private val _favoriteState = MutableStateFlow(FavoriteState())
    override val favoriteState: StateFlow<FavoriteState> = _favoriteState.asStateFlow()

    override val favoriteIds: StateFlow<Set<String>> = _favoriteState
        .map { it.favoriteIds }
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptySet())

    override val favoriteHotels: StateFlow<List<Hotel>> = _favoriteState
        .map { it.favoriteHotels }
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptyList())

    override val inFlightFavoriteHotelIds: StateFlow<Set<String>> = _favoriteState
        .map { it.inFlightHotelIds }
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptySet())

    override val isFavoritesHydrated: StateFlow<Boolean> = _favoriteState
        .map { it.isHydrated }
        .stateIn(repositoryScope, SharingStarted.Eagerly, false)

    init {
        repositoryScope.launch {
            authRepository.currentUser.collect { user ->
                val newUid = user?.firebaseUid?.takeIf { it.isNotBlank() }
                val jobsToCancel = mutableListOf<Job>()

                mutex.withLock {
                    val currentUid = _favoriteState.value.activeSessionUid
                    if (newUid != currentUid) {
                        jobsToCancel.addAll(activeMutationJobs.values)
                        activeMutationJobs.clear()

                        _favoriteState.update {
                            FavoriteState(
                                activeSessionUid = newUid,
                                sessionGeneration = it.sessionGeneration + 1L,
                                stateRevision = it.stateRevision + 1L,
                            )
                        }
                    }
                }

                // Cancel captured jobs after releasing the Mutex
                jobsToCancel.forEach { it.cancel() }

                if (newUid != null) {
                    runCatching { syncFavorites() }
                }
            }
        }
    }

    override suspend fun getHotels(filter: HotelSearchFilter): List<Hotel> {
        val (startSessionGen, startRevisions, startUid) = mutex.withLock {
            Triple(_favoriteState.value.sessionGeneration, _favoriteState.value.hotelRevisions, _favoriteState.value.activeSessionUid)
        }
        val hotels = apiService.getHotels(
            city = filter.city.ifBlank { null },
            minPrice = filter.minPrice,
            maxPrice = filter.maxPrice,
            minRating = filter.minRating,
            amenities = filter.amenities.takeIf { it.isNotEmpty() }?.joinToString(","),
            limit = 50,
        ).data.items.map { it.toDomain() }

        if (startUid != null) {
            seedFavoritesFromHotelsInternal(hotels, startRevisions, startSessionGen, startUid)
        }
        val currentUid = mutex.withLock { _favoriteState.value.activeSessionUid }
        return if (currentUid == null) hotels.map { it.copy(isFavorite = false) } else hotels
    }

    override suspend fun getHotelDetail(hotelId: String): Hotel {
        val (startSessionGen, startRevisions, startUid) = mutex.withLock {
            Triple(_favoriteState.value.sessionGeneration, _favoriteState.value.hotelRevisions, _favoriteState.value.activeSessionUid)
        }
        val hotel = apiService.getHotelDetail(hotelId).data.toDomain()
        if (startUid != null) {
            seedFavoritesFromHotelsInternal(listOf(hotel), startRevisions, startSessionGen, startUid)
        }
        val currentUid = mutex.withLock { _favoriteState.value.activeSessionUid }
        return if (currentUid == null) hotel.copy(isFavorite = false) else hotel
    }

    private suspend fun seedFavoritesFromHotelsInternal(
        hotels: List<Hotel>,
        startRevisions: Map<String, Long>,
        startSessionGen: Long,
        startUid: String,
    ) {
        mutex.withLock {
            val current = _favoriteState.value
            if (current.sessionGeneration != startSessionGen || current.activeSessionUid == null || current.activeSessionUid != startUid) {
                return
            }

            _favoriteState.update { state ->
                var newFavoriteIds = state.favoriteIds
                var newFavoriteHotels = state.favoriteHotels
                var newKnownStates = state.knownFavoriteStates

                for (hotel in hotels) {
                    val currentRev = state.hotelRevisions[hotel.id] ?: 0L
                    val startRev = startRevisions[hotel.id] ?: 0L

                    // Skip if a mutation occurred during the request or is currently in-flight/overridden
                    if (currentRev > startRev || hotel.id in state.inFlightHotelIds || hotel.id in state.optimisticOverrides) {
                        continue
                    }

                    newKnownStates = newKnownStates + (hotel.id to hotel.isFavorite)

                    if (hotel.isFavorite) {
                        newFavoriteIds = newFavoriteIds + hotel.id
                        newFavoriteHotels = newFavoriteHotels.upsertHotel(hotel.copy(isFavorite = true))
                    } else {
                        newFavoriteIds = newFavoriteIds - hotel.id
                        newFavoriteHotels = newFavoriteHotels.removeHotel(hotel.id)
                    }
                }

                state.copy(
                    favoriteIds = newFavoriteIds,
                    favoriteHotels = newFavoriteHotels,
                    knownFavoriteStates = newKnownStates,
                    stateRevision = state.stateRevision + 1L,
                )
            }
        }
    }

    override suspend fun getAvailability(
        hotelId: String,
        roomId: String,
        checkIn: String,
        checkOut: String,
        rooms: Int,
    ): Availability {
        return apiService.getAvailability(
            hotelId = hotelId,
            roomId = roomId,
            checkIn = checkIn,
            checkOut = checkOut,
            rooms = rooms,
        ).data.toDomain()
    }

    override suspend fun getFavorites(): List<Hotel> {
        return syncFavorites()
    }

    override suspend fun syncFavorites(): List<Hotel> {
        val (startSessionGen, startHotelRevisions, startUid) = mutex.withLock {
            Triple(_favoriteState.value.sessionGeneration, _favoriteState.value.hotelRevisions, _favoriteState.value.activeSessionUid)
        }

        if (startUid == null) {
            return emptyList()
        }

        val backendFavorites = try {
            apiService.getFavorites().data.map { it.toDomain() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return emptyList()
        }

        mutex.withLock {
            val current = _favoriteState.value
            if (current.sessionGeneration != startSessionGen || current.activeSessionUid == null || current.activeSessionUid != startUid) {
                return emptyList()
            }

            val allHotelIds = (backendFavorites.map { it.id } + current.favoriteHotels.map { it.id } + current.knownFavoriteStates.keys).toSet()
            val reconciledList = mutableListOf<Hotel>()
            val seenIds = mutableSetOf<String>()

            // 1. Locally added/mutated favorites during sync (preserve current local recency order at top)
            for (hotel in current.favoriteHotels) {
                val currentRev = current.hotelRevisions[hotel.id] ?: 0L
                val startRev = startHotelRevisions[hotel.id] ?: 0L
                val isMutatedOrInFlight = currentRev > startRev || hotel.id in current.inFlightHotelIds || hotel.id in current.optimisticOverrides
                if (isMutatedOrInFlight && hotel.id in current.favoriteIds) {
                    if (seenIds.add(hotel.id)) {
                        reconciledList.add(hotel.copy(isFavorite = true))
                    }
                }
            }

            // 2. Authoritative backend favorites (already ordered newest-first by backend ORDER BY created_at DESC)
            for (backendHotel in backendFavorites) {
                val currentRev = current.hotelRevisions[backendHotel.id] ?: 0L
                val startRev = startHotelRevisions[backendHotel.id] ?: 0L
                val isMutatedOrInFlight = currentRev > startRev || backendHotel.id in current.inFlightHotelIds || backendHotel.id in current.optimisticOverrides

                if (!isMutatedOrInFlight) {
                    if (seenIds.add(backendHotel.id)) {
                        reconciledList.add(backendHotel.copy(isFavorite = true))
                    }
                }
            }

            val reconciledIds = reconciledList.map { it.id }.toSet()
            val reconciledKnown = current.knownFavoriteStates + allHotelIds.associateWith { it in reconciledIds }

            _favoriteState.update { state ->
                if (state.sessionGeneration == startSessionGen && state.activeSessionUid == startUid) {
                    state.copy(
                        favoriteHotels = reconciledList,
                        favoriteIds = reconciledIds,
                        knownFavoriteStates = reconciledKnown,
                        isHydrated = true,
                        stateRevision = state.stateRevision + 1L,
                    )
                } else {
                    state
                }
            }
            return reconciledList
        }
    }

    override suspend fun toggleFavoriteOptimistic(hotel: Hotel): FavoriteMutationResult {
        val context = mutex.withLock {
            if (hotel.id in _favoriteState.value.inFlightHotelIds) {
                return FavoriteMutationResult.AlreadyInProgress
            }

            val current = _favoriteState.value
            if (current.activeSessionUid == null) {
                return FavoriteMutationResult.StaleSession
            }
            val isCurrentlyFavorited = if (current.isHydrated) {
                current.favoriteIds.contains(hotel.id)
            } else {
                current.optimisticOverrides[hotel.id] ?: current.knownFavoriteStates[hotel.id] ?: hotel.isFavorite
            }
            val target = !isCurrentlyFavorited
            val sessionGen = current.sessionGeneration
            val uid = current.activeSessionUid
            val mutationId = UUID.randomUUID().toString()
            val previousIndex = current.favoriteHotels.indexOfFirst { it.id == hotel.id }
            val previousKnown = current.knownFavoriteStates[hotel.id]

            _favoriteState.update { state ->
                state.copy(
                    inFlightHotelIds = state.inFlightHotelIds + hotel.id,
                    optimisticOverrides = state.optimisticOverrides + (hotel.id to target),
                    latestMutationId = state.latestMutationId + (hotel.id to mutationId),
                    favoriteIds = if (target) state.favoriteIds + hotel.id else state.favoriteIds - hotel.id,
                    favoriteHotels = if (target) {
                        state.favoriteHotels.upsertHotel(hotel.copy(isFavorite = true), targetIndex = 0)
                    } else {
                        state.favoriteHotels.removeHotel(hotel.id)
                    },
                    hotelRevisions = state.hotelRevisions + (hotel.id to (state.hotelRevisions[hotel.id] ?: 0L) + 1L),
                    stateRevision = state.stateRevision + 1L,
                )
            }

            MutationContext(sessionGen, uid, mutationId, target, previousIndex, previousKnown)
        }

        // Create Deferred with CoroutineStart.LAZY and register it before starting
        val deferredJob = repositoryScope.async(start = CoroutineStart.LAZY) {
            try {
                if (context.targetFavorite) {
                    apiService.addFavorite(hotel.id)
                } else {
                    apiService.removeFavorite(hotel.id)
                }

                mutex.withLock {
                    val current = _favoriteState.value
                    if (current.sessionGeneration == context.sessionGen &&
                        current.activeSessionUid == context.uid &&
                        current.latestMutationId[hotel.id] == context.mutationId
                    ) {
                        _favoriteState.update { state ->
                            state.copy(
                                inFlightHotelIds = state.inFlightHotelIds - hotel.id,
                                optimisticOverrides = state.optimisticOverrides - hotel.id,
                                latestMutationId = state.latestMutationId - hotel.id,
                                knownFavoriteStates = state.knownFavoriteStates + (hotel.id to context.targetFavorite),
                                hotelRevisions = state.hotelRevisions + (hotel.id to (state.hotelRevisions[hotel.id] ?: 0L) + 1L),
                                stateRevision = state.stateRevision + 1L,
                            )
                        }
                        FavoriteMutationResult.Success(isFavorited = context.targetFavorite)
                    } else {
                        FavoriteMutationResult.StaleSession
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutex.withLock {
                    val current = _favoriteState.value
                    if (current.sessionGeneration == context.sessionGen &&
                        current.activeSessionUid == context.uid &&
                        current.latestMutationId[hotel.id] == context.mutationId
                    ) {
                        _favoriteState.update { state ->
                            val rolledBackFavoriteIds = if (context.targetFavorite) state.favoriteIds - hotel.id else state.favoriteIds + hotel.id
                            val rolledBackFavoriteHotels = if (context.targetFavorite) {
                                state.favoriteHotels.removeHotel(hotel.id)
                            } else {
                                state.favoriteHotels.upsertHotel(hotel.copy(isFavorite = true), targetIndex = context.previousIndex)
                            }
                            val rolledBackKnown = if (context.previousKnown != null) {
                                state.knownFavoriteStates + (hotel.id to context.previousKnown)
                            } else {
                                state.knownFavoriteStates - hotel.id
                            }
                            state.copy(
                                favoriteIds = rolledBackFavoriteIds,
                                favoriteHotels = rolledBackFavoriteHotels,
                                inFlightHotelIds = state.inFlightHotelIds - hotel.id,
                                optimisticOverrides = state.optimisticOverrides - hotel.id,
                                latestMutationId = state.latestMutationId - hotel.id,
                                knownFavoriteStates = rolledBackKnown,
                                hotelRevisions = state.hotelRevisions + (hotel.id to (state.hotelRevisions[hotel.id] ?: 0L) + 1L),
                                stateRevision = state.stateRevision + 1L,
                            )
                        }
                        FavoriteMutationResult.Failed(e)
                    } else {
                        FavoriteMutationResult.StaleSession
                    }
                }
            } finally {
                activeMutationJobs.remove(context.mutationId)
            }
        }

        activeMutationJobs[context.mutationId] = deferredJob
        return try {
            deferredJob.await()
        } catch (e: CancellationException) {
            FavoriteMutationResult.StaleSession
        }
    }

    override suspend fun restoreFavoriteOptimistic(hotel: Hotel, originalIndex: Int): FavoriteMutationResult {
        val context = mutex.withLock {
            if (hotel.id in _favoriteState.value.inFlightHotelIds) {
                return FavoriteMutationResult.AlreadyInProgress
            }

            val current = _favoriteState.value
            if (current.activeSessionUid == null) {
                return FavoriteMutationResult.StaleSession
            }
            val target = true
            val sessionGen = current.sessionGeneration
            val uid = current.activeSessionUid
            val mutationId = UUID.randomUUID().toString()
            val previousKnown = current.knownFavoriteStates[hotel.id]

            _favoriteState.update { state ->
                state.copy(
                    inFlightHotelIds = state.inFlightHotelIds + hotel.id,
                    optimisticOverrides = state.optimisticOverrides + (hotel.id to target),
                    latestMutationId = state.latestMutationId + (hotel.id to mutationId),
                    favoriteIds = state.favoriteIds + hotel.id,
                    favoriteHotels = state.favoriteHotels.upsertHotel(hotel.copy(isFavorite = true), targetIndex = originalIndex),
                    hotelRevisions = state.hotelRevisions + (hotel.id to (state.hotelRevisions[hotel.id] ?: 0L) + 1L),
                    stateRevision = state.stateRevision + 1L,
                )
            }

            MutationContext(sessionGen, uid, mutationId, target, originalIndex, previousKnown)
        }

        val deferredJob = repositoryScope.async(start = CoroutineStart.LAZY) {
            try {
                apiService.addFavorite(hotel.id)

                mutex.withLock {
                    val current = _favoriteState.value
                    if (current.sessionGeneration == context.sessionGen &&
                        current.activeSessionUid == context.uid &&
                        current.latestMutationId[hotel.id] == context.mutationId
                    ) {
                        _favoriteState.update { state ->
                            state.copy(
                                inFlightHotelIds = state.inFlightHotelIds - hotel.id,
                                optimisticOverrides = state.optimisticOverrides - hotel.id,
                                latestMutationId = state.latestMutationId - hotel.id,
                                knownFavoriteStates = state.knownFavoriteStates + (hotel.id to true),
                                hotelRevisions = state.hotelRevisions + (hotel.id to (state.hotelRevisions[hotel.id] ?: 0L) + 1L),
                                stateRevision = state.stateRevision + 1L,
                            )
                        }
                        FavoriteMutationResult.Success(isFavorited = true)
                    } else {
                        FavoriteMutationResult.StaleSession
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutex.withLock {
                    val current = _favoriteState.value
                    if (current.sessionGeneration == context.sessionGen &&
                        current.activeSessionUid == context.uid &&
                        current.latestMutationId[hotel.id] == context.mutationId
                    ) {
                        _favoriteState.update { state ->
                            val rolledBackFavoriteIds = state.favoriteIds - hotel.id
                            val rolledBackFavoriteHotels = state.favoriteHotels.removeHotel(hotel.id)
                            val rolledBackKnown = if (context.previousKnown != null) {
                                state.knownFavoriteStates + (hotel.id to context.previousKnown)
                            } else {
                                state.knownFavoriteStates - hotel.id
                            }
                            state.copy(
                                favoriteIds = rolledBackFavoriteIds,
                                favoriteHotels = rolledBackFavoriteHotels,
                                inFlightHotelIds = state.inFlightHotelIds - hotel.id,
                                optimisticOverrides = state.optimisticOverrides - hotel.id,
                                latestMutationId = state.latestMutationId - hotel.id,
                                knownFavoriteStates = rolledBackKnown,
                                hotelRevisions = state.hotelRevisions + (hotel.id to (state.hotelRevisions[hotel.id] ?: 0L) + 1L),
                                stateRevision = state.stateRevision + 1L,
                            )
                        }
                        FavoriteMutationResult.Failed(e)
                    } else {
                        FavoriteMutationResult.StaleSession
                    }
                }
            } finally {
                activeMutationJobs.remove(context.mutationId)
            }
        }

        activeMutationJobs[context.mutationId] = deferredJob
        return try {
            deferredJob.await()
        } catch (e: CancellationException) {
            FavoriteMutationResult.StaleSession
        }
    }

    override suspend fun getHotelReviews(
        hotelId: String,
        page: Int,
        limit: Int,
    ): PublicReviewPage {
        return apiService.getHotelReviews(hotelId = hotelId, page = page, limit = limit).data.toDomain()
    }

    override suspend fun getMyReview(hotelId: String): MyReviewState {
        return apiService.getMyReview(hotelId = hotelId).data.toDomain()
    }

    override suspend fun submitReview(
        hotelId: String,
        bookingId: String,
        reviewId: String?,
        rating: Int,
        title: String,
        comment: String,
    ): PrivateReview {
        return apiService.submitReview(
            request = ReviewRequestDto(
                hotelId = hotelId,
                bookingId = bookingId,
                reviewId = reviewId,
                rating = rating,
                title = title,
                comment = comment,
            ),
        ).data.toDomain()
    }
}
