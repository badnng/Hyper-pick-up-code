package com.Badnng.moe.ui.screen.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.helper.AppLogger
import com.Badnng.moe.helper.BackupHelper
import com.Badnng.moe.helper.NotificationHelper
import com.Badnng.moe.helper.UpdateHelper
import com.Badnng.moe.helper.UpdateInfo
import com.Badnng.moe.privacy.PrivacyConsent
import com.Badnng.moe.recognition.OnlineRecognitionPreferences
import com.Badnng.moe.ui.component.PrivacyPolicyBottomSheet
import com.Badnng.moe.ui.component.UpdateSheet
import com.Badnng.moe.ui.component.UpdateProgressSheet
import com.Badnng.moe.ui.component.GroupPosition
import com.Badnng.moe.ui.component.PreferenceSection
import com.Badnng.moe.ui.component.SettingsGroup
import com.Badnng.moe.ui.component.SettingsGroupItem
import com.Badnng.moe.ui.component.SettingsListItem
import com.Badnng.moe.ui.miuix.rememberMiuixStyle
import com.Badnng.moe.ui.miuix.miuixScrollModifiers
import com.Badnng.moe.ui.miuix.MiuixSettingsLazyColumn
import com.Badnng.moe.ui.oobe.OobeCarvedLogoView
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
fun AboutSettingsContent(performHaptic: () -> Unit, topPadding: androidx.compose.ui.unit.Dp = 0.dp, scrollState: androidx.compose.foundation.ScrollState = androidx.compose.foundation.rememberScrollState(), onNavigateToCredits: () -> Unit = {}, onNavigateToDeveloperOptions: () -> Unit = {}, scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior? = null, onBack: () -> Unit = {}, supportingPane: Boolean = false) {
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
            scrollState = scrollState,
            onVersionTap = onVersionTap,
            onNavigateToCredits = onNavigateToCredits,
            privacyAccepted = privacyAccepted,
            onShowPrivacyPolicy = { showPrivacyPolicy = true },
            onBack = onBack,
            supportingPane = supportingPane,
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
            privacyAccepted = privacyAccepted,
            onShowPrivacyPolicy = { showPrivacyPolicy = true },
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
                kotlinx.coroutines.delay(200)
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
    scrollState: androidx.compose.foundation.ScrollState,
    onVersionTap: () -> Unit,
    onNavigateToCredits: () -> Unit,
    privacyAccepted: Boolean,
    onShowPrivacyPolicy: () -> Unit,
    onBack: () -> Unit = {},
    supportingPane: Boolean = false,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val topAppBarScrollBehavior = top.yukonga.miuix.kmp.basic.MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current
    val logoTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 120.dp
    val versionFadeEndPx = with(density) { 56.dp.toPx() }
    val nameFadeStartPx = with(density) { 48.dp.toPx() }
    val nameFadeEndPx = with(density) { 112.dp.toPx() }
    val iconFadeStartPx = with(density) { 96.dp.toPx() }
    val iconFadeEndPx = with(density) { 176.dp.toPx() }

    var logoHeightDp by remember { mutableStateOf(300.dp) }

    val scrollProgress by remember {
        derivedStateOf {
            when {
                lazyListState.firstVisibleItemIndex > 0 -> 1f
                else -> {
                    val spacer = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "logoSpacer" }
                    if (spacer != null && spacer.size > 0) {
                        (lazyListState.firstVisibleItemScrollOffset.toFloat() / spacer.size).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
            }
        }
    }

    val backdrop = com.Badnng.moe.ui.miuix.rememberMiuixBackdrop()
    val sheetBackdrop = com.Badnng.moe.ui.miuix.rememberMiuixBackdrop()
    val collapsed by remember { derivedStateOf { scrollProgress == 1f } }
    val blurActive by remember(backdrop) { derivedStateOf { backdrop != null && scrollProgress == 1f } }
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
            if (lazyListState.firstVisibleItemIndex > 0) {
                Float.POSITIVE_INFINITY
            } else {
                lazyListState.firstVisibleItemScrollOffset.toFloat()
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
            val textBackdrop = com.Badnng.moe.ui.miuix.rememberMiuixBackdrop()
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
                    if (textBackdrop != null) {
                        Modifier.textureBlur(
                            backdrop = textBackdrop,
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
                if (textBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
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
                Box(modifier = Modifier.fillMaxSize()) {
                    // 可滚动内容（先声明，Z 轴较低）
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .miuixScrollModifiers(topAppBarScrollBehavior),
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding(),
                            bottom = innerPadding.calculateBottomPadding() + 32.dp,
                        ),
                    ) {
                        item(key = "logoSpacer") {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(logoHeightDp + 80.dp)
                            )
                        }

                        item(key = "about") {
                            Column(
                                modifier = Modifier.fillParentMaxHeight().padding(bottom = innerPadding.calculateBottomPadding()),
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
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Card(
                                    modifier = aboutCardModifier,
                                    colors = aboutCardColors,
                                ) {
                                    MiuixAboutBackupSection(performHaptic = performHaptic)
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
                                            "已同意 · 点击查看或撤销"
                                        } else {
                                            "未同意 · 点击查看"
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
                    }

                    // Logo + 应用名 + 版本号 + 检查更新（后声明，Z 轴更高，可接收点击）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = logoTopPadding,
                                start = 16.dp,
                                end = 16.dp,
                            )
                            .align(Alignment.TopCenter)
                            .onSizeChanged { size ->
                                with(density) { logoHeightDp = size.height.toDp() }
                            },
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
    privacyAccepted: Boolean,
    onShowPrivacyPolicy: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val md3LogoColor = MaterialTheme.colorScheme.primary.toArgb()

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
                    position = GroupPosition.Last,
                    onClick = {
                        performHaptic()
                        uriHandler.openUri("https://github.com/badnng/Hyper-pick-up-code/blob/master/LICENSE")
                    }
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // 备份与恢复
        Md3eBackupSection(performHaptic = performHaptic)

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
            Triple("Paddle4Android", "不需要学习原理即可一键在Android上引入OCR识别", "https://github.com/equationl/paddleocr4android"),
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
                        "已同意 · 点击查看或撤销"
                    } else {
                        "未同意 · 点击查看"
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
        Spacer(Modifier.height(32.dp))
    }
}

// ═══════════════════════════════════════════
//  备份与恢复（Miuix）
// ═══════════════════════════════════════════

@Composable
private fun MiuixAboutBackupSection(performHaptic: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var pendingBackupData by remember { mutableStateOf<ByteArray?>(null) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                try {
                    pendingBackupData?.let { data ->
                        context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                        android.widget.Toast.makeText(context, "备份成功！", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "保存备份失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                } finally {
                    pendingBackupData = null
                    isBackingUp = false
                }
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            isRestoring = true
            coroutineScope.launch {
                try {
                    val backupData = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw Exception("无法读取备份文件")
                    val restoredData = BackupHelper.restoreBackup(context, backupData)
                    val editor = prefs.edit()
                    restoredData.settings.forEach { (key, value) ->
                        when (value) {
                            is Boolean -> editor.putBoolean(key, value)
                            is String -> editor.putString(key, value)
                            is Int -> editor.putInt(key, value)
                            is Long -> editor.putLong(key, value)
                            is Float -> editor.putFloat(key, value)
                        }
                    }
                    editor.apply()
                    val database = OrderDatabase.getDatabase(context)
                    restoredData.orders.forEach { order ->
                        if (database.orderDao().getOrderById(order.id) == null) database.orderDao().insert(order)
                    }
                    android.widget.Toast.makeText(context, "恢复成功！共恢复 ${restoredData.orders.size} 条取餐码", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "恢复备份失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                } finally {
                    isRestoring = false
                }
            }
        }
    }

    ArrowPreference(
        title = "备份数据",
        summary = "备份取餐码和设置到压缩包",
            onClick = {
                performHaptic()
                isBackingUp = true
                coroutineScope.launch {
                    try {
                        val database = OrderDatabase.getDatabase(context)
                        val orders = database.orderDao().getAllOrdersList()
                        val settingsMap = mutableMapOf<String, Any?>()
                        prefs.all.forEach { (key, value) -> settingsMap[key] = value }
                        val backupData = BackupHelper.createBackup(context, orders, settingsMap)
                        pendingBackupData = backupData
                        createBackupLauncher.launch(BackupHelper.generateBackupFileName())
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "备份失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        isBackingUp = false
                    }
                }
            }
        )
        ArrowPreference(
            title = "恢复数据",
            summary = "从备份文件恢复取餐码和设置",
            onClick = {
                performHaptic()
                restoreBackupLauncher.launch(arrayOf("*/*"))
            }
        )
}

// ═══════════════════════════════════════════
//  备份与恢复（MD3E）
// ═══════════════════════════════════════════

@Composable
private fun Md3eBackupSection(performHaptic: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var pendingBackupData by remember { mutableStateOf<ByteArray?>(null) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                try {
                    pendingBackupData?.let { data ->
                        context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                        android.widget.Toast.makeText(context, "备份成功！", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "保存备份失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                } finally {
                    pendingBackupData = null
                    isBackingUp = false
                }
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            isRestoring = true
            coroutineScope.launch {
                try {
                    val backupData = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw Exception("无法读取备份文件")
                    val restoredData = BackupHelper.restoreBackup(context, backupData)
                    val editor = prefs.edit()
                    restoredData.settings.forEach { (key, value) ->
                        when (value) {
                            is Boolean -> editor.putBoolean(key, value)
                            is String -> editor.putString(key, value)
                            is Int -> editor.putInt(key, value)
                            is Long -> editor.putLong(key, value)
                            is Float -> editor.putFloat(key, value)
                        }
                    }
                    editor.apply()
                    val database = OrderDatabase.getDatabase(context)
                    restoredData.orders.forEach { order ->
                        if (database.orderDao().getOrderById(order.id) == null) database.orderDao().insert(order)
                    }
                    android.widget.Toast.makeText(context, "恢复成功！共恢复 ${restoredData.orders.size} 条取餐码", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "恢复备份失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                } finally {
                    isRestoring = false
                }
            }
        }
    }

    PreferenceSection(title = "备份与恢复") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 备份卡片
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "备份数据", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(text = "备份取餐码和设置到压缩包", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            performHaptic()
                            isBackingUp = true
                            coroutineScope.launch {
                                try {
                                    val database = OrderDatabase.getDatabase(context)
                                    val orders = database.orderDao().getAllOrdersList()
                                    val settingsMap = mutableMapOf<String, Any?>()
                                    prefs.all.forEach { (key, value) -> settingsMap[key] = value }
                                    val backupData = BackupHelper.createBackup(context, orders, settingsMap)
                                    pendingBackupData = backupData
                                    createBackupLauncher.launch(BackupHelper.generateBackupFileName())
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "备份失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    isBackingUp = false
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isBackingUp
                    ) {
                        if (isBackingUp) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("备份")
                    }
                }
            }

            // 恢复卡片
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "恢复数据", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(text = "从备份文件恢复取餐码和设置", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = { performHaptic(); restoreBackupLauncher.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isRestoring
                    ) {
                        if (isRestoring) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("恢复")
                    }
                }
            }
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
        Triple("Paddle4Android", "不需要学习原理即可一键在Android上引入OCR识别", "https://github.com/equationl/paddleocr4android"),
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
