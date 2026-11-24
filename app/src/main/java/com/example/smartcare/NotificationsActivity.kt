package com.example.smartcare

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.ReminderItem
import com.example.smartcare.data.ReminderRepository
import com.example.smartcare.data.userMessage
import com.example.smartcare.util.DateText
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * patient notifications. Shows the appointment reminders that have been sent to the
 * signed-in patient, newest first. "View appointment" opens My appointments.
 */
class NotificationsActivity : BaseActivity() {

    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var progress: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        list = findViewById(R.id.notifList)
        status = findViewById(R.id.tvNotifStatus)
        progress = findViewById(R.id.progress)
        findViewById<View>(R.id.btnNotifBack).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        list.removeAllViews()
        status.visibility = View.GONE
        progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = ReminderRepository.loadMine()
            progress.visibility = View.GONE
            result
                .onSuccess { items ->
                    if (items.isEmpty()) showStatus(getString(R.string.clinic_notif_empty))
                    else items.forEach { addRow(it) }
                }
                .onFailure {
                    showStatus(it.userMessage(getString(R.string.clinic_notif_error)))
                }
        }
    }

    private fun showStatus(message: String) {
        status.text = message
        status.visibility = View.VISIBLE
    }

    private fun addRow(item: ReminderItem) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_notification, list, false)

        row.findViewById<TextView>(R.id.tvNotifDate).text =
            longDate(item.sentTime ?: item.scheduledTime)

        // "Reminder:" in bold, then the message.
        val lead = getString(R.string.clinic_notif_lead)
        val body = getString(
            R.string.clinic_notif_body,
            item.doctorName,
            DateText.dateTime(item.appointmentStart)
        )
        row.findViewById<TextView>(R.id.tvNotifBody).text =
            android.text.SpannableStringBuilder().apply {
                append(lead)
                setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    0, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                append(": ")
                append(body)
            }

        row.findViewById<View>(R.id.btnNotifView).setOnClickListener {
            startActivity(Intent(this, MyAppointmentsActivity::class.java))
        }
        list.addView(row)
    }

    /** "25 November 2025" from "2025-11-25T08:00:00", or the raw text if it cannot be read. */
    private fun longDate(value: String?): String {
        if (value == null || value.length < 10) return value.orEmpty()
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value.substring(0, 10))
            if (parsed == null) value
            else SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(parsed)
        } catch (_: Exception) {
            value.substring(0, 10)
        }
    }
}
