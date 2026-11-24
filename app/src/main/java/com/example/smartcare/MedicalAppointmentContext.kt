package com.example.smartcare

import java.time.LocalDate

internal enum class MedicalEntryType { HISTORY, PRESCRIPTION, CERTIFICATE }

internal data class MedicalAppointmentContext(
    val appointmentId: Int,
    val patientId: Int,
    val patientName: String,
    val assignedDoctorId: Int,
    val assignedDoctorName: String,
    val scheduledDate: LocalDate,
    val bookingStatus: String,
    val existingEntries: Set<MedicalEntryType> = emptySet(),
)

internal data class MedicalDoctorIdentity(val userId: Int, val doctorId: Int)

internal enum class MedicalAppointmentBlock { MISSING, INELIGIBLE, WRONG_DOCTOR, DUPLICATE }

internal fun appointmentBlock(
    appointment: MedicalAppointmentContext?,
    doctor: MedicalDoctorIdentity,
    entry: MedicalEntryType? = null,
): MedicalAppointmentBlock? = when {
    appointment == null -> MedicalAppointmentBlock.MISSING
    appointment.assignedDoctorId != doctor.doctorId -> MedicalAppointmentBlock.WRONG_DOCTOR
    appointment.bookingStatus != "COMPLETED" -> MedicalAppointmentBlock.INELIGIBLE
    entry in appointment.existingEntries -> MedicalAppointmentBlock.DUPLICATE
    else -> null
}

internal fun MedicalAppointmentBlock.messageId(): Int = when (this) {
    MedicalAppointmentBlock.MISSING -> R.string.medical_appointment_missing
    MedicalAppointmentBlock.INELIGIBLE -> R.string.medical_appointment_ineligible
    MedicalAppointmentBlock.WRONG_DOCTOR -> R.string.medical_appointment_wrong_doctor
    MedicalAppointmentBlock.DUPLICATE -> R.string.medical_appointment_duplicate
}

/** Local fixtures only. The account ID scopes the demo identity, never a real doctor ID. */
internal object MedicalAppointmentSamples {
    fun doctorFor(userId: Int) = MedicalDoctorIdentity(userId, doctorId = 51)

    fun find(appointmentId: Int): MedicalAppointmentContext? {
        val completed = MedicalAppointmentContext(
            appointmentId = 7001,
            patientId = 41,
            patientName = "Sample Patient",
            assignedDoctorId = 51,
            assignedDoctorName = "Dr. Sample",
            scheduledDate = LocalDate.of(2026, 11, 12),
            bookingStatus = "COMPLETED",
        )
        return when (appointmentId) {
            7001 -> completed
            7002 -> completed.copy(appointmentId = 7002, bookingStatus = "CONFIRMED")
            7003 -> completed.copy(appointmentId = 7003, assignedDoctorId = 52)
            7004 -> completed.copy(
                appointmentId = 7004,
                existingEntries = setOf(MedicalEntryType.HISTORY, MedicalEntryType.PRESCRIPTION),
            )
            7005 -> completed.copy(
                appointmentId = 7005,
                existingEntries = setOf(MedicalEntryType.CERTIFICATE),
            )
            else -> null
        }
    }
}
