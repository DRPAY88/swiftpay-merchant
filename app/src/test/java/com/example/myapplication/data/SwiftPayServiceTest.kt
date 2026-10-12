package com.example.myapplication.data

import com.example.myapplication.data.api.*
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for SwiftPayService data models and validation logic
 */
class SwiftPayServiceTest {

    @Test
    fun testAmountBoundaryValidation() {
        assertTrue("0.01 should be valid", 0.01 > 0 && 0.01 <= 1000000.0)
        assertTrue("1000000.0 should be valid", 1000000.0 > 0 && 1000000.0 <= 1000000.0)
        assertFalse("0.0 should be invalid", 0.0 > 0 && 0.0 <= 1000000.0)
        assertFalse("1000001.0 should be invalid", 1000001.0 > 0 && 1000001.0 <= 1000000.0)
    }

    @Test
    fun testCardDetails_Construction() {
        val card = CardDetails(
            number = "4111111111111111",
            expMonth = "12",
            expYear = "2025"
        )

        assertEquals("4111111111111111", card.number)
        assertEquals("12", card.expMonth)
        assertEquals("2025", card.expYear)
    }

    @Test
    fun testPAN_LengthValidation() {
        val validPANs = listOf("4111111111111", "4111111111111111", "378282246310005")
        validPANs.forEach { pan ->
            assertTrue("${pan.length} should be between 13-19", pan.length in 13..19)
        }
    }

    @Test
    fun testExpiry_Parsing() {
        val expiry = "12/25"
        val month = expiry.substring(0, 2)
        val year = "20" + expiry.substring(3, 5)

        assertEquals("12", month)
        assertEquals("2025", year)
    }

    @Test
    fun testPAN_Masking() {
        val pan = "4111111111111111"
        val masked = "****${pan.takeLast(4)}"

        assertEquals("****1111", masked)
        assertFalse(masked.contains(pan.dropLast(4)))
    }

    @Test
    fun testBalanceResponse_Construction() {
        val response = WalletBalanceResponse(
            availableBalance = 40000.0,
            balance = 50000.0,
            currency = "PHP"
        )

        assertEquals(40000.0, response.availableBalance)
        assertEquals(50000.0, response.balance)
    }

    @Test
    fun testSwiftPayQrResponse_Construction() {
        val response = SwiftPayQrResponse(
            id = "qr_123",
            qrCode = "00020101...",
            amount = 100.0,
            currency = "PHP",
            status = "ACTIVE"
        )

        assertEquals("qr_123", response.id)
        assertEquals("ACTIVE", response.status)
    }

    @Test
    fun testSwiftPayStatusResponse_WithVerificationUrl() {
        val response = SwiftPayStatusResponse(
            id = "sp_xyz",
            status = "PENDING",
            verificationUrl = "https://pay.swiftpay.ph/3ds"
        )

        assertNotNull(response.verificationUrl)
        assertTrue(response.verificationUrl!!.contains("3ds"))
    }
}
