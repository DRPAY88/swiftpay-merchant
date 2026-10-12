package com.example.myapplication.domain.usecase

import com.example.myapplication.data.SwiftPayService
import com.example.myapplication.data.api.SwiftPayPaymentLinkResponse
import com.example.myapplication.data.api.strategy.PaymentLinkRequestBuilder
import android.util.Log

class GeneratePaymentLinkUseCase(
    private val swiftPayService: SwiftPayService
) {
    suspend fun execute(
        amount: Double = 0.0,
        description: String = "",
        merchantAlias: String? = null
    ): Result<SwiftPayPaymentLinkResponse> {
        return try {
            if (amount < 0) {
                return Result.failure(Exception("Amount cannot be negative"))
            }
            if (amount > 1_000_000) {
                return Result.failure(Exception("Amount exceeds maximum limit of ₱1,000,000"))
            }

            val desc = description.ifBlank { "SwiftPay Payment" }
            Log.d("GeneratePaymentLinkUseCase", "Generating payment link: amount=$amount, desc=$desc")

            val result = swiftPayService.createPaymentLink(
                amount = if (amount > 0) amount else 0.0,
                description = desc
            )

            when {
                result.isSuccess -> {
                    val linkResponse = result.getOrNull()
                    Log.i("GeneratePaymentLinkUseCase", "Payment link generated: ${linkResponse?.paymentLinkUrl}")
                    Result.success(linkResponse!!)
                }
                else -> {
                    val error = result.exceptionOrNull()?.message ?: "Failed to generate payment link"
                    Log.e("GeneratePaymentLinkUseCase", "Payment link generation failed: $error")
                    Result.failure(Exception(error))
                }
            }
        } catch (e: Exception) {
            Log.e("GeneratePaymentLinkUseCase", "Exception in GeneratePaymentLinkUseCase", e)
            Result.failure(e)
        }
    }

    fun buildRequest(): PaymentLinkRequestBuilder {
        return PaymentLinkRequestBuilder()
    }
}
