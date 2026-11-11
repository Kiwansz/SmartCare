package com.example.smartcare.util

import org.junit.Assert.assertEquals
import org.junit.Test

class IcMaskTest {
    @Test fun twelveDigits_showsLastFour() = assertEquals("******-**-5677", maskIc("010203145677"))
    @Test fun withDashes_sameResult() = assertEquals("******-**-5677", maskIc("010203-14-5677"))
    @Test fun wrongLength_fullyMasked() = assertEquals("******-**-****", maskIc("12345"))
    @Test fun nullOrEmpty_fullyMasked() {
        assertEquals("******-**-****", maskIc(null))
        assertEquals("******-**-****", maskIc(""))
    }
}
