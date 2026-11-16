package com.example.smartcare

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AuthRepository
import com.example.smartcare.ui.SmartField
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * 4. Forgot password, part 1: patients and staff. Supabase emails a reset code
 * (AuthRepository.sendPasswordReset). After sending, the "Code sent" card shows,
 * the code screen opens, and this button becomes "Enter code".
 * The card shows whether or not the email is registered, so this screen never
 * reveals which emails have accounts.
 */
class ForgotPasswordActivity : BaseActivity() {

    companion object {
        private const val EXTRA_STAFF = "staff"
        private const val STATE_SENT_TO = "sent_to"

        fun intent(context: Context, staff: Boolean) =
            Intent(context, ForgotPasswordActivity::class.java).putExtra(EXTRA_STAFF, staff)
    }

    private lateinit var fieldEmail: SmartField
    private lateinit var btnSend: MaterialButton
    /** Email the code was sent to, or null if not sent yet. */
    private var sentTo: String? = null
    private val isStaff by lazy { intent.getBooleanExtra(EXTRA_STAFF, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Navy theme when opened from the staff login.
        if (intent.getBooleanExtra(EXTRA_STAFF, false)) setTheme(R.style.Theme_SmartCare_Staff)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        setupHeader(R.string.forgot_hero_title, R.string.forgot_hero_sub)
        setupFooterLink(R.string.footer_remembered, R.string.footer_back_login) { finish() }

        fieldEmail = findViewById(R.id.fieldEmail)
        btnSend = findViewById(R.id.btnSend)
        btnSend.setOnClickListener { if (sentTo != null) openCodeScreen() else send() }
        // A different email needs a new code.
        fieldEmail.editText.doAfterTextChanged {
            if (sentTo != null && fieldEmail.text.trim().lowercase() != sentTo) showSent(null)
        }
        fieldEmail.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { btnSend.performClick(); true } else false
        }

        showSent(savedInstanceState?.getString(STATE_SENT_TO))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SENT_TO, sentTo)
    }

    private fun showSent(email: String?) {
        sentTo = email
        findViewById<View>(R.id.cardSent).visibility = if (email != null) View.VISIBLE else View.GONE
        btnSend.setText(if (email != null) R.string.action_resend_link else R.string.action_send_link)
    }

    private fun openCodeScreen() {
        startActivity(ResetCodeActivity.intent(this, sentTo ?: return, isStaff))
    }

    private fun send() {
        val email = fieldEmail.text.trim()
        fieldEmail.error = when {
            email.isEmpty() -> getString(R.string.error_required)
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> getString(R.string.error_invalid_email)
            else -> null
        }
        if (fieldEmail.error != null) return

        setLoading(true, btnSend)
        lifecycleScope.launch {
            AuthRepository.sendPasswordReset(email)
                .onSuccess {
                    setLoading(false, btnSend)
                    showSent(email.lowercase())
                    openCodeScreen()
                }
                .onFailure {
                    setLoading(false, btnSend)
                    toast(it.friendly())
                }
        }
    }
}
