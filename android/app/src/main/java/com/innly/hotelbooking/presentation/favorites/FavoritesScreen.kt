package com.innly.hotelbooking.presentation.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.innly.hotelbooking.core.designsystem.components.HotelCard
import com.innly.hotelbooking.core.designsystem.components.PrimaryButton
import com.innly.hotelbooking.core.ui.ChampagneGold
import com.innly.hotelbooking.core.ui.EmeraldPrimary
import com.innly.hotelbooking.core.ui.InnlyTheme
import com.innly.hotelbooking.domain.model.Hotel

@Composable
fun FavoritesScreen(
    onHotelClick: (String) -> Unit,
    onExploreClick: () -> Unit = {},
    onSignInClick: () -> Unit = {},
    onRegisterClick: () -> Unit = {},
    onContinueBrowsingClick: () -> Unit = {},
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // One-shot Undo event handling with event ID validation
    LaunchedEffect(state.undoEvent?.eventId) {
        val currentEvent = state.undoEvent ?: return@LaunchedEffect

        val result = snackbarHostState.showSnackbar(
            message = currentEvent.message,
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
        )

        when (result) {
            SnackbarResult.ActionPerformed -> {
                viewModel.undoRemoveFavorite(
                    eventId = currentEvent.eventId,
                    hotel = currentEvent.hotel,
                    originalIndex = currentEvent.originalIndex,
                )
            }
            SnackbarResult.Dismissed -> {
                viewModel.onSnackbarDismissed(currentEvent.eventId)
            }
        }
    }

    DisposableEffect(state.activeRemovalEventId) {
        onDispose {
            viewModel.onScreenDisposed(state.activeRemovalEventId)
        }
    }

    FavoritesContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onHotelClick = onHotelClick,
        onFavoriteClick = { hotel -> viewModel.removeFavorite(hotel) },
        onExploreClick = onExploreClick,
        onSignInClick = onSignInClick,
        onRegisterClick = onRegisterClick,
        onContinueBrowsingClick = onContinueBrowsingClick,
        onRetryClick = { viewModel.loadFavorites() },
        onDismissError = { viewModel.clearErrorMessage() },
    )
}

@Composable
fun FavoritesContent(
    state: FavoritesUiState,
    snackbarHostState: SnackbarHostState,
    onHotelClick: (String) -> Unit,
    onFavoriteClick: (Hotel) -> Unit,
    onExploreClick: () -> Unit,
    onSignInClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onContinueBrowsingClick: () -> Unit,
    onRetryClick: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            // Priority 1: Session Resolving State
            if (!state.isAuthResolved) {
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
            // Priority 2: Unauthenticated Guest State
            else if (!state.isAuthenticated) {
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
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(42.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Save Your Favorite Stays",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Sign in to save hotels and access your favorites across all devices.",
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
            // Priority 3: Initial Data Loading
            else if (state.isLoading && state.hotels.isEmpty()) {
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
                            text = "Loading your saved hotels...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Priority 4: Full-Screen Error State
            else if (state.errorMessage != null && state.hotels.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Unable to Load Favorites",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    PrimaryButton(
                        text = "Retry",
                        onClick = onRetryClick,
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .heightIn(min = 48.dp),
                    )
                }
            }
            // Priority 5: True Empty Favorites State
            else if (state.hotels.isEmpty()) {
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
                                imageVector = Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(42.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "No favorites saved yet",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Explore luxury resorts, boutique hotels, and heritage stays across India and tap the heart icon to save them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        lineHeight = 22.sp,
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    PrimaryButton(
                        text = "Explore Hotels",
                        onClick = onExploreClick,
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .heightIn(min = 48.dp),
                    )
                }
            }
            // Priority 6: Populated Favorites List
            else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "SAVED RETREATS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 2.2.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Favorites",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                                    ),
                                ) {
                                    Text(
                                        text = "${state.hotels.size} saved",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Inline Error Banner if background refresh or operation fails with loaded data
                    state.errorMessage?.let { err ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.88f)),
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
                                    TextButton(onClick = onDismissError) {
                                        Text("Dismiss", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    items(state.hotels, key = { it.id }) { hotel ->
                        HotelCard(
                            hotel = hotel,
                            onClick = { onHotelClick(hotel.id) },
                            onFavoriteClick = { onFavoriteClick(hotel) },
                            favoriteEnabled = !state.isMutationInProgress && !state.isLoading && !state.isRefreshing,
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FavoritesScreenPopulatedPreview() {
    InnlyTheme {
        FavoritesContent(
            state = FavoritesUiState(
                isAuthResolved = true,
                isAuthenticated = true,
                isLoading = false,
                hotels = listOf(
                    Hotel(
                        id = "1",
                        name = "Cedar Peak Retreat",
                        description = "Luxury mountain stay",
                        city = "Manali",
                        address = "Old Manali Road",
                        rating = 4.8,
                        reviewCount = 120,
                        startingPrice = 6100,
                        thumbnailUrl = "",
                        images = emptyList(),
                        amenities = emptyList(),
                        isFavorite = true,
                    ),
                ),
            ),
            snackbarHostState = SnackbarHostState(),
            onHotelClick = {},
            onFavoriteClick = {},
            onExploreClick = {},
            onSignInClick = {},
            onRegisterClick = {},
            onContinueBrowsingClick = {},
            onRetryClick = {},
            onDismissError = {},
        )
    }
}
