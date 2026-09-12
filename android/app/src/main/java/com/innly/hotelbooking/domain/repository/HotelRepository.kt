package com.innly.hotelbooking.domain.repository

import com.innly.hotelbooking.domain.model.Availability
import com.innly.hotelbooking.domain.model.Hotel
import com.innly.hotelbooking.domain.model.HotelSearchFilter
import com.innly.hotelbooking.domain.model.MyReviewState
import com.innly.hotelbooking.domain.model.PrivateReview
import com.innly.hotelbooking.domain.model.PublicReviewPage
import kotlinx.coroutines.flow.StateFlow

data class FavoriteState(
    val favoriteIds: Set<String> = emptySet(),
    val favoriteHotels: List<Hotel> = emptyList(),
    val inFlightHotelIds: Set<String> = emptySet(),
    val optimisticOverrides: Map<String, Boolean> = emptyMap(),
    val knownFavoriteStates: Map<String, Boolean> = emptyMap(),
    val latestMutationId: Map<String, String> = emptyMap(),
    val isHydrated: Boolean = false,
    val activeSessionUid: String? = null,
    val sessionGeneration: Long = 0L,
    val stateRevision: Long = 0L,
    val hotelRevisions: Map<String, Long> = emptyMap(),
)

sealed interface FavoriteMutationResult {
    data class Success(val isFavorited: Boolean) : FavoriteMutationResult
    data class Failed(val error: Throwable) : FavoriteMutationResult
    data object AlreadyInProgress : FavoriteMutationResult
    data object StaleSession : FavoriteMutationResult
}

interface HotelRepository {
    val favoriteState: StateFlow<FavoriteState>
    val favoriteIds: StateFlow<Set<String>>
    val favoriteHotels: StateFlow<List<Hotel>>
    val inFlightFavoriteHotelIds: StateFlow<Set<String>>
    val isFavoritesHydrated: StateFlow<Boolean>

    suspend fun getHotels(filter: HotelSearchFilter): List<Hotel>
    suspend fun getHotelDetail(hotelId: String): Hotel
    suspend fun getAvailability(
        hotelId: String,
        roomId: String,
        checkIn: String,
        checkOut: String,
        rooms: Int,
    ): Availability
    suspend fun getFavorites(): List<Hotel>
    suspend fun syncFavorites(): List<Hotel>
    suspend fun toggleFavoriteOptimistic(hotel: Hotel): FavoriteMutationResult
    suspend fun restoreFavoriteOptimistic(hotel: Hotel, originalIndex: Int): FavoriteMutationResult
    suspend fun getHotelReviews(hotelId: String, page: Int = 1, limit: Int = 10): PublicReviewPage
    suspend fun getMyReview(hotelId: String): MyReviewState
    suspend fun submitReview(
        hotelId: String,
        bookingId: String,
        reviewId: String? = null,
        rating: Int,
        title: String,
        comment: String,
    ): PrivateReview
}
