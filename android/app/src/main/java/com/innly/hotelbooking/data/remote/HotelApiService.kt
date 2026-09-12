package com.innly.hotelbooking.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface HotelApiService {
    @POST("auth/sync")
    suspend fun syncProfile(
        @Body request: ProfileSyncRequest,
    ): ApiResponse<UserProfileDto>

    @GET("hotels")
    suspend fun getHotels(
        @Query("city") city: String? = null,
        @Query("minPrice") minPrice: Int? = null,
        @Query("maxPrice") maxPrice: Int? = null,
        @Query("minRating") minRating: Double? = null,
        @Query("amenities") amenities: String? = null,
        @Query("limit") limit: Int? = 50,
    ): ApiResponse<PaginatedPayload<HotelDto>>

    @GET("hotels/{hotelId}")
    suspend fun getHotelDetail(
        @Path("hotelId") hotelId: String,
    ): ApiResponse<HotelDto>

    @GET("hotels/{hotelId}/availability")
    suspend fun getAvailability(
        @Path("hotelId") hotelId: String,
        @Query("roomId") roomId: String,
        @Query("checkIn") checkIn: String,
        @Query("checkOut") checkOut: String,
        @Query("rooms") rooms: Int,
    ): ApiResponse<AvailabilityDto>

    @GET("favorites")
    suspend fun getFavorites(): ApiResponse<List<HotelDto>>

    @POST("favorites/{hotelId}")
    suspend fun addFavorite(
        @Path("hotelId") hotelId: String,
    ): ApiResponse<Unit>

    @DELETE("favorites/{hotelId}")
    suspend fun removeFavorite(
        @Path("hotelId") hotelId: String,
    ): ApiResponse<Unit>

    @GET("hotels/{hotelId}/reviews")
    suspend fun getHotelReviews(
        @Path("hotelId") hotelId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 10,
    ): ApiResponse<ReviewListResponseDto>

    @GET("hotels/{hotelId}/my-review")
    suspend fun getMyReview(
        @Path("hotelId") hotelId: String,
    ): ApiResponse<MyReviewResponseDto>

    @POST("reviews")
    suspend fun submitReview(
        @Body request: ReviewRequestDto,
    ): ApiResponse<PrivateReviewDto>

    @POST("bookings")
    suspend fun createBooking(
        @Body request: BookingRequestDto,
    ): ApiResponse<CreateBookingResponseDto>

    @GET("bookings")
    suspend fun getBookingHistory(): ApiResponse<List<BookingDto>>

    @POST("payments/verify")
    suspend fun verifyPayment(
        @Body request: PaymentVerificationRequestDto,
    ): ApiResponse<PaymentVerificationResponseDto>

    @POST("bookings/{bookingId}/cancel")
    suspend fun cancelBooking(
        @Path("bookingId") bookingId: String,
        @Body request: Map<String, String>,
    ): ApiResponse<CancellationResponseDto>
}
