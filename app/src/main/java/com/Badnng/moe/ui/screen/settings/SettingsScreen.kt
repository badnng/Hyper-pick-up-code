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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.R
import com.Badnng.moe.service.CaptureTileService
import com.Badnng.moe.ui.LocalAppUi
import com.Badnng.moe.ui.component.GroupPosition
import com.Badnng.moe.ui.component.Md3eNavigationRailExpandButton
import com.Badnng.moe.ui.component.SettingsGroup
import com.Badnng.moe.ui.component.SettingsGroupItem
import com.Badnng.moe.ui.miuix.rememberMiuixStyle
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.Serializable

@Serializable
enum class SettingsPage {
    Main, Preference, Permission, Screenshot, Recognition, CustomPrompt, RecognitionCorrection, KeepAlive, WearableSync, Storage, About, Backup, Sponsor, NotificationApps, Credits, Developer
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    rootPage: SettingsPage = SettingsPage.Main,
    rootContentTopPadding: androidx.compose.ui.unit.Dp = 0.dp,
    rootContentBottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onExpandNavigationRail: (() -> Unit)? = null,
    onNavigateToSubPage: ((SettingsPage) -> Unit)? = null,
    onSubPageStatusChange: (Boolean) -> Unit = {},
) {
    var pageStack by remember(rootPage) { mutableStateOf(listOf<SettingsPage>()) }
    val currentPage = pageStack.lastOrNull() ?: rootPage
    var previousPage by remember(rootPage) { mutableStateOf(rootPage) }
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
    val motionScheme = MaterialTheme.motionScheme

    val performHaptic = {
        if (prefs.getBoolean("haptic_enabled", true)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun navigateTo(page: SettingsPage) {
        performHaptic()
        if (onNavigateToSubPage != null) {
            onNavigateToSubPage(page)
        } else {
            isGoingBack = false
            pageStack = pageStack + page
        }
    }

    fun navigateBack() {
        performHaptic()
        if (pageStack.isNotEmpty()) {
            isGoingBack = true
            pageStack = pageStack.dropLast(1)
        }
    }

    LaunchedEffect(currentPage) {
        onSubPageStatusChange(pageStack.isNotEmpty())
        if (pageStack.isNotEmpty()) previousPage = currentPage
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
        if (rootPage == SettingsPage.About) {
            AboutSettingsContent(
                performHaptic = performHaptic,
                topPadding = rootContentTopPadding,
                bottomPadding = rootContentBottomPadding,
                scrollState = rememberScrollState(),
                onNavigateToCredits = { navigateTo(SettingsPage.Credits) },
                onNavigateToSponsor = { navigateTo(SettingsPage.Sponsor) },
                onNavigateToBackup = { navigateTo(SettingsPage.Backup) },
                onNavigateToDeveloperOptions = { navigateTo(SettingsPage.Developer) },
                showBackButton = false,
                onExpandNavigationRail = onExpandNavigationRail,
            )
        } else {
            MainSettingsList(
                onNavigate = { navigateTo(it) },
                isMiuix = isMiuix,
                onExpandNavigationRail = onExpandNavigationRail,
            )
        }

        AnimatedVisibility(
            visible = pageStack.isNotEmpty(),
            enter = slideInHorizontally(
                animationSpec = motionScheme.defaultSpatialSpec<IntOffset>(),
            ) { it } + fadeIn(
                animationSpec = motionScheme.defaultEffectsSpec<Float>(),
            ),
            exit = slideOutHorizontally(
                animationSpec = motionScheme.defaultSpatialSpec<IntOffset>(),
            ) { it } + fadeOut(
                animationSpec = motionScheme.defaultEffectsSpec<Float>(),
            ),
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
                            slideInHorizontally(
                                animationSpec = motionScheme.defaultSpatialSpec<IntOffset>(),
                            ) { -it } + fadeIn(
                                animationSpec = motionScheme.defaultEffectsSpec<Float>(),
                            ) togetherWith slideOutHorizontally(
                                animationSpec = motionScheme.defaultSpatialSpec<IntOffset>(),
                            ) { it } + fadeOut(
                                animationSpec = motionScheme.defaultEffectsSpec<Float>(),
                            )
                        } else {
                            slideInHorizontally(
                                animationSpec = motionScheme.defaultSpatialSpec<IntOffset>(),
                            ) { it } + fadeIn(
                                animationSpec = motionScheme.defaultEffectsSpec<Float>(),
                            ) togetherWith slideOutHorizontally(
                                animationSpec = motionScheme.defaultSpatialSpec<IntOffset>(),
                            ) { -it } + fadeOut(
                                animationSpec = motionScheme.defaultEffectsSpec<Float>(),
                            )
                        }
                    },
                    label = "settings_nested"
                ) { page ->
                    val title = when (page) {
                        SettingsPage.Preference -> "偏好设置"
                        SettingsPage.Permission -> "权限与保活"
                        SettingsPage.Screenshot -> "截图方式"
                        SettingsPage.Recognition -> "识别方式"
                        SettingsPage.CustomPrompt -> "自定义 Prompt"
                        SettingsPage.KeepAlive -> "保活设置"
                        SettingsPage.WearableSync -> "手表同步"
                        SettingsPage.Storage -> "清理空间"
                        SettingsPage.About -> "关于"
                        SettingsPage.Backup -> "备份与恢复"
                        SettingsPage.Sponsor -> "赞助"
                        SettingsPage.NotificationApps -> "通知识别应用管理"
                        SettingsPage.Credits -> "致谢"
                        SettingsPage.Developer -> "开发者选项"
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsList(
    onNavigate: (SettingsPage) -> Unit,
    isMiuix: Boolean = false,
    onExpandNavigationRail: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val isLargeScreen = LocalConfiguration.current.screenWidthDp >= 700
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
                    SettingsGroupItem(title = "识别方式", description = "选择离线或在线多模态识别", position = GroupPosition.Middle, onClick = { onNavigate(SettingsPage.Recognition) })
                    SettingsGroupItem(title = "手表同步", description = "未完成取餐码同步到小米手表", position = GroupPosition.Middle, onClick = { onNavigate(SettingsPage.WearableSync) })
                    SettingsGroupItem(title = "添加到控制中心", description = "将「截图识别」磁贴添加到控制中心快捷栏", position = GroupPosition.Last, onClick = { performHaptic(); requestAddTile(context) })
                }
            }
            item {
                SmallTitle(text = "其他")
                SettingsGroup {
                    SettingsGroupItem(title = "清理空间", description = "管理缓存、截图、日志与更新文件", position = GroupPosition.Single, onClick = { onNavigate(SettingsPage.Storage) })
                }
            }
        }
    } else {
        val sections = remember {
            listOf(
                Md3eSettingsSection(
                    title = "常规",
                    items = listOf(
                        Md3eSettingsEntry("偏好设置", "管理界面、交互与通知偏好", Icons.Default.Tune, SettingsPage.Preference),
                        Md3eSettingsEntry("权限与保活", "检查必要权限与后台运行状态", Icons.Default.Security, SettingsPage.Permission),
                        Md3eSettingsEntry("截图方式", "选择截图来源与授权方式", Icons.Default.Screenshot, SettingsPage.Screenshot),
                        Md3eSettingsEntry("识别方式", "选择离线或在线多模态识别", Icons.Default.AutoAwesome, SettingsPage.Recognition),
                        Md3eSettingsEntry("手表同步", "未完成取餐码同步到小米手表", Icons.Default.Watch, SettingsPage.WearableSync),
                    ),
                ),
                Md3eSettingsSection(
                    title = "设备与存储",
                    items = listOf(
                        Md3eSettingsEntry("添加到控制中心", "将「截图识别」磁贴加入快捷栏", Icons.Default.DashboardCustomize, null),
                        Md3eSettingsEntry("清理空间", "管理缓存、截图、日志与更新文件", Icons.Default.CleaningServices, SettingsPage.Storage),
                    ),
                ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(
                        start = if (onExpandNavigationRail != null) 8.dp else 24.dp,
                        end = 24.dp,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    onExpandNavigationRail?.let { onExpand ->
                        Md3eNavigationRailExpandButton(onClick = onExpand)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 8.dp,
                    end = 20.dp,
                    bottom = if (isLargeScreen) 32.dp else 120.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(sections, key = { it.title }) { section ->
                    Column(
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            section.items.forEachIndexed { index, entry ->
                                val itemShape = when {
                                    section.items.size == 1 -> RoundedCornerShape(15.dp)
                                    index == 0 -> RoundedCornerShape(
                                        topStart = 15.dp,
                                        topEnd = 15.dp,
                                    )
                                    index == section.items.lastIndex -> RoundedCornerShape(
                                        bottomStart = 15.dp,
                                        bottomEnd = 15.dp,
                                    )
                                    else -> RoundedCornerShape(0.dp)
                                }
                                Md3eSettingsHomeItem(
                                    entry = entry,
                                    shape = itemShape,
                                    onClick = {
                                        if (entry.page != null) {
                                            onNavigate(entry.page)
                                        } else {
                                            performHaptic()
                                            requestAddTile(context)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class Md3eSettingsSection(
    val title: String,
    val items: List<Md3eSettingsEntry>,
)

private data class Md3eSettingsEntry(
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val page: SettingsPage?,
)

@Composable
private fun Md3eSettingsHomeItem(
    entry: Md3eSettingsEntry,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = entry.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = {
            Text(
                text = entry.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        leadingContent = {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(15.dp),
            ) {
                Box(
                    modifier = Modifier.size(46.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(entry.icon, contentDescription = null, modifier = Modifier.size(23.dp))
                }
            }
        },
        trailingContent = {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick),
    )
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
    isMiuix: Boolean = false,
    supportingPane: Boolean = false,
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
                    SettingsPage.Recognition -> RecognitionSettingsContent(
                        performHaptic,
                        0.dp,
                        scrollState,
                        onNavigateToPromptEditor = { onNavigate(SettingsPage.CustomPrompt) },
                    )
                    SettingsPage.CustomPrompt -> PromptEditorContent(performHaptic, 0.dp)
                    SettingsPage.RecognitionCorrection -> com.Badnng.moe.ui.component.RecognitionCorrectionRouteContent(
                        isMiuix = true,
                        onBack = onBack,
                        modifier = Modifier.fillMaxSize(),
                    )
                    SettingsPage.Permission -> PermissionSettingsContent(performHaptic, 0.dp, scrollState)
                    SettingsPage.Preference -> PreferenceSettingsContent(performHaptic, onNavigate, 0.dp, scrollState)
                    SettingsPage.KeepAlive -> KeepAliveSettingsContent(performHaptic, 0.dp, scrollState)
                    SettingsPage.Storage -> StorageSettingsContent(performHaptic, prefs, 0.dp, scrollState)
                    SettingsPage.Backup -> BackupSettingsContent(performHaptic, 0.dp)
                    SettingsPage.About -> AboutSettingsContent(
                        performHaptic = performHaptic,
                        topPadding = 0.dp,
                        scrollState = scrollState,
                        onNavigateToCredits = { onNavigate(SettingsPage.Credits) },
                        onNavigateToSponsor = { onNavigate(SettingsPage.Sponsor) },
                        onNavigateToBackup = { onNavigate(SettingsPage.Backup) },
                        onNavigateToDeveloperOptions = { onNavigate(SettingsPage.Developer) },
                    )
                    SettingsPage.Sponsor -> SponsorSettingsContent(0.dp, scrollState)
                    SettingsPage.NotificationApps -> NotificationAppsSettingsContent(
                        performHaptic = performHaptic,
                        topPadding = 0.dp,
                        showSystemApps = showSystemApps
                    )
                    SettingsPage.Credits -> CreditsSettingsContent(performHaptic, 0.dp, scrollState)
                    SettingsPage.Developer -> DeveloperSettingsContent(performHaptic, 0.dp, scrollState)
                    SettingsPage.WearableSync -> WearableSyncSettingsContent(performHaptic, 0.dp, scrollState)
                    SettingsPage.Main -> {}
                }
            }
        }
    } else {
        val scrollState = rememberScrollState()
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                MediumTopAppBar(
                    title = { Text(text = title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = if (supportingPane) {
                                    Icons.Default.Close
                                } else {
                                    Icons.AutoMirrored.Filled.ArrowBack
                                },
                                contentDescription = if (supportingPane) "关闭" else "返回",
                            )
                        }
                    },
                    actions = {
                        if (page == SettingsPage.NotificationApps) {
                            appUi.notificationAppsTopBarAction(
                                showSystemApps,
                                { showSystemApps = it },
                                performHaptic,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            },
        ) { innerPadding ->
            val topContentPadding = innerPadding.calculateTopPadding()
            Box(modifier = Modifier.fillMaxSize()) {
            when (page) {
                SettingsPage.Screenshot -> ScreenshotSettingsContent(performHaptic, topContentPadding, scrollState)
                SettingsPage.Recognition -> RecognitionSettingsContent(
                    performHaptic,
                    topContentPadding,
                    scrollState,
                    onNavigateToPromptEditor = { onNavigate(SettingsPage.CustomPrompt) },
                )
                SettingsPage.CustomPrompt -> PromptEditorContent(performHaptic, topContentPadding)
                SettingsPage.RecognitionCorrection -> com.Badnng.moe.ui.component.RecognitionCorrectionRouteContent(
                    isMiuix = false,
                    onBack = onBack,
                    modifier = Modifier.fillMaxSize().padding(top = topContentPadding),
                )
                SettingsPage.Permission -> PermissionSettingsContent(performHaptic, topContentPadding, scrollState)
                SettingsPage.Preference -> PreferenceSettingsContent(performHaptic, onNavigate, topContentPadding, scrollState)
                SettingsPage.KeepAlive -> KeepAliveSettingsContent(performHaptic, topContentPadding, scrollState)
                SettingsPage.Storage -> StorageSettingsContent(performHaptic, prefs, topContentPadding, scrollState)
                SettingsPage.Backup -> BackupSettingsContent(performHaptic, topContentPadding)
                SettingsPage.About -> AboutSettingsContent(
                    performHaptic = performHaptic,
                    topPadding = topContentPadding,
                    scrollState = scrollState,
                    onNavigateToSponsor = { onNavigate(SettingsPage.Sponsor) },
                    onNavigateToBackup = { onNavigate(SettingsPage.Backup) },
                    onNavigateToDeveloperOptions = { onNavigate(SettingsPage.Developer) },
                )
                SettingsPage.Sponsor -> SponsorSettingsContent(topContentPadding, scrollState)
                SettingsPage.NotificationApps -> NotificationAppsSettingsContent(
                    performHaptic = performHaptic,
                    topPadding = topContentPadding,
                    showSystemApps = showSystemApps
                )
                SettingsPage.Credits -> CreditsSettingsContent(performHaptic, topContentPadding, scrollState)
                SettingsPage.Developer -> DeveloperSettingsContent(performHaptic, topContentPadding, scrollState)
                SettingsPage.WearableSync -> WearableSyncSettingsContent(performHaptic, topContentPadding, scrollState)
                SettingsPage.Main -> {}
            }
            }
        }
    }
}
