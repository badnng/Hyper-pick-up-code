package com.Badnng.moe.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.Badnng.moe.helper.AccessibilityShortcutHelper
import com.Badnng.moe.helper.EdgeToEdgeHelper
import com.Badnng.moe.service.ScreenCaptureService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PermissionActivity : ComponentActivity() {
    companion object {
        private const val ACTION_CAPTURE = "com.Badnng.moe.action.CAPTURE_SCREEN"

        fun createCaptureIntent(context: Context): Intent =
            Intent(context, PermissionActivity::class.java).apply {
                action = ACTION_CAPTURE
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.applyGestureEdgeToEdge(this)
        
        // 磁贴与 App 长按快捷操作在点击时读取设置，避免快捷方式保存过期的截图模式。
        val useCurrentCaptureMode = intent.action == ACTION_CAPTURE
        val captureMode = if (useCurrentCaptureMode) {
            getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString("capture_mode", "media_projection")
        } else {
            null
        }
        val useShizuku = if (useCurrentCaptureMode) {
            captureMode == "shizuku" && AccessibilityShortcutHelper.isShizukuReady()
        } else {
            intent.getBooleanExtra("use_shizuku", false)
        }
        val useRoot = if (useCurrentCaptureMode) {
            captureMode == "root"
        } else {
            intent.getBooleanExtra("use_root", false)
        }
        val triggeredByAccessibilityShortcut = intent.getBooleanExtra("triggered_by_accessibility_shortcut", false)
        
        if (useShizuku || useRoot) {
            // Shizuku / Root 模式复用同一后台服务，退出透明 Activity 后截图。
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("use_shizuku", useShizuku)
                putExtra("use_root", useRoot)
                putExtra("triggered_by_accessibility_shortcut", triggeredByAccessibilityShortcut)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            finish()
            overridePendingTransition(0, 0)
        } else {
            // 共享屏幕模式：正常走授权流程
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                        putExtra("resultCode", result.resultCode)
                        putExtra("data", result.data)
                        putExtra("triggered_by_accessibility_shortcut", triggeredByAccessibilityShortcut)
                    }
                    ContextCompat.startForegroundService(this, serviceIntent)
                }
                finish()
                overridePendingTransition(0, 0)
            }
            
            lifecycleScope.launch {
                delay(200)
                try {
                    captureLauncher.launch(projectionManager.createScreenCaptureIntent())
                } catch (e: Exception) {
                    finish()
                }
            }
        }
    }
}
