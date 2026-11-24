package com.example.smartcare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MedicalAppointmentTest {
    private val identity = MedicalAppointmentSamples.doctorFor(29)

    @Test
    fun completedAssignedAppointmentAllowsEachEntry() {
        assertNotEquals(identity.userId, identity.doctorId)
        val appointment = MedicalAppointmentSamples.find(7001)
        for (entry in MedicalEntryType.entries) {
            assertNull(appointmentBlock(appointment, identity, entry))
        }
    }

    @Test
    fun confirmedAppointmentIsIneligible() {
        assertEquals(
            MedicalAppointmentBlock.INELIGIBLE,
            appointmentBlock(MedicalAppointmentSamples.find(7002), identity),
        )
    }

    @Test
    fun completedAppointmentForAnotherDoctorIsDenied() {
        assertEquals(
            MedicalAppointmentBlock.WRONG_DOCTOR,
            appointmentBlock(MedicalAppointmentSamples.find(7003), identity),
        )
    }

    @Test
    fun missingAndUnknownIdsAreDenied() {
        for (id in listOf(0, -1, 9012)) {
            assertEquals(
                MedicalAppointmentBlock.MISSING,
                appointmentBlock(MedicalAppointmentSamples.find(id), identity),
            )
        }
    }

    @Test
    fun duplicatesAreBlockedIndependently() {
        val appointment = MedicalAppointmentSamples.find(7004)
        assertEquals(MedicalAppointmentBlock.DUPLICATE, appointmentBlock(appointment, identity, MedicalEntryType.HISTORY))
        assertEquals(MedicalAppointmentBlock.DUPLICATE, appointmentBlock(appointment, identity, MedicalEntryType.PRESCRIPTION))
        assertNull(appointmentBlock(appointment, identity, MedicalEntryType.CERTIFICATE))
        assertEquals(
            MedicalAppointmentBlock.DUPLICATE,
            appointmentBlock(MedicalAppointmentSamples.find(7005), identity, MedicalEntryType.CERTIFICATE),
        )
    }
}
