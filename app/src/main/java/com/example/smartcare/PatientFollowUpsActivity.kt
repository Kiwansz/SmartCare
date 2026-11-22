package com.example.smartcare

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.PatientFollowUp
import com.example.smartcare.data.PatientFollowUpRepository
import com.example.smartcare.ui.CardKit
import com.example.smartcare.util.DateText
import kotlinx.coroutines.launch

class PatientFollowUpsActivity : ClinicListActivity() {

    override val screenTitle = R.string.clinic_pf_title
    override val screenSubtitle = R.string.clinic_pf_sub

    private var items: List<PatientFollowUp> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFilters(
            listOf(
                getString(R.string.clinic_pf_filter_upcoming),
                getString(R.string.clinic_filter_done),
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
            PatientFollowUpRepository.loadMine()
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
            showMessage(getString(R.string.clinic_pf_empty))
            return
        }
        showMessage(null)
        shown.forEach { addCard(buildCard(it)) }
    }

    private fun buildCard(item: PatientFollowUp): View {
        val card = CardKit.card(this)

        val typeLabel = getString(
            when (item.type) {
                "SMS" -> R.string.clinic_pf_type_sms
                "REVISIT" -> R.string.clinic_pf_type_revisit
                else -> R.string.clinic_pf_type_call
            }
        )
        card.addView(CardKit.title(this, typeLabel))

        val (statusText, tone) = when {
            item.isOpen -> getString(R.string.clinic_pf_status_open) to CardKit.Tone.WARN
            item.status == "COMPLETED" -> getString(R.string.clinic_pf_status_done) to CardKit.Tone.GOOD
            else -> getString(R.string.clinic_pf_status_closed) to CardKit.Tone.NEUTRAL
        }
        card.addView(CardKit.chipRow(this, CardKit.chip(this, statusText, tone)))

        if (item.isOpen) CardKit.addLine(card, "Due", item.dueDate?.let { DateText.date(it) })
        if (item.doctorName.isNotBlank()) {
            CardKit.addLine(
                card, "After your visit",
                "${item.doctorName}, ${DateText.dateTime(item.visitStart)}"
            )
        }

        val explanation = when {
            !item.isOpen -> null
            item.type == "SMS" -> R.string.clinic_pf_msg_sms
            item.type == "REVISIT" ->
                if (item.newAppointmentId != null) R.string.clinic_pf_msg_revisit_booked
                else R.string.clinic_pf_msg_revisit
            else -> R.string.clinic_pf_msg_call
        }
        explanation?.let { CardKit.addLine(card, "What happens next", getString(it)) }

        if (item.isOpen && item.type == "REVISIT" && item.newAppointmentId == null) {
            card.addView(
                CardKit.buttonRow(
                    this,
                    listOf(CardKit.button(this, getString(R.string.clinic_pf_book)) {
                        startActivity(Intent(this, PatientAppointmentsActivity::class.java))
                    })
                )
            )
        }
        return card
    }
}