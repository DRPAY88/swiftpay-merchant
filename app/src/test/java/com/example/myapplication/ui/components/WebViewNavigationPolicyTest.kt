package com.example.myapplication.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewNavigationPolicyTest {
    @Test
    fun allowsOnlyBundledAndroidAssetFiles() {
        assertTrue(isTrustedWebViewAssetUrl("file:///android_asset/index.html#topup-page"))
        assertTrue(isTrustedWebViewAssetUrl("file:///android_asset/image.png"))
        assertFalse(isTrustedWebViewAssetUrl("https://example.com/page"))
        assertFalse(isTrustedWebViewAssetUrl("file:///data/data/app/private.html"))
        assertFalse(isTrustedWebViewAssetUrl("file://attacker/android_asset/index.html"))
        assertFalse(isTrustedWebViewAssetUrl("file:///android_asset/../private.html"))
    }
}
