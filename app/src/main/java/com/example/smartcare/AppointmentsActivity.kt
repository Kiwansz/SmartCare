
package com.example.smartcare

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AppointmentRepository
import com.example.smartcare.data.StaffAppointment
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class AppointmentsActivity : BaseActivity() {

    private lateinit var btnViewAppointments: Button
    private lateinit var tvAppointmentStatus: TextView
    private lateinit var tvAppointmentEmpty: TextView
    private lateinit var appointmentsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_appointments)

        findViewById<Button>(R.id.btnManageSlots)
            .setOnClickListener {
                startActivity(
                    android.content.Intent(
                        this,
                        ManageSlotsActivity::class.java
                    )
                )
            }

        btnViewAppointments =
            findViewById(R.id.btnViewAppointments)

        tvAppointmentStatus =
            findViewById(R.id.tvAppointmentStatus)

        tvAppointmentEmpty =
            findViewById(R.id.tvAppointmentEmpty)

        appointmentsContainer =
            findViewById(R.id.staffAppointmentsContainer)

        btnViewAppointments.setOnClickListener {
            loadAppointments()
        }
    }

    private fun loadAppointments() {
        btnViewAppointments.isEnabled = false

        tvAppointmentStatus.text = "Loading appointments..."
        tvAppointmentEmpty.visibility = View.GONE
        appointmentsContainer.removeAllViews()

        lifecycleScope.launch {

            val result = AppointmentRepository.getAllAppointments()

            result.onSuccess { appointments ->

                if (appointments.isEmpty()) {
                    tvAppointmentStatus.text = "0 appointments found"
                    tvAppointmentEmpty.text =
                        "No appointment records are available."
                    tvAppointmentEmpty.visibility = View.VISIBLE

                } else {
                    tvAppointmentStatus.text =
                        "${appointments.size} appointment(s) found"

                    tvAppointmentEmpty.visibility = View.GONE

                    appointments.forEach { appointment ->
                        addAppointmentCard(appointment)
                    }
                }

            }.onFailure { error ->

                tvAppointmentStatus.text =
                    "Unable to load appointments"

                tvAppointmentEmpty.text =
                    error.localizedMessage
                        ?: "Please check your connection and try again."

                tvAppointmentEmpty.visibility = View.VISIBLE
            }

            btnViewAppointments.isEnabled = true
        }
    }

    private fun addAppointmentCard(
        appointment: StaffAppointment
    ) {

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(14),
                dp(12),
                dp(14),
                dp(12)
            )

            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(10).toFloat()
                setStroke(
                    dp(1),
                    Color.parseColor("#DCE3EC")
                )
            }
        }

        addDetail(
            card,
            "Appointment ID",
            appointment.appointmentId.toString(),
            true
        )

        addDetail(
            card,
            "Patient",
            appointment.patientName
        )

        addDetail(
            card,
            "Patient ID",
            appointment.patientId.toString()
        )

        addDetail(
            card,
            "Doctor",
            appointment.doctorName
        )

        addDetail(
            card,
            "Specialization",
            appointment.specialization ?: "Not specified"
        )

        addDetail(
            card,
            "Date",
            formatDateTime(appointment.slotStart)
        )

        addDetail(
            card,
            "End",
            formatDateTime(appointment.slotEnd)
        )

        addDetail(
            card,
            "Status",
            appointment.bookingStatus,
            true
        )

        addDetail(
            card,
            "Reason",
            appointment.visitReason ?: "Not specified"
        )

        addDetail(
            card,
            "Check-in code",
            appointment.checkinCode
        )

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(12)
        }

        appointmentsContainer.addView(card, params)
    }

    private fun addDetail(
        card: LinearLayout,
        label: String,
        value: String,
        bold: Boolean = false
    ) {
        val textView = TextView(this).apply {
            text = "$label: $value"
            textSize = 14f
            setTextColor(
                Color.parseColor("#102B55")
            )
            setPadding(0, dp(3), 0, dp(3))

            if (bold) {
                setTypeface(null, Typeface.BOLD)
            }
        }

        card.addView(textView)
    }

    private fun formatDateTime(value: String): String {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )

        for (pattern in patterns) {
            try {
                val inputFormat =
                    SimpleDateFormat(pattern, Locale.getDefault())

                val date = inputFormat.parse(value)

                if (date != null) {
                    val outputFormat =
                        SimpleDateFormat(
                            "EEE, dd MMM yyyy hh:mm a",
                            Locale.getDefault()
                        )

                    return outputFormat.format(date)
                }
            } catch (_: Exception) {
                // Try the next timestamp format.
            }
        }

        return value.replace("T", " ")
    }
}
