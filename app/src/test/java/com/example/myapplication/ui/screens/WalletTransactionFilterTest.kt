package com.example.myapplication.ui.screens

import com.example.myapplication.data.api.InternalTransaction
import com.example.myapplication.data.api.SwiftPayStatusResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class WalletTransactionFilterTest {
    private val transactions = listOf(
        InternalTransaction(
            transactionId = "DEP-001",
            amount = 500.0,
            status = "RECORDED",
            date = "2026-10-18",
            transactionType = "MANUAL_BANK_DEPOSIT"
        ),
        InternalTransaction(
            transactionId = "PAY-001",
            amount = 250.0,
            status = "PENDING",
            date = "2026-10-18",
            transactionType = "PAYMENT"
        ),
        InternalTransaction(
            transactionId = "TRF-001",
            amount = -100.0,
            status = "EXECUTED",
            date = "2026-10-18",
            transactionType = "DISBURSEMENT"
        )
    )

    @Test
    fun filtersIncomeExpenseAndManualDepositRecords() {
        assertEquals(
            listOf("DEP-001", "PAY-001"),
            filterWalletTransactions(transactions, "Income", emptyMap()).map { it.transactionId }
        )
        assertEquals(
            listOf("TRF-001"),
            filterWalletTransactions(transactions, "Expense", emptyMap()).map { it.transactionId }
        )
        assertEquals(
            listOf("DEP-001"),
            filterWalletTransactions(transactions, "Bank Deposits", emptyMap()).map { it.transactionId }
        )
    }

    @Test
    fun failedFilterUsesLatestProviderStatusWhenAvailable() {
        val statuses = mapOf(
            "PAY-001" to SwiftPayStatusResponse(status = "PAYMENT_EXPIRED")
        )

        assertEquals(
            listOf("PAY-001"),
            filterWalletTransactions(transactions, "Failed", statuses).map { it.transactionId }
        )
    }

    @Test
    fun failedStatusIncludesExpiredAndCancelledPayments() {
        assertEquals(true, isFailedTransactionStatus("PAYMENT_EXPIRED"))
        assertEquals(true, isFailedTransactionStatus("CANCELED"))
        assertEquals(false, isFailedTransactionStatus("EXECUTED"))
    }

    @Test
    fun statusClassificationDistinguishesProcessingSuccessAndUnknown() {
        assertEquals(true, isPendingTransactionStatus("PROCESSING"))
        assertEquals(true, isPendingTransactionStatus("WAITING"))
        assertEquals(true, isSuccessfulTransactionStatus("COMPLETED"))
        assertEquals(true, isSuccessfulTransactionStatus("EXECUTED"))
        assertEquals(false, isSuccessfulTransactionStatus("PROCESSING"))
        assertEquals(false, isSuccessfulTransactionStatus("UNKNOWN"))
    }
}
