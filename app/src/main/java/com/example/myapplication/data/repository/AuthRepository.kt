package com.example.myapplication.data.repository

import com.example.myapplication.data.SwiftPayService

/**
 * Repository for authentication and merchant operations.
 */
class AuthRepository(private val swiftPayService: SwiftPayService) {

    suspend fun sendCustomOtp(email: String, code: String): Result<Boolean> =
        swiftPayService.sendCustomOtp(email, code)

    suspend fun onlineLogin(email: String, password: String): Result<Boolean> =
        swiftPayService.onlineLogin(email, password)

    suspend fun verifyOnlineCode(email: String, code: String): Result<Boolean> =
        swiftPayService.verifyOnlineCode(email, code)
}
