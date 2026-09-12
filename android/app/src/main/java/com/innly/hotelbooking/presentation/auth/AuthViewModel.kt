package com.innly.hotelbooking.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innly.hotelbooking.core.network.parseErrorMessage
import com.innly.hotelbooking.domain.model.UserProfile
import com.innly.hotelbooking.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isAuthResolved: Boolean = false,
    val isLoading: Boolean = false,
    val isSigningOut: Boolean = false,
    val isSyncingProfile: Boolean = false,
    val isAuthenticated: Boolean = false,
    val user: UserProfile? = null,
    val errorMessage: String? = null,
    val syncError: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.update {
                    it.copy(
                        isAuthResolved = true,
                        user = user,
                        isAuthenticated = user != null,
                    )
                }
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                authRepository.signIn(email, password)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.parseErrorMessage("Unable to sign in"),
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun signUp(name: String, email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                authRepository.signUp(name, email, password)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.parseErrorMessage("Unable to register"),
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun signOut(
        onSuccess: () -> Unit = {},
        onFailure: () -> Unit = {},
    ) {
        if (_uiState.value.isSigningOut) return
        _uiState.update { it.copy(isSigningOut = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                authRepository.signOut()
            }.onSuccess {
                _uiState.update { it.copy(isSigningOut = false) }
                onSuccess()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSigningOut = false,
                        errorMessage = error.parseErrorMessage("Sign out failed. Please try again."),
                    )
                }
                onFailure()
            }
        }
    }

    fun retrySyncProfile() {
        if (_uiState.value.isSyncingProfile) return
        _uiState.update { it.copy(isSyncingProfile = true, syncError = null) }
        viewModelScope.launch {
            runCatching {
                authRepository.syncProfile()
            }.onSuccess { syncedProfile ->
                _uiState.update {
                    it.copy(
                        isSyncingProfile = false,
                        user = syncedProfile,
                        syncError = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSyncingProfile = false,
                        syncError = error.parseErrorMessage("Unable to sync profile with server."),
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update {
            it.copy(
                errorMessage = null,
                syncError = null,
            )
        }
    }
}
