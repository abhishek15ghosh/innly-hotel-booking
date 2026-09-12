package com.innly.hotelbooking.presentation.booking

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.innly.hotelbooking.core.designsystem.components.PrimaryButton
import com.innly.hotelbooking.core.ui.ChampagneGold
import com.innly.hotelbooking.core.ui.EmeraldPrimary
import com.innly.hotelbooking.core.ui.InnlyTheme
import com.innly.hotelbooking.core.ui.StatusCancelledNeutral
import com.innly.hotelbooking.core.ui.StatusErrorRed
import com.innly.hotelbooking.core.ui.StatusPendingAmber
import com.innly.hotelbooking.core.ui.StatusSuccessGreen
import com.innly.hotelbooking.domain.model.Booking

enum class BookingFilterTab {
    ALL,
    ACTIVE,
    COMPLETED,
    CANCELLED
}

@Composable
fun BookingHistoryScreen(
    onExploreClick: () -> Unit = {},
    onSignInClick: () -> Unit = {},
    onRegisterClick: () -> Unit = {},
    onContinueBrowsingClick: () -> Unit = {},
    viewModel: BookingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedBookingForCancel by remember { mutableStateOf<Booking?>(null) }
    var selectedFilter by remember { mutableStateOf(BookingFilterTab.ALL) }

    LaunchedEffect(state.isAuthResolved, state.isGuest) {
        if (state.isAuthResolved && !state.isGuest && state.bookingHistory.isEmpty() && !state.isLoading && state.errorMessage == null) {
            viewModel.loadBookingHistory()
        }
    }

    BookingHistoryContent(
        bookingHistory = state.bookingHistory,
        isAuthResolved = state.isAuthResolved,
        isGuest = state.isGuest,
        isLoading = state.isLoading,
        isCancelling = state.isCancelling,
        errorMessage = state.errorMessage,
        successMessage = state.successMessage,
        selectedFilter = selectedFilter,
        onFilterSelected = { selectedFilter = it },
        onRetryClick = { viewModel.loadBookingHistory() },
        onExploreClick = onExploreClick,
        onSignInClick = onSignInClick,
        onRegisterClick = onRegisterClick,
        onContinueBrowsingClick = onContinueBrowsingClick,
        onCancelBookingClick = { booking -> selectedBookingForCancel = booking },
    )

    selectedBookingForCancel?.let { booking ->
        CancellationDialog(
            booking = booking,
            isCancelling = state.isCancelling,
            onDismiss = {
                if (!state.isCancelling) selectedBookingForCancel = null
            },
            onConfirmCancellation = { reason ->
                viewModel.cancelBooking(booking.id, reason) {
                    selectedBookingForCancel = null
                }
            },
        )
    }
}

@Composable
fun BookingHistoryContent(
    bookingHistory: List<Booking>,
    isAuthResolved: Boolean = true,
    isGuest: Boolean = false,
    isLoading: Boolean = false,
    isCancelling: Boolean = false,
    errorMessage: String? = null,
    successMessage: String? = null,
    selectedFilter: BookingFilterTab = BookingFilterTab.ALL,
    onFilterSelected: (BookingFilterTab) -> Unit = {},
    onRetryClick: () -> Unit = {},
    onExploreClick: () -> Unit = {},
    onSignInClick: () -> Unit = {},
    onRegisterClick: () -> Unit = {},
    onContinueBrowsingClick: () -> Unit = {},
    onCancelBookingClick: (Booking) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val filteredBookings = remember(bookingHistory, selectedFilter) {
        BookingHistoryFormatter.filterBookings(bookingHistory, selectedFilter)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Page Header
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "My Bookings",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Manage your upcoming stays, view receipts, and track refunds.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // --- STRICT SCREEN STATE PRIORITIZATION ---

        // State Priority 1: Resolving Initial Auth Session
        if (!isAuthResolved) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
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
        else if (isGuest) {
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
                    modifier = Modifier.size(80.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Luggage,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Sign in to view your bookings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Access your reservations, check-in dates, receipts, and cancellation details in one place.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )

                Spacer(modifier = Modifier.height(24.dp))

                PrimaryButton(
                    text = "Sign In",
                    onClick = onSignInClick,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(48.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onRegisterClick,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Text("Create Account", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onContinueBrowsingClick,
                    modifier = Modifier.height(48.dp),
                ) {
                    Text("Continue Browsing", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        else {
            // Filter Tabs Row (Horizontally Scrollable for all screen sizes)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val chipShape = RoundedCornerShape(12.dp)
                val chipBorder = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )

                FilterChip(
                    selected = selectedFilter == BookingFilterTab.ALL,
                    onClick = { onFilterSelected(BookingFilterTab.ALL) },
                    label = { Text("All (${bookingHistory.size})", fontWeight = if (selectedFilter == BookingFilterTab.ALL) FontWeight.Bold else FontWeight.Normal) },
                    shape = chipShape,
                    border = if (selectedFilter == BookingFilterTab.ALL) null else chipBorder,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                FilterChip(
                    selected = selectedFilter == BookingFilterTab.ACTIVE,
                    onClick = { onFilterSelected(BookingFilterTab.ACTIVE) },
                    label = {
                        val activeCount = BookingHistoryFormatter.filterBookings(bookingHistory, BookingFilterTab.ACTIVE).size
                        Text("Active ($activeCount)", fontWeight = if (selectedFilter == BookingFilterTab.ACTIVE) FontWeight.Bold else FontWeight.Normal)
                    },
                    shape = chipShape,
                    border = if (selectedFilter == BookingFilterTab.ACTIVE) null else chipBorder,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                FilterChip(
                    selected = selectedFilter == BookingFilterTab.COMPLETED,
                    onClick = { onFilterSelected(BookingFilterTab.COMPLETED) },
                    label = {
                        val completedCount = BookingHistoryFormatter.filterBookings(bookingHistory, BookingFilterTab.COMPLETED).size
                        Text("Completed ($completedCount)", fontWeight = if (selectedFilter == BookingFilterTab.COMPLETED) FontWeight.Bold else FontWeight.Normal)
                    },
                    shape = chipShape,
                    border = if (selectedFilter == BookingFilterTab.COMPLETED) null else chipBorder,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                FilterChip(
                    selected = selectedFilter == BookingFilterTab.CANCELLED,
                    onClick = { onFilterSelected(BookingFilterTab.CANCELLED) },
                    label = {
                        val cancelledCount = BookingHistoryFormatter.filterBookings(bookingHistory, BookingFilterTab.CANCELLED).size
                        Text("Cancelled ($cancelledCount)", fontWeight = if (selectedFilter == BookingFilterTab.CANCELLED) FontWeight.Bold else FontWeight.Normal)
                    },
                    shape = chipShape,
                    border = if (selectedFilter == BookingFilterTab.CANCELLED) null else chipBorder,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }

            // Success Feedback Banner
            successMessage?.let { success ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = success,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            // State Priority 3: Loading Initial State
            if (isLoading && bookingHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Loading your bookings...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // State Priority 4: Error State when History is Empty (Dedicated Error State with Retry)
            else if (errorMessage != null && bookingHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.size(64.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Loading Error",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                        Text(
                            text = "Unable to Load Bookings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        OutlinedButton(
                            onClick = onRetryClick,
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Retry Loading", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            // State Priority 5: True Empty State (Empty History from successful request)
            else if (bookingHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ChampagneGold.copy(alpha = 0.20f),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = ChampagneGold.copy(alpha = 0.35f),
                            ),
                            modifier = Modifier.size(64.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Luggage,
                                    contentDescription = null,
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                        Text(
                            text = "No Bookings Yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "When you book a stay at any of our hotels, your reservations will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        PrimaryButton(
                            text = "Explore Hotels",
                            onClick = onExploreClick,
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(48.dp),
                        )
                    }
                }
            }
            // State Priority 6: Populated History Content (LazyColumn with Filtered Bookings)
            else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (filteredBookings.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                val emptyText = when (selectedFilter) {
                                    BookingFilterTab.ALL -> "No bookings found."
                                    BookingFilterTab.ACTIVE -> "No active bookings found."
                                    BookingFilterTab.COMPLETED -> "No completed stays yet. Your finished trips will appear here."
                                    BookingFilterTab.CANCELLED -> "No cancelled bookings found."
                                }
                                Text(
                                    text = emptyText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                )
                            }
                        }
                    } else {
                        items(filteredBookings, key = { it.id }) { booking ->
                            BookingHistoryCard(
                                booking = booking,
                                isCancelling = isCancelling,
                                onCancelClick = { onCancelBookingClick(booking) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookingHistoryCard(
    booking: Booking,
    isCancelling: Boolean,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val formattedAmount = BookingFormValidator.formatCurrency(booking.currency, booking.amount)
    val cleanCheckIn = booking.checkIn.split("T")[0]
    val cleanCheckOut = booking.checkOut.split("T")[0]
    val compactId = BookingHistoryFormatter.formatCompactBookingId(booking.id)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header Row: Hotel Name + Booking Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Hotel,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Text(
                        text = booking.hotelName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                BookingStatusBadge(
                    status = booking.status,
                    checkIn = booking.checkIn,
                    checkOut = booking.checkOut,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Booking Reference Row with Compact ID and Full-ID Copy Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Booking Reference",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        ),
                    ) {
                        Text(
                            text = compactId,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }

                IconButton(
                    onClick = {
                        // Always copy the complete original booking ID
                        clipboardManager.setText(AnnotatedString(booking.id))
                        Toast.makeText(context, "Booking ID copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy booking reference: ${booking.id}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Room and Stay Dates
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MeetingRoom,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = booking.roomName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = BookingHistoryFormatter.formatFriendlyDateRange(booking.checkIn, booking.checkOut, " → "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Total Amount Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Total Amount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formattedAmount,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Separate Refund / Policy Status Section
            RefundStatusBanner(booking = booking)

            // Cancel Booking Action (Authoritative Guard: booking.canCancel == true AND active category)
            val displayCategory = BookingHistoryFormatter.getDisplayCategory(booking)
            if (BookingHistoryFormatter.shouldShowCancelAction(booking, displayCategory)) {
                OutlinedButton(
                    onClick = onCancelClick,
                    enabled = !isCancelling,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = StatusErrorRed,
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = !isCancelling).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(StatusErrorRed.copy(alpha = 0.5f)),
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        tint = StatusErrorRed,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (booking.status.equals("payment_pending", ignoreCase = true)) "Cancel Pending Order" else "Cancel Booking",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
fun BookingStatusBadge(
    status: String,
    checkIn: String? = null,
    checkOut: String? = null,
    modifier: Modifier = Modifier,
) {
    val statusInfo = BookingHistoryFormatter.mapBookingStatus(status, checkIn, checkOut)
    val (bgColor, textColor, icon) = when (statusInfo.type) {
        BookingStatusType.CONFIRMED,
        BookingStatusType.UPCOMING -> Triple(
            StatusSuccessGreen.copy(alpha = 0.14f),
            StatusSuccessGreen,
            Icons.Default.Check,
        )
        BookingStatusType.CURRENTLY_STAYING -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary,
            Icons.Default.Hotel,
        )
        BookingStatusType.COMPLETED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.CheckCircle,
        )
        BookingStatusType.PAYMENT_PENDING -> Triple(
            StatusPendingAmber.copy(alpha = 0.14f),
            StatusPendingAmber,
            Icons.Default.Pending,
        )
        BookingStatusType.CANCELLATION_PENDING -> Triple(
            StatusPendingAmber.copy(alpha = 0.14f),
            StatusPendingAmber,
            Icons.Default.Refresh,
        )
        BookingStatusType.CANCELLED -> Triple(
            StatusCancelledNeutral.copy(alpha = 0.14f),
            StatusCancelledNeutral,
            Icons.Default.Cancel,
        )
        BookingStatusType.UNKNOWN -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.Info,
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = statusInfo.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = textColor,
            )
        }
    }
}

@Composable
fun RefundStatusBanner(booking: Booking, modifier: Modifier = Modifier) {
    val refundInfo = BookingHistoryFormatter.mapRefundStatus(
        refundStatus = booking.refundStatus,
        bookingStatus = booking.status,
        displayMessage = booking.displayMessage,
        canCancel = booking.canCancel,
        cancellationDeadline = booking.cancellationDeadline,
    ) ?: return

    val (bgColor, textColor, icon) = when (refundInfo.type) {
        RefundPresentationType.PROCESSED -> Triple(
            StatusSuccessGreen.copy(alpha = 0.12f),
            StatusSuccessGreen,
            Icons.Default.Check,
        )
        RefundPresentationType.PENDING -> Triple(
            StatusPendingAmber.copy(alpha = 0.12f),
            StatusPendingAmber,
            Icons.Default.Schedule,
        )
        RefundPresentationType.FAILED -> Triple(
            StatusErrorRed.copy(alpha = 0.12f),
            StatusErrorRed,
            Icons.Default.ErrorOutline,
        )
        RefundPresentationType.CANCELLED_NO_REFUND -> Triple(
            StatusCancelledNeutral.copy(alpha = 0.12f),
            StatusCancelledNeutral,
            Icons.Default.Info,
        )
        RefundPresentationType.POLICY_INFO -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.Info,
        )
        RefundPresentationType.NEUTRAL_FALLBACK -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.Info,
        )
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = refundInfo.message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (refundInfo.type == RefundPresentationType.PROCESSED || refundInfo.type == RefundPresentationType.PENDING || refundInfo.type == RefundPresentationType.FAILED) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
            )
        }
    }
}

@Composable
fun CancellationDialog(
    booking: Booking,
    isCancelling: Boolean,
    onDismiss: () -> Unit,
    onConfirmCancellation: (reason: String) -> Unit,
) {
    var cancellationReasonPreset by remember { mutableStateOf("Change of plans") }
    var customReason by remember { mutableStateOf("") }

    val presetOptions = listOf(
        "Change of plans",
        "Found a better hotel option",
        "Personal emergency",
        "Trip cancelled or rescheduled",
        "Other",
    )

    val selectedReason = if (cancellationReasonPreset == "Other") {
        customReason.trim()
    } else {
        cancellationReasonPreset
    }

    val isConfirmEnabled = !isCancelling && BookingHistoryFormatter.isCancellationReasonValid(selectedReason)

    AlertDialog(
        onDismissRequest = { if (!isCancelling) onDismiss() },
        shape = RoundedCornerShape(24.dp),
        icon = {
            Surface(
                shape = CircleShape,
                color = StatusErrorRed.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "Cancellation Warning",
                        tint = StatusErrorRed,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
        title = {
            Text(
                text = "Cancel Reservation?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Stay summary card
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = booking.hotelName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${booking.roomName} • ${BookingHistoryFormatter.formatFriendlyDateRange(booking.checkIn, booking.checkOut, " → ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        booking.cancellationDeadline?.let { deadline ->
                            Text(
                                text = "Free cancellation deadline: ${BookingHistoryFormatter.formatFriendlyDate(deadline)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Text(
                    text = "Select a reason for cancellation:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                Column(modifier = Modifier.selectableGroup()) {
                    presetOptions.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .selectable(
                                    selected = (cancellationReasonPreset == option),
                                    onClick = { cancellationReasonPreset = option },
                                    role = Role.RadioButton,
                                ),
                        ) {
                            RadioButton(
                                selected = (cancellationReasonPreset == option),
                                onClick = null,
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(text = option, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                if (cancellationReasonPreset == "Other") {
                    OutlinedTextField(
                        value = customReason,
                        onValueChange = { customReason = it },
                        label = { Text("Specify cancellation reason") },
                        supportingText = {
                            Text(
                                text = "${customReason.trim().length}/3 minimum characters",
                                color = if (customReason.trim().length >= 3) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            )
                        },
                        isError = customReason.isNotBlank() && customReason.trim().length < 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isConfirmEnabled,
                onClick = { onConfirmCancellation(selectedReason) },
                colors = ButtonDefaults.textButtonColors(contentColor = StatusErrorRed),
            ) {
                Text(
                    text = if (isCancelling) "Cancelling..." else "Confirm Cancellation",
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isCancelling,
                onClick = onDismiss,
            ) {
                Text("Keep Reservation")
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun BookingHistoryScreenPreview() {
    InnlyTheme {
        BookingHistoryContent(
            bookingHistory = listOf(
                Booking(
                    id = "b-101-long-reference-uuid-test",
                    hotelName = "Valley Crest Resort",
                    roomName = "Valley Room",
                    checkIn = "2026-08-16",
                    checkOut = "2026-08-18",
                    status = "confirmed",
                    amount = 13400,
                    currency = "INR",
                    canCancel = true,
                    cancellationDeadline = "2026-08-15 14:00",
                    displayMessage = "Free cancellation available until 2026-08-15",
                ),
                Booking(
                    id = "b-102-marine-grand-suite-test",
                    hotelName = "The Marine Grand",
                    roomName = "Deluxe King",
                    checkIn = "2026-08-20",
                    checkOut = "2026-08-22",
                    status = "cancelled",
                    amount = 11800,
                    currency = "INR",
                    canCancel = false,
                    refundStatus = "processed",
                    displayMessage = "Refund completed",
                ),
            ),
            isLoading = false,
            isCancelling = false,
            errorMessage = null,
            successMessage = null,
            selectedFilter = BookingFilterTab.ALL,
            onFilterSelected = {},
            onRetryClick = {},
            onExploreClick = {},
            onCancelBookingClick = {},
        )
    }
}
