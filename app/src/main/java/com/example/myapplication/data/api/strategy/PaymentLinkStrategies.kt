package com.example.myapplication.data.api.strategy

import com.example.myapplication.data.api.SwiftPayPaymentLinkResponse
import android.util.Log

interface PaymentLinkStrategy {
    suspend fun createPaymentLink(): SwiftPayPaymentLinkResponse?
}

data class PaymentLinkStrategyConfig(
    val amount: Double,
    val description: String,
    val merchantAlias: String? = null,
    val referenceNumber: String = "FPLINK${System.currentTimeMillis()}",
    val maxAmount: Double = 1_000_000.0
) {
    init {
        require(amount <= maxAmount) { "Amount exceeds maximum limit of ₱$maxAmount" }
    }
}

class PaymentLinkStrategyFactory(
    private val strategies: List<PaymentLinkStrategy>
) {
    suspend fun execute(): Result<SwiftPayPaymentLinkResponse> {
        return try {
            for ((index, strategy) in strategies.withIndex()) {
                try {
                    Log.d("PaymentLinkFactory", "Attempting strategy ${index + 1}/${strategies.size}")
                    val result = strategy.createPaymentLink()
                    if (result != null) {
                        Log.i("PaymentLinkFactory", "Strategy ${index + 1} succeeded")
                        return Result.success(result)
                    }
                } catch (e: Exception) {
                    Log.w("PaymentLinkFactory", "Strategy ${index + 1} failed: ${e.message}")
                    if (index == strategies.size - 1) {
                        return Result.failure(Exception("All payment link strategies failed", e))
                    }
                }
            }
            Result.failure(Exception("No payment link strategy could generate a link"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

object PaymentLinkValidator {
    fun validate(config: PaymentLinkStrategyConfig): Result<Unit> {
        return try {
            when {
                config.description.isBlank() -> Result.failure(Exception("Description cannot be empty"))
                config.amount < 0 -> Result.failure(Exception("Amount cannot be negative"))
                config.amount > config.maxAmount -> Result.failure(Exception("Amount exceeds maximum limit of ₱${config.maxAmount}"))
                else -> Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class PaymentLinkRequestBuilder {
    private var amount: Double = 0.0
    private var description: String = ""
    private var merchantAlias: String? = null
    private var referenceNumber: String = "FPLINK${System.currentTimeMillis()}"

    fun amount(amount: Double) = apply { this.amount = amount }

    fun description(description: String) = apply {
        this.description = description.ifBlank { "Fast Pay Payment" }
    }

    fun merchantAlias(alias: String?) = apply {
        this.merchantAlias = alias?.takeIf { it.isNotBlank() }
    }

    fun referenceNumber(refNo: String) = apply {
        this.referenceNumber = refNo
    }

    fun build(): PaymentLinkStrategyConfig {
        return PaymentLinkStrategyConfig(
            amount = amount,
            description = description.ifBlank { "Fast Pay Payment" },
            merchantAlias = merchantAlias,
            referenceNumber = referenceNumber
        )
    }
}
