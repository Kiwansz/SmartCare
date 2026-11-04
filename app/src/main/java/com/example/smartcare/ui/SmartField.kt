package com.example.smartcare.ui

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.text.InputFilter
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.util.AttributeSet
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.smartcare.R
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Input field from the design: label inside the box, icon on the left, soft glow when active,
 * and an inline error or helper line underneath.
 *
 * XML: app:sfLabel, app:sfPlaceholder, app:sfHelper, app:sfIcon, app:sfPassword,
 *      android:inputType, android:imeOptions, android:maxLength, android:digits.
 */
class SmartField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val box: FrameLayout
    val inputLayout: TextInputLayout
    val editText: TextInputEditText
    private val message: TextView
    private var helper: CharSequence? = null

    /** Current text, trimmed of nothing (callers trim if they need to). */
    var text: String
        get() = editText.text?.toString().orEmpty()
        set(value) = editText.setText(value)

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_smart_field, this, true)
        box = findViewById(R.id.sfBox)
        inputLayout = findViewById(R.id.sfLayout)
        editText = findViewById(R.id.sfEdit)
        message = findViewById(R.id.sfMessage)

        val a = context.obtainStyledAttributes(attrs, R.styleable.SmartField)
        try {
            inputLayout.hint = a.getString(R.styleable.SmartField_sfLabel)
            a.getString(R.styleable.SmartField_sfPlaceholder)?.let { inputLayout.placeholderText = it }
            a.getResourceId(R.styleable.SmartField_sfIcon, 0).takeIf { it != 0 }
                ?.let { inputLayout.setStartIconDrawable(it) }

            val password = a.getBoolean(R.styleable.SmartField_sfPassword, false)
            editText.inputType = if (password) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                a.getInt(R.styleable.SmartField_android_inputType, InputType.TYPE_CLASS_TEXT)
            }
            if (password) {
                inputLayout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
                inputLayout.setEndIconDrawable(R.drawable.ic_sc_password_toggle)
            }
            // Password input types switch the font to monospace; keep Figtree.
            editText.typeface = ResourcesCompat.getFont(context, R.font.figtree_semibold)

            editText.imeOptions = a.getInt(R.styleable.SmartField_android_imeOptions, EditorInfo.IME_ACTION_NEXT)
            a.getInt(R.styleable.SmartField_android_maxLength, -1).takeIf { it > 0 }
                ?.let { editText.filters = arrayOf(InputFilter.LengthFilter(it)) }
            a.getString(R.styleable.SmartField_android_digits)
                ?.let { editText.keyListener = DigitsKeyListener.getInstance(it) }

            helper = a.getString(R.styleable.SmartField_sfHelper)
        } finally {
            a.recycle()
        }
        showMessage(helper, isError = false)

        // Clear the error as soon as the user edits the field.
        editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (error != null) error = null
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
    }

    /** Inline error under the box (null clears it and shows the helper again). */
    var error: CharSequence? = null
        set(value) {
            field = value
            box.background = ContextCompat.getDrawable(
                context, if (value != null) R.drawable.bg_field_error else R.drawable.bg_field
            )
            showMessage(value ?: helper, isError = value != null)
        }

    /** Grey line under the box when there is no error. */
    var helperText: CharSequence?
        get() = helper
        set(value) {
            helper = value
            if (error == null) showMessage(value, isError = false)
        }

    private fun showMessage(text: CharSequence?, isError: Boolean) {
        message.text = text
        message.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        message.setTextColor(ContextCompat.getColor(context, if (isError) R.color.danger else R.color.ink_sub))
    }

    // ---- Keep the typed text across rotation. Every SmartField contains a child EditText
    // with the same id, so let this view save its own state instead of its children. ----

    override fun onSaveInstanceState(): Parcelable = Bundle().apply {
        putParcelable("super", super.onSaveInstanceState())
        putString("text", text)
        putCharSequence("error", error)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            @Suppress("DEPRECATION")
            super.onRestoreInstanceState(state.getParcelable("super"))
            text = state.getString("text").orEmpty()
            error = state.getCharSequence("error")
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>) =
        dispatchFreezeSelfOnly(container)

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>) =
        dispatchThawSelfOnly(container)
}
