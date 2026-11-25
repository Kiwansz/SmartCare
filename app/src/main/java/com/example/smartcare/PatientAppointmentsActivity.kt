package com.example.smartcare

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AppointmentRepository
import com.example.smartcare.data.AvailableAppointmentSlot
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class PatientAppointmentsActivity : BaseActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_appointments)


        }

        loadAvailableSlots()
    }

    private fun loadAvailableSlots() {

        lifecycleScope.launch {

                    }
                }
                .onFailure { error ->
                }
        }
    }

    private fun addSlotCard(slot: AvailableAppointmentSlot) {
        }
                }
        }


    }


        lifecycleScope.launch {
                .onSuccess { confirmation ->
                    AlertDialog.Builder(this@PatientAppointmentsActivity)
                        }
                        .setCancelable(false)
                        .show()
                }
                .onFailure { error ->
                }
        }
    }
}
