package com.example.myapplication.ui.screens.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.myapplication.bridge.PaymentData

/**
 * Payment flow state manager.
 * Handles the different stages and states of a payment operation.
 */
class PaymentFlowState {
    var stage by mutableStateOf<PaymentStage>(PaymentStage.Idle)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var currentPaymentData by mutableStateOf<PaymentData?>(null)
        private set

    /**
     * Transition to a new payment stage
     */
    fun moveToStage(newStage: PaymentStage) {
        stage = newStage
        errorMessage = null
    }

    /**
     * Set error message and reset loading state
     */
    fun setError(message: String) {
        errorMessage = message
        isLoading = false
    }

    /**
     * Update loading state
     */
    fun updateLoading(loading: Boolean) {
        isLoading = loading
    }

    /**
     * Update current payment data
     */
    fun setPaymentData(data: PaymentData) {
        currentPaymentData = data
    }

    /**
     * Clear all state
     */
    fun reset() {
        stage = PaymentStage.Idle
        errorMessage = null
        isLoading = false
        currentPaymentData = null
    }
}

/**
 * Enum representing different stages of payment flow
 */
enum class PaymentStage {
    Idle,           // Waiting for payment initiation
    Consent,        // Showing payment consent screen
    Processing,     // Processing payment
    QrReady,        // QR code ready for scanning
    NfcReady,       // NFC reader waiting for card
    Success,        // Payment successful
    Error           // Payment error
}


