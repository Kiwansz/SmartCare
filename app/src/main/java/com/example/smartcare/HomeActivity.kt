package com.example.smartcare

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.Account
import com.example.smartcare.data.AuthRepository
import com.example.smartcare.data.SessionPrefs
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/** 7. Home: landing screen after login. Patient version; staff see a placeholder for now. */
class HomeActivity : BaseActivity() {

    private var account: Account? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        findViewById<View>(R.id.cardBook).setOnClickListener { toast(R.string.coming_soon) }
        findViewById<View>(R.id.cardFollowups).setOnClickListener { toast(R.string.coming_soon) }

        findViewById<BottomNavigationView>(R.id.bottomNav).setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_visits -> { toast(R.string.coming_soon); false }
                R.id.nav_profile -> { showProfile(); false }
                else -> false
            }
        }

        loadAccount()
    }

    private fun loadAccount() {
        lifecycleScope.launch {
            val acc = AuthRepository.currentAccount()
            if (acc == null) {
                // Session ended or account missing: back to the start.
                goToRolePicker()
                return@launch
            }
            account = acc
            findViewById<TextView>(R.id.tvName).text = acc.fullName
            findViewById<TextView>(R.id.tvInitials).text = initials(acc.fullName)

            if (acc.role == "PATIENT" && acc.patientId != null) {
                loadNextAppointment(acc.patientId)
            } else {
                findViewById<View>(R.id.patientSection).visibility = View.GONE
                findViewById<TextView>(R.id.tvStaffNote).apply {
                    visibility = View.VISIBLE
                    text = getString(R.string.home_staff_note, acc.role.lowercase())
                }
            }
        }
    }

    private suspend fun loadNextAppointment(patientId: Int) {
        val tvWhen = findViewById<TextView>(R.id.tvApptWhen)
        val tvDetail = findViewById<TextView>(R.id.tvApptDetail)
        val appt = AuthRepository.nextAppointment(patientId).getOrNull()
        if (appt == null) {
            tvWhen.setText(R.string.home_no_appointment)
            tvDetail.setText(R.string.home_no_appointment_desc)
            return
        }
        tvWhen.text = SimpleDateFormat("EEE, d MMM · h:mm a", Locale.ENGLISH).format(appt.slotStart)
        tvDetail.text = listOfNotNull(appt.doctorName?.let { "Dr. $it" }, appt.reason)
            .joinToString(" · ")
    }

    private fun showProfile() {
        val acc = account ?: return
        AlertDialog.Builder(this)
            .setTitle(acc.fullName)
            .setMessage("${acc.email}\n${acc.role.lowercase().replaceFirstChar { it.uppercase() }}")
            .setPositiveButton(R.string.logout) { _, _ ->
                lifecycleScope.launch {
                    AuthRepository.signOut()
                    SessionPrefs.setRememberMe(this@HomeActivity, false)
                    goToRolePicker()
                }
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    /** "Ahmad Hafiz" -> "AH" */
    private fun initials(name: String) = name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
}
