package com.example.smartcare

import android.content.Context
import android.net.Uri
import android.widget.ImageView
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.time.DateTimeException
import java.util.UUID
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AuthRepository
import com.example.smartcare.ui.SmartField
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Documents shown here are fictional. Drafts and previews exist only in this activity. */
open class MedicalDocumentActivity : BaseActivity() {
    protected open val doctor = false
    private val prescription by lazy { intent.getBooleanExtra(EXTRA_PRESCRIPTION, false) }
    private val editor by lazy { findViewById<View>(R.id.documentEditor) }
    private val results by lazy { findViewById<LinearLayout>(R.id.documentResults) }
    private val medicationItems by lazy { findViewById<LinearLayout>(R.id.documentMedicationItems) }

    private val reference = "DEMO-${UUID.randomUUID()}"
    private var pendingExport: SampleMedicalCertificate? = null
    private val exportPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val certificate = pendingExport
        pendingExport = null
        if (uri != null && certificate != null) writePdf(uri, certificate)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medical_document)
        setupHeader(
            if (prescription) R.string.document_prescription else R.string.document_certificate,
            R.string.medical_records_sample
        )
        findViewById<View>(R.id.documentRetry).setOnClickListener { load() }
        findViewById<View>(R.id.documentEditor).isSaveFromParentEnabled = false
        load()
    }

    private fun load() {
        val progress = findViewById<View>(R.id.progress)
        val error = findViewById<View>(R.id.documentError)
        progress.isVisible = true
        error.isVisible = false
        lifecycleScope.launch {
            val account = AuthRepository.loadCurrentAccount().getOrElse {
                progress.isVisible = false
                error.isVisible = true
                return@launch
            }
            if (account == null) {
                goToRolePicker()
                return@launch
            }
            if (account.role != (if (doctor) "DOCTOR" else "PATIENT")) {
                finish()
                return@launch
            }
            progress.isVisible = false
            if (doctor) setupEditor() else setupPatientView()
        }
    }

    private fun setupEditor() {
        editor.isVisible = true
        findViewById<View>(R.id.documentPrescriptionForm).isVisible = prescription
        findViewById<View>(R.id.documentCertificateForm).isVisible = !prescription
        if (prescription) {
            addMedication()
            findViewById<View>(R.id.documentAddMedication).setOnClickListener { addMedication() }
        } else {
            setupCertificateFields()
        }
        findViewById<View>(R.id.documentPreview).setOnClickListener {
            if (prescription) {
                readPrescription()?.let { showPreview(it) }
            } else {
                readCertificate()?.let { certificate ->
                    showPreview(listOf(certificateRows(certificate)))
                    showPdfActions(certificate)
                }
            }
        }
    }

    private fun showPreview(sections: List<List<DocumentRow>>) {
        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(editor.windowToken, 0)
        val panel = findViewById<View>(R.id.documentPreviewPanel)
        val heading = findViewById<View>(R.id.documentPreviewHeading)
        findViewById<View>(R.id.documentEdit).setOnClickListener {
            panel.isVisible = false
            results.isVisible = false
            findViewById<View>(R.id.documentPdfActions).isVisible = false
            editor.isVisible = true
        }
        render(sections)
        editor.isVisible = false
        ViewCompat.setAccessibilityPaneTitle(panel, getString(R.string.document_preview_heading))
        panel.isVisible = true
        results.isVisible = true
        heading.post {
            heading.requestRectangleOnScreen(Rect(0, 0, heading.width, heading.height))
        }
    }

    private fun addMedication() {
        val item = layoutInflater.inflate(R.layout.item_medication_editor, medicationItems, false)
        item.findViewById<View>(R.id.documentRemoveMedication).setOnClickListener {
            medicationItems.removeView(item)
            updateMedicationControls()
        }
        item.findViewById<View>(R.id.documentQuickSelect).setOnClickListener {
            quickSelect(item.findViewById(R.id.documentMedicineName))
        }
        medicationItems.addView(item)
        updateMedicationControls()
    }

    private fun updateMedicationControls() {
        for (index in 0 until medicationItems.childCount) {
            val item = medicationItems.getChildAt(index)
            item.findViewById<TextView>(R.id.documentMedicationHeading).text =
                getString(R.string.document_medication_number, index + 1)
            item.findViewById<View>(R.id.documentRemoveMedication).isEnabled =
                medicationItems.childCount > 1
        }
    }

    private fun readPrescription(): List<List<DocumentRow>>? {
        val sections = (0 until medicationItems.childCount).map { index ->
            val item = medicationItems.getChildAt(index)
            medicationFields.map { (id, label) ->
                required(item.findViewById(id))?.let { DocumentRow(label, it) }
            }
        }
        if (sections.any { rows -> rows.any { it == null } }) return null
        return sections.map { it.filterNotNull() }
    }

    private fun quickSelect(target: SmartField) {
        val picker = layoutInflater.inflate(R.layout.dialog_demo_medication, null)
        val search = picker.findViewById<SmartField>(R.id.documentMedicationSearch)
        val choices = picker.findViewById<ListView>(R.id.documentMedicationChoices)
        val empty = picker.findViewById<View>(R.id.documentNoMedicationMatches)
        val names = resources.getStringArray(R.array.document_demo_medications)
        val adapter = ArrayAdapter<String>(this, R.layout.item_demo_medication_choice)
        choices.adapter = adapter
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.document_demo_picker)
            .setView(picker)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        choices.setOnItemClickListener { _, _, position, _ ->
            target.text = adapter.getItem(position).orEmpty()
            dialog.dismiss()
        }
        fun filter(query: String) {
            val matches = names.filter { it.contains(query.trim(), ignoreCase = true) }
            adapter.clear()
            adapter.addAll(matches)
            empty.isVisible = matches.isEmpty()
        }
        filter("")
        search.editText.doAfterTextChanged { filter(it.toString()) }
        dialog.show()
    }

    private fun setupCertificateFields() {
        val today = SampleMedicalCertificate.today().format(SampleMedicalCertificate.dateFormat)
        val issue = findViewById<SmartField>(R.id.documentIssueDate)
        val start = findViewById<SmartField>(R.id.documentLeaveStart)
        val days = findViewById<SmartField>(R.id.documentDays)
        issue.text = today
        start.text = today
        val calculated = findViewById<TextView>(R.id.documentCalculatedDates)
        fun update() {
            val startDate = parseDate(start.text.trim())
            val duration = days.text.trim().toIntOrNull()
            val dates = if (startDate != null && duration != null && duration > 0) {
                leaveDates(startDate, duration)
            } else null
            calculated.isVisible = dates != null
            if (dates != null) {
                val format = SampleMedicalCertificate.dateFormat
                calculated.text = getString(
                    R.string.document_calculated_dates, dates.first.format(format), dates.second.format(format)
                )
            }
        }
        start.editText.doAfterTextChanged { update() }
        days.editText.doAfterTextChanged { update() }
        update()
    }

    private fun readCertificate(): SampleMedicalCertificate? {
        val patient = required(findViewById(R.id.documentPatientName))
        val doctorName = required(findViewById(R.id.documentDoctorName))
        val issueField = findViewById<SmartField>(R.id.documentIssueDate)
        val issue = readDate(issueField)
        val start = readDate(findViewById(R.id.documentLeaveStart))
        val daysField = findViewById<SmartField>(R.id.documentDays)
        val duration = required(daysField)
        val days = duration?.toIntOrNull()?.takeIf { it > 0 }
        if (duration != null && days == null) daysField.error = getString(R.string.document_invalid_days)
        val futureIssue = issue != null && issue.isAfter(SampleMedicalCertificate.today())
        if (futureIssue) issueField.error = getString(R.string.document_future_issue)
        val invalidRange = start != null && days != null && leaveDates(start, days) == null
        if (invalidRange) daysField.error = getString(R.string.document_invalid_range)
        if (patient == null || doctorName == null || issue == null || start == null || days == null ||
            futureIssue || invalidRange
        ) return null

        return SampleMedicalCertificate(reference, patient, doctorName, issue, start, days)
    }

    private fun leaveDates(start: LocalDate, days: Int): Pair<LocalDate, LocalDate>? {
        return try {
            val returnDate = start.plusDays(days.toLong())
            if (returnDate.year > 9999) null else returnDate.minusDays(1) to returnDate
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun required(field: SmartField): String? {
        return field.text.trim().takeIf { it.isNotEmpty() }.also {
            field.error = if (it == null) getString(R.string.document_required) else null
        }
    }

    private fun parseDate(value: String): LocalDate? {
        if (value.length != 10) return null
        return try {
            LocalDate.parse(value, SampleMedicalCertificate.dateFormat).takeIf { it.year in 1..9999 }
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun readDate(field: SmartField): LocalDate? {
        val value = required(field) ?: return null
        return parseDate(value).also {
            if (it == null) field.error = getString(R.string.document_invalid_date)
        }
    }

    private fun certificateRows(mc: SampleMedicalCertificate): List<DocumentRow> {
        val format = SampleMedicalCertificate.dateFormat
        return listOf(
            DocumentRow(R.string.document_patient_name, mc.patient),
            DocumentRow(R.string.document_doctor_name, mc.doctor),
            DocumentRow(R.string.document_issue_date, mc.issue.format(format)),
            DocumentRow(R.string.document_days, mc.days.toString()),
            DocumentRow(R.string.document_leave_start, mc.start.format(format)),
            DocumentRow(R.string.document_leave_end, mc.end.format(format)),
            DocumentRow(R.string.document_return_date, mc.returnToWork.format(format)),
        )
    }

    private fun setupPatientView() {
        val empty = findViewById<TextView>(R.id.documentEmpty)
        val toggle = findViewById<TextView>(R.id.documentToggleEmpty)
        val label = findViewById<TextView>(R.id.documentDemoLabel)
        empty.setText(if (prescription) R.string.document_no_prescriptions else R.string.document_no_certificates)
        empty.isVisible = true
        toggle.isVisible = true
        toggle.setText(R.string.document_show_sample)
        toggle.setOnClickListener {
            val showSample = !label.isVisible
            label.isVisible = showSample
            empty.isVisible = !showSample
            results.isVisible = showSample
            toggle.setText(if (showSample) R.string.document_hide_sample else R.string.document_show_sample)
            if (showSample) {
                if (prescription) {
                    render(MedicalDocumentSamples.medications.map { medication ->
                        medicationFields.zip(medication).map { (field, value) ->
                            DocumentRow(field.second, getString(value))
                        }
                    })
                } else {
                    val demo = MedicalDocumentSamples.certificate
                    render(listOf(certificateRows(demo)))
                    showPdfActions(demo)
                }
            } else {
                findViewById<View>(R.id.documentPdfActions).isVisible = false
            }
        }
    }

    private fun showPdfActions(certificate: SampleMedicalCertificate) {
        findViewById<View>(R.id.documentPdfActions).isVisible = true
        findViewById<View>(R.id.documentPdfPreview).setOnClickListener { previewPdf(it, certificate) }
        findViewById<View>(R.id.documentPdfDownload).setOnClickListener {
            pendingExport = certificate
            exportPdf.launch("sample-mc.pdf")
        }
    }

    private fun previewPdf(button: View, certificate: SampleMedicalCertificate) {
        button.isEnabled = false
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    SampleCertificatePdf.preview(certificate, cacheDir)
                }
                if (!button.isShown) return@launch
                val preview = layoutInflater.inflate(R.layout.dialog_sample_certificate_pdf, null)
                preview.findViewById<ImageView>(R.id.documentPdfImage).setImageBitmap(bitmap)
                MaterialAlertDialogBuilder(this@MedicalDocumentActivity)
                    .setTitle(R.string.document_pdf_preview)
                    .setView(preview)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                toast(R.string.document_pdf_failed)
            } finally {
                button.isEnabled = true
            }
        }
    }

    private fun writePdf(uri: Uri, certificate: SampleMedicalCertificate) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = contentResolver.openOutputStream(uri, "wt")
                        ?: error("PDF destination unavailable")
                    output.use { SampleCertificatePdf.write(certificate, it) }
                }
                toast(R.string.document_pdf_exported)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                toast(R.string.document_pdf_failed)
            }
        }
    }

    private fun render(sections: List<List<DocumentRow>>) {
        results.removeAllViews()
        sections.forEachIndexed { index, rows ->
            val card = layoutInflater.inflate(R.layout.view_medical_document_card, results, false) as LinearLayout
            if (prescription) {
                card.findViewById<TextView>(R.id.documentCardTitle).apply {
                    isVisible = true
                    text = getString(R.string.document_medication_number, index + 1)
                }
            }
            for (data in rows) {
                val row = layoutInflater.inflate(R.layout.view_profile_row, card, false)
                row.findViewById<TextView>(R.id.rowLabel).setText(data.label)
                row.findViewById<TextView>(R.id.rowValue).text = data.value
                card.addView(row)
            }
            results.addView(card)
        }
    }

    private data class DocumentRow(@param:StringRes val label: Int, val value: String)

    companion object {
        private const val EXTRA_PRESCRIPTION = "prototype_prescription"
        private val medicationFields = listOf(
            R.id.documentMedicineName to R.string.document_medicine_name,
            R.id.documentDosage to R.string.document_dosage,
            R.id.documentFrequency to R.string.document_frequency,
            R.id.documentDuration to R.string.document_duration,
            R.id.documentInstructions to R.string.document_instructions,
        )

        internal fun intent(context: Context, doctor: Boolean, prescription: Boolean): Intent {
            val target = if (doctor) DoctorMedicalDocumentActivity::class.java else MedicalDocumentActivity::class.java
            return Intent(context, target).putExtra(EXTRA_PRESCRIPTION, prescription)
        }
    }
}

class DoctorMedicalDocumentActivity : MedicalDocumentActivity() {
    override val doctor = true
}
