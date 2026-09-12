package com.innly.hotelbooking.domain.model

data class DailyAvailability(
    val date: String,
    val price: Int,
    val availableInventory: Int,
)

data class Availability(
    val available: Boolean,
    val nights: Int,
    val totalAmount: Int,
    val currency: String,
    val dailyBreakdown: List<DailyAvailability>,
)
