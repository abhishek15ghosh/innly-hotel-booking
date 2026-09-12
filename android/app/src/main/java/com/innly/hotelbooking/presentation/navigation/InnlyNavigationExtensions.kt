package com.innly.hotelbooking.presentation.navigation

import androidx.navigation.NavController

/**
 * Centralized deterministic navigation helper for Innly bottom-level destinations.
 *
 * Guarantees:
 * 1. Selecting the currently active tab performs no operation (avoids duplicate navigation and state churn).
 * 2. Navigating to Explore (AppDestinations.Home) always finishes on Home by popping any top-level tab.
 * 3. Navigating to any other bottom tab (Search, Bookings, Favorites, Profile) pops up to Home (inclusive = false)
 *    and pushes the destination as single top, maintaining a predictable [Home, Tab] stack.
 * 4. Eliminates ambiguous saveState/restoreState races with the Onboarding-root graph.
 */
fun NavController.navigateToBottomTab(targetRoute: String) {
    val currentRoute = currentBackStackEntry?.destination?.route
    if (currentRoute == targetRoute) return

    if (targetRoute == AppDestinations.Home) {
        val popped = popBackStack(AppDestinations.Home, inclusive = false)
        if (!popped) {
            navigate(AppDestinations.Home) {
                popUpTo(AppDestinations.Home) { inclusive = true }
                launchSingleTop = true
            }
        }
    } else {
        navigate(targetRoute) {
            popUpTo(AppDestinations.Home) {
                inclusive = false
            }
            launchSingleTop = true
        }
    }
}
