package com.example.smartcare.data

import com.example.smartcare.util.DateText
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** One follow-up on the list. */
data class FollowUpItem(
    val followUpId: Int,
    val appointmentId: Int?,
    val patientId: Int?,
    val patientName: String,
    val type: String,               // CALL, SMS, REVISIT
    val status: String,             // PENDING, COMPLETED, UNSUCCESSFUL
    val dueDate: String?,           // yyyy-MM-dd
    val assignedStaffName: String?,
    val resultNote: String?,
    val newAppointmentId: Int?
) {
    /** Still waiting for a result. */
    val isOpen: Boolean get() = status !in FollowUpRepository.RESULT_STATUSES
}

/** A staff member a follow-up can be assigned to. Shown by name in a spinner. */
data class StaffOption(val staffId: Int, val fullName: String) {
    override fun toString(): String = fullName
}

/** A finished or missed visit a follow-up can be created for. Shown by label in a spinner. */
data class VisitOption(val appointmentId: Int, val label: String) {
    override fun toString(): String = label
}

/** An appointment a revisit can be linked to. */
data class AppointmentOption(val appointmentId: Int, val label: String) {
    override fun toString(): String = label
}

/** Follow-up. */
object FollowUpRepository {

    const val PENDING = "PENDING"
    const val COMPLETED = "COMPLETED"
    const val UNSUCCESSFUL = "UNSUCCESSFUL"
    val RESULT_STATUSES = setOf(COMPLETED, UNSUCCESSFUL)

    val TYPES = listOf("CALL", "SMS", "REVISIT")

    private val db get() = SupabaseProvider.client.postgrest

    /** Staff and doctors: all follow-ups, open ones first (earliest due date first). */
    suspend fun load(): Result<List<FollowUpItem>> = runCatching {
        val appointments = AppointmentRepository.getAllAppointments().getOrThrow()
            .associateBy { it.appointmentId }
        val staff = loadStaff().getOrThrow().associateBy { it.staffId }

        db.from(ClinicDb.FOLLOW_UP).select().decodeList<JsonObject>().mapNotNull { row ->
            val id = row.optInt(ClinicDb.FU_ID) ?: return@mapNotNull null
            val appointmentId = row.optInt(ClinicDb.FU_APPOINTMENT)
            val appointment = appointmentId?.let { appointments[it] }
            FollowUpItem(
                followUpId = id,
                appointmentId = appointmentId,
                patientId = appointment?.patientId,
                patientName = appointment?.patientName ?: "Follow-up $id",
                type = row.optStr(ClinicDb.FU_TYPE) ?: "CALL",
                status = row.optStr(ClinicDb.FU_STATUS) ?: PENDING,
                dueDate = row.optStr(ClinicDb.FU_DUE),
                assignedStaffName = row.optInt(ClinicDb.FU_STAFF)?.let { staff[it]?.fullName },
                resultNote = row.optStr(ClinicDb.FU_NOTE)?.takeIf { it.isNotBlank() },
                newAppointmentId = row.optInt(ClinicDb.FU_NEW_APPOINTMENT)
            )
        }.sortedWith(compareBy({ !it.isOpen }, { it.dueDate ?: "9999-12-31" }))
    }

    /** Everyone a follow-up can be assigned to. */
    suspend fun loadStaff(): Result<List<StaffOption>> = runCatching {
        db.from(ClinicDb.STAFF).select().decodeList<JsonObject>().mapNotNull { row ->
            val id = row.optInt(ClinicDb.ST_ID) ?: return@mapNotNull null
            StaffOption(id, row.optStr(ClinicDb.ST_NAME) ?: "Staff $id")
        }.sortedBy { it.fullName.lowercase() }
    }

    /** Visits a follow-up can be created for: completed visits and no-shows, newest first. */
    suspend fun loadVisitOptions(): Result<List<VisitOption>> = runCatching {
        AppointmentRepository.getAllAppointments().getOrThrow()
            .filter { it.bookingStatus == "COMPLETED" || it.bookingStatus == "NO_SHOW" }
            .sortedByDescending { it.slotStart }
            .map {
                VisitOption(
                    it.appointmentId,
                    "${it.patientName} - ${DateText.dateTime(it.slotStart)} (${DateText.pretty(it.bookingStatus)})"
                )
            }
    }

    /** Upcoming appointments of one patient, for linking a revisit to a new appointment. */
    suspend fun loadLinkableAppointments(patientId: Int): Result<List<AppointmentOption>> = runCatching {
        AppointmentRepository.getAllAppointments().getOrThrow()
            .filter { it.patientId == patientId && it.bookingStatus in AttendanceRepository.ACTIVE_BOOKING }
            .sortedBy { it.slotStart }
            .map {
                AppointmentOption(
                    it.appointmentId,
                    "${DateText.dateTime(it.slotStart)} - ${it.doctorName}"
                )
            }
    }

    /** Creates a follow-up for a visit and assigns it. [dueDate] is yyyy-MM-dd. Returns its id. */
    suspend fun create(
        appointmentId: Int,
        type: String,
        staffId: Int,
        dueDate: String
    ): Result<Int> = runCatching {
        if (type !in TYPES) throw SmartCareUserError("Choose call, SMS or revisit.")
        val result = rpcCall("create_follow_up", buildJsonObject {
            put("p_appointment_id", appointmentId)
            put("p_type", type)
            put("p_staff_id", staffId)
            put("p_due_date", dueDate)
        })
        result.jsonPrimitive.intOrNull ?: 0
    }

    /** Records how the follow-up ended: completed or unsuccessful, with an optional note. */
    suspend fun recordResult(followUpId: Int, completed: Boolean, note: String): Result<Unit> = runCatching {
        rpcCall("record_follow_up_result", buildJsonObject {
            put("p_follow_up_id", followUpId)
            put("p_completed", completed)
            put("p_note", note.trim())
        })
        Unit
    }

    /** Links a revisit follow-up to the appointment that was booked for it. */
    suspend fun linkAppointment(followUpId: Int, newAppointmentId: Int): Result<Unit> = runCatching {
        rpcCall("link_follow_up_appointment", buildJsonObject {
            put("p_follow_up_id", followUpId)
            put("p_new_appointment_id", newAppointmentId)
        })
        Unit
    }
}
