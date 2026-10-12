package com.example.myapplication.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPhPayloadParserTest {
    @Test
    fun parsesFixedAmountFromEmvPayload() {
        val parsed = parseScannedQrPayment(withValidCrc("000201540510.50"))

        assertNotNull(parsed)
        assertEquals(10.5, parsed?.amount ?: 0.0, 0.001)
        assertEquals("608", parsed?.currencyCode)
        assertEquals("₱", parsed?.currencySymbol)
    }

    @Test
    fun acceptsPayloadWithoutAmount() {
        val parsed = parseScannedQrPayment(withValidCrc("000201"))

        assertNotNull(parsed)
        assertNull(parsed?.amount)
    }

    @Test
    fun rejectsMalformedTlvPayload() {
        assertNull(parseScannedQrPayment("540510.50"))
        assertNull(parseScannedQrPayment("00020"))
        assertNull(parseScannedQrPayment("x".repeat(4097)))
        assertNull(parseScannedQrPayment("000201540510.506304ABCD"))
    }

    @Test
    fun generatesAndParsesAlipayConnectTossKrwPayload() {
        val payload = generateAlipayConnectTossQrPhPayload(
            amount = 25000.0,
            currency = "KRW",
            merchantName = "Fast Pay Merchant",
            referenceNo = "TOSS-1001"
        )

        val parsed = parseScannedQrPayment(payload)

        assertNotNull(parsed)
        assertEquals(25000.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals("410", parsed?.currencyCode)
        assertEquals("₩", parsed?.currencySymbol)
        assertTrue(parsed?.isAlipayConnectSupported == true)
        assertTrue(parsed?.isTossCompatible == true)
        assertTrue(parsed?.isKoreanBankCompatible == true)
        assertNotNull(parsed?.amountInPhp)
    }

    @Test
    fun generatesAndParsesKoreanCrossBorderBankPayload() {
        val payload = generateKoreanCrossBorderQrPhPayload(
            amount = 50000.0,
            currency = "KRW",
            merchantName = "Fast Pay Merchant",
            referenceNo = "KB-2002"
        )

        val parsed = parseScannedQrPayment(payload)

        assertNotNull(parsed)
        assertEquals(50000.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals("410", parsed?.currencyCode)
        assertEquals("₩", parsed?.currencySymbol)
        assertTrue(parsed?.isKoreanBankCompatible == true)
    }

    private fun withValidCrc(body: String): String {
        val checksumPayload = "${body}6304"
        var crc = 0xFFFF
        checksumPayload.toByteArray(Charsets.UTF_8).forEach { byte ->
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return checksumPayload + crc.toString(16).uppercase().padStart(4, '0')
    }
}
