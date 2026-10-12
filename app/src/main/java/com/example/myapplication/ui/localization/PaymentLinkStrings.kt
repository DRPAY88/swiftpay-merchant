package com.example.myapplication.ui.localization

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.R

/**
 * Centralized localization for payment link UI strings.
 * Supports multiple languages and easy future expansion.
 */
data class PaymentLinkLocalization(
    val paymentLinkReady: String = "Payment Link Ready",
    val shareThisLink: String = "Share this link with your customer to collect payment.",
    val shareLink: String = "Share Link",
    val openLink: String = "Open",
    val closeLink: String = "Close",
    val linkCopiedSuccess: String = "Link copied!",
    val generatePaymentLink: String = "Generating payment link...",
    val failedToGenerateLink: String = "Failed to generate payment link",
    val unexpectedError: String = "Unexpected error",
    val linkExpiresIn: String = "Link expires in",
    val hours: String = "hours",
    val days: String = "days",
    val copyLink: String = "Copy Link",
    val invalidPaymentAmount: String = "Invalid payment amount. Amount must be greater than ₱0.00",
    val amountExceedsLimit: String = "Payment amount exceeds maximum limit of ₱1,000,000",
    val selectLanguage: String = "Select Language",
    val english: String = "English",
    val tagalog: String = "Tagalog",
    val spanish: String = "Spanish (Español)"
)

/**
 * Factory for creating localized strings based on language code.
 */
object PaymentLinkLocalizationFactory {

    /**
     * Get localization strings for the specified language.
     * Defaults to English if language not found.
     */
    fun getLocalization(languageCode: String = "en"): PaymentLinkLocalization {
        return when (languageCode.lowercase()) {
            "tl", "fil" -> getTagalogLocalization()
            "es" -> getSpanishLocalization()
            "en", "en-US", "en-PH" -> getEnglishLocalization()
            else -> getEnglishLocalization()
        }
    }

    /**
     * English localization (default)
     */
    private fun getEnglishLocalization() = PaymentLinkLocalization(
        paymentLinkReady = "Payment Link Ready",
        shareThisLink = "Share this link with your customer to collect payment.",
        shareLink = "Share Link",
        openLink = "Open",
        closeLink = "Close",
        linkCopiedSuccess = "Link copied!",
        generatePaymentLink = "Generating payment link...",
        failedToGenerateLink = "Failed to generate payment link",
        unexpectedError = "Unexpected error",
        linkExpiresIn = "Link expires in",
        hours = "hours",
        days = "days",
        copyLink = "Copy Link",
        invalidPaymentAmount = "Invalid payment amount. Amount must be greater than ₱0.00",
        amountExceedsLimit = "Payment amount exceeds maximum limit of ₱1,000,000",
        selectLanguage = "Select Language",
        english = "English",
        tagalog = "Tagalog",
        spanish = "Spanish (Español)"
    )

    /**
     * Tagalog localization
     */
    private fun getTagalogLocalization() = PaymentLinkLocalization(
        paymentLinkReady = "Handa na ang Payment Link",
        shareThisLink = "Ibahagi ang link na ito sa iyong customer upang makatanggap ng bayad.",
        shareLink = "Ibahagi ang Link",
        openLink = "Buksan",
        closeLink = "Isara",
        linkCopiedSuccess = "Nakopya ang link!",
        generatePaymentLink = "Lumilikha ng payment link...",
        failedToGenerateLink = "Bigo ang paglikha ng payment link",
        unexpectedError = "Hindi inaasahang pagkakamali",
        linkExpiresIn = "Mag-expire ang link sa loob ng",
        hours = "oras",
        days = "araw",
        copyLink = "Kopyahin ang Link",
        invalidPaymentAmount = "Invalid na halaga ng bayad. Ang halaga ay dapat mas malaki kaysa ₱0.00",
        amountExceedsLimit = "Ang halaga ng bayad ay lumampas sa maximum na limitasyon ng ₱1,000,000",
        selectLanguage = "Pumili ng Wika",
        english = "English",
        tagalog = "Tagalog",
        spanish = "Spanish (Español)"
    )

    /**
     * Spanish localization
     */
    private fun getSpanishLocalization() = PaymentLinkLocalization(
        paymentLinkReady = "Enlace de Pago Listo",
        shareThisLink = "Comparte este enlace con tu cliente para cobrar el pago.",
        shareLink = "Compartir Enlace",
        openLink = "Abrir",
        closeLink = "Cerrar",
        linkCopiedSuccess = "¡Enlace copiado!",
        generatePaymentLink = "Generando enlace de pago...",
        failedToGenerateLink = "Error al generar enlace de pago",
        unexpectedError = "Error inesperado",
        linkExpiresIn = "El enlace expira en",
        hours = "horas",
        days = "días",
        copyLink = "Copiar Enlace",
        invalidPaymentAmount = "Cantidad de pago inválida. La cantidad debe ser mayor que ₱0.00",
        amountExceedsLimit = "La cantidad de pago excede el límite máximo de ₱1,000,000",
        selectLanguage = "Seleccionar Idioma",
        english = "English",
        tagalog = "Tagalog",
        spanish = "Spanish (Español)"
    )
}

/**
 * Composable function to get current localization strings.
 * Uses system locale if available.
 */
@Composable
fun rememberPaymentLinkLocalization(): PaymentLinkLocalization {
    val configuration = LocalConfiguration.current
    val locale = configuration.locale
    val languageCode = locale.language
    return PaymentLinkLocalizationFactory.getLocalization(languageCode)
}



