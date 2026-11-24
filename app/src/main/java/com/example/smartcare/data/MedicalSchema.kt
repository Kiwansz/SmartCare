package com.example.smartcare.data

import java.time.LocalDate
import java.time.LocalDateTime

internal enum class MedicalDocumentType { BLOOD_TEST, XRAY, SCAN, OTHER }

// Database rows. The timestamp columns do not carry a time zone.
internal data class MedicalHistoryRow(
    val historyId: Int,
    val patientId: Int,
    val appointmentId: Int,
    val doctorId: Int,
    val chiefComplaint: String?,
    val diagnosis: String?,
    val notes: String?,
    val recordedAt: LocalDateTime,
)

internal data class MedicalCertificateRow(
    val mcId: Int,
    val appointmentId: Int,
    val patientId: Int,
    val doctorId: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reason: String?,
    val issuedAt: LocalDateTime,
)

internal data class PrescriptionRow(
    val prescriptionId: Int,
    val appointmentId: Int,
    val patientId: Int,
    val doctorId: Int,
    val notes: String?,
    val prescribedAt: LocalDateTime,
)

internal data class PrescriptionItemRow(
    val itemId: Int,
    val prescriptionId: Int,
    val medicineName: String,
    val dosage: String?,
    val frequency: String?,
    val durationDays: Int?,
    val instructions: String?,
)

internal data class MedicalDocumentRow(
    val documentId: Int,
    val patientId: Int,
    val appointmentId: Int?,
    val uploadedByUserId: Int,
    val documentType: MedicalDocumentType,
    val fileName: String,
    val filePath: String,
    val uploadedAt: LocalDateTime,
)

internal data class PrescriptionWithItems(
    val prescription: PrescriptionRow,
    val items: List<PrescriptionItemRow>,
)

internal data class PatientConsultation(
    val history: MedicalHistoryRow,
    val prescription: PrescriptionWithItems?,
    val certificate: MedicalCertificateRow?,
    val attachments: List<MedicalDocumentRow>,
)

// IDs use sequences and row timestamps default to now(). Keep both out of inserts.
// Ownership is resolved by the authorized workflow before encoding a row payload.
internal data class MedicalVisitIds(val appointmentId: Int, val patientId: Int, val doctorId: Int)

internal data class MedicalDocumentInput(
    val patientId: Int,
    val appointmentId: Int?,
    val uploadedByUserId: Int,
    val documentType: MedicalDocumentType,
    val fileName: String,
    val filePath: String,
)
internal data class MedicalHistoryInput(
    val chiefComplaint: String?,
    val diagnosis: String?,
    val notes: String?,
)

internal data class MedicalCertificateInput(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reason: String?,
) {
    init {
        require(!endDate.isBefore(startDate)) { "Leave cannot end before it starts." }
    }
}

internal data class PrescriptionInput(
    val notes: String?,
    val items: List<PrescriptionItemInput>,
) {
    init {
        require(items.isNotEmpty()) { "A prescription needs at least one medication item." }
    }
}

internal data class PrescriptionItemInput(
    val medicineName: String,
    val dosage: String,
    val frequency: String,
    val durationDays: Int,
    val instructions: String,
) {
    init {
        require(listOf(medicineName, dosage, frequency, instructions).none { it.isBlank() }) {
            "Complete all medication fields."
        }
        require(durationDays > 0) { "Medication duration must be a positive number of days." }
    }
}
