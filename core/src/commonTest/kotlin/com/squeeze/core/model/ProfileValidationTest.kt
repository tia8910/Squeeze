package com.squeeze.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The gate on the three fields the app cannot work without.
 *
 * Height matters most: it is the scale reference for the entire photo scan, so a wrong
 * height is not a wrong field but a wrong app — every circumference it ever produces scales
 * with it.
 */
class ProfileValidationTest {

    private val currentYear = 2026

    @Test
    fun `ordinary values are accepted`() {
        assertNull(ProfileValidation.heightError("175"))
        assertNull(ProfileValidation.heightError("162.5"))
        assertNull(ProfileValidation.birthYearError("1989", currentYear))
    }

    @Test
    fun `a height typed in metres is named rather than just rejected`() {
        val error = ProfileValidation.heightError("1.75")
        assertNotNull(error)
        assertTrue(
            error.message.contains("metres", ignoreCase = true),
            "expected the message to name the actual mistake, got: ${error.message}",
        )
    }

    @Test
    fun `heights outside the plausible range are rejected`() {
        assertNotNull(ProfileValidation.heightError("90"))
        assertNotNull(ProfileValidation.heightError("260"))
        assertNotNull(ProfileValidation.heightError("abc"))
    }

    @Test
    fun `birth years implying an implausible age are rejected`() {
        // Too young to have an adult equation apply, and too old to be a typo-free entry.
        assertNotNull(ProfileValidation.birthYearError("2020", currentYear))
        assertNotNull(ProfileValidation.birthYearError("1850", currentYear))
        // A two-digit shorthand, which would otherwise read as year 89.
        assertNotNull(ProfileValidation.birthYearError("89", currentYear))
    }

    @Test
    fun `blank is only an error once the user has been asked to commit`() {
        // While typing, an empty field is not yet wrong — flagging it immediately would put
        // an error under every box the moment the screen opens.
        assertNull(ProfileValidation.heightError(""))
        assertNull(ProfileValidation.birthYearError("", currentYear))

        assertNotNull(ProfileValidation.heightError("", blankIsError = true))
        assertNotNull(ProfileValidation.birthYearError("", currentYear, blankIsError = true))
    }

    @Test
    fun `completeness requires all three fields`() {
        assertTrue(ProfileValidation.isComplete("175", "1989", Sex.MALE, currentYear))

        assertTrue(!ProfileValidation.isComplete("175", "1989", null, currentYear))
        assertTrue(!ProfileValidation.isComplete("", "1989", Sex.MALE, currentYear))
        assertTrue(!ProfileValidation.isComplete("175", "", Sex.MALE, currentYear))
        assertTrue(!ProfileValidation.isComplete("999", "1989", Sex.MALE, currentYear))
    }

    @Test
    fun `build returns a profile only for valid input`() {
        val profile = ProfileValidation.build("175", "1989", Sex.MALE, currentYear)
        assertNotNull(profile)
        assertEquals(175.0, profile.heightCm, 1e-9)
        assertEquals(1989, profile.birthYear)
        assertEquals(Sex.MALE, profile.sex)
        assertEquals(37, profile.ageAt(currentYear))

        assertNull(ProfileValidation.build("1.75", "1989", Sex.MALE, currentYear))
        assertNull(ProfileValidation.build("175", "89", Sex.MALE, currentYear))
        assertNull(ProfileValidation.build("175", "1989", null, currentYear))
    }

    @Test
    fun `the age bounds move with the year rather than being fixed`() {
        // The same birth year is valid now and out of range decades later, which is what
        // passing the year in rather than reading a clock makes testable.
        assertNull(ProfileValidation.birthYearError("1950", currentYear = 2026))
        assertNotNull(ProfileValidation.birthYearError("1950", currentYear = 2060))
    }
}
