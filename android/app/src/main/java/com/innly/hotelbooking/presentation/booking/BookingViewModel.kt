package com.innly.hotelbooking.presentation.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innly.hotelbooking.core.network.parseErrorMessage
import com.innly.hotelbooking.domain.model.Availability
import com.innly.hotelbooking.domain.model.Booking
import com.innly.hotelbooking.domain.model.BookingRequest
import com.innly.hotelbooking.domain.model.Hotel
import com.innly.hotelbooking.domain.model.PaymentOrder
import com.innly.hotelbooking.domain.repository.AuthRepository
import com.innly.hotelbooking.domain.repository.BookingRepository
import com.innly.hotelbooking.domain.repository.HotelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookingUiState(
    val isAuthResolved: Boolean = false,
    val isGuest: Boolean = false,
    val isLoading: Boolean = false,
    val isOpeningCheckout: Boolean = false,
    val isCancelling: Boolean = false,
    val hotel: Hotel? = null,
    val availability: Availability? = null,
    val paymentOrder: PaymentOrder? = null,
    val bookingHistory: List<Booking> = emptyList(),
    val profileName: String = "",
    val profileEmail: String = "",
    val profilePhone: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val paymentVerified: Boolean = false,
    val confirmedBooking: Booking? = null,
    val isDetailsLoading: Boolean = false,
    val detailsError: String? = null,
)

@HiltViewModel
class BookingViewModel @Inject constructor(
    private val hotelRepository: HotelRepository,
    private val bookingRepository: BookingRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookingUiState())
    val uiState: StateFlow<BookingUiState> = _uiState.asStateFlow()

    private var currentSessionUid: String? = null
    private var sessionGeneration: Long = 0L
    private var bookingHistoryJob: Job? = null
    private var bookingDetailsJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                val newUid = user?.firebaseUid
                val isFirstEmission = !_uiState.value.isAuthResolved
                val isSessionTransition = newUid != currentSessionUid

                if (isFirstEmission) {
                    currentSessionUid = newUid
                    _uiState.update {
                        it.copy(
                            isAuthResolved = true,
                            isGuest = newUid == null,
                            profileName = user?.displayName.orEmpty(),
                            profileEmail = user?.email.orEmpty(),
                            profilePhone = user?.phoneNumber.orEmpty(),
                        )
                    }
                    if (newUid != null) {
                        loadBookingHistoryInternal(sessionGeneration)
                    }
                } else if (isSessionTransition) {
                    currentSessionUid = newUid
                    sessionGeneration++
                    bookingHistoryJob?.cancel()
                    bookingDetailsJob?.cancel()

                    val currentGen = sessionGeneration

                    _uiState.update {
                        it.copy(
                            isAuthResolved = true,
                            isGuest = newUid == null,
                            profileName = user?.displayName.orEmpty(),
                            profileEmail = user?.email.orEmpty(),
                            profilePhone = user?.phoneNumber.orEmpty(),
                            bookingHistory = emptyList(),
                            confirmedBooking = null,
                            paymentOrder = null,
                            paymentVerified = false,
                            errorMessage = null,
                            detailsError = null,
                            successMessage = null,
                            isLoading = false,
                            isCancelling = false,
                            isOpeningCheckout = false,
                            isDetailsLoading = false,
                        )
                    }

                    if (newUid != null) {
                        loadBookingHistoryInternal(currentGen)
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isAuthResolved = true,
                            isGuest = newUid == null,
                            profileName = user?.displayName.orEmpty(),
                            profileEmail = user?.email.orEmpty(),
                            profilePhone = user?.phoneNumber.orEmpty(),
                        )
                    }
                }
            }
        }
    }

    fun loadHotel(hotelId: String) {
        if (_uiState.value.hotel?.id == hotelId) return
        viewModelScope.launch {
            runCatching {
                hotelRepository.getHotelDetail(hotelId)
            }.onSuccess { hotel ->
                _uiState.update { it.copy(hotel = hotel) }
            }
        }
    }

    fun invalidateAvailability() {
        _uiState.update {
            it.copy(
                availability = null,
                paymentOrder = null,
                successMessage = null,
            )
        }
    }

    fun checkAvailability(
        hotelId: String,
        roomId: String,
        checkIn: String,
        checkOut: String,
        rooms: Int,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            runCatching {
                hotelRepository.getAvailability(hotelId, roomId, checkIn, checkOut, rooms)
            }.onSuccess { availability ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        availability = availability,
                        successMessage = if (availability.available) "Inventory available" else "Selected room is sold out for these dates",
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.parseErrorMessage("Unable to check availability"),
                    )
                }
            }
        }
    }

    fun createBooking(request: BookingRequest) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
        val capturedGeneration = sessionGeneration
        viewModelScope.launch {
            runCatching {
                authRepository.syncProfile()
                bookingRepository.createBooking(request)
            }.onSuccess { order ->
                if (capturedGeneration == sessionGeneration) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            paymentOrder = order,
                            successMessage = "Booking reserved. Complete payment to confirm.",
                        )
                    }
                }
            }.onFailure { error ->
                if (capturedGeneration == sessionGeneration) {
                    val parsedMessage = error.parseErrorMessage("Unable to create booking. Please try again.")
                    val isProfileSyncError = parsedMessage.contains("Sync your profile", ignoreCase = true)
                    if (isProfileSyncError) {
                        viewModelScope.launch {
                            runCatching { authRepository.syncProfile() }
                        }
                    }
                    val friendlyMessage = if (isProfileSyncError) {
                        "Your account is being synchronized. Please try again."
                    } else {
                        parsedMessage
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = friendlyMessage,
                        )
                    }
                }
            }
        }
    }

    fun verifyPayment(
        bookingId: String,
        razorpayOrderId: String,
        razorpayPaymentId: String,
        razorpaySignature: String,
    ) {
        val capturedGeneration = sessionGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isOpeningCheckout = false, errorMessage = null, successMessage = null) }
            runCatching {
                bookingRepository.verifyPayment(
                    bookingId = bookingId,
                    razorpayOrderId = razorpayOrderId,
                    razorpayPaymentId = razorpayPaymentId,
                    razorpaySignature = razorpaySignature,
                )
            }.onSuccess { result ->
                if (capturedGeneration == sessionGeneration) {
                    val isVerified = result.bookingId == bookingId &&
                            result.bookingStatus.equals("confirmed", ignoreCase = true) &&
                            result.paymentStatus.equals("captured", ignoreCase = true)

                    if (isVerified) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                paymentVerified = true,
                                successMessage = "Payment verified. Booking confirmed.",
                            )
                        }
                    } else {
                        val fallbackError = if (result.bookingId.isNotBlank() && result.bookingId != bookingId) {
                            "Payment verification returned an invalid booking reference."
                        } else {
                            result.message ?: "Payment authorized; confirmation is still pending."
                        }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                paymentVerified = false,
                                errorMessage = fallbackError,
                            )
                        }
                    }
                }
            }.onFailure { error ->
                if (capturedGeneration == sessionGeneration) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            paymentVerified = false,
                            errorMessage = error.parseErrorMessage("Payment verification failed"),
                        )
                    }
                }
            }
        }
    }

    fun consumePaymentVerifiedEvent() {
        _uiState.update { it.copy(paymentVerified = false) }
    }

    fun loadBookingDetails(bookingId: String) {
        val capturedGeneration = sessionGeneration
        bookingDetailsJob?.cancel()
        bookingDetailsJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    confirmedBooking = null,
                    isDetailsLoading = true,
                    detailsError = null,
                )
            }
            val result = runCatching {
                bookingRepository.getBookingHistory()
            }
            if (capturedGeneration != sessionGeneration) return@launch

            result.onSuccess { history ->
                if (capturedGeneration == sessionGeneration) {
                    val match = history.firstOrNull { it.id == bookingId }
                    _uiState.update {
                        it.copy(
                            isDetailsLoading = false,
                            confirmedBooking = match,
                            detailsError = null,
                        )
                    }
                }
            }.onFailure { error ->
                if (capturedGeneration == sessionGeneration) {
                    _uiState.update {
                        it.copy(
                            isDetailsLoading = false,
                            detailsError = error.parseErrorMessage("Unable to load booking details. Please try again."),
                        )
                    }
                }
            }
        }
    }

    fun loadBookingHistory() {
        if (!_uiState.value.isAuthResolved || _uiState.value.isGuest) return
        loadBookingHistoryInternal(sessionGeneration)
    }

    private fun loadBookingHistoryInternal(generation: Long) {
        bookingHistoryJob?.cancel()
        bookingHistoryJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = runCatching { bookingRepository.getBookingHistory() }
            if (generation != sessionGeneration) return@launch

            result.onSuccess { history ->
                if (generation == sessionGeneration) {
                    _uiState.update { it.copy(isLoading = false, bookingHistory = history) }
                }
            }.onFailure { error ->
                if (generation == sessionGeneration) {
                    val message = if (_uiState.value.isGuest) null else error.parseErrorMessage("Unable to load bookings")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = message,
                        )
                    }
                }
            }
        }
    }

    fun cancelBooking(bookingId: String, reason: String, onComplete: () -> Unit = {}) {
        if (_uiState.value.isCancelling) return
        val capturedGeneration = sessionGeneration
        _uiState.update { it.copy(isLoading = true, isCancelling = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            runCatching {
                bookingRepository.cancelBooking(bookingId, reason)
            }.onSuccess { result ->
                if (capturedGeneration == sessionGeneration) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isCancelling = false,
                            successMessage = result.displayMessage,
                        )
                    }
                    loadBookingHistoryInternal(capturedGeneration)
                }
                onComplete()
            }.onFailure { error ->
                if (capturedGeneration == sessionGeneration) {
                    val parsedMessage = error.parseErrorMessage("Cancellation failed")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isCancelling = false,
                            errorMessage = parsedMessage,
                        )
                    }
                }
                onComplete()
            }
        }
    }

    fun clearFeedback() {
        _uiState.update {
            it.copy(
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    fun mapPaymentErrorToFriendlyMessage(rawMessage: String?, code: Int = 0): String {
        val msg = rawMessage.orEmpty()
        val isLaunchError = msg.contains("Unable to open", ignoreCase = true) ||
                msg.contains("Activity missing", ignoreCase = true)
        if (isLaunchError) {
            return "Unable to open secure payment. Please try again."
        }
        return "Payment was not completed. No money was charged. Please try another payment method."
    }

    fun reportPaymentError(message: String?, code: Int = 0) {
        val friendlyMessage = mapPaymentErrorToFriendlyMessage(message, code)
        _uiState.update {
            it.copy(
                errorMessage = friendlyMessage,
                successMessage = null,
                isOpeningCheckout = false,
            )
        }
    }

    fun reportCheckoutLaunchError() {
        _uiState.update {
            it.copy(
                errorMessage = "Unable to open secure payment. Please try again.",
                successMessage = null,
                isOpeningCheckout = false,
            )
        }
    }

    fun startCheckoutAttempt() {
        _uiState.update {
            it.copy(
                errorMessage = null,
                successMessage = null,
                isOpeningCheckout = true,
            )
        }
    }
}
