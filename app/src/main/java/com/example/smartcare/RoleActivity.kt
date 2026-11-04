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
import kotlinx.coroutines.launch

/** 1. Role picker: first screen, decides which login the user sees. */
class RoleActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role)

        setupCard(R.id.cardPatient, R.drawable.ic_person, R.string.role_patient, R.string.role_patient_desc) {
            startActivity(LoginActivity.intent(this, staff = false))
        }
        setupCard(R.id.cardStaff, R.drawable.ic_medical_bag, R.string.role_staff, R.string.role_staff_desc) {
            startActivity(LoginActivity.intent(this, staff = true))
        }

        if (savedInstanceState == null) restoreSession()
    }

    /** "Remember me": skip straight to Home if a saved session exists; otherwise clear it. */
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

    private fun setupCard(
        cardId: Int,
        @DrawableRes icon: Int,
        @StringRes title: Int,
        @StringRes desc: Int,
        onClick: () -> Unit
    ) {
        val card = findViewById<View>(cardId)
        card.findViewById<ImageView>(R.id.ivRoleIcon).setImageResource(icon)
        card.findViewById<TextView>(R.id.tvRoleTitle).setText(title)
        card.findViewById<TextView>(R.id.tvRoleDesc).setText(desc)
        card.setOnClickListener { onClick() }
    }
}
