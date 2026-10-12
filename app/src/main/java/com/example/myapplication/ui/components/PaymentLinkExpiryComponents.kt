package com.example.myapplication.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.*

/**
 * Data class for link expiry information.
 */
data class PaymentLinkExpiry(
    val expiryDate: Long,
    val isExpired: Boolean = false,
    val hours: Int = 24,
    val days: Int = 0
) {
    val displayText: String
        get() = when {
            isExpired -> "Link Expired"
            days > 0 -> "$days ${if (days == 1) "day" else "days"} left"
            hours > 0 -> "$hours ${if (hours == 1) "hour" else "hours"} left"
            else -> "Expiring soon"
        }
}

/**
 * Composable that calculates and updates link expiry information.
 */
@Composable
fun rememberPaymentLinkExpiry(expiryTimestamp: Long): PaymentLinkExpiry {
    var expiry by remember { mutableStateOf(calculateExpiry(expiryTimestamp)) }

    LaunchedEffect(expiryTimestamp) {
        while (true) {
            delay(60000) // Update every minute
            expiry = calculateExpiry(expiryTimestamp)
        }
    }

    return expiry
}

private fun calculateExpiry(expiryTimestamp: Long): PaymentLinkExpiry {
    val now = System.currentTimeMillis()
    val isExpired = now > expiryTimestamp

    if (isExpired) {
        return PaymentLinkExpiry(expiryTimestamp, isExpired = true)
    }

    val diffMs = expiryTimestamp - now
    val hours = (diffMs / (1000 * 60 * 60)).toInt()
    val days = hours / 24

    return PaymentLinkExpiry(
        expiryDate = expiryTimestamp,
        isExpired = false,
        hours = hours % 24,
        days = days
    )
}

/**
 * Widget showing link expiry countdown.
 */
@Composable
fun PaymentLinkExpiryWidget(
    expiryTimestamp: Long,
    modifier: Modifier = Modifier
) {
    val expiry = rememberPaymentLinkExpiry(expiryTimestamp)
    val isDarkTheme = MaterialTheme.colorScheme.background == Color(0xFF000000)
    val bgColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)
    val textColor = if (isDarkTheme) Color.White else Color(0xFF333333)

    // Color based on expiry urgency
    val warningColor = when {
        expiry.isExpired -> Color(0xFFEF4444)
        expiry.days == 0 && expiry.hours < 6 -> Color(0xFFF97316)
        else -> Color(0xFF10B981)
    }

    AnimatedContent(
        targetState = expiry.displayText,
        modifier = modifier,
        label = "expiryAnimation"
    ) { text ->
        Row(
            modifier = Modifier
                .background(bgColor, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = "Expiry",
                modifier = Modifier.size(16.dp),
                tint = warningColor
            )

            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Alert banner for expired or expiring soon links.
 */
@Composable
fun PaymentLinkExpiryAlert(
    expiry: PaymentLinkExpiry,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.background == Color(0xFF000000)

    if (expiry.isExpired) {
        val bgColor = if (isDarkTheme) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.1f)
        val textColor = Color(0xFFEF4444)

        Surface(
            modifier = modifier.fillMaxWidth(),
            color = bgColor,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = "Expired",
                    modifier = Modifier.size(18.dp),
                    tint = textColor
                )

                Text(
                    text = "This payment link has expired. Generate a new one to continue accepting payments.",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            }
        }
    } else if (expiry.days == 0 && expiry.hours < 6) {
        val bgColor = if (isDarkTheme) Color(0xFFF97316).copy(alpha = 0.15f) else Color(0xFFF97316).copy(alpha = 0.1f)
        val textColor = Color(0xFFF97316)

        Surface(
            modifier = modifier.fillMaxWidth(),
            color = bgColor,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = "Expiring Soon",
                    modifier = Modifier.size(18.dp),
                    tint = textColor
                )

                Text(
                    text = "This link expires soon (${expiry.displayText}). Share it now!",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            }
        }
    }
}


