package com.innly.hotelbooking.presentation.navigation

object AppDestinations {
    const val Onboarding = "onboarding"
    const val Login = "login"
    const val Register = "register"
    const val Home = "home"
    const val Search = "search"
    const val BookingHistory = "history"
    const val Favorites = "favorites"
    const val Profile = "profile"
    const val HotelDetail = "hotel/{hotelId}"
    const val RoomDetail = "room/{hotelId}/{roomId}"
    const val Booking = "booking/{hotelId}/{roomId}"
    const val Payment = "payment/{bookingId}/{razorpayOrderId}/{razorpayAmount}/{displayAmount}/{currency}"
    const val Confirmation = "confirmation/{bookingId}/{displayAmount}/{currency}"
    const val Reviews = "reviews/{hotelId}"

    fun hotelDetail(hotelId: String) = "hotel/$hotelId"
    fun roomDetail(hotelId: String, roomId: String) = "room/$hotelId/$roomId"
    fun booking(hotelId: String, roomId: String) = "booking/$hotelId/$roomId"
    fun payment(bookingId: String, razorpayOrderId: String, razorpayAmount: Int, displayAmount: Int, currency: String) =
        "payment/$bookingId/$razorpayOrderId/$razorpayAmount/$displayAmount/$currency"

    fun confirmation(bookingId: String, displayAmount: Int, currency: String) =
        "confirmation/$bookingId/$displayAmount/$currency"

    fun reviews(hotelId: String) = "reviews/$hotelId"
}
