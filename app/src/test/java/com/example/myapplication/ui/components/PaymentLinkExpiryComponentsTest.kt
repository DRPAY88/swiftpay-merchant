package com.example.myapplication.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentLinkExpiryComponentsTest {
    @Test
    fun expiryIsCalculatedFromTheCurrentTimestamp() {
        val expiry = calculateExpiry(expiryTimestamp = 10_000L, now = 11_000L)

        assertEquals(true, expiry.isExpired)
        assertEquals("Link Expired", expiry.displayText)
    }

    @Test
    fun expiryCalculatesRemainingDaysAndHours() {
        val expiry = calculateExpiry(
            expiryTimestamp = 10_000_000L,
            now = 10_000_000L - (25L * 60 * 60 * 1000)
        )

        assertEquals(false, expiry.isExpired)
        assertEquals(1, expiry.days)
        assertEquals(1, expiry.hours)
        assertEquals("1 day left", expiry.displayText)
    }
}
