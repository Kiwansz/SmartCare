
package com.example.smartcare

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AppointmentRepository
import com.example.smartcare.data.AvailableAppointmentSlot
import com.example.smartcare.data.PatientAppointment
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class MyAppointmentsActivity : BaseActivity() {

    private lateinit var appointmentsContainer: LinearLayout
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_appointments)

        appointmentsContainer =
            findViewById(R.id.myAppointmentsContainer)

        statusText =
            findViewById(R.id.tvMyAppointmentsStatus)

        findViewById<Button>(R.id.btnAppointmentsBack)
            .setOnClickListener {
                finish()
            }

        findViewById<Button>(R.id.btnRefreshAppointments)
            .setOnClickListener {
                loadAppointments()
            }

        loadAppointments()
    }

    // Load appointments belonging to the logged-in patient.
    private fun loadAppointments() {
        statusText.text = "Loading your appointments..."
        appointmentsContainer.removeAllViews()

        lifecycleScope.launch {
            AppointmentRepository.getMyAppointments()
                .onSuccess { appointments ->

                    appointmentsContainer.removeAllViews()

                    if (appointments.isEmpty()) {
                        statusText.text =
                            "You don't have any appointments yet."
                        return@onSuccess
                    }

                    statusText.text =
                        "${appointments.size} appointment(s) found"

                    appointments.forEach { appointment ->
                        addAppointmentCard(appointment)
                    }
                }
                .onFailure { error ->
                    statusText.text = safeErrorMessage(
                        error,
                        "Could not load appointments. Please try again."
                    )
                }
        }
    }

    // Display appointment details and available actions.
    private fun addAppointmentCard(
        appointment: PatientAppointment
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(16), dp(16), dp(16), dp(16))

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        }

        val details = TextView(this).apply {
            text = buildString {
                appendLine("Appointment ID: ${appointment.appointmentId}")
                appendLine("Doctor: ${appointment.doctorName}")

                appointment.specialization?.let {
                    appendLine("Specialization: $it")
                }

                appendLine(
                    "Date: ${formatDateTime(appointment.slotStart)}"
                )

                appendLine(
                    "End: ${formatDateTime(appointment.slotEnd)}"
                )

                appendLine(
                    "Status: ${appointment.bookingStatus.replace("_", " ")}"
                )

                appendLine(
                    "Reason: ${appointment.visitReason ?: "Not specified"}"
                )

                appendLine()
                append("Check-in code: ${appointment.checkinCode}")
            }

            textSize = 14f
            setTextColor(Color.rgb(16, 43, 85))
        }

        card.addView(details)

        // Only active appointments can be cancelled or rescheduled.
        if (appointment.bookingStatus in listOf("BOOKED", "CONFIRMED")) {

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(12)
                }
            }

            val rescheduleButton = Button(this).apply {
                text = "Reschedule"

                setOnClickListener {
                    showRescheduleDialog(appointment, this)
                }
            }

            val cancelButton = Button(this).apply {
                text = "Cancel"

                setOnClickListener {
                    confirmCancel(appointment, this)
                }
            }

            actions.addView(
                rescheduleButton,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginEnd = dp(6)
                }
            )

            actions.addView(
                cancelButton,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = dp(6)
                }
            )

            card.addView(actions)
        }

        appointmentsContainer.addView(card)
    }

    // Ask the patient to confirm cancellation.
    private fun confirmCancel(
        appointment: PatientAppointment,
        button: Button
    ) {
        AlertDialog.Builder(this)
            .setTitle("Cancel appointment?")
            .setMessage(
                "Are you sure you want to cancel appointment " +
                        "${appointment.appointmentId}?\n\n" +
                        "The clinic's cancellation cut-off applies."
            )
            .setNegativeButton("Keep appointment", null)
            .setPositiveButton("Cancel appointment") { _, _ ->

                button.isEnabled = false
                statusText.text = "Cancelling appointment..."

                lifecycleScope.launch {
                    AppointmentRepository.cancelAppointment(
                        appointmentId = appointment.appointmentId,
                        reason = null
                    )
                        .onSuccess {
                            AlertDialog.Builder(
                                this@MyAppointmentsActivity
                            )
                                .setTitle("Appointment cancelled")
                                .setMessage(
                                    "Your appointment has been cancelled successfully."
                                )
                                .setPositiveButton("OK") { _, _ ->
                                    loadAppointments()
                                }
                                .show()
                        }
                        .onFailure { error ->

                            button.isEnabled = true

                            val message = safeErrorMessage(
                                error,
                                "Cancellation failed. Please try again."
                            )

                            showErrorDialog(
                                "Cancellation unsuccessful",
                                message
                            )
                        }
                }
            }
            .show()
    }

    // Load alternative appointment slots.
    private fun showRescheduleDialog(
        appointment: PatientAppointment,
        button: Button
    ) {
        button.isEnabled = false
        statusText.text = "Loading available appointment slots..."

        lifecycleScope.launch {
            AppointmentRepository.getAvailableSlots()
                .onSuccess { slots ->

                    button.isEnabled = true

                    val alternatives = slots.filter {
                        it.slotId != appointment.slotId
                    }

                    if (alternatives.isEmpty()) {
                        AlertDialog.Builder(this@MyAppointmentsActivity)
                            .setTitle("No alternative slots")
                            .setMessage(
                                "There are currently no other available appointments."
                            )
                            .setPositiveButton("OK", null)
                            .show()

                        return@onSuccess
                    }

                    val labels = alternatives.map { slot ->
                        "${slot.doctorName} - " +
                                "${formatDateTime(slot.slotStart)} to " +
                                "${formatDateTime(slot.slotEnd)}"
                    }

                    AlertDialog.Builder(this@MyAppointmentsActivity)
                        .setTitle("Choose a new appointment")
                        .setItems(labels.toTypedArray()) { _, position ->

                            confirmReschedule(
                                appointment,
                                alternatives[position],
                                button
                            )
                        }
                        .setNegativeButton("Close", null)
                        .show()
                }
                .onFailure { error ->

                    button.isEnabled = true

                    statusText.text = safeErrorMessage(
                        error,
                        "Could not load available slots. Please try again."
                    )
                }
        }
    }

    // Ask the patient to confirm the replacement appointment.
    private fun confirmReschedule(
        appointment: PatientAppointment,
        newSlot: AvailableAppointmentSlot,
        button: Button
    ) {
        AlertDialog.Builder(this)
            .setTitle("Confirm rescheduling")
            .setMessage(
                "Your current appointment will be replaced with:\n\n" +
                        "${newSlot.doctorName}\n" +
                        "${formatDateTime(newSlot.slotStart)}\n\n" +
                        "A new check-in code will be generated."
            )
            .setNegativeButton("Go back", null)
            .setPositiveButton("Confirm") { _, _ ->

                button.isEnabled = false
                statusText.text = "Rescheduling appointment..."

                lifecycleScope.launch {
                    AppointmentRepository.rescheduleAppointment(
                        appointmentId = appointment.appointmentId,
                        newSlotId = newSlot.slotId
                    )
                        .onSuccess { confirmation ->

                            AlertDialog.Builder(
                                this@MyAppointmentsActivity
                            )
                                .setTitle("Rescheduled successfully")
                                .setMessage(
                                    "Original appointment ID: " +
                                            "${confirmation.oldAppointmentId}\n\n" +
                                            "New appointment ID: " +
                                            "${confirmation.newAppointmentId}\n\n" +
                                            "New check-in code:\n" +
                                            confirmation.checkinCode
                                )
                                .setPositiveButton("OK") { _, _ ->
                                    loadAppointments()
                                }
                                .setCancelable(false)
                                .show()
                        }
                        .onFailure { error ->

                            button.isEnabled = true

                            val message = safeErrorMessage(
                                error,
                                "Rescheduling failed. Please try again."
                            )

                            showErrorDialog(
                                "Rescheduling unsuccessful",
                                message
                            )
                        }
                }
            }
            .show()
    }

    // Display a safe, user-friendly error dialog.
    private fun showErrorDialog(
        title: String,
        message: String
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    // Remove technical request details from Supabase errors.
    private fun safeErrorMessage(
        error: Throwable,
        fallback: String
    ): String {

        val raw = error.localizedMessage
            ?.takeIf { it.isNotBlank() }
            ?: return fallback

        val technicalDetails = Regex(
            "(?i)\\b(Code|Hint|Details|URL|Headers):"
        )

        val marker = technicalDetails.find(raw)

        val message = if (marker != null) {
            raw.substring(0, marker.range.first).trim()
        } else {
            raw.trim()
        }

        if (message.isBlank()) return fallback

        // Never display a message containing sensitive request details.
        val sensitiveContent = Regex(
            "(?i)authorization|bearer\\s|https?://|supabase\\.co|postgrest"
        )

        if (sensitiveContent.containsMatchIn(message)) {
            return fallback
        }

        return message
    }

    // Format database timestamps for display.
    private fun formatDateTime(value: String): String {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )

        for (pattern in formats) {
            try {
                val input = SimpleDateFormat(pattern, Locale.US)
                val date = input.parse(value) ?: continue

                val output = SimpleDateFormat(
                    "EEE, dd MMM yyyy  hh:mm a",
                    Locale.getDefault()
                )

                return output.format(date)

            } catch (_: Exception) {
                // Try the next supported timestamp format.
            }
        }

        return value.replace('T', ' ')
    }
}
