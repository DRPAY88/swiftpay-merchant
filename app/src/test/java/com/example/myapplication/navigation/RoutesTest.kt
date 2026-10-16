package com.example.myapplication.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    @Test
    fun paymentDeepLinkRequiresAnAuthenticatedUnlockedSession() {
        assertEquals(false, canProcessPaymentDeepLink(isLoggedIn = false, isDeviceUnlocked = true))
        assertEquals(false, canProcessPaymentDeepLink(isLoggedIn = true, isDeviceUnlocked = false))
        assertEquals(true, canProcessPaymentDeepLink(isLoggedIn = true, isDeviceUnlocked = true))
    }

    @Test
    fun selectingMainTabReplacesTheCurrentBackStack() {
        val navigationState = AppNavigationState(Route.Home)
        navigationState.navigate(Route.MiniApp("topup-page"))

        navigationState.navigate(Route.Wallet)

        assertEquals(listOf(Route.Wallet), navigationState.backStack)
        assertEquals(Route.Wallet, navigationState.selectedMainTab())
    }

    @Test
    fun secondaryDestinationsKeepTheirMainTabSelected() {
        val navigationState = AppNavigationState(Route.Profile)

        navigationState.navigate(Route.ApiKeys)

        assertEquals(listOf(Route.Profile, Route.ApiKeys), navigationState.backStack)
        assertEquals(Route.Profile, navigationState.selectedMainTab())
    }

    @Test
    fun selectingTheCurrentMainTabResetsNestedDestinations() {
        val navigationState = AppNavigationState(Route.Home)
        navigationState.navigate(Route.MiniApp("payment-link-page"))

        navigationState.navigate(Route.Home)

        assertEquals(listOf(Route.Home), navigationState.backStack)
    }

    @Test
    fun walletTopUpOpensTheTopUpMiniApp() {
        val navigationState = AppNavigationState(Route.Wallet)
        val topUpRoute = Route.MiniApp(initialPath = "topup-page")

        navigationState.navigate(topUpRoute)

        assertEquals(topUpRoute, navigationState.backStack.last())
    }

    @Test
    fun backingOutOfWalletMiniAppReturnsToWallet() {
        val navigationState = AppNavigationState(Route.Wallet)
        navigationState.navigate(Route.MiniApp(initialPath = "disbursement-page"))

        assertEquals(true, navigationState.popOrNavigateHome())
        assertEquals(Route.Wallet, navigationState.currentRoute)
    }

    @Test
    fun poppingNestedDestinationReturnsToPreviousRoute() {
        val navigationState = AppNavigationState(Route.Home)
        navigationState.navigate(Route.Profile)
        navigationState.navigate(Route.ApiKeys)

        assertEquals(true, navigationState.popOrNavigateHome())
        assertEquals(Route.Profile, navigationState.currentRoute)
    }

    @Test
    fun poppingFromRootNavigatesHomeUnlessAlreadyHome() {
        val navigationState = AppNavigationState(Route.Profile)

        assertEquals(true, navigationState.popOrNavigateHome())
        assertEquals(Route.Home, navigationState.currentRoute)
        assertEquals(false, navigationState.popOrNavigateHome())
    }
}
