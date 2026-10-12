package com.example.myapplication.data

import com.example.myapplication.BuildConfig
import kotlinx.coroutines.flow.first

data class SwiftPayCredentials(
    val hasBackendSession: Boolean
) {
    val hasApiKey: Boolean = hasBackendSession
}

suspend fun SettingsManager.loadSwiftPayCredentials(): SwiftPayCredentials {
    val token = getAccessToken()?.takeIf { it.isNotBlank() }
        ?: backendAccessToken.first()?.takeIf { it.isNotBlank() }
    return SwiftPayCredentials(!token.isNullOrBlank())
}

suspend fun SettingsManager.createSwiftPayService(): SwiftPayService {
    val token = getAccessToken()?.takeIf { it.isNotBlank() }
        ?: backendAccessToken.first()?.takeIf { it.isNotBlank() }
    return SwiftPayService(
        backendBaseUrl = BuildConfig.PAYMENT_BACKEND_URL,
        accessToken = token
    )
}
