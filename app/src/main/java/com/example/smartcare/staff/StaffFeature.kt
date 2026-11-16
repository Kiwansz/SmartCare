package com.example.smartcare.staff

import android.app.Activity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.smartcare.R

/**
 * The one registry of staff home tiles. Each entry says who sees it and which screen it
 * opens. Features owned by other modules have screen = null and open the shared
 * "Coming soon" screen.
 *
 * When a module's real screen is ready, change ONE line: set its screen, e.g.
 *   APPOINTMENTS(..., screen = AppointmentsActivity::class.java)
 * Set owner to the module or member name once known; the Coming soon screen shows it.
 * Tiles show in the order they are listed here.
 */
enum class StaffFeature(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    @DrawableRes val icon: Int,
    val roles: Set<String>,
    val owner: String? = null,
    val screen: Class<out Activity>? = null,
) {
    APPOINTMENTS(R.string.feature_appointments, R.string.feature_appointments_sub, R.drawable.ic_sc_calendar, setOf(STAFF)),
    ATTENDANCE(R.string.feature_attendance, R.string.feature_attendance_sub, R.drawable.ic_sc_check, setOf(STAFF)),
    PATIENTS(R.string.feature_patients, R.string.feature_patients_sub, R.drawable.ic_sc_user, setOf(STAFF)),
    TODAYS_PATIENTS(R.string.feature_todays_patients, R.string.feature_todays_patients_sub, R.drawable.ic_sc_clock, setOf(DOCTOR)),
    FOLLOW_UPS(R.string.feature_follow_ups, R.string.feature_follow_ups_sub, R.drawable.ic_sc_loop, setOf(STAFF, DOCTOR)),
    MEDICAL_RECORDS(R.string.feature_medical_records, R.string.feature_medical_records_sub, R.drawable.ic_sc_file, setOf(DOCTOR));

    companion object {
        /** Tiles for a role, in registry order. Unknown roles (or ADMIN/PATIENT) get none. */
        fun forRole(role: String?): List<StaffFeature> {
            val r = role?.trim()?.uppercase() ?: return emptyList()
            return entries.filter { r in it.roles }
        }
    }
}

private const val STAFF = "STAFF"
private const val DOCTOR = "DOCTOR"
