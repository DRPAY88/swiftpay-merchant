package com.example.myapplication.ui.screens.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * NFC reader state manager.
 * Handles NFC operation states, timers, and error management.
 */
class NfcReaderState {
    var isActive by mutableStateOf(false)
        private set

    var timeRemaining by mutableIntStateOf(60)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var readProgress by mutableStateOf("")
        private set

    var cardData by mutableStateOf<NfcCardData?>(null)
        private set

    /**
     * Start NFC reader
     */
    fun startReading() {
        isActive = true
        errorMessage = null
        timeRemaining = 60
    }

    /**
     * Stop NFC reader
     */
    fun stopReading() {
        isActive = false
    }

    /**
     * Update timer countdown
     */
    fun updateTimer(remaining: Int) {
        timeRemaining = remaining
    }

    /**
     * Set error and stop reading
     */
    fun setError(message: String) {
        errorMessage = message
        isActive = false
    }

    /**
     * Update read progress message
     */
    fun setProgress(message: String) {
        readProgress = message
    }

    /**
     * Store read card data
     */
    fun updateCardData(data: NfcCardData) {
        cardData = data
        isActive = false
    }

    /**
     * Reset all state
     */
    fun reset() {
        isActive = false
        timeRemaining = 60
        errorMessage = null
        readProgress = ""
        cardData = null
    }
}

/**
 * Data class for NFC card information
 */
data class NfcCardData(
    val pan: String,        // Primary Account Number (last 4 digits visible only)
    val expiry: String,     // MM/YY format
    val cardholderName: String = "CARD HOLDER",
    val maskedPan: String = "**** **** **** ${pan.takeLast(4)}"
)


