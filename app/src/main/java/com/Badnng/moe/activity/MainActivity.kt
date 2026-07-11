package com.Badnng.moe.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.lifecycleScope
import com.Badnng.moe.R
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.helper.DailyExpressGroupingHelper
import com.Badnng.moe.helper.EdgeToEdgeHelper
import com.Badnng.moe.helper.StorageCleanupHelper
import com.Badnng.moe.ocr.PaddleOcrHelper
import com.Badnng.moe.rules.RecognitionRuleEngine
import com.Badnng.moe.service.ScreenCaptureService
import com.Badnng.moe.ui.screen.HomeScreen
import com.Badnng.moe.ui.screen.OnboardingScreen
import com.Badnng.moe.ui.oobe.OOBE_HOME_ENTER_MILLIS
import com.Badnng.moe.ui.oobe.OobeActivityTransition
import com.Badnng.moe.ui.oobe.OobeHomeReadiness
import com.Badnng.moe.ui.oobe.OobeHomeSpringEasing
import com.Badnng.moe.ui.oobe.OobeSinOutEasing
import com.Badnng.moe.ui.oobe.OobeVisualBackend
import com.Badnng.moe.ui.oobe.OobeVisualBackendResolver
import com.Badnng.moe.ui.oobe.homeToOobeTransform
import com.Badnng.moe.ui.oobe.oobeToHomeTransform
import com.Badnng.moe.ui.theme.MD3E_MONET_ENABLED_KEY
import com.Badnng.moe.ui.theme.MIUIX_MONET_ENABLED_KEY
import com.Badnng.moe.ui.theme.澎湃记Theme
import com.Badnng.moe.helper.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    var intentToProcess by mutableStateOf<Intent?>(null)
    private lateinit var projectionManager: MediaProjectionManager
    private var isFromNotification = false
    private lateinit var settingsPrefs: SharedPreferences
    private var showOnboarding by mutableStateOf(false)
    private var hideOnboardingForHome by mutableStateOf(false)
    private var onboardingLaunchInProgress by mutableStateOf(false)
    private var onboardingSourceView: View? = null
    private var homeRevealGeneration by mutableIntStateOf(0)
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

    private val onboardingContentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when (result.resultCode) {
            OnboardingContentActivity.RESULT_SHOW_COMPLETION -> {
                releaseOnboardingSource()
                launchOnboardingCompletion()
            }
            RESULT_OK -> {
                completeOnboarding()
                onboardingSourceView = null
                onboardingLaunchInProgress = false
            }
            else -> restoreOnboardingAfterExit()
        }
    }

    private val onboardingCompletionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            completeOnboarding()
            onboardingSourceView = null
            onboardingLaunchInProgress = false
        } else {
            restoreOnboardingAfterExit()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.Badnng.moe.helper.AppLogger.app("MainActivity onCreate")
        EdgeToEdgeHelper.applyGestureEdgeToEdge(this)
        settingsPrefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (
            !settingsPrefs.contains("theme_mode") ||
            !settingsPrefs.contains(MD3E_MONET_ENABLED_KEY) ||
            !settingsPrefs.contains(MIUIX_MONET_ENABLED_KEY)
        ) {
            settingsPrefs.edit().apply {
                if (!settingsPrefs.contains("theme_mode")) {
                    putString("theme_mode", "system")
                }
                if (!settingsPrefs.contains(MD3E_MONET_ENABLED_KEY)) {
                    putBoolean(MD3E_MONET_ENABLED_KEY, true)
                }
                if (!settingsPrefs.contains(MIUIX_MONET_ENABLED_KEY)) {
                    putBoolean(MIUIX_MONET_ENABLED_KEY, false)
                }
            }.apply()
        }
        settingsPrefs.registerOnSharedPreferenceChangeListener(settingsListener)

        lifecycleScope.launch(Dispatchers.IO) {
            // 初始化规则引擎
            RecognitionRuleEngine.initialize(applicationContext)
            Log.d("RuleEngine", "规则引擎初始化完成")

            // 启动在线规则自动更新
            com.Badnng.moe.rules.RuleAutoUpdateManager.start(applicationContext)

            StorageCleanupHelper.runStartupCleanup(applicationContext)
            runCatching {
                val db = OrderDatabase.getDatabase(applicationContext)
                DailyExpressGroupingHelper.regroupPendingExpressByDay(
                    orderDao = db.orderDao(),
                    groupDao = db.orderGroupDao(),
                    context = applicationContext
                )
            }
        }

        // 检查是否从通知进入
        isFromNotification = intent?.getBooleanExtra("from_notification", false) == true

        // 异步初始化 PaddleOCR（只需一次）
        lifecycleScope.launch {
            PaddleOcrHelper.getInstance(applicationContext).initAsync()
        }

        // 启动保活服务（仅在开启时）
        val keepAlivePrefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (keepAlivePrefs.getBoolean("persistent_notification_enabled", false)) {
            com.Badnng.moe.service.KeepAliveService.start(this)
        }

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        intentToProcess = intent

        val shouldShowOnboarding =
            !settingsPrefs.getBoolean("onboarding_completed", false) ||
                settingsPrefs.getBoolean("show_onboarding_on_next_launch", false)
        showOnboarding = shouldShowOnboarding
        if (shouldShowOnboarding) OobeHomeReadiness.reset()

        lifecycleScope.launch {
            OobeHomeReadiness.welcomeSourceReleaseRequested.collectLatest { requested ->
                if (requested && showOnboarding) releaseOnboardingSource()
            }
        }

        if (!shouldShowOnboarding && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            val homeScale = remember { Animatable(1f) }
            val homeAlpha = remember { Animatable(1f) }
            var startedRevealGeneration by remember { mutableIntStateOf(0) }
            var homeLaidOut by remember { mutableStateOf(false) }

            LaunchedEffect(homeLaidOut) {
                if (!homeLaidOut) return@LaunchedEffect
                withFrameNanos { }
                withFrameNanos { }
                OobeHomeReadiness.markReady()
            }

            LaunchedEffect(homeRevealGeneration) {
                if (homeRevealGeneration == 0) return@LaunchedEffect
                homeScale.snapTo(1.4f)
                homeAlpha.snapTo(0f)
                startedRevealGeneration = homeRevealGeneration
                coroutineScope {
                    launch {
                        delay(60L)
                        homeAlpha.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 230,
                                easing = OobeSinOutEasing,
                            ),
                        )
                    }
                    launch {
                        homeScale.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = OOBE_HOME_ENTER_MILLIS,
                                easing = OobeHomeSpringEasing,
                            ),
                        )
                    }
                }
            }

            Box(Modifier.fillMaxSize()) {
                澎湃记Theme {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val waitingForReveal =
                                    homeRevealGeneration > startedRevealGeneration
                                alpha = if (waitingForReveal) 0f else homeAlpha.value
                                val scale = if (waitingForReveal) 1.4f else homeScale.value
                                scaleX = scale
                                scaleY = scale
                            }
                            .onGloballyPositioned { coordinates ->
                                if (coordinates.size.width > 0 && coordinates.size.height > 0) {
                                    homeLaidOut = true
                                }
                            }
                            .then(
                                if (showOnboarding) {
                                    Modifier.clearAndSetSemantics { }
                                } else {
                                    Modifier
                                },
                            ),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        HomeScreen(intentToProcess = intentToProcess)
                    }
                }

                AnimatedContent(
                    targetState = showOnboarding,
                    label = "onboarding_transition",
                    transitionSpec = {
                        if (initialState && !targetState) {
                            oobeToHomeTransform()
                        } else {
                            homeToOobeTransform()
                        }
                    },
                ) { isOnboarding ->
                    if (isOnboarding) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (hideOnboardingForHome) 0f else 1f),
                        ) {
                            if (!hideOnboardingForHome) {
                                OnboardingScreen(
                                    onComplete = {
                                        completeOnboarding()
                                    },
                                    welcomeEnabled = !onboardingLaunchInProgress,
                                    onWelcomeStart = ::launchOnboardingContent,
                                )
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    private fun launchOnboardingContent(source: View) {
        if (onboardingLaunchInProgress || isFinishing || isDestroyed) return
        onboardingLaunchInProgress = true
        onboardingSourceView = source
        val backend = OobeVisualBackendResolver.resolve(applicationContext)
        OobeActivityTransition.createLaunchOptions(
            activity = this,
            source = source,
            useHyperOsTransition = OobeVisualBackendResolver.isHyperOsDevice(),
            hyperOsBlurEnabled = backend == OobeVisualBackend.HyperOsEnhanced,
        ) { options ->
            if (isFinishing || isDestroyed) {
                restoreOnboardingLaunchState()
                return@createLaunchOptions
            }
            runCatching {
                val intent = Intent(this, OnboardingContentActivity::class.java)
                if (options != null) {
                    onboardingContentLauncher.launch(intent, RawActivityOptionsCompat(options))
                } else {
                    onboardingContentLauncher.launch(intent)
                }
                hideOnboardingSource()
            }.onFailure {
                restoreOnboardingLaunchState()
            }
        }
    }

    private fun launchOnboardingCompletion() {
        runCatching {
            onboardingCompletionLauncher.launch(
                Intent(this, OnboardingCompleteActivity::class.java),
            )
            @Suppress("DEPRECATION")
            overridePendingTransition(
                R.anim.oobe_page_enter_right,
                R.anim.oobe_page_exit_left,
            )
        }.onFailure {
            restoreOnboardingAfterExit()
        }
    }

    private fun restoreOnboardingAfterExit() {
        OobeHomeReadiness.restoreWelcomeSource()
        hideOnboardingForHome = false
        restoreOnboardingSourceVisibility()
        onboardingSourceView = null
        onboardingLaunchInProgress = false
    }

    private fun restoreOnboardingLaunchState() {
        restoreOnboardingSourceVisibility()
        onboardingSourceView = null
        onboardingLaunchInProgress = false
    }

    private fun restoreOnboardingSourceVisibility() {
        onboardingSourceView?.let { source ->
            (source.tag as? View)?.visibility = View.VISIBLE
            source.visibility = View.VISIBLE
        }
    }

    private fun hideOnboardingSource() {
        onboardingSourceView?.let { source ->
            (source.tag as? View)?.visibility = View.INVISIBLE
            source.visibility = View.INVISIBLE
        }
    }

    private fun releaseOnboardingSource() {
        hideOnboardingSource()
        onboardingSourceView = null
        hideOnboardingForHome = true
    }

    private fun completeOnboarding() {
        hideOnboardingSource()
        hideOnboardingForHome = true
        if (showOnboarding) homeRevealGeneration += 1
        settingsPrefs.edit()
            .putBoolean("onboarding_completed", true)
            .putBoolean("show_onboarding_on_next_launch", false)
            .apply()
        showOnboarding = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentToProcess = intent
        AppLogger.app("MainActivity onNewIntent, fromNotification=${intent?.getBooleanExtra("from_notification", false)}")
        // 检查是否从通知进入
        isFromNotification = intent?.getBooleanExtra("from_notification", false) == true
    }

    override fun onResume() {
        super.onResume()
        AppLogger.app("MainActivity onResume")
        EdgeToEdgeHelper.applyGestureEdgeToEdge(this)
        if (settingsPrefs.getBoolean("persistent_notification_enabled", true)) {
            com.Badnng.moe.service.KeepAliveService.hideNotification(this)
        }
    }

    override fun onPause() {
        super.onPause()
        if (settingsPrefs.getBoolean("persistent_notification_enabled", true)) {
            com.Badnng.moe.service.KeepAliveService.showNotification(this)
        }
    }

    override fun onDestroy() {
        if (::settingsPrefs.isInitialized) {
            settingsPrefs.unregisterOnSharedPreferenceChangeListener(settingsListener)
        }
        com.Badnng.moe.helper.AppLogger.app("MainActivity onDestroy")
        com.Badnng.moe.helper.AppLogger.flush()
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        AppLogger.app("MainActivity onUserLeaveHint, fromNotification=$isFromNotification")
        // 从通知进入后，按 Home 键离开时从最近任务移除
        if (isFromNotification) {
            finishAndRemoveTask()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isFromNotification", isFromNotification)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        isFromNotification = savedInstanceState.getBoolean("isFromNotification", false)
    }

    fun isFromNotification(): Boolean = isFromNotification

    // 外部跳转（如身份码）前调用，避免 onUserLeaveHint 抢先把任务移除导致跳转失败。
    fun clearNotificationLaunchState() {
        isFromNotification = false
    }

    private class RawActivityOptionsCompat(
        private val options: Bundle,
    ) : ActivityOptionsCompat() {
        override fun toBundle(): Bundle = options
    }

}

