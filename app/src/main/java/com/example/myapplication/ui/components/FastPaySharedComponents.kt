package com.example.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.*

@Composable
fun FastPayLogo(
    modifier: Modifier = Modifier,
    isDark: Boolean = false,
    scale: Float = 1f
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size((42 * scale).dp)
                    .offset(y = (2 * scale).dp)
                    .background(
                        color = if (isDark) Color.Black.copy(alpha = 0.4f) else FastPayBlue.copy(alpha = 0.2f),
                        shape = RoundedCornerShape((14 * scale).dp)
                    )
            )
            
            Box(
                modifier = Modifier
                    .size((40 * scale).dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(FastPayAccent, FastPayBlue),
                            start = androidx.compose.ui.geometry.Offset.Zero,
                            end = androidx.compose.ui.geometry.Offset.Infinite
                        ),
                        shape = RoundedCornerShape((12 * scale).dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size((36 * scale).dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(FastPayBlue, FastPayNavy)
                            ),
                            shape = RoundedCornerShape((10 * scale).dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ElectricBolt,
                        contentDescription = null,
                        modifier = Modifier.size((22 * scale).dp),
                        tint = Color.White
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width((12 * scale).dp))
        
        Column(verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "FAST",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = (26 * scale).sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp
                    ),
                    color = if (isDark) Color.White else FastPayNavy
                )
                Text(
                    text = "PAY",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = (26 * scale).sp,
                        fontWeight = FontWeight.ExtraLight,
                        letterSpacing = (-1).sp
                    ),
                    color = if (isDark) Color.White.copy(alpha = 0.9f) else FastPayBlue,
                    modifier = Modifier.offset(x = (-1 * scale).dp)
                )
            }
            
            Surface(
                color = if (isDark) Color.White.copy(alpha = 0.15f) else FastPayNavy,
                shape = RoundedCornerShape((2 * scale).dp),
                modifier = Modifier.offset(y = (-2 * scale).dp)
            ) {
                Text(
                    text = "ENTERPRISE",
                    modifier = Modifier.padding(horizontal = (6 * scale).dp, vertical = (2 * scale).dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = (8 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun FastPayInfoCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, FastPayBlue.copy(alpha = 0.1f)),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            if (icon != null) {
                icon()
            }
        }
    }
}

@Composable
fun FastPayStatusBadge(
    status: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    textColor: Color? = null
) {
    val defaultBg = FastPayBlue.copy(alpha = 0.1f)
    val defaultText = FastPayBlue

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor ?: defaultBg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = textColor ?: defaultText,
                modifier = Modifier.size(12.dp)
            )
            Text(
                status,
                style = MaterialTheme.typography.labelSmall,
                color = textColor ?: defaultText,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
