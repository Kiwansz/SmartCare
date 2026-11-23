package com.example.smartcare

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

internal data class SampleMedicalCertificate(
    val reference: String,
    val patient: String,
    val doctor: String,
    val issue: LocalDate,
    val start: LocalDate,
    val days: Int,
) {
    val end: LocalDate get() = start.plusDays(days.toLong() - 1)
    val returnToWork: LocalDate get() = end.plusDays(1)

    companion object {
        val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT)

        fun today(): LocalDate = LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur"))
    }
}
