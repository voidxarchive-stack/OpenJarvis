package com.openjarvis.mobile

import android.content.Context
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/** Encrypts provider credentials with a non-exportable Android Keystore AES key. */
class SecureKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences("openjarvis_connection", Context.MODE_PRIVATE)

    fun saveApiKey(apiKey: String) {
        require(apiKey.isNotBlank()) { "API key cannot be blank" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        val payload = ByteBuffer.allocate(4 + cipher.iv.size + encrypted.size)
            .putInt(cipher.iv.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
        preferences.edit().putString(KEY_API, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    fun loadApiKey(): String? {
        val encoded = preferences.getString(KEY_API, null) ?: return null
        return try {
            val payload = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP))
            val ivSize = payload.int
            require(ivSize in 12..16 && payload.remaining() > ivSize) { "Invalid encrypted key payload" }
            val iv = ByteArray(ivSize).also(payload::get)
            val encrypted = ByteArray(payload.remaining()).also(payload::get)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun saveWakeWordAccessKey(accessKey: String) {
        require(accessKey.isNotBlank()) { "Wake-word access key cannot be blank" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(accessKey.toByteArray(Charsets.UTF_8))
        val payload = ByteBuffer.allocate(4 + cipher.iv.size + encrypted.size)
            .putInt(cipher.iv.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
        preferences.edit()
            .putString(KEY_WAKE_WORD, Base64.encodeToString(payload, Base64.NO_WRAP))
            .apply()
    }

    fun loadWakeWordAccessKey(): String? {
        val encoded = preferences.getString(KEY_WAKE_WORD, null) ?: return null
        return try {
            val payload = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP))
            val ivSize = payload.int
            require(ivSize in 12..16 && payload.remaining() > ivSize) { "Invalid encrypted key payload" }
            val iv = ByteArray(ivSize).also(payload::get)
            val encrypted = ByteArray(payload.remaining()).also(payload::get)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            preferences.edit().remove(KEY_WAKE_WORD).apply()
            null
        }
    }

    fun clear() {
        preferences.edit().remove(KEY_API).apply()
    }

    fun clearWakeWordAccessKey() {
        preferences.edit().remove(KEY_WAKE_WORD).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "openjarvis_server_api_key_v1"
        const val KEY_API = "encrypted_api_key"
        const val KEY_WAKE_WORD = "encrypted_wake_word_access_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
