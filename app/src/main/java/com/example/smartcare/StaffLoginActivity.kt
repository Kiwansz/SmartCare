package com.example.smartcare

import android.os.Bundle
import android.view.View
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AuthRepository
import com.example.smartcare.data.InvalidLoginException
import com.example.smartcare.data.SessionPrefs
import com.example.smartcare.ui.SmartField
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch

/**
 * 5. Staff login (navy theme, set in the manifest): work email + password.
 * Uses the existing AuthRepository.signIn by email with roles STAFF/DOCTOR.
 * No sign up: staff accounts are created by the Admin.
 */
class StaffLoginActivity : BaseActivity() {

    private lateinit var fieldEmail: SmartField
    private lateinit var fieldPassword: SmartField
    private lateinit var switchRemember: MaterialSwitch
    private lateinit var btnLogin: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staff_login)
        setupHeader(
            R.string.staff_hero_title,
            R.string.staff_hero_sub,
            pill = getString(R.string.staff_pill),
            pillIcon = R.drawable.ic_sc_staff_small
        )
        setupFooterLink(R.string.footer_no_account, R.string.footer_ask_admin) {
            toast(R.string.msg_ask_admin)
        }

        fieldEmail = findViewById(R.id.fieldEmail)
        fieldPassword = findViewById(R.id.fieldPassword)
        switchRemember = findViewById(R.id.switchRemember)
        btnLogin = findViewById(R.id.btnLogin)
        switchRemember.isChecked = SessionPrefs.rememberMe(this)
        // Tapping the "Remember me" text toggles the switch as well.
        findViewById<View>(R.id.labelRemember).setOnClickListener { switchRemember.toggle() }

        findViewById<TextView>(R.id.linkForgot).setOnClickListener {
            startActivity(ForgotPasswordActivity.intent(this, staff = true))
        }
        fieldPassword.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { attemptLogin(); true } else false
        }
        btnLogin.setOnClickListener { attemptLogin() }
    }

    private fun attemptLogin() {
        val email = fieldEmail.text.trim()
        val password = fieldPassword.text
        var valid = true

        if (email.isEmpty()) {
            fieldEmail.error = getString(R.string.error_required); valid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            fieldEmail.error = getString(R.string.error_invalid_email); valid = false
        }
        if (password.isEmpty()) {
            fieldPassword.error = getString(R.string.error_required); valid = false
        }
        if (!valid) return

        setLoading(true, btnLogin)
        lifecycleScope.launch {
            AuthRepository.signIn(email, password, AuthRepository.STAFF_ROLES)
                .onSuccess { account ->
                    SessionPrefs.setRememberMe(this@StaffLoginActivity, switchRemember.isChecked)
                    goAfterLogin(account) // change password first if must_change_password
                }
                .onFailure {
                    setLoading(false, btnLogin)
                    toast(if (it is InvalidLoginException) getString(R.string.error_login_email) else it.friendly())
                }
        }
    }
}
