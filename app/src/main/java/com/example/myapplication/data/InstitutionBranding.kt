package com.example.myapplication.data

import java.net.URI

fun institutionBrandKey(name: String): String {
    val normalizedName = name.lowercase()
        .replace("&", "and")
        .replace(Regex("\\b(incorporated|inc|corporation|corporaton|corp|limited|ltd)\\b"), "")
        .replace(Regex("[^a-z0-9]"), "")
    return institutionBrandAliases[normalizedName] ?: normalizedName
}

fun isTrustedInstitutionLogoUrl(url: String): Boolean = runCatching {
    val uri = URI(url)
    val host = uri.host?.lowercase()
    uri.scheme == "https" &&
        uri.userInfo == null &&
        (
            host == "swiftpay.ph" ||
                host?.endsWith(".swiftpay.ph") == true ||
                host in swiftPayLogoCdnHosts
            )
}.getOrDefault(false)

private val swiftPayLogoCdnHosts = setOf(
    "swiftpay-qa-gateway-frontend.s3-ap-southeast-1.amazonaws.com",
    "swiftpay-qa-gateway-frontend.s3.ap-southeast-1.amazonaws.com",
    "swiftpay-prod-gateway-frontend.s3.ap-southeast-1.amazonaws.com"
)

private val institutionBrandAliases = mapOf(
    "bdounibank" to "bdo",
    "bdonetworkbank" to "bdo",
    "bankofthephilippineislands" to "bpi",
    "bankofphilippineislands" to "bpi",
    "bpifamilybank" to "bpifamily",
    "bpifamilysavingsbank" to "bpifamily",
    "metropolitanbankandtrustco" to "metrobank",
    "metropolitanbankandtrustcompany" to "metrobank",
    "landbankofthephilippines" to "landbank",
    "philippinenationalbank" to "pnb",
    "unionbankofthephilippines" to "unionbank",
    "chinabanking" to "chinabank",
    "chinabanksavings" to "chinabank",
    "eastwestbanking" to "eastwest",
    "eastwestruralbank" to "eastwest",
    "gotymebank" to "gotyme",
    "gxchangegcash" to "gcash",
    "mayabank" to "maya",
    "mayaphilippines" to "maya",
    "rcbcsavingsbank" to "rcbc"
)
