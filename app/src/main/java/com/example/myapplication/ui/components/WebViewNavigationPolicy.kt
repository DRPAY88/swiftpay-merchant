package com.example.myapplication.ui.components

import java.net.URI

internal fun isTrustedWebViewAssetUrl(value: String): Boolean {
    val uri = try {
        URI(value)
    } catch (_: IllegalArgumentException) {
        return false
    }

    if (!uri.scheme.equals("file", ignoreCase = true) || uri.rawAuthority != null) return false
    val path = uri.path ?: return false
    if (!path.startsWith("/android_asset/")) return false
    return path.split('/').none { it == "." || it == ".." }
}
