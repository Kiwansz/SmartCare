package com.example.smartcare.data

import android.content.Context

/** Stores the "Remember me" choice. Supabase itself keeps the login session on the device. */
object SessionPrefs {
    private const val FILE = "smartcare_prefs"
    private const val KEY_REMEMBER = "remember_me"

    fun setRememberMe(context: Context, remember: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_REMEMBER, remember).apply()
    }

    fun rememberMe(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_REMEMBER, false)
}
