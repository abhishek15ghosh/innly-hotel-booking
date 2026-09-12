package com.innly.hotelbooking.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.innly.hotelbooking.data.remote.HotelApiService
import com.innly.hotelbooking.data.remote.ProfileSyncRequest
import com.innly.hotelbooking.data.remote.toDomain
import com.innly.hotelbooking.domain.model.UserProfile
import com.innly.hotelbooking.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val apiService: HotelApiService,
) : AuthRepository {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val cachedProfiles = ConcurrentHashMap<String, UserProfile>()
    private val activeSyncJobs = ConcurrentHashMap<String, Deferred<UserProfile>>()

    override val currentUser: Flow<UserProfile?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                val uid = user.uid
                val cached = cachedProfiles[uid]
                trySend(
                    cached ?: UserProfile(
                        firebaseUid = uid,
                        displayName = user.displayName.orEmpty(),
                        email = user.email.orEmpty(),
                        phoneNumber = user.phoneNumber,
                    ),
                )
                if (cached == null || cached.id.isBlank()) {
                    repositoryScope.launch {
                        runCatching {
                            syncProfile(
                                displayName = user.displayName,
                                phoneNumber = user.phoneNumber,
                            )
                        }.onSuccess { syncedProfile ->
                            trySend(syncedProfile)
                        }
                    }
                }
            } else {
                repositoryScope.launch {
                    syncMutex.withLock {
                        activeSyncJobs.values.forEach { it.cancel() }
                        activeSyncJobs.clear()
                        cachedProfiles.clear()
                    }
                }
                trySend(null)
            }
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override suspend fun signIn(email: String, password: String) {
        firebaseAuth.signInWithEmailAndPassword(email, password).await()
        syncProfile()
    }

    override suspend fun signUp(name: String, email: String, password: String) {
        firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        firebaseAuth.currentUser?.updateProfile(userProfileChangeRequest { displayName = name })?.await()
        syncProfile(displayName = name)
    }

    override suspend fun signOut() {
        syncMutex.withLock {
            activeSyncJobs.values.forEach { it.cancel() }
            activeSyncJobs.clear()
            cachedProfiles.clear()
        }
        firebaseAuth.signOut()
    }

    override suspend fun syncProfile(displayName: String?, phoneNumber: String?): UserProfile {
        val user = firebaseAuth.currentUser ?: throw IllegalStateException("No active Firebase user")
        val uid = user.uid

        val jobToAwait: Deferred<UserProfile> = syncMutex.withLock {
            if (displayName == null && phoneNumber == null) {
                cachedProfiles[uid]?.let { cached ->
                    if (cached.id.isNotBlank()) return cached
                }
            }

            val existingJob = activeSyncJobs[uid]
            if (existingJob != null && existingJob.isActive) {
                existingJob
            } else {
                val newJob = repositoryScope.async {
                    val sanitizedDisplayName = (displayName ?: user.displayName)?.ifBlank { null }
                    val sanitizedPhoneNumber = (phoneNumber ?: user.phoneNumber)?.ifBlank { null }
                    val response = apiService.syncProfile(
                        request = ProfileSyncRequest(
                            displayName = sanitizedDisplayName,
                            phoneNumber = sanitizedPhoneNumber,
                        ),
                    )
                    val profile = response.data.toDomain()
                    cachedProfiles[uid] = profile
                    profile
                }
                newJob.invokeOnCompletion {
                    repositoryScope.launch {
                        syncMutex.withLock {
                            activeSyncJobs.remove(uid)
                        }
                    }
                }
                activeSyncJobs[uid] = newJob
                newJob
            }
        }

        return jobToAwait.await()
    }
}
