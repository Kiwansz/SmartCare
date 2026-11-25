package com.example.smartcare

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.userMessage
import com.example.smartcare.ui.CardKit
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Shared base for the list screens (Attendance, Reminders, Follow-ups).
 * It owns the header, the filter chips, up to two buttons, a status line and the card list,
 * so each screen only decides what the cards say. Layout: activity_clinic_list.xml.
 */
abstract class ClinicListActivity : BaseActivity() {

    protected abstract val screenTitle: Int
    protected abstract val screenSubtitle: Int

    private lateinit var btnPrimary: MaterialButton
    private lateinit var btnSecondary: MaterialButton
    private lateinit var filterScroll: View
    private lateinit var filterRow: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var progress: View
    private lateinit var container: LinearLayout

    private var filterLabels: List<String> = emptyList()
    private var onFilterChanged: () -> Unit = {}

    /** Index of the selected filter chip (0 when the screen has no filters). */
    protected var filterIndex = 0
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clinic_list)
        setupHeader(screenTitle, screenSubtitle)

        btnPrimary = findViewById(R.id.btnPrimary)
        btnSecondary = findViewById(R.id.btnSecondary)
        filterScroll = findViewById(R.id.filterScroll)
        filterRow = findViewById(R.id.filterRow)
        tvStatus = findViewById(R.id.tvStatus)
        progress = findViewById(R.id.progress)
        container = findViewById(R.id.listContainer)
    }

    // ---------- chrome ----------

    /** Shows the filter chips; [onChange] runs after the user picks another one. */
    protected fun setFilters(labels: List<String>, onChange: () -> Unit) {
        filterLabels = labels
        onFilterChanged = onChange
        filterScroll.visibility = View.VISIBLE
        drawFilters()
    }

    private fun drawFilters() {
        filterRow.removeAllViews()
        filterLabels.forEachIndexed { index, label ->
            val chip = CardKit.filterChip(this, label, index == filterIndex) {
                if (index != filterIndex) {
                    filterIndex = index
                    drawFilters()
                    onFilterChanged()
                }
            }
            filterRow.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { if (index > 0) marginStart = dp(8) })
        }
    }

    protected fun setPrimaryButton(text: String, onClick: () -> Unit) {
        btnPrimary.text = text
        btnPrimary.visibility = View.VISIBLE
        btnPrimary.setOnClickListener { onClick() }
    }

    protected fun setSecondaryButton(text: String, onClick: () -> Unit) {
        btnSecondary.text = text
        btnSecondary.visibility = View.VISIBLE
        btnSecondary.setOnClickListener { onClick() }
    }

    // ---------- list ----------

    protected fun showMessage(text: String?) {
        tvStatus.text = text
        tvStatus.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    protected fun showLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        btnPrimary.isEnabled = !loading
        btnSecondary.isEnabled = !loading
    }

    protected fun clearCards() = container.removeAllViews()

    protected fun addCard(card: View) {
        container.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })
    }

    /** A failed load: clears the list and shows a friendly message. */
    protected fun showLoadError(error: Throwable) {
        // The screen only shows a friendly message; the real reason goes to Logcat (tag: SmartCareLoad).
        android.util.Log.e("SmartCareLoad", "Load failed in ${javaClass.simpleName}", error)
        showLoading(false)
        clearCards()
        showMessage(error.userMessage(getString(R.string.clinic_error_generic)))
    }

    // ---------- actions ----------

    /** Asks "are you sure?" before a change that is awkward to undo. */
    protected fun confirm(title: String, message: String, positive: String, onYes: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(R.string.clinic_cancel, null)
            .setPositiveButton(positive) { _, _ -> onYes() }
            .show()
    }

    /**
     * Runs one database call, shows a spinner meanwhile, then [onSuccess] with its value.
     * A failure shows a short toast (never raw Supabase text).
     */
    protected fun <T> runAction(work: suspend () -> Result<T>, onSuccess: (T) -> Unit) {
        showLoading(true)
        lifecycleScope.launch {
            val result = work()
            showLoading(false)
            result
                .onSuccess { onSuccess(it) }
                .onFailure { toast(it.userMessage(getString(R.string.clinic_error_generic))) }
        }
    }
}