package com.example.myapplication.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DepositWizardDialog(
    accountNumber: String,
    balance: Double,
    onDismiss: () -> Unit
) {
    var accountCopied by remember { mutableStateOf(false) }
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
                    "Use this number as the destination for an inbound top-up. The published SwiftPay API does not provide the receiving institution or beneficiary name, and this app cannot initiate or confirm the bank transfer. Verify those details with your merchant administrator before sending funds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

private fun formatPhp(amount: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH")).format(amount)
