package com.example.myapplication.data.api

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class BackendOrderRequest(
    val amount: Double,
    val referenceNo: String,
    val currency: String = "PHP",
    val details: Map<String, String> = emptyMap()
)

@Serializable
data class BackendQrPaymentRequest(
    val amount: Double,
    val referenceNo: String,
    val currency: String = "PHP",
    val rejectDuplicatedPayments: Boolean = true
)

@Serializable
data class BackendQrPaymentResponse(
    val paymentId: String,
    val referenceNo: String,
    val status: String,
    val amount: Double,
    val currency: String,
    val qrCode: String? = null,
    val qrImageBase64: String? = null,
    val checkoutUrl: String? = null,
    val bankAccountNumber: String? = null,
    val instapayBankCode: String? = null
)

@Serializable
data class CryptoWidgetRequest(
    val mode: String,
    val amount: Double,
    val walletAddress: String? = null
)

@Serializable
data class CryptoWidgetResponse(
    val widgetUrl: String,
    val partnerOrderId: String
)

@Serializable
data class BackendOrderResponse(
    val paymentId: String,
    val referenceNo: String,
    val status: String,
    val redirectUrl: String
)

@Serializable
data class BackendStatusResponse(
    val paymentId: String? = null,
    val status: String? = null
)

@Serializable
data class BackendWalletBalanceResponse(
    val balance: Double,
    val adjustedBalance: Double? = null,
    val accountNumber: String? = null
)

@Serializable
data class BackendKrwReceiptBalanceResponse(
    val currency: String = "KRW",
    val balance: Double,
    val paymentCount: Int
)

data class BackendWalletDepositAccount(
    val accountNumber: String,
    val balance: Double
)

@Serializable
data class BackendPaymentLinkRequest(
    val amount: Double,
    val currency: String = "PHP",
    val title: String? = null,
    val description: String? = null,
    val referenceNo: String? = null
)

@Serializable
data class BackendPaymentLinkResponse(
    val id: String? = null,
    val code: String,
    val paymentUrl: String,
    val linkStatus: String? = null,
    val status: String? = null,
    val amount: Double? = null,
    val description: String? = null
)

@Serializable
data class BackendDisbursementRequest(
    val amount: Double,
    val referenceNo: String,
    val channel: String,
    val type: String? = null,
    val institutionCode: String,
    val recipientInformation: BackendRecipientInformation,
    val remarks: String? = null
)

@Serializable
data class BackendRecipientInformation(
    val accountNumber: String,
    val fullName: String
)

@Serializable
data class BackendDisbursementResponse(
    val id: String,
    val referenceNo: String? = null,
    val status: String = "PENDING"
)

@Serializable
data class BackendDisbursementRecord(
    val id: String? = null,
    val merchantReferenceNo: String? = null,
    val status: String? = null,
    val amount: Double? = null,
    val registrationTime: String? = null,
    val createdOn: String? = null,
    val createdAt: String? = null,
    val creditInformation: BackendCreditInformation? = null
)

@Serializable
data class BackendCreditInformation(val amount: Double? = null)

@Serializable
data class BackendTransferInstitution(
    val code: String,
    val name: String,
    val instapayEnabled: Boolean = false,
    val pesonetEnabled: Boolean = false
)

interface SwiftPayBackendApi {
    @POST("v1/crypto/widget-url")
    suspend fun createCryptoWidgetUrl(@Body request: CryptoWidgetRequest): Response<CryptoWidgetResponse>

    @GET("v1/wallet/balance")
    suspend fun getWalletBalance(): Response<BackendWalletBalanceResponse>

    @GET("v1/wallet/krw-receipts")
    suspend fun getKrwReceiptBalance(): Response<BackendKrwReceiptBalanceResponse>

    @POST("v1/payments/orders")
    suspend fun createOrder(@Body request: BackendOrderRequest): Response<BackendOrderResponse>

    @POST("v1/payments/qrph")
    suspend fun createQrPayment(@Body request: BackendQrPaymentRequest): Response<BackendQrPaymentResponse>

    @GET("v1/payments/{paymentToken}/status")
    suspend fun getPaymentStatus(@Path("paymentToken") paymentToken: String): Response<BackendStatusResponse>

    @POST("v1/payment-links")
    suspend fun createPaymentLink(@Body request: BackendPaymentLinkRequest): Response<BackendPaymentLinkResponse>

    @GET("v1/payment-links/{code}")
    suspend fun getPaymentLink(@Path("code") code: String): Response<BackendPaymentLinkResponse>

    @DELETE("v1/payment-links/{code}")
    suspend fun invalidatePaymentLink(@Path("code") code: String): Response<Unit>

    @POST("v1/disbursements")
    suspend fun sendDisbursement(@Body request: BackendDisbursementRequest): Response<BackendDisbursementResponse>

    @GET("v1/disbursements")
    suspend fun getDisbursements(): Response<List<BackendDisbursementRecord>>

    @GET("v1/disbursement-institutions")
    suspend fun getDisbursementInstitutions(): Response<List<BackendTransferInstitution>>

    @GET("v1/disbursements/{id}")
    suspend fun getDisbursement(@Path("id") id: String): Response<BackendDisbursementRecord>
}