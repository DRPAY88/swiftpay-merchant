package com.example.myapplication.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.TextButton
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.example.myapplication.ui.theme.FastPayActionIcon
import com.example.myapplication.ui.theme.FastPayTextPrimary
import com.example.myapplication.ui.theme.FastPayTextSecondary
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

    Box(modifier = modifier.fillMaxSize().background(Color.White)) {
        MeshBackground()
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            HeaderSection(
                balance = balance,
                isLoading = isLoading,
                balanceError = balanceError,
                onBalanceRetry = {
                    isLoading = true
                    balanceError = null
                    refreshKey++
                },
                onProfileClick = onProfileClick,
                onHelpClick = onHelpClick
            )
            Spacer(modifier = Modifier.height(24.dp))
            PrimaryActionRow(
                onCollect = { onLaunchMiniApp(null) },
                onTransfer = { onLaunchMiniApp("disbursement-page") },
                onPaymentLink = { onLaunchMiniApp("payment-link-page") },
                onQr = { onLaunchMiniApp("qr-page") }
            )
            Spacer(modifier = Modifier.height(28.dp))
            SecondaryToolGrid(
                onNavigateToWallet = onNavigateToWallet,
                onDeposit = { onLaunchMiniApp("deposit-page") },
                onDeveloperDocs = { onLaunchMiniApp("api-docs-page") },
                onHelpClick = onHelpClick
            )
            Spacer(modifier = Modifier.height(24.dp))
            PromotionalBanner()
            Spacer(modifier = Modifier.height(24.dp))
            TransactionHistorySection(
                transactions = recentTransactions,
                isLoading = isLoadingTransactions,
                hasError = transactionHistoryUnavailable,
                onClick = onNavigateToWallet
            )
            Spacer(modifier = Modifier.height(24.dp))
            RewardsHubSection()
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun MeshBackground() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            drawCircle(
                color = Color(0xFFE0E7FF),
                center = Offset(x = canvasWidth * 0.1f, y = canvasHeight * 0.2f),
                radius = canvasWidth * 0.4f
            )

            drawCircle(
                color = Color(0xFFEFF6FF),
                center = Offset(x = canvasWidth * 0.85f, y = canvasHeight * 0.1f),
                radius = canvasWidth * 0.5f
            )

            drawCircle(
                color = Color(0xFFF1F5F9),
                center = Offset(x = canvasWidth * 0.5f, y = canvasHeight * 0.4f),
                radius = canvasWidth * 0.6f
            )
        }
    }
}

@Composable
fun HeaderSection(
    balance: Double?,
    isLoading: Boolean,
    balanceError: String?,
    onBalanceRetry: () -> Unit,
    onProfileClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            if (isLoading) {
                Box(modifier = Modifier.width(120.dp).height(32.dp).clip(RoundedCornerShape(8.dp)).shimmerEffect())
            } else {
                Text(
                    text = balance?.let { "₱${"%,.2f".format(it)}" } ?: "Unavailable",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = FastPayTextPrimary,
                    fontSize = 26.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    text = "Available transfer balance",
                    style = MaterialTheme.typography.bodySmall,
                    color = FastPayTextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    Icons.Rounded.ArrowDropDown,
                    null,
                    tint = FastPayTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (!isLoading && balanceError != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = balanceError,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1
                    )
                    TextButton(onClick = onBalanceRetry) {
                        Text("Retry")
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onProfileClick) {
                Icon(Icons.Rounded.AccountCircle, null, tint = FastPayTextPrimary, modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = onHelpClick) {
                Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, tint = FastPayTextPrimary, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
fun PrimaryActionRow(
    onCollect: () -> Unit,
    onTransfer: () -> Unit,
    onPaymentLink: () -> Unit,
    onQr: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PrimaryActionButton(icon = Icons.Rounded.Payments, label = "Collect", onClick = onCollect, modifier = Modifier.weight(1f))
        PrimaryActionButton(icon = Icons.AutoMirrored.Rounded.Send, label = "Transfer", onClick = onTransfer, modifier = Modifier.weight(1f))
        PrimaryActionButton(icon = Icons.Rounded.Link, label = "Payment Link", onClick = onPaymentLink, modifier = Modifier.weight(1f))
        PrimaryActionButton(icon = Icons.Rounded.QrCode2, label = "QR", onClick = onQr, modifier = Modifier.weight(1f))
    }
}

@Composable
fun PrimaryActionButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = FastPayTextPrimary, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = FastPayTextPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SecondaryToolGrid(
    onNavigateToWallet: () -> Unit,
    onDeposit: () -> Unit,
    onDeveloperDocs: () -> Unit,
    onHelpClick: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "More",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = FastPayTextPrimary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            ToolItem(Icons.Rounded.AccountBalanceWallet, "Wallet", Modifier.weight(1f), onNavigateToWallet)
            ToolItem(Icons.Rounded.AccountBalance, "Deposit", Modifier.weight(1f), onDeposit)
            ToolItem(Icons.Rounded.Terminal, "API Docs", Modifier.weight(1f), onDeveloperDocs)
            ToolItem(Icons.AutoMirrored.Rounded.HelpOutline, "Help", Modifier.weight(1f), onHelpClick)
        }
    }
}

@Composable
fun ToolItem(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = Color(0xFF475569), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = FastPayTextSecondary,
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PromotionalBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFEFF6FF)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SwiftPay Deals",
                    style = MaterialTheme.typography.labelSmall,
                    color = FastPayTextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Loyalty Rewards\nProgram Is Live!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = FastPayTextPrimary,
                    lineHeight = 24.sp
                )
                Text(
                    text = "Powered by SwiftPay",
                    style = MaterialTheme.typography.labelSmall,
                    color = FastPayTextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Button(
                    onClick = { },
                    colors = ButtonDefaults.buttonColors(containerColor = FastPayActionIcon),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Get Started", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Icon(Icons.Rounded.DonutLarge, null, tint = Color(0xFFF97316).copy(alpha = 0.8f), modifier = Modifier.size(64.dp))
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
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(4.dp).height(20.dp).clip(CircleShape).background(Color(0xFF818CF8)))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Transaction History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = FastPayTextPrimary
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

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

@Composable
fun RewardsHubSection() {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(4.dp).height(20.dp).clip(CircleShape).background(Color(0xFF2DD4BF)))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Rewards Hub",
                    style = MaterialTheme.typography.titleMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    fontWeight = FontWeight.ExtraBold,
                    color = FastPayTextPrimary
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFF0FDFA)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Points & Deals", style = MaterialTheme.typography.labelSmall, color = Color(0xFF0D9488), fontWeight = FontWeight.Bold)
                Text(
                    "Earn points with every payment\nand unlock exclusive merchant rewards.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FastPayTextPrimary,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Icon(Icons.Rounded.CardGiftcard, null, tint = Color(0xFF2DD4BF), modifier = Modifier.size(60.dp).alpha(0.3f).align(Alignment.End))
            }
        }
    }
}

fun Modifier.shimmerEffect(): Modifier = this.then(
    background(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.LightGray.copy(alpha = 0.3f),
                Color.LightGray.copy(alpha = 0.5f),
                Color.LightGray.copy(alpha = 0.3f)
            )
        )
    )
)
