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

    private fun format(value: String?, pattern: String): String? =
        parseTimestamp(value)?.let { SimpleDateFormat(pattern, Locale.getDefault()).format(it) }

    /** Epoch milliseconds of a database timestamp (device time zone), or null if unreadable. */
    fun millis(value: String?): Long? = parseTimestamp(value)?.time

    /** Day of month for a date badge: "21". */
    fun dayNumber(value: String?): String = format(value, "d") ?: "–"

    /** Short month for a date badge: "NOV". */
    fun monthShort(value: String?): String =
        format(value, "MMM")?.uppercase(Locale.getDefault()) ?: ""

    /** "Sat, 21 Nov 2026" (section headings, dialogs). */
    fun dayLabel(value: String?): String = format(value, "EEE, d MMM yyyy") ?: value.orEmpty()

    /** "Sat, 21 Nov" (home card). */
    fun shortDay(value: String?): String = format(value, "EEE, d MMM") ?: dayLabel(value)

    /** "9:30 AM" */
    fun time(value: String?): String = format(value, "h:mm a") ?: value.orEmpty()

    /** "9:30 AM – 10:00 AM" */
    fun timeRange(start: String?, end: String?): String = "${time(start)} – ${time(end)}"

    /** "Sat, 21 Nov · 9:30 AM – 10:00 AM" (one line, for dialogs and lists). */
    fun slotLine(start: String?, end: String?): String =
        "${format(start, "EEE, d MMM") ?: dayLabel(start)} · ${timeRange(start, end)}"

    /** yyyy-MM-dd part of a timestamp, used to group a list by day. */
    fun dayKey(value: String?): String = value.orEmpty().take(10)

    /** "Today", "Tomorrow" or "Sat, 21 Nov 2026" for a list section heading. */
    fun relativeDay(value: String?): String {
        val key = dayKey(value)
        val cal = java.util.Calendar.getInstance()
        val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = iso.format(cal.time)
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        val tomorrow = iso.format(cal.time)
        return when (key) {
            today -> "Today"
            tomorrow -> "Tomorrow"
            else -> dayLabel(value)
        }
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
