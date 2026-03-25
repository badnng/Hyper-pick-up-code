package com.Badnng.moe

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PermissionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val useShizuku = intent.getBooleanExtra("use_shizuku", false)
        val triggeredByAccessibilityShortcut = intent.getBooleanExtra("triggered_by_accessibility_shortcut", false)
        
        if (useShizuku) {
            // Shizuku 模式：由于此 Activity 已通过 startActivityAndCollapse 拉起
            // 系统控制中心已自动收起。现在我们直接启动后台服务并退出 Activity。
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("use_shizuku", true)
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
