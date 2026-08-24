package com.Badnng.moe.privacy

import android.content.Context
import android.content.SharedPreferences

data class PrivacyPolicyDocument(
    val content: String,
    val isAvailable: Boolean,
)

object PrivacyConsent {
    const val ACCEPTED_KEY = "privacy_agreement_v1_accepted"
    const val ACCEPTED_VERSION_KEY = "privacy_agreement_v1_accepted_version"
    const val NETWORK_UPDATE_ENABLED_KEY = "network_update_enabled_privacy_v1"
    const val POLICY_VERSION = "2.0"

    private const val LEGACY_NETWORK_UPDATE_ENABLED_KEY = "network_update_enabled"
    private const val POLICY_ASSET_NAME = "PRIVACY.md"

    fun isAccepted(preferences: SharedPreferences): Boolean =
        preferences.getBoolean(ACCEPTED_KEY, false)

    fun accept(preferences: SharedPreferences) {
        preferences.edit()
            .putBoolean(ACCEPTED_KEY, true)
            .putString(ACCEPTED_VERSION_KEY, POLICY_VERSION)
            .apply()
    }

    fun revoke(preferences: SharedPreferences) {
        preferences.edit()
            .putBoolean(ACCEPTED_KEY, false)
            .putBoolean(NETWORK_UPDATE_ENABLED_KEY, false)
            .remove(ACCEPTED_VERSION_KEY)
            .apply()
    }

    fun acknowledgedPolicyVersion(preferences: SharedPreferences): String? =
        preferences.getString(ACCEPTED_VERSION_KEY, null)

    fun hasPolicyUpdate(preferences: SharedPreferences): Boolean =
        isAccepted(preferences) && acknowledgedPolicyVersion(preferences) != POLICY_VERSION

    /**
     * 是否已同意且确认的版本与当前政策版本一致。
     * 政策更新（版本落后）后必须重新同意，否则视为未同意。
     */
    fun isCurrentPolicyAccepted(preferences: SharedPreferences): Boolean =
        isAccepted(preferences) && acknowledgedPolicyVersion(preferences) == POLICY_VERSION

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
