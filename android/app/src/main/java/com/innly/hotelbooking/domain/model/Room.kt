package com.innly.hotelbooking.domain.model

data class Room(
    val id: String,
    val hotelId: String,
    val name: String,
    val description: String,
    val capacity: Int,
    val bedType: String,
    val basePrice: Int,
    val thumbnailUrl: String,
    val images: List<String>,
)
