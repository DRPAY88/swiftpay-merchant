package com.example.myapplication.util

import androidx.compose.ui.graphics.Color

/**
 * Normalizes payment status strings to a standard set of values.
 * Handles various API response formats and aliases.
 */
fun normalizePaymentStatus(status: String?): String {
    if (status.isNullOrBlank()) return "PENDING"

    return when {
        status.contains("SUCCESS", ignoreCase = true) ||
        status.contains("PAID", ignoreCase = true) ||
        status.contains("CAPTURED", ignoreCase = true) ||
        status.contains("AUTHORIZED", ignoreCase = true) ||
        status.contains("COMPLETED", ignoreCase = true) -> "SUCCESS"

        status.contains("FAIL", ignoreCase = true) ||
        status.contains("FAILED", ignoreCase = true) ||
        status.contains("EXPIRED", ignoreCase = true) ||
        status.contains("VOIDED", ignoreCase = true) -> "FAILED"

        status.contains("CANCEL", ignoreCase = true) -> "CANCELLED"

        else -> status.uppercase()
    }
}

/**
 * Get the color associated with a payment status for UI display.
 */
fun getStatusColor(status: String): Color {
    return when {
        status.contains("SUCCESS", ignoreCase = true) -> Color(0xFF0ACF83)
        status.contains("FAIL", ignoreCase = true) || status.contains("FAILED", ignoreCase = true) -> Color(0xFFFF3B30)
        status.contains("CANCEL", ignoreCase = true) -> Color(0xFFFF9500)
        else -> Color(0xFFB9C0C6)
    }
}

/**
 * Get text color for a given background status color.
 */
fun getStatusTextColor(status: String): Color {
    return if (status.contains("SUCCESS", ignoreCase = true)) {
        Color(0xFF04663A)
    } else {
        Color(0xFF0A0E27) // FastPayNavy
    }
}

