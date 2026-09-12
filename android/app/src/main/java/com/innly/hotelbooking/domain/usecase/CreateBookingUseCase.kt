package com.innly.hotelbooking.domain.usecase

import com.innly.hotelbooking.domain.model.BookingRequest
import com.innly.hotelbooking.domain.repository.BookingRepository
import javax.inject.Inject

class CreateBookingUseCase @Inject constructor(
    private val bookingRepository: BookingRepository,
) {
    suspend operator fun invoke(request: BookingRequest) = bookingRepository.createBooking(request)
}
