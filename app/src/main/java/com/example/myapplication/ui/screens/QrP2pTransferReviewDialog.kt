package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.myapplication.R
import com.example.myapplication.data.api.BackendTransferInstitution
import com.example.myapplication.data.hasCompleteRecipientName
import com.example.myapplication.data.institutionBrandKey
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
                Text(
                    "Transfers are sent immediately after confirmation and may not be reversible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
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
                        leadingIcon = selectedInstitution?.let { institution ->
                            { InstitutionLogo(institution) }
                        },
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
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        InstitutionLogo(institution)
                                        Column {
                                            Text(institution.name)
                                            Text(
                                                institution.code,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
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
                if (valid) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Final transfer review", style = MaterialTheme.typography.labelLarge)
                            Text("Amount: PHP ${"%,.2f".format(java.util.Locale.US, parsedAmount)}")
                            Text("To: ${selectedInstitution?.name}")
                            Text("Account: ${accountNumber.trim()}")
                            Text("Recipient: ${recipientName.trim()}")
                        }
                    }
                }
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
            ) {
                Text(
                    if (parsedAmount != null && parsedAmount in 0.01..50_000.0) {
                        "Confirm and send PHP ${"%,.2f".format(java.util.Locale.US, parsedAmount)}"
                    } else {
                        "Confirm and send"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun InstitutionLogo(institution: BackendTransferInstitution) {
    val bundledLogo = when (institutionBrandKey(institution.name)) {
        "bdo" -> R.drawable.institution_bdo
        "bpi" -> R.drawable.institution_bpi
        "bpifamily" -> R.drawable.institution_bpi_family
        "chinabank" -> R.drawable.institution_chinabank
        "eastwest" -> R.drawable.institution_eastwest
        "gcash" -> R.drawable.institution_gcash
        "gotyme" -> R.drawable.institution_gotyme
        "landbank" -> R.drawable.institution_landbank
        "maya" -> R.drawable.institution_maya
        "metrobank" -> R.drawable.institution_metrobank
        "pnb" -> R.drawable.institution_pnb
        "rcbc" -> R.drawable.institution_rcbc
        "securitybank" -> R.drawable.institution_security_bank
        "unionbank" -> R.drawable.institution_unionbank
        else -> null
    }
    var imageFailed by remember(institution.logoUrl, bundledLogo) { mutableStateOf(false) }
    val logoUrl = institution.logoUrl
    if (bundledLogo != null) {
        Surface(
            modifier = Modifier.size(width = 58.dp, height = 38.dp),
            shape = RoundedCornerShape(6.dp),
            color = Color.White
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(bundledLogo),
                contentDescription = "${institution.name} logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.padding(3.dp)
            )
        }
    } else if (logoUrl != null && !imageFailed) {
        Surface(
            modifier = Modifier.size(width = 58.dp, height = 38.dp),
            shape = RoundedCornerShape(6.dp),
            color = androidx.compose.ui.graphics.Color.White
        ) {
            AsyncImage(
                model = logoUrl,
                contentDescription = "${institution.name} logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.padding(3.dp),
                onError = { imageFailed = true }
            )
        }
    } else {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    institution.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
