package com.example.myapplication.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.myapplication.data.api.InternalTransaction
import com.example.myapplication.ui.theme.FastPayTheme

import androidx.compose.runtime.CompositionLocalProvider
import com.example.myapplication.LocalNavController
import com.example.myapplication.NavController
import com.example.myapplication.navigation.Route

val MockNavController = object : NavController {
    override fun navigate(route: Route) {}
    override fun pop() {}
    override fun logout() {}
}

@Preview(showBackground = true)
@Composable
fun TransactionDetailPreview() {
    FastPayTheme {
        CompositionLocalProvider(LocalNavController provides MockNavController) {
            TransactionDetailScreen(
                tx = InternalTransaction(
                    transactionId = "TX-992837465",
                    amount = 2500.0,
                    status = "SUCCESS",
                    date = "Oct 24, 2023, 10:30 AM"
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WalletListPreview() {
    FastPayTheme {
        CompositionLocalProvider(LocalNavController provides MockNavController) {
            WalletListContent(
                balance = 45820.50,
                transactions = listOf(
                    InternalTransaction("TX-1", 500.0, "SUCCESS", "Oct 24, 2023"),
                    InternalTransaction("TX-2", -120.0, "SUCCESS", "Oct 23, 2023"),
                    InternalTransaction("TX-3", 1000.0, "PENDING", "Oct 22, 2023")
                ),
                selectedFilter = "All",
                onFilterSelected = {},
                onTransactionClick = {}
            )
        }
    }
}
