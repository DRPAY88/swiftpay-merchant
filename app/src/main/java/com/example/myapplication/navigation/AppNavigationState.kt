package com.example.myapplication.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

internal fun canProcessPaymentDeepLink(isLoggedIn: Boolean, isDeviceUnlocked: Boolean): Boolean =
    isLoggedIn && isDeviceUnlocked

internal class AppNavigationState(initialRoute: Route = Route.Login) {
    val backStack: SnapshotStateList<Route> = mutableStateListOf(initialRoute)

    val currentRoute: Route
        get() = backStack.lastOrNull() ?: Route.Login

    fun navigate(route: Route) {
        if (route.isMainTab()) {
            replaceRoot(route)
        } else if (currentRoute != route) {
            backStack.removeAll { it == route }
            backStack.add(route)
        }
    }

    fun replaceRoot(route: Route) {
        backStack.clear()
        backStack.add(route)
    }

    fun popOrNavigateHome(): Boolean {
        return when {
            backStack.size > 1 -> {
                backStack.removeAt(backStack.lastIndex)
                true
            }
            currentRoute != Route.Home -> {
                navigate(Route.Home)
                true
            }
            else -> false
        }
    }

    fun selectedMainTab(): Route? = backStack.lastOrNull { it.isMainTab() }

    private fun Route.isMainTab(): Boolean =
        this == Route.Home || this == Route.Wallet || this == Route.Crypto || this == Route.Profile
}
