package com.Badnng.moe.ui.screen

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Home as OutlinedHome
import androidx.compose.material.icons.outlined.Info as OutlinedInfo
import androidx.compose.material.icons.outlined.Settings as OutlinedSettings
import androidx.compose.material.icons.outlined.Tune as OutlinedTune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Badnng.moe.activity.MainActivity
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.data.db.OrderGroup
import com.Badnng.moe.helper.ImageSourceMetadataResolver
import com.Badnng.moe.helper.ScreenshotStorage
import com.Badnng.moe.ocr.RecognitionResult
import com.Badnng.moe.recognition.RecognizedOrderFactory
import com.Badnng.moe.recognition.RecognitionExecutionMetadata
import com.Badnng.moe.recognition.RecognitionRouter
import com.Badnng.moe.recognition.RecognitionCorrectionDetector
import com.Badnng.moe.recognition.RecognitionCorrectionStore
import com.Badnng.moe.recognition.OnlineRecognitionPreferences
import com.Badnng.moe.recognition.RecognitionTrigger
import com.Badnng.moe.viewmodel.OrderViewModel
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.launch
import com.Badnng.moe.ui.screen.settings.SettingsScreen
import com.Badnng.moe.ui.screen.settings.SettingsPage
import com.Badnng.moe.ui.screen.settings.SubPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * 辅助函数：支持 rememberSaveable 的 PagerState
 * 确保折叠屏展开/折叠时页面状态不丢失
 */
@Composable
fun rememberSaveablePagerState(pageCount: () -> Int): PagerState {
    val currentPage = rememberSaveable { mutableIntStateOf(0) }
    val latestPageCount by rememberUpdatedState(pageCount)
    val pagerState = remember {
        PagerState(
            currentPage = currentPage.value,
            pageCount = { latestPageCount() },
        )
    }

    // 首次加载时从保存的状态恢复页面位置
    LaunchedEffect(Unit) {
        if (pagerState.currentPage != currentPage.value) {
            pagerState.scrollToPage(currentPage.value, 0f)
        }
    }

    // 只保存已经停稳的页面。窗口失焦可能中断拖动，currentPage 此时只是临时页，
    // 若直接保存会在恢复窗口时跳到相邻页面。
    LaunchedEffect(pagerState.settledPage) {
        if (currentPage.value != pagerState.settledPage) {
            currentPage.intValue = pagerState.settledPage
        }
    }

    return pagerState
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    intentToProcess: Intent? = null
) {
    val isMiuix = com.Badnng.moe.ui.miuix.rememberMiuixStyle()
    val pagerState = rememberSaveablePagerState(pageCount = { 4 })

    if (isMiuix) {
        com.Badnng.moe.ui.screen.miuix.MiuixHomeScreen(
            modifier = modifier,
            intentToProcess = intentToProcess,
            pagerState = pagerState
        )
        return
    }
    val coroutineScope = rememberCoroutineScope()
    var showBottomSheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val viewModel: OrderViewModel = viewModel()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val orderGroups by viewModel.orderGroups.collectAsStateWithLifecycle()
    var selectedOrderForQr by remember { mutableStateOf<OrderEntity?>(null) }
    var detailOrder by remember { mutableStateOf<OrderEntity?>(null) }
    var detailGroup by remember { mutableStateOf<OrderGroup?>(null) }
    var settingsDetailStack by remember { mutableStateOf<List<SettingsPage>>(emptyList()) }
    var isFromNotification by rememberSaveable { mutableStateOf(false) }
    var isManaging by rememberSaveable { mutableStateOf(false) }
    var groupOrders by remember { mutableStateOf<List<OrderEntity>>(emptyList()) }

    var backProgress by remember { mutableFloatStateOf(0f) }
    var backSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var isPredictiveBackInProgress by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    var hapticEnabled by remember { mutableStateOf(prefs.getBoolean("haptic_enabled", true)) }
    var predictiveBackEnabled by remember {
        mutableStateOf(prefs.getBoolean("predictive_back_enabled", true))
    }
    var amoledPureBlack by remember { mutableStateOf(prefs.getBoolean("amoled_pure_black", false)) }
    var useFloatingNavBar by remember {
        mutableStateOf(prefs.getBoolean("use_floating_nav_bar", false))
    }
    val configuration = LocalConfiguration.current
    val isLargeScreen = configuration.screenWidthDp >= 700
    val compactNavigationRail = isLargeScreen &&
        configuration.screenWidthDp < MD3E_FIXED_NAVIGATION_RAIL_MIN_WIDTH_DP
    val navigationRailState = rememberWideNavigationRailState(
        initialValue = if (compactNavigationRail) {
            WideNavigationRailValue.Collapsed
        } else {
            WideNavigationRailValue.Expanded
        },
    )
    val navigationRailExpanded =
        navigationRailState.targetValue == WideNavigationRailValue.Expanded
    val navigationRailOverlayVisible = navigationRailExpanded ||
        navigationRailState.currentValue == WideNavigationRailValue.Expanded
    var selectedTopLevelPage by rememberSaveable {
        mutableIntStateOf(pagerState.currentPage)
    }
    var directTopLevelTransition by remember {
        mutableStateOf<Md3eDirectTopLevelTransition?>(null)
    }
    val directTopLevelTransitionProgress = remember { Animatable(0f) }

    LaunchedEffect(isLargeScreen, compactNavigationRail) {
        when {
            !isLargeScreen -> navigationRailState.collapse()
            compactNavigationRail -> navigationRailState.collapse()
            else -> navigationRailState.expand()
        }
    }
    LaunchedEffect(isLargeScreen) {
        if (!isLargeScreen && pagerState.currentPage != selectedTopLevelPage) {
            pagerState.scrollToPage(selectedTopLevelPage)
        }
    }
    LaunchedEffect(pagerState.currentPage, isLargeScreen) {
        if (!isLargeScreen && selectedTopLevelPage != pagerState.currentPage) {
            selectedTopLevelPage = pagerState.currentPage
        }
    }

    // 折叠屏开合检测
    val windowInfoTracker = remember(context) { WindowInfoTracker.getOrCreate(context) }
    val layoutInfo by windowInfoTracker.windowLayoutInfo(context)
        .collectAsStateWithLifecycle(initialValue = null)
    val foldingFeature = layoutInfo?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull()
    val isFolded = foldingFeature?.state == FoldingFeature.State.HALF_OPENED
    val imeBottomPadding = WindowInsets.ime.getBottom(LocalDensity.current)
    val isImeVisible = imeBottomPadding > 0 && LocalWindowInfo.current.isWindowFocused

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                "haptic_enabled" -> hapticEnabled = p.getBoolean(key, true)
                "predictive_back_enabled" -> predictiveBackEnabled = p.getBoolean(key, true)
                "amoled_pure_black" -> amoledPureBlack = p.getBoolean(key, false)
                "use_floating_nav_bar" -> useFloatingNavBar = p.getBoolean(key, false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val performHaptic = {
        if (hapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    val onExpandNavigationRail: (() -> Unit)? = if (
        compactNavigationRail && !navigationRailOverlayVisible
    ) {
        {
            performHaptic()
            coroutineScope.launch { navigationRailState.expand() }
        }
    } else {
        null
    }
    val motionScheme = MaterialTheme.motionScheme
    val compactNavigationRailOffset by animateDpAsState(
        targetValue = if (navigationRailExpanded) {
            0.dp
        } else {
            -MD3E_COLLAPSED_NAVIGATION_RAIL_WIDTH
        },
        animationSpec = motionScheme.defaultSpatialSpec<Dp>(),
        label = "compactNavigationRailOffset",
    )
    val fontScale = LocalDensity.current.fontScale
    val largeFont = fontScale >= 1.2f
    val bottomBarHeight = if (largeFont) 72.dp else 64.dp
    val targetFabAboveBottomBarPadding = when {
        isLargeScreen -> 24.dp
        useFloatingNavBar -> bottomBarHeight + 70.dp
        else -> bottomBarHeight + 24.dp
    }
    val fabAboveBottomBarPadding by animateDpAsState(
        targetValue = targetFabAboveBottomBarPadding,
        animationSpec = motionScheme.defaultSpatialSpec<Dp>(),
        label = "md3eFabBottomPadding",
    )
    val fabHorizontalPadding = 24.dp
    val fabColumnAlignment = Alignment.End
    val identityContainerOffsetX = 0.dp
    val fabContentAlignment = Alignment.BottomEnd

    LaunchedEffect(detailGroup) {
        detailGroup?.let { group ->
            viewModel.getOrdersByGroupId(group.id).collect { orders ->
                groupOrders = orders
            }
        }
    }


    PredictiveBackHandler(
        enabled = predictiveBackEnabled &&
            (settingsDetailStack.isNotEmpty() || detailOrder != null || detailGroup != null)
    ) { backEvent: Flow<androidx.activity.BackEventCompat> ->
        isPredictiveBackInProgress = true
        try {
            backEvent.collect { event ->
                backProgress = event.progress
                backSwipeEdge = event.swipeEdge
            }
            when {
                settingsDetailStack.isNotEmpty() -> settingsDetailStack = settingsDetailStack.dropLast(1)
                detailOrder != null -> detailOrder = null
                detailGroup != null -> detailGroup = null
            }
        } catch (e: CancellationException) {
            // 手势取消时状态尚未提交，无需恢复详情对象。
        } finally {
            isPredictiveBackInProgress = false
            backProgress = 0f
        }
    }

    BackHandler(
        enabled = !predictiveBackEnabled &&
            (settingsDetailStack.isNotEmpty() || detailOrder != null || detailGroup != null)
    ) {
        when {
            settingsDetailStack.isNotEmpty() -> settingsDetailStack = settingsDetailStack.dropLast(1)
            detailOrder != null -> detailOrder = null
            detailGroup != null -> detailGroup = null
        }
    }

    val activity = context as? MainActivity

    // 主页面按返回键时，从最近任务移除卡片
    BackHandler(
        enabled = settingsDetailStack.isEmpty() && detailOrder == null && detailGroup == null,
    ) {
        activity?.finishAndRemoveTask()
    }
    BackHandler(enabled = compactNavigationRail && navigationRailExpanded) {
        coroutineScope.launch { navigationRailState.collapse() }
    }

    val currentScale = if (isPredictiveBackInProgress) 1f - (backProgress * 0.08f) else 1f
    val currentTranslationX = if (isPredictiveBackInProgress) {
        val multiplier = if (backSwipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
        backProgress * 100f * multiplier
    } else 0f
    val currentCornerRadius = if (isPredictiveBackInProgress) (backProgress * 32).dp else 0.dp

    LaunchedEffect(intentToProcess, orders) {
        if (intentToProcess?.getBooleanExtra("show_qr_detail", false) == true) {
            val orderId = intentToProcess.getStringExtra("order_id")
            val order = orders.find { it.id == orderId }
            if (order != null) {
                selectedOrderForQr = order
                isFromNotification = intentToProcess.getBooleanExtra("from_notification", false)
                activity?.intentToProcess = null
            }
        }
        if (intentToProcess?.hasExtra("highlight_order_id") == true) {
            detailOrder = null // 自动关闭详情页回到列表
            detailGroup = null
            settingsDetailStack = emptyList()
            if (isLargeScreen) {
                selectedTopLevelPage = 0
            } else {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(
                        page = 0,
                        animationSpec = motionScheme.defaultSpatialSpec<Float>(),
                    )
                }
            }
        }
    }

    LaunchedEffect(intentToProcess, orderGroups) {
        if (intentToProcess?.getBooleanExtra("show_group_detail", false) == true) {
            detailOrder = null
            detailGroup = null
            settingsDetailStack = emptyList()
            if (isLargeScreen) {
                selectedTopLevelPage = 0
            } else {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(
                        page = 0,
                        animationSpec = motionScheme.defaultSpatialSpec<Float>(),
                    )
                }
            }
        }
    }

    // 从更新下载通知进入时，跳转到关于页
    LaunchedEffect(intentToProcess) {
        if (intentToProcess?.getBooleanExtra("show_update_download", false) == true) {
            if (isLargeScreen) {
                selectedTopLevelPage = 3
            } else {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(
                        page = 3,
                        animationSpec = motionScheme.defaultSpatialSpec<Float>(),
                    )
                }
            }
            activity?.intentToProcess = null
        }
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    val isDarkPalette = backgroundColor.luminance() < 0.5f
    val usePureBlackHomeBackground = amoledPureBlack && isDarkPalette
    val homeBackgroundColor = if (usePureBlackHomeBackground) Color.Black else backgroundColor

    var isScrollingDown by remember { mutableStateOf(false) }
    val isUiHidden = settingsDetailStack.isNotEmpty() ||
        detailOrder != null ||
        detailGroup != null ||
        isManaging

    // 切换页面时重置滚动状态，确保底栏和FAB正确显示
    LaunchedEffect(selectedTopLevelPage) {
        isScrollingDown = false
    }

    // 全屏菜单状态
    var showMenu by remember { mutableStateOf(false) }
    var menuPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var menuRename by remember { mutableStateOf<(() -> Unit)?>(null) }
    var menuDelete by remember { mutableStateOf<(() -> Unit)?>(null) }
    var menuExport by remember { mutableStateOf<(() -> Unit)?>(null) }

    val navigateToTopLevel: (Int) -> Unit = { page ->
        if (directTopLevelTransition == null) {
            performHaptic()
            selectedTopLevelPage = page
            if (compactNavigationRail) {
                coroutineScope.launch { navigationRailState.collapse() }
            }
            if (!isLargeScreen && page != pagerState.currentPage) {
                val sourcePage = pagerState.currentPage
                if (kotlin.math.abs(page - sourcePage) > 1) {
                    directTopLevelTransition = Md3eDirectTopLevelTransition(
                        sourcePage = sourcePage,
                        targetPage = page,
                    )
                    coroutineScope.launch {
                        directTopLevelTransitionProgress.snapTo(0f)
                        try {
                            directTopLevelTransitionProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = motionScheme.defaultSpatialSpec<Float>(),
                            )
                            // 目标页完全覆盖后再同步 Pager，避免跨页滚动渲染中间页面。
                            pagerState.scrollToPage(page)
                            withFrameNanos { }
                        } finally {
                            directTopLevelTransition = null
                            directTopLevelTransitionProgress.snapTo(0f)
                        }
                    }
                } else {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(
                            page = page,
                            animationSpec = motionScheme.defaultSpatialSpec<Float>(),
                        )
                    }
                }
            }
        }
    }

    val openSettingsDetail: (SettingsPage) -> Unit = { page ->
        detailOrder = null
        detailGroup = null
        settingsDetailStack = listOf(page)
    }
    val pageContent: @Composable (Int) -> Unit = { page ->
        when (page) {
            0 -> CaptureScreen(
                modifier = Modifier.fillMaxSize(),
                bottomPadding = if (isLargeScreen) 24.dp else 100.dp,
                onExpandNavigationRail = onExpandNavigationRail,
                onEditModeChange = { isManaging = it },
                onScrollStateChange = { isScrollingDown = it },
                onNavigateToDetail = { detailItem ->
                    settingsDetailStack = emptyList()
                    when (detailItem) {
                        is OrderEntity -> {
                            detailGroup = null
                            detailOrder = detailItem
                        }
                        is OrderGroup -> {
                            detailOrder = null
                            detailGroup = detailItem
                        }
                        is SettingsPage -> {
                            detailOrder = null
                            detailGroup = null
                            settingsDetailStack = listOf(detailItem)
                        }
                    }
                },
            )
            1 -> RulesScreen(
                modifier = Modifier.fillMaxSize(),
                onExpandNavigationRail = onExpandNavigationRail,
                onShowMenu = { position, rename, delete, export ->
                    menuPosition = position
                    menuRename = rename
                    menuDelete = delete
                    menuExport = export
                    showMenu = true
                },
                onDismissMenu = { showMenu = false },
            )
            2 -> SettingsScreen(
                modifier = Modifier.fillMaxSize(),
                onExpandNavigationRail = onExpandNavigationRail,
                onNavigateToSubPage = openSettingsDetail,
            )
            3 -> SettingsScreen(
                modifier = Modifier.fillMaxSize(),
                rootPage = SettingsPage.About,
                onExpandNavigationRail = onExpandNavigationRail,
                rootContentTopPadding = WindowInsets.statusBars
                    .asPaddingValues()
                    .calculateTopPadding() + 24.dp,
                rootContentBottomPadding = if (isLargeScreen) 24.dp else 100.dp,
                onNavigateToSubPage = openSettingsDetail,
            )
        }
    }

    val currentDetailTarget = when {
        settingsDetailStack.isNotEmpty() -> Md3eHomeDetailTarget.Settings(
            page = settingsDetailStack.last(),
            depth = settingsDetailStack.size,
        )
        detailOrder != null -> Md3eHomeDetailTarget.Order(
            order = detailOrder!!,
            depth = if (detailGroup != null) 2 else 1,
        )
        detailGroup != null -> Md3eHomeDetailTarget.Group(detailGroup!!)
        else -> null
    }
    val homeDetailContent: @Composable (Md3eHomeDetailTarget) -> Unit = { target ->
        Md3eHomeDetailContent(
            target = target,
            groupOrders = groupOrders,
            supportingPane = isLargeScreen,
            performHaptic = performHaptic,
            onSettingsBack = {
                performHaptic()
                settingsDetailStack = settingsDetailStack.dropLast(1)
            },
            onSettingsNavigate = { page ->
                settingsDetailStack = settingsDetailStack + page
            },
            onOrderBack = { detailOrder = null },
            onGroupBack = { detailGroup = null },
            onOpenGroupOrder = { order -> detailOrder = order },
            onMarkGroupCompleted = { group ->
                val completedAt = System.currentTimeMillis()
                groupOrders = groupOrders.map {
                    if (it.isCompleted) it else it.copy(isCompleted = true, completedAt = completedAt)
                }
                detailGroup = group.copy(
                    isCompleted = true,
                    completedAt = completedAt,
                    orderCount = groupOrders.size,
                )
                viewModel.markGroupAsCompleted(group.id)
            },
            onMarkOrderCompleted = { order ->
                groupOrders = groupOrders.map {
                    if (it.id == order.id) {
                        it.copy(isCompleted = true, completedAt = System.currentTimeMillis())
                    } else {
                        it
                    }
                }
                viewModel.markAsCompleted(order.id)
            },
        )
    }

    Box(modifier = modifier.fillMaxSize().background(homeBackgroundColor)) {
    Row(modifier = Modifier.fillMaxSize()) {
        if (isLargeScreen && !compactNavigationRail) {
            Md3eHomeNavigationRail(
                state = navigationRailState,
                selectedPage = selectedTopLevelPage,
                onNavigate = navigateToTopLevel,
                onToggle = performHaptic,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(homeBackgroundColor),
        ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = homeBackgroundColor,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isLargeScreen &&
                        !isUiHidden &&
                        !isFolded &&
                        !isImeVisible &&
                        !isScrollingDown,
                    enter = fadeIn(
                        animationSpec = motionScheme.defaultEffectsSpec<Float>(),
                    ) + slideInVertically(
                        animationSpec = motionScheme.defaultSpatialSpec<IntOffset>(),
                    ) { it },
                    exit = fadeOut(
                        animationSpec = motionScheme.defaultEffectsSpec<Float>(),
                    ) + slideOutVertically(
                        animationSpec = motionScheme.defaultSpatialSpec<IntOffset>(),
                    ) { it },
                ) {
                    AnimatedContent(
                        targetState = useFloatingNavBar,
                        transitionSpec = {
                            fadeIn(animationSpec = motionScheme.defaultEffectsSpec<Float>()) togetherWith
                                fadeOut(animationSpec = motionScheme.defaultEffectsSpec<Float>())
                        },
                        label = "md3eBottomBarStyle",
                    ) { floating ->
                        if (floating) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom))
                                    .padding(horizontal = 24.dp, vertical = 16.dp),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Md3eFloatingNavigationBar(
                                    selectedPage = selectedTopLevelPage,
                                    onNavigate = navigateToTopLevel,
                                )
                            }
                        } else {
                            Md3eStandardNavigationBar(
                                selectedPage = selectedTopLevelPage,
                                onNavigate = navigateToTopLevel,
                            )
                        }
                    }
                }
                },
        ) { _ ->
            Md3eSupportingPaneLayout(
                detailTarget = if (isLargeScreen) currentDetailTarget else null,
                detailContent = homeDetailContent,
            ) {
                if (isLargeScreen) {
                    AnimatedContent(
                        targetState = selectedTopLevelPage,
                        transitionSpec = {
                            fadeIn(animationSpec = motionScheme.defaultEffectsSpec<Float>()) togetherWith
                                fadeOut(animationSpec = motionScheme.defaultEffectsSpec<Float>())
                        },
                        label = "md3eTopLevelPage",
                    ) { page ->
                        pageContent(page)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds(),
                    ) {
                        val transition = directTopLevelTransition
                        val transitionProgress = directTopLevelTransitionProgress.value
                        val transitionDirection = if (
                            transition != null && transition.targetPage > transition.sourcePage
                        ) {
                            1f
                        } else {
                            -1f
                        }

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = if (transition == null) {
                                        0f
                                    } else {
                                        -transitionDirection * transitionProgress * size.width
                                    }
                                },
                            beyondViewportPageCount = 1,
                            userScrollEnabled = !isManaging && !isUiHidden && transition == null,
                        ) { page ->
                            pageContent(page)
                        }

                        if (transition != null) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .graphicsLayer {
                                        translationX = transitionDirection *
                                            (1f - transitionProgress) * size.width
                                    }
                                    .background(homeBackgroundColor),
                            ) {
                                pageContent(transition.targetPage)
                            }
                        }
                    }
                }
            }
        }

        // MD3E 快捷菜单使用标准 scrim，不调用 Miuix 模糊组件。
        val animatedScrimAlpha by animateFloatAsState(
            targetValue = if (showMenu) 0.32f else 0f,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>(),
            label = "rulesMenuScrim",
        )
        val animatedCardAlpha by animateFloatAsState(
            targetValue = if (showMenu) 1f else 0f,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>(),
            label = "rulesMenuAlpha",
        )
        val animatedCardScale by animateFloatAsState(
            targetValue = if (showMenu) 1f else 0.9f,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
            label = "rulesMenuScale",
        )
        if (animatedScrimAlpha > 0f) {
            val density = LocalDensity.current
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = animatedScrimAlpha))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showMenu = false }
                )
                val configuration = LocalConfiguration.current
                val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
                val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
                val cardMaxHeightPx = with(density) { 160.dp.toPx() }

                var cardWidthMeasured by remember { mutableIntStateOf(0) }
                val cardXDp = with(density) {
                    menuPosition.x.coerceIn(0f, screenWidthPx - cardWidthMeasured).toDp()
                }
                val cardYDp = with(density) {
                    val rawY = menuPosition.y
                    if (rawY + cardMaxHeightPx > screenHeightPx) {
                        // 下方空间不够，卡片显示在长按位置上方
                        (menuPosition.y - cardMaxHeightPx).coerceAtLeast(0f).toDp()
                    } else {
                        rawY.toDp()
                    }
                }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .offset(x = cardXDp, y = cardYDp)
                        .onGloballyPositioned { cardWidthMeasured = it.size.width }
                        .widthIn(max = 280.dp)
                        .graphicsLayer {
                            alpha = animatedCardAlpha
                            scaleX = animatedCardScale
                            scaleY = animatedCardScale
                        }
                ) {
                    val menuItems = buildList {
                        if (menuRename != null) add("rename")
                        if (menuExport != null) add("export")
                        if (menuDelete != null) add("delete")
                    }
                    Column {
                        menuItems.forEachIndexed { index, item ->
                            val isFirst = index == 0
                            val isLast = index == menuItems.lastIndex
                            val shape = when {
                                isFirst && isLast -> RoundedCornerShape(16.dp)
                                isFirst -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                isLast -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                                else -> RoundedCornerShape(0.dp)
                            }
                            Surface(
                                onClick = {
                                    showMenu = false
                                    when (item) {
                                        "rename" -> menuRename?.invoke()
                                        "export" -> menuExport?.invoke()
                                        "delete" -> menuDelete?.invoke()
                                    }
                                },
                                shape = shape,
                                color = Color.Transparent
                            ) {
                                val (icon, label, color) = when (item) {
                                    "rename" -> Triple(Icons.Default.Edit, "重命名", MaterialTheme.colorScheme.onSurface)
                                    "export" -> Triple(Icons.Default.FileUpload, "导出规则", MaterialTheme.colorScheme.onSurface)
                                    "delete" -> Triple(Icons.Default.Delete, "删除", MaterialTheme.colorScheme.error)
                                    else -> Triple(Icons.Default.Edit, "", Color.Unspecified)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(icon, null, tint = color)
                                    Spacer(Modifier.width(12.dp))
                                    Text(label, color = color)
                                }
                            }
                        }
                    }
                    }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = selectedTopLevelPage == 0 && !isUiHidden && !isScrollingDown,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(fabContentAlignment),
        ) {
            Box(
                modifier = Modifier
                    .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom))
                    .padding(horizontal = fabHorizontalPadding)
                    .padding(bottom = fabAboveBottomBarPadding)
            ) {
                Column(
                    horizontalAlignment = fabColumnAlignment,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExtendedFloatingActionButton(
                        onClick = { performHaptic(); showBottomSheet = true },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp, hoveredElevation = 0.dp, focusedElevation = 0.dp)
                    ) {
                        Icon(Icons.Default.Add, "添加", Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("添加")
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        tonalElevation = 8.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .width(104.dp)
                            .offset(x = identityContainerOffsetX)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = {
                                    performHaptic()
                                    openTaobaoIdentityEntry(context)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode,
                                        contentDescription = "淘宝身份码",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(end = 5.dp, bottom = 5.dp)
                                            .size(8.dp)
                                            .background(Color(0xFFFF8A00), RoundedCornerShape(50))
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    performHaptic()
                                    openPddIdentityEntry(context)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode,
                                        contentDescription = "拼多多身份码",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(end = 5.dp, bottom = 5.dp)
                                            .size(8.dp)
                                            .background(Color(0xFFE53935), RoundedCornerShape(50))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Md3eMobileDetailOverlay(
            visible = !isLargeScreen && currentDetailTarget != null,
            detailTarget = currentDetailTarget,
            scale = currentScale,
            translationX = currentTranslationX,
            cornerRadius = currentCornerRadius,
            borderAlpha = backProgress,
            showPredictiveBorder = isPredictiveBackInProgress,
            detailContent = homeDetailContent,
        )

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                // 内联定义bottomSheetContent内容，避免函数可见性问题
                var text by remember { mutableStateOf("") }
                var detectedQrData by remember { mutableStateOf<String?>(null) }
                var orderType by remember { mutableStateOf("餐食") }
                var brandName by remember { mutableStateOf<String?>(null) }
                var pickupLocation by remember { mutableStateOf<String?>(null) }
                var recognizedFullText by remember { mutableStateOf<String?>(null) }
                var imageSourceApp by remember { mutableStateOf<String?>(null) }
                var imageSourcePackage by remember { mutableStateOf<String?>(null) }
                var recognitionMetadata by remember { mutableStateOf<RecognitionExecutionMetadata?>(null) }
                var additionalRecognizedResults by remember { mutableStateOf(emptyList<RecognitionResult>()) }
                var expanded by remember { mutableStateOf(false) }
                val options = listOf("餐食", "饮品", "快递")
                val context = LocalContext.current
                val haptic = LocalHapticFeedback.current
                val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

                var hapticEnabled by remember { mutableStateOf(prefs.getBoolean("haptic_enabled", true)) }
                DisposableEffect(prefs) {
                    val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                        if (key == "haptic_enabled") hapticEnabled = p.getBoolean(key, true)
                    }
                    prefs.registerOnSharedPreferenceChangeListener(listener)
                    onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
                }

                val performHaptic = {
                    if (hapticEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }

                val coroutineScope = rememberCoroutineScope()
                var screenshotPath by remember { mutableStateOf<String?>(null) }

                val photoPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) {
                        coroutineScope.launch {
                            val imageSource = ImageSourceMetadataResolver.resolve(context, uri)
                            imageSourceApp = imageSource.appName ?: imageSource.packageName
                            imageSourcePackage = imageSource.packageName
                            recognizedFullText = null
                            recognitionMetadata = null
                            additionalRecognizedResults = emptyList()
                            screenshotPath = null
                            val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                            } else {
                                @Suppress("DEPRECATION")
                                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                            }

                            val routedResult = RecognitionRouter(context).recognizeImage(
                                originalBitmap,
                                imageSourceApp,
                                imageSourcePackage,
                                RecognitionTrigger.IMPORTED_IMAGE,
                            )
                            val successfulResults = routedResult.orders
                                .filter { it.code != null }
                                .distinctBy { it.code }
                            val result = successfulResults.firstOrNull() ?: routedResult.orders.firstOrNull()
                            additionalRecognizedResults = successfulResults.drop(1)
                            recognitionMetadata = routedResult.metadata

                            text = result?.code ?: ""
                            detectedQrData = result?.qr
                            recognizedFullText = result?.fullText?.takeIf { it.isNotBlank() }
                            result?.let {
                                orderType = it.type
                                brandName = it.brand
                                pickupLocation = it.pickupLocation
                            }

                            // 保存本次识别使用的图片。
                            if (result?.code != null) {
                                val savedScreenshotPath = ScreenshotStorage.saveBitmap(
                                    context,
                                    originalBitmap,
                                    namePrefix = "导入图片",
                                )
                                screenshotPath = savedScreenshotPath
                                val unrecognizedExplicitCodes = RecognitionCorrectionDetector.findUnrecognizedCodes(
                                    fullText = result.fullText,
                                    recognizedCodes = successfulResults.mapNotNull { it.code },
                                )
                                val partialDraftSaved = if (unrecognizedExplicitCodes.isNotEmpty()) {
                                    RecognitionCorrectionStore.saveImageDraft(
                                        context = context,
                                        bitmap = originalBitmap,
                                        result = result.copy(code = null, brand = null, pickupLocation = null),
                                        metadata = routedResult.metadata,
                                        recognizedText = "导入图片（部分待纠正）",
                                        sourceApp = imageSourceApp,
                                        sourcePackage = imageSourcePackage,
                                        screenshotPrefix = "导入待纠正",
                                        existingScreenshotPath = savedScreenshotPath,
                                    )
                                } else {
                                    false
                                }
                                when {
                                    partialDraftSaved -> Toast.makeText(
                                        context,
                                        "部分取件码未识别，已加入纠正识别",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    successfulResults.size > 1 -> Toast.makeText(
                                        context,
                                        "识别到 ${successfulResults.size} 个取件码，添加时将一并保存",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            } else if (result != null) {
                                val saved = RecognitionCorrectionStore.saveImageDraft(
                                    context = context,
                                    bitmap = originalBitmap,
                                    result = result,
                                    metadata = routedResult.metadata,
                                    recognizedText = "导入图片（待纠正）",
                                    sourceApp = imageSourceApp,
                                    sourcePackage = imageSourcePackage,
                                    screenshotPrefix = "导入待纠正",
                                )
                                if (saved) {
                                    Toast.makeText(context, "识别失败，已加入纠正识别", Toast.LENGTH_SHORT).show()
                                }
                            }

                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(horizontal = 24.dp).padding(bottom = 32.dp).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom)), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("添加记录", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("输入取餐码/取件码") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { performHaptic(); photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                    Icon(if (detectedQrData != null) Icons.Default.QrCodeScanner else Icons.Default.PhotoLibrary, contentDescription = "选择图片识别", tint = if (detectedQrData != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        )

                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                            OutlinedTextField(
                                value = orderType,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("类别") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                options.forEach { selectionOption ->
                                    DropdownMenuItem(text = { Text(selectionOption) }, onClick = { performHaptic(); orderType = selectionOption; expanded = false })
                                }
                            }
                        }

                        if (detectedQrData != null) {
                            Text(text = "已识别到二维码信息", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
                        }

                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { performHaptic(); showBottomSheet = false }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                                Text("取消")
                            }
                            Button(onClick = {
                                performHaptic()
                                val hasImportedImage = screenshotPath != null
                                val order = if (hasImportedImage && recognitionMetadata != null) {
                                    RecognizedOrderFactory.fromValues(
                                        takeoutCode = text,
                                        metadata = recognitionMetadata!!,
                                        qrCodeData = detectedQrData,
                                        screenshotPath = screenshotPath.orEmpty(),
                                        recognizedText = "图片识别",
                                        orderType = orderType,
                                        brandName = brandName,
                                        sourceApp = imageSourceApp ?: "图片识别",
                                        sourcePackage = imageSourcePackage,
                                        fullText = recognizedFullText,
                                        pickupLocation = pickupLocation,
                                    )
                                } else {
                                    RecognizedOrderFactory.manual(
                                        takeoutCode = text,
                                        qrCodeData = detectedQrData,
                                        orderType = orderType,
                                        brandName = brandName,
                                        fullText = recognizedFullText,
                                        pickupLocation = pickupLocation,
                                    )
                                }
                                viewModel.addOrder(order)
                                val metadata = recognitionMetadata
                                val sharedScreenshotPath = screenshotPath
                                if (metadata != null && sharedScreenshotPath != null) {
                                    additionalRecognizedResults
                                        .filterNot { it.code == text }
                                        .mapNotNull { result ->
                                            RecognizedOrderFactory.fromRecognition(
                                                result = result,
                                                metadata = metadata,
                                                screenshotPath = sharedScreenshotPath,
                                                recognizedText = "图片识别",
                                                sourceApp = imageSourceApp ?: "图片识别",
                                                sourcePackage = imageSourcePackage,
                                            )
                                        }
                                        .forEach(viewModel::addOrder)
                                }
                                showBottomSheet = false
                            }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                                Text("添加")
                            }
                        }
                    }
                }
            }
        }

        if (selectedOrderForQr != null) {
            QrCodeDialog(order = selectedOrderForQr!!, onDismiss = {
                selectedOrderForQr = null
                if (isFromNotification) { 
                    activity?.moveTaskToBack(true)
                    isFromNotification = false 
                }
            })
        }
    }
    }

    if (compactNavigationRail) {
        AnimatedVisibility(
            visible = navigationRailOverlayVisible,
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec<Float>()),
            exit = fadeOut(animationSpec = motionScheme.defaultEffectsSpec<Float>()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        coroutineScope.launch { navigationRailState.collapse() }
                    },
            )
        }
        Md3eHomeNavigationRail(
            state = navigationRailState,
            selectedPage = selectedTopLevelPage,
            onNavigate = navigateToTopLevel,
            onToggle = performHaptic,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = compactNavigationRailOffset)
                .fillMaxHeight(),
        )
    }
}
}

private const val MD3E_FIXED_NAVIGATION_RAIL_MIN_WIDTH_DP = 900
private val MD3E_COLLAPSED_NAVIGATION_RAIL_WIDTH = 96.dp

private data class Md3eDirectTopLevelTransition(
    val sourcePage: Int,
    val targetPage: Int,
)

private data class Md3eNavigationDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val md3eNavigationDestinations = listOf(
    Md3eNavigationDestination("主页", Icons.Filled.Home, Icons.Outlined.OutlinedHome),
    Md3eNavigationDestination("规则", Icons.Filled.Tune, Icons.Outlined.OutlinedTune),
    Md3eNavigationDestination("设置", Icons.Filled.Settings, Icons.Outlined.OutlinedSettings),
    Md3eNavigationDestination("关于", Icons.Filled.Info, Icons.Outlined.OutlinedInfo),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Md3eHomeNavigationRail(
    state: WideNavigationRailState,
    selectedPage: Int,
    onNavigate: (Int) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val motionScheme = MaterialTheme.motionScheme
    val expanded = state.targetValue == WideNavigationRailValue.Expanded
    val header: @Composable () -> Unit = {
        Box(
            modifier = Modifier.width(MD3E_COLLAPSED_NAVIGATION_RAIL_WIDTH),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                onClick = {
                    onToggle()
                    scope.launch { state.toggle() }
                },
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (expanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Default.Menu,
                        contentDescription = if (expanded) "收起侧边栏" else "展开侧边栏",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
    val content: @Composable () -> Unit = {
        md3eNavigationDestinations.forEachIndexed { index, destination ->
            val selected = selectedPage == index
            WideNavigationRailItem(
                selected = selected,
                onClick = { onNavigate(index) },
                icon = {
                    AnimatedContent(
                        targetState = selected,
                        transitionSpec = {
                            (fadeIn(
                                animationSpec = motionScheme.defaultEffectsSpec<Float>(),
                            ) + scaleIn(
                                animationSpec = motionScheme.defaultSpatialSpec<Float>(),
                                initialScale = 0.82f,
                            )) togetherWith (fadeOut(
                                animationSpec = motionScheme.defaultEffectsSpec<Float>(),
                            ) + scaleOut(
                                animationSpec = motionScheme.defaultSpatialSpec<Float>(),
                                targetScale = 0.82f,
                            ))
                        },
                        label = "md3eRailItemIcon",
                    ) { active ->
                        Icon(
                            imageVector = if (active) {
                                destination.selectedIcon
                            } else {
                                destination.unselectedIcon
                            },
                            contentDescription = destination.label,
                        )
                    }
                },
                label = { Text(destination.label) },
                railExpanded = expanded,
            )
        }
    }

    WideNavigationRail(
        modifier = modifier,
        state = state,
        header = header,
        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Md3eFloatingNavigationBar(
    selectedPage: Int,
    onNavigate: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier
            .widthIn(max = 344.dp)
            .fillMaxWidth()
            .height(76.dp),
        shape = RoundedCornerShape(38.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            md3eNavigationDestinations.forEachIndexed { index, destination ->
                Md3eFloatingNavigationItem(
                    destination = destination,
                    selected = selectedPage == index,
                    onClick = { onNavigate(index) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowScope.Md3eFloatingNavigationItem(
    destination: Md3eNavigationDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    val animatedWeight by animateFloatAsState(
        targetValue = if (selected) 1.14f else 1f,
        animationSpec = motionScheme.defaultSpatialSpec<Float>(),
        label = "md3eNavigationItemWeight",
    )
    val selectedProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motionScheme.defaultEffectsSpec<Float>(),
        label = "md3eNavigationItemSelection",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = motionScheme.defaultSpatialSpec<Float>(),
        label = "md3eNavigationItemIconScale",
    )
    val containerColor = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0f),
        MaterialTheme.colorScheme.secondaryContainer,
        selectedProgress,
    )
    val contentColor = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.onSecondaryContainer,
        selectedProgress,
    )

    Surface(
        modifier = Modifier
            .weight(animatedWeight)
            .fillMaxHeight(),
        shape = RoundedCornerShape(34.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.Tab,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(23.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        fadeIn(
                            animationSpec = motionScheme.defaultEffectsSpec<Float>(),
                        ) togetherWith fadeOut(
                            animationSpec = motionScheme.defaultEffectsSpec<Float>(),
                        )
                    },
                    label = "md3eNavigationItemIcon",
                ) { active ->
                    Icon(
                        imageVector = if (active) {
                            destination.selectedIcon
                        } else {
                            destination.unselectedIcon
                        },
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = destination.label,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Md3eStandardNavigationBar(
    selectedPage: Int,
    onNavigate: (Int) -> Unit,
) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        windowInsets = NavigationBarDefaults.windowInsets,
    ) {
        md3eNavigationDestinations.forEachIndexed { index, destination ->
            val selected = selectedPage == index
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(index) },
                icon = {
                    Icon(
                        imageVector = if (selected) {
                            destination.selectedIcon
                        } else {
                            destination.unselectedIcon
                        },
                        contentDescription = destination.label,
                    )
                },
                label = { Text(destination.label) },
                alwaysShowLabel = true,
            )
        }
    }
}

private sealed interface Md3eHomeDetailTarget {
    val depth: Int
    val contentKey: Any

    data class Settings(
        val page: SettingsPage,
        override val depth: Int,
    ) : Md3eHomeDetailTarget {
        override val contentKey: Any = "settings:$page:$depth"
    }

    data class Order(
        val order: OrderEntity,
        override val depth: Int = 1,
    ) : Md3eHomeDetailTarget {
        override val contentKey: Any = "order:${order.id}"
    }

    data class Group(val group: OrderGroup) : Md3eHomeDetailTarget {
        override val depth: Int = 1
        override val contentKey: Any = "group:${group.id}"
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Md3eSupportingPaneLayout(
    detailTarget: Md3eHomeDetailTarget?,
    detailContent: @Composable (Md3eHomeDetailTarget) -> Unit,
    mainContent: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val motionScheme = MaterialTheme.motionScheme
        val detailPaneWidth = (maxWidth * 0.42f).coerceIn(360.dp, 600.dp)
        var retainedTarget by remember { mutableStateOf(detailTarget) }
        if (detailTarget != null) {
            SideEffect { retainedTarget = detailTarget }
        }
        val renderedTarget = detailTarget ?: retainedTarget

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                mainContent()
            }
            AnimatedVisibility(
                visible = detailTarget != null,
                enter = expandHorizontally(
                    animationSpec = motionScheme.defaultSpatialSpec<IntSize>(),
                    expandFrom = Alignment.End,
                ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec<Float>()),
                exit = shrinkHorizontally(
                    animationSpec = motionScheme.defaultSpatialSpec<IntSize>(),
                    shrinkTowards = Alignment.End,
                ) + fadeOut(animationSpec = motionScheme.defaultEffectsSpec<Float>()),
            ) {
                Row(modifier = Modifier.fillMaxHeight()) {
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(
                        modifier = Modifier
                            .width(detailPaneWidth)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        renderedTarget?.let { target ->
                            Md3eAnimatedDetailContent(target, detailContent)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Md3eMobileDetailOverlay(
    visible: Boolean,
    detailTarget: Md3eHomeDetailTarget?,
    scale: Float,
    translationX: Float,
    cornerRadius: androidx.compose.ui.unit.Dp,
    borderAlpha: Float,
    showPredictiveBorder: Boolean,
    detailContent: @Composable (Md3eHomeDetailTarget) -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    var retainedTarget by remember { mutableStateOf(detailTarget) }
    if (detailTarget != null) {
        SideEffect { retainedTarget = detailTarget }
    }
    val renderedTarget = detailTarget ?: retainedTarget

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            animationSpec = motionScheme.defaultSpatialSpec<IntOffset>(),
            initialOffsetX = { it },
        ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec<Float>()),
        exit = slideOutHorizontally(
            animationSpec = motionScheme.defaultSpatialSpec<IntOffset>(),
            targetOffsetX = { it },
        ) + fadeOut(animationSpec = motionScheme.defaultEffectsSpec<Float>()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.translationX = translationX
                    shape = RoundedCornerShape(cornerRadius)
                    clip = true
                }
                .border(
                    width = if (showPredictiveBorder) 1.dp else 0.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = borderAlpha),
                    shape = RoundedCornerShape(cornerRadius),
                )
                .background(MaterialTheme.colorScheme.background),
        ) {
            renderedTarget?.let { target ->
                Md3eAnimatedDetailContent(target, detailContent)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Md3eAnimatedDetailContent(
    target: Md3eHomeDetailTarget,
    detailContent: @Composable (Md3eHomeDetailTarget) -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = target,
        contentKey = { it.contentKey },
        transitionSpec = {
            val spatialSpec = motionScheme.defaultSpatialSpec<IntOffset>()
            val effectsSpec = motionScheme.defaultEffectsSpec<Float>()
            when {
                targetState.depth > initialState.depth -> {
                    (slideInHorizontally(spatialSpec) { it } + fadeIn(effectsSpec)) togetherWith
                        (slideOutHorizontally(spatialSpec) { -it / 4 } + fadeOut(effectsSpec))
                }
                targetState.depth < initialState.depth -> {
                    (slideInHorizontally(spatialSpec) { -it / 4 } + fadeIn(effectsSpec)) togetherWith
                        (slideOutHorizontally(spatialSpec) { it } + fadeOut(effectsSpec))
                }
                else -> fadeIn(effectsSpec) togetherWith fadeOut(effectsSpec)
            }
        },
        label = "md3eDetailNavigation",
    ) { renderedTarget ->
        detailContent(renderedTarget)
    }
}

@Composable
private fun Md3eHomeDetailContent(
    target: Md3eHomeDetailTarget,
    groupOrders: List<OrderEntity>,
    supportingPane: Boolean,
    performHaptic: () -> Unit,
    onSettingsBack: () -> Unit,
    onSettingsNavigate: (SettingsPage) -> Unit,
    onOrderBack: () -> Unit,
    onGroupBack: () -> Unit,
    onOpenGroupOrder: (OrderEntity) -> Unit,
    onMarkGroupCompleted: (OrderGroup) -> Unit,
    onMarkOrderCompleted: (OrderEntity) -> Unit,
) {
    when (target) {
        is Md3eHomeDetailTarget.Settings -> SubPage(
            title = target.page.md3eTitle(),
            page = target.page,
            performHaptic = performHaptic,
            onNavigate = onSettingsNavigate,
            onBack = onSettingsBack,
            isMiuix = false,
            supportingPane = supportingPane,
        )
        is Md3eHomeDetailTarget.Order -> OrderDetailScreen(
            order = target.order,
            onBack = onOrderBack,
            supportingPane = supportingPane,
        )
        is Md3eHomeDetailTarget.Group -> GroupDetailScreen(
            group = target.group,
            orders = groupOrders,
            onBack = onGroupBack,
            onMarkAllCompleted = { onMarkGroupCompleted(target.group) },
            onMarkOrderCompleted = onMarkOrderCompleted,
            onOpenOrder = onOpenGroupOrder,
            supportingPane = supportingPane,
        )
    }
}

private fun SettingsPage.md3eTitle(): String = when (this) {
    SettingsPage.Main -> "设置"
    SettingsPage.Preference -> "偏好设置"
    SettingsPage.Permission -> "权限与保活"
    SettingsPage.Screenshot -> "截图方式"
    SettingsPage.Recognition -> "识别方式"
    SettingsPage.CustomPrompt -> "自定义 Prompt"
    SettingsPage.RecognitionCorrection -> "纠正识别"
    SettingsPage.KeepAlive -> "保活设置"
    SettingsPage.WearableSync -> "手表同步"
    SettingsPage.Storage -> "清理空间"
    SettingsPage.About -> "关于"
    SettingsPage.Backup -> "备份与恢复"
    SettingsPage.Sponsor -> "赞助"
    SettingsPage.NotificationApps -> "通知识别应用管理"
    SettingsPage.Credits -> "致谢"
    SettingsPage.Developer -> "开发者选项"
}
