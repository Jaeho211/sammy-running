package com.sammy.running

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.sammy.running.core.GitHubConfig
import com.sammy.running.core.GitHubDeviceAuthClient
import com.sammy.running.core.DeviceAuthorization
import com.sammy.running.core.OAuthTokens
import com.sammy.running.core.PublishedRunIdentity
import com.sammy.running.core.RawRun
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.Base64

class GitHubSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("github_settings", Context.MODE_PRIVATE)
    private val alias = "run_log_github_token"

    data class Credentials(
        val accessToken: String,
        val accessExpiresAt: Long?,
        val refreshToken: String?,
        val refreshExpiresAt: Long?,
    )

    fun credentials(): Credentials? {
        val access = preferences.getString("oauth_access", null) ?: return null
        return Credentials(
            decrypt(access),
            preferences.getLong("oauth_access_expires", 0L).takeIf { it > 0 },
            preferences.getString("oauth_refresh", null)?.let(::decrypt),
            preferences.getLong("oauth_refresh_expires", 0L).takeIf { it > 0 },
        )
    }

    fun savePendingAuthorization(value: DeviceAuthorization) {
        preferences.edit()
            .putString("pending_device", encrypt(value.deviceCode))
            .putString("pending_user", value.userCode)
            .putString("pending_uri", value.verificationUri)
            .putLong("pending_expires", System.currentTimeMillis() + value.expiresInSeconds * 1000)
            .putLong("pending_interval", value.intervalSeconds)
            .apply()
    }

    fun pendingAuthorization(): DeviceAuthorization? {
        val expiresAt = preferences.getLong("pending_expires", 0L)
        if (expiresAt <= System.currentTimeMillis()) { clearPendingAuthorization(); return null }
        val encryptedDevice = preferences.getString("pending_device", null) ?: return null
        val userCode = preferences.getString("pending_user", null) ?: return null
        val uri = preferences.getString("pending_uri", null) ?: return null
        return DeviceAuthorization(
            decrypt(encryptedDevice), userCode, uri,
            ((expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(1),
            preferences.getLong("pending_interval", 5L).coerceAtLeast(1),
        )
    }

    fun clearPendingAuthorization() {
        preferences.edit().remove("pending_device").remove("pending_user").remove("pending_uri")
            .remove("pending_expires").remove("pending_interval").apply()
    }

    fun saveTokens(tokens: OAuthTokens) {
        val now = System.currentTimeMillis()
        val previous = credentials()
        val refresh = tokens.refreshToken ?: previous?.refreshToken
        val refreshExpiry = tokens.refreshTokenExpiresInSeconds?.let { now + it * 1000 } ?: previous?.refreshExpiresAt
        val editor = preferences.edit()
            .putString("oauth_access", encrypt(tokens.accessToken))
            .putLong("oauth_access_expires", tokens.expiresInSeconds?.let { now + it * 1000 } ?: 0L)
            .remove("token")
        if (refresh != null) editor.putString("oauth_refresh", encrypt(refresh)) else editor.remove("oauth_refresh")
        editor.putLong("oauth_refresh_expires", refreshExpiry ?: 0L).apply()
    }

    fun configWithValidToken(auth: GitHubDeviceAuthClient, clientId: String): GitHubConfig {
        val saved = credentials() ?: error("GitHub 로그인이 필요합니다.")
        val now = System.currentTimeMillis()
        if (saved.accessExpiresAt == null || saved.accessExpiresAt > now + 60_000) return config(saved.accessToken)
        val refresh = saved.refreshToken?.takeIf { saved.refreshExpiresAt == null || saved.refreshExpiresAt > now }
            ?: error("GitHub 로그인이 만료되었습니다. 다시 로그인해 주세요.")
        val updated = auth.refresh(clientId, refresh)
        saveTokens(updated)
        return config(updated.accessToken)
    }

    fun hasCredentials() = preferences.contains("oauth_access")
    fun clearCredentials() {
        preferences.edit().remove("oauth_access").remove("oauth_access_expires")
            .remove("oauth_refresh").remove("oauth_refresh_expires").remove("token").apply()
        clearPendingAuthorization()
    }

    private fun config(token: String) = GitHubConfig("Jaeho211", "sammy-running", "main", token)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.getEncoder().encodeToString(cipher.iv) + ":" +
            Base64.getEncoder().encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
    }

    private fun decrypt(value: String): String {
        val parts = value.split(":", limit = 2)
        require(parts.size == 2) { "저장된 token을 읽을 수 없습니다." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])))
        }
        return String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), Charsets.UTF_8)
    }
}

class PublishedRunStore(context: Context) {
    private val preferences = context.getSharedPreferences("published_runs", Context.MODE_PRIVATE)
    fun contains(run: RawRun) = preferences.contains(PublishedRunIdentity.key(run))
    fun mark(run: RawRun, path: String) { preferences.edit().putString(PublishedRunIdentity.key(run), path).apply() }
}
