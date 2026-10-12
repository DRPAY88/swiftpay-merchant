package com.example.myapplication.ui.components

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.myapplication.analytics.PaymentLinkAnalyticsEvent
import com.example.myapplication.analytics.PaymentLinkAnalyticsTracker
import com.example.myapplication.ui.localization.rememberPaymentLinkLocalization
import com.example.myapplication.ui.theme.FastPayBlack
import com.example.myapplication.ui.theme.FastPayNavy
import com.example.myapplication.ui.components.FastPayInfoCard

/**
 * Enhanced payment link dialog with localization, analytics, and expiry support.
 * Replaces the basic PaymentLinkDialog with advanced features.
 */
@Composable
fun EnhancedPaymentLinkDialog(
    url: String,
    linkId: String = "LINK${System.currentTimeMillis()}",
    amount: Double = 0.0,
    hasFixedAmount: Boolean = false,
    expiryTimestamp: Long? = null,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val localization = rememberPaymentLinkLocalization()
    val analyticsTracker = remember { PaymentLinkAnalyticsTracker(context) }
    val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
    val bgColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkTheme) Color.White else FastPayNavy

    // Track dialog view
    LaunchedEffect(linkId) {
        analyticsTracker.trackEvent(
            PaymentLinkAnalyticsEvent.LinkGenerated(
                linkId = linkId,
                amount = amount,
                hasFixedAmount = hasFixedAmount
            )
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = bgColor,
            tonalElevation = 12.dp,
            modifier = modifier.fillMaxWidth(0.9f)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon header with animation
                val scale by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "iconScale"
                )

                Surface(
                    shape = CircleShape,
                    color = Color(0xFF00C389).copy(alpha = 0.12f),
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Link,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = Color(0xFF00C389)
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Title
                Text(
                    localization.paymentLinkReady,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor
                )

                Spacer(Modifier.height(8.dp))

                // Description
                Text(
                    localization.shareThisLink,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                // Amount display (if fixed)
                if (hasFixedAmount && amount > 0) {
                    FastPayInfoCard(
                        title = "Amount",
                        value = "₱${String.format("%.2f", amount)}",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // Expiry information
                if (expiryTimestamp != null) {
                    val expiry = rememberPaymentLinkExpiry(expiryTimestamp)
                    PaymentLinkExpiryWidget(
                        expiryTimestamp = expiryTimestamp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))

                    // Show alert if expiring soon
                    PaymentLinkExpiryAlert(
                        expiry = expiry,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // URL display
                CopyableTextField(
                    value = url,
                    label = localization.copyLink,
                    modifier = Modifier.fillMaxWidth(),
                    onCopySuccess = {
                        analyticsTracker.trackEvent(
                            PaymentLinkAnalyticsEvent.LinkCopied(linkId = linkId)
                        )
                    }
                )

                Spacer(Modifier.height(24.dp))

                // Action buttons
                EnhancedPaymentLinkActions(
                    url = url,
                    linkId = linkId,
                    analyticsTracker = analyticsTracker,
                    onOpen = onOpen,
                    onDismiss = onDismiss,
                    localization = localization,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Enhanced action buttons with analytics tracking.
 */
@Composable
fun EnhancedPaymentLinkActions(
    url: String,
    linkId: String,
    analyticsTracker: PaymentLinkAnalyticsTracker,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    localization: com.example.myapplication.ui.localization.PaymentLinkLocalization,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
    val textColor = if (isDarkTheme) Color.White else FastPayNavy
    val secondaryTextColor = if (isDarkTheme) Color.LightGray else Color.Gray

    Column(modifier = modifier) {
        // Share button
        Button(
            onClick = {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "Pay me using this link: $url")
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, localization.shareLink)
                context.startActivity(shareIntent)

                // Track share event
                analyticsTracker.trackEvent(
                    PaymentLinkAnalyticsEvent.LinkShared(
                        linkId = linkId,
                        shareMethod = "system"
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FastPayNavy)
        ) {
            Icon(Icons.Rounded.Share, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(localization.shareLink, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Action row for Close and Open
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (isDarkTheme) Color.DarkGray else Color.LightGray)
            ) {
                Text(localization.closeLink, color = secondaryTextColor, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = {
                    analyticsTracker.trackEvent(
                        PaymentLinkAnalyticsEvent.LinkOpened(linkId = linkId)
                    )
                    onOpen()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (isDarkTheme) Color.DarkGray else Color.LightGray)
            ) {
                Icon(Icons.Rounded.OpenInBrowser, null, tint = textColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(localization.openLink, color = textColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}



