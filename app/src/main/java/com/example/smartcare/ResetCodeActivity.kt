package com.example.smartcare

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AuthRepository
import com.example.smartcare.data.InvalidResetCodeException
import com.example.smartcare.ui.SmartField
import com.example.smartcare.ui.StrengthMeter
import com.example.smartcare.util.PasswordRules
import com.example.smartcare.util.ResetCode
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Forgot password, part 2: the user types the code Supabase emailed and a new password.
 * The code is verified as a recovery email OTP, the password is updated, the user is
 * signed out and sent back to the login screen they came from.
 */
class ResetCodeActivity : BaseActivity() {

    companion object {
        private const val EXTRA_EMAIL = "email"
        private const val EXTRA_STAFF = "staff"
        private const val STATE_RESEND_AT = "resend_at"
        private const val RESEND_WAIT_MS = 60_000L

        fun intent(context: Context, email: String, staff: Boolean) =
            Intent(context, ResetCodeActivity::class.java)
                .putExtra(EXTRA_EMAIL, email)
                .putExtra(EXTRA_STAFF, staff)
    }

    private val email by lazy { intent.getStringExtra(EXTRA_EMAIL).orEmpty() }
    private val isStaff by lazy { intent.getBooleanExtra(EXTRA_STAFF, false) }

    private lateinit var fieldCode: SmartField
    private lateinit var fieldNew: SmartField
    private lateinit var fieldConfirm: SmartField
    private lateinit var linkResend: TextView
    private lateinit var btnReset: MaterialButton

    /** elapsedRealtime when Resend becomes available (the email was just sent on open). */
    private var resendAt = 0L
    private var timer: CountDownTimer? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent.getBooleanExtra(EXTRA_STAFF, false)) setTheme(R.style.Theme_SmartCare_Staff)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_code)
        setupHeader(R.string.code_hero_title, R.string.code_hero_sub)
        findViewById<TextView>(R.id.headerSubtitle).text = getString(R.string.code_hero_sub, email)

        fieldCode = findViewById(R.id.fieldCode)
        fieldNew = findViewById(R.id.fieldNew)
        fieldConfirm = findViewById(R.id.fieldConfirm)
        linkResend = findViewById(R.id.linkResend)
        btnReset = findViewById(R.id.btnReset)

        // Large, letter-spaced code text.
        fieldCode.editText.apply {
            textSize = 22f
            letterSpacing = 0.3f
            typeface = ResourcesCompat.getFont(this@ResetCodeActivity, R.font.figtree_bold)
        }

        fieldCode.editText.doAfterTextChanged { updateState() }
        fieldNew.editText.doAfterTextChanged { updateState() }
        fieldConfirm.editText.doAfterTextChanged { updateState() }
        btnReset.setOnClickListener { reset() }
        linkResend.setOnClickListener { resend() }

        resendAt = savedInstanceState?.getLong(STATE_RESEND_AT)
            ?: (SystemClock.elapsedRealtime() + RESEND_WAIT_MS)
        startCountdown()
        updateState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_RESEND_AT, resendAt)
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    /** Rule chips, strength bar, and the Reset button's enabled state. */
    private fun updateState() {
        val pw = fieldNew.text
        val check = PasswordRules.check(pw)
        val match = PasswordRules.matches(pw, fieldConfirm.text)

        StrengthMeter.bind(findViewById(R.id.meter), check)
        setChip(R.id.chipLength, check.longEnough)
        setChip(R.id.chipUpper, check.hasUppercase)
        setChip(R.id.chipNumber, check.hasNumber)
        setChip(R.id.chipMatch, match)

        val ready = !busy && ResetCode.isValid(fieldCode.text) && check.isValid && match
        btnReset.isEnabled = ready
        btnReset.alpha = if (ready) 1f else 0.5f
    }

    private fun setChip(id: Int, ok: Boolean) {
        findViewById<TextView>(id).apply {
            setBackgroundResource(if (ok) R.drawable.bg_chip_ok else R.drawable.bg_chip_off)
            setTextColor(if (ok) themeColor(R.attr.scMain) ?: currentTextColor else getColor(R.color.ink_sub))
            setCompoundDrawablesRelativeWithIntrinsicBounds(if (ok) R.drawable.ic_sc_check_small else 0, 0, 0, 0)
        }
    }

    private fun startCountdown() {
        timer?.cancel()
        val left = resendAt - SystemClock.elapsedRealtime()
        if (left <= 0) {
            linkResend.isEnabled = true
            linkResend.alpha = 1f
            linkResend.setText(R.string.resend_code)
            return
        }
        linkResend.isEnabled = false
        linkResend.alpha = 0.5f
        timer = object : CountDownTimer(left, 1000) {
            override fun onTick(ms: Long) {
                linkResend.text = getString(R.string.resend_code_in, ((ms + 999) / 1000).toInt())
            }
            override fun onFinish() = startCountdown()
        }.start()
    }

    private fun resend() {
        linkResend.isEnabled = false
        lifecycleScope.launch {
            AuthRepository.sendPasswordReset(email)
                .onSuccess {
                    toast(R.string.msg_code_resent)
                    resendAt = SystemClock.elapsedRealtime() + RESEND_WAIT_MS
                }
                .onFailure { toast(it.friendly()) }
            startCountdown()
        }
    }

    private fun reset() {
        busy = true
        setLoading(true, btnReset)
        lifecycleScope.launch {
            AuthRepository.resetPasswordWithCode(email, fieldCode.text, fieldNew.text)
                .onSuccess {
                    toast(R.string.msg_password_reset_done)
                    // Back to the login screen they started from (already in the back stack).
                    val login = if (isStaff) StaffLoginActivity::class.java else LoginActivity::class.java
                    startActivity(
                        Intent(this@ResetCodeActivity, login)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                    finish()
                }
                .onFailure {
                    busy = false
                    setLoading(false, btnReset)
                    if (it is InvalidResetCodeException) {
                        fieldCode.error = getString(R.string.error_reset_code)
                    } else {
                        toast(it.friendly())
                    }
                    updateState()
                }
        }
    }
}
