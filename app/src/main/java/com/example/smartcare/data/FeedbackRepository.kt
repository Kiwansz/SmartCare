package com.example.smartcare.data

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The feedback a patient already gave for one appointment. */
data class FeedbackItem(val appointmentId: Int, val rating: Int, val comment: String?)

/** Feedback. One feedback per completed appointment. */
object FeedbackRepository {

    private val db get() = SupabaseProvider.client.postgrest

    /** The patient's own feedback for this appointment, or null if none was given yet. */
    suspend fun getForAppointment(appointmentId: Int): Result<FeedbackItem?> = runCatching {
        db.from(ClinicDb.FEEDBACK).select {
            filter { eq(ClinicDb.FB_APPOINTMENT, appointmentId) }
        }.decodeList<JsonObject>().firstOrNull()?.let { row ->
            FeedbackItem(
                appointmentId = appointmentId,
                rating = row.optInt(ClinicDb.FB_RATING) ?: 0,
                comment = row.optStr(ClinicDb.FB_COMMENT)?.takeIf { it.isNotBlank() }
            )
        }
    }

    /** Saves 1 to 5 stars and an optional comment. The database checks the appointment is yours and completed. */
    suspend fun submit(appointmentId: Int, rating: Int, comment: String): Result<Unit> = runCatching {
        if (rating !in 1..5) throw SmartCareUserError("Please choose between 1 and 5 stars.")
        rpcCall("submit_feedback", buildJsonObject {
            put("p_appointment_id", appointmentId)
            put("p_rating", rating)
            put("p_comment", comment.trim())
        })
        Unit
    }
}
