package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.hasCompleteRecipientName
import com.example.myapplication.data.api.BackendTransferInstitution
import java.math.BigDecimal

@Composable
fun QrP2pTransferReviewDialog(
    suggestedAmount: Double?,
    institutions: List<BackendTransferInstitution>,
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String, String) -> Unit
) {
    var amount by remember(suggestedAmount) {
        mutableStateOf(
            suggestedAmount?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty()
        )
    }
    var selectedInstitution by remember(institutions) {
        mutableStateOf<BackendTransferInstitution?>(null)
    }
    var institutionMenuExpanded by remember { mutableStateOf(false) }
    var accountNumber by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf("") }
    val parsedAmount = amount.toBigDecimalOrNull()?.toDouble()
    val completeRecipientName = hasCompleteRecipientName(recipientName)
    val valid = parsedAmount != null && parsedAmount in 0.01..50_000.0 &&
        selectedInstitution != null && accountNumber.isNotBlank() && completeRecipientName

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review QR_P2P transfer") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "The QR amount is prefilled only when present. Enter the recipient's account and legal name, " +
                        "then verify every detail. A QR scan does not confirm the recipient's identity.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (PHP)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedInstitution?.let { "${it.name} (${it.code})" }.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Recipient institution") },
                        supportingText = { Text("Select the institution shown on the recipient's QR or account details.") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { institutionMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = institutionMenuExpanded,
                        onDismissRequest = { institutionMenuExpanded = false }
                    ) {
                        institutions.forEach { institution ->
                            DropdownMenuItem(
                                text = { Text("${institution.name} (${institution.code})") },
                                onClick = {
                                    selectedInstitution = institution
                                    institutionMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it.trim().take(64) },
                    label = { Text("Recipient account / wallet number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = recipientName,
                    onValueChange = { recipientName = it.take(128) },
                    label = { Text("Recipient full legal name") },
                    supportingText = {
                        if (recipientName.isNotBlank() && !completeRecipientName) {
                            Text("Enter the complete name as registered, including the recipient's surname.")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "QR_P2P • InstaPay • PHP 0.01–50,000.00",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onConfirm(
                        parsedAmount ?: return@TextButton,
                        selectedInstitution?.code ?: return@TextButton,
                        accountNumber.trim(),
                        recipientName.trim()
                    )
                }
            ) { Text("Confirm and send") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
