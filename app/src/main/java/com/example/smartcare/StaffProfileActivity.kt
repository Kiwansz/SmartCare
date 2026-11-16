package com.example.smartcare

import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.Account
import com.example.smartcare.data.AuthRepository
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Staff profile (navy): the signed-in STAFF or DOCTOR's own account. Change password
 * reuses ChangePasswordActivity; Log out ends the session and returns to the role picker.
 */
class StaffProfileActivity : BaseActivity() {

    private lateinit var stateLoading: View
    private lateinit var stateError: View
    private lateinit var content: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staff_profile)
        setupHeader(R.string.staff_profile_title, R.string.staff_profile_sub)

        stateLoading = findViewById(R.id.stateLoading)
        stateError = findViewById(R.id.stateError)
        content = findViewById(R.id.content)

        setupRow(R.id.rowRole, R.string.profile_role)
        setupRow(R.id.rowLastLogin, R.string.profile_last_login)
        setupRow(R.id.rowPassword, R.string.profile_password, R.string.action_change) {
            startActivity(ChangePasswordActivity.changeIntent(this))
        }
        rowValue(R.id.rowPassword, getString(R.string.profile_password_hidden))

        findViewById<MaterialButton>(R.id.btnRetry).setOnClickListener { load() }
        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener { logOut() }

        load()
    }

    private fun load() {
        showState(loading = true)
        lifecycleScope.launch {
            AuthRepository.loadCurrentAccount()
                .onSuccess { acc ->
                    when {
                        acc == null -> goToRolePicker() // session ended
                        acc.role != "STAFF" && acc.role != "DOCTOR" -> goAfterLogin(acc)
                        else -> {
                            show(acc)
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

    private fun show(acc: Account) {
        findViewById<TextView>(R.id.tvInitials).text = initials(acc.fullName)
        findViewById<TextView>(R.id.tvName).text = acc.fullName
        findViewById<TextView>(R.id.tvEmail).text = acc.email
        rowValue(R.id.rowRole, getString(if (acc.role == "DOCTOR") R.string.role_badge_doctor else R.string.role_badge_staff))
        rowValue(R.id.rowLastLogin, acc.lastLoginAt?.let { formatDateTime(it) }
            ?: getString(R.string.profile_last_login_none))
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

    private fun setupRow(id: Int, @StringRes label: Int, @StringRes action: Int? = null, onAction: (() -> Unit)? = null) {
        val row = findViewById<View>(id)
        row.findViewById<TextView>(R.id.rowLabel).setText(label)
        if (action != null && onAction != null) {
            row.findViewById<TextView>(R.id.rowAction).apply {
                setText(action)
                visibility = View.VISIBLE
                setOnClickListener { onAction() }
            }
        }
    }

    private fun rowValue(id: Int, value: String) {
        findViewById<View>(id).findViewById<TextView>(R.id.rowValue).text = value
    }

    /** Device's own date and time style, in the device's time zone. */
    private fun formatDateTime(date: Date): String =
        "${DateFormat.getMediumDateFormat(this).format(date)}, ${DateFormat.getTimeFormat(this).format(date)}"

    /** "Siti Aminah" -> "SA" */
    private fun initials(name: String) = name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
}
