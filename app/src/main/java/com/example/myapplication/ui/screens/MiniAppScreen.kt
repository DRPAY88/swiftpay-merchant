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
    initialPath: String? = null,
    modifier: Modifier = Modifier,
    viewModel: MiniAppViewModel = viewModel()
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val webViewUrl = remember(initialPath) {
        val baseUrl = "file:///android_asset/index.html"
        if (!initialPath.isNullOrBlank()) "$baseUrl#$initialPath" else baseUrl
    }

    val uiState = viewModel.uiState

    LaunchedEffect(Unit) {
        viewModel.refreshBalance()
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
                onGenerateDynamicQrWithCurrency = { amount, currency -> viewModel.onGenerateDynamicQr(amount, currency) },
                onQuoteKrwPhpQr = viewModel::onQuoteKrwPhpQr,
                onGenerateQuotedKrwPhpQr = viewModel::onGenerateQuotedKrwPhpQr,
                onStartDepositWizard = viewModel::onStartDepositWizard,
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
                            rateUpdatedAt = state.rateUpdatedAt
                        )
                    }
                    is MiniAppUiState.DepositWizard -> {
                        DepositWizardDialog(
                            accountNumber = state.accountNumber,
                            balance = state.balance,
                            onDismiss = { viewModel.dismissConsent() }
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
                            title = "Confirm Payment",
                            description = "Merchant: ${state.data.description}\nAmount: ${state.data.currency} ${state.data.amount}",
                            buttons = {
                                SwiftPaySecondaryButton(text = "Cancel", onClick = { viewModel.dismissConsent() }, modifier = Modifier.weight(1f))
                                SwiftPayPrimaryButton(text = "Pay Now", onClick = { viewModel.approvePayment(state.data) }, modifier = Modifier.weight(1f))
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
