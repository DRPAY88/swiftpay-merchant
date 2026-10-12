package com.example.myapplication.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.api.MerchantProfileResponse
import com.example.myapplication.data.api.VaultPaymentResponse
import com.example.myapplication.ui.theme.*

/**
 * Merchant Profile Card Component
 * Displays merchant information from Maya API
 */
@Composable
fun MerchantProfileCard(
    profile: MerchantProfileResponse?,
    isLoading: Boolean = false,
    error: String? = null,
    modifier: Modifier = Modifier
) {
    if (error != null) {
        // Error State
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = "Error",
                    tint = Color(0xFFC62828),
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC62828),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        return
    }

    if (isLoading || profile == null) {
        // Loading State
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = FastPayNavy)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Skeleton loader animation
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .fillMaxWidth(0.6f)
                        .clip(RoundedCornerShape(8.dp))
                        .shimmerEffect()
                )
                Box(
                    modifier = Modifier
                        .height(16.dp)
                        .fillMaxWidth(0.8f)
                        .clip(RoundedCornerShape(6.dp))
                        .shimmerEffect()
                )
                Box(
                    modifier = Modifier
                        .height(16.dp)
                        .fillMaxWidth(0.7f)
                        .clip(RoundedCornerShape(6.dp))
                        .shimmerEffect()
                )
            }
        }
        return
    }

    // Loaded State
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = FastPayNavy),
        border = androidx.compose.foundation.BorderStroke(1.dp, FastPayBlue.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with Business Name
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Merchant Profile",
                        style = MaterialTheme.typography.labelSmall,
                        color = FastPayBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = profile.businessName ?: "${profile.firstName} ${profile.lastName}".trim(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when (profile.kycStatus?.uppercase()) {
                        "APPROVED" -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                        "PENDING" -> Color(0xFFFF9800).copy(alpha = 0.2f)
                        else -> Color(0xFF2196F3).copy(alpha = 0.2f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when (profile.kycStatus?.uppercase()) {
                                "APPROVED" -> Icons.Rounded.CheckCircle
                                "PENDING" -> Icons.Rounded.Schedule
                                else -> Icons.Rounded.Info
                            },
                            contentDescription = "Status",
                            tint = when (profile.kycStatus?.uppercase()) {
                                "APPROVED" -> Color(0xFF4CAF50)
                                "PENDING" -> Color(0xFFFF9800)
                                else -> Color(0xFF2196F3)
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = profile.kycStatus ?: "Unknown",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = when (profile.kycStatus?.uppercase()) {
                                "APPROVED" -> Color(0xFF4CAF50)
                                "PENDING" -> Color(0xFFFF9800)
                                else -> Color(0xFF2196F3)
                            }
                        )
                    }
                }
            }

            // Info Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MerchantInfoBox(
                    icon = Icons.Rounded.Business,
                    label = "Account Status",
                    value = profile.accountStatus ?: "N/A",
                    modifier = Modifier.weight(1f)
                )
                MerchantInfoBox(
                    icon = Icons.Rounded.AttachMoney,
                    label = "Monthly Volume",
                    value = "₱${String.format("%,.0f", profile.monthlyVolume ?: 0.0)}",
                    modifier = Modifier.weight(1f)
                )
            }

            // Contact Information
            if (!profile.email.isNullOrEmpty() || !profile.phone.isNullOrEmpty()) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth())

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!profile.email.isNullOrEmpty()) {
                        ContactInfoRow(
                            icon = Icons.Rounded.Email,
                            label = "Email",
                            value = profile.email
                        )
                    }
                    if (!profile.phone.isNullOrEmpty()) {
                        ContactInfoRow(
                            icon = Icons.Rounded.Phone,
                            label = "Phone",
                            value = profile.phone
                        )
                    }
                }
            }

            // Master MID Information
            if (!profile.masterMid.isNullOrEmpty()) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth())

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Key,
                        contentDescription = "MID",
                        tint = FastPayBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "Merchant ID",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = profile.masterMid,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            fontFamily = androidx.compose.material3.LocalTextStyle.current.fontFamily,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Info box for merchant profile card
 */
@Composable
private fun MerchantInfoBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = FastPayBlue,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Contact info row component
 */
@Composable
private fun ContactInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = FastPayBlue.copy(alpha = 0.2f),
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = FastPayBlue,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxSize()
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Transaction Status Card Component
 * Displays status of a single transaction
 */
@Composable
fun TransactionStatusCard(
    transactionId: String,
    payment: VaultPaymentResponse?,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (isLoading || payment == null) {
        // Loading state
        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(8.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shimmerEffect()
            )
        }
        return
    }

    val statusColor = when (payment.status?.uppercase()) {
        "SUCCESS" -> Color(0xFF4CAF50)
        "PENDING" -> Color(0xFFFF9800)
        "FAILED" -> Color(0xFFF44336)
        "CANCELLED" -> Color(0xFF9E9E9E)
        else -> FastPayBlue
    }

    val statusIcon = when (payment.status?.uppercase()) {
        "SUCCESS" -> Icons.Rounded.CheckCircle
        "PENDING" -> Icons.Rounded.Schedule
        "FAILED" -> Icons.Rounded.Error
        "CANCELLED" -> Icons.Rounded.Cancel
        else -> Icons.Rounded.Info
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = statusColor.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    statusIcon,
                    contentDescription = payment.status,
                    tint = statusColor,
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxSize()
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = payment.status ?: "Unknown",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Text(
                    text = "ID: ${transactionId.take(16)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                if (!payment.currency.isNullOrEmpty()) {
                    Text(
                        text = "₱${payment.amount ?: "0.00"} ${payment.currency}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = FastPayNavy
                    )
                }
            }

            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = "Details",
                tint = Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Compact Transaction Status Indicator
 * For inline display in transaction lists
 */
@Composable
fun TransactionStatusIndicator(
    status: String?,
    modifier: Modifier = Modifier
) {
    val (color, icon) = when (status?.uppercase()) {
        "SUCCESS" -> Color(0xFF4CAF50) to Icons.Rounded.CheckCircle
        "PENDING" -> Color(0xFFFF9800) to Icons.Rounded.Schedule
        "FAILED" -> Color(0xFFF44336) to Icons.Rounded.Close
        "CANCELLED" -> Color(0xFF9E9E9E) to Icons.Rounded.Cancel
        else -> FastPayBlue to Icons.Rounded.Info
    }

    Surface(
        modifier = modifier.size(32.dp),
        shape = CircleShape,
        color = color.copy(alpha = 0.15f)
    ) {
        Icon(
            icon,
            contentDescription = status,
            tint = color,
            modifier = Modifier
                .padding(6.dp)
                .fillMaxSize()
        )
    }
}







