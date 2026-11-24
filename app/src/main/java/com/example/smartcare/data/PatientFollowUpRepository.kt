package com.example.smartcare.data

import java.time.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** One follow-up as the patient sees it. The staff member and the staff notes are not included. */
data class PatientFollowUp(
    val followUpId: Int,
    val appointmentId: Int,
    val type: String,               // CALL, SMS, REVISIT
    val status: String,             // PENDING, COMPLETED, UNSUCCESSFUL
    val dueDate: String?,           // yyyy-MM-dd
    val newAppointmentId: Int?,
    val doctorName: String,
    val visitStart: String
) {
    /** Still waiting for the clinic. */
    val isOpen: Boolean get() = status !in FollowUpRepository.RESULT_STATUSES
}

/** Follow-up, patient side. Read-only: the clinic creates and closes follow-ups. */
object PatientFollowUpRepository {

    /** The signed-in patient's own follow-ups, soonest due first. */
    suspend fun loadMine(): Result<List<PatientFollowUp>> = runCatching {
        val appointments = AppointmentRepository.getMyAppointments().getOrThrow()
            .associateBy { it.appointmentId }

        val rows = rpcCall("my_follow_ups") as? JsonArray ?: JsonArray(emptyList())
        rows.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val id = row.optInt("follow_up_id") ?: return@mapNotNull null
            val appointmentId = row.optInt("appointment_id") ?: return@mapNotNull null
            val appointment = appointments[appointmentId]
            PatientFollowUp(
                followUpId = id,
                appointmentId = appointmentId,
                type = row.optStr("follow_up_type") ?: "CALL",
                status = row.optStr("follow_up_status") ?: "PENDING",
                dueDate = row.optStr("due_date"),
                newAppointmentId = row.optInt("new_appointment_id"),
                doctorName = appointment?.doctorName.orEmpty(),
                visitStart = appointment?.slotStart.orEmpty()
            )
        }.sortedWith(compareBy({ !it.isOpen }, { it.dueDate.orEmpty() }))
    }

    /** How many open follow-ups are due within the next 7 days (overdue ones count too). */
    suspend fun countDueThisWeek(): Result<Int> = runCatching {
        val limit = LocalDate.now().plusDays(7)
        loadMine().getOrThrow().count { item ->
            item.isOpen && item.dueDate?.let {
                try { !LocalDate.parse(it.take(10)).isAfter(limit) } catch (_: Exception) { false }
            } == true
        }
    }
}
