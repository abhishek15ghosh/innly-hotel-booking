package com.innly.hotelbooking.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innly.hotelbooking.core.network.parseErrorMessage
import com.innly.hotelbooking.domain.model.Hotel
import com.innly.hotelbooking.domain.model.HotelSearchFilter
import com.innly.hotelbooking.domain.repository.FavoriteState
import com.innly.hotelbooking.domain.repository.HotelRepository
import com.innly.hotelbooking.domain.usecase.GetHotelsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val rawHotels: List<Hotel> = emptyList(),
    val hotels: List<Hotel> = emptyList(),
    val inFlightFavoriteHotelIds: Set<String> = emptySet(),
    val filter: HotelSearchFilter = HotelSearchFilter(),
    val errorMessage: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHotelsUseCase: GetHotelsUseCase,
    private val hotelRepository: HotelRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            hotelRepository.favoriteState.collect { favoriteState ->
                _uiState.update { current ->
                    current.copy(
                        hotels = mapHotelsWithFavoriteState(current.rawHotels, favoriteState),
                        inFlightFavoriteHotelIds = favoriteState.inFlightHotelIds,
                    )
                }
            }
        }
        loadHotels()
    }

    fun loadHotels(filter: HotelSearchFilter = _uiState.value.filter) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, filter = filter, errorMessage = null) }
        viewModelScope.launch {
            runCatching { getHotelsUseCase(filter) }
                .onSuccess { hotels ->
                    val favoriteState = hotelRepository.favoriteState.value
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            rawHotels = hotels,
                            hotels = mapHotelsWithFavoriteState(hotels, favoriteState),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.parseErrorMessage("Unable to load hotels. Please try again."),
                        )
                    }
                }
        }
    }

    fun toggleFavorite(hotel: Hotel) {
        viewModelScope.launch {
            runCatching {
                hotelRepository.toggleFavoriteOptimistic(hotel)
            }
        }
    }

    private fun mapHotelsWithFavoriteState(
        hotels: List<Hotel>,
        favoriteState: FavoriteState,
    ): List<Hotel> {
        if (favoriteState.activeSessionUid == null) {
            return hotels.map { it.copy(isFavorite = false) }
        }
        return hotels.map { hotel ->
            val isFavorited = if (favoriteState.isHydrated) {
                favoriteState.favoriteIds.contains(hotel.id)
            } else {
                favoriteState.optimisticOverrides[hotel.id]
                    ?: favoriteState.knownFavoriteStates[hotel.id]
                    ?: hotel.isFavorite
            }
            hotel.copy(isFavorite = isFavorited)
        }
    }
}
