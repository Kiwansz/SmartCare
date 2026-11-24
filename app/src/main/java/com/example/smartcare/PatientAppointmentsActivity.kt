package com.example.smartcare

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AppointmentRepository
import com.example.smartcare.data.AuthRepository
import com.example.smartcare.data.AvailableAppointmentSlot
import com.example.smartcare.ui.ApptKit
import com.example.smartcare.ui.CardKit
import com.example.smartcare.ui.SmartField
import com.example.smartcare.util.DateText
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Patient: browse open slots and book one.
 * - a notice when the patient has reached the clinic's active-appointment limit
 * - quick reason chips
 * - a strip of the next 14 days (days without times are greyed out) and a doctor filter
 * - "N spots left" on each time (needs supabase/appointment_extras.sql; hidden without it)
 */
class PatientAppointmentsActivity : BaseActivity() {

    private lateinit var reasonField: SmartField
    private lateinit var reasonChips: LinearLayout
    private lateinit var limitBanner: TextView
    private lateinit var dateRow: LinearLayout
    private lateinit var filterScroll: View
    private lateinit var filterRow: LinearLayout
    private lateinit var progress: View
    private lateinit var list: LinearLayout

    private var slots: List<AvailableAppointmentSlot> = emptyList()
    private var bookedCounts: Map<Int, Int>? = null   // null = unknown, then no spots shown
    private var limitReached = false
    private var doctorFilter: String? = null
    private var dayFilter: String? = null              // yyyy-MM-dd, null = any day
    private var firstLoad = true

    private val isoDay = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_appointments)
        setupHeader(title = R.string.book_title, subtitle = R.string.book_subtitle)

        reasonField = findViewById(R.id.fieldReason)
        reasonChips = findViewById(R.id.reasonChips)
        limitBanner = findViewById(R.id.limitBanner)
        dateRow = findViewById(R.id.dateRow)
        filterScroll = findViewById(R.id.filterScroll)
        filterRow = findViewById(R.id.filterRow)
        progress = findViewById(R.id.progress)
        list = findViewById(R.id.listContainer)

        reasonField.editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = drawReasonChips()
        })
        drawReasonChips()
    }

    // Reload on return, e.g. after cancelling an appointment on My Appointments.
    override fun onResume() {
        super.onResume()
        loadAvailableSlots()
    }

    // ---------- quick reasons ----------

    private fun drawReasonChips() {
        val reasons = listOf(
            R.string.reason_fever, R.string.reason_cough, R.string.reason_checkup,
            R.string.reason_followup, R.string.reason_refill
        ).map { getString(it) }
        val current = reasonField.text.trim()

        reasonChips.removeAllViews()
        reasons.forEachIndexed { i, label ->
            val chip = CardKit.filterChip(this, label, label.equals(current, ignoreCase = true)) {
                // Tapping the chosen one again clears it.
                reasonField.text = if (label.equals(reasonField.text.trim(), ignoreCase = true)) "" else label
            }
            reasonChips.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { if (i > 0) marginStart = dp(8) })
        }
    }

    // ---------- loading ----------

    private fun loadAvailableSlots() {
        progress.visibility = View.VISIBLE
        list.removeAllViews()

        lifecycleScope.launch {
            val slotsCall = async { AppointmentRepository.getAvailableSlots() }
            val countsCall = async { AppointmentRepository.getSlotBookedCounts() }
            val mineCall = async { AppointmentRepository.getMyAppointments() }
            val settingsCall = async { AuthRepository.loadClinicSettings() }

            // The slots are required; everything else only adds detail when it is available.
            slotsCall.await()
                .onSuccess { result ->
                    slots = result.sortedBy { it.slotStart }
                    bookedCounts = countsCall.await().getOrNull()
                    updateLimit(
                        max = settingsCall.await().getOrNull()?.maxActiveAppointments,
                        active = mineCall.await().getOrNull()
                            ?.count { it.bookingStatus in ApptKit.ACTIVE_BOOKING }
                    )
                    if (firstLoad) {
                        firstLoad = false
                        // Start on the first day that has a time, so the list is never empty.
                        dayFilter = nextDays().map { isoDay.format(it.time) }.firstOrNull { dayHasTimes(it) }
                    }
                    drawDays()
                    drawFilters()
                    drawSlots()
                }
                .onFailure { error ->
                    list.removeAllViews()
                    list.addView(ApptKit.emptyState(this@PatientAppointmentsActivity, R.drawable.ic_sc_calendar,
                        getString(R.string.clinic_error_generic), error.friendly()))
                }
            progress.visibility = View.GONE
        }
    }

    // ---------- booking limit ----------

    private fun updateLimit(max: Int?, active: Int?) {
        limitReached = max != null && active != null && active >= max
        if (max == null || active == null || active == 0) {
            limitBanner.visibility = View.GONE
            return
        }
        val info = resources.getQuantityString(R.plurals.book_limit_info, max, active, max)
        limitBanner.text = if (limitReached) "$info ${getString(R.string.book_limit_reached)}" else info
        limitBanner.setBackgroundResource(if (limitReached) R.drawable.appt_banner_warn else R.drawable.appt_banner_info)
        limitBanner.setTextColor(getColor(if (limitReached) R.color.clinic_warn_text else R.color.ink))
        limitBanner.visibility = View.VISIBLE
        limitBanner.setOnClickListener {
            startActivity(Intent(this, MyAppointmentsActivity::class.java))
        }
    }

    // ---------- filters ----------

    private fun spotsLeft(slot: AvailableAppointmentSlot): Int? =
        bookedCounts?.let { slot.capacity - (it[slot.slotId] ?: 0) }

    /** Slots a patient can actually take: not full (when the counts are known). */
    private fun bookable() = slots.filter { (spotsLeft(it) ?: 1) > 0 }

    private fun dayHasTimes(day: String) = bookable().any { DateText.dayKey(it.slotStart) == day }

    private fun nextDays(): List<Calendar> = (0 until 14).map { offset ->
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
    }

    private fun drawDays() {
        dateRow.removeAllViews()
        val weekday = SimpleDateFormat("EEE", Locale.getDefault())
        val dayNumber = SimpleDateFormat("d", Locale.getDefault())

        fun add(view: View) {
            dateRow.addView(view, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(64)
            ).apply { if (dateRow.childCount > 0) marginStart = dp(8) })
        }

        add(ApptKit.dayChip(this, getString(R.string.book_day_any), getString(R.string.book_day_all),
            selected = dayFilter == null, enabled = true) {
            dayFilter = null
            drawDays(); drawSlots()
        })
        nextDays().forEach { cal ->
            val key = isoDay.format(cal.time)
            add(ApptKit.dayChip(this, weekday.format(cal.time), dayNumber.format(cal.time),
                selected = dayFilter == key, enabled = dayHasTimes(key)) {
                dayFilter = key
                drawDays(); drawSlots()
            })
        }
    }

    private fun drawFilters() {
        val doctors = slots.map { it.doctorName }.distinct().sorted()
        if (doctorFilter !in doctors) doctorFilter = null
        // A filter only helps when there is more than one doctor to choose from.
        filterScroll.visibility = if (doctors.size > 1) View.VISIBLE else View.GONE
        filterRow.removeAllViews()

        (listOf<String?>(null) + doctors).forEachIndexed { i, name ->
            val label = name ?: getString(R.string.book_all_doctors)
            val chip = CardKit.filterChip(this, label, name == doctorFilter) {
                doctorFilter = name
                drawFilters()
                drawSlots()
            }
            filterRow.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { if (i > 0) marginStart = dp(8) })
        }
    }

    // ---------- list ----------

    private fun drawSlots() {
        list.removeAllViews()
        val shown = bookable().filter {
            (doctorFilter == null || it.doctorName == doctorFilter) &&
                (dayFilter == null || DateText.dayKey(it.slotStart) == dayFilter)
        }

        if (shown.isEmpty()) {
            val onDay = dayFilter != null && bookable().isNotEmpty()
            list.addView(ApptKit.emptyState(this, R.drawable.ic_sc_calendar,
                getString(if (onDay) R.string.book_empty_day else R.string.book_empty),
                getString(if (onDay) R.string.book_empty_day_sub else R.string.book_empty_sub)))
            return
        }

        shown.groupBy { DateText.dayKey(it.slotStart) }.entries.forEachIndexed { i, (_, day) ->
            list.addView(ApptKit.sectionHeader(this,
                getString(R.string.appt_section_count, DateText.relativeDay(day.first().slotStart), day.size),
                first = i == 0))
            day.forEach { addSlotCard(it) }
        }
    }

    private fun addSlotCard(slot: AvailableAppointmentSlot) {
        val left = spotsLeft(slot)
        val card = ApptKit.card(
            this, slot.slotStart, slot.slotEnd,
            title = slot.doctorName,
            subtitle = slot.specialization,
            chip = left?.let {
                CardKit.chip(this, resources.getQuantityString(R.plurals.book_spots, it, it),
                    if (it <= 2) CardKit.Tone.WARN else CardKit.Tone.GOOD)
            }
        )
        val button = CardKit.button(this, getString(if (limitReached) R.string.book_limit_button else R.string.book_button)) {
            confirmBooking(slot)
        }
        button.isEnabled = !limitReached
        card.addView(CardKit.buttonRow(this, listOf(button)))
        list.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })
    }

    // ---------- booking ----------

    private fun confirmBooking(slot: AvailableAppointmentSlot) {
        AlertDialog.Builder(this)
            .setTitle(R.string.book_confirm_title)
            .setMessage(getString(R.string.book_confirm_msg, slot.doctorName,
                DateText.slotLine(slot.slotStart, slot.slotEnd)))
            .setNegativeButton(R.string.clinic_cancel, null)
            .setPositiveButton(R.string.book_confirm_yes) { _, _ -> bookSlot(slot) }
            .show()
    }

    private fun bookSlot(slot: AvailableAppointmentSlot) {
        progress.visibility = View.VISIBLE
        val reason = reasonField.text.trim().takeIf { it.isNotEmpty() }

        lifecycleScope.launch {
            AppointmentRepository.bookAppointment(slotId = slot.slotId, visitReason = reason)
                .onSuccess { confirmation ->
                    progress.visibility = View.GONE
                    reasonField.text = ""
                    AlertDialog.Builder(this@PatientAppointmentsActivity)
                        .setTitle(R.string.book_done_title)
                        .setMessage(getString(R.string.book_done_msg, confirmation.checkinCode))
                        .setPositiveButton(R.string.book_view_mine) { _, _ ->
                            startActivity(Intent(this@PatientAppointmentsActivity, MyAppointmentsActivity::class.java))
                            finish()
                        }
                        .setNegativeButton(R.string.book_done) { _, _ -> loadAvailableSlots() }
                        .setCancelable(false)
                        .show()
                }
                .onFailure { error ->
                    progress.visibility = View.GONE
                    AlertDialog.Builder(this@PatientAppointmentsActivity)
                        .setTitle(R.string.book_failed)
                        .setMessage(ApptKit.errorMessage(error, getString(R.string.clinic_error_generic)))
                        .setPositiveButton(R.string.mine_ok, null)
                        .show()
                }
        }
    }
}
