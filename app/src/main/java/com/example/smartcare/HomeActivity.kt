package com.example.smartcare

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.Account
import com.example.smartcare.data.AppointmentRepository
import com.example.smartcare.data.AuthRepository
import com.example.smartcare.data.PatientAppointment
import com.example.smartcare.data.PatientFollowUpRepository
import com.example.smartcare.ui.ApptKit
import com.example.smartcare.util.ApptReminders
import com.example.smartcare.util.DateText
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 7. Patient home: greeting with the real name, next-appointment card, 4 shortcut tiles
 * and a floating tab bar.
 */
class HomeActivity : BaseActivity() {

    private var account: Account? = null

    // Android 13+: notifications must be allowed for appointment reminders to show.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        findViewById<TextView>(R.id.tvGreeting).setText(greeting())
        // Sample name until the real one loads (and if it cannot be loaded).
        showName(getString(R.string.home_sample_name))

        setupTile(R.id.tileBook, R.drawable.ic_sc_plus, R.string.tile_book, R.string.tile_book_sub) {
            startActivity(Intent(this, PatientAppointmentsActivity::class.java))
        }
        setupTile(R.id.tileChat, R.drawable.ic_sc_chat, R.string.tile_chat, R.string.tile_chat_sub, chat = true)
        setupTile(R.id.tileFollowups, R.drawable.ic_sc_loop, R.string.tile_followups, R.string.tile_followups_sub) {
            startActivity(Intent(this, PatientFollowUpsActivity::class.java))
        }
        setupTile(R.id.tileRecords, R.drawable.ic_sc_file, R.string.tile_records, R.string.tile_records_sub)

        findViewById<View>(R.id.btnBell).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        findViewById<View>(R.id.dockRecords).setOnClickListener {
            toast(R.string.coming_soon)
        }

        findViewById<View>(R.id.dockVisits).setOnClickListener {
            startActivity(Intent(this, MyAppointmentsActivity::class.java))
        }
        findViewById<View>(R.id.dockProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        loadAccount()
        refreshFollowUpSubtitle()
    }

    override fun onResume() {
        super.onResume()
        // Bookings may have changed on another screen (booked, cancelled, rescheduled).
        loadNextAppointment()
        // The name and follow-up count may have changed while on another screen.
        if (account != null) {
            loadAccount()
            refreshFollowUpSubtitle()
        }
    }

    /** Fills the "Next appointment" card with the earliest upcoming active booking. */
    private fun loadNextAppointment() {
        lifecycleScope.launch {
            AppointmentRepository.getMyAppointments()
                .onSuccess { list ->
                    val next = list
                        .filter { it.bookingStatus in ApptKit.ACTIVE_BOOKING && !DateText.hasStarted(it.slotEnd) }
                        .minByOrNull { it.slotStart }
                    showNextAppointment(next)

                    // Phone reminders (day before, one hour before) follow the real bookings.
                    ApptReminders.sync(this@HomeActivity, list)
                    if (next != null && ApptReminders.shouldAskPermission(this@HomeActivity)) {
                        notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                .onFailure {
                    showNoAppointment(R.string.home_appt_error, R.string.home_appt_error_sub, MyAppointmentsActivity::class.java)
                }
        }
    }

    private fun showNextAppointment(a: PatientAppointment?) {
        if (a == null) {
            showNoAppointment(R.string.home_appt_none, R.string.home_appt_none_sub, PatientAppointmentsActivity::class.java)
            return
        }
        findViewById<TextView>(R.id.apptStatus).apply {
            text = DateText.pretty(a.bookingStatus)
            visibility = View.VISIBLE
        }
        // "Today" / "Tomorrow", otherwise "Tue, 10 Nov".
        val day = DateText.relativeDay(a.slotStart)
        findViewById<TextView>(R.id.apptDay).text =
            if (day == DateText.dayLabel(a.slotStart)) DateText.shortDay(a.slotStart) else day
        val reason = a.visitReason?.takeIf { it.isNotBlank() }
        findViewById<TextView>(R.id.apptTime).apply {
            text = if (reason != null) getString(R.string.home_appt_time, DateText.time(a.slotStart), reason)
            else DateText.time(a.slotStart)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_sc_clock, 0, 0, 0)
            visibility = View.VISIBLE
        }
        findViewById<View>(R.id.apptDivider).visibility = View.VISIBLE
        findViewById<View>(R.id.apptDoctorRow).visibility = View.VISIBLE
        findViewById<TextView>(R.id.apptDocInitials).text = initials(a.doctorName.removePrefix("Dr.").removePrefix("Dr "))
        findViewById<TextView>(R.id.apptDoctor).text = a.doctorName
        findViewById<TextView>(R.id.apptDoctorRole).apply {
            text = a.specialization
            visibility = if (a.specialization.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        findViewById<TextView>(R.id.apptCode).text = a.checkinCode
        findViewById<View>(R.id.cardAppointment).setOnClickListener {
            startActivity(Intent(this, MyAppointmentsActivity::class.java))
        }
    }

    private fun showNoAppointment(@StringRes title: Int, @StringRes sub: Int, target: Class<*>) {
        findViewById<View>(R.id.apptStatus).visibility = View.INVISIBLE
        findViewById<TextView>(R.id.apptDay).setText(title)
        findViewById<TextView>(R.id.apptTime).apply {
            setText(sub)
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            visibility = View.VISIBLE
        }
        findViewById<View>(R.id.apptDivider).visibility = View.GONE
        findViewById<View>(R.id.apptDoctorRow).visibility = View.GONE
        findViewById<View>(R.id.cardAppointment).setOnClickListener {
            startActivity(Intent(this, target))
        }
    }

    private fun loadAccount() {
        lifecycleScope.launch {
            val acc = AuthRepository.currentAccount()
            if (acc == null) {
                // Session ended or account missing: back to the start.
                goToRolePicker()
                return@launch
            }
            if (acc.role != "PATIENT") {
                goAfterLogin(acc) // staff have their own home
                return@launch
            }
            account = acc
            showName(acc.fullName)
        }
    }

    private fun refreshFollowUpSubtitle() {
        lifecycleScope.launch {
            PatientFollowUpRepository.countDueThisWeek().onSuccess { count ->
                val text = if (count == 0) {
                    getString(R.string.clinic_home_followups_none)
                } else {
                    resources.getQuantityString(R.plurals.clinic_home_followups_due, count, count)
                }
                findViewById<View>(R.id.tileFollowups)
                    .findViewById<TextView>(R.id.tileSub).text = text
            }
        }
    }

    private fun showName(name: String) {
        findViewById<TextView>(R.id.tvName).text = name
        findViewById<TextView>(R.id.tvInitials).text = initials(name)
    }

    private fun setupTile(
        id: Int,
        @DrawableRes icon: Int,
        @StringRes title: Int,
        @StringRes sub: Int,
        chat: Boolean = false,
        onClick: (() -> Unit)? = null
    ) {
        val tile = findViewById<View>(id)
        val iconView = tile.findViewById<ImageView>(R.id.tileIcon)
        iconView.setImageResource(icon)
        tile.findViewById<TextView>(R.id.tileTitle).setText(title)
        tile.findViewById<TextView>(R.id.tileSub).setText(sub)
        if (chat) {
            // Chatbot tile is navy in both themes.
            tile.setBackgroundResource(R.drawable.bg_tile_ai)
            iconView.setBackgroundResource(R.drawable.bg_tile_ai_icon)
            iconView.imageTintList = ContextCompat.getColorStateList(this, R.color.white)
            tile.findViewById<TextView>(R.id.tileTitle).setTextColor(getColor(R.color.white))
            tile.findViewById<TextView>(R.id.tileSub).setTextColor(getColor(R.color.hero_subtitle))
        }
        tile.setOnClickListener {
            if (onClick != null) onClick() else toast(R.string.coming_soon)
        }
    }

    @StringRes
    private fun greeting(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    }

    /** "Ahmad Hafiz" -> "AH" */
    private fun initials(name: String) = name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
}