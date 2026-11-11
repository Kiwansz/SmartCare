package com.example.smartcare.staff

import com.example.smartcare.staff.StaffFeature.APPOINTMENTS
import com.example.smartcare.staff.StaffFeature.ATTENDANCE
import com.example.smartcare.staff.StaffFeature.FOLLOW_UPS
import com.example.smartcare.staff.StaffFeature.MEDICAL_RECORDS
import com.example.smartcare.staff.StaffFeature.PATIENTS
import com.example.smartcare.staff.StaffFeature.TODAYS_PATIENTS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffFeatureTest {

    @Test
    fun staffGetsTheirFourTilesInOrder() {
        assertEquals(listOf(APPOINTMENTS, ATTENDANCE, PATIENTS, FOLLOW_UPS), StaffFeature.forRole("STAFF"))
    }

    @Test
    fun doctorGetsTheirThreeTilesInOrder() {
        assertEquals(listOf(TODAYS_PATIENTS, FOLLOW_UPS, MEDICAL_RECORDS), StaffFeature.forRole("DOCTOR"))
    }

    @Test
    fun roleIsMatchedIgnoringCaseAndSpaces() {
        assertEquals(StaffFeature.forRole("DOCTOR"), StaffFeature.forRole(" doctor "))
        assertEquals(StaffFeature.forRole("STAFF"), StaffFeature.forRole("Staff"))
    }

    @Test
    fun adminPatientUnknownAndMissingRolesGetNoTiles() {
        listOf("ADMIN", "PATIENT", "NURSE", "", null).forEach {
            assertTrue("role '$it'", StaffFeature.forRole(it).isEmpty())
        }
    }

    @Test
    fun everyFeatureBelongsToAtLeastOneClinicRole() {
        StaffFeature.entries.forEach { f ->
            assertTrue(f.name, f.roles.isNotEmpty() && f.roles.all { it == "STAFF" || it == "DOCTOR" })
        }
    }
}
