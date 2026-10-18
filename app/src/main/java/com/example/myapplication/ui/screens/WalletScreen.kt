package com.example.myapplication.ui.screens

import android.app.Application
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.api.InternalTransaction
import com.example.myapplication.data.api.BackendKrwPhpQrQuoteResponse
import com.example.myapplication.data.api.SwiftPayQrResponse
import com.example.myapplication.data.api.SwiftPayStatusResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.example.myapplication.ui.components.DrPayLogo
import com.example.myapplication.ui.components.DrPayMarkBadge
import com.example.myapplication.ui.components.rememberReducedMotion
import kotlin.math.abs

import com.example.myapplication.LocalNavController
import com.example.myapplication.navigation.Route
import com.example.myapplication.data.SettingsManager
import com.example.myapplication.data.TransactionStore
import com.example.myapplication.data.createSwiftPayService
import com.example.myapplication.data.mergeTransactions
import com.example.myapplication.ui.utils.formatReceiptDate
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.theme.*

import com.example.myapplication.ui.components.TransactionStatusIndicator
import com.example.myapplication.ui.components.FastPayStatusBadge
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun WalletScreen(
    modifier: Modifier = Modifier,
    viewModel: MiniAppViewModel = viewModel()
) {
    val balance = viewModel.walletBalance
    val adjustedBalance = viewModel.walletAdjustedBalance
    var localTransactions by remember { mutableStateOf<List<InternalTransaction>>(emptyList()) }
    var remoteTransactions by remember { mutableStateOf<List<InternalTransaction>>(emptyList()) }
    var transactionHistoryError by remember { mutableStateOf<String?>(null) }
    var isHistoryLoading by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }
    var showTopupDialog by remember { mutableStateOf(false) }
    var topupAmount by remember { mutableStateOf("100") }
    var topupCurrency by remember { mutableStateOf("PHP") }
    var selectedPaymentApp by remember { mutableStateOf("TOSS") }
    var krwTopupQuote by remember { mutableStateOf<BackendKrwPhpQrQuoteResponse?>(null) }
    var topupQr by remember { mutableStateOf<SwiftPayQrResponse?>(null) }
    var topupStatus by remember { mutableStateOf<String?>(null) }
    var topupError by remember { mutableStateOf<String?>(null) }
    var isCreatingTopup by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val appContext = viewModel.getApplication<Application>()
    val settings = remember(appContext) { SettingsManager(appContext) }
    val signedInIdentity by settings.loggedInIdentity.collectAsState(initial = null)
    val signedInRole by settings.loggedInRole.collectAsState(initial = null)
    val isCustomerWallet = signedInRole == "customer"
    val isInternalMerchantWallet = signedInRole == "merchant" && viewModel.walletMode == "internal"
    val isLedgerWallet = isCustomerWallet || isInternalMerchantWallet
    val canShowProviderDepositAccount =
        signedInRole in setOf("merchant", "super_admin") && viewModel.walletMode == "pooled"

    val navigator = rememberListDetailPaneScaffoldNavigator<String>()

    val bgColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onBackground

    val transactions = remember(localTransactions, remoteTransactions) {
        mergeTransactions(localTransactions, remoteTransactions)
    }
    val transactionStatusMap = viewModel.transactionStatusMap
    val filteredTransactions = remember(transactions, selectedFilter, transactionStatusMap) {
        filterWalletTransactions(transactions, selectedFilter, transactionStatusMap)
    }
    LaunchedEffect(Unit) {
        val transactionStore = TransactionStore(appContext)
        val service = settings.createSwiftPayService()

        launch {
            transactionStore.transactions.collectLatest { savedTransactions ->
                localTransactions = savedTransactions
            }
        }
        launch { viewModel.refreshBalance() }

        try {
            val history = service.getWalletTransactionHistory()
            remoteTransactions = history.transactions
            transactionHistoryError = history.errors.takeIf { it.isNotEmpty() }?.joinToString("; ")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("WalletScreen", "Error loading transactions", e)
            transactionHistoryError = e.message
                ?: "Transaction history could not be loaded. Refresh to try again."
        } finally {
            isHistoryLoading = false
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = bgColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DrPayMarkBadge(size = 30.dp)
                        Text(
                            text = "Wallet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bgColor,
                    scrolledContainerColor = surfaceColor
                ),
                navigationIcon = {
                    if (navigator.canNavigateBack()) {
                        IconButton(onClick = { 
                            scope.launch { navigator.navigateBack() }
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = textColor)
                        }
                    }
                },
                actions = {
                    AnimatedRefreshButton(
                        isLoading = isLoading,
                        onClick = {
                            scope.launch {
                                isLoading = true
                                isHistoryLoading = true
                                try {
                                    viewModel.refreshBalance()
                                    val transactionStore = TransactionStore(appContext)
                                    val service = settings.createSwiftPayService()
                                    localTransactions = transactionStore.transactions.first()
                                    val history = service.getWalletTransactionHistory()
                                    remoteTransactions = history.transactions
                                    transactionHistoryError = history.errors.takeIf { it.isNotEmpty() }?.joinToString("; ")
                                    delay(800L)
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Log.e("WalletScreen", "Refresh Error", e)
                                    transactionHistoryError = e.message ?: "Transaction history could not be refreshed."
                                } finally {
                                    isLoading = false
                                    isHistoryLoading = false
                                }
                            }
                        }
                    )
                }
            )
        }
    ) { innerPadding ->
        val cachedAccountNumber by settings.depositAccountNumber.collectAsState(initial = null)
        val depositAccountNumber = viewModel.depositAccountNumber ?: cachedAccountNumber

        ListDetailPaneScaffold(
            modifier = Modifier.padding(innerPadding),
            directive = navigator.scaffoldDirective,
            value = navigator.scaffoldValue,
            listPane = {
                WalletListContent(
                    balance = balance,
                    adjustedBalance = adjustedBalance,
                    accountNumber = depositAccountNumber.takeIf { canShowProviderDepositAccount },
                    transactions = filteredTransactions,
                    selectedFilter = selectedFilter,
                    transactionHistoryError = transactionHistoryError,
                    isHistoryLoading = isHistoryLoading,
                    isInternalMerchantWallet = isInternalMerchantWallet,
                    isLedgerWallet = isLedgerWallet,
                    showDepositAccount = canShowProviderDepositAccount,
                    isCustomerWallet = isCustomerWallet,
                    onTopupClick = {
                        topupError = null
                        topupQr = null
                        topupStatus = null
                        topupAmount = "100"
                        topupCurrency = "PHP"
                        krwTopupQuote = null
                        showTopupDialog = true
                    },
                    onFilterSelected = { selectedFilter = it },
                    onTransactionClick = { tx ->
                        scope.launch {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, tx.transactionId)
                        }
                    },
                    viewModel = viewModel
                )
            },
            detailPane = {
                val selectedId = navigator.currentDestination?.contentKey
                val selectedTx = transactions.find { it.transactionId == selectedId }
                if (selectedTx != null) {
                    TransactionDetailScreen(
                        tx = selectedTx,
                        onShowSnackbar = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        },
                        viewModel = viewModel
                    )
                } else {
                    EmptyDetailState()
                }
            }
        )
    }

    if (showTopupDialog) {
        WalletTopupDialog(
            amount = topupAmount,
            currency = topupCurrency,
            paymentApp = selectedPaymentApp,
            quote = krwTopupQuote,
            topup = topupQr,
            status = topupStatus,
            error = topupError,
            isLoading = isCreatingTopup,
            onAmountChange = {
                topupAmount = it
                krwTopupQuote = null
            },
            onCurrencyChange = {
                topupCurrency = it
                topupAmount = if (it == "KRW") "100000" else "100"
                krwTopupQuote = null
                topupError = null
            },
            onPaymentAppChange = { selectedPaymentApp = it },
            onGetQuote = {
                if (!isCreatingTopup) {
                    val amountKrw = topupAmount.toIntOrNull()
                    if (amountKrw == null || amountKrw !in 1..1_000_000) {
                        topupError = "Enter a whole KRW amount from ₩1 to ₩1,000,000."
                    } else {
                        scope.launch {
                            isCreatingTopup = true
                            topupError = null
                            settings.createSwiftPayService().getWalletKrwPhpTopupQuote(amountKrw)
                                .onSuccess { krwTopupQuote = it }
                                .onFailure { error ->
                                    topupError = error.message ?: "Could not get a KRW-to-PHP quote."
                                    Log.e("WalletScreen", "Personal-wallet KRW quote failed", error)
                                }
                            isCreatingTopup = false
                        }
                    }
                }
            },
            onCreate = {
                if (!isCreatingTopup) {
                    val quote = krwTopupQuote
                    val amount = topupAmount.toDoubleOrNull()
                    if (topupCurrency == "KRW" && quote == null) {
                        topupError = "Get a live KRW-to-PHP quote before creating the payment QR."
                    } else if (topupCurrency == "PHP" && (amount == null || !amount.isFinite() || amount !in 1.0..100_000.0)) {
                        topupError = "Enter an amount from PHP 1 to PHP 100,000."
                    } else {
                        scope.launch {
                            isCreatingTopup = true
                            topupError = null
                            val service = settings.createSwiftPayService()
                            val result = if (topupCurrency == "KRW" && quote != null) {
                                service.createWalletKrwTopupQr(quote, selectedPaymentApp)
                            } else {
                                service.createWalletTopupQr(amount!!)
                            }
                            result
                                .onSuccess { response ->
                                    topupQr = response
                                    topupStatus = response.status ?: "PENDING"
                                }
                                .onFailure { error ->
                                    topupError = error.message ?: "Could not create a wallet top-up QR."
                                    Log.e("WalletScreen", "Wallet top-up QR creation failed", error)
                                }
                            isCreatingTopup = false
                        }
                    }
                }
            },
            onDismiss = {
                showTopupDialog = false
                topupQr = null
                topupStatus = null
                topupError = null
                krwTopupQuote = null
            }
        )
    }

    LaunchedEffect(topupQr?.referenceNo) {
        val referenceNo = topupQr?.referenceNo ?: return@LaunchedEffect
        val service = settings.createSwiftPayService()
        repeat(60) {
            delay(5_000)
            val result = service.getWalletTopupStatus(referenceNo)
            val status = result.getOrElse { error ->
                topupError = error.message ?: "Could not check top-up status. Refresh the wallet to try again."
                Log.e("WalletScreen", "Wallet top-up status check failed", error)
                return@LaunchedEffect
            }
            topupStatus = status
            if (status in setOf("EXECUTED", "REJECTED", "CANCELED", "EXPIRED", "ERROR")) {
                if (status == "EXECUTED") {
                    viewModel.refreshBalance()
                    val history = service.getWalletTransactionHistory()
                    remoteTransactions = history.transactions
                    transactionHistoryError = history.errors.takeIf { it.isNotEmpty() }?.joinToString("; ")
                }
                return@LaunchedEffect
            }
        }
    }
}

@Composable
private fun WalletTopupDialog(
    amount: String,
    currency: String,
    paymentApp: String,
    quote: BackendKrwPhpQrQuoteResponse?,
    topup: SwiftPayQrResponse?,
    status: String?,
    error: String?,
    isLoading: Boolean,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onPaymentAppChange: (String) -> Unit,
    onGetQuote: () -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    val qrBitmap = remember(topup?.qrImageBase64) {
        topup?.qrImageBase64?.let { encoded ->
            try {
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (topup != null) "Scan to top up" else if (currency == "KRW") "Exchange KRW" else "Add money") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (topup == null) {
                    Text("Choose a payment currency. Your personal PHP wallet is credited only after SwiftPay verifies settlement.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = currency == "KRW",
                            onClick = { onCurrencyChange("KRW") },
                            label = { Text("KRW → PHP") }
                        )
                        FilterChip(
                            selected = currency == "PHP",
                            onClick = { onCurrencyChange("PHP") },
                            label = { Text("PHP top-up") }
                        )
                    }
                    OutlinedTextField(
                        value = amount,
                        onValueChange = onAmountChange,
                        label = { Text(if (currency == "KRW") "Amount (KRW)" else "Amount (PHP)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (currency == "KRW") KeyboardType.Number else KeyboardType.Decimal
                        ),
                        singleLine = true
                    )
                    if (currency == "KRW") {
                        Text("Pay in KRW with one of these Korean payment apps:")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("TOSS", "KAKAO", "KBANK").forEach { app ->
                                FilterChip(
                                    selected = paymentApp == app,
                                    onClick = { onPaymentAppChange(app) },
                                    label = {
                                        Text(
                                            when (app) {
                                                "TOSS" -> "Toss"
                                                "KAKAO" -> "Kakao"
                                                else -> "KBank"
                                            }
                                        )
                                    }
                                )
                            }
                        }
                        if (quote != null) {
                            Text("₩${quote.customerAmountKrw.toLocaleAmount()} → PHP ${"%.2f".format(Locale.US, quote.settlementAmountPhp)}")
                            Text("Rate: 1 KRW = PHP ${"%.6f".format(Locale.US, quote.ratePhpPerKrw)} · quote valid for 5 minutes")
                        }
                    }
                } else {
                    if (topup.customerAmountKrw != null) {
                        Text(
                            "Pay ₩${topup.customerAmountKrw.toLocaleAmount()} using ${paymentApp.displayPaymentApp()}. " +
                                "PHP ${"%.2f".format(Locale.US, topup.amount ?: 0.0)} is credited after SwiftPay confirms settlement."
                        )
                    } else {
                        Text("Scan this QR Ph code to pay PHP ${"%.2f".format(Locale.US, topup.amount ?: 0.0)}.")
                    }
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "QR Ph code to top up your wallet",
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text("The QR image could not be displayed. Close this dialog and create a new QR.")
                    }
                    Text("Wallet credit is added only after the signed SwiftPay settlement webhook confirms payment.")
                    Text("Status: ${(status ?: "PENDING").lowercase().replaceFirstChar(Char::uppercase)}")
                }
                if (!error.isNullOrBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (topup == null) {
                val action = if (currency == "KRW" && quote == null) onGetQuote else onCreate
                val label = when {
                    isLoading -> null
                    currency == "KRW" && quote == null -> "Get live quote"
                    currency == "KRW" -> "Create KRW QR"
                    else -> "Create QR"
                }
                Button(onClick = action, enabled = !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(label.orEmpty())
                    }
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (topup == null) {
                Row {
                    if (currency == "KRW" && quote != null) {
                        TextButton(onClick = onGetQuote, enabled = !isLoading) { Text("Refresh quote") }
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    )
}

private fun Int.toLocaleAmount(): String = String.format(Locale.US, "%,d", this)

private fun String.displayPaymentApp(): String = when (this) {
    "KAKAO" -> "Kakao"
    "KBANK" -> "KBank"
    else -> "Toss"
}

@Composable
fun WalletListContent(
    balance: Double?,
    adjustedBalance: Double? = null,
    accountNumber: String? = null,
    transactions: List<InternalTransaction>,
    selectedFilter: String,
    transactionHistoryError: String? = null,
    isHistoryLoading: Boolean = false,
    isInternalMerchantWallet: Boolean = false,
    isLedgerWallet: Boolean = false,
    showDepositAccount: Boolean = true,
    isCustomerWallet: Boolean = false,
    onTopupClick: () -> Unit = {},
    onFilterSelected: (String) -> Unit,
    onTransactionClick: (InternalTransaction) -> Unit,
    viewModel: MiniAppViewModel? = null
) {
    val transactionStatusMap = viewModel?.transactionStatusMap ?: emptyMap()

    LaunchedEffect(transactions) {
        val pendingIds = transactions
            .filter { it.status.uppercase() == "PENDING" }
            .map { it.transactionId }
        
        if (pendingIds.isNotEmpty()) {
            viewModel?.fetchPaymentStatuses(pendingIds)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "wallet-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Your money",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Manage your balance and recent activity",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item(key = "wallet-balance") {
            PremiumBalanceCard(
                balance = balance,
                adjustedBalance = adjustedBalance,
                accountNumber = accountNumber,
                isInternalMerchantWallet = isInternalMerchantWallet,
                isLedgerWallet = isLedgerWallet,
                showDepositAccount = showDepositAccount,
                errorMessage = viewModel?.walletBalanceError,
                onRetry = { viewModel?.refreshBalance() }
            )
        }
        if (isInternalMerchantWallet) {
            item(key = "merchant-wallet-info") {
                Text(
                    text = "This is your merchant's app-managed ledger on a shared SwiftPay provider account. It is not a separate bank or virtual account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item(key = "wallet-actions") {
            QuickActionsRow(
                isCustomerWallet = isCustomerWallet,
                onCustomerTopupClick = onTopupClick
            )
        }
        item(key = "transaction-heading") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Payments, transfers, and deposit records",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.semantics {
                        contentDescription = "${transactions.size} transactions"
                    }
                ) {
                    Text(
                        text = transactions.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                }
            }
        }
        item(key = "transaction-filters") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val filters = listOf("All", "Income", "Expense", "Bank Deposits", "Failed")
                items(filters) { filter ->
                    PremiumFilterChip(
                        selected = selectedFilter == filter,
                        text = filter,
                        onClick = { onFilterSelected(filter) }
                    )
                }
            }
        }
        transactionHistoryError?.let { error ->
            item(key = "transaction-history-error") {
                Text(
                    text = if (transactions.isEmpty()) {
                        "Transaction history could not be loaded. $error"
                    } else {
                        "Some transaction history could not be loaded. $error"
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp)
                )
            }
        }
        if (transactions.isEmpty() && selectedFilter == "All" && isHistoryLoading) {
            item(key = "transaction-history-loading") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Loading transaction history…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (transactions.isEmpty() && selectedFilter == "All" && transactionHistoryError != null) {
            item(key = "transaction-history-retry") {
                Text(
                    text = "Refresh to try loading your transaction history again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else if (transactions.isEmpty() && selectedFilter == "All") {
            item(key = "transaction-history-empty") { EmptyHistoryState() }
        } else if (transactions.isEmpty()) {
            item(key = "filtered-transaction-history-empty") {
                EmptyFilteredHistoryState(selectedFilter)
            }
        } else {
            items(transactions, key = { it.transactionId }) { tx ->
                AnimatedTransactionItem(
                    tx = tx,
                    onClick = { onTransactionClick(tx) },
                    viewModel = viewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DrPayMarkBadge(size = 30.dp)
                        Text(
                            text = "Crypto",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        CryptoWalletContent(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun CryptoWalletContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = FastPayNavy
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.12f), modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.CurrencyBitcoin, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("USDT wallet", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("TRON network", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.72f))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("External wallet", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                Text("Balance is not connected", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(
                    "SwiftPay does not custody USDT or read an on-chain balance. Buy and sell orders open in Transak.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }
        UsdtTradePanel()
        Text(
            "Review the destination address, network, quote and fees in Transak before confirming a trade.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun UsdtTradePanel() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("BUY") }
    var amountInput by remember { mutableStateOf("") }
    var walletAddress by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CurrencyExchange, contentDescription = null, tint = FastPayBlue)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("USDT on TRON", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                    Text("Buy or sell through Transak", style = MaterialTheme.typography.bodySmall, color = secondaryTextColor)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == "BUY",
                    onClick = { mode = "BUY"; errorMessage = null },
                    label = { Text("Buy") },
                    leadingIcon = if (mode == "BUY") ({ Icon(Icons.Rounded.Add, contentDescription = null) }) else null
                )
                FilterChip(
                    selected = mode == "SELL",
                    onClick = { mode = "SELL"; errorMessage = null },
                    label = { Text("Sell") },
                    leadingIcon = if (mode == "SELL") ({ Icon(Icons.Rounded.SouthWest, contentDescription = null) }) else null
                )
            }

            OutlinedTextField(
                value = amountInput,
                onValueChange = { value ->
                    if (value.isEmpty() || value.matches(Regex("^\\d{0,9}(\\.\\d{0,2})?$"))) amountInput = value
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (mode == "BUY") "Spend (PHP)" else "Sell (USDT)") },
                leadingIcon = { Text(if (mode == "BUY") "₱" else "₮", fontWeight = FontWeight.SemiBold) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
            )

            if (mode == "BUY") {
                OutlinedTextField(
                    value = walletAddress,
                    onValueChange = { value -> walletAddress = value.trim().take(48); errorMessage = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Your TRON wallet address") },
                    placeholder = { Text("T...") },
                    singleLine = true,
                    supportingText = { Text("Check the destination again in Transak before confirming.") }
                )
            } else {
                Text(
                    "Transak will show the deposit address, PHP payout options, and final fees before you confirm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
            }

            errorMessage?.let { message ->
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    val amount = amountInput.toDoubleOrNull()
                    if (amount == null || !amount.isFinite() || amount <= 0.0) {
                        errorMessage = "Enter a valid ${if (mode == "BUY") "PHP" else "USDT"} amount."
                        return@Button
                    }
                    if (mode == "BUY" && !walletAddress.matches(Regex("^T[1-9A-HJ-NP-Za-km-z]{33}$"))) {
                        errorMessage = "Enter a valid TRON mainnet wallet address."
                        return@Button
                    }
                    scope.launch {
                        isSubmitting = true
                        errorMessage = null
                        try {
                            val service = settingsManager.createSwiftPayService()
                            service.createCryptoWidgetUrl(mode, amount, walletAddress.takeIf { mode == "BUY" })
                                .onSuccess { session -> uriHandler.openUri(session.widgetUrl) }
                                .onFailure { failure -> errorMessage = failure.message ?: "Could not open Transak checkout." }
                        } catch (exception: Exception) {
                            errorMessage = exception.message ?: "Could not open Transak checkout."
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (isSubmitting) "Opening Transak..." else "Continue with Transak")
            }

            Text(
                "Transak handles checkout and verification. This app does not custody USDT.",
                style = MaterialTheme.typography.labelSmall,
                color = secondaryTextColor
            )
        }
    }
}

@Composable
fun PremiumBalanceCard(
    balance: Double?,
    adjustedBalance: Double? = null,
    accountNumber: String? = null,
    isInternalMerchantWallet: Boolean = false,
    isLedgerWallet: Boolean = false,
    showDepositAccount: Boolean = true,
    errorMessage: String? = null,
    onRetry: () -> Unit = {}
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var isBalanceVisible by remember { mutableStateOf(true) }
    val formattedBalance = balance?.let { "₱ ${"%,.2f".format(it)}" } ?: "Unavailable"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = FastPayNavy,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val brush = Brush.linearGradient(
                        colors = listOf(FastPayNavy, FastPayDarkNavy),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    )
                    drawRect(brush)
                    
                    drawCircle(
                        color = Color.White.copy(alpha = 0.04f),
                        radius = size.width * 0.7f,
                        center = Offset(size.width * 0.95f, size.height * 0.1f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.02f),
                        radius = size.width * 0.5f,
                        center = Offset(0f, size.height)
                    )
                }
                .padding(horizontal = 20.dp, vertical = 19.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when {
                            isInternalMerchantWallet -> "MERCHANT APP WALLET"
                            isLedgerWallet -> "PERSONAL APP WALLET"
                            else -> "SHARED SWIFTPAY PROVIDER BALANCE"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.74f),
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { isBalanceVisible = !isBalanceVisible },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                stateDescription = if (isBalanceVisible) "Balance visible" else "Balance hidden"
                            }
                    ) {
                        Icon(
                            imageVector = if (isBalanceVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = if (isBalanceVisible) "Hide balance" else "Show balance",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                Text(
                    text = when {
                        balance == null -> formattedBalance
                        isBalanceVisible -> formattedBalance
                        else -> "₱ ••••••"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 34.sp,
                    letterSpacing = (-0.7).sp,
                    maxLines = 2,
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .then(
                            if (balance != null && !isBalanceVisible) {
                                Modifier.semantics { contentDescription = "Balance hidden" }
                            } else Modifier
                        )
                )
                adjustedBalance?.takeUnless { isLedgerWallet }?.let {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reconciliation reference",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (isBalanceVisible) "₱ ${"%,.2f".format(it)}" else "₱ ••••••",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        "Includes admin adjustments; not the provider-reported balance or available funds.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.66f)
                    )
                }
                errorMessage?.let {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(start = 12.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFFFC7C7),
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 2,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                        TextButton(onClick = onRetry) {
                            Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.17f),
                    modifier = Modifier.padding(top = 3.dp)
                )
                if (isLedgerWallet) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.82f),
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.padding(start = 10.dp)) {
                            Text(
                                text = if (isInternalMerchantWallet) "Merchant ledger" else "Personal wallet",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.68f)
                            )
                            Text(
                                text = if (isInternalMerchantWallet) "Per-merchant app balance" else "App-managed balance",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else if (showDepositAccount) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .then(
                                if (!accountNumber.isNullOrBlank()) {
                                    Modifier
                                        .semantics(mergeDescendants = true) {
                                            contentDescription = "Deposit account, $accountNumber. Double tap to copy."
                                        }
                                        .clickable(role = Role.Button) {
                                            clipboardManager.setText(AnnotatedString(accountNumber))
                                            Toast.makeText(context, "Account number copied to clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                } else Modifier
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.AccountBalance,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.82f),
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(
                                text = "Deposit account",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.68f)
                            )
                            Text(
                                text = accountNumber?.takeIf { it.isNotBlank() } ?: "Loading account number…",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!accountNumber.isNullOrBlank()) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = "Copy account number",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionsRow(
    isCustomerWallet: Boolean,
    onCustomerTopupClick: () -> Unit
) {
    val navController = LocalNavController.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionItem(
            Icons.Rounded.Payments,
            if (isCustomerWallet) "Add money" else "Collect payment",
            if (isCustomerWallet) "Top up your personal wallet" else "Create a QR Ph collection",
            FastPayBlue,
            Modifier.weight(1f)
        ) {
            if (isCustomerWallet) {
                onCustomerTopupClick()
            } else {
                navController.navigate(Route.MiniApp(initialPath = "collect-institution-page"))
            }
        }
        QuickActionItem(Icons.AutoMirrored.Rounded.Send, "Transfer", "Send money", FastPayAccent, Modifier.weight(1f)) {
            navController.navigate(Route.MiniApp("disbursement-page"))
        }
    }
}

@Composable
fun QuickActionItem(
    icon: ImageVector,
    label: String,
    description: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color.copy(alpha = 0.1f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun PremiumFilterChip(selected: Boolean, text: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.labelLarge) },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun AnimatedRefreshButton(isLoading: Boolean, onClick: () -> Unit) {
    val rotation = remember { Animatable(0f) }
    val reduceMotion = rememberReducedMotion()
    LaunchedEffect(isLoading, reduceMotion) {
        if (isLoading && !reduceMotion) {
            while (true) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(900, easing = LinearEasing)
                )
            }
        } else {
            rotation.snapTo(0f)
        }
    }
    IconButton(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.semantics {
            stateDescription = if (isLoading) "Refreshing" else "Ready"
        }
    ) {
        Icon(
            Icons.Rounded.Refresh,
            contentDescription = if (isLoading) "Refreshing wallet" else "Refresh wallet",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.rotate(rotation.value)
        )
    }
}

@Composable
fun AnimatedTransactionItem(tx: InternalTransaction, onClick: () -> Unit, viewModel: MiniAppViewModel? = null) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(350, easing = EaseOutCubic),
        label = "txAlpha"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 8f,
        animationSpec = tween(350, easing = EaseOutCubic),
        label = "txOffsetY"
    )
    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            this.translationY = offsetY
        }
    ) {
        TransactionItem(tx = tx, onClick = onClick, viewModel = viewModel)
    }
}

internal fun isFailedTransactionStatus(status: String): Boolean {
    val normalized = status.uppercase()
    return normalized.contains("FAIL") ||
        normalized.contains("REJECT") ||
        normalized.contains("ERROR") ||
        normalized.contains("EXPIRED") ||
        normalized.contains("CANCEL")
}

internal fun isPendingTransactionStatus(status: String): Boolean {
    val normalized = status.uppercase()
    return normalized.contains("PEND") ||
        normalized.contains("PROCESS") ||
        normalized.contains("WAIT")
}

internal fun isSuccessfulTransactionStatus(status: String): Boolean {
    val normalized = status.uppercase()
    return normalized.contains("SUCCESS") ||
        normalized == "EXECUTED" ||
        normalized.contains("COMPLETED") ||
        normalized.contains("PAID")
}

internal fun filterWalletTransactions(
    transactions: List<InternalTransaction>,
    selectedFilter: String,
    statusMap: Map<String, SwiftPayStatusResponse>
): List<InternalTransaction> = when (selectedFilter) {
    "Income" -> transactions.filter { it.amount > 0 }
    "Expense" -> transactions.filter { it.amount < 0 }
    "Bank Deposits" -> transactions.filter {
        it.transactionType.equals("MANUAL_BANK_DEPOSIT", ignoreCase = true) ||
            it.transactionId.startsWith("DEP") ||
            it.transactionId.contains("NETBANK") ||
            (it.amount > 0 && it.transactionId.startsWith("VA"))
    }
    "Failed" -> transactions.filter { transaction ->
        val response = statusMap[transaction.transactionId]
        isFailedTransactionStatus(
            response?.status ?: response?.paymentStatus ?: transaction.status
        )
    }
    else -> transactions
}

@Composable
fun TransactionItem(tx: InternalTransaction, onClick: () -> Unit, viewModel: MiniAppViewModel? = null) {
    val isExpense = tx.amount < 0
    val isManualBankDeposit = tx.transactionType.equals("MANUAL_BANK_DEPOSIT", ignoreCase = true)
    val normalizedStatus = tx.status.uppercase()
    val apiPaymentStatus = viewModel?.getPaymentStatusForId(tx.transactionId)
    val apiStatus = (apiPaymentStatus?.status ?: apiPaymentStatus?.paymentStatus)?.uppercase()
    val statusForDisplay = apiStatus ?: normalizedStatus
    val isFailed = isFailedTransactionStatus(statusForDisplay)
    val isPending = isPendingTransactionStatus(statusForDisplay)
    val isSuccessful = isSuccessfulTransactionStatus(statusForDisplay)
    val isCancelled = statusForDisplay.contains("CANCEL")
    val isExpired = statusForDisplay.contains("EXPIRED")
    val transactionType = tx.transactionType?.uppercase()
    val transactionTitle = when {
        isManualBankDeposit -> "Bank deposit"
        isCancelled && isExpense -> "Transfer cancelled"
        isCancelled -> "Payment cancelled"
        isExpired && isExpense -> "Transfer expired"
        isExpired -> "Payment expired"
        isFailed && isExpense -> "Transfer failed"
        isFailed -> "Payment failed"
        isPending && isExpense -> "Transfer pending"
        isPending -> "Payment pending"
        isSuccessful && (transactionType == "DISBURSEMENT" || isExpense) -> "Bank transfer"
        isSuccessful && transactionType == "PAYMENT" -> "Payment received"
        transactionType == "DISBURSEMENT" || isExpense -> "Transfer status update"
        transactionType == "PAYMENT" -> "Payment status update"
        else -> "Wallet transaction"
    }
    val transactionReference = tx.referenceNo?.takeIf { it.isNotBlank() }

    val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
    val successColor = if (isDarkTheme) Color(0xFF8BE0AC) else FastPaySuccess
    val warningColor = if (isDarkTheme) Color(0xFFFFD18A) else FastPayWarning
    val (icon, statusColor) = when {
        isFailed || apiStatus?.contains("FAIL") == true -> Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
        isPending -> Icons.Rounded.Schedule to warningColor
        isSuccessful && isExpense -> Icons.Rounded.NorthEast to MaterialTheme.colorScheme.onSurface
        isSuccessful -> Icons.Rounded.SouthWest to successColor
        else -> Icons.Rounded.Info to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val itemScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "txScale"
    )

    val surfaceColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(itemScale)
            .clickable(
                interactionSource = interactionSource,
                role = Role.Button,
                onClick = onClick
            ),
        shape = RoundedCornerShape(18.dp),
        color = surfaceColor,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isPressed) FastPayBlue.copy(alpha = 0.38f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(statusColor.copy(alpha = 0.09f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, 
                    contentDescription = null, 
                    tint = statusColor, 
                    modifier = Modifier.size(23.dp)
                )
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transactionTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
                Text(
                    text = transactionReference ?: tx.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (transactionReference != null) {
                    Text(
                        text = tx.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (!isExpense && !isFailed) "+" else ""}₱ ${"%,.2f".format(abs(tx.amount))}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = when {
                        isFailed -> MaterialTheme.colorScheme.error
                        isExpense -> textColor
                        else -> successColor
                    }
                )
                
                Spacer(Modifier.height(4.dp))
                
                FastPayStatusBadge(
                    status = apiStatus ?: normalizedStatus,
                    modifier = Modifier.scale(0.85f)
                )
            }
        }
    }
}

@Composable
fun TransactionDetailScreen(
    tx: InternalTransaction,
    onShowSnackbar: (String) -> Unit = {},
    viewModel: MiniAppViewModel? = null
) {
    val apiPaymentStatus = viewModel?.transactionStatusMap?.get(tx.transactionId)
    
    LaunchedEffect(tx.transactionId) {
        if (!tx.transactionType.equals("MANUAL_BANK_DEPOSIT", ignoreCase = true)) {
            viewModel?.fetchPaymentStatus(tx.transactionId)
        }
    }
    
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val bgColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val isExpense = tx.amount < 0
        val isManualBankDeposit = tx.transactionType.equals("MANUAL_BANK_DEPOSIT", ignoreCase = true)
        val statusToDisplay = (apiPaymentStatus?.status ?: apiPaymentStatus?.paymentStatus)?.uppercase()
            ?: tx.status.uppercase()
        val isFailed = isFailedTransactionStatus(statusToDisplay)
        val isPending = isPendingTransactionStatus(statusToDisplay)
        val isSuccessful = isSuccessfulTransactionStatus(statusToDisplay)
        val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
        val color = when {
            isFailed -> MaterialTheme.colorScheme.error
            isPending -> if (isDarkTheme) Color(0xFFFFD18A) else FastPayWarning
            isSuccessful && isExpense -> MaterialTheme.colorScheme.onSurface
            isSuccessful ->
                if (isDarkTheme) Color(0xFF8BE0AC) else FastPaySuccess
            else -> MaterialTheme.colorScheme.onSurface
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = surfaceColor,
            shadowElevation = 2.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DrPayLogo(size = 68.dp)
                
                Spacer(Modifier.height(20.dp))
                
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(color.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpense) Icons.Rounded.NorthEast else Icons.Rounded.SouthWest,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(30.dp)
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    text = when {
                        isManualBankDeposit -> "Manual Bank Deposit Received"
                        statusToDisplay.contains("CANCEL") && isExpense -> "Transfer Cancelled"
                        statusToDisplay.contains("CANCEL") -> "Payment Cancelled"
                        statusToDisplay.contains("EXPIRED") && isExpense -> "Transfer Expired"
                        statusToDisplay.contains("EXPIRED") -> "Payment Expired"
                        isFailed ->
                            if (isExpense) "Transfer Failed" else "Payment Failed"
                        isPending -> if (isExpense) "Transfer Pending" else "Payment Pending"
                        isSuccessful && isExpense -> "Transfer Completed"
                        isSuccessful -> "Payment Received"
                        isExpense -> "Transfer Status Update"
                        else -> "Payment Status Update"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                
                Text(
                    text = "₱${"%,.2f".format(abs(tx.amount))}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = textColor,
                    fontSize = 36.sp,
                    maxLines = 1
                )
                
                Spacer(Modifier.height(20.dp))
                
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                )
                
                Spacer(Modifier.height(20.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ReceiptRow("Status", statusToDisplay, color, isBadge = true)
                    ReceiptRow("Date", formatReceiptDate(tx.date), textColor)
                    ReceiptRow(
                        "Type",
                        when {
                            isManualBankDeposit -> "Manual bank deposit"
                            tx.transactionType.equals("PAYMENT", ignoreCase = true) -> "Payment received"
                            tx.transactionType.equals("DISBURSEMENT", ignoreCase = true) -> "Bank transfer"
                            isExpense -> "Business Expense"
                            else -> "Sales Revenue"
                        },
                        textColor
                    )
                    ReceiptRow("Channel", tx.bankName ?: "SwiftPay Corporate", textColor)
                    tx.description?.takeIf { it.isNotBlank() }?.let { ReceiptRow("Notes", it, textColor) }
                    
                    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.6f),
                            start = Offset(0f, 0f),
                            end = Offset(this.size.width, 0f),
                            pathEffect = pathEffect,
                            strokeWidth = 2f
                        )
                    }
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Reference No.",
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryTextColor,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = tx.referenceNo ?: tx.transactionId,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(tx.referenceNo ?: tx.transactionId))
                                    onShowSnackbar("Reference number copied")
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy transaction reference",
                                    modifier = Modifier.size(18.dp),
                                    tint = FastPayBlue
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { onShowSnackbar("Preparing to share...") },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, FastPayBlue)
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, tint = FastPayBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share", color = FastPayBlue, fontWeight = FontWeight.Bold)
            }
            
            Button(
                onClick = { onShowSnackbar("Generating PDF receipt...") },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FastPayNavy)
            ) {
                Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save receipt", fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        
        TextButton(
            onClick = { onShowSnackbar("Opening Priority Support...") },
            modifier = Modifier.padding(top = 0.dp, bottom = 8.dp)
        ) {
            Text(
                text = "Need help? Contact Support",
                color = FastPayBlue,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ReceiptRow(label: String, value: String, valueColor: Color, isBadge: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.8f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (isBadge) {
            Surface(
                modifier = Modifier.weight(1.2f, fill = false),
                color = valueColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = value,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = valueColor,
                    textAlign = TextAlign.End
                )
            }
        } else {
            Text(
                value,
                modifier = Modifier.weight(1.2f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DetailRowWithIcon(icon: ImageVector, label: String, value: String) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, Modifier.size(20.dp), tint = secondaryTextColor)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = secondaryTextColor)
        }
        Text(
            text = value, 
            style = MaterialTheme.typography.bodyLarge, 
            fontWeight = FontWeight.Bold, 
            color = textColor,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 180.dp)
        )
    }
}

@Composable
fun EmptyDetailState() {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
            Surface(
                shape = CircleShape,
                color = surfaceColor,
                modifier = Modifier.size(100.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Search, 
                        contentDescription = null, 
                        modifier = Modifier.size(48.dp),
                        tint = textColor.copy(alpha = 0.1f)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "No Transaction Selected",
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Select a record from the list to view its full details and receipt.",
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryTextColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EmptyHistoryState() {
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.Inbox, 
            contentDescription = null, 
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No Transactions Found",
            style = MaterialTheme.typography.bodyLarge,
            color = secondaryTextColor,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Your sales history will appear here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EmptyFilteredHistoryState(filter: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(27.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No $filter transactions",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Try another filter to see more activity.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
