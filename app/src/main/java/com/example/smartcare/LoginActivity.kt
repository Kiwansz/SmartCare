package com.example.smartcare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.SupabaseProvider
import kotlinx.coroutines.launch
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class LoginActivity : AppCompatActivity() {

    private lateinit var tilIcNumber: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etIcNumber: TextInputEditText
    private lateinit var etPassword: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        tilIcNumber = findViewById(R.id.tilIcNumber)
        tilPassword = findViewById(R.id.tilPassword)
        etIcNumber = findViewById(R.id.etIcNumber)
        etPassword = findViewById(R.id.etPassword)

        // Temporary: verify the Supabase connection on launch (debug builds only).
        if (BuildConfig.DEBUG) testSupabaseConnection()

        findViewById<MaterialButton>(R.id.btnLogin).setOnClickListener { attemptLogin() }

        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            Toast.makeText(this, R.string.msg_forgot_password, Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.tvRegister).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun testSupabaseConnection() {
        lifecycleScope.launch {
            SupabaseProvider.testConnection()
                .onSuccess {
                    Log.d("SupabaseTest", it)
                    Toast.makeText(this@LoginActivity, it, Toast.LENGTH_LONG).show()
                }
                .onFailure {
                    Log.e("SupabaseTest", "Connection failed", it)
                    Toast.makeText(
                        this@LoginActivity,
                        "Supabase connection failed: ${it.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun attemptLogin() {
        val icNumber = etIcNumber.text?.toString()?.trim().orEmpty()
        val password = etPassword.text?.toString().orEmpty()

        tilIcNumber.error = null
        tilPassword.error = null

        var valid = true

        if (icNumber.isEmpty()) {
            tilIcNumber.error = getString(R.string.error_required)
            valid = false
        } else if (icNumber.length != 12 || !icNumber.all { it.isDigit() }) {
            tilIcNumber.error = getString(R.string.error_invalid_ic)
            valid = false
        }

        if (password.isEmpty()) {
            tilPassword.error = getString(R.string.error_required)
            valid = false
        }

        if (!valid) return

        // TODO: Replace with real authentication once the backend exists.
        Toast.makeText(this, R.string.msg_login_success, Toast.LENGTH_SHORT).show()
    }
}
