package com.Badnng.moe.ui.screen.miuix

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Sidebar
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplayTransitionEffects
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.data.db.OrderGroup
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.ui.LocalAppUi
import com.Badnng.moe.ui.miuix.MIUIX_FLOATING_NAV_BAR_STYLE_KEY
import com.Badnng.moe.ui.miuix.MiuixFloatingNavigationBarStyle
import com.Badnng.moe.ui.miuix.miuixScrollModifiers
import com.Badnng.moe.ui.miuix.liquid.IosLiquidGlassNavigationBar
import com.Badnng.moe.ui.screen.rememberSaveablePagerState
import com.Badnng.moe.ui.screen.settings.SettingsPage
import com.Badnng.moe.viewmodel.OrderViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarDefaults
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDefaults
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.NavigationRailValue
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults

// 顶层路由
sealed interface HomeRoute : NavKey {
    data object Main : HomeRoute
    data class SettingsSubPage(val page: SettingsPage) : HomeRoute
    data class OrderDetail(val orderId: String) : HomeRoute
    data class GroupDetail(val groupId: Long) : HomeRoute
}

@Composable
fun MiuixHomeScreen(
    modifier: Modifier = Modifier,
    intentToProcess: Intent? = null,
    pagerState: androidx.compose.foundation.pager.PagerState? = null
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var hapticEnabled by remember { mutableStateOf(prefs.getBoolean("haptic_enabled", true)) }
    var useFloatingNavBar by remember { mutableStateOf(prefs.getBoolean("use_floating_nav_bar", false)) }
    var floatingNavBarStyle by remember {
        mutableStateOf(
            MiuixFloatingNavigationBarStyle.fromPreference(
                prefs.getString(MIUIX_FLOATING_NAV_BAR_STYLE_KEY, null)
            )
        )
    }
    var navAlignment by remember { mutableStateOf(prefs.getString("nav_alignment", "center") ?: "center") }
    var predictiveBackEnabled by remember {
        mutableStateOf(prefs.getBoolean("predictive_back_enabled", true))
    }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                "haptic_enabled" -> hapticEnabled = p.getBoolean(key, true)
                "use_floating_nav_bar" -> useFloatingNavBar = p.getBoolean(key, false)
                MIUIX_FLOATING_NAV_BAR_STYLE_KEY -> {
                    floatingNavBarStyle = MiuixFloatingNavigationBarStyle.fromPreference(
                        p.getString(key, null)
                    )
                }
                "nav_alignment" -> navAlignment = p.getString(key, "center") ?: "center"
                "predictive_back_enabled" -> predictiveBackEnabled = p.getBoolean(key, true)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val backStack = remember { mutableStateListOf<HomeRoute>(HomeRoute.Main) }

    val homeEntryProvider = remember(backStack) {
        entryProvider<NavKey> {
            entry<HomeRoute.Main> {
                MiuixMainContent(
                    modifier = modifier,
                    intentToProcess = intentToProcess,
                    hapticEnabled = hapticEnabled,
                    useFloatingNavBar = useFloatingNavBar,
                    floatingNavBarStyle = floatingNavBarStyle,
                    navAlignment = navAlignment,
                    allowAppExit = backStack.size == 1,
                    externalPagerState = pagerState,
                    onNavigateToSettingsSubPage = { page ->
                        backStack.add(HomeRoute.SettingsSubPage(page))
                    },
                    onNavigateToOrderDetail = { orderId ->
                        backStack.add(HomeRoute.OrderDetail(orderId))
                    },
                    onNavigateToGroupDetail = { groupId ->
                        backStack.add(HomeRoute.GroupDetail(groupId))
                    }
                )
            }
            entry<HomeRoute.SettingsSubPage> { route ->
                MiuixSettingsSubPageDirect(
                    page = route.page,
                    onBack = { backStack.removeLastOrNull() },
                    onNavigate = { page ->
                        backStack.add(HomeRoute.SettingsSubPage(page))
                    }
                )
            }
            entry<HomeRoute.OrderDetail> { route ->
                val context = LocalContext.current
                val order = remember(route.orderId) {
                    runBlocking { OrderDatabase.getDatabase(context).orderDao().getOrderById(route.orderId) }
                }
                if (order != null) {
                    com.Badnng.moe.ui.screen.miuix.MiuixOrderDetailScreen(
                        order = order,
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
            }
            entry<HomeRoute.GroupDetail> { route ->
                val context = LocalContext.current
                val db = remember { OrderDatabase.getDatabase(context) }
                val group = remember(route.groupId) {
                    runBlocking { db.orderGroupDao().getGroupById(route.groupId) }
                }
                val orders = remember(route.groupId) {
                    runBlocking { db.orderGroupDao().getOrdersByGroupId(route.groupId).first() }
                }
                val completedCount = orders.count { it.isCompleted }
                val totalCount = orders.size
                if (group != null) {
                    com.Badnng.moe.ui.screen.miuix.MiuixGroupDetailScreen(
                        group = group,
                        orders = orders,
                        completedCount = completedCount,
                        totalCount = totalCount,
                        onBack = { backStack.removeLastOrNull() },
                        onMarkAllCompleted = {
                            runBlocking {
                                val now = System.currentTimeMillis()
                                db.orderGroupDao().markGroupAsCompleted(route.groupId, now)
                                db.orderGroupDao().markAllOrdersInGroupCompleted(route.groupId, now)
                            }
                            backStack.removeLastOrNull()
                        }
                    )
                }
            }
        }
    }

    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = homeEntryProvider,
    )
    val displayedEntries = if (predictiveBackEnabled) entries else entries.takeLast(1)
    NavDisplay(
        entries = displayedEntries,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        transitionEffects = remember {
            NavDisplayTransitionEffects(blockInputDuringTransition = true)
        },
    )

    BackHandler(enabled = !predictiveBackEnabled && backStack.size > 1) {
        backStack.removeLastOrNull()
    }
}

@Composable
private fun MiuixMainContent(
    modifier: Modifier,
    intentToProcess: Intent?,
    hapticEnabled: Boolean,
    useFloatingNavBar: Boolean,
    floatingNavBarStyle: MiuixFloatingNavigationBarStyle,
    navAlignment: String = "center",
    allowAppExit: Boolean,
    externalPagerState: androidx.compose.foundation.pager.PagerState? = null,
    onNavigateToSettingsSubPage: (SettingsPage) -> Unit,
    onNavigateToOrderDetail: (String) -> Unit = {},
    onNavigateToGroupDetail: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val pagerState = externalPagerState ?: rememberSaveablePagerState(pageCount = { 3 })
    val currentPage by remember { androidx.compose.runtime.derivedStateOf { pagerState.currentPage } }
    val navigationTargetPage by remember {
        androidx.compose.runtime.derivedStateOf { pagerState.targetPage }
    }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val viewModel: OrderViewModel = viewModel()

    // 大屏自适应底栏
    var largeScreenNavAdaptiveEnabled by remember {
        mutableStateOf(prefs.getBoolean("large_screen_nav_adaptive_enabled", true))
    }
    var currentNavAlignment by remember { mutableStateOf(navAlignment) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "nav_alignment") currentNavAlignment = p.getString(key, "center") ?: "center"
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                "theme_mode" -> themeMode = p.getString(key, "system") ?: "system"
                "large_screen_nav_adaptive_enabled" -> largeScreenNavAdaptiveEnabled = p.getBoolean(key, true)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val isInDarkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    var isManaging by remember { mutableStateOf(false) }
    var isScrollingDown by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isLargeScreen = configuration.screenWidthDp >= 700
    // 大屏默认使用侧边导航；用户显式开启悬浮底栏时继续保留原有底栏样式。
    val useNavigationRail = isLargeScreen && !useFloatingNavBar
    val isIosLikeFloatingBar = floatingNavBarStyle == MiuixFloatingNavigationBarStyle.IosLike
    val effectiveNavAlignment = if (isIosLikeFloatingBar && !isLargeScreen) {
        "center"
    } else {
        currentNavAlignment
    }
    val navAdaptiveActive = isLargeScreen && largeScreenNavAdaptiveEnabled && useFloatingNavBar

    // 规则页长按菜单状态
    var rulesMenuPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var rulesMenuShow by remember { mutableStateOf(false) }
    var rulesMenuExport: (() -> Unit)? by remember { mutableStateOf(null) }
    var rulesMenuRename: (() -> Unit)? by remember { mutableStateOf(null) }
    var rulesMenuDelete: (() -> Unit)? by remember { mutableStateOf(null) }

    val activity = context as? android.app.Activity

    // 折叠屏检测
    val windowInfoTracker = remember(context) { WindowInfoTracker.getOrCreate(context) }
    val layoutInfo by windowInfoTracker.windowLayoutInfo(context)
        .collectAsStateWithLifecycle(initialValue = null)
    val foldingFeature = layoutInfo?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull()
    val isFolded = foldingFeature?.state == FoldingFeature.State.HALF_OPENED
    val imeBottomInset = WindowInsets.ime.getBottom(LocalDensity.current)
    // 系统可能会把其他悬浮窗口的输入法 Insets 同步给当前窗口；仅当前 App
    // 仍持有窗口焦点时，才把它视为本应用正在使用输入法。
    val isImeVisible = imeBottomInset > 0 && LocalWindowInfo.current.isWindowFocused

    // 主页面按返回键时，从最近任务移除卡片
    androidx.activity.compose.BackHandler(
        enabled = allowAppExit && !isEditMode && !isManaging
    ) {
        activity?.finishAndRemoveTask()
    }

    val performHaptic = {
        if (hapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val targetBottomBarBias = when (effectiveNavAlignment) {
        "left" -> -1f
        "right" -> 1f
        else -> 0f
    }
    val animatedBottomBarBias by animateFloatAsState(
        targetValue = targetBottomBarBias,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 260f),
        label = "bottomBarBias"
    )
    val targetFloatingBarOffsetX = when (effectiveNavAlignment) {
        "left" -> (-5).dp
        "right" -> 5.dp
        else -> 0.dp
    }
    val animatedFloatingBarOffsetX by androidx.compose.animation.core.animateDpAsState(
        targetValue = targetFloatingBarOffsetX,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 260f),
        label = "floatingBarOffsetX"
    )
    val floatingBarVerticalOffset = if (isLargeScreen) {
        MiuixHomeBottomLayoutDefaults.LargeScreenFloatingBarVerticalOffset
    } else {
        MiuixHomeBottomLayoutDefaults.FloatingBarVerticalOffset
    }
    val density = LocalDensity.current
    val safeBottomInset = with(density) {
        WindowInsets.safeDrawing.getBottom(this).toDp()
    }
    var rootBottomInRootPx by remember { mutableIntStateOf(0) }
    var rootPositionInWindow by remember {
        mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    }
    var rootWidthPx by remember { mutableIntStateOf(0) }
    var navigationBarTopInRootPx by remember(
        useFloatingNavBar,
        floatingNavBarStyle,
        useNavigationRail,
    ) {
        mutableIntStateOf(-1)
    }
    val estimatedNavigationBarTopFromBottom = when {
        useNavigationRail -> safeBottomInset
        !useFloatingNavBar -> {
            safeBottomInset + NavigationBarDefaults.ItemHeight + NavigationBarDefaults.BottomPadding
        }
        isIosLikeFloatingBar -> {
            val bottomSpacing = if (safeBottomInset > 0.dp) {
                safeBottomInset + MiuixHomeBottomLayoutDefaults.IosLikeBottomSpacing
            } else {
                MiuixHomeBottomLayoutDefaults.IosLikeNoInsetBottomSpacing
            }
            bottomSpacing + MiuixHomeBottomLayoutDefaults.IosLikeBarHeight
        }
        else -> {
            (safeBottomInset - floatingBarVerticalOffset).coerceAtLeast(0.dp) +
                FloatingNavigationBarDefaults.IconSize +
                FloatingNavigationBarDefaults.IconPadding +
                FloatingNavigationBarDefaults.IconPadding
        }
    }
    // 切换到 NavigationRail 时，退出中的底栏仍可能在最后一帧回写旧坐标。
    // 窗口随后增高会把这份旧坐标误算成数百 dp 的底部间距，导致工具栏悬在中间。
    // 侧栏模式没有底部导航，必须直接使用当前窗口的安全区，忽略所有底栏测量值。
    val measuredNavigationBarTopFromBottom = if (useNavigationRail) {
        null
    } else {
        (rootBottomInRootPx - navigationBarTopInRootPx)
            .takeIf { rootBottomInRootPx > 0 && navigationBarTopInRootPx >= 0 && it > 0 }
            ?.let { with(density) { it.toDp() } }
    }
    val bottomLayoutInfo = MiuixHomeBottomLayoutInfo(
        safeBottomInset = safeBottomInset,
        navigationBarTopFromBottom = measuredNavigationBarTopFromBottom
            ?: estimatedNavigationBarTopFromBottom
    )

    // 模糊效果
    val backdrop = com.Badnng.moe.ui.miuix.rememberMiuixBackdrop()
    // 独立采样完整主页（包含底栏与 NavigationRail），供所有主页模态层使用。
    // 必须与底栏自身使用的 backdrop 分开，避免 layerBackdrop / textureBlur 递归。
    val homeOverlayBackdrop = com.Badnng.moe.ui.miuix.rememberMiuixBackdrop()
    val blurEnabled = backdrop != null
    val animatedMenuAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (rulesMenuShow) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
        label = "rulesMenuScrimAlpha",
    )
    val sheetProgress = com.Badnng.moe.ui.component.BlurState.progress.floatValue
    val homeOverlayProgress = maxOf(sheetProgress, animatedMenuAlpha)
    // NavigationRailState 是侧栏展开/完全收起的唯一状态源。
    val navigationRailState = rememberNavigationRailState(
        initialValue = NavigationRailValue.Expanded,
    )
    val navigationRailAvailable = useNavigationRail &&
        !isEditMode &&
        !isManaging
    val navigationRailVisible = navigationRailAvailable && navigationRailState.isExpanded
    val onExpandNavigationRail: (() -> Unit)? = if (
        navigationRailAvailable && !navigationRailState.isExpanded
    ) {
        {
            performHaptic()
            navigationRailState.expand()
        }
    } else {
        null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                rootBottomInRootPx = coordinates.boundsInRoot().bottom.roundToInt()
                rootPositionInWindow = coordinates.positionInWindow()
                rootWidthPx = coordinates.size.width
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (homeOverlayBackdrop != null && homeOverlayProgress > 0.01f) {
                        Modifier.layerBackdrop(homeOverlayBackdrop)
                    } else {
                        Modifier
                    },
                ),
        ) {
        AnimatedVisibility(
            visible = navigationRailVisible,
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
        ) {
            NavigationRail(
                state = navigationRailState,
                modifier = Modifier.fillMaxHeight(),
                color = MiuixTheme.colorScheme.surface,
                expandContentDescription = "展开侧边导航",
                collapseContentDescription = "收起侧边导航",
            ) {
                NavigationRailItem(
                    selected = currentPage == 0,
                    onClick = {
                        performHaptic()
                        coroutineScope.launch { pagerState.animateScrollToPage(0) }
                    },
                    icon = MiuixIcons.Regular.Home,
                    label = "主页",
                )
                NavigationRailItem(
                    selected = currentPage == 1,
                    onClick = {
                        performHaptic()
                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                    },
                    icon = MiuixIcons.Regular.Edit,
                    label = "规则",
                )
                NavigationRailItem(
                    selected = currentPage == 2,
                    onClick = {
                        performHaptic()
                        coroutineScope.launch { pagerState.animateScrollToPage(2) }
                    },
                    icon = MiuixIcons.Regular.Settings,
                    label = "设置",
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
        // Scaffold 内容层（layerBackdrop 只应用到内容，不包含底栏）
        Scaffold(
            modifier = Modifier.fillMaxSize(),
        ) { _ ->
            Box(modifier = (if (backdrop != null) Modifier.fillMaxSize().layerBackdrop(backdrop) else Modifier.fillMaxSize())
                .pointerInput(navAdaptiveActive, pagerState.currentPage) {
                    if (!navAdaptiveActive) return@pointerInput
                    var downX = 0f
                    var downY = 0f
                    var downZone = "center"
                    var gestureDirection = 0
                    var directionLocked = false
                    val directionThresholdPx = 14f
                    val axisRatio = 1.2f
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (pagerState.currentPage != 0) {
                                gestureDirection = 0; directionLocked = false; continue
                            }
                            val change = event.changes.firstOrNull() ?: continue
                            val x = change.position.x
                            val y = change.position.y
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            val zoneAtX = when {
                                x < width / 3f -> "left"
                                x > width * 2f / 3f -> "right"
                                else -> "center"
                            }
                            if (change.pressed && !change.previousPressed) {
                                gestureDirection = 0; directionLocked = false
                                downX = x; downY = y; downZone = zoneAtX
                            }
                            if (change.pressed && !directionLocked) {
                                val dx = x - downX; val dy = y - downY
                                val absDx = kotlin.math.abs(dx); val absDy = kotlin.math.abs(dy)
                                if (absDx >= directionThresholdPx || absDy >= directionThresholdPx) {
                                    val verticalDominant = absDy > absDx * axisRatio
                                    val horizontalDominant = absDx > absDy * axisRatio
                                    if (!verticalDominant && !horizontalDominant) continue
                                    gestureDirection = if (verticalDominant) 1 else -1
                                    directionLocked = true
                                    if (gestureDirection == 1 && navAdaptiveActive) {
                                        if (currentNavAlignment != downZone) {
                                            currentNavAlignment = downZone
                                            prefs.edit().putString("nav_alignment", downZone).apply()
                                        }
                                    }
                                }
                            }
                            if (!change.pressed && change.previousPressed || event.changes.none { it.pressed }) {
                                gestureDirection = 0; directionLocked = false
                            }
                        }
                    }
                }
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1 // 预加载相邻页面，减少切换时重组
                ) { page ->
                    androidx.compose.runtime.key(page) {
                        when (page) {
                            0 -> MiuixCaptureScreen(
                                bottomLayoutInfo = bottomLayoutInfo,
                                onExpandNavigationRail = onExpandNavigationRail,
                                onScrollStateChange = { isScrollingDown = it },
                                onEditModeChange = { isEditMode = it },
                                onAddClick = { showBottomSheet = true },
                                navAlignment = effectiveNavAlignment,
                                useFloatingNavBar = useFloatingNavBar,
                                onNavigateToOrderDetail = onNavigateToOrderDetail,
                                onNavigateToGroupDetail = onNavigateToGroupDetail
                            )
                            1 -> MiuixRulesScreen(
                                bottomLayoutInfo = bottomLayoutInfo,
                                onExpandNavigationRail = onExpandNavigationRail,
                                onShowMenu = { position, rename, delete, export ->
                                    rulesMenuPosition = position
                                    rulesMenuRename = rename
                                    rulesMenuDelete = delete
                                    rulesMenuExport = export
                                    rulesMenuShow = true
                                }
                            )
                            2 -> MiuixSettingsScreen(
                                bottomLayoutInfo = bottomLayoutInfo,
                                onExpandNavigationRail = onExpandNavigationRail,
                                onNavigateToSubPage = onNavigateToSettingsSubPage
                            )
                        }
                    }
                }
            } // Box layerBackdrop
        }

        // 底栏：覆盖在 Scaffold 上方，支持模糊效果。
        // 模态层的全屏模糊在外层 Row 之后绘制，因此底栏不会消失，但会处于模糊层下方。
        // 标准底栏（手机/中等宽度设备的非悬浮模式）
        NavigationOverlayVisibility(
            visible = !isEditMode &&
                !isManaging &&
                !isScrollingDown &&
                !isImeVisible &&
                !useNavigationRail &&
                !useFloatingNavBar,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val barColor = if (blurEnabled) Color.Transparent else MiuixTheme.colorScheme.surface
            Box(
                modifier = if (blurEnabled && backdrop != null) {
                    Modifier.fillMaxWidth().textureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = 25f,
                        colors = BlurDefaults.blurColors(
                            blendColors = listOf(
                                BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(0.8f)),
                            ),
                        ),
                    )
                } else {
                    Modifier.fillMaxWidth()
                }
            ) {
                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            navigationBarTopInRootPx = coordinates.boundsInRoot().top.roundToInt()
                        },
                    color = barColor
                ) {
                    NavigationBarItem(
                        selected = currentPage == 0,
                        onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        icon = MiuixIcons.Regular.Home,
                        label = "主页"
                    )
                    NavigationBarItem(
                        selected = currentPage == 1,
                        onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        icon = MiuixIcons.Regular.Edit,
                        label = "规则"
                    )
                    NavigationBarItem(
                        selected = currentPage == 2,
                        onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                        icon = MiuixIcons.Regular.Settings,
                        label = "设置"
                    )
                }
            }
        }

        // 悬浮底栏
        NavigationOverlayVisibility(
            visible = !isEditMode &&
                !isManaging &&
                !isScrollingDown &&
                !isImeVisible &&
                useFloatingNavBar &&
                !isFolded,
        ) {
            if (isIosLikeFloatingBar) {
                val navigationItems = remember {
                    listOf(
                        NavigationItem(label = "主页", icon = MiuixIcons.Regular.Home),
                        NavigationItem(label = "规则", icon = MiuixIcons.Regular.Edit),
                        NavigationItem(label = "设置", icon = MiuixIcons.Regular.Settings),
                    )
                }
                val iosBarModifier = if (isLargeScreen) {
                    Modifier.widthIn(max = MiuixHomeBottomLayoutDefaults.IosLikeLargeScreenMaxWidth)
                } else {
                    Modifier
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(x = if (isLargeScreen) animatedFloatingBarOffsetX else 0.dp),
                    contentAlignment = BiasAlignment(
                        if (isLargeScreen) animatedBottomBarBias else 0f,
                        1f,
                    )
                ) {
                    IosLiquidGlassNavigationBar(
                        items = navigationItems,
                        selectedIndex = navigationTargetPage,
                        onItemClick = { page ->
                            performHaptic()
                            coroutineScope.launch { pagerState.animateScrollToPage(page) }
                        },
                        backdrop = backdrop,
                        isBlurActive = blurEnabled,
                        isDark = isInDarkTheme,
                        modifier = iosBarModifier.onGloballyPositioned { coordinates ->
                            navigationBarTopInRootPx = coordinates.boundsInRoot().top.roundToInt()
                        },
                    )
                }
            } else {
                val floatingBarColor = if (blurEnabled) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer
                val isDark = isInDarkTheme
                val floatingHighlight = remember(isDark) {
                    if (isDark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight
                }
                val floatingBarModifier = if (blurEnabled && backdrop != null) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(FloatingToolbarDefaults.CornerRadius),
                        blurRadius = 25f,
                        colors = BlurDefaults.blurColors(
                            blendColors = listOf(
                                BlendColorEntry(color = MiuixTheme.colorScheme.surfaceContainer.copy(0.6f)),
                            ),
                        ),
                        highlight = floatingHighlight,
                    )
                } else {
                    Modifier
                }
                val animatedHorizontalAlignment = remember(animatedBottomBarBias) {
                    object : Alignment.Horizontal {
                        override fun align(size: Int, space: Int, layoutDirection: LayoutDirection): Int {
                            val start = 0
                            val center = (space - size) / 2
                            val end = space - size
                            return when {
                                animatedBottomBarBias < 0f -> (center + (center - start) * animatedBottomBarBias).toInt()
                                animatedBottomBarBias > 0f -> (center + (end - center) * animatedBottomBarBias).toInt()
                                else -> center
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        .padding(horizontal = 24.dp)
                        .offset(
                            x = animatedFloatingBarOffsetX,
                            y = floatingBarVerticalOffset
                        ),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    FloatingNavigationBar(
                        modifier = floatingBarModifier
                            .onGloballyPositioned { coordinates ->
                                navigationBarTopInRootPx = coordinates.boundsInRoot().top.roundToInt()
                            },
                        color = floatingBarColor,
                        horizontalAlignment = animatedHorizontalAlignment,
                        horizontalOutSidePadding = 24.dp
                    ) {
                        FloatingNavigationBarItem(
                            selected = currentPage == 0,
                            onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                            icon = MiuixIcons.Regular.Home,
                            label = "主页"
                        )
                        FloatingNavigationBarItem(
                            selected = currentPage == 1,
                            onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                            icon = MiuixIcons.Regular.Edit,
                            label = "规则"
                        )
                        FloatingNavigationBarItem(
                            selected = currentPage == 2,
                            onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                            icon = MiuixIcons.Regular.Settings,
                            label = "设置"
                        )
                    }
                }
            }
        }

        // 添加记录底部弹窗
        val addOrderViewModel: OrderViewModel = viewModel()
        com.Badnng.moe.ui.component.AddOrderBottomSheet(
            show = showBottomSheet,
            viewModel = addOrderViewModel,
            onDismiss = { showBottomSheet = false }
        )
        } // 内容 Box
        } // Row

        // 主页模态层统一覆盖完整 Row，确保 BottomSheet 打开时底栏/侧边栏仍存在但不会漏在模糊上方。
        com.Badnng.moe.ui.miuix.MiuixModalScrim(
            backdrop = homeOverlayBackdrop,
            progress = homeOverlayProgress,
        )

        // 规则长按菜单位于统一模糊层之上。
        val animatedMenuCardAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (rulesMenuShow) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 250, delayMillis = 50)
        )
        val animatedMenuCardScale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (rulesMenuShow) 1f else 0.9f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 250, delayMillis = 50)
        )
        if (animatedMenuAlpha > 0f) {
            val density = LocalDensity.current
            Box(modifier = Modifier.fillMaxSize()) {
                // 点击遮罩关闭菜单
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { rulesMenuShow = false }
                )
                val configuration = LocalConfiguration.current
                val screenWidthPx = rootWidthPx
                    .takeIf { it > 0 }
                    ?.toFloat()
                    ?: with(density) { configuration.screenWidthDp.dp.toPx() }
                val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
                val cardMaxHeightPx = with(density) { 160.dp.toPx() }
                val localMenuPosition = rulesMenuPosition - rootPositionInWindow

                var cardWidthMeasured by remember { mutableIntStateOf(0) }
                val cardXDp = with(density) {
                    localMenuPosition.x
                        .coerceIn(0f, (screenWidthPx - cardWidthMeasured).coerceAtLeast(0f))
                        .toDp()
                }
                val cardYDp = with(density) {
                    val rawY = localMenuPosition.y
                    if (rawY + cardMaxHeightPx > screenHeightPx) {
                        (localMenuPosition.y - cardMaxHeightPx).coerceAtLeast(0f).toDp()
                    } else {
                        rawY.toDp()
                    }
                }
                top.yukonga.miuix.kmp.basic.Card(
                    modifier = Modifier
                        .offset(x = cardXDp, y = cardYDp)
                        .onGloballyPositioned { cardWidthMeasured = it.size.width }
                        .widthIn(max = 280.dp)
                        .graphicsLayer {
                            alpha = animatedMenuCardAlpha
                            scaleX = animatedMenuCardScale
                            scaleY = animatedMenuCardScale
                        }
                        .squircleBorder(
                            1.dp,
                            MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f),
                            16.dp,
                        ),
                    colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                        MiuixTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                ) {
                    val menuItems = buildList {
                        if (rulesMenuRename != null) add("rename")
                        if (rulesMenuExport != null) add("export")
                        if (rulesMenuDelete != null) add("delete")
                    }
                    Column {
                        menuItems.forEach { item ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        performHaptic()
                                        when (item) {
                                            "rename" -> rulesMenuRename?.invoke()
                                            "export" -> rulesMenuExport?.invoke()
                                            "delete" -> rulesMenuDelete?.invoke()
                                        }
                                        rulesMenuShow = false
                                    }
                            ) {
                                val (icon, label, color) = when (item) {
                                    "rename" -> Triple(MiuixIcons.Regular.Edit, "重命名", MiuixTheme.colorScheme.onSurface)
                                    "export" -> Triple(MiuixIcons.Regular.UploadCloud, "导出规则", MiuixTheme.colorScheme.onSurface)
                                    "delete" -> Triple(MiuixIcons.Regular.Delete, "删除", MiuixTheme.colorScheme.error)
                                    else -> Triple(MiuixIcons.Regular.Edit, "", Color.Unspecified)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    top.yukonga.miuix.kmp.basic.Icon(icon, null, tint = color)
                                    Spacer(Modifier.width(12.dp))
                                    top.yukonga.miuix.kmp.basic.Text(label, color = color)
                                }
                            }
                        }
                    }
                }
            }
        }
    } // 根层 Box
}

@Composable
internal fun MiuixNavigationRailExpandButton(
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = MiuixIcons.Basic.Sidebar,
            contentDescription = "展开侧边导航",
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun NavigationOverlayVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        content()
    }
}

@Composable
private fun MiuixSettingsSubPageDirect(
    page: SettingsPage,
    onBack: () -> Unit,
    onNavigate: (SettingsPage) -> Unit = {}
) {
    val title = when (page) {
        SettingsPage.Preference -> "偏好设置"
        SettingsPage.Permission -> "权限与保活"
        SettingsPage.Screenshot -> "截图方式"
        SettingsPage.Recognition -> "识别方式"
        SettingsPage.KeepAlive -> "保活设置"
        SettingsPage.Storage -> "清理空间"
        SettingsPage.About -> "关于"
        SettingsPage.Sponsor -> "赞助"
        SettingsPage.NotificationApps -> "通知识别应用管理"
        SettingsPage.Credits -> "致谢"
        SettingsPage.Main -> ""
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val haptic = LocalHapticFeedback.current
    val appUi = LocalAppUi.current
    val performHaptic = {
        if (prefs.getBoolean("haptic_enabled", true)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    var showSystemApps by remember(page) { mutableStateOf(false) }

    // 顶栏采样层统一受 Miuix 视觉性能策略控制。
    val backdrop = com.Badnng.moe.ui.miuix.rememberMiuixBackdrop()
    val blurEnabled = backdrop != null

    if (page == SettingsPage.About) {
        // 关于页面：自包含 Scaffold（照搬示例项目 AboutPage）
        com.Badnng.moe.ui.screen.settings.AboutSettingsContent(
            performHaptic = performHaptic,
            topPadding = 0.dp,
            scrollState = androidx.compose.foundation.rememberScrollState(),
            onNavigateToCredits = { onNavigate(SettingsPage.Credits) },
            onBack = onBack
        )
    } else {
        // 独立采样整个页面（包含状态栏与 TopAppBar），供 BottomSheet 遮罩使用。
        // 与顶栏自身的 backdrop 分开，避免 layerBackdrop / textureBlur 递归渲染。
        val sheetBackdrop = com.Badnng.moe.ui.miuix.rememberMiuixBackdrop()
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (sheetBackdrop != null) {
                            Modifier.layerBackdrop(sheetBackdrop)
                        } else {
                            Modifier
                        },
                    ),
                topBar = {
                    val topBarColor = if (blurEnabled) Color.Transparent else MiuixTheme.colorScheme.surface
                    com.Badnng.moe.ui.miuix.MiuixBlurredBar(backdrop = backdrop, blurEnabled = blurEnabled) {
                        TopAppBar(
                            title = title,
                            color = topBarColor,
                            scrollBehavior = topAppBarScrollBehavior,
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(
                                        MiuixIcons.Regular.Back,
                                        contentDescription = "返回"
                                    )
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
                }
            ) { innerPadding ->
                val scrollState = androidx.compose.foundation.rememberScrollState()
                val topBarHeight = innerPadding.calculateTopPadding()
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .miuixScrollModifiers(topAppBarScrollBehavior)
                        ) {
                            when (page) {
                                SettingsPage.Screenshot -> com.Badnng.moe.ui.screen.settings.ScreenshotSettingsContent(performHaptic, topBarHeight, scrollState)
                                SettingsPage.Recognition -> com.Badnng.moe.ui.screen.settings.RecognitionSettingsContent(performHaptic, topBarHeight, scrollState)
                                SettingsPage.Permission -> com.Badnng.moe.ui.screen.settings.PermissionSettingsContent(performHaptic, topBarHeight, scrollState)
                                SettingsPage.Preference -> com.Badnng.moe.ui.screen.settings.PreferenceSettingsContent(performHaptic, onNavigate, topBarHeight, scrollState)
                                SettingsPage.KeepAlive -> com.Badnng.moe.ui.screen.settings.KeepAliveSettingsContent(performHaptic, topBarHeight, scrollState)
                                SettingsPage.Storage -> com.Badnng.moe.ui.screen.settings.StorageSettingsContent(performHaptic, prefs, topBarHeight + 26.dp, scrollState)
                                SettingsPage.Sponsor -> com.Badnng.moe.ui.screen.settings.SponsorSettingsContent(topBarHeight, scrollState)
                                SettingsPage.NotificationApps -> com.Badnng.moe.ui.screen.settings.NotificationAppsSettingsContent(
                                    performHaptic = performHaptic,
                                    topPadding = topBarHeight + 8.dp,
                                    showSystemApps = showSystemApps
                                )
                                SettingsPage.Credits -> com.Badnng.moe.ui.screen.settings.CreditsSettingsContent(performHaptic, topBarHeight, scrollState)
                                SettingsPage.Main -> {}
                                else -> {}
                            }
                        }
                    }
                }
            }

            val sheetProgress = com.Badnng.moe.ui.component.BlurState.progress.floatValue
            com.Badnng.moe.ui.miuix.MiuixModalScrim(
                backdrop = sheetBackdrop,
                progress = sheetProgress,
            )
        }
    }
}
