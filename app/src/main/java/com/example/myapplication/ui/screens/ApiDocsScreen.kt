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
import com.example.myapplication.ui.theme.FastPayBlue
import com.example.myapplication.ui.theme.FastPayBlack
import com.example.myapplication.ui.theme.FastPayBackground
import com.example.myapplication.ui.theme.FastPayTextSecondary
import com.example.myapplication.ui.components.DrPayMarkBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiDocsScreen() {
    val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
    val bgColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    val cardColor = MaterialTheme.colorScheme.surface

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DrPayMarkBadge(size = 28.dp)
                        Text("Developer portal", color = textColor, fontWeight = FontWeight.Black)
                    }
                },
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                content = "Create a PHP order with a unique reference. SwiftPay hosts the enabled payment methods, including available banks and e-wallets; the app can embed this secure checkout for wallet deposits. Card details stay on SwiftPay's page.",
                code = "POST /v1/payments/orders\n{\n  \"amount\": 100.00,\n  \"referenceNo\": \"INV-1001\",\n  \"currency\": \"PHP\",\n  \"details\": {}\n}",
                cardColor = cardColor,
                textColor = textColor
            )

            DocSection(
                title = "4. Payment links",
                icon = Icons.Rounded.Description,
                content = "Create a fixed-amount DRPAY checkout page in PHP, USD, or EUR and share its public URL. Alipay, Naver Pay, Kakao Pay, and Toss Bank shortcuts use the same QR Ph flow; customers must use an app that supports QR Ph. Foreign-currency QR prices settle in PHP.",
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
                content = "QR Ph settles in PHP. KRW, USD, and EUR customer prices use a short-lived signed FX quote to lock the PHP amount. Configure the SwiftPay callback URL in the Merchant Portal; verified terminal callbacks take precedence over polling.",
                code = "POST /v1/payments/qrph/quote\nPOST /v1/payments/qrph\nPOST /v1/webhooks/swiftpay",
                cardColor = cardColor,
                textColor = textColor
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = FastPayBlue),
                shape = RoundedCornerShape(22.dp),
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
        Text(subtitle, fontSize = 14.sp, color = FastPayTextSecondary, lineHeight = 22.sp)
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
    val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = FastPayBlue, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = textColor)
            }
            Spacer(Modifier.height(12.dp))
            Text(content, fontSize = 13.sp, color = FastPayTextSecondary, lineHeight = 20.sp)
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDarkTheme) Color(0xFF17243A) else FastPayBackground, RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = code,
                    color = if (isDarkTheme) Color(0xFFB9D5FA) else FastPayBlue,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
