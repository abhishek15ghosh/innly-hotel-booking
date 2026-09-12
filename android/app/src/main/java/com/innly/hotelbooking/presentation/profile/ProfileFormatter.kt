package com.innly.hotelbooking.presentation.profile

data class ProfileDisplayData(
    val initials: String,
    val displayName: String,
    val email: String,
    val phoneNumber: String,
    val roleLabel: String,
)

object ProfileFormatter {

    fun formatUserInitials(displayName: String?, email: String?): String {
        val trimmedName = displayName?.trim().orEmpty()
        if (trimmedName.isNotBlank()) {
            val parts = trimmedName.split("\\s+".toRegex()).filter { it.isNotBlank() }
            return if (parts.size >= 2) {
                "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
            } else {
                parts[0].take(2).uppercase()
            }
        }
        val trimmedEmail = email?.trim().orEmpty()
        if (trimmedEmail.isNotBlank()) {
            return trimmedEmail.first().uppercaseChar().toString()
        }
        return "IM" // Innly Member default
    }

    fun formatDisplayPhone(phoneNumber: String?): String {
        val trimmed = phoneNumber?.trim().orEmpty()
        return if (trimmed.isNotBlank()) trimmed else "Not provided"
    }

    fun formatAccountRole(role: String?): String {
        return when (role?.trim()?.lowercase()) {
            "admin" -> "Administrator"
            else -> "Innly Member"
        }
    }

    fun toProfileDisplayData(
        displayName: String?,
        email: String?,
        phoneNumber: String?,
        role: String?,
    ): ProfileDisplayData {
        val cleanName = displayName?.trim().orEmpty().ifBlank {
            email?.substringBefore("@")?.ifBlank { "Innly Member" } ?: "Innly Member"
        }
        val cleanEmail = email?.trim().orEmpty()
        val initials = formatUserInitials(displayName, email)
        val displayPhone = formatDisplayPhone(phoneNumber)
        val roleLabel = formatAccountRole(role)

        return ProfileDisplayData(
            initials = initials,
            displayName = cleanName,
            email = cleanEmail,
            phoneNumber = displayPhone,
            roleLabel = roleLabel,
        )
    }
}
