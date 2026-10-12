package com.example.myapplication.domain.usecase

import com.example.myapplication.data.repository.PaymentRepository
import com.example.myapplication.bridge.PaymentData

class ProcessPaymentUseCase(private val paymentRepository: PaymentRepository) {

    suspend fun executeCheckout(paymentData: PaymentData): Result<String> {
        return try {
            if (paymentData.amount <= 0) {
                return Result.failure(Exception("Invalid amount"))
            }

            val result = paymentRepository.createCheckout(paymentData)
            result.map { it.checkoutUrl ?: it.paymentUrl ?: "UNKNOWN" }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPaymentStatus(paymentId: String) =
        paymentRepository.getPaymentStatus(paymentId)

    suspend fun createPaymentLink(
        amount: Double,
        description: String
    ): Result<String> {
        return try {
            val result = paymentRepository.createPaymentLink(amount, description)
            result.map { it.paymentLinkUrl ?: throw Exception("No payment link URL") }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class CardDetailsInput(
    val cardNumber: String,
    val expiryMonth: String,
    val expiryYear: String,
    val cvv: String,
    val cardHolderName: String
)
