package com.example.myapplication.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val infiniteTransition = rememberInfiniteTransition(label = "settings")
    val settingsRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing)
        ),
        label = "settingsRotation"
    )

    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val themeMode by settingsManager.themeMode.collectAsState(initial = "SYSTEM")
    var showThemeDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
    val bgColor = if (isDarkTheme) Color(0xFF121212) else Color.White
    val surfaceColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkTheme) Color.White else FastPayNavy
    val secondaryTextColor = if (isDarkTheme) Color.LightGray else Color.Gray
    val borderColor = if (isDarkTheme) Color.DarkGray else Color(0xFFE3E8EF)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", color = textColor) },
                navigationIcon = {},
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = bgColor,
                    scrolledContainerColor = bgColor
                ),
                modifier = Modifier.graphicsLayer {
                    alpha = if (visible) 1f else 0f
                }
            )
        },
        containerColor = bgColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            val avatarScale by animateFloatAsState(
                targetValue = if (visible) 1f else 0.5f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "avatarScale"
            )
            val avatarAlpha by animateFloatAsState(
                targetValue = if (visible) 1f else 0f,
                animationSpec = tween(500),
                label = "avatarAlpha"
            )

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        scaleX = avatarScale
                        scaleY = avatarScale
                        alpha = avatarAlpha
                    }
                    .clip(RoundedCornerShape(40.dp))
                    .background(if (isDarkTheme) Color.LightGray.copy(alpha = 0.1f) else FastPayNavy.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Settings, null, tint = textColor, modifier = Modifier.size(40.dp).graphicsLayer { rotationZ = settingsRotation })
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("SwiftPay Merchant", style = MaterialTheme.typography.titleLarge, color = textColor, fontWeight = FontWeight.Bold)
            Text("Account Settings", style = MaterialTheme.typography.labelSmall, color = secondaryTextColor)
            Spacer(modifier = Modifier.height(32.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                color = Color(0xFF1F4BA8).copy(alpha = 0.08f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.1f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.StoreMallDirectory, null, tint = FastPayAccent, modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("About Account", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("App version and system information", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.4f))
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val navController = LocalNavController.current
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = surfaceColor,
                shadowElevation = if (isDarkTheme) 0.dp else 1.dp,
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showThemeDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDarkTheme) Color.LightGray.copy(alpha = 0.1f) else FastPayNavy.copy(alpha = 0.05f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (themeMode == "DARK") Icons.Rounded.DarkMode else if (themeMode == "LIGHT") Icons.Rounded.LightMode else Icons.Rounded.SettingsSuggest,
                                    modifier = Modifier.size(20.dp),
                                    contentDescription = null,
                                    tint = textColor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Theme Mode", fontWeight = FontWeight.Bold, color = textColor)
                            Text(
                                text = when (themeMode) {
                                    "LIGHT" -> "Light Mode"
                                    "DARK" -> "Dark Mode"
                                    else -> "System Default"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryTextColor
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = textColor)
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = borderColor)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(Route.ApiKeys) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDarkTheme) Color.LightGray.copy(alpha = 0.1f) else FastPayNavy.copy(alpha = 0.05f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Key, modifier = Modifier.size(20.dp), contentDescription = null, tint = textColor)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("API Credentials", fontWeight = FontWeight.Bold, color = textColor)
                            Text("View and manage API keys", style = MaterialTheme.typography.labelSmall, color = secondaryTextColor)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = textColor)
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = borderColor)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(Route.ApiDocs) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDarkTheme) Color.LightGray.copy(alpha = 0.1f) else FastPayNavy.copy(alpha = 0.05f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Description, modifier = Modifier.size(20.dp), contentDescription = null, tint = textColor)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("API Documentation", fontWeight = FontWeight.Bold, color = textColor)
                            Text("SwiftPay developer integration guide", style = MaterialTheme.typography.labelSmall, color = secondaryTextColor)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = textColor)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { navController.logout() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.2f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Sign Out of Device", fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.weight(1f))

            DebugNetworkOverlay()

            Text(
                text = "SwiftPay Corporate Platform",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Version 2024.12.16-Release",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(24.dp))
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
