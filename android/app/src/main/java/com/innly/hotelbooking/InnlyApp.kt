package com.innly.hotelbooking

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.innly.hotelbooking.presentation.navigation.AppDestinations
import com.innly.hotelbooking.presentation.navigation.BottomNavItems
import com.innly.hotelbooking.presentation.navigation.InnlyBottomNavigation
import com.innly.hotelbooking.presentation.navigation.InnlyNavGraph
import com.innly.hotelbooking.presentation.navigation.navigateToBottomTab

@Composable
fun InnlyApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in BottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                InnlyBottomNavigation(
                    currentRoute = currentRoute,
                    onNavigateToDestination = { route ->
                        navController.navigateToBottomTab(route)
                    },
                )
            }
        },
    ) { innerPadding ->
        InnlyNavGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
