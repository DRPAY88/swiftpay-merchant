package com.example.myapplication.ui.screens

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.BackendAuthService
import com.example.myapplication.data.DeviceBiometricAuthenticator
import com.example.myapplication.data.DevicePinCheck
import com.example.myapplication.data.DevicePinManager
import com.example.myapplication.data.SettingsManager
import com.example.myapplication.ui.components.DrPayLogo
import com.example.myapplication.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import android.util.Patterns

sealed interface LoginState {
    object Landing : LoginState
    object WaitingApproval : LoginState
    data class Otp(val email: String) : LoginState
    object MpinSetup : LoginState
    object MpinConfirmation : LoginState
    object OnboardingCheck : LoginState
    object Onboarding : LoginState
    object BiometricOptIn : LoginState
    object RegistrationEmail : LoginState
    data class RegistrationOtp(val email: String) : LoginState
    object RegistrationForm : LoginState
    data class RegistrationResult(val email: String, val status: String, val reviewNote: String) : LoginState
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)
    private val authService = BackendAuthService()

    var state by mutableStateOf<LoginState>(LoginState.Landing)
        private set

    var username by mutableStateOf("")
    var otpCode by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var resendTimer by mutableStateOf(0)
    var registrationAvailable by mutableStateOf(false)
        private set
    var registrationConsent by mutableStateOf(false)
    var businessName by mutableStateOf("")
    var businessRegistrationNumber by mutableStateOf("")
    var businessAddress by mutableStateOf("")
    var businessType by mutableStateOf("")
    var representativeFullName by mutableStateOf("")
    var representativePhone by mutableStateOf("")
    var registrationReviewNote by mutableStateOf("")
    var uploadedRegistrationDocuments by mutableStateOf(setOf<String>())
    private var pendingMpin: String? = null
    private var onboardingAccessToken: String? = null
    private var registrationAccessToken: String? = null

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            authService.getRegistrationConfig().onSuccess { settings ->
                registrationAvailable = settings.enabled && settings.secureStorageAvailable
            }
        }
        viewModelScope.launch {
            val token = settingsManager.backendAccessToken.first()
            if (!token.isNullOrBlank() && !settingsManager.hasDevicePin()) {
                onboardingAccessToken = token
                checkOnboardingState()
            }
        }
    }

    fun navigateToLanding() {
        isLoading = false
        state = LoginState.Landing
        errorMessage = null
        otpCode = ""
        registrationAccessToken = null
    }

    fun onRegisterClick() {
        if (!registrationAvailable) {
            errorMessage = "Merchant registration is temporarily unavailable. Please contact support."
            return
        }
        errorMessage = null
        state = LoginState.RegistrationEmail
    }

    fun requestRegistrationOtp() {
        if (isLoading) return
        val email = username.trim().lowercase()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errorMessage = "Enter a valid email address"
            return
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                authService.requestRegistrationOtp(email).onSuccess {
                    otpCode = ""
                    state = LoginState.RegistrationOtp(email)
                    startResendTimer()
                    Toast.makeText(getApplication(), "Verification code sent", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    errorMessage = error.message ?: "Unable to send verification code"
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = error.message ?: "Network error"
            } finally {
                isLoading = false
            }
        }
    }

    fun verifyRegistrationOtp() {
        if (isLoading) return
        val email = (state as? LoginState.RegistrationOtp)?.email ?: return
        if (otpCode.length != 6) {
            errorMessage = "Enter the 6-digit verification code"
            return
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                authService.verifyRegistrationOtp(email, otpCode).onSuccess { session ->
                    registrationAccessToken = session.accessToken
                    otpCode = ""
                    authService.getRegistrationStatus(session.accessToken).onSuccess { application ->
                        businessName = application.profile.businessName
                        businessRegistrationNumber = application.profile.businessRegistrationNumber
                        businessAddress = application.profile.businessAddress
                        businessType = application.profile.businessType
                        representativeFullName = application.profile.representativeFullName
                        representativePhone = application.profile.representativePhone
                        registrationReviewNote = application.reviewNote
                        uploadedRegistrationDocuments = application.documents.toSet()
                        state = if (application.status == "submitted") {
                            LoginState.RegistrationResult(application.email, application.status, application.reviewNote)
                        } else {
                            LoginState.RegistrationForm
                        }
                    }.onFailure { error ->
                        errorMessage = error.message ?: "Could not load your application."
                    }
                }.onFailure { error ->
                    errorMessage = error.message ?: "Invalid or expired verification code"
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = error.message ?: "Network error"
            } finally {
                isLoading = false
            }
        }
    }

    fun resendRegistrationOtp() {
        if (isLoading || resendTimer > 0) return
        val email = (state as? LoginState.RegistrationOtp)?.email ?: return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                authService.requestRegistrationOtp(email).onSuccess { startResendTimer() }
                    .onFailure { error -> errorMessage = error.message ?: "Unable to resend verification code" }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = error.message ?: "Network error"
            } finally {
                isLoading = false
            }
        }
    }

    fun uploadRegistrationDocument(kind: String, contentType: String, bytes: ByteArray) {
        if (!registrationConsent) {
            errorMessage = "Review and accept the document privacy notice before uploading."
            return
        }
        if (bytes.isEmpty() || bytes.size > 8 * 1024 * 1024) {
            errorMessage = "Each document must be no larger than 8 MB."
            return
        }
        val token = registrationAccessToken ?: run {
            errorMessage = "Your application session expired. Verify your email again."
            return
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                authService.uploadRegistrationDocument(token, kind, contentType, bytes)
                    .onSuccess { uploadedRegistrationDocuments = uploadedRegistrationDocuments + kind }
                    .onFailure { error -> errorMessage = error.message ?: "Document upload failed" }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = error.message ?: "Document upload failed"
            } finally {
                isLoading = false
            }
        }
    }

    fun submitRegistration() {
        if (isLoading) return
        if (!registrationConsent) {
            errorMessage = "Accept the privacy and selfie-review notice before submitting."
            return
        }
        val requiredDocuments = setOf("business_certificate", "representative_id", "selfie")
        if (!uploadedRegistrationDocuments.containsAll(requiredDocuments)) {
            errorMessage = "Upload the business certificate, representative ID, and selfie before submitting."
            return
        }
        val token = registrationAccessToken ?: run {
            errorMessage = "Your application session expired. Verify your email again."
            return
        }
        val profile = com.example.myapplication.data.api.RegistrationProfileBody(
            businessName = businessName,
            businessRegistrationNumber = businessRegistrationNumber,
            businessAddress = businessAddress,
            businessType = businessType,
            representativeFullName = representativeFullName,
            representativePhone = representativePhone
        )
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                authService.saveRegistrationProfile(token, profile).onSuccess {
                    authService.submitRegistration(token).onSuccess { result ->
                        state = LoginState.RegistrationResult(result.email, result.status, result.reviewNote)
                    }.onFailure { error ->
                        errorMessage = error.message ?: "Could not submit application"
                    }
                }.onFailure { error ->
                    errorMessage = error.message ?: "Could not save business details"
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = error.message ?: "Could not submit application"
            } finally {
                isLoading = false
            }
        }
    }

    fun onLoginClick() {
        if (isLoading) return
        val email = username.trim().lowercase()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errorMessage = "Enter a valid merchant email address"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                authService.requestOtp(email).onSuccess {
                    state = LoginState.Otp(email)
                    startResendTimer()
                    Toast.makeText(getApplication(), "OTP code sent to email", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    errorMessage = error.message ?: "Unable to send sign-in code"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "Network error"
            } finally {
                isLoading = false
            }
        }
    }

    fun onVerifyOtpClick(onSuccess: () -> Unit) {
        if (isLoading) return
        val currentOtp = (state as? LoginState.Otp)?.email ?: username
        if (otpCode.length < 6) {
            errorMessage = "Please enter a valid 6-digit code"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                authService.verifyOtp(currentOtp, otpCode).onSuccess { session ->
                    settingsManager.saveBackendAccessToken(session.accessToken)
                    settingsManager.setLoggedIn(session.email, true)
                    onboardingAccessToken = session.accessToken
                    checkOnboardingState()
                }.onFailure { error ->
                    errorMessage = error.message ?: "Invalid or expired verification code"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "Network error"
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun checkOnboardingState() {
        val accessToken = onboardingAccessToken
        if (accessToken.isNullOrBlank()) {
            state = LoginState.OnboardingCheck
            errorMessage = "Your sign-in session is unavailable. Please sign in again."
            return
        }

        state = LoginState.OnboardingCheck
        isLoading = true
        errorMessage = null
        try {
            authService.getOnboardingState(accessToken).onSuccess { onboarding ->
                state = if (onboarding.required) {
                    LoginState.Onboarding
                } else {
                    LoginState.MpinSetup
                }
            }.onFailure { error ->
                errorMessage = error.message ?: "Could not check onboarding status."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.message ?: "Could not check onboarding status."
        } finally {
            isLoading = false
        }
    }

    fun retryOnboardingCheck() {
        if (isLoading) return
        viewModelScope.launch { checkOnboardingState() }
    }

    fun finishOnboarding() {
        if (isLoading) return
        val accessToken = onboardingAccessToken
        if (accessToken.isNullOrBlank()) {
            errorMessage = "Your sign-in session is unavailable. Please sign in again."
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                authService.completeOnboarding(accessToken).onSuccess {
                    state = LoginState.MpinSetup
                }.onFailure { error ->
                    errorMessage = error.message ?: "Could not save onboarding completion."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not save onboarding completion."
            } finally {
                isLoading = false
            }
        }
    }

    fun onMpinCreated(pin: String) {
        if (!pin.matches(Regex("^\\d{6}$"))) {
            errorMessage = "Enter a six-digit MPIN."
            return
        }
        pendingMpin = pin
        errorMessage = null
        state = LoginState.MpinConfirmation
    }

    fun onMpinConfirmationBack() {
        errorMessage = null
        state = LoginState.MpinSetup
    }

    fun onMpinConfirmed(pin: String) {
        if (pin != pendingMpin) {
            errorMessage = "Those MPINs do not match. Try again."
            return
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                settingsManager.saveDevicePin(pin)
                pendingMpin = null
                state = LoginState.BiometricOptIn
            } catch (error: Exception) {
                errorMessage = error.message ?: "Could not secure the MPIN on this device."
            } finally {
                isLoading = false
            }
        }
    }

    fun onBiometricEnabled(onSuccess: () -> Unit) {
        try {
            settingsManager.setBiometricUnlockEnabled(true)
            errorMessage = null
            onSuccess()
        } catch (error: Exception) {
            errorMessage = error.message ?: "Could not enable biometric unlock."
        }
    }

    fun onResendOtpClick() {
        if (isLoading || resendTimer > 0) return

        val email = (state as? LoginState.Otp)?.email ?: username
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                authService.requestOtp(email).onSuccess {
                    Toast.makeText(getApplication(), "New sign-in code sent", Toast.LENGTH_SHORT).show()
                    startResendTimer()
                }.onFailure { error ->
                    errorMessage = error.message ?: "Unable to resend sign-in code"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "Network error"
            } finally {
                isLoading = false
            }
        }
    }

    private fun startResendTimer() {
        resendTimer = 60
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (resendTimer > 0) {
                delay(1000)
                resendTimer--
            }
        }
    }
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val state = viewModel.state

    Crossfade(targetState = state, label = "loginState") { currentState ->
        when (currentState) {
            LoginState.Landing -> LandingContent(
                username = viewModel.username,
                isLoading = viewModel.isLoading,
                errorMessage = viewModel.errorMessage,
                registrationAvailable = viewModel.registrationAvailable,
                onUsernameChange = { viewModel.username = it },
                onLoginClick = { viewModel.onLoginClick() },
                onRegisterClick = viewModel::onRegisterClick
            )
            LoginState.WaitingApproval -> WaitingApprovalContent(
                onCancel = { viewModel.navigateToLanding() }
            )
            is LoginState.Otp -> OtpContent(
                otpCode = viewModel.otpCode,
                isLoading = viewModel.isLoading,
                errorMessage = viewModel.errorMessage,
                resendTimer = viewModel.resendTimer,
                onOtpChange = { viewModel.otpCode = it },
                onBack = { viewModel.navigateToLanding() },
                onResendClick = { viewModel.onResendOtpClick() },
                onVerifyClick = { viewModel.onVerifyOtpClick(onLoginSuccess) }
            )
            LoginState.RegistrationEmail -> RegistrationEmailContent(
                email = viewModel.username,
                isLoading = viewModel.isLoading,
                errorMessage = viewModel.errorMessage,
                onEmailChange = { viewModel.username = it },
                onBack = viewModel::navigateToLanding,
                onContinue = viewModel::requestRegistrationOtp
            )
            is LoginState.RegistrationOtp -> OtpContent(
                otpCode = viewModel.otpCode,
                isLoading = viewModel.isLoading,
                errorMessage = viewModel.errorMessage,
                resendTimer = viewModel.resendTimer,
                onOtpChange = { viewModel.otpCode = it },
                onBack = viewModel::navigateToLanding,
                onResendClick = viewModel::resendRegistrationOtp,
                onVerifyClick = viewModel::verifyRegistrationOtp,
                registrationFlow = true
            )
            LoginState.RegistrationForm -> RegistrationFormContent(
                viewModel = viewModel,
                onBack = viewModel::navigateToLanding
            )
            is LoginState.RegistrationResult -> RegistrationResultContent(
                result = currentState,
                onDone = viewModel::navigateToLanding
            )
            LoginState.MpinSetup -> MpinEnrollmentContent(
                isConfirmation = false,
                isLoading = viewModel.isLoading,
                errorMessage = viewModel.errorMessage,
                onSubmit = viewModel::onMpinCreated
            )
            LoginState.MpinConfirmation -> MpinEnrollmentContent(
                isConfirmation = true,
                isLoading = viewModel.isLoading,
                errorMessage = viewModel.errorMessage,
                onBack = viewModel::onMpinConfirmationBack,
                onSubmit = viewModel::onMpinConfirmed
            )
            LoginState.OnboardingCheck -> OnboardingCheckContent(
                isLoading = viewModel.isLoading,
                errorMessage = viewModel.errorMessage,
                onRetry = viewModel::retryOnboardingCheck
            )
            LoginState.Onboarding -> MerchantOnboardingContent(
                isLoading = viewModel.isLoading,
                errorMessage = viewModel.errorMessage,
                onFinish = viewModel::finishOnboarding
            )
            LoginState.BiometricOptIn -> BiometricOptInContent(
                persistenceError = viewModel.errorMessage,
                onEnabled = { viewModel.onBiometricEnabled(onLoginSuccess) },
                onSkip = onLoginSuccess
            )
        }
    }
}

@Composable
private fun OnboardingCheckContent(
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = FastPayBlue)
        Spacer(Modifier.height(24.dp))
        Text(
            "Preparing your account",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            textAlign = TextAlign.Center
        )
        Text(
            errorMessage ?: "Checking whether your merchant account needs onboarding.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
        if (!isLoading) {
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 20.dp)
            ) {
                Text("Try again")
            }
        }
    }
}

private data class MerchantOnboardingStep(
    val icon: ImageVector,
    val title: String,
    val description: String
)

@Composable
private fun MerchantOnboardingContent(
    isLoading: Boolean,
    errorMessage: String?,
    onFinish: () -> Unit
) {
    val steps = listOf(
        MerchantOnboardingStep(
            Icons.Rounded.Home,
            "Your merchant dashboard",
            "See your account activity and get to the tools you use most."
        ),
        MerchantOnboardingStep(
            Icons.Rounded.QrCodeScanner,
            "Accept payments",
            "Create payment requests and let customers pay through supported methods."
        ),
        MerchantOnboardingStep(
            Icons.Rounded.AccountBalanceWallet,
            "Track your funds",
            "Review wallet activity and transfer funds from the wallet screen."
        )
    )
    var currentStep by remember { mutableIntStateOf(0) }
    val step = steps[currentStep]
    val textColor = MaterialTheme.colorScheme.onBackground

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            steps.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = 32.dp, height = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (index <= currentStep) FastPayBlue
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Surface(
            modifier = Modifier.size(104.dp),
            shape = CircleShape,
            color = FastPayBlue.copy(alpha = 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(step.icon, contentDescription = null, tint = FastPayBlue, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Welcome to DRPAY",
            style = MaterialTheme.typography.labelLarge,
            color = FastPayBlue,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            step.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            textAlign = TextAlign.Center
        )
        Text(
            step.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
        if (errorMessage != null) {
            Text(
                errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                if (currentStep < steps.lastIndex) currentStep++ else onFinish()
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(if (currentStep < steps.lastIndex) "Continue" else "Get started", fontWeight = FontWeight.Bold)
        }
        TextButton(
            onClick = onFinish,
            enabled = !isLoading,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text("Skip onboarding", color = FastPayBlue)
        }
    }
}

@Composable
private fun BiometricOptInContent(
    persistenceError: String?,
    onEnabled: () -> Unit,
    onSkip: () -> Unit
) {
    val activity = LocalContext.current as? androidx.fragment.app.FragmentActivity
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val currentOnEnabled by rememberUpdatedState(onEnabled)
    val currentOnError by rememberUpdatedState<(String) -> Unit> { errorMessage = it }
    val authenticator = remember(activity) {
        activity?.let {
            DeviceBiometricAuthenticator(
                activity = it,
                onAuthenticated = { currentOnEnabled() },
                onError = { currentOnError(it) }
            )
        }
    }
    val availabilityMessage = authenticator?.availabilityMessage()
        ?: "Biometric unlock is unavailable in this app session."
    val textColor = MaterialTheme.colorScheme.onBackground
    val gradient = Brush.horizontalGradient(listOf(Color(0xFFA44DE2), Color(0xFF087BE5)))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFCC9BFF), Color(0xFFB990F5))))
        )
        Spacer(Modifier.weight(0.5f))
        Box(
            modifier = Modifier
                .size(250.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF71396F), Color(0xFF174C76), Color(0xFF087BE5)))),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(162.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Fingerprint,
                        contentDescription = null,
                        tint = FastPayBlue,
                        modifier = Modifier.size(82.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(48.dp))
        Text(
            "Sign in fast",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium,
            color = textColor,
            textAlign = TextAlign.Center
        )
        Text(
            "Use your fingerprint to unlock DRPAY without entering your MPIN every time.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp)
        )
        Spacer(Modifier.weight(0.8f))
        if (errorMessage != null || persistenceError != null) {
            Text(
                errorMessage ?: persistenceError.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        } else if (authenticator?.isAvailable() != true) {
            Text(
                availabilityMessage,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(gradient)
                .semantics(mergeDescendants = true) {}
                .clickable(
                    enabled = authenticator?.isAvailable() == true,
                    role = Role.Button
                ) {
                    errorMessage = null
                    authenticator?.authenticate()
                },
            contentAlignment = Alignment.Center
        ) {
            Text("Turn on fingerprint ID", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        TextButton(onClick = onSkip, modifier = Modifier.padding(top = 8.dp)) {
            Text("Not now", color = FastPayBlue)
        }
    }
}

@Composable
fun MpinEnrollmentContent(
    isConfirmation: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: (() -> Unit)? = null,
    onSubmit: (String) -> Unit
) {
    var pin by remember(isConfirmation) { mutableStateOf("") }
    val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
    val textColor = MaterialTheme.colorScheme.onBackground

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = textColor)
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Surface(shape = CircleShape, color = FastPayBlue.copy(alpha = 0.1f), modifier = Modifier.size(76.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = FastPayBlue, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            if (isConfirmation) "Confirm your MPIN" else "Create a device MPIN",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            textAlign = TextAlign.Center
        )
        Text(
            if (isConfirmation) "Enter it once more to finish securing this device."
            else "Use this six-digit code to unlock DRPAY on this device. OTP sign-in is only needed to register another device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp)
        )
        Spacer(modifier = Modifier.height(30.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { value -> if (value.length <= 6 && value.all(Char::isDigit)) pin = value },
            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
            label = { Text("6-digit MPIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center, letterSpacing = 10.sp),
            isError = errorMessage != null,
            supportingText = { if (errorMessage != null) Text(errorMessage) }
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = { onSubmit(pin) },
            enabled = pin.length == 6 && !isLoading,
            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(if (isConfirmation) "Confirm MPIN" else "Continue", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun DevicePinScreen(
    email: String?,
    biometricUnlockEnabled: Boolean,
    onUnlocked: () -> Unit,
    onUseAnotherAccount: () -> Unit
) {
    val context = LocalContext.current
    val pinManager = remember { DevicePinManager(context) }
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val currentOnUnlocked by rememberUpdatedState(onUnlocked)
    val currentOnError by rememberUpdatedState<(String) -> Unit> { message = it }
    val activity = context as? androidx.fragment.app.FragmentActivity
    val authenticator = remember(activity) {
        activity?.let {
            DeviceBiometricAuthenticator(
                activity = it,
                onAuthenticated = { currentOnUnlocked() },
                onError = { currentOnError(it) }
            )
        }
    }
    val textColor = MaterialTheme.colorScheme.onBackground

    LaunchedEffect(biometricUnlockEnabled, authenticator) {
        if (biometricUnlockEnabled) authenticator?.authenticate()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Surface(shape = CircleShape, color = FastPayBlue.copy(alpha = 0.1f), modifier = Modifier.size(76.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = FastPayBlue, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text("Welcome back", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = textColor)
        Text(email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f))
        Spacer(modifier = Modifier.height(28.dp))
        if (biometricUnlockEnabled && authenticator?.isAvailable() == true) {
            TextButton(
                onClick = {
                    message = null
                    authenticator.authenticate()
                }
            ) {
                Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Use fingerprint")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = pin,
            onValueChange = { value -> if (value.length <= 6 && value.all(Char::isDigit)) pin = value },
            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
            label = { Text("Enter your MPIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center, letterSpacing = 10.sp),
            isError = message != null,
            supportingText = { message?.let { Text(it) } }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                when (val result = pinManager.verifyPin(pin)) {
                    DevicePinCheck.Verified -> onUnlocked()
                    is DevicePinCheck.Rejected -> {
                        message = if (result.attemptsRemaining > 0) "Incorrect MPIN. ${result.attemptsRemaining} attempts remaining."
                        else "MPIN is unavailable. Sign in again to secure this device."
                        pin = ""
                    }
                    is DevicePinCheck.Locked -> {
                        message = "Too many attempts. Try again in ${result.secondsRemaining} seconds."
                        pin = ""
                    }
                }
            },
            enabled = pin.length == 6,
            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Unlock", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onUseAnotherAccount, modifier = Modifier.padding(top = 8.dp)) {
            Text("Use another account")
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun WaitingApprovalContent(onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Waiting for authorization from your trusted device...", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        CircularProgressIndicator(color = FastPayBlue)
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
fun LandingContent(
    username: String,
    isLoading: Boolean,
    errorMessage: String?,
    registrationAvailable: Boolean,
    onUsernameChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(FintechMotion.entranceMillis, easing = FintechMotion.standardEasing),
        label = "contentAlpha"
    )
    val contentOffset by animateFloatAsState(
        targetValue = if (visible) 0f else 20f,
        animationSpec = tween(FintechMotion.entranceMillis, easing = FintechMotion.standardEasing),
    label = "contentOffset"
    )

    val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
    val backgroundColor = MaterialTheme.colorScheme.background
    val headlineColor = MaterialTheme.colorScheme.onBackground
    val supportingColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f)
    val fieldColor = if (isDarkTheme) FastPayDarkNavy else Color.White
    val outlineColor = if (isDarkTheme) Color.White.copy(alpha = 0.28f) else Color(0xFFCACDD2)

    Column(
    modifier = Modifier
        .fillMaxSize()
        .background(backgroundColor)
        .statusBarsPadding()
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 28.dp, vertical = 20.dp)
        .graphicsLayer {
            alpha = contentAlpha
            translationY = contentOffset
        },
    horizontalAlignment = Alignment.CenterHorizontally
    ) {
    Spacer(modifier = Modifier.height(24.dp))
    DrPayLogo(size = 190.dp)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Merchant payments, securely managed.",
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
        fontWeight = FontWeight.SemiBold,
        color = supportingColor,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(36.dp))
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Merchant email address") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.AlternateEmail,
                contentDescription = "Email address",
                tint = if (isDarkTheme) Color.LightGray else FastPayTextSecondary
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Go
        ),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = headlineColor,
            unfocusedTextColor = headlineColor,
            focusedContainerColor = fieldColor,
            unfocusedContainerColor = fieldColor,
            focusedBorderColor = FastPayBlue,
            unfocusedBorderColor = outlineColor,
            focusedLeadingIconColor = FastPayBlue,
            unfocusedLeadingIconColor = supportingColor,
            cursorColor = FastPayBlue
        )
    )
    Text(
        text = "We'll email you a one-time sign-in code.",
        style = MaterialTheme.typography.bodySmall,
        color = supportingColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 8.dp)
    )

    if (errorMessage != null) {
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
            color = if (isDarkTheme) Color(0xFF3A1C25) else Color(0xFFFFEBEE),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = errorMessage,
                color = if (isDarkTheme) Color(0xFFFFB4AB) else Color(0xFFD32F2F),
                modifier = Modifier.padding(14.dp),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onLoginClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = FastPayNavy,
            disabledContainerColor = FastPayNavy.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(14.dp),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = "Log In",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 18.sp
            )
        }
    }

    if (registrationAvailable) {
        OutlinedButton(
            onClick = onRegisterClick,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            enabled = !isLoading
        ) {
            Text("Create merchant account", fontWeight = FontWeight.Bold)
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = if (registrationAvailable) "New businesses can apply for merchant access." else "Merchant access is available to authorized accounts only.",
        style = MaterialTheme.typography.bodySmall,
        color = supportingColor,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(44.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = if (isDarkTheme) Color.White.copy(alpha = 0.24f) else Color(0xFFD1D3D6)
        )
        Text(
            text = "Secure Banking",
            style = MaterialTheme.typography.bodyMedium,
            color = supportingColor,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = if (isDarkTheme) Color.White.copy(alpha = 0.24f) else Color(0xFFD1D3D6)
        )
    }
    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = "Powered by: Boss Den Den",
        style = MaterialTheme.typography.labelMedium,
        color = supportingColor,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun RegistrationEmailContent(
    email: String,
    isLoading: Boolean,
    errorMessage: String?,
    onEmailChange: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
        }
        Spacer(Modifier.height(18.dp))
        Icon(Icons.Rounded.Business, contentDescription = null, tint = FastPayBlue, modifier = Modifier.size(48.dp))
        Text("Apply for a merchant account", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Verify your email to start the business registration and KYB review.",
            modifier = Modifier.padding(top = 10.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            label = { Text("Business contact email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Go)
        )
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp)) }
        Button(
            onClick = onContinue,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
        ) {
            if (isLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("Verify email")
        }
        Text(
            "Verification only starts an application. It does not grant merchant or payment access.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun RegistrationFormContent(
    viewModel: LoginViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var documentKind by remember { mutableStateOf("business_certificate") }
    var selfieFile by remember { mutableStateOf<File?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val contentType = context.contentResolver.getType(uri)?.substringBefore(";")?.lowercase()
                        ?: throw IllegalArgumentException("Could not determine the selected file type.")
                    if (contentType !in setOf("application/pdf", "image/jpeg", "image/png")) {
                        throw IllegalArgumentException("Choose a PDF, JPEG, or PNG document.")
                    }
                    val bytes = readRegistrationDocument(context, uri)
                    viewModel.uploadRegistrationDocument(documentKind, contentType, bytes)
                    localError = null
                } catch (error: Exception) {
                    localError = error.message ?: "Could not read the selected document."
                }
            }
        }
    }

    val selfieCapture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val file = selfieFile
        if (captured && file != null) {
            scope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) { file.inputStream().use { input -> readLimited(input) } }
                    viewModel.uploadRegistrationDocument("selfie", "image/jpeg", bytes)
                    localError = null
                } catch (error: Exception) {
                    localError = error.message ?: "Could not read the selfie."
                } finally {
                    file.delete()
                    selfieFile = null
                }
            }
        } else {
            file?.delete()
            selfieFile = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "MERCHANT KYB · APPLICATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text("Business details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    "STEP 1 OF 2",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            "Tell us about your business and authorized representative, then securely upload the required review documents.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
        )
        if (viewModel.registrationReviewNote.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Reviewer note", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                    Text(
                        viewModel.registrationReviewNote,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        RegistrationSection(
            title = "Business information",
            subtitle = "Use the details shown on your registration documents."
        ) {
            OutlinedTextField(
                viewModel.businessName,
                { viewModel.businessName = it },
                Modifier.fillMaxWidth(),
                label = { Text("Registered business name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                viewModel.businessRegistrationNumber,
                { viewModel.businessRegistrationNumber = it },
                Modifier.fillMaxWidth(),
                label = { Text("Business registration number") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                viewModel.businessType,
                { viewModel.businessType = it },
                Modifier.fillMaxWidth(),
                label = { Text("Business type") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                viewModel.businessAddress,
                { viewModel.businessAddress = it },
                Modifier.fillMaxWidth(),
                label = { Text("Business address") },
                minLines = 2,
                shape = RoundedCornerShape(14.dp)
            )
        }
        RegistrationSection(
            title = "Authorized representative",
            subtitle = "Provide a contact who can verify this application."
        ) {
            OutlinedTextField(
                viewModel.representativeFullName,
                { viewModel.representativeFullName = it },
                Modifier.fillMaxWidth(),
                label = { Text("Full legal name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                viewModel.representativePhone,
                { viewModel.representativePhone = it },
                Modifier.fillMaxWidth(),
                label = { Text("Phone number") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
        }
        RegistrationSection(
            title = "Review documents",
            subtitle = "Upload all three items. Files must be 8 MB or smaller."
        ) {
            RegistrationDocumentRow(
                label = "Business registration certificate",
                supportingText = "PDF, JPEG or PNG",
                uploaded = "business_certificate" in viewModel.uploadedRegistrationDocuments,
                enabled = !viewModel.isLoading && viewModel.registrationConsent,
                onClick = {
                    documentKind = "business_certificate"
                    documentPicker.launch(arrayOf("application/pdf", "image/jpeg", "image/png"))
                }
            )
            RegistrationDocumentRow(
                label = "Representative government ID",
                supportingText = "PDF, JPEG or PNG",
                uploaded = "representative_id" in viewModel.uploadedRegistrationDocuments,
                enabled = !viewModel.isLoading && viewModel.registrationConsent,
                onClick = {
                    documentKind = "representative_id"
                    documentPicker.launch(arrayOf("application/pdf", "image/jpeg", "image/png"))
                }
            )
            RegistrationDocumentRow(
                label = "Selfie for manual review",
                supportingText = "Taken with your device camera",
                uploaded = "selfie" in viewModel.uploadedRegistrationDocuments,
                enabled = !viewModel.isLoading && viewModel.registrationConsent,
                onClick = {
                    try {
                        val directory = File(context.cacheDir, "kyb-selfie").apply { mkdirs() }
                        val file = File.createTempFile("selfie_", ".jpg", directory)
                        selfieFile = file
                        val uri: Uri = FileProvider.getUriForFile(
                            context,
                            "${BuildConfig.APPLICATION_ID}.kybfiles",
                            file
                        )
                        selfieCapture.launch(uri)
                    } catch (error: Exception) {
                        localError = error.message ?: "Could not open the camera."
                    }
                }
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Checkbox(
                    checked = viewModel.registrationConsent,
                    onCheckedChange = { viewModel.registrationConsent = it }
                )
                Column(modifier = Modifier.padding(start = 8.dp, top = 10.dp)) {
                    Text(
                        "Privacy and retention",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "I consent to submitting these documents for KYB review. They are encrypted in storage, available to authorized super-admin reviewers, and retained until an admin explicitly deletes the application. Your selfie is reviewed manually; no automated face matching or liveness check is performed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }
        }
        (localError ?: viewModel.errorMessage)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }
        Button(
            onClick = viewModel::submitRegistration,
            enabled = !viewModel.isLoading,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 22.dp)
        ) {
            if (viewModel.isLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("Submit for KYB review")
        }
    }
}

@Composable
private fun RegistrationDocumentRow(
    label: String,
    supportingText: String,
    uploaded: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (uploaded) Icons.Rounded.CheckCircle else Icons.Rounded.UploadFile,
                contentDescription = null,
                tint = if (uploaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (uploaded) "Uploaded · tap to replace" else supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                if (uploaded) Icons.Rounded.Edit else Icons.Rounded.ChevronRight,
                contentDescription = if (uploaded) "Replace $label" else "Add $label",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun RegistrationSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

@Composable
private fun RegistrationResultContent(
    result: LoginState.RegistrationResult,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = FastPayBlue, modifier = Modifier.size(54.dp))
        Text("Application received", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Your merchant application for ${result.email} is pending manual KYB review. You will be able to sign in after approval.",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
        if (result.reviewNote.isNotBlank()) {
            Text(result.reviewNote, modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Text("Back to sign in")
        }
    }
}

private suspend fun readRegistrationDocument(context: android.content.Context, uri: Uri): ByteArray =
    withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Could not open the selected document.")
        input.use(::readLimited)
    }

private fun readLimited(input: java.io.InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= 8 * 1024 * 1024) { "Each document must be no larger than 8 MB." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    isDarkTheme: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val isFocused = remember { mutableStateOf(false) }

    val bgColor = if (isDarkTheme) Color(0xFF1E1E1E) else FastPayTextField
    val iconColor = if (isDarkTheme) Color.LightGray.copy(alpha = 0.7f) else FastPayNavy.copy(alpha = 0.4f)
    val textColor = if (isDarkTheme) Color.White else FastPayNavy

    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = if (isDarkTheme) Color.Gray else Color.Gray.copy(alpha = 0.5f)) },
        leadingIcon = {
            Icon(
                leadingIcon,
                null,
                tint = if (isFocused.value) FastPayBlue else iconColor,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = onTogglePassword ?: {}) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !isPasswordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused.value = it.isFocused },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = bgColor,
            unfocusedContainerColor = bgColor,
            disabledContainerColor = bgColor,
            focusedIndicatorColor = FastPayBlue,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = FastPayBlue,
            focusedTextColor = textColor,
            unfocusedTextColor = textColor
        ),
        shape = RoundedCornerShape(14.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType)
    )
}

@Composable
fun OtpContent(
    otpCode: String,
    isLoading: Boolean,
    errorMessage: String?,
    resendTimer: Int,
    onOtpChange: (String) -> Unit,
    onBack: () -> Unit,
    onResendClick: () -> Unit,
    onVerifyClick: () -> Unit,
    registrationFlow: Boolean = false
) {
    val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
    val bgColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    val inputBgColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFF2F4F7)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = textColor)
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Surface(
            shape = CircleShape,
            color = if (isDarkTheme) FastPayBlue.copy(alpha = 0.15f) else FastPayNavy.copy(alpha = 0.05f),
            modifier = Modifier.size(84.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Security, null, tint = if (isDarkTheme) FastPayBlue else FastPayNavy, modifier = Modifier.size(42.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            if (registrationFlow) "Verify your email" else "Security Verification",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = textColor
        )
        Text(
            text = if (registrationFlow) {
                "Enter the 6-digit code to continue your merchant application. This does not grant account access."
            } else {
                "We sent a 6-digit verification code to your email.\nPlease check your inbox."
            },
            color = secondaryTextColor,
            modifier = Modifier.padding(top = 12.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(56.dp))

        TextField(
            value = otpCode,
            onValueChange = onOtpChange,
            placeholder = { Text("000000", color = Color.Gray.copy(alpha = 0.5f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = inputBgColor,
                unfocusedContainerColor = inputBgColor,
                focusedIndicatorColor = FastPayBlue,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = textColor,
                unfocusedTextColor = textColor
            ),
            shape = RoundedCornerShape(18.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                textAlign = TextAlign.Center,
                letterSpacing = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textColor
            )
        )

        if (errorMessage != null) {
            Text(errorMessage, color = Color(0xFFEF5350), modifier = Modifier.padding(top = 20.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onVerifyClick,
            modifier = Modifier.fillMaxWidth().height(62.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FastPayBlue),
            shape = RoundedCornerShape(18.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text(
                    if (registrationFlow) "Continue application" else "Verify & Sign In",
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Didn't receive the code? ", color = secondaryTextColor, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (resendTimer > 0) "Wait ${resendTimer}s" else "Resend Code",
                color = FastPayBlue,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = resendTimer == 0) { onResendClick() }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
