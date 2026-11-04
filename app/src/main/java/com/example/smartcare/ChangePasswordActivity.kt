package com.example.smartcare

import android.os.Bundle
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AuthRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** 6. Change password: forced on first staff login (must_change_password = true). */
class ChangePasswordActivity : BaseActivity() {

    private lateinit var etTemp: TextInputEditText
    private lateinit var etNew: TextInputEditText
    private lateinit var etConfirm: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        val tilTemp = findViewById<TextInputLayout>(R.id.tilTempPassword)
        val tilNew = findViewById<TextInputLayout>(R.id.tilNewPassword)
        val tilConfirm = findViewById<TextInputLayout>(R.id.tilConfirmPassword)
        etTemp = findViewById(R.id.etTempPassword)
        etNew = findViewById(R.id.etNewPassword)
        etConfirm = findViewById(R.id.etConfirmPassword)
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)

        // Update the rule checklist as the user types.
        etNew.doAfterTextChanged { updateRules() }
        etConfirm.doAfterTextChanged { updateRules() }
        updateRules()

        // Leaving without a new password logs the user out.
        onBackPressedDispatcher.addCallback(this) {
            lifecycleScope.launch {
                AuthRepository.signOut()
                goToRolePicker()
            }
        }

        btnSave.setOnClickListener {
            val temp = etTemp.text?.toString().orEmpty()
            val newPw = etNew.text?.toString().orEmpty()
            val confirm = etConfirm.text?.toString().orEmpty()

            tilTemp.error = if (temp.isEmpty()) getString(R.string.error_required) else null
            tilNew.error = if (!rules(newPw, confirm).take(3).all { it }) getString(R.string.error_password_rules) else null
            tilConfirm.error = if (newPw != confirm) getString(R.string.error_password_mismatch) else null
            if (tilTemp.error != null || tilNew.error != null || tilConfirm.error != null) return@setOnClickListener

            setLoading(true, btnSave)
            lifecycleScope.launch {
                AuthRepository.changeTemporaryPassword(temp, newPw)
                    .onSuccess {
                        toast(R.string.msg_password_changed)
                        val account = AuthRepository.currentAccount()
                        if (account != null) goAfterLogin(account) else goToRolePicker()
                    }
                    .onFailure {
                        setLoading(false, btnSave)
                        toast(it.friendly())
                    }
            }
        }
    }

    /** [length, uppercase, number, match] */
    private fun rules(pw: String, confirm: String) = listOf(
        pw.length >= 8,
        pw.any { it.isUpperCase() },
        pw.any { it.isDigit() },
        pw.isNotEmpty() && pw == confirm
    )

    private fun updateRules() {
        val results = rules(etNew.text?.toString().orEmpty(), etConfirm.text?.toString().orEmpty())
        listOf(R.id.tvRuleLength, R.id.tvRuleUpper, R.id.tvRuleNumber, R.id.tvRuleMatch)
            .zip(results)
            .forEach { (id, ok) ->
                findViewById<TextView>(id).setCompoundDrawablesRelativeWithIntrinsicBounds(
                    if (ok) R.drawable.ic_check_on else R.drawable.ic_check_off, 0, 0, 0
                )
            }
    }
}
