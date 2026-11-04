package com.example.smartcare

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AuthRepository
import com.example.smartcare.data.SessionPrefs
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/**
 * 2. Patient login and 5. Staff login. Same layout, different texts and rules:
 * - Patient: username = IC number or email, has a Sign Up link.
 * - Staff: work email only, no Sign Up (accounts are Admin-created).
 */
class LoginActivity : BaseActivity() {

    companion object {
        private const val EXTRA_STAFF = "staff"

        fun intent(context: Context, staff: Boolean) =
            Intent(context, LoginActivity::class.java).putExtra(EXTRA_STAFF, staff)
    }

    private val isStaff by lazy { intent.getBooleanExtra(EXTRA_STAFF, false) }

    private lateinit var tilUsername: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etUsername: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var cbRemember: MaterialCheckBox
    private lateinit var btnLogin: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        tilUsername = findViewById(R.id.tilUsername)
        tilPassword = findViewById(R.id.tilPassword)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        cbRemember = findViewById(R.id.cbRemember)
        btnLogin = findViewById(R.id.btnLogin)

        if (isStaff) {
            setupTopBar(R.string.staff_login_bar)
            findViewById<TextView>(R.id.tvTitle).setText(R.string.staff_title)
            findViewById<TextView>(R.id.tvSubtitle).setText(R.string.staff_subtitle)
            findViewById<TextView>(R.id.tvUsernameLabel).setText(R.string.label_work_email)
            etUsername.setHint(R.string.hint_work_email)
            tilUsername.setStartIconDrawable(R.drawable.ic_mail)
            setupFooterLink(R.string.staff_no_account_prefix, R.string.ask_admin) {
                toast(R.string.msg_ask_admin)
            }
        } else {
            setupTopBar(R.string.patient_login_bar)
            findViewById<TextView>(R.id.tvTitle).setText(R.string.login_title)
            findViewById<TextView>(R.id.tvSubtitle).setText(R.string.login_subtitle)
            findViewById<TextView>(R.id.tvUsernameLabel).setText(R.string.label_username)
            etUsername.setHint(R.string.hint_username)
            etUsername.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setupFooterLink(R.string.no_account_prefix, R.string.sign_up) {
                startActivity(Intent(this, RegisterActivity::class.java))
            }
        }

        cbRemember.isChecked = SessionPrefs.rememberMe(this)

        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
        btnLogin.setOnClickListener { attemptLogin() }
    }

    private fun attemptLogin() {
        val username = etUsername.text?.toString()?.trim().orEmpty()
        val password = etPassword.text?.toString().orEmpty()
        tilUsername.error = null
        tilPassword.error = null

        var valid = true
        if (username.isEmpty()) {
            tilUsername.error = getString(R.string.error_required)
            valid = false
        } else if (isStaff && !Patterns.EMAIL_ADDRESS.matcher(username).matches()) {
            tilUsername.error = getString(R.string.error_invalid_email)
            valid = false
        }
        if (password.isEmpty()) {
            tilPassword.error = getString(R.string.error_required)
            valid = false
        }
        if (!valid) return

        val roles = if (isStaff) AuthRepository.STAFF_ROLES else AuthRepository.PATIENT_ROLES
        setLoading(true, btnLogin)
        lifecycleScope.launch {
            AuthRepository.signIn(username, password, roles)
                .onSuccess { account ->
                    SessionPrefs.setRememberMe(this@LoginActivity, cbRemember.isChecked)
                    goAfterLogin(account)
                }
                .onFailure {
                    setLoading(false, btnLogin)
                    toast(it.friendly())
                }
        }
    }
}
