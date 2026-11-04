package com.example.smartcare

import android.os.Bundle
import android.util.Patterns
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AuthRepository
import com.example.smartcare.util.IcUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** 3. Patient sign up: saves to user_account and patient, including IC number. */
class RegisterActivity : BaseActivity() {

    private lateinit var btnRegister: MaterialButton
    private lateinit var tilFullName: TextInputLayout
    private lateinit var tilIcNumber: TextInputLayout
    private lateinit var tilPhone: TextInputLayout
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etFullName: TextInputEditText
    private lateinit var etIcNumber: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        setupTopBar(R.string.register_bar)

        tilFullName = findViewById(R.id.tilFullName)
        tilIcNumber = findViewById(R.id.tilIcNumber)
        tilPhone = findViewById(R.id.tilPhone)
        tilEmail = findViewById(R.id.tilEmail)
        tilPassword = findViewById(R.id.tilPassword)
        etFullName = findViewById(R.id.etFullName)
        etIcNumber = findViewById(R.id.etIcNumber)
        etPhone = findViewById(R.id.etPhone)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnRegister = findViewById(R.id.btnRegister)

        btnRegister.setOnClickListener { attemptRegister() }
        // Login is the previous screen, so just close this one.
        setupFooterLink(R.string.have_account_prefix, R.string.login_link) { finish() }
    }

    private fun attemptRegister() {
        val fullName = etFullName.text?.toString()?.trim().orEmpty()
        val icNumber = etIcNumber.text?.toString().orEmpty().filter { it.isDigit() } // dashes allowed
        val phone = etPhone.text?.toString()?.trim().orEmpty()
        val email = etEmail.text?.toString()?.trim().orEmpty()
        val password = etPassword.text?.toString().orEmpty()

        listOf(tilFullName, tilIcNumber, tilPhone, tilEmail, tilPassword).forEach { it.error = null }
        var valid = true

        if (fullName.isEmpty()) {
            tilFullName.error = getString(R.string.error_required); valid = false
        }

        when {
            etIcNumber.text.isNullOrBlank() -> {
                tilIcNumber.error = getString(R.string.error_required); valid = false
            }
            icNumber.length != 12 -> {
                tilIcNumber.error = getString(R.string.error_invalid_ic); valid = false
            }
            IcUtils.birthDate(icNumber) == null -> {
                tilIcNumber.error = getString(R.string.error_invalid_ic_date); valid = false
            }
        }

        if (phone.isEmpty()) {
            tilPhone.error = getString(R.string.error_required); valid = false
        } else if (!Patterns.PHONE.matcher(phone).matches() || phone.count { it.isDigit() } < 9) {
            tilPhone.error = getString(R.string.error_invalid_phone); valid = false
        }

        if (email.isEmpty()) {
            tilEmail.error = getString(R.string.error_required); valid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = getString(R.string.error_invalid_email); valid = false
        }

        if (password.isEmpty()) {
            tilPassword.error = getString(R.string.error_required); valid = false
        } else if (password.length < 8) {
            tilPassword.error = getString(R.string.error_password_short); valid = false
        }

        if (!valid) return

        setLoading(true, btnRegister)
        lifecycleScope.launch {
            AuthRepository.signUpPatient(fullName, icNumber, email, phone, password)
                .onSuccess { loggedIn ->
                    if (!loggedIn) {
                        // Email confirmation is on: they must confirm before logging in.
                        toast(R.string.msg_register_confirm_email)
                        finish()
                        return@onSuccess
                    }
                    val account = AuthRepository.currentAccount()
                    if (account != null) goAfterLogin(account) else finish()
                }
                .onFailure {
                    setLoading(false, btnRegister)
                    toast(it.friendly())
                }
        }
    }
}
