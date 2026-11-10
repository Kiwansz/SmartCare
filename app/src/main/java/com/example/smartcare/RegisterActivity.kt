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
import com.example.smartcare.data.EmailAlreadyRegisteredException
import com.example.smartcare.data.IcAlreadyRegisteredException
import com.example.smartcare.ui.SmartField
import com.example.smartcare.ui.StrengthMeter
import com.example.smartcare.util.IcInfo
import com.example.smartcare.util.PasswordRules
import com.example.smartcare.util.icDigits
import com.example.smartcare.util.parseIc
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 3. Patient sign up in two steps (one screen, two panels).
 * Step 1: full name, IC number (date of birth fills in from it), optional gender, phone.
 * Step 2: email, password with strength bar, confirm, terms.
 * Saves through AuthRepository.signUpPatient (Auth user + user_account + patient).
 *
 * Duplicate checks: the IC is checked as soon as 12 valid digits are typed and again on
 * Next; the email shortly after a valid address is typed and again on Create account.
 * signUpPatient checks both once more before creating anything.
 */
class RegisterActivity : BaseActivity() {

    private companion object {
        const val STATE_STEP = "step"
        const val STATE_GENDER = "gender"
        const val EMAIL_CHECK_DELAY_MS = 600L
        val DOB_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    }

    private var step = 1

    /** MALE, FEMALE or null (not chosen). Never guessed from the IC. */
    private var gender: String? = null
    private lateinit var genderOptions: Map<String, View>

    /** Last duplicate-check results, so the same value isn't checked twice. */
    private val icChecked = mutableMapOf<String, Boolean>()     // digits -> taken
    private val emailChecked = mutableMapOf<String, Boolean>()  // lowercase email -> taken
    private var icCheckJob: Job? = null
    private var emailCheckJob: Job? = null

    private lateinit var step1: View
    private lateinit var step2: View
    private lateinit var fieldFullName: SmartField
    private lateinit var fieldIc: SmartField
    private lateinit var fieldPhone: SmartField
    private lateinit var fieldEmail: SmartField
    private lateinit var fieldPassword: SmartField
    private lateinit var fieldConfirm: SmartField
    private lateinit var linkIcLogin: View
    private lateinit var linkEmailLogin: View
    private lateinit var tvDob: TextView
    private lateinit var meter: View
    private lateinit var cbTerms: MaterialCheckBox
    private lateinit var tvTermsError: TextView
    private lateinit var btnNext: MaterialButton
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
        linkIcLogin = findViewById(R.id.linkIcLogin)
        linkEmailLogin = findViewById(R.id.linkEmailLogin)
        tvDob = findViewById(R.id.tvDob)
        genderOptions = mapOf(
            "MALE" to findViewById(R.id.genderMale),
            "FEMALE" to findViewById(R.id.genderFemale)
        )
        // Tap to choose, tap the chosen one again to clear (gender is optional).
        genderOptions.forEach { (value, view) ->
            view.setOnClickListener { selectGender(if (gender == value) null else value) }
        }
        meter = findViewById(R.id.meter)
        cbTerms = findViewById(R.id.cbTerms)
        tvTermsError = findViewById(R.id.tvTermsError)
        btnNext = findViewById(R.id.btnNext)
        btnCreate = findViewById(R.id.btnCreate)

        // "Log in instead": the login screen is the previous screen.
        linkIcLogin.setOnClickListener { finish() }
        linkEmailLogin.setOnClickListener { finish() }

        // Live: date of birth from the IC, then the duplicate check.
        fieldIc.editText.doAfterTextChanged { onIcChanged() }
        // Live: duplicate check shortly after a valid email is typed.
        fieldEmail.editText.doAfterTextChanged { onEmailChanged() }
        // Live: strength bar.
        fieldPassword.editText.doAfterTextChanged { StrengthMeter.bind(meter, PasswordRules.check(fieldPassword.text)) }
        cbTerms.setOnCheckedChangeListener { _, checked -> if (checked) tvTermsError.visibility = View.GONE }

        btnNext.setOnClickListener { goToStep2() }
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
        onIcChanged()
        onEmailChanged()
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

    // ---------- IC number ----------

    /** IC info while typing; shows the date error only once all 12 digits are in. */
    private fun liveIc(): IcInfo? {
        val digits = icDigits(fieldIc.text)
        if (digits.length != 12) return null
        val info = parseIc(digits)
        if (info == null) fieldIc.error = getString(R.string.error_invalid_ic_date)
        return info
    }

    private fun onIcChanged() {
        val info = liveIc()
        showIcInfo(info)
        icCheckJob?.cancel()
        linkIcLogin.visibility = View.GONE
        fieldIc.helperText = getString(R.string.helper_ic)
        if (info == null) return

        val digits = icDigits(fieldIc.text)
        when (icChecked[digits]) {
            true -> showIcTaken()
            false -> Unit
            null -> {
                fieldIc.helperText = getString(R.string.helper_checking)
                icCheckJob = lifecycleScope.launch {
                    val taken = AuthRepository.isIcRegistered(digits).getOrNull()
                    fieldIc.helperText = getString(R.string.helper_ic)
                    // No result (e.g. offline): it is checked again on Next.
                    if (taken != null) icChecked[digits] = taken
                    if (taken == true && icDigits(fieldIc.text) == digits) showIcTaken()
                }
            }
        }
    }

    private fun showIcTaken() {
        fieldIc.error = getString(R.string.error_ic_registered)
        linkIcLogin.visibility = View.VISIBLE
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

    // ---------- Email ----------

    private fun onEmailChanged() {
        emailCheckJob?.cancel()
        linkEmailLogin.visibility = View.GONE
        val email = fieldEmail.text.trim().lowercase()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) return

        when (emailChecked[email]) {
            true -> showEmailTaken()
            false -> Unit
            null -> emailCheckJob = lifecycleScope.launch {
                delay(EMAIL_CHECK_DELAY_MS) // wait until the user stops typing
                val taken = AuthRepository.isEmailRegistered(email).getOrNull()
                if (taken != null) emailChecked[email] = taken
                if (taken == true && fieldEmail.text.trim().lowercase() == email) showEmailTaken()
            }
        }
    }

    private fun showEmailTaken() {
        fieldEmail.error = getString(R.string.error_email_registered)
        linkEmailLogin.visibility = View.VISIBLE
    }

    // ---------- Gender ----------

    private fun selectGender(value: String?) {
        gender = value
        genderOptions.forEach { (key, view) -> view.isSelected = key == value }
    }

    // ---------- Buttons ----------

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
            icChecked[ic] == true -> { showIcTaken(); valid = false }
        }
        if (phone.isEmpty()) {
            fieldPhone.error = getString(R.string.error_required); valid = false
        } else if (!Patterns.PHONE.matcher(phone).matches() || phone.count { it.isDigit() } < 9) {
            fieldPhone.error = getString(R.string.error_invalid_phone); valid = false
        }
        if (!valid) return

        // Already confirmed free: go straight on.
        if (icChecked[ic] == false) {
            showStep(2)
            return
        }
        // Not checked yet (still typing, or the live check failed): check now.
        icCheckJob?.cancel()
        setLoading(true, btnNext)
        lifecycleScope.launch {
            AuthRepository.isIcRegistered(ic)
                .onSuccess { taken ->
                    setLoading(false, btnNext)
                    icChecked[ic] = taken
                    if (taken) showIcTaken() else showStep(2)
                }
                .onFailure {
                    setLoading(false, btnNext)
                    toast(it.friendly())
                }
        }
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
        } else if (emailChecked[email.lowercase()] == true) {
            showEmailTaken(); valid = false
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

        emailCheckJob?.cancel()
        setLoading(true, btnCreate)
        lifecycleScope.launch {
            // signUpPatient checks IC and email again before creating anything.
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
                    when (it) {
                        is EmailAlreadyRegisteredException -> {
                            emailChecked[email.lowercase()] = true
                            showEmailTaken()
                        }
                        is IcAlreadyRegisteredException -> {
                            // Taken since step 1: go back and show it there.
                            icChecked[ic] = true
                            showStep(1)
                            showIcTaken()
                        }
                        else -> toast(it.friendly())
                    }
                }
        }
    }
}
