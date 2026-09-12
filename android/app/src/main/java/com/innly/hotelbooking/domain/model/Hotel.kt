package com.innly.hotelbooking.domain.model

data class Hotel(
    val id: String,
    val name: String,
    val description: String,
    val city: String,
    val address: String,
    val rating: Double,
    val reviewCount: Int,
    val startingPrice: Int,
    val thumbnailUrl: String,
    val images: List<String>,
    val amenities: List<String>,
    val isFavorite: Boolean,
    val rooms: List<Room> = emptyList(),
)
