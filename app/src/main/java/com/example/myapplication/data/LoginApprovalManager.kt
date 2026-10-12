package com.example.myapplication.data

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.util.UUID
import com.example.myapplication.util.DebugLogger

private val Context.approvalDataStore: DataStore<Preferences> by preferencesDataStore(name = "login_approvals")

/**
 * Manages login approval requests for multi-device authentication.
 * - Device 2 initiates login with any password
 * - Huawei (Device 1/Primary) receives approval notification
 * - User on Huawei approves/denies
 * - Device 2 completes login (if approved)
 */
class LoginApprovalManager(private val context: Context) {

    companion object {
        private const val TAG = "LoginApprovalManager"
        val PENDING_APPROVALS = stringPreferencesKey("pending_approvals")
    }

    private fun getDeviceId(): String = Build.DEVICE ?: "unknown_device"
    private fun getDeviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

    /**
     * Create a login approval request when Device 2 attempts to login
     */
    suspend fun createApprovalRequest(email: String): LoginApprovalRequest {
        val request = LoginApprovalRequest(
            requestId = UUID.randomUUID().toString(),
            email = email,
            deviceId = getDeviceId(),
            deviceName = getDeviceName()
        )

        savePendingRequest(request)
        DebugLogger.logApprovalRequested(email, request.deviceId)
        Log.i(TAG, "Approval request created for $email from ${request.deviceName}")

        return request
    }

    /**
     * Get all pending approval requests (for Huawei to show notifications)
     */
    fun getPendingApprovalsFlow(): Flow<List<LoginApprovalRequest>> {
        return context.approvalDataStore.data.map { preferences ->
            val json = preferences[PENDING_APPROVALS] ?: "[]"
            try {
                val requests: List<LoginApprovalRequest> = Json.decodeFromString(json)
                // Filter out expired requests
                requests.filter { System.currentTimeMillis() < it.expiresAt }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse pending approvals", e)
                emptyList()
            }
        }
    }

    /**
     * Approve a login request
     */
    suspend fun approveRequest(requestId: String): Boolean {
        return try {
            val requests = getPendingRequests()
            val updated = requests.map { request ->
                if (request.requestId == requestId) {
                    request.copy(status = ApprovalStatus.APPROVED)
                } else {
                    request
                }
            }
            savePendingRequestsList(updated)
            DebugLogger.logApprovalApproved(requestId)
            Log.i(TAG, "Approval request $requestId approved")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to approve request", e)
            false
        }
    }

    /**
     * Deny a login request
     */
    suspend fun denyRequest(requestId: String): Boolean {
        return try {
            val requests = getPendingRequests()
            val updated = requests.map { request ->
                if (request.requestId == requestId) {
                    request.copy(status = ApprovalStatus.DENIED)
                } else {
                    request
                }
            }
            savePendingRequestsList(updated)
            DebugLogger.logApprovalDenied(requestId)
            Log.i(TAG, "Approval request $requestId denied")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deny request", e)
            false
        }
    }

    /**
     * Check if a specific request was approved
     */
    suspend fun isRequestApproved(requestId: String): Boolean {
        val request = getPendingRequests().find { it.requestId == requestId }
        return request?.status == ApprovalStatus.APPROVED
    }

    /**
     * Remove a request after it's been processed
     */
    suspend fun removeRequest(requestId: String) {
        val requests = getPendingRequests().filter { it.requestId != requestId }
        savePendingRequestsList(requests)
    }

    private suspend fun savePendingRequest(request: LoginApprovalRequest) {
        val requests = getPendingRequests().toMutableList()
        requests.add(request)
        savePendingRequestsList(requests)
    }

    private suspend fun savePendingRequestsList(requests: List<LoginApprovalRequest>) {
        context.approvalDataStore.edit { preferences ->
            val json = Json.encodeToString(requests)
            preferences[PENDING_APPROVALS] = json
        }
    }

    private suspend fun getPendingRequests(): List<LoginApprovalRequest> {
        return try {
            val json = context.approvalDataStore.data.map {
                it[PENDING_APPROVALS] ?: "[]"
            }.first()
            Json.decodeFromString(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get pending requests", e)
            emptyList()
        }
    }

    suspend fun clearAllExpiredRequests() {
        val requests = getPendingRequests()
            .filter { System.currentTimeMillis() < it.expiresAt }
        savePendingRequestsList(requests)
    }
}

