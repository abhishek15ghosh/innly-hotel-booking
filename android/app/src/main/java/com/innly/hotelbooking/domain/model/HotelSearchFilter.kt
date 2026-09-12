package com.innly.hotelbooking.domain.model

data class HotelSearchFilter(
    val city: String = "",
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
    val minRating: Double? = null,
    val amenities: List<String> = emptyList(),
)
