package com.example.smartcare.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.smartcare.MyAppointmentsActivity
import com.example.smartcare.R
import com.example.smartcare.data.PatientAppointment
import com.example.smartcare.ui.ApptKit

/**
 * Appointment reminders on the patient's own phone: one the day before and one an hour
 * before each active appointment.
 *
 * They are local alarms, so no server is needed. [sync] is called whenever the app loads the
 * patient's appointments, and replaces everything it scheduled before, so booking,
 * cancelling and rescheduling are picked up the next time the app is opened. The alarms are
 * "inexact" (Android may deliver a few minutes late) so no special permission is needed.
 */
object ApptReminders {

    const val CHANNEL_ID = "appointment_reminders"
    private const val PREFS = "appt_reminders"
    private const val KEY_SCHEDULED = "scheduled_codes"
    private const val KEY_ASKED = "asked_permission"

    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val HOUR_MS = 60 * 60 * 1000L

    // Extras for the receiver
    internal const val EXTRA_KIND = "kind"
    internal const val EXTRA_DOCTOR = "doctor"
    internal const val EXTRA_TIME = "time"
    internal const val EXTRA_CODE = "code"
    internal const val EXTRA_ID = "id"

    /** Replaces every reminder with the ones for [appointments] (only active, future ones count). */
    fun sync(context: Context, appointments: List<PatientAppointment>) {
        val app = context.applicationContext
        val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().forEach { code ->
            code.toIntOrNull()?.let { alarms.cancel(pendingIntent(app, it, null)) }
        }

        val now = System.currentTimeMillis()
        val scheduled = mutableSetOf<String>()
        appointments
            .filter { it.bookingStatus in ApptKit.ACTIVE_BOOKING }
            .forEach { a ->
                val start = DateText.millis(a.slotStart) ?: return@forEach
                listOf(KIND_DAY to DAY_MS, KIND_HOUR to HOUR_MS).forEach { (kind, before) ->
                    val at = start - before
                    if (at <= now) return@forEach
                    val code = a.appointmentId * 2 + kind
                    val extras = Intent().apply {
                        putExtra(EXTRA_KIND, kind)
                        putExtra(EXTRA_ID, a.appointmentId)
                        putExtra(EXTRA_DOCTOR, a.doctorName)
                        putExtra(EXTRA_TIME, DateText.time(a.slotStart))
                        putExtra(EXTRA_CODE, a.checkinCode)
                    }
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(app, code, extras))
                    scheduled += code.toString()
                }
            }
        prefs.edit().putStringSet(KEY_SCHEDULED, scheduled).apply()
    }

    /** Removes every reminder (used on logout so the next person on this phone gets none). */
    fun clear(context: Context) = sync(context, emptyList())

    /** True when Android 13+ still needs the user to allow notifications. */
    fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** Asks only once, so a patient who says no is not nagged on every visit. */
    fun shouldAskPermission(context: Context): Boolean {
        if (!needsPermission(context)) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false)) return false
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        return true
    }

    private fun pendingIntent(context: Context, code: Int, extras: Intent?): PendingIntent {
        val intent = Intent(context, ApptReminderReceiver::class.java)
        extras?.extras?.let { intent.putExtras(it) }
        return PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    internal fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.notif_channel_desc) }
        )
    }

    internal const val KIND_DAY = 0
    internal const val KIND_HOUR = 1
}

/** Shows the reminder notification when its alarm goes off. */
class ApptReminderReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission") // checked with needsPermission() below
    override fun onReceive(context: Context, intent: Intent) {
        if (ApptReminders.needsPermission(context)) return
        ApptReminders.ensureChannel(context)

        val kind = intent.getIntExtra(ApptReminders.EXTRA_KIND, ApptReminders.KIND_DAY)
        val id = intent.getIntExtra(ApptReminders.EXTRA_ID, 0)
        val doctor = intent.getStringExtra(ApptReminders.EXTRA_DOCTOR).orEmpty()
        val time = intent.getStringExtra(ApptReminders.EXTRA_TIME).orEmpty()
        val code = intent.getStringExtra(ApptReminders.EXTRA_CODE).orEmpty()

        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MyAppointmentsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val body = context.getString(R.string.notif_body, doctor, time, code)
        val notification = NotificationCompat.Builder(context, ApptReminders.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sc_bell)
            .setContentTitle(context.getString(
                if (kind == ApptReminders.KIND_HOUR) R.string.notif_title_hour else R.string.notif_title_day))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id * 2 + kind, notification)
    }
}
