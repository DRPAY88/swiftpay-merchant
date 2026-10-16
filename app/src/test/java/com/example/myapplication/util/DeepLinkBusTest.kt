package com.example.myapplication.util

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkBusTest {
    @Test
    fun eachDeepLinkPayloadIsConsumedOnce() = runTest {
        val payload = mapOf("linkId" to "payment-123", "status" to "SUCCESS")

        DeepLinkBus.send(payload)

        assertEquals(payload, DeepLinkBus.flow.first())
        assertNull(withTimeoutOrNull(1) { DeepLinkBus.flow.first() })
    }
}
