package com.example.smartcare

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AppointmentRepository
import com.example.smartcare.data.AvailableAppointmentSlot
import com.example.smartcare.data.PatientLite
import com.example.smartcare.ui.ApptKit
import com.example.smartcare.ui.CardKit
import com.example.smartcare.ui.SmartField
import com.example.smartcare.util.DateText
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Staff: book an appointment for a walk-in or phone patient. Find the patient by name, add an
 * optional reason, pick an open time and book. The database function checks the clinic's
 * rules (open slot, free place, the patient's active-appointment limit).
 */
class StaffBookActivity : BaseActivity() {

    private lateinit var fieldSearch: SmartField
    private lateinit var fieldReason: SmartField
    private lateinit var searchArea: View
    private lateinit var patientResults: LinearLayout
    private lateinit var selectedPatientBox: LinearLayout
    private lateinit var filterScroll: View
    private lateinit var filterRow: LinearLayout
    private lateinit var progress: View
    private lateinit var list: LinearLayout
    private lateinit var tvSelection: TextView
    private lateinit var btnBook: MaterialButton

    private var patient: PatientLite? = null
    private var slots: List<AvailableAppointmentSlot> = emptyList()
    private var bookedCounts: Map<Int, Int>? = null
    private var selectedSlotId: Int? = null
    private var doctorFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staff_book)
        setupHeader(title = R.string.walkin_title, subtitle = R.string.walkin_subtitle)

        fieldSearch = findViewById(R.id.fieldSearch)
        fieldReason = findViewById(R.id.fieldReason)
        searchArea = findViewById(R.id.searchArea)
        patientResults = findViewById(R.id.patientResults)
        selectedPatientBox = findViewById(R.id.selectedPatient)
        filterScroll = findViewById(R.id.filterScroll)
        filterRow = findViewById(R.id.filterRow)
        progress = findViewById(R.id.progress)
        list = findViewById(R.id.listContainer)
        tvSelection = findViewById(R.id.tvSelection)
        btnBook = findViewById(R.id.btnBook)

        findViewById<View>(R.id.btnSearch).setOnClickListener { searchPatients() }
        fieldSearch.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { searchPatients(); true } else false
        }
        btnBook.setOnClickListener { confirmBooking() }

        loadSlots()
    }

    // ---------- 1 · patient ----------

    private fun searchPatients() {
        val query = fieldSearch.text.trim()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(fieldSearch.windowToken, 0)
        patientResults.removeAllViews()

        lifecycleScope.launch {
            AppointmentRepository.searchPatients(query)
                .onSuccess { found ->
                    if (found.isEmpty()) {
                        patientResults.addView(ApptKit.emptyState(this@StaffBookActivity, R.drawable.ic_sc_user,
                            getString(R.string.walkin_none), getString(R.string.walkin_none_sub)))
                    } else {
                        found.forEach { addPatientCard(it) }
                    }
                }
                .onFailure { error ->
                    patientResults.addView(ApptKit.emptyState(this@StaffBookActivity, R.drawable.ic_sc_user,
                        getString(R.string.walkin_none),
                        ApptKit.errorMessage(error, getString(R.string.clinic_error_generic))))
                }
        }
    }

    private fun patientCard(p: PatientLite): LinearLayout {
        val card = CardKit.card(this)
        card.addView(CardKit.title(this, p.fullName))
        p.dateOfBirth?.takeIf { it.isNotBlank() }?.let {
            CardKit.addLine(card, getString(R.string.walkin_born), DateText.date(it))
        }
        return card
    }

    private fun addPatientCard(p: PatientLite) {
        val card = patientCard(p)
        card.setOnClickListener { selectPatient(p) }
        patientResults.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })
    }

    private fun selectPatient(p: PatientLite) {
        patient = p
        searchArea.visibility = View.GONE
        patientResults.removeAllViews()

        selectedPatientBox.removeAllViews()
        val card = patientCard(p).apply { setBackgroundResource(R.drawable.appt_card_selected) }
        card.addView(CardKit.buttonRow(this, listOf(
            CardKit.button(this, getString(R.string.walkin_change), CardKit.ButtonStyle.OUTLINED) { clearPatient() }
        )))
        selectedPatientBox.addView(card)
        selectedPatientBox.visibility = View.VISIBLE
        updateSelection()
    }

    private fun clearPatient() {
        patient = null
        selectedPatientBox.visibility = View.GONE
        searchArea.visibility = View.VISIBLE
        updateSelection()
    }

    // ---------- 3 · time ----------

    private fun loadSlots() {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val countsCall = async { AppointmentRepository.getSlotBookedCounts() }
            AppointmentRepository.getAvailableSlots()
                .onSuccess { result ->
                    slots = result
                        .filter { !DateText.hasStarted(it.slotEnd) }   // still running or in the future
                        .sortedBy { it.slotStart }
                    bookedCounts = countsCall.await().getOrNull()
                    if (selectedSlotId !in slots.map { it.slotId }) selectedSlotId = null
                    drawFilters()
                    drawSlots()
                    updateSelection()
                }
                .onFailure { error ->
                    list.removeAllViews()
                    list.addView(ApptKit.emptyState(this@StaffBookActivity, R.drawable.ic_sc_calendar,
                        getString(R.string.clinic_error_generic), error.friendly()))
                }
            progress.visibility = View.GONE
        }
    }

    private fun spotsLeft(slot: AvailableAppointmentSlot): Int? =
        bookedCounts?.let { slot.capacity - (it[slot.slotId] ?: 0) }

    private fun drawFilters() {
        val doctors = slots.map { it.doctorName }.distinct().sorted()
        if (doctorFilter !in doctors) doctorFilter = null
        filterScroll.visibility = if (doctors.size > 1) View.VISIBLE else View.GONE
        filterRow.removeAllViews()
        (listOf<String?>(null) + doctors).forEachIndexed { i, name ->
            val chip = CardKit.filterChip(this, name ?: getString(R.string.book_all_doctors), name == doctorFilter) {
                doctorFilter = name
                drawFilters()
                drawSlots()
            }
            filterRow.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { if (i > 0) marginStart = dp(8) })
        }
    }

    private fun drawSlots() {
        list.removeAllViews()
        val shown = slots.filter {
            (doctorFilter == null || it.doctorName == doctorFilter) && (spotsLeft(it) ?: 1) > 0
        }
        if (shown.isEmpty()) {
            list.addView(ApptKit.emptyState(this, R.drawable.ic_sc_calendar,
                getString(R.string.walkin_no_times), getString(R.string.walkin_no_times_sub)))
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
        if (slot.slotId == selectedSlotId) card.setBackgroundResource(R.drawable.appt_card_selected)
        card.setOnClickListener {
            selectedSlotId = slot.slotId
            drawSlots()
            updateSelection()
        }
        list.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })
    }

    // ---------- booking ----------

    private fun selectedSlot() = slots.firstOrNull { it.slotId == selectedSlotId }

    /** The line above the Book button: who and when, as picked so far. */
    private fun updateSelection() {
        val parts = listOfNotNull(
            patient?.fullName,
            selectedSlot()?.let { "${it.doctorName} · ${DateText.slotLine(it.slotStart, it.slotEnd)}" }
        )
        tvSelection.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
        tvSelection.text = parts.joinToString("\n")
    }

    private fun confirmBooking() {
        val chosenPatient = patient ?: return toast(R.string.walkin_pick_patient)
        val slot = selectedSlot() ?: return toast(R.string.walkin_pick_time)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.walkin_confirm_title, chosenPatient.fullName))
            .setMessage(getString(R.string.walkin_confirm_msg, slot.doctorName,
                DateText.slotLine(slot.slotStart, slot.slotEnd)))
            .setNegativeButton(R.string.clinic_cancel, null)
            .setPositiveButton(R.string.book_confirm_yes) { _, _ -> book(chosenPatient, slot) }
            .show()
    }

    private fun book(chosen: PatientLite, slot: AvailableAppointmentSlot) {
        btnBook.isEnabled = false
        btnBook.alpha = 0.6f
        progress.visibility = View.VISIBLE
        val reason = fieldReason.text.trim().takeIf { it.isNotEmpty() }

        lifecycleScope.launch {
            AppointmentRepository.staffBookAppointment(chosen.patientId, slot.slotId, reason)
                .onSuccess { confirmation ->
                    AlertDialog.Builder(this@StaffBookActivity)
                        .setTitle(R.string.walkin_done_title)
                        .setMessage(getString(R.string.walkin_done_msg, chosen.fullName, confirmation.checkinCode))
                        .setPositiveButton(R.string.mine_ok, null)
                        .show()
                    // Ready for the next patient.
                    fieldReason.text = ""
                    fieldSearch.text = ""
                    selectedSlotId = null
                    clearPatient()
                    loadSlots()
                }
                .onFailure { error ->
                    AlertDialog.Builder(this@StaffBookActivity)
                        .setTitle(R.string.walkin_failed)
                        .setMessage(ApptKit.errorMessage(error, getString(R.string.clinic_error_generic)))
                        .setPositiveButton(R.string.mine_ok, null)
                        .show()
                }
            progress.visibility = View.GONE
            btnBook.alpha = 1f
            btnBook.isEnabled = true
        }
    }
}
