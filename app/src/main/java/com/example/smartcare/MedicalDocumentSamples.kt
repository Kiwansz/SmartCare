package com.example.smartcare

import java.time.LocalDate

/** Fixed fictional documents for the patient prototype. */
internal object MedicalDocumentSamples {
    val medications = listOf(
        listOf(
            R.string.document_sample_name_one,
            R.string.document_sample_dosage_one,
            R.string.document_sample_frequency_one,
            R.string.document_sample_duration_one,
            R.string.document_sample_instructions_one,
        ),
        listOf(
            R.string.document_sample_name_two,
            R.string.document_sample_dosage_two,
            R.string.document_sample_frequency_two,
            R.string.document_sample_duration_two,
            R.string.document_sample_instructions_two,
        ),
    )

    val certificate = SampleMedicalCertificate(
        reference = "DEMO-CONSULTATION-001",
        patient = "Sample Patient",
        doctor = "Dr. Sample",
        issue = LocalDate.of(2026, 11, 12),
        start = LocalDate.of(2026, 11, 12),
        days = 2,
    )
}
