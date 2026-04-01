package com.Badnng.moe

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.Badnng.moe.ui.theme.澎湃记Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    var intentToProcess by mutableStateOf<Intent?>(null)
    private lateinit var projectionManager: MediaProjectionManager
    private var isFromNotification = false
    private lateinit var settingsPrefs: SharedPreferences
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "theme_mode" || key == "amoled_pure_black") {
            EdgeToEdgeHelper.applyGestureEdgeToEdge(this)
        }
    }

    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            lifecycleScope.launch {
                delay(500)
                moveTaskToBack(true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.applyGestureEdgeToEdge(this)
        settingsPrefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        settingsPrefs.registerOnSharedPreferenceChangeListener(settingsListener)

        LogManager.startCollecting()

        // 检查是否从通知进入
        isFromNotification = intent?.getBooleanExtra("from_notification", false) == true

        // 异步初始化 PaddleOCR（只需一次）
        lifecycleScope.launch {
            PaddleOcrHelper.getInstance(applicationContext).initAsync()
        }

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        intentToProcess = intent

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            澎湃记Theme {
                val prefs = remember { getSharedPreferences("settings", Context.MODE_PRIVATE) }
                var showOnboarding by remember { 
                    mutableStateOf(
                        !prefs.getBoolean("onboarding_completed", false) || 
                        prefs.getBoolean("show_onboarding_on_next_launch", false)
                    )
                }

                AnimatedContent(
                    targetState = showOnboarding,
                    label = "onboarding_transition",
                    transitionSpec = {
                        fadeIn(animationSpec = tween(500)) togetherWith
                                fadeOut(animationSpec = tween(500))
                    }
                ) { isOnboarding ->
                    if (isOnboarding) {
                        OnboardingScreen(
                            onComplete = { 
                                showOnboarding = false
                                // 完成引导后，自动关闭"下次启动时打开引导页面"开关
                                prefs.edit().putBoolean("show_onboarding_on_next_launch", false).apply()
                            }
                        )
                    } else {
                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            HomeScreen(intentToProcess = intentToProcess)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentToProcess = intent
        // 检查是否从通知进入
        isFromNotification = intent?.getBooleanExtra("from_notification", false) == true
    }

    override fun onResume() {
        super.onResume()
        EdgeToEdgeHelper.applyGestureEdgeToEdge(this)
    }

    override fun onDestroy() {
        if (::settingsPrefs.isInitialized) {
            settingsPrefs.unregisterOnSharedPreferenceChangeListener(settingsListener)
        }
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // 从通知进入后，按 Home 键离开时从最近任务移除
        if (isFromNotification) {
            finishAndRemoveTask()
        }
    }

    fun isFromNotification(): Boolean = isFromNotification
}
