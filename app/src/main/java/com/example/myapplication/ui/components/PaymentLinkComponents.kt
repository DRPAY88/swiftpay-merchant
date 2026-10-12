package com.example.myapplication.ui.components

import android.content.ClipData
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.widget.Toast
import com.example.myapplication.ui.theme.FastPayBlack
import com.example.myapplication.ui.theme.FastPayNavy
import com.example.myapplication.ui.constants.ComponentDimensions

/**
 * Component for displaying the payment link URL with copy functionality.
 */
@Composable
fun PaymentLinkDisplayCard(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
    val textColor = if (isDarkTheme) Color.White else FastPayNavy

    Surface(
        shape = RoundedCornerShape(ComponentDimensions.CornerRadiusSmall),
        color = if (isDarkTheme) Color.LightGray.copy(alpha = 0.1f) else FastPayNavy.copy(alpha = 0.05f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = ComponentDimensions.MediumPadding,
                vertical = ComponentDimensions.SmallPadding
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = url,
                fontSize = 12.sp,
                color = textColor,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                lineHeight = 16.sp
            )
            IconButton(
                onClick = {
                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("Payment Link", url))
                    Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Rounded.ContentCopy, null, tint = textColor, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Component for payment link action buttons (Share, Open, Close).
 */
@Composable
fun PaymentLinkActions(
    url: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
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
                val sendIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, "Pay me using this link: $url")
                    type = "text/plain"
                }
                val shareIntent = android.content.Intent.createChooser(sendIntent, "Share Payment Link")
                context.startActivity(shareIntent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FastPayNavy)
        ) {
            Icon(Icons.Rounded.Share, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Share Link", fontWeight = FontWeight.Bold)
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
                Text("Close", color = secondaryTextColor, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = onOpen,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (isDarkTheme) Color.DarkGray else Color.LightGray)
            ) {
                Icon(Icons.Rounded.OpenInBrowser, null, tint = textColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open", color = textColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Main payment link dialog content component.
 */
@Composable
fun PaymentLinkContent(
    url: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
    val bgColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkTheme) Color.White else FastPayNavy
    val secondaryTextColor = if (isDarkTheme) Color.LightGray else Color.Gray

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = bgColor,
        tonalElevation = 12.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon header
            Surface(
                shape = CircleShape,
                color = Color(0xFF00C389).copy(alpha = 0.12f),
                modifier = Modifier.size(72.dp)
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
                "Payment Link Ready",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = textColor
            )

            Spacer(Modifier.height(8.dp))

            // Description
            Text(
                "Share this link with your customer to collect payment.",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            // URL display
            PaymentLinkDisplayCard(url)

            Spacer(Modifier.height(24.dp))

            // Action buttons
            PaymentLinkActions(
                url = url,
                onOpen = onOpen,
                onDismiss = onDismiss
            )
        }
    }
}

/**
 * Complete payment link dialog component.
 * Combines all payment link UI elements into a reusable dialog.
 */
@Composable
fun PaymentLinkDialog(
    url: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        PaymentLinkContent(
            url = url,
            onOpen = onOpen,
            onDismiss = onDismiss
        )
    }
}

