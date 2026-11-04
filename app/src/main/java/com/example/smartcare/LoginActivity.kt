package com.example.smartcare

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AuthRepository
import com.example.smartcare.data.InvalidLoginException
import com.example.smartcare.data.SessionPrefs
import com.example.smartcare.ui.SmartField
import com.example.smartcare.util.icDigits
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch

/**
 * 2. Patient login: IC number + password. Finds the account by IC, signs in with
 * Supabase Auth using its email, then checks role = PATIENT and status = ACTIVE.
 *
 * The staff branch (work email) is temporary until StaffLoginActivity exists (step 7).
 */
class LoginActivity : BaseActivity() {

    companion object {
        private const val EXTRA_STAFF = "staff"

        fun intent(context: Context, staff: Boolean) =
            Intent(context, LoginActivity::class.java).putExtra(EXTRA_STAFF, staff)
    }

    private val isStaff by lazy { intent.getBooleanExtra(EXTRA_STAFF, false) }

    private lateinit var fieldUser: SmartField
    private lateinit var fieldPassword: SmartField
    private lateinit var switchRemember: MaterialSwitch
    private lateinit var btnLogin: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        setupHeader(R.string.login_hero_title, R.string.login_hero_sub, showBrand = true)

        fieldUser = findViewById(R.id.fieldIc)
        fieldPassword = findViewById(R.id.fieldPassword)
        switchRemember = findViewById(R.id.switchRemember)
        btnLogin = findViewById(R.id.btnLogin)

        if (isStaff) {
            // TODO(step 7): replace with StaffLoginActivity (navy theme).
            fieldUser.inputLayout.hint = getString(R.string.label_work_email)
            fieldUser.inputLayout.placeholderText = getString(R.string.hint_work_email)
            fieldUser.inputLayout.setStartIconDrawable(R.drawable.ic_sc_mail)
            fieldUser.editText.filters = arrayOf()
            // Setting inputType also replaces the IC digits-only key listener.
            fieldUser.editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            fieldUser.helperText = null
            findViewById<TextView>(R.id.tvFooter).text = null
        } else {
            setupFooterLink(R.string.footer_new_prefix, R.string.footer_create_account) {
                startActivity(Intent(this, RegisterActivity::class.java))
            }
        }

        switchRemember.isChecked = SessionPrefs.rememberMe(this)
        if (!isStaff && savedInstanceState == null) {
            SessionPrefs.savedIc(this)?.let { fieldUser.text = it }
        }

        findViewById<TextView>(R.id.linkForgot).setOnClickListener {
            startActivity(ForgotPasswordActivity.intent(this, staff = isStaff))
        }
        fieldPassword.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { attemptLogin(); true } else false
        }
        btnLogin.setOnClickListener { attemptLogin() }
    }

    private fun attemptLogin() {
        val user = fieldUser.text.trim()
        val password = fieldPassword.text
        fieldUser.error = null
        fieldPassword.error = null

        var valid = true
        if (user.isEmpty()) {
            fieldUser.error = getString(R.string.error_required); valid = false
        } else if (isStaff && !Patterns.EMAIL_ADDRESS.matcher(user).matches()) {
            fieldUser.error = getString(R.string.error_invalid_email); valid = false
        } else if (!isStaff && icDigits(user).length != 12) {
            fieldUser.error = getString(R.string.error_ic_length); valid = false
        }
        if (password.isEmpty()) {
            fieldPassword.error = getString(R.string.error_required); valid = false
        }
        if (!valid) return

        val username = if (isStaff) user else icDigits(user)
        val roles = if (isStaff) AuthRepository.STAFF_ROLES else AuthRepository.PATIENT_ROLES
        setLoading(true, btnLogin)
        lifecycleScope.launch {
            AuthRepository.signIn(username, password, roles)
                .onSuccess { account ->
                    val remember = switchRemember.isChecked
                    SessionPrefs.setRememberMe(this@LoginActivity, remember)
                    if (!isStaff) SessionPrefs.setSavedIc(this@LoginActivity, if (remember) username else null)
                    goAfterLogin(account)
                }
                .onFailure {
                    setLoading(false, btnLogin)
                    toast(
                        if (it is InvalidLoginException) {
                            getString(if (isStaff) R.string.error_login_email else R.string.error_login_ic)
                        } else {
                            it.friendly()
                        }
                    )
                }
        }
    }
}
