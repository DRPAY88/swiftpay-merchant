package com.example.myapplication.ui.theme

import androidx.compose.ui.graphics.Color

// Denshitx Bank palette: deep evergreen with a restrained brass accent.
val FastPayNavy = Color(0xFF123C35)
val FastPayBlue = Color(0xFF176B55)
val FastPayAccent = Color(0xFFD5B16D)
val FastPayDarkNavy = Color(0xFF0B2823)

// Enhanced Color Palette
val FastPayPurple = Color(0xFF6366F1) // Indigo Purple
val FastPayCyan = Color(0xFF77B99B) // Mint Accent
val FastPayGreen = Color(0xFF10B981) // Emerald Green
val FastPayOrange = Color(0xFFF97316) // Vibrant Orange
val FastPayRed = Color(0xFFEF4444) // Modern Red
val FastPayPink = Color(0xFFF43F5E) // Hot Pink

val FastPayBlack = Color(0xFF050505)
val FastPayDarkGray = Color(0xFF121212)
val FastPaySurface = Color(0xFFF4F7F5)
val FastPayGray = Color(0xFF8E8E93)
val FastPayTextField = Color(0xFFF2F4F7)
val FastPayLightGray = Color(0xFFEBEEF0)

// SwiftPay Primary UI Palette
val FastPayBackground = Color(0xFFF4F7F5)
val FastPayMeshBlue = Color(0xFFE2EEE7)
val FastPayMeshTeal = Color(0xFFD8EAE0)
val FastPayTextPrimary = Color(0xFF142B27)
val FastPayTextSecondary = Color(0xFF526861)
val FastPayActionIcon = Color(0xFF176B55)

// Brand Gradients
val FastPayGradient = listOf(FastPayNavy, FastPayBlue)
val FastPayAccentGradient = listOf(FastPayBlue, FastPayAccent)
val FastPayPremiumGradient = listOf(FastPayPurple, FastPayCyan)
val FastPayGreenGradient = listOf(FastPayGreen, FastPayCyan)
val FastPayOrangeGradient = listOf(FastPayOrange, FastPayRed)
val FastPaySoftGradient = listOf(Color(0xFFE2EEE7), Color(0xFFF4F7F5))
val FastPayMeshGradient = listOf(FastPayMeshBlue, FastPayMeshTeal, Color.White)

// Light Theme mapping
val primaryLight = FastPayNavy
val onPrimaryLight = Color.White
val primaryContainerLight = Color(0xFFE2EEE7)
val onPrimaryContainerLight = FastPayNavy
val backgroundLight = Color.White
val onBackgroundLight = FastPayBlack
val surfaceLight = Color.White
val onSurfaceLight = FastPayBlack

// Dark Theme mapping
val primaryDark = Color(0xFF9CCDB5)
val onPrimaryDark = Color(0xFF123C35)
val primaryContainerDark = Color(0xFF245548)
val onPrimaryContainerDark = Color(0xFFD9EEE2)
val backgroundDark = Color(0xFF0E1B18)
val onBackgroundDark = Color.White
val surfaceDark = Color(0xFF162622)
val onSurfaceDark = Color.White

// Legacy aliases for compatibility
val MayaGreen = FastPayBlue
val MayaBlack = FastPayBlack
