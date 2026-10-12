package com.example.myapplication.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.LocalNavController
import com.example.myapplication.navigation.Route
import com.example.myapplication.ui.components.MerchantLogo
import com.example.myapplication.ui.theme.*
import com.example.myapplication.data.DebugNetworkTracker
import com.example.myapplication.BuildConfig
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.data.SettingsManager
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Popup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val themeMode by settingsManager.themeMode.collectAsState(initial = "SYSTEM")
    val signedInEmail by settingsManager.loggedInEmail.collectAsState(initial = null)
    val merchantAlias by settingsManager.merchantAlias.collectAsState(initial = null)
    var showThemeDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current

    val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
    val bgColor = if (isDarkTheme) Color(0xFF121212) else Color.White
    val surfaceColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkTheme) Color.White else FastPayNavy
    val secondaryTextColor = if (isDarkTheme) Color.LightGray else Color.Gray
    val borderColor = if (isDarkTheme) Color.DarkGray else Color(0xFFE3E8EF)
    val accountName = merchantAlias?.takeIf { it.isNotBlank() }
        ?: signedInEmail?.substringBefore('@')?.replace('.', ' ')?.replaceFirstChar { it.uppercase() }
        ?: "SwiftPay merchant"

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Profile", color = textColor, fontWeight = FontWeight.Bold) },
                navigationIcon = {},
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = bgColor,
                    scrolledContainerColor = bgColor
                )
            )
        },
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
                shape = RoundedCornerShape(16.dp),
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
                            Text(accountName, style = MaterialTheme.typography.titleMedium, color = textColor, fontWeight = FontWeight.Bold)
                            Text(
                                signedInEmail ?: "No signed-in account",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryTextColor
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 18.dp), color = borderColor)
                    ProfileDetailRow("Account", if (signedInEmail == null) "Signed out" else "Merchant account", textColor, secondaryTextColor)
                    Spacer(Modifier.height(14.dp))
                    ProfileDetailRow("App version", "${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})", textColor, secondaryTextColor)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PREFERENCES", style = MaterialTheme.typography.labelSmall, color = secondaryTextColor, fontWeight = FontWeight.Bold)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
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

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("DEVELOPER", style = MaterialTheme.typography.labelSmall, color = secondaryTextColor, fontWeight = FontWeight.Bold)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
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
                            tint = FastPayGreen,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            onClick = { navController.navigate(Route.ApiDocs) }
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { navController.logout() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.2f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = secondaryTextColor)
        Text(value, style = MaterialTheme.typography.bodySmall, color = textColor, fontWeight = FontWeight.SemiBold)
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
            .clickable(onClick = onClick)
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
