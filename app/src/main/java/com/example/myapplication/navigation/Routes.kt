package com.example.myapplication.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Route {
    @Serializable
    data object Login : Route

    @Serializable
    data object DeviceLock : Route

    @Serializable
    data object Home : Route

    @Serializable
    data class MiniApp(
        val initialPath: String,
        val openDepositWizard: Boolean = false
    ) : Route

    @Serializable
    data object Wallet : Route

    @Serializable
    data object Scanner : Route

    @Serializable
    data object Crypto : Route

    @Serializable
    data class Payment(val amount: Double) : Route

    @Serializable
    data object Profile : Route

    @Serializable
    data object ApiKeys : Route

    @Serializable
    data object ApiDocs : Route

}
