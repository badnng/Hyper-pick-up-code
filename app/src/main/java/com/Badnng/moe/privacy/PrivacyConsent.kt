package com.Badnng.moe.privacy

import android.content.Context
import android.content.SharedPreferences

data class PrivacyPolicyDocument(
    val content: String,
    val isAvailable: Boolean,
)

object PrivacyConsent {
    const val ACCEPTED_KEY = "privacy_agreement_v1_accepted"
    const val NETWORK_UPDATE_ENABLED_KEY = "network_update_enabled_privacy_v1"

    private const val LEGACY_NETWORK_UPDATE_ENABLED_KEY = "network_update_enabled"
    private const val POLICY_ASSET_NAME = "PRIVACY.md"

    fun isAccepted(preferences: SharedPreferences): Boolean =
        preferences.getBoolean(ACCEPTED_KEY, false)

    fun accept(preferences: SharedPreferences) {
        preferences.edit().putBoolean(ACCEPTED_KEY, true).apply()
    }

    fun revoke(preferences: SharedPreferences) {
        preferences.edit()
            .putBoolean(ACCEPTED_KEY, false)
            .putBoolean(NETWORK_UPDATE_ENABLED_KEY, false)
            .apply()
    }

    fun isNetworkUpdateEnabled(preferences: SharedPreferences): Boolean =
        isAccepted(preferences) &&
            preferences.getBoolean(NETWORK_UPDATE_ENABLED_KEY, false)

    fun setNetworkUpdateEnabled(preferences: SharedPreferences, enabled: Boolean) {
        preferences.edit()
            .putBoolean(NETWORK_UPDATE_ENABLED_KEY, enabled && isAccepted(preferences))
            .apply()
    }

    fun discardLegacyNetworkUpdatePreference(preferences: SharedPreferences) {
        if (preferences.contains(LEGACY_NETWORK_UPDATE_ENABLED_KEY)) {
            preferences.edit().remove(LEGACY_NETWORK_UPDATE_ENABLED_KEY).apply()
        }
    }

    fun loadPolicy(context: Context): PrivacyPolicyDocument = runCatching {
        context.assets.open(POLICY_ASSET_NAME)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }.fold(
        onSuccess = { text ->
            PrivacyPolicyDocument(
                content = text.trim(),
                isAvailable = text.isNotBlank(),
            )
        },
        onFailure = {
            PrivacyPolicyDocument(
                content = "协议内容加载失败，暂时无法记录同意状态。请稍后重试。",
                isAvailable = false,
            )
        },
    )
}
