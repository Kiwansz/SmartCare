package com.example.smartcare.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetCodeTest {
    @Test fun eightDigits_isValid() = assertTrue(ResetCode.isValid("12345678"))
    @Test fun sixDigits_isValid() = assertTrue(ResetCode.isValid("123456"))
    @Test fun fiveDigits_isInvalid() = assertFalse(ResetCode.isValid("12345"))
    @Test fun nineDigits_isInvalid() = assertFalse(ResetCode.isValid("123456789"))
    @Test fun letters_areInvalid() = assertFalse(ResetCode.isValid("12ab5678"))
    @Test fun empty_isInvalid() = assertFalse(ResetCode.isValid(""))
}
