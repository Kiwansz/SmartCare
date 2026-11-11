package com.example.smartcare

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.Account
import com.example.smartcare.data.AuthRepository
import com.example.smartcare.data.ClinicSettings
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Staff home (Module 1 shell), navy theme. Shows only data Module 1 owns: the user's own
 * account (name, role) and the clinic_setting row. Clinic features belong to other
 * modules and open a "Coming soon" screen until they exist.
 */
class StaffHomeActivity : BaseActivity() {

    private lateinit var stateLoading: View
    private lateinit var stateError: View
    private lateinit var content: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staff_home)

        stateLoading = findViewById(R.id.stateLoading)
        stateError = findViewById(R.id.stateError)
        content = findViewById(R.id.content)

        findViewById<TextView>(R.id.tvGreeting).setText(greeting())
        findViewById<MaterialButton>(R.id.btnRetry).setOnClickListener { load() }
        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener { logOut() }

        load()
    }

    private fun load() {
        showState(loading = true)
        lifecycleScope.launch {
            // Both reads in parallel; the clinic row is optional for showing the home.
            val clinicJob = async { AuthRepository.loadClinicSettings() }
            val accountResult = AuthRepository.loadCurrentAccount()

            accountResult
                .onSuccess { acc ->
                    when {
                        acc == null -> goToRolePicker() // session ended
                        acc.role == "ADMIN" -> blockAdmin()
                        acc.role == "PATIENT" -> goAfterLogin(acc) // wrong home: send patients to theirs
                        else -> {
                            showAccount(acc)
                            showClinic(clinicJob.await().getOrNull())
                            showState(loading = false)
                        }
                    }
                }
                .onFailure {
                    findViewById<TextView>(R.id.tvError).text = it.friendly()
                    showState(loading = false, error = true)
                }
        }
    }

    private fun showAccount(acc: Account) {
        findViewById<TextView>(R.id.tvName).text = acc.fullName
        findViewById<TextView>(R.id.tvRoleBadge).apply {
            setText(if (acc.role == "DOCTOR") R.string.role_badge_doctor else R.string.role_badge_staff)
            visibility = View.VISIBLE
        }
    }

    private fun showClinic(settings: ClinicSettings?) {
        findViewById<TextView>(R.id.tvClinicName).apply {
            val name = settings?.clinicName?.takeIf { it.isNotBlank() }
            text = name
            visibility = if (name != null) View.VISIBLE else View.GONE
        }
    }

    /** Admins manage the clinic in the web panel; the app is for patients and clinic staff. */
    private fun blockAdmin() {
        toast(R.string.admin_use_web_panel)
        logOut()
    }

    private fun logOut() {
        lifecycleScope.launch {
            AuthRepository.signOut() // ends the session; the Remember me choice is kept
            goToRolePicker()
        }
    }

    private fun showState(loading: Boolean, error: Boolean = false) {
        stateLoading.visibility = if (loading) View.VISIBLE else View.GONE
        stateError.visibility = if (!loading && error) View.VISIBLE else View.GONE
        content.visibility = if (!loading && !error) View.VISIBLE else View.GONE
    }

    private fun greeting(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    }
}
