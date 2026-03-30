package com.Badnng.moe

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

class VolumeShortcutAccessibilityService : AccessibilityService() {
    private val tag = "VolumeShortcutService"

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (prefs.getBoolean("skip_next_accessibility_connect", false)) {
            prefs.edit().putBoolean("skip_next_accessibility_connect", false).apply()
            Log.d(tag, "Skip programmatic accessibility connect")
            disableSelf()
            return
        }
        if (!prefs.getBoolean("volume_key_shortcut_enabled", false)) {
            Log.d(tag, "Shortcut disabled, ignoring trigger")
            disableSelf()
            return
        }

        disableSelf()

        val captureMode = prefs.getString("capture_mode", "media_projection")
        val useShizuku = captureMode == "shizuku" && AccessibilityShortcutHelper.isShizukuReady()
        val useRoot = captureMode == "root" && RootHelper.hasRootAccess()

        // 启动截图流程
        val intent = if (useShizuku || useRoot) {
            Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("use_shizuku", useShizuku)
                putExtra("use_root", useRoot)
                putExtra("triggered_by_accessibility_shortcut", true)
            }
        } else {
            Intent(this, PermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("triggered_by_accessibility_shortcut", true)
            }
        }

        try {
            if (useShizuku || useRoot) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch capture flow", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
