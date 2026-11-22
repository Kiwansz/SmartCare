package com.example.smartcare

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RatingBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.FeedbackItem
import com.example.smartcare.data.FeedbackRepository
import com.example.smartcare.data.userMessage
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

/**
 * Feedback (patient): 1 to 5 stars and an optional comment for a completed
 * appointment. One feedback per appointment; if it already exists it is shown read-only.
 */
class FeedbackActivity : BaseActivity() {

    private lateinit var ratingBar: RatingBar
    private lateinit var etComment: TextInputEditText
    private lateinit var btnSubmit: MaterialButton
    private lateinit var tvInfo: TextView

    private var appointmentId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appointmentId = intent.getIntExtra(EXTRA_APPOINTMENT_ID, 0)
        if (appointmentId <= 0) {
            finish() // opened without an appointment
            return
        }
        setContentView(R.layout.activity_feedback)
        setupHeader(R.string.clinic_feedback_title, R.string.clinic_feedback_sub)

        ratingBar = findViewById(R.id.ratingBar)
        etComment = findViewById(R.id.etComment)
        btnSubmit = findViewById(R.id.btnSubmit)
        tvInfo = findViewById(R.id.tvInfo)
        findViewById<TextView>(R.id.tvVisit).text = intent.getStringExtra(EXTRA_SUMMARY).orEmpty()

        btnSubmit.setOnClickListener { submit() }
        loadExisting()
    }

    /** If this visit was already rated, show that rating instead of the empty form. */
    private fun loadExisting() {
        lifecycleScope.launch {
            FeedbackRepository.getForAppointment(appointmentId)
                .onSuccess { existing -> if (existing != null) showSubmitted(existing) }
            // A failed lookup is ignored: the database still refuses a second feedback.
        }
    }

    private fun showSubmitted(feedback: FeedbackItem) {
        ratingBar.rating = feedback.rating.toFloat()
        ratingBar.setIsIndicator(true)
        etComment.setText(feedback.comment.orEmpty())
        etComment.isEnabled = false
        btnSubmit.visibility = View.GONE
        showInfo(getString(R.string.clinic_feedback_already), isError = false)
    }

    private fun submit() {
        val stars = ratingBar.rating.toInt()
        if (stars < 1) {
            showInfo(getString(R.string.clinic_feedback_pick_stars), isError = true)
            return
        }
        showInfo(null, isError = false)
        setLoading(true, btnSubmit)
        lifecycleScope.launch {
            FeedbackRepository.submit(appointmentId, stars, etComment.text?.toString().orEmpty())
                .onSuccess {
                    setLoading(false, btnSubmit)
                    AlertDialog.Builder(this@FeedbackActivity)
                        .setTitle(R.string.clinic_feedback_thanks_title)
                        .setMessage(R.string.clinic_feedback_thanks_body)
                        .setCancelable(false)
                        .setPositiveButton(R.string.clinic_ok) { _, _ -> finish() }
                        .show()
                }
                .onFailure {
                    setLoading(false, btnSubmit)
                    showInfo(it.userMessage(getString(R.string.clinic_error_generic)), isError = true)
                }
        }
    }

    private fun showInfo(text: String?, isError: Boolean) {
        tvInfo.text = text
        tvInfo.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
        tvInfo.setTextColor(ContextCompat.getColor(this, if (isError) R.color.danger else R.color.ink_sub))
    }

    companion object {
        private const val EXTRA_APPOINTMENT_ID = "appointment_id"
        private const val EXTRA_SUMMARY = "summary"

        /** Intent to rate one completed appointment. [summary] is the line shown at the top. */
        fun intent(context: Context, appointmentId: Int, summary: String): Intent =
            Intent(context, FeedbackActivity::class.java)
                .putExtra(EXTRA_APPOINTMENT_ID, appointmentId)
                .putExtra(EXTRA_SUMMARY, summary)
    }
}
