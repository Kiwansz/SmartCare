package com.example.smartcare

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AttendanceItem
import com.example.smartcare.data.AttendanceRepository
import com.example.smartcare.ui.CardKit
import com.example.smartcare.util.DateText
import kotlinx.coroutines.launch

/**
 * Attendance (staff). Lists appointments with their check-in state and lets staff
 * mark a no-show, decide a late check-in, or mark a checked-in visit as completed.
 */
class AttendanceActivity : ClinicListActivity() {

    override val screenTitle = R.string.clinic_attendance_title
    override val screenSubtitle = R.string.clinic_attendance_sub

    private var items: List<AttendanceItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFilters(
            listOf(
                getString(R.string.clinic_filter_today),
                getString(R.string.clinic_filter_review),
                getString(R.string.clinic_filter_all)
            )
        ) { render() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        showLoading(true)
        lifecycleScope.launch {
            AttendanceRepository.loadBoard()
                .onSuccess {
                    items = it
                    showLoading(false)
                    render()
                }
                .onFailure { showLoadError(it) }
        }
    }

    private fun render() {
        clearCards()
        val shown = when (filterIndex) {
            0 -> items.filter {
                DateText.isToday(it.slotStart) ||
                        it.attendanceStatus == AttendanceRepository.PENDING_VERIFICATION
            }
            1 -> items.filter { it.attendanceStatus == AttendanceRepository.PENDING_VERIFICATION }
            else -> items
        }
        if (shown.isEmpty()) {
            showMessage(
                getString(
                    when (filterIndex) {
                        0 -> R.string.clinic_attendance_empty_today
                        1 -> R.string.clinic_attendance_empty_review
                        else -> R.string.clinic_attendance_empty_all
                    }
                )
            )
            return
        }
        showMessage(resources.getQuantityString(R.plurals.clinic_count_appointments, shown.size, shown.size))
        shown.forEach { addCard(buildCard(it)) }
    }

    private fun buildCard(item: AttendanceItem): View {
        val card = CardKit.card(this)
        card.addView(CardKit.title(this, item.patientName))

        val active = item.bookingStatus in AttendanceRepository.ACTIVE_BOOKING

        val attendanceChip = when {
            item.attendanceStatus != null -> CardKit.chip(
                this, DateText.pretty(item.attendanceStatus), CardKit.toneFor(item.attendanceStatus)
            )
            active -> CardKit.chip(this, getString(R.string.clinic_not_arrived), CardKit.Tone.NEUTRAL)
            else -> CardKit.chip(this, getString(R.string.clinic_no_record), CardKit.Tone.NEUTRAL)
        }
        val chips = mutableListOf(attendanceChip)
        if (item.bookingStatus != item.attendanceStatus) {
            chips += CardKit.chip(
                this, DateText.pretty(item.bookingStatus), CardKit.toneFor(item.bookingStatus)
            )
        }
        card.addView(CardKit.chipRow(this, *chips.toTypedArray()))

        CardKit.addLine(card, "Doctor", item.doctorName)
        CardKit.addLine(card, "Appointment", DateText.dateTime(item.slotStart))
        CardKit.addLine(card, "Checked in at", item.checkinTime?.let { DateText.dateTime(it) })

        val attendanceId = item.attendanceId
        val buttons = mutableListOf<View>()
        when {
            item.attendanceStatus == AttendanceRepository.PENDING_VERIFICATION && attendanceId != null -> {
                buttons += CardKit.button(this, getString(R.string.clinic_action_accept)) {
                    accept(item, attendanceId)
                }
                buttons += CardKit.button(
                    this, getString(R.string.clinic_action_reject), CardKit.ButtonStyle.DANGER
                ) { reject(item, attendanceId) }
            }
            item.attendanceStatus == AttendanceRepository.CHECKED_IN && active ->
                buttons += CardKit.button(this, getString(R.string.clinic_action_complete)) {
                    complete(item)
                }
            item.attendanceStatus == null && active && DateText.hasStarted(item.slotStart) ->
                buttons += CardKit.button(
                    this, getString(R.string.clinic_action_no_show), CardKit.ButtonStyle.DANGER
                ) { noShow(item) }
        }
        if (buttons.isNotEmpty()) card.addView(CardKit.buttonRow(this, buttons))
        return card
    }

    private fun noShow(item: AttendanceItem) = confirm(
        getString(R.string.clinic_confirm_no_show_title),
        getString(R.string.clinic_confirm_no_show_body, item.patientName),
        getString(R.string.clinic_action_no_show)
    ) {
        runAction({ AttendanceRepository.markNoShow(item.appointmentId) }) {
            toast(R.string.clinic_done_no_show)
            load()
        }
    }

    private fun accept(item: AttendanceItem, attendanceId: Int) = confirm(
        getString(R.string.clinic_confirm_accept_title),
        getString(R.string.clinic_confirm_accept_body, item.patientName),
        getString(R.string.clinic_action_accept)
    ) {
        runAction({ AttendanceRepository.resolvePending(attendanceId, accept = true) }) {
            toast(R.string.clinic_done_accepted)
            load()
        }
    }

    private fun reject(item: AttendanceItem, attendanceId: Int) = confirm(
        getString(R.string.clinic_confirm_reject_title),
        getString(R.string.clinic_confirm_reject_body, item.patientName),
        getString(R.string.clinic_action_reject)
    ) {
        runAction({ AttendanceRepository.resolvePending(attendanceId, accept = false) }) {
            toast(R.string.clinic_done_rejected)
            load()
        }
    }

    private fun complete(item: AttendanceItem) = confirm(
        getString(R.string.clinic_confirm_complete_title),
        getString(R.string.clinic_confirm_complete_body, item.patientName),
        getString(R.string.clinic_action_complete)
    ) {
        runAction({ AttendanceRepository.completeVisit(item.appointmentId) }) {
            toast(R.string.clinic_done_completed)
            load()
        }
    }
}
