package com.example.smartcare.util

import java.util.Calendar

/** Helpers for Malaysian MyKad IC numbers (format YYMMDD-PB-###G, 12 digits). */
object IcUtils {

    /**
     * Returns the date of birth as "yyyy-MM-dd" (the format Postgres DATE expects),
     * or null if the first 6 digits are not a real date.
     *
     * Century rule: if YY is after this year's last two digits, it's 19YY, else 20YY.
     * e.g. in 2026: "030512..." -> 2003-05-12, "850512..." -> 1985-05-12.
     */
    fun birthDate(ic: String): String? {
        val digits = ic.filter { it.isDigit() }
        if (digits.length != 12) return null

        val yy = digits.substring(0, 2).toInt()
        val month = digits.substring(2, 4).toInt()
        val day = digits.substring(4, 6).toInt()

        val today = Calendar.getInstance()
        val currentYY = today.get(Calendar.YEAR) % 100
        val year = if (yy > currentYY) 1900 + yy else 2000 + yy

        // Strict check so e.g. 31 Feb or month 13 is rejected.
        val cal = Calendar.getInstance().apply {
            isLenient = false
            clear()
            set(year, month - 1, day)
        }
        try {
            cal.time
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (cal.after(today)) return null

        return "%04d-%02d-%02d".format(year, month, day)
    }

    /** Last digit odd = MALE, even = FEMALE (matches the patient.gender CHECK values). */
    fun gender(ic: String): String? {
        val last = ic.lastOrNull { it.isDigit() } ?: return null
        return if ((last - '0') % 2 == 1) "MALE" else "FEMALE"
    }
}
