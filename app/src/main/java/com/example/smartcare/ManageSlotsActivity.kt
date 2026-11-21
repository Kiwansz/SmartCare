package com.example.smartcare

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AppointmentDoctor
import com.example.smartcare.data.AppointmentRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.graphics.Color
import android.widget.LinearLayout

class ManageSlotsActivity : BaseActivity() {

    private lateinit var doctorSpinner: Spinner
    private lateinit var dateButton: Button
    private lateinit var startButton: Button
    private lateinit var endButton: Button
    private lateinit var capacityInput: EditText
    private lateinit var createButton: Button
    private lateinit var statusText: TextView

    private lateinit var slotsContainer: LinearLayout
    private lateinit var slotsStatusText: TextView

    private var doctors: List<AppointmentDoctor> = emptyList()
    private var selectedDate: Calendar? = null
    private var startTime: Pair<Int, Int>? = null
    private var endTime: Pair<Int, Int>? = null

    private val databaseDateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_slots)

        doctorSpinner = findViewById(R.id.spinnerDoctor)
        dateButton = findViewById(R.id.btnPickDate)
        startButton = findViewById(R.id.btnPickStartTime)
        endButton = findViewById(R.id.btnPickEndTime)
        capacityInput = findViewById(R.id.etCapacity)
        createButton = findViewById(R.id.btnCreateSlot)
        statusText = findViewById(R.id.tvSlotStatus)
        slotsContainer = findViewById(R.id.slotsContainer)
        slotsStatusText = findViewById(R.id.tvSlotsStatus)

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }

        dateButton.setOnClickListener { chooseDate() }
        startButton.setOnClickListener { chooseTime(true) }
        endButton.setOnClickListener { chooseTime(false) }
        createButton.setOnClickListener { createSlot() }

        loadDoctors()
        loadSlots()
    }

    private fun loadDoctors() {
        statusText.text = "Loading active doctors..."
        createButton.isEnabled = false

        lifecycleScope.launch {
            AppointmentRepository.getActiveDoctors()
                .onSuccess { result ->
                    doctors = result

                    val labels = doctors.map { doctor ->
                        if (doctor.specialization.isNullOrBlank()) {
                            doctor.fullName
                        } else {
                            "${doctor.fullName} - ${doctor.specialization}"
                        }
                    }

                    val adapter = ArrayAdapter(
                        this@ManageSlotsActivity,
                        android.R.layout.simple_spinner_item,
                        labels
                    )

                    adapter.setDropDownViewResource(
                        android.R.layout.simple_spinner_dropdown_item
                    )

                    doctorSpinner.adapter = adapter
                    doctorSpinner.isEnabled = doctors.isNotEmpty()
                    createButton.isEnabled = doctors.isNotEmpty()

                    statusText.text = if (doctors.isEmpty()) {
                        "No active doctors were found."
                    } else {
                        "Select a doctor and enter the appointment slot details."
                    }
                }
                .onFailure { error ->
                    statusText.text = error.friendly()
                }
        }
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

                dateButton.text = SimpleDateFormat(
                    "EEE, dd MMM yyyy",
                    Locale.getDefault()
                ).format(selectedDate!!.time)
            },
            current.get(Calendar.YEAR),
            current.get(Calendar.MONTH),
            current.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun chooseTime(isStart: Boolean) {
        val now = Calendar.getInstance()
        val previous = if (isStart) startTime else endTime

        val hour = previous?.first ?: now.get(Calendar.HOUR_OF_DAY)
        val minute = previous?.second ?: now.get(Calendar.MINUTE)

        TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                val selected = Pair(selectedHour, selectedMinute)

                if (isStart) {
                    startTime = selected
                    startButton.text = formatTime(selected)
                } else {
                    endTime = selected
                    endButton.text = formatTime(selected)
                }
            },
            hour,
            minute,
            true
        ).show()
    }

    private fun formatTime(time: Pair<Int, Int>): String {
        return String.format(
            Locale.getDefault(),
            "%02d:%02d",
            time.first,
            time.second
        )
    }

    private fun combineDateAndTime(
        date: Calendar,
        time: Pair<Int, Int>
    ): Calendar {
        return (date.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, time.first)
            set(Calendar.MINUTE, time.second)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun createSlot() {
        val doctor = doctors.getOrNull(doctorSpinner.selectedItemPosition)

        if (doctor == null) {
            statusText.text = "Please select a doctor."
            return
        }

        val date = selectedDate
        if (date == null) {
            statusText.text = "Please select an appointment date."
            return
        }

        val start = startTime
        val end = endTime

        if (start == null || end == null) {
            statusText.text = "Please select both start and end times."
            return
        }

        val capacity = capacityInput.text.toString().toIntOrNull()

        if (capacity == null || capacity < 1) {
            statusText.text = "Enter a valid capacity of at least 1."
            return
        }

        val startDateTime = combineDateAndTime(date, start)
        val endDateTime = combineDateAndTime(date, end)

        if (!startDateTime.after(Calendar.getInstance())) {
            statusText.text = "The appointment must be in the future."
            return
        }

        if (!endDateTime.after(startDateTime)) {
            statusText.text = "End time must be after start time."
            return
        }

        val startString = databaseDateFormat.format(startDateTime.time)
        val endString = databaseDateFormat.format(endDateTime.time)

        createButton.isEnabled = false
        statusText.text = "Creating appointment slot..."

        lifecycleScope.launch {
            AppointmentRepository.createSlot(
                doctorId = doctor.doctorId,
                slotStart = startString,
                slotEnd = endString,
                capacity = capacity
            )
                .onSuccess { slotId ->
                    statusText.text =
                        "Appointment slot created successfully. Slot ID: $slotId"

                    toast("Appointment slot created successfully.")
                    loadSlots()
                }
                .onFailure { error ->
                    statusText.text = error.friendly()
                    toast(error.friendly())
                }

            createButton.isEnabled = doctors.isNotEmpty()
        }
    }

    private fun loadSlots() {
        slotsStatusText.text = "Loading saved slots..."

        lifecycleScope.launch {
            AppointmentRepository.getAppointmentSlots()
                .onSuccess { slots ->
                    slotsContainer.removeAllViews()

                    if (slots.isEmpty()) {
                        slotsStatusText.text = "No appointment slots found."
                        return@onSuccess
                    }

                    slotsStatusText.text = "${slots.size} saved slot(s)"

                    slots.forEach { slot ->
                        val slotCard = TextView(this@ManageSlotsActivity).apply {
                            text = """
                            Slot ID: ${slot.slotId}
                            Doctor: ${slot.doctorName}
                            Start: ${slot.slotStart.replace('T', ' ')}
                            End: ${slot.slotEnd.replace('T', ' ')}
                            Capacity: ${slot.capacity}
                            Status: ${slot.status}
                        """.trimIndent()

                            textSize = 14f
                            setTextColor(Color.rgb(16, 43, 85))
                            setBackgroundColor(Color.WHITE)
                            setPadding(dp(14), dp(14), dp(14), dp(14))

                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                bottomMargin = dp(10)
                            }
                        }

                        slotsContainer.addView(slotCard)
                    }
                }
                .onFailure { error ->
                    slotsStatusText.text = error.friendly()
                }
        }
    }
}
