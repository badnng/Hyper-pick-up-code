package com.Badnng.moe.ui.screen

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Badnng.moe.activity.MainActivity
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.data.db.OrderGroup
import com.Badnng.moe.ocr.TextRecognitionHelper
import com.Badnng.moe.viewmodel.OrderViewModel
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.lens
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop as rememberMiuixBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop as miuixLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.launch
import com.Badnng.moe.ui.screen.settings.SettingsScreen
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * 辅助函数：支持 rememberSaveable 的 PagerState
 * 确保折叠屏展开/折叠时页面状态不丢失
 */
@Composable
fun rememberSaveablePagerState(pageCount: () -> Int): PagerState {
    val currentPage = rememberSaveable { mutableIntStateOf(0) }
    val pagerState = remember(pageCount) {
        PagerState(currentPage = currentPage.value, pageCount = pageCount)
    }

    // 首次加载时从保存的状态恢复页面位置
    LaunchedEffect(Unit) {
        if (pagerState.currentPage != currentPage.value) {
            pagerState.scrollToPage(currentPage.value, 0f)
        }
    }

    // 同步 pagerState 的当前页回到 rememberSaveable
    LaunchedEffect(pagerState.currentPage) {
        if (currentPage.value != pagerState.currentPage) {
            currentPage.intValue = pagerState.currentPage
        }
    }

    return pagerState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    intentToProcess: Intent? = null
) {
    val isMiuix = com.Badnng.moe.ui.miuix.rememberMiuixStyle()
    val pagerState = rememberSaveablePagerState(pageCount = { 3 })

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
    val orders by viewModel.orders.collectAsState()
    val orderGroups by viewModel.orderGroups.collectAsState()
    var selectedOrderForQr by remember { mutableStateOf<OrderEntity?>(null) }
    var detailOrder by remember { mutableStateOf<OrderEntity?>(null) }
    var detailGroup by remember { mutableStateOf<OrderGroup?>(null) }
    var previousDetailOrder by remember { mutableStateOf<OrderEntity?>(null) }
    var previousDetailGroup by remember { mutableStateOf<OrderGroup?>(null) }
    var isFromNotification by rememberSaveable { mutableStateOf(false) }
    var isManaging by rememberSaveable { mutableStateOf(false) }
    var groupOrders by remember { mutableStateOf<List<OrderEntity>>(emptyList()) }

    var backProgress by remember { mutableFloatStateOf(0f) }
    var backSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var isPredictiveBackInProgress by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    var navAlignment by remember { mutableStateOf(prefs.getString("nav_alignment", "center") ?: "center") }
    var largeScreenNavAdaptiveEnabled by remember {
        mutableStateOf(prefs.getBoolean("large_screen_nav_adaptive_enabled", true))
    }
    var dynamicNavAlignment by remember { mutableStateOf<String?>(null) }
    var dynamicFabSide by remember {
        mutableStateOf(
            if ((prefs.getString("nav_alignment", "center") ?: "center") == "left") "left" else "right"
        )
    }
    // 关键修复：hapticEnabled 现在是实时响应的状态
    var hapticEnabled by remember { mutableStateOf(prefs.getBoolean("haptic_enabled", true)) }
    var predictiveBackEnabled by remember {
        mutableStateOf(prefs.getBoolean("predictive_back_enabled", true))
    }
    var amoledPureBlack by remember { mutableStateOf(prefs.getBoolean("amoled_pure_black", false)) }
    val configuration = LocalConfiguration.current
    val isLargeScreen = configuration.screenWidthDp >= 700

    // 折叠屏开合检测
    val windowInfoTracker = remember(context) { WindowInfoTracker.getOrCreate(context) }
    val layoutInfo by windowInfoTracker.windowLayoutInfo(context).collectAsState(initial = null)
    val foldingFeature = layoutInfo?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull()
    val isFolded = foldingFeature?.state == FoldingFeature.State.HALF_OPENED
    val imeBottomPadding = WindowInsets.ime.getBottom(LocalDensity.current)
    val isImeVisible = imeBottomPadding > 0
    val navAdaptiveActive = isLargeScreen && largeScreenNavAdaptiveEnabled
    val effectiveNavAlignment = if (navAdaptiveActive) (dynamicNavAlignment ?: navAlignment) else navAlignment
    var allowPagerHorizontalSwipe by remember { mutableStateOf(true) }
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
    val effectiveFabSide = if (navAdaptiveActive) {
        if (effectiveNavAlignment == "center") "right" else dynamicFabSide
    } else {
        effectiveNavAlignment
    }
    val targetFabBias = when (effectiveFabSide) {
        "left" -> -1f
        else -> 1f
    }
    val animatedFabBias by animateFloatAsState(
        targetValue = targetFabBias,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 260f),
        label = "fabBias"
    )

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                "nav_alignment" -> navAlignment = p.getString(key, "center") ?: "center"
                "haptic_enabled" -> hapticEnabled = p.getBoolean(key, true)
                "predictive_back_enabled" -> predictiveBackEnabled = p.getBoolean(key, true)
                "amoled_pure_black" -> amoledPureBlack = p.getBoolean(key, false)
                "large_screen_nav_adaptive_enabled" -> largeScreenNavAdaptiveEnabled =
                    p.getBoolean(key, true)
            }
            if (key == "nav_alignment") {
                val updated = p.getString(key, "center") ?: "center"
                if (updated == "left" || updated == "right") {
                    dynamicFabSide = updated
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LaunchedEffect(navAdaptiveActive, navAlignment) {
        if (!navAdaptiveActive) {
            dynamicNavAlignment = null
            if (navAlignment == "left" || navAlignment == "right") {
                dynamicFabSide = navAlignment
            } else {
                dynamicFabSide = "right"
            }
        }
    }

    val performHaptic = {
        if (hapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    val bottomBarWidth = 275.dp
    val fontScale = LocalDensity.current.fontScale
    val largeFont = fontScale >= 1.2f
    val bottomBarHeight = if (largeFont) 72.dp else 64.dp
    val fabAboveBottomBarPadding = bottomBarHeight + 70.dp
    val fabHorizontalPadding = when (effectiveNavAlignment) {
        "left", "right", "center" -> 24.dp
        else -> 24.dp
    }
    val fabColumnAlignment = if (effectiveNavAlignment == "left") Alignment.Start else Alignment.End
    val identityContainerOffsetX = 0.dp
    val fabContentAlignment = when (effectiveNavAlignment) {
        "left" -> Alignment.BottomStart
        else -> Alignment.BottomEnd
    }

    LaunchedEffect(detailOrder) {
        detailOrder?.let {
            previousDetailOrder = it
        }
    }

    LaunchedEffect(detailGroup) {
        detailGroup?.let {
            previousDetailGroup = it
        }
    }

    LaunchedEffect(detailGroup) {
        detailGroup?.let { group ->
            viewModel.getOrdersByGroupId(group.id).collect { orders ->
                groupOrders = orders
            }
        }
    }


    PredictiveBackHandler(
        enabled = predictiveBackEnabled && (detailOrder != null || detailGroup != null)
    ) { backEvent: Flow<androidx.activity.BackEventCompat> ->
        isPredictiveBackInProgress = true
        try {
            backEvent.collect { event ->
                backProgress = event.progress
                backSwipeEdge = event.swipeEdge
            }
            if (detailGroup != null) {
                detailGroup = null
            } else {
                detailOrder = null
            }
        } catch (e: CancellationException) {
            if (previousDetailGroup != null && detailGroup == null) {
                detailGroup = previousDetailGroup
            } else {
                detailOrder = previousDetailOrder
            }
        } finally {
            isPredictiveBackInProgress = false
            backProgress = 0f
        }
    }

    BackHandler(
        enabled = !predictiveBackEnabled && (detailOrder != null || detailGroup != null)
    ) {
        if (detailGroup != null) {
            detailGroup = null
        } else {
            detailOrder = null
        }
    }

    val activity = context as? MainActivity

    // 主页面按返回键时，从最近任务移除卡片
    BackHandler(enabled = detailOrder == null && detailGroup == null) {
        activity?.finishAndRemoveTask()
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
            coroutineScope.launch { pagerState.animateScrollToPage(0) }
        }
    }

    LaunchedEffect(intentToProcess, orderGroups) {
        if (intentToProcess?.getBooleanExtra("show_group_detail", false) == true) {
            detailOrder = null
            detailGroup = null
            coroutineScope.launch { pagerState.animateScrollToPage(0) }
        }
    }

    // 从更新下载通知进入时，跳转到设置页
    LaunchedEffect(intentToProcess) {
        if (intentToProcess?.getBooleanExtra("show_update_download", false) == true) {
            coroutineScope.launch { pagerState.animateScrollToPage(2) }
            activity?.intentToProcess = null
        }
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val isDarkPalette = backgroundColor.luminance() < 0.5f
    val usePureBlackHomeBackground = amoledPureBlack && isDarkPalette
    val homeBackgroundColor = if (usePureBlackHomeBackground) Color.Black else backgroundColor

    val miuixBackdrop = rememberMiuixBackdrop()

    var isSettingsSubPageOpen by remember { mutableStateOf(false) }
    var isScrollingDown by remember { mutableStateOf(false) }
    val isUiHidden = isSettingsSubPageOpen || isManaging

    // 切换页面时重置滚动状态，确保底栏和FAB正确显示
    LaunchedEffect(pagerState.currentPage) {
        isScrollingDown = false
    }

    // 全屏菜单状态
    var showMenu by remember { mutableStateOf(false) }
    var menuPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var menuRename by remember { mutableStateOf<(() -> Unit)?>(null) }
    var menuDelete by remember { mutableStateOf<(() -> Unit)?>(null) }
    var menuExport by remember { mutableStateOf<(() -> Unit)?>(null) }

    Box(modifier = modifier.fillMaxSize().background(homeBackgroundColor)) {
        // 内层：miuixLayerBackdrop 只捕获 Scaffold 内容（不含菜单叠加层）
        Box(modifier = Modifier.fillMaxSize().background(homeBackgroundColor).miuixLayerBackdrop(miuixBackdrop)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = homeBackgroundColor,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                AnimatedVisibility(
                    visible = !isUiHidden && !isFolded && !isImeVisible && !isScrollingDown,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    // MD3E 模式：悬浮药丸底栏 + Kyant Backdrop
                    val alignment = BiasAlignment(animatedBottomBarBias, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom))
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        contentAlignment = alignment
                    ) {
                        Box(
                            modifier = Modifier
                                .width(bottomBarWidth)
                                .height(bottomBarHeight)
                                .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f)), RoundedCornerShape(20.dp))
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { RoundedCornerShape(20.dp) },
                                    effects = { vibrancy(); blur(16.dp.toPx()); lens(20.dp.toPx(), 40.dp.toPx()) },
                                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.1f)) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            NavigationBar(containerColor = Color.Transparent, modifier = Modifier.fillMaxSize(), windowInsets = WindowInsets(0, 0, 0, 0)) {
                                NavigationBarItem(
                                    icon = { val s by animateDpAsState(if (pagerState.currentPage == 0) 28.dp else 24.dp); Icon(Icons.Default.Home, null, Modifier.size(s)) },
                                    label = { Text("主页", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    selected = pagerState.currentPage == 0,
                                    alwaysShowLabel = true,
                                    onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer, selectedIconColor = MaterialTheme.colorScheme.primary, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                )
                                NavigationBarItem(
                                    icon = { val s by animateDpAsState(if (pagerState.currentPage == 1) 28.dp else 24.dp); Icon(Icons.Default.Tune, null, Modifier.size(s)) },
                                    label = { Text("规则", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    selected = pagerState.currentPage == 1,
                                    alwaysShowLabel = true,
                                    onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                                    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer, selectedIconColor = MaterialTheme.colorScheme.primary, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                )
                                NavigationBarItem(
                                    icon = { val s by animateDpAsState(if (pagerState.currentPage == 2) 28.dp else 24.dp); Icon(Icons.Default.Settings, null, Modifier.size(s)) },
                                    label = { Text("设置", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    selected = pagerState.currentPage == 2,
                                    alwaysShowLabel = true,
                                    onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                                    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer, selectedIconColor = MaterialTheme.colorScheme.primary, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                )
                            }
                        }
                    }
                }
                },
            floatingActionButton = {
                {}
            }
        ) { _ ->
            Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isLargeScreen, navAdaptiveActive, pagerState.currentPage, isUiHidden) {
                            var gestureActive = false
                            var downX = 0f
                            var downY = 0f
                            var downZone = "center"
                            // 0=未判定, 1=纵向, -1=横向
                            var gestureDirection = 0
                            var directionLocked = false
                            val directionThresholdPx = 14f
                            val axisRatio = 1.2f
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (pagerState.currentPage != 0 || isUiHidden) {
                                        if (!allowPagerHorizontalSwipe) allowPagerHorizontalSwipe = true
                                        gestureActive = false
                                        gestureDirection = 0
                                        directionLocked = false
                                        continue
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

                                    if (change.changedToDownIgnoreConsumed()) {
                                        gestureActive = true
                                        gestureDirection = 0
                                        directionLocked = false
                                        // 手势开始保持可切页，避免“先纵滑后立刻横滑”出现延迟体感
                                        if (!allowPagerHorizontalSwipe) allowPagerHorizontalSwipe = true
                                        downX = x
                                        downY = y
                                        downZone = zoneAtX
                                    }

                                    if (gestureActive && change.pressed && !directionLocked) {
                                        val dx = x - downX
                                        val dy = y - downY
                                        val absDx = abs(dx)
                                        val absDy = abs(dy)
                                        if (absDx >= directionThresholdPx || absDy >= directionThresholdPx) {
                                            val verticalDominant = absDy > absDx * axisRatio
                                            val horizontalDominant = absDx > absDy * axisRatio
                                            if (!verticalDominant && !horizontalDominant) {
                                                // 比值未明显偏向，继续等待更多位移再判定
                                                continue
                                            }
                                            gestureDirection = if (verticalDominant) 1 else -1
                                            directionLocked = true
                                            // 仅纵向手势触发底栏切换，并锁定为按下时区域，避免长按后横移误触发
                                            if (gestureDirection == 1 && navAdaptiveActive) {
                                                // 方向锁定为纵向后，横向切页彻底关闭，直到抬手重置
                                                if (allowPagerHorizontalSwipe) allowPagerHorizontalSwipe = false
                                                if (dynamicNavAlignment != downZone) dynamicNavAlignment = downZone
                                                if (downZone == "left" || downZone == "right") {
                                                    if (dynamicFabSide != downZone) dynamicFabSide = downZone
                                                } else if (dynamicFabSide != "right") {
                                                    dynamicFabSide = "right"
                                                }
                                            } else if (gestureDirection == -1) {
                                                // 方向锁定为横向后，仅保留切页响应
                                                if (!allowPagerHorizontalSwipe) allowPagerHorizontalSwipe = true
                                            }
                                        }
                                    }

                                    if (change.changedToUpIgnoreConsumed() || event.changes.none { it.pressed }) {
                                        gestureActive = false
                                        gestureDirection = 0
                                        directionLocked = false
                                        if (!allowPagerHorizontalSwipe) allowPagerHorizontalSwipe = true
                                    }
                                }
                            }
                        },
                    beyondViewportPageCount = 1,
                    userScrollEnabled = !isManaging &&
                        !isSettingsSubPageOpen &&
                        detailOrder == null &&
                        detailGroup == null &&
                        allowPagerHorizontalSwipe
                ) { page ->
                    when (page) {
                        0 -> CaptureScreen(modifier = Modifier.fillMaxSize(), bottomPadding = 100.dp, backdrop = backdrop, onEditModeChange = { isManaging = it }, onScrollStateChange = { isScrollingDown = it }, onNavigateToDetail = { detailItem ->
                            when (detailItem) {
                                is OrderEntity -> detailOrder = detailItem
                                is OrderGroup -> detailGroup = detailItem
                            }
                        })
                        1 -> RulesScreen(
                            modifier = Modifier.fillMaxSize(),
                            onShowMenu = { position, rename, delete, export ->
                                menuPosition = position
                                menuRename = rename
                                menuDelete = delete
                                menuExport = export
                                showMenu = true
                            },
                            onDismissMenu = { showMenu = false }
                        )
                        2 -> SettingsScreen(modifier = Modifier.fillMaxSize(), onSubPageStatusChange = { isSettingsSubPageOpen = it })
                    }
                }
            }
        }
        } // 关闭 miuixLayerBackdrop 内层 Box

        // 全屏毛玻璃快捷菜单（覆盖底栏）
        val animatedBlurRadius by animateFloatAsState(
            targetValue = if (showMenu) 60f else 0f,
            animationSpec = tween(durationMillis = 300)
        )
        val animatedCardAlpha by animateFloatAsState(
            targetValue = if (showMenu) 1f else 0f,
            animationSpec = tween(durationMillis = 250, delayMillis = 50)
        )
        val animatedCardScale by animateFloatAsState(
            targetValue = if (showMenu) 1f else 0.9f,
            animationSpec = tween(durationMillis = 250, delayMillis = 50)
        )
        if (animatedBlurRadius > 0f) {
            val density = LocalDensity.current
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .textureBlur(
                            backdrop = miuixBackdrop,
                            shape = RoundedCornerShape(0.dp),
                            blurRadius = animatedBlurRadius,
                            colors = top.yukonga.miuix.kmp.blur.BlurColors(brightness = -0.15f)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showMenu = false }
                )
                val configuration = LocalConfiguration.current
                val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
                val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
                val cardWidthPx = 280f
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

        AnimatedVisibility(
            visible = pagerState.currentPage == 0 && !isUiHidden && !isScrollingDown,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = if (isLargeScreen) {
                Modifier.align(BiasAlignment(animatedFabBias, 1f))
            } else {
                Modifier.align(fabContentAlignment)
            }
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

        AnimatedVisibility(
            visible = detailOrder != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut()
        ) {
            val displayOrder = detailOrder ?: previousDetailOrder
            if (displayOrder != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize() // 完全全屏，不限制宽度
                            .graphicsLayer {
                                scaleX = currentScale; scaleY = currentScale;
                                translationX = currentTranslationX;
                                shape = RoundedCornerShape(currentCornerRadius);
                                clip = true
                            }
                            .border(
                                width = if (isPredictiveBackInProgress) 1.dp else 0.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = backProgress),
                                shape = RoundedCornerShape(currentCornerRadius)
                            )
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        OrderDetailScreen(order = displayOrder, onBack = { detailOrder = null })
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = detailGroup != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut()
        ) {
            val displayGroup = detailGroup ?: previousDetailGroup
            if (displayGroup != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize() // 完全全屏，不限制宽度
                            .graphicsLayer {
                                scaleX = currentScale; scaleY = currentScale;
                                translationX = currentTranslationX;
                                shape = RoundedCornerShape(currentCornerRadius);
                                clip = true
                            }
                            .border(
                                width = if (isPredictiveBackInProgress) 1.dp else 0.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = backProgress),
                                shape = RoundedCornerShape(currentCornerRadius)
                            )
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                    GroupDetailScreen(
                        group = displayGroup,
                        orders = groupOrders,
                        onBack = { detailGroup = null },
                        onMarkAllCompleted = {
                            val completedAt = System.currentTimeMillis()
                            groupOrders = groupOrders.map {
                                if (it.isCompleted) it else it.copy(isCompleted = true, completedAt = completedAt)
                            }
                            detailGroup = displayGroup.copy(
                                isCompleted = true,
                                completedAt = completedAt,
                                orderCount = groupOrders.size
                            )
                            previousDetailGroup = detailGroup
                            viewModel.markGroupAsCompleted(displayGroup.id)
                        },
                        onMarkOrderCompleted = { order ->
                            groupOrders = groupOrders.map {
                                if (it.id == order.id) it.copy(isCompleted = true, completedAt = System.currentTimeMillis()) else it
                            }
                            viewModel.markAsCompleted(order.id)
                        }
                    )
                }
            }
        }
        }

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

                fun cropStatusBar(src: Bitmap): Bitmap {
                    val statusBarHeight = 150
                    val sideMargin = (src.width * 0.02).toInt()
                    val targetWidth = (src.width * 0.96).toInt()
                    val targetHeight = (src.height * 0.81).toInt()
                    return if (src.height > statusBarHeight + targetHeight && src.width > sideMargin + targetWidth) {
                        Bitmap.createBitmap(src, sideMargin, statusBarHeight, targetWidth, targetHeight)
                    } else {
                        src
                    }
                }

                val photoPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) {
                        coroutineScope.launch {
                            val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                            } else {
                                @Suppress("DEPRECATION")
                                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                            }

                            val bitmap = cropStatusBar(originalBitmap)

                            val helper = TextRecognitionHelper(context)
                            helper.initOcr() // 初始化 PaddleOCR
                            val recognizeResult = helper.recognizeAll(bitmap)
                            val result = recognizeResult.first

                            text = result.code ?: ""
                            detectedQrData = result.qr
                            orderType = result.type
                            brandName = result.brand
                            pickupLocation = result.pickupLocation

                            // 保存裁剪后的图片
                            if (result.code != null) {
                                val screenshotFile = java.io.File(context.filesDir, "screenshots/manual_${System.currentTimeMillis()}.png")
                                screenshotFile.parentFile?.mkdirs()
                                val outputStream = java.io.FileOutputStream(screenshotFile)
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                                outputStream.close()
                                screenshotPath = screenshotFile.absolutePath
                            }

                            helper.close()
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
                                viewModel.addOrder(OrderEntity(
                                    takeoutCode = text,
                                    qrCodeData = detectedQrData,
                                    screenshotPath = screenshotPath ?: "",
                                    recognizedText = "手动输入",
                                    orderType = orderType,
                                    brandName = brandName,
                                    pickupLocation = pickupLocation
                                ))
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
