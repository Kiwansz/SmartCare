package com.example.smartcare

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AppointmentRepository
import com.example.smartcare.data.AvailableAppointmentSlot
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class PatientAppointmentsActivity : BaseActivity() {

    private lateinit var slotsContainer: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var reasonInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_appointments)

        slotsContainer = findViewById(R.id.patientSlotsContainer)
        statusText = findViewById(R.id.tvPatientSlotsStatus)
        reasonInput = findViewById(R.id.etVisitReason)

        findViewById<Button>(R.id.btnPatientBack).setOnClickListener {
            finish()
        }

        loadAvailableSlots()
    }

    private fun loadAvailableSlots() {
        statusText.text = "Loading available appointments..."
        slotsContainer.removeAllViews()

        lifecycleScope.launch {
            AppointmentRepository.getAvailableSlots()
                .onSuccess { slots ->
                    slotsContainer.removeAllViews()

                    if (slots.isEmpty()) {
                        statusText.text =
                            "No available appointments at the moment."
                        return@onSuccess
                    }

                    statusText.text = "${slots.size} available slot(s)"

                    slots.forEach { slot ->
                        addSlotCard(slot)
                    }
                }
                .onFailure { error ->
                    statusText.text = error.friendly()
                }
        }
    }

    private fun addSlotCard(slot: AvailableAppointmentSlot) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(14), dp(14), dp(14), dp(14))

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }

        val details = TextView(this).apply {
            text = buildString {
                appendLine("Doctor: ${slot.doctorName}")
                slot.specialization?.let {
                    appendLine("Specialization: $it")
                }
                appendLine("Date: ${formatDateTime(slot.slotStart)}")
                appendLine("End: ${formatDateTime(slot.slotEnd)}")
                append("Capacity: ${slot.capacity}")
            }
            textSize = 14f
            setTextColor(Color.rgb(16, 43, 85))
        }

        val bookButton = Button(this).apply {
            text = "Book This Slot"
            setOnClickListener {
                bookSlot(slot, this)
            }
        }

        card.addView(details)
        card.addView(bookButton)
        slotsContainer.addView(card)
    }

    private fun formatDateTime(value: String): String {
        return try {
            val input = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss",
                Locale.US
            )
            val output = SimpleDateFormat(
                "EEE, dd MMM yyyy  HH:mm",
                Locale.getDefault()
            )
            output.format(input.parse(value)!!)
        } catch (e: Exception) {
            value.replace('T', ' ')
        }
    }

    private fun bookSlot(
        slot: AvailableAppointmentSlot,
        button: Button
    ) {
        button.isEnabled = false
        statusText.text = "Booking your appointment..."

        val reason = reasonInput.text.toString()
            .trim()
            .takeIf { it.isNotEmpty() }

        lifecycleScope.launch {
            AppointmentRepository.bookAppointment(
                slotId = slot.slotId,
                visitReason = reason
            )
                .onSuccess { confirmation ->
                    AlertDialog.Builder(this@PatientAppointmentsActivity)
                        .setTitle("Appointment booked successfully")
                        .setMessage(
                            "Appointment ID: ${confirmation.appointmentId}\n\n" +
                                    "Your check-in code:\n${confirmation.checkinCode}"
                        )
                        .setPositiveButton("OK") { _, _ ->
                            loadAvailableSlots()
                        }
                        .setCancelable(false)
                        .show()
                }
                .onFailure { error ->
                    statusText.text = error.friendly()
                    button.isEnabled = true
                }
        }
    }
}
