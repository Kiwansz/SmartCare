package com.example.smartcare

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AppointmentDoctor
import com.example.smartcare.data.AppointmentRepository
import com.example.smartcare.data.AppointmentSlot
import com.example.smartcare.ui.ApptKit
import com.example.smartcare.ui.CardKit
import com.example.smartcare.util.DateText
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Staff: create appointment slots for a doctor and review / remove the saved ones.
 * One form creates a single slot, or many: split the start-end window into slots of a chosen
 * length and repeat it on weekdays or every day for up to 8 weeks.
 */
class ManageSlotsActivity : BaseActivity() {

    private enum class Repeat { ONCE, WEEKDAYS, DAILY }

    private class Planned(val start: Calendar, val end: Calendar)

    private lateinit var tileDoctor: View
    private lateinit var tileDate: View
    private lateinit var tileStart: View
    private lateinit var tileEnd: View
    private lateinit var tvCapacity: TextView
    private lateinit var tvWeeks: TextView
    private lateinit var weeksRow: View
    private lateinit var lengthRow: LinearLayout
    private lateinit var repeatRow: LinearLayout
    private lateinit var summaryText: TextView
    private lateinit var createButton: MaterialButton
    private lateinit var statusText: TextView
    private lateinit var filterRow: LinearLayout
    private lateinit var progress: View
    private lateinit var list: LinearLayout

    private var doctors: List<AppointmentDoctor> = emptyList()
    private var doctorIndex = -1
    private var selectedDate: Calendar? = null
    private var startTime: Pair<Int, Int>? = null
    private var endTime: Pair<Int, Int>? = null
    private var capacity = 1
    private var lengthMinutes: Int? = null      // null = the whole window is one slot
    private var repeat = Repeat.ONCE
    private var weeks = 1
    private var creating = false

    private var slots: List<AppointmentSlot> = emptyList()
    private var bookedCounts: Map<Int, Int>? = null   // null = unknown (SQL extras not installed)
    private var showAll = false

    private val databaseDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_slots)
        setupHeader(title = R.string.slot_title, subtitle = R.string.slot_subtitle)

        tileDoctor = findViewById(R.id.tileDoctor)
        tileDate = findViewById(R.id.tileDate)
        tileStart = findViewById(R.id.tileStart)
        tileEnd = findViewById(R.id.tileEnd)
        tvCapacity = findViewById(R.id.tvCapacity)
        tvWeeks = findViewById(R.id.tvWeeks)
        weeksRow = findViewById(R.id.weeksRow)
        lengthRow = findViewById(R.id.lengthRow)
        repeatRow = findViewById(R.id.repeatRow)
        summaryText = findViewById(R.id.tvBulkSummary)
        createButton = findViewById(R.id.btnCreateSlot)
        statusText = findViewById(R.id.tvSlotStatus)
        filterRow = findViewById(R.id.filterRow)
        progress = findViewById(R.id.progress)
        list = findViewById(R.id.listContainer)

        setupTile(tileDoctor, R.drawable.ic_sc_staff_small, R.string.slot_doctor, getString(R.string.slot_doctor_loading))
        setupTile(tileDate, R.drawable.ic_sc_calendar, R.string.slot_date, getString(R.string.slot_date_choose))
        setupTile(tileStart, R.drawable.ic_sc_clock, R.string.slot_start, getString(R.string.slot_time_choose))
        setupTile(tileEnd, R.drawable.ic_sc_clock, R.string.slot_end, getString(R.string.slot_time_choose))

        tileDoctor.setOnClickListener { chooseDoctor() }
        tileDate.setOnClickListener { chooseDate() }
        tileStart.setOnClickListener { chooseTime(isStart = true) }
        tileEnd.setOnClickListener { chooseTime(isStart = false) }

        findViewById<View>(R.id.btnCapacityMinus).setOnClickListener { setCapacity(capacity - 1) }
        findViewById<View>(R.id.btnCapacityPlus).setOnClickListener { setCapacity(capacity + 1) }
        setCapacity(1)

        findViewById<View>(R.id.btnWeeksMinus).setOnClickListener { setWeeks(weeks - 1) }
        findViewById<View>(R.id.btnWeeksPlus).setOnClickListener { setWeeks(weeks + 1) }
        setWeeks(1)

        createButton.setOnClickListener { createSlots() }

        drawLengthChips()
        drawRepeatChips()
        drawFilters()
        loadDoctors()
        loadSlots()
    }

    // ---------- form tiles ----------

    private fun setupTile(tile: View, @DrawableRes icon: Int, @StringRes label: Int, placeholder: String) {
        tile.findViewById<ImageView>(R.id.pickerIcon).setImageResource(icon)
        tile.findViewById<TextView>(R.id.pickerLabel).setText(label)
        setTileValue(tile, placeholder, filled = false)
    }

    private fun setTileValue(tile: View, value: String, filled: Boolean = true) {
        tile.findViewById<TextView>(R.id.pickerValue).apply {
            text = value
            setTextColor(ContextCompat.getColor(this@ManageSlotsActivity,
                if (filled) R.color.ink else R.color.ink_placeholder))
        }
    }

    private fun setCapacity(value: Int) {
        capacity = value.coerceIn(1, 999)
        tvCapacity.text = capacity.toString()
        findViewById<View>(R.id.btnCapacityMinus).alpha = if (capacity > 1) 1f else 0.4f
    }

    private fun setWeeks(value: Int) {
        weeks = value.coerceIn(1, MAX_WEEKS)
        tvWeeks.text = weeks.toString()
        findViewById<View>(R.id.btnWeeksMinus).alpha = if (weeks > 1) 1f else 0.4f
        findViewById<View>(R.id.btnWeeksPlus).alpha = if (weeks < MAX_WEEKS) 1f else 0.4f
        refreshSummary()
    }

    private fun showFormMessage(text: String?, error: Boolean = true) {
        statusText.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
        statusText.text = text
        statusText.setTextColor(ContextCompat.getColor(this,
            if (error) R.color.danger else R.color.clinic_good_text))
    }

    // ---------- slot length and repeat ----------

    private fun drawLengthChips() {
        lengthRow.removeAllViews()
        listOf<Int?>(null, 15, 20, 30, 45, 60).forEachIndexed { i, minutes ->
            val label = if (minutes == null) getString(R.string.slot_len_whole)
            else getString(R.string.slot_len_min, minutes)
            lengthRow.addView(CardKit.filterChip(this, label, minutes == lengthMinutes) {
                lengthMinutes = minutes
                drawLengthChips()
                refreshSummary()
            }, chipParams(i))
        }
    }

    private fun drawRepeatChips() {
        repeatRow.removeAllViews()
        listOf(
            Repeat.ONCE to R.string.slot_repeat_once,
            Repeat.WEEKDAYS to R.string.slot_repeat_weekdays,
            Repeat.DAILY to R.string.slot_repeat_daily
        ).forEachIndexed { i, (mode, label) ->
            repeatRow.addView(CardKit.filterChip(this, getString(label), mode == repeat) {
                repeat = mode
                weeksRow.visibility = if (mode == Repeat.ONCE) View.GONE else View.VISIBLE
                drawRepeatChips()
                refreshSummary()
            }, chipParams(i))
        }
    }

    private fun chipParams(index: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { if (index > 0) marginStart = dp(8) }

    /** Every slot the form would create right now (past times are left out). */
    private fun plan(): List<Planned> {
        val date = selectedDate ?: return emptyList()
        val start = startTime ?: return emptyList()
        val end = endTime ?: return emptyList()
        if (toMinutes(end) <= toMinutes(start)) return emptyList()

        val now = Calendar.getInstance()
        val days = if (repeat == Repeat.ONCE) 1 else weeks * 7
        val result = mutableListOf<Planned>()

        for (offset in 0 until days) {
            val day = (date.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            val weekday = day.get(Calendar.DAY_OF_WEEK)
            if (repeat == Repeat.WEEKDAYS && (weekday == Calendar.SATURDAY || weekday == Calendar.SUNDAY)) continue

            val windowEnd = combineDateAndTime(day, end)
            var cursor = combineDateAndTime(day, start)
            val length = lengthMinutes
            if (length == null) {
                result += Planned(cursor, windowEnd)
            } else {
                while (true) {
                    val next = (cursor.clone() as Calendar).apply { add(Calendar.MINUTE, length) }
                    if (next.after(windowEnd)) break
                    result += Planned(cursor, next)
                    cursor = next
                }
            }
        }
        return result.filter { it.start.after(now) }
    }

    /** "Creates 24 slots on 6 days." and the button label follow the form. */
    private fun refreshSummary() {
        if (creating) return
        val planned = plan()
        val days = planned.map { it.start.get(Calendar.DAY_OF_YEAR) to it.start.get(Calendar.YEAR) }.distinct().size
        val many = lengthMinutes != null || repeat != Repeat.ONCE

        summaryText.visibility = if (many && planned.isNotEmpty()) View.VISIBLE else View.GONE
        summaryText.text = when {
            planned.size > MAX_SLOTS -> getString(R.string.slot_err_too_many, planned.size, MAX_SLOTS)
            planned.size == 1 -> getString(R.string.slot_summary_one)
            else -> getString(R.string.slot_summary_many, planned.size, days)
        }
        createButton.text = if (planned.size > 1) getString(R.string.slot_create_many, planned.size)
        else getString(R.string.slot_create)
    }

    // ---------- pickers ----------

    private fun loadDoctors() {
        createButton.isEnabled = false
        lifecycleScope.launch {
            AppointmentRepository.getActiveDoctors()
                .onSuccess { result ->
                    doctors = result
                    if (doctors.isEmpty()) {
                        setTileValue(tileDoctor, getString(R.string.slot_doctor_none), filled = false)
                    } else {
                        // Pre-select when there is only one doctor.
                        if (doctors.size == 1) selectDoctor(0)
                        else setTileValue(tileDoctor, getString(R.string.slot_doctor_choose), filled = false)
                    }
                    createButton.isEnabled = doctors.isNotEmpty()
                }
                .onFailure { error ->
                    setTileValue(tileDoctor, getString(R.string.slot_doctor_none), filled = false)
                    showFormMessage(error.friendly())
                }
        }
    }

    private fun doctorLabel(d: AppointmentDoctor) =
        if (d.specialization.isNullOrBlank()) d.fullName else "${d.fullName} · ${d.specialization}"

    private fun selectDoctor(index: Int) {
        doctorIndex = index
        setTileValue(tileDoctor, doctorLabel(doctors[index]))
    }

    private fun chooseDoctor() {
        if (doctors.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.slot_pick_doctor)
            .setSingleChoiceItems(doctors.map { doctorLabel(it) }.toTypedArray(), doctorIndex) { dialog, which ->
                selectDoctor(which)
                showFormMessage(null)
                dialog.dismiss()
            }
            .show()
    }

    private fun chooseDate() {
        val current = selectedDate ?: Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedDate = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                setTileValue(tileDate,
                    SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(selectedDate!!.time))
                showFormMessage(null)
                refreshSummary()
            },
            current.get(Calendar.YEAR),
            current.get(Calendar.MONTH),
            current.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = Calendar.getInstance().timeInMillis - 1000
        }.show()
    }

    private fun chooseTime(isStart: Boolean) {
        val previous = if (isStart) startTime else endTime
        val fallback = if (!isStart) startTime?.let { plusMinutes(it, 30) } else null
        val initial = previous ?: fallback ?: Pair(9, 0)

        TimePickerDialog(
            this,
            { _, hour, minute ->
                val picked = Pair(hour, minute)
                if (isStart) {
                    startTime = picked
                    setTileValue(tileStart, formatTime(picked))
                    // Suggest a 30-minute slot when the end is empty or now before the start.
                    val end = endTime
                    if (end == null || toMinutes(end) <= toMinutes(picked)) {
                        endTime = plusMinutes(picked, 30)
                        setTileValue(tileEnd, formatTime(endTime!!))
                    }
                } else {
                    endTime = picked
                    setTileValue(tileEnd, formatTime(picked))
                }
                showFormMessage(null)
                refreshSummary()
            },
            initial.first,
            initial.second,
            DateFormat.is24HourFormat(this)
        ).show()
    }

    private fun toMinutes(t: Pair<Int, Int>) = t.first * 60 + t.second

    private fun plusMinutes(t: Pair<Int, Int>, minutes: Int): Pair<Int, Int> {
        val total = (toMinutes(t) + minutes).coerceAtMost(23 * 60 + 59)
        return Pair(total / 60, total % 60)
    }

    private fun formatTime(time: Pair<Int, Int>): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, time.first)
            set(Calendar.MINUTE, time.second)
        }
        return DateFormat.getTimeFormat(this).format(cal.time)
    }

    private fun combineDateAndTime(date: Calendar, time: Pair<Int, Int>): Calendar =
        (date.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, time.first)
            set(Calendar.MINUTE, time.second)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    // ---------- create ----------

    private fun createSlots() {
        val doctor = doctors.getOrNull(doctorIndex)
            ?: return showFormMessage(getString(R.string.slot_err_doctor))
        val date = selectedDate ?: return showFormMessage(getString(R.string.slot_err_date))
        val start = startTime
        val end = endTime
        if (start == null || end == null) return showFormMessage(getString(R.string.slot_err_times))
        if (toMinutes(end) <= toMinutes(start)) return showFormMessage(getString(R.string.slot_err_order))

        val planned = plan()
        if (planned.isEmpty()) {
            val simple = repeat == Repeat.ONCE && lengthMinutes == null
            return showFormMessage(getString(if (simple) R.string.slot_err_future else R.string.slot_err_none))
        }
        if (planned.size > MAX_SLOTS) {
            return showFormMessage(getString(R.string.slot_err_too_many, planned.size, MAX_SLOTS))
        }
        showFormMessage(null)

        if (planned.size == 1) {
            submit(planned, doctor, start, end)
            return
        }

        // Several slots at once: show exactly what will be created first.
        val dayFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        val range = dayFormat.format(planned.first().start.time) + " – " + dayFormat.format(planned.last().start.time)
        val lengthText = lengthMinutes?.let { getString(R.string.slot_len_each, it) } ?: getString(R.string.slot_len_one)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.slot_bulk_title, planned.size))
            .setMessage(getString(R.string.slot_bulk_msg, doctorLabel(doctor), range,
                formatTime(start), formatTime(end), lengthText))
            .setNegativeButton(R.string.clinic_cancel, null)
            .setPositiveButton(getString(R.string.slot_create_many, planned.size)) { _, _ ->
                submit(planned, doctor, start, end)
            }
            .show()
    }

    /** Creates the slots one by one, then reports how many worked and why any were skipped. */
    private fun submit(planned: List<Planned>, doctor: AppointmentDoctor, start: Pair<Int, Int>, end: Pair<Int, Int>) {
        creating = true
        createButton.isEnabled = false
        createButton.alpha = 0.6f

        lifecycleScope.launch {
            var created = 0
            var skipped = 0
            var firstError: String? = null

            planned.forEachIndexed { index, slot ->
                if (planned.size > 1) {
                    createButton.text = getString(R.string.slot_bulk_progress, index + 1, planned.size)
                } else {
                    createButton.setText(R.string.slot_creating)
                }
                AppointmentRepository.createSlot(
                    doctorId = doctor.doctorId,
                    slotStart = databaseDateFormat.format(slot.start.time),
                    slotEnd = databaseDateFormat.format(slot.end.time),
                    capacity = capacity
                )
                    .onSuccess { created++ }
                    .onFailure { error ->
                        skipped++
                        if (firstError == null) {
                            firstError = ApptKit.errorMessage(error, getString(R.string.clinic_error_generic))
                        }
                    }
            }

            when {
                created == 0 -> showFormMessage(firstError)
                skipped > 0 -> showFormMessage(getString(R.string.slot_bulk_partial, created, skipped, firstError))
                created == 1 -> showFormMessage(getString(R.string.slot_created, doctor.fullName), error = false)
                else -> showFormMessage(getString(R.string.slot_bulk_done, created), error = false)
            }

            // A single slot: move the times along so the next one can be added straight away.
            if (created == 1 && planned.size == 1) {
                startTime = end
                endTime = plusMinutes(end, toMinutes(end) - toMinutes(start))
                setTileValue(tileStart, formatTime(startTime!!))
                setTileValue(tileEnd, formatTime(endTime!!))
            }

            creating = false
            createButton.alpha = 1f
            createButton.isEnabled = doctors.isNotEmpty()
            refreshSummary()
            if (created > 0) loadSlots()
        }
    }

    // ---------- saved slots ----------

    private fun drawFilters() {
        filterRow.removeAllViews()
        listOf(false to getString(R.string.appt_filter_upcoming), true to getString(R.string.appt_filter_all))
            .forEachIndexed { i, (all, label) ->
                filterRow.addView(CardKit.filterChip(this, label, all == showAll) {
                    showAll = all
                    drawFilters()
                    drawSlots()
                }, chipParams(i))
            }
    }

    private fun loadSlots() {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val countsCall = async { AppointmentRepository.getSlotBookedCounts() }
            AppointmentRepository.getAppointmentSlots()
                .onSuccess { result ->
                    slots = result.sortedBy { it.slotStart }
                    bookedCounts = countsCall.await().getOrNull()
                    drawSlots()
                }
                .onFailure { error ->
                    list.removeAllViews()
                    list.addView(ApptKit.emptyState(this@ManageSlotsActivity, R.drawable.ic_sc_calendar,
                        getString(R.string.clinic_error_generic), error.friendly()))
                }
            progress.visibility = View.GONE
        }
    }

    private fun drawSlots() {
        list.removeAllViews()
        val shown = if (showAll) slots else slots.filter { !DateText.hasStarted(it.slotEnd) }
        if (shown.isEmpty()) {
            list.addView(ApptKit.emptyState(this, R.drawable.ic_sc_calendar,
                getString(R.string.slot_empty), getString(R.string.slot_empty_sub)))
            return
        }

        shown.groupBy { DateText.dayKey(it.slotStart) }.entries.forEachIndexed { i, (_, day) ->
            list.addView(ApptKit.sectionHeader(this,
                getString(R.string.appt_section_count, DateText.relativeDay(day.first().slotStart), day.size),
                first = i == 0))
            day.forEach { addSlotCard(it) }
        }
    }

    private fun addSlotCard(slot: AppointmentSlot) {
        val booked = bookedCounts?.let { it[slot.slotId] ?: 0 }
        val subtitle = buildString {
            append(getString(R.string.slot_capacity_value)).append(": ").append(slot.capacity)
            if (booked != null) append(" · ").append(getString(R.string.slot_booked_count, booked))
        }
        val card = ApptKit.card(
            this, slot.slotStart, slot.slotEnd,
            title = slot.doctorName,
            subtitle = subtitle,
            chip = ApptKit.statusChip(this, slot.status, ApptKit.slotTone(slot.status))
        )

        // A slot can be removed until it starts, as long as nobody has booked it.
        if (!DateText.hasStarted(slot.slotStart) && (booked ?: 0) == 0) {
            card.addView(CardKit.buttonRow(this, listOf(
                CardKit.button(this, getString(R.string.slot_remove), CardKit.ButtonStyle.DANGER) {
                    confirmRemove(slot)
                }
            )))
        }

        list.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })
    }

    private fun confirmRemove(slot: AppointmentSlot) {
        AlertDialog.Builder(this)
            .setTitle(R.string.slot_remove_title)
            .setMessage(getString(R.string.slot_remove_msg, slot.doctorName,
                DateText.slotLine(slot.slotStart, slot.slotEnd)))
            .setNegativeButton(R.string.clinic_cancel, null)
            .setPositiveButton(R.string.slot_remove) { _, _ ->
                progress.visibility = View.VISIBLE
                lifecycleScope.launch {
                    AppointmentRepository.closeSlot(slot.slotId)
                        .onSuccess {
                            toast(R.string.slot_removed)
                            loadSlots()
                        }
                        .onFailure { error ->
                            progress.visibility = View.GONE
                            AlertDialog.Builder(this@ManageSlotsActivity)
                                .setTitle(R.string.slot_remove_failed)
                                .setMessage(ApptKit.errorMessage(error, getString(R.string.clinic_error_generic)))
                                .setPositiveButton(R.string.mine_ok, null)
                                .show()
                        }
                }
            }
            .show()
    }

    private companion object {
        const val MAX_WEEKS = 8
        const val MAX_SLOTS = 200
    }
}
