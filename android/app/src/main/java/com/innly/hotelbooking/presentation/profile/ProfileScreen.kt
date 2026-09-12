package com.innly.hotelbooking.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.innly.hotelbooking.core.designsystem.components.PrimaryButton
import com.innly.hotelbooking.core.ui.ChampagneGold
import com.innly.hotelbooking.core.ui.EmeraldPrimary
import com.innly.hotelbooking.core.ui.InnlyTheme
import com.innly.hotelbooking.core.ui.StatusErrorRed
import com.innly.hotelbooking.presentation.auth.AuthViewModel

@Composable
fun ProfileScreen(
    onSignedOut: () -> Unit,
    onSignInClick: () -> Unit = {},
    onRegisterClick: () -> Unit = {},
    onViewBookingsClick: () -> Unit = {},
    onViewFavoritesClick: () -> Unit = {},
    onContinueBrowsingClick: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSignOutDialog by remember { mutableStateOf(false) }

    val profileDisplayData = remember(state.user) {
        state.user?.let { user ->
            ProfileFormatter.toProfileDisplayData(
                displayName = user.displayName,
                email = user.email,
                phoneNumber = user.phoneNumber,
                role = user.role,
            )
        }
    }

    ProfileContent(
        profileData = profileDisplayData,
        isAuthResolved = state.isAuthResolved,
        isSigningOut = state.isSigningOut,
        isSyncingProfile = state.isSyncingProfile,
        errorMessage = state.errorMessage,
        syncError = state.syncError,
        onSignInClick = onSignInClick,
        onRegisterClick = onRegisterClick,
        onViewBookingsClick = onViewBookingsClick,
        onViewFavoritesClick = onViewFavoritesClick,
        onContinueBrowsingClick = onContinueBrowsingClick,
        onRetrySyncClick = { viewModel.retrySyncProfile() },
        onSignOutClick = { showSignOutDialog = true },
    )

    if (showSignOutDialog) {
        SignOutConfirmationDialog(
            isSigningOut = state.isSigningOut,
            onDismiss = {
                if (!state.isSigningOut) showSignOutDialog = false
            },
            onConfirmSignOut = {
                viewModel.signOut(
                    onSuccess = {
                        showSignOutDialog = false
                        onSignedOut()
                    },
                    onFailure = {
                        showSignOutDialog = false
                    },
                )
            },
        )
    }
}

@Composable
fun ProfileContent(
    profileData: ProfileDisplayData?,
    isAuthResolved: Boolean,
    isSigningOut: Boolean,
    isSyncingProfile: Boolean,
    errorMessage: String?,
    syncError: String?,
    onSignInClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onViewBookingsClick: () -> Unit,
    onViewFavoritesClick: () -> Unit,
    onContinueBrowsingClick: () -> Unit,
    onRetrySyncClick: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
    ) {
        // State Priority 1: Resolving Initial Auth Session
        if (!isAuthResolved) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(38.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Restoring account session...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // State Priority 2: Unauthenticated Guest State
        else if (profileData == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = ChampagneGold.copy(alpha = 0.20f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = ChampagneGold.copy(alpha = 0.35f),
                    ),
                    modifier = Modifier.size(88.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Luggage,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(42.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Welcome to Innly",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Sign in to view your bookings, save favorite hotels, and complete fast reservations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    lineHeight = 22.sp,
                )

                Spacer(modifier = Modifier.height(28.dp))

                PrimaryButton(
                    text = "Sign In",
                    onClick = onSignInClick,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .heightIn(min = 48.dp),
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = onRegisterClick,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        text = "Create Account",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onContinueBrowsingClick,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        text = "Continue Browsing",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        // State Priority 3: Authenticated User Profile Dashboard
        else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Header Profile Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(20.dp),
                        ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Monogram Initial Avatar
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            border = androidx.compose.foundation.BorderStroke(2.dp, ChampagneGold),
                            modifier = Modifier.size(68.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = profileData.initials,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = profileData.displayName,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = profileData.email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                                ),
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Text(
                                    text = profileData.roleLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }

                // Explicit Sync Error Banner
                syncError?.let { err ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(
                                onClick = onRetrySyncClick,
                                enabled = !isSyncingProfile,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.heightIn(min = 40.dp),
                            ) {
                                if (isSyncingProfile) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Retry Sync", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                // General Error Banner
                errorMessage?.let { err ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }

                // Account Information Section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(18.dp),
                        ),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "Account Details",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Full Name Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Account Name",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = profileData.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        // Email Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Email Address",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = profileData.email,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        // Phone Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Phone Number",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = profileData.phoneNumber,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                // Working Navigation Shortcuts
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(18.dp),
                        ),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    ) {
                        // My Bookings Shortcut
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onViewBookingsClick)
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(42.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "My Bookings",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "View upcoming reservations and receipts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = "Open My Bookings",
                                tint = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )

                        // Saved Favorites Shortcut
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onViewFavoritesClick)
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                modifier = Modifier.size(42.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = ChampagneGold,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Saved Favorites",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Hotels and stays you saved",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = "Open Saved Favorites",
                                tint = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                // Sign Out Action Button
                OutlinedButton(
                    onClick = onSignOutClick,
                    enabled = !isSigningOut,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusErrorRed),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = !isSigningOut).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(StatusErrorRed.copy(alpha = 0.5f)),
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = StatusErrorRed,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Sign Out",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
fun SignOutConfirmationDialog(
    isSigningOut: Boolean,
    onDismiss: () -> Unit,
    onConfirmSignOut: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isSigningOut) onDismiss() },
        shape = RoundedCornerShape(24.dp),
        icon = {
            Surface(
                shape = CircleShape,
                color = StatusErrorRed.copy(alpha = 0.15f),
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "Sign Out Warning",
                        tint = StatusErrorRed,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        },
        title = {
            Text(
                text = "Sign out of Innly?",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                ),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = "You will need to sign in again to view your bookings and manage reservations.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
        },
        confirmButton = {
            TextButton(
                enabled = !isSigningOut,
                onClick = onConfirmSignOut,
                colors = ButtonDefaults.textButtonColors(contentColor = StatusErrorRed),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = if (isSigningOut) "Signing out..." else "Sign Out",
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSigningOut,
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Cancel", fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenAuthenticatedPreview() {
    InnlyTheme {
        ProfileContent(
            profileData = ProfileDisplayData(
                initials = "AG",
                displayName = "Abhishek Ghosh",
                email = "abhishek@example.com",
                phoneNumber = "+91 9876543210",
                roleLabel = "Innly Member",
            ),
            isAuthResolved = true,
            isSigningOut = false,
            isSyncingProfile = false,
            errorMessage = null,
            syncError = null,
            onSignInClick = {},
            onRegisterClick = {},
            onViewBookingsClick = {},
            onViewFavoritesClick = {},
            onContinueBrowsingClick = {},
            onRetrySyncClick = {},
            onSignOutClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenGuestPreview() {
    InnlyTheme {
        ProfileContent(
            profileData = null,
            isAuthResolved = true,
            isSigningOut = false,
            isSyncingProfile = false,
            errorMessage = null,
            syncError = null,
            onSignInClick = {},
            onRegisterClick = {},
            onViewBookingsClick = {},
            onViewFavoritesClick = {},
            onContinueBrowsingClick = {},
            onRetrySyncClick = {},
            onSignOutClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenLoadingPreview() {
    InnlyTheme {
        ProfileContent(
            profileData = null,
            isAuthResolved = false,
            isSigningOut = false,
            isSyncingProfile = false,
            errorMessage = null,
            syncError = null,
            onSignInClick = {},
            onRegisterClick = {},
            onViewBookingsClick = {},
            onViewFavoritesClick = {},
            onContinueBrowsingClick = {},
            onRetrySyncClick = {},
            onSignOutClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenSyncErrorPreview() {
    InnlyTheme {
        ProfileContent(
            profileData = ProfileDisplayData(
                initials = "AG",
                displayName = "Abhishek Ghosh",
                email = "abhishek@example.com",
                phoneNumber = "Not provided",
                roleLabel = "Innly Member",
            ),
            isAuthResolved = true,
            isSigningOut = false,
            isSyncingProfile = false,
            errorMessage = null,
            syncError = "Unable to sync profile with server.",
            onSignInClick = {},
            onRegisterClick = {},
            onViewBookingsClick = {},
            onViewFavoritesClick = {},
            onContinueBrowsingClick = {},
            onRetrySyncClick = {},
            onSignOutClick = {},
        )
    }
}
