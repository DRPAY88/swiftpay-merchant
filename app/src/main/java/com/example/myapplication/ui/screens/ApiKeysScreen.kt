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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
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
import com.example.myapplication.data.SwiftPayService
import com.example.myapplication.data.api.BackendMerchantApiKey
import com.example.myapplication.ui.theme.*
import com.example.myapplication.ui.components.DrPayMarkBadge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysScreen() {
    val context = LocalContext.current
    val settings = SettingsManager(context)
    val scope = rememberCoroutineScope()

    val backendAccessToken by settings.backendAccessToken.collectAsState(initial = null)
    val backendUrl = BuildConfig.PAYMENT_BACKEND_URL.ifBlank { "Backend URL not configured" }
    val apiService = remember(backendAccessToken) {
        SwiftPayService(
            accessToken = backendAccessToken,
            onUnauthorized = { backendAccessToken?.let { settings.clearSessionIfAccessTokenMatches(it) } }
        )
    }
    var apiKeys by remember { mutableStateOf<List<BackendMerchantApiKey>>(emptyList()) }
    var apiKeyName by remember { mutableStateOf("Store backend") }
    var apiKeysLoading by remember { mutableStateOf(false) }
    var apiKeyBusy by remember { mutableStateOf(false) }
    var apiKeyError by remember { mutableStateOf<String?>(null) }
    var revealedApiKey by remember { mutableStateOf<String?>(null) }
    var pendingApiKeyAction by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
    suspend fun refreshApiKeys() {
        if (backendAccessToken.isNullOrBlank()) {
            apiKeys = emptyList()
            apiKeyError = null
            return
        }
        apiKeysLoading = true
        try {
            apiService.listMerchantApiKeys()
                .onSuccess {
                    apiKeys = it
                    apiKeyError = null
                }
                .onFailure { apiKeyError = it.message ?: "Could not load API keys" }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            apiKeyError = error.message ?: "Could not load API keys"
        } finally {
            apiKeysLoading = false
        }
    }
    LaunchedEffect(apiService) { refreshApiKeys() }

    val bgColor = MaterialTheme.colorScheme.background
    val isDarkTheme = bgColor == FastPayBlack
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DrPayMarkBadge(size = 28.dp)
                        Text("Payment Service", color = textColor, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = bgColor
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
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                    Text(
                        "SwiftPay merchant credentials are held by the secure payment backend, not on this device.",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        if (backendAccessToken.isNullOrBlank()) "Sign in to authenticate with the payment service" else "Authenticated session active",
                        color = if (backendAccessToken.isNullOrBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            MerchantApiKeyCard(
                name = apiKeyName,
                onNameChange = { apiKeyName = it },
                keys = apiKeys,
                loading = apiKeysLoading,
                busy = apiKeyBusy,
                error = apiKeyError,
                authenticated = !backendAccessToken.isNullOrBlank(),
                onRefresh = { scope.launch { refreshApiKeys() } },
                onCreate = {
                    apiKeyBusy = true
                    scope.launch {
                        try {
                            apiService.createMerchantApiKey(apiKeyName.trim())
                                .onSuccess { created ->
                                    revealedApiKey = created.apiKey
                                    refreshApiKeys()
                                }
                                .onFailure { apiKeyError = it.message ?: "Could not create API key" }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            apiKeyError = error.message ?: "Could not create API key"
                        } finally {
                            apiKeyBusy = false
                        }
                    }
                },
                onRotate = { keyId ->
                    pendingApiKeyAction = keyId to true
                },
                onRevoke = { keyId ->
                    pendingApiKeyAction = keyId to false
                }
            )

            Spacer(Modifier.height(24.dp))

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "How to manage SwiftPay credentials",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Merchant API keys are generated for server-side collection integrations. Copy a key when it is first created; the full secret cannot be viewed again. Never put it in browser JavaScript, a mobile app, source control, or a public repository. Rotate a key immediately if it may have been exposed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        lineHeight = 19.sp
                    )
                }
            }

            revealedApiKey?.let { apiKey ->
                AlertDialog(
                    onDismissRequest = { revealedApiKey = null },
                    title = { Text("Copy your API key now") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("This secret is shown only once. Store it in your web server's secret environment.")
                            Text(apiKey, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            clipboardManager.setPrimaryClip(ClipData.newPlainText("DRPAY merchant API key", apiKey))
                            Toast.makeText(context, "API key copied", Toast.LENGTH_SHORT).show()
                            revealedApiKey = null
                        }) { Text("Copy and close") }
                    },
                    dismissButton = {
                        TextButton(onClick = { revealedApiKey = null }) { Text("Close") }
                    }
                )
            }

            pendingApiKeyAction?.let { (keyId, rotate) ->
                val selected = apiKeys.firstOrNull { it.id == keyId }
                AlertDialog(
                    onDismissRequest = { pendingApiKeyAction = null },
                    title = { Text(if (rotate) "Rotate API key?" else "Revoke API key?") },
                    text = {
                        Text(
                            if (rotate) "The current key will stop working immediately. Your store must switch to the new key."
                            else "The key will stop working immediately. This action cannot be undone."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !apiKeyBusy,
                            onClick = {
                                pendingApiKeyAction = null
                                apiKeyBusy = true
                                scope.launch {
                                    try {
                                        val result = if (rotate) {
                                            apiService.rotateMerchantApiKey(keyId, selected?.name ?: "Store backend")
                                        } else {
                                            apiService.revokeMerchantApiKey(keyId).map { null }
                                        }
                                        result.onSuccess { created ->
                                            if (rotate) revealedApiKey = created?.apiKey
                                            refreshApiKeys()
                                        }.onFailure { apiKeyError = it.message ?: "Could not update API key" }
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        apiKeyError = error.message ?: "Could not update API key"
                                    } finally {
                                        apiKeyBusy = false
                                    }
                                }
                            }
                        ) { Text(if (rotate) "Rotate key" else "Revoke key") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingApiKeyAction = null }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

@Composable
private fun MerchantApiKeyCard(
    name: String,
    onNameChange: (String) -> Unit,
    keys: List<BackendMerchantApiKey>,
    loading: Boolean,
    busy: Boolean,
    error: String?,
    authenticated: Boolean,
    onRefresh: () -> Unit,
    onCreate: () -> Unit,
    onRotate: (String) -> Unit,
    onRevoke: (String) -> Unit
) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Key, null, tint = FastPayBlue)
                        Text("Merchant collection API keys", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onRefresh, enabled = authenticated && !busy && !loading) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh API keys")
                        }
                    }
                    Text(
                        "Create a server-only credential for QR Ph collections, status checks, and FX quotes. Each key is limited to collection endpoints.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = onNameChange,
                        label = { Text("Key label") },
                        singleLine = true,
                        enabled = authenticated && !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = onCreate,
                        enabled = authenticated && !busy && !loading && name.isNotBlank() && name.length <= 64,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (busy) "Please wait…" else "Generate API key")
                    }
                    if (!authenticated) {
                        Text("Sign in with a merchant account to manage API keys.", color = MaterialTheme.colorScheme.error)
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    keys.forEach { key ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(key.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "${key.prefix}••••  ·  ${if (key.revokedAt == null) "Active" else "Revoked"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            Text(
                                if (key.lastUsedAt == null) "Never used" else "Last used ${key.lastUsedAt}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (key.revokedAt == null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { onRotate(key.id) }, enabled = !busy) {
                                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Rotate")
                                    }
                                    TextButton(onClick = { onRevoke(key.id) }, enabled = !busy) {
                                        Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Revoke")
                                    }
                                }
                            }
                        }
                    }
                    if (!loading && authenticated && keys.isEmpty() && error == null) {
                        Text("No API keys created yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = secondaryTextColor, fontWeight = FontWeight.Bold)
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
            IconButton(onClick = onCopy, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy API key", tint = FastPayBlue, modifier = Modifier.size(18.dp))
            }
        }
    }
}
