package com.example.myapplication.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.BuildConfig
import com.example.myapplication.ui.components.DrPayMarkBadge
import com.example.myapplication.ui.theme.FastPayBlue
import com.example.myapplication.ui.theme.FastPayBlack
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.math.BigDecimal

private val collectionCurrencies = listOf("PHP", "KRW", "USD", "EUR", "CNY")
private val codeLanguages = listOf("Node.js", "PHP", "cURL")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiDocsScreen() {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
    val scope = rememberCoroutineScope()
    val baseUrl = BuildConfig.PAYMENT_BACKEND_URL.trimEnd('/')
    var language by remember { mutableStateOf(codeLanguages.first()) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var amountInput by remember { mutableStateOf("100.00") }
    var currency by remember { mutableStateOf("PHP") }
    var referenceInput by remember { mutableStateOf("ORDER-1001") }
    var copied by remember { mutableStateOf(false) }
    val amount = amountInput.trim().toBigDecimalOrNull()
    val validAmount = amount != null && amount >= BigDecimal("0.01") && amount <= BigDecimal("1000000")
    val validReference = referenceInput.trim().isNotEmpty() && referenceInput.trim().length <= 50
    val amountLiteral = amount?.stripTrailingZeros()?.toPlainString() ?: "100"
    val generatedCode = if (validAmount && validReference && baseUrl.startsWith("https://")) {
        collectionExample(language, baseUrl, amountLiteral, currency, referenceInput.trim())
    } else {
        null
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DrPayMarkBadge(size = 28.dp)
                        Text("Collection API", fontWeight = FontWeight.Black)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Integrate QR Ph collections into your web store. Create a payment from your server, show the returned QR to your customer, then check payment status.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DocPanel(title = "Base URL and authentication", icon = Icons.Rounded.Security) {
                Text("Production base URL", fontWeight = FontWeight.SemiBold)
                CodeBlock(baseUrl)
                Text(
                    "Send Authorization: Bearer <API_KEY> on each request. Generate a collection key from Profile → Payment Service. The full key is shown only once.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Keep the key in your web server's secret manager or environment. Never put it in browser JavaScript, a mobile app, a URL, or source control. These keys can create QR Ph collections, request FX quotes, read payment status, and read payment institutions only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CodeBlock("Authorization: Bearer drpay_live_<key-id>_<secret>")
            }

            DocPanel(title = "Create a QR Ph collection", icon = Icons.Rounded.Description) {
                Endpoint("POST", "/v1/payments/qrph")
                Text("PHP collection request:", fontWeight = FontWeight.SemiBold)
                CodeBlock(
                    """{
  "amount": 100.00,
  "referenceNo": "ORDER-1001",
  "currency": "PHP",
  "rejectDuplicatedPayments": true
}"""
                )
                Text("A successful response is HTTP 201 and includes paymentId, referenceNo, status, amount, currency, and qrCode. qrImageBase64 may also be present. Display the QR to the customer and keep paymentId with your order.")
                Text("Use a unique referenceNo per order (1–50 characters). Do not treat QR creation or a PENDING response as proof of payment.")
            }

            DocPanel(title = "Foreign-currency QR Ph quote", icon = Icons.Rounded.Description) {
                Text("For KRW, USD, EUR, or CNY, first request a short-lived quote:", fontWeight = FontWeight.SemiBold)
                Endpoint("POST", "/v1/payments/qrph/quote")
                CodeBlock(
                    """{
  "amount": 250,
  "currency": "USD"
}"""
                )
                Text("Then create the QR using only the returned quoteToken:")
                Endpoint("POST", "/v1/payments/qrph")
                CodeBlock(
                    """{
  "quoteToken": "<quoteToken from quote response>",
  "rejectDuplicatedPayments": true
}"""
                )
                Text("The quote response includes customerAmount, customerCurrency, settlementAmountPhp, ratePhpPerUnit, rateUpdatedAt, and expiresAt. The QR settles in PHP. Quote tokens are short-lived and bound to the merchant; request a new quote after expiry.")
                Text("For quoted foreign-currency orders the backend generates the payment reference from the quote; the caller cannot supply referenceNo on the quote request.")
            }

            DocPanel(title = "Check payment status", icon = Icons.Rounded.Description) {
                Endpoint("GET", "/v1/payments/{paymentId}/status")
                CodeBlock(
                    """curl -H "Authorization: Bearer ${'$'}DRPAY_API_KEY" \
  "$baseUrl/v1/payments/PAYMENT_ID/status""""
                )
                Text("Poll from your server using the paymentId returned by QR creation. Treat only a confirmed terminal success as paid; keep pending orders pending. Apply backoff and do not create another QR when a request times out until you reconcile the original request.")
            }

            DocPanel(title = "Webhook status", icon = Icons.Rounded.Security) {
                Text(
                    "Merchant-configurable outbound webhooks are not available yet. The backend's POST /v1/webhooks/swiftpay endpoint is for authenticated provider callbacks; do not call it from your store. For now, poll the payment-status endpoint from your server.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "The app credits merchant wallet funds only after it receives and verifies a signed SwiftPay callback that matches the payment, reference, PHP amount, and currency. A browser redirect or QR scan is not settlement confirmation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DocPanel(title = "Errors and safe retries", icon = Icons.Rounded.Security) {
                Text("400 invalid request or expired quote · 401 missing, invalid, or revoked key · 403 key is outside the collection API · 429 rate limit · 5xx provider/backend issue.")
                Text("Do not automatically retry QR creation after a network timeout. First reconcile the order and payment reference/status to avoid creating duplicate customer payments.")
            }

            DocPanel(title = "Integration code generator", icon = Icons.Rounded.Code) {
                Text("Generate a server-side starting point for the QR Ph collection flow.")
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("Customer amount") },
                    supportingText = {
                        if (!validAmount) Text("Enter an amount from 0.01 to 1,000,000.")
                    },
                    isError = !validAmount,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Customer currency", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    collectionCurrencies.forEach { option ->
                        FilterChip(
                            selected = currency == option,
                            onClick = { currency = option },
                            label = { Text(option) }
                        )
                    }
                }
                OutlinedTextField(
                    value = referenceInput,
                    onValueChange = { referenceInput = it },
                    label = { Text("Order reference") },
                    supportingText = {
                        if (!validReference) Text("Reference is required and may contain at most 50 characters.")
                    },
                    isError = !validReference,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(onClick = { languageMenuExpanded = true }) {
                        Text(language)
                    }
                    DropdownMenu(
                        expanded = languageMenuExpanded,
                        onDismissRequest = { languageMenuExpanded = false }
                    ) {
                        codeLanguages.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    language = option
                                    languageMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                if (generatedCode == null) {
                    Text(
                        "Configure an HTTPS backend URL and enter valid amount and reference values to generate code.",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    CodeBlock(generatedCode)
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setPrimaryClip(ClipData.newPlainText("DRPAY collection integration", generatedCode))
                            copied = true
                            scope.launch {
                                kotlinx.coroutines.delay(1800)
                                copied = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.ContentCopy, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (copied) "Copied" else "Copy generated code")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DocPanel(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(icon, null, tint = FastPayBlue)
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                content()
            }
        )
    }
}

@Composable
private fun Endpoint(method: String, path: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = FastPayBlue.copy(alpha = 0.1f),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(method, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = FastPayBlue, fontWeight = FontWeight.Bold)
        }
        Text(path, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CodeBlock(code: String) {
    Surface(
        color = if (MaterialTheme.colorScheme.background == FastPayBlack) Color(0xFF17243A) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            code,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun collectionExample(
    language: String,
    baseUrl: String,
    amount: String,
    currency: String,
    reference: String
): String {
    val amountNumber = amount.toBigDecimalOrNull() ?: BigDecimal("100")
    val amountJson = amountNumber.stripTrailingZeros().toPlainString()
    val baseJson = JSONObject.quote(baseUrl)
    val referenceJson = JSONObject.quote(reference)
    val currencyJson = JSONObject.quote(currency)
    val createBody = if (currency == "PHP") {
        """{ amount: $amountJson, referenceNo: $referenceJson, currency: "PHP", rejectDuplicatedPayments: true }"""
    } else {
        """{ quoteToken: quote.quoteToken, rejectDuplicatedPayments: true }"""
    }
    val quoteStep = if (currency == "PHP") "" else """
  const quote = await request("/v1/payments/qrph/quote", "POST", {
    amount: $amountJson,
    currency: $currencyJson
  });
"""
    return when (language) {
        "Node.js" -> """
import process from "node:process";

const baseUrl = $baseJson;
const apiKey = process.env.DRPAY_API_KEY;
if (!apiKey) throw new Error("Set DRPAY_API_KEY in your server environment");

async function request(path, method = "POST", body) {
  const response = await fetch(`${'$'}{baseUrl}${'$'}{path}`, {
    method,
    headers: {
      Authorization: "Bearer " + apiKey,
      ...(body ? { "Content-Type": "application/json" } : {})
    },
    ...(body ? { body: JSON.stringify(body) } : {})
  });
  const data = await response.json();
  if (!response.ok) throw new Error(`DRPAY ${'$'}{response.status}: ${'$'}{JSON.stringify(data)}`);
  return data;
}

async function createCollection() {
$quoteStep  const payment = await request("/v1/payments/qrph", "POST", $createBody);
  console.log("Show this QR to your customer:", payment.qrCode);
  console.log("Save payment id and order reference:", payment.paymentId, payment.referenceNo);
  return payment;
}

async function getPaymentStatus(paymentId) {
  return request(`/v1/payments/${'$'}{encodeURIComponent(paymentId)}/status`, "GET");
}

// Call createCollection() from your server-side order flow.
""".trimIndent()
        "PHP" -> phpCollectionExample(
            baseJson = baseJson,
            amountJson = amountJson,
            currencyJson = currencyJson,
            reference = reference,
            currency = currency
        )
        else -> curlCollectionExample(
            baseUrl = baseUrl,
            amountJson = amountJson,
            currencyJson = currencyJson,
            reference = reference,
            currency = currency
        )
    }
}

private fun phpCollectionExample(
    baseJson: String,
    amountJson: String,
    currencyJson: String,
    reference: String,
    currency: String
): String {
    val payload = if (currency == "PHP") {
        """['amount' => $amountJson, 'referenceNo' => ${phpQuote(reference)}, 'currency' => 'PHP', 'rejectDuplicatedPayments' => true]"""
    } else {
        """['quoteToken' => ${'$'}quote['quoteToken'], 'rejectDuplicatedPayments' => true]"""
    }
    val quoteStep = if (currency == "PHP") "" else """
${'$'}quote = drpayRequest('POST', '/v1/payments/qrph/quote', [
    'amount' => $amountJson,
    'currency' => $currencyJson,
]);
"""
    return """
<?php
${'$'}baseUrl = $baseJson;
${'$'}apiKey = getenv('DRPAY_API_KEY');
if (!${'$'}apiKey) throw new RuntimeException('Set DRPAY_API_KEY in the server environment');

function drpayRequest(${ '$' }method, ${'$'}path, ${'$'}body = null) {
    global ${'$'}baseUrl, ${'$'}apiKey;
    ${'$'}headers = ['Authorization: Bearer ' . ${'$'}apiKey, 'Accept: application/json'];
    ${'$'}options = ['http' => ['method' => ${'$'}method, 'header' => implode("\\r\\n", ${'$'}headers), 'ignore_errors' => true]];
    if (${ '$' }body !== null) {
        ${'$'}headers[] = 'Content-Type: application/json';
        ${'$'}options['http']['header'] = implode("\\r\\n", ${'$'}headers);
        ${'$'}options['http']['content'] = json_encode(${ '$' }body, JSON_THROW_ON_ERROR);
    }
    ${'$'}raw = file_get_contents(rtrim(${ '$' }baseUrl, '/') . ${'$'}path, false, stream_context_create(${ '$' }options));
    ${'$'}status = ${ '$' }http_response_header[0] ?? 'HTTP/1.1 500';
    ${'$'}data = json_decode(${ '$' }raw ?: '', true);
    if (strpos(${ '$' }status, ' 2') === false) throw new RuntimeException('DRPAY request failed: ' . ${ '$' }raw);
    return ${ '$' }data;
}

$quoteStep${'$'}payment = drpayRequest('POST', '/v1/payments/qrph', $payload);
echo "Show this QR to your customer: " . ${'$'}payment['qrCode'] . PHP_EOL;
echo "Save payment id: " . ${'$'}payment['paymentId'] . PHP_EOL;

// Later, check status with:
// drpayRequest('GET', '/v1/payments/' . rawurlencode(${ '$' }payment['paymentId']) . '/status');
""".trimIndent()
}

private fun curlCollectionExample(
    baseUrl: String,
    amountJson: String,
    currencyJson: String,
    reference: String,
    currency: String
): String {
    val createBody = if (currency == "PHP") {
        JSONObject()
            .put("amount", amountJson.toBigDecimal())
            .put("referenceNo", reference)
            .put("currency", "PHP")
            .put("rejectDuplicatedPayments", true)
            .toString()
    } else {
        """{"quoteToken":"${'$'}QUOTE_TOKEN","rejectDuplicatedPayments":true}"""
    }
    val quote = if (currency == "PHP") "" else """
QUOTE_TOKEN=${'$'}(curl --fail-with-body -sS "$baseUrl/v1/payments/qrph/quote" \
  -H "Authorization: Bearer ${'$'}DRPAY_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"amount":${amountJson},"currency":$currencyJson}' | jq -r '.quoteToken')
"""
    return """
export DRPAY_API_KEY='YOUR_SERVER_API_KEY'
${quote.trimEnd()}
curl --fail-with-body -sS "$baseUrl/v1/payments/qrph" \
  -H "Authorization: Bearer ${'$'}DRPAY_API_KEY" \
  -H "Content-Type: application/json" \
  -d '${shellQuote(createBody)}'

# Check later using the paymentId returned above:
curl --fail-with-body -sS "$baseUrl/v1/payments/PAYMENT_ID/status" \
  -H "Authorization: Bearer ${'$'}DRPAY_API_KEY"
""".trimIndent()
}

private fun shellQuote(value: String): String = value.replace("'", "'\\''")

private fun phpQuote(value: String): String =
    "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "\\r").replace("\n", "\\n") + "'"
