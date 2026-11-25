package com.example.smartcare

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.smartcare.data.AppointmentDoctor
import com.example.smartcare.data.AppointmentRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ManageSlotsActivity : BaseActivity() {

    private lateinit var statusText: TextView

    private var doctors: List<AppointmentDoctor> = emptyList()
    private var selectedDate: Calendar? = null
    private var startTime: Pair<Int, Int>? = null
    private var endTime: Pair<Int, Int>? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_slots)

        createButton = findViewById(R.id.btnCreateSlot)
        statusText = findViewById(R.id.tvSlotStatus)



        loadDoctors()
        loadSlots()
    }

    private fun loadDoctors() {
        createButton.isEnabled = false
        lifecycleScope.launch {
            AppointmentRepository.getActiveDoctors()
                .onSuccess { result ->
                    doctors = result
                        } else {
                        }
                    createButton.isEnabled = doctors.isNotEmpty()
                }
                .onFailure { error ->
                }
        }
    }

    private fun chooseDate() {
        val current = selectedDate ?: Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedDate = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            },
            current.get(Calendar.YEAR),
            current.get(Calendar.MONTH),
            current.get(Calendar.DAY_OF_MONTH)
    }

    private fun chooseTime(isStart: Boolean) {
        val previous = if (isStart) startTime else endTime

        TimePickerDialog(
            this,
                if (isStart) {
                } else {
                }
            },
        ).show()
    }

    private fun formatTime(time: Pair<Int, Int>): String {
    }

            set(Calendar.HOUR_OF_DAY, time.first)
            set(Calendar.MINUTE, time.second)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }


        val start = startTime
        val end = endTime

        }
        }

            return
        }


        createButton.isEnabled = false

        lifecycleScope.launch {
            AppointmentRepository.createSlot(
                doctorId = doctor.doctorId,
                capacity = capacity
            )
                .onFailure { error ->
                }

            createButton.isEnabled = doctors.isNotEmpty()
        }
    }

    private fun loadSlots() {
        lifecycleScope.launch {
            AppointmentRepository.getAppointmentSlots()

                    }



                            }
                        }

                    }
                }
                .onFailure { error ->
                }
        }
    }
}
