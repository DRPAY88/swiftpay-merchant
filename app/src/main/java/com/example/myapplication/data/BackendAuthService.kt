package com.example.myapplication.data

import com.example.myapplication.BuildConfig
import com.example.myapplication.data.api.BackendAuthApi
import com.example.myapplication.data.api.BackendSessionResponse
import com.example.myapplication.data.api.RequestOtpBody
import com.example.myapplication.data.api.VerifyOtpBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

internal fun resolveBackendBaseUrl(configuredUrl: String): String {
    val value = configuredUrl.takeUnless { it.isBlank() } ?: "http://10.0.2.2:3000/"
    val uri = URI(value)
    val localHosts = setOf("localhost", "127.0.0.1", "10.0.2.2")
    require(uri.scheme == "https" || (uri.scheme == "http" && uri.host in localHosts)) {
        "The payment backend must use HTTPS outside local development"
    }
    require(uri.userInfo == null && uri.host != null) { "Invalid payment backend URL" }
    return if (value.endsWith("/")) value else "$value/"
}

class BackendAuthService {
    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = resolveBackendBaseUrl(BuildConfig.PAYMENT_BACKEND_URL)

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val api = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(BackendAuthApi::class.java)

    suspend fun requestOtp(email: String): Result<Unit> {
        return try {
            val response = api.requestOtp(RequestOtpBody(email))
            if (!response.isSuccessful) throw authError(response.code(), response.errorBody()?.string())
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun verifyOtp(email: String, code: String): Result<BackendSessionResponse> {
        return try {
            val response = api.verifyOtp(VerifyOtpBody(email, code))
            if (response.isSuccessful) {
                Result.success(response.body() ?: throw IllegalStateException("Empty sign-in response"))
            } else {
                throw authError(response.code(), response.errorBody()?.string())
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun authError(statusCode: Int, responseBody: String?): IllegalStateException {
        val backendMessage = runCatching {
            json.parseToJsonElement(responseBody.orEmpty())
                .jsonObject["error"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()

        val message = when {
            statusCode == 401 -> "Invalid or expired verification code"
            statusCode == 503 -> "Sign-in code delivery is temporarily unavailable. Please try again later."
            else -> backendMessage ?: "Sign-in failed ($statusCode)"
        }
        return IllegalStateException(message)
    }
}