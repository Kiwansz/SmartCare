package com.example.smartcare

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AppointmentRepository
import com.example.smartcare.data.StaffAppointment
import com.example.smartcare.ui.ApptKit
import com.example.smartcare.ui.CardKit
import com.example.smartcare.util.DateText
import kotlinx.coroutines.launch

/** Staff: every patient booking, with quick stats, filters and a shortcut to Manage Slots. */
class AppointmentsActivity : BaseActivity() {

    private lateinit var filterRow: LinearLayout
    private lateinit var progress: View
    private lateinit var list: LinearLayout
    private lateinit var statToday: TextView
    private lateinit var statUpcoming: TextView
    private lateinit var statTotal: TextView

    private var appointments: List<StaffAppointment> = emptyList()

    /** null = All, TODAY / UPCOMING, or a booking status. */
    private var filter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_appointments)
        setupHeader(
            title = R.string.appt_staff_title,
            subtitle = R.string.appt_staff_subtitle,
            pill = getString(R.string.appt_refresh),
            pillIcon = R.drawable.ic_sc_loop
        )
        findViewById<View>(R.id.headerPill).setOnClickListener { loadAppointments() }

        filterRow = findViewById(R.id.filterRow)
        progress = findViewById(R.id.progress)
        list = findViewById(R.id.listContainer)
        statToday = findViewById(R.id.statToday)
        statUpcoming = findViewById(R.id.statUpcoming)
        statTotal = findViewById(R.id.statTotal)

        findViewById<View>(R.id.btnManageSlots).setOnClickListener {
            startActivity(Intent(this, ManageSlotsActivity::class.java))
        }
        findViewById<View>(R.id.btnBookForPatient).setOnClickListener {
            startActivity(Intent(this, StaffBookActivity::class.java))
        }
    }

    // Reload when coming back from Manage Slots so the list is never stale.
    override fun onResume() {
        super.onResume()
        loadAppointments()
    }

    private fun loadAppointments() {
        progress.visibility = View.VISIBLE
        list.removeAllViews()

        lifecycleScope.launch {
            AppointmentRepository.getAllAppointments()
                .onSuccess { result ->
                    appointments = result.sortedBy { it.slotStart }
                    updateStats()
                    drawFilters()
                    drawList()
                }
                .onFailure { error ->
                    list.removeAllViews()
                    list.addView(ApptKit.emptyState(
                        this@AppointmentsActivity, R.drawable.ic_sc_calendar,
                        getString(R.string.clinic_error_generic), error.friendly()
                    ))
                }
            progress.visibility = View.GONE
        }
    }

    private fun isUpcoming(a: StaffAppointment) =
        a.bookingStatus in ApptKit.ACTIVE_BOOKING && !DateText.hasStarted(a.slotEnd)

    private fun updateStats() {
        statToday.text = appointments.count { DateText.isToday(it.slotStart) }.toString()
        statUpcoming.text = appointments.count { isUpcoming(it) }.toString()
        statTotal.text = appointments.size.toString()
    }

    private fun drawFilters() {
        val options = mutableListOf<Pair<String?, String>>(
            null to getString(R.string.appt_filter_all),
            FILTER_TODAY to getString(R.string.appt_filter_today),
            FILTER_UPCOMING to getString(R.string.appt_filter_upcoming)
        )
        appointments.map { it.bookingStatus }.distinct().sorted()
            .forEach { options += it to DateText.pretty(it) }
        if (options.none { it.first == filter }) filter = null

        filterRow.removeAllViews()
        options.forEachIndexed { i, (key, label) ->
            val chip = CardKit.filterChip(this, label, key == filter) {
                filter = key
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
        val shown = when (filter) {
            null -> appointments
            FILTER_TODAY -> appointments.filter { DateText.isToday(it.slotStart) }
            FILTER_UPCOMING -> appointments.filter { isUpcoming(it) }
            else -> appointments.filter { it.bookingStatus == filter }
        }

        if (shown.isEmpty()) {
            list.addView(ApptKit.emptyState(
                this, R.drawable.ic_sc_calendar,
                getString(R.string.appt_staff_empty), getString(R.string.appt_staff_empty_sub)
            ))
            return
        }

        shown.groupBy { DateText.dayKey(it.slotStart) }.entries.forEachIndexed { i, (_, day) ->
            list.addView(ApptKit.sectionHeader(
                this,
                getString(R.string.appt_section_count, DateText.relativeDay(day.first().slotStart), day.size),
                first = i == 0
            ))
            day.forEach { addCard(it) }
        }
    }

    private fun addCard(a: StaffAppointment) {
        val doctor = listOfNotNull(a.doctorName, a.specialization?.takeIf { it.isNotBlank() })
            .joinToString(" · ")
        val card = ApptKit.card(
            this, a.slotStart, a.slotEnd,
            title = a.patientName,
            subtitle = doctor,
            chip = ApptKit.statusChip(this, a.bookingStatus, ApptKit.bookingTone(a.bookingStatus))
        )
        ApptKit.divider(card)
        ApptKit.detail(card, R.drawable.ic_sc_file, getString(R.string.appt_reason),
            a.visitReason?.takeIf { it.isNotBlank() } ?: getString(R.string.appt_not_specified))
        ApptKit.detail(card, R.drawable.ic_sc_id, getString(R.string.appt_check_in_code), a.checkinCode)
        ApptKit.detail(card, R.drawable.ic_sc_user, getString(R.string.appt_patient_id), a.patientId.toString())
        ApptKit.detail(card, R.drawable.ic_sc_calendar, getString(R.string.appt_booking_id), "#${a.appointmentId}")

        list.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })
    }

    private companion object {
        const val FILTER_TODAY = "__today"
        const val FILTER_UPCOMING = "__upcoming"
    }
}
