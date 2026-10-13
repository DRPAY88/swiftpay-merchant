package com.example.myapplication.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstitutionBrandingTest {
    @Test
    fun resolvesBankAndWalletNameVariantsToTheirBrandKey() {
        assertEquals("bpi", institutionBrandKey("Bank of the Philippine Islands"))
        assertEquals("bpifamily", institutionBrandKey("BPI Family Savings Bank"))
        assertEquals("metrobank", institutionBrandKey("METROPOLITAN BANK AND TRUST CO"))
        assertEquals("gcash", institutionBrandKey("G-Xchange, Inc. (GCash)"))
        assertEquals("gotyme", institutionBrandKey("GoTyme Bank Corporation"))
        assertEquals("maya", institutionBrandKey("Maya Bank, Inc."))
    }

    @Test
    fun allowsOnlyHttpsSwiftPayLogoHosts() {
        assertTrue(isTrustedInstitutionLogoUrl("https://pay.live.swiftpay.ph/static/bpi.png"))
        assertTrue(
            isTrustedInstitutionLogoUrl(
                "https://swiftpay-qa-gateway-frontend.s3-ap-southeast-1.amazonaws.com/bpi.png"
            )
        )
        assertFalse(isTrustedInstitutionLogoUrl("http://pay.live.swiftpay.ph/bpi.png"))
        assertFalse(isTrustedInstitutionLogoUrl("https://logos.example/bpi.png"))
        assertFalse(isTrustedInstitutionLogoUrl("https://pay.live.swiftpay.ph.example/bpi.png"))
    }
}
