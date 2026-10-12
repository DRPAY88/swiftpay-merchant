package com.example.myapplication.util

import android.os.Bundle

/**
 * Helper class to extract and process deep link payloads.
 */
object DeepLinkExtractor {
    /**
     * Convert a Bundle to a map of String key-value pairs.
     */
    fun bundleToMap(bundle: Bundle?): Map<String, String>? {
        if (bundle == null) return null

        val map = mutableMapOf<String, String>()
        for (key in bundle.keySet()) {
            val value = bundle.get(key)?.toString()
            if (value != null) {
                map[key] = value
            }
        }
        return if (map.isEmpty()) null else map
    }

    /**
     * Extract payment identity and status from deep link payload.
     * Returns a Pair of (linkId, status).
     */
    fun extractPaymentInfo(payload: Map<String, String>): Pair<String, String> {
        val linkId = payload["linkId"] ?: payload["paymentId"] ?: ""
        val status = payload["paymentStatus"] ?: payload["status"] ?: ""
        return linkId to status
    }

    /**
     * Format a deep link response message for display to user.
     */
    fun formatPaymentMessage(linkId: String, status: String): String {
        return buildString {
            append("Payment status: ")
            if (status.isNotEmpty()) {
                append(status)
            } else {
                append("updated")
            }
            if (linkId.isNotEmpty()) {
                append(" ($linkId)")
            }
        }
    }
}

