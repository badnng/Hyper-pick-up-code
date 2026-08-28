package com.Badnng.moe.ui.screen.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import android.os.Build
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.Badnng.moe.R
import com.Badnng.moe.helper.AppLogger
import com.Badnng.moe.helper.NotificationHelper
import com.Badnng.moe.helper.UpdateHelper
import com.Badnng.moe.helper.UpdateInfo
import com.Badnng.moe.privacy.PrivacyConsent
import com.Badnng.moe.recognition.OnlineRecognitionPreferences
import com.Badnng.moe.ui.component.PrivacyPolicyBottomSheet
import com.Badnng.moe.ui.component.UpdateSheet
import com.Badnng.moe.ui.component.UpdateProgressSheet
import com.Badnng.moe.ui.component.GroupPosition
import com.Badnng.moe.ui.component.Md3eNavigationRailExpandButton
import com.Badnng.moe.ui.component.PreferenceSection
import com.Badnng.moe.ui.component.SettingsGroup
import com.Badnng.moe.ui.component.SettingsGroupItem
import com.Badnng.moe.ui.component.SettingsListItem
import com.Badnng.moe.ui.miuix.rememberMiuixStyle
import com.Badnng.moe.ui.miuix.miuixScrollModifiers
import com.Badnng.moe.ui.miuix.MiuixSettingsLazyColumn
import com.Badnng.moe.ui.miuix.MiuixDevicePerformanceTier
import com.Badnng.moe.ui.miuix.MiuixVisualEffectsPolicy
import com.Badnng.moe.ui.miuix.rememberMiuixBlurAllowed
import com.Badnng.moe.ui.miuix.rememberMiuixDevicePerformanceTier
import com.Badnng.moe.ui.miuix.rememberMiuixIconColorMixingBackdrop
import com.Badnng.moe.ui.oobe.OobeCarvedLogoView
import com.Badnng.moe.ui.screen.miuix.MiuixNavigationRailExpandButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val DEVELOPER_OPTIONS_TAP_THRESHOLD = 7

@Composable
fun AboutSettingsContent(
    performHaptic: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    scrollState: androidx.compose.foundation.ScrollState = androidx.compose.foundation.rememberScrollState(),
    miuixLazyListState: LazyListState? = null,
    onNavigateToCredits: () -> Unit = {},
    onNavigateToSponsor: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToDeveloperOptions: () -> Unit = {},
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior? = null,
    onBack: () -> Unit = {},
    supportingPane: Boolean = false,
    showBackButton: Boolean = true,
    onExpandNavigationRail: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val uriHandler = LocalUriHandler.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val versionName = remember { getVersionName(context) }
    val versionCode = remember { getVersionCode(context) }

    var showUpdateDialog by remember { mutableStateOf(false) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var isPaused by remember { mutableStateOf(UpdateHelper.isPaused) }
    var isChecking by remember { mutableStateOf(false) }
    var isStartingDownload by remember { mutableStateOf(false) }
    var versionTapCount by remember { mutableIntStateOf(0) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var privacyAccepted by remember { mutableStateOf(PrivacyConsent.isAccepted(prefs)) }
    /** 隐私/联网更新偏好变化时自增，驱动 networkUpdateEnabled 及时重算，让检查更新按钮立即显示。 */
    var privacyPrefsRevision by remember { mutableIntStateOf(0) }

    androidx.compose.runtime.DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PrivacyConsent.ACCEPTED_KEY ||
                key == PrivacyConsent.NETWORK_UPDATE_ENABLED_KEY
            ) {
                privacyAccepted = PrivacyConsent.isAccepted(prefs)
                privacyPrefsRevision++
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // 显式读取 revision，使隐私/联网更新偏好变化时触发重组
    @Suppress("UNUSED_EXPRESSION")
    privacyPrefsRevision

    val coroutineScope = rememberCoroutineScope()
    val notificationHelper = remember(appContext) { NotificationHelper(appContext) }
    val isMiuix = rememberMiuixStyle()
    val onVersionTap = {
        performHaptic()
        val nextCount = versionTapCount + 1
        if (nextCount >= DEVELOPER_OPTIONS_TAP_THRESHOLD) {
            versionTapCount = 0
            Toast.makeText(context, "已进入开发者选项", Toast.LENGTH_SHORT).show()
            onNavigateToDeveloperOptions()
        } else {
            versionTapCount = nextCount
        }
    }

    // 从更新下载通知进入时，自动弹出更新进度弹窗
    LaunchedEffect(Unit) {
        if (prefs.getBoolean("show_update_download", false)) {
            prefs.edit().putBoolean("show_update_download", false).apply()
            if (UpdateHelper.isDownloading && UpdateHelper.currentDownloadingVersion != null) {
                updateInfo = UpdateHelper.currentDownloadingVersion
                downloadProgress = UpdateHelper.currentProgress
                isPaused = UpdateHelper.isPaused
                showProgressDialog = true
            }
        }
    }

    val networkUpdateEnabled = privacyAccepted && PrivacyConsent.isNetworkUpdateEnabled(prefs)
    val updateChannel = prefs.getString("update_channel", "stable") ?: "stable"

    val checkUpdateAction: () -> Unit = {
        performHaptic()
        if (!isChecking) {
            isChecking = true
            coroutineScope.launch {
                val info = UpdateHelper.checkUpdate(updateChannel == "dev")
                isChecking = false
                if (info != null) {
                    val localVersion = UpdateHelper.getCurrentVersionCode(context)
                    if (info.versionCode > localVersion) {
                        updateInfo = info
                        if (UpdateHelper.isDownloading) {
                            if (UpdateHelper.currentDownloadingVersion?.versionCode == info.versionCode) {
                                downloadProgress = UpdateHelper.currentProgress
                                isPaused = UpdateHelper.isPaused
                                showProgressDialog = true
                            } else {
                                Toast.makeText(context, "已有其他版本正在下载", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            // 缓存 APK 也必须先由用户确认，不能在检查更新后直接唤起安装器。
                            showUpdateDialog = true
                        }
                    } else {
                        UpdateHelper.showNoUpdateToast(context)
                    }
                } else {
                    Toast.makeText(context, "检查更新失败，请稍后重试", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (isMiuix) {
        MiuixAboutPage(
            versionName = versionName,
            versionCode = versionCode,
            networkUpdateEnabled = networkUpdateEnabled,
            isChecking = isChecking,
            onCheckUpdate = checkUpdateAction,
            performHaptic = performHaptic,
            topPadding = topPadding,
            lazyListState = miuixLazyListState,
            onVersionTap = onVersionTap,
            onNavigateToCredits = onNavigateToCredits,
            onNavigateToSponsor = onNavigateToSponsor,
            onNavigateToBackup = onNavigateToBackup,
            privacyAccepted = privacyAccepted,
            onShowPrivacyPolicy = { showPrivacyPolicy = true },
            onBack = onBack,
            supportingPane = supportingPane,
            bottomPadding = bottomPadding,
            showBackButton = showBackButton,
            onExpandNavigationRail = onExpandNavigationRail,
        )
    } else {
        Md3eAboutPage(
            versionName = versionName,
            versionCode = versionCode,
            networkUpdateEnabled = networkUpdateEnabled,
            isChecking = isChecking,
            onCheckUpdate = checkUpdateAction,
            performHaptic = performHaptic,
            topPadding = topPadding,
            scrollState = scrollState,
            onVersionTap = onVersionTap,
            onNavigateToSponsor = onNavigateToSponsor,
            onNavigateToBackup = onNavigateToBackup,
            privacyAccepted = privacyAccepted,
            onShowPrivacyPolicy = { showPrivacyPolicy = true },
            bottomPadding = bottomPadding,
            onExpandNavigationRail = onExpandNavigationRail,
        )
    }

    PrivacyPolicyBottomSheet(
        show = showPrivacyPolicy,
        isMiuix = isMiuix,
        isAccepted = privacyAccepted,
        onDismiss = {
            performHaptic()
            showPrivacyPolicy = false
        },
        onAccept = {
            performHaptic()
            PrivacyConsent.accept(prefs)
            privacyAccepted = true
            Toast.makeText(
                context,
                "已同意用户协议与隐私说明",
                Toast.LENGTH_SHORT,
            ).show()
        },
        onRevoke = {
            performHaptic()
            PrivacyConsent.revoke(prefs)
            prefs.edit()
                .putString(
                    OnlineRecognitionPreferences.MODE_KEY,
                    OnlineRecognitionPreferences.MODE_OFFLINE,
                )
                .apply()
            privacyAccepted = false
            Toast.makeText(
                context,
                "已撤销同意，在线识别与联网更新已关闭",
                Toast.LENGTH_SHORT,
            ).show()
        },
    )

    // 更新弹窗
    updateInfo?.let { info ->
        UpdateSheet(
            show = showUpdateDialog,
            updateInfo = info,
            onDismiss = { showUpdateDialog = false },
            onInstall = {
                if (!isStartingDownload) {
                    showUpdateDialog = false
                    val cachedFile = UpdateHelper.getDownloadedFile(info)
                    if (cachedFile != null) {
                        UpdateHelper.installUpdate(appContext, cachedFile)
                    } else if (UpdateHelper.isDownloading) {
                        if (UpdateHelper.currentDownloadingVersion?.versionCode == info.versionCode) {
                            downloadProgress = UpdateHelper.currentProgress
                            isPaused = UpdateHelper.isPaused
                            showProgressDialog = true
                        } else {
                            Toast.makeText(context, "已有其他版本正在下载", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        isStartingDownload = true
                        showProgressDialog = true
                        downloadProgress = null
                        isPaused = false
                        prefs.edit().putBoolean("show_update_download", true).apply()
                        notificationHelper.showUpdateDownloadNotification(info.versionName, null, false)
                        val started = UpdateHelper.startDownload(
                            context = appContext,
                            updateInfo = info,
                            onProgress = {
                                notificationHelper.showUpdateDownloadNotification(
                                    info.versionName,
                                    it,
                                    UpdateHelper.isPaused,
                                )
                            },
                            onComplete = { file ->
                                isStartingDownload = false
                                showProgressDialog = false
                                isPaused = false
                                prefs.edit().putBoolean("show_update_download", false).apply()
                                notificationHelper.cancelUpdateDownloadNotification()
                                if (file != null) {
                                    UpdateHelper.installUpdate(appContext, file)
                                } else {
                                    Toast.makeText(
                                        appContext,
                                        "下载失败：${UpdateHelper.lastDownloadError ?: "请稍后重试"}",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                        if (!started) {
                            isStartingDownload = false
                            showProgressDialog = false
                            prefs.edit().putBoolean("show_update_download", false).apply()
                            notificationHelper.cancelUpdateDownloadNotification()
                            Toast.makeText(context, "已有更新正在下载", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }

    // 下载进度弹窗
    if (showProgressDialog) {
        LaunchedEffect(showProgressDialog, isStartingDownload) {
            while (showProgressDialog) {
                downloadProgress = UpdateHelper.currentProgress
                isPaused = UpdateHelper.isPaused
                if (!isStartingDownload && !UpdateHelper.isDownloading) break
                kotlinx.coroutines.delay(100)
            }
        }
    }
    updateInfo?.let { info ->
        UpdateProgressSheet(
            show = showProgressDialog,
            updateInfo = info,
            progress = downloadProgress,
            isPaused = isPaused,
            onPause = {
                isPaused = true
                UpdateHelper.pauseDownload()
                notificationHelper.showUpdateDownloadNotification(info.versionName, downloadProgress, true)
            },
            onResume = {
                isPaused = false
                UpdateHelper.resumeDownload()
                notificationHelper.showUpdateDownloadNotification(info.versionName, downloadProgress, false)
            },
            onDismiss = {
                showProgressDialog = false
                notificationHelper.showUpdateDownloadNotification(info.versionName, downloadProgress, isPaused)
            }
        )
    }
}

// ═══════════════════════════════════════════
//  Miuix 关于页面（参考示例项目 AboutPage）
// ═══════════════════════════════════════════

// ═══════════════════════════════════════════
//  Miuix 关于页面（自包含 Scaffold，照搬示例项目 AboutPage）
// ═══════════════════════════════════════════

@Composable
private fun MiuixAboutPage(
    versionName: String,
    versionCode: Long,
    networkUpdateEnabled: Boolean,
    isChecking: Boolean,
    onCheckUpdate: () -> Unit,
    performHaptic: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
    lazyListState: LazyListState?,
    onVersionTap: () -> Unit,
    onNavigateToCredits: () -> Unit,
    onNavigateToSponsor: () -> Unit,
    onNavigateToBackup: () -> Unit,
    privacyAccepted: Boolean,
    onShowPrivacyPolicy: () -> Unit,
    onBack: () -> Unit = {},
    supportingPane: Boolean = false,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    showBackButton: Boolean = true,
    onExpandNavigationRail: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val topAppBarScrollBehavior = top.yukonga.miuix.kmp.basic.MiuixScrollBehavior()
    val fallbackLazyListState = rememberLazyListState()
    val resolvedLazyListState = lazyListState ?: fallbackLazyListState
    val density = LocalDensity.current
    val logoTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 120.dp
    val versionFadeEndPx = with(density) { 56.dp.toPx() }
    val nameFadeStartPx = with(density) { 48.dp.toPx() }
    val nameFadeEndPx = with(density) { 112.dp.toPx() }
    val iconFadeStartPx = with(density) { 96.dp.toPx() }
    val iconFadeEndPx = with(density) { 176.dp.toPx() }
    val headerCollapseDistance = 176.dp

    var logoHeightPx by rememberSaveable(density.density, density.fontScale) {
        mutableIntStateOf(with(density) { 300.dp.roundToPx() })
    }
    var aboutContentHeightPx by rememberSaveable(density.density, density.fontScale) {
        mutableIntStateOf(0)
    }
    val logoHeightDp = with(density) { logoHeightPx.toDp() }
    val aboutContentHeightDp = with(density) { aboutContentHeightPx.toDp() }

    val scrollProgress by remember(iconFadeEndPx) {
        derivedStateOf {
            when {
                resolvedLazyListState.firstVisibleItemIndex > 0 -> 1f
                iconFadeEndPx > 0f ->
                    (resolvedLazyListState.firstVisibleItemScrollOffset / iconFadeEndPx).coerceIn(0f, 1f)
                else -> 0f
            }
        }
    }

    val backdrop = com.Badnng.moe.ui.miuix.rememberMiuixBackdrop()
    val sheetBackdrop = com.Badnng.moe.ui.miuix.rememberMiuixBackdrop()
    val fullBlurAllowed = rememberMiuixBlurAllowed()
    val performanceTier = rememberMiuixDevicePerformanceTier()
    val collapsed by remember { derivedStateOf { scrollProgress >= 0.999f } }
    val blurActive by remember(backdrop) {
        derivedStateOf { backdrop != null && scrollProgress >= 0.999f }
    }
    val aboutTopBar: @Composable () -> Unit = {
        val barColor = if (blurActive) {
            Color.Transparent
        } else {
            if (collapsed) MiuixTheme.colorScheme.surface else Color.Transparent
        }
        val titleColor = MiuixTheme.colorScheme.onSurface.copy(
            alpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f),
        )
        val topBarContent: @Composable () -> Unit = {
            top.yukonga.miuix.kmp.basic.SmallTopAppBar(
                title = "关于",
                scrollBehavior = topAppBarScrollBehavior,
                color = barColor,
                titleColor = titleColor,
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    when {
                        onExpandNavigationRail != null -> {
                            MiuixNavigationRailExpandButton(onClick = onExpandNavigationRail)
                        }
                        showBackButton -> {
                            top.yukonga.miuix.kmp.basic.IconButton(onClick = {
                                performHaptic()
                                onBack()
                            }) {
                                top.yukonga.miuix.kmp.basic.Icon(
                                    if (supportingPane) {
                                        MiuixIcons.Regular.Close
                                    } else {
                                        MiuixIcons.Regular.Back
                                    },
                                    contentDescription = if (supportingPane) "关闭" else "返回",
                                )
                            }
                        }
                    }
                },
            )
        }

        if (!collapsed) {
            // 首屏只覆盖透明导航按钮，让动态背景延伸到页面顶部。
            topBarContent()
        } else {
            com.Badnng.moe.ui.miuix.MiuixBlurredBar(
                backdrop = backdrop,
                blurEnabled = blurActive,
                content = topBarContent,
            )
        }
    }
    val headerScrollOffsetPx by remember {
        derivedStateOf {
            if (resolvedLazyListState.firstVisibleItemIndex > 0) {
                Float.POSITIVE_INFINITY
            } else {
                resolvedLazyListState.firstVisibleItemScrollOffset.toFloat()
            }
        }
    }
    val versionFadeProgress by remember(versionFadeEndPx) {
        derivedStateOf {
            (headerScrollOffsetPx / versionFadeEndPx).coerceIn(0f, 1f)
        }
    }
    val nameFadeProgress by remember(nameFadeStartPx, nameFadeEndPx) {
        derivedStateOf {
            ((headerScrollOffsetPx - nameFadeStartPx) / (nameFadeEndPx - nameFadeStartPx))
                .coerceIn(0f, 1f)
        }
    }
    val iconFadeProgress by remember(iconFadeStartPx, iconFadeEndPx) {
        derivedStateOf {
            ((headerScrollOffsetPx - iconFadeStartPx) / (iconFadeEndPx - iconFadeStartPx))
                .coerceIn(0f, 1f)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        top.yukonga.miuix.kmp.basic.Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (sheetBackdrop != null) Modifier.layerBackdrop(sheetBackdrop) else Modifier,
                ),
            topBar = { aboutTopBar() },
        ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            val pageBottomPadding = maxOf(
                innerPadding.calculateBottomPadding(),
                bottomPadding,
            )
            val textBackdrop = rememberMiuixIconColorMixingBackdrop()
            val isHyperOsDevice = remember(context.applicationContext) {
                MiuixVisualEffectsPolicy.isHyperOsDevice()
            }
            val contentBlurBackdrop = textBackdrop.takeIf { fullBlurAllowed }
            val appPrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
            var themeMode by remember { mutableStateOf(appPrefs.getString("theme_mode", "system") ?: "system") }
            DisposableEffect(appPrefs) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                    if (key == "theme_mode") themeMode = p.getString(key, "system") ?: "system"
                }
                appPrefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { appPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            val isInDark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            val cardBlend = remember(isInDark) {
                if (isInDark) {
                    listOf(
                        top.yukonga.miuix.kmp.blur.BlendColorEntry(
                            Color(0x4DA9A9A9),
                            top.yukonga.miuix.kmp.blur.BlurBlendMode.Luminosity,
                        ),
                        top.yukonga.miuix.kmp.blur.BlendColorEntry(
                            Color(0x1A9C9C9C),
                            top.yukonga.miuix.kmp.blur.BlurBlendMode.PlusDarker,
                        ),
                    )
                } else {
                    listOf(
                        top.yukonga.miuix.kmp.blur.BlendColorEntry(
                            Color(0x340034F9),
                            top.yukonga.miuix.kmp.blur.BlurBlendMode.Overlay,
                        ),
                        top.yukonga.miuix.kmp.blur.BlendColorEntry(
                            Color(0xB3FFFFFF),
                            top.yukonga.miuix.kmp.blur.BlurBlendMode.HardLight,
                        ),
                    )
                }
            }
            val aboutCardModifier = Modifier
                .padding(horizontal = 12.dp)
                .then(
                    if (contentBlurBackdrop != null) {
                        Modifier.textureBlur(
                            backdrop = contentBlurBackdrop,
                            shape = RoundedCornerShape(16.dp),
                            blurRadius = 60f,
                            colors = top.yukonga.miuix.kmp.blur.BlurDefaults.blurColors(
                                blendColors = cardBlend,
                                brightness = 0f,
                                contrast = 1f,
                                saturation = 1f,
                            ),
                        )
                    } else {
                        Modifier
                    }
                )
            val aboutCardColors = CardDefaults.defaultColors(
                if (contentBlurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
                Color.Transparent,
            )
            val logoBlend = remember(isInDark) {
                if (isInDark) {
                    listOf(
                        top.yukonga.miuix.kmp.blur.BlendColorEntry(Color(0xe6a1a1a1), top.yukonga.miuix.kmp.blur.BlurBlendMode.ColorDodge),
                        top.yukonga.miuix.kmp.blur.BlendColorEntry(Color(0x4de6e6e6), top.yukonga.miuix.kmp.blur.BlurBlendMode.LinearLight),
                        top.yukonga.miuix.kmp.blur.BlendColorEntry(Color(0xff1af500), top.yukonga.miuix.kmp.blur.BlurBlendMode.Lab),
                    )
                } else {
                    listOf(
                        top.yukonga.miuix.kmp.blur.BlendColorEntry(Color(0xcc4a4a4a), top.yukonga.miuix.kmp.blur.BlurBlendMode.ColorBurn),
                        top.yukonga.miuix.kmp.blur.BlendColorEntry(Color(0xff4f4f4f), top.yukonga.miuix.kmp.blur.BlurBlendMode.LinearLight),
                        top.yukonga.miuix.kmp.blur.BlendColorEntry(Color(0xff1af200), top.yukonga.miuix.kmp.blur.BlurBlendMode.Lab),
                    )
                }
            }

            com.Badnng.moe.ui.miuix.effect.BgEffectBackground(
                dynamicBackground = true,
                modifier = Modifier.fillMaxSize(),
                isFullSize = true,
                alpha = { 1f - scrollProgress },
                bgModifier = if (textBackdrop != null) Modifier.layerBackdrop(textBackdrop) else Modifier,
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val listTopPadding = innerPadding.calculateTopPadding()
                    val listBottomPadding = pageBottomPadding + 32.dp
                    val logoSpacerHeight =
                        (logoHeightDp - listTopPadding + 8.dp).coerceAtLeast(0.dp)
                    val collapseReserveHeight = (
                        maxHeight + headerCollapseDistance + 1.dp -
                            listTopPadding -
                            listBottomPadding -
                            logoSpacerHeight -
                            aboutContentHeightDp
                        ).coerceAtLeast(0.dp)

                    // 可滚动内容（先声明，Z 轴较低）
                    LazyColumn(
                        state = resolvedLazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .miuixScrollModifiers(topAppBarScrollBehavior),
                        contentPadding = PaddingValues(
                            top = listTopPadding,
                            bottom = listBottomPadding,
                        ),
                    ) {
                        item(key = "logoSpacer") {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(logoSpacerHeight)
                            )
                        }

                        item(key = "aboutContent") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onSizeChanged { size ->
                                        aboutContentHeightPx = size.height
                                    },
                            ) {
                                Card(
                                    modifier = aboutCardModifier,
                                    colors = aboutCardColors,
                                ) {
                                    ArrowPreference(
                                        title = "项目地址",
                                        endActions = {
                                            MiuixText(
                                                text = "GitHub",
                                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                            )
                                        },
                                        onClick = {
                                            performHaptic()
                                            uriHandler.openUri("https://github.com/badnng/Hyper-pick-up-code")
                                        }
                                    )
                                    ArrowPreference(
                                        title = "开源许可证",
                                        endActions = {
                                            MiuixText(
                                                text = "AGPL-3.0",
                                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                            )
                                        },
                                        onClick = {
                                            performHaptic()
                                            uriHandler.openUri("https://github.com/badnng/Hyper-pick-up-code/blob/master/LICENSE")
                                        }
                                    )
                                    ArrowPreference(
                                        title = "赞助",
                                        summary = "支持项目持续更新",
                                        onClick = {
                                            performHaptic()
                                            onNavigateToSponsor()
                                        },
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Card(
                                    modifier = aboutCardModifier,
                                    colors = aboutCardColors,
                                ) {
                                    ArrowPreference(
                                        title = "备份与恢复",
                                        summary = "选择内容、预检并恢复应用数据",
                                        onClick = {
                                            performHaptic()
                                            onNavigateToBackup()
                                        },
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Card(
                                    modifier = aboutCardModifier,
                                    colors = aboutCardColors,
                                ) {
                                    MiuixAboutLogSection(performHaptic = performHaptic)
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Card(
                                    modifier = aboutCardModifier,
                                    colors = aboutCardColors,
                                ) {
                                    ArrowPreference(
                                        title = "致谢",
                                        summary = "开源项目与贡献者",
                                        onClick = {
                                            performHaptic()
                                            onNavigateToCredits()
                                        }
                                    )
                                    ArrowPreference(
                                        title = "用户协议与隐私说明",
                                        summary = if (privacyAccepted) {
                                            "已同意，可查看或撤销"
                                        } else {
                                            "未同意，点击查看"
                                        },
                                        onClick = {
                                            performHaptic()
                                            onShowPrivacyPolicy()
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                        if (collapseReserveHeight > 0.dp) {
                            item(key = "headerCollapseReserve") {
                                Spacer(modifier = Modifier.height(collapseReserveHeight))
                            }
                        }
                    }

                    // Logo + 应用名 + 版本号 + 检查更新（后声明，Z 轴更高，可接收点击）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { size ->
                                logoHeightPx = size.height
                            }
                            .padding(
                                top = logoTopPadding,
                                start = 16.dp,
                                end = 16.dp,
                            )
                            .align(Alignment.TopCenter),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(88.dp)
                                .graphicsLayer {
                                    clip = true
                                    shape = RoundedCornerShape(24.dp)
                                    alpha = 1f - iconFadeProgress
                                    scaleX = 1f - (iconFadeProgress * 0.05f)
                                    scaleY = 1f - (iconFadeProgress * 0.05f)
                                }
                        ) {
                            if (
                                performanceTier == MiuixDevicePerformanceTier.Low ||
                                (textBackdrop == null && !isHyperOsDevice)
                            ) {
                                AndroidView(
                                    factory = { logoContext ->
                                        ImageView(logoContext).apply {
                                            scaleType = ImageView.ScaleType.FIT_XY
                                            importantForAccessibility =
                                                android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                                        }
                                    },
                                    update = { logoView ->
                                        logoView.setImageResource(R.mipmap.ic_launcher)
                                    },
                                    modifier = Modifier
                                        .fillMaxSize(),
                                )
                            } else {
                                val fallbackLogoColor = MiuixTheme.colorScheme.onBackground.toArgb()
                                AndroidView(
                                    factory = { logoContext ->
                                        OobeCarvedLogoView(logoContext)
                                    },
                                    update = { logoView ->
                                        logoView.setBaseColor(
                                            if (textBackdrop != null) {
                                                android.graphics.Color.WHITE
                                            } else {
                                                fallbackLogoColor
                                            },
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (textBackdrop != null) {
                                                Modifier.textureBlur(
                                                    backdrop = textBackdrop,
                                                    shape = RoundedCornerShape(24.dp),
                                                    blurRadius = 150f,
                                                    colors = top.yukonga.miuix.kmp.blur.BlurDefaults.blurColors(
                                                        blendColors = logoBlend,
                                                    ),
                                                    contentBlendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                                                )
                                            } else {
                                                Modifier
                                            },
                                        ),
                                )
                            }
                        }
                        // 应用名（带 textureBlur 渲染，contentBlendMode = DstIn 让模糊只作用于文字像素）
                        MiuixText(
                            modifier = Modifier
                                .padding(top = 12.dp, bottom = 5.dp)
                                .graphicsLayer {
                                    alpha = 1f - nameFadeProgress
                                    scaleX = 1f - (nameFadeProgress * 0.05f)
                                    scaleY = 1f - (nameFadeProgress * 0.05f)
                                }
                                .then(
                                    if (textBackdrop != null) {
                                        Modifier.textureBlur(
                                            backdrop = textBackdrop,
                                            shape = RoundedCornerShape(16.dp),
                                            blurRadius = 150f,
                                            colors = top.yukonga.miuix.kmp.blur.BlurDefaults.blurColors(
                                                blendColors = logoBlend,
                                            ),
                                            contentBlendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                                        )
                                    } else {
                                        Modifier
                                    }
                                ),
                            text = "澎湃记",
                            color = MiuixTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 35.sp
                        )
                        MiuixText(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = 1f - versionFadeProgress
                                    scaleX = 1f - (versionFadeProgress * 0.05f)
                                    scaleY = 1f - (versionFadeProgress * 0.05f)
                                }
                                .clickable(
                                    enabled = versionFadeProgress < 0.95f,
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = onVersionTap,
                                ),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            text = "v$versionName ($versionCode)",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        // 检查更新按钮（和 logo 一起淡出缩小）
                        if (networkUpdateEnabled) {
                            Spacer(modifier = Modifier.height(24.dp))
                            MiuixButton(
                                onClick = onCheckUpdate,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = 1f - versionFadeProgress
                                        scaleX = 1f - (versionFadeProgress * 0.05f)
                                        scaleY = 1f - (versionFadeProgress * 0.05f)
                                    },
                                enabled = !isChecking,
                                colors = MiuixButtonDefaults.buttonColorsPrimary()
                            ) {
                                if (isChecking) {
                                    InfiniteProgressIndicator(modifier = Modifier.size(18.dp), color = MiuixTheme.colorScheme.onPrimary)
                                    Spacer(Modifier.width(8.dp))
                                } else {
                                    MiuixIcon(MiuixIcons.Regular.Update, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                }
                                MiuixText(if (isChecking) "检查中..." else "检查更新")
                            }
                        }
                    }
                }
            }

            }
        }

        // BottomSheet 模糊背景（实时跟随 Sheet 拖拽进度）
        val animatedBlurAlpha = com.Badnng.moe.ui.component.BlurState.progress.floatValue
        com.Badnng.moe.ui.miuix.MiuixModalScrim(
            backdrop = sheetBackdrop,
            progress = animatedBlurAlpha,
        )
    }
}

// ═══════════════════════════════════════════
//  MD3E 关于页面
// ═══════════════════════════════════════════

@Composable
private fun Md3eAboutPage(
    versionName: String,
    versionCode: Long,
    networkUpdateEnabled: Boolean,
    isChecking: Boolean,
    onCheckUpdate: () -> Unit,
    performHaptic: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
    scrollState: androidx.compose.foundation.ScrollState,
    onVersionTap: () -> Unit,
    onNavigateToSponsor: () -> Unit,
    onNavigateToBackup: () -> Unit,
    privacyAccepted: Boolean,
    onShowPrivacyPolicy: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onExpandNavigationRail: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val md3LogoColor = MaterialTheme.colorScheme.primary.toArgb()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Spacer(Modifier.height(topPadding))

        // 图标
        Surface(
            modifier = Modifier
                .size(86.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { logoContext -> OobeCarvedLogoView(logoContext) },
                    update = { logoView ->
                        logoView.setBaseColor(md3LogoColor)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(text = "澎湃记", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            text = "版本 $versionName ($versionCode)",
            modifier = Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onVersionTap,
            ),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )

        Spacer(Modifier.height(24.dp))

        // 检查更新按钮
        if (networkUpdateEnabled) {
            Button(
                onClick = onCheckUpdate,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !isChecking
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.Default.SystemUpdate, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isChecking) "检查中..." else "检查更新")
            }
        }

        Spacer(Modifier.height(24.dp))

        // 项目与许可证
        PreferenceSection(title = "项目") {
            SettingsGroup {
                SettingsGroupItem(
                    title = "项目地址",
                    description = "GitHub · badnng/Hyper-pick-up-code",
                    position = GroupPosition.First,
                    onClick = {
                        performHaptic()
                        uriHandler.openUri("https://github.com/badnng/Hyper-pick-up-code")
                    }
                )
                SettingsGroupItem(
                    title = "开源许可证",
                    description = "GNU AGPL v3.0",
                    position = GroupPosition.Middle,
                    onClick = {
                        performHaptic()
                        uriHandler.openUri("https://github.com/badnng/Hyper-pick-up-code/blob/master/LICENSE")
                    }
                )
                SettingsGroupItem(
                    title = "赞助",
                    description = "支持项目持续更新",
                    position = GroupPosition.Last,
                    onClick = {
                        performHaptic()
                        onNavigateToSponsor()
                    },
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        PreferenceSection(title = "数据") {
            SettingsGroup {
                SettingsGroupItem(
                    title = "备份与恢复",
                    description = "选择内容、预检并恢复应用数据",
                    position = GroupPosition.Single,
                    onClick = {
                        performHaptic()
                        onNavigateToBackup()
                    },
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // 日志导出
        Md3eLogSection(performHaptic = performHaptic)

        Spacer(Modifier.height(32.dp))

        // 致谢
        val credits = listOf(
            Triple("Jetpack Compose", "现代化声明式 UI 框架", "https://developer.android.com/jetpack/compose"),
            Triple("Material Design 3", "Google 现代设计语言规范", "https://m3.material.io"),
            Triple("ML Kit", "Google 强大的设备端机器学习 SDK", "https://developers.google.com/ml-kit"),
            Triple("Shizuku", "利用系统 API 实现高级权限调用", "https://shizuku.rikka.app"),
            Triple("ZXing", "高效的二维码生成与处理库", "https://github.com/zxing/zxing"),
            Triple("Room", "官方高性能 SQLite 数据库封装", "https://developer.android.com/training/data-storage/room"),
            Triple("Coil", "现代化的 Android 图片加载库", "https://coil-kt.github.io/coil/"),
            Triple("Kyant Backdrop", "优雅的毛玻璃与层级模糊效果实现", "https://github.com/Kyant0/AndroidLiquidGlass"),
            Triple("Paddle Lite", "使用深度识别算法在本地进行OCR识别", "https://www.paddlepaddle.org.cn/paddle/paddlelite"),
            Triple("PaddleOCR", "提供 PP-OCRv6 Tiny 模型与 Android ONNX Runtime 部署实现", "https://github.com/PaddlePaddle/PaddleOCR"),
            Triple("Miuix", "多平台UI/效果实现的UI设计库", "https://github.com/compose-miuix-ui/miuix/"),
            Triple("HyperCeiler", "首次使用引导的视觉效果与动画参考", "https://github.com/ReChronoRain/HyperCeiler"),
        )
        PreferenceSection(title = "开源项目") {
            credits.forEach { (name, description, url) ->
                SettingsListItem(
                    title = name,
                    description = description,
                    onClick = {
                        performHaptic()
                        uriHandler.openUri(url)
                    }
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        PreferenceSection(title = "协议与政策") {
            SettingsGroup {
                SettingsGroupItem(
                    title = "用户协议与隐私说明",
                    description = if (privacyAccepted) {
                        "已同意，可查看或撤销"
                    } else {
                        "未同意，点击查看"
                    },
                    position = GroupPosition.Single,
                    onClick = {
                        performHaptic()
                        onShowPrivacyPolicy()
                    },
                )
            }
        }

        Spacer(Modifier.height(64.dp))

        val currentYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
        Text(
            text = "Made with ❤️ by Badnng and Vibe Codding\n© $currentYear 澎湃记",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
            Spacer(Modifier.height(32.dp + bottomPadding))
        }

        onExpandNavigationRail?.let { onExpand ->
            Md3eNavigationRailExpandButton(
                onClick = onExpand,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 8.dp, top = 8.dp),
            )
        }
    }
}

// ═══════════════════════════════════════════
//  日志导出（Miuix）
// ═══════════════════════════════════════════

@Composable
private fun MiuixAboutLogSection(performHaptic: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pendingLogData by remember { mutableStateOf<ByteArray?>(null) }

    val createLogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                try {
                    pendingLogData?.let { data ->
                        context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                        android.widget.Toast.makeText(context, "日志导出成功！", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "导出失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                } finally {
                    pendingLogData = null
                }
            }
        }
    }

    ArrowPreference(
        title = "导出当天日志",
        summary = "将今天的四类日志导出为 ZIP 文件",
        onClick = {
            performHaptic()
            coroutineScope.launch {
                val files = AppLogger.getTodayLogFiles(context)
                if (files.isEmpty()) {
                    android.widget.Toast.makeText(context, "今天暂无日志记录", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val zipBytes = createLogZip(files)
                if (zipBytes != null) {
                    pendingLogData = zipBytes
                    val fileName = "com.Badnng.moe-Log-${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())}.zip"
                    createLogLauncher.launch(fileName)
                }
            }
        }
    )
}

// ═══════════════════════════════════════════
//  日志导出（MD3E）
// ═══════════════════════════════════════════

@Composable
private fun Md3eLogSection(performHaptic: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pendingLogData by remember { mutableStateOf<ByteArray?>(null) }

    val createLogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                try {
                    pendingLogData?.let { data ->
                        context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                        android.widget.Toast.makeText(context, "日志导出成功！", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "导出失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                } finally {
                    pendingLogData = null
                }
            }
        }
    }

    PreferenceSection(title = "日志") {
        SettingsListItem(
            title = "导出当天日志",
            description = "将今天的四类日志导出为 ZIP 文件",
            onClick = {
                performHaptic()
                coroutineScope.launch {
                    val files = AppLogger.getTodayLogFiles(context)
                    if (files.isEmpty()) {
                        android.widget.Toast.makeText(context, "今天暂无日志记录", android.widget.Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val zipBytes = createLogZip(files)
                    if (zipBytes != null) {
                        pendingLogData = zipBytes
                        val fileName = "com.Badnng.moe-Log-${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())}.zip"
                        createLogLauncher.launch(fileName)
                    }
                }
            }
        )
    }
}

private suspend fun createLogZip(files: List<java.io.File>): ByteArray? {
    return withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val baos = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(baos).use { zos ->
                zos.setLevel(java.util.zip.Deflater.BEST_COMPRESSION)
                files.forEach { file ->
                    if (file.exists()) {
                        val entry = java.util.zip.ZipEntry(file.name)
                        zos.putNextEntry(entry)
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            baos.toByteArray()
        } catch (e: Exception) {
            null
        }
    }
}

// ═══════════════════════════════════════════
//  致谢页面
// ═══════════════════════════════════════════

@Composable
fun CreditsSettingsContent(performHaptic: () -> Unit, topPadding: androidx.compose.ui.unit.Dp = 0.dp, scrollState: androidx.compose.foundation.ScrollState = androidx.compose.foundation.rememberScrollState()) {
    val uriHandler = LocalUriHandler.current
    val isMiuix = rememberMiuixStyle()

    val credits = listOf(
        Triple("Jetpack Compose", "现代化声明式 UI 框架", "https://developer.android.com/jetpack/compose"),
        Triple("Material Design 3", "Google 现代设计语言规范", "https://m3.material.io"),
        Triple("ML Kit", "Google 强大的设备端机器学习 SDK", "https://developers.google.com/ml-kit"),
        Triple("Shizuku", "利用系统 API 实现高级权限调用", "https://shizuku.rikka.app"),
        Triple("ZXing", "高效的二维码生成与处理库", "https://github.com/zxing/zxing"),
        Triple("Room", "官方高性能 SQLite 数据库封装", "https://developer.android.com/training/data-storage/room"),
        Triple("Coil", "现代化的 Android 图片加载库", "https://coil-kt.github.io/coil/"),
        Triple("Kyant Backdrop", "优雅的毛玻璃与层级模糊效果实现", "https://github.com/Kyant0/AndroidLiquidGlass"),
        Triple("Paddle Lite", "使用深度识别算法在本地进行OCR识别", "https://www.paddlepaddle.org.cn/paddle/paddlelite"),
        Triple("PaddleOCR", "提供 PP-OCRv6 Tiny 模型与 Android ONNX Runtime 部署实现", "https://github.com/PaddlePaddle/PaddleOCR"),
        Triple("Miuix", "多平台UI/效果实现的UI设计库", "https://github.com/compose-miuix-ui/miuix/"),
        Triple("HyperCeiler", "首次使用引导的视觉效果与动画参考", "https://github.com/ReChronoRain/HyperCeiler"),
    )

    if (isMiuix) {
        val creditsSection: @Composable () -> Unit = {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp).padding(top = 12.dp).padding(bottom = 12.dp),
                colors = CardDefaults.defaultColors(MiuixTheme.colorScheme.surfaceContainer)
            ) {
                credits.forEach { (name, _, url) ->
                    ArrowPreference(
                        title = name,
                        onClick = {
                            performHaptic()
                            uriHandler.openUri(url)
                        }
                    )
                }
            }
        }
        MiuixSettingsLazyColumn(
            sections = listOf(creditsSection),
            contentPadding = PaddingValues(
                top = topPadding,
                bottom = 32.dp + WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding(),
            ),
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(topPadding))
            PreferenceSection(title = "开源项目") {
                credits.forEach { (name, description, url) ->
                    SettingsListItem(
                        title = name,
                        description = description,
                        onClick = {
                            performHaptic()
                            uriHandler.openUri(url)
                        }
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
