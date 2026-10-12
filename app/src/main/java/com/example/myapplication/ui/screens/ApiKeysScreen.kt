package com.example.myapplication.ui.screens

import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.SettingsManager
import com.example.myapplication.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysScreen() {
    val context = LocalContext.current
    val settings = SettingsManager(context)
    val scope = rememberCoroutineScope()

    val backendAccessToken by settings.backendAccessToken.collectAsState(initial = null)
    val aliasFlow = settings.merchantAlias.collectAsState(initial = null)
    val backendUrl = BuildConfig.PAYMENT_BACKEND_URL.ifBlank { "Backend URL not configured" }

    var editingAlias by remember { mutableStateOf(false) }
    var aliasInput by remember(aliasFlow.value) { mutableStateOf(aliasFlow.value ?: "") }

    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager

    val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
    val bgColor = if (isDarkTheme) Color(0xFF121212) else FastPaySurface
    val surfaceColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkTheme) Color.White else FastPayNavy
    val secondaryTextColor = if (isDarkTheme) Color.LightGray else Color.Gray

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Payment Service", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = FastPayNavy
                )
            )
        },
        containerColor = bgColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(20.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {

            Surface(
                color = Color(0xFFFFF3CD),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.Warning, null, tint = Color(0xFFF57C00), modifier = Modifier.size(20.dp))
                    Text(
                        "SwiftPay merchant credentials are held by the secure payment backend, not on this device.",
                        color = Color(0xFFF57C00),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 0.dp else 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Backend", fontWeight = FontWeight.Bold, color = textColor)
                    Text(backendUrl, color = secondaryTextColor, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.LightGray.copy(alpha = 0.3f))
                    Text(
                        if (backendAccessToken.isNullOrBlank()) "Sign in to authenticate with the payment service" else "Authenticated session active",
                        color = if (backendAccessToken.isNullOrBlank()) Color(0xFFE53935) else Color(0xFF16835D),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 0.dp else 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Link, null, tint = textColor, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Payment Link Alias",
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Your customized merchant alias used for generating shareable payment links: https://pay.swiftpay.ph/{alias}",
                        fontSize = 11.sp,
                        color = secondaryTextColor,
                        lineHeight = 16.sp
                    )

                    Spacer(Modifier.height(14.dp))

                    if (editingAlias) {
                        OutlinedTextField(
                            value = aliasInput,
                            onValueChange = { aliasInput = it.trim() },
                            label = { Text("Merchant Alias", fontSize = 12.sp) },
                            placeholder = { Text("e.g. sweetrise or mybusiness", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FastPayNavy,
                                focusedLabelColor = FastPayNavy
                            ),
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (aliasInput.isNotBlank()) {
                                        scope.launch {
                                            settings.saveMerchantAlias(aliasInput.trim())
                                            Toast.makeText(context, "Alias saved! Link: https://pay.swiftpay.ph/${aliasInput.trim()}", Toast.LENGTH_LONG).show()
                                            editingAlias = false
                                        }
                                    } else {
                                        Toast.makeText(context, "Alias cannot be empty", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Rounded.Save, null, tint = FastPayNavy)
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    aliasInput = aliasFlow.value ?: ""
                                    editingAlias = false
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Cancel", fontSize = 13.sp)
                            }
                            Button(
                                onClick = {
                                    if (aliasInput.isNotBlank()) {
                                        scope.launch {
                                            settings.saveMerchantAlias(aliasInput.trim())
                                            Toast.makeText(context, "✓ Alias saved: ${aliasInput.trim()}", Toast.LENGTH_SHORT).show()
                                            editingAlias = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = FastPayNavy)
                            ) {
                                Icon(Icons.Rounded.Save, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val currentAlias = aliasFlow.value
                                if (currentAlias.isNullOrBlank()) {
                                    Text(
                                        "Not Configured",
                                        color = Color(0xFFE53935),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Click edit to set your payment alias",
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                } else {
                                    Text(
                                        currentAlias,
                                        color = textColor,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "https://pay.swiftpay.ph/$currentAlias",
                                        color = secondaryTextColor,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Row {
                                if (!aliasFlow.value.isNullOrBlank()) {
                                    IconButton(onClick = {
                                        val link = "https://pay.swiftpay.ph/${aliasFlow.value}"
                                        clipboardManager.setPrimaryClip(ClipData.newPlainText("Payment Link", link))
                                        Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
                                    }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Rounded.ContentCopy, null, tint = if (isDarkTheme) Color.LightGray else FastPayNavy.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        aliasInput = aliasFlow.value ?: ""
                                        editingAlias = true
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Rounded.Edit, null, tint = textColor, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Surface(
                color = Color(0xFFFFF3CD),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = Color(0xFFF57C00), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "How to manage SwiftPay credentials",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF57C00),
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "To manage or update API credentials, contact SwiftPay support:\n" +
                        "• Email: support@swiftpay.ph\n" +
                        "• Portal: https://api.pay.live.swiftpay.ph/api-documentation",
                        fontSize = 11.sp,
                        color = Color(0xFFF57C00).copy(alpha = 0.85f),
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CredentialRow(
    label: String,
    value: String?,
    isSecret: Boolean,
    isVisible: Boolean,
    onCopy: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = secondaryTextColor, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            if (value.isNullOrBlank()) {
                Text("Not Configured", fontSize = 13.sp, color = Color.Red.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
            } else {
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1
                )
            }
        }

        if (!value.isNullOrBlank()) {
            IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = FastPayBlue, modifier = Modifier.size(18.dp))
            }
        }
    }
}
