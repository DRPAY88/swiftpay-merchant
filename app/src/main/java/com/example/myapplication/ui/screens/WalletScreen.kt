package com.example.myapplication.ui.screens

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.api.InternalTransaction
import com.example.myapplication.data.api.VaultPaymentResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import com.example.myapplication.ui.components.DrPayLogo
import com.example.myapplication.ui.components.DrPayMarkBadge
import kotlin.math.abs

import com.example.myapplication.LocalNavController
import com.example.myapplication.navigation.Route
import com.example.myapplication.data.SettingsManager
import com.example.myapplication.data.TransactionStore
import com.example.myapplication.data.createSwiftPayService
import com.example.myapplication.data.mergeTransactions
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.theme.*

import com.example.myapplication.ui.components.TransactionStatusIndicator
import com.example.myapplication.ui.components.SectionHeader
import com.example.myapplication.ui.components.FastPayStatusBadge

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun WalletScreen(
    modifier: Modifier = Modifier,
    viewModel: MiniAppViewModel = viewModel()
) {
    val balance = viewModel.walletBalance
    val adjustedBalance = viewModel.walletAdjustedBalance
    var transactions by remember { mutableStateOf<List<InternalTransaction>>(emptyList()) }
    var transactionHistoryError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedWallet by remember { mutableStateOf("PHP") }
    val scope = rememberCoroutineScope()

    val navigator = rememberListDetailPaneScaffoldNavigator<String>()

    val bgColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onBackground

    val filteredTransactions = remember(transactions, selectedFilter) {
        when (selectedFilter) {
            "Income" -> transactions.filter { it.amount > 0 }
            "Expense" -> transactions.filter { it.amount < 0 }
            "Failed" -> transactions.filter { it.status.uppercase() == "FAILED" }
            else -> transactions
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshBalance()
        launch {
            try {
                val context = viewModel.getApplication<Application>()
                val settings = SettingsManager(context)
                val transactionStore = TransactionStore(context)
                val service = settings.createSwiftPayService()

                transactionStore.transactions.collectLatest { localTransactions ->
                    val history = service.getWalletTransactionHistory()
                    transactions = mergeTransactions(
                        localTransactions,
                        history.transactions
                    )
                    transactionHistoryError = history.errors.takeIf { it.isNotEmpty() }?.joinToString("; ")
                }
            } catch (e: Exception) {
                Log.e("WalletScreen", "Error loading transactions", e)
            }
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
                                try {
                                    viewModel.refreshBalance()
                                    val context = viewModel.getApplication<Application>()
                                    val settings = SettingsManager(context)
                                    val transactionStore = TransactionStore(context)
                                    val service = settings.createSwiftPayService()
                                    val localTransactions = transactionStore.transactions.first()
                                    val history = service.getWalletTransactionHistory()
                                    transactions = mergeTransactions(
                                        localTransactions,
                                        history.transactions
                                    )
                                    transactionHistoryError = history.errors.takeIf { it.isNotEmpty() }?.joinToString("; ")
                                    delay(800L)
                                } catch (e: Exception) {
                                    Log.e("WalletScreen", "Refresh Error", e)
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    )
                }
            )
        }
    ) { innerPadding ->
        val appContext = viewModel.getApplication<Application>()
        val settings = remember { SettingsManager(appContext) }
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
                    accountNumber = depositAccountNumber,
                    transactions = filteredTransactions,
                    selectedFilter = selectedFilter,
                    transactionHistoryError = transactionHistoryError,
                    selectedWallet = selectedWallet,
                    onWalletSelected = { selectedWallet = it },
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
}

@Composable
fun WalletListContent(
    balance: Double?,
    adjustedBalance: Double? = null,
    accountNumber: String? = null,
    transactions: List<InternalTransaction>,
    selectedFilter: String,
    selectedWallet: String = "PHP",
    transactionHistoryError: String? = null,
    onWalletSelected: (String) -> Unit = {},
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        WalletTypeSelector(selected = selectedWallet, onSelect = onWalletSelected)
        Spacer(Modifier.height(18.dp))

        when (selectedWallet) {
            "USDT" -> CryptoWalletContent(modifier = Modifier.weight(1f))
            else -> {
                PremiumBalanceCard(
                    balance = balance,
                    adjustedBalance = adjustedBalance,
                    accountNumber = accountNumber,
                    errorMessage = viewModel?.walletBalanceError,
                    onRetry = { viewModel?.refreshBalance() }
                )

                Spacer(Modifier.height(18.dp))
                QuickActionsRow()
                Spacer(Modifier.height(22.dp))
                SectionHeader(
                    title = "Transaction History",
                    subtitle = "Recent activity on your PHP wallet",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val filters = listOf("All", "Income", "Expense", "Failed")
                    items(filters) { filter ->
                        PremiumFilterChip(
                            selected = selectedFilter == filter,
                            text = filter,
                            onClick = { onFilterSelected(filter) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    transactionHistoryError?.let { error ->
                        item {
                            Text(
                                text = "Some transaction history could not be loaded: $error",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (transactions.isEmpty()) {
                        item { EmptyHistoryState() }
                    } else {
                        items(transactions, key = { it.transactionId }) { tx ->
                            AnimatedTransactionItem(tx = tx, onClick = { onTransactionClick(tx) }, viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletTypeSelector(selected: String, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("PHP" to "PHP", "USDT" to "USDT").forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label, maxLines = 1) },
                modifier = Modifier.weight(1f)
            )
        }
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
    errorMessage: String? = null,
    onRetry: () -> Unit = {}
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var isBalanceVisible by remember { mutableStateOf(true) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 204.dp),
        shape = RoundedCornerShape(28.dp),
        color = FastPayNavy,
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
                .padding(horizontal = 22.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "PHP BALANCE",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { isBalanceVisible = !isBalanceVisible }, modifier = Modifier.size(32.dp)) {
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
                        balance == null -> "Unavailable"
                        isBalanceVisible -> "₱ ${"%,.2f".format(balance)}"
                        else -> "₱ ••••••"
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontSize = 32.sp,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (adjustedBalance != null || errorMessage != null) {
                        Column(modifier = Modifier.weight(1.15f)) {
                            adjustedBalance?.let {
                                Text(
                                    text = "ADJUSTED BALANCE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.62f),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp
                                )
                                Text(
                                    text = "₱ ${"%,.2f".format(it)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1
                            )
                        }
                        }
                    }
                    if (errorMessage != null) {
                        TextButton(onClick = onRetry) {
                            Text("RETRY", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = FastPayGreen.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, FastPayGreen.copy(alpha = 0.3f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(FastPayGreen, CircleShape)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "ACTIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = FastPayGreen,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text("DEPOSIT ACCOUNT", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.62f), fontWeight = FontWeight.Bold)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        if (!accountNumber.isNullOrBlank()) {
                                            clipboardManager.setText(AnnotatedString(accountNumber))
                                            Toast.makeText(context, "Account number copied to clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = accountNumber?.takeIf { it.isNotBlank() } ?: "Loading...",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                if (!accountNumber.isNullOrBlank()) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy account number", tint = Color.White, modifier = Modifier.size(13.dp))
                                }
                            }
                            Text("Tap to copy", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.48f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionsRow() {
    val navController = LocalNavController.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickActionItem(Icons.Rounded.Payments, "Collect", FastPayBlue) {
            navController.navigate(Route.MiniApp(null))
        }
        QuickActionItem(Icons.AutoMirrored.Rounded.Send, "Transfer", FastPayAccent) {
            navController.navigate(Route.MiniApp("disbursement-page"))
        }
        QuickActionItem(Icons.Rounded.MoreHoriz, "More", Color.LightGray) {
            navController.navigate(Route.MiniApp())
        }
    }
}

@Composable
fun QuickActionItem(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(60.dp),
            shape = RoundedCornerShape(12.dp),
            color = surfaceColor,
            shadowElevation = if (isDarkTheme) 0.dp else 2.dp,
            onClick = onClick
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = textColor)
    }
}

@Composable
fun PremiumFilterChip(selected: Boolean, text: String, onClick: () -> Unit) {
    val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
    val unselectedBg = MaterialTheme.colorScheme.surface
    val selectedBg = if (isDarkTheme) MaterialTheme.colorScheme.primary else FastPayNavy
    val unselectedText = MaterialTheme.colorScheme.onSurface
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    val alpha by animateFloatAsState(if (selected) 1f else 0.72f, label = "alpha")
    
    Surface(
        color = if (selected) selectedBg else unselectedBg,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = if (selected && !isDarkTheme) 1.dp else 0.dp,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .clickable { onClick() }
            .alpha(alpha)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else unselectedText
        )
    }
}

@Composable
fun AnimatedRefreshButton(isLoading: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "refresh")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "refreshRotation"
    )
    IconButton(onClick = onClick) {
        Icon(
            Icons.Rounded.Refresh,
            contentDescription = "Refresh",
            tint = FastPayNavy,
            modifier = if (isLoading) Modifier.rotate(rotation) else Modifier
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
    val offsetX by animateFloatAsState(
        targetValue = if (visible) 0f else 30f,
        animationSpec = tween(350, easing = EaseOutCubic),
        label = "txOffset"
    )
    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            this.translationX = offsetX
        }
    ) {
        TransactionItem(tx = tx, onClick = onClick, viewModel = viewModel)
    }
}

@Composable
fun TransactionItem(tx: InternalTransaction, onClick: () -> Unit, viewModel: MiniAppViewModel? = null) {
    val isExpense = tx.amount < 0
    val isManualBankDeposit = tx.transactionType.equals("MANUAL_BANK_DEPOSIT", ignoreCase = true)
    val normalizedStatus = tx.status.uppercase()
    val apiPaymentStatus = viewModel?.getPaymentStatusForId(tx.transactionId)
    val apiStatus = apiPaymentStatus?.status?.uppercase()
    val statusForDisplay = apiStatus ?: normalizedStatus
    val isFailed = statusForDisplay.contains("FAIL") || statusForDisplay.contains("REJECT") || statusForDisplay == "ERROR"
    val isPending = statusForDisplay.contains("PEND")

    val (icon, statusColor) = when {
        isFailed || apiStatus?.contains("FAIL") == true -> Icons.Rounded.ErrorOutline to Color(0xFFEF4444)
        isPending || apiStatus?.contains("PEND") == true -> Icons.Rounded.Schedule to Color(0xFFF59E0B)
        isExpense -> Icons.Rounded.NorthEast to FastPayNavy
        else -> Icons.Rounded.SouthWest to Color(0xFF10B981)
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
    val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(itemScale)
            .clickable(
                interactionSource = interactionSource, 
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        color = surfaceColor,
        shadowElevation = if (isPressed) 1.dp else 4.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            if (isPressed) FastPayBlue.copy(alpha = 0.1f) else Color.Transparent
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
                    .size(52.dp)
                    .background(statusColor.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, 
                    contentDescription = null, 
                    tint = statusColor, 
                    modifier = Modifier.size(26.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isManualBankDeposit -> "Manual bank deposit"
                        isFailed && isExpense -> "Transfer failed"
                        isFailed -> "Payment failed"
                        isPending && isExpense -> "Transfer pending"
                        isPending -> "Payment pending"
                        isExpense -> "Transfer completed"
                        else -> "Payment received"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = tx.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (!isExpense && !isFailed) "+" else ""}₱ ${"%,.2f".format(abs(tx.amount))}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = if (!isExpense && !isFailed) Color(0xFF10B981) else FastPayNavy
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
    var apiPaymentStatus by remember { mutableStateOf<VaultPaymentResponse?>(null) }
    
    LaunchedEffect(tx.transactionId) {
        if (!tx.transactionType.equals("MANUAL_BANK_DEPOSIT", ignoreCase = true)) {
            viewModel?.fetchPaymentStatus(tx.transactionId)
            apiPaymentStatus = viewModel?.getPaymentStatusForId(tx.transactionId)
        }
    }
    
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val bgColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        val isExpense = tx.amount < 0
        val isManualBankDeposit = tx.transactionType.equals("MANUAL_BANK_DEPOSIT", ignoreCase = true)
        val statusToDisplay = apiPaymentStatus?.status?.uppercase() ?: tx.status.uppercase()
        val color = when {
            statusToDisplay.contains("FAIL") -> FastPayRed
            isExpense -> FastPayNavy
            statusToDisplay.contains("SUCCESS") || statusToDisplay == "EXECUTED" -> FastPayGreen
            statusToDisplay.contains("PENDING") -> FastPayOrange
            else -> FastPayNavy
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = surfaceColor,
            shadowElevation = 2.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DrPayLogo(size = 90.dp)
                
                Spacer(Modifier.height(32.dp))
                
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(color.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpense) Icons.Rounded.NorthEast else Icons.Rounded.SouthWest,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(36.dp)
                    )
                }
                
                Spacer(Modifier.height(20.dp))
                
                Text(
                    text = when {
                        isManualBankDeposit -> "Manual Bank Deposit Received"
                        statusToDisplay.contains("FAIL") || statusToDisplay.contains("REJECT") || statusToDisplay == "ERROR" ->
                            if (isExpense) "Transfer Failed" else "Payment Failed"
                        statusToDisplay.contains("PEND") -> if (isExpense) "Transfer Pending" else "Payment Pending"
                        isExpense -> "Transfer Completed"
                        else -> "Payment Received"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                
                Text(
                    text = "₱ ${"%,.2f".format(abs(tx.amount))}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = textColor,
                    fontSize = 36.sp
                )
                
                Spacer(Modifier.height(24.dp))
                
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = Color.LightGray.copy(alpha = 0.3f)
                )
                
                Spacer(Modifier.height(24.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    ReceiptRow("Status", statusToDisplay, color, isBadge = true)
                    ReceiptRow("Date", tx.date, textColor)
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
                                fontWeight = FontWeight.Medium
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(tx.referenceNo ?: tx.transactionId))
                                    onShowSnackbar("Reference number copied")
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy",
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
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = { onShowSnackbar("Preparing to share...") },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, FastPayBlue)
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, tint = FastPayBlue)
                Spacer(Modifier.width(8.dp))
                Text("Share", color = FastPayBlue, fontWeight = FontWeight.Bold)
            }
            
            Button(
                onClick = { onShowSnackbar("Generating PDF receipt...") },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FastPayNavy)
            ) {
                Icon(Icons.Rounded.FileDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Download", fontWeight = FontWeight.Bold)
            }
        }
        
        TextButton(
            onClick = { onShowSnackbar("Opening Priority Support...") },
            modifier = Modifier.padding(top = 8.dp)
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        if (isBadge) {
            Surface(
                color = valueColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = value,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = valueColor
                )
            }
        } else {
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}

@Composable
fun DetailRowWithIcon(icon: ImageVector, label: String, value: String) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

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
    val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

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
    val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.Inbox, 
            contentDescription = null, 
            modifier = Modifier.size(64.dp),
            tint = Color.LightGray.copy(alpha = 0.5f)
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
            color = Color.Gray.copy(alpha = 0.7f)
        )
    }
}
