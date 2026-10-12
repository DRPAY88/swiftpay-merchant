package com.example.myapplication.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class RedirectUrl(
    val success: String,
    val failure: String,
    val cancel: String
)

@Serializable
data class InternalTransaction(
    val transactionId: String,
    val amount: Double,
    val status: String,
    val date: String,
)

@Serializable
data class PaymentChannel(
    val name: String,
    val status: String,
    val icon: String? = null,
)

@Serializable
data class CardDetails(
    val number: String,
    val expMonth: String,
    val expYear: String,
    val cvc: String? = null
)

typealias VaultPaymentResponse = SwiftPayStatusResponse

@Serializable
data class MerchantProfileResponse(
    val id: String? = null,
    val merchantId: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val businessName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val kycStatus: String? = null,
    val accountStatus: String? = null,
    val createdAt: String? = null,
    val businessType: String? = null,
    val monthlyVolume: Double? = null,
    val masterMid: String? = null,
    val mids: List<String>? = null
)

@Serializable
data class SwiftPayOrderRequest(
    val amount: Double,
    @SerialName("reference_no")
    val referenceNo: String,
    val description: String = "",
    val currency: String = "PHP",
    @SerialName("institution_code")
    val institutionCode: String? = null,
    @SerialName("customer_name")
    val customerName: String? = null,
    @SerialName("customer_email")
    val customerEmail: String? = null,
    val details: Map<String, String>? = null
)

@Serializable
data class SwiftPayOrderResponse(
    val id: String? = null,
    @SerialName("order_id")
    val orderId: String? = null,
    @SerialName("checkout_url")
    val checkoutUrl: String? = null,
    @SerialName("payment_url")
    val paymentUrl: String? = null,
    @SerialName("qr_code")
    val qrCode: String? = null,
    val status: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    @SerialName("reference_no")
    val referenceNo: String? = null,
    @SerialName("redirect_url")
    val redirectUrl: RedirectUrl? = null
)

@Serializable
data class SwiftPayStatusResponse(
    val id: String? = null,
    @SerialName("order_id")
    val orderId: String? = null,
    val status: String? = null,
    @SerialName("payment_status")
    val paymentStatus: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    @SerialName("reference_no")
    val referenceNo: String? = null,
    @SerialName("verification_url")
    val verificationUrl: String? = null,
    @SerialName("receipt_number")
    val receiptNumber: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class SwiftPayDisbursementRequest(
    val amount: Double,
    @SerialName("reference_no")
    val referenceNo: String,
    @SerialName("bank_code")
    val bankCode: String,
    @SerialName("account_number")
    val accountNumber: String,
    @SerialName("first_name")
    val firstName: String,
    @SerialName("last_name")
    val lastName: String,
    @SerialName("middle_name")
    val middleName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val line1: String? = "N/A",
    val line2: String? = null,
    val city: String? = "Manila",
    val province: String? = "Metro Manila",
    @SerialName("postal_code")
    val postalCode: String? = "1000",
    @SerialName("country_code")
    val countryCode: String? = "PH",
    val note: String? = ""
)

@Serializable
data class Recipient(
    val firstName: String? = null,
    val lastName: String? = null,
    val accountNumber: String? = null,
)

@Serializable
data class SwiftPayDisbursementResponse(
    val id: String? = null,
    @SerialName("disbursement_id")
    val disbursementId: String? = null,
    val status: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    @SerialName("reference_no")
    val referenceNo: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class SwiftPayInstitution(
    val code: String,
    val name: String,
    val type: String? = null,
    val icon: String? = null,
    val status: String = "ACTIVE"
)

@Serializable
data class SwiftPayPaymentLinkRequest(
    val amount: Double? = null,
    val description: String? = null,
    @SerialName("reference_no")
    val referenceNo: String? = null,
    @SerialName("redirect_url")
    val redirectUrl: RedirectUrl? = null,
    @SerialName("expires_at")
    val expiresAt: String? = null
)

@Serializable
data class SwiftPayPaymentLinkResponse(
    val id: String? = null,
    @SerialName("payment_link_url")
    val paymentLinkUrl: String? = null,
    @SerialName("short_code")
    val shortCode: String? = null,
    val status: String? = null,
    val description: String? = null,
    val amount: Double? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("expires_at")
    val expiresAt: String? = null
)

@Serializable
data class SwiftPayQrRequest(
    val amount: Double,
    @SerialName("reference_no")
    val referenceNo: String,
    val description: String? = null,
    val currency: String = "PHP"
)

@Serializable
data class SwiftPayQrResponse(
    val id: String? = null,
    @SerialName("payment_id")
    val paymentId: String? = null,
    val checkoutUrl: String? = null,
    @SerialName("qr_code")
    val qrCode: String? = null,
    @SerialName("qr_code_url")
    val qrCodeUrl: String? = null,
    val status: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val qrCodeBody: String? = null,
    val qrImageBase64: String? = null,
    val bankAccountNumber: String? = null,
    val instapayBankCode: String? = null
)

@Serializable
data class WalletBalanceResponse(
    @SerialName("wallet_id")
    val walletId: Long? = null,
    val balance: Double? = null,
    @SerialName("available_balance")
    val availableBalance: Double? = 0.0,
    @SerialName("pending_balance")
    val pendingBalance: Double? = 0.0,
    val currency: String? = "PHP"
)

@Serializable
data class WalletTransaction(
    val id: String? = null,
    @SerialName("reference_no")
    val referenceNo: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val status: String? = null,
    val type: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val description: String? = null
)

@Serializable
data class SwiftPayErrorResponse(
    val code: String? = null,
    val message: String? = null,
    val detail: String? = null,
    val errors: List<SwiftPayErrorDetail>? = null
)

@Serializable
data class SwiftPayErrorDetail(
    val code: String? = null,
    val message: String? = null,
    val field: String? = null
)
