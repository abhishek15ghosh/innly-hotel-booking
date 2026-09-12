package com.innly.hotelbooking.presentation.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innly.hotelbooking.core.network.parseErrorMessage
import com.innly.hotelbooking.domain.model.EligibleBooking
import com.innly.hotelbooking.domain.model.PrivateReview
import com.innly.hotelbooking.domain.repository.HotelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReviewScreenMode {
    WRITE_NEW,
    RESUBMIT_REJECTED,
    CANNOT_SUBMIT,
}

data class ReviewsUiState(
    val isLoading: Boolean = false,
    val isContextLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val mode: ReviewScreenMode = ReviewScreenMode.WRITE_NEW,
    val eligibleBooking: EligibleBooking? = null,
    val existingReview: PrivateReview? = null,
    val targetBookingId: String? = null,
    val targetReviewId: String? = null,
    val rating: Int = 5,
    val title: String = "",
    val comment: String = "",
    val submittedReview: PrivateReview? = null,
    val errorMessage: String? = null,
) {
    val isTitleValid: Boolean get() = title.trim().length in 2..120
    val isCommentValid: Boolean get() = comment.trim().length in 5..2000
    val isRatingValid: Boolean get() = rating in 1..5
    val isFormValid: Boolean get() = isTitleValid && isCommentValid && isRatingValid
    val canSubmit: Boolean get() = mode != ReviewScreenMode.CANNOT_SUBMIT && isFormValid
    val isResubmission: Boolean get() = mode == ReviewScreenMode.RESUBMIT_REJECTED
}

@HiltViewModel
class ReviewsViewModel @Inject constructor(
    private val hotelRepository: HotelRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReviewsUiState())
    val uiState: StateFlow<ReviewsUiState> = _uiState.asStateFlow()

    fun loadReviewContext(hotelId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isContextLoading = true, errorMessage = null) }
            runCatching {
                hotelRepository.getMyReview(hotelId)
            }.onSuccess { myReviewState ->
                val newerBooking = myReviewState.eligibleBooking
                val rejectedReview = myReviewState.myReviews.firstOrNull { it.status == "rejected" }

                if (newerBooking != null) {
                    // Priority 1: Newer eligible completed stay -> Fresh new-review form (never pass old review ID)
                    _uiState.update {
                        it.copy(
                            isContextLoading = false,
                            mode = ReviewScreenMode.WRITE_NEW,
                            eligibleBooking = newerBooking,
                            targetBookingId = newerBooking.bookingId,
                            targetReviewId = null,
                            existingReview = null,
                            rating = 5,
                            title = "",
                            comment = "",
                        )
                    }
                } else if (rejectedReview != null) {
                    // Priority 2: No newer stay, but has a rejected review -> Edit & resubmit exact review on same review ID
                    _uiState.update {
                        it.copy(
                            isContextLoading = false,
                            mode = ReviewScreenMode.RESUBMIT_REJECTED,
                            eligibleBooking = null,
                            existingReview = rejectedReview,
                            targetBookingId = rejectedReview.bookingId,
                            targetReviewId = rejectedReview.id,
                            rating = rejectedReview.rating,
                            title = rejectedReview.title,
                            comment = rejectedReview.comment,
                        )
                    }
                } else {
                    // Priority 3: No new stay and no rejected review (pending moderation or already approved) -> Cannot submit
                    val latest = myReviewState.latestReview
                    _uiState.update {
                        it.copy(
                            isContextLoading = false,
                            mode = ReviewScreenMode.CANNOT_SUBMIT,
                            eligibleBooking = null,
                            existingReview = latest,
                            targetBookingId = null,
                            targetReviewId = null,
                            rating = latest?.rating ?: 5,
                            title = latest?.title.orEmpty(),
                            comment = latest?.comment.orEmpty(),
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isContextLoading = false,
                        errorMessage = error.parseErrorMessage("Unable to load review status. Please try again."),
                    )
                }
            }
        }
    }

    fun onRatingChanged(newRating: Int) {
        if (newRating in 1..5) {
            _uiState.update { it.copy(rating = newRating) }
        }
    }

    fun onTitleChanged(newTitle: String) {
        _uiState.update { it.copy(title = newTitle) }
    }

    fun onCommentChanged(newComment: String) {
        _uiState.update { it.copy(comment = newComment) }
    }

    fun submitReview(hotelId: String) {
        val currentState = _uiState.value
        if (!currentState.canSubmit) return

        val bookingId = currentState.targetBookingId ?: return
        val reviewId = currentState.targetReviewId

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                hotelRepository.submitReview(
                    hotelId = hotelId,
                    bookingId = bookingId,
                    reviewId = reviewId,
                    rating = currentState.rating,
                    title = currentState.title.trim(),
                    comment = currentState.comment.trim(),
                )
            }.onSuccess { review ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSuccess = true,
                        submittedReview = review,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.parseErrorMessage("Unable to submit review. Please try again."),
                    )
                }
            }
        }
    }
}
