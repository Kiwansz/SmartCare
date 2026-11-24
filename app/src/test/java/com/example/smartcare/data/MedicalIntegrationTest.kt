package com.example.smartcare.data

import com.example.smartcare.toDisplay
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicalIntegrationTest {
    private val visit = MedicalVisitIds(8, 12, 21)
    private val start = LocalDate.of(2026, 11, 30)
    private val end = LocalDate.of(2026, 12, 1)
    private val item = PrescriptionItemInput("Fictional medicine", "Sample dose", "Sample frequency", 2, "Sample note")

    @Test
    fun insertPayloadsOmitGeneratedColumns() {
        val history = MedicalJson.historyInsert(visit, MedicalHistoryInput(null, "Sample diagnosis", null))
        val prescription = MedicalJson.prescriptionInsert(visit, PrescriptionInput(null, listOf(item)))
        val medication = MedicalJson.prescriptionItemInsert(31, item)
        val certificate = MedicalJson.certificateInsert(visit, MedicalCertificateInput(start, end, null))
        val document = MedicalJson.documentInsert(
            MedicalDocumentInput(12, null, 42, MedicalDocumentType.OTHER, "demo.pdf", "demo/demo.pdf")
        )

        assertFalse(history.keys.any { it in setOf("history_id", "recorded_at") })
        assertFalse(prescription.keys.any { it in setOf("prescription_id", "prescribed_at") })
        assertFalse(medication.containsKey("item_id"))
        assertEquals("31", medication.getValue("prescription_id").jsonPrimitive.content)
        assertFalse(certificate.keys.any { it in setOf("mc_id", "issued_at") })
        assertFalse(document.keys.any { it in setOf("document_id", "uploaded_at") })
        assertEquals(JsonNull, document["appointment_id"])
        assertEquals("2026-11-30", certificate["start_date"]?.jsonPrimitive?.contentOrNull)
        assertEquals("2026-12-01", certificate["end_date"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun rowsPreserveNullsAndUiFormatsDates() {
        val history = MedicalJson.historyRow(Json.parseToJsonElement(
            """{"history_id":1,"patient_id":12,"appointment_id":8,"doctor_id":21,"diagnosis":null,"recorded_at":"2026-11-30 11:00:00"}"""
        ).jsonObject)
        val certificate = MedicalJson.certificateRow(Json.parseToJsonElement(
            """{"mc_id":2,"patient_id":12,"appointment_id":8,"doctor_id":21,"start_date":"2026-11-30","end_date":"2026-12-01","issued_at":"2026-11-30T12:00:00"}"""
        ).jsonObject)
        val consultation = PatientConsultation(history, null, certificate, emptyList())
        val display = consultation.toDisplay("Dr. Sample")

        assertEquals(LocalDateTime.of(2026, 11, 30, 11, 0), history.recordedAt)
        assertNull(display.diagnosis)
        assertNull(display.medications)
        assertNull(consultation.copy(certificate = null).toDisplay(null).certificate)
        val mc = requireNotNull(display.certificate)
        assertEquals("30/11/2026", mc.issueDate)
        assertEquals("30/11/2026", mc.startDate)
        assertEquals("01/12/2026", mc.endDate)
        assertEquals(2L, mc.durationDays)
        assertEquals("02/12/2026", mc.returnToWorkDate)
    }

    @Test
    fun disabledRepositoryRejectsEveryOperation() = runBlocking {
        assertTrue(DisabledMedicalRepository.loadMyConsultations().isFailure)
        assertTrue(DisabledMedicalRepository.createHistory(8, MedicalHistoryInput(null, null, null)).isFailure)
        assertTrue(DisabledMedicalRepository.createCertificate(8, MedicalCertificateInput(start, end, null)).isFailure)
        assertTrue(DisabledMedicalRepository.createPrescription(8, PrescriptionInput(null, listOf(item))).isFailure)
    }
}
