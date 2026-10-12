package com.example.myapplication.ui.components

import android.content.ClipData
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.myapplication.ui.theme.FastPayBlack
import com.example.myapplication.ui.theme.FastPayNavy
import com.example.myapplication.ui.theme.FastPayGreen
import kotlinx.coroutines.delay

/**
 * Enhanced copy button with animated feedback.
 * Shows success animation after copying to clipboard.
 */
@Composable
fun CopyButtonWithFeedback(
    text: String,
    onCopySuccess: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }
    val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
    val textColor = if (isDarkTheme) Color.White else FastPayNavy

    // Animation for icon
    val scale by animateFloatAsState(
        targetValue = if (isCopied) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "copyButtonScale"
    )

    val iconAlpha by animateFloatAsState(
        targetValue = if (isCopied) 0f else 1f,
        animationSpec = tween(durationMillis = 500),
        label = "copyIconAlpha"
    )

    // Auto-reset copied state
    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(1500)
            isCopied = false
        }
    }

    Button(
        onClick = {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            clipboardManager.setPrimaryClip(ClipData.newPlainText("Payment Link", text))
            isCopied = true
            onCopySuccess?.invoke()
            if (!isCopied) {
                Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
            }
        },
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isCopied) FastPayGreen else FastPayNavy
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Copy icon (visible when not copied)
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        scaleX = scale * (1 - iconAlpha)
                        scaleY = scale * (1 - iconAlpha)
                        this.alpha = iconAlpha
                    }
            )

            // Check icon (visible when copied)
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        scaleX = scale * iconAlpha
                        scaleY = scale * iconAlpha
                        this.alpha = iconAlpha
                    }
            )
        }

        if (showLabel) {
            Spacer(Modifier.width(8.dp))
            Text(
                if (isCopied) "Copied!" else "Copy Link",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Enhanced text field with copy functionality and theme awareness.
 */
@Composable
fun CopyableTextField(
    value: String,
    label: String = "Payment Link",
    modifier: Modifier = Modifier,
    onCopySuccess: (() -> Unit)? = null
) {
    val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
    val bgColor = if (isDarkTheme) Color.LightGray.copy(alpha = 0.1f) else FastPayNavy.copy(alpha = 0.05f)
    val textColor = if (isDarkTheme) Color.White else FastPayNavy

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Text content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isDarkTheme) Color.LightGray else Color.Gray,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                fontSize = 12.sp,
                maxLines = 2,
                lineHeight = 14.sp
            )
        }

        // Copy button
        CopyButtonWithFeedback(
            text = value,
            onCopySuccess = onCopySuccess,
            showLabel = false,
            modifier = Modifier.size(44.dp)
        )
    }
}

/**
 * Toast notification with custom styling.
 */
@Composable
fun CopySuccessToast(message: String = "Link copied!") {
    val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(
                if (isDarkTheme) FastPayGreen.copy(alpha = 0.9f) else FastPayGreen,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                message,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
    }
}


