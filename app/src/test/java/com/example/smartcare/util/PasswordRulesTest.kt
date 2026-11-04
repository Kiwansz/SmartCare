package com.example.smartcare.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordRulesTest {

    @Test fun empty_failsEverything() {
        val c = PasswordRules.check("")
        assertFalse(c.longEnough || c.hasUppercase || c.hasNumber || c.isValid)
        assertEquals(0, c.strength)
    }

    @Test fun eachRuleOnItsOwn() {
        assertTrue(PasswordRules.check("abcdefgh").longEnough)
        assertTrue(PasswordRules.check("A").hasUppercase)
        assertTrue(PasswordRules.check("1").hasNumber)
    }

    @Test fun meetsAllThree_isValid() = assertTrue(PasswordRules.check("Smartcare1").isValid)

    @Test fun missingUppercase_isInvalid() = assertFalse(PasswordRules.check("smartcare1").isValid)

    @Test fun missingNumber_isInvalid() = assertFalse(PasswordRules.check("Smartcare").isValid)

    @Test fun tooShort_isInvalid() = assertFalse(PasswordRules.check("Smar1").isValid)

    @Test fun strength_growsWithRules() {
        assertEquals(1, PasswordRules.check("a").strength)
        assertEquals(3, PasswordRules.check("Smartcare1").strength)
        assertEquals(4, PasswordRules.check("Smartcare1!").strength)
    }

    @Test fun matches_needsSameNonEmptyText() {
        assertTrue(PasswordRules.matches("Smartcare1", "Smartcare1"))
        assertFalse(PasswordRules.matches("Smartcare1", "Smartcare2"))
        assertFalse(PasswordRules.matches("", ""))
    }
}
