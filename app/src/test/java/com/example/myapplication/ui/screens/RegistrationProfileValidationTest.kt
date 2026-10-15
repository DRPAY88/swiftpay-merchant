package com.example.myapplication.ui.screens

import com.example.myapplication.data.api.RegistrationProfileBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegistrationProfileValidationTest {
    @Test
    fun acceptsTrimmedValuesWithinBackendLimits() {
        val profile = RegistrationProfileBody(
            businessName = "  AC  ",
            businessRegistrationNumber = " 12 ",
            businessAddress = " 12345 ",
            businessType = " AB ",
            representativeFullName = " Joe ",
            representativePhone = " 12345 "
        )

        assertNull(validateRegistrationProfile(profile))
    }

    @Test
    fun rejectsValuesBelowBackendMinimumLength() {
        val profile = validProfile().copy(businessAddress = "1234")

        assertEquals(
            "Business address must be at least 5 characters.",
            validateRegistrationProfile(profile)
        )
    }

    @Test
    fun rejectsValuesAboveBackendMaximumLength() {
        val profile = validProfile().copy(businessRegistrationNumber = "1".repeat(81))

        assertEquals(
            "Business registration number must be no more than 80 characters.",
            validateRegistrationProfile(profile)
        )
    }

    private fun validProfile() = RegistrationProfileBody(
        businessName = "Acme",
        businessRegistrationNumber = "123",
        businessAddress = "123 Main Street",
        businessType = "Retail",
        representativeFullName = "Jane Doe",
        representativePhone = "+639171234567"
    )
}
