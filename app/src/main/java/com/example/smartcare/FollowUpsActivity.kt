package com.example.smartcare

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.FollowUpItem
import com.example.smartcare.data.FollowUpRepository
import com.example.smartcare.ui.CardKit
import com.example.smartcare.ui.CardKit.chip
import com.example.smartcare.util.DateText
import kotlinx.coroutines.launch

/**
 * Follow-up (staff and doctors). Lists follow-ups, opens the create form, and lets
 * staff record the result (completed or unsuccessful) or link a revisit to a new appointment.
 */
class FollowUpsActivity : ClinicListActivity() {

    override val screenTitle = R.string.clinic_followups_title
    override val screenSubtitle = R.string.clinic_followups_sub

    private var items: List<FollowUpItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFilters(
            listOf(
                getString(R.string.clinic_filter_open),
                getString(R.string.clinic_filter_done),
                getString(R.string.clinic_filter_all)
            )
        ) { render() }
        setPrimaryButton(getString(R.string.clinic_new_followup)) {
            startActivity(Intent(this, CreateFollowUpActivity::class.java))
        }
    }

    /** Also reloads when coming back from the create form. */
    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        showLoading(true)
        lifecycleScope.launch {
            FollowUpRepository.load()
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
            0 -> items.filter { it.isOpen }
            1 -> items.filter { !it.isOpen }
            else -> items
        }
        if (shown.isEmpty()) {
            showMessage(getString(R.string.clinic_followups_empty))
            return
        }
        showMessage(resources.getQuantityString(R.plurals.clinic_count_followups, shown.size, shown.size))
        shown.forEach { addCard(buildCard(it)) }
    }

    private fun buildCard(item: FollowUpItem): View {
        val card = CardKit.card(this)
        card.addView(CardKit.title(this, item.patientName))

        val chips = mutableListOf(
            chip(this, DateText.pretty(item.type), CardKit.Tone.NEUTRAL),
            chip(this, DateText.pretty(item.status), CardKit.toneFor(item.status))
        )
        if (item.isOpen && DateText.isBeforeToday(item.dueDate)) {
            chips += chip(this, getString(R.string.clinic_overdue), CardKit.Tone.BAD)
        }
        card.addView(CardKit.chipRow(this, *chips.toTypedArray()))

        CardKit.addLine(card, "Due", item.dueDate?.let { DateText.date(it) })
        CardKit.addLine(card, "Assigned to", item.assignedStaffName)
        CardKit.addLine(card, "Result", item.resultNote)
        CardKit.addLine(card, "New appointment", item.newAppointmentId?.let { "#$it" })

        val buttons = mutableListOf<View>()
        if (item.isOpen) {
            buttons += CardKit.button(this, getString(R.string.clinic_action_record_result)) {
                chooseResult(item)
            }
        }
        if (item.type == "REVISIT" && item.newAppointmentId == null) {
            buttons += CardKit.button(
                this, getString(R.string.clinic_action_link), CardKit.ButtonStyle.OUTLINED
            ) { link(item) }
        }
        if (buttons.isNotEmpty()) card.addView(CardKit.buttonRow(this, buttons))
        return card
    }

    // ---------- record the result ----------

    private fun chooseResult(item: FollowUpItem) {
        val options = arrayOf(
            getString(R.string.clinic_result_completed),
            getString(R.string.clinic_result_unsuccessful)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.clinic_result_title)
            .setItems(options) { _, which -> askNote(item, completed = which == 0) }
            .setNegativeButton(R.string.clinic_cancel, null)
            .show()
    }

    private fun askNote(item: FollowUpItem, completed: Boolean) {
        val input = EditText(this).apply {
            hint = getString(R.string.clinic_result_note_hint)
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2
        }
        val holder = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(
                input,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        AlertDialog.Builder(this)
            .setTitle(if (completed) R.string.clinic_result_completed else R.string.clinic_result_unsuccessful)
            .setView(holder)
            .setNegativeButton(R.string.clinic_cancel, null)
            .setPositiveButton(R.string.clinic_save) { _, _ ->
                runAction({
                    FollowUpRepository.recordResult(item.followUpId, completed, input.text.toString())
                }) {
                    toast(R.string.clinic_result_saved)
                    load()
                }
            }
            .show()
    }

    // ---------- link a revisit to its new appointment ----------

    private fun link(item: FollowUpItem) {
        val patientId = item.patientId
        if (patientId == null) {
            toast(R.string.clinic_link_no_patient)
            return
        }
        runAction({ FollowUpRepository.loadLinkableAppointments(patientId) }) { options ->
            if (options.isEmpty()) {
                toast(R.string.clinic_link_none)
                return@runAction
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.clinic_link_title)
                .setItems(options.map { it.label }.toTypedArray()) { _, which ->
                    runAction({
                        FollowUpRepository.linkAppointment(item.followUpId, options[which].appointmentId)
                    }) {
                        toast(R.string.clinic_link_saved)
                        load()
                    }
                }
                .setNegativeButton(R.string.clinic_cancel, null)
                .show()
        }
    }
}
