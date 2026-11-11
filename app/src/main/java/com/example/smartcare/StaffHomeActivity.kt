package com.example.smartcare

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.Account
import com.example.smartcare.data.AuthRepository
import com.example.smartcare.data.ClinicSettings
import com.example.smartcare.staff.StaffFeature
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
        findViewById<View>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, StaffProfileActivity::class.java))
        }

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
        showTiles(StaffFeature.forRole(acc.role))
    }

    /** Two tiles per row, in registry order; each opens its screen or "Coming soon". */
    private fun showTiles(features: List<StaffFeature>) {
        val grid = findViewById<LinearLayout>(R.id.tilesGrid)
        grid.removeAllViews()
        findViewById<View>(R.id.tvTilesNone).visibility = if (features.isEmpty()) View.VISIBLE else View.GONE
        features.chunked(2).forEachIndexed { rowIndex, pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
            }
            for (i in 0 until 2) {
                val feature = pair.getOrNull(i)
                val tile = layoutInflater.inflate(R.layout.view_home_tile, row, false)
                tile.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (i == 0) marginEnd = dp(5) else marginStart = dp(5)
                }
                if (feature == null) {
                    tile.visibility = View.INVISIBLE // keeps the last odd tile half width
                } else {
                    tile.findViewById<ImageView>(R.id.tileIcon).setImageResource(feature.icon)
                    tile.findViewById<TextView>(R.id.tileTitle).setText(feature.title)
                    tile.findViewById<TextView>(R.id.tileSub).setText(feature.subtitle)
                    tile.setOnClickListener { ComingSoonActivity.open(this, feature) }
                }
                row.addView(tile)
            }
            grid.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { if (rowIndex > 0) topMargin = dp(10) })
        }
    }

    private fun showClinic(settings: ClinicSettings?) {
        findViewById<TextView>(R.id.tvClinicName).apply {
            val name = settings?.clinicName?.takeIf { it.isNotBlank() }
            text = name
            visibility = if (name != null) View.VISIBLE else View.GONE
        }
        showRules(settings)
    }

    /** Clinic rules card: the values the Admin saved, or a friendly empty state. */
    private fun showRules(s: ClinicSettings?) {
        val card = findViewById<View>(R.id.cardRules)
        val empty = findViewById<View>(R.id.tvRulesEmpty)
        val hasRules = s != null && listOf(s.gracePeriodMinutes, s.cancelCutoffHours, s.maxActiveAppointments)
            .any { it != null }
        card.visibility = if (hasRules) View.VISIBLE else View.GONE
        empty.visibility = if (hasRules) View.GONE else View.VISIBLE
        if (s == null || !hasRules) return

        val notSet = getString(R.string.rule_not_set)
        setRow(R.id.rowGrace, R.string.rule_grace_period,
            s.gracePeriodMinutes?.let { resources.getQuantityString(R.plurals.rule_minutes, it, it) } ?: notSet)
        setRow(R.id.rowCutoff, R.string.rule_cancel_cutoff,
            s.cancelCutoffHours?.let { resources.getQuantityString(R.plurals.rule_hours_before, it, it) } ?: notSet)
        setRow(R.id.rowMaxActive, R.string.rule_max_active,
            s.maxActiveAppointments?.let { resources.getQuantityString(R.plurals.rule_max_active_value, it, it) } ?: notSet)
        setRow(R.id.rowClinicPhone, R.string.rule_clinic_phone, s.phone)
        setRow(R.id.rowClinicAddress, R.string.rule_clinic_address, s.address)
    }

    /** Fills one label/value row; a row with no value is hidden. */
    private fun setRow(id: Int, @StringRes label: Int, value: String?) {
        val row = findViewById<View>(id)
        row.visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
        row.findViewById<TextView>(R.id.rowLabel).setText(label)
        row.findViewById<TextView>(R.id.rowValue).text = value
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
