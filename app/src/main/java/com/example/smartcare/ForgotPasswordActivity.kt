package com.example.smartcare

import android.os.Bundle
import android.util.Patterns
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AuthRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** 4. Forgot password: used by patients and staff. Supabase sends the email. */
class ForgotPasswordActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        setupTopBar(R.string.forgot_bar)
        setupFooterLink(R.string.back_to_prefix, R.string.login_link) { finish() }

        val tilEmail = findViewById<TextInputLayout>(R.id.tilEmail)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val btnSend = findViewById<MaterialButton>(R.id.btnSend)

        btnSend.setOnClickListener {
            val email = etEmail.text?.toString()?.trim().orEmpty()
            tilEmail.error = when {
                email.isEmpty() -> getString(R.string.error_required)
                !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> getString(R.string.error_invalid_email)
                else -> null
            }
            if (tilEmail.error != null) return@setOnClickListener

            setLoading(true, btnSend)
            lifecycleScope.launch {
                AuthRepository.sendPasswordReset(email)
                    .onSuccess {
                        toast(R.string.msg_reset_sent)
                        finish()
                    }
                    .onFailure {
                        setLoading(false, btnSend)
                        toast(it.friendly())
                    }
            }
        }
    }
}
