package com.example.myapplication.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DepositWizardDialog(
    accountNumber: String,
    balance: Double,
    onCheckout: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var accountCopied by remember { mutableStateOf(false) }
    var amountInput by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wallet deposit details") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("CURRENT WALLET BALANCE", style = MaterialTheme.typography.labelSmall)
                        Text(
                            formatPhp(balance),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text("SWIFTPAY WALLET ACCOUNT NUMBER", style = MaterialTheme.typography.labelSmall)
                        Text(accountNumber, style = MaterialTheme.typography.titleMedium)
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                if (clipboard != null) {
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("Wallet account number", accountNumber)
                                    )
                                    accountCopied = true
                                }
                            }
                        ) {
                            Text(if (accountCopied) "Copied" else "Copy account number")
                        }
                    }
                }
                Text(
                    "Transfer to this account number using your bank's transfer service. Or pay through SwiftPay checkout and choose an available bank or e-wallet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    label = { Text("Deposit amount (PHP)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = amountError != null,
                    supportingText = {
                        Text(amountError ?: "Select a bank or e-wallet on the secure checkout page.")
                    }
                )
                Button(
                    onClick = {
                        val amount = amountInput.toDoubleOrNull()
                        if (amount == null || !amount.isFinite() || amount <= 0.0) {
                            amountError = "Enter a valid PHP deposit amount."
                        } else {
                            onCheckout(amount)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue to secure checkout")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
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
