package com.innly.hotelbooking.domain.model

data class UserProfile(
    val id: String = "",
    val firebaseUid: String = "",
    val displayName: String = "",
    val email: String = "",
    val phoneNumber: String? = null,
    val role: String = "user",
)
