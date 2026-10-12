package com.example.myapplication.ui.screens

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.BackendAuthService
import com.example.myapplication.data.DevicePinCheck
import com.example.myapplication.data.DevicePinManager
import com.example.myapplication.data.SettingsManager
import com.example.myapplication.ui.components.FastPayLogo
import com.example.myapplication.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.util.Patterns

sealed interface LoginState {
    object Landing : LoginState
    object Credentials : LoginState
    object WaitingApproval : LoginState
    data class Otp(val email: String) : LoginState
    object MpinSetup : LoginState
    object MpinConfirmation : LoginState
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
    private var pendingMpin: String? = null

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            val token = settingsManager.backendAccessToken.first()
            if (!token.isNullOrBlank() && !settingsManager.hasDevicePin()) {
                state = LoginState.MpinSetup
            }
        }
    }

    fun navigateToCredentials() {
        isLoading = false
        state = LoginState.Credentials
        errorMessage = null
    }

    fun navigateToLanding() {
        isLoading = false
        state = LoginState.Landing
        errorMessage = null
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
                    errorMessage = null
                    state = LoginState.MpinSetup
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

    fun onMpinConfirmed(pin: String, onSuccess: () -> Unit) {
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
                onSuccess()
            } catch (error: Exception) {
                errorMessage = error.message ?: "Could not secure the MPIN on this device."
            } finally {
                isLoading = false
            }
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
                onLoginClick = { viewModel.navigateToCredentials() }
            )
            LoginState.Credentials -> CredentialsContent(
                username = viewModel.username,
                isLoading = viewModel.isLoading,
                errorMessage = viewModel.errorMessage,
                onUsernameChange = { viewModel.username = it },
                onBack = { viewModel.navigateToLanding() },
                onLoginClick = { viewModel.onLoginClick() }
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
                onBack = { viewModel.navigateToCredentials() },
                onResendClick = { viewModel.onResendOtpClick() },
                onVerifyClick = { viewModel.onVerifyOtpClick(onLoginSuccess) }
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
                onSubmit = { viewModel.onMpinConfirmed(it, onLoginSuccess) }
            )
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
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = MaterialTheme.colorScheme.onBackground

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDarkTheme) Color(0xFF121212) else Color(0xFFF4F8FF))
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
            else "Use this six-digit code to unlock SwiftPay on this device. OTP sign-in is only needed to register another device.",
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
    onUnlocked: () -> Unit,
    onUseAnotherAccount: () -> Unit
) {
    val context = LocalContext.current
    val pinManager = remember { DevicePinManager(context) }
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val textColor = MaterialTheme.colorScheme.onBackground

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isSystemInDarkTheme()) Color(0xFF121212) else Color(0xFFF4F8FF))
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
fun LandingContent(onLoginClick: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(800, easing = LinearOutSlowInEasing),
        label = "contentAlpha"
    )
    val contentOffset by animateFloatAsState(
        targetValue = if (visible) 0f else 50f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 100f),
        label = "contentOffset"
    )

    val isDarkTheme = isSystemInDarkTheme()
    val bgColors = if (isDarkTheme) {
        listOf(Color(0xFF0F172A), Color(0xFF020617))
    } else {
        listOf(Color(0xFFF4F8FF), Color(0xFFE6F0FF))
    }
    val headlineColor = if (isDarkTheme) Color.White else Color(0xFF102B59)
    val supportingColor = if (isDarkTheme) Color.White.copy(alpha = 0.9f) else Color(0xFF526780)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(bgColors))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentOffset
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            FastPayLogo(scale = 1.35f, isDark = isDarkTheme)

            Spacer(modifier = Modifier.weight(1f))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Accept Payments\nAnywhere, Anytime.",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 42.sp
                    ),
                    color = headlineColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.White,
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = if (isDarkTheme) 0.dp else 2.dp
                ) {
                    Text(
                        text = "Hosted Checkout · Payment Links · Transfers",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = supportingColor,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onLoginClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FastPayBlue),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Login, null, modifier = Modifier.size(20.dp), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun CredentialsContent(
    username: String,
    isLoading: Boolean,
    errorMessage: String?,
    onUsernameChange: (String) -> Unit,
    onBack: () -> Unit,
    onLoginClick: () -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    val bgColor = if (isDarkTheme) Color(0xFF121212) else Color.White
    val textColor = if (isDarkTheme) Color.White else FastPayNavy
    val secondaryTextColor = if (isDarkTheme) Color.LightGray else FastPayTextSecondary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = textColor)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text("Help Center", color = textColor.copy(alpha = 0.6f), style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(40.dp))
        FastPayLogo(scale = 1.3f, isDark = isSystemInDarkTheme())

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "Merchant Sign In",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = textColor
        )
        Text(
            text = "Sign in with an authorized merchant email",
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryTextColor
        )

        Spacer(modifier = Modifier.height(52.dp))

        CustomTextField(
            value = username,
            onValueChange = onUsernameChange,
            placeholder = "Merchant email address",
            leadingIcon = Icons.Rounded.AlternateEmail,
            keyboardType = KeyboardType.Email,
            isDarkTheme = isSystemInDarkTheme()
        )
        Spacer(modifier = Modifier.height(20.dp))

        Spacer(modifier = Modifier.weight(1f))

        if (errorMessage != null) {
            Surface(
                color = Color(0xFFFFEBEE),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFD32F2F),
                    modifier = Modifier.padding(14.dp),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = onLoginClick,
            modifier = Modifier.fillMaxWidth().height(62.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FastPayBlue,
                disabledContainerColor = FastPayBlue.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(18.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Sign In", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
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
    onVerifyClick: () -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
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
        Text("Security Verification", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = textColor)
        Text(
            text = "We sent a 6-digit verification code to your email.\nPlease check your inbox.",
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
                Text("Verify & Sign In", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 16.sp)
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
