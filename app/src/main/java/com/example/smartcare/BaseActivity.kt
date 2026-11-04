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
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.smartcare.data.Account

/** Shared setup for every SmartCare screen: light system bars, insets, top bar, helpers. */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Transparent status bar with dark icons; the page background shows through.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        super.onCreate(savedInstanceState)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        val root = findViewById<View>(R.id.root) ?: return
        val header = findViewById<View>(R.id.header)
        applySystemBarPadding(root, header)
        if (header != null) {
            // Gradient header draws behind a transparent status bar, with white status bar icons.
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
            )
        }
    }

    /**
     * Keeps content clear of the status bar, navigation bar and keyboard.
     * With a gradient [header], the header takes the status bar inset (it draws behind it)
     * and the root takes the rest; otherwise the root takes all of it.
     */
    private fun applySystemBarPadding(root: View, header: View?) {
        val start = root.paddingLeft
        val top = root.paddingTop
        val end = root.paddingRight
        val bottom = root.paddingBottom
        val headerTop = header?.paddingTop ?: 0
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            if (header != null) {
                header.setPadding(header.paddingLeft, headerTop + bars.top, header.paddingRight, header.paddingBottom)
                v.setPadding(start + bars.left, top, end + bars.right, bottom + bars.bottom)
            } else {
                v.setPadding(start + bars.left, top + bars.top, end + bars.right, bottom + bars.bottom)
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    /**
     * Configures view_auth_header.xml. [pill] is the glass label on the right
     * (e.g. "Step 1 of 2"); [step] (1 or 2) shows the two-segment progress bar.
     */
    protected fun setupHeader(
        @StringRes title: Int,
        @StringRes subtitle: Int? = null,
        showBack: Boolean = true,
        showBrand: Boolean = false,
        brandCentered: Boolean = true,
        pill: CharSequence? = null,
        @DrawableRes pillIcon: Int = 0,
        step: Int? = null
    ) {
        findViewById<TextView>(R.id.headerTitle).setText(title)
        findViewById<TextView>(R.id.headerSubtitle).apply {
            visibility = if (subtitle != null) View.VISIBLE else View.GONE
            subtitle?.let { setText(it) }
        }
        findViewById<ImageButton>(R.id.headerBack).apply {
            visibility = if (showBack) View.VISIBLE else View.GONE
            setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
        findViewById<View>(R.id.headerBrand).apply {
            visibility = if (showBrand) View.VISIBLE else View.GONE
            (layoutParams as android.widget.FrameLayout.LayoutParams).apply {
                gravity = if (brandCentered) android.view.Gravity.CENTER
                    else android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                marginStart = if (!brandCentered && showBack) dp(52) else 0
            }
        }
        findViewById<TextView>(R.id.headerPill).apply {
            visibility = if (pill != null) View.VISIBLE else View.GONE
            text = pill
            setCompoundDrawablesRelativeWithIntrinsicBounds(pillIcon, 0, 0, 0)
        }
        findViewById<View>(R.id.headerProgress).visibility = if (step != null) View.VISIBLE else View.GONE
        if (step != null) {
            findViewById<View>(R.id.headerStep2).setBackgroundResource(
                if (step >= 2) R.drawable.bg_progress_on else R.drawable.bg_progress_off
            )
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
        themeColor(R.attr.scMain)?.let {
            text.setSpan(android.text.style.ForegroundColorSpan(it), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        findViewById<TextView>(R.id.tvFooter).apply {
            setText(text)
            setOnClickListener { onClick() }
        }
    }

    /** Shows the spinner and disables [button] while a request runs. */
    protected fun setLoading(loading: Boolean, button: View) {
        button.isEnabled = !loading
        button.alpha = if (loading) 0.6f else 1f
        findViewById<View>(R.id.progress)?.visibility = if (loading) View.VISIBLE else View.GONE
    }

    protected fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** Resolves a colour theme attribute such as R.attr.scMain. */
    protected fun themeColor(attr: Int): Int? {
        val tv = android.util.TypedValue()
        return if (theme.resolveAttribute(attr, tv, true)) tv.data else null
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
            // Only our own messages (from error("...") in AuthRepository) are shown as-is;
            // raw library/server text never reaches the user.
            this is IllegalStateException && first.isNotEmpty() -> first
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
