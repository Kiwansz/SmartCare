package com.example.smartcare

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AuthRepository
import com.example.smartcare.data.SessionPrefs
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/** 1. Role picker: pick patient or staff, then Continue. First screen of the app. */
class RoleActivity : BaseActivity() {

    private companion object {
        const val STATE_STAFF = "staff_selected"
    }

    private lateinit var choicePatient: View
    private lateinit var choiceStaff: View
    private var staffSelected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role)

        choicePatient = findViewById(R.id.choicePatient)
        choiceStaff = findViewById(R.id.choiceStaff)
        setupChoice(choicePatient, R.drawable.ic_sc_user, R.string.role_choice_patient, R.string.role_choice_patient_desc)
        setupChoice(choiceStaff, R.drawable.ic_sc_staff, R.string.role_choice_staff, R.string.role_choice_staff_desc)
        choicePatient.setOnClickListener { select(staff = false) }
        choiceStaff.setOnClickListener { select(staff = true) }
        select(savedInstanceState?.getBoolean(STATE_STAFF) ?: false)

        findViewById<MaterialButton>(R.id.btnContinue).setOnClickListener {
            val target = if (staffSelected) StaffLoginActivity::class.java else LoginActivity::class.java
            startActivity(android.content.Intent(this, target))
        }

        if (savedInstanceState == null) restoreSession()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_STAFF, staffSelected)
    }

    private fun select(staff: Boolean) {
        staffSelected = staff
        choicePatient.isSelected = !staff
        choiceStaff.isSelected = staff
    }

    /** "Remember me": skip straight to the right home if a saved session exists; otherwise clear it. */
    private fun restoreSession() {
        val progress = findViewById<View>(R.id.progress)
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val account = AuthRepository.currentAccount()
            when {
                account != null && SessionPrefs.rememberMe(this@RoleActivity) -> {
                    goAfterLogin(account)
                    return@launch
                }
                account != null -> AuthRepository.signOut()
            }
            progress.visibility = View.GONE
        }
    }

    private fun setupChoice(card: View, @DrawableRes icon: Int, @StringRes title: Int, @StringRes desc: Int) {
        card.findViewById<ImageView>(R.id.choiceIcon).setImageResource(icon)
        card.findViewById<TextView>(R.id.choiceTitle).setText(title)
        card.findViewById<TextView>(R.id.choiceDesc).setText(desc)
    }
}
