package com.example.smartcare.data

import android.content.Context
import androidx.core.content.edit

/**
 * Small on-device settings.
 * - "Remember me": keeps the Supabase session when the app reopens, and pre-fills the IC number.
 * Supabase itself stores the login session on the device.
 */
object SessionPrefs {
    private const val FILE = "smartcare_prefs"
    private const val KEY_REMEMBER = "remember_me"
    private const val KEY_SAVED_IC = "saved_ic"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun setRememberMe(context: Context, remember: Boolean) {
        prefs(context).edit { putBoolean(KEY_REMEMBER, remember) }
    }

    fun rememberMe(context: Context): Boolean = prefs(context).getBoolean(KEY_REMEMBER, false)

    /** IC number (digits only) to pre-fill the patient login; null clears it. */
    fun setSavedIc(context: Context, ic: String?) {
        prefs(context).edit {
            if (ic == null) remove(KEY_SAVED_IC) else putString(KEY_SAVED_IC, ic)
        }
    }

    fun savedIc(context: Context): String? = prefs(context).getString(KEY_SAVED_IC, null)
}
