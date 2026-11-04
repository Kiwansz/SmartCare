package com.example.smartcare

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.Account
import com.example.smartcare.data.AuthRepository
import com.example.smartcare.data.SessionPrefs
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 7. Patient home: greeting with the real name, next-appointment card, 4 shortcut tiles
 * and a floating tab bar.
 * TODO(appointment module): the card and tile subtitles are sample data.
 */
class HomeActivity : BaseActivity() {

    private var account: Account? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        findViewById<TextView>(R.id.tvGreeting).setText(greeting())
        // Sample name until the real one loads (and if it cannot be loaded).
        showName(getString(R.string.home_sample_name))

        setupTile(R.id.tileBook, R.drawable.ic_sc_plus, R.string.tile_book, R.string.tile_book_sub)
        setupTile(R.id.tileChat, R.drawable.ic_sc_chat, R.string.tile_chat, R.string.tile_chat_sub, chat = true)
        setupTile(R.id.tileFollowups, R.drawable.ic_sc_loop, R.string.tile_followups, R.string.tile_followups_sub)
        setupTile(R.id.tileRecords, R.drawable.ic_sc_file, R.string.tile_records, R.string.tile_records_sub)

        // TODO(appointment module): open the real screens.
        listOf(R.id.btnBell, R.id.cardAppointment, R.id.dockVisits, R.id.dockRecords).forEach { id ->
            findViewById<View>(id).setOnClickListener { toast(R.string.coming_soon) }
        }
        findViewById<View>(R.id.dockProfile).setOnClickListener { showProfile() }

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
            if (acc.role != "PATIENT") {
                goAfterLogin(acc) // staff have their own home
                return@launch
            }
            account = acc
            showName(acc.fullName)
        }
    }

    private fun showName(name: String) {
        findViewById<TextView>(R.id.tvName).text = name
        findViewById<TextView>(R.id.tvInitials).text = initials(name)
    }

    private fun setupTile(
        id: Int,
        @DrawableRes icon: Int,
        @StringRes title: Int,
        @StringRes sub: Int,
        chat: Boolean = false
    ) {
        val tile = findViewById<View>(id)
        val iconView = tile.findViewById<ImageView>(R.id.tileIcon)
        iconView.setImageResource(icon)
        tile.findViewById<TextView>(R.id.tileTitle).setText(title)
        tile.findViewById<TextView>(R.id.tileSub).setText(sub)
        if (chat) {
            // Chatbot tile is navy in both themes.
            tile.setBackgroundResource(R.drawable.bg_tile_ai)
            iconView.setBackgroundResource(R.drawable.bg_tile_ai_icon)
            iconView.imageTintList = ContextCompat.getColorStateList(this, R.color.white)
            tile.findViewById<TextView>(R.id.tileTitle).setTextColor(getColor(R.color.white))
            tile.findViewById<TextView>(R.id.tileSub).setTextColor(getColor(R.color.hero_subtitle))
        }
        // TODO(appointment module / chatbot module): open the real screens.
        tile.setOnClickListener { toast(R.string.coming_soon) }
    }

    private fun showProfile() {
        val acc = account ?: return
        AlertDialog.Builder(this)
            .setTitle(acc.fullName)
            .setMessage(acc.email)
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

    @StringRes
    private fun greeting(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    }

    /** "Ahmad Hafiz" -> "AH" */
    private fun initials(name: String) = name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
}
