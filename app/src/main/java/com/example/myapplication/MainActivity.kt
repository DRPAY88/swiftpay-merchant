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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.example.myapplication.data.SessionManager
import com.example.myapplication.data.SettingsManager
import com.example.myapplication.navigation.Route
import com.example.myapplication.ui.screens.ApiDocsScreen
import com.example.myapplication.ui.screens.ApiKeysScreen
import com.example.myapplication.ui.screens.HomeScreen
import com.example.myapplication.ui.screens.LoginScreen
import com.example.myapplication.ui.screens.MiniAppScreen
import com.example.myapplication.ui.screens.MiniAppViewModel
import com.example.myapplication.ui.screens.ProfileScreen
import com.example.myapplication.ui.screens.WalletScreen
import com.example.myapplication.ui.theme.FastPayBlue
import com.example.myapplication.ui.theme.FastPayTheme
import com.example.myapplication.util.DebugLogger
import com.example.myapplication.util.DeepLinkBus
import com.example.myapplication.util.DeepLinkExtractor
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
    val sessionManager = remember { SessionManager(context, settings) }
    val isLoggedInFlag by settings.isLoggedIn.collectAsState(initial = false)
    val backendAccessToken by settings.backendAccessToken.collectAsState(initial = null)
    val isLoggedIn = isLoggedInFlag && !backendAccessToken.isNullOrBlank()
    val loggedInEmail by settings.loggedInEmail.collectAsState(initial = null)

    DebugLogger.logAuthCheck(isLoggedIn, loggedInEmail)
    DebugLogger.logDeviceInfo()

    val backStack = remember { mutableStateListOf<Route>(if (isLoggedIn) Route.Home else Route.Login) }
    val currentRoute = backStack.lastOrNull() ?: Route.Login

    androidx.compose.runtime.LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && backStack.contains(Route.Login)) {
            backStack.clear()
            backStack.add(Route.Home)
        } else if (!isLoggedIn && !backStack.contains(Route.Login)) {
            backStack.clear()
            backStack.add(Route.Login)
        }
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
                        Toast.makeText(currentContext, "Checking payment status with SwiftPay", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        if (currentRoute == Route.Login) {
            NavDisplay(
                modifier = Modifier.fillMaxSize(),
                backStack = backStack,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                }
            ) { key ->
                when (key) {
                    Route.Login -> NavEntry(key) {
                        LoginScreen(onLoginSuccess = { navController.navigate(Route.Home) })
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
                }
            ) {
                NavDisplay(
                    modifier = Modifier.fillMaxSize(),
                    backStack = backStack,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(300)) + slideInHorizontally(animationSpec = tween(400), initialOffsetX = { it }))
                            .togetherWith(fadeOut(animationSpec = tween(300)) + slideOutHorizontally(animationSpec = tween(400), targetOffsetX = { -it }))
                    },
                    popTransitionSpec = {
                        (fadeIn(animationSpec = tween(300)) + slideInHorizontally(animationSpec = tween(400), initialOffsetX = { -it }))
                            .togetherWith(fadeOut(animationSpec = tween(300)) + slideOutHorizontally(animationSpec = tween(400), targetOffsetX = { it }))
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
