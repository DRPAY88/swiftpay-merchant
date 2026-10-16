package com.example.myapplication.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class ReceiptDateFormatterTest {
    @Test
    fun formatsMicrosecondIsoTimestampWithoutTimezone() {
        withUsLocaleAndUtc {
            assertEquals(
                "Oct 13, 2026 · 2:22 PM",
                formatReceiptDate("2026-10-13T14:22:49.906615")
            )
        }
    }

    @Test
    fun convertsOffsetTimestampToDeviceTimezone() {
        withUsLocaleAndUtc {
            assertEquals(
                "Oct 13, 2026 · 6:22 AM",
                formatReceiptDate("2026-10-13T14:22:49.906615+08:00")
            )
        }
    }

    @Test
    fun leavesUnrecognizedDateUnchanged() {
        assertEquals("date unavailable", formatReceiptDate("date unavailable"))
    }

    private fun withUsLocaleAndUtc(block: () -> Unit) {
        val originalLocale = Locale.getDefault()
        val originalTimeZone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.US)
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            block()
        } finally {
            Locale.setDefault(originalLocale)
            TimeZone.setDefault(originalTimeZone)
        }
    }
}
