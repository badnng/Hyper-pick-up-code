package com.Badnng.moe.ui.screen.settings

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.os.Build
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.R
import com.Badnng.moe.service.CaptureTileService
import com.Badnng.moe.ui.LocalAppUi
import com.Badnng.moe.ui.component.GroupPosition
import com.Badnng.moe.ui.component.SettingsGroup
import com.Badnng.moe.ui.component.SettingsGroupItem
import com.Badnng.moe.ui.component.SettingsListItem
import com.Badnng.moe.ui.miuix.rememberMiuixStyle
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

enum class SettingsPage {
    Main, Preference, Permission, Screenshot, KeepAlive, Storage, About, Sponsor, NotificationApps, Credits
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onSubPageStatusChange: (Boolean) -> Unit = {}
) {
    var pageStack by remember { mutableStateOf(listOf<SettingsPage>()) }
    val currentPage = pageStack.lastOrNull() ?: SettingsPage.Main
    var previousPage by remember { mutableStateOf(SettingsPage.Main) }
    var isGoingBack by remember { mutableStateOf(false) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var isPredictiveBackInProgress by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var predictiveBackEnabled by remember {
        mutableStateOf(prefs.getBoolean("predictive_back_enabled", true))
    }
    val isMiuix = rememberMiuixStyle()

    val performHaptic = {
        if (prefs.getBoolean("haptic_enabled", true)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun navigateTo(page: SettingsPage) {
        performHaptic()
        isGoingBack = false
        pageStack = pageStack + page
    }

    fun navigateBack() {
        performHaptic()
        if (pageStack.isNotEmpty()) {
            isGoingBack = true
            pageStack = pageStack.dropLast(1)
        }
    }

    LaunchedEffect(currentPage) {
        onSubPageStatusChange(currentPage != SettingsPage.Main)
        if (currentPage != SettingsPage.Main) previousPage = currentPage
    }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                "predictive_back_enabled" -> predictiveBackEnabled = p.getBoolean(key, true)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    PredictiveBackHandler(
        enabled = predictiveBackEnabled && pageStack.isNotEmpty()
    ) { backEvent: Flow<BackEventCompat> ->
        isPredictiveBackInProgress = true
        try {
            backEvent.collect { event ->
                backProgress = event.progress
                backSwipeEdge = event.swipeEdge
            }
            performHaptic()
            isGoingBack = true
            pageStack = pageStack.dropLast(1)
        } catch (e: CancellationException) {
            // 取消时保持原样
        } finally {
            isPredictiveBackInProgress = false
            backProgress = 0f
        }
    }

    BackHandler(enabled = !predictiveBackEnabled && pageStack.isNotEmpty()) {
        navigateBack()
    }

    val currentScale = if (isPredictiveBackInProgress) 1f - (backProgress * 0.08f) else 1f
    val currentTranslationX = if (isPredictiveBackInProgress) {
        val multiplier = if (backSwipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
        backProgress * 100f * multiplier
    } else 0f
    val currentCornerRadius = if (isPredictiveBackInProgress) (backProgress * 32).dp else 0.dp

    Box(modifier = modifier.fillMaxSize()) {
        MainSettingsList(onNavigate = { navigateTo(it) }, isMiuix = isMiuix)

        AnimatedVisibility(
            visible = pageStack.isNotEmpty(),
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = currentScale
                        scaleY = currentScale
                        translationX = currentTranslationX
                        shape = RoundedCornerShape(currentCornerRadius)
                        clip = true
                    }
                    .border(
                        width = if (isPredictiveBackInProgress) 1.dp else 0.dp,
                        color = if (isMiuix) MiuixTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant.copy(alpha = backProgress),
                        shape = RoundedCornerShape(currentCornerRadius)
                    )
                    .background(if (isMiuix) MiuixTheme.colorScheme.surface else MaterialTheme.colorScheme.background)
            ) {
                // 嵌套页面切换用 AnimatedContent
                AnimatedContent(
                    targetState = currentPage,
                    transitionSpec = {
                        if (isGoingBack) {
                            slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                        } else {
                            slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                        }
                    },
                    label = "settings_nested"
                ) { page ->
                    val title = when (page) {
                        SettingsPage.Preference -> "偏好设置"
                        SettingsPage.Permission -> "权限与保活"
                        SettingsPage.Screenshot -> "截图方式"
                        SettingsPage.KeepAlive -> "保活设置"
                        SettingsPage.Storage -> "清理空间"
                        SettingsPage.About -> "关于"
                        SettingsPage.Sponsor -> "赞助"
                        SettingsPage.NotificationApps -> "通知识别应用管理"
                        SettingsPage.Credits -> "致谢"
                        else -> ""
                    }
                    SubPage(
                        title = title,
                        page = page,
                        performHaptic = performHaptic,
                        onNavigate = { navigateTo(it) },
                        onBack = { navigateBack() },
                        isMiuix = isMiuix
                    )
                }
            }
        }
    }
}

@Composable
fun MainSettingsList(onNavigate: (SettingsPage) -> Unit, isMiuix: Boolean = false) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val performHaptic = {
        if (prefs.getBoolean("haptic_enabled", true)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    if (isMiuix) {
        // Miuix 模式：参考 Miuix 示例应用的布局
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
            contentPadding = PaddingValues(bottom = 100.dp + WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding())
        ) {
            item {
                SmallTitle(text = "设置")
            }
            item {
                SettingsGroup {
                    SettingsGroupItem(title = "偏好设置", description = "管理自行习惯的设置", position = GroupPosition.First, onClick = { onNavigate(SettingsPage.Preference) })
                    SettingsGroupItem(title = "权限与保活", description = "管理权限和防止系统清理后台", position = GroupPosition.Middle, onClick = { onNavigate(SettingsPage.Permission) })
                    SettingsGroupItem(title = "截图方式", description = "管理App截图的方式", position = GroupPosition.Middle, onClick = { onNavigate(SettingsPage.Screenshot) })
                    SettingsGroupItem(title = "添加到控制中心", description = "将「截图识别」磁贴添加到控制中心快捷栏", position = GroupPosition.Last, onClick = { performHaptic(); requestAddTile(context) })
                }
            }
            item {
                SmallTitle(text = "其他")
                SettingsGroup {
                    SettingsGroupItem(title = "清理空间", description = "管理App占用的缓存与截图空间", position = GroupPosition.Single, onClick = { onNavigate(SettingsPage.Storage) })
                }
            }
            item {
                SettingsGroup {
                    SettingsGroupItem(title = "关于", description = "应用信息与开源许可", position = GroupPosition.First, onClick = { onNavigate(SettingsPage.About) })
                    SettingsGroupItem(title = "赞助", description = "支持项目持续更新", position = GroupPosition.Last, onClick = { onNavigate(SettingsPage.Sponsor) })
                }
            }
        }
    } else {
        // MD3E 模式
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp + WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding())
        ) {
            item {
                Text(text = "设置", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            }
            item {
                SettingsGroup {
                    SettingsGroupItem(title = "偏好设置", description = "管理自行习惯的设置", position = GroupPosition.First, onClick = { onNavigate(SettingsPage.Preference) })
                    SettingsGroupItem(title = "权限与保活", description = "管理权限和防止系统清理后台", position = GroupPosition.Middle, onClick = { onNavigate(SettingsPage.Permission) })
                    SettingsGroupItem(title = "截图方式", description = "管理App截图的方式", position = GroupPosition.Middle, onClick = { onNavigate(SettingsPage.Screenshot) })
                    SettingsGroupItem(title = "添加到控制中心", description = "将「截图识别」磁贴添加到控制中心快捷栏", position = GroupPosition.Last, onClick = { performHaptic(); requestAddTile(context) })
                }
            }
            item {
                SettingsGroup {
                    SettingsGroupItem(title = "清理空间", description = "管理App占用的缓存与截图空间", position = GroupPosition.Single, onClick = { onNavigate(SettingsPage.Storage) })
                }
            }
            item {
                SettingsGroup {
                    SettingsGroupItem(title = "关于", description = "应用信息与开源许可", position = GroupPosition.First, onClick = { onNavigate(SettingsPage.About) })
                    SettingsGroupItem(title = "赞助", description = "支持项目持续更新", position = GroupPosition.Last, onClick = { onNavigate(SettingsPage.Sponsor) })
                }
            }
        }
    }
}

private fun requestAddTile(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val statusBarManager = context.getSystemService(Context.STATUS_BAR_SERVICE) as StatusBarManager
        statusBarManager.requestAddTileService(
            ComponentName(context, CaptureTileService::class.java),
            "截图识别",
            Icon.createWithResource(context, R.drawable.note),
            {}, {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubPage(
    title: String,
    page: SettingsPage,
    performHaptic: () -> Unit,
    onNavigate: (SettingsPage) -> Unit,
    onBack: () -> Unit,
    isMiuix: Boolean = false
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val appUi = LocalAppUi.current
    var showSystemApps by remember(page) { mutableStateOf(false) }

    if (isMiuix) {
        // Miuix 模式：使用 Miuix Scaffold + TopAppBar（自动处理大标题滚动动画）
        val miuixScrollBehavior = top.yukonga.miuix.kmp.basic.MiuixScrollBehavior()
        top.yukonga.miuix.kmp.basic.Scaffold(
            topBar = {
                top.yukonga.miuix.kmp.basic.TopAppBar(
                    title = title,
                    scrollBehavior = miuixScrollBehavior,
                    color = MiuixTheme.colorScheme.surface,
                    navigationIcon = {
                        MiuixIconButton(onClick = onBack) {
                            MiuixIcon(MiuixIcons.Regular.Back, "返回")
                        }
                    },
                    actions = {
                        if (page == SettingsPage.NotificationApps) {
                            appUi.notificationAppsTopBarAction(
                                showSystemApps,
                                { showSystemApps = it },
                                performHaptic
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surface)
                    .padding(innerPadding)
            ) {
                when (page) {
                    SettingsPage.Screenshot -> ScreenshotSettingsContent(performHaptic, 0.dp, scrollState)
                    SettingsPage.Permission -> PermissionSettingsContent(performHaptic, 0.dp, scrollState)
                    SettingsPage.Preference -> PreferenceSettingsContent(performHaptic, onNavigate, 0.dp, scrollState)
                    SettingsPage.KeepAlive -> KeepAliveSettingsContent(performHaptic, 0.dp, scrollState)
                    SettingsPage.Storage -> StorageSettingsContent(performHaptic, prefs, 0.dp, scrollState)
                    SettingsPage.About -> AboutSettingsContent(performHaptic, 0.dp, scrollState, onNavigateToCredits = { onNavigate(SettingsPage.Credits) })
                    SettingsPage.Sponsor -> SponsorSettingsContent(0.dp, scrollState)
                    SettingsPage.NotificationApps -> NotificationAppsSettingsContent(
                        performHaptic = performHaptic,
                        topPadding = 0.dp,
                        showSystemApps = showSystemApps
                    )
                    SettingsPage.Credits -> CreditsSettingsContent(performHaptic, 0.dp, scrollState)
                    SettingsPage.Main -> {}
                }
            }
        }
    } else {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val scrollState = rememberScrollState()
    var isScrolled by remember { mutableStateOf(false) }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }
            .collect { value ->
                isScrolled = value > 30
            }
    }

    val animatedBrightness by animateFloatAsState(
        targetValue = if (isScrolled) -0.1f else 0f,
        animationSpec = tween(300),
        label = "brightness"
    )

    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val canBlur = isRenderEffectSupported()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 内容层：延伸到顶栏下方
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val topContentPadding = statusBarHeight + 64.dp
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            when (page) {
                SettingsPage.Screenshot -> ScreenshotSettingsContent(performHaptic, topContentPadding, scrollState)
                SettingsPage.Permission -> PermissionSettingsContent(performHaptic, topContentPadding, scrollState)
                SettingsPage.Preference -> PreferenceSettingsContent(performHaptic, onNavigate, topContentPadding, scrollState)
                SettingsPage.KeepAlive -> KeepAliveSettingsContent(performHaptic, topContentPadding, scrollState)
                SettingsPage.Storage -> StorageSettingsContent(performHaptic, prefs, topContentPadding, scrollState)
                SettingsPage.About -> AboutSettingsContent(performHaptic, topContentPadding, scrollState)
                SettingsPage.Sponsor -> SponsorSettingsContent(topContentPadding, scrollState)
                SettingsPage.NotificationApps -> NotificationAppsSettingsContent(
                    performHaptic = performHaptic,
                    topPadding = topContentPadding,
                    showSystemApps = showSystemApps
                )
                SettingsPage.Credits -> CreditsSettingsContent(performHaptic, topContentPadding, scrollState)
                SettingsPage.Main -> {}
            }
        }

        // 毛玻璃 TopAppBar 覆盖层
        if (canBlur) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // 模糊背景层（底层）
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .textureBlur(
                            backdrop = backdrop,
                            shape = RectangleShape,
                            blurRadius = 80f,
                            colors = BlurColors(brightness = animatedBrightness)
                        )
                        .frostedGlassMask()
                )
                // TopAppBar 内容层（顶层）
                TopAppBar(
                    title = {
                        AnimatedVisibility(
                            visible = !isScrolled,
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut() + slideOutVertically()
                        ) {
                            Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                    actions = {
                        if (page == SettingsPage.NotificationApps) {
                            appUi.notificationAppsTopBarAction(
                                showSystemApps,
                                { showSystemApps = it },
                                performHaptic
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            ) {
                TopAppBar(
                    title = { Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                    actions = {
                        if (page == SettingsPage.NotificationApps) {
                            appUi.notificationAppsTopBarAction(
                                showSystemApps,
                                { showSystemApps = it },
                                performHaptic
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        }
    }
    }
}

private fun Modifier.frostedGlassMask(): Modifier = this.drawWithContent {
    drawContent()
    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.Black,
                0.75f to Color.Black,
                1f to Color.Transparent
            )
        ),
        blendMode = BlendMode.DstIn
    )
}
