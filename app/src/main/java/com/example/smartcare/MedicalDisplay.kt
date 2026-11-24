package com.example.smartcare

import com.example.smartcare.data.PatientConsultation

internal data class ConsultationDisplay(
    val recordedDate: String,
    val doctorName: String?,
    val chiefComplaint: String?,
    val diagnosis: String?,
    val notes: String?,
    val medications: List<MedicationDisplay>?,
    val certificate: CertificateDisplay?,
)

internal data class MedicationDisplay(
    val medicineName: String,
    val dosage: String?,
    val frequency: String?,
    val durationDays: Int?,
    val instructions: String?,
)

internal data class CertificateDisplay(
    val issueDate: String,
    val startDate: String,
    val endDate: String,
    val durationDays: Long,
    val returnToWorkDate: String,
    val reason: String?,
)

/** Maps future authorized results without substituting sample values or assuming a visit time zone. */
internal fun PatientConsultation.toDisplay(doctorName: String?): ConsultationDisplay {
    val dates = SampleMedicalCertificate.dateFormat
    return ConsultationDisplay(
        recordedDate = history.recordedAt.toLocalDate().format(dates),
        doctorName = doctorName,
        chiefComplaint = history.chiefComplaint,
        diagnosis = history.diagnosis,
        notes = history.notes,
        medications = prescription?.items?.map {
            MedicationDisplay(it.medicineName, it.dosage, it.frequency, it.durationDays, it.instructions)
        },
        certificate = certificate?.let {
            CertificateDisplay(
                issueDate = it.issuedAt.toLocalDate().format(dates),
                startDate = it.startDate.format(dates),
                endDate = it.endDate.format(dates),
                durationDays = it.endDate.toEpochDay() - it.startDate.toEpochDay() + 1,
                returnToWorkDate = it.endDate.plusDays(1).format(dates),
                reason = it.reason,
            )
        },
    )
}
