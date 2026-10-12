package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.FastPayBlack
import com.example.myapplication.ui.theme.FastPayBlue
import com.example.myapplication.ui.theme.FastPayNavy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiDocsScreen() {
    val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
    val bgColor = if (isDarkTheme) Color(0xFF121212) else Color(0xFFF8F9FA)
    val textColor = if (isDarkTheme) Color.White else FastPayNavy
    val cardColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SwiftPay Developer Portal", color = textColor, fontWeight = FontWeight.Black) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = bgColor)
            )
        },
        containerColor = bgColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            DocHeader(
                title = "Integration Overview",
                subtitle = "Build rapid payment solutions using SwiftPay corporate REST APIs.",
                textColor = textColor
            )

            DocSection(
                title = "1. Server-held credentials",
                icon = Icons.Rounded.Key,
                content = "SwiftPay access and secret keys stay in the payment backend environment. They are never stored in the Android app or exposed to the embedded mini-app.",
                code = "SWIFTPAY_ACCESS_KEY=server-only\nSWIFTPAY_SECRET_KEY=server-only",
                cardColor = cardColor,
                textColor = textColor
            )

            DocSection(
                title = "2. Merchant sign-in",
                icon = Icons.Rounded.Security,
                content = "Only allowlisted merchant emails can request a sign-in code. The verified backend session authorizes payment requests.",
                code = "POST /v1/auth/request-otp\nPOST /v1/auth/verify-otp",
                cardColor = cardColor,
                textColor = textColor
            )

            DocSection(
                title = "3. Hosted checkout",
                icon = Icons.Rounded.Description,
                content = "Create a PHP order with a unique reference. SwiftPay hosts payment methods including card entry and 3-D Secure; card details stay on SwiftPay's page.",
                code = "POST /v1/payments/orders\n{\n  \"amount\": 100.00,\n  \"referenceNo\": \"INV-1001\",\n  \"currency\": \"PHP\",\n  \"details\": {}\n}",
                cardColor = cardColor,
                textColor = textColor
            )

            DocSection(
                title = "4. Payment links",
                icon = Icons.Rounded.Description,
                content = "Create fixed-amount hosted payment links. The published SwiftPay API requires a positive PHP amount.",
                code = "POST /v1/payment-links\n{\n  \"amount\": 50.00,\n  \"currency\": \"PHP\",\n  \"description\": \"Invoice 1002\"\n}",
                cardColor = cardColor,
                textColor = textColor
            )

            DocSection(
                title = "5. Transfers and status",
                icon = Icons.Rounded.Description,
                content = "Transfers are accepted asynchronously. A successful submission is pending until SwiftPay reports a terminal status.",
                code = "POST /v1/disbursements\nGET /v1/disbursements/{id}\nGET /v1/payments/{paymentToken}/status",
                cardColor = cardColor,
                textColor = textColor
            )

            DocSection(
                title = "6. QR Ph and verified callbacks",
                icon = Icons.Rounded.Security,
                content = "Create QR Ph payments through the signed backend integration. Configure the SwiftPay callback URL in the Merchant Portal; verified terminal callbacks are stored and take precedence over status polling.",
                code = "POST /v1/payments/qrph\nPOST /v1/webhooks/swiftpay\nGET /v1/payments/{paymentToken}/status",
                cardColor = cardColor,
                textColor = textColor
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = FastPayBlue),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Need Technical Support?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Our engineering team is available 24/7. Contact support@swiftpay.ph for custom API integration assistance.",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }
            
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun DocHeader(title: String, subtitle: String, textColor: Color) {
    Column {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black, color = textColor)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, fontSize = 14.sp, color = Color.Gray, lineHeight = 22.sp)
    }
}

@Composable
fun DocSection(
    title: String,
    icon: ImageVector,
    content: String,
    code: String,
    cardColor: Color,
    textColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = FastPayBlue, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = textColor)
            }
            Spacer(Modifier.height(12.dp))
            Text(content, fontSize = 13.sp, color = Color.Gray, lineHeight = 20.sp)
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E2F), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = code,
                    color = Color(0xFFCE9178),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
