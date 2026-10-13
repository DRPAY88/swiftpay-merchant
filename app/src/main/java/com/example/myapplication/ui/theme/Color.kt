package com.example.myapplication.ui.theme

import androidx.compose.ui.graphics.Color

// Denshitx Bank palette: deep navy, clear bank blue, and a restrained brass accent.
val FastPayNavy = Color(0xFF0B1F3A)
val FastPayBlue = Color(0xFF1B4F9C)
val FastPayAccent = Color(0xFFD5B16D)
val FastPayDarkNavy = Color(0xFF071429)

// Enhanced Color Palette
val FastPayPurple = Color(0xFF6366F1) // Indigo Purple
val FastPayCyan = Color(0xFF8BB7E8) // Soft bank-blue accent
val FastPayGreen = Color(0xFF10B981) // Emerald Green
val FastPayOrange = Color(0xFFF97316) // Vibrant Orange
val FastPayRed = Color(0xFFEF4444) // Modern Red
val FastPayPink = Color(0xFFF43F5E) // Hot Pink

val FastPayBlack = Color(0xFF0E1726)
val FastPayDarkGray = Color(0xFF121212)
val FastPaySurface = Color(0xFFF4F7FC)
val FastPayGray = Color(0xFF8E8E93)
val FastPayTextField = Color(0xFFF2F4F7)
val FastPayLightGray = Color(0xFFEBEEF0)

// SwiftPay Primary UI Palette
val FastPayBackground = Color(0xFFF5F8FD)
val FastPayMeshBlue = Color(0xFFEAF3FF)
val FastPayMeshTeal = Color(0xFFDCEBFA)
val FastPayTextPrimary = Color(0xFF11223D)
val FastPayTextSecondary = Color(0xFF64748B)
val FastPayActionIcon = FastPayBlue

// Brand Gradients
val FastPayGradient = listOf(FastPayNavy, FastPayBlue, Color(0xFF2F6DE6))
val FastPayAccentGradient = listOf(FastPayBlue, FastPayAccent)
val FastPayPremiumGradient = listOf(FastPayPurple, FastPayCyan, FastPayBlue)
val FastPayGreenGradient = listOf(FastPayGreen, FastPayCyan)
val FastPayOrangeGradient = listOf(FastPayOrange, FastPayRed)
val FastPaySoftGradient = listOf(Color(0xFFE6EEF8), Color(0xFFF4F7FC))
val FastPayMeshGradient = listOf(FastPayMeshBlue, FastPayMeshTeal, Color.White)

// Light Theme mapping
val primaryLight = FastPayNavy
val onPrimaryLight = Color.White
val primaryContainerLight = Color(0xFFE6EEF8)
val onPrimaryContainerLight = FastPayNavy
val backgroundLight = Color.White
val onBackgroundLight = FastPayBlack
val surfaceLight = Color.White
val onSurfaceLight = FastPayBlack

// Dark Theme mapping
val primaryDark = Color(0xFF9BBCEB)
val onPrimaryDark = Color(0xFF071429)
val primaryContainerDark = Color(0xFF1D3557)
val onPrimaryContainerDark = Color(0xFFDCE7F4)
val backgroundDark = FastPayBlack
val onBackgroundDark = Color.White
val surfaceDark = Color(0xFF17243A)
val onSurfaceDark = Color.White

// Legacy aliases for compatibility
val MayaGreen = FastPayBlue
val MayaBlack = FastPayBlack
