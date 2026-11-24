package com.example.smartcare.data

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * table and column names.
 *
 * Every column name the app reads lives HERE and nowhere else. If a column in Supabase is
 * called something different, change it in this one object (and in
 * supabase/02_setup.sql) and the rest of the code keeps working.
 * Check the real names first with supabase/01_check_schema.sql.
 */
object ClinicDb {
    // attendance_record
    const val ATTENDANCE = "attendance_record"
    const val AT_ID = "attendance_id"
    const val AT_APPOINTMENT = "appointment_id"
    const val AT_STATUS = "attendance_status"   // CHECKED_IN, PENDING_VERIFICATION, NO_SHOW
    const val AT_TIME = "checkin_time"

    // reminder
    const val REMINDER = "reminder"
    const val RM_ID = "reminder_id"
    const val RM_APPOINTMENT = "appointment_id"
    const val RM_CHANNEL = "channel"            // PUSH, SMS, EMAIL
    const val RM_SCHEDULED = "scheduled_time"
    const val RM_STATUS = "reminder_status"     // PENDING, SENT, FAILED
    const val RM_REASON = "failure_reason"
    const val RM_SENT = "sent_time"

    // follow_up
    const val FOLLOW_UP = "follow_up"
    const val FU_ID = "follow_up_id"
    const val FU_APPOINTMENT = "appointment_id"
    const val FU_TYPE = "follow_up_type"        // CALL, SMS, REVISIT
    const val FU_STAFF = "assigned_staff_id"
    const val FU_DUE = "due_date"
    const val FU_STATUS = "follow_up_status"    // PENDING, COMPLETED, UNSUCCESSFUL
    const val FU_NOTE = "result_note"
    const val FU_NEW_APPOINTMENT = "new_appointment_id"

    // feedback
    const val FEEDBACK = "feedback"
    const val FB_APPOINTMENT = "appointment_id"
    const val FB_RATING = "rating"
    const val FB_COMMENT = "comment"

    // staff (read only, to pick who a follow-up is assigned to)
    const val STAFF = "staff"
    const val ST_ID = "staff_id"
    const val ST_NAME = "full_name"
}

/**
 * An error whose message is safe and meant to be shown to the user as it is
 * (for example "Please enter your check-in code.").
 */
class SmartCareUserError(message: String) : Exception(message)

private val DB_USER_MESSAGE = Regex("""\[SC\]\s*([^\n\r"]+)""")

/**
 * Turns any failure into a short message for the user.
 * - our own [SmartCareUserError]: shown as written
 * - no network: "No internet connection"
 * - a rule from our database functions (they raise errors that start with "[SC] "):
 *   only the readable sentence is shown
 * - anything else (raw Supabase / Ktor text): the [fallback], never the raw text.
 */
fun Throwable.userMessage(fallback: String = "Something went wrong. Please try again."): String {
    if (this is SmartCareUserError) return message ?: fallback
    if (this is java.io.IOException) return "No internet connection. Please check it and try again."
    val fromDb = DB_USER_MESSAGE.find(message.orEmpty())?.groupValues?.get(1)?.trim()
    return if (fromDb.isNullOrEmpty()) fallback else fromDb
}

/**
 * Calls a Postgres function (Supabase RPC) and returns its JSON result.
 * Functions that return void give JsonNull.
 */
internal suspend fun rpcCall(name: String, params: JsonObject = buildJsonObject { }): JsonElement {
    val body = SupabaseProvider.client.postgrest.rpc(name, params).data
    return if (body.isBlank()) JsonNull else Json.parseToJsonElement(body)
}

internal fun JsonObject.optStr(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.optInt(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull
