package com.example.myapplication.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class MerchantProfileFormattingTest {
    @Test
    fun monthlyVolumeUsesStableGroupingRegardlessOfDeviceLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1,234", formatMonthlyVolume(1234.0))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
