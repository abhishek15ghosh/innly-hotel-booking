package com.innly.hotelbooking.presentation.hoteldetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innly.hotelbooking.core.network.parseErrorMessage
import com.innly.hotelbooking.domain.model.Hotel
import com.innly.hotelbooking.domain.model.MyReviewState
import com.innly.hotelbooking.domain.model.PublicReview
import com.innly.hotelbooking.domain.model.RatingSummary
import com.innly.hotelbooking.domain.repository.FavoriteState
import com.innly.hotelbooking.domain.repository.HotelRepository
import com.innly.hotelbooking.domain.usecase.GetHotelDetailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HotelDetailUiState(
    val isLoading: Boolean = false,
    val rawHotel: Hotel? = null,
    val hotel: Hotel? = null,
    val inFlightFavoriteHotelIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val reviews: List<PublicReview> = emptyList(),
    val ratingSummary: RatingSummary? = null,
    val reviewsPage: Int = 1,
    val isReviewsLoading: Boolean = false,
    val isPaginatingReviews: Boolean = false,
    val canLoadMoreReviews: Boolean = false,
    val reviewsErrorMessage: String? = null,
    val paginationErrorMessage: String? = null,
    val myReviewState: MyReviewState? = null,
    val isMyReviewLoading: Boolean = false,
)

@HiltViewModel
class HotelDetailViewModel @Inject constructor(
    private val getHotelDetailUseCase: GetHotelDetailUseCase,
    private val hotelRepository: HotelRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HotelDetailUiState())
    val uiState: StateFlow<HotelDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            hotelRepository.favoriteState.collect { favoriteState ->
                _uiState.update { current ->
                    current.copy(
                        hotel = current.rawHotel?.let { mapHotelWithFavoriteState(it, favoriteState) },
                        inFlightFavoriteHotelIds = favoriteState.inFlightHotelIds,
                    )
                }
            }
        }
    }

    fun loadHotel(
        hotelId: String,
        fallbackErrorMessage: String = "Unable to load hotel details. Please try again.",
    ) {
        if (_uiState.value.isLoading) return
        _uiState.update { current ->
            val isDifferentHotel = current.rawHotel?.id != hotelId
            current.copy(
                isLoading = true,
                errorMessage = null,
                reviews = if (isDifferentHotel) emptyList() else current.reviews,
                ratingSummary = if (isDifferentHotel) null else current.ratingSummary,
                reviewsPage = if (isDifferentHotel) 1 else current.reviewsPage,
                reviewsErrorMessage = if (isDifferentHotel) null else current.reviewsErrorMessage,
                paginationErrorMessage = if (isDifferentHotel) null else current.paginationErrorMessage,
                myReviewState = if (isDifferentHotel) null else current.myReviewState,
            )
        }
        viewModelScope.launch {
            runCatching { getHotelDetailUseCase(hotelId) }
                .onSuccess { hotel ->
                    val favoriteState = hotelRepository.favoriteState.value
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            errorMessage = null,
                            rawHotel = hotel,
                            hotel = mapHotelWithFavoriteState(hotel, favoriteState),
                            inFlightFavoriteHotelIds = favoriteState.inFlightHotelIds,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            errorMessage = error.parseErrorMessage(fallbackErrorMessage),
                        )
                    }
                }
        }
        loadReviews(hotelId, page = 1)
        loadMyReview(hotelId)
    }

    fun loadReviews(hotelId: String, page: Int = 1) {
        viewModelScope.launch {
            _uiState.update {
                if (page == 1) it.copy(isReviewsLoading = true, reviewsErrorMessage = null)
                else it.copy(isPaginatingReviews = true, paginationErrorMessage = null)
            }
            runCatching {
                hotelRepository.getHotelReviews(hotelId = hotelId, page = page, limit = 10)
            }.onSuccess { reviewPage ->
                _uiState.update { current ->
                    val updatedReviews = if (page == 1) {
                        reviewPage.items
                    } else {
                        // Deduplicate by ID
                        val existingIds = current.reviews.map { r -> r.id }.toSet()
                        current.reviews + reviewPage.items.filter { r -> !existingIds.contains(r.id) }
                    }
                    val totalLoaded = updatedReviews.size
                    val canLoadMore = totalLoaded < reviewPage.total && reviewPage.items.isNotEmpty()

                    current.copy(
                        reviews = updatedReviews,
                        ratingSummary = reviewPage.ratingSummary,
                        reviewsPage = page,
                        isReviewsLoading = false,
                        isPaginatingReviews = false,
                        canLoadMoreReviews = canLoadMore,
                        reviewsErrorMessage = null,
                        paginationErrorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    if (page == 1) {
                        current.copy(
                            isReviewsLoading = false,
                            reviewsErrorMessage = error.parseErrorMessage("Unable to load reviews. Please try again."),
                        )
                    } else {
                        current.copy(
                            isPaginatingReviews = false,
                            paginationErrorMessage = error.parseErrorMessage("Unable to load more reviews. Please try again."),
                        )
                    }
                }
            }
        }
    }

    fun loadNextReviewPage() {
        val current = _uiState.value
        val hotelId = current.rawHotel?.id ?: return
        if (current.isReviewsLoading || current.isPaginatingReviews || !current.canLoadMoreReviews) return
        loadReviews(hotelId, page = current.reviewsPage + 1)
    }

    fun retryReviewPagination() {
        val current = _uiState.value
        val hotelId = current.rawHotel?.id ?: return
        loadReviews(hotelId, page = current.reviewsPage + 1)
    }

    fun refreshReviewsAndAuthorState(hotelId: String) {
        loadMyReview(hotelId)
        loadReviews(hotelId, page = 1)
    }

    fun loadMyReview(hotelId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMyReviewLoading = true) }
            runCatching {
                hotelRepository.getMyReview(hotelId)
            }.onSuccess { myReviewState ->
                _uiState.update {
                    it.copy(
                        isMyReviewLoading = false,
                        myReviewState = myReviewState,
                    )
                }
            }.onFailure {
                // If unauthenticated or network error, silently set to null or not_eligible
                _uiState.update { it.copy(isMyReviewLoading = false, myReviewState = null) }
            }
        }
    }

    fun toggleFavorite() {
        val hotel = _uiState.value.rawHotel ?: return
        viewModelScope.launch {
            runCatching {
                hotelRepository.toggleFavoriteOptimistic(hotel)
            }
        }
    }

    private fun mapHotelWithFavoriteState(
        hotel: Hotel,
        favoriteState: FavoriteState,
    ): Hotel {
        if (favoriteState.activeSessionUid == null) {
            return hotel.copy(isFavorite = false)
        }
        val isFavorited = if (favoriteState.isHydrated) {
            favoriteState.favoriteIds.contains(hotel.id)
        } else {
            favoriteState.optimisticOverrides[hotel.id]
                ?: favoriteState.knownFavoriteStates[hotel.id]
                ?: hotel.isFavorite
        }
        return hotel.copy(isFavorite = isFavorited)
    }
}
