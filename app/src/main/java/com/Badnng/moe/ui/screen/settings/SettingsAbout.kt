package com.Badnng.moe.ui.screen.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.R
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.helper.AppLogger
import com.Badnng.moe.helper.BackupHelper
import com.Badnng.moe.helper.NotificationHelper
import com.Badnng.moe.helper.UpdateHelper
import com.Badnng.moe.helper.UpdateInfo
import com.Badnng.moe.ui.component.UpdateSheet
import com.Badnng.moe.ui.component.UpdateProgressSheet
import com.Badnng.moe.ui.component.PreferenceSection
import com.Badnng.moe.ui.component.SettingsListItem
import com.Badnng.moe.ui.miuix.rememberMiuixStyle
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
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
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutSettingsContent(performHaptic: () -> Unit, topPadding: androidx.compose.ui.unit.Dp = 0.dp, scrollState: androidx.compose.foundation.ScrollState = androidx.compose.foundation.rememberScrollState(), onNavigateToCredits: () -> Unit = {}, scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior? = null, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val versionName = remember { getVersionName(context) }
    val versionCode = remember { getVersionCode(context) }

    var showUpdateDialog by remember { mutableStateOf(false) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var isPaused by remember { mutableStateOf(false) }
    val pausedFlag = remember { AtomicBoolean(false) }
    var isChecking by remember { mutableStateOf(false) }
    var iconTapCount by remember { mutableIntStateOf(0) }

    val coroutineScope = rememberCoroutineScope()
    val notificationHelper = remember { NotificationHelper(context) }
    val isMiuix = rememberMiuixStyle()

    // 从更新下载通知进入时，自动弹出更新进度弹窗
    LaunchedEffect(Unit) {
        if (prefs.getBoolean("show_update_download", false)) {
            prefs.edit().putBoolean("show_update_download", false).apply()
            if (UpdateHelper.isDownloading && UpdateHelper.currentDownloadingVersion != null) {
                updateInfo = UpdateHelper.currentDownloadingVersion
                downloadProgress = UpdateHelper.currentProgress
                showProgressDialog = true
            }
        }
    }

    val networkUpdateEnabled = prefs.getBoolean("network_update_enabled", false)
    val updateChannel = prefs.getString("update_channel", "stable") ?: "stable"

    val checkUpdateAction: () -> Unit = {
        performHaptic()
        isChecking = true
        coroutineScope.launch {
            val info = UpdateHelper.checkUpdate(updateChannel == "dev")
            isChecking = false
            if (info != null) {
                val localVersion = UpdateHelper.getCurrentVersionCode(context)
                if (info.versionCode > localVersion) {
                    updateInfo = info
                    if (UpdateHelper.isDownloading && UpdateHelper.currentDownloadingVersion?.versionCode == info.versionCode) {
                        showProgressDialog = true
                    } else if (UpdateHelper.downloadedFile != null && UpdateHelper.downloadedFile!!.exists()) {
                        UpdateHelper.installUpdate(context, UpdateHelper.downloadedFile!!)
                    } else {
                        showUpdateDialog = true
                    }
                } else {
                    UpdateHelper.showNoUpdateToast(context)
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
            iconTapCount = iconTapCount,
            onIconTap = { iconTapCount = it },
            onNavigateToCredits = onNavigateToCredits,
            onBack = onBack
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
            iconTapCount = iconTapCount,
            onIconTap = { iconTapCount = it }
        )
    }

    // 更新弹窗
    updateInfo?.let { info ->
        UpdateSheet(
            show = showUpdateDialog,
            updateInfo = info,
            onDismiss = { showUpdateDialog = false },
            onInstall = {
                showUpdateDialog = false
                showProgressDialog = true
                downloadProgress = null
                isPaused = false
                pausedFlag.set(false)
                prefs.edit().putBoolean("show_update_download", true).apply()
                coroutineScope.launch {
                    val file = UpdateHelper.downloadUpdate(
                        context = context,
                        updateInfo = info,
                        onProgress = {
                            downloadProgress = it
                            notificationHelper.showUpdateDownloadNotification(info.versionName, it, pausedFlag.get())
                        },
                        isPaused = { pausedFlag.get() }
                    )
                    showProgressDialog = false
                    notificationHelper.cancelUpdateDownloadNotification()
                    if (file != null) {
                        UpdateHelper.installUpdate(context, file)
                    }
                }
            }
        )
    }

    // 下载进度弹窗
    if (showProgressDialog) {
        LaunchedEffect(Unit) {
            while (showProgressDialog && UpdateHelper.isDownloading) {
                downloadProgress = UpdateHelper.currentProgress
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
                pausedFlag.set(true)
                notificationHelper.showUpdateDownloadNotification(info.versionName, downloadProgress ?: 0f, true)
            },
            onResume = {
                isPaused = false
                pausedFlag.set(false)
                notificationHelper.showUpdateDownloadNotification(info.versionName, downloadProgress ?: 0f, false)
            },
            onDismiss = {
                showProgressDialog = false
                notificationHelper.showUpdateDownloadNotification(info.versionName, downloadProgress ?: 0f, isPaused)
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
    iconTapCount: Int,
    onIconTap: (Int) -> Unit,
    onNavigateToCredits: () -> Unit,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val topAppBarScrollBehavior = top.yukonga.miuix.kmp.basic.MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current

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
    val collapsed by remember { derivedStateOf { scrollProgress == 1f } }
    val blurActive by remember(backdrop) { derivedStateOf { backdrop != null && scrollProgress == 1f } }

    top.yukonga.miuix.kmp.basic.Scaffold(
        topBar = {
            val barColor = if (blurActive) {
                Color.Transparent
            } else {
                if (collapsed) MiuixTheme.colorScheme.surface else Color.Transparent
            }
            val titleColor = MiuixTheme.colorScheme.onSurface.copy(
                alpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f),
            )
            com.Badnng.moe.ui.miuix.MiuixBlurredBar(backdrop = backdrop, blurEnabled = blurActive) {
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
                                MiuixIcons.Regular.Back,
                                contentDescription = "返回",
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            val surfaceForBackdrop = MiuixTheme.colorScheme.surface
            val textBackdrop = top.yukonga.miuix.kmp.blur.rememberLayerBackdrop {
                drawRect(surfaceForBackdrop)
                drawContent()
            }
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
                            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
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
                                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 120.dp,
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
                                    val iconProgress = ((scrollProgress - 0.35f) / 0.15f).coerceIn(0f, 1f)
                                    clip = true
                                    shape = RoundedCornerShape(24.dp)
                                    alpha = 1f - iconProgress
                                    scaleX = 1f - (iconProgress * 0.05f)
                                    scaleY = 1f - (iconProgress * 0.05f)
                                }
                                .background(MiuixTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    onIconTap(iconTapCount + 1)
                                    if (iconTapCount + 1 >= 10) {
                                        onIconTap(0)
                                        throw RuntimeException("Test crash triggered from About icon")
                                    }
                                }
                        ) {
                            MiuixIcon(
                                painter = painterResource(id = R.drawable.abouttopicon),
                                contentDescription = "Logo",
                                modifier = Modifier.size(74.dp),
                                tint = Color.Unspecified
                            )
                        }
                        // 应用名（带 textureBlur 渲染，contentBlendMode = DstIn 让模糊只作用于文字像素）
                        MiuixText(
                            modifier = Modifier
                                .padding(top = 12.dp, bottom = 5.dp)
                                .graphicsLayer {
                                    val nameProgress = ((scrollProgress - 0.20f) / 0.15f).coerceIn(0f, 1f)
                                    alpha = 1f - nameProgress
                                    scaleX = 1f - (nameProgress * 0.05f)
                                    scaleY = 1f - (nameProgress * 0.05f)
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
                                    val versionProgress = ((scrollProgress - 0.05f) / 0.15f).coerceIn(0f, 1f)
                                    alpha = 1f - versionProgress
                                    scaleX = 1f - (versionProgress * 0.05f)
                                    scaleY = 1f - (versionProgress * 0.05f)
                                },
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
                                        val btnProgress = ((scrollProgress - 0.0f) / 0.15f).coerceIn(0f, 1f)
                                        alpha = 1f - btnProgress
                                        scaleX = 1f - (btnProgress * 0.05f)
                                        scaleY = 1f - (btnProgress * 0.05f)
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

        // BottomSheet 模糊背景（实时跟随 Sheet 拖拽进度）
        val animatedBlurAlpha = com.Badnng.moe.ui.component.BlurState.progress.floatValue
        if (animatedBlurAlpha > 0.01f && backdrop != null) {
            val blurPrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
            var blurThemeMode by remember { mutableStateOf(blurPrefs.getString("theme_mode", "system") ?: "system") }
            DisposableEffect(blurPrefs) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                    if (key == "theme_mode") blurThemeMode = p.getString(key, "system") ?: "system"
                }
                blurPrefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { blurPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            val isInDark = when (blurThemeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            val baseBrightness = if (isInDark) -0.3f else -0.5f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .textureBlur(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(0.dp),
                        blurRadius = 56f * animatedBlurAlpha,
                        colors = top.yukonga.miuix.kmp.blur.BlurDefaults.blurColors(
                            brightness = baseBrightness * animatedBlurAlpha,
                            contrast = 1f + 0.2f * animatedBlurAlpha,
                            saturation = 1f + 0.08f * animatedBlurAlpha,
                        ),
                    )
                    .graphicsLayer(alpha = animatedBlurAlpha)
            )
        }
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
    iconTapCount: Int,
    onIconTap: (Int) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()

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
                .size(86.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    onIconTap(iconTapCount + 1)
                    if (iconTapCount + 1 >= 10) {
                        onIconTap(0)
                        throw RuntimeException("Test crash triggered from About icon")
                    }
                },
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.abouttopicon),
                    contentDescription = "Logo",
                    modifier = Modifier.size(66.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(text = "澎湃记", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(text = "版本 $versionName ($versionCode)", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))

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

        // 项目地址
        PreferenceSection(title = "项目") {
            SettingsListItem(
                title = "项目地址",
                description = "GitHub · badnng/Hyper-pick-up-code",
                onClick = {
                    performHaptic()
                    uriHandler.openUri("https://github.com/badnng/Hyper-pick-up-code")
                }
            )
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
    )

    if (isMiuix) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        ) {
            Spacer(Modifier.height(topPadding))
            Card(
                modifier = Modifier.padding(horizontal = 12.dp).padding(top = 12.dp),
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
            Spacer(Modifier.height(32.dp))
        }
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
