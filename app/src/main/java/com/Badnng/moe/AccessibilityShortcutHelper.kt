package com.Badnng.moe

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import rikka.shizuku.Shizuku

object AccessibilityShortcutHelper {
    private const val TAG = "AccessibilityShortcut"
    private const val KEY_ENABLED_SERVICES = "enabled_accessibility_services"
    private const val KEY_ACCESSIBILITY_ENABLED = "accessibility_enabled"
    private const val KEY_SHORTCUT_TARGET = "accessibility_shortcut_target_service"
    private const val KEY_SHORTCUT_ENABLED = "accessibility_shortcut_enabled"
    private const val KEY_SHORTCUT_DIALOG_SHOWN = "accessibility_shortcut_dialog_shown"

    fun getServiceComponent(context: Context): String {
        return ComponentName(context, VolumeShortcutAccessibilityService::class.java).flattenToString()
    }

    fun isShizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    fun isServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, KEY_ENABLED_SERVICES).orEmpty()
        return enabled.split(':').any { it == getServiceComponent(context) }
    }

    fun configureShortcutWithShizuku(context: Context): Boolean {
        if (!isShizukuReady()) return false
        val component = getServiceComponent(context)
        val existing = runSettingsGet(KEY_ENABLED_SERVICES).orEmpty()
        val merged = existing.split(':').filter { it.isNotBlank() }.toMutableSet().apply { add(component) }.joinToString(":")

        val results = listOf(
            runSettingsPut(KEY_ENABLED_SERVICES, merged),
            runSettingsPut(KEY_ACCESSIBILITY_ENABLED, "1"),
            runSettingsPut(KEY_SHORTCUT_TARGET, component),
            runSettingsPut(KEY_SHORTCUT_ENABLED, "1"),
            runSettingsPut(KEY_SHORTCUT_DIALOG_SHOWN, "1")
        )
        return results.all { it }
    }

    fun disableServiceWithShizuku(context: Context): Boolean {
        if (!isShizukuReady()) return false
        val component = getServiceComponent(context)
        val existing = runSettingsGet(KEY_ENABLED_SERVICES).orEmpty()
        val filtered = existing.split(':').filter { it.isNotBlank() && it != component }.joinToString(":")
        val results = listOf(
            runSettingsPut(KEY_ENABLED_SERVICES, filtered),
            runSettingsPut(KEY_ACCESSIBILITY_ENABLED, if (filtered.isBlank()) "0" else "1")
        )
        return results.all { it }
    }

    private fun runSettingsPut(key: String, value: String): Boolean {
        return try {
            val process = newProcess(arrayOf("settings", "put", "secure", key, value))
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "settings put failed: $key=$value", e)
            false
        }
    }

    private fun runSettingsGet(key: String): String? {
        return try {
            val process = newProcess(arrayOf("settings", "get", "secure", key))
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            output
        } catch (e: Exception) {
            Log.e(TAG, "settings get failed: $key", e)
            null
        }
    }

    private fun newProcess(command: Array<String>): rikka.shizuku.ShizukuRemoteProcess {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(null, command, null, null) as rikka.shizuku.ShizukuRemoteProcess
    }
}
