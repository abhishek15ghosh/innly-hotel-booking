package com.innly.hotelbooking.domain.usecase

import com.innly.hotelbooking.domain.model.HotelSearchFilter
import com.innly.hotelbooking.domain.repository.HotelRepository
import javax.inject.Inject

class GetHotelsUseCase @Inject constructor(
    private val hotelRepository: HotelRepository,
) {
    suspend operator fun invoke(filter: HotelSearchFilter) = hotelRepository.getHotels(filter)
}
