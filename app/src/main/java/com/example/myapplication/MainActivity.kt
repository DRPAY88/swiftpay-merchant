package com.example.myapplication

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CurrencyBitcoin
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.fragment.app.FragmentActivity
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.example.myapplication.data.SessionManager
import com.example.myapplication.data.SettingsManager
import com.example.myapplication.data.DevicePinManager
import com.example.myapplication.data.IncomingMoneyPush
import com.example.myapplication.navigation.AppNavigationState
import com.example.myapplication.navigation.canProcessPaymentDeepLink
import com.example.myapplication.navigation.Route
import com.example.myapplication.ui.screens.ApiDocsScreen
import com.example.myapplication.ui.screens.ApiKeysScreen
import com.example.myapplication.ui.screens.CryptoScreen
import com.example.myapplication.ui.components.DrPayLogo
import com.example.myapplication.ui.screens.HomeScreen
import com.example.myapplication.ui.screens.LoginScreen
import com.example.myapplication.ui.screens.DevicePinScreen
import com.example.myapplication.ui.screens.MiniAppScreen
import com.example.myapplication.ui.screens.MiniAppViewModel
import com.example.myapplication.ui.screens.ProfileScreen
import com.example.myapplication.ui.screens.WalletScreen
import com.example.myapplication.ui.theme.FintechMotion
import com.example.myapplication.ui.theme.FastPayTheme
import com.example.myapplication.util.DebugLogger
import com.example.myapplication.util.DeepLinkBus
import com.example.myapplication.util.DeepLinkExtractor
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val LocalMiniAppViewModel = compositionLocalOf<MiniAppViewModel> { error("No MiniAppViewModel provided") }
val LocalNavController = compositionLocalOf<NavController> { error("No NavController provided") }

interface NavController {
    fun navigate(route: Route)
    fun pop()
    fun logout()
}

@Composable
private fun MainNavigationIcon(
    image: ImageVector,
    description: String,
    selected: Boolean
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(FintechMotion.contentMillis, easing = FintechMotion.standardEasing),
        label = "navigationIconContainer"
    )
    val iconColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.size(width = 52.dp, height = 36.dp),
        shape = RoundedCornerShape(14.dp),
        color = containerColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = image,
                contentDescription = description,
                tint = iconColor,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@Composable
private fun SessionStartupContent() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                DrPayLogo(size = 108.dp)
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings = remember { SettingsManager(application) }
            val themeMode by settings.themeMode.collectAsState(initial = "SYSTEM")
            val isDarkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }
            FastPayTheme(darkTheme = isDarkTheme) {
                SwiftPayApp()
            }
        }

        intent?.extras?.let { extras ->
            val map = DeepLinkExtractor.bundleToMap(extras)
            if (map != null) {
                lifecycleScope.launch {
                    DeepLinkBus.send(map)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val map = DeepLinkExtractor.bundleToMap(intent.extras)
        if (map != null) {
            lifecycleScope.launch {
                DeepLinkBus.send(map)
            }
        }
    }
}

@Composable
fun MayaApp() = SwiftPayApp()

@Composable
fun SwiftPayApp() {
    val context = LocalContext.current
    val settings = remember { SettingsManager(context) }
    val devicePinManager = remember { DevicePinManager(context) }
    val sessionManager = remember { SessionManager(context, settings) }
    val isLoggedInFlag by settings.isLoggedIn.collectAsState(initial = false)
    val backendAccessToken by settings.backendAccessToken.collectAsState(initial = null)
    val isLoggedIn = isLoggedInFlag && !backendAccessToken.isNullOrBlank()
    val loggedInEmail by settings.loggedInEmail.collectAsState(initial = null)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    var hasDevicePin by remember { mutableStateOf(false) }
    var isDeviceUnlocked by remember { mutableStateOf(false) }
    var isStartupResolved by remember { mutableStateOf(false) }
    val pendingPaymentDeepLinks = remember { mutableStateListOf<String>() }

    DebugLogger.logAuthCheck(isLoggedIn)
    DebugLogger.logDeviceInfo()

    val navigationState = remember { AppNavigationState() }
    val backStack = navigationState.backStack
    val currentRoute = navigationState.currentRoute

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val hasStoredSession = withContext(Dispatchers.IO) {
            settings.isLoggedIn.first() && !settings.getAccessToken().isNullOrBlank()
        }
        hasDevicePin = hasStoredSession && withContext(Dispatchers.IO) {
            devicePinManager.hasPin()
        }
        if (hasStoredSession && hasDevicePin) {
            navigationState.replaceRoot(Route.DeviceLock)
        }
        isStartupResolved = true
    }

    androidx.compose.runtime.LaunchedEffect(isLoggedIn) {
        hasDevicePin = isLoggedIn && withContext(Dispatchers.IO) { devicePinManager.hasPin() }
        if (!isLoggedIn) isDeviceUnlocked = false
    }

    androidx.compose.runtime.LaunchedEffect(isLoggedIn, backendAccessToken) {
        val accessToken = backendAccessToken
        if (isLoggedIn && !accessToken.isNullOrBlank() && IncomingMoneyPush.isConfigured()) {
            if (
                Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            IncomingMoneyPush.registerCurrentDevice(context, accessToken)
        }
    }

    androidx.compose.runtime.LaunchedEffect(isLoggedIn, hasDevicePin, isDeviceUnlocked) {
        when {
            isLoggedIn && hasDevicePin && !isDeviceUnlocked && currentRoute != Route.DeviceLock -> {
                navigationState.replaceRoot(Route.DeviceLock)
            }
            isLoggedIn && !hasDevicePin && currentRoute != Route.Login -> {
                navigationState.replaceRoot(Route.Login)
            }
            !isLoggedIn && currentRoute != Route.Login -> {
                navigationState.replaceRoot(Route.Login)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isLoggedIn, hasDevicePin, isDeviceUnlocked, currentRoute) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && isLoggedIn && hasDevicePin && isDeviceUnlocked && currentRoute != Route.DeviceLock) {
                isDeviceUnlocked = false
                navigationState.replaceRoot(Route.DeviceLock)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val miniAppViewModel: MiniAppViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    val navController = remember {
        object : NavController {
            override fun navigate(route: Route) {
                navigationState.navigate(route)
            }
            override fun pop() {
                if (!navigationState.popOrNavigateHome()) {
                    (context as? ComponentActivity)?.finish()
                }
            }
            override fun logout() {
                isDeviceUnlocked = false
                hasDevicePin = false
                (context as? ComponentActivity)?.lifecycleScope?.launch {
                    settings.getAccessToken()?.let { IncomingMoneyPush.unregisterCurrentDevice(context, it) }
                    settings.clearSession()
                    sessionManager.clearSession()
                    navigationState.replaceRoot(Route.Login)
                }
            }
        }
    }

    if (!isStartupResolved) {
        SessionStartupContent()
    } else CompositionLocalProvider(
        LocalNavController provides navController,
        LocalMiniAppViewModel provides miniAppViewModel
    ) {
        val currentContext = LocalContext.current
        androidx.compose.runtime.LaunchedEffect(Unit) {
            DeepLinkBus.flow.collectLatest { payload ->
                val (linkId, _) = DeepLinkExtractor.extractPaymentInfo(payload)
                if (linkId.isBlank()) return@collectLatest
                pendingPaymentDeepLinks.add(linkId)
            }
        }
        androidx.compose.runtime.LaunchedEffect(
            pendingPaymentDeepLinks.size,
            isStartupResolved,
            isLoggedIn,
            isDeviceUnlocked
        ) {
            if (
                !isStartupResolved ||
                !canProcessPaymentDeepLink(isLoggedIn, isDeviceUnlocked)
            ) return@LaunchedEffect

            val linkId = pendingPaymentDeepLinks.firstOrNull() ?: return@LaunchedEffect
            pendingPaymentDeepLinks.removeAt(0)
            navController.navigate(Route.Home)
            miniAppViewModel.handleDeepLink(linkId)
            Toast.makeText(currentContext, "Checking your payment status", Toast.LENGTH_LONG).show()
        }
        if (currentRoute == Route.Login || currentRoute == Route.DeviceLock) {
            NavDisplay(
                modifier = Modifier.fillMaxSize(),
                backStack = backStack,
                transitionSpec = {
                    fadeIn(tween(FintechMotion.navigationMillis, easing = FintechMotion.standardEasing)) togetherWith
                        fadeOut(tween(FintechMotion.navigationMillis, easing = FintechMotion.standardEasing))
                }
            ) { key ->
                when (key) {
                    Route.Login -> NavEntry(key) {
                        LoginScreen(onLoginSuccess = {
                            hasDevicePin = true
                            isDeviceUnlocked = true
                            navController.navigate(Route.Home)
                        })
                    }
                    Route.DeviceLock -> NavEntry(key) {
                        DevicePinScreen(
                            email = loggedInEmail,
                            biometricUnlockEnabled = settings.isBiometricUnlockEnabled(),
                            onUnlocked = {
                                isDeviceUnlocked = true
                                navController.navigate(Route.Home)
                            },
                            onUseAnotherAccount = { navController.logout() }
                        )
                    }
                    else -> NavEntry(key) {}
                }
            }
        } else {
            val activeTab = navigationState.selectedMainTab()
            NavigationSuiteScaffold(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                navigationSuiteItems = {
                    item(
                        selected = activeTab == Route.Home,
                        onClick = { navController.navigate(Route.Home) },
                        icon = { MainNavigationIcon(Icons.Rounded.Home, "Home", activeTab == Route.Home) },
                        label = {
                            Text(
                                "Home",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (activeTab == Route.Home) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (activeTab == Route.Home) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                    )
                    item(
                        selected = activeTab == Route.Wallet,
                        onClick = { navController.navigate(Route.Wallet) },
                        icon = { MainNavigationIcon(Icons.Rounded.AccountBalanceWallet, "Wallet", activeTab == Route.Wallet) },
                        label = {
                            Text(
                                "Wallet",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (activeTab == Route.Wallet) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (activeTab == Route.Wallet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                    )
                    item(
                        selected = activeTab == Route.Crypto,
                        onClick = { navController.navigate(Route.Crypto) },
                        icon = { MainNavigationIcon(Icons.Rounded.CurrencyBitcoin, "Crypto", activeTab == Route.Crypto) },
                        label = {
                            Text(
                                "Crypto",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (activeTab == Route.Crypto) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (activeTab == Route.Crypto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                    )
                    item(
                        selected = activeTab == Route.Profile,
                        onClick = { navController.navigate(Route.Profile) },
                        icon = { MainNavigationIcon(Icons.Rounded.PersonOutline, "Profile", activeTab == Route.Profile) },
                        label = {
                            Text(
                                "Profile",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (activeTab == Route.Profile) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (activeTab == Route.Profile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                    )
                }
            ) {
                NavDisplay(
                    modifier = Modifier.fillMaxSize(),
                    backStack = backStack,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(FintechMotion.navigationMillis, easing = FintechMotion.standardEasing)) +
                            slideInHorizontally(
                                animationSpec = tween(FintechMotion.navigationMillis, easing = FintechMotion.standardEasing),
                                initialOffsetX = { it / 8 }
                            ))
                            .togetherWith(
                                fadeOut(animationSpec = tween(FintechMotion.exitMillis, easing = FintechMotion.standardEasing)) +
                                    slideOutHorizontally(
                                        animationSpec = tween(FintechMotion.exitMillis, easing = FintechMotion.standardEasing),
                                        targetOffsetX = { -it / 10 }
                                    )
                            )
                    },
                    popTransitionSpec = {
                        (fadeIn(animationSpec = tween(FintechMotion.navigationMillis, easing = FintechMotion.standardEasing)) +
                            slideInHorizontally(
                                animationSpec = tween(FintechMotion.navigationMillis, easing = FintechMotion.standardEasing),
                                initialOffsetX = { -it / 8 }
                            ))
                            .togetherWith(
                                fadeOut(animationSpec = tween(FintechMotion.exitMillis, easing = FintechMotion.standardEasing)) +
                                    slideOutHorizontally(
                                        animationSpec = tween(FintechMotion.exitMillis, easing = FintechMotion.standardEasing),
                                        targetOffsetX = { it / 10 }
                                    )
                            )
                    },
                    onBack = {
                        navController.pop()
                    }
                ) { key ->
                    when (key) {
                        Route.Home -> NavEntry(key) {
                            HomeScreen(
                                onLaunchMiniApp = { path -> navController.navigate(Route.MiniApp(path)) },
                                onNavigateToWallet = { navController.navigate(Route.Wallet) },
                                onProfileClick = { navController.navigate(Route.Profile) },
                                onHelpClick = { navController.navigate(Route.ApiDocs) }
                            )
                        }
                        is Route.MiniApp -> NavEntry(key) {
                            val launchScanner = key.initialPath == "scan-qr-page"
                            MiniAppScreen(
                                initialPath = key.initialPath,
                                openDepositWizard = key.openDepositWizard,
                                openQrScanner = launchScanner,
                                onNavigateHome = { navController.pop() }
                            )
                        }
                        Route.Profile -> NavEntry(key) {
                            ProfileScreen()
                        }
                        Route.ApiKeys -> NavEntry(key) {
                            ApiKeysScreen()
                        }
                        Route.ApiDocs -> NavEntry(key) {
                            ApiDocsScreen()
                        }
                        Route.Wallet -> NavEntry(key) {
                            WalletScreen()
                        }
                        Route.Scanner -> NavEntry(key) {
                            MiniAppScreen(
                                initialPath = "scan-qr-page",
                                openQrScanner = true,
                                onNavigateHome = { navController.pop() }
                            )
                        }
                        Route.Crypto -> NavEntry(key) {
                            CryptoScreen()
                        }
                        else -> NavEntry(key) {}
                    }
                }
            }
        }
    }
}
