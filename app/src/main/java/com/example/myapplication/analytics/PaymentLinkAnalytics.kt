package com.example.myapplication.analytics

import android.content.Context
import android.util.Log
import com.example.myapplication.data.TransactionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Events that can be tracked for payment link operations.
 */
sealed class PaymentLinkAnalyticsEvent {
    data class LinkGenerated(
        val linkId: String,
        val amount: Double,
        val hasFixedAmount: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    ) : PaymentLinkAnalyticsEvent()

    data class LinkShared(
        val linkId: String,
        val shareMethod: String = "system",  // "system", "sms", "email", etc.
        val timestamp: Long = System.currentTimeMillis()
    ) : PaymentLinkAnalyticsEvent()

    data class LinkOpened(
        val linkId: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : PaymentLinkAnalyticsEvent()

    data class LinkCopied(
        val linkId: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : PaymentLinkAnalyticsEvent()

    data class LinkFailed(
        val errorMessage: String,
        val errorCode: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : PaymentLinkAnalyticsEvent()

    data class GenerationCompleted(
        val linkId: String,
        val amount: Double,
        val durationMs: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) : PaymentLinkAnalyticsEvent()
}

/**
 * Analytics tracker for payment link operations.
 * Tracks user interactions and link usage patterns.
 */
class PaymentLinkAnalyticsTracker(private val context: Context) {
    private val tag = "PaymentLinkAnalytics"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * Track a payment link analytics event.
     */
    fun trackEvent(event: PaymentLinkAnalyticsEvent) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (event) {
                    is PaymentLinkAnalyticsEvent.LinkGenerated -> {
                        logEvent(
                            "LINK_GENERATED",
                            mapOf(
                                "linkId" to event.linkId,
                                "amount" to event.amount.toString(),
                                "hasFixedAmount" to event.hasFixedAmount.toString(),
                                "timestamp" to dateFormat.format(Date(event.timestamp))
                            )
                        )
                    }
                    is PaymentLinkAnalyticsEvent.LinkShared -> {
                        logEvent(
                            "LINK_SHARED",
                            mapOf(
                                "linkId" to event.linkId,
                                "shareMethod" to event.shareMethod,
                                "timestamp" to dateFormat.format(Date(event.timestamp))
                            )
                        )
                    }
                    is PaymentLinkAnalyticsEvent.LinkOpened -> {
                        logEvent(
                            "LINK_OPENED",
                            mapOf(
                                "linkId" to event.linkId,
                                "timestamp" to dateFormat.format(Date(event.timestamp))
                            )
                        )
                    }
                    is PaymentLinkAnalyticsEvent.LinkCopied -> {
                        logEvent(
                            "LINK_COPIED",
                            mapOf(
                                "linkId" to event.linkId,
                                "timestamp" to dateFormat.format(Date(event.timestamp))
                            )
                        )
                    }
                    is PaymentLinkAnalyticsEvent.LinkFailed -> {
                        logEvent(
                            "LINK_GENERATION_FAILED",
                            mapOf(
                                "errorMessage" to event.errorMessage,
                                "errorCode" to (event.errorCode ?: "UNKNOWN"),
                                "timestamp" to dateFormat.format(Date(event.timestamp))
                            )
                        )
                    }
                    is PaymentLinkAnalyticsEvent.GenerationCompleted -> {
                        logEvent(
                            "GENERATION_COMPLETED",
                            mapOf(
                                "linkId" to event.linkId,
                                "amount" to event.amount.toString(),
                                "durationMs" to event.durationMs.toString(),
                                "timestamp" to dateFormat.format(Date(event.timestamp))
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error tracking event: ${e.message}", e)
            }
        }
    }

    /**
     * Log event to logcat with formatted output.
     */
    private fun logEvent(eventName: String, properties: Map<String, String>) {
        val propertiesStr = properties.entries.joinToString(", ") { "${it.key}=${it.value}" }
        Log.i(tag, "EVENT: $eventName | $propertiesStr")
    }

    /**
     * Get analytics summary for a specific period.
     */
    fun getAnalyticsSummary(daysBack: Int = 7): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -daysBack)
        val timestamp = calendar.timeInMillis
        val dateStr = dateFormat.format(Date(timestamp))

        return buildString {
            appendLine("Payment Link Analytics Summary (Last $daysBack days)")
            appendLine("Generated At: ${dateFormat.format(Date())}")
            appendLine("Period: From $dateStr")
            appendLine("---")
            appendLine("Note: Check logcat for detailed event logs")
        }
    }
}

