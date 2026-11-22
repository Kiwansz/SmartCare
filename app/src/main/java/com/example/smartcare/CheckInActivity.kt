package com.example.smartcare

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AttendanceRepository
import com.example.smartcare.data.CheckInResult
import com.example.smartcare.data.userMessage
import com.example.smartcare.ui.SmartField
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Attendance (patient): check in with the code from the booking.
 * On time -> checked in. Late past the grace period -> waiting for staff to verify.
 */
class CheckInActivity : BaseActivity() {

    private lateinit var fieldCode: SmartField
    private lateinit var btnCheckIn: MaterialButton
    private lateinit var cardResult: View
    private lateinit var tvResultTitle: TextView
    private lateinit var tvResultBody: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_check_in)
        setupHeader(R.string.clinic_checkin_title, R.string.clinic_checkin_sub)

        fieldCode = findViewById(R.id.fieldCode)
        btnCheckIn = findViewById(R.id.btnCheckIn)
        cardResult = findViewById(R.id.cardResult)
        tvResultTitle = findViewById(R.id.tvResultTitle)
        tvResultBody = findViewById(R.id.tvResultBody)

        // Pre-fill the code when opened from "My appointments".
        if (savedInstanceState == null) fieldCode.text = intent.getStringExtra(EXTRA_CODE).orEmpty()

        btnCheckIn.setOnClickListener { submit() }
        fieldCode.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
    }

    private fun submit() {
        val code = fieldCode.text.trim()
        if (code.isEmpty()) {
            fieldCode.error = getString(R.string.clinic_checkin_empty)
            return
        }
        cardResult.visibility = View.GONE
        setLoading(true, btnCheckIn)
        lifecycleScope.launch {
            AttendanceRepository.checkIn(code)
                .onSuccess { showResult(it) }
                .onFailure {
                    setLoading(false, btnCheckIn)
                    fieldCode.error = it.userMessage(getString(R.string.clinic_error_generic))
                }
        }
    }

    private fun showResult(result: CheckInResult) {
        setLoading(false, btnCheckIn)
        val late = result.status == AttendanceRepository.PENDING_VERIFICATION
        tvResultTitle.setText(if (late) R.string.clinic_checkin_late_title else R.string.clinic_checkin_ok_title)
        tvResultBody.setText(if (late) R.string.clinic_checkin_late_body else R.string.clinic_checkin_ok_body)
        cardResult.visibility = View.VISIBLE
        // Done: the code cannot be used twice.
        btnCheckIn.visibility = View.GONE
        fieldCode.editText.isEnabled = false
    }

    companion object {
        private const val EXTRA_CODE = "code"

        /** Intent to open the check-in screen, optionally with the booking's code filled in. */
        fun intent(context: Context, code: String?): Intent =
            Intent(context, CheckInActivity::class.java).putExtra(EXTRA_CODE, code)
    }
}
