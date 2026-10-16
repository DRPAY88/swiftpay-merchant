package com.example.myapplication.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.TextButton
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.SettingsManager
import com.example.myapplication.data.TransactionStore
import com.example.myapplication.data.createSwiftPayService
import com.example.myapplication.data.mergeTransactions
import com.example.myapplication.data.api.InternalTransaction
import com.example.myapplication.ui.components.DrPayMarkBadge
import com.example.myapplication.ui.theme.FastPayBlue
import com.example.myapplication.ui.theme.FastPayGradient
import com.example.myapplication.ui.theme.FastPayNavy
import com.example.myapplication.ui.theme.FintechMotion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onLaunchMiniApp: (String) -> Unit,
    onNavigateToWallet: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onHelpClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val merchantAlias by settingsManager.merchantAlias.collectAsState(initial = null)
    val merchantName = merchantAlias?.takeIf { it.isNotBlank() }
        ?: "Merchant account"
    val transactionStore = remember { TransactionStore(context) }
    var balance by remember { mutableStateOf<Double?>(null) }
    var balanceError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var recentTransactions by remember { mutableStateOf<List<InternalTransaction>>(emptyList()) }
    var isLoadingTransactions by remember { mutableStateOf(true) }
    var transactionHistoryUnavailable by remember { mutableStateOf(false) }
    var isBalanceVisible by remember { mutableStateOf(true) }

    LaunchedEffect(refreshKey) {
        try {
            val service = settingsManager.createSwiftPayService()
            service.getWalletBalance()
                .onSuccess {
                    balance = it
                    balanceError = null
                }
                .onFailure { error ->
                    balanceError = error.message ?: "Could not load wallet balance."
                    Log.e("HomeScreen", "Failed to load wallet balance", error)
                }
            val localTransactions = transactionStore.transactions.first()
            val history = service.getWalletTransactionHistory()
            history.errors.forEach { error ->
                Log.e("HomeScreen", "Failed to load a transaction history source: $error")
            }
            transactionHistoryUnavailable = history.errors.isNotEmpty() && localTransactions.isEmpty()
            recentTransactions = mergeTransactions(
                localTransactions,
                history.transactions
            ).take(3)
        } catch (error: Exception) {
            Log.e("HomeScreen", "Failed to refresh wallet and transaction history", error)
            balanceError = "Could not connect to the wallet service. Check your connection and retry."
            transactionHistoryUnavailable = true
        } finally {
            isLoading = false
            isLoadingTransactions = false
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            HomeGreeting(
                merchantName = merchantName,
                onProfileClick = onProfileClick,
                onHelpClick = onHelpClick
            )
            Spacer(modifier = Modifier.height(18.dp))
            WalletBalanceCard(
                balance = balance,
                isLoading = isLoading,
                balanceError = balanceError,
                isBalanceVisible = isBalanceVisible,
                onToggleBalance = { isBalanceVisible = !isBalanceVisible },
                onBalanceRetry = {
                    isLoading = true
                    balanceError = null
                    refreshKey++
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
            QuickActionsSection(
                onCollect = { onLaunchMiniApp("topup-page") },
                onTransfer = { onLaunchMiniApp("disbursement-page") },
                onPaymentLink = { onLaunchMiniApp("payment-link-page") },
                onScanQr = { onLaunchMiniApp("scan-qr-page") },
                onDeveloperDocs = { onLaunchMiniApp("api-docs-page") },
                onProfileClick = onProfileClick,
                onTransactionsClick = onNavigateToWallet
            )
            Spacer(modifier = Modifier.height(22.dp))
            MerchantToolsBanner(onCreatePaymentLink = { onLaunchMiniApp("payment-link-page") })
            Spacer(modifier = Modifier.height(24.dp))
            TransactionHistorySection(
                transactions = recentTransactions,
                isLoading = isLoadingTransactions,
                hasError = transactionHistoryUnavailable,
                onClick = onNavigateToWallet
            )
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
fun HomeGreeting(
    merchantName: String,
    onProfileClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrPayMarkBadge(size = 42.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Welcome back",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    merchantName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.size(48.dp)
            ) {
                IconButton(onClick = onHelpClick, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.AutoMirrored.Rounded.HelpOutline, "Help and API documentation", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(21.dp))
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.size(48.dp)
            ) {
                IconButton(onClick = onProfileClick, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Rounded.AccountCircle, "Profile", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
fun WalletBalanceCard(
    balance: Double?,
    isLoading: Boolean,
    balanceError: String?,
    isBalanceVisible: Boolean,
    onToggleBalance: () -> Unit,
    onBalanceRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        shadowElevation = 10.dp
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(FastPayGradient))
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .animateContentSize(
                    animationSpec = tween(
                        FintechMotion.contentMillis,
                        easing = FintechMotion.standardEasing
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    .align(Alignment.TopEnd)
                    .offset(x = 20.dp, y = (-18).dp)
            )
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(Color.White.copy(alpha = 0.06f), CircleShape)
                    .align(Alignment.BottomStart)
                    .offset(x = (-18).dp, y = 8.dp)
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "DRPAY WALLET BALANCE",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onToggleBalance,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                stateDescription = if (isBalanceVisible) "Balance visible" else "Balance hidden"
                            }
                    ) {
                        Icon(
                            if (isBalanceVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = if (isBalanceVisible) "Hide balance" else "Show balance",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    "Balance from payment provider",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.88f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    when {
                        isLoading -> Box(
                            Modifier
                                .width(168.dp)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.22f))
                                .semantics { contentDescription = "Loading wallet balance" }
                        )
                        balance != null && isBalanceVisible -> Text(
                            text = "₱${"%,.2f".format(balance)}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 30.sp,
                            maxLines = 2
                        )
                        balance != null -> Text(
                            "₱ ••••••",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.semantics { contentDescription = "Balance hidden" }
                        )
                        else -> Text("Unavailable", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                if (!isLoading && balanceError != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text(balanceError, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 2, modifier = Modifier.weight(1f))
                        TextButton(onClick = onBalanceRetry) { Text("Retry", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionsSection(
    onCollect: () -> Unit,
    onTransfer: () -> Unit,
    onPaymentLink: () -> Unit,
    onScanQr: () -> Unit,
    onDeveloperDocs: () -> Unit,
    onProfileClick: () -> Unit,
    onTransactionsClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val actionSurface = scheme.primaryContainer
    Column(modifier = Modifier.padding(horizontal = 18.dp)) {
        Text(
            "Quick actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(Icons.Rounded.Payments, "Collect payment", actionSurface, onCollect, Modifier.weight(1f))
            QuickAction(Icons.AutoMirrored.Rounded.Send, "Send money", actionSurface, onTransfer, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(Icons.Rounded.Link, "Payment link", actionSurface, onPaymentLink, Modifier.weight(1f))
            QuickAction(Icons.Rounded.QrCodeScanner, "Scan QR code", scheme.secondaryContainer, onScanQr, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(Icons.Rounded.Terminal, "API docs", scheme.secondaryContainer, onDeveloperDocs, Modifier.weight(1f))
            QuickAction(Icons.AutoMirrored.Rounded.ReceiptLong, "Transactions", actionSurface, onTransactionsClick, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(Icons.Rounded.Person, "Profile", actionSurface, onProfileClick, Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun QuickAction(icon: ImageVector, label: String, background: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 72.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = background,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun MerchantToolsBanner(onCreatePaymentLink: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        shadowElevation = 6.dp
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(FastPayNavy, FastPayBlue)))
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("DRPAY", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD5B16D), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Get paid your way", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Collections, payment links and transfers in one place.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.78f))
                    TextButton(onClick = onCreatePaymentLink, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Text("Create a payment link", color = Color.White, fontWeight = FontWeight.Bold)
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White, modifier = Modifier.padding(start = 5.dp).size(16.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.CurrencyExchange, null, tint = Color(0xFFD5B16D), modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
fun TransactionHistorySection(
    transactions: List<InternalTransaction>,
    isLoading: Boolean,
    hasError: Boolean,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 18.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Recent activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "See all",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    Icons.Rounded.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        when {
            isLoading -> Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
            transactions.isEmpty() -> Text(
                text = if (hasError) "Transaction history unavailable" else "No transactions yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 20.dp)
            )
            else -> transactions.forEachIndexed { index, transaction ->
                if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                TransactionListItem(transaction)
            }
        }
    }
}

@Composable
fun TransactionListItem(transaction: InternalTransaction) {
    val isOutflow = transaction.amount < 0
    val status = transaction.status.uppercase()
    val isFailed = status == "FAILED"
    val isPending = status == "PENDING"
    val label = when {
        isFailed && isOutflow -> "Transfer failed"
        isFailed -> "Payment failed"
        isPending && isOutflow -> "Transfer pending"
        isPending -> "Payment pending"
        isOutflow -> "Transfer completed"
        else -> "Payment received"
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    when {
                        isFailed -> Icons.Rounded.ErrorOutline
                        isPending -> Icons.Rounded.Schedule
                        isOutflow -> Icons.Rounded.NorthEast
                        else -> Icons.Rounded.SouthWest
                    },
                    null,
                    tint = if (isFailed) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(text = transaction.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(
            text = "${if (isOutflow) "-" else if (isFailed) "" else "+"}₱${"%,.2f".format(kotlin.math.abs(transaction.amount))}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                isFailed -> Color(0xFFEF4444)
                isOutflow -> MaterialTheme.colorScheme.onSurface
                else -> Color(0xFF16835D)
            }
        )
    }
}
