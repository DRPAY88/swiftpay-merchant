package com.example.myapplication.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    @Test
    fun selectingMainTabReplacesTheCurrentBackStack() {
        val backStack = mutableListOf<Route>(Route.Home, Route.MiniApp("topup-page"))

        backStack.navigateTo(Route.Wallet)

        assertEquals(listOf(Route.Wallet), backStack)
        assertEquals(Route.Wallet, backStack.selectedMainTab())
    }

    @Test
    fun secondaryDestinationsKeepTheirMainTabSelected() {
        val backStack = mutableListOf<Route>(Route.Profile)

        backStack.navigateTo(Route.ApiKeys)

        assertEquals(listOf(Route.Profile, Route.ApiKeys), backStack)
        assertEquals(Route.Profile, backStack.selectedMainTab())
    }

    @Test
    fun selectingTheCurrentMainTabResetsNestedDestinations() {
        val backStack = mutableListOf<Route>(Route.Home, Route.MiniApp("payment-link-page"))

        backStack.navigateTo(Route.Home)

        assertEquals(listOf(Route.Home), backStack)
    }
}
