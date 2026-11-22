package com.example.smartcare.data

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** One row of the reminder list, with the appointment details it belongs to. */
data class ReminderItem(
    val reminderId: Int,
    val appointmentId: Int,
    val patientName: String,
    val doctorName: String,
    val appointmentStart: String,
    val channel: String,          // PUSH, SMS, EMAIL
    val scheduledTime: String?,
    val status: String,           // PENDING, SENT, FAILED
    val failureReason: String?,
    val sentTime: String?
)

/** How many reminders the last "process due reminders" run sent and failed. */
data class ReminderRunResult(val sent: Int, val failed: Int)

/** Reminder. */
object ReminderRepository {

    val CHANNELS = listOf("PUSH", "SMS", "EMAIL")

    private val db get() = SupabaseProvider.client.postgrest

    /** Staff: all reminders, soonest first. */
    suspend fun load(): Result<List<ReminderItem>> = runCatching {
        val appointments = AppointmentRepository.getAllAppointments().getOrThrow()
            .associateBy { it.appointmentId }

        db.from(ClinicDb.REMINDER).select().decodeList<JsonObject>().mapNotNull { row ->
            val id = row.optInt(ClinicDb.RM_ID) ?: return@mapNotNull null
            val appointmentId = row.optInt(ClinicDb.RM_APPOINTMENT) ?: return@mapNotNull null
            val appointment = appointments[appointmentId]
            ReminderItem(
                reminderId = id,
                appointmentId = appointmentId,
                patientName = appointment?.patientName ?: "Appointment $appointmentId",
                doctorName = appointment?.doctorName.orEmpty(),
                appointmentStart = appointment?.slotStart.orEmpty(),
                channel = row.optStr(ClinicDb.RM_CHANNEL) ?: "PUSH",
                scheduledTime = row.optStr(ClinicDb.RM_SCHEDULED),
                status = row.optStr(ClinicDb.RM_STATUS) ?: "PENDING",
                failureReason = row.optStr(ClinicDb.RM_REASON)?.takeIf { it.isNotBlank() },
                sentTime = row.optStr(ClinicDb.RM_SENT)
            )
        }.sortedBy { it.scheduledTime.orEmpty() }
    }

    /**
     * Patient: the reminders that have been sent for the patient's own appointments, newest
     * first. Row Level Security already limits the reminder table to the patient's own rows.
     */
    suspend fun loadMine(): Result<List<ReminderItem>> = runCatching {
        val appointments = AppointmentRepository.getMyAppointments().getOrThrow()
            .associateBy { it.appointmentId }

        db.from(ClinicDb.REMINDER).select().decodeList<JsonObject>().mapNotNull { row ->
            val status = row.optStr(ClinicDb.RM_STATUS) ?: return@mapNotNull null
            if (status != "SENT") return@mapNotNull null
            val id = row.optInt(ClinicDb.RM_ID) ?: return@mapNotNull null
            val appointmentId = row.optInt(ClinicDb.RM_APPOINTMENT) ?: return@mapNotNull null
            val appointment = appointments[appointmentId] ?: return@mapNotNull null
            ReminderItem(
                reminderId = id,
                appointmentId = appointmentId,
                patientName = "",
                doctorName = appointment.doctorName,
                appointmentStart = appointment.slotStart,
                channel = row.optStr(ClinicDb.RM_CHANNEL) ?: "PUSH",
                scheduledTime = row.optStr(ClinicDb.RM_SCHEDULED),
                status = status,
                failureReason = null,
                sentTime = row.optStr(ClinicDb.RM_SENT)
            )
        }.sortedByDescending { it.sentTime ?: it.scheduledTime.orEmpty() }
    }

    /**
     * Staff: creates one PENDING reminder for every upcoming booked appointment that has none,
     * [hoursBefore] hours before it starts. Returns how many were created.
     */
    suspend fun schedule(hoursBefore: Int, channel: String): Result<Int> = runCatching {
        if (channel !in CHANNELS) throw SmartCareUserError("Choose push, SMS or email.")
        val result = rpcCall("schedule_reminders", buildJsonObject {
            put("p_hours_before", hoursBefore)
            put("p_channel", channel)
        })
        result.jsonPrimitive.intOrNull ?: 0
    }

    /**
     * Staff: handles every PENDING reminder whose time has come. A reminder becomes SENT, or
     * FAILED with a reason (for example the patient has no phone number for an SMS).
     */
    suspend fun processDue(): Result<ReminderRunResult> = runCatching {
        val json = rpcCall("process_due_reminders") as? JsonObject
            ?: throw SmartCareUserError("The reminders could not be processed. Please try again.")
        ReminderRunResult(
            sent = json.optInt("sent") ?: 0,
            failed = json.optInt("failed") ?: 0
        )
    }
}
