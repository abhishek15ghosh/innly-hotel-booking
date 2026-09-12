package com.innly.hotelbooking.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.innly.hotelbooking.presentation.auth.LoginScreen
import com.innly.hotelbooking.presentation.auth.RegisterScreen
import com.innly.hotelbooking.presentation.booking.BookingConfirmationScreen
import com.innly.hotelbooking.presentation.booking.BookingHistoryScreen
import com.innly.hotelbooking.presentation.booking.BookingScreen
import com.innly.hotelbooking.presentation.booking.PaymentScreen
import com.innly.hotelbooking.presentation.favorites.FavoritesScreen
import com.innly.hotelbooking.presentation.home.HomeScreen
import com.innly.hotelbooking.presentation.hoteldetail.HotelDetailScreen
import com.innly.hotelbooking.presentation.onboarding.OnboardingScreen
import com.innly.hotelbooking.presentation.profile.ProfileScreen
import com.innly.hotelbooking.presentation.reviews.ReviewsScreen
import com.innly.hotelbooking.presentation.roomdetail.RoomDetailScreen
import com.innly.hotelbooking.presentation.search.SearchScreen

@Composable
fun InnlyNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppDestinations.Onboarding,
        modifier = modifier,
    ) {
        composable(AppDestinations.Onboarding) {
            OnboardingScreen(
                onAnimationFinished = {
                    navController.navigate(AppDestinations.Home) {
                        popUpTo(AppDestinations.Onboarding) { inclusive = true }
                    }
                },
            )
        }
        composable(AppDestinations.Login) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(AppDestinations.Home) {
                        popUpTo(AppDestinations.Home) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onRegisterClick = { navController.navigate(AppDestinations.Register) },
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(AppDestinations.Register) {
            RegisterScreen(
                onRegistered = {
                    navController.navigate(AppDestinations.Home) {
                        popUpTo(AppDestinations.Home) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onLoginClick = { navController.popBackStack() },
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(AppDestinations.Home) {
            HomeScreen(
                onSearchClick = { navController.navigateToBottomTab(AppDestinations.Search) },
                onHotelClick = { hotelId -> navController.navigate(AppDestinations.hotelDetail(hotelId)) },
                onHistoryClick = { navController.navigateToBottomTab(AppDestinations.BookingHistory) },
                onFavoritesClick = { navController.navigateToBottomTab(AppDestinations.Favorites) },
                onProfileClick = { navController.navigateToBottomTab(AppDestinations.Profile) },
            )
        }
        composable(AppDestinations.Search) {
            SearchScreen(
                onHotelClick = { hotelId -> navController.navigate(AppDestinations.hotelDetail(hotelId)) },
            )
        }
        composable(
            route = AppDestinations.HotelDetail,
            arguments = listOf(navArgument("hotelId") { type = NavType.StringType }),
        ) { entry ->
            val reviewSubmittedFlow = entry.savedStateHandle.getStateFlow("review_submitted", false)
            val reviewSubmitted by reviewSubmittedFlow.collectAsStateWithLifecycle()

            HotelDetailScreen(
                hotelId = entry.arguments?.getString("hotelId").orEmpty(),
                reviewSubmitted = reviewSubmitted,
                onReviewSubmittedConsumed = {
                    entry.savedStateHandle["review_submitted"] = false
                },
                onRoomClick = { hotelId, roomId -> navController.navigate(AppDestinations.roomDetail(hotelId, roomId)) },
                onBookRoom = { hotelId, roomId -> navController.navigate(AppDestinations.booking(hotelId, roomId)) },
                onReviewsClick = { hotelId -> navController.navigate(AppDestinations.reviews(hotelId)) },
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(
            route = AppDestinations.RoomDetail,
            arguments = listOf(
                navArgument("hotelId") { type = NavType.StringType },
                navArgument("roomId") { type = NavType.StringType },
            ),
        ) { entry ->
            RoomDetailScreen(
                hotelId = entry.arguments?.getString("hotelId").orEmpty(),
                roomId = entry.arguments?.getString("roomId").orEmpty(),
                onBookNow = { hotelId, roomId -> navController.navigate(AppDestinations.booking(hotelId, roomId)) },
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(
            route = AppDestinations.Booking,
            arguments = listOf(
                navArgument("hotelId") { type = NavType.StringType },
                navArgument("roomId") { type = NavType.StringType },
            ),
        ) { entry ->
            BookingScreen(
                hotelId = entry.arguments?.getString("hotelId").orEmpty(),
                roomId = entry.arguments?.getString("roomId").orEmpty(),
                onPaymentReady = { bookingId, orderId, razorpayAmount, amount, currency ->
                    navController.navigate(
                        AppDestinations.payment(
                            bookingId = bookingId,
                            razorpayOrderId = orderId,
                            razorpayAmount = razorpayAmount,
                            displayAmount = amount,
                            currency = currency,
                        ),
                    )
                },
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(
            route = AppDestinations.Payment,
            arguments = listOf(
                navArgument("bookingId") { type = NavType.StringType },
                navArgument("razorpayOrderId") { type = NavType.StringType },
                navArgument("razorpayAmount") { type = NavType.IntType },
                navArgument("displayAmount") { type = NavType.IntType },
                navArgument("currency") { type = NavType.StringType },
            ),
        ) { entry ->
            val bookingId = entry.arguments?.getString("bookingId").orEmpty()
            val displayAmount = entry.arguments?.getInt("displayAmount") ?: 0
            val currency = entry.arguments?.getString("currency").orEmpty()

            PaymentScreen(
                bookingId = bookingId,
                razorpayOrderId = entry.arguments?.getString("razorpayOrderId").orEmpty(),
                razorpayAmount = entry.arguments?.getInt("razorpayAmount") ?: 0,
                displayAmount = displayAmount,
                currency = currency,
                onPaymentVerified = {
                    navController.navigate(
                        AppDestinations.confirmation(
                            bookingId = bookingId,
                            displayAmount = displayAmount,
                            currency = currency,
                        ),
                    ) {
                        popUpTo(AppDestinations.Home) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = AppDestinations.Confirmation,
            arguments = listOf(
                navArgument("bookingId") { type = NavType.StringType },
                navArgument("displayAmount") { type = NavType.IntType },
                navArgument("currency") { type = NavType.StringType },
            ),
        ) { entry ->
            BookingConfirmationScreen(
                bookingId = entry.arguments?.getString("bookingId").orEmpty(),
                displayAmount = entry.arguments?.getInt("displayAmount") ?: 0,
                currency = entry.arguments?.getString("currency").orEmpty(),
                onViewBookings = {
                    navController.navigateToBottomTab(AppDestinations.BookingHistory)
                },
                onBackToExplore = {
                    navController.navigateToBottomTab(AppDestinations.Home)
                },
            )
        }
        composable(AppDestinations.BookingHistory) {
            BookingHistoryScreen(
                onExploreClick = {
                    navController.navigateToBottomTab(AppDestinations.Home)
                },
                onSignInClick = { navController.navigate(AppDestinations.Login) },
                onRegisterClick = { navController.navigate(AppDestinations.Register) },
                onContinueBrowsingClick = {
                    navController.navigateToBottomTab(AppDestinations.Home)
                },
            )
        }
        composable(AppDestinations.Favorites) {
            FavoritesScreen(
                onHotelClick = { hotelId -> navController.navigate(AppDestinations.hotelDetail(hotelId)) },
                onExploreClick = {
                    navController.navigateToBottomTab(AppDestinations.Home)
                },
                onSignInClick = { navController.navigate(AppDestinations.Login) },
                onRegisterClick = { navController.navigate(AppDestinations.Register) },
                onContinueBrowsingClick = {
                    navController.navigateToBottomTab(AppDestinations.Home)
                },
            )
        }
        composable(AppDestinations.Profile) {
            ProfileScreen(
                onSignedOut = {
                    navController.navigateToBottomTab(AppDestinations.Home)
                },
                onSignInClick = { navController.navigate(AppDestinations.Login) },
                onRegisterClick = { navController.navigate(AppDestinations.Register) },
                onViewBookingsClick = { navController.navigateToBottomTab(AppDestinations.BookingHistory) },
                onViewFavoritesClick = { navController.navigateToBottomTab(AppDestinations.Favorites) },
                onContinueBrowsingClick = {
                    navController.navigateToBottomTab(AppDestinations.Home)
                },
            )
        }
        composable(
            route = AppDestinations.Reviews,
            arguments = listOf(navArgument("hotelId") { type = NavType.StringType }),
        ) { entry ->
            ReviewsScreen(
                hotelId = entry.arguments?.getString("hotelId").orEmpty(),
                onBackClick = { navController.popBackStack() },
                onReviewSubmitted = {
                    navController.previousBackStackEntry?.savedStateHandle?.set("review_submitted", true)
                    navController.popBackStack()
                },
            )
        }
    }
}
