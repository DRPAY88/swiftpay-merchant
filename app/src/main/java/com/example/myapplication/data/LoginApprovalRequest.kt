package com.example.myapplication.data

import kotlinx.serialization.Serializable

@Serializable
data class LoginApprovalRequest(
    val requestId: String,
    val email: String,
    val deviceId: String,
    val deviceName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: ApprovalStatus = ApprovalStatus.PENDING,
    val expiresAt: Long = System.currentTimeMillis() + (5 * 60 * 1000) // 5 minutes
)

enum class ApprovalStatus {
    PENDING,
    APPROVED,
    DENIED,
    EXPIRED
}

