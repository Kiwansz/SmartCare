package com.example.smartcare.data

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class AppointmentDoctor(
    val doctorId: Int,
    val fullName: String,
    val specialization: String?
)

data class AppointmentSlot(
    val slotId: Int,
    val doctorId: Int,
    val doctorName: String,
    val slotStart: String,
    val slotEnd: String,
    val capacity: Int,
    val status: String
)
data class AvailableAppointmentSlot(
    val slotId: Int,
    val doctorName: String,
    val specialization: String?,
    val slotStart: String,
    val slotEnd: String,
    val capacity: Int
)

data class BookingConfirmation(
    val appointmentId: Int,
    val checkinCode: String
)
data class PatientAppointment(
    val appointmentId: Int,
    val slotId: Int,
    val bookingStatus: String,
    val visitReason: String?,
    val checkinCode: String,
    val doctorName: String,
    val specialization: String?,
    val slotStart: String,
    val slotEnd: String
)
data class StaffAppointment(
    val appointmentId: Int,
    val patientId: Int,
    val patientName: String,
    val doctorName: String,
    val specialization: String?,
    val slotStart: String,
    val slotEnd: String,
    val bookingStatus: String,
    val visitReason: String?,
    val checkinCode: String
)
data class RescheduleConfirmation(
    val oldAppointmentId: Int,
    val newAppointmentId: Int,
    val bookingStatus: String,
    val checkinCode: String,
    val newSlotId: Int
)
object AppointmentRepository {

    private val db get() = SupabaseProvider.client.postgrest

    // Get active doctors for the staff slot-management screen.
    suspend fun getActiveDoctors(): Result<List<AppointmentDoctor>> =
        runCatching {
            db.from("doctor")
                .select {
                    filter {
                        eq("is_active", true)
                    }
                }
                .decodeList<JsonObject>()
                .mapNotNull { row ->
                    val id = row["doctor_id"]
                        ?.jsonPrimitive?.intOrNull
                        ?: return@mapNotNull null

                    AppointmentDoctor(
                        doctorId = id,
                        fullName = row["full_name"]
                            ?.jsonPrimitive?.contentOrNull
                            ?: "Doctor $id",
                        specialization = row["specialization"]
                            ?.jsonPrimitive?.contentOrNull
                    )
                }
        }

    // Create a slot through the secure Supabase database function.
    suspend fun createSlot(
        doctorId: Int,
        slotStart: String,
        slotEnd: String,
        capacity: Int
    ): Result<Int> = runCatching {

        require(doctorId > 0) { "Please select a doctor." }
        require(capacity >= 1) { "Capacity must be at least 1." }
        require(slotEnd > slotStart) {
            "End time must be after start time."
        }

        val response = db.rpc(
            "create_appointment_slot",
            buildJsonObject {
                put("p_doctor_id", doctorId)
                put("p_slot_start", slotStart)
                put("p_slot_end", slotEnd)
                put("p_capacity", capacity)
            }
        ).data

        Json.parseToJsonElement(response)
            .jsonPrimitive
            .intOrNull
            ?: error("The database did not return a valid slot ID.")
    }

    // Retrieve saved appointment slots with their doctor's name.
    suspend fun getAppointmentSlots(): Result<List<AppointmentSlot>> =
        runCatching {
            db.from("appointment_slot")
                .select(
                    Columns.raw(
                        "slot_id, doctor_id, slot_start, slot_end, capacity, slot_status, doctor(full_name)"
                    )
                )
                .decodeList<JsonObject>()
                .mapNotNull { row ->

                    val slotId = row["slot_id"]
                        ?.jsonPrimitive?.intOrNull
                        ?: return@mapNotNull null

                    val doctorId = row["doctor_id"]
                        ?.jsonPrimitive?.intOrNull
                        ?: return@mapNotNull null

                    val slotStart = row["slot_start"]
                        ?.jsonPrimitive?.contentOrNull
                        ?: return@mapNotNull null

                    val slotEnd = row["slot_end"]
                        ?.jsonPrimitive?.contentOrNull
                        ?: return@mapNotNull null

                    val doctorName = row["doctor"]
                        ?.jsonObject
                        ?.get("full_name")
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?: "Unknown doctor"

                    AppointmentSlot(
                        slotId = slotId,
                        doctorId = doctorId,
                        doctorName = doctorName,
                        slotStart = slotStart,
                        slotEnd = slotEnd,
                        capacity = row["capacity"]
                            ?.jsonPrimitive?.intOrNull ?: 1,
                        status = row["slot_status"]
                            ?.jsonPrimitive?.contentOrNull ?: "UNKNOWN"
                    )
                }
                .sortedBy { it.slotStart }
        }

    // Retrieve available future OPEN slots for patient booking.
    suspend fun getAvailableSlots(): Result<List<AvailableAppointmentSlot>> =
        runCatching {
            db.from("appointment_slot")
                .select(
                    Columns.raw(
                        "slot_id, slot_start, slot_end, capacity, slot_status, doctor(full_name, specialization)"
                    )
                ) {
                    filter {
                        eq("slot_status", "OPEN")
                    }
                }
                .decodeList<JsonObject>()
                .mapNotNull { row ->
                    val slotId = row["slot_id"]
                        ?.jsonPrimitive?.intOrNull
                        ?: return@mapNotNull null

                    val doctor = row["doctor"]?.jsonObject

                    AvailableAppointmentSlot(
                        slotId = slotId,
                        doctorName = doctor?.get("full_name")
                            ?.jsonPrimitive?.contentOrNull
                            ?: "Unknown doctor",
                        specialization = doctor?.get("specialization")
                            ?.jsonPrimitive?.contentOrNull,
                        slotStart = row["slot_start"]
                            ?.jsonPrimitive?.contentOrNull ?: "",
                        slotEnd = row["slot_end"]
                            ?.jsonPrimitive?.contentOrNull ?: "",
                        capacity = row["capacity"]
                            ?.jsonPrimitive?.intOrNull ?: 1
                    )
                }
                .sortedBy { it.slotStart }
        }

    // Book an appointment through the secure Supabase function.
    suspend fun bookAppointment(
        slotId: Int,
        visitReason: String?
    ): Result<BookingConfirmation> = runCatching {

        require(slotId > 0) { "Invalid appointment slot." }

        val response = db.rpc(
            "book_appointment",
            buildJsonObject {
                put("p_slot_id", slotId)
                put("p_visit_reason", visitReason?.trim().orEmpty())
            }
        ).data

        val result = Json.parseToJsonElement(response).jsonObject

        val appointmentId = result["appointment_id"]
            ?.jsonPrimitive?.intOrNull
            ?: error("Appointment ID was not returned.")

        val checkinCode = result["checkin_code"]
            ?.jsonPrimitive?.contentOrNull
            ?: error("Check-in code was not returned.")

        BookingConfirmation(
            appointmentId = appointmentId,
            checkinCode = checkinCode
        )
    }

    // Retrieve the logged-in patient's appointments.
    // RLS ensures patients can only retrieve their own records.
    suspend fun getMyAppointments(): Result<List<PatientAppointment>> =
        runCatching {
            db.from("appointment")
                .select(
                    Columns.raw(
                        """
                        appointment_id,
                        booking_status,
                        visit_reason,
                        checkin_code,
                        appointment_slot(
                            slot_id,
                            slot_start,
                            slot_end,
                            doctor(full_name, specialization)
                        )
                        """.trimIndent()
                    )
                )
                .decodeList<JsonObject>()
                .mapNotNull { row ->

                    val appointmentId = row["appointment_id"]
                        ?.jsonPrimitive?.intOrNull
                        ?: return@mapNotNull null

                    val slot = row["appointment_slot"]
                        ?.jsonObject
                        ?: return@mapNotNull null

                    val doctor = slot["doctor"]?.jsonObject

                    PatientAppointment(
                        appointmentId = appointmentId,
                        slotId = slot["slot_id"]
                            ?.jsonPrimitive?.intOrNull
                            ?: return@mapNotNull null,
                        bookingStatus = row["booking_status"]
                            ?.jsonPrimitive?.contentOrNull ?: "UNKNOWN",
                        visitReason = row["visit_reason"]
                            ?.jsonPrimitive?.contentOrNull,
                        checkinCode = row["checkin_code"]
                            ?.jsonPrimitive?.contentOrNull ?: "",
                        doctorName = doctor?.get("full_name")
                            ?.jsonPrimitive?.contentOrNull
                            ?: "Unknown doctor",
                        specialization = doctor?.get("specialization")
                            ?.jsonPrimitive?.contentOrNull,
                        slotStart = slot["slot_start"]
                            ?.jsonPrimitive?.contentOrNull ?: "",
                        slotEnd = slot["slot_end"]
                            ?.jsonPrimitive?.contentOrNull ?: ""
                    )
                }
                .sortedBy { it.slotStart }
        }
    // Retrieve all appointments for the staff appointment-management screen.
    suspend fun getAllAppointments(): Result<List<StaffAppointment>> =
        runCatching {
            db.from("appointment")
                .select(
                    Columns.raw(
                        """
                    appointment_id,
                    patient_id,
                    booking_status,
                    visit_reason,
                    checkin_code,
                    patient(full_name),
                    appointment_slot(
                        slot_start,
                        slot_end,
                        doctor(full_name, specialization)
                    )
                    """.trimIndent()
                    )
                )
                .decodeList<JsonObject>()
                .mapNotNull { row ->

                    val appointmentId = row["appointment_id"]
                        ?.jsonPrimitive?.intOrNull
                        ?: return@mapNotNull null

                    val patientId = row["patient_id"]
                        ?.jsonPrimitive?.intOrNull
                        ?: return@mapNotNull null

                    val patient = row["patient"]?.jsonObject
                    val slot = row["appointment_slot"]?.jsonObject
                        ?: return@mapNotNull null
                    val doctor = slot["doctor"]?.jsonObject

                    StaffAppointment(
                        appointmentId = appointmentId,
                        patientId = patientId,
                        patientName = patient?.get("full_name")
                            ?.jsonPrimitive?.contentOrNull
                            ?: "Patient $patientId",
                        doctorName = doctor?.get("full_name")
                            ?.jsonPrimitive?.contentOrNull
                            ?: "Unknown doctor",
                        specialization = doctor?.get("specialization")
                            ?.jsonPrimitive?.contentOrNull,
                        slotStart = slot["slot_start"]
                            ?.jsonPrimitive?.contentOrNull ?: "",
                        slotEnd = slot["slot_end"]
                            ?.jsonPrimitive?.contentOrNull ?: "",
                        bookingStatus = row["booking_status"]
                            ?.jsonPrimitive?.contentOrNull ?: "UNKNOWN",
                        visitReason = row["visit_reason"]
                            ?.jsonPrimitive?.contentOrNull,
                        checkinCode = row["checkin_code"]
                            ?.jsonPrimitive?.contentOrNull ?: ""
                    )
                }
                .sortedBy { it.slotStart }
        }
    // Cancel an appointment through the secure database function.
    suspend fun cancelAppointment(
        appointmentId: Int,
        reason: String?
    ): Result<Unit> = runCatching {

        require(appointmentId > 0) {
            "Invalid appointment ID."
        }

        val response = db.rpc(
            "cancel_appointment",
            buildJsonObject {
                put("p_appointment_id", appointmentId)
                put("p_cancellation_reason", reason?.trim().orEmpty())
            }
        ).data

        Json.parseToJsonElement(response)
        Unit
    }
    // Reschedule an appointment through the secure database function.
    suspend fun rescheduleAppointment(
        appointmentId: Int,
        newSlotId: Int
    ): Result<RescheduleConfirmation> = runCatching {

        require(appointmentId > 0) {
            "Invalid appointment ID."
        }

        require(newSlotId > 0) {
            "Please select a valid new slot."
        }

        val response = db.rpc(
            "reschedule_appointment",
            buildJsonObject {
                put("p_appointment_id", appointmentId)
                put("p_new_slot_id", newSlotId)
            }
        ).data

        val result = Json.parseToJsonElement(response).jsonObject

        RescheduleConfirmation(
            oldAppointmentId = result["old_appointment_id"]
                ?.jsonPrimitive?.intOrNull
                ?: error("Original appointment ID was not returned."),

            newAppointmentId = result["new_appointment_id"]
                ?.jsonPrimitive?.intOrNull
                ?: error("New appointment ID was not returned."),

            bookingStatus = result["booking_status"]
                ?.jsonPrimitive?.contentOrNull ?: "UNKNOWN",

            checkinCode = result["checkin_code"]
                ?.jsonPrimitive?.contentOrNull
                ?: error("New check-in code was not returned."),

            newSlotId = result["new_slot_id"]
                ?.jsonPrimitive?.intOrNull
                ?: error("New slot ID was not returned.")
        )
    }


}
