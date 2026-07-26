package com.Badnng.moe.helper

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.Badnng.moe.privacy.PrivacyConsent
import com.Badnng.moe.recognition.OnlineRecognitionPreferences
import com.Badnng.moe.ocr.OcrDiagnosticsPreferences
import com.Badnng.moe.service.NotificationListenerRecognitionService
import com.Badnng.moe.service.VolumeShortcutAccessibilityService
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File

data class BackupOptions(
    val includeOrders: Boolean = true,
    val includeSettings: Boolean = true,
    val includeRules: Boolean = true,
    val includeScreenshots: Boolean = false,
    val includeApiKeys: Boolean = false,
)

enum class RestoreOrderPolicy {
    MERGE_KEEP_LOCAL,
    REPLACE_ALL,
}

data class RestoreSelection(
    val restoreOrders: Boolean,
    val restoreSettings: Boolean,
    val restoreRules: Boolean,
    val restoreScreenshots: Boolean,
    val restoreApiKeys: Boolean,
    val orderPolicy: RestoreOrderPolicy = RestoreOrderPolicy.MERGE_KEEP_LOCAL,
)

data class BackupPreview(
    val formatVersion: Int,
    val appVersion: String,
    val createdAt: Long,
    val orderCount: Int,
    val groupCount: Int,
    val settingsCount: Int,
    val ruleFileCount: Int,
    val screenshotCount: Int,
    val hasApiKeys: Boolean,
    val isLegacy: Boolean,
    val archiveSizeBytes: Long,
    val warnings: List<String>,
    val conflictingOrderCount: Int = 0,
    val compatibilityAdjustments: List<String> = emptyList(),
)

data class RestoreReport(
    val insertedOrders: Int,
    val overwrittenOrders: Int,
    val skippedOrders: Int,
    val restoredGroups: Int,
    val restoredSettings: Int,
    val restoredRuleFiles: Int,
    val restoredScreenshots: Int,
    val restoredApiKeys: Int,
    val adjustments: List<String>,
    val failedItems: Int = 0,
) {
    fun summary(): String = buildString {
        append("恢复完成：新增 $insertedOrders 条订单")
        if (overwrittenOrders > 0) append("，覆盖 $overwrittenOrders 条")
        if (skippedOrders > 0) append("，跳过 $skippedOrders 条")
        if (restoredGroups > 0) append("，恢复 $restoredGroups 个组")
        if (adjustments.isNotEmpty()) append("，降级 ${adjustments.size} 项")
        append("，失败 $failedItems 项")
    }
}

class StagedBackup internal constructor(
    internal val archiveFile: File,
    internal val payload: BackupPayload,
    val preview: BackupPreview,
) : Closeable {
    override fun close() {
        archiveFile.delete()
    }
}

internal data class BackupPayload(
    val formatVersion: Int,
    val orders: List<BackupOrder>,
    val groups: List<BackupGroup>,
    val settings: Map<String, Any?>,
    val ruleEntries: Map<String, String>,
    val screenshotEntries: Set<String>,
    val hasEncryptedSecrets: Boolean,
)

internal data class BackupOrder(
    val order: com.Badnng.moe.data.db.OrderEntity,
    val screenshotEntry: String?,
)

internal data class BackupGroup(
    val group: com.Badnng.moe.data.db.OrderGroup,
    val screenshotEntry: String?,
)

internal data class NormalizedSettings(
    val values: Map<String, Any?>,
    val adjustments: List<String>,
)

internal object BackupSettingsPolicy {
    private val portableKeys = setOf(
        "ui_style",
        "theme_mode",
        "monet_enabled",
        "miuix_monet_enabled",
        "amoled_pure_black",
        "key_color_index",
        "theme_color",
        "use_floating_nav_bar",
        "miuix_floating_nav_bar_style",
        "large_screen_nav_adaptive_enabled",
        "nav_alignment",
        "haptic_enabled",
        "predictive_back_enabled",
        "auto_group_enabled",
        "sms_recognition_enabled",
        "notification_listener_recognition_enabled",
        "notification_listener_apps",
        "notification_type",
        "persistent_notification_enabled",
        "capture_mode",
        "media_projection_no_prompt_enabled",
        "volume_key_shortcut_enabled",
        "update_channel",
        OnlineRecognitionPreferences.MODE_KEY,
        OnlineRecognitionPreferences.PROVIDER_KEY,
        OnlineRecognitionPreferences.MIMO_BILLING_KEY,
        OnlineRecognitionPreferences.CUSTOM_REQUEST_MODE_KEY,
        OnlineRecognitionPreferences.CUSTOM_BASE_URL_KEY,
        OnlineRecognitionPreferences.CUSTOM_PROMPT_KEY,
        "recognition_blocked_words",
        "custom_pickup_locations",
        OcrDiagnosticsPreferences.DETAILS_ENABLED_KEY,
    )
    private val portablePrefixes = listOf("online_recognition_model_")

    fun collect(preferences: SharedPreferences): Map<String, Any?> =
        preferences.all.filterKeys(::isPortableKey).filterValues(::isSupportedValue)

    fun isPortableKey(key: String): Boolean =
        key in portableKeys || portablePrefixes.any { prefix -> key.startsWith(prefix) }

    fun normalize(context: Context, source: Map<String, Any?>): NormalizedSettings {
        val values = source.filterKeys(::isPortableKey).filterValues(::isSupportedValue).toMutableMap()
        val adjustments = mutableListOf<String>()

        val captureMode = values["capture_mode"] as? String
        val needsShizukuCheck = captureMode == "root" || captureMode == "shizuku" ||
            values["media_projection_no_prompt_enabled"] == true
        val shizukuReady = needsShizukuCheck && ShizukuScreenshotHelper().isShizukuAvailable()
        val rootReady = captureMode == "root" && RootHelper.isSuAvailable()
        when (captureMode) {
            "root" -> if (!rootReady) {
                values["capture_mode"] = if (shizukuReady) "shizuku" else "media_projection"
                adjustments += "Root 截图不可用，已降级为${if (shizukuReady) " Shizuku" else " MediaProjection"}"
            }
            "shizuku" -> if (!shizukuReady) {
                values["capture_mode"] = "media_projection"
                adjustments += "Shizuku 截图不可用，已降级为 MediaProjection"
            }
        }

        if (values["media_projection_no_prompt_enabled"] == true && !shizukuReady) {
            values["media_projection_no_prompt_enabled"] = false
            adjustments += "无提示 MediaProjection 需要 Shizuku，已关闭"
        }
        if (values["volume_key_shortcut_enabled"] == true && !isAccessibilityEnabled(context)) {
            values["volume_key_shortcut_enabled"] = false
            adjustments += "音量键快捷方式缺少无障碍授权，已关闭"
        }
        if (values["sms_recognition_enabled"] == true && !hasSmsPermissions(context)) {
            values["sms_recognition_enabled"] = false
            adjustments += "短信权限不可用，短信识别已关闭"
        }
        if (values["notification_listener_recognition_enabled"] == true &&
            !NotificationListenerRecognitionService.isNotificationListenerEnabled(context)
        ) {
            values["notification_listener_recognition_enabled"] = false
            adjustments += "通知使用权不可用，通知识别已关闭"
        }
        if (values["persistent_notification_enabled"] == true && !hasNotificationPermission(context)) {
            values["persistent_notification_enabled"] = false
            adjustments += "通知权限不可用，后台常驻通知已关闭"
        }
        if (values["notification_type"] == "island" && !SuperIslandHelper.isDeviceSupported(context)) {
            values["notification_type"] = "native"
            adjustments += "当前设备不支持超级岛通知，已改为原生通知"
        }

        if (values[OnlineRecognitionPreferences.MODE_KEY] == OnlineRecognitionPreferences.MODE_ONLINE) {
            values[OnlineRecognitionPreferences.MODE_KEY] = OnlineRecognitionPreferences.MODE_OFFLINE
            adjustments += "在线识别需重新同意隐私政策，已暂时切换为离线识别"
        }
        values[PrivacyConsent.ACCEPTED_KEY] = false
        values[PrivacyConsent.NETWORK_UPDATE_ENABLED_KEY] = false
        values["network_update_enabled"] = false
        return NormalizedSettings(values, adjustments)
    }

    fun encode(values: Map<String, Any?>): JSONObject = JSONObject().apply {
        values.forEach { (key, value) ->
            put(key, JSONObject().apply {
                when (value) {
                    is Boolean -> { put("type", "boolean"); put("value", value) }
                    is Int -> { put("type", "int"); put("value", value) }
                    is Long -> { put("type", "long"); put("value", value) }
                    is Float -> { put("type", "float"); put("value", value.toDouble()) }
                    is String -> { put("type", "string"); put("value", value) }
                    is Set<*> -> {
                        put("type", "string_set")
                        put("value", JSONArray(value.filterIsInstance<String>()))
                    }
                }
            })
        }
    }

    fun decode(json: JSONObject): Map<String, Any?> = buildMap {
        json.keys().forEach { key ->
            if (!isPortableKey(key)) return@forEach
            val entry = json.optJSONObject(key) ?: return@forEach
            when (entry.optString("type")) {
                "boolean" -> put(key, entry.optBoolean("value"))
                "int" -> put(key, entry.optInt("value"))
                "long" -> put(key, entry.optLong("value"))
                "float" -> put(key, entry.optDouble("value").toFloat())
                "string" -> put(key, entry.optString("value"))
                "string_set" -> put(key, entry.optJSONArray("value")?.let { array ->
                    buildSet { for (index in 0 until array.length()) add(array.optString(index)) }
                } ?: emptySet<String>())
            }
        }
    }

    fun decodeLegacy(json: JSONObject): Map<String, Any?> = buildMap {
        json.keys().forEach { key ->
            val value = json.opt(key)
            if (value != null && value != JSONObject.NULL && isPortableKey(key)) {
                put(key, when (value) {
                    is Double -> value.toFloat()
                    is JSONArray -> buildSet {
                        for (index in 0 until value.length()) {
                            (value.opt(index) as? String)?.let(::add)
                        }
                    }
                    else -> value
                })
            }
        }
    }

    fun write(editor: SharedPreferences.Editor, values: Map<String, Any?>) {
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
    }

    private fun hasSmsPermissions(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val component = ComponentName(context, VolumeShortcutAccessibilityService::class.java).flattenToString()
        return enabled.split(':').any { it.equals(component, ignoreCase = true) }
    }

    private fun isSupportedValue(value: Any?): Boolean =
        value is Boolean || value is Int || value is Long || value is Float ||
            value is String || (value is Set<*> && value.all { it is String })
}
