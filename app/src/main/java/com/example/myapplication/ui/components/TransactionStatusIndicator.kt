package com.example.myapplication.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.api.VaultPaymentResponse
import com.example.myapplication.ui.constants.AnimationDurations
import com.example.myapplication.ui.constants.ComponentDimensions
import com.example.myapplication.util.getStatusColor
import com.example.myapplication.util.getStatusTextColor
import kotlinx.coroutines.delay

/**
 * Reusable transaction status indicator component.
 * Displays payment status in a compact pill/badge format with auto-clear capability.
 *
 * @param visible Whether the indicator is visible (drives AnimatedVisibility)
 * @param transactionId The ID of the transaction being displayed
 * @param transactionStatus The VaultPaymentResponse containing status details
 * @param onAutoClose Callback invoked when auto-clear timeout expires
 * @param modifier Optional modifier for customization
 */
@Composable
fun TransactionStatusIndicator(
    visible: Boolean,
    transactionId: String,
    transactionStatus: VaultPaymentResponse,
    onAutoClose: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(AnimationDurations.TransactionIndicatorEnter.inWholeMilliseconds.toInt())) +
                expandVertically(),
        exit = fadeOut(tween(AnimationDurations.TransactionIndicatorExit.inWholeMilliseconds.toInt())) +
               shrinkVertically(),
        modifier = modifier
    ) {
        val status = transactionStatus.status ?: "PENDING"
        val bgColor = getStatusColor(status)
        val textColor = getStatusTextColor(status)

        Surface(
            shape = RoundedCornerShape(ComponentDimensions.CornerRadiusSmall),
            color = bgColor.copy(alpha = 0.12f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ComponentDimensions.SmallPadding)
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = ComponentDimensions.MediumPadding,
                    vertical = ComponentDimensions.SmallPadding
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = bgColor,
                    modifier = Modifier.size(10.dp)
                ) {}
                Spacer(Modifier.width(ComponentDimensions.SmallPadding))
                Text(
                    text = "Payment $transactionId: $status",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Updated",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }

    // Trigger auto-close after delay
    LaunchedEffect(transactionId) {
        if (visible) {
            delay(6000L) // 6 seconds display time
            try {
                onAutoClose(transactionId)
            } catch (_: Exception) {
                // Ignore errors during cleanup
            }
        }
    }
}

