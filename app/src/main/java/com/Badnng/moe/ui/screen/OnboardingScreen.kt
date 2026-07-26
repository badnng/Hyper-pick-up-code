package com.Badnng.moe.ui.screen

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContextWrapper
import com.Badnng.moe.R
import android.app.AppOpsManager
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Process
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.Badnng.moe.helper.EdgeToEdgeHelper
import com.Badnng.moe.recognition.CustomRequestMode
import com.Badnng.moe.recognition.MimoBillingMode
import com.Badnng.moe.recognition.OnlineRecognitionCatalog
import com.Badnng.moe.recognition.OnlineRecognitionClient
import com.Badnng.moe.recognition.OnlineRecognitionModel
import com.Badnng.moe.recognition.OnlineRecognitionPreferences
import com.Badnng.moe.recognition.OnlineRecognitionProvider
import com.Badnng.moe.recognition.SecureApiKeyStore
import com.Badnng.moe.privacy.PrivacyConsent
import com.Badnng.moe.service.CaptureTileService
import com.Badnng.moe.ui.component.OnlineRecognitionProviderIcon
import com.Badnng.moe.ui.component.PrivacyConsentBottomSheet
import com.Badnng.moe.ui.screen.settings.ProviderUsageGuide
import com.Badnng.moe.ui.oobe.OOBE_PAGE_TRANSITION_MILLIS
import com.Badnng.moe.ui.oobe.OobeAccelerateDecelerateEasing
import com.Badnng.moe.ui.oobe.OobeCubicOutEasing
import com.Badnng.moe.ui.oobe.OobeCompleteView
import com.Badnng.moe.ui.oobe.OobeContentHeaderView
import com.Badnng.moe.ui.oobe.OobeGlowBackground
import com.Badnng.moe.ui.oobe.OobeHomeReadiness
import com.Badnng.moe.ui.oobe.OobePrimaryButtonView
import com.Badnng.moe.ui.oobe.OobeVisualBackend
import com.Badnng.moe.ui.oobe.OobeVisualBackendResolver
import com.Badnng.moe.ui.oobe.OobeWelcomeView
import com.Badnng.moe.ui.oobe.oobeCircularReveal
import com.Badnng.moe.ui.oobe.oobeMiuixPressFeedback
import com.Badnng.moe.ui.oobe.rememberOobeVisualBackend
import rikka.shizuku.Shizuku
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.RadioButton as MiuixRadioButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

private enum class OnboardingStep {
    Welcome,
    Permissions,
    RecognitionPreference,
    Features,
    Complete,
}

private val LocalOobeBackend = staticCompositionLocalOf { OobeVisualBackend.AndroidFallback }
private val LocalOobeDarkTheme = staticCompositionLocalOf { false }
private const val FEATURES_ACK_COUNTDOWN_SECONDS = 15
private const val FEATURES_ACK_COUNTDOWN_MILLIS = FEATURES_ACK_COUNTDOWN_SECONDS * 1000L
private const val OOBE_ONLINE_RECOGNITION_DESCRIPTION =
    "使用在线多模态模型。截图、分享图片、通知文字、短信内容或所选文字会发送给您选择的供应商，请在下方完成供应商和 API 密钥配置。"

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    showWelcome: Boolean = true,
    startAtComplete: Boolean = false,
    welcomeEnabled: Boolean = true,
    onWelcomeStart: ((View) -> Unit)? = null,
    onFinalStepRequested: (() -> Unit)? = null,
    onExit: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val backendState = rememberOobeVisualBackend()
    val homeReady by OobeHomeReadiness.ready.collectAsStateWithLifecycle()
    var currentStep by rememberSaveable(showWelcome, startAtComplete) {
        mutableStateOf(
            when {
                startAtComplete -> OnboardingStep.Complete
                showWelcome -> OnboardingStep.Welcome
                else -> OnboardingStep.Permissions
            },
        )
    }
    var welcomeIntroPlayed by rememberSaveable { mutableStateOf(false) }
    var transitionLocked by remember { mutableStateOf(false) }
    var revealActive by remember { mutableStateOf(false) }
    val revealProgress = remember { Animatable(1f) }
    var startButtonCenter by remember { mutableStateOf(Offset.Unspecified) }
    val coroutineScope = rememberCoroutineScope()
    val darkTheme = isSystemInDarkTheme()

    val performHaptic = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    var hasNotificationPermission by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var isIgnoringBattery by remember { mutableStateOf(false) }
    var hasUsageStatsPermission by remember { mutableStateOf(checkUsageStatsPermission(context)) }
    var shizukuReady by remember { mutableStateOf(false) }
    var featuresAckCountdown by rememberSaveable { mutableIntStateOf(15) }
    var featuresAckDeadlineMillis by rememberSaveable { mutableLongStateOf(0L) }
    var privacyAccepted by remember { mutableStateOf(PrivacyConsent.isAccepted(prefs)) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var preferredRecognitionMode by rememberSaveable {
        val savedMode = prefs.getString(OnlineRecognitionPreferences.MODE_KEY, null)
        mutableStateOf(
            savedMode.takeUnless {
                it == OnlineRecognitionPreferences.MODE_ONLINE && !privacyAccepted
            },
        )
    }
    var onlineConfigurationReady by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        hasNotificationPermission = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    LaunchedEffect(currentStep) {
        if (currentStep != OnboardingStep.Permissions) return@LaunchedEffect
        while (currentStep == OnboardingStep.Permissions) {
            hasNotificationPermission = NotificationManagerCompat.from(context).areNotificationsEnabled()
            isIgnoringBattery = checkBatteryOptimization(context)
            hasUsageStatsPermission = checkUsageStatsPermission(context)
            shizukuReady = withContext(Dispatchers.IO) { isShizukuReady() }
            delay(1500)
        }
    }

    LaunchedEffect(currentStep) {
        if (currentStep == OnboardingStep.Features) {
            val now = android.os.SystemClock.elapsedRealtime()
            val deadline = featuresAckDeadlineMillis.takeIf { it > 0L } ?: run {
                (now + FEATURES_ACK_COUNTDOWN_MILLIS).also {
                    featuresAckDeadlineMillis = it
                }
            }
            while (currentStep == OnboardingStep.Features) {
                val remainingMillis = (deadline - android.os.SystemClock.elapsedRealtime())
                    .coerceAtLeast(0L)
                featuresAckCountdown = ((remainingMillis + 999L) / 1000L).toInt()
                if (remainingMillis == 0L) break
                delay(minOf(1000L, remainingMillis))
            }
        }
    }

    fun navigateTo(step: OnboardingStep) {
        if (transitionLocked) return
        performHaptic()
        transitionLocked = true
        if (step == OnboardingStep.Features && currentStep != OnboardingStep.Features) {
            featuresAckCountdown = FEATURES_ACK_COUNTDOWN_SECONDS
            featuresAckDeadlineMillis = android.os.SystemClock.elapsedRealtime() +
                FEATURES_ACK_COUNTDOWN_MILLIS
        }
        currentStep = step
        coroutineScope.launch {
            delay(OOBE_PAGE_TRANSITION_MILLIS.toLong())
            transitionLocked = false
        }
    }

    fun startOobe(source: View) {
        if (transitionLocked) return
        performHaptic()
        if (onWelcomeStart != null) {
            onWelcomeStart(source)
            return
        }
        transitionLocked = true
        revealActive = true
        coroutineScope.launch {
            revealProgress.snapTo(0f)
            currentStep = OnboardingStep.Permissions
            revealProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(520, easing = OobeCubicOutEasing),
            )
            revealActive = false
            transitionLocked = false
        }
    }

    val requestNotificationPermission = {
        val alreadyRequested = prefs.getBoolean("notification_permission_requested", false)
        if (!alreadyRequested) {
            prefs.edit().putBoolean("notification_permission_requested", true).apply()
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            })
        }
    }

    BackHandler(enabled = transitionLocked || currentStep != OnboardingStep.Welcome || !showWelcome) {
        if (transitionLocked) return@BackHandler
        when (currentStep) {
            OnboardingStep.Welcome -> Unit
            OnboardingStep.Permissions -> {
                if (showWelcome) {
                    navigateTo(OnboardingStep.Welcome)
                } else {
                    performHaptic()
                    onExit()
                }
            }
            OnboardingStep.RecognitionPreference -> navigateTo(OnboardingStep.Permissions)
            OnboardingStep.Features -> navigateTo(OnboardingStep.RecognitionPreference)
            OnboardingStep.Complete -> Unit
        }
    }

    val useDarkSystemBarIcons = when (currentStep) {
        OnboardingStep.Welcome, OnboardingStep.Complete ->
            backendState.value != OobeVisualBackend.HyperOsEnhanced && !darkTheme
        OnboardingStep.Permissions,
        OnboardingStep.RecognitionPreference,
        OnboardingStep.Features -> !darkTheme
    }
    OobeSystemBars(darkIcons = useDarkSystemBarIcons)
    OobeFixedTheme(
        darkTheme = darkTheme,
        backend = backendState.value,
    ) {
        OobeMiuixTheme {
            val backdrop = com.Badnng.moe.ui.miuix.rememberMiuixBackdrop()
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
                        ),
                ) {
                    if (revealActive) {
                        OobeWelcomePage(
                            backendState = backendState,
                            onStart = { _ -> },
                            enabled = false,
                            onStartButtonCenter = {},
                            modifier = Modifier.zIndex(0f),
                            playIntro = false,
                        )
                    }

                    AnimatedContent(
                        targetState = currentStep,
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(1f)
                            .then(
                                if (revealActive) {
                                    Modifier.oobeCircularReveal(
                                        progress = { revealProgress.value },
                                        center = {
                                            if (startButtonCenter.x.isFinite() && startButtonCenter.y.isFinite()) {
                                                startButtonCenter
                                            } else {
                                                Offset.Zero
                                            }
                                        },
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                        label = "oobe_step_transition",
                        transitionSpec = {
                            if (initialState == OnboardingStep.Welcome && targetState == OnboardingStep.Permissions) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else if (targetState.ordinal > initialState.ordinal) {
                                slideInHorizontally(
                                    animationSpec = tween(OOBE_PAGE_TRANSITION_MILLIS, easing = OobeAccelerateDecelerateEasing),
                                ) { it } togetherWith slideOutHorizontally(
                                    animationSpec = tween(OOBE_PAGE_TRANSITION_MILLIS, easing = OobeAccelerateDecelerateEasing),
                                ) { -it }
                            } else {
                                slideInHorizontally(
                                    animationSpec = tween(OOBE_PAGE_TRANSITION_MILLIS, easing = OobeAccelerateDecelerateEasing),
                                ) { -it } togetherWith slideOutHorizontally(
                                    animationSpec = tween(OOBE_PAGE_TRANSITION_MILLIS, easing = OobeAccelerateDecelerateEasing),
                                ) { it }
                            }
                        },
                    ) { step ->
                        when (step) {
                            OnboardingStep.Welcome -> OobeWelcomePage(
                                backendState = backendState,
                                onStart = ::startOobe,
                                enabled = !transitionLocked && welcomeEnabled,
                                onStartButtonCenter = { startButtonCenter = it },
                                playIntro = backendState.value != OobeVisualBackend.StaticFallback &&
                                    !welcomeIntroPlayed,
                                onIntroFinished = { welcomeIntroPlayed = true },
                            )
                            OnboardingStep.Permissions -> OobePageScaffold(
                                title = "权限设置",
                                subtitle = "通知权限为必选设置哦",
                                previewDrawable = R.drawable.oobe_permission_preview,
                                primaryLabel = "继续",
                                primaryEnabled = hasNotificationPermission && !transitionLocked,
                                onBack = {
                                    if (showWelcome) {
                                        navigateTo(OnboardingStep.Welcome)
                                    } else {
                                        performHaptic()
                                        onExit()
                                    }
                                },
                                onPrimary = { navigateTo(OnboardingStep.RecognitionPreference) },
                            ) {
                                PermissionsStep(
                                    hasNotificationPermission = hasNotificationPermission,
                                    isIgnoringBattery = isIgnoringBattery,
                                    hasUsageStatsPermission = hasUsageStatsPermission,
                                    shizukuReady = shizukuReady,
                                    performHaptic = performHaptic,
                                    onRequestNotificationPermission = requestNotificationPermission,
                                )
                            }
                            OnboardingStep.RecognitionPreference -> OobePageScaffold(
                                title = "识别方式",
                                subtitle = "您偏好哪一种识别方式？",
                                previewDrawable = R.drawable.oobe_recognition_preview,
                                primaryLabel = "继续",
                                primaryEnabled = preferredRecognitionMode != null &&
                                    (preferredRecognitionMode != OnlineRecognitionPreferences.MODE_ONLINE ||
                                        (privacyAccepted && onlineConfigurationReady)) &&
                                    !transitionLocked,
                                onBack = { navigateTo(OnboardingStep.Permissions) },
                                onPrimary = {
                                    preferredRecognitionMode?.let { selectedMode ->
                                        prefs.edit()
                                            .putString(
                                                OnlineRecognitionPreferences.MODE_KEY,
                                                selectedMode,
                                            )
                                            .apply()
                                        navigateTo(OnboardingStep.Features)
                                    }
                                },
                            ) {
                                RecognitionPreferenceStep(
                                    selectedMode = preferredRecognitionMode,
                                    performHaptic = performHaptic,
                                    onOnlineConfigurationReady = { onlineConfigurationReady = it },
                                    onModeSelected = { selectedMode ->
                                        performHaptic()
                                        if (selectedMode == OnlineRecognitionPreferences.MODE_ONLINE &&
                                            !privacyAccepted
                                        ) {
                                            showPrivacyDialog = true
                                        } else {
                                            if (selectedMode == OnlineRecognitionPreferences.MODE_ONLINE) {
                                                onlineConfigurationReady = false
                                            }
                                            preferredRecognitionMode = selectedMode
                                        }
                                    },
                                )
                            }
                            OnboardingStep.Features -> OobePageScaffold(
                                title = "了解如何识别",
                                subtitle = "了解适合当前场景的识别方式",
                                previewDrawable = R.drawable.oobe_features_preview,
                                primaryLabel = if (featuresAckCountdown == 0) "完成设置" else "完成设置（${featuresAckCountdown}s）",
                                primaryEnabled = featuresAckCountdown == 0 && !transitionLocked,
                                onBack = { navigateTo(OnboardingStep.RecognitionPreference) },
                                onPrimary = {
                                    if (onFinalStepRequested != null) {
                                        performHaptic()
                                        onFinalStepRequested()
                                    } else {
                                        navigateTo(OnboardingStep.Complete)
                                    }
                                },
                            ) {
                                FeaturesStep()
                            }
                            OnboardingStep.Complete -> OobeCompletePage(
                                backendState = backendState,
                                actionLabel = if (homeReady) "进入澎湃记" else "正在准备中",
                                enabled = homeReady && !transitionLocked,
                                onComplete = {
                                    performHaptic()
                                    prefs.edit().putBoolean("onboarding_completed", true).apply()
                                    onComplete()
                                },
                            )
                        }
                    }
                }

                val blurProgress = com.Badnng.moe.ui.component.BlurState.progress.floatValue
                if (blurProgress > 0.01f) {
                    if (backdrop != null) {
                        val baseBrightness = if (darkTheme) -0.3f else -0.5f
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .textureBlur(
                                    backdrop = backdrop,
                                    shape = RectangleShape,
                                    blurRadius = 56f * blurProgress,
                                    colors = BlurDefaults.blurColors(
                                        brightness = baseBrightness * blurProgress,
                                        contrast = 1f + 0.2f * blurProgress,
                                        saturation = 1f + 0.08f * blurProgress,
                                    ),
                                )
                                .graphicsLayer(alpha = blurProgress),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.32f * blurProgress)),
                        )
                    }
                }

                PrivacyConsentBottomSheet(
                    show = showPrivacyDialog,
                    title = "启用在线识别",
                    onDismiss = {
                        performHaptic()
                        showPrivacyDialog = false
                    },
                    onConfirm = {
                        performHaptic()
                        PrivacyConsent.accept(prefs)
                        privacyAccepted = true
                        onlineConfigurationReady = false
                        preferredRecognitionMode = OnlineRecognitionPreferences.MODE_ONLINE
                        showPrivacyDialog = false
                    },
                )
            }
        }
    }
}

@Composable
private fun OobeFixedTheme(
    darkTheme: Boolean,
    backend: OobeVisualBackend,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF9CB5FF),
            onPrimary = Color(0xFF001A43),
            background = Color(0xFF101114),
            surface = Color(0xFF1B1C20),
            surfaceVariant = Color(0xFF27282D),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0D84FF),
            onPrimary = Color.White,
            background = Color(0xFFF7F7FA),
            surface = Color.White,
            surfaceVariant = Color(0xFFECEEF3),
        )
    }
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(
            LocalOobeBackend provides backend,
            LocalOobeDarkTheme provides darkTheme,
        ) {
            content()
        }
    }
}

@Composable
private fun OobeWelcomePage(
    backendState: MutableState<OobeVisualBackend>,
    onStart: (View) -> Unit,
    enabled: Boolean,
    onStartButtonCenter: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    playIntro: Boolean = true,
    onIntroFinished: () -> Unit = {},
) {
    val backend = backendState.value
    val darkTheme = LocalOobeDarkTheme.current
    val introRequested = remember { playIntro }
    val motionEnabled = introRequested && backend != OobeVisualBackend.StaticFallback
    var circleYOffset by remember { mutableFloatStateOf(0.1f) }

    OobeGlowBackground(
        backendState = backendState,
        showOpeningCircle = motionEnabled,
        darkTheme = darkTheme,
        circleYOffset = circleYOffset,
        modifier = modifier,
    ) {
        key(motionEnabled) {
            AndroidView(
                factory = { context ->
                    OobeWelcomeView(context, motionEnabled)
                },
                update = { welcomeView ->
                    welcomeView.bind(
                        backend = backend,
                        darkTheme = darkTheme,
                        enabled = enabled,
                        onStart = { source ->
                            val location = IntArray(2)
                            source.getLocationInWindow(location)
                            onStartButtonCenter(
                                Offset(
                                    location[0] + source.width / 2f,
                                    location[1] + source.height / 2f,
                                ),
                            )
                            onStart(source)
                        },
                        onIntroFinished = onIntroFinished,
                        onBackendFailure = {
                            backendState.value = OobeVisualBackendResolver.downgradeFromHyperOs()
                        },
                        onCircleYOffsetChanged = { circleYOffset = it },
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun OobePageScaffold(
    title: String,
    subtitle: String,
    previewDrawable: Int,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onBack: () -> Unit,
    onPrimary: () -> Unit,
    content: @Composable () -> Unit,
) {
    val darkTheme = LocalOobeDarkTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
        ) {
            AndroidView(
                factory = { context -> OobeContentHeaderView(context) },
                update = { header ->
                    header.bind(
                        title = title,
                        subtitle = subtitle,
                        previewDrawable = previewDrawable,
                        darkTheme = darkTheme,
                        onBack = onBack,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Box(Modifier.weight(1f)) { content() }
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AndroidView(
                        factory = { context -> OobePrimaryButtonView(context) },
                        update = { button ->
                            button.bind(
                                label = primaryLabel,
                                enabled = primaryEnabled,
                                onClick = onPrimary,
                            )
                        },
                        modifier = Modifier
                            .widthIn(max = 552.dp)
                            .fillMaxWidth()
                            .height(50.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OobeCompletePage(
    backendState: MutableState<OobeVisualBackend>,
    actionLabel: String,
    enabled: Boolean,
    onComplete: () -> Unit,
) {
    val darkTheme = LocalOobeDarkTheme.current
    val motionEnabled = backendState.value != OobeVisualBackend.StaticFallback

    OobeGlowBackground(
        backendState = backendState,
        showOpeningCircle = false,
        darkTheme = darkTheme,
    ) {
        key(motionEnabled) {
            AndroidView(
                factory = { context -> OobeCompleteView(context, motionEnabled) },
                update = { completeView ->
                    completeView.bind(
                        backend = backendState.value,
                        darkTheme = darkTheme,
                        actionLabel = actionLabel,
                        enabled = enabled,
                        onComplete = onComplete,
                        onBackendFailure = {
                            backendState.value = OobeVisualBackendResolver.downgradeFromHyperOs()
                        },
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun OobeSystemBars(darkIcons: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(view, darkIcons) {
        val activity = context.findActivity()
        val window = activity?.window
        if (window != null) {
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
            )
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = darkIcons
                isAppearanceLightNavigationBars = darkIcons
            }
        }
        onDispose {
            if (activity != null) EdgeToEdgeHelper.applyGestureEdgeToEdge(activity)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val base = current.baseContext
        if (base === current) return null
        current = base
    }
    return current as? Activity
}

@Composable
private fun RecognitionPreferenceStep(
    selectedMode: String?,
    performHaptic: () -> Unit,
    onOnlineConfigurationReady: (Boolean) -> Unit,
    onModeSelected: (String) -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    OobePermissionContent {
        OobeRecognitionModeRow(
            title = "离线识别",
            selected = selectedMode == OnlineRecognitionPreferences.MODE_OFFLINE,
            onClick = {
                onModeSelected(OnlineRecognitionPreferences.MODE_OFFLINE)
            },
        )
        Spacer(Modifier.height(10.dp))
        OobeRecognitionModeRow(
            title = "在线识别",
            selected = selectedMode == OnlineRecognitionPreferences.MODE_ONLINE,
            onClick = {
                onModeSelected(OnlineRecognitionPreferences.MODE_ONLINE)
            },
        )

        val description = when (selectedMode) {
            OnlineRecognitionPreferences.MODE_OFFLINE ->
                "使用本地 OCR 与识别规则，无需网络或 API 密钥，识别内容不会上传。"
            OnlineRecognitionPreferences.MODE_ONLINE ->
                OOBE_ONLINE_RECOGNITION_DESCRIPTION
            else -> "请选择一种识别方式，之后仍可在设置中更改。"
        }
        Spacer(Modifier.height(16.dp))
        OobeRecognitionHint(description)

        AnimatedVisibility(
            visible = selectedMode == OnlineRecognitionPreferences.MODE_ONLINE,
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec<Float>()),
            exit = fadeOut(animationSpec = motionScheme.defaultEffectsSpec<Float>()),
        ) {
            Column {
                Spacer(Modifier.height(16.dp))
                OobeOnlineRecognitionSetup(
                    performHaptic = performHaptic,
                    onConfigurationReady = onOnlineConfigurationReady,
                )
            }
        }
    }
}

@Composable
private fun OobeOnlineRecognitionSetup(
    performHaptic: () -> Unit,
    onConfigurationReady: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val keyStore = remember { SecureApiKeyStore(context) }

    var provider by remember {
        mutableStateOf(OnlineRecognitionPreferences.provider(context))
    }
    var model by remember {
        mutableStateOf(OnlineRecognitionPreferences.model(context, provider))
    }
    var mimoBillingMode by remember {
        mutableStateOf(OnlineRecognitionPreferences.mimoBillingMode(context))
    }
    var customRequestMode by remember {
        mutableStateOf(OnlineRecognitionPreferences.customRequestMode(context))
    }
    var customBaseUrl by remember {
        val savedUrl = OnlineRecognitionPreferences.customBaseUrl(context)
        mutableStateOf(TextFieldValue(savedUrl, TextRange(savedUrl.length)))
    }
    var apiKeyInput by remember {
        val savedKey = keyStore.get(provider).orEmpty()
        mutableStateOf(TextFieldValue(savedKey, TextRange(savedKey.length)))
    }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var customModels by remember { mutableStateOf(emptyList<OnlineRecognitionModel>()) }
    var customModelsLoading by remember { mutableStateOf(false) }
    var customModelsError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(provider) {
        model = OnlineRecognitionPreferences.model(context, provider)
        val savedKey = keyStore.get(provider).orEmpty()
        apiKeyInput = TextFieldValue(savedKey, TextRange(savedKey.length))
        apiKeyVisible = false
    }

    LaunchedEffect(provider, customBaseUrl.text, apiKeyInput.text) {
        if (provider != OnlineRecognitionProvider.CUSTOM) return@LaunchedEffect
        customModels = emptyList()
        customModelsError = null
        customModelsLoading = false
        if (customBaseUrl.text.isBlank() || apiKeyInput.text.isBlank()) return@LaunchedEffect
        delay(700)
        customModelsLoading = true
        runCatching {
            OnlineRecognitionClient(context).fetchCustomModels(customBaseUrl.text)
        }.onSuccess { fetchedModels ->
            customModels = fetchedModels
            val savedModel = OnlineRecognitionPreferences.model(context, provider)
            val selectedModel = fetchedModels.firstOrNull { it.id == savedModel.id }
                ?: fetchedModels.first()
            model = selectedModel
            OnlineRecognitionPreferences.saveModel(context, provider, selectedModel.id)
        }.onFailure { error ->
            customModelsError = error.message ?: "获取模型失败"
        }
        customModelsLoading = false
    }

    val availableModels = if (provider == OnlineRecognitionProvider.CUSTOM) {
        customModels
    } else {
        OnlineRecognitionCatalog.modelsFor(provider)
    }
    val configurationReady = apiKeyInput.text.isNotBlank() &&
        model.id.isNotBlank() &&
        (provider != OnlineRecognitionProvider.CUSTOM ||
            (customBaseUrl.text.isNotBlank() && customModels.any { it.id == model.id }))

    LaunchedEffect(configurationReady) {
        onConfigurationReady(configurationReady)
    }

    OobeMiuixTheme {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (provider == OnlineRecognitionProvider.MIMO ||
                provider == OnlineRecognitionProvider.ZHIPU ||
                provider == OnlineRecognitionProvider.MINIMAX ||
                provider == OnlineRecognitionProvider.MOONSHOT
            ) {
                ProviderUsageGuide(
                    provider = provider,
                    isMiuix = true,
                    performHaptic = performHaptic,
                    miuixHorizontalPadding = 0.dp,
                )
            }

            MiuixCard(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    WindowDropdownPreference(
                        title = "供应商",
                        entries = listOf(
                            DropdownEntry(
                                items = OnlineRecognitionProvider.entries.map { item ->
                                    DropdownItem(
                                        text = item.displayName,
                                        selected = provider == item,
                                        onClick = {
                                            performHaptic()
                                            provider = item
                                            model = OnlineRecognitionPreferences.model(context, item)
                                            val savedKey = keyStore.get(item).orEmpty()
                                            apiKeyInput = TextFieldValue(
                                                savedKey,
                                                TextRange(savedKey.length),
                                            )
                                            apiKeyVisible = false
                                            if (item == OnlineRecognitionProvider.CUSTOM) {
                                                customModels = emptyList()
                                                customModelsError = null
                                            }
                                            prefs.edit()
                                                .putString(
                                                    OnlineRecognitionPreferences.PROVIDER_KEY,
                                                    item.key,
                                                )
                                                .apply()
                                        },
                                        icon = { _ ->
                                            Box(
                                                modifier = Modifier.size(width = 38.dp, height = 28.dp),
                                                contentAlignment = Alignment.CenterStart,
                                            ) {
                                                OnlineRecognitionProviderIcon(
                                                    provider = item,
                                                    modifier = Modifier.size(28.dp),
                                                )
                                            }
                                        },
                                    )
                                },
                            ),
                        ),
                        showValue = false,
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OnlineRecognitionProviderIcon(
                            provider = provider,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        MiuixText(
                            text = provider.displayName,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (provider == OnlineRecognitionProvider.MIMO) {
                    WindowDropdownPreference(
                        title = "计费模式",
                        items = MimoBillingMode.entries.map { it.displayName },
                        selectedIndex = MimoBillingMode.entries.indexOf(mimoBillingMode),
                        onSelectedIndexChange = { index ->
                            performHaptic()
                            mimoBillingMode = MimoBillingMode.entries[index]
                            prefs.edit()
                                .putString(
                                    OnlineRecognitionPreferences.MIMO_BILLING_KEY,
                                    mimoBillingMode.key,
                                )
                                .apply()
                        },
                    )
                }

                if (provider == OnlineRecognitionProvider.CUSTOM) {
                    WindowDropdownPreference(
                        title = "请求模式",
                        items = CustomRequestMode.entries.map { it.displayName },
                        selectedIndex = CustomRequestMode.entries.indexOf(customRequestMode),
                        onSelectedIndexChange = { index ->
                            performHaptic()
                            customRequestMode = CustomRequestMode.entries[index]
                            prefs.edit()
                                .putString(
                                    OnlineRecognitionPreferences.CUSTOM_REQUEST_MODE_KEY,
                                    customRequestMode.key,
                                )
                                .apply()
                        },
                    )
                }

                if (availableModels.isNotEmpty()) {
                    WindowDropdownPreference(
                        title = "模型",
                        items = availableModels.map { it.displayName },
                        selectedIndex = availableModels.indexOfFirst { it.id == model.id }
                            .coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            performHaptic()
                            model = availableModels[index]
                            OnlineRecognitionPreferences.saveModel(context, provider, model.id)
                        },
                    )
                }
            }

            if (provider == OnlineRecognitionProvider.CUSTOM) {
                MiuixTextField(
                    value = customBaseUrl,
                    onValueChange = { value ->
                        customBaseUrl = value
                        customModels = emptyList()
                        customModelsError = null
                        prefs.edit()
                            .putString(
                                OnlineRecognitionPreferences.CUSTOM_BASE_URL_KEY,
                                value.text.trim(),
                            )
                            .apply()
                    },
                    label = "API 请求地址",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }

            MiuixTextField(
                value = apiKeyInput,
                onValueChange = { value ->
                    apiKeyInput = value
                    if (provider == OnlineRecognitionProvider.CUSTOM) {
                        customModels = emptyList()
                        customModelsError = null
                    }
                    keyStore.save(provider, value.text)
                },
                label = "${provider.displayName} API 密钥",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                visualTransformation = if (apiKeyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    MiuixIconButton(
                        onClick = {
                            performHaptic()
                            apiKeyVisible = !apiKeyVisible
                        },
                    ) {
                        MiuixIcon(
                            imageVector = if (apiKeyVisible) {
                                MiuixIcons.Regular.Hide
                            } else {
                                MiuixIcons.Regular.Show
                            },
                            contentDescription = if (apiKeyVisible) "隐藏密钥" else "显示密钥",
                        )
                    }
                },
            )

            val statusText = when {
                provider == OnlineRecognitionProvider.CUSTOM && customModelsLoading ->
                    "正在获取模型…"
                provider == OnlineRecognitionProvider.CUSTOM && customModelsError != null ->
                    customModelsError.orEmpty()
                provider == OnlineRecognitionProvider.CUSTOM && customModels.isEmpty() ->
                    "填写 API 请求地址和密钥后将自动获取模型。"
                apiKeyInput.text.isBlank() -> "填写 API 密钥后即可继续。"
                else -> "密钥已通过 Android Keystore 加密保存在当前设备。"
            }
            MiuixText(
                text = statusText,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            if (provider == OnlineRecognitionProvider.CUSTOM) {
                MiuixText(
                    text = "请填写到 /responses 或 /chat/completions 前的 API 请求地址。使用第三方供应商时，请自行辨别其安全性。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                MiuixText(
                    text = "您应该使用多模态模型作为识别模型。为了保证使用体验，请选择参数量相对较小、" +
                        "非思考模式下也足够聪明且速度较快的模型，如 GLM-V4.7-Flash、MiMo 2.5 等。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

        }
    }
}

@Composable
private fun OobeMiuixTheme(
    content: @Composable () -> Unit,
) {
    val darkTheme = LocalOobeDarkTheme.current
    val controller = remember(darkTheme) {
        ThemeController(
            colorSchemeMode = if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light,
            keyColor = Color(0xFF3482FF),
            isDark = darkTheme,
        )
    }
    MiuixTheme(controller = controller, content = content)
}

@Composable
private fun OobeRecognitionModeRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OobeSettingSurface(
        onClick = onClick,
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        MiuixRadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun OobeRecognitionHint(text: String) {
    val darkTheme = LocalOobeDarkTheme.current
    val shape = RoundedCornerShape(15.dp)
    val backgroundColor = if (darkTheme) Color(0x293482FF) else Color(0xFFE8F2FF)
    val contentColor = if (darkTheme) Color(0xFF8BB8FF) else Color(0xFF2F7FEA)
    val textStyle = TextStyle(
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // 以最长说明固定提示块高度，切换识别方式时不会推动下方内容。
        Text(
            text = OOBE_ONLINE_RECOGNITION_DESCRIPTION,
            style = textStyle,
            color = Color.Transparent,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Text(
            text = text,
            style = textStyle,
            color = contentColor,
        )
    }
}

@Composable
private fun PermissionsStep(
    hasNotificationPermission: Boolean,
    isIgnoringBattery: Boolean,
    hasUsageStatsPermission: Boolean,
    shizukuReady: Boolean,
    performHaptic: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    val context = LocalContext.current

    OobePermissionContent {
        OobePermissionRow(
            title = "通知权限",
            granted = hasNotificationPermission,
            performHaptic = performHaptic,
            onClick = onRequestNotificationPermission,
        )

        OobeDeferredHint("您可以稍后设置以下权限")

        OobePermissionRow(
            title = "忽略电池优化",
            granted = isIgnoringBattery,
            performHaptic = performHaptic,
            onClick = {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        OobePermissionRow(
            title = "应用使用情况",
            granted = hasUsageStatsPermission,
            performHaptic = performHaptic,
            onClick = {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        OobePermissionRow(
            title = "Shizuku 授权",
            granted = shizukuReady,
            performHaptic = performHaptic,
            onClick = {
                if (Shizuku.pingBinder()) {
                    try {
                        Shizuku.requestPermission(1001)
                    } catch (_: Exception) {
                    }
                }
            },
        )
    }
}

@Composable
private fun OobePermissionContent(
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            content = {
                content()
                Spacer(Modifier.height(96.dp))
            },
        )
    }
}

@SuppressLint("WrongConstant")
@Composable
private fun FeaturesStep() {
    val context = LocalContext.current

    OobePageContent {
        OobeSectionLabel(
            title = "识别方式",
            summary = "澎湃记支持以下四种入口，点击任一项目可查看操作步骤。",
        )
        OobeFeatureRow(
            title = "截图识别",
            subtitle = "常用做法",
            description = "通过控制中心磁贴快速截图识别，识别完成后自动通知你取餐码信息。",
            steps = listOf(
                "下拉打开控制中心",
                "点击「截图识别」磁贴",
                "应用自动截图并识别",
                "收到取餐码通知"
            ),
            actionLabel = "添加快捷设置",
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val statusBarManager = context.getSystemService(Context.STATUS_BAR_SERVICE) as StatusBarManager
                    statusBarManager.requestAddTileService(
                        ComponentName(context, CaptureTileService::class.java),
                        "截图识别",
                        Icon.createWithResource(context, R.drawable.note),
                        {},
                        {}
                    )
                }
            }
        )
        OobeFeatureRow(
            title = "划词识别",
            subtitle = "识别率最高",
            description = "在特定页面选择文字后，点击右上角菜单选择「识别取餐码」，适合大量文字或短信记录。",
            steps = listOf(
                "长按选择文字",
                "点击右上角「...」菜单",
                "选择「识别取餐码」",
                "自动提取并保存"
            ),
            actionLabel = null,
            onAction = null
        )
        OobeFeatureRow(
            title = "分享识别",
            subtitle = "更方便",
            description = "部分机型截图后可以进行分享，点击分享/发送后选择澎湃记，即可自动识别。",
            steps = listOf(
                "截图后点击分享按钮",
                "在分享列表中选择澎湃记",
                "应用自动识别截图内容",
                "生成取餐码通知"
            ),
            actionLabel = null,
            onAction = null
        )
        OobeFeatureRow(
            title = "音量键快捷触发",
            subtitle = "快速触发",
            description = "开启后可通过无障碍快捷方式触发识别，适合单手操作。",
            steps = listOf(
                "在第一步里开启音量键快捷触发开关",
                "按音量键呼出无障碍快捷方式",
                "应用自动执行截图识别",
                "若当前不可用，请前往 设置-截图方式 再次设置"
            ),
            actionLabel = null,
            onAction = null
        )
    }
}

@Composable
private fun OobePageContent(
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Spacer(Modifier.height(4.dp))
                content()
                Spacer(Modifier.height(96.dp))
            },
        )
    }
}

@Composable
private fun OobeSectionLabel(
    title: String,
    summary: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(2.dp),
                ),
        )
        Spacer(Modifier.width(11.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = summary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OobeDeferredHint(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun OobePermissionRow(
    title: String,
    granted: Boolean,
    performHaptic: () -> Unit,
    onClick: () -> Unit,
) {
    OobeSettingSurface(
        onClick = {
            performHaptic()
            onClick()
        },
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (granted) {
            Spacer(Modifier.width(12.dp))
            Icon(
                painter = painterResource(R.drawable.oobe_picker_check),
                contentDescription = "已授权",
                tint = Color.Unspecified,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun OobeSettingSurface(
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .oobeMiuixPressFeedback(
                shape = shape,
                enabled = enabled,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun OobeFeatureRow(
    title: String,
    subtitle: String,
    description: String,
    steps: List<String>,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    val haptic = LocalHapticFeedback.current
    val motionScheme = MaterialTheme.motionScheme
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motionScheme.defaultSpatialSpec<Float>(),
        label = "oobe_feature_arrow",
    )
    val cardShape = RoundedCornerShape(15.dp)
    Surface(
        shape = cardShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .oobeMiuixPressFeedback(
                shape = cardShape,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    expanded = !expanded
                },
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 13.dp, end = 12.dp, bottom = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = description,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起步骤" else "查看步骤",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(arrowRotation),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec<Float>()) +
                    expandVertically(
                        animationSpec = motionScheme.defaultSpatialSpec<IntSize>(),
                        expandFrom = Alignment.Top,
                    ),
                exit = fadeOut(animationSpec = motionScheme.defaultEffectsSpec<Float>()) +
                    shrinkVertically(
                        animationSpec = motionScheme.defaultSpatialSpec<IntSize>(),
                        shrinkTowards = Alignment.Top,
                    ),
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                    Column(
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        steps.forEachIndexed { index, step ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF277AF7),
                                    modifier = Modifier.width(22.dp),
                                )
                                Text(
                                    text = step,
                                    fontSize = 14.sp,
                                    lineHeight = 19.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (actionLabel != null && onAction != null) {
                            val actionShape = RoundedCornerShape(15.dp)
                            Surface(
                                shape = actionShape,
                                color = Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .oobeMiuixPressFeedback(
                                        shape = actionShape,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onAction()
                                        },
                                    ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 44.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color(0xFF277AF7),
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = actionLabel,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF277AF7),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 辅助函数
private fun checkUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun checkBatteryOptimization(context: Context): Boolean {
    @Suppress("DEPRECATION")
    return try {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    } catch (e: Exception) {
        false
    }
}

private fun isShizukuReady(): Boolean {
    return try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }
}
