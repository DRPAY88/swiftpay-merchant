package com.example.myapplication.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthenticationUiUtilsTest {
    @Test
    fun emailMaskKeepsOnlyTheFirstLocalPartCharacter() {
        assertEquals("m***@example.com", maskEmailAddress("merchant@example.com"))
    }

    @Test
    fun emailMaskHandlesSingleCharacterLocalParts() {
        assertEquals("a***@example.com", maskEmailAddress("a@example.com"))
    }

    @Test
    fun otpInputRemovesNonAsciiDigitsAndCapsAtSixCharacters() {
        assertEquals("123456", filterOtpInput("1a2-3 4x567"))
        assertEquals("", filterOtpInput("１２３４５６"))
    }
}
