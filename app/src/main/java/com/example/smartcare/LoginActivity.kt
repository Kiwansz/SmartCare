package com.example.smartcare

import android.content.Intent
import android.os.Bundle
import android.view.View
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
 * Supabase Auth using its email, then checks role = PATIENT and status = ACTIVE
 * (AuthRepository.signIn, unchanged). Staff use StaffLoginActivity.
 */
class LoginActivity : BaseActivity() {

    private lateinit var fieldIc: SmartField
    private lateinit var fieldPassword: SmartField
    private lateinit var switchRemember: MaterialSwitch
    private lateinit var btnLogin: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        setupHeader(R.string.login_hero_title, R.string.login_hero_sub, showBrand = true)
        setupFooterLink(R.string.footer_new_prefix, R.string.footer_create_account) {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        fieldIc = findViewById(R.id.fieldIc)
        fieldPassword = findViewById(R.id.fieldPassword)
        switchRemember = findViewById(R.id.switchRemember)
        btnLogin = findViewById(R.id.btnLogin)

        switchRemember.isChecked = SessionPrefs.rememberMe(this)
        // Tapping the "Remember me" text toggles the switch as well.
        findViewById<View>(R.id.labelRemember).setOnClickListener { switchRemember.toggle() }
        if (savedInstanceState == null) {
            SessionPrefs.savedIc(this)?.let { fieldIc.text = it }
        }

        findViewById<TextView>(R.id.linkForgot).setOnClickListener {
            startActivity(ForgotPasswordActivity.intent(this, staff = false))
        }
        fieldPassword.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { attemptLogin(); true } else false
        }
        btnLogin.setOnClickListener { attemptLogin() }
    }

    private fun attemptLogin() {
        val ic = icDigits(fieldIc.text)
        val password = fieldPassword.text
        var valid = true

        if (fieldIc.text.isBlank()) {
            fieldIc.error = getString(R.string.error_required); valid = false
        } else if (ic.length != 12) {
            fieldIc.error = getString(R.string.error_ic_length); valid = false
        }
        if (password.isEmpty()) {
            fieldPassword.error = getString(R.string.error_required); valid = false
        }
        if (!valid) return

        setLoading(true, btnLogin)
        lifecycleScope.launch {
            AuthRepository.signIn(ic, password, AuthRepository.PATIENT_ROLES)
                .onSuccess { account ->
                    val remember = switchRemember.isChecked
                    SessionPrefs.setRememberMe(this@LoginActivity, remember)
                    SessionPrefs.setSavedIc(this@LoginActivity, if (remember) ic else null)
                    goAfterLogin(account)
                }
                .onFailure {
                    setLoading(false, btnLogin)
                    toast(if (it is InvalidLoginException) getString(R.string.error_login_ic) else it.friendly())
                }
        }
    }
}
