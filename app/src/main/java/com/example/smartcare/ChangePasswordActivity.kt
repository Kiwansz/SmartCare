package com.example.smartcare

import android.os.Bundle
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AuthRepository
import com.example.smartcare.ui.SmartField
import com.example.smartcare.ui.StrengthMeter
import com.example.smartcare.util.PasswordRules
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * 6. Change password (navy): forced on first staff login (must_change_password = true).
 * Rule chips and the strength bar update live; Save stays disabled until every rule
 * passes and both new-password fields match. Saving uses the existing
 * AuthRepository.changeTemporaryPassword (checks the temp password, sets the new one,
 * clears the flag), then opens the staff home.
 */
class ChangePasswordActivity : BaseActivity() {

    private lateinit var fieldTemp: SmartField
    private lateinit var fieldNew: SmartField
    private lateinit var fieldConfirm: SmartField
    private lateinit var btnSave: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)
        setupHeader(
            R.string.change_hero_title,
            R.string.change_hero_sub,
            showBack = false,
            showBrand = true,
            brandCentered = false,
            pill = getString(R.string.change_pill)
        )

        fieldTemp = findViewById(R.id.fieldTemp)
        fieldNew = findViewById(R.id.fieldNew)
        fieldConfirm = findViewById(R.id.fieldConfirm)
        btnSave = findViewById(R.id.btnSave)

        fieldTemp.editText.doAfterTextChanged { updateRules() }
        fieldNew.editText.doAfterTextChanged { updateRules() }
        fieldConfirm.editText.doAfterTextChanged { updateRules() }
        updateRules()

        // Leaving without a new password logs the user out.
        onBackPressedDispatcher.addCallback(this) {
            lifecycleScope.launch {
                AuthRepository.signOut()
                goToRolePicker()
            }
        }

        btnSave.setOnClickListener { save() }
    }

    private fun updateRules() {
        val pw = fieldNew.text
        val check = PasswordRules.check(pw)
        val match = PasswordRules.matches(pw, fieldConfirm.text)

        StrengthMeter.bind(findViewById(R.id.meter), check)
        setChip(R.id.chipLength, check.longEnough)
        setChip(R.id.chipUpper, check.hasUppercase)
        setChip(R.id.chipNumber, check.hasNumber)
        setChip(R.id.chipMatch, match)

        val ready = check.isValid && match && fieldTemp.text.isNotEmpty()
        btnSave.isEnabled = ready
        btnSave.alpha = if (ready) 1f else 0.5f
    }

    private fun setChip(id: Int, ok: Boolean) {
        findViewById<TextView>(id).apply {
            setBackgroundResource(if (ok) R.drawable.bg_chip_ok else R.drawable.bg_chip_off)
            setTextColor(if (ok) themeColor(R.attr.scMain) ?: currentTextColor else getColor(R.color.ink_sub))
            setCompoundDrawablesRelativeWithIntrinsicBounds(if (ok) R.drawable.ic_sc_check_small else 0, 0, 0, 0)
        }
    }

    private fun save() {
        setLoading(true, btnSave)
        lifecycleScope.launch {
            AuthRepository.changeTemporaryPassword(fieldTemp.text, fieldNew.text)
                .onSuccess {
                    toast(R.string.msg_password_changed)
                    val account = AuthRepository.currentAccount()
                    if (account != null) goAfterLogin(account) else goToRolePicker()
                }
                .onFailure {
                    setLoading(false, btnSave)
                    updateRules()
                    toast(it.friendly())
                }
        }
    }
}
