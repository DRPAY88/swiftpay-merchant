package com.example.myapplication.ui.constants

import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * UI-related constants for consistent styling and behavior across the app.
 */

// Animation Durations
object AnimationDurations {
    val TransitionDefault = 300.milliseconds
    val TransitionFast = 200.milliseconds
    val TransitionSlow = 500.milliseconds
    val TransactionIndicatorEnter = 300.milliseconds
    val TransactionIndicatorExit = 200.milliseconds
}

// Component Dimensions
object ComponentDimensions {
    val StandardPadding = 16.dp
    val LargePadding = 20.dp
    val SmallPadding = 8.dp
    val MediumPadding = 12.dp

    val CornerRadiusSmall = 12.dp
    val CornerRadiusMedium = 16.dp
    val CornerRadiusLarge = 18.dp

    val MinimumTouchTarget = 48.dp
}

// Timeouts and Delays
object TimeoutValues {
    val TransactionIndicatorDisplay = 6.seconds
    val NfcSessionTimeout = 90.seconds
    val NetworkTimeout = 5000.milliseconds
    val PaymentStatusDelay = 3.seconds
    val ShortDelay = 1500.milliseconds
}

// Transaction Status Keys
object TransactionStatusDefaults {
    val PENDING = "PENDING"
    val SUCCESS = "SUCCESS"
    val FAILED = "FAILED"
    val CANCELLED = "CANCELLED"
}

