package com.innly.hotelbooking.presentation.booking

import com.innly.hotelbooking.core.ui.PresentationFormatters
import com.innly.hotelbooking.domain.model.Availability
import java.time.LocalDate

object BookingFormValidator {
    private val emailRegex = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

    fun isCheckInValid(checkIn: LocalDate, today: LocalDate): Boolean = checkIn.isAfter(today)

    fun isCheckOutValid(checkOut: LocalDate, checkIn: LocalDate): Boolean = checkOut.isAfter(checkIn)

    fun isDateRangeValid(checkIn: LocalDate, checkOut: LocalDate, today: LocalDate): Boolean =
        isCheckInValid(checkIn, today) && isCheckOutValid(checkOut, checkIn)

    fun isRoomsValid(rooms: Int): Boolean = rooms in 1..5

    fun isGuestsValid(guests: Int): Boolean = guests in 1..10

    fun isGuestNameValid(name: String): Boolean = name.trim().length in 2..120

    fun isGuestEmailValid(email: String): Boolean = emailRegex.matches(email.trim())

    fun isGuestPhoneValid(phone: String): Boolean = phone.trim().length in 8..20

    fun isFormValid(
        checkIn: LocalDate,
        checkOut: LocalDate,
        today: LocalDate,
        rooms: Int,
        guests: Int,
        guestName: String,
        guestEmail: String,
        guestPhone: String,
    ): Boolean {
        return isDateRangeValid(checkIn, checkOut, today) &&
                isRoomsValid(rooms) &&
                isGuestsValid(guests) &&
                isGuestNameValid(guestName) &&
                isGuestEmailValid(guestEmail) &&
                isGuestPhoneValid(guestPhone)
    }

    fun isContinueToPaymentEligible(
        isFormValid: Boolean,
        availability: Availability?,
        isLoading: Boolean,
    ): Boolean {
        return isFormValid && availability?.available == true && !isLoading
    }

    fun isRazorpayTestMode(keyId: String): Boolean = keyId.startsWith("rzp_test_")

    fun formatIndianNumber(amount: Long): String = PresentationFormatters.formatIndianNumber(amount)

    fun formatInr(amount: Int): String = PresentationFormatters.formatInr(amount)

    fun formatCurrency(currency: String, amount: Int): String = PresentationFormatters.formatCurrency(currency, amount)
}
