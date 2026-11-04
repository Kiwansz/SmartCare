package com.example.smartcare.data

import com.example.smartcare.util.parseIc
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** A logged-in user, combined from user_account + patient/staff/doctor. */
data class Account(
    val userId: Int,
    val email: String,
    val role: String,               // PATIENT, STAFF, DOCTOR, ADMIN
    val fullName: String,
    val mustChangePassword: Boolean,
    val patientId: Int?             // only for PATIENT
)

data class NextAppointment(val slotStart: Date, val doctorName: String?, val reason: String?)

/** All Supabase calls for the User Account module. */
object AuthRepository {

    val PATIENT_ROLES = setOf("PATIENT")
    val STAFF_ROLES = setOf("STAFF", "DOCTOR") // Admins use the web panel

    private val auth get() = SupabaseProvider.client.auth
    private val db get() = SupabaseProvider.client.postgrest

    // ---------- 3. Sign up ----------

    /**
     * Creates the Auth user, then the user_account + patient rows.
     * Returns true if the user is now logged in (email confirmation off),
     * false if they must confirm their email first.
     */
    suspend fun signUpPatient(
        fullName: String,
        icNumber: String,   // 12 digits, no dashes
        email: String,
        phone: String,
        password: String
    ): Result<Boolean> = runCatching {
        val cleanEmail = email.trim().lowercase()

        auth.signUpWith(Email) {
            this.email = cleanEmail
            this.password = password
            data = buildJsonObject {
                put("full_name", fullName)
                put("ic_number", icNumber)
                put("phone", phone)
            }
        }

        val account = db.from("user_account").insert(
            buildJsonObject {
                put("email", cleanEmail)
                put("ic_number", icNumber)
                put("phone_number", phone)
                put("role", "PATIENT")
            }
        ) { select() }.decodeSingle<JsonObject>()

        val ic = parseIc(icNumber)
        db.from("patient").insert(
            buildJsonObject {
                put("user_id", account.int("user_id"))
                put("full_name", fullName)
                put("date_of_birth", ic?.dateOfBirth?.toString()) // ISO yyyy-MM-dd for DATE
                put("gender", ic?.gender)
            }
        )

        auth.currentSessionOrNull() != null
    }

    // ---------- 2 / 5. Login ----------

    /**
     * [username] can be an email or a 12-digit IC number (patients).
     * Fails if the account's role isn't in [allowedRoles] or it isn't ACTIVE.
     */
    suspend fun signIn(username: String, password: String, allowedRoles: Set<String>): Result<Account> =
        runCatching {
            val email = resolveEmail(username)
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            val account = try {
                loadAccount(email).also { checkAllowed(it, allowedRoles) }
            } catch (e: Exception) {
                auth.signOut()
                throw e
            }

            db.from("user_account").update(
                buildJsonObject { put("last_login_at", nowTimestamp()) }
            ) { filter { eq("user_id", account.userId) } }

            account
        }

    /** The account for the session saved on the device, or null if not logged in. */
    suspend fun currentAccount(): Account? {
        auth.sessionStatus.first { it !is SessionStatus.Initializing }
        val email = auth.currentUserOrNull()?.email ?: return null
        return runCatching { loadAccount(email) }.getOrNull()
    }

    suspend fun signOut() {
        runCatching { auth.signOut() }
    }

    // ---------- 4. Forgot password ----------

    /** Supabase emails a reset link to this address. */
    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.resetPasswordForEmail(email.trim().lowercase())
    }

    // ---------- 6. Change password ----------

    /** Checks the temporary password, sets the new one, and clears the must-change flag. */
    suspend fun changeTemporaryPassword(tempPassword: String, newPassword: String): Result<Unit> =
        runCatching {
            val email = auth.currentUserOrNull()?.email
                ?: error("Your session has ended. Please log in again.")

            // Re-sign in to prove the temporary password is correct.
            try {
                auth.signInWith(Email) {
                    this.email = email
                    this.password = tempPassword
                }
            } catch (e: Exception) {
                error("Temporary password is incorrect")
            }

            auth.updateUser { password = newPassword }

            val userId = findAccountRowByEmail(email)?.int("user_id")
                ?: error("No SmartCare account is linked to this login")
            db.from("user_account").update(
                buildJsonObject {
                    put("must_change_password", false)
                    put("temporary_password_expires_at", JsonNull)
                }
            ) { filter { eq("user_id", userId) } }
        }

    // ---------- 7. Home ----------

    /** Earliest upcoming BOOKED/CONFIRMED appointment, or null if there is none. */
    suspend fun nextAppointment(patientId: Int): Result<NextAppointment?> = runCatching {
        val rows = db.from("appointment").select(
            Columns.raw("visit_reason, appointment_slot(slot_start, doctor(full_name))")
        ) {
            filter {
                eq("patient_id", patientId)
                isIn("booking_status", listOf("BOOKED", "CONFIRMED"))
            }
        }.decodeList<JsonObject>()

        val now = Date()
        rows.mapNotNull { row ->
            val slot = row["appointment_slot"] as? JsonObject ?: return@mapNotNull null
            val start = parseTimestamp(slot.str("slot_start"), TimeZone.getDefault())
                ?: return@mapNotNull null
            if (start.before(now)) return@mapNotNull null
            val doctor = (slot["doctor"] as? JsonObject)?.str("full_name")
            NextAppointment(start, doctor, row.str("visit_reason"))
        }.minByOrNull { it.slotStart }
    }

    // ---------- helpers ----------

    /** IC number -> email from user_account; anything with "@" is used as-is. */
    private suspend fun resolveEmail(username: String): String {
        val input = username.trim()
        if (input.contains("@")) return input.lowercase()

        val ic = input.filter { it.isDigit() }
        if (ic.length != 12) error("Enter your email or 12-digit IC number")
        val row = db.from("user_account").select(Columns.list("email")) {
            filter { eq("ic_number", ic) }
        }.decodeSingleOrNull<JsonObject>()
        return row?.str("email") ?: error("No account found for this IC number")
    }

    private suspend fun loadAccount(email: String): Account {
        val row = findAccountRowByEmail(email)
            ?: error("No SmartCare account is linked to this login")

        val userId = row.int("user_id")
        val role = row.str("role") ?: "PATIENT"
        val status = row.str("account_status") ?: "ACTIVE"
        if (status != "ACTIVE") error("This account is ${status.lowercase()}. Please contact the clinic.")

        // Temporary passwords expire (design: 24 hours after they are issued).
        val mustChange = row["must_change_password"]?.jsonPrimitive?.booleanOrNull ?: false
        val expiresAt = parseTimestamp(row.str("temporary_password_expires_at"), TimeZone.getTimeZone("UTC"))
        if (mustChange && expiresAt != null && expiresAt.before(Date())) {
            error("Your temporary password has expired. Ask your Admin for a new one.")
        }

        val profileTable = when (role) {
            "PATIENT" -> "patient"
            "DOCTOR" -> "doctor"
            else -> "staff"
        }
        val profile = db.from(profileTable).select {
            filter { eq("user_id", userId) }
        }.decodeSingleOrNull<JsonObject>()

        return Account(
            userId = userId,
            email = row.str("email") ?: email,
            role = role,
            fullName = profile?.str("full_name") ?: email.substringBefore("@"),
            mustChangePassword = mustChange,
            patientId = if (role == "PATIENT") profile?.get("patient_id")?.jsonPrimitive?.intOrNull else null
        )
    }

    /**
     * Finds the user_account row for [email], ignoring case.
     *
     * Why not just eq("email", email)? The client library wraps any filter value
     * containing "." in quotes (e.g. ilike."a_b.com"), and PostgREST then looks for an
     * email that literally contains those quotes, so nothing matches. Instead we send a
     * LIKE pattern where every non-letter/digit (@ . + -) becomes "_" (matches any one
     * character), so no quotes are added, then pick the exact match on the device.
     */
    private suspend fun findAccountRowByEmail(email: String): JsonObject? {
        val pattern = email.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
        val rows = db.from("user_account").select {
            filter { ilike("email", pattern) }
        }.decodeList<JsonObject>()
        return rows.firstOrNull { it.str("email").equals(email, ignoreCase = true) }
    }

    private fun checkAllowed(account: Account, allowedRoles: Set<String>) {
        if (account.role in allowedRoles) return
        error(
            if (account.role == "PATIENT") "This is a patient account. Please use the Patient login."
            else if (account.role == "ADMIN") "Admins sign in through the web panel."
            else "This is a staff account. Please use the Staff login."
        )
    }

    /** Current time for TIMESTAMP columns (stored as UTC, same as Postgres NOW()). */
    private fun nowTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    /** Parses "2026-11-10T09:30:00" (fractions / offsets ignored). */
    private fun parseTimestamp(value: String?, zone: TimeZone): Date? {
        if (value == null || value.length < 19) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = zone }
                .parse(value.substring(0, 19).replace(' ', 'T'))
        }.getOrNull()
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: error("Missing $key")
}
