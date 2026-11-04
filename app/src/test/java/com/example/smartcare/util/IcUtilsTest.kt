package com.example.smartcare.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IcUtilsTest {

    @Test fun birthDate_1900s() = assertEquals("1985-05-12", IcUtils.birthDate("850512041234"))

    @Test fun birthDate_2000s() = assertEquals("2003-01-31", IcUtils.birthDate("030131101235"))

    @Test fun birthDate_leapDay() = assertEquals("2000-02-29", IcUtils.birthDate("000229011234"))

    @Test fun birthDate_invalidDay() = assertNull(IcUtils.birthDate("010231011234"))

    @Test fun birthDate_invalidMonth() = assertNull(IcUtils.birthDate("011301011234"))

    @Test fun birthDate_wrongLength() = assertNull(IcUtils.birthDate("85051204"))

    @Test fun gender_oddIsMale() = assertEquals("MALE", IcUtils.gender("850512041235"))

    @Test fun gender_evenIsFemale() = assertEquals("FEMALE", IcUtils.gender("850512041234"))
}
