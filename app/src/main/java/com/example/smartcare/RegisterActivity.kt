package com.example.smartcare

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AuthRepository
import com.example.smartcare.ui.SmartField
import com.example.smartcare.ui.StrengthMeter
import com.example.smartcare.util.IcInfo
import com.example.smartcare.util.PasswordRules
import com.example.smartcare.util.icDigits
import com.example.smartcare.util.parseIc
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 3. Patient sign up in two steps (one screen, two panels).
 * Step 1: full name, IC number (date of birth fills in from it), optional gender, phone.
 * Step 2: email, password with strength bar, confirm, terms.
 * Saves through the existing AuthRepository.signUpPatient (Auth user + user_account + patient).
 */
class RegisterActivity : BaseActivity() {

    private companion object {
        const val STATE_STEP = "step"
        const val STATE_GENDER = "gender"
        val DOB_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    }

    private var step = 1

    /** MALE, FEMALE, OTHER or null (not chosen). Never guessed from the IC. */
    private var gender: String? = null
    private lateinit var genderOptions: Map<String, View>

    private lateinit var step1: View
    private lateinit var step2: View
    private lateinit var fieldFullName: SmartField
    private lateinit var fieldIc: SmartField
    private lateinit var fieldPhone: SmartField
    private lateinit var fieldEmail: SmartField
    private lateinit var fieldPassword: SmartField
    private lateinit var fieldConfirm: SmartField
    private lateinit var tvDob: TextView
    private lateinit var meter: View
    private lateinit var cbTerms: MaterialCheckBox
    private lateinit var tvTermsError: TextView
    private lateinit var btnCreate: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        step1 = findViewById(R.id.step1)
        step2 = findViewById(R.id.step2)
        fieldFullName = findViewById(R.id.fieldFullName)
        fieldIc = findViewById(R.id.fieldIc)
        fieldPhone = findViewById(R.id.fieldPhone)
        fieldEmail = findViewById(R.id.fieldEmail)
        fieldPassword = findViewById(R.id.fieldPassword)
        fieldConfirm = findViewById(R.id.fieldConfirm)
        tvDob = findViewById(R.id.tvDob)
        genderOptions = mapOf(
            "MALE" to findViewById(R.id.genderMale),
            "FEMALE" to findViewById(R.id.genderFemale),
            "OTHER" to findViewById(R.id.genderOther)
        )
        // Tap to choose, tap the chosen one again to clear (gender is optional).
        genderOptions.forEach { (value, view) ->
            view.setOnClickListener { selectGender(if (gender == value) null else value) }
        }
        meter = findViewById(R.id.meter)
        cbTerms = findViewById(R.id.cbTerms)
        tvTermsError = findViewById(R.id.tvTermsError)
        btnCreate = findViewById(R.id.btnCreate)

        // Live: fill the "From IC" cards once 12 digits are typed.
        fieldIc.editText.doAfterTextChanged { showIcInfo(liveIc()) }
        // Live: strength bar.
        fieldPassword.editText.doAfterTextChanged { StrengthMeter.bind(meter, PasswordRules.check(fieldPassword.text)) }
        cbTerms.setOnCheckedChangeListener { _, checked -> if (checked) tvTermsError.visibility = View.GONE }

        findViewById<MaterialButton>(R.id.btnNext).setOnClickListener { goToStep2() }
        fieldPhone.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { goToStep2(); true } else false
        }
        btnCreate.setOnClickListener { createAccount() }

        // Back (header arrow or system back): step 2 -> step 1, step 1 -> close.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (step == 2) showStep(1) else finish()
            }
        })

        showStep(savedInstanceState?.getInt(STATE_STEP) ?: 1)
        selectGender(savedInstanceState?.getString(STATE_GENDER))
        showIcInfo(liveIc())
        StrengthMeter.bind(meter, PasswordRules.check(fieldPassword.text))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_STEP, step)
        outState.putString(STATE_GENDER, gender)
    }

    private fun showStep(n: Int) {
        step = n
        step1.visibility = if (n == 1) View.VISIBLE else View.GONE
        step2.visibility = if (n == 2) View.VISIBLE else View.GONE
        setupHeader(
            title = if (n == 1) R.string.signup_step1_title else R.string.signup_step2_title,
            pill = getString(R.string.step_pill, n),
            step = n
        )
    }

    /** IC info while typing; shows the date error only once all 12 digits are in. */
    private fun liveIc(): IcInfo? {
        val digits = icDigits(fieldIc.text)
        if (digits.length != 12) return null
        val info = parseIc(digits)
        if (info == null) fieldIc.error = getString(R.string.error_invalid_ic_date)
        return info
    }

    private fun showIcInfo(info: IcInfo?) {
        if (info == null) {
            tvDob.setText(R.string.from_ic_dob_placeholder)
            tvDob.setTextColor(getColor(R.color.ink_placeholder))
        } else {
            tvDob.text = info.dateOfBirth.format(DOB_FORMAT)
            tvDob.setTextColor(getColor(R.color.ink))
        }
    }

    private fun selectGender(value: String?) {
        gender = value
        genderOptions.forEach { (key, view) -> view.isSelected = key == value }
    }

    private fun goToStep2() {
        val name = fieldFullName.text.trim()
        val ic = icDigits(fieldIc.text)
        val phone = fieldPhone.text.trim()
        var valid = true

        if (name.isEmpty()) {
            fieldFullName.error = getString(R.string.error_required); valid = false
        }
        when {
            fieldIc.text.isBlank() -> { fieldIc.error = getString(R.string.error_required); valid = false }
            ic.length != 12 -> { fieldIc.error = getString(R.string.error_ic_length); valid = false }
            parseIc(ic) == null -> { fieldIc.error = getString(R.string.error_invalid_ic_date); valid = false }
        }
        if (phone.isEmpty()) {
            fieldPhone.error = getString(R.string.error_required); valid = false
        } else if (!Patterns.PHONE.matcher(phone).matches() || phone.count { it.isDigit() } < 9) {
            fieldPhone.error = getString(R.string.error_invalid_phone); valid = false
        }
        if (valid) showStep(2)
    }

    private fun createAccount() {
        val email = fieldEmail.text.trim()
        val password = fieldPassword.text
        val confirm = fieldConfirm.text
        var valid = true

        if (email.isEmpty()) {
            fieldEmail.error = getString(R.string.error_required); valid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            fieldEmail.error = getString(R.string.error_invalid_email); valid = false
        }
        if (password.isEmpty()) {
            fieldPassword.error = getString(R.string.error_required); valid = false
        } else if (!PasswordRules.check(password).isValid) {
            fieldPassword.error = getString(R.string.error_password_needs); valid = false
        }
        if (confirm.isEmpty()) {
            fieldConfirm.error = getString(R.string.error_required); valid = false
        } else if (!PasswordRules.matches(password, confirm)) {
            fieldConfirm.error = getString(R.string.error_password_mismatch); valid = false
        }
        if (!cbTerms.isChecked) {
            tvTermsError.visibility = View.VISIBLE; valid = false
        }
        if (!valid) return

        val name = fieldFullName.text.trim()
        val ic = icDigits(fieldIc.text)
        val phone = fieldPhone.text.trim()

        setLoading(true, btnCreate)
        lifecycleScope.launch {
            AuthRepository.signUpPatient(name, ic, email, phone, password, gender)
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
                    setLoading(false, btnCreate)
                    toast(it.friendly())
                }
        }
    }
}
