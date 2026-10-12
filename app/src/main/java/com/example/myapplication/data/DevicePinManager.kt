package com.example.myapplication.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

sealed interface DevicePinCheck {
    data object Verified : DevicePinCheck
    data class Rejected(val attemptsRemaining: Int) : DevicePinCheck
    data class Locked(val secondsRemaining: Long) : DevicePinCheck
}

class DevicePinManager(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun hasPin(): Boolean {
        if (preferences.getString(SALT_KEY, null).isNullOrBlank() || preferences.getString(HASH_KEY, null).isNullOrBlank()) {
            return false
        }
        return runCatching { loadKeyStore().containsAlias(KEY_ALIAS) }.getOrDefault(false)
    }

    fun savePin(pin: String) {
        require(PIN_PATTERN.matches(pin)) { "MPIN must contain exactly 6 digits" }
        val salt = ByteArray(SALT_LENGTH).also { java.security.SecureRandom().nextBytes(it) }
        val hash = calculateMac(pin, salt)
        val saved = preferences.edit()
            .putString(SALT_KEY, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(HASH_KEY, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(FAILED_ATTEMPTS_KEY, 0)
            .putLong(LOCKED_UNTIL_KEY, 0L)
            .commit()
        check(saved) { "Could not save the device MPIN" }
    }

    fun verifyPin(pin: String): DevicePinCheck {
        val now = System.currentTimeMillis()
        val lockedUntil = preferences.getLong(LOCKED_UNTIL_KEY, 0L)
        if (lockedUntil > now) {
            return DevicePinCheck.Locked(((lockedUntil - now + 999) / 1000).coerceAtLeast(1))
        }

        val salt = preferences.getString(SALT_KEY, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: return DevicePinCheck.Rejected(0)
        val expected = preferences.getString(HASH_KEY, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: return DevicePinCheck.Rejected(0)
        val matches = runCatching {
            PIN_PATTERN.matches(pin) && MessageDigest.isEqual(expected, calculateMac(pin, salt))
        }.getOrDefault(false)

        if (matches) {
            preferences.edit().putInt(FAILED_ATTEMPTS_KEY, 0).putLong(LOCKED_UNTIL_KEY, 0L).apply()
            return DevicePinCheck.Verified
        }

        val attempts = preferences.getInt(FAILED_ATTEMPTS_KEY, 0) + 1
        val editor = preferences.edit().putInt(FAILED_ATTEMPTS_KEY, attempts)
        if (attempts >= MAX_ATTEMPTS) {
            editor.putInt(FAILED_ATTEMPTS_KEY, 0).putLong(LOCKED_UNTIL_KEY, now + LOCKOUT_MILLIS)
        }
        editor.apply()
        return if (attempts >= MAX_ATTEMPTS) {
            DevicePinCheck.Locked(LOCKOUT_MILLIS / 1000)
        } else {
            DevicePinCheck.Rejected(MAX_ATTEMPTS - attempts)
        }
    }

    fun clear() {
        preferences.edit().clear().apply()
        runCatching { loadKeyStore().deleteEntry(KEY_ALIAS) }
    }

    private fun calculateMac(pin: String, salt: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(getOrCreateKey())
        mac.update(salt)
        return mac.doFinal(pin.toByteArray(Charsets.UTF_8))
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = loadKeyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return generator.generateKey()
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val HMAC_ALGORITHM = "HmacSHA256"
        const val KEY_ALIAS = "swiftpay.device.mpin.hmac"
        const val PREFERENCES_NAME = "device_pin"
        const val SALT_KEY = "salt"
        const val HASH_KEY = "pin_hmac"
        const val FAILED_ATTEMPTS_KEY = "failed_attempts"
        const val LOCKED_UNTIL_KEY = "locked_until"
        const val SALT_LENGTH = 16
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MILLIS = 30_000L
        val PIN_PATTERN = Regex("^\\d{6}$")
    }
}