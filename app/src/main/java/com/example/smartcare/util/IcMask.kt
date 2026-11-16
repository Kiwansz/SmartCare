package com.example.smartcare.util

/**
 * Shows only the last 4 digits of an IC number: "040520120777" -> "******-**-0777".
 * Dashes in the input are ignored. Anything that is not 12 digits is fully masked.
 */
fun maskIc(ic: String?): String {
    val d = icDigits(ic.orEmpty())
    return if (d.length == 12) "******-**-" + d.takeLast(4) else "******-**-****"
}
