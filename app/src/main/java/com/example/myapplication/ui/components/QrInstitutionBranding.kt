package com.example.myapplication.ui.components

import java.util.Locale

internal fun qrInstitutionLogoAsset(paymentAppName: String?): String? =
    when (paymentAppName?.trim()?.lowercase(Locale.ROOT)) {
        "alipay" -> "collect_alipay.svg"
        "toss" -> "collect_toss.png"
        "kakao" -> "collect_kakao.svg"
        "kbank" -> "collect_kbank.png"
        "gcash" -> "collect_gcash.png"
        "maya" -> "collect_maya.png"
        else -> null
    }
