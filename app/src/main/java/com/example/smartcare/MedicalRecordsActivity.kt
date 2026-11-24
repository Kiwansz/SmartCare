package com.example.smartcare

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AuthRepository
import kotlinx.coroutines.launch

/** Medical records preview. Every record and appointment shown here is fictional. */
open class MedicalRecordsActivity : BaseActivity() {
    protected open val doctor = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medical_records)
        setupHeader(R.string.medical_records_title, R.string.medical_records_sample)
        findViewById<View>(R.id.medicalRetry).setOnClickListener { load() }
        load()
    }

    private fun load() {
        val progress = findViewById<View>(R.id.progress)
        val error = findViewById<View>(R.id.medicalError)
        val content = findViewById<View>(R.id.medicalContent)
        progress.isVisible = true
        error.isVisible = false
        content.isVisible = false

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
            content.isVisible = true
            if (intent.hasExtra(DoctorMedicalRecordsActivity.EXTRA_APPOINTMENT_ID)) {
                if (doctor) showAppointmentEntry(account.userId) else finish()
                return@launch
            }
            when (intent.getStringExtra(EXTRA_SCREEN)) {
                DETAILS -> showDetails()
                SAMPLE_APPOINTMENT -> if (doctor) showSampleAppointment() else finish()
                PREVIEW -> if (doctor) showPreview() else finish()
                else -> showHistory()
            }
        }
    }

    private fun showAppointmentEntry(userId: Int) {
        val appointmentId = intent.getIntExtra(DoctorMedicalRecordsActivity.EXTRA_APPOINTMENT_ID, 0)
        val appointment = MedicalAppointmentSamples.find(appointmentId)
        val identity = MedicalAppointmentSamples.doctorFor(userId)
        val block = appointmentBlock(appointment, identity)
        if (block != null || appointment == null) {
            findViewById<TextView>(R.id.medicalAppointmentMessage).apply {
                setText((block ?: MedicalAppointmentBlock.MISSING).messageId())
                isVisible = true
            }
            return
        }

        setupHeader(R.string.medical_details_title, R.string.medical_records_sample)
        findViewById<View>(R.id.medicalAppointment).isVisible = true
        findViewById<TextView>(R.id.medicalAppointmentSummary).text = getString(
            R.string.medical_scoped_appointment,
            appointment.appointmentId,
            appointment.patientName,
            appointment.assignedDoctorName,
            appointment.scheduledDate.format(SampleMedicalCertificate.dateFormat),
        )

        bindAppointmentAction(R.id.medicalOpenPreview, appointment, identity, MedicalEntryType.HISTORY) {
            showPreview()
        }
        findViewById<View>(R.id.medicalDocumentOptions).isVisible = true
        bindAppointmentAction(
            R.id.documentOpenPrescription, appointment, identity, MedicalEntryType.PRESCRIPTION
        ) {
            startActivity(MedicalDocumentActivity.intent(this, true, prescription = true, appointmentId = appointmentId))
        }
        bindAppointmentAction(
            R.id.documentOpenCertificate, appointment, identity, MedicalEntryType.CERTIFICATE
        ) {
            startActivity(MedicalDocumentActivity.intent(this, true, prescription = false, appointmentId = appointmentId))
        }
    }

    private fun bindAppointmentAction(
        viewId: Int,
        appointment: MedicalAppointmentContext,
        identity: MedicalDoctorIdentity,
        entry: MedicalEntryType,
        open: () -> Unit,
    ) {
        findViewById<View>(viewId).setOnClickListener {
            val block = appointmentBlock(appointment, identity, entry)
            if (block == null) {
                open()
            } else {
                toast(block.messageId())
            }
        }
    }

    private fun showHistory() {
        val tile = findViewById<View>(R.id.medicalRecordTile)
        val empty = findViewById<TextView>(R.id.medicalEmpty)
        val toggle = findViewById<TextView>(R.id.medicalToggleEmpty)
        fun renderEmpty(showEmpty: Boolean) {
            tile.isVisible = !showEmpty
            empty.isVisible = showEmpty
            toggle.setText(if (showEmpty) R.string.medical_show_history else R.string.medical_show_empty)
        }
        renderEmpty(false)
        toggle.isVisible = true
        toggle.setOnClickListener { renderEmpty(!empty.isVisible) }

        tile.findViewById<ImageView>(R.id.tileIcon).setImageResource(R.drawable.ic_sc_file)
        tile.findViewById<TextView>(R.id.tileTitle).setText(R.string.medical_sample_visit)
        tile.findViewById<TextView>(R.id.tileSub).setText(R.string.medical_sample_date)
        tile.setOnClickListener { open(DETAILS) }
        if (doctor) {
            findViewById<View>(R.id.medicalSampleAppointment).apply {
                isVisible = true
                setOnClickListener { open(SAMPLE_APPOINTMENT) }
            }
        }
    }

    private fun showDetails() {
        setupHeader(R.string.medical_details_title, R.string.medical_records_sample)
        findViewById<View>(R.id.medicalDetails).isVisible = true
        setRow(R.id.medicalDate, R.string.medical_date, R.string.medical_sample_date)
        setRow(R.id.medicalDoctor, R.string.medical_doctor, R.string.medical_sample_doctor)
        setRow(R.id.medicalDiagnosis, R.string.medical_diagnosis, R.string.medical_sample_diagnosis)
        setRow(R.id.medicalTreatment, R.string.medical_treatment, R.string.medical_sample_treatment)
        setRow(R.id.medicalNotes, R.string.medical_notes, R.string.medical_sample_notes)
        findViewById<View>(R.id.medicalDocumentOptions).isVisible = true
        findViewById<View>(R.id.documentOpenPrescription).setOnClickListener {
            startActivity(MedicalDocumentActivity.intent(this, doctor, prescription = true))
        }
        findViewById<View>(R.id.documentOpenCertificate).setOnClickListener {
            startActivity(MedicalDocumentActivity.intent(this, doctor, prescription = false))
        }
    }

    private fun showSampleAppointment() {
        setupHeader(R.string.medical_sample_appointment, R.string.medical_records_sample)
        findViewById<View>(R.id.medicalAppointment).isVisible = true
        // This screen represents the one fictional appointment eligible in the preview.
        findViewById<View>(R.id.medicalOpenPreview).setOnClickListener { open(PREVIEW) }
    }

    private fun showPreview() {
        setupHeader(R.string.medical_create_preview, R.string.medical_records_sample)
        findViewById<View>(R.id.medicalForm).isVisible = true
        // The form is transient, including across activity recreation.
        for (id in listOf(R.id.medicalFieldDiagnosis, R.id.medicalFieldTreatment, R.id.medicalFieldNotes)) {
            findViewById<View>(id).isSaveFromParentEnabled = false
        }
    }

    private fun setRow(id: Int, label: Int, value: Int) {
        val row = findViewById<View>(id)
        row.findViewById<TextView>(R.id.rowLabel).setText(label)
        row.findViewById<TextView>(R.id.rowValue).setText(value)
    }

    private fun open(screen: String) {
        startActivity(Intent(this, javaClass).putExtra(EXTRA_SCREEN, screen))
    }

    private companion object {
        const val EXTRA_SCREEN = "medical_sample_screen"
        const val DETAILS = "details"
        const val SAMPLE_APPOINTMENT = "sample_appointment"
        const val PREVIEW = "preview"
    }
}

class DoctorMedicalRecordsActivity : MedicalRecordsActivity() {
    override val doctor = true

    companion object {
        const val EXTRA_APPOINTMENT_ID = "appointment_id"

        /** Launch from a doctor appointment detail screen with the selected appointment's ID. */
        fun forAppointment(context: Context, appointmentId: Int): Intent =
            Intent(context, DoctorMedicalRecordsActivity::class.java)
                .putExtra(EXTRA_APPOINTMENT_ID, appointmentId)
    }
}
