package com.example.myapplication.domain.model

/**
 * Represents the result of a payment link generation request.
 */
sealed class PaymentLinkResult {
    data class Success(
        val url: String,
        val linkId: String,
        val description: String,
        val amount: Double = 0.0,
        val hasFixedAmount: Boolean = false
    ) : PaymentLinkResult()

    data class Error(val message: String) : PaymentLinkResult()

    data object Processing : PaymentLinkResult()
}

/**
 * Event to be emitted when a payment link is generated.
 */
data class PaymentLinkEvent(
    val result: PaymentLinkResult,
    val timestamp: Long = System.currentTimeMillis()
)

