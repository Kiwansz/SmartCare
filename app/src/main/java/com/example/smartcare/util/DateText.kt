package com.example.smartcare.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small date helpers for these screens.
 * The database sends clinic wall-clock times like "2026-11-21T09:30:00" (any offset after
 * the seconds is ignored, the same way the appointment screens treat them).
 */
object DateText {

    private val TIMESTAMP_PATTERNS = listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss")

    private fun parseTimestamp(value: String?): Date? {
        if (value == null || value.length < 19) return null
        for (pattern in TIMESTAMP_PATTERNS) {
            try {
                val parsed = SimpleDateFormat(pattern, Locale.US).parse(value)
                if (parsed != null) return parsed
            } catch (_: Exception) {
                // try the next pattern
            }
        }
        return null
    }

    /** "Sat, 21 Nov 2026, 9:30 AM", or the raw text if it cannot be read. */
    fun dateTime(value: String?): String {
        val date = parseTimestamp(value)
        if (date != null) {
            return SimpleDateFormat("EEE, d MMM yyyy, h:mm a", Locale.getDefault()).format(date)
        }
        return value?.replace('T', ' ').orEmpty()
    }

    /** "21 Nov 2026" from "2026-11-21", or the raw text if it cannot be read. */
    fun date(value: String?): String {
        if (value == null || value.length < 10) return value.orEmpty()
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value.substring(0, 10))
            if (parsed == null) value else SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(parsed)
        } catch (_: Exception) {
            value
        }
    }

    /** Today on this device as yyyy-MM-dd. */
    fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** True if the timestamp falls on today's date. */
    fun isToday(value: String?): Boolean =
        value != null && value.length >= 10 && value.substring(0, 10) == todayIso()

    /** True if the date (yyyy-MM-dd) is before today. */
    fun isBeforeToday(value: String?): Boolean =
        value != null && value.length >= 10 && value.substring(0, 10) < todayIso()

    /** True once the appointment start time has passed on this device. */
    fun hasStarted(slotStart: String?): Boolean {
        val start = parseTimestamp(slotStart) ?: return false
        return !start.after(Date())
    }

    /** "PENDING_VERIFICATION" -> "Pending verification" */
    fun pretty(status: String?): String =
        status.orEmpty().replace('_', ' ').lowercase(Locale.getDefault())
            .replaceFirstChar { it.uppercase() }
}
