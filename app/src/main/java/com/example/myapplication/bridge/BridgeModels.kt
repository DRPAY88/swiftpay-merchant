package com.example.myapplication.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class BridgeRequest(
    val action: String,
    val data: JsonElement? = null
)

@Serializable
data class BridgeResponse(
    val status: String,
    val data: JsonElement? = null,
    val message: String? = null,
    val action: String? = null
)

@Serializable
data class PaymentData(
    val amount: Double = 0.0,
    val currency: String = "PHP",
    val description: String = "Payment Link"
)

@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val kycStatus: String,
    val email: String
)
