package com.example.myapplication.ui.components

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.bridge.PaymentData
import com.example.myapplication.bridge.SwiftPaySDKBridge
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SwiftPayWebView(
    url: String,
    modifier: Modifier = Modifier,
    onPaymentRequest: (PaymentData) -> Unit,
    onBalanceRequest: () -> Unit,
    onTransactionsRequest: () -> Unit,
    onPaymentChannelsRequest: () -> Unit,
    onPaymentLinkRequest: (PaymentData) -> Unit,
    onSaveSettings: (Map<String, String>) -> Unit,
    onGetSettings: () -> Unit,
    onScanNFCCard: (Double) -> Unit,
    onGenerateDynamicQr: (Double) -> Unit,
    onStartDepositWizard: (Boolean) -> Unit,
    onScanQrToSend: () -> Unit,
    onGetTransferInstitutions: () -> Unit,
    onSendDisbursement: ((Double, String, String, String) -> Unit)? = null,
    onGenerateDynamicQrWithCurrency: ((Double, String, String?) -> Unit)? = null,
    onQuoteKrwPhpQr: ((Int) -> Unit)? = null,
    onGenerateQuotedKrwPhpQr: ((String, String?) -> Unit)? = null,
    onQuoteFxPhpQr: ((Double, String) -> Unit)? = null,
    onGenerateQuotedFxPhpQr: ((String, String?) -> Unit)? = null,
    onNavigateHome: (() -> Unit)? = null,
    onBridgeReady: (SwiftPaySDKBridge) -> Unit
) {
    val scope = rememberCoroutineScope()
    var webView: WebView? by remember { mutableStateOf(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    var pageError: String? by remember { mutableStateOf(null) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    val currentLoading = rememberUpdatedState(isLoading)
    val currentPageError = rememberUpdatedState(pageError)

    LaunchedEffect(url, loadAttempt) {
        isLoading = true
        pageError = null
        delay(15_000)
        if (currentLoading.value && currentPageError.value == null) {
            isLoading = false
            pageError = "The screen did not finish loading. Check your connection and retry."
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val currentWebView = rememberUpdatedState(webView)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> currentWebView.value?.onPause()
                Lifecycle.Event.ON_RESUME -> currentWebView.value?.onResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            currentWebView.value?.destroy()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val uri = request?.url ?: return true
                                if (isTrustedWebViewAssetUrl(uri.toString())) return false
                                if (request.isForMainFrame != true) return true

                                val scheme = uri.scheme?.lowercase()
                                val isPaymentCallback = scheme in setOf("swiftpay", "swiftpay-showcase") &&
                                    uri.host.equals("payment", ignoreCase = true) &&
                                    uri.path in setOf("/success", "/failure", "/cancel")
                                val isExternalWebLink = scheme in setOf("http", "https") &&
                                    !uri.host.isNullOrBlank() && uri.userInfo == null
                                if (!isPaymentCallback && !isExternalWebLink) return true

                                return try {
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    if (isPaymentCallback) intent.setPackage(context.packageName)
                                    context.startActivity(intent)
                                    true
                                } catch (_: ActivityNotFoundException) {
                                    pageError = "No app is available to open this link."
                                    true
                                }
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                pageError = null
                                progress = 0f
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                canGoBack = view?.canGoBack() == true
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    pageError = error?.description?.toString() ?: "Network connection error"
                                    isLoading = false
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: WebResourceResponse?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    pageError = "HTTP Error ${errorResponse?.statusCode}: ${errorResponse?.reasonPhrase}"
                                    isLoading = false
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                consoleMessage?.let {
                                            Log.e(
                                        "SwiftPayWebView",
                                        "${it.message()} (${it.sourceId()}:${it.lineNumber()})"
                                    )
                                }
                                return true
                            }

                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress / 100f
                                if (newProgress == 100) isLoading = false
                            }
                        }

                        val bridge = SwiftPaySDKBridge(
                            webView = this,
                            scope = scope,
                            onPaymentRequest = onPaymentRequest,
                            onBalanceRequest = onBalanceRequest,
                            onTransactionsRequest = onTransactionsRequest,
                            onPaymentChannelsRequest = onPaymentChannelsRequest,
                            onPaymentLinkRequest = onPaymentLinkRequest,
                            onSaveSettings = onSaveSettings,
                            onGetSettings = onGetSettings,
                            onScanNFCCard = onScanNFCCard,
                            onGenerateDynamicQr = onGenerateDynamicQr,
                            onStartDepositWizard = onStartDepositWizard,
                            onScanQrToSend = onScanQrToSend,
                            onGetTransferInstitutions = onGetTransferInstitutions,
                            onSendDisbursement = onSendDisbursement,
                            onGenerateDynamicQrWithCurrency = onGenerateDynamicQrWithCurrency,
                            onQuoteKrwPhpQr = onQuoteKrwPhpQr,
                            onGenerateQuotedKrwPhpQr = onGenerateQuotedKrwPhpQr,
                            onQuoteFxPhpQr = onQuoteFxPhpQr,
                            onGenerateQuotedFxPhpQr = onGenerateQuotedFxPhpQr,
                            onNavigateHome = onNavigateHome
                        )

                        addJavascriptInterface(bridge, "SwiftPaySDK")
                        addJavascriptInterface(bridge, "MayaSDK")
                        onBridgeReady(bridge)

                        loadUrl(url)
                        webView = this
                    }
            },
            update = { view ->
                if (view.url != url) {
                    view.loadUrl(url)
                }
            }
        )

        pageError?.let { error ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text(
                    text = "This screen could not be opened",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = error,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = {
                        pageError = null
                        isLoading = true
                        loadAttempt++
                        webView?.reload()
                    }
                ) {
                    Text("Retry")
                }
            }
        }

        AnimatedVisibility(
            visible = isLoading && pageError == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
