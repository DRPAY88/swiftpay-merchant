package com.example.myapplication.ui.theme

import androidx.compose.ui.graphics.Color

// DRPAY palette: deep navy, clear bank blue, and a restrained brass accent.
val FastPayNavy = Color(0xFF0B1F3A)
val FastPayBlue = Color(0xFF1B4F9C)
val FastPayAccent = Color(0xFFD5B16D)
val FastPayDarkNavy = Color(0xFF071429)
val FastPaySuccess = Color(0xFF16724A)
val FastPayWarning = Color(0xFF9A5B12)
val FastPayError = Color(0xFFB42332)

// Enhanced Color Palette
val FastPayPurple = Color(0xFF315F9F) // Legacy name; use the bank-blue family.
val FastPayCyan = Color(0xFF8BB7E8) // Soft bank-blue accent
val FastPayGreen = FastPaySuccess
val FastPayOrange = FastPayWarning
val FastPayRed = FastPayError
val FastPayPink = Color(0xFFB42332)

val FastPayBlack = Color(0xFF0A1424)
val FastPayDarkGray = Color(0xFF121212)
val FastPaySurface = Color(0xFFF4F7FC)
val FastPayGray = Color(0xFF8E8E93)
val FastPayTextField = Color(0xFFF2F4F7)
val FastPayLightGray = Color(0xFFEBEEF0)

// SwiftPay Primary UI Palette
val FastPayBackground = Color(0xFFF4F7FC)
val FastPayMeshBlue = Color(0xFFEAF3FF)
val FastPayMeshTeal = Color(0xFFDCEBFA)
val FastPayTextPrimary = Color(0xFF11223D)
val FastPayTextSecondary = Color(0xFF52647C)
val FastPayActionIcon = FastPayBlue

// Brand Gradients
val FastPayGradient = listOf(FastPayNavy, FastPayBlue)
val FastPayAccentGradient = listOf(FastPayBlue, FastPayAccent)
val FastPayPremiumGradient = listOf(FastPayNavy, FastPayBlue)
val FastPayGreenGradient = listOf(FastPaySuccess, Color(0xFF23815A))
val FastPayOrangeGradient = listOf(FastPayWarning, Color(0xFFB8751E))
val FastPaySoftGradient = listOf(Color(0xFFE6EEF8), Color(0xFFF4F7FC))
val FastPayMeshGradient = listOf(FastPayMeshBlue, FastPayMeshTeal, Color.White)

// Light Theme mapping
val primaryLight = FastPayNavy
val onPrimaryLight = Color.White
val primaryContainerLight = Color(0xFFE6EEF8)
val onPrimaryContainerLight = FastPayNavy
val secondaryLight = FastPayBlue
val onSecondaryLight = Color.White
val secondaryContainerLight = Color(0xFFE5EDFA)
val onSecondaryContainerLight = FastPayNavy
val tertiaryLight = FastPayAccent
val onTertiaryLight = FastPayNavy
val backgroundLight = FastPayBackground
val onBackgroundLight = FastPayTextPrimary
val surfaceLight = Color.White
val onSurfaceLight = FastPayBlack
val surfaceVariantLight = Color(0xFFEDF2F8)
val onSurfaceVariantLight = Color(0xFF50627A)
val outlineLight = Color(0xFF71839A)
val outlineVariantLight = Color(0xFFD1DCE9)
val errorLight = FastPayError
val errorContainerLight = Color(0xFFFCE8EA)
val onErrorContainerLight = Color(0xFF68131C)

// Dark Theme mapping
val primaryDark = Color(0xFF9BBCEB)
val onPrimaryDark = Color(0xFF071429)
val primaryContainerDark = Color(0xFF1D3557)
val onPrimaryContainerDark = Color(0xFFDCE7F4)
val secondaryDark = Color(0xFFB2CCF0)
val onSecondaryDark = Color(0xFF142B4A)
val secondaryContainerDark = Color(0xFF294363)
val onSecondaryContainerDark = Color(0xFFDCE7F4)
val tertiaryDark = Color(0xFFE4C789)
val onTertiaryDark = Color(0xFF32250D)
val backgroundDark = FastPayBlack
val onBackgroundDark = Color(0xFFF1F5FB)
val surfaceDark = Color(0xFF142238)
val onSurfaceDark = Color(0xFFEAF1FA)
val surfaceVariantDark = Color(0xFF20334C)
val onSurfaceVariantDark = Color(0xFFC2CDDC)
val outlineDark = Color(0xFF91A2B8)
val outlineVariantDark = Color(0xFF3D5069)
val errorDark = Color(0xFFFFB4AB)
val errorContainerDark = Color(0xFF601D27)
val onErrorContainerDark = Color(0xFFFFDAD6)

// Legacy aliases for compatibility
val MayaGreen = FastPayBlue
val MayaBlack = FastPayBlack
