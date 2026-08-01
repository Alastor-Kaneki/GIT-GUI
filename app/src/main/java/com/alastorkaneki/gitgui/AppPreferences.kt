package com.alastorkaneki.gitgui

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("git_gui_preferences", Context.MODE_PRIVATE)
    private val alias = "git_gui_token_key"

    var clientId: String
        get() = preferences.getString("client_id", "") ?: ""
        set(value) = preferences.edit().putString("client_id", value.trim()).apply()

    var gitName: String
        get() = preferences.getString("git_name", "Alastor Kaneki") ?: "Alastor Kaneki"
        set(value) = preferences.edit().putString("git_name", value.trim()).apply()

    var gitEmail: String
        get() = preferences.getString("git_email", "") ?: ""
        set(value) = preferences.edit().putString("git_email", value.trim()).apply()

    var rainbowEnabled: Boolean
        get() = preferences.getBoolean("rainbow_enabled", true)
        set(value) = preferences.edit().putBoolean("rainbow_enabled", value).apply()

    var rainbowReverse: Boolean
        get() = preferences.getBoolean("rainbow_reverse", false)
        set(value) = preferences.edit().putBoolean("rainbow_reverse", value).apply()

    var rainbowSpeed: Float
        get() = preferences.getFloat("rainbow_speed", 1f)
        set(value) = preferences.edit().putFloat("rainbow_speed", value).apply()

    fun saveToken(token: String?) {
        if (token.isNullOrBlank()) {
            preferences.edit().remove("github_token").apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(token.toByteArray())
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        preferences.edit().putString("github_token", value).apply()
    }

    fun token(): String? = runCatching {
        val stored = preferences.getString("github_token", null) ?: return null
        val parts = stored.split(':', limit = 2)
        if (parts.size != 2) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted))
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
