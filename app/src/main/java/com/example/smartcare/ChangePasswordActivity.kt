package com.example.smartcare

import android.content.Context
import android.content.Intent
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
 *
 * Opened with [changeIntent] from the staff profile it is a normal "change my password"
 * screen instead: the first field is the current password, Back just closes it, and
 * success returns to the profile.
 */
class ChangePasswordActivity : BaseActivity() {

    private lateinit var fieldTemp: SmartField
    private lateinit var fieldNew: SmartField
    private lateinit var fieldConfirm: SmartField
    private lateinit var btnSave: MaterialButton
    private val ownChange by lazy { intent.getBooleanExtra(EXTRA_OWN_CHANGE, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)
        if (ownChange) {
            setupHeader(R.string.change_own_title, R.string.change_own_sub)
        } else {
            setupHeader(
                R.string.change_hero_title,
                R.string.change_hero_sub,
                showBack = false,
                showBrand = true,
                brandCentered = false,
                pill = getString(R.string.change_pill)
            )
        }

        fieldTemp = findViewById(R.id.fieldTemp)
        fieldNew = findViewById(R.id.fieldNew)
        fieldConfirm = findViewById(R.id.fieldConfirm)
        btnSave = findViewById(R.id.btnSave)

        fieldTemp.editText.doAfterTextChanged { updateRules() }
        fieldNew.editText.doAfterTextChanged { updateRules() }
        fieldConfirm.editText.doAfterTextChanged { updateRules() }
        updateRules()
        if (ownChange) {
            fieldTemp.inputLayout.hint = getString(R.string.field_current_password)
            btnSave.setText(R.string.action_save)
        }

        // First login: leaving without a new password logs the user out.
        if (!ownChange) onBackPressedDispatcher.addCallback(this) {
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
            val result = if (ownChange) AuthRepository.changeOwnPassword(fieldTemp.text, fieldNew.text)
                else AuthRepository.changeTemporaryPassword(fieldTemp.text, fieldNew.text)
            result
                .onSuccess {
                    toast(R.string.msg_password_changed)
                    if (ownChange) { finish(); return@onSuccess }
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

    companion object {
        private const val EXTRA_OWN_CHANGE = "own_change"

        /** A logged-in user changing their own password (not the first-login flow). */
        fun changeIntent(context: Context) =
            Intent(context, ChangePasswordActivity::class.java).putExtra(EXTRA_OWN_CHANGE, true)
    }
}
