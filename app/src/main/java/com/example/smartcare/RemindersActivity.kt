package com.example.smartcare

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.ReminderItem
import com.example.smartcare.data.ReminderRepository
import com.example.smartcare.ui.CardKit
import com.example.smartcare.util.DateText
import kotlinx.coroutines.launch

/**
 * Reminder (staff). Schedules a reminder before each upcoming appointment and shows
 * every reminder as pending, sent or failed (with the reason when it failed).
 */
class RemindersActivity : ClinicListActivity() {

    override val screenTitle = R.string.clinic_reminders_title
    override val screenSubtitle = R.string.clinic_reminders_sub

    private var items: List<ReminderItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFilters(
            listOf(
                getString(R.string.clinic_filter_all),
                getString(R.string.clinic_filter_pending),
                getString(R.string.clinic_filter_sent),
                getString(R.string.clinic_filter_failed)
            )
        ) { render() }
        setPrimaryButton(getString(R.string.clinic_schedule_button)) { chooseChannel() }
        setSecondaryButton(getString(R.string.clinic_process_button)) { confirmProcess() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        showLoading(true)
        lifecycleScope.launch {
            ReminderRepository.load()
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
        val wanted = when (filterIndex) {
            1 -> "PENDING"
            2 -> "SENT"
            3 -> "FAILED"
            else -> null
        }
        val shown = if (wanted == null) items else items.filter { it.status.uppercase() == wanted }
        if (shown.isEmpty()) {
            showMessage(getString(R.string.clinic_reminders_empty))
            return
        }
        showMessage(resources.getQuantityString(R.plurals.clinic_count_reminders, shown.size, shown.size))
        shown.forEach { addCard(buildCard(it)) }
    }

    private fun buildCard(item: ReminderItem): View {
        val card = CardKit.card(this)
        card.addView(CardKit.title(this, item.patientName))
        card.addView(
            CardKit.chipRow(
                this,
                CardKit.chip(this, DateText.pretty(item.status), CardKit.toneFor(item.status)),
                CardKit.chip(this, DateText.pretty(item.channel), CardKit.Tone.NEUTRAL)
            )
        )
        CardKit.addLine(card, "Appointment", DateText.dateTime(item.appointmentStart).takeIf { item.appointmentStart.isNotBlank() })
        CardKit.addLine(card, "Send at", item.scheduledTime?.let { DateText.dateTime(it) })
        CardKit.addLine(card, "Sent at", item.sentTime?.let { DateText.dateTime(it) })
        if (item.status.uppercase() == "FAILED") {
            CardKit.addLine(card, "Reason", item.failureReason, danger = true)
        }
        return card
    }

    // ---------- schedule ----------

    private fun chooseChannel() {
        val labels = arrayOf(
            getString(R.string.clinic_channel_push),
            getString(R.string.clinic_channel_sms),
            getString(R.string.clinic_channel_email)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.clinic_choose_channel)
            .setItems(labels) { _, which -> chooseLead(ReminderRepository.CHANNELS[which]) }
            .setNegativeButton(R.string.clinic_cancel, null)
            .show()
    }

    private fun chooseLead(channel: String) {
        val hours = intArrayOf(24, 2, 1)
        val labels = arrayOf(
            getString(R.string.clinic_lead_24),
            getString(R.string.clinic_lead_2),
            getString(R.string.clinic_lead_1)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.clinic_choose_lead)
            .setItems(labels) { _, which -> schedule(channel, hours[which]) }
            .setNegativeButton(R.string.clinic_cancel, null)
            .show()
    }

    private fun schedule(channel: String, hoursBefore: Int) {
        runAction({ ReminderRepository.schedule(hoursBefore, channel) }) { created ->
            toast(
                if (created > 0) getString(R.string.clinic_scheduled_done, created)
                else getString(R.string.clinic_scheduled_none)
            )
            load()
        }
    }

    // ---------- process ----------

    private fun confirmProcess() = confirm(
        getString(R.string.clinic_process_title),
        getString(R.string.clinic_process_body),
        getString(R.string.clinic_process_confirm)
    ) {
        runAction({ ReminderRepository.processDue() }) { result ->
            toast(getString(R.string.clinic_processed_done, result.sent, result.failed))
            load()
        }
    }
}
