package com.example.myapplication.ui.utils

import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility functions for formatting and validation
 */
object FormatUtils {

    /**
     * Format amount as currency
     */
    fun formatCurrency(amount: Double, currency: String = "₱"): String {
        return "$currency%.2f".format(amount)
    }

    /**
     * Format date for display
     */
    fun formatDate(timestamp: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return sdf.format(calendar.time)
    }

    /**
     * Format date and time for display
     */
    fun formatDateTime(timestamp: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return sdf.format(calendar.time)
    }

    /**
     * Format time as HH:MM:SS
     */
    fun formatTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return "%02d:%02d:%02d".format(hours, minutes, secs)
    }

    /**
     * Mask card number (show only last 4 digits)
     */
    fun maskCardNumber(cardNumber: String): String {
        return if (cardNumber.length >= 4) {
            "**** **** **** ${cardNumber.takeLast(4)}"
        } else {
            cardNumber
        }
    }

    /**
     * Format expiry as MM/YY
     */
    fun formatExpiry(month: String, year: String): String {
        return "${month.padStart(2, '0')}/${year.takeLast(2)}"
    }
}

/**
 * Validation utilities
 */
object ValidationUtils {

    /**
     * Validate card number using Luhn algorithm
     */
    fun isValidCardNumber(cardNumber: String): Boolean {
        if (cardNumber.length < 13 || cardNumber.length > 19) return false

        var sum = 0
        var isEvenPosition = false

        for (i in cardNumber.length - 1 downTo 0) {
            var digit = cardNumber[i].toString().toIntOrNull() ?: return false

            if (isEvenPosition) {
                digit *= 2
                if (digit > 9) digit -= 9
            }

            sum += digit
            isEvenPosition = !isEvenPosition
        }

        return sum % 10 == 0
    }

    /**
     * Validate CVV
     */
    fun isValidCvv(cvv: String): Boolean {
        return cvv.length in 3..4 && cvv.all { it.isDigit() }
    }

    /**
     * Validate expiry date
     */
    fun isValidExpiry(month: String, year: String): Boolean {
        val monthInt = month.toIntOrNull() ?: return false
        val yearInt = year.toIntOrNull() ?: return false

        if (monthInt < 1 || monthInt > 12) return false

        val currentYear = Calendar.getInstance().get(Calendar.YEAR) % 100
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1

        return if (yearInt > currentYear) true else yearInt == currentYear && monthInt >= currentMonth
    }

    /**
     * Validate email format
     */
    fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$".toRegex()
        return emailRegex.matches(email)
    }

    /**
     * Validate payment amount
     */
    fun isValidAmount(amount: Double): Boolean {
        return amount > 0 && amount <= 1_000_000.0
    }
}

