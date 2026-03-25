package com.Badnng.moe

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class VolumeShortcutAccessibilityService : AccessibilityService() {
    private val tag = "VolumeShortcutService"

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()

        val connectedIntent = Intent("com.Badnng.moe.ACCESSIBILITY_SERVICE_CONNECTED")
        LocalBroadcastManager.getInstance(this).sendBroadcast(connectedIntent)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (!prefs.getBoolean("volume_key_shortcut_enabled", false)) {
            Log.d(tag, "Shortcut disabled in settings, ignore trigger")
            return
        }

        // Expected flow:
        // 1) Service is triggered by accessibility shortcut.
        // 2) Disable accessibility service first.
        // 3) Trigger screenshot recognition.
        // Use local disable first to avoid blocking this trigger path.
        disableSelf()

        val useShizuku = prefs.getString("capture_mode", "media_projection") == "shizuku" &&
            AccessibilityShortcutHelper.isShizukuReady()

        // Run secure-settings cleanup in background, do not block recognition startup.
        Thread {
            val cleaned = AccessibilityShortcutHelper.disableServiceWithShizuku(this)
            Log.d(tag, "async disableServiceWithShizuku result=$cleaned")
        }.start()

        if (useShizuku) {
            Log.d(tag, "Trigger ScreenCaptureService with Shizuku")
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("use_shizuku", true)
                putExtra("triggered_by_accessibility_shortcut", true)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            return
        }

        Log.d(tag, "Trigger PermissionActivity for MediaProjection flow")
        val permissionIntent = Intent(this, PermissionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("use_shizuku", false)
            putExtra("triggered_by_accessibility_shortcut", true)
        }
        startActivity(permissionIntent)
    }

    override fun onDestroy() {
        val destroyedIntent = Intent("com.Badnng.moe.ACCESSIBILITY_SERVICE_DESTROYED")
        LocalBroadcastManager.getInstance(this).sendBroadcast(destroyedIntent)
        super.onDestroy()
    }
}
