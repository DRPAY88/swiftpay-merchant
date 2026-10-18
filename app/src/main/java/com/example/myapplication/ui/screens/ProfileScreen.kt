package com.example.myapplication.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.LocalNavController
import com.example.myapplication.navigation.Route
import com.example.myapplication.ui.components.DrPayMarkBadge
import com.example.myapplication.ui.theme.*
import com.example.myapplication.data.DebugNetworkTracker
import com.example.myapplication.BuildConfig
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.data.SettingsManager
import com.example.myapplication.data.DeviceBiometricAuthenticator
import com.example.myapplication.data.DevicePinManager
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Popup
import androidx.fragment.app.FragmentActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val themeMode by settingsManager.themeMode.collectAsState(initial = "SYSTEM")
    val signedInIdentity by settingsManager.loggedInIdentity.collectAsState(initial = null)
    val signedInRole by settingsManager.loggedInRole.collectAsState(initial = null)
    val hasDevicePin = remember(context) { DevicePinManager(context).hasPin() }
    var biometricEnabled by remember(context) {
        mutableStateOf(settingsManager.isBiometricUnlockEnabled())
    }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val activity = context as? FragmentActivity

    val handleBiometricAuthenticated by rememberUpdatedState<() -> Unit> {
        try {
            settingsManager.setBiometricUnlockEnabled(true)
            biometricEnabled = true
        } catch (error: Exception) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    error.message ?: "Could not enable biometric unlock."
                )
            }
        }
    }
    val handleBiometricError by rememberUpdatedState<(String) -> Unit> { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val biometricAuthenticator = remember(activity) {
        activity?.let {
            DeviceBiometricAuthenticator(
                activity = it,
                onAuthenticated = { handleBiometricAuthenticated() },
                onError = { handleBiometricError(it) }
            )
        }
    }

    val bgColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val isCustomer = signedInRole == "customer"
    val accountName = if (isCustomer) "Personal wallet" else "Merchant account"
    val canEnableBiometrics = hasDevicePin && biometricAuthenticator?.isAvailable() == true

    fun onBiometricToggle(enabled: Boolean) {
        if (enabled) {
            when {
                !hasDevicePin -> scope.launch {
                    snackbarHostState.showSnackbar("Set up an MPIN before enabling biometric unlock.")
                }
                biometricAuthenticator == null -> scope.launch {
                    snackbarHostState.showSnackbar("Biometric unlock is unavailable in this app session.")
                }
                else -> biometricAuthenticator.authenticate()
            }
        } else {
            try {
                settingsManager.setBiometricUnlockEnabled(false)
                biometricEnabled = false
            } catch (error: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        error.message ?: "Could not disable biometric unlock."
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DrPayMarkBadge(size = 28.dp)
                        Text("Profile", color = textColor, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {},
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = bgColor,
                    scrolledContainerColor = bgColor
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = bgColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = surfaceColor,
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape,
                            color = FastPayBlue.copy(alpha = 0.12f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = accountName.firstOrNull()?.uppercase() ?: "S",
                                    color = FastPayBlue,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                accountName,
                                style = MaterialTheme.typography.titleMedium,
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                signedInIdentity?.takeIf { it.isNotBlank() } ?: "No signed-in account",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryTextColor,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        val isSignedIn = !signedInIdentity.isNullOrBlank()
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isSignedIn) FastPayGreen.copy(alpha = 0.12f)
                            else secondaryTextColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = if (isSignedIn) "SIGNED IN" else "SIGNED OUT",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSignedIn) FastPayGreen else secondaryTextColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 18.dp), color = borderColor)
                    ProfileDetailRow(
                        if (signedInRole == "customer") "Account email" else "Merchant email",
                        signedInIdentity?.takeIf { it.isNotBlank() } ?: "Not signed in",
                        textColor,
                        secondaryTextColor
                    )
                    Spacer(Modifier.height(14.dp))
                    ProfileDetailRow("App version", "${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})", textColor, secondaryTextColor)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SECURITY", style = MaterialTheme.typography.labelSmall, color = secondaryTextColor, fontWeight = FontWeight.Bold)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = surfaceColor,
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = biometricEnabled,
                                enabled = biometricEnabled || canEnableBiometrics,
                                role = Role.Switch,
                                onValueChange = ::onBiometricToggle
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Fingerprint,
                            contentDescription = null,
                            tint = FastPayBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Biometric unlock", color = textColor, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = when {
                                    biometricEnabled -> "Enabled for this device"
                                    !hasDevicePin -> "Set up an MPIN before enabling biometric unlock"
                                    biometricAuthenticator == null -> "Unavailable in this app session"
                                    biometricAuthenticator.isAvailable() -> "Use an enrolled biometric to unlock"
                                    else -> biometricAuthenticator.availabilityMessage()
                                },
                                color = secondaryTextColor,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = null
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PREFERENCES", style = MaterialTheme.typography.labelSmall, color = secondaryTextColor, fontWeight = FontWeight.Bold)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = surfaceColor,
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column {
                        ProfileActionRow(
                            icon = if (themeMode == "DARK") Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                            title = "Appearance",
                            subtitle = when (themeMode) {
                                "LIGHT" -> "Light theme"
                                "DARK" -> "Dark theme"
                                else -> "Follow device setting"
                            },
                            tint = FastPayBlue,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            onClick = { showThemeDialog = true }
                        )
                    }
                }
            }

            if (!isCustomer) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("DEVELOPER", style = MaterialTheme.typography.labelSmall, color = secondaryTextColor, fontWeight = FontWeight.Bold)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = surfaceColor,
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Column {
                            ProfileActionRow(
                                icon = Icons.Rounded.Key,
                                title = "API credentials",
                                subtitle = "Manage integration keys",
                                tint = FastPayAccent,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                onClick = { navController.navigate(Route.ApiKeys) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = borderColor)
                            ProfileActionRow(
                                icon = Icons.Rounded.Description,
                                title = "API documentation",
                                subtitle = "SwiftPay integration reference",
                                tint = MaterialTheme.colorScheme.tertiary,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                onClick = { navController.navigate(Route.ApiDocs) }
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { showSignOutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Sign out", fontWeight = FontWeight.SemiBold)
            }

            DebugNetworkOverlay()

            Text(
                text = "SWIFTPAY MERCHANT",
                style = MaterialTheme.typography.labelSmall,
                color = secondaryTextColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out of DRPAY?", color = textColor) },
            text = {
                Text(
                    "You will need to sign in again to access your merchant account.",
                    color = secondaryTextColor
                )
            },
            containerColor = surfaceColor,
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        navController.logout()
                    }
                ) {
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Theme", color = textColor) },
            containerColor = surfaceColor,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = listOf(
                        "SYSTEM" to "System Default",
                        "LIGHT" to "Light Mode",
                        "DARK" to "Dark Mode"
                    )
                    options.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        settingsManager.saveThemeMode(value)
                                        showThemeDialog = false
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == value,
                                onClick = {
                                    scope.launch {
                                        settingsManager.saveThemeMode(value)
                                        showThemeDialog = false
                                    }
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = FastPayBlue,
                                    unselectedColor = secondaryTextColor
                                )
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(label, color = textColor, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cancel", color = FastPayBlue)
                }
            }
        )
    }
}

@Composable
private fun ProfileDetailRow(
    label: String,
    value: String,
    textColor: Color,
    secondaryTextColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = secondaryTextColor)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProfileActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    textColor: Color,
    secondaryTextColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = textColor, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = secondaryTextColor, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = secondaryTextColor)
    }
}

@Composable
fun DebugNetworkOverlay() {
    if (!BuildConfig.DEBUG) return

    val attempts = remember { mutableStateOf(DebugNetworkTracker.getAttempts()) }
    LaunchedEffect(Unit) {
        while (true) {
            attempts.value = DebugNetworkTracker.getAttempts()
            kotlinx.coroutines.delay(1500)
        }
    }

    Popup(alignment = Alignment.BottomEnd) {
        Surface(
            modifier = Modifier
                .padding(12.dp)
                .widthIn(max = 340.dp)
                .heightIn(max = 260.dp),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 8.dp,
            color = Color(0xFF0F1720).copy(alpha = 0.9f)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("Network Debug (recent)", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (attempts.value.isEmpty()) {
                    Text("No attempts recorded", color = Color.LightGray)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(attempts.value) { a ->
                            Row(modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(a.timestamp + " • " + a.note, color = Color.White, fontSize = 12.sp)
                                    Text(a.url, color = Color.LightGray, fontSize = 11.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(a.code.toString(), color = if (a.code in 200..299) Color(0xFF2ECC71) else Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
