package com.example.myapplication.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import okhttp3.RequestBody

@Serializable
data class RequestOtpBody(val email: String)

@Serializable
data class VerifyOtpBody(val email: String, val code: String)

@Serializable
data class BackendSessionResponse(
    val accessToken: String,
    val expiresInSeconds: Long,
    val email: String? = null,
    val role: String = "merchant"
)

@Serializable
data class BackendOnboardingStateResponse(val required: Boolean)

@Serializable
data class BackendOnboardingCompleteResponse(val completed: Boolean)

@Serializable
data class BackendRegistrationConfigResponse(
    val enabled: Boolean = false,
    val secureStorageAvailable: Boolean = false
)

@Serializable
data class RegistrationProfileBody(
    val businessName: String,
    val businessRegistrationNumber: String,
    val businessAddress: String,
    val businessType: String,
    val representativeFullName: String,
    val representativePhone: String
)

@Serializable
data class BackendRegistrationProfileResponse(
    val businessName: String = "",
    val businessRegistrationNumber: String = "",
    val businessAddress: String = "",
    val businessType: String = "",
    val representativeFullName: String = "",
    val representativePhone: String = ""
)

@Serializable
data class BackendRegistrationStatusResponse(
    val email: String,
    val status: String,
    val reviewNote: String = "",
    val updatedAt: String = "",
    val profile: BackendRegistrationProfileResponse = BackendRegistrationProfileResponse(),
    val documents: List<String> = emptyList()
)

interface BackendAuthApi {
    @POST("v1/auth/request-otp")
    suspend fun requestOtp(@Body body: RequestOtpBody): Response<Map<String, String>>

    @POST("v1/auth/verify-otp")
    suspend fun verifyOtp(@Body body: VerifyOtpBody): Response<BackendSessionResponse>

    @GET("v1/registration/config")
    suspend fun getRegistrationConfig(): Response<BackendRegistrationConfigResponse>

    @POST("v1/registration/request-otp")
    suspend fun requestRegistrationOtp(@Body body: RequestOtpBody): Response<Map<String, String>>

    @POST("v1/registration/verify-otp")
    suspend fun verifyRegistrationOtp(@Body body: VerifyOtpBody): Response<BackendSessionResponse>

    @PUT("v1/registration/profile")
    suspend fun saveRegistrationProfile(
        @Header("Authorization") authorization: String,
        @Body body: RegistrationProfileBody
    ): Response<Map<String, String>>

    @PUT("v1/registration/documents/{kind}")
    suspend fun uploadRegistrationDocument(
        @Header("Authorization") authorization: String,
        @retrofit2.http.Path("kind") kind: String,
        @Header("Content-Type") contentType: String,
        @Body body: RequestBody
    ): Response<Map<String, String>>

    @POST("v1/registration/submit")
    suspend fun submitRegistration(
        @Header("Authorization") authorization: String
    ): Response<BackendRegistrationStatusResponse>

    @GET("v1/registration/status")
    suspend fun getRegistrationStatus(
        @Header("Authorization") authorization: String
    ): Response<BackendRegistrationStatusResponse>

    @GET("v1/onboarding/state")
    suspend fun getOnboardingState(
        @Header("Authorization") authorization: String
    ): Response<BackendOnboardingStateResponse>

    @POST("v1/onboarding/complete")
    suspend fun completeOnboarding(
        @Header("Authorization") authorization: String
    ): Response<BackendOnboardingCompleteResponse>
}