package com.example.smartcare.util

/**
 * The password-reset code Supabase emails ({{ .Token }}). It is 8 digits today;
 * 6 to 8 are accepted so it keeps working if the length setting changes.
 */
object ResetCode {
    const val MIN_LENGTH = 6
    const val MAX_LENGTH = 8

    fun isValid(code: String): Boolean =
        code.length in MIN_LENGTH..MAX_LENGTH && code.all { it.isDigit() }
}
