package com.example.myapplication.util

import android.util.Log

object DebugLogger {
    private const val TAG = "FastPay_DEBUG"

    fun logAuthCheck(isLoggedIn: Boolean, email: String? = null) {
        Log.d(TAG, "AUTH_CHECK: isLoggedIn=$isLoggedIn, email=$email")
    }

    fun logBuildConfigKeys(publicKey: String?, secretKey: String?, mid: String?, terminalId: String?) {
        val publicMasked = publicKey?.take(10) + "..."
        val secretMasked = secretKey?.take(8) + "..."
        Log.d(TAG, "BUILDCONFIG_KEYS: public=$publicMasked, secret=$secretMasked, mid=$mid, terminalId=$terminalId")
    }

    fun logStoredKeys(publicKey: String?, secretKey: String?, mid: String?, terminalId: String?) {
        val publicMasked = publicKey?.take(10) + "..."
        val secretMasked = secretKey?.take(8) + "..."
        Log.d(TAG, "STORED_KEYS: public=$publicMasked, secret=$secretMasked, mid=$mid, terminalId=$terminalId")
    }

    fun logLoginSuccess(email: String) {
        Log.d(TAG, "LOGIN_SUCCESS: email=$email, logging in user...")
    }

    fun logLoginFailure(email: String, errorMessage: String) {
        Log.e(TAG, "LOGIN_FAILURE: email=$email, error=$errorMessage")
    }

    fun logLogout(email: String) {
        Log.d(TAG, "LOGOUT: email=$email, clearing session...")
    }

    fun logCredentialsLoaded(hasSecret: Boolean, hasPublic: Boolean) {
        Log.d(TAG, "CREDENTIALS_LOADED: hasSecret=$hasSecret, hasPublic=$hasPublic")
    }

    fun logDeviceInfo(deviceId: String = android.os.Build.DEVICE) {
        Log.d(TAG, "DEVICE_INFO: device=$deviceId, app_version=${android.os.Build.VERSION.SDK_INT}")
    }
    
    fun logSessionCreated(email: String, deviceId: String, isPrimary: Boolean) {
        Log.d(TAG, "SESSION_CREATED: email=$email, device=$deviceId, primary=$isPrimary")
    }
    
    fun logSessionRestored(email: String, deviceId: String) {
        Log.d(TAG, "SESSION_RESTORED: email=$email, device=$deviceId (auto-login without OTP)")
    }
    
    fun logSessionCleared() {
        Log.d(TAG, "SESSION_CLEARED: All device sessions invalidated, login required")
    }

    fun logApprovalRequested(email: String, deviceId: String) {
        Log.d(TAG, "APPROVAL_REQUESTED: email=$email, device=$deviceId (awaiting Huawei approval)")
    }

    fun logApprovalApproved(requestId: String) {
        Log.d(TAG, "APPROVAL_APPROVED: requestId=$requestId (Device 2 now logging in)")
    }

    fun logApprovalDenied(requestId: String) {
        Log.d(TAG, "APPROVAL_DENIED: requestId=$requestId (Device 2 login rejected)")
    }
}


