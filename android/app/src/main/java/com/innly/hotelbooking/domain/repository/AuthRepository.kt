package com.innly.hotelbooking.domain.repository

import com.innly.hotelbooking.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<UserProfile?>
    suspend fun signIn(email: String, password: String)
    suspend fun signUp(name: String, email: String, password: String)
    suspend fun signOut()
    suspend fun syncProfile(displayName: String? = null, phoneNumber: String? = null): UserProfile
}
