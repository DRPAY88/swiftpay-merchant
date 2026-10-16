package com.example.myapplication.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.myapplication.data.api.InternalTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.transactionDataStore: DataStore<Preferences> by preferencesDataStore(name = "transactions")

class TransactionStore(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val transactionListKey = stringPreferencesKey("transaction_list")
    private val listSerializer = ListSerializer(InternalTransaction.serializer())

    val transactions: Flow<List<InternalTransaction>> = context.transactionDataStore.data.map { preferences ->
        preferences[transactionListKey]?.let { stored ->
            runCatching { json.decodeFromString(listSerializer, stored) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun record(transaction: InternalTransaction) {
        context.transactionDataStore.edit { preferences ->
            val current = preferences[transactionListKey]?.let { stored ->
                runCatching { json.decodeFromString(listSerializer, stored) }.getOrDefault(emptyList())
            } ?: emptyList()

            val existing = current.find { it.transactionId == transaction.transactionId }
            val updated = if (existing != null) {
                existing.copy(
                    amount = if (transaction.amount > 0) transaction.amount else existing.amount,
                    status = transaction.status,
                    date = transaction.date
                )
            } else {
                transaction
            }

            val merged = listOf(updated)
                .plus(current.filterNot { it.transactionId == transaction.transactionId })
                .take(MAX_TRANSACTIONS)

            preferences[transactionListKey] = json.encodeToString(listSerializer, merged)
        }
    }

    companion object {
        private const val MAX_TRANSACTIONS = 100

        fun nowLabel(): String {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        }
    }
}

fun mergeTransactions(
    local: List<InternalTransaction>,
    remote: List<InternalTransaction>
): List<InternalTransaction> {
    val all = local + remote
    
    return all
        .filter { it.transactionId.isNotBlank() }
        .groupBy { it.transactionId }
        .map { (_, versions) ->
            val preferred = versions.last()
            preferred.copy(
                transactionType = preferred.transactionType
                    ?: versions.firstNotNullOfOrNull { it.transactionType },
                referenceNo = preferred.referenceNo
                    ?: versions.firstNotNullOfOrNull { it.referenceNo },
                description = preferred.description
                    ?: versions.firstNotNullOfOrNull { it.description },
                bankName = preferred.bankName
                    ?: versions.firstNotNullOfOrNull { it.bankName }
            )
        }
        .sortedByDescending { it.date }
}
