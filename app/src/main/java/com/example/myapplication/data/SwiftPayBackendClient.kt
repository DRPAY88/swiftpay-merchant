package com.example.myapplication.data

import com.example.myapplication.BuildConfig
import com.example.myapplication.bridge.PaymentData
import com.example.myapplication.data.api.BackendDisbursementRecord
import com.example.myapplication.data.api.BackendDisbursementRequest
import com.example.myapplication.data.api.CryptoWidgetRequest
import com.example.myapplication.data.api.CryptoWidgetResponse
import com.example.myapplication.data.api.BackendOrderRequest
import com.example.myapplication.data.api.BackendPaymentLinkRequest
import com.example.myapplication.data.api.BackendQrPaymentRequest
import com.example.myapplication.data.api.BackendRecipientInformation
import com.example.myapplication.data.api.BackendTransferInstitution
import com.example.myapplication.data.api.BackendWalletBalanceResponse
import com.example.myapplication.data.api.BackendWalletDepositAccount
import com.example.myapplication.data.api.CardDetails
import com.example.myapplication.data.api.InternalTransaction
import com.example.myapplication.data.api.PaymentChannel
import com.example.myapplication.data.api.SwiftPayBackendApi
import com.example.myapplication.data.api.SwiftPayDisbursementResponse
import com.example.myapplication.data.api.SwiftPayOrderResponse
import com.example.myapplication.data.api.SwiftPayPaymentLinkResponse
import com.example.myapplication.data.api.SwiftPayQrResponse
import com.example.myapplication.data.api.SwiftPayStatusResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

class SwiftPayService(
    private val backendBaseUrl: String = BuildConfig.PAYMENT_BACKEND_URL,
    private val accessToken: String? = null
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = resolveBackendBaseUrl(backendBaseUrl)

    val hasApiKey: Boolean = !accessToken.isNullOrBlank()

    private val api: SwiftPayBackendApi by lazy {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder().apply {
                accessToken?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
            }.build()
            chain.proceed(request)
        }
        val client = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .build()
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SwiftPayBackendApi::class.java)
    }

    private fun missingSession() = Result.failure<Nothing>(
        IllegalStateException("Sign in with an authorized merchant account to continue")
    )

    private fun <T> failure(response: Response<T>) = Result.failure<Nothing>(
        IllegalStateException(
            if (response.code() == 401) {
                "Your merchant session has expired. Sign out and sign in again to restore wallet and transfer access."
            } else {
                runCatching {
                    val rawBody = response.errorBody()?.string().orEmpty()
                    val jsonObj = json.parseToJsonElement(rawBody).jsonObject
                    val errorCode = jsonObj["errorCode"]?.jsonPrimitive?.contentOrNull
                    val baseMessage = if (response.code() == 400 && errorCode == "INVALID_INPUT") {
                        "SwiftPay rejected the transfer details. Verify the account number and recipient's full legal name."
                    } else {
                        jsonObj["error"]?.jsonPrimitive?.contentOrNull
                        ?: jsonObj["message"]?.jsonPrimitive?.contentOrNull
                        ?: rawBody
                    }
                    val supportReference = jsonObj["supportReference"]?.jsonPrimitive?.contentOrNull
                    if (supportReference.isNullOrBlank()) baseMessage else "$baseMessage (Reference: $supportReference)"
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Backend request failed (${response.code()})"
            }
        )
    )

    suspend fun sendCustomOtp(email: String, code: String): Result<Boolean> =
        Result.failure(UnsupportedOperationException("Use BackendAuthService to request sign-in codes"))

    suspend fun onlineLogin(email: String, password: String): Result<Boolean> =
        Result.failure(UnsupportedOperationException("Password login is not supported"))

    suspend fun verifyOnlineCode(email: String, code: String): Result<Boolean> =
        Result.failure(UnsupportedOperationException("Use BackendAuthService to verify sign-in codes"))

    suspend fun createCheckout(data: PaymentData): Result<SwiftPayOrderResponse> {
        if (!hasApiKey) return missingSession()
        if (data.amount <= 0.0 || data.currency != "PHP") {
            return Result.failure(IllegalArgumentException("Enter a valid PHP amount"))
        }
        return try {
            val response = api.createOrder(
                BackendOrderRequest(
                    amount = data.amount,
                    referenceNo = "APP-${UUID.randomUUID()}",
                    currency = "PHP",
                    details = mapOf("description" to data.description.take(500))
                )
            )
            if (!response.isSuccessful) return failure(response)
            val body = response.body() ?: return Result.failure(IllegalStateException("Empty order response"))
            Result.success(
                SwiftPayOrderResponse(
                    id = body.paymentId,
                    orderId = body.paymentId,
                    checkoutUrl = body.redirectUrl,
                    status = body.status,
                    amount = data.amount,
                    currency = "PHP",
                    referenceNo = body.referenceNo
                )
            )
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun getCheckoutStatus(checkoutId: String): Result<SwiftPayStatusResponse> = getPaymentStatus(checkoutId)

    suspend fun getPaymentStatus(paymentId: String): Result<SwiftPayStatusResponse> {
        if (!hasApiKey) return missingSession()
        return try {
            val response = api.getPaymentStatus(paymentId)
            if (response.isSuccessful) {
                val body = response.body() ?: return Result.failure(IllegalStateException("Empty payment status"))
                return Result.success(SwiftPayStatusResponse(id = body.paymentId ?: paymentId, status = body.status))
            }
            val transferResponse = api.getDisbursement(paymentId)
            if (transferResponse.isSuccessful) {
                val transfer = transferResponse.body()
                Result.success(SwiftPayStatusResponse(id = transfer?.id ?: paymentId, status = transfer?.status))
            } else failure(response)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun createPaymentLink(
        amount: Double,
        description: String? = null,
        merchantAliasOverride: String? = null
    ): Result<SwiftPayPaymentLinkResponse> {
        if (!hasApiKey) return missingSession()
        if (amount <= 0.0) return Result.failure(IllegalArgumentException("SwiftPay payment links require a fixed amount"))
        return try {
            val response = api.createPaymentLink(
                BackendPaymentLinkRequest(
                    amount = amount,
                    currency = "PHP",
                    title = description?.take(100),
                    description = description?.take(500),
                    referenceNo = "LINK-${UUID.randomUUID()}"
                )
            )
            if (!response.isSuccessful) return failure(response)
            val body = response.body() ?: return Result.failure(IllegalStateException("Empty payment-link response"))
            Result.success(
                SwiftPayPaymentLinkResponse(
                    id = body.code,
                    paymentLinkUrl = body.paymentUrl,
                    status = body.linkStatus ?: body.status,
                    description = body.description,
                    amount = body.amount
                )
            )
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun createDynamicQr(amount: Double): Result<SwiftPayQrResponse> {
        if (!hasApiKey) return missingSession()
        if (!amount.isFinite() || amount <= 0.0) return Result.failure(IllegalArgumentException("Enter a valid QR Ph amount"))
        return try {
            val refNo = "QR-${UUID.randomUUID()}"
            val response = api.createQrPayment(
                BackendQrPaymentRequest(
                    amount = amount,
                    referenceNo = refNo
                )
            )
            if (!response.isSuccessful) return failure(response)
            val body = response.body() ?: return Result.failure(IllegalStateException("Empty QR Ph response"))

            val qrCodeValue = body.qrCode?.takeIf { it.isNotBlank() }
                ?: return Result.failure(IllegalStateException("SwiftPay did not return a QR Ph payload"))
            val qrImageBase64 = body.qrImageBase64?.takeIf { it.length > 200 }
                ?: com.example.myapplication.util.generateQrBitmapBase64(qrCodeValue)

            Result.success(
                SwiftPayQrResponse(
                    id = body.paymentId,
                    paymentId = body.paymentId,
                    qrCode = qrCodeValue,
                    checkoutUrl = null,
                    status = body.status,
                    amount = body.amount,
                    currency = body.currency ?: "PHP",
                    qrCodeBody = qrCodeValue,
                    qrImageBase64 = qrImageBase64,
                    bankAccountNumber = body.bankAccountNumber,
                    instapayBankCode = body.instapayBankCode
                )
            )
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun getWalletBalanceDetails(): Result<BackendWalletBalanceResponse> {
        if (!hasApiKey) return missingSession()
        return try {
            val response = api.getWalletBalance()
            if (!response.isSuccessful) return failure(response)
            val body = response.body() ?: return Result.failure(IllegalStateException("Empty wallet balance response"))
            Result.success(body)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun getWalletBalance(): Result<Double> =
        getWalletBalanceDetails().map { it.balance }

    suspend fun createCryptoWidgetUrl(
        mode: String,
        amount: Double,
        walletAddress: String? = null
    ): Result<CryptoWidgetResponse> {
        if (!hasApiKey) return missingSession()
        if (mode !in setOf("BUY", "SELL")) {
            return Result.failure(IllegalArgumentException("Choose buy or sell USDT."))
        }
        if (!amount.isFinite() || amount <= 0.0) {
            return Result.failure(IllegalArgumentException("Enter a valid amount."))
        }
        if (mode == "BUY" && walletAddress.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Enter your TRON wallet address."))
        }
        return try {
            val response = api.createCryptoWidgetUrl(CryptoWidgetRequest(mode, amount, walletAddress?.trim()))
            if (!response.isSuccessful) return failure(response)
            val body = response.body() ?: return Result.failure(IllegalStateException("Empty Transak checkout response"))
            val widgetUrl = java.net.URI(body.widgetUrl)
            if (widgetUrl.scheme != "https" || widgetUrl.host !in setOf("global-stg.transak.com", "global.transak.com")) {
                return Result.failure(IllegalStateException("Transak returned an invalid checkout URL"))
            }
            Result.success(body)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun getWalletDepositAccount(): Result<BackendWalletDepositAccount> =
        getWalletBalanceDetails().mapCatching { body ->
            val accountNumber = body.accountNumber?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("SwiftPay did not return the wallet's inbound deposit account number")
            BackendWalletDepositAccount(
                accountNumber = accountNumber,
                balance = body.balance
            )
        }

    suspend fun getInternalTransactions(): Result<List<InternalTransaction>> {
        if (!hasApiKey) return missingSession()
        return try {
            val response = api.getDisbursements()
            if (!response.isSuccessful) return failure(response)
            Result.success(response.body().orEmpty().mapNotNull { it.toInternalTransaction() })
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun getTransferInstitutions(): Result<List<BackendTransferInstitution>> {
        if (!hasApiKey) return missingSession()
        return try {
            val response = api.getDisbursementInstitutions()
            if (!response.isSuccessful) return failure(response)
            Result.success(response.body().orEmpty())
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    fun getTransactionUpdates(): Flow<List<InternalTransaction>> = flow {
        while (true) {
            emit(getInternalTransactions().getOrDefault(emptyList()))
            delay(10_000)
        }
    }

    fun getPaymentChannels(): List<PaymentChannel> = emptyList()

    suspend fun processVaultPayment(
        amount: Double,
        cardDetails: CardDetails,
        notes: String? = null
    ): Result<SwiftPayStatusResponse> = Result.failure(
        UnsupportedOperationException("Direct card vault processing is not supported by the published SwiftPay API")
    )

    suspend fun disburse(
        amount: Double,
        bankCode: String,
        accountNumber: String,
        recipientName: String,
        transferType: String? = null
    ): Result<SwiftPayDisbursementResponse> {
        if (!hasApiKey) return missingSession()
        if (!amount.isFinite() || amount <= 0.0) return Result.failure(IllegalArgumentException("Enter a valid transfer amount"))
        return try {
            val reference = "DISB-${UUID.randomUUID().toString().take(20)}"
            val trimmedAccount = accountNumber.trim()
            val trimmedName = recipientName.trim().ifBlank { "Recipient" }
            val trimmedBank = bankCode.trim().take(64)

            val response = api.sendDisbursement(
                BackendDisbursementRequest(
                    amount = amount,
                    referenceNo = reference,
                    channel = "INSTAPAY",
                    institutionCode = trimmedBank,
                    type = transferType ?: "P2P",
                    recipientInformation = BackendRecipientInformation(
                        accountNumber = trimmedAccount,
                        fullName = trimmedName
                    )
                )
            )
            if (!response.isSuccessful) return failure(response)
            val body = response.body() ?: return Result.failure(IllegalStateException("Empty transfer response"))
            Result.success(
                SwiftPayDisbursementResponse(
                    id = body.id,
                    disbursementId = body.id,
                    status = body.status,
                    amount = amount,
                    currency = "PHP",
                    referenceNo = body.referenceNo ?: reference
                )
            )
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun getDisbursementTransactions(): Result<List<SwiftPayDisbursementResponse>> =
        getInternalTransactions().map { transactions ->
            transactions.map { transaction ->
                SwiftPayDisbursementResponse(
                    id = transaction.transactionId,
                    disbursementId = transaction.transactionId,
                    status = transaction.status,
                    amount = kotlin.math.abs(transaction.amount),
                    currency = "PHP",
                    referenceNo = transaction.transactionId
                )
            }
        }

    private fun BackendDisbursementRecord.toInternalTransaction(): InternalTransaction? {
        val transactionId = id ?: return null
        val transactionAmount = creditInformation?.amount ?: amount ?: return null
        val normalizedStatus = when (status?.uppercase()) {
            "EXECUTED", "SUCCESS", "COMPLETED" -> "SUCCESS"
            "REJECTED", "ERROR", "FAILED" -> "FAILED"
            else -> "PENDING"
        }
        return InternalTransaction(
            transactionId = transactionId,
            amount = -transactionAmount,
            status = normalizedStatus,
            date = registrationTime ?: createdOn ?: createdAt ?: ""
        )
    }
}