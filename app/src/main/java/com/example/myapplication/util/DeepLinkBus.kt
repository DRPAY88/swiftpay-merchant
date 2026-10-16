package com.example.myapplication.util

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Simple in-app bus used to send deep-link payloads from Activity -> Compose layer.
 * Each payload is queued until the Compose layer handles it, then consumed once.
 */
object DeepLinkBus {
    private val channel = Channel<Map<String, String>>(Channel.BUFFERED)

    val flow = channel.receiveAsFlow()

    suspend fun send(payload: Map<String, String>) {
        channel.send(payload)
    }
}
