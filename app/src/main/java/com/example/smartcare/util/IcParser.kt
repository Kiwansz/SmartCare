package com.example.smartcare.util

import java.time.DateTimeException
import java.time.LocalDate

/** What a Malaysian MyKad IC number (YYMMDD-PB-###G) tells us. gender is MALE or FEMALE. */
data class IcInfo(val dateOfBirth: LocalDate, val gender: String)

/** Keeps only the digits, so "010203-14-5677" and "010203145677" are the same IC. */
fun icDigits(ic: String): String = ic.filter { it.isDigit() }

/**
 * Date of birth and gender from an IC number, or null if it is not 12 digits
 * or the first 6 digits are not a real date.
 *
 * Century: 20YY unless that would be in the future, then 19YY.
 * Gender: last digit odd = MALE, even = FEMALE.
 */
fun parseIc(ic: String, today: LocalDate = LocalDate.now()): IcInfo? {
    val d = icDigits(ic)
    if (d.length != 12) return null
    val yy = d.substring(0, 2).toInt()
    val mm = d.substring(2, 4).toInt()
    val dd = d.substring(4, 6).toInt()
    return try {
        var dob = LocalDate.of(2000 + yy, mm, dd)
        if (dob.isAfter(today)) dob = dob.minusYears(100)
        val gender = if (d.last().digitToInt() % 2 == 1) "MALE" else "FEMALE"
        IcInfo(dob, gender)
    } catch (e: DateTimeException) {
        null
    }
}
