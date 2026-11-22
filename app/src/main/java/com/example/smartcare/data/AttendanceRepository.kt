package com.example.smartcare.data

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One appointment on the staff attendance board, with its attendance record (if any). */
data class AttendanceItem(
    val appointmentId: Int,
    val patientName: String,
    val doctorName: String,
    val slotStart: String,
    val bookingStatus: String,
    val attendanceId: Int?,
    val attendanceStatus: String?,   // CHECKED_IN, PENDING_VERIFICATION, NO_SHOW, or null = not arrived yet
    val checkinTime: String?
)

/** What the database says after a patient checks in. */
data class CheckInResult(val appointmentId: Int, val status: String)

/** Attendance. All writes go through secure database functions. */
object AttendanceRepository {

    const val CHECKED_IN = "CHECKED_IN"
    const val PENDING_VERIFICATION = "PENDING_VERIFICATION"
    const val NO_SHOW = "NO_SHOW"

    /** Booking statuses that mean "this visit is still to happen". */
    val ACTIVE_BOOKING = setOf("BOOKED", "CONFIRMED")

    /** Booking statuses that belong on the attendance board. */
    private val BOARD_BOOKING = ACTIVE_BOOKING + setOf("COMPLETED", "NO_SHOW")

    private val db get() = SupabaseProvider.client.postgrest

    /**
     * Staff: every appointment that belongs on the attendance board, joined with its
     * attendance record. Appointments come from Module 2's repository; attendance records
     * are read separately and matched here, so no table relationship is needed.
     */
    suspend fun loadBoard(): Result<List<AttendanceItem>> = runCatching {
        val appointments = AppointmentRepository.getAllAppointments().getOrThrow()
        val records = db.from(ClinicDb.ATTENDANCE).select().decodeList<JsonObject>()
            .associateBy { it.optInt(ClinicDb.AT_APPOINTMENT) }

        appointments.mapNotNull { appt ->
            val record = records[appt.appointmentId]
            if (record == null && appt.bookingStatus !in BOARD_BOOKING) return@mapNotNull null
            AttendanceItem(
                appointmentId = appt.appointmentId,
                patientName = appt.patientName,
                doctorName = appt.doctorName,
                slotStart = appt.slotStart,
                bookingStatus = appt.bookingStatus,
                attendanceId = record?.optInt(ClinicDb.AT_ID),
                attendanceStatus = record?.optStr(ClinicDb.AT_STATUS),
                checkinTime = record?.optStr(ClinicDb.AT_TIME)
            )
        }.sortedBy { it.slotStart }
    }

    /** Patient: check in with the code from the booking. Late arrivals become PENDING_VERIFICATION. */
    suspend fun checkIn(code: String): Result<CheckInResult> = runCatching {
        val clean = code.trim()
        if (clean.isEmpty()) throw SmartCareUserError("Please enter your check-in code.")
        val json = rpcCall("check_in_with_code", buildJsonObject { put("p_code", clean) }) as? JsonObject
            ?: throw SmartCareUserError("Check-in did not return a result. Please try again.")
        CheckInResult(
            appointmentId = json.optInt("appointment_id") ?: 0,
            status = json.optStr("attendance_status")
                ?: throw SmartCareUserError("Check-in did not return a result. Please try again.")
        )
    }

    /** Staff: the patient did not come. */
    suspend fun markNoShow(appointmentId: Int): Result<Unit> = runCatching {
        rpcCall("mark_no_show", buildJsonObject { put("p_appointment_id", appointmentId) })
        Unit
    }

    /** Staff: decide a late check-in. accept = true -> present, false -> no-show. */
    suspend fun resolvePending(attendanceId: Int, accept: Boolean): Result<Unit> = runCatching {
        rpcCall("resolve_pending_attendance", buildJsonObject {
            put("p_attendance_id", attendanceId)
            put("p_accept", accept)
        })
        Unit
    }

    /** Staff: the checked-in patient has been seen. Makes feedback possible. */
    suspend fun completeVisit(appointmentId: Int): Result<Unit> = runCatching {
        rpcCall("complete_visit", buildJsonObject { put("p_appointment_id", appointmentId) })
        Unit
    }
}
