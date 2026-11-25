package com.example.smartcare

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AppointmentRepository
import com.example.smartcare.data.AvailableAppointmentSlot
import com.example.smartcare.data.PatientAppointment
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class MyAppointmentsActivity : BaseActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_appointments)


        loadAppointments()
    }

    private fun loadAppointments() {

        lifecycleScope.launch {
            AppointmentRepository.getMyAppointments()
                }
                .onFailure { error ->
                }
        }
    }

            }
        }


                }



                )

            }


                }
            }

                }

                }
            }
                }

                }

        }


        AlertDialog.Builder(this)
                button.isEnabled = false
                lifecycleScope.launch {
                        .onSuccess {
                                    loadAppointments()
                                }
                        .onFailure { error ->
                            button.isEnabled = true
                        }
                }
            }
            .show()
    }

        button.isEnabled = false

        lifecycleScope.launch {
            AppointmentRepository.getAvailableSlots()
                .onSuccess { slots ->
                    if (alternatives.isEmpty()) {
                        return@onSuccess
                    }
                    }
                    AlertDialog.Builder(this@MyAppointmentsActivity)
                        .setItems(labels.toTypedArray()) { _, position ->
                        }
                        .show()
                }
                .onFailure { error ->
                    button.isEnabled = true
        }
    }

        AlertDialog.Builder(this)
                button.isEnabled = false
                lifecycleScope.launch {
                    AppointmentRepository.rescheduleAppointment(
                        newSlotId = newSlot.slotId
                    )
                        .onSuccess { confirmation ->
                                .setCancelable(false)
                                .show()
                        }
                        .onFailure { error ->
                            button.isEnabled = true
                        }
                }
            }
            .show()
    }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .show()
    }

}
