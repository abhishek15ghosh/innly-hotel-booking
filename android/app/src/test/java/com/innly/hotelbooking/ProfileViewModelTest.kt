package com.innly.hotelbooking

import com.innly.hotelbooking.domain.model.UserProfile
import com.innly.hotelbooking.domain.repository.AuthRepository
import com.innly.hotelbooking.presentation.auth.AuthViewModel
import com.innly.hotelbooking.presentation.profile.ProfileFormatter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeAuthRepositoryForProfile(
        private val authFlow: Flow<UserProfile?> = flowOf(
            UserProfile(
                id = "db-user-uuid-12345",
                firebaseUid = "fb-uid-secret-67890",
                displayName = "Abhishek Ghosh",
                email = "abhishek@example.com",
                phoneNumber = "+91 9876543210",
                role = "user",
            )
        ),
        private val shouldFailSignOut: Boolean = false,
        private val signOutDeferred: CompletableDeferred<Unit>? = null,
        private val shouldFailSync: Boolean = false,
        private val syncedProfile: UserProfile = UserProfile(
            id = "db-user-uuid-12345",
            firebaseUid = "fb-uid-secret-67890",
            displayName = "Abhishek Ghosh Updated",
            email = "abhishek@example.com",
            phoneNumber = "+91 9876543210",
            role = "user",
        )
    ) : AuthRepository {
        var signOutCallCount = 0
        var syncCallCount = 0

        override val currentUser: Flow<UserProfile?> = authFlow

        override suspend fun signIn(email: String, password: String) {}
        override suspend fun signUp(name: String, email: String, password: String) {}

        override suspend fun signOut() {
            signOutCallCount++
            signOutDeferred?.await()
            if (shouldFailSignOut) {
                throw Exception("Sign out network error")
            }
        }

        override suspend fun syncProfile(displayName: String?, phoneNumber: String?): UserProfile {
            syncCallCount++
            if (shouldFailSync) {
                throw Exception("Server unavailable during sync")
            }
            return syncedProfile
        }
    }

    @Test
    fun `1 initial state has isAuthResolved false until first auth emission is collected`() = runTest {
        val delayedSharedFlow = MutableSharedFlow<UserProfile?>(replay = 1)
        val fakeRepo = FakeAuthRepositoryForProfile(authFlow = delayedSharedFlow)

        val viewModel = AuthViewModel(authRepository = fakeRepo)

        assertFalse(viewModel.uiState.value.isAuthResolved)
        assertNull(viewModel.uiState.value.user)

        delayedSharedFlow.emit(UserProfile(displayName = "Test", email = "test@example.com"))
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAuthResolved)
        assertNotNull(viewModel.uiState.value.user)
    }

    @Test
    fun `2 initial auth emission resolves auth state and populates user profile`() = runTest {
        val fakeRepo = FakeAuthRepositoryForProfile()
        val viewModel = AuthViewModel(authRepository = fakeRepo)

        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAuthResolved)
        assertTrue(viewModel.uiState.value.isAuthenticated)
        assertEquals("Abhishek Ghosh", viewModel.uiState.value.user?.displayName)
        assertEquals("abhishek@example.com", viewModel.uiState.value.user?.email)
    }

    @Test
    fun `3 initials formatter extracts two-letter initials from full names and single names`() {
        assertEquals("AG", ProfileFormatter.formatUserInitials("Abhishek Ghosh", "abhishek@example.com"))
        assertEquals("JD", ProfileFormatter.formatUserInitials("John Doe", "john@example.com"))
        assertEquals("AL", ProfileFormatter.formatUserInitials("Alice", "alice@example.com"))
        assertEquals("RO", ProfileFormatter.formatUserInitials("   robert   ", "robert@example.com"))
    }

    @Test
    fun `4 initials formatter falls back to email character when display name is blank`() {
        assertEquals("A", ProfileFormatter.formatUserInitials("", "abhishek@example.com"))
        assertEquals("S", ProfileFormatter.formatUserInitials("   ", "sam@example.com"))
        assertEquals("IM", ProfileFormatter.formatUserInitials(null, null))
    }

    @Test
    fun `5 phone number formatter returns Not provided for null or blank numbers`() {
        assertEquals("+91 9876543210", ProfileFormatter.formatDisplayPhone("+91 9876543210"))
        assertEquals("Not provided", ProfileFormatter.formatDisplayPhone(null))
        assertEquals("Not provided", ProfileFormatter.formatDisplayPhone(""))
        assertEquals("Not provided", ProfileFormatter.formatDisplayPhone("   "))
    }

    @Test
    fun `6 account role formatter maps admin to Administrator and user to Innly Member`() {
        assertEquals("Administrator", ProfileFormatter.formatAccountRole("admin"))
        assertEquals("Administrator", ProfileFormatter.formatAccountRole("ADMIN"))
        assertEquals("Innly Member", ProfileFormatter.formatAccountRole("user"))
        assertEquals("Innly Member", ProfileFormatter.formatAccountRole("guest"))
        assertEquals("Innly Member", ProfileFormatter.formatAccountRole(null))
        assertEquals("Innly Member", ProfileFormatter.formatAccountRole(""))
    }

    @Test
    fun `7 signOut waits for completion before invoking onSuccess callback`() = runTest {
        val signOutDeferred = CompletableDeferred<Unit>()
        val fakeRepo = FakeAuthRepositoryForProfile(signOutDeferred = signOutDeferred)
        val viewModel = AuthViewModel(authRepository = fakeRepo)

        var navigationInvoked = false
        var failureInvoked = false
        viewModel.signOut(
            onSuccess = { navigationInvoked = true },
            onFailure = { failureInvoked = true },
        )

        testScheduler.runCurrent()

        assertTrue("isSigningOut must be true while signOut is suspended", viewModel.uiState.value.isSigningOut)
        assertFalse("navigation must not be called while signOut is in-flight", navigationInvoked)
        assertFalse("onFailure must not be called while signOut is in-flight", failureInvoked)
        assertEquals(1, fakeRepo.signOutCallCount)

        signOutDeferred.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertFalse("isSigningOut must be false after completion", viewModel.uiState.value.isSigningOut)
        assertTrue("navigation must be called after completion", navigationInvoked)
        assertFalse(failureInvoked)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `8 failed signOut keeps user on Profile and sets retryable errorMessage`() = runTest {
        val fakeRepo = FakeAuthRepositoryForProfile(shouldFailSignOut = true)
        val viewModel = AuthViewModel(authRepository = fakeRepo)

        var navigationInvoked = false
        var failureInvoked = false
        viewModel.signOut(
            onSuccess = { navigationInvoked = true },
            onFailure = { failureInvoked = true },
        )

        testScheduler.advanceUntilIdle()

        assertEquals(1, fakeRepo.signOutCallCount)
        assertFalse("navigation must NOT be called on failure", navigationInvoked)
        assertTrue("failure callback must be invoked on failure", failureInvoked)
        assertFalse("isSigningOut must be false after failure", viewModel.uiState.value.isSigningOut)
    }

    @Test
    fun `9 explicit retrySyncProfile failure sets syncError banner without signing user out`() = runTest {
        val fakeRepo = FakeAuthRepositoryForProfile(shouldFailSync = true)
        val viewModel = AuthViewModel(authRepository = fakeRepo)

        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isAuthenticated)

        viewModel.retrySyncProfile()
        testScheduler.advanceUntilIdle()

        assertEquals(1, fakeRepo.syncCallCount)
        assertFalse(viewModel.uiState.value.isSyncingProfile)
        assertEquals("Server unavailable during sync", viewModel.uiState.value.syncError)
        // User must remain logged in with local data
        assertTrue(viewModel.uiState.value.isAuthenticated)
        assertNotNull(viewModel.uiState.value.user)
    }

    @Test
    fun `10 successful retrySyncProfile clears syncError and updates user profile`() = runTest {
        val fakeRepo = FakeAuthRepositoryForProfile(shouldFailSync = false)
        val viewModel = AuthViewModel(authRepository = fakeRepo)

        testScheduler.advanceUntilIdle()

        viewModel.retrySyncProfile()
        testScheduler.advanceUntilIdle()

        assertEquals(1, fakeRepo.syncCallCount)
        assertFalse(viewModel.uiState.value.isSyncingProfile)
        assertNull(viewModel.uiState.value.syncError)
        assertEquals("Abhishek Ghosh Updated", viewModel.uiState.value.user?.displayName)
    }

    @Test
    fun `11 privacy check UserProfile formatting strictly excludes firebaseUid and internal database IDs`() {
        val fullUser = UserProfile(
            id = "db-user-uuid-12345-secret",
            firebaseUid = "fb-uid-secret-67890-private",
            displayName = "Abhishek Ghosh",
            email = "abhishek@example.com",
            phoneNumber = "+91 9876543210",
            role = "user",
        )

        val displayData = ProfileFormatter.toProfileDisplayData(
            displayName = fullUser.displayName,
            email = fullUser.email,
            phoneNumber = fullUser.phoneNumber,
            role = fullUser.role,
        )

        // Verify public fields are mapped accurately
        assertEquals("AG", displayData.initials)
        assertEquals("Abhishek Ghosh", displayData.displayName)
        assertEquals("abhishek@example.com", displayData.email)
        assertEquals("+91 9876543210", displayData.phoneNumber)
        assertEquals("Innly Member", displayData.roleLabel)

        // Verify private fields never enter display data
        assertFalse(displayData.displayName.contains("db-user-uuid"))
        assertFalse(displayData.displayName.contains("fb-uid"))
        assertFalse(displayData.email.contains("db-user-uuid"))
        assertFalse(displayData.email.contains("fb-uid"))
        assertFalse(displayData.phoneNumber.contains("db-user-uuid"))
        assertFalse(displayData.phoneNumber.contains("fb-uid"))
    }

    @Test
    fun `12 blank display names use factual email fallback and never render empty or invented name`() {
        val userWithBlankName = UserProfile(
            id = "db-user-uuid-99999",
            firebaseUid = "fb-uid-secret-99999",
            displayName = "",
            email = "user@example.com",
            phoneNumber = null,
            role = "user",
        )

        val displayData = ProfileFormatter.toProfileDisplayData(
            displayName = userWithBlankName.displayName,
            email = userWithBlankName.email,
            phoneNumber = userWithBlankName.phoneNumber,
            role = userWithBlankName.role,
        )

        // Factual email prefix fallback
        assertEquals("user", displayData.displayName)
        assertTrue("displayName heading must not be empty or blank", displayData.displayName.isNotBlank())
        assertFalse("Must not invent arbitrary synthetic names like Guest or Anonymous", displayData.displayName.equals("Guest", ignoreCase = true))
        assertEquals("U", displayData.initials)
        assertEquals("Not provided", displayData.phoneNumber)
        assertEquals("Innly Member", displayData.roleLabel)
    }
}
