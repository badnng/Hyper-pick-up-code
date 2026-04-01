package com.Badnng.moe

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import rikka.shizuku.Shizuku

object AccessibilityShortcutHelper {
    private const val TAG = "AccessibilityShortcut"
    private const val KEY_SHORTCUT_TARGET = "accessibility_shortcut_target_service"
    private const val KEY_SHORTCUT_TARGETS = "accessibility_shortcut_targets"
    private const val KEY_SHORTCUT_ENABLED = "accessibility_shortcut_enabled"
    private const val KEY_SHORTCUT_DIALOG_SHOWN = "accessibility_shortcut_dialog_shown"
    private const val KEY_SHORTCUT_ON_LOCK_SCREEN = "accessibility_shortcut_on_lock_screen"
    private const val SP_NAME = "accessibility_shortcut_backup"
    private const val FLAG_PREFIX = "exists_"
    private const val VALUE_PREFIX = "value_"
    private val BACKUP_KEYS = listOf(
        KEY_SHORTCUT_TARGET,
        KEY_SHORTCUT_TARGETS,
        KEY_SHORTCUT_ENABLED,
        KEY_SHORTCUT_DIALOG_SHOWN,
        KEY_SHORTCUT_ON_LOCK_SCREEN
    )

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
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_accessibility_services").orEmpty()
        return enabled.split(':').any { it == getServiceComponent(context) }
    }

    fun setProjectMediaAppOpsWithShizuku(context: Context, allow: Boolean): Boolean {
        if (!isShizukuReady()) return false
        val mode = if (allow) "allow" else "default"
        return try {
            val process = newProcess(
                arrayOf(
                    "cmd",
                    "appops",
                    "set",
                    "--user",
                    "0",
                    context.packageName,
                    "PROJECT_MEDIA",
                    mode
                )
            )
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "set PROJECT_MEDIA failed, mode=$mode", e)
            false
        }
    }

    fun configureShortcutWithShizuku(context: Context): Boolean {
        if (!isShizukuReady()) return false
        backupCurrentSettings(context, ::runSettingsGet)
        val component = getServiceComponent(context)

        val results = listOf(
            runSettingsPut(KEY_SHORTCUT_TARGET, component),
            runSettingsPut(KEY_SHORTCUT_TARGETS, component),
            runSettingsPut(KEY_SHORTCUT_ENABLED, "1"),
            runSettingsPut(KEY_SHORTCUT_DIALOG_SHOWN, "1"),
            runSettingsPut(KEY_SHORTCUT_ON_LOCK_SCREEN, "1")
        )
        return results.all { it }
    }

    fun configureShortcutWithRoot(context: Context): Boolean {
        if (!RootHelper.hasRootAccess()) return false
        backupCurrentSettings(context, ::runSettingsGetAsRoot)
        val component = getServiceComponent(context)

        val results = listOf(
            runSettingsPutAsRoot(KEY_SHORTCUT_TARGET, component),
            runSettingsPutAsRoot(KEY_SHORTCUT_TARGETS, component),
            runSettingsPutAsRoot(KEY_SHORTCUT_ENABLED, "1"),
            runSettingsPutAsRoot(KEY_SHORTCUT_DIALOG_SHOWN, "1"),
            runSettingsPutAsRoot(KEY_SHORTCUT_ON_LOCK_SCREEN, "1")
        )
        return results.all { it }
    }

    fun disableServiceWithShizuku(context: Context): Boolean {
        if (!isShizukuReady()) return false
        if (restoreBackedUpSettings(context, ::runSettingsPut, ::runSettingsDelete)) {
            return true
        }
        val results = listOf(
            runSettingsPut(KEY_SHORTCUT_TARGET, ""),
            runSettingsPut(KEY_SHORTCUT_TARGETS, ""),
            runSettingsPut(KEY_SHORTCUT_ENABLED, "0"),
            runSettingsPut(KEY_SHORTCUT_ON_LOCK_SCREEN, "0")
        )
        return results.all { it }
    }

    fun disableServiceWithRoot(context: Context): Boolean {
        if (!RootHelper.hasRootAccess()) return false
        if (restoreBackedUpSettings(context, ::runSettingsPutAsRoot, ::runSettingsDeleteAsRoot)) {
            return true
        }
        val results = listOf(
            runSettingsPutAsRoot(KEY_SHORTCUT_TARGET, ""),
            runSettingsPutAsRoot(KEY_SHORTCUT_TARGETS, ""),
            runSettingsPutAsRoot(KEY_SHORTCUT_ENABLED, "0"),
            runSettingsPutAsRoot(KEY_SHORTCUT_ON_LOCK_SCREEN, "0")
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
            if (output == "null") null else output
        } catch (e: Exception) {
            Log.e(TAG, "settings get failed: $key", e)
            null
        }
    }

    private fun runSettingsDelete(key: String): Boolean {
        return try {
            val process = newProcess(arrayOf("settings", "delete", "secure", key))
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "settings delete failed: $key", e)
            false
        }
    }

    private fun runSettingsPutAsRoot(key: String, value: String): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", "settings put secure $key \"$value\"").start()
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "root settings put failed: $key=$value", e)
            false
        }
    }

    private fun runSettingsGetAsRoot(key: String): String? {
        return try {
            val process = ProcessBuilder("su", "-c", "settings get secure $key").start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (output == "null") null else output
        } catch (e: Exception) {
            Log.e(TAG, "root settings get failed: $key", e)
            null
        }
    }

    private fun runSettingsDeleteAsRoot(key: String): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", "settings delete secure $key").start()
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "root settings delete failed: $key", e)
            false
        }
    }

    private fun backupCurrentSettings(context: Context, getter: (String) -> String?) {
        try {
            val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            val editor = sp.edit()
            BACKUP_KEYS.forEach { key ->
                val current = getter(key)
                if (current == null) {
                    editor.putBoolean("$FLAG_PREFIX$key", false)
                    editor.remove("$VALUE_PREFIX$key")
                } else {
                    editor.putBoolean("$FLAG_PREFIX$key", true)
                    editor.putString("$VALUE_PREFIX$key", current)
                }
            }
            editor.apply()
        } catch (e: Exception) {
            Log.e(TAG, "backup settings failed", e)
        }
    }

    private fun restoreBackedUpSettings(
        context: Context,
        putter: (String, String) -> Boolean,
        deleter: (String) -> Boolean
    ): Boolean {
        return try {
            val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            if (!BACKUP_KEYS.any { sp.contains("$FLAG_PREFIX$it") }) {
                return false
            }
            var ok = true
            BACKUP_KEYS.forEach { key ->
                val existed = sp.getBoolean("$FLAG_PREFIX$key", false)
                ok = ok && if (existed) {
                    val value = sp.getString("$VALUE_PREFIX$key", null)
                    value != null && putter(key, value)
                } else {
                    deleter(key)
                }
            }
            if (ok) {
                val editor = sp.edit()
                BACKUP_KEYS.forEach { key ->
                    editor.remove("$FLAG_PREFIX$key")
                    editor.remove("$VALUE_PREFIX$key")
                }
                editor.apply()
            }
            ok
        } catch (e: Exception) {
            Log.e(TAG, "restore settings failed", e)
            false
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
