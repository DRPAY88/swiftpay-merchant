package com.example.myapplication.data.api

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class RequestOtpBody(val email: String)

@Serializable
data class VerifyOtpBody(val email: String, val code: String)

@Serializable
data class BackendSessionResponse(
    val accessToken: String,
    val expiresInSeconds: Long,
    val email: String
)

interface BackendAuthApi {
    @POST("v1/auth/request-otp")
    suspend fun requestOtp(@Body body: RequestOtpBody): Response<Map<String, String>>

    @POST("v1/auth/verify-otp")
    suspend fun verifyOtp(@Body body: VerifyOtpBody): Response<BackendSessionResponse>
}