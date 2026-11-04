package com.example.smartcare

import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AuthRepository
import com.example.smartcare.data.SessionPrefs
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/** 8. Staff home: placeholder until the staff design exists. Name, role and Log out. */
class StaffHomeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staff_home)

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            lifecycleScope.launch {
                AuthRepository.signOut()
                SessionPrefs.setRememberMe(this@StaffHomeActivity, false)
                goToRolePicker()
            }
        }

        lifecycleScope.launch {
            val acc = AuthRepository.currentAccount()
            if (acc == null) {
                goToRolePicker()
                return@launch
            }
            if (acc.role == "PATIENT") {
                goAfterLogin(acc) // wrong home: send patients to theirs
                return@launch
            }
            findViewById<TextView>(R.id.tvName).text = acc.fullName
            findViewById<TextView>(R.id.tvRole).text = getString(R.string.staff_home_sub, acc.role.lowercase())
        }
    }
}
