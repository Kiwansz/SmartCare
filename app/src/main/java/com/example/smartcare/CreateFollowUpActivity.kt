package com.example.smartcare

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.FollowUpRepository
import com.example.smartcare.data.StaffOption
import com.example.smartcare.data.VisitOption
import com.example.smartcare.data.userMessage
import com.example.smartcare.util.DateText
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateFollowUpActivity : BaseActivity() {

    private lateinit var spVisit: Spinner
    private lateinit var spStaff: Spinner
    private lateinit var rgType: RadioGroup
    private lateinit var btnDueDate: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var tvMessage: TextView

    private val due: Calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_follow_up)
        setupHeader(R.string.clinic_form_title, R.string.clinic_form_sub)

        spVisit = findViewById(R.id.spVisit)
        spStaff = findViewById(R.id.spStaff)
        rgType = findViewById(R.id.rgType)
        btnDueDate = findViewById(R.id.btnDueDate)
        btnSave = findViewById(R.id.btnSave)
        tvMessage = findViewById(R.id.tvFormMessage)

        showDue()
        btnDueDate.setOnClickListener { pickDate() }
        btnSave.setOnClickListener { save() }
        btnSave.isEnabled = false

        loadOptions()
    }

    private fun loadOptions() {
        lifecycleScope.launch {
            val visitsResult = FollowUpRepository.loadVisitOptions()
            val staffResult = FollowUpRepository.loadStaff()

            val failure = visitsResult.exceptionOrNull() ?: staffResult.exceptionOrNull()
            if (failure != null) {
                showMessage(failure.userMessage(getString(R.string.clinic_error_generic)))
                return@launch
            }
            val visits = visitsResult.getOrThrow()
            val staff = staffResult.getOrThrow()
            when {
                visits.isEmpty() -> showMessage(getString(R.string.clinic_form_no_visits))
                staff.isEmpty() -> showMessage(getString(R.string.clinic_form_no_staff))
                else -> {
                    spVisit.adapter = spinnerAdapter(visits)
                    spStaff.adapter = spinnerAdapter(staff)
                    btnSave.isEnabled = true
                }
            }
        }
    }

    private fun <T> spinnerAdapter(options: List<T>): ArrayAdapter<T> =
        ArrayAdapter(this, android.R.layout.simple_spinner_item, options).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

    private fun pickDate() {
        val dialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                due.set(year, month, day)
                showDue()
            },
            due.get(Calendar.YEAR), due.get(Calendar.MONTH), due.get(Calendar.DAY_OF_MONTH)
        )
        dialog.datePicker.minDate = System.currentTimeMillis() - 1000L
        dialog.show()
    }

    private fun dueIso(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(due.time)

    private fun showDue() {
        btnDueDate.text = DateText.date(dueIso())
    }

    private fun save() {
        val visit = spVisit.selectedItem as? VisitOption ?: return
        val member = spStaff.selectedItem as? StaffOption ?: return
        val type = when (rgType.checkedRadioButtonId) {
            R.id.rbSms -> "SMS"
            R.id.rbRevisit -> "REVISIT"
            else -> "CALL"
        }

        showMessage(null)
        setLoading(true, btnSave)
        lifecycleScope.launch {
            FollowUpRepository.create(visit.appointmentId, type, member.staffId, dueIso())
                .onSuccess {
                    toast(R.string.clinic_form_created)
                    finish()
                }
                .onFailure {
                    setLoading(false, btnSave)
                    showMessage(it.userMessage(getString(R.string.clinic_error_generic)))
                }
        }
    }

    private fun showMessage(text: String?) {
        tvMessage.text = text
        tvMessage.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }
}