package com.example.myapplication.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import com.example.myapplication.util.DebugLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(val context: Context) {
    private val encryptedTokenStore = EncryptedTokenStore(context)
    private val devicePinManager = DevicePinManager(context)

    fun getAccessToken(): String? = encryptedTokenStore.read()

    val backendAccessToken: Flow<String?> = context.dataStore.data.map {
        encryptedTokenStore.read()
    }

    companion object {
        val SWIFTPAY_ACCESS_KEY = stringPreferencesKey("swiftpay_access_key")
        val SWIFTPAY_SECRET_KEY = stringPreferencesKey("swiftpay_secret_key")
        val SWIFTPAY_API_KEY = stringPreferencesKey("swiftpay_api_key")
        val SWIFTPAY_BASE_URL = stringPreferencesKey("swiftpay_base_url")
        val SWIFTPAY_MERCHANT_ID = stringPreferencesKey("swiftpay_merchant_id")
        val SWIFTPAY_TERMINAL_ID = stringPreferencesKey("swiftpay_terminal_id")
        val WALLET_BALANCE = stringPreferencesKey("wallet_balance")
        val MERCHANT_ALIAS = stringPreferencesKey("merchant_alias")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val IS_LOGGED_IN = stringPreferencesKey("is_logged_in")
        val LOGGED_IN_IDENTITY = stringPreferencesKey("logged_in_email")
        val LOGGED_IN_ROLE = stringPreferencesKey("logged_in_role")
        val SESSION_TOKEN = stringPreferencesKey("session_token")
        val DEPOSIT_ACCOUNT_NUMBER = stringPreferencesKey("deposit_account_number")
    }

    val depositAccountNumber: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[DEPOSIT_ACCOUNT_NUMBER]
    }

    suspend fun saveDepositAccountNumber(accountNumber: String) {
        context.dataStore.edit { preferences ->
            preferences[DEPOSIT_ACCOUNT_NUMBER] = accountNumber
        }
    }

    suspend fun clearDepositAccountNumber() {
        context.dataStore.edit { preferences ->
            preferences.remove(DEPOSIT_ACCOUNT_NUMBER)
        }
    }

    val merchantAlias: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[MERCHANT_ALIAS]
    }

    suspend fun saveWalletBalance(balance: String) {
        context.dataStore.edit { preferences ->
            preferences[WALLET_BALANCE] = balance
        }
    }

    val walletBalance: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[WALLET_BALANCE]
    }

    suspend fun saveMerchantAlias(alias: String) {
        context.dataStore.edit { preferences ->
            preferences[MERCHANT_ALIAS] = alias
        }
    }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    val themeMode: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE]
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_LOGGED_IN] == "true"
    }

    val loggedInIdentity: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LOGGED_IN_IDENTITY]
    }

    val loggedInRole: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LOGGED_IN_ROLE]
    }

    suspend fun setLoggedIn(identity: String, loggedIn: Boolean, role: String? = null) {
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = if (loggedIn) "true" else "false"
            if (loggedIn) {
                if (preferences[LOGGED_IN_IDENTITY] != identity) {
                    preferences.remove(DEPOSIT_ACCOUNT_NUMBER)
                }
                clearLegacySwiftPayKeys(preferences)
                preferences.remove(WALLET_BALANCE)
            }
            if (loggedIn) {
                preferences[LOGGED_IN_IDENTITY] = identity
                if (role != null) preferences[LOGGED_IN_ROLE] = role
                DebugLogger.logLoginSuccess(identity.logSafeIdentity())
            } else {
                preferences.remove(LOGGED_IN_IDENTITY)
                preferences.remove(LOGGED_IN_ROLE)
                preferences.remove(DEPOSIT_ACCOUNT_NUMBER)
                DebugLogger.logLogout(identity.logSafeIdentity())
            }

        }
    }

    suspend fun clearSession() {
        encryptedTokenStore.clear()
        devicePinManager.clear()
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = "false"
            preferences.remove(LOGGED_IN_IDENTITY)
            preferences.remove(LOGGED_IN_ROLE)
            preferences.remove(WALLET_BALANCE)
            preferences.remove(DEPOSIT_ACCOUNT_NUMBER)
            clearLegacySwiftPayKeys(preferences)
        }
    }

    suspend fun saveBackendAccessToken(token: String) {
        encryptedTokenStore.write(token)
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = "true"
        }
    }

    fun hasDevicePin(): Boolean = devicePinManager.hasPin()

    fun saveDevicePin(pin: String) = devicePinManager.savePin(pin)

    fun verifyDevicePin(pin: String): DevicePinCheck = devicePinManager.verifyPin(pin)

    fun isBiometricUnlockEnabled(): Boolean = devicePinManager.isBiometricUnlockEnabled()

    fun setBiometricUnlockEnabled(enabled: Boolean) = devicePinManager.setBiometricUnlockEnabled(enabled)

    private fun clearLegacySwiftPayKeys(preferences: androidx.datastore.preferences.core.MutablePreferences) {
        preferences.remove(SWIFTPAY_ACCESS_KEY)
        preferences.remove(SWIFTPAY_SECRET_KEY)
        preferences.remove(SWIFTPAY_API_KEY)
        preferences.remove(SWIFTPAY_BASE_URL)
        preferences.remove(SWIFTPAY_MERCHANT_ID)
        preferences.remove(SWIFTPAY_TERMINAL_ID)
    }

    suspend fun saveSessionToken(session: SessionToken) {
        context.dataStore.edit { preferences ->
            val json = Json.encodeToString(session)
            preferences[SESSION_TOKEN] = json
        }
    }

    val sessionToken: Flow<SessionToken?> = context.dataStore.data.map { preferences ->
        val json = preferences[SESSION_TOKEN]
        if (json != null) {
            try {
                Json.decodeFromString<SessionToken>(json)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    suspend fun getSessionToken(): Flow<SessionToken?> {
        return sessionToken
    }

    suspend fun clearSessionToken() {
        context.dataStore.edit { preferences ->
            preferences.remove(SESSION_TOKEN)
        }
    }
}

private fun String.logSafeIdentity(): String =
    substringAfter('@', missingDelimiterValue = "user").let { domain -> "***@$domain" }
