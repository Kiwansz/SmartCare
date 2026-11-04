package com.example.smartcare

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AuthRepository
import com.example.smartcare.ui.SmartField
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * 4. Forgot password: patients and staff. Supabase emails the reset link
 * (existing AuthRepository.sendPasswordReset). After sending, the "Link sent"
 * card shows and the button becomes "Resend link".
 */
class ForgotPasswordActivity : BaseActivity() {

    companion object {
        private const val EXTRA_STAFF = "staff"
        private const val STATE_SENT = "sent"

        fun intent(context: Context, staff: Boolean) =
            Intent(context, ForgotPasswordActivity::class.java).putExtra(EXTRA_STAFF, staff)
    }

    private lateinit var fieldEmail: SmartField
    private lateinit var btnSend: MaterialButton
    private var sent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Navy theme when opened from the staff login.
        if (intent.getBooleanExtra(EXTRA_STAFF, false)) setTheme(R.style.Theme_SmartCare_Staff)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        setupHeader(R.string.forgot_hero_title, R.string.forgot_hero_sub)
        setupFooterLink(R.string.footer_remembered, R.string.footer_back_login) { finish() }

        fieldEmail = findViewById(R.id.fieldEmail)
        btnSend = findViewById(R.id.btnSend)
        btnSend.setOnClickListener { send() }
        fieldEmail.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { send(); true } else false
        }

        showSent(savedInstanceState?.getBoolean(STATE_SENT) ?: false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_SENT, sent)
    }

    private fun showSent(value: Boolean) {
        sent = value
        findViewById<View>(R.id.cardSent).visibility = if (value) View.VISIBLE else View.GONE
        btnSend.setText(if (value) R.string.action_resend_link else R.string.action_send_link)
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
            // Note: the link opens the Supabase project's Site URL. Finishing the reset
            // in-app needs a deep link or web page later.
            AuthRepository.sendPasswordReset(email)
                .onSuccess {
                    setLoading(false, btnSend)
                    showSent(true)
                }
                .onFailure {
                    setLoading(false, btnSend)
                    toast(it.friendly())
                }
        }
    }
}
