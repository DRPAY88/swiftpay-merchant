package com.example.myapplication.util

import android.util.Log

object DebugLogger {
    private const val TAG = "FastPay_DEBUG"

    fun logAuthCheck(isLoggedIn: Boolean) {
        Log.d(TAG, "AUTH_CHECK: isLoggedIn=$isLoggedIn")
    }

    fun logBuildConfigKeys(publicKey: String?, secretKey: String?, mid: String?, terminalId: String?) {
        Log.d(TAG, "BUILDCONFIG_KEYS: configured=${!publicKey.isNullOrBlank() && !secretKey.isNullOrBlank()}")
    }

    fun logStoredKeys(publicKey: String?, secretKey: String?, mid: String?, terminalId: String?) {
        Log.d(TAG, "STORED_KEYS: configured=${!publicKey.isNullOrBlank() && !secretKey.isNullOrBlank()}")
    }

    fun logLoginSuccess(email: String) {
        Log.d(TAG, "LOGIN_SUCCESS")
    }

    fun logLoginFailure(email: String, errorMessage: String) {
        Log.e(TAG, "LOGIN_FAILURE")
    }

    fun logLogout(email: String) {
        Log.d(TAG, "LOGOUT")
    }

    fun logCredentialsLoaded(hasSecret: Boolean, hasPublic: Boolean) {
        Log.d(TAG, "CREDENTIALS_LOADED: hasSecret=$hasSecret, hasPublic=$hasPublic")
    }

    fun logDeviceInfo(deviceId: String = android.os.Build.DEVICE) {
        Log.d(TAG, "DEVICE_INFO: api=${android.os.Build.VERSION.SDK_INT}")
    }
    
    fun logSessionCreated(email: String, deviceId: String, isPrimary: Boolean) {
        Log.d(TAG, "SESSION_CREATED: primary=$isPrimary")
    }
    
    fun logSessionRestored(email: String, deviceId: String) {
        Log.d(TAG, "SESSION_RESTORED")
    }
    
    fun logSessionCleared() {
        Log.d(TAG, "SESSION_CLEARED: All device sessions invalidated, login required")
    }

    fun logApprovalRequested(email: String, deviceId: String) {
        Log.d(TAG, "APPROVAL_REQUESTED")
    }

    fun logApprovalApproved(requestId: String) {
        Log.d(TAG, "APPROVAL_APPROVED")
    }

    fun logApprovalDenied(requestId: String) {
        Log.d(TAG, "APPROVAL_DENIED")
    }
}

