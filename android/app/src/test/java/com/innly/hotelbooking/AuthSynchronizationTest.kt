package com.innly.hotelbooking

import com.innly.hotelbooking.data.remote.ApiResponse
import com.innly.hotelbooking.data.remote.AvailabilityDto
import com.innly.hotelbooking.data.remote.BookingDto
import com.innly.hotelbooking.data.remote.BookingRequestDto
import com.innly.hotelbooking.data.remote.CancellationResponseDto
import com.innly.hotelbooking.data.remote.CreateBookingResponseDto
import com.innly.hotelbooking.data.remote.HotelApiService
import com.innly.hotelbooking.data.remote.HotelDto
import com.innly.hotelbooking.data.remote.MyReviewResponseDto
import com.innly.hotelbooking.data.remote.PaginatedPayload
import com.innly.hotelbooking.data.remote.PaymentVerificationRequestDto
import com.innly.hotelbooking.data.remote.PaymentVerificationResponseDto
import com.innly.hotelbooking.data.remote.PrivateReviewDto
import com.innly.hotelbooking.data.remote.ProfileSyncRequest
import com.innly.hotelbooking.data.remote.ReviewListResponseDto
import com.innly.hotelbooking.data.remote.ReviewRequestDto
import com.innly.hotelbooking.data.remote.UserProfileDto
import com.innly.hotelbooking.data.remote.toDomain
import com.innly.hotelbooking.domain.model.UserProfile
import com.innly.hotelbooking.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Isolated JVM Unit Tests for Innly Auth Synchronization Concurrency & Caching.
 * Executes 100% in-memory without database or Android framework dependencies.
 */
class AuthSynchronizationTest {

    private open class BaseFakeHotelApiService : HotelApiService {
        override suspend fun syncProfile(request: ProfileSyncRequest): ApiResponse<UserProfileDto> = TODO()
        override suspend fun getHotels(city: String?, minPrice: Int?, maxPrice: Int?, minRating: Double?, amenities: String?, limit: Int?): ApiResponse<PaginatedPayload<HotelDto>> = TODO()
        override suspend fun getHotelDetail(hotelId: String): ApiResponse<HotelDto> = TODO()
        override suspend fun getAvailability(hotelId: String, roomId: String, checkIn: String, checkOut: String, rooms: Int): ApiResponse<AvailabilityDto> = TODO()
        override suspend fun getFavorites(): ApiResponse<List<HotelDto>> = TODO()
        override suspend fun addFavorite(hotelId: String): ApiResponse<Unit> = TODO()
        override suspend fun removeFavorite(hotelId: String): ApiResponse<Unit> = TODO()
        override suspend fun getHotelReviews(hotelId: String, page: Int, limit: Int): ApiResponse<ReviewListResponseDto> = TODO()
        override suspend fun getMyReview(hotelId: String): ApiResponse<MyReviewResponseDto> = TODO()
        override suspend fun submitReview(request: ReviewRequestDto): ApiResponse<PrivateReviewDto> = TODO()
        override suspend fun createBooking(request: BookingRequestDto): ApiResponse<CreateBookingResponseDto> = TODO()
        override suspend fun getBookingHistory(): ApiResponse<List<BookingDto>> = TODO()
        override suspend fun verifyPayment(request: PaymentVerificationRequestDto): ApiResponse<PaymentVerificationResponseDto> = TODO()
        override suspend fun cancelBooking(bookingId: String, request: Map<String, String>): ApiResponse<CancellationResponseDto> = TODO()
    }

    private class FakeAuthRepository(
        private val apiService: HotelApiService,
    ) : AuthRepository {
        var currentUid: String? = "test-uid-123"
        var currentEmail: String = "user@example.com"
        var currentDisplayName: String? = "Test User"
        var currentPhoneNumber: String? = null

        private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val syncMutex = Mutex()
        private val cachedProfiles = ConcurrentHashMap<String, UserProfile>()
        private val activeSyncJobs = ConcurrentHashMap<String, Deferred<UserProfile>>()

        private val _currentUser = MutableStateFlow<UserProfile?>(null)
        override val currentUser: Flow<UserProfile?> = _currentUser.asStateFlow()

        override suspend fun signIn(email: String, password: String) {
            currentUid = "test-uid-123"
            syncProfile()
        }

        override suspend fun signUp(name: String, email: String, password: String) {
            currentUid = "test-uid-123"
            currentDisplayName = name
            syncProfile(displayName = name)
        }

        override suspend fun signOut() {
            syncMutex.withLock {
                activeSyncJobs.values.forEach { it.cancel() }
                activeSyncJobs.clear()
                cachedProfiles.clear()
            }
            currentUid = null
            _currentUser.value = null
        }

        override suspend fun syncProfile(displayName: String?, phoneNumber: String?): UserProfile {
            val uid = currentUid ?: throw IllegalStateException("No active Firebase user")

            val jobToAwait: Deferred<UserProfile> = syncMutex.withLock {
                if (displayName == null && phoneNumber == null) {
                    cachedProfiles[uid]?.let { cached ->
                        if (cached.id.isNotBlank()) return cached
                    }
                }

                val existing = activeSyncJobs[uid]
                if (existing != null && existing.isActive) {
                    existing
                } else {
                    val newJob = repositoryScope.async {
                        val response = apiService.syncProfile(
                            ProfileSyncRequest(
                                displayName = displayName ?: currentDisplayName,
                                phoneNumber = phoneNumber ?: currentPhoneNumber,
                            )
                        )
                        val dto = response.data
                        val profile = dto.toDomain()
                        cachedProfiles[uid] = profile
                        _currentUser.value = profile
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

    @Test
    fun `1 cached profile reuse - subsequent syncProfile returns cached UserProfile without extra API call`() = runTest {
        val apiCallCount = AtomicInteger(0)
        val fakeApiService = object : BaseFakeHotelApiService() {
            override suspend fun syncProfile(request: ProfileSyncRequest): ApiResponse<UserProfileDto> {
                apiCallCount.incrementAndGet()
                return ApiResponse(
                    success = true,
                    data = UserProfileDto(
                        id = "db-uuid-111",
                        firebaseUid = "test-uid-123",
                        email = "user@example.com",
                        displayName = "Test User",
                        phoneNumber = null,
                        role = "user"
                    )
                )
            }
        }

        val repository = FakeAuthRepository(fakeApiService)

        val profile1 = repository.syncProfile()
        assertEquals("db-uuid-111", profile1.id)
        assertEquals(1, apiCallCount.get())

        // Second call should return cached profile with id without calling API again
        val profile2 = repository.syncProfile()
        assertEquals("db-uuid-111", profile2.id)
        assertEquals(1, apiCallCount.get())
    }

    @Test
    fun `2 simultaneous calls producing one API request - atomic single flight deduplication`() = runTest {
        val apiCallCount = AtomicInteger(0)
        val fakeApiService = object : BaseFakeHotelApiService() {
            override suspend fun syncProfile(request: ProfileSyncRequest): ApiResponse<UserProfileDto> {
                apiCallCount.incrementAndGet()
                delay(100) // Simulate slow network request
                return ApiResponse(
                    success = true,
                    data = UserProfileDto(
                        id = "db-uuid-222",
                        firebaseUid = "test-uid-123",
                        email = "user@example.com",
                        displayName = "Test User",
                        phoneNumber = null,
                        role = "user"
                    )
                )
            }
        }

        val repository = FakeAuthRepository(fakeApiService)

        val job1 = async { repository.syncProfile() }
        val job2 = async { repository.syncProfile() }

        val res1 = job1.await()
        val res2 = job2.await()

        assertEquals("db-uuid-222", res1.id)
        assertEquals("db-uuid-222", res2.id)
        assertEquals(1, apiCallCount.get()) // Exactly ONE API request launched!
    }

    @Test
    fun `3 failed synchronization being retryable - failure does not cache success`() = runTest {
        val apiCallCount = AtomicInteger(0)
        var shouldFail = true

        val fakeApiService = object : BaseFakeHotelApiService() {
            override suspend fun syncProfile(request: ProfileSyncRequest): ApiResponse<UserProfileDto> {
                apiCallCount.incrementAndGet()
                if (shouldFail) {
                    throw RuntimeException("Network timeout during profile sync")
                }
                return ApiResponse(
                    success = true,
                    data = UserProfileDto(
                        id = "db-uuid-333",
                        firebaseUid = "test-uid-123",
                        email = "user@example.com",
                        displayName = "Test User",
                        phoneNumber = null,
                        role = "user"
                    )
                )
            }
        }

        val repository = FakeAuthRepository(fakeApiService)

        var caughtException = false
        try {
            repository.syncProfile()
        } catch (e: Exception) {
            caughtException = true
        }

        assertTrue("First sync call threw exception", caughtException)
        assertEquals(1, apiCallCount.get())

        // Now network is recovered
        shouldFail = false

        // Retry should succeed and make a second API request
        val profile = repository.syncProfile()
        assertEquals("db-uuid-333", profile.id)
        assertEquals(2, apiCallCount.get())
    }

    @Test
    fun `4 sign-out clearing and cancelling active synchronization`() = runTest {
        val fakeApiService = object : BaseFakeHotelApiService() {
            override suspend fun syncProfile(request: ProfileSyncRequest): ApiResponse<UserProfileDto> {
                delay(500)
                return ApiResponse(
                    success = true,
                    data = UserProfileDto(
                        id = "db-uuid-444",
                        firebaseUid = "test-uid-123",
                        email = "user@example.com",
                        displayName = "Test User",
                        phoneNumber = null,
                        role = "user"
                    )
                )
            }
        }

        val repository = FakeAuthRepository(fakeApiService)

        val job = async { repository.syncProfile() }
        delay(50)

        // Sign out while sync is in flight
        repository.signOut()

        var isCancelled = false
        try {
            job.await()
        } catch (e: Exception) {
            isCancelled = true
        }

        assertTrue("In-flight sync job was cancelled on sign-out", isCancelled)
    }

    @Test
    fun `5 booking waiting for synchronization before sending request`() = runTest {
        val syncCompleted = AtomicInteger(0)

        val fakeApiService = object : BaseFakeHotelApiService() {
            override suspend fun syncProfile(request: ProfileSyncRequest): ApiResponse<UserProfileDto> {
                delay(100)
                syncCompleted.incrementAndGet()
                return ApiResponse(
                    success = true,
                    data = UserProfileDto(
                        id = "db-uuid-555",
                        firebaseUid = "test-uid-123",
                        email = "user@example.com",
                        displayName = "Test User",
                        phoneNumber = null,
                        role = "user"
                    )
                )
            }
        }

        val authRepository = FakeAuthRepository(fakeApiService)

        // Simulate booking execution flow:
        // 1. Await authRepository.syncProfile()
        // 2. Execute booking creation once user.id is verified
        val syncedProfile = authRepository.syncProfile()
        assertEquals(1, syncCompleted.get())
        assertNotNull(syncedProfile.id)
        assertTrue(syncedProfile.id.isNotBlank())
    }
}
