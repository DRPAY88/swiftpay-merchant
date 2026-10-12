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
import com.example.myapplication.data.api.InternalTransaction
import com.example.myapplication.data.api.BackendTransferInstitution
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

sealed interface MiniAppUiState {
    object Idle : MiniAppUiState
    data class Processing(val message: String) : MiniAppUiState
    data class Error(val message: String) : MiniAppUiState
    data class PaymentConsent(val data: PaymentData) : MiniAppUiState
    data class PaymentRedirect(val url: String, val data: PaymentData) : MiniAppUiState
    data class PaymentLinkReady(val url: String) : MiniAppUiState
    data class DynamicQrReady(
        val qrImageBase64: String,
        val amount: Double,
        val bankAccountNumber: String?,
        val instapayBankCode: String?
    ) : MiniAppUiState
    data class DepositWizard(
        val accountNumber: String,
        val balance: Double
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

    private fun launchProcessingAction(message: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            uiState = MiniAppUiState.Processing(message)
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val errorMessage = error.message ?: "The request failed. Please try again."
                Log.e("MiniAppViewModel", message, error)
                bridge?.sendError(errorMessage)
                uiState = MiniAppUiState.Error(errorMessage)
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
                        details.accountNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { accountNumber ->
                            depositAccountNumber = accountNumber
                            viewModelScope.launch {
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
                val result = getService().getInternalTransactions()
                val localTransactions = repository.getAllTransactions().first()
                result.onSuccess { transactions ->
                    bridge?.sendResponse(mergeTransactions(localTransactions, transactions))
                }.onFailure { error ->
                    if (localTransactions.isNotEmpty()) {
                        bridge?.sendResponse(localTransactions)
                    } else {
                        bridge?.sendError(error.message ?: "Failed to fetch transactions")
                    }
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
                description = data.description
            )

            result.onSuccess { response ->
                val url = response.paymentLinkUrl
                if (url != null) {
                    uiState = MiniAppUiState.PaymentLinkReady(url)

                    bridge?.sendResponse(mapOf(
                        "paymentLinkUrl" to url,
                        "linkId" to (response.id ?: ""),
                        "description" to (response.description ?: data.description),
                        "hasFixedAmount" to (data.amount > 0),
                        "amount" to data.amount
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

    fun onGenerateDynamicQr(amount: Double) {
        launchProcessingAction("Generating dynamic QR Ph code...") {
            val result = getService().createDynamicQr(amount)

            result.onSuccess { response ->
                val qrCode = response.qrCodeBody ?: response.qrCode
                val qrImageBase64 = response.qrImageBase64
                val paymentId = response.paymentId ?: response.id
                val checkoutUrl = response.checkoutUrl
                if (!checkoutUrl.isNullOrBlank() && !paymentId.isNullOrBlank()) {
                    pendingCheckoutId = paymentId
                    recordLocalTransaction(
                        id = paymentId,
                        amount = amount,
                        status = "PENDING"
                    )
                    uiState = MiniAppUiState.PaymentRedirect(
                        checkoutUrl,
                        PaymentData(amount = amount, currency = "PHP", description = "QR Ph collection")
                    )
                    bridge?.sendResponse(mapOf("checkoutUrl" to checkoutUrl, "paymentId" to paymentId))
                } else if (!qrCode.isNullOrBlank() && !qrImageBase64.isNullOrBlank() && !paymentId.isNullOrBlank()) {
                    recordLocalTransaction(
                        id = paymentId,
                        amount = amount,
                        status = "PENDING"
                    )
                    uiState = MiniAppUiState.DynamicQrReady(
                        qrImageBase64 = qrImageBase64,
                        amount = amount,
                        bankAccountNumber = response.bankAccountNumber,
                        instapayBankCode = response.instapayBankCode
                    )
                    bridge?.sendResponse(mapOf("qrCodeBody" to qrCode))
                } else {
                    val errorMsg = "SwiftPay returned an incomplete QR Ph payment"
                    bridge?.sendError(errorMsg)
                    uiState = MiniAppUiState.Error(errorMsg)
                }
            }.onFailure { error ->
                val errorMsg = error.message ?: "QR generation failed"
                bridge?.sendError(errorMsg)
                uiState = MiniAppUiState.Error(errorMsg)
            }
        }
    }

    fun onStartDepositWizard() {
        launchProcessingAction("Loading deposit instructions...") {
            val service = getService()
            val account = service.getWalletDepositAccount().getOrElse { throw it }
            uiState = MiniAppUiState.DepositWizard(
                accountNumber = account.accountNumber,
                balance = account.balance
            )
        }
    }

    fun onScanQrToSend() {
        uiState = MiniAppUiState.ScanQrToSend
    }

    fun onGetTransferInstitutions() {
        viewModelScope.launch {
                try {
                    getService().getTransferInstitutions()
                        .onSuccess { institutions ->
                            bridge?.sendResponse(
                                institutions.filter { it.instapayEnabled },
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
        viewModelScope.launch {
            uiState = MiniAppUiState.Processing("Loading InstaPay institutions...")
            getService().getTransferInstitutions()
                .onSuccess { institutions ->
                    val instapayInstitutions = institutions.filter { it.instapayEnabled }
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
                recordLocalTransaction(id, -amount, "PENDING")
                bridge?.sendResponse(
                    mapOf(
                        "status" to "pending",
                        "disbursementId" to id,
                        "message" to "QR_P2P transfer submitted. Check its status before treating it as complete."
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
        launchProcessingAction("Processing SwiftPay transfer...") {
            val result = getService().disburse(amount, bankCode, accountNumber, recipientName)
            result.onSuccess { response ->
                val status = normalizePaymentStatus(response.status)
                recordLocalTransaction(
                    id = response.disbursementId ?: response.id ?: "DISB${System.currentTimeMillis()}",
                    amount = -amount,
                    status = status
                )
                bridge?.sendResponse(mapOf(
                    "status" to status.lowercase(),
                    "disbursementId" to (response.disbursementId ?: response.id ?: ""),
                    "message" to if (status == "SUCCESS") {
                        "Transfer of ₱${"%.2f".format(amount)} to $recipientName ($bankCode) completed."
                    } else {
                        "Transfer submitted for processing. Check transaction status before treating it as complete."
                    }
                ))
                uiState = MiniAppUiState.Idle
            }.onFailure { error ->
                val errorMsg = error.message ?: "Transfer failed"
                bridge?.sendError(errorMsg)
                uiState = MiniAppUiState.Error(errorMsg)
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
        status: String
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
                date = TransactionStore.nowLabel()
            )
        )

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
                val response = service.getPaymentStatus(paymentId).getOrNull()
                    ?: service.getCheckoutStatus(paymentId).getOrNull()

                if (response != null) {
                    val status = response.status ?: response.paymentStatus
                    if (status != null) {
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
                    }
                }
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

                    recordLocalTransaction(
                        id = checkoutId,
                        amount = response?.amount ?: 0.0,
                        status = "SUCCESS"
                    )
                    refreshBalance()

                    bridge?.sendResponse(mapOf(
                        "status" to "success",
                        "paymentId" to checkoutId,
                        "amount" to (response?.amount ?: 0.0)
                    ))

                    uiState = MiniAppUiState.Idle
                    Log.i("MiniAppViewModel", "✅ Checkout $checkoutId completed successfully via status polling!")
                    break
                } else if (status != null && status in listOf("FAILED", "DECLINED", "REJECTED", "ERROR", "EXPIRED", "CANCELLED", "CANCELED")) {
                    threeDSecureTimeoutJob?.cancel()
                    pendingCheckoutId = null

                    recordLocalTransaction(
                        id = checkoutId,
                        amount = response?.amount ?: 0.0,
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
