package com.example.myapplication.data

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class DeviceBiometricAuthenticator(
    activity: FragmentActivity,
    private val onAuthenticated: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val biometricManager = BiometricManager.from(activity)
    private val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onAuthenticated()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    onError(errString.toString())
                }
            }
        }
    )

    fun isAvailable(): Boolean = biometricManager.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun availabilityMessage(): String = when (biometricManager.canAuthenticate(AUTHENTICATORS)) {
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "This device does not have a supported biometric sensor."
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "The biometric sensor is temporarily unavailable."
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Enroll a fingerprint in your device settings to use this feature."
        else -> "Biometric authentication is unavailable. You can continue with your MPIN."
    }

    fun authenticate() {
        if (!isAvailable()) {
            onError(availabilityMessage())
            return
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock DRPAY")
            .setSubtitle("Confirm your identity to continue")
            .setNegativeButtonText("Use MPIN")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(promptInfo)
    }

    private companion object {
        const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG
    }
}
