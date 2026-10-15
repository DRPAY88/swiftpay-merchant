package com.example.myapplication.ui.screens

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Payment
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.bridge.PaymentData
import com.example.myapplication.bridge.SwiftPaySDKBridge
import com.example.myapplication.data.SettingsManager
import com.example.myapplication.ui.components.*
import com.example.myapplication.ui.theme.FintechMotion

@Composable
fun MiniAppScreen(
    initialPath: String,
    openDepositWizard: Boolean = false,
    openQrScanner: Boolean = false,
    onNavigateHome: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: MiniAppViewModel = viewModel()
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val settings = remember { SettingsManager(context) }
    val themeMode by settings.themeMode.collectAsState(initial = "SYSTEM")
    val isDarkTheme = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }

    val webViewUrl = remember(initialPath, isDarkTheme) {
        val baseUrl = "file:///android_asset/index.html"
        val themeUrl = "$baseUrl?theme=${if (isDarkTheme) "dark" else "light"}"
        "$themeUrl#$initialPath"
    }

    val uiState = viewModel.uiState

    LaunchedEffect(initialPath, openDepositWizard, openQrScanner) {
        viewModel.refreshBalance()
        when {
            openDepositWizard -> viewModel.onStartDepositWizard()
            openQrScanner -> {
                viewModel.onScanQrToSend()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            SwiftPayWebView(
                url = webViewUrl,
                modifier = Modifier.fillMaxSize(),
                onPaymentRequest = { viewModel.requestPaymentConsent(it) },
                onBalanceRequest = { viewModel.onBalanceRequest() },
                onTransactionsRequest = { viewModel.onTransactionsRequest() },
                onPaymentChannelsRequest = { viewModel.onPaymentChannelsRequest() },
                onPaymentLinkRequest = { viewModel.onPaymentLinkRequest(it) },
                onSaveSettings = { viewModel.onSaveSettings(it) },
                onGetSettings = { viewModel.getSettings() },
                onScanNFCCard = { viewModel.onScanNFCCard(it) },
                onGenerateDynamicQr = { viewModel.onGenerateDynamicQr(it) },
                onGenerateDynamicQrWithCurrency = { amount, currency, paymentApp -> viewModel.onGenerateDynamicQr(amount, currency, paymentApp) },
                onQuoteKrwPhpQr = viewModel::onQuoteKrwPhpQr,
                onGenerateQuotedKrwPhpQr = { quoteToken, paymentApp ->
                    viewModel.onGenerateQuotedKrwPhpQr(quoteToken, paymentApp)
                },
                onQuoteFxPhpQr = viewModel::onQuoteFxPhpQr,
                onGenerateQuotedFxPhpQr = { quoteToken, paymentApp ->
                    viewModel.onGenerateQuotedFxPhpQr(quoteToken, paymentApp)
                },
                onNavigateHome = onNavigateHome,
                onStartDepositWizard = { showAccountNumber -> viewModel.onStartDepositWizard(showAccountNumber) },
                onScanQrToSend = { viewModel.onScanQrToSend() },
                onGetTransferInstitutions = viewModel::onGetTransferInstitutions,
                onSendDisbursement = { amount, bank, acc, name -> viewModel.onSendDisbursement(amount, bank, acc, name) },
                onBridgeReady = { viewModel.bridge = it }
            )

            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    (fadeIn(
                        animationSpec = tween(
                            FintechMotion.contentMillis,
                            easing = FintechMotion.standardEasing
                        )
                    ) + scaleIn(
                        initialScale = 0.97f,
                        animationSpec = tween(
                            FintechMotion.contentMillis,
                            easing = FintechMotion.standardEasing
                        )
                    )).togetherWith(
                        fadeOut(
                            animationSpec = tween(
                                FintechMotion.exitMillis,
                                easing = FintechMotion.standardEasing
                            )
                        )
                    )
                },
                label = "OverlayState"
            ) { state ->
                when (state) {
                    is MiniAppUiState.Processing -> {
                        SwiftPayProcessingDialog(message = state.message)
                    }
                    is MiniAppUiState.Error -> {
                        SwiftPayErrorDialog(message = state.message, onDismiss = { viewModel.dismissError() })
                    }
                    is MiniAppUiState.DynamicQrReady -> {
                        SwiftPayQrDialog(
                            qrImageBase64 = state.qrImageBase64,
                            amount = state.amount,
                            bankAccountNumber = state.bankAccountNumber,
                            instapayBankCode = state.instapayBankCode,
                            onDismiss = { viewModel.dismissConsent() },
                            currency = state.currency,
                            customerAmountKrw = state.customerAmountKrw,
                            ratePhpPerKrw = state.ratePhpPerKrw,
                            customerAmountFx = state.customerAmountFx,
                            customerCurrency = state.customerCurrency,
                            ratePhpPerUnit = state.ratePhpPerUnit,
                            rateUpdatedAt = state.rateUpdatedAt,
                            paymentAppName = state.paymentAppName
                        )
                    }
                    is MiniAppUiState.DepositWizard -> {
                        DepositWizardDialog(
                            accountNumber = state.accountNumber,
                            balance = state.balance,
                            showTransferInstructionsInitially = state.showTransferInstructionsInitially,
                            onCheckout = viewModel::startDepositCheckout,
                            onDismiss = { viewModel.dismissConsent() }
                        )
                    }
                    is MiniAppUiState.DepositCheckoutReady -> {
                        DepositCheckoutDialog(
                            url = state.url,
                            paymentId = state.paymentId,
                            referenceNo = state.referenceNo,
                            amount = state.amount,
                            onDismiss = { viewModel.closeDepositCheckout() }
                        )
                    }
                    is MiniAppUiState.DepositCheckoutComplete -> {
                        DepositCheckoutResultDialog(
                            paymentId = state.paymentId,
                            amount = state.amount,
                            success = state.success,
                            status = state.status,
                            onDismiss = {
                                if (state.success) viewModel.refreshBalance()
                                viewModel.dismissConsent()
                            }
                        )
                    }
                    is MiniAppUiState.ScanQrToSend -> {
                        CameraQrScannerDialog(
                            onQrDetected = viewModel::onQrScanned,
                            onDismiss = { viewModel.dismissConsent() }
                        )
                    }
                    is MiniAppUiState.QrTransferReview -> {
                        QrP2pTransferReviewDialog(
                            suggestedAmount = state.suggestedAmount,
                            institutions = state.institutions,
                            onDismiss = { viewModel.dismissConsent() },
                            onConfirm = { amount, institutionCode, accountNumber, fullName ->
                                viewModel.sendQrP2pDisbursement(
                                    amount,
                                    institutionCode,
                                    accountNumber,
                                    fullName
                                )
                            }
                        )
                    }
                    is MiniAppUiState.WaitingForNFC -> {
                        SwiftPayNfcTapDialog(
                            amount = state.amount,
                            merchantName = state.merchantName,
                            merchantAddress = state.merchantAddress,
                            timeLeft = state.timeLeft,
                            sessionDurationSeconds = 90,
                            errorMessage = state.errorMessage,
                            statusMessage = state.statusMessage,
                            onRetry = { viewModel.retryNfc() },
                            onCancel = { viewModel.dismissConsent() }
                        )
                    }
                    is MiniAppUiState.WaitingForCVV -> {
                        SwiftPayCvvDialog(
                            amount = state.amount,
                            cardLabel = state.label,
                            last4 = state.pan.takeLast(4),
                            onConfirm = { cvv -> viewModel.onCvvEntered(cvv) },
                            onCancel = { viewModel.dismissConsent() }
                        )
                    }
                    is MiniAppUiState.PaymentConsent -> {
                        SwiftPayBaseDialog(
                            onDismissRequest = { viewModel.dismissConsent() },
                            icon = Icons.Rounded.Payment,
                            title = "Confirm collection",
                            description = "Create a ${state.data.currency} checkout for ${state.data.description}.\nAmount: ${state.data.amount}",
                            buttons = {
                                SwiftPaySecondaryButton(text = "Cancel", onClick = { viewModel.dismissConsent() }, modifier = Modifier.weight(1f))
                                SwiftPayPrimaryButton(text = "Create checkout", onClick = { viewModel.approvePayment(state.data) }, modifier = Modifier.weight(1f))
                            }
                        )
                    }
                    is MiniAppUiState.PaymentRedirect -> {
                        SwiftPayBaseDialog(
                            onDismissRequest = { viewModel.dismissConsent() },
                            icon = Icons.Rounded.Security,
                            title = "Secure Redirect",
                            description = "You will be redirected to the secure SwiftPay payment page to complete your order.",
                            buttons = {
                                SwiftPaySecondaryButton(text = "Cancel", onClick = { viewModel.dismissConsent() }, modifier = Modifier.weight(1f))
                                SwiftPayPrimaryButton(
                                    text = "Continue",
                                    onClick = {
                                        uriHandler.openUri(state.url)
                                        viewModel.dismissConsent()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        )
                    }
                    is MiniAppUiState.PaymentLinkReady -> {
                        EnhancedPaymentLinkDialog(
                            url = state.url,
                            linkId = state.linkId,
                            amount = state.amount,
                            currency = state.currency,
                            hasFixedAmount = true,
                            description = state.description,
                            onOpen = { uriHandler.openUri(state.url) },
                            onDismiss = { viewModel.dismissConsent() }
                        )
                    }
                    is MiniAppUiState.Idle -> {
                    }
                }
            }
        }
    }
}
