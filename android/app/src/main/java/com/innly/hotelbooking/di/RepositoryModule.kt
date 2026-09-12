package com.innly.hotelbooking.di

import com.innly.hotelbooking.data.repository.AuthRepositoryImpl
import com.innly.hotelbooking.data.repository.BookingRepositoryImpl
import com.innly.hotelbooking.data.repository.HotelRepositoryImpl
import com.innly.hotelbooking.domain.repository.AuthRepository
import com.innly.hotelbooking.domain.repository.BookingRepository
import com.innly.hotelbooking.domain.repository.HotelRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl,
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindHotelRepository(
        impl: HotelRepositoryImpl,
    ): HotelRepository

    @Binds
    @Singleton
    abstract fun bindBookingRepository(
        impl: BookingRepositoryImpl,
    ): BookingRepository
}
