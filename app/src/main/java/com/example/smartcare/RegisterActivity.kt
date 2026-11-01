package com.example.smartcare

import android.os.Bundle
import android.util.Patterns
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.SupabaseProvider
import kotlinx.coroutines.launch
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class RegisterActivity : AppCompatActivity() {

    private lateinit var btnRegister: MaterialButton
    private lateinit var tilFullName: TextInputLayout
    private lateinit var tilIcNumber: TextInputLayout
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPhone: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var tilConfirmPassword: TextInputLayout
    private lateinit var etFullName: TextInputEditText
    private lateinit var etIcNumber: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        tilFullName        = findViewById(R.id.tilFullName)
        tilIcNumber        = findViewById(R.id.tilIcNumber)
        tilEmail           = findViewById(R.id.tilEmail)
        tilPhone           = findViewById(R.id.tilPhone)
        tilPassword        = findViewById(R.id.tilPassword)
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword)
        etFullName         = findViewById(R.id.etFullName)
        etIcNumber         = findViewById(R.id.etIcNumber)
        etEmail            = findViewById(R.id.etEmail)
        etPhone            = findViewById(R.id.etPhone)
        etPassword         = findViewById(R.id.etPassword)
        etConfirmPassword  = findViewById(R.id.etConfirmPassword)

        btnRegister = findViewById(R.id.btnRegister)
        btnRegister.setOnClickListener { attemptRegister() }

        // LoginActivity is the previous screen, so just close this one.
        findViewById<TextView>(R.id.tvLogin).setOnClickListener { finish() }
    }

    private fun attemptRegister() {
        val fullName        = etFullName.text?.toString()?.trim().orEmpty()
        val icNumber        = etIcNumber.text?.toString()?.trim().orEmpty()
        val email           = etEmail.text?.toString()?.trim().orEmpty()
        val phone           = etPhone.text?.toString()?.trim().orEmpty()
        val password        = etPassword.text?.toString().orEmpty()
        val confirmPassword = etConfirmPassword.text?.toString().orEmpty()

        listOf(tilFullName, tilIcNumber, tilEmail, tilPhone, tilPassword, tilConfirmPassword)
            .forEach { it.error = null }

        var valid = true

        if (fullName.isEmpty()) {
            tilFullName.error = getString(R.string.error_required)
            valid = false
        }

        if (icNumber.isEmpty()) {
            tilIcNumber.error = getString(R.string.error_required)
            valid = false
        } else if (icNumber.length != 12 || !icNumber.all { it.isDigit() }) {
            tilIcNumber.error = getString(R.string.error_invalid_ic)
            valid = false
        }

        if (email.isEmpty()) {
            tilEmail.error = getString(R.string.error_required)
            valid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = getString(R.string.error_invalid_email)
            valid = false
        }

        if (phone.isEmpty()) {
            tilPhone.error = getString(R.string.error_required)
            valid = false
        } else if (!Patterns.PHONE.matcher(phone).matches() || phone.count { it.isDigit() } < 8) {
            tilPhone.error = getString(R.string.error_invalid_phone)
            valid = false
        }

        if (password.isEmpty()) {
            tilPassword.error = getString(R.string.error_required)
            valid = false
        } else if (password.length < 6) {
            tilPassword.error = getString(R.string.error_password_short)
            valid = false
        }

        if (confirmPassword.isEmpty()) {
            tilConfirmPassword.error = getString(R.string.error_required)
            valid = false
        } else if (password != confirmPassword) {
            tilConfirmPassword.error = getString(R.string.error_password_mismatch)
            valid = false
        }

        if (!valid) return

        btnRegister.isEnabled = false
        lifecycleScope.launch {
            SupabaseProvider.signUp(fullName, icNumber, email, phone, password)
                .onSuccess {
                    Toast.makeText(
                        this@RegisterActivity,
                        R.string.msg_register_success,
                        Toast.LENGTH_LONG
                    ).show()
                    finish() // back to the login screen
                }
                .onFailure {
                    btnRegister.isEnabled = true
                    Toast.makeText(
                        this@RegisterActivity,
                        getString(R.string.msg_register_failed, it.message ?: "unknown error"),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }
}
