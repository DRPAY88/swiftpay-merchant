package com.example.myapplication.ui.theme

import androidx.compose.ui.graphics.Color

// Fast Pay Premium Palette (Navy Blue & Tech Blue)
val FastPayNavy = Color(0xFF002B5B) // Deep Professional Navy
val FastPayBlue = Color(0xFF0052CC) // Vibrant Tech Blue
val FastPayAccent = Color(0xFF00BFFF) // Sky Blue Accent
val FastPayDarkNavy = Color(0xFF001A36)

// Enhanced Color Palette
val FastPayPurple = Color(0xFF6366F1) // Indigo Purple
val FastPayCyan = Color(0xFF06B6D4) // Cyan Accent
val FastPayGreen = Color(0xFF10B981) // Emerald Green
val FastPayOrange = Color(0xFFF97316) // Vibrant Orange
val FastPayRed = Color(0xFFEF4444) // Modern Red
val FastPayPink = Color(0xFFF43F5E) // Hot Pink

val FastPayBlack = Color(0xFF050505)
val FastPayDarkGray = Color(0xFF121212)
val FastPaySurface = Color(0xFFF4F7FA) // Light grayish blue surface
val FastPayGray = Color(0xFF8E8E93)
val FastPayTextField = Color(0xFFF2F4F7)
val FastPayLightGray = Color(0xFFEBEEF0)

// SwiftPay Primary UI Palette
val FastPayBackground = Color(0xFFF8FAFC)
val FastPayMeshBlue = Color(0xFFD0E8FF)
val FastPayMeshTeal = Color(0xFFD1FAE5)
val FastPayTextPrimary = Color(0xFF0F172A)
val FastPayTextSecondary = Color(0xFF64748B)
val FastPayActionIcon = Color(0xFF0052CC)

// Brand Gradients
val FastPayGradient = listOf(FastPayNavy, FastPayBlue)
val FastPayAccentGradient = listOf(FastPayBlue, FastPayAccent)
val FastPayPremiumGradient = listOf(FastPayPurple, FastPayCyan)
val FastPayGreenGradient = listOf(FastPayGreen, FastPayCyan)
val FastPayOrangeGradient = listOf(FastPayOrange, FastPayRed)
val FastPaySoftGradient = listOf(Color(0xFFE0F2FE), Color(0xFFF0FDFA))
val FastPayMeshGradient = listOf(FastPayMeshBlue, FastPayMeshTeal, Color.White)

// Light Theme mapping
val primaryLight = FastPayNavy
val onPrimaryLight = Color.White
val primaryContainerLight = Color(0xFFD6E4FF)
val onPrimaryContainerLight = FastPayNavy
val backgroundLight = Color.White
val onBackgroundLight = FastPayBlack
val surfaceLight = Color.White
val onSurfaceLight = FastPayBlack

// Dark Theme mapping
val primaryDark = Color(0xFF81D4FA)
val onPrimaryDark = Color(0xFF00344F)
val primaryContainerDark = Color(0xFF004C6D)
val onPrimaryContainerDark = Color(0xFFB3E5FC)
val backgroundDark = FastPayBlack
val onBackgroundDark = Color.White
val surfaceDark = FastPayDarkGray
val onSurfaceDark = Color.White

// Legacy aliases for compatibility
val MayaGreen = FastPayBlue
val MayaBlack = FastPayBlack
