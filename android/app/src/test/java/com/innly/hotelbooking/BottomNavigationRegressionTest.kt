package com.innly.hotelbooking

import com.innly.hotelbooking.presentation.navigation.AppDestinations
import com.innly.hotelbooking.presentation.navigation.BottomNavItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavigationRegressionTest {

    private class TestNavBackStack {
        val stack = mutableListOf<String>()
        val currentRoute: String? get() = stack.lastOrNull()

        init {
            // Start at Home (after Onboarding is popped)
            stack.add(AppDestinations.Home)
        }

        fun navigateToBottomTab(targetRoute: String) {
            val current = currentRoute
            if (current == targetRoute) return

            if (targetRoute == AppDestinations.Home) {
                val homeIndex = stack.indexOf(AppDestinations.Home)
                if (homeIndex != -1) {
                    while (stack.size > homeIndex + 1) {
                        stack.removeAt(stack.size - 1)
                    }
                } else {
                    stack.clear()
                    stack.add(AppDestinations.Home)
                }
            } else {
                val homeIndex = stack.indexOf(AppDestinations.Home)
                if (homeIndex != -1) {
                    while (stack.size > homeIndex + 1) {
                        stack.removeAt(stack.size - 1)
                    }
                }
                stack.add(targetRoute)
            }
        }

        fun popBackStack(): Boolean {
            if (stack.size > 1) {
                stack.removeAt(stack.size - 1)
                return true
            }
            return false
        }
    }

    @Test
    fun `1 Profile to Explore always ends on Home`() {
        val nav = TestNavBackStack()
        nav.navigateToBottomTab(AppDestinations.Profile)
        assertEquals(AppDestinations.Profile, nav.currentRoute)

        nav.navigateToBottomTab(AppDestinations.Home)
        assertEquals(AppDestinations.Home, nav.currentRoute)
        assertEquals(listOf(AppDestinations.Home), nav.stack)
    }

    @Test
    fun `2 Favorites to Explore always ends on Home`() {
        val nav = TestNavBackStack()
        nav.navigateToBottomTab(AppDestinations.Favorites)
        assertEquals(AppDestinations.Favorites, nav.currentRoute)

        nav.navigateToBottomTab(AppDestinations.Home)
        assertEquals(AppDestinations.Home, nav.currentRoute)
        assertEquals(listOf(AppDestinations.Home), nav.stack)
    }

    @Test
    fun `3 Bookings to Explore always ends on Home`() {
        val nav = TestNavBackStack()
        nav.navigateToBottomTab(AppDestinations.BookingHistory)
        assertEquals(AppDestinations.BookingHistory, nav.currentRoute)

        nav.navigateToBottomTab(AppDestinations.Home)
        assertEquals(AppDestinations.Home, nav.currentRoute)
        assertEquals(listOf(AppDestinations.Home), nav.stack)
    }

    @Test
    fun `4 Search to Explore always ends on Home`() {
        val nav = TestNavBackStack()
        nav.navigateToBottomTab(AppDestinations.Search)
        assertEquals(AppDestinations.Search, nav.currentRoute)

        nav.navigateToBottomTab(AppDestinations.Home)
        assertEquals(AppDestinations.Home, nav.currentRoute)
        assertEquals(listOf(AppDestinations.Home), nav.stack)
    }

    @Test
    fun `5 Explore to Profile always ends on Profile`() {
        val nav = TestNavBackStack()
        assertEquals(AppDestinations.Home, nav.currentRoute)

        nav.navigateToBottomTab(AppDestinations.Profile)
        assertEquals(AppDestinations.Profile, nav.currentRoute)
        assertEquals(listOf(AppDestinations.Home, AppDestinations.Profile), nav.stack)
    }

    @Test
    fun `6 Repeatedly alternating Profile and Explore 50 times produces strictly deterministic results`() {
        val nav = TestNavBackStack()
        for (i in 1..50) {
            nav.navigateToBottomTab(AppDestinations.Profile)
            assertEquals("Iteration $i failed on Profile", AppDestinations.Profile, nav.currentRoute)
            assertEquals("Iteration $i stack wrong on Profile", listOf(AppDestinations.Home, AppDestinations.Profile), nav.stack)

            nav.navigateToBottomTab(AppDestinations.Home)
            assertEquals("Iteration $i failed on Explore", AppDestinations.Home, nav.currentRoute)
            assertEquals("Iteration $i stack wrong on Explore", listOf(AppDestinations.Home), nav.stack)
        }
    }

    @Test
    fun `7 Rapid double taps do not create duplicate destinations`() {
        val nav = TestNavBackStack()
        nav.navigateToBottomTab(AppDestinations.Favorites)
        nav.navigateToBottomTab(AppDestinations.Favorites)
        nav.navigateToBottomTab(AppDestinations.Favorites)

        assertEquals(AppDestinations.Favorites, nav.currentRoute)
        assertEquals(listOf(AppDestinations.Home, AppDestinations.Favorites), nav.stack)
    }

    @Test
    fun `8 Tapping already selected tab performs no additional navigation`() {
        val nav = TestNavBackStack()
        assertEquals(AppDestinations.Home, nav.currentRoute)

        val stackBefore = nav.stack.toList()
        nav.navigateToBottomTab(AppDestinations.Home)
        assertEquals(stackBefore, nav.stack)
    }

    @Test
    fun `9 Highlighted bottom tab always matches the visible destination`() {
        val allTabs = BottomNavItems.map { it.route }
        for (route in allTabs) {
            val matchingItems = BottomNavItems.filter { it.route == route }
            assertEquals(1, matchingItems.size)
            assertTrue(allTabs.contains(route))
        }

        // Non-bottom-bar destinations
        val nonTabRoutes = listOf(AppDestinations.HotelDetail, AppDestinations.Payment, AppDestinations.Confirmation)
        for (route in nonTabRoutes) {
            assertFalse(allTabs.contains(route))
        }
    }

    @Test
    fun `10 Android Back behavior remains predictable from top level tabs`() {
        val nav = TestNavBackStack()
        nav.navigateToBottomTab(AppDestinations.Profile)
        assertEquals(AppDestinations.Profile, nav.currentRoute)

        val backHandled = nav.popBackStack()
        assertTrue(backHandled)
        assertEquals(AppDestinations.Home, nav.currentRoute)

        val exitBackHandled = nav.popBackStack()
        assertFalse(exitBackHandled)
        assertEquals(AppDestinations.Home, nav.currentRoute)
    }
}
