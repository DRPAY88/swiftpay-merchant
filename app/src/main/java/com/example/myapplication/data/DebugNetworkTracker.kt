package com.example.myapplication.data

import java.text.SimpleDateFormat
import java.util.*

/**
 * Simple debug tracker to record recent network attempts for on-screen diagnostics in debug builds.
 * This file and tracker are safe to keep in source; it will only be surfaced in debug builds.
 */
object DebugNetworkTracker {
    private val attempts = ArrayDeque<NetworkAttempt>()
    private val maxSize = 40

    data class NetworkAttempt(
        val url: String,
        val code: Int,
        val note: String? = null,
        val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    )

    @Synchronized
    fun recordAttempt(url: String, code: Int, note: String? = null) {
        attempts.addFirst(NetworkAttempt(url = url, code = code, note = note))
        while (attempts.size > maxSize) attempts.removeLast()
    }

    @Synchronized
    fun getAttempts(): List<NetworkAttempt> = attempts.toList()

    @Synchronized
    fun clear() = attempts.clear()
}

