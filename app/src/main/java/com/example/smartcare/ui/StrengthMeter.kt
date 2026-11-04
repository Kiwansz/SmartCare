package com.example.smartcare.ui

import android.view.View
import android.widget.TextView
import com.example.smartcare.R
import com.example.smartcare.util.PasswordCheck

/** Fills the 4 segments and the label of view_strength_meter.xml from a [PasswordCheck]. */
object StrengthMeter {
    private val SEGMENTS = intArrayOf(R.id.meter1, R.id.meter2, R.id.meter3, R.id.meter4)
    private val LABELS = intArrayOf(0, R.string.strength_weak, R.string.strength_fair, R.string.strength_good, R.string.strength_strong)

    fun bind(meter: View, check: PasswordCheck) {
        SEGMENTS.forEachIndexed { i, id ->
            meter.findViewById<View>(id).setBackgroundResource(
                if (i < check.strength) R.drawable.bg_meter_on else R.drawable.bg_meter_off
            )
        }
        meter.findViewById<TextView>(R.id.meterLabel).text =
            if (check.strength == 0) "" else meter.context.getString(LABELS[check.strength])
    }
}
