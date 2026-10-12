package com.example.myapplication.domain.usecase

import com.example.myapplication.data.repository.QrRepository

class GenerateQrUseCase(private val qrRepository: QrRepository) {

    suspend fun generateQr(amount: Double): Result<String> {
        return try {
            if (amount <= 0) {
                return Result.failure(Exception("Invalid amount: must be greater than 0"))
            }

            val result = qrRepository.createDynamicQr(amount)

            result.map { response ->
                response.qrCode ?: response.qrCodeUrl ?: response.qrCodeBody ?: throw Exception("No QR data returned")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isAmountValid(amount: Double): Boolean {
        return amount in 0.01..1_000_000.0
    }

    fun getAmountErrorMessage(amount: Double): String? {
        return when {
            amount <= 0 -> "Amount must be greater than ₱0.00"
            amount > 1_000_000 -> "Amount cannot exceed ₱1,000,000.00"
            else -> null
        }
    }
}
