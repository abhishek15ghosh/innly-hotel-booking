package com.innly.hotelbooking.presentation.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innly.hotelbooking.core.network.parseErrorMessage
import com.innly.hotelbooking.domain.model.Hotel
import com.innly.hotelbooking.domain.repository.AuthRepository
import com.innly.hotelbooking.domain.repository.FavoriteMutationResult
import com.innly.hotelbooking.domain.repository.HotelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FavoritesRemovalPhase {
    IDLE,
    REMOVING,
    UNDO_AVAILABLE,
    RESTORING,
}

data class FavoritesUndoEvent(
    val eventId: String,
    val hotel: Hotel,
    val originalIndex: Int,
    val message: String,
)

data class FavoritesUiState(
    val isAuthResolved: Boolean = false,
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val removalPhase: FavoritesRemovalPhase = FavoritesRemovalPhase.IDLE,
    val activeRemovalEventId: String? = null,
    val hotels: List<Hotel> = emptyList(),
    val undoEvent: FavoritesUndoEvent? = null,
    val errorMessage: String? = null,
) {
    val isMutationInProgress: Boolean
        get() = removalPhase != FavoritesRemovalPhase.IDLE
}

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val hotelRepository: HotelRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    private var lastLoadedUid: String? = null
    private var loadGeneration: Long = 0L
    private var loadJob: Job? = null
    private var mutationJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                val currentUid = user?.firebaseUid?.takeIf { it.isNotBlank() }
                val isAuthenticated = currentUid != null

                if (isAuthenticated) {
                    _uiState.update {
                        it.copy(
                            isAuthResolved = true,
                            isAuthenticated = true,
                            hotels = if (it.isMutationInProgress) it.hotels else hotelRepository.favoriteHotels.value,
                        )
                    }
                    if (currentUid != lastLoadedUid) {
                        val isAccountSwitch = lastLoadedUid != null && currentUid != lastLoadedUid
                        lastLoadedUid = currentUid
                        if (isAccountSwitch || !hotelRepository.isFavoritesHydrated.value) {
                            loadFavorites()
                        }
                    }
                } else {
                    lastLoadedUid = null
                    loadGeneration++
                    loadJob?.cancel()
                    mutationJob?.cancel()
                    _uiState.update {
                        it.copy(
                            isAuthResolved = true,
                            isAuthenticated = false,
                            isLoading = false,
                            isRefreshing = false,
                            removalPhase = FavoritesRemovalPhase.IDLE,
                            activeRemovalEventId = null,
                            hotels = emptyList(),
                            undoEvent = null,
                            errorMessage = null,
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            hotelRepository.favoriteHotels.collect { sharedHotels ->
                _uiState.update { state ->
                    if (!state.isAuthenticated) {
                        state.copy(hotels = emptyList())
                    } else if (state.isMutationInProgress) {
                        state
                    } else {
                        state.copy(hotels = sharedHotels)
                    }
                }
            }
        }
    }

    fun loadFavorites(isRefresh: Boolean = false) {
        val currentUid = lastLoadedUid
        if (!_uiState.value.isAuthenticated || currentUid == null) return
        if (_uiState.value.isMutationInProgress) return

        loadJob?.cancel()
        val currentGeneration = ++loadGeneration

        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh && it.hotels.isEmpty(),
                    isRefreshing = isRefresh && it.hotels.isNotEmpty(),
                    errorMessage = null,
                )
            }
            try {
                val favorites = hotelRepository.syncFavorites()
                if (currentGeneration != loadGeneration || currentUid != lastLoadedUid || !_uiState.value.isAuthenticated) {
                    return@launch
                }
                _uiState.update { state ->
                    if (state.isMutationInProgress) {
                        state.copy(
                            isLoading = false,
                            isRefreshing = false,
                        )
                    } else {
                        state.copy(
                            isLoading = false,
                            isRefreshing = false,
                            hotels = favorites.map { hotel -> hotel.copy(isFavorite = true) },
                            errorMessage = null,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (currentGeneration != loadGeneration || currentUid != lastLoadedUid || !_uiState.value.isAuthenticated) {
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = e.parseErrorMessage("Unable to load favorites"),
                    )
                }
            }
        }
    }

    fun removeFavorite(hotel: Hotel) {
        if (_uiState.value.isMutationInProgress || _uiState.value.isLoading || _uiState.value.isRefreshing || !_uiState.value.isAuthenticated) {
            return
        }

        val originalIndex = _uiState.value.hotels.indexOfFirst { it.id == hotel.id }
        if (originalIndex == -1) return

        loadJob?.cancel()
        val eventId = UUID.randomUUID().toString()

        _uiState.update { state ->
            state.copy(
                removalPhase = FavoritesRemovalPhase.REMOVING,
                activeRemovalEventId = eventId,
                hotels = state.hotels.filterNot { it.id == hotel.id },
                errorMessage = null,
            )
        }

        mutationJob = viewModelScope.launch {
            try {
                val result = hotelRepository.toggleFavoriteOptimistic(hotel)
                when (result) {
                    is FavoriteMutationResult.Success -> {
                        _uiState.update { state ->
                            if (state.activeRemovalEventId == eventId && state.removalPhase == FavoritesRemovalPhase.REMOVING) {
                                state.copy(
                                    removalPhase = FavoritesRemovalPhase.UNDO_AVAILABLE,
                                    undoEvent = FavoritesUndoEvent(
                                        eventId = eventId,
                                        hotel = hotel,
                                        originalIndex = originalIndex,
                                        message = "Removed ${hotel.name} from Favorites",
                                    ),
                                )
                            } else {
                                state
                            }
                        }
                    }
                    is FavoriteMutationResult.Failed -> {
                        _uiState.update { state ->
                            if (state.activeRemovalEventId == eventId) {
                                state.copy(
                                    hotels = hotelRepository.favoriteHotels.value,
                                    removalPhase = FavoritesRemovalPhase.IDLE,
                                    activeRemovalEventId = null,
                                    undoEvent = null,
                                    errorMessage = result.error.parseErrorMessage("Unable to remove favorite. Please try again."),
                                )
                            } else {
                                state
                            }
                        }
                    }
                    FavoriteMutationResult.AlreadyInProgress, FavoriteMutationResult.StaleSession -> {
                        _uiState.update { state ->
                            if (state.activeRemovalEventId == eventId) {
                                state.copy(
                                    hotels = hotelRepository.favoriteHotels.value,
                                    removalPhase = FavoritesRemovalPhase.IDLE,
                                    activeRemovalEventId = null,
                                    undoEvent = null,
                                )
                            } else {
                                state
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { state ->
                    if (state.activeRemovalEventId == eventId) {
                        state.copy(
                            hotels = hotelRepository.favoriteHotels.value,
                            removalPhase = FavoritesRemovalPhase.IDLE,
                            activeRemovalEventId = null,
                            undoEvent = null,
                            errorMessage = e.parseErrorMessage("Unable to remove favorite. Please try again."),
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun consumeUndoEvent(eventId: String) {
        _uiState.update { state ->
            if (state.activeRemovalEventId == eventId && state.undoEvent?.eventId == eventId) {
                state.copy(undoEvent = null)
            } else {
                state
            }
        }
    }

    fun onSnackbarDismissed(eventId: String) {
        _uiState.update { state ->
            if (state.activeRemovalEventId == eventId && state.removalPhase == FavoritesRemovalPhase.UNDO_AVAILABLE) {
                state.copy(
                    removalPhase = FavoritesRemovalPhase.IDLE,
                    activeRemovalEventId = null,
                    undoEvent = null,
                )
            } else {
                state
            }
        }
    }

    fun undoRemoveFavorite(eventId: String, hotel: Hotel, originalIndex: Int) {
        if (_uiState.value.activeRemovalEventId != eventId || _uiState.value.removalPhase != FavoritesRemovalPhase.UNDO_AVAILABLE) {
            return
        }

        _uiState.update { state ->
            state.copy(
                removalPhase = FavoritesRemovalPhase.RESTORING,
                undoEvent = null,
                errorMessage = null,
            )
        }

        mutationJob = viewModelScope.launch {
            try {
                val result = hotelRepository.restoreFavoriteOptimistic(hotel, originalIndex)
                _uiState.update { state ->
                    if (state.activeRemovalEventId == eventId) {
                        state.copy(
                            hotels = hotelRepository.favoriteHotels.value,
                            removalPhase = FavoritesRemovalPhase.IDLE,
                            activeRemovalEventId = null,
                            errorMessage = if (result is FavoriteMutationResult.Failed) {
                                result.error.parseErrorMessage("Unable to restore favorite. Please try again.")
                            } else null,
                        )
                    } else {
                        state
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { state ->
                    if (state.activeRemovalEventId == eventId) {
                        state.copy(
                            hotels = hotelRepository.favoriteHotels.value,
                            removalPhase = FavoritesRemovalPhase.IDLE,
                            activeRemovalEventId = null,
                            errorMessage = e.parseErrorMessage("Unable to restore favorite. Please try again."),
                        )
                    } else {
                        state
                    }
                }
            } finally {
                _uiState.update { state ->
                    if (state.activeRemovalEventId == eventId && state.removalPhase == FavoritesRemovalPhase.RESTORING) {
                        state.copy(
                            removalPhase = FavoritesRemovalPhase.IDLE,
                            activeRemovalEventId = null,
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun onScreenDisposed(eventId: String?) {
        if (eventId == null) return
        _uiState.update { state ->
            if (state.activeRemovalEventId == eventId && state.removalPhase == FavoritesRemovalPhase.UNDO_AVAILABLE) {
                state.copy(
                    removalPhase = FavoritesRemovalPhase.IDLE,
                    activeRemovalEventId = null,
                    undoEvent = null,
                )
            } else {
                state
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
