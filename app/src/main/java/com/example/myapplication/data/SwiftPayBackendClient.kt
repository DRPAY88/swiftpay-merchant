package com.example.myapplication.data

import com.example.myapplication.BuildConfig
import com.example.myapplication.bridge.PaymentData
import com.example.myapplication.data.api.BackendDisbursementRecord
import com.example.myapplication.data.api.BackendDisbursementRequest
import com.example.myapplication.data.api.CryptoWidgetRequest
import com.example.myapplication.data.api.CryptoWidgetResponse
import com.example.myapplication.data.api.BackendOrderRequest
import com.example.myapplication.data.api.BackendPushTokenRequest
import com.example.myapplication.data.api.BackendPaymentLinkRequest
import com.example.myapplication.data.api.BackendQrPaymentRequest
import com.example.myapplication.data.api.BackendWalletTopupRequest
import com.example.myapplication.data.api.BackendWalletKrwTopupRequest
import com.example.myapplication.data.api.BackendWalletActivityTransaction
import com.example.myapplication.data.api.BackendKrwPhpQrQuoteRequest
import com.example.myapplication.data.api.BackendKrwPhpQrQuoteResponse
import com.example.myapplication.data.api.BackendFxPhpQrQuoteRequest
import com.example.myapplication.data.api.BackendFxPhpQrQuoteResponse
import com.example.myapplication.data.api.BackendQuotedQrPaymentRequest
import com.example.myapplication.data.api.BackendRecipientInformation
import com.example.myapplication.data.api.BackendTransferInstitution
import com.example.myapplication.data.api.BackendPaymentInstitutionLogo
import com.example.myapplication.data.api.BackendWalletBalanceResponse
import com.example.myapplication.data.api.BackendManualBankDeposit
import com.example.myapplication.data.api.BackendKrwReceiptBalanceResponse
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

private const val HISTORY_PAGE_SIZE = 200
private const val DISBURSEMENT_PAGE_SIZE = 100
private const val MAX_HISTORY_PAGES = 100_000

data class WalletTransactionHistory(
    val transactions: List<InternalTransaction>,
    val errors: List<String>
)

internal fun hasCompleteRecipientName(value: String): Boolean =
    value.trim().split(Regex("\\s+")).count { it.isNotBlank() } >= 2

private fun BackendKrwPhpQrQuoteResponse.matchesAmount(amountKrw: Int): Boolean =
    customerAmountKrw == amountKrw &&
        settlementCurrency == "PHP" &&
        settlementAmountPhp.isFinite() &&
        settlementAmountPhp > 0.0 &&
        ratePhpPerKrw.isFinite() &&
        ratePhpPerKrw > 0.0 &&
        quoteToken.isNotBlank()

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
                accessToken?.takeIf { it.isNotBlank() }?.let { header("Authorization", bearerAuthorization(it)) }
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
        IllegalStateException("Sign in with an authorized account to continue")
    )

    private fun <T> failure(response: Response<T>) = Result.failure<Nothing>(
        IllegalStateException(
            if (response.code() == 401) {
                "Your session has expired. Sign out and sign in again to restore wallet and transfer access."
            } else {
                runCatching {
                    val rawBody = response.errorBody()?.string().orEmpty()
                    val jsonObj = json.parseToJsonElement(rawBody).jsonObject
                    val errorCode = jsonObj["errorCode"]?.jsonPrimitive?.contentOrNull
                    val baseMessage = if (response.code() == 400 && errorCode == "INVALID_INPUT") {
                        "SwiftPay rejected the transfer details. Confirm the selected InstaPay institution matches the recipient's account, then verify the account number and recipient's exact legal name. Contact SwiftPay with the support reference if those details are correct."
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

    suspend fun registerPushToken(token: String): Result<Unit> = updatePushToken(token, remove = false)

    suspend fun removePushToken(token: String): Result<Unit> = updatePushToken(token, remove = true)

    private suspend fun updatePushToken(token: String, remove: Boolean): Result<Unit> {
        if (!hasApiKey) return missingSession()
        if (token.length !in 20..4096 || token.any(Char::isWhitespace)) {
            return Result.failure(IllegalArgumentException("Invalid Firebase push token"))
        }
        return try {
            val request = BackendPushTokenRequest(token)
            val response = if (remove) api.deletePushToken(request) else api.registerPushToken(request)
            if (response.isSuccessful) Result.success(Unit) else failure(response)
        } catch (error: CancellationException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

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
            if (response.code() != 404) return failure(response)
            val transferResponse = api.getDisbursement(paymentId)
            if (transferResponse.isSuccessful) {
                val transfer = transferResponse.body()
                    ?: return Result.failure(IllegalStateException("Empty transfer status response"))
                Result.success(SwiftPayStatusResponse(id = transfer.id ?: paymentId, status = transfer.status))
            } else failure(transferResponse)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun createPaymentLink(
        amount: Double,
        description: String? = null,
        merchantAliasOverride: String? = null,
        currency: String = "PHP"
    ): Result<SwiftPayPaymentLinkResponse> {
        if (!hasApiKey) return missingSession()
        if (!amount.isFinite() || amount <= 0.0) {
            return Result.failure(IllegalArgumentException("SwiftPay payment links require a valid fixed amount"))
        }
        val normalizedCurrency = currency.uppercase()
        if (normalizedCurrency !in setOf("PHP", "USD", "EUR")) {
            return Result.failure(IllegalArgumentException("SwiftPay payment links support PHP, USD, and EUR"))
        }
        return try {
            val response = api.createPaymentLink(
                BackendPaymentLinkRequest(
                    amount = amount,
                    currency = normalizedCurrency,
                    title = description?.take(100),
                    description = description?.take(500),
                    referenceNo = "LINK-${UUID.randomUUID()}"
                )
            )
            if (!response.isSuccessful) return failure(response)
            val body = response.body() ?: return Result.failure(IllegalStateException("Empty payment-link response"))
            if (body.currency != null && body.currency != normalizedCurrency) {
                return Result.failure(IllegalStateException("SwiftPay returned a payment link in a different currency"))
            }
            Result.success(
                SwiftPayPaymentLinkResponse(
                    id = body.code,
                    paymentLinkUrl = body.paymentUrl,
                    status = body.linkStatus ?: body.status,
                    description = body.description,
                    amount = body.amount,
                    currency = body.currency ?: normalizedCurrency
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun createDynamicQr(amount: Double, currency: String = "PHP"): Result<SwiftPayQrResponse> {
        if (!hasApiKey) return missingSession()
        if (!amount.isFinite() || amount <= 0.0) return Result.failure(IllegalArgumentException("Enter a valid QR Ph amount"))
        if (currency != "PHP") {
            return Result.failure(IllegalArgumentException("SwiftPay QR Ph settles in PHP. Request a KRW, USD, EUR, or CNY-to-PHP quote first."))
        }
        return try {
            val refNo = "QR-${UUID.randomUUID()}"
            val response = api.createQrPayment(
                BackendQrPaymentRequest(
                    amount = amount,
                    referenceNo = refNo,
                    currency = currency
                )
            )
            if (!response.isSuccessful) return failure(response)
            val body = response.body() ?: return Result.failure(IllegalStateException("Empty QR Ph response"))
            Result.success(toSwiftPayQrResponse(body))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun getKrwPhpQrQuote(amountKrw: Int): Result<BackendKrwPhpQrQuoteResponse> {
        if (!hasApiKey) return missingSession()
        if (amountKrw !in 1..1_000_000) {
            return Result.failure(IllegalArgumentException("Enter a KRW price between ₩1 and ₩1,000,000."))
        }
        return try {
            val response = api.getKrwPhpQrQuote(BackendKrwPhpQrQuoteRequest(amountKrw))
            if (!response.isSuccessful) return failure(response)
            val quote = response.body()
                ?: return Result.failure(IllegalStateException("Empty KRW-to-PHP quote response"))
            if (!quote.matchesAmount(amountKrw)) {
                return Result.failure(IllegalStateException("Backend returned an invalid KRW-to-PHP quote"))
            }
            Result.success(quote)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun getWalletKrwPhpTopupQuote(amountKrw: Int): Result<BackendKrwPhpQrQuoteResponse> {
        if (!hasApiKey) return missingSession()
        if (amountKrw !in 1..1_000_000) {
            return Result.failure(IllegalArgumentException("Enter a KRW amount between ₩1 and ₩1,000,000."))
        }
        return try {
            val response = api.getWalletKrwPhpTopupQuote(BackendKrwPhpQrQuoteRequest(amountKrw))
            if (!response.isSuccessful) return failure(response)
            val quote = response.body()
                ?: return Result.failure(IllegalStateException("Empty personal-wallet KRW quote response"))
            if (!quote.matchesAmount(amountKrw)) {
                return Result.failure(IllegalStateException("Backend returned an invalid personal-wallet KRW quote"))
            }
            Result.success(quote)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun createWalletKrwTopupQr(
        quote: BackendKrwPhpQrQuoteResponse,
        paymentApp: String
    ): Result<SwiftPayQrResponse> {
        if (!hasApiKey) return missingSession()
        if (quote.quoteToken.isBlank() || paymentApp !in setOf("TOSS", "KAKAO", "KBANK")) {
            return Result.failure(IllegalArgumentException("A valid KRW quote and Korean payment app are required."))
        }
        return try {
            val response = api.createWalletKrwTopupQr(
                BackendWalletKrwTopupRequest(quote.quoteToken, paymentApp)
            )
            if (!response.isSuccessful) return failure(response)
            val body = response.body()
                ?: return Result.failure(IllegalStateException("Empty personal-wallet KRW QR response"))
            if (
                body.currency != "PHP" ||
                body.referenceNo.isBlank() ||
                body.customerAmountKrw != quote.customerAmountKrw ||
                body.settlementCurrency != "PHP" ||
                !body.amount.isFinite() ||
                kotlin.math.round(body.amount * 100) != kotlin.math.round(quote.settlementAmountPhp * 100)
            ) {
                return Result.failure(IllegalStateException("Backend returned a QR payment that does not match the KRW quote"))
            }
            Result.success(toSwiftPayQrResponse(body))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun getFxPhpQrQuote(amount: Double, currency: String): Result<BackendFxPhpQrQuoteResponse> {
        if (!hasApiKey) return missingSession()
        if (!amount.isFinite() || amount <= 0.0 || amount > 1_000_000.0) {
            return Result.failure(IllegalArgumentException("Enter a valid customer amount."))
        }
        if (currency !in setOf("KRW", "USD", "EUR", "CNY")) {
            return Result.failure(IllegalArgumentException("QR Ph quotes support KRW, USD, EUR, and CNY customer prices."))
        }
        if (currency == "KRW" && amount % 1.0 != 0.0) {
            return Result.failure(IllegalArgumentException("KRW prices must be whole numbers."))
        }
        return try {
            val response = api.getFxPhpQrQuote(BackendFxPhpQrQuoteRequest(amount, currency))
            if (!response.isSuccessful) return failure(response)
            val quote = response.body()
                ?: return Result.failure(IllegalStateException("Empty FX-to-PHP quote response"))
            if (
                quote.customerAmount != amount ||
                quote.customerCurrency != currency ||
                quote.settlementCurrency != "PHP" ||
                !quote.settlementAmountPhp.isFinite() ||
                quote.settlementAmountPhp <= 0.0 ||
                !quote.ratePhpPerUnit.isFinite() ||
                quote.ratePhpPerUnit <= 0.0 ||
                quote.quoteToken.isBlank()
            ) {
                return Result.failure(IllegalStateException("Backend returned an invalid FX-to-PHP quote"))
            }
            Result.success(quote)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun createKrwPhpQuotedQr(quoteToken: String): Result<SwiftPayQrResponse> {
        if (!hasApiKey) return missingSession()
        if (quoteToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Request a new KRW-to-PHP quote before creating the QR."))
        }
        return try {
            val response = api.createQuotedKrwPhpQr(BackendQuotedQrPaymentRequest(quoteToken))
            if (!response.isSuccessful) return failure(response)
            val body = response.body()
                ?: return Result.failure(IllegalStateException("Empty PHP QR response for KRW quote"))
            if (body.currency != "PHP" || body.settlementCurrency != "PHP" || body.customerAmountKrw == null) {
                return Result.failure(IllegalStateException("Backend returned a QR that does not match the KRW-to-PHP quote"))
            }
            Result.success(toSwiftPayQrResponse(body))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun createFxPhpQuotedQr(quoteToken: String): Result<SwiftPayQrResponse> {
        if (!hasApiKey) return missingSession()
        if (quoteToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Request a new currency quote before creating the QR."))
        }
        return try {
            val response = api.createQuotedKrwPhpQr(BackendQuotedQrPaymentRequest(quoteToken))
            if (!response.isSuccessful) return failure(response)
            val body = response.body()
                ?: return Result.failure(IllegalStateException("Empty PHP QR response for FX quote"))
            if (
                body.currency != "PHP" ||
                body.settlementCurrency != "PHP" ||
                body.customerAmount == null ||
                body.customerCurrency?.let { it in setOf("KRW", "USD", "EUR", "CNY") } != true
            ) {
                return Result.failure(IllegalStateException("Backend returned a QR that does not match the FX-to-PHP quote"))
            }
            Result.success(toSwiftPayQrResponse(body))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun toSwiftPayQrResponse(body: com.example.myapplication.data.api.BackendQrPaymentResponse): SwiftPayQrResponse {
        val qrCodeValue = body.qrCode?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("SwiftPay did not return a QR Ph payload")
        val qrImageBase64 = body.qrImageBase64?.takeIf { it.length > 200 }
            ?: com.example.myapplication.util.generateQrBitmapBase64(qrCodeValue)
        return SwiftPayQrResponse(
            id = body.paymentId,
            paymentId = body.paymentId,
            qrCode = qrCodeValue,
            checkoutUrl = null,
            status = body.status,
            amount = body.amount,
            currency = body.currency,
            qrCodeBody = qrCodeValue,
            qrImageBase64 = qrImageBase64,
            referenceNo = body.referenceNo,
            bankAccountNumber = body.bankAccountNumber,
            instapayBankCode = body.instapayBankCode,
            customerAmountKrw = body.customerAmountKrw,
            ratePhpPerKrw = body.ratePhpPerKrw,
            customerAmount = body.customerAmount,
            customerCurrency = body.customerCurrency,
            ratePhpPerUnit = body.ratePhpPerUnit,
            rateUpdatedAt = body.rateUpdatedAt,
            settlementCurrency = body.settlementCurrency
        )
    }

    suspend fun createWalletTopupQr(amount: Double): Result<SwiftPayQrResponse> {
        if (!hasApiKey) return missingSession()
        if (!amount.isFinite() || amount !in 1.0..100_000.0) {
            return Result.failure(IllegalArgumentException("Enter a top-up amount between PHP 1 and PHP 100,000."))
        }
        return try {
            val response = api.createWalletTopupQr(BackendWalletTopupRequest(amount))
            if (!response.isSuccessful) return failure(response)
            val body = response.body()
                ?: return Result.failure(IllegalStateException("Empty wallet top-up QR response"))
            if (
                body.currency != "PHP" ||
                body.referenceNo.isBlank() ||
                kotlin.math.round(body.amount * 100) != kotlin.math.round(amount * 100)
            ) {
                return Result.failure(IllegalStateException("Backend returned a top-up QR that does not match the requested PHP amount"))
            }
            Result.success(toSwiftPayQrResponse(body))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun getWalletTopupStatus(referenceNo: String): Result<String> {
        if (!hasApiKey) return missingSession()
        if (referenceNo.isBlank()) return Result.failure(IllegalArgumentException("A wallet top-up reference is required."))
        return try {
            val response = api.getWalletTopupStatus(referenceNo)
            if (!response.isSuccessful) return failure(response)
            val body = response.body()
                ?: return Result.failure(IllegalStateException("Empty wallet top-up status response"))
            Result.success(body.status)
        } catch (error: CancellationException) {
            throw error
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

    suspend fun getKrwReceiptBalance(): Result<BackendKrwReceiptBalanceResponse> {
        if (!hasApiKey) return missingSession()
        return try {
            val response = api.getKrwReceiptBalance()
            if (!response.isSuccessful) return failure(response)
            val body = response.body() ?: return Result.failure(IllegalStateException("Empty KRW receipt balance response"))
            if (body.currency != "KRW" || !body.balance.isFinite() || body.balance < 0.0) {
                return Result.failure(IllegalStateException("Backend returned an invalid KRW receipt balance"))
            }
            Result.success(body)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

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
            val transactions = mutableListOf<InternalTransaction>()
            var pageNo = 0
            while (true) {
                val response = api.getDisbursements(pageNo, DISBURSEMENT_PAGE_SIZE)
                if (!response.isSuccessful) return failure(response)
                val page = response.body()
                    ?: return Result.failure(IllegalStateException("Empty disbursement history response"))
                transactions += page.mapNotNull { it.toInternalTransaction() }
                if (page.size < DISBURSEMENT_PAGE_SIZE) break
                pageNo++
                if (pageNo > MAX_HISTORY_PAGES) {
                    return Result.failure(IllegalStateException("Disbursement history exceeded the paging limit"))
                }
            }
            Result.success(transactions)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun getManualBankDepositTransactions(): Result<List<InternalTransaction>> {
        if (!hasApiKey) return missingSession()
        return getAllOffsetPages { limit, offset ->
            api.getManualBankDeposits(limit, offset)
        }.map { deposits ->
            deposits.map { deposit ->
                InternalTransaction(
                    transactionId = deposit.id,
                    amount = deposit.amount,
                    status = "SUCCESS",
                    date = deposit.createdAt,
                    transactionType = "MANUAL_BANK_DEPOSIT",
                    referenceNo = deposit.referenceNo,
                    description = deposit.notes,
                    bankName = deposit.bankName
                )
            }
        }
    }

    suspend fun getPaymentTransactions(): Result<List<InternalTransaction>> {
        if (!hasApiKey) return missingSession()
        return getAllOffsetPages { limit, offset ->
            api.getPaymentTransactions(limit, offset)
        }.mapCatching { payments ->
            if (payments.any {
                    it.currency != "PHP" || !it.amount.isFinite() || it.amount <= 0.0
                }
            ) {
                throw IllegalStateException("Backend returned an invalid PHP payment transaction")
            }
            payments.map { payment ->
                InternalTransaction(
                    transactionId = payment.id,
                    amount = payment.amount,
                    status = payment.status.toTransactionStatus(),
                    date = payment.createdAt,
                    transactionType = "PAYMENT",
                    referenceNo = payment.referenceNo,
                    bankName = "SwiftPay"
                )
            }
        }
    }

    private suspend fun <T> getAllOffsetPages(
        fetchPage: suspend (limit: Int, offset: Int) -> Response<List<T>>
    ): Result<List<T>> {
        return try {
            val results = mutableListOf<T>()
            var offset = 0
            while (true) {
                val response = fetchPage(HISTORY_PAGE_SIZE, offset)
                if (!response.isSuccessful) return failure(response).map { emptyList<T>() }
                val page = response.body()
                    ?: return Result.failure(IllegalStateException("Empty transaction history response"))
                results += page
                if (page.size < HISTORY_PAGE_SIZE) break
                offset += page.size
                if (offset / HISTORY_PAGE_SIZE >= MAX_HISTORY_PAGES) {
                    return Result.failure(IllegalStateException("Transaction history exceeded the paging limit"))
                }
            }
            Result.success(results)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun getWalletTransactionHistory(): WalletTransactionHistory {
        val activity = getAllOffsetPages { limit, offset ->
            api.getWalletActivity(limit, offset)
        }.map { entries ->
            entries.map { it.toInternalTransaction() }
        }
        return WalletTransactionHistory(
            transactions = activity.getOrNull().orEmpty(),
            errors = listOfNotNull(activity.exceptionOrNull()?.message)
        )
    }

    private fun BackendWalletActivityTransaction.toInternalTransaction() = InternalTransaction(
        transactionId = id,
        amount = amount,
        status = status.toTransactionStatus(),
        date = createdAt,
        transactionType = transactionType,
        referenceNo = referenceNo,
        description = description
    )

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

    suspend fun getPaymentInstitutionLogos(): Result<List<BackendPaymentInstitutionLogo>> {
        if (!hasApiKey) return missingSession()
        return try {
            val response = api.getPaymentInstitutions()
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
        val trimmedAccount = accountNumber.trim()
        if (trimmedAccount.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter the recipient's account or mobile number"))
        }
        val trimmedName = recipientName.trim()
        if (!hasCompleteRecipientName(trimmedName)) {
            return Result.failure(
                IllegalArgumentException("Enter the recipient's complete legal name, including their surname")
            )
        }
        return try {
            val reference = "DISB-${UUID.randomUUID().toString().take(20)}"
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
                    outcomeUnknown = body.outcomeUnknown,
                    message = body.message,
                    referenceNo = body.referenceNo ?: reference
                )
            )
        } catch (error: CancellationException) {
            throw error
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
            date = registrationTime ?: createdOn ?: createdAt ?: "",
            transactionType = "DISBURSEMENT",
            referenceNo = merchantReferenceNo
        )
    }
}

private fun String.toTransactionStatus(): String = when (uppercase()) {
    "EXECUTED", "SUCCESS", "COMPLETED" -> "SUCCESS"
    "REJECTED", "ERROR", "FAILED", "CANCELED", "CANCELLED", "EXPIRED" -> "FAILED"
    else -> "PENDING"
}