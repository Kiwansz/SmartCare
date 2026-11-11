package com.example.smartcare.data

import com.example.smartcare.util.parseIc
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
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
    val patientId: Int?,            // only for PATIENT
    val lastLoginAt: Date? = null   // user_account.last_login_at (set at each password login)
)

/** The logged-in patient's own details for the profile screen. */
data class PatientProfile(
    val userId: Int,
    val fullName: String,
    val email: String,
    val icNumber: String?,      // 12 digits
    val phone: String?,
    val dateOfBirth: String?,   // yyyy-MM-dd
    val gender: String?         // MALE, FEMALE or null
)

data class NextAppointment(val slotStart: Date, val doctorName: String?, val reason: String?)

/** The single clinic_setting row (setting_id = 1), saved by the Admin in the web panel. */
data class ClinicSettings(
    val clinicName: String,
    val address: String?,
    val phone: String?,
    val gracePeriodMinutes: Int?,
    val cancelCutoffHours: Int?,
    val maxActiveAppointments: Int?
)

/**
 * Wrong IC/email or wrong password. One error for both, so the login screen
 * never reveals whether an account exists.
 */
class InvalidLoginException : Exception("Invalid login")

/** Forgot password: the emailed code is wrong or has expired. */
class InvalidResetCodeException : Exception("Invalid reset code")

/** Sign up: this IC number already has an account. */
class IcAlreadyRegisteredException : Exception("IC already registered")

/** Sign up: this email already has an account. */
class EmailAlreadyRegisteredException : Exception("Email already registered")

/** All Supabase calls for the User Account module. */
object AuthRepository {

    val PATIENT_ROLES = setOf("PATIENT")
    val STAFF_ROLES = setOf("STAFF", "DOCTOR") // Admins use the web panel

    private val auth get() = SupabaseProvider.client.auth
    private val db get() = SupabaseProvider.client.postgrest

    // ---------- 3. Sign up ----------

    /**
     * True if a user_account already uses this IC number. Database function
     * is_ic_registered (works before login without reading user_account directly).
     */
    suspend fun isIcRegistered(icNumber: String): Result<Boolean> = runCatching {
        rpc("is_ic_registered", buildJsonObject { put("p_ic", icNumber) }).jsonPrimitive.boolean
    }

    /** True if a user_account already uses this email. Database function is_email_registered. */
    suspend fun isEmailRegistered(email: String): Result<Boolean> = runCatching {
        rpc("is_email_registered", buildJsonObject { put("p_email", email.trim()) }).jsonPrimitive.boolean
    }

    /**
     * Creates the Auth user, then the user_account + patient rows.
     * Returns true if the user is now logged in (email confirmation off),
     * false if they must confirm their email first.
     * Fails with [IcAlreadyRegisteredException] / [EmailAlreadyRegisteredException]
     * before anything is created if the IC or email is taken.
     */
    suspend fun signUpPatient(
        fullName: String,
        icNumber: String,   // 12 digits, no dashes
        email: String,
        phone: String,
        password: String,
        gender: String? // MALE, FEMALE, or null if the user skipped it
    ): Result<Boolean> = runCatching {
        val cleanEmail = email.trim().lowercase()

        // Final check right before saving. Without it, the Auth login below would be
        // created even though the user_account insert then fails on the unique IC,
        // leaving an orphan login for this email.
        if (isIcRegistered(icNumber).getOrThrow()) throw IcAlreadyRegisteredException()
        if (isEmailRegistered(cleanEmail).getOrThrow()) throw EmailAlreadyRegisteredException()

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
                put("gender", gender) // chosen by the user, not taken from the IC
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
            try {
                auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                if (msg.contains("invalid_credentials", true) || msg.contains("Invalid login credentials", true)) {
                    throw InvalidLoginException()
                }
                throw e
            }

            val account = try {
                loadAccount(email).also { checkAllowed(it, allowedRoles) }
            } catch (e: Exception) {
                auth.signOut()
                throw e
            }

            rpc("mark_login") // sets last_login_at on the user's own row

            account
        }

    /**
     * Like [currentAccount], but tells "not logged in" (success with null) apart from
     * "could not load" (failure), so a screen can show an error with Try again instead
     * of sending the user back to the start.
     */
    suspend fun loadCurrentAccount(): Result<Account?> = runCatching {
        auth.sessionStatus.first { it !is SessionStatus.Initializing }
        val email = auth.currentUserOrNull()?.email ?: return@runCatching null
        loadAccount(email)
    }

    /** The clinic_setting row, or null if it has not been created yet. */
    suspend fun loadClinicSettings(): Result<ClinicSettings?> = runCatching {
        val row = db.from("clinic_setting").select {
            filter { eq("setting_id", 1) }
        }.decodeSingleOrNull<JsonObject>() ?: return@runCatching null
        ClinicSettings(
            clinicName = row.str("clinic_name") ?: "",
            address = row.str("address")?.takeIf { it.isNotBlank() },
            phone = row.str("phone_number")?.takeIf { it.isNotBlank() },
            gracePeriodMinutes = row["grace_period_minutes"]?.jsonPrimitive?.intOrNull,
            cancelCutoffHours = row["cancel_cutoff_hours"]?.jsonPrimitive?.intOrNull,
            maxActiveAppointments = row["max_active_appointments"]?.jsonPrimitive?.intOrNull
        )
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

    /**
     * Finishes a reset with the emailed code: verifies it as a recovery email OTP
     * (which signs the user in), sets the new password, then signs out again so
     * they log in normally. A wrong or expired code fails with [InvalidResetCodeException].
     */
    suspend fun resetPasswordWithCode(email: String, code: String, newPassword: String): Result<Unit> =
        runCatching {
            try {
                auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, email = email.trim().lowercase(), token = code)
            } catch (e: RestException) {
                throw InvalidResetCodeException() // server rejected the code
            }
            try {
                auth.updateUser { password = newPassword }
            } finally {
                signOut()
            }
        }

    // ---------- 6. Change password ----------

    /** Checks the temporary password, sets the new one, and clears the must-change flag. */
    suspend fun changeTemporaryPassword(tempPassword: String, newPassword: String): Result<Unit> =
        changePassword(tempPassword, newPassword, "Temporary password is incorrect")

    /** A logged-in user changing their own password (staff profile). Same steps as above. */
    suspend fun changeOwnPassword(currentPassword: String, newPassword: String): Result<Unit> =
        changePassword(currentPassword, newPassword, "Current password is incorrect")

    private suspend fun changePassword(oldPassword: String, newPassword: String, wrongOld: String): Result<Unit> =
        runCatching {
            val email = auth.currentUserOrNull()?.email
                ?: error("Your session has ended. Please log in again.")

            // Re-sign in to prove the old password is correct.
            try {
                auth.signInWith(Email) {
                    this.email = email
                    this.password = oldPassword
                }
            } catch (e: Exception) {
                error(wrongOld)
            }

            auth.updateUser { password = newPassword }

            // Clears must_change_password and temporary_password_expires_at on the own row.
            rpc("complete_password_change")
        }

    // ---------- Profile ----------

    /** The logged-in patient's own user_account + patient rows (allowed by RLS). */
    suspend fun loadMyProfile(): Result<PatientProfile> = runCatching {
        auth.sessionStatus.first { it !is SessionStatus.Initializing }
        val email = auth.currentUserOrNull()?.email
            ?: error("Your session has ended. Please log in again.")
        val row = findAccountRowByEmail(email)
            ?: error("No SmartCare account is linked to this login")
        val userId = row.int("user_id")
        val patient = db.from("patient").select {
            filter { eq("user_id", userId) }
        }.decodeSingleOrNull<JsonObject>()

        PatientProfile(
            userId = userId,
            fullName = patient?.str("full_name") ?: email.substringBefore("@"),
            email = row.str("email") ?: email,
            icNumber = row.str("ic_number"),
            phone = row.str("phone_number"),
            dateOfBirth = patient?.str("date_of_birth"),
            gender = patient?.str("gender")
        )
    }

    /** Saves the phone number on the user's own account row (database function update_my_phone). */
    suspend fun updateMyPhone(phone: String): Result<Unit> = runCatching {
        rpc("update_my_phone", buildJsonObject { put("p_phone", phone) })
        Unit
    }

    /** Saves gender (MALE, FEMALE or null) on the user's own patient row (RLS: own row only). */
    suspend fun updateMyGender(userId: Int, gender: String?): Result<Unit> = runCatching {
        db.from("patient").update(buildJsonObject { put("gender", gender) }) {
            filter { eq("user_id", userId) }
        }
        Unit
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

    /**
     * IC number -> email through the database function get_login_email (patients only;
     * returns null when not found). Anything with "@" is used as-is (staff login).
     */
    private suspend fun resolveEmail(username: String): String {
        val input = username.trim()
        if (input.contains("@")) return input.lowercase()

        val ic = input.filter { it.isDigit() }
        if (ic.length != 12) throw InvalidLoginException()
        val email = rpc("get_login_email", buildJsonObject { put("p_ic", ic) })
        return (email as? JsonPrimitive)?.contentOrNull ?: throw InvalidLoginException()
    }

    /**
     * Calls a Postgres function (Supabase RPC) and returns its JSON result.
     * Functions that return void give JsonNull.
     */
    private suspend fun rpc(name: String, params: JsonObject = buildJsonObject { }): JsonElement {
        val body = db.rpc(name, params).data
        return if (body.isBlank()) JsonNull else Json.parseToJsonElement(body)
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
            error("Your temporary password has expired. Ask your Admin to issue a new one.")
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
            patientId = if (role == "PATIENT") profile?.get("patient_id")?.jsonPrimitive?.intOrNull else null,
            lastLoginAt = parseTimestamp(row.str("last_login_at"), TimeZone.getTimeZone("UTC"))
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
