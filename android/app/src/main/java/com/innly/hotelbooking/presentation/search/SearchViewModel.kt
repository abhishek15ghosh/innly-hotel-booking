package com.innly.hotelbooking.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innly.hotelbooking.core.network.parseErrorMessage
import com.innly.hotelbooking.domain.model.Hotel
import com.innly.hotelbooking.domain.model.HotelSearchFilter
import com.innly.hotelbooking.domain.repository.FavoriteState
import com.innly.hotelbooking.domain.repository.HotelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val isLoading: Boolean = false,
    val city: String = "",
    val minPrice: String = "",
    val maxPrice: String = "",
    val minRating: Double? = null,
    val lastSuccessfulFilter: HotelSearchFilter? = null,
    val rawResults: List<Hotel> = emptyList(),
    val results: List<Hotel> = emptyList(),
    val inFlightFavoriteHotelIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val validationError: String? = null,
    val hasSearched: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val hotelRepository: HotelRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            hotelRepository.favoriteState.collect { favoriteState ->
                _uiState.update { current ->
                    current.copy(
                        results = mapHotelsWithFavoriteState(current.rawResults, favoriteState),
                        inFlightFavoriteHotelIds = favoriteState.inFlightHotelIds,
                    )
                }
            }
        }
        search()
    }

    fun updateCity(value: String) {
        _uiState.update { it.copy(city = value, errorMessage = null) }
    }

    fun updateMinPrice(value: String) {
        val filtered = value.filter { it.isDigit() }
        _uiState.update {
            val validation = validatePriceRange(filtered, it.maxPrice)
            it.copy(minPrice = filtered, validationError = validation, errorMessage = null)
        }
    }

    fun updateMaxPrice(value: String) {
        val filtered = value.filter { it.isDigit() }
        _uiState.update {
            val validation = validatePriceRange(it.minPrice, filtered)
            it.copy(maxPrice = filtered, validationError = validation, errorMessage = null)
        }
    }

    fun updateMinRating(value: Double?) {
        _uiState.update { it.copy(minRating = value, errorMessage = null) }
    }

    fun clearFilters() {
        val favoriteState = hotelRepository.favoriteState.value
        _uiState.update {
            SearchUiState(
                isLoading = false,
                city = "",
                minPrice = "",
                maxPrice = "",
                minRating = null,
                lastSuccessfulFilter = null,
                rawResults = emptyList(),
                results = emptyList(),
                inFlightFavoriteHotelIds = favoriteState.inFlightHotelIds,
                errorMessage = null,
                validationError = null,
                hasSearched = false,
            )
        }
        search()
    }

    fun search() {
        if (_uiState.value.isLoading) return
        val currentState = _uiState.value
        val validation = validatePriceRange(currentState.minPrice, currentState.maxPrice)
        if (validation != null) {
            _uiState.update { it.copy(validationError = validation) }
            return
        }

        val targetFilter = HotelSearchFilter(
            city = currentState.city.trim(),
            minPrice = currentState.minPrice.toIntOrNull(),
            maxPrice = currentState.maxPrice.toIntOrNull(),
            minRating = currentState.minRating,
        )
        val isSameQuery = currentState.lastSuccessfulFilter != null && currentState.lastSuccessfulFilter == targetFilter

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                validationError = null,
                hasSearched = true,
                rawResults = if (isSameQuery) it.rawResults else emptyList(),
                results = if (isSameQuery) it.results else emptyList(),
            )
        }

        viewModelScope.launch {
            runCatching {
                hotelRepository.getHotels(targetFilter)
            }.onSuccess { hotels ->
                val favoriteState = hotelRepository.favoriteState.value
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        lastSuccessfulFilter = targetFilter,
                        rawResults = hotels,
                        results = mapHotelsWithFavoriteState(hotels, favoriteState),
                        inFlightFavoriteHotelIds = favoriteState.inFlightHotelIds,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.parseErrorMessage("Unable to complete hotel search. Please try again."),
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

    private fun validatePriceRange(minPriceStr: String, maxPriceStr: String): String? {
        val min = minPriceStr.toIntOrNull()
        val max = maxPriceStr.toIntOrNull()

        if (minPriceStr.isNotBlank() && min == null) {
            return "Please enter a valid minimum price."
        }
        if (maxPriceStr.isNotBlank() && max == null) {
            return "Please enter a valid maximum price."
        }
        if (min != null && min < 0) {
            return "Minimum price cannot be negative."
        }
        if (max != null && max < 0) {
            return "Maximum price cannot be negative."
        }
        if (min != null && max != null && min > max) {
            return "Minimum price cannot be greater than maximum price."
        }
        return null
    }
}
