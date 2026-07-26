package com.Badnng.moe.recognition

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureApiKeyStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun save(provider: OnlineRecognitionProvider, apiKey: String) {
        val normalized = apiKey.trim()
        if (normalized.isEmpty()) {
            preferences.edit().remove(provider.key).apply()
            return
        }

        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        }
        val encrypted = cipher.doFinal(normalized.toByteArray(Charsets.UTF_8))
        val value = listOf(
            FORMAT_VERSION,
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(encrypted, Base64.NO_WRAP),
        ).joinToString(":")
        preferences.edit().putString(provider.key, value).apply()
    }

    fun get(provider: OnlineRecognitionProvider): String? {
        val stored = preferences.getString(provider.key, null) ?: return null
        return runCatching {
            val parts = stored.split(':', limit = 3)
            require(parts.size == 3 && parts[0] == FORMAT_VERSION)
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[2], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateSecretKey(),
                    GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
                )
            }
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrElse {
            preferences.edit().remove(provider.key).apply()
            null
        }
    }

    fun has(provider: OnlineRecognitionProvider): Boolean = !get(provider).isNullOrBlank()

    fun exportApiKeys(): Map<String, String> = buildMap<String, String> {
        OnlineRecognitionProvider.entries.forEach { provider ->
            this@SecureApiKeyStore.get(provider)
                ?.takeIf { it.isNotBlank() }
                ?.let { put(provider.key, it) }
        }
    }

    fun replaceApiKeys(values: Map<String, String>) {
        OnlineRecognitionProvider.entries.forEach { provider ->
            save(provider, values[provider.key].orEmpty())
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "online_recognition_secrets"
        const val KEY_ALIAS = "hyper_note_online_recognition_api_keys"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val FORMAT_VERSION = "v1"
    }
}
