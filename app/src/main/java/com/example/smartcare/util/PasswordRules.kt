package com.example.smartcare.util

/**
 * Password rules shared by sign up and change password:
 * at least 8 characters, one uppercase letter, one number.
 */
data class PasswordCheck(
    val longEnough: Boolean,
    val hasUppercase: Boolean,
    val hasNumber: Boolean,
    /** 0..4, drives the 4-segment strength bar. */
    val strength: Int
) {
    /** All required rules pass (matching the confirm field is checked separately). */
    val isValid: Boolean get() = longEnough && hasUppercase && hasNumber
}

object PasswordRules {
    const val MIN_LENGTH = 8

    fun check(password: String): PasswordCheck {
        val longEnough = password.length >= MIN_LENGTH
        val hasUpper = password.any { it.isUpperCase() }
        val hasNumber = password.any { it.isDigit() }
        // 4th point for something extra: a symbol or 12+ characters.
        val extra = password.any { !it.isLetterOrDigit() } || password.length >= 12
        val strength = if (password.isEmpty()) 0
            else listOf(longEnough, hasUpper, hasNumber, extra).count { it }.coerceAtLeast(1)
        return PasswordCheck(longEnough, hasUpper, hasNumber, strength)
    }

    /** Confirm field matches and is not empty. */
    fun matches(password: String, confirm: String): Boolean =
        confirm.isNotEmpty() && password == confirm
}
