package com.example.myapplication

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.example.myapplication.data.SessionManager
import com.example.myapplication.data.SettingsManager
import com.example.myapplication.data.DevicePinManager
import com.example.myapplication.navigation.Route
import com.example.myapplication.ui.screens.ApiDocsScreen
import com.example.myapplication.ui.screens.ApiKeysScreen
import com.example.myapplication.ui.screens.HomeScreen
import com.example.myapplication.ui.screens.LoginScreen
import com.example.myapplication.ui.screens.DevicePinScreen
import com.example.myapplication.ui.screens.MiniAppScreen
import com.example.myapplication.ui.screens.MiniAppViewModel
import com.example.myapplication.ui.screens.ProfileScreen
import com.example.myapplication.ui.screens.WalletScreen
import com.example.myapplication.ui.theme.FastPayBlue
import com.example.myapplication.ui.theme.FintechMotion
import com.example.myapplication.ui.theme.FastPayTheme
import com.example.myapplication.util.DebugLogger
import com.example.myapplication.util.DeepLinkBus
import com.example.myapplication.util.DeepLinkExtractor
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.flow.collectLatest
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

class MainActivity : ComponentActivity() {
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
                    DeepLinkBus.flow.emit(map)
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
                DeepLinkBus.flow.emit(map)
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
    var hasDevicePin by remember { mutableStateOf(false) }
    var isDeviceUnlocked by remember { mutableStateOf(false) }

    DebugLogger.logAuthCheck(isLoggedIn, loggedInEmail)
    DebugLogger.logDeviceInfo()

    val backStack = remember { mutableStateListOf<Route>(Route.Login) }
    val currentRoute = backStack.lastOrNull() ?: Route.Login

    androidx.compose.runtime.LaunchedEffect(isLoggedIn) {
        hasDevicePin = isLoggedIn && withContext(Dispatchers.IO) { devicePinManager.hasPin() }
        if (!isLoggedIn) isDeviceUnlocked = false
    }

    androidx.compose.runtime.LaunchedEffect(isLoggedIn, hasDevicePin, isDeviceUnlocked) {
        when {
            isLoggedIn && hasDevicePin && !isDeviceUnlocked && currentRoute != Route.DeviceLock -> {
                backStack.clear()
                backStack.add(Route.DeviceLock)
            }
            isLoggedIn && !hasDevicePin && currentRoute != Route.Login -> {
                backStack.clear()
                backStack.add(Route.Login)
            }
            !isLoggedIn && currentRoute != Route.Login -> {
                backStack.clear()
                backStack.add(Route.Login)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isLoggedIn, hasDevicePin, isDeviceUnlocked, currentRoute) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && isLoggedIn && hasDevicePin && isDeviceUnlocked && currentRoute != Route.DeviceLock) {
                isDeviceUnlocked = false
            backStack.clear()
                backStack.add(Route.DeviceLock)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val miniAppViewModel: MiniAppViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    val navController = remember {
        object : NavController {
            override fun navigate(route: Route) {
                if (route == Route.Home) {
                    backStack.clear()
                    backStack.add(Route.Home)
                } else {
                    backStack.removeAll { it == route }
                    backStack.add(route)
                }
            }
            override fun pop() {
                if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
            }
            override fun logout() {
                isDeviceUnlocked = false
                hasDevicePin = false
                (context as? ComponentActivity)?.lifecycleScope?.launch {
                    settings.clearSession()
                    sessionManager.clearSession()
                    backStack.clear()
                    backStack.add(Route.Login)
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalNavController provides navController,
        LocalMiniAppViewModel provides miniAppViewModel
    ) {
        val currentContext = LocalContext.current
        androidx.compose.runtime.LaunchedEffect(Unit) {
            DeepLinkBus.flow.collectLatest { payload ->
                if (payload != null) {
                    navController.navigate(Route.Home)
                    val (linkId, _) = DeepLinkExtractor.extractPaymentInfo(payload)
                    if (linkId.isNotBlank()) {
                        miniAppViewModel.handleDeepLink(linkId)
                        Toast.makeText(currentContext, "Checking your payment status", Toast.LENGTH_LONG).show()
                    }
                }
            }
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
            NavigationSuiteScaffold(
                containerColor = MaterialTheme.colorScheme.background,
                navigationSuiteItems = {
                    val activeColor = FastPayBlue
                    val inactiveColor = Color.Gray.copy(alpha = 0.6f)
                    val qrRouteSelected = currentRoute is Route.MiniApp && currentRoute.initialPath == "qr-page"

                    item(
                        selected = currentRoute == Route.Home,
                        onClick = { navController.navigate(Route.Home) },
                        icon = {
                            Icon(
                                Icons.Rounded.Home,
                                contentDescription = "Home",
                                tint = if (currentRoute == Route.Home) activeColor else inactiveColor
                            )
                        },
                        label = {
                            Text(
                                "Home",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (currentRoute == Route.Home) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (currentRoute == Route.Home) activeColor else inactiveColor
                            )
                        },
                    )
                    item(
                        selected = qrRouteSelected,
                        onClick = { navController.navigate(Route.MiniApp("qr-page")) },
                        icon = {
                            Icon(
                                Icons.Rounded.QrCode2,
                                contentDescription = "QR payments",
                                tint = if (qrRouteSelected) activeColor else inactiveColor
                            )
                        },
                        label = {
                            Text(
                                "QR",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (qrRouteSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (qrRouteSelected) activeColor else inactiveColor
                            )
                        },
                    )
                    item(
                        selected = currentRoute == Route.Wallet,
                        onClick = { navController.navigate(Route.Wallet) },
                        icon = {
                            Icon(
                                Icons.Rounded.AccountBalanceWallet,
                                contentDescription = "Wallet",
                                tint = if (currentRoute == Route.Wallet) activeColor else inactiveColor
                            )
                        },
                        label = {
                            Text(
                                "Wallet",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (currentRoute == Route.Wallet) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (currentRoute == Route.Wallet) activeColor else inactiveColor
                            )
                        },
                    )
                    item(
                        selected = currentRoute == Route.Profile,
                        onClick = { navController.navigate(Route.Profile) },
                        icon = {
                            Icon(
                                Icons.Rounded.PersonOutline,
                                contentDescription = "Profile",
                                tint = if (currentRoute == Route.Profile) activeColor else inactiveColor
                            )
                        },
                        label = {
                            Text(
                                "Profile",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (currentRoute == Route.Profile) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (currentRoute == Route.Profile) activeColor else inactiveColor
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
                                initialOffsetX = { it }
                            ))
                            .togetherWith(
                                fadeOut(animationSpec = tween(FintechMotion.exitMillis, easing = FintechMotion.standardEasing)) +
                                    slideOutHorizontally(
                                        animationSpec = tween(FintechMotion.exitMillis, easing = FintechMotion.standardEasing),
                                        targetOffsetX = { -it }
                                    )
                            )
                    },
                    popTransitionSpec = {
                        (fadeIn(animationSpec = tween(FintechMotion.navigationMillis, easing = FintechMotion.standardEasing)) +
                            slideInHorizontally(
                                animationSpec = tween(FintechMotion.navigationMillis, easing = FintechMotion.standardEasing),
                                initialOffsetX = { -it }
                            ))
                            .togetherWith(
                                fadeOut(animationSpec = tween(FintechMotion.exitMillis, easing = FintechMotion.standardEasing)) +
                                    slideOutHorizontally(
                                        animationSpec = tween(FintechMotion.exitMillis, easing = FintechMotion.standardEasing),
                                        targetOffsetX = { it }
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
                                onProfileClick = { navController.navigate(Route.Profile) }
                            )
                        }
                        is Route.MiniApp -> NavEntry(key) {
                            MiniAppScreen(initialPath = key.initialPath)
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
                        else -> NavEntry(key) {}
                    }
                }
            }
        }
    }
}
