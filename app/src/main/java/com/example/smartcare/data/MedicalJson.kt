package com.example.smartcare.data

import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Local JSON mapping only. This class never sends a request. */
internal object MedicalJson {
    // These identifiers must come from the authorized appointment workflow.
    fun historyInsert(visit: MedicalVisitIds, input: MedicalHistoryInput): JsonObject = buildJsonObject {
        putVisit(visit)
        put("chief_complaint", input.chiefComplaint)
        put("diagnosis", input.diagnosis)
        put("notes", input.notes)
    }

    fun certificateInsert(visit: MedicalVisitIds, input: MedicalCertificateInput): JsonObject = buildJsonObject {
        putVisit(visit)
        put("start_date", input.startDate.toString())
        put("end_date", input.endDate.toString())
        put("reason", input.reason)
    }

    fun prescriptionInsert(visit: MedicalVisitIds, input: PrescriptionInput): JsonObject = buildJsonObject {
        putVisit(visit)
        put("notes", input.notes)
    }

    // Used after obtaining the generated parent ID within the same transaction.
    fun prescriptionItemInsert(prescriptionId: Int, input: PrescriptionItemInput): JsonObject = buildJsonObject {
        put("prescription_id", prescriptionId)
        put("medicine_name", input.medicineName)
        put("dosage", input.dosage)
        put("frequency", input.frequency)
        put("duration_days", input.durationDays)
        put("instructions", input.instructions)
    }

    fun documentInsert(input: MedicalDocumentInput): JsonObject = buildJsonObject {
        put("patient_id", input.patientId)
        put("appointment_id", input.appointmentId)
        put("uploaded_by_user_id", input.uploadedByUserId)
        put("document_type", input.documentType.name)
        put("file_name", input.fileName)
        put("file_path", input.filePath)
    }

    fun historyRow(row: JsonObject): MedicalHistoryRow = with(row) {
        MedicalHistoryRow(
            historyId = getValue("history_id").jsonPrimitive.int,
            patientId = getValue("patient_id").jsonPrimitive.int,
            appointmentId = getValue("appointment_id").jsonPrimitive.int,
            doctorId = getValue("doctor_id").jsonPrimitive.int,
            chiefComplaint = get("chief_complaint")?.jsonPrimitive?.contentOrNull,
            diagnosis = get("diagnosis")?.jsonPrimitive?.contentOrNull,
            notes = get("notes")?.jsonPrimitive?.contentOrNull,
            recordedAt = LocalDateTime.parse(getValue("recorded_at").jsonPrimitive.requiredText().replace(' ', 'T')),
        )
    }

    fun certificateRow(row: JsonObject): MedicalCertificateRow = with(row) {
        MedicalCertificateRow(
            mcId = getValue("mc_id").jsonPrimitive.int,
            appointmentId = getValue("appointment_id").jsonPrimitive.int,
            patientId = getValue("patient_id").jsonPrimitive.int,
            doctorId = getValue("doctor_id").jsonPrimitive.int,
            startDate = LocalDate.parse(getValue("start_date").jsonPrimitive.requiredText()),
            endDate = LocalDate.parse(getValue("end_date").jsonPrimitive.requiredText()),
            reason = get("reason")?.jsonPrimitive?.contentOrNull,
            issuedAt = LocalDateTime.parse(getValue("issued_at").jsonPrimitive.requiredText().replace(' ', 'T')),
        )
    }

    fun prescriptionRow(row: JsonObject): PrescriptionRow = with(row) {
        PrescriptionRow(
            prescriptionId = getValue("prescription_id").jsonPrimitive.int,
            appointmentId = getValue("appointment_id").jsonPrimitive.int,
            patientId = getValue("patient_id").jsonPrimitive.int,
            doctorId = getValue("doctor_id").jsonPrimitive.int,
            notes = get("notes")?.jsonPrimitive?.contentOrNull,
            prescribedAt = LocalDateTime.parse(getValue("prescribed_at").jsonPrimitive.requiredText().replace(' ', 'T')),
        )
    }

    fun prescriptionItemRow(row: JsonObject): PrescriptionItemRow = with(row) {
        PrescriptionItemRow(
            itemId = getValue("item_id").jsonPrimitive.int,
            prescriptionId = getValue("prescription_id").jsonPrimitive.int,
            medicineName = getValue("medicine_name").jsonPrimitive.requiredText(),
            dosage = get("dosage")?.jsonPrimitive?.contentOrNull,
            frequency = get("frequency")?.jsonPrimitive?.contentOrNull,
            durationDays = get("duration_days")?.jsonPrimitive?.intOrNull,
            instructions = get("instructions")?.jsonPrimitive?.contentOrNull,
        )
    }

    fun documentRow(row: JsonObject): MedicalDocumentRow = with(row) {
        MedicalDocumentRow(
            documentId = getValue("document_id").jsonPrimitive.int,
            patientId = getValue("patient_id").jsonPrimitive.int,
            appointmentId = get("appointment_id")?.jsonPrimitive?.intOrNull,
            uploadedByUserId = getValue("uploaded_by_user_id").jsonPrimitive.int,
            documentType = MedicalDocumentType.valueOf(getValue("document_type").jsonPrimitive.requiredText()),
            fileName = getValue("file_name").jsonPrimitive.requiredText(),
            filePath = getValue("file_path").jsonPrimitive.requiredText(),
            uploadedAt = LocalDateTime.parse(getValue("uploaded_at").jsonPrimitive.requiredText().replace(' ', 'T')),
        )
    }

    private fun JsonObjectBuilder.putVisit(visit: MedicalVisitIds) {
        put("appointment_id", visit.appointmentId)
        put("patient_id", visit.patientId)
        put("doctor_id", visit.doctorId)
    }

    private fun JsonPrimitive.requiredText(): String = requireNotNull(contentOrNull)
}
