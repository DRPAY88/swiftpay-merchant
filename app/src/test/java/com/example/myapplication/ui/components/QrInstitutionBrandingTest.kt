package com.example.myapplication.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrInstitutionBrandingTest {
    @Test
    fun mapsSupportedPaymentAppsToTheirLogos() {
        assertEquals("collect_alipay.svg", qrInstitutionLogoAsset("Alipay"))
        assertEquals("collect_toss.png", qrInstitutionLogoAsset("Toss"))
        assertEquals("collect_kakao.svg", qrInstitutionLogoAsset("Kakao"))
        assertEquals("collect_kbank.png", qrInstitutionLogoAsset("KBank"))
        assertEquals("collect_gcash.png", qrInstitutionLogoAsset("GCash"))
        assertEquals("collect_maya.png", qrInstitutionLogoAsset("Maya"))
    }

    @Test
    fun matchesNamesWithoutCaseOrSurroundingWhitespace() {
        assertEquals("collect_toss.png", qrInstitutionLogoAsset("  tOsS "))
    }

    @Test
    fun genericOrUnknownSelectionDoesNotReuseQrPhBranding() {
        assertNull(qrInstitutionLogoAsset("QR Ph"))
        assertNull(qrInstitutionLogoAsset(null))
        assertNull(qrInstitutionLogoAsset("Unknown"))
    }
}
