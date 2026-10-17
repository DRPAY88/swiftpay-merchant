package com.example.myapplication.ui.screens

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.bridge.PaymentData
import com.example.myapplication.bridge.SwiftPaySDKBridge
import com.example.myapplication.data.SettingsManager
import com.example.myapplication.data.SwiftPayService
import com.example.myapplication.data.TransactionStore
import com.example.myapplication.data.institutionBrandKey
import com.example.myapplication.data.isTrustedInstitutionLogoUrl
import com.example.myapplication.data.api.InternalTransaction
import com.example.myapplication.data.api.BackendTransferInstitution
import com.example.myapplication.data.api.BackendPaymentInstitutionLogo
import com.example.myapplication.data.api.SwiftPayStatusResponse
import com.example.myapplication.data.createSwiftPayService
import com.example.myapplication.data.mergeTransactions
import com.example.myapplication.util.parseScannedQrPayment
import com.example.myapplication.data.repository.PaymentRepository
import com.example.myapplication.data.repository.TransactionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

private fun normalizePaymentAppName(rawName: String?): String? =
    rawName
        ?.trim()
        ?.replace("\\s+".toRegex(), "")
        ?.uppercase(Locale.ROOT)
        ?.let { normalized ->
            when (normalized) {
                "ALIPAY", "TOSS", "KAKAO", "KBANK", "GCASH", "MAYA", "QRPH" -> normalized
                else -> null
            }
        }

sealed interface MiniAppUiState {
    object Idle : MiniAppUiState
    data class Processing(val message: String) : MiniAppUiState
    data class Error(val message: String) : MiniAppUiState
    data class PaymentConsent(val data: PaymentData) : MiniAppUiState
    data class PaymentRedirect(val url: String, val data: PaymentData) : MiniAppUiState
    data class PaymentLinkReady(
        val url: String,
        val linkId: String,
        val amount: Double,
        val description: String,
        val currency: String = "PHP"
    ) : MiniAppUiState
    data class DynamicQrReady(
        val qrImageBase64: String,
        val amount: Double,
        val currency: String,
        val bankAccountNumber: String?,
        val instapayBankCode: String?,
        val customerAmountKrw: Int? = null,
        val ratePhpPerKrw: Double? = null,
        val customerAmountFx: Double? = null,
        val customerCurrency: String? = null,
        val ratePhpPerUnit: Double? = null,
        val rateUpdatedAt: String? = null,
        val paymentAppName: String? = null
    ) : MiniAppUiState
    object ScanQrToSend : MiniAppUiState
    data class QrTransferReview(
        val suggestedAmount: Double?,
        val institutions: List<BackendTransferInstitution>
    ) : MiniAppUiState

    data class WaitingForNFC(
        val amount: Double,
        val merchantName: String = "Fast Pay Merchant",
        val merchantAddress: String = "Pila, Laguna, PHL",
        val timeLeft: Int = 90,
        val sessionDurationSeconds: Int = 90,
        val errorMessage: String? = null,
        val statusMessage: String? = null
    ) : MiniAppUiState

    data class WaitingForCVV(
        val amount: Double,
        val pan: String,
        val expiry: String,
        val label: String,
        val sessionRef: String
    ) : MiniAppUiState
}

class MiniAppViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(application)
    private val transactionStore = TransactionStore(application)
    private val repository = TransactionRepository(
        swiftPayService = SwiftPayService(),
        transactionStore = transactionStore
    )
    private val paymentRepository = PaymentRepository(SwiftPayService())

    var walletBalance by mutableStateOf<Double?>(null)
        private set

    var walletAdjustedBalance by mutableStateOf<Double?>(null)
        private set

    var walletBalanceError by mutableStateOf<String?>(null)
        private set

    var krwReceiptBalance by mutableStateOf<Double?>(null)
        private set

    var krwReceiptPaymentCount by mutableStateOf(0)
        private set

    var krwReceiptBalanceError by mutableStateOf<String?>(null)
        private set

    var isLoadingKrwReceiptBalance by mutableStateOf(false)
        private set

    var depositAccountNumber by mutableStateOf<String?>(null)
        private set

    var uiState by mutableStateOf<MiniAppUiState>(MiniAppUiState.Idle)
        private set

    var transactionStatusMap by mutableStateOf<Map<String, SwiftPayStatusResponse>>(emptyMap())
        private set

    val latestTransactionStatus: Map.Entry<String, SwiftPayStatusResponse>?
        get() = transactionStatusMap.entries.lastOrNull {
            val status = it.value.status?.uppercase()
            status != "PAYMENT_EXPIRED" && status != "EXPIRED"
        }

    var bridge: SwiftPaySDKBridge? = null
    private var nfcTimerJob: Job? = null
    private var threeDSecureTimeoutJob: Job? = null
    private var pendingCheckoutId: String? = null
        set(value) {
            field = value
            if (value != null) {
                viewModelScope.launch {
                    checkPendingPaymentStatus()
                }
            }
        }

    private suspend fun getService(): SwiftPayService {
        return settingsManager.createSwiftPayService()
    }

    private fun launchProcessingAction(
        message: String,
        bridgeAction: String? = null,
        action: suspend () -> Unit
    ) {
        viewModelScope.launch {
            uiState = MiniAppUiState.Processing(message)
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val errorMessage = error.message ?: "The request failed. Please try again."
                Log.e("MiniAppViewModel", message, error)
                bridge?.sendError(errorMessage, action = bridgeAction)
                uiState = if (bridgeAction == null) MiniAppUiState.Error(errorMessage) else MiniAppUiState.Idle
            }
        }
    }

    fun refreshBalance() {
        viewModelScope.launch {
            walletBalanceError = null
            try {
                val service = getService()
                service.getWalletBalanceDetails()
                    .onSuccess { details ->
                        walletBalance = details.balance
                        walletAdjustedBalance = details.adjustedBalance
                        walletBalanceError = null
                        val accountNumber = details.accountNumber?.trim()?.takeIf { it.isNotEmpty() }
                        depositAccountNumber = accountNumber
                        viewModelScope.launch {
                            if (accountNumber == null) {
                                settingsManager.clearDepositAccountNumber()
                            } else {
                                settingsManager.saveDepositAccountNumber(accountNumber)
                            }
                        }
                    }
                    .onFailure { error ->
                        walletBalanceError = error.message ?: "Could not load wallet balance."
                        Log.e("MiniAppViewModel", "Failed to refresh wallet balance", error)
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                walletBalanceError = error.message ?: "Could not load wallet balance."
                Log.e("MiniAppViewModel", "Failed to refresh wallet balance", error)
            }
        }
    }

    fun refreshKrwReceiptBalance() {
        viewModelScope.launch {
            isLoadingKrwReceiptBalance = true
            krwReceiptBalanceError = null
            try {
                getService().getKrwReceiptBalance()
                    .onSuccess { result ->
                        krwReceiptBalance = result.balance
                        krwReceiptPaymentCount = result.paymentCount
                    }
                    .onFailure { error ->
                        krwReceiptBalanceError = error.message ?: "Could not load executed KRW QR Ph receipts."
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                krwReceiptBalanceError = error.message ?: "Could not load executed KRW QR Ph receipts."
            } finally {
                isLoadingKrwReceiptBalance = false
            }
        }
    }

    fun requestPaymentConsent(data: PaymentData) {
        if (!data.amount.isFinite() || data.amount <= 0) {
            uiState = MiniAppUiState.Error("Invalid payment amount. Amount must be greater than ₱0.00")
            return
        }
        if (data.amount > 1_000_000) {
            uiState = MiniAppUiState.Error("Payment amount exceeds maximum limit of ₱1,000,000")
            return
        }
        uiState = MiniAppUiState.PaymentConsent(data)
    }

    fun onBalanceRequest() {
        viewModelScope.launch {
            try {
                getService().getWalletBalance()
                    .onSuccess { bridge?.sendResponse(it) }
                    .onFailure { error -> bridge?.sendError(error.message ?: "Wallet balance is unavailable") }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                bridge?.sendError(error.message ?: "Wallet balance is unavailable")
            }
        }
    }

    fun onTransactionsRequest() {
        viewModelScope.launch {
            try {
                val history = getService().getWalletTransactionHistory()
                val localTransactions = repository.getAllTransactions().first()
                history.errors.forEach { error ->
                    Log.e("MiniAppViewModel", "Failed to load a transaction history source: $error")
                }
                val transactions = mergeTransactions(localTransactions, history.transactions)
                if (transactions.isEmpty() && history.errors.isNotEmpty()) {
                    bridge?.sendError(history.errors.joinToString("; "))
                } else {
                    bridge?.sendResponse(transactions)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                bridge?.sendError(error.message ?: "Failed to fetch transactions")
            }
        }
    }

    fun onPaymentChannelsRequest() {
        viewModelScope.launch {
            try {
                bridge?.sendResponse(getService().getPaymentChannels())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                bridge?.sendError(error.message ?: "Payment channels are unavailable")
            }
        }
    }

    fun onPaymentLinkRequest(data: PaymentData) {
        launchProcessingAction("Generating payment link...") {
            val result = getService().createPaymentLink(
                amount = if (data.amount > 0) data.amount else 0.0,
                description = data.description,
                currency = data.currency.uppercase()
            )

            result.onSuccess { response ->
                val url = response.paymentLinkUrl
                if (url != null) {
                    val linkId = response.id.orEmpty()
                    val description = response.description ?: data.description
                    val amount = response.amount ?: data.amount
                    uiState = MiniAppUiState.PaymentLinkReady(
                        url = url,
                        linkId = linkId,
                        amount = amount,
                        description = description,
                        currency = response.currency ?: data.currency
                    )

                    bridge?.sendResponse(mapOf(
                        "paymentLinkUrl" to url,
                        "linkId" to linkId,
                        "description" to description,
                        "hasFixedAmount" to true,
                        "amount" to amount,
                        "currency" to (response.currency ?: data.currency)
                    ))

                    Log.i("MiniAppViewModel", "Payment link generated successfully: $url")
                } else {
                    handlePaymentLinkError("Payment link URL was not provided by SwiftPay server")
                }
            }.onFailure { error ->
                handlePaymentLinkError(error.message ?: "Failed to generate payment link")
            }
        }
    }

    private fun handlePaymentLinkError(errorMessage: String) {
        Log.e("MiniAppViewModel", "Payment link error: $errorMessage")
        bridge?.sendError(errorMessage)
        uiState = MiniAppUiState.Error(errorMessage)
    }

    fun onScanNFCCard(amount: Double) {
        val message = "Tap-to-phone card processing is not supported by the configured SwiftPay API."
        bridge?.sendError(message)
        uiState = MiniAppUiState.Error(message)
    }

    fun onNfcProgress(message: String) {
        val friendlyMessage = message.toFriendlyNfcMessage()
        viewModelScope.launch {
            (uiState as? MiniAppUiState.WaitingForNFC)?.let { state ->
                uiState = state.copy(statusMessage = friendlyMessage)
            }
        }
    }

    fun onGenerateDynamicQr(amount: Double, currency: String = "PHP", paymentAppName: String? = null) {
        val normalizedPaymentApp = normalizePaymentAppName(paymentAppName)
        if (paymentAppName != null && normalizedPaymentApp == null) {
            bridge?.sendError("Unsupported QR Ph payment app", action = "generate_dynamic_qr")
            return
        }
        if (currency != "PHP") {
            bridge?.sendError(
                "SwiftPay QR Ph settles in PHP. Request a KRW, USD, EUR, or CNY-to-PHP quote first.",
                action = "generate_dynamic_qr"
            )
            return
        }
        launchProcessingAction("Generating dynamic QR Ph code...", bridgeAction = "generate_dynamic_qr") {
            handleQrGenerationResult(
                result = getService().createDynamicQr(amount, currency),
                amount = amount,
                currency = currency,
                bridgeAction = "generate_dynamic_qr",
                paymentAppName = paymentAppName
            )
        }
    }

    fun onQuoteKrwPhpQr(amountKrw: Int) {
        launchProcessingAction("Getting current KRW-to-PHP quote...", bridgeAction = "quote_krw_php_qr") {
            getService().getKrwPhpQrQuote(amountKrw)
                .onSuccess { quote ->
                    bridge?.sendResponse(
                        mapOf(
                            "quoteToken" to quote.quoteToken,
                            "customerAmountKrw" to quote.customerAmountKrw,
                            "settlementAmountPhp" to quote.settlementAmountPhp,
                            "ratePhpPerKrw" to quote.ratePhpPerKrw,
                            "rateUpdatedAt" to quote.rateUpdatedAt,
                            "expiresAt" to quote.expiresAt,
                            "settlementCurrency" to quote.settlementCurrency
                        ),
                        action = "quote_krw_php_qr"
                    )
                    uiState = MiniAppUiState.Idle
                }
                .onFailure { error ->
                    bridge?.sendError(
                        error.message ?: "Could not get the KRW-to-PHP quote",
                        action = "quote_krw_php_qr"
                    )
                    uiState = MiniAppUiState.Idle
            }
        }
    }

    fun onQuoteFxPhpQr(amount: Double, currency: String) {
        launchProcessingAction("Getting current $currency-to-PHP quote...", bridgeAction = "quote_fx_php_qr") {
            getService().getFxPhpQrQuote(amount, currency)
                .onSuccess { quote ->
                    bridge?.sendResponse(
                        mapOf(
                            "quoteToken" to quote.quoteToken,
                            "customerAmount" to quote.customerAmount,
                            "customerCurrency" to quote.customerCurrency,
                            "settlementAmountPhp" to quote.settlementAmountPhp,
                            "ratePhpPerUnit" to quote.ratePhpPerUnit,
                            "rateUpdatedAt" to quote.rateUpdatedAt,
                            "expiresAt" to quote.expiresAt,
                            "settlementCurrency" to quote.settlementCurrency
                        ),
                        action = "quote_fx_php_qr"
                    )
                    uiState = MiniAppUiState.Idle
                }
                .onFailure { error ->
                    bridge?.sendError(
                        error.message ?: "Could not get the $currency-to-PHP quote",
                        action = "quote_fx_php_qr"
                    )
                    uiState = MiniAppUiState.Idle
                }
        }
    }

    fun onGenerateQuotedKrwPhpQr(quoteToken: String, paymentAppName: String? = null) {
        val normalizedPaymentApp = normalizePaymentAppName(paymentAppName)
        if (paymentAppName != null && normalizedPaymentApp == null) {
            bridge?.sendError("Unsupported QR Ph payment app", action = "generate_quoted_krw_php_qr")
            return
        }
        launchProcessingAction("Creating PHP QR from the KRW price quote...", bridgeAction = "generate_quoted_krw_php_qr") {
            handleQrGenerationResult(
                result = getService().createKrwPhpQuotedQr(quoteToken),
                amount = 0.0,
                currency = "PHP",
                bridgeAction = "generate_quoted_krw_php_qr",
                paymentAppName = paymentAppName
            )
        }
    }

    fun onGenerateQuotedFxPhpQr(quoteToken: String, paymentAppName: String? = null) {
        val normalizedPaymentApp = normalizePaymentAppName(paymentAppName)
        if (paymentAppName != null && normalizedPaymentApp == null) {
            bridge?.sendError("Unsupported QR Ph payment app", action = "generate_quoted_fx_php_qr")
            return
        }
        launchProcessingAction("Creating PHP QR from the currency price quote...", bridgeAction = "generate_quoted_fx_php_qr") {
            handleQrGenerationResult(
                result = getService().createFxPhpQuotedQr(quoteToken),
                amount = 0.0,
                currency = "PHP",
                bridgeAction = "generate_quoted_fx_php_qr",
                paymentAppName = paymentAppName
            )
        }
    }

    private suspend fun handleQrGenerationResult(
        result: Result<com.example.myapplication.data.api.SwiftPayQrResponse>,
        amount: Double,
        currency: String,
        bridgeAction: String,
        paymentAppName: String? = null
    ) {
        result.onSuccess { response ->
            val qrCode = response.qrCodeBody ?: response.qrCode
            val qrImageBase64 = response.qrImageBase64
            val paymentId = response.paymentId ?: response.id
            val paymentAmount = response.amount ?: amount
            val paymentCurrency = response.currency ?: currency
            val checkoutUrl = response.checkoutUrl
            if (!checkoutUrl.isNullOrBlank() && !paymentId.isNullOrBlank()) {
                pendingCheckoutId = paymentId
                recordLocalTransaction(id = paymentId, amount = paymentAmount, status = "PENDING")
                uiState = MiniAppUiState.PaymentRedirect(
                    checkoutUrl,
                    PaymentData(
                        amount = paymentAmount,
                        currency = paymentCurrency,
                        description = "$paymentCurrency QR Ph collection"
                    )
                )
                bridge?.sendResponse(
                    mapOf("checkoutUrl" to checkoutUrl, "paymentId" to paymentId),
                    action = bridgeAction
                )
            } else if (!qrCode.isNullOrBlank() && !qrImageBase64.isNullOrBlank() && !paymentId.isNullOrBlank()) {
                recordLocalTransaction(id = paymentId, amount = paymentAmount, status = "PENDING")
                uiState = MiniAppUiState.DynamicQrReady(
                    qrImageBase64 = qrImageBase64,
                    amount = paymentAmount,
                    currency = paymentCurrency,
                    bankAccountNumber = response.bankAccountNumber,
                    instapayBankCode = response.instapayBankCode,
                    customerAmountKrw = response.customerAmountKrw,
                    ratePhpPerKrw = response.ratePhpPerKrw,
                    customerAmountFx = response.customerAmount,
                    customerCurrency = response.customerCurrency,
                    ratePhpPerUnit = response.ratePhpPerUnit,
                    rateUpdatedAt = response.rateUpdatedAt,
                    paymentAppName = paymentAppName
                )
                bridge?.sendResponse(mapOf("qrCodeBody" to qrCode), action = bridgeAction)
            } else {
                bridge?.sendError("SwiftPay returned an incomplete QR Ph payment", action = bridgeAction)
                uiState = MiniAppUiState.Idle
            }
        }.onFailure { error ->
            bridge?.sendError(error.message ?: "QR generation failed", action = bridgeAction)
            uiState = MiniAppUiState.Idle
        }
    }

    fun onGetWalletDepositAccount() {
        launchProcessingAction(
            "Loading deposit account...",
            bridgeAction = "get_wallet_deposit_account"
        ) {
            val service = getService()
            val account = service.getWalletDepositAccount().getOrElse { throw it }
            bridge?.sendResponse(
                mapOf(
                    "accountNumber" to account.accountNumber,
                    "balance" to account.balance
                ),
                action = "get_wallet_deposit_account"
            )
            uiState = MiniAppUiState.Idle
        }
    }

    fun onScanQrToSend() {
        uiState = MiniAppUiState.ScanQrToSend
    }

    fun onGetTransferInstitutions() {
        viewModelScope.launch {
                try {
                    val service = getService()
                    service.getTransferInstitutions()
                        .onSuccess { institutions ->
                            val brandedInstitutions = institutions.withPaymentInstitutionLogos(
                                service.getPaymentInstitutionLogos()
                            )
                            bridge?.sendResponse(
                                brandedInstitutions.filter { it.instapayEnabled },
                                action = "get_transfer_institutions"
                            )
                        }
                        .onFailure { error ->
                            bridge?.sendError(
                                error.message ?: "Could not load transfer institutions.",
                                action = "get_transfer_institutions"
                            )
                        }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.e("MiniAppViewModel", "Failed to load transfer institutions", error)
                    bridge?.sendError(
                        error.message ?: "Could not load transfer institutions.",
                        action = "get_transfer_institutions"
                    )
                }
        }
    }

    fun onQrScanned(payload: String) {
        val scanned = parseScannedQrPayment(payload)
        if (scanned == null) {
            val message = "This QR code could not be read as a supported payment QR."
            bridge?.sendError(message)
            uiState = MiniAppUiState.Error(message)
            return
        }
        if (scanned.currencyCode != "608") {
            val message = "This QR code is denominated in KRW. QR transfers currently require a PHP amount; no exchange conversion was applied."
            bridge?.sendError(message)
            uiState = MiniAppUiState.Error(message)
            return
        }
        viewModelScope.launch {
            uiState = MiniAppUiState.Processing("Loading InstaPay institutions...")
            val service = getService()
            service.getTransferInstitutions()
                .onSuccess { institutions ->
                    val brandedInstitutions = institutions.withPaymentInstitutionLogos(
                        service.getPaymentInstitutionLogos()
                    )
                    val instapayInstitutions = brandedInstitutions.filter { it.instapayEnabled }
                    if (instapayInstitutions.isEmpty()) {
                        val message = "SwiftPay returned no InstaPay institutions."
                        bridge?.sendError(message)
                        uiState = MiniAppUiState.Error(message)
                    } else {
                        uiState = MiniAppUiState.QrTransferReview(scanned.amount, instapayInstitutions)
                    }
                }
                .onFailure { error ->
                    val message = error.message ?: "Could not load InstaPay institutions."
                    bridge?.sendError(message)
                    uiState = MiniAppUiState.Error(message)
                }
        }
    }

    fun sendQrP2pDisbursement(
        amount: Double,
        institutionCode: String,
        accountNumber: String,
        recipientName: String
    ) {
        if (!amount.isFinite() || amount <= 0.0 || amount > 50_000.0) {
            uiState = MiniAppUiState.Error("QR_P2P InstaPay transfers must be between PHP 0.01 and PHP 50,000.")
            return
        }
        launchProcessingAction("Submitting QR_P2P transfer...") {
            val result = getService().disburse(
                amount = amount,
                bankCode = institutionCode,
                accountNumber = accountNumber,
                recipientName = recipientName,
                transferType = "QR_P2P"
            )
            result.onSuccess { response ->
                val id = response.disbursementId ?: response.id ?: "DISB${System.currentTimeMillis()}"
                val recordedLocally = recordAcceptedDisbursement(
                    id = id,
                    amount = -amount,
                    status = "PENDING",
                    transactionType = "DISBURSEMENT",
                    referenceNo = response.referenceNo,
                    description = "QR_P2P transfer"
                )
                bridge?.sendResponse(
                    mapOf(
                        "status" to "pending",
                        "disbursementId" to id,
                        "message" to if (recordedLocally) {
                            "QR_P2P transfer submitted. Check its status before treating it as complete."
                        } else {
                            "SwiftPay accepted the QR_P2P transfer, but this device could not save it to transaction history. Check its status before retrying."
                        }
                    )
                )
                uiState = MiniAppUiState.Idle
            }.onFailure { error ->
                val message = error.message ?: "QR_P2P transfer failed"
                bridge?.sendError(message)
                uiState = MiniAppUiState.Error(message)
            }
        }
    }

    fun onSendDisbursement(amount: Double, bankCode: String, accountNumber: String, recipientName: String) {
        if (!amount.isFinite() || amount <= 0.0) {
            uiState = MiniAppUiState.Error("Please enter a valid transfer amount.")
            return
        }
        launchProcessingAction("Processing SwiftPay transfer...", bridgeAction = "send_disbursement") {
            val result = getService().disburse(amount, bankCode, accountNumber, recipientName)
            result.onSuccess { response ->
                if (response.outcomeUnknown) {
                    bridge?.sendResponse(
                        mapOf(
                            "status" to "unknown",
                            "outcomeUnknown" to true,
                            "referenceNo" to (response.referenceNo ?: ""),
                            "message" to (response.message
                                ?: "SwiftPay has not confirmed whether this transfer was accepted. Do not submit it again. Check your transaction history or contact support with this reference.")
                        ),
                        action = "send_disbursement"
                    )
                    uiState = MiniAppUiState.Idle
                    return@onSuccess
                }

                val status = normalizePaymentStatus(response.status)
                val transferId = response.disbursementId ?: response.id
                val recordedLocally = recordAcceptedDisbursement(
                    id = transferId ?: "DISB${System.currentTimeMillis()}",
                    amount = -amount,
                    status = status,
                    transactionType = "DISBURSEMENT",
                    referenceNo = response.referenceNo,
                    description = "Transfer to $recipientName ($bankCode)"
                )
                val message = when {
                    !recordedLocally ->
                        "SwiftPay accepted the transfer, but this device could not save it to transaction history. Verify its status before retrying."
                    status == "SUCCESS" ->
                        "Transfer of ₱${"%.2f".format(amount)} to $recipientName ($bankCode) completed."
                    else ->
                        "Transfer submitted for processing. Check transaction status before treating it as complete."
                }
                bridge?.sendResponse(mapOf(
                    "status" to status.lowercase(),
                    "disbursementId" to (transferId ?: ""),
                    "message" to message
                ), action = "send_disbursement")
                uiState = MiniAppUiState.Idle
            }.onFailure { error ->
                val errorMsg = error.message ?: "Transfer failed"
                bridge?.sendError(errorMsg, action = "send_disbursement")
                uiState = MiniAppUiState.Idle
            }
        }
    }

    private fun startNFCTimer(
        amount: Double,
        merchantName: String = "Fast Pay Merchant",
        merchantAddress: String = "Pila, Laguna, PHL"
    ) {
        nfcTimerJob?.cancel()
        nfcTimerJob = viewModelScope.launch {
            var seconds = NFC_SESSION_SECONDS
            while (seconds >= 0) {
                val currentState = uiState as? MiniAppUiState.WaitingForNFC
                uiState = MiniAppUiState.WaitingForNFC(
                    amount = amount,
                    merchantName = currentState?.merchantName ?: merchantName,
                    merchantAddress = currentState?.merchantAddress ?: merchantAddress,
                    timeLeft = seconds,
                    sessionDurationSeconds = NFC_SESSION_SECONDS,
                    statusMessage = currentState?.statusMessage
                        ?: "Reader ready. Hold contactless card firmly against the back of phone."
                )
                if (seconds == 0) {
                    uiState = MiniAppUiState.WaitingForNFC(
                        amount = amount,
                        merchantName = currentState?.merchantName ?: merchantName,
                        merchantAddress = currentState?.merchantAddress ?: merchantAddress,
                        timeLeft = 0,
                        sessionDurationSeconds = NFC_SESSION_SECONDS,
                        errorMessage = "Scan timed out. Hold card against device for 2-3 seconds and try again."
                    )
                    bridge?.sendError("NFC session timed out after $NFC_SESSION_SECONDS seconds")
                    return@launch
                }
                delay(1000)
                seconds--
            }
        }
    }

    fun retryNfc() {
        val message = "Tap-to-phone card processing is not supported by the configured SwiftPay API."
        bridge?.sendError(message)
        uiState = MiniAppUiState.Error(message)
    }

    private fun start3DSTimeout(checkoutId: String) {
        threeDSecureTimeoutJob?.cancel()
        threeDSecureTimeoutJob = viewModelScope.launch {
            delay(10 * 60 * 1000L)
            if (pendingCheckoutId == checkoutId) {
                pendingCheckoutId = null
                val errorMsg = "3D Secure verification timed out (10 minutes)."
                bridge?.sendError(errorMsg)
                uiState = MiniAppUiState.Error(errorMsg)
            }
        }
    }

    fun onCvvEntered(cvv: String) {
        val message = "Direct card entry is not supported. Use SwiftPay hosted checkout instead."
        bridge?.sendError(message)
        uiState = MiniAppUiState.Error(message)
    }

    fun onSaveSettings(data: Map<String, String>) {
        launchProcessingAction("Saving settings...") {
            data["merchantAlias"]?.takeIf { it.isNotBlank() }?.let { settingsManager.saveMerchantAlias(it) }

            bridge?.sendResponse(mapOf(
                "status" to "saved",
                "message" to "SwiftPay credentials are managed securely by the payment service."
            ))
            uiState = MiniAppUiState.Idle
        }
    }

    fun getSettings() {
        viewModelScope.launch {
            try {
                val hasBackendSession = !settingsManager.backendAccessToken.first().isNullOrBlank()
                val alias = settingsManager.merchantAlias.first() ?: ""
                bridge?.sendResponse(
                    mapOf(
                        "hasApiKey" to hasBackendSession.toString(),
                        "managedByServer" to "true",
                        "apiKey" to "",
                        "baseUrl" to "",
                        "merchantId" to "",
                        "terminalId" to "",
                        "merchantAlias" to alias
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                bridge?.sendError(error.message ?: "Unable to load payment service settings")
            }
        }
    }

    fun approvePayment(data: PaymentData) {
        launchProcessingAction("Creating SwiftPay checkout...") {
            val result = getService().createCheckout(data)

            result.onSuccess { response ->
                val redirectUrl = response.checkoutUrl ?: response.paymentUrl
                if (redirectUrl != null) {
                    val checkoutId = response.id ?: response.orderId ?: "CHECKOUT${System.currentTimeMillis()}"
                    pendingCheckoutId = checkoutId
                    recordLocalTransaction(
                        id = checkoutId,
                        amount = data.amount,
                        status = "PENDING"
                    )
                    uiState = MiniAppUiState.PaymentRedirect(redirectUrl, data)
                } else {
                    val errorMsg = "Redirect URL was not provided by SwiftPay server"
                    bridge?.sendError(errorMsg)
                    uiState = MiniAppUiState.Error(errorMsg)
                }
            }.onFailure { error ->
                val errorMessage = error.message ?: "Payment initialization failed"
                bridge?.sendError(errorMessage)
                uiState = MiniAppUiState.Error(errorMessage)
            }
        }
    }

    private suspend fun recordLocalTransaction(
        id: String,
        amount: Double,
        status: String,
        transactionType: String? = null,
        referenceNo: String? = null,
        description: String? = null
    ) {
        val normalizedStatus = normalizePaymentStatus(status)
        var actualAmount = amount

        val existingTx = repository.getAllTransactions().first().find { it.transactionId == id }

        if (existingTx != null && normalizePaymentStatus(existingTx.status) == normalizedStatus) {
            return
        }

        if (existingTx != null) {
            if (actualAmount <= 0.0) {
                actualAmount = existingTx.amount
            }
        }

        repository.saveTransaction(
            InternalTransaction(
                transactionId = id,
                amount = actualAmount,
                status = normalizedStatus,
                date = TransactionStore.nowLabel(),
                transactionType = transactionType,
                referenceNo = referenceNo,
                description = description
            )
        )

    }

    private suspend fun recordAcceptedDisbursement(
        id: String,
        amount: Double,
        status: String,
        transactionType: String,
        referenceNo: String?,
        description: String
    ): Boolean =
        try {
            recordLocalTransaction(
                id = id,
                amount = amount,
                status = status,
                transactionType = transactionType,
                referenceNo = referenceNo,
                description = description
            )
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e("MiniAppViewModel", "SwiftPay accepted a transfer but local history could not be updated", error)
            false
        }

    private fun normalizePaymentStatus(rawStatus: String?): String {
        val s = rawStatus?.uppercase() ?: return "PENDING"
        return when {
            s in listOf("SUCCESS", "PAYMENT_SUCCESS", "CAPTURED", "COMPLETED", "APPROVED", "AUTHORIZED", "EXECUTED", "PAID") -> "SUCCESS"
            s in listOf("FAILED", "PAYMENT_FAILED", "DECLINED", "REJECTED", "ERROR", "EXPIRED", "PAYMENT_EXPIRED", "CANCELLED", "CANCELED") -> "FAILED"
            else -> "PENDING"
        }
    }

    private val inFlightStatusChecks = mutableSetOf<String>()

    fun fetchPaymentStatus(paymentId: String) {
        if (paymentId.isBlank() || inFlightStatusChecks.contains(paymentId)) return

        val cachedStatus = transactionStatusMap[paymentId]?.status?.uppercase()
        if (cachedStatus in listOf("SUCCESS", "EXECUTED", "FAILED", "REJECTED", "ERROR", "EXPIRED", "CANCELLED", "CANCELED", "DECLINED")) {
            return
        }

        inFlightStatusChecks.add(paymentId)
        viewModelScope.launch {
            try {
                val service = getService()
                for (attempt in 0 until MAX_STATUS_POLL_ATTEMPTS) {
                    val result = service.getPaymentStatus(paymentId)
                    val response = result.getOrNull()
                    if (response == null) {
                        if (attempt == MAX_STATUS_POLL_ATTEMPTS - 1) {
                            Log.e(
                                "MiniAppViewModel",
                                "Could not fetch payment status for $paymentId after $MAX_STATUS_POLL_ATTEMPTS attempts",
                                result.exceptionOrNull()
                            )
                            break
                        }
                        delay(STATUS_POLL_INTERVAL_MS)
                        continue
                    }

                    val status = response.status ?: response.paymentStatus
                    if (status == null) {
                        if (attempt == MAX_STATUS_POLL_ATTEMPTS - 1) {
                            Log.e(
                                "MiniAppViewModel",
                                "Payment status response for $paymentId contained no status after $MAX_STATUS_POLL_ATTEMPTS attempts"
                            )
                            break
                        }
                        delay(STATUS_POLL_INTERVAL_MS)
                        continue
                    }

                    val currentMap = transactionStatusMap.toMutableMap()
                    currentMap[paymentId] = response
                    transactionStatusMap = currentMap

                    val normalized = normalizePaymentStatus(status)
                    val storedAmount = response.amount ?: 0.0
                    recordLocalTransaction(
                        id = paymentId,
                        amount = storedAmount,
                        status = normalized
                    )
                    if (normalized != "PENDING" || attempt == MAX_STATUS_POLL_ATTEMPTS - 1) {
                        break
                    }
                    delay(STATUS_POLL_INTERVAL_MS)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MiniAppViewModel", "Exception fetching payment status", e)
            } finally {
                inFlightStatusChecks.remove(paymentId)
            }
        }
    }

    fun fetchPaymentStatuses(paymentIds: List<String>) {
        paymentIds.forEach { fetchPaymentStatus(it) }
    }

    fun getPaymentStatusForId(paymentId: String): SwiftPayStatusResponse? {
        return transactionStatusMap[paymentId]
    }

    fun clearTransactionStatus(id: String) {
        if (id.isBlank()) return
        transactionStatusMap = transactionStatusMap.toMutableMap().apply {
            remove(id)
        }
    }

    fun handleDeepLink(linkId: String?, providedStatus: String? = null) {
        if (linkId.isNullOrBlank()) return
        fetchPaymentStatus(linkId)
    }

    private suspend fun checkPendingPaymentStatus() {
        val checkoutId = pendingCheckoutId ?: return
        var polls = 0
        val maxPolls = 120

        while (pendingCheckoutId == checkoutId && polls < maxPolls) {
            try {
                val service = getService()
                val response = service.getCheckoutStatus(checkoutId).getOrNull()
                    ?: service.getPaymentStatus(checkoutId).getOrNull()

                val rawStatus = response?.status ?: response?.paymentStatus
                val status = rawStatus?.uppercase()

                if (status != null && status in listOf("SUCCESS", "EXECUTED", "PAID", "CAPTURED", "COMPLETED", "APPROVED", "AUTHORIZED")) {
                    threeDSecureTimeoutJob?.cancel()
                    pendingCheckoutId = null
                    val paymentAmount = response?.amount?.takeIf { it > 0.0 }
                        ?: 0.0

                    recordLocalTransaction(
                        id = checkoutId,
                        amount = paymentAmount,
                        status = "SUCCESS"
                    )
                    refreshBalance()

                    bridge?.sendResponse(mapOf(
                        "status" to "success",
                        "paymentId" to checkoutId,
                        "amount" to paymentAmount
                    ))

                    uiState = MiniAppUiState.Idle
                    Log.i("MiniAppViewModel", "✅ Checkout $checkoutId completed successfully via status polling!")
                    break
                } else if (status != null && status in listOf("FAILED", "DECLINED", "REJECTED", "ERROR", "EXPIRED", "CANCELLED", "CANCELED")) {
                    threeDSecureTimeoutJob?.cancel()
                    pendingCheckoutId = null
                    val paymentAmount = response?.amount?.takeIf { it > 0.0 }
                        ?: 0.0

                    recordLocalTransaction(
                        id = checkoutId,
                        amount = paymentAmount,
                        status = "FAILED"
                    )

                    val errorMsg = "Payment $checkoutId failed: $status"
                    bridge?.sendError(errorMsg)
                    uiState = MiniAppUiState.Error(errorMsg)
                    Log.w("MiniAppViewModel", "❌ Checkout $checkoutId failed with status: $status")
                    break
                }
            } catch (e: Exception) {
                Log.e("MiniAppViewModel", "Error checking status for $checkoutId", e)
            }

            delay(3000)
            polls++
        }
    }

    fun dismissConsent() {
        nfcTimerJob?.cancel()
        threeDSecureTimeoutJob?.cancel()
        pendingCheckoutId = null
        uiState = MiniAppUiState.Idle
    }

    fun dismissError() {
        uiState = MiniAppUiState.Idle
    }

    companion object {
        const val NFC_SESSION_SECONDS = 90
        private const val MAX_STATUS_POLL_ATTEMPTS = 30
        private const val STATUS_POLL_INTERVAL_MS = 10_000L
    }
}

private suspend fun List<BackendTransferInstitution>.withPaymentInstitutionLogos(
    logosResult: Result<List<BackendPaymentInstitutionLogo>>
): List<BackendTransferInstitution> {
    val logos = logosResult.getOrElse { error ->
        Log.w("MiniAppViewModel", "Institution logos could not be loaded; showing institution names instead.", error)
        return this
    }
    val logosByName = logos.mapNotNull { institution ->
        institution.logoUrl
            ?.takeIf(::isTrustedInstitutionLogoUrl)
            ?.let { institutionBrandKey(institution.name) to it }
    }.toMap()

    return map { institution ->
        institution.copy(logoUrl = logosByName[institutionBrandKey(institution.name)])
    }
}

private fun String.toFriendlyNfcMessage(): String = when (trim()) {
    "Selecting Payment System..." -> "Connecting contactless card..."
    "Accessing Card Application..." -> "Opening card application..."
    "Extracting Card Details..." -> "Checking card details..."
    "Retrieving Security Options..." -> "Reading card security options..."
    "Scanning Card Records..." -> "Scanning card records..."
    else -> this
}
