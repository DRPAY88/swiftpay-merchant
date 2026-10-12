package com.example.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.FastPayBlack

/**
 * Utility functions for common dialog styling and patterns.
 */
object DialogUtils {

    /**
     * Get appropriate background color for dialogs based on current theme.
     */
    @Composable
    fun getDialogBackgroundColor(): Color {
        val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
        return if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
    }

    /**
     * Get appropriate text color for dialogs based on current theme.
     */
    @Composable
    fun getDialogTextColor(): Color {
        val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
        return if (isDarkTheme) Color.White else Color(0xFF0A0E27)
    }

    /**
     * Get appropriate secondary text color for dialogs.
     */
    @Composable
    fun getDialogSecondaryTextColor(): Color {
        val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
        return if (isDarkTheme) Color.LightGray else Color.Gray
    }

    /**
     * Get appropriate accent color for the current theme.
     */
    @Composable
    fun getDialogAccentColor(): Color {
        return Color(0xFF00C389)
    }

    /**
     * Standard dialog surface modifier with consistent styling.
     */
    fun dialogSurfaceModifier(): Modifier {
        return Modifier
            .background(
                color = Color.Transparent,
                shape = RoundedCornerShape(28.dp)
            )
    }
}

/**
 * Builder for creating consistent dialog titles and descriptions.
 */
class DialogContent {
    data class Content(
        val title: String,
        val description: String,
        val accentColor: Color = Color(0xFF00C389),
        val iconResourceId: Int? = null
    )

    companion object {
        fun paymentLinkReady() = Content(
            title = "Payment Link Ready",
            description = "Share this link with your customer to collect payment.",
            accentColor = Color(0xFF00C389)
        )

        fun paymentProcessing() = Content(
            title = "Processing Payment",
            description = "Please wait while we process your payment...",
            accentColor = Color(0xFF0ACF83)
        )

        fun paymentError(message: String) = Content(
            title = "Payment Failed",
            description = message,
            accentColor = Color(0xFFFF3B30)
        )
    }
}

