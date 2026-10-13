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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.SettingsManager
import com.example.myapplication.data.TransactionStore
import com.example.myapplication.data.createSwiftPayService
import com.example.myapplication.data.mergeTransactions
import com.example.myapplication.data.api.InternalTransaction
import com.example.myapplication.ui.components.DrPayMarkBadge
import com.example.myapplication.ui.theme.FastPayActionIcon
import com.example.myapplication.ui.theme.FastPayBackground
import com.example.myapplication.ui.theme.FastPayBlue
import com.example.myapplication.ui.theme.FastPayGradient
import com.example.myapplication.ui.theme.FastPayMeshBlue
import com.example.myapplication.ui.theme.FastPayNavy
import com.example.myapplication.ui.theme.FastPayTextPrimary
import com.example.myapplication.ui.theme.FastPayTextSecondary
import com.example.myapplication.ui.theme.FintechMotion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onLaunchMiniApp: (String?) -> Unit,
    onNavigateToWallet: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onHelpClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
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
            val remoteResult = service.getInternalTransactions()
            transactionHistoryUnavailable = remoteResult.isFailure && localTransactions.isEmpty()
            recentTransactions = mergeTransactions(localTransactions, remoteResult.getOrNull().orEmpty()).take(3)
        } catch (error: Exception) {
            Log.e("HomeScreen", "Failed to refresh wallet and transaction history", error)
            balanceError = "Could not connect to the wallet service. Check your connection and retry."
            transactionHistoryUnavailable = true
        } finally {
            isLoading = false
            isLoadingTransactions = false
        }
    }

    Box(modifier = modifier.fillMaxSize().background(FastPayBackground)) {
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
                onDeposit = { onLaunchMiniApp("deposit-page") },
                onBalanceRetry = {
                    isLoading = true
                    balanceError = null
                    refreshKey++
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
            QuickActionsSection(
                onCollect = { onLaunchMiniApp("topup-page") },
                onQrClick = { onLaunchMiniApp("qr-page") },
                onTransfer = { onLaunchMiniApp("disbursement-page") },
                onPaymentLink = { onLaunchMiniApp("payment-link-page") },
                onNavigateToWallet = onNavigateToWallet,
                onDeposit = { onLaunchMiniApp("deposit-page") },
                onDeveloperDocs = { onLaunchMiniApp("api-docs-page") },
                onProfileClick = onProfileClick
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            DrPayMarkBadge(size = 44.dp)
            Spacer(modifier = Modifier.width(11.dp))
            Column {
                Text("Hello!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = FastPayTextPrimary)
                Text("Your Denshitx Bank account", style = MaterialTheme.typography.labelMedium, color = FastPayTextSecondary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onHelpClick, modifier = Modifier.size(42.dp)) {
                Icon(Icons.AutoMirrored.Rounded.HelpOutline, "Help", tint = FastPayTextPrimary, modifier = Modifier.size(23.dp))
            }
            IconButton(onClick = onProfileClick, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Rounded.AccountCircle, "Profile", tint = FastPayTextPrimary, modifier = Modifier.size(26.dp))
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
    onDeposit: () -> Unit,
    onBalanceRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        shadowElevation = 5.dp
    ) {
        Column(
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "AVAILABLE BALANCE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.76f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onToggleBalance, modifier = Modifier.size(30.dp)) {
                    Icon(
                        if (isBalanceVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = if (isBalanceVisible) "Hide balance" else "Show balance",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                when {
                    isLoading -> Box(Modifier.width(168.dp).height(38.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.22f)))
                    balance != null && isBalanceVisible -> Text(
                        text = "₱${"%,.2f".format(balance)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 30.sp,
                        maxLines = 1
                    )
                    balance != null -> Text("₱ ••••••", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    else -> Text("Unavailable", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Button(
                    onClick = onDeposit,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = FastPayNavy),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.height(42.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cash in", fontWeight = FontWeight.Bold, maxLines = 1)
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

@Composable
fun QuickActionsSection(
    onCollect: () -> Unit,
    onQrClick: () -> Unit,
    onTransfer: () -> Unit,
    onPaymentLink: () -> Unit,
    onNavigateToWallet: () -> Unit,
    onDeposit: () -> Unit,
    onDeveloperDocs: () -> Unit,
    onProfileClick: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 18.dp)) {
        Text("Quick actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = FastPayTextPrimary)
        Spacer(modifier = Modifier.height(13.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            QuickAction(Icons.Rounded.Payments, "Collect", FastPayMeshBlue, onCollect, Modifier.weight(1f))
            QuickAction(Icons.AutoMirrored.Rounded.Send, "Transfer", FastPayMeshBlue, onTransfer, Modifier.weight(1f))
            QuickAction(Icons.Rounded.Link, "Payment link", FastPayMeshBlue, onPaymentLink, Modifier.weight(1f))
            QuickAction(Icons.Rounded.AccountBalanceWallet, "Wallet", Color(0xFFF3EBDD), onNavigateToWallet, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            QuickAction(Icons.Rounded.AccountBalance, "Cash in", FastPayMeshBlue, onDeposit, Modifier.weight(1f))
            QuickAction(Icons.Rounded.QrCode2, "QR", FastPayMeshBlue, onQrClick, Modifier.weight(1f))
            QuickAction(Icons.Rounded.Terminal, "API docs", Color(0xFFF3EBDD), onDeveloperDocs, Modifier.weight(1f))
            QuickAction(Icons.Rounded.Person, "Profile", FastPayMeshBlue, onProfileClick, Modifier.weight(1f))
        }
    }
}

@Composable
fun QuickAction(icon: ImageVector, label: String, background: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = RoundedCornerShape(17.dp), color = background, modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = FastPayActionIcon, modifier = Modifier.size(29.dp))
            }
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = FastPayTextPrimary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, fontSize = 12.sp)
    }
}

@Composable
fun MerchantToolsBanner(onCreatePaymentLink: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        shape = RoundedCornerShape(20.dp),
        color = FastPayNavy
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Denshitx Bank", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD5B16D), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(5.dp))
                Text("Get paid your way", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text("QR collections, payment links and transfers.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.76f))
                TextButton(onClick = onCreatePaymentLink, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text("Create a payment link", color = Color.White, fontWeight = FontWeight.Bold)
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White, modifier = Modifier.padding(start = 5.dp).size(16.dp))
                }
            }
            Icon(Icons.Rounded.CurrencyExchange, null, tint = Color(0xFFD5B16D), modifier = Modifier.size(48.dp))
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
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Recent activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = FastPayTextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("See all", style = MaterialTheme.typography.labelMedium, color = FastPayBlue, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Rounded.ChevronRight, null, tint = FastPayBlue, modifier = Modifier.size(18.dp))
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
                color = FastPayTextSecondary,
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
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                    tint = if (isFailed) Color(0xFFEF4444) else FastPayTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = FastPayTextPrimary, fontWeight = FontWeight.Medium)
            Text(text = transaction.date, style = MaterialTheme.typography.labelSmall, color = FastPayTextSecondary, maxLines = 1)
        }
        Text(
            text = "${if (isOutflow) "-" else if (isFailed) "" else "+"}₱${"%,.2f".format(kotlin.math.abs(transaction.amount))}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                isFailed -> Color(0xFFEF4444)
                isOutflow -> FastPayTextPrimary
                else -> Color(0xFF16835D)
            }
        )
    }
}
