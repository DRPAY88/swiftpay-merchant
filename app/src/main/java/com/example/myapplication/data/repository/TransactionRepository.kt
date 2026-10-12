package com.example.myapplication.data.repository

import com.example.myapplication.data.SwiftPayService
import com.example.myapplication.data.TransactionStore
import com.example.myapplication.data.api.InternalTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Repository for transaction operations using SwiftPay and local store.
 */
class TransactionRepository(
    private val swiftPayService: SwiftPayService,
    private val transactionStore: TransactionStore
) {

    fun getAllTransactions(): Flow<List<InternalTransaction>> =
        transactionStore.transactions

    suspend fun saveTransaction(transaction: InternalTransaction) {
        transactionStore.record(transaction)
    }
}
