package com.example.smartcare.ui

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.smartcare.R
import com.google.android.material.button.MaterialButton

/**
 * Small builders for the cards on the list screens (Attendance, Reminders,
 * Follow-ups). They use the project's colours and fonts, and follow the theme: navy on staff
 * screens, Clinic Sky on patient screens.
 */
object CardKit {

    enum class Tone { GOOD, WARN, BAD, NEUTRAL }

    enum class ButtonStyle { FILLED, OUTLINED, DANGER }

    /** Colour of a status chip for the status words used by these screens. */
    fun toneFor(status: String?): Tone = when (status?.uppercase()) {
        "CHECKED_IN", "SENT", "COMPLETED" -> Tone.GOOD
        "PENDING", "PENDING_VERIFICATION" -> Tone.WARN
        "NO_SHOW", "FAILED", "UNSUCCESSFUL" -> Tone.BAD
        else -> Tone.NEUTRAL
    }

    private fun Context.px(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun themeColor(ctx: Context, attr: Int): Int {
        val tv = TypedValue()
        return if (ctx.theme.resolveAttribute(attr, tv, true)) tv.data
        else ContextCompat.getColor(ctx, R.color.navy_main)
    }

    private fun font(ctx: Context, res: Int) = ResourcesCompat.getFont(ctx, res)

    /** White rounded card, children stacked vertically. */
    fun card(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = ContextCompat.getDrawable(ctx, R.drawable.clinic_card)
        setPadding(ctx.px(16), ctx.px(14), ctx.px(16), ctx.px(14))
    }

    /** Bold card heading. */
    fun title(ctx: Context, text: String): TextView = TextView(ctx).apply {
        this.text = text
        textSize = 16f
        typeface = font(ctx, R.font.figtree_bold)
        setTextColor(ContextCompat.getColor(ctx, R.color.ink))
    }

    /** Adds "Label: value" under the heading; nothing is added when the value is empty. */
    fun addLine(card: LinearLayout, label: String, value: String?, danger: Boolean = false) {
        if (value.isNullOrBlank()) return
        val ctx = card.context
        card.addView(TextView(ctx).apply {
            text = "$label: $value"
            textSize = 13.5f
            typeface = font(ctx, R.font.figtree_medium)
            setTextColor(ContextCompat.getColor(ctx, if (danger) R.color.danger else R.color.ink_sub))
            setPadding(0, ctx.px(3), 0, 0)
        })
    }

    /** Small rounded status label. */
    fun chip(ctx: Context, text: String, tone: Tone): TextView = TextView(ctx).apply {
        this.text = text
        textSize = 11.5f
        typeface = font(ctx, R.font.figtree_semibold)
        setPadding(ctx.px(10), ctx.px(4), ctx.px(10), ctx.px(4))
        val (bg, fg) = when (tone) {
            Tone.GOOD -> R.drawable.clinic_chip_good to ContextCompat.getColor(ctx, R.color.clinic_good_text)
            Tone.WARN -> R.drawable.clinic_chip_warn to ContextCompat.getColor(ctx, R.color.clinic_warn_text)
            Tone.BAD -> R.drawable.clinic_chip_bad to ContextCompat.getColor(ctx, R.color.clinic_bad_text)
            Tone.NEUTRAL -> R.drawable.bg_chip_ok to themeColor(ctx, R.attr.scMain)
        }
        background = ContextCompat.getDrawable(ctx, bg)
        setTextColor(fg)
    }

    /** Chips side by side, with a little space above. */
    fun chipRow(ctx: Context, vararg chips: TextView): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = ctx.px(8); bottomMargin = ctx.px(4) }
        chips.forEachIndexed { i, chip ->
            addView(chip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { if (i > 0) marginStart = ctx.px(6) })
        }
    }

    /** Filter chip for the row above a list; the selected one is filled with the theme colour. */
    fun filterChip(ctx: Context, text: String, selected: Boolean, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = 13f
            typeface = font(ctx, R.font.figtree_semibold)
            setPadding(ctx.px(16), ctx.px(8), ctx.px(16), ctx.px(8))
            background = ContextCompat.getDrawable(
                ctx, if (selected) R.drawable.clinic_filter_on else R.drawable.bg_chip_off
            )
            setTextColor(ContextCompat.getColor(ctx, if (selected) R.color.white else R.color.ink_sub))
            setOnClickListener { onClick() }
        }

    /** Compact button for a card. */
    fun button(
        ctx: Context,
        text: String,
        style: ButtonStyle = ButtonStyle.FILLED,
        onClick: () -> Unit
    ): MaterialButton {
        val button = if (style == ButtonStyle.FILLED) {
            MaterialButton(ctx)
        } else {
            MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        }
        return button.apply {
            this.text = text
            textSize = 13f
            minimumHeight = ctx.px(40)
            insetTop = 0
            insetBottom = 0
            if (style == ButtonStyle.DANGER) {
                val danger = ContextCompat.getColor(ctx, R.color.danger)
                setTextColor(danger)
                strokeColor = ColorStateList.valueOf(danger)
            }
            setOnClickListener { onClick() }
        }
    }

    /** Buttons side by side, sharing the width equally. */
    fun buttonRow(ctx: Context, buttons: List<View>): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = ctx.px(12) }
        buttons.forEachIndexed { i, button ->
            addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { if (i > 0) marginStart = ctx.px(8) })
        }
    }
}
