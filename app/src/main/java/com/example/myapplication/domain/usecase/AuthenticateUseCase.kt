package com.example.myapplication.domain.usecase

import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.SettingsManager

/**
 * Use case for authentication operations.
 * Handles login, OTP verification, and merchant profile operations.
 */
class AuthenticateUseCase(
    private val authRepository: AuthRepository,
    private val settingsManager: SettingsManager
) {

    /**
     * Perform online login
     */
    suspend fun login(email: String, password: String): Result<Boolean> {
        return try {
            if (!isValidEmail(email)) {
                return Result.failure(Exception("Invalid email format"))
            }

            authRepository.onlineLogin(email, password)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Send OTP code
     */
    suspend fun sendOtpCode(email: String, code: String): Result<Boolean> {
        return try {
            if (!isValidEmail(email)) {
                return Result.failure(Exception("Invalid email format"))
            }

            authRepository.sendCustomOtp(email, code)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verify OTP code
     */
    suspend fun verifyOtpCode(email: String, code: String): Result<Boolean> {
        return try {
            if (code.length != 6 || !code.all { it.isDigit() }) {
                return Result.failure(Exception("Invalid OTP format"))
            }

            authRepository.verifyOnlineCode(email, code)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Validate email format
     */
    private fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$".toRegex()
        return emailRegex.matches(email)
    }
}




