package com.example.smartcare.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class IcParserTest {

    private val today = LocalDate.of(2026, 11, 5)

    @Test fun withDashes_givesDateAndMale() {
        val info = parseIc("010203-14-5677", today)!!
        assertEquals(LocalDate.of(2001, 2, 3), info.dateOfBirth)
        assertEquals("MALE", info.gender)
    }

    @Test fun withoutDashes_sameResult() =
        assertEquals(parseIc("010203-14-5677", today), parseIc("010203145677", today))

    @Test fun evenLastDigit_isFemale() = assertEquals("FEMALE", parseIc("010203145678", today)!!.gender)

    @Test fun futureYear_goesTo1900s() =
        assertEquals(LocalDate.of(1985, 5, 12), parseIc("850512041234", today)!!.dateOfBirth)

    @Test fun leapDay_isValid() =
        assertEquals(LocalDate.of(2000, 2, 29), parseIc("000229011234", today)!!.dateOfBirth)

    @Test fun invalidMonth_isNull() = assertNull(parseIc("011301145677", today))

    @Test fun invalidDay_isNull() = assertNull(parseIc("010231145677", today))

    @Test fun wrongLength_isNull() {
        assertNull(parseIc("0102031456", today))
        assertNull(parseIc("0102031456771", today))
        assertNull(parseIc("", today))
    }
}
