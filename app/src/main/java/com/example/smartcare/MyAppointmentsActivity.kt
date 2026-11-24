package com.example.smartcare

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AppointmentRepository
import com.example.smartcare.data.AuthRepository
import com.example.smartcare.data.AvailableAppointmentSlot
import com.example.smartcare.data.PatientAppointment
import com.example.smartcare.ui.ApptKit
import com.example.smartcare.ui.CardKit
import com.example.smartcare.util.ApptReminders
import com.example.smartcare.util.DateText
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Patient: own bookings split into Upcoming and History, with check-in code, reschedule, cancel. */
class MyAppointmentsActivity : BaseActivity() {

    private lateinit var filterRow: LinearLayout
    private lateinit var progress: View
    private lateinit var list: LinearLayout

    private var appointments: List<PatientAppointment> = emptyList()
    private var showHistory = false
    private var cancelCutoffHours: Int? = null   // clinic rule; null = no deadline known

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_appointments)
        setupHeader(
            title = R.string.mine_title,
            subtitle = R.string.mine_subtitle,
            pill = getString(R.string.appt_refresh),
            pillIcon = R.drawable.ic_sc_loop
        )
        findViewById<View>(R.id.headerPill).setOnClickListener { loadAppointments() }

        filterRow = findViewById(R.id.filterRow)
        progress = findViewById(R.id.progress)
        list = findViewById(R.id.listContainer)

        drawFilters()
        loadAppointments()
    }

    private fun isUpcoming(a: PatientAppointment) = a.bookingStatus in ApptKit.ACTIVE_BOOKING

    private fun loadAppointments() {
        progress.visibility = View.VISIBLE
        list.removeAllViews()

        lifecycleScope.launch {
            val settingsCall = async { AuthRepository.loadClinicSettings() }
            AppointmentRepository.getMyAppointments()
                .onSuccess { result ->
                    appointments = result
                    cancelCutoffHours = settingsCall.await().getOrNull()?.cancelCutoffHours
                    ApptReminders.sync(this@MyAppointmentsActivity, result)
                    drawFilters()
                    drawList()
                }
                .onFailure { error ->
                    list.removeAllViews()
                    list.addView(ApptKit.emptyState(this@MyAppointmentsActivity, R.drawable.ic_sc_calendar,
                        getString(R.string.clinic_error_generic),
                        safeErrorMessage(error, getString(R.string.clinic_error_generic))))
                }
            progress.visibility = View.GONE
        }
    }

    private fun drawFilters() {
        val upcoming = appointments.count { isUpcoming(it) }
        val history = appointments.size - upcoming
        filterRow.removeAllViews()
        listOf(
            false to "${getString(R.string.mine_upcoming)} ($upcoming)",
            true to "${getString(R.string.mine_history)} ($history)"
        ).forEachIndexed { i, (isHistory, label) ->
            val chip = CardKit.filterChip(this, label, isHistory == showHistory) {
                showHistory = isHistory
                drawFilters()
                drawList()
            }
            filterRow.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { if (i > 0) marginStart = dp(8) })
        }
    }

    private fun drawList() {
        list.removeAllViews()
        // Upcoming: soonest first. History: most recent first.
        val shown = if (showHistory) {
            appointments.filterNot { isUpcoming(it) }.sortedByDescending { it.slotStart }
        } else {
            appointments.filter { isUpcoming(it) }.sortedBy { it.slotStart }
        }

        if (shown.isEmpty()) {
            val empty = if (showHistory) {
                ApptKit.emptyState(this, R.drawable.ic_sc_calendar,
                    getString(R.string.mine_empty_history), getString(R.string.mine_empty_history_sub))
            } else {
                ApptKit.emptyState(this, R.drawable.ic_sc_calendar,
                    getString(R.string.mine_empty_upcoming), getString(R.string.mine_empty_upcoming_sub))
            }
            list.addView(empty)
            if (!showHistory) {
                list.addView(buildBookButton(), LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            }
            return
        }

        shown.forEach { addAppointmentCard(it) }
    }

    private fun buildBookButton(): View =
        CardKit.button(this, getString(R.string.mine_book_new)) {
            startActivity(Intent(this, PatientAppointmentsActivity::class.java))
        }

    private fun addAppointmentCard(a: PatientAppointment) {
        val card = ApptKit.card(
            this, a.slotStart, a.slotEnd,
            title = a.doctorName,
            subtitle = a.specialization,
            chip = ApptKit.statusChip(this, a.bookingStatus, ApptKit.bookingTone(a.bookingStatus))
        )

        val active = isUpcoming(a)
        if (!a.visitReason.isNullOrBlank() || !active) {
            ApptKit.divider(card)
            ApptKit.detail(card, R.drawable.ic_sc_file, getString(R.string.appt_reason), a.visitReason)
            ApptKit.detail(card, R.drawable.ic_sc_calendar, getString(R.string.appt_booking_id), "#${a.appointmentId}")
        }

        // Only active appointments show the code and can be cancelled or rescheduled.
        if (active) {
            ApptKit.codeBox(card, getString(R.string.appt_check_in_code), a.checkinCode)

            // Clinic rule: cancelling is only allowed until N hours before the visit.
            val start = DateText.millis(a.slotStart)
            val deadline = cancelCutoffHours?.let { hours -> start?.minus(hours * 60 * 60 * 1000L) }
            val canCancel = deadline == null || System.currentTimeMillis() < deadline
            if (deadline != null) {
                val deadlineText = SimpleDateFormat("EEE, d MMM, h:mm a", Locale.getDefault()).format(Date(deadline))
                if (canCancel) {
                    ApptKit.note(card, R.drawable.ic_sc_clock, getString(R.string.mine_cancel_until, deadlineText))
                } else {
                    ApptKit.note(card, R.drawable.ic_sc_clock, getString(R.string.mine_cancel_late), warn = true)
                }
            }

            card.addView(CardKit.buttonRow(this, listOf(
                CardKit.button(this, getString(R.string.mine_add_calendar), CardKit.ButtonStyle.OUTLINED) {
                    addToCalendar(a)
                }
            )))

            lateinit var reschedule: MaterialButton
            lateinit var cancel: MaterialButton
            reschedule = CardKit.button(this, getString(R.string.mine_reschedule), CardKit.ButtonStyle.OUTLINED) {
                showRescheduleDialog(a, reschedule)
            }
            cancel = CardKit.button(this, getString(R.string.mine_cancel), CardKit.ButtonStyle.DANGER) {
                confirmCancel(a, cancel)
            }
            // Past the deadline the cancel button is not offered at all.
            card.addView(CardKit.buttonRow(this, if (canCancel) listOf(reschedule, cancel) else listOf(reschedule)))
        }

        list.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })
    }

    // ---------- calendar ----------

    /** Opens the phone's calendar app with the appointment filled in (no permission needed). */
    private fun addToCalendar(a: PatientAppointment) {
        val begin = DateText.millis(a.slotStart) ?: return
        val end = DateText.millis(a.slotEnd) ?: (begin + 30 * 60 * 1000L)
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, getString(R.string.cal_title, a.doctorName))
            .putExtra(CalendarContract.Events.DESCRIPTION, getString(R.string.cal_desc, a.checkinCode))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast(R.string.mine_no_calendar)
        }
    }

    // ---------- cancel ----------

    private fun confirmCancel(a: PatientAppointment, button: View) {
        AlertDialog.Builder(this)
            .setTitle(R.string.mine_cancel_title)
            .setMessage(getString(R.string.mine_cancel_msg, a.doctorName, DateText.slotLine(a.slotStart, a.slotEnd)))
            .setNegativeButton(R.string.mine_cancel_keep, null)
            .setPositiveButton(R.string.mine_cancel_yes) { _, _ ->
                button.isEnabled = false
                progress.visibility = View.VISIBLE
                lifecycleScope.launch {
                    AppointmentRepository.cancelAppointment(appointmentId = a.appointmentId, reason = null)
                        .onSuccess {
                            toast(R.string.mine_cancelled)
                            loadAppointments()
                        }
                        .onFailure { error ->
                            progress.visibility = View.GONE
                            button.isEnabled = true
                            showErrorDialog(getString(R.string.mine_cancel_failed),
                                safeErrorMessage(error, getString(R.string.clinic_error_generic)))
                        }
                }
            }
            .show()
    }

    // ---------- reschedule ----------

    private fun showRescheduleDialog(a: PatientAppointment, button: View) {
        button.isEnabled = false
        progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            AppointmentRepository.getAvailableSlots()
                .onSuccess { slots ->
                    val alternatives = slots.filter { it.slotId != a.slotId }.sortedBy { it.slotStart }
                    if (alternatives.isEmpty()) {
                        showErrorDialog(getString(R.string.mine_pick_slot), getString(R.string.mine_no_slots))
                        return@onSuccess
                    }
                    val labels = alternatives.map {
                        "${it.doctorName}\n${DateText.slotLine(it.slotStart, it.slotEnd)}"
                    }
                    AlertDialog.Builder(this@MyAppointmentsActivity)
                        .setTitle(R.string.mine_pick_slot)
                        .setItems(labels.toTypedArray()) { _, position ->
                            confirmReschedule(a, alternatives[position], button)
                        }
                        .setNegativeButton(R.string.mine_close, null)
                        .show()
                }
                .onFailure { error ->
                    showErrorDialog(getString(R.string.mine_resched_failed),
                        safeErrorMessage(error, getString(R.string.clinic_error_generic)))
                }
            progress.visibility = View.GONE
            button.isEnabled = true
        }
    }

    private fun confirmReschedule(a: PatientAppointment, newSlot: AvailableAppointmentSlot, button: View) {
        AlertDialog.Builder(this)
            .setTitle(R.string.mine_resched_title)
            .setMessage(getString(R.string.mine_resched_msg, newSlot.doctorName,
                DateText.slotLine(newSlot.slotStart, newSlot.slotEnd)))
            .setNegativeButton(R.string.mine_resched_back, null)
            .setPositiveButton(R.string.mine_resched_yes) { _, _ ->
                button.isEnabled = false
                progress.visibility = View.VISIBLE
                lifecycleScope.launch {
                    AppointmentRepository.rescheduleAppointment(
                        appointmentId = a.appointmentId,
                        newSlotId = newSlot.slotId
                    )
                        .onSuccess { confirmation ->
                            progress.visibility = View.GONE
                            AlertDialog.Builder(this@MyAppointmentsActivity)
                                .setTitle(R.string.mine_resched_done_title)
                                .setMessage(getString(R.string.mine_resched_done_msg, confirmation.checkinCode))
                                .setPositiveButton(R.string.mine_ok) { _, _ -> loadAppointments() }
                                .setCancelable(false)
                                .show()
                        }
                        .onFailure { error ->
                            progress.visibility = View.GONE
                            button.isEnabled = true
                            showErrorDialog(getString(R.string.mine_resched_failed),
                                safeErrorMessage(error, getString(R.string.clinic_error_generic)))
                        }
                }
            }
            .show()
    }

    // ---------- helpers ----------

    private fun showErrorDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.mine_ok, null)
            .show()
    }

    private fun safeErrorMessage(error: Throwable, fallback: String) = ApptKit.errorMessage(error, fallback)
}
