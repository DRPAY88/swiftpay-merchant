package com.example.myapplication.util

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Simple in-app bus used to send deep-link payloads from Activity -> Compose layer.
 * Emitted value is a map of string extras (e.g. linkId, paymentStatus) or null.
 */
object DeepLinkBus {
    val flow = MutableSharedFlow<Map<String, String>?>(replay = 1)
}

