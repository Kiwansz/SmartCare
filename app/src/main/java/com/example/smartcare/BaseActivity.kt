package com.example.smartcare

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.smartcare.data.Account

/** Shared setup for every SmartCare screen: light system bars, insets, top bar, helpers. */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        super.onCreate(savedInstanceState)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        findViewById<View>(R.id.root)?.let(::applySystemBarPadding)
    }

    /** Keeps content clear of the status bar, navigation bar and keyboard. */
    private fun applySystemBarPadding(root: View) {
        val start = root.paddingLeft
        val top = root.paddingTop
        val end = root.paddingRight
        val bottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(start + bars.left, top + bars.top, end + bars.right, bottom + bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    /** Back button + title from view_top_bar.xml. */
    protected fun setupTopBar(@StringRes title: Int) {
        findViewById<TextView>(R.id.tvTopTitle).setText(title)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    /** Footer like "Don't have an account? **Sign Up**". */
    protected fun setupFooterLink(
        @StringRes prefix: Int,
        @StringRes boldPart: Int,
        onClick: () -> Unit
    ) {
        val bold = getString(boldPart)
        val text = SpannableStringBuilder(getString(prefix)).append(' ')
        val start = text.length
        text.append(bold)
        text.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        findViewById<TextView>(R.id.tvFooter).apply {
            setText(text)
            setOnClickListener { onClick() }
        }
    }

    /** Shows the spinner and disables [button] while a request runs. */
    protected fun setLoading(loading: Boolean, button: View) {
        button.isEnabled = !loading
        findViewById<View>(R.id.progress)?.visibility = if (loading) View.VISIBLE else View.GONE
    }

    protected fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    protected fun toast(@StringRes message: Int) = toast(getString(message))

    /** Turns Supabase / network errors into a short message for the user. */
    protected fun Throwable.friendly(): String {
        val first = message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        return when {
            first.contains("Invalid login credentials", true) -> "Incorrect username or password"
            first.contains("User already registered", true) -> "An account with this email already exists"
            first.contains("duplicate key", true) && first.contains("ic_number", true) ->
                "An account with this IC number already exists"
            first.contains("duplicate key", true) && first.contains("email", true) ->
                "An account with this email already exists"
            first.contains("email_address_invalid", true) || first.contains("Email address", true) && first.contains("invalid", true) ->
                "Please use a valid email address"
            first.contains("rate limit", true) -> "Too many attempts. Please try again later."
            first.contains("should be different", true) -> "New password must be different from the old one"
            this is java.io.IOException || first.contains("Unable to resolve host", true) ->
                "No internet connection"
            first.isNotEmpty() -> first
            else -> "Something went wrong. Please try again."
        }
    }
}

/** Where to go after a successful login. Clears the back stack so Back doesn't return to login. */
fun Activity.goAfterLogin(account: Account) {
    val target = if (account.mustChangePassword) ChangePasswordActivity::class.java else HomeActivity::class.java
    startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
}

/** Back to the role picker with a clean back stack (used on logout). */
fun Activity.goToRolePicker() {
    startActivity(Intent(this, RoleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
}
