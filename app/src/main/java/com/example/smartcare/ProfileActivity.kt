package com.example.smartcare

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AuthRepository
import com.example.smartcare.data.PatientProfile
import com.example.smartcare.ui.SmartField
import com.example.smartcare.util.maskIc
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Patient profile (opened from the Profile icon in the home tab bar).
 * Shows name, email, masked IC, date of birth, gender and phone. Phone is saved through
 * the update_my_phone database function; gender on the user's own patient row.
 */
class ProfileActivity : BaseActivity() {

    private companion object {
        val DOB_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    }

    private var profile: PatientProfile? = null
    private lateinit var progress: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        setupHeader(R.string.profile_title)
        progress = findViewById(R.id.progress)

        setupRow(R.id.rowIc, R.string.profile_ic)
        setupRow(R.id.rowDob, R.string.profile_dob)
        setupRow(R.id.rowGender, R.string.profile_gender, R.string.action_change) { pickGender() }
        setupRow(R.id.rowPhone, R.string.profile_phone, R.string.action_edit) { editPhone() }

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            lifecycleScope.launch {
                AuthRepository.signOut() // ends the session; the Remember me choice is kept
                goToRolePicker()
            }
        }

        load()
    }

    private fun load() {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            AuthRepository.loadMyProfile()
                .onSuccess { show(it) }
                .onFailure {
                    toast(it.friendly())
                    if (AuthRepository.currentAccount() == null) goToRolePicker()
                }
            progress.visibility = View.GONE
        }
    }

    private fun show(p: PatientProfile) {
        profile = p
        findViewById<TextView>(R.id.tvName).text = p.fullName
        findViewById<TextView>(R.id.tvInitials).text = initials(p.fullName)
        findViewById<TextView>(R.id.tvEmail).text = p.email
        rowValue(R.id.rowIc, maskIc(p.icNumber))
        rowValue(R.id.rowDob, p.dateOfBirth?.let { formatDob(it) })
        rowValue(R.id.rowGender, genderLabel(p.gender))
        rowValue(R.id.rowPhone, p.phone?.takeIf { it.isNotBlank() })
    }

    // ---------- Phone ----------

    private fun editPhone() {
        val p = profile ?: return
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_phone, null)
        val field = view.findViewById<SmartField>(R.id.fieldPhone)
        field.text = p.phone.orEmpty()

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_phone_title)
            .setView(view)
            .setPositiveButton(R.string.action_save, null) // set below so it can stay open on error
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val phone = field.text.trim()
                field.error = when {
                    phone.isEmpty() -> getString(R.string.error_required)
                    !Patterns.PHONE.matcher(phone).matches() || phone.count { it.isDigit() } < 9 ->
                        getString(R.string.error_invalid_phone)
                    else -> null
                }
                if (field.error != null) return@setOnClickListener
                dialog.dismiss()
                savePhone(phone)
            }
        }
        dialog.show()
    }

    private fun savePhone(phone: String) {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            AuthRepository.updateMyPhone(phone)
                .onSuccess {
                    toast(R.string.msg_phone_saved)
                    profile = profile?.copy(phone = phone)
                    profile?.let { show(it) }
                }
                .onFailure { toast(it.friendly()) }
            progress.visibility = View.GONE
        }
    }

    // ---------- Gender ----------

    private fun pickGender() {
        val p = profile ?: return
        val values = arrayOf("MALE", "FEMALE", null)
        val labels = arrayOf(getString(R.string.gender_male), getString(R.string.gender_female), getString(R.string.not_set))
        AlertDialog.Builder(this)
            .setTitle(R.string.profile_gender)
            .setSingleChoiceItems(labels, values.indexOf(p.gender)) { dialog, which ->
                dialog.dismiss()
                if (values[which] != p.gender) saveGender(p.userId, values[which])
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun saveGender(userId: Int, gender: String?) {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            AuthRepository.updateMyGender(userId, gender)
                .onSuccess {
                    toast(R.string.msg_gender_saved)
                    profile = profile?.copy(gender = gender)
                    profile?.let { show(it) }
                }
                .onFailure { toast(it.friendly()) }
            progress.visibility = View.GONE
        }
    }

    // ---------- helpers ----------

    private fun setupRow(id: Int, @StringRes label: Int, @StringRes action: Int? = null, onAction: (() -> Unit)? = null) {
        val row = findViewById<View>(id)
        row.findViewById<TextView>(R.id.rowLabel).setText(label)
        row.findViewById<TextView>(R.id.rowAction).apply {
            if (action != null && onAction != null) {
                setText(action)
                visibility = View.VISIBLE
                setOnClickListener { onAction() }
            }
        }
    }

    /** null shows "Not set" in the placeholder colour. */
    private fun rowValue(id: Int, value: String?) {
        findViewById<View>(id).findViewById<TextView>(R.id.rowValue).apply {
            text = value ?: getString(R.string.not_set)
            setTextColor(getColor(if (value == null) R.color.ink_placeholder else R.color.ink))
        }
    }

    private fun genderLabel(g: String?): String? = when (g) {
        "MALE" -> getString(R.string.gender_male)
        "FEMALE" -> getString(R.string.gender_female)
        else -> null
    }

    private fun formatDob(iso: String): String =
        runCatching { LocalDate.parse(iso.take(10)).format(DOB_FORMAT) }.getOrDefault(iso)

    /** "Ahmad Hafiz" -> "AH" */
    private fun initials(name: String) = name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
}
