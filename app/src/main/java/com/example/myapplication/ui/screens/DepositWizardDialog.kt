package com.example.myapplication.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DepositWizardDialog(
    accountNumber: String,
    balance: Double,
    showTransferInstructionsInitially: Boolean = false,
    onCheckout: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var accountCopied by remember { mutableStateOf(false) }
    var amountInput by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf<String?>(null) }
    var selectedMethod by remember { mutableStateOf("apps") }
    var expandedMethod by remember { mutableStateOf("apps") }
    var selectedCard by remember { mutableStateOf("Mastercard") }
    var page by remember(showTransferInstructionsInitially) {
        mutableStateOf(if (showTransferInstructionsInitially) "transfer" else "methods")
    }
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val background = colors.background
    val foreground = colors.onBackground
    val muted = colors.onSurfaceVariant
    val blue = colors.primary

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(background).padding(horizontal = 22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 34.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        if (page == "methods") onDismiss() else page = "methods"
                    }
                ) {
                    Text("←", color = foreground, fontSize = 32.sp, fontWeight = FontWeight.Light)
                }
            }

            when (page) {
                "methods" -> {
                    Text(
                        "How would you like to top up?",
                        color = foreground,
                        fontSize = 27.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 26.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TopUpMethodSection(
                            title = "Payment apps",
                            subtitle = "Available e-wallets and bank apps",
                            selected = selectedMethod == "apps",
                            expanded = expandedMethod == "apps",
                            onClick = {
                                selectedMethod = "apps"
                                expandedMethod = if (expandedMethod == "apps") "" else "apps"
                            },
                            foreground = foreground,
                            muted = muted,
                            blue = blue
                        ) {
                            Text(
                                "Choose from the payment apps available for your deposit on SwiftPay checkout.",
                                color = muted,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(start = 54.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)
                            )
                        }
                        TopUpMethodSection(
                            title = "Pay with card",
                            subtitle = "Mastercard, JCB",
                            selected = selectedMethod == "card",
                            expanded = expandedMethod == "card",
                            onClick = {
                                selectedMethod = "card"
                                expandedMethod = if (expandedMethod == "card") "" else "card"
                            },
                            foreground = foreground,
                            muted = muted,
                            blue = blue
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf("Mastercard", "JCB").forEach { card ->
                                    TopUpChoiceTile(
                                        label = card,
                                        selected = selectedCard == card,
                                        modifier = Modifier.weight(1f),
                                        foreground = foreground,
                                        blue = blue,
                                        onClick = { selectedCard = card }
                                    )
                                }
                            }
                        }
                        TopUpMethodSection(
                            title = "UnionPay",
                            subtitle = null,
                            selected = selectedMethod == "unionpay",
                            expanded = expandedMethod == "unionpay",
                            onClick = {
                                selectedMethod = "unionpay"
                                expandedMethod = if (expandedMethod == "unionpay") "" else "unionpay"
                            },
                            foreground = foreground,
                            muted = muted,
                            blue = blue
                        ) {
                            Text(
                                "Availability is confirmed on secure SwiftPay checkout.",
                                color = muted,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(start = 54.dp, top = 4.dp, bottom = 8.dp)
                            )
                        }
                        TopUpMethodSection(
                            title = "Send money to your account",
                            subtitle = null,
                            selected = selectedMethod == "transfer",
                            expanded = false,
                            onClick = {
                                selectedMethod = "transfer"
                                expandedMethod = ""
                            },
                            foreground = foreground,
                            muted = muted,
                            blue = blue
                        )
                        TopUpMethodSection(
                            title = "Convenience store top-up",
                            subtitle = null,
                            selected = selectedMethod == "store",
                            expanded = expandedMethod == "store",
                            onClick = {
                                selectedMethod = "store"
                                expandedMethod = if (expandedMethod == "store") "" else "store"
                            },
                            foreground = foreground,
                            muted = muted,
                            blue = blue
                        ) {
                            Text(
                                "If supported for your account, this option will appear on SwiftPay checkout.",
                                color = muted,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(start = 54.dp, top = 4.dp, bottom = 8.dp)
                            )
                        }
                    }
                    TextButton(
                        onClick = { amountError = "Contact support to request a higher top-up limit." },
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                    ) {
                        Text("Increase top-up limit  ›", color = blue, fontSize = 16.sp)
                    }
                    amountError?.let { message ->
                        Text(message, color = muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    Button(
                        onClick = { page = if (selectedMethod == "transfer") "transfer" else "amount" },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 26.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Next", fontSize = 18.sp, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }

                "amount" -> {
                    Text(
                        "Enter top-up amount",
                        color = foreground,
                        fontSize = 27.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "You'll choose from the payment methods available for your account on SwiftPay checkout.",
                        color = muted,
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                        modifier = Modifier.padding(top = 10.dp, bottom = 28.dp)
                    )
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { value ->
                            if (value.isEmpty() || value.matches(Regex("^\\d{0,9}(\\.\\d{0,2})?$"))) {
                                amountInput = value
                                amountError = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Amount (PHP)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = amountError != null,
                        supportingText = {
                            Text(amountError ?: "Current wallet balance: ${formatPhp(balance)}")
                        }
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            val amount = amountInput.toDoubleOrNull()
                            if (amount == null || !amount.isFinite() || amount <= 0.0) {
                                amountError = "Enter a valid PHP deposit amount."
                            } else {
                                onCheckout(amount)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 26.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Continue to checkout", fontSize = 18.sp, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }

                else -> {
                    Text(
                        "Top up your DRPAY Wallet",
                        color = foreground,
                        fontSize = 27.sp,
                        lineHeight = 35.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 28.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("NETBANK (A RURAL BANK), INC.", color = muted, fontSize = 15.sp)
                                Text(
                                    accountNumber,
                                    color = foreground,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                            TextButton(onClick = { accountCopied = copyAccountNumber(context, accountNumber) }) {
                                Text(if (accountCopied) "Copied" else "Copy", color = blue)
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f).padding(top = 34.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        TopUpInstruction(1, "Send money using your bank app or ask a friend", foreground)
                        TopUpInstruction(2, "Select SwiftPay or the transfer option shown by your bank", foreground)
                        TopUpInstruction(3, "Copy and paste your account number", foreground)
                        TopUpInstruction(4, "Send any amount to the account", foreground)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 26.dp, top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(56.dp)
                        ) { Text("Close", color = foreground, fontSize = 17.sp) }
                        Button(
                            onClick = { accountCopied = copyAccountNumber(context, accountNumber) },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text(if (accountCopied) "Copied" else "Copy", fontSize = 17.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopUpMethodSection(
    title: String,
    subtitle: String?,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    foreground: Color,
    muted: Color,
    blue: Color,
    content: (@Composable () -> Unit)? = null
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = if (selected) blue else Color.Transparent,
                border = if (selected) null else BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (selected) "✓" else "",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, color = foreground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                subtitle?.let { Text(it, color = muted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp)) }
            }
            if (expanded || content != null) {
                Text(if (expanded) "⌃" else "⌄", color = muted, fontSize = 18.sp)
            }
        }
        if (expanded) content?.invoke()
    }
}

@Composable
private fun TopUpChoiceTile(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    foreground: Color,
    blue: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) blue else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.height(66.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else foreground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TopUpInstruction(number: Int, text: String, foreground: Color) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    number.toString(),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Text(
            text,
            color = foreground,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun copyAccountNumber(context: android.content.Context, accountNumber: String): Boolean {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText("Wallet account number", accountNumber))
    return true
}

@Composable
fun DepositCheckoutDialog(
    url: String,
    paymentId: String,
    referenceNo: String,
    amount: Double,
    onDismiss: () -> Unit
) {
    var progress by remember(url) { mutableFloatStateOf(0f) }
    var pageError by remember(url) { mutableStateOf<String?>(null) }
    var checkoutWebView by remember { mutableStateOf<android.webkit.WebView?>(null) }

    DisposableEffect(checkoutWebView) {
        onDispose {
            checkoutWebView?.stopLoading()
            checkoutWebView?.destroy()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SwiftPay secure checkout", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${formatPhp(amount)} · Ref $referenceNo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            if (progress < 1f && pageError == null) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                "Choose an available bank or e-wallet on SwiftPay's secure payment page.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            key(url) {
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { viewContext ->
                        android.webkit.WebView(viewContext).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                            android.webkit.CookieManager.getInstance()
                                .setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : android.webkit.WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: android.webkit.WebView?,
                                    request: android.webkit.WebResourceRequest?
                                ): Boolean {
                                    val target = request?.url ?: return false
                                    if (target.scheme == "https") return false
                                    if (target.scheme == "http") {
                                        pageError = "An insecure checkout redirect was blocked."
                                        return true
                                    }
                                    return try {
                                        viewContext.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                target
                                            )
                                        )
                                        true
                                    } catch (error: android.content.ActivityNotFoundException) {
                                        pageError = "This payment method requires an app that is not installed."
                                        true
                                    }
                                }

                                override fun onReceivedError(
                                    view: android.webkit.WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                    error: android.webkit.WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        pageError = error?.description?.toString()
                                            ?: "Could not load secure checkout."
                                    }
                                }
                            }
                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onProgressChanged(
                                    view: android.webkit.WebView?,
                                    newProgress: Int
                                ) {
                                    progress = newProgress / 100f
                                }
                            }
                            loadUrl(url)
                            checkoutWebView = this
                        }
                    }
                )
            }
            pageError?.let { message ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = {
                        pageError = null
                        progress = 0f
                        checkoutWebView?.reload()
                    }) {
                        Text("Retry")
                    }
                }
            }
            Text(
                "Payment ID: $paymentId",
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DepositCheckoutResultDialog(
    paymentId: String,
    amount: Double,
    success: Boolean,
    status: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (success) "Deposit received" else "Deposit not completed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (success) formatPhp(amount) else "Status: $status")
                Text("Payment reference: $paymentId")
                if (!success) {
                    Text("You can close this message and try another payment method.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

private fun formatPhp(amount: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH")).format(amount)
