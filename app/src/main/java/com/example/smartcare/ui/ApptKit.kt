package com.example.smartcare.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.smartcare.R
import com.example.smartcare.util.DateText
import java.util.Locale

/**
 * Builders for the appointment and slot screens (staff Appointments, Manage Slots,
 * patient Book Appointment, My Appointments). Cards share one shape: a date block on the
 * left, title / subtitle / time on the right, an optional status chip, then detail rows.
 * Colours follow the theme (navy for staff, Clinic Sky for patients).
 */
object ApptKit {

    /** Booking statuses that still count as an upcoming, changeable appointment. */
    val ACTIVE_BOOKING = setOf("BOOKED", "CONFIRMED")

    fun bookingTone(status: String?): CardKit.Tone = when (status?.uppercase(Locale.US)) {
        "BOOKED", "CONFIRMED" -> CardKit.Tone.NEUTRAL
        "COMPLETED", "CHECKED_IN", "ATTENDED" -> CardKit.Tone.GOOD
        "RESCHEDULED", "PENDING", "PENDING_VERIFICATION" -> CardKit.Tone.WARN
        "CANCELLED", "CANCELED", "NO_SHOW", "REJECTED" -> CardKit.Tone.BAD
        else -> CardKit.Tone.NEUTRAL
    }

    fun slotTone(status: String?): CardKit.Tone = when (status?.uppercase(Locale.US)) {
        "OPEN", "AVAILABLE" -> CardKit.Tone.GOOD
        "FULL", "BOOKED" -> CardKit.Tone.WARN
        "CLOSED", "CANCELLED", "CANCELED" -> CardKit.Tone.BAD
        else -> CardKit.Tone.NEUTRAL
    }

    fun statusChip(ctx: Context, status: String, tone: CardKit.Tone): TextView =
        CardKit.chip(ctx, DateText.pretty(status), tone)

    // ---------- card ----------

    /**
     * White card with the date block and heading. Returns the card; add detail rows,
     * a code box or buttons to it afterwards.
     */
    fun card(
        ctx: Context,
        slotStart: String,
        slotEnd: String,
        title: String,
        subtitle: String?,
        chip: TextView? = null
    ): LinearLayout {
        val card = CardKit.card(ctx)
        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }

        top.addView(dateBadge(ctx, slotStart), LinearLayout.LayoutParams(ctx.px(56), ctx.px(62)))

        val text = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        text.addView(TextView(ctx).apply {
            this.text = title
            textSize = 15.5f
            typeface = font(ctx, R.font.figtree_bold)
            setTextColor(color(ctx, R.color.ink))
        })
        if (!subtitle.isNullOrBlank()) {
            text.addView(TextView(ctx).apply {
                this.text = subtitle
                textSize = 12.5f
                typeface = font(ctx, R.font.figtree_medium)
                setTextColor(color(ctx, R.color.ink_sub))
                setPadding(0, ctx.px(1), 0, 0)
            })
        }
        text.addView(iconText(ctx, R.drawable.ic_sc_clock, DateText.timeRange(slotStart, slotEnd), main = true)
            .apply { setPadding(0, ctx.px(6), 0, 0) })

        top.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { marginStart = ctx.px(12) })

        if (chip != null) {
            top.addView(chip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = ctx.px(8) })
        }

        card.addView(top)
        return card
    }

    private fun dateBadge(ctx: Context, slotStart: String): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = ContextCompat.getDrawable(ctx, R.drawable.appt_date_badge)
            addView(TextView(ctx).apply {
                text = DateText.monthShort(slotStart)
                textSize = 10.5f
                letterSpacing = 0.08f
                typeface = font(ctx, R.font.figtree_bold)
                setTextColor(themeColor(ctx, R.attr.scMain))
                gravity = Gravity.CENTER
            })
            addView(TextView(ctx).apply {
                text = DateText.dayNumber(slotStart)
                textSize = 21f
                typeface = font(ctx, R.font.figtree_bold)
                setTextColor(color(ctx, R.color.ink))
                gravity = Gravity.CENTER
                includeFontPadding = false
            })
        }

    /** Thin line between the card heading and its details. */
    fun divider(card: LinearLayout) {
        val ctx = card.context
        card.addView(View(ctx).apply { setBackgroundColor(color(ctx, R.color.line)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.px(1)).apply {
                topMargin = ctx.px(12); bottomMargin = ctx.px(8)
            })
    }

    /** "icon  Label  value" row; nothing is added when the value is empty. */
    fun detail(card: LinearLayout, @DrawableRes icon: Int, label: String, value: String?) {
        if (value.isNullOrBlank()) return
        val ctx = card.context
        val text = SpannableStringBuilder(label).append("  ")
        val start = text.length
        text.append(value)
        text.setSpan(ForegroundColorSpan(color(ctx, R.color.ink)), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        card.addView(iconText(ctx, icon, text, main = false).apply { setPadding(0, ctx.px(4), 0, 0) })
    }

    /** Short line with an icon under the card heading; amber when [warn] is set. */
    fun note(card: LinearLayout, @DrawableRes icon: Int, text: String, warn: Boolean = false) {
        val ctx = card.context
        val tint = if (warn) color(ctx, R.color.clinic_warn_text) else color(ctx, R.color.ink_sub)
        card.addView(TextView(ctx).apply {
            this.text = text
            textSize = 12.5f
            typeface = font(ctx, R.font.figtree_semibold)
            setTextColor(tint)
            gravity = Gravity.CENTER_VERTICAL
            compoundDrawablePadding = ctx.px(6)
            ContextCompat.getDrawable(ctx, icon)?.mutate()?.let { d ->
                d.setBounds(0, 0, ctx.px(15), ctx.px(15))
                d.setTintList(ColorStateList.valueOf(tint))
                setCompoundDrawablesRelative(d, null, null, null)
            }
            setPadding(0, ctx.px(10), 0, 0)
        })
    }

    private fun iconText(ctx: Context, @DrawableRes icon: Int, text: CharSequence, main: Boolean): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = 13f
            typeface = font(ctx, if (main) R.font.figtree_semibold else R.font.figtree_medium)
            val tint = if (main) themeColor(ctx, R.attr.scMain) else color(ctx, R.color.ink_sub)
            setTextColor(if (main) tint else color(ctx, R.color.ink_sub))
            gravity = Gravity.CENTER_VERTICAL
            compoundDrawablePadding = ctx.px(6)
            ContextCompat.getDrawable(ctx, icon)?.mutate()?.let { d ->
                d.setBounds(0, 0, ctx.px(15), ctx.px(15))
                d.setTintList(ColorStateList.valueOf(tint))
                setCompoundDrawablesRelative(d, null, null, null)
            }
        }

    /** Dashed box with the patient's check-in code, large and easy to read out. */
    fun codeBox(card: LinearLayout, label: String, code: String) {
        val ctx = card.context
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(ctx, R.drawable.appt_code_box)
            setPadding(ctx.px(14), ctx.px(10), ctx.px(14), ctx.px(10))
        }
        val text = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        text.addView(TextView(ctx).apply {
            this.text = label.uppercase(Locale.getDefault())
            textSize = 10.5f
            letterSpacing = 0.08f
            typeface = font(ctx, R.font.figtree_bold)
            setTextColor(themeColor(ctx, R.attr.scMain))
        })
        text.addView(TextView(ctx).apply {
            this.text = code
            textSize = 20f
            letterSpacing = 0.12f
            typeface = font(ctx, R.font.figtree_bold)
            setTextColor(color(ctx, R.color.ink))
            setTextIsSelectable(true)
        })
        box.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_sc_id)
            imageTintList = ColorStateList.valueOf(themeColor(ctx, R.attr.scMain))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(ctx.px(26), ctx.px(26)))
        card.addView(box, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = ctx.px(12) })
    }

    // ---------- date strip ----------

    /** One day in the date strip: weekday over day number. Greyed out when it has nothing to pick. */
    fun dayChip(
        ctx: Context,
        weekday: String,
        day: String,
        selected: Boolean,
        enabled: Boolean,
        onClick: () -> Unit
    ): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = ContextCompat.getDrawable(ctx,
            if (selected) R.drawable.clinic_filter_on else R.drawable.bg_chip_off)
        minimumWidth = ctx.px(54)
        setPadding(ctx.px(10), 0, ctx.px(10), 0)
        alpha = if (enabled || selected) 1f else 0.4f
        isEnabled = enabled
        isClickable = enabled
        isFocusable = enabled
        contentDescription = "$weekday $day"
        val main = if (selected) color(ctx, R.color.white) else color(ctx, R.color.ink)
        addView(TextView(ctx).apply {
            text = weekday.uppercase(Locale.getDefault())
            textSize = 10.5f
            letterSpacing = 0.06f
            typeface = font(ctx, R.font.figtree_semibold)
            setTextColor(if (selected) main else color(ctx, R.color.ink_sub))
            gravity = Gravity.CENTER
        })
        addView(TextView(ctx).apply {
            text = day
            textSize = 18f
            typeface = font(ctx, R.font.figtree_bold)
            setTextColor(main)
            gravity = Gravity.CENTER
        })
        setOnClickListener { onClick() }
    }

    // ---------- list chrome ----------

    /** Small uppercase heading between groups of cards, e.g. "TODAY · 3". */
    fun sectionHeader(ctx: Context, text: String, first: Boolean): TextView = TextView(ctx).apply {
        this.text = text.uppercase(Locale.getDefault())
        textSize = 11.5f
        letterSpacing = 0.08f
        typeface = font(ctx, R.font.figtree_bold)
        setTextColor(color(ctx, R.color.ink_sub))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = if (first) 0 else ctx.px(10); bottomMargin = ctx.px(10) }
    }

    /** Friendly empty / error state with an icon in a soft circle. */
    fun emptyState(ctx: Context, @DrawableRes icon: Int, title: String, message: String?): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(ctx.px(16), ctx.px(28), ctx.px(16), ctx.px(28))
            addView(ImageView(ctx).apply {
                setImageResource(icon)
                background = ContextCompat.getDrawable(ctx, R.drawable.appt_date_badge)
                setPadding(ctx.px(14), ctx.px(14), ctx.px(14), ctx.px(14))
                imageTintList = ColorStateList.valueOf(themeColor(ctx, R.attr.scMain))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(ctx.px(56), ctx.px(56)))
            addView(TextView(ctx).apply {
                text = title
                textSize = 15f
                gravity = Gravity.CENTER
                typeface = font(ctx, R.font.figtree_bold)
                setTextColor(color(ctx, R.color.ink))
                setPadding(0, ctx.px(12), 0, 0)
            })
            if (!message.isNullOrBlank()) {
                addView(TextView(ctx).apply {
                    text = message
                    textSize = 13f
                    gravity = Gravity.CENTER
                    typeface = font(ctx, R.font.figtree_medium)
                    setTextColor(color(ctx, R.color.ink_sub))
                    setPadding(0, ctx.px(4), 0, 0)
                })
            }
        }

    // ---------- errors ----------

    /**
     * Message for a failed appointment call. Keeps the database's own reason (e.g. a booking
     * rule) but cuts technical details and never shows URLs or keys. Only the cleaned message
     * and the error class go to logcat (tag "SmartCareAppt"): the raw Supabase error contains
     * the login token and API key, so it is never logged.
     */
    fun errorMessage(error: Throwable, fallback: String): String {
        if (error is java.io.IOException) return "No internet connection. Please check it and try again."
        val raw = error.localizedMessage?.takeIf { it.isNotBlank() } ?: return fallback
        val marker = Regex("(?i)\\b(Code|Hint|Details|URL|Headers):").find(raw)
        val message = (if (marker != null) raw.substring(0, marker.range.first) else raw)
            .trim().removePrefix("[SC]").trim()
        android.util.Log.w("SmartCareAppt", "Appointment call failed (${error.javaClass.simpleName}): $message")
        if (message.isBlank()) return fallback
        // A function from supabase/appointment_extras.sql has not been installed yet.
        if (message.contains("Could not find the function", ignoreCase = true)) {
            return "This feature isn't set up in the database yet. Run supabase/appointment_extras.sql first."
        }
        if (Regex("(?i)authorization|bearer\\s|https?://|supabase\\.co|postgrest|apikey").containsMatchIn(message)) {
            return fallback
        }
        return message
    }

    // ---------- helpers ----------

    private fun Context.px(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun font(ctx: Context, res: Int) = ResourcesCompat.getFont(ctx, res)

    private fun color(ctx: Context, res: Int) = ContextCompat.getColor(ctx, res)

    fun themeColor(ctx: Context, attr: Int): Int {
        val tv = TypedValue()
        return if (ctx.theme.resolveAttribute(attr, tv, true)) tv.data
        else ContextCompat.getColor(ctx, R.color.navy_main)
    }
}
