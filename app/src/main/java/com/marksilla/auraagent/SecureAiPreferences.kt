package com.marksilla.auraagent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureAiPreferences(
    context: Context
) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getApiKey(): String? {
        val encrypted = preferences.getString(KEY_ENCRYPTED, null) ?: return null
        val iv = preferences.getString(KEY_IV, null) ?: return null

        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getSecretKey(),
                GCMParameterSpec(TAG_LENGTH_BITS, Base64.decode(iv, Base64.DEFAULT))
            )
            cipher.doFinal(Base64.decode(encrypted, Base64.DEFAULT))
                .toString(Charsets.UTF_8)
                .ifBlank { null }
        }.getOrNull()
    }

    fun saveApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isBlank()) {
            clearApiKey()
            return
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val encrypted = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))

        preferences.edit()
            .putString(KEY_ENCRYPTED, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun clearApiKey() {
        preferences.edit()
            .remove(KEY_ENCRYPTED)
            .remove(KEY_IV)
            .apply()
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) {
            return existing
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "aura_online_ai_key"
        private const val PREFERENCES_NAME = "aura_secure_ai_preferences"
        private const val KEY_ENCRYPTED = "encrypted_api_key"
        private const val KEY_IV = "api_key_iv"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BITS = 128
    }
}
