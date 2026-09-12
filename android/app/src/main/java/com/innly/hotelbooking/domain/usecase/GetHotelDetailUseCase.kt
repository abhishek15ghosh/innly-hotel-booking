package com.innly.hotelbooking.domain.usecase

import com.innly.hotelbooking.domain.repository.HotelRepository
import javax.inject.Inject

class GetHotelDetailUseCase @Inject constructor(
    private val hotelRepository: HotelRepository,
) {
    suspend operator fun invoke(hotelId: String) = hotelRepository.getHotelDetail(hotelId)
}
