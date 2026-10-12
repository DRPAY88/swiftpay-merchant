package com.example.myapplication.data

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import com.example.myapplication.util.DebugLogger
import java.util.*

@Serializable
data class SessionToken(
    val email: String,
    val token: String,
    val deviceId: String,
    val deviceName: String,
    val isPrimaryDevice: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000) // 30 days
)

/**
 * Manages multi-device session synchronization.
 * Ensures that when user logs in on one device (primary), other devices can auto-login
 * without requiring re-authentication.
 */
class SessionManager(private val context: Context, private val settingsManager: SettingsManager) {

    companion object {
        private const val TAG = "SessionManager"
        private const val SESSION_STORAGE_KEY = "fastpay_sessions"
    }

    private fun getDeviceId(): String {
        return Build.DEVICE ?: "unknown_device"
    }

    private fun getDeviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    /**
     * Called after successful OTP verification on any device.
     * Stores the session and marks device as active.
     */
    suspend fun createSession(email: String, isPrimary: Boolean = false) {
        try {
            val deviceId = getDeviceId()
            val deviceName = getDeviceName()
            val token = UUID.randomUUID().toString()

            val session = SessionToken(
                email = email,
                token = token,
                deviceId = deviceId,
                deviceName = deviceName,
                isPrimaryDevice = isPrimary
            )

            // Store session locally for quick access
            settingsManager.saveSessionToken(session)

            DebugLogger.logSessionCreated(email, deviceId, isPrimary)
            Log.i(TAG, "Session created for $email on $deviceName (Primary: $isPrimary)")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create session", e)
        }
    }

    /**
     * Check if there's an active session for the email.
     * Called on app startup to auto-login.
     */
    suspend fun getActiveSession(email: String): SessionToken? {
        return try {
            val sessionToken = settingsManager.getSessionToken().first()

            if (sessionToken != null && sessionToken.email == email) {
                // Check if session has expired
                if (System.currentTimeMillis() < sessionToken.expiresAt) {
                    DebugLogger.logSessionRestored(email, sessionToken.deviceId)
                    Log.i(TAG, "Active session found for $email")
                    return sessionToken
                } else {
                    // Session expired
                    clearSession()
                    Log.i(TAG, "Session expired for $email")
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get active session", e)
            null
        }
    }

    /**
     * Clear session when user logs out on primary device.
     * This will cause other devices to show login screen on next check.
     */
    suspend fun clearSession() {
        try {
            settingsManager.clearSessionToken()
            DebugLogger.logSessionCleared()
            Log.i(TAG, "Session cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear session", e)
        }
    }

    /**
     * Get currently stored session token
     */
    suspend fun getCurrentSessionToken(): SessionToken? {
        return settingsManager.getSessionToken().first()
    }

    /**
     * Refresh session expiration to keep user logged in
     */
    suspend fun refreshSessionExpiration() {
        try {
            val current = settingsManager.getSessionToken().first()
            if (current != null) {
                val refreshed = current.copy(
                    expiresAt = System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000) // Extend 30 days
                )
                settingsManager.saveSessionToken(refreshed)
                Log.d(TAG, "Session expiration refreshed for ${current.email}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh session", e)
        }
    }
}

