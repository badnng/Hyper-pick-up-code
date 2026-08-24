package com.Badnng.moe.ui.screen.miuix

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Sidebar
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.data.db.OrderGroup
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.ui.LocalAppUi
import com.Badnng.moe.ui.miuix.MIUIX_FLOATING_NAV_BAR_STYLE_KEY
import com.Badnng.moe.ui.miuix.MiuixFloatingNavigationBarStyle
import com.Badnng.moe.ui.miuix.miuixScrollModifiers
import com.Badnng.moe.ui.miuix.liquid.IosLiquidGlassNavigationBar
import com.Badnng.moe.ui.screen.rememberSaveablePagerState
import com.Badnng.moe.ui.screen.settings.AboutSettingsContent
import com.Badnng.moe.ui.screen.settings.SettingsPage
import com.Badnng.moe.rules.SimpleRuleCategory
import com.Badnng.moe.ui.component.SimpleRuleCenterPage
import com.Badnng.moe.viewmodel.OrderViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
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
import top.yukonga.miuix.kmp.basic.NavigationRailState
import top.yukonga.miuix.kmp.basic.NavigationRailValue
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults

// 顶层路由
@Serializable
enum class RuleSubPageKind {
    Category,
    CreateBrand,
    Brand,
    CreateTemplate,
    Template,
    BlockedWords,
    CustomIcons,
}

@Serializable
sealed interface HomeRoute : NavKey {
    @Serializable
    data object Main : HomeRoute

    @Serializable
    data class SettingsSubPage(val page: SettingsPage) : HomeRoute

    @Serializable
    data class RuleSubPage(
        val kind: RuleSubPageKind,
        val category: String = "",
        val brandId: String = "",
        val templateId: String = "",
    ) : HomeRoute

    @Serializable
    data class RecognitionCorrectionEditor(val orderId: String) : HomeRoute

    @Serializable
    data class OrderDetail(val orderId: String) : HomeRoute

    @Serializable
    data class GroupDetail(val groupId: Long) : HomeRoute
}

private fun SimpleRuleCenterPage.toHomeRoute(): HomeRoute.RuleSubPage = when (this) {
    SimpleRuleCenterPage.Root -> error("规则主页不能作为二级路由")
    is SimpleRuleCenterPage.Category -> HomeRoute.RuleSubPage(
        kind = RuleSubPageKind.Category,
        category = category.name,
    )
    is SimpleRuleCenterPage.CreateBrand -> HomeRoute.RuleSubPage(
        kind = RuleSubPageKind.CreateBrand,
        category = category.name,
    )
    is SimpleRuleCenterPage.Brand -> HomeRoute.RuleSubPage(
        kind = RuleSubPageKind.Brand,
        brandId = brandId,
    )
    is SimpleRuleCenterPage.CreateTemplate -> HomeRoute.RuleSubPage(
        kind = RuleSubPageKind.CreateTemplate,
        brandId = brandId,
    )
    is SimpleRuleCenterPage.Template -> HomeRoute.RuleSubPage(
        kind = RuleSubPageKind.Template,
        brandId = brandId,
        templateId = templateId,
    )
    SimpleRuleCenterPage.BlockedWords -> HomeRoute.RuleSubPage(kind = RuleSubPageKind.BlockedWords)
    SimpleRuleCenterPage.CustomIcons -> HomeRoute.RuleSubPage(kind = RuleSubPageKind.CustomIcons)
}

private fun HomeRoute.RuleSubPage.toRulePage(): SimpleRuleCenterPage = when (kind) {
    RuleSubPageKind.Category -> SimpleRuleCenterPage.Category(SimpleRuleCategory.valueOf(category))
    RuleSubPageKind.CreateBrand -> SimpleRuleCenterPage.CreateBrand(SimpleRuleCategory.valueOf(category))
    RuleSubPageKind.Brand -> SimpleRuleCenterPage.Brand(brandId)
    RuleSubPageKind.CreateTemplate -> SimpleRuleCenterPage.CreateTemplate(brandId)
    RuleSubPageKind.Template -> SimpleRuleCenterPage.Template(brandId, templateId)
    RuleSubPageKind.BlockedWords -> SimpleRuleCenterPage.BlockedWords
    RuleSubPageKind.CustomIcons -> SimpleRuleCenterPage.CustomIcons
}

@Composable
fun MiuixHomeScreen(
    modifier: Modifier = Modifier,
    intentToProcess: Intent? = null,
    pagerState: androidx.compose.foundation.pager.PagerState? = null
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val configuration = LocalConfiguration.current
    val isLargeScreenWindow = configuration.screenWidthDp >= MIUIX_LARGE_SCREEN_MIN_WIDTH_DP
    val windowInfoTracker = remember(context) { WindowInfoTracker.getOrCreate(context) }
    val windowLayoutInfo = remember(windowInfoTracker, context) {
        windowInfoTracker.windowLayoutInfo(context)
    }
    val layoutInfo by windowLayoutInfo
        .collectAsStateWithLifecycle(initialValue = null)
    val foldingFeature = layoutInfo?.displayFeatures
        ?.filterIsInstance<FoldingFeature>()
        ?.firstOrNull()
    val isFolded = foldingFeature?.state == FoldingFeature.State.HALF_OPENED
    var hapticEnabled by remember { mutableStateOf(prefs.getBoolean("haptic_enabled", true)) }
    // 大屏切换会改变 NavigationRail/NavDisplay 场景，只在下次主页加载时生效；
    // 手机端仅切换底栏样式，可以直接响应偏好变化。
    var useFloatingNavBar by remember(prefs) {
        mutableStateOf(prefs.getBoolean("use_floating_nav_bar", false))
    }
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

    LaunchedEffect(isLargeScreenWindow) {
        if (!isLargeScreenWindow) {
            useFloatingNavBar = prefs.getBoolean("use_floating_nav_bar", false)
        }
    }

    DisposableEffect(prefs, isLargeScreenWindow) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                "haptic_enabled" -> hapticEnabled = p.getBoolean(key, true)
                "use_floating_nav_bar" -> {
                    if (!isLargeScreenWindow) {
                        useFloatingNavBar = p.getBoolean(key, false)
                    }
                }
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

    val supportsNavigationRail =
        isLargeScreenWindow && !useFloatingNavBar
    val compactNavigationRail = supportsNavigationRail &&
        configuration.screenWidthDp < MIUIX_FIXED_NAVIGATION_RAIL_MIN_WIDTH_DP
    // miuix-nav 0.9.4-rc01 不再暴露 Navigation3 的 SceneStrategy 扩展点；
    // 二级页统一使用完整页面呈现，避免把旧 SceneStrategy API 残留到新导航运行时。
    val supportsSupportingPane = false
    val navigationRailState = rememberNavigationRailState(
        initialValue = if (compactNavigationRail) {
            NavigationRailValue.Collapsed
        } else {
            NavigationRailValue.Expanded
        },
    )
    LaunchedEffect(supportsNavigationRail, compactNavigationRail) {
        when {
            !supportsNavigationRail -> navigationRailState.collapse()
            compactNavigationRail -> navigationRailState.collapse()
            else -> navigationRailState.expand()
        }
    }

    val backStack = rememberNavBackStack<HomeRoute>(HomeRoute.Main)
    val closeDetailPane = {
        while (backStack.size > 1) backStack.removeLastOrNull()
    }
    // EntryProvider 必须跨窗口尺寸变化保持同一实例；第三段仍在退场时重建入口，
    // 会让 SaveableStateHolder 同时注册两个相同的二级页 key。
    val latestUseFloatingNavBar by rememberUpdatedState(useFloatingNavBar)
    val latestHapticEnabled by rememberUpdatedState(hapticEnabled)
    val latestFloatingNavBarStyle by rememberUpdatedState(floatingNavBarStyle)
    val latestNavAlignment by rememberUpdatedState(navAlignment)
    val latestCompactNavigationRail by rememberUpdatedState(compactNavigationRail)
    val latestSupportsSupportingPane by rememberUpdatedState(supportsSupportingPane)
    val latestModifier by rememberUpdatedState(modifier)
    val latestIntentToProcess by rememberUpdatedState(intentToProcess)
    val latestPagerState by rememberUpdatedState(pagerState)
    val latestIsFolded by rememberUpdatedState(isFolded)

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        transition = NavTransitions.MiuixDefault,
        effects = NavDisplayEffects(
            enableCornerClip = true,
            dimAmount = 0.5f,
            blockInputDuringTransition = false,
        ),
    ) {
            entry<HomeRoute.Main> {
                MiuixMainContent(
                    modifier = latestModifier,
                    intentToProcess = latestIntentToProcess,
                    hapticEnabled = latestHapticEnabled,
                    useFloatingNavBar = latestUseFloatingNavBar,
                    floatingNavBarStyle = latestFloatingNavBarStyle,
                    navAlignment = latestNavAlignment,
                    allowAppExit = backStack.size == 1,
                    navigationRailState = navigationRailState,
                    compactNavigationRail = latestCompactNavigationRail,
                    externalPagerState = latestPagerState,
                    isFolded = latestIsFolded,
                    onTopLevelPageChanged = {
                        // 主内容/二级页并排时只更新主内容，二级页保持原位。
                        if (!latestSupportsSupportingPane) closeDetailPane()
                    },
                    onNavigateToSettingsSubPage = { page ->
                        backStack.add(HomeRoute.SettingsSubPage(page))
                    },
                    onNavigateToRuleSubPage = { page ->
                        backStack.add(page.toHomeRoute())
                    },
                    onNavigateToOrderDetail = { orderId ->
                        backStack.add(HomeRoute.OrderDetail(orderId))
                    },
                    onNavigateToGroupDetail = { groupId ->
                        backStack.add(HomeRoute.GroupDetail(groupId))
                    }
                )
            }
            entry<HomeRoute.RuleSubPage> { route ->
                com.Badnng.moe.ui.screen.miuix.MiuixRuleSubPageScreen(
                    page = route.toRulePage(),
                    supportingPane = latestSupportsSupportingPane,
                    onBack = { backStack.removeLastOrNull() },
                    onNavigate = { page -> backStack.add(page.toHomeRoute()) },
                    onReplace = { page ->
                        backStack.removeLastOrNull()
                        backStack.add(page.toHomeRoute())
                    },
                )
            }
            entry<HomeRoute.SettingsSubPage> { route ->
                MiuixSettingsSubPageDirect(
                    page = route.page,
                    supportingPane = latestSupportsSupportingPane,
                    onBack = { backStack.removeLastOrNull() },
                    onNavigate = { page ->
                        backStack.add(HomeRoute.SettingsSubPage(page))
                    },
                    onOpenCorrectionDraft = { orderId ->
                        backStack.add(HomeRoute.RecognitionCorrectionEditor(orderId))
                    },
                )
            }
            entry<HomeRoute.RecognitionCorrectionEditor> { route ->
                MiuixSettingsSubPageDirect(
                    page = SettingsPage.RecognitionCorrection,
                    correctionOrderId = route.orderId,
                    supportingPane = latestSupportsSupportingPane,
                    onBack = { backStack.removeLastOrNull() },
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
                        supportingPane = latestSupportsSupportingPane,
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
                val orders by db.orderGroupDao()
                    .getOrdersByGroupId(route.groupId)
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val completedCount = orders.count { it.isCompleted }
                val totalCount = orders.size
                if (group != null) {
                    com.Badnng.moe.ui.screen.miuix.MiuixGroupDetailScreen(
                        group = group,
                        orders = orders,
                        completedCount = completedCount,
                        totalCount = totalCount,
                        supportingPane = latestSupportsSupportingPane,
                        onBack = { backStack.removeLastOrNull() },
                        onOpenOrder = { order ->
                            backStack.add(HomeRoute.OrderDetail(order.id))
                        },
                        onMarkOrderCompleted = { order ->
                            runBlocking {
                                db.orderDao().markAsCompleted(order.id, System.currentTimeMillis())
                            }
                        },
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MiuixMainContent(
    modifier: Modifier,
    intentToProcess: Intent?,
    hapticEnabled: Boolean,
    useFloatingNavBar: Boolean,
    floatingNavBarStyle: MiuixFloatingNavigationBarStyle,
    navAlignment: String = "center",
    allowAppExit: Boolean,
    navigationRailState: NavigationRailState,
    compactNavigationRail: Boolean,
    externalPagerState: androidx.compose.foundation.pager.PagerState? = null,
    isFolded: Boolean,
    onTopLevelPageChanged: (Int) -> Unit = {},
    onNavigateToSettingsSubPage: (SettingsPage) -> Unit,
    onNavigateToRuleSubPage: (SimpleRuleCenterPage) -> Unit,
    onNavigateToOrderDetail: (String) -> Unit = {},
    onNavigateToGroupDetail: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val pagerState = externalPagerState ?: rememberSaveablePagerState(pageCount = { 4 })
    val isWindowFocused = LocalWindowInfo.current.isWindowFocused
    val latestWindowFocused by rememberUpdatedState(isWindowFocused)
    var committedTopLevelPage by remember { mutableIntStateOf(pagerState.settledPage) }
    val currentPage by remember { androidx.compose.runtime.derivedStateOf { pagerState.settledPage } }
    val navigationSelectedPage = committedTopLevelPage
    val coroutineScope = rememberCoroutineScope()
    val motionScheme = MaterialTheme.motionScheme
    val topLevelContentAlpha = remember { Animatable(1f) }
    var topLevelTransitionJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(pagerState.currentPage, isWindowFocused) {
        if (isWindowFocused && topLevelTransitionJob?.isActive != true) {
            committedTopLevelPage = pagerState.currentPage
        }
    }
    LaunchedEffect(isWindowFocused) {
        val pageToRestore = if (topLevelTransitionJob?.isActive == true) {
            committedTopLevelPage
        } else {
            pagerState.settledPage
        }
        val hasUncommittedPage =
            pagerState.currentPage != pageToRestore ||
                pagerState.targetPage != pageToRestore ||
                kotlin.math.abs(pagerState.currentPageOffsetFraction) > 0.001f ||
                pagerState.isScrollInProgress
        if (!isWindowFocused && hasUncommittedPage) {
            topLevelTransitionJob?.cancelAndJoin()
            topLevelContentAlpha.snapTo(1f)
            pagerState.scrollToPage(pageToRestore)
        }
    }
    val haptic = LocalHapticFeedback.current
    val orderViewModelFactory = remember(context) {
        ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as Application,
        )
    }
    val viewModel: OrderViewModel = viewModel(factory = orderViewModelFactory)

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
    var isQrDialogVisible by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isLargeScreen = configuration.screenWidthDp >= MIUIX_LARGE_SCREEN_MIN_WIDTH_DP
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

    val imeBottomInset = WindowInsets.ime.getBottom(LocalDensity.current)
    // 系统可能会把其他悬浮窗口的输入法 Insets 同步给当前窗口；仅当前 App
    // 仍持有窗口焦点时，才把它视为本应用正在使用输入法。
    val isImeVisible = imeBottomInset > 0 && LocalWindowInfo.current.isWindowFocused

    // 主页面按返回键时，从最近任务移除卡片
    androidx.activity.compose.BackHandler(
        enabled = allowAppExit && !isEditMode && !isManaging && !isQrDialogVisible
    ) {
        activity?.finishAndRemoveTask()
    }

    val performHaptic = {
        if (hapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    var previousTopLevelPage by remember { mutableIntStateOf(currentPage) }
    LaunchedEffect(currentPage) {
        if (currentPage != previousTopLevelPage) {
            previousTopLevelPage = currentPage
            onTopLevelPageChanged(currentPage)
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
    var navigationBarMeasurement by remember(
        useFloatingNavBar,
        floatingNavBarStyle,
        useNavigationRail,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
    ) {
        mutableStateOf<MiuixBottomBarMeasurement?>(null)
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
        navigationBarMeasurement
            ?.takeIf {
                it.rootBottomInRootPx == rootBottomInRootPx &&
                    it.rootWidthPx == rootWidthPx
            }
            ?.let { it.rootBottomInRootPx - it.navigationBarTopInRootPx }
            ?.takeIf { it > 0 }
            ?.let { with(density) { it.toDp() } }
            ?.takeIf {
                val minimum = (
                    estimatedNavigationBarTopFromBottom -
                        MiuixHomeBottomLayoutDefaults.NavigationMeasurementTolerance
                    ).coerceAtLeast(safeBottomInset)
                val maximum = estimatedNavigationBarTopFromBottom +
                    MiuixHomeBottomLayoutDefaults.NavigationMeasurementTolerance
                it >= minimum && it <= maximum
            }
    }
    val bottomLayoutInfo = MiuixHomeBottomLayoutInfo(
        safeBottomInset = safeBottomInset,
        navigationBarTopFromBottom = measuredNavigationBarTopFromBottom
            ?: estimatedNavigationBarTopFromBottom
    )
    val updateNavigationBarMeasurement = { coordinates: androidx.compose.ui.layout.LayoutCoordinates ->
        if (rootBottomInRootPx > 0 && rootWidthPx > 0) {
            navigationBarMeasurement = MiuixBottomBarMeasurement(
                navigationBarTopInRootPx = coordinates.boundsInRoot().top.roundToInt(),
                rootBottomInRootPx = rootBottomInRootPx,
                rootWidthPx = rootWidthPx,
            )
        }
    }

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
    // NavigationRailState 由主页外层持有，窗口尺寸变化时仍使用同一个展开状态源。
    val navigationRailAvailable = useNavigationRail &&
        !isEditMode &&
        !isManaging
    val navigationRailExpanded = navigationRailAvailable && navigationRailState.isExpanded
    LaunchedEffect(navigationRailAvailable, compactNavigationRail) {
        if (!navigationRailAvailable && compactNavigationRail) {
            navigationRailState.collapse()
        }
    }
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
    BackHandler(
        enabled = compactNavigationRail && navigationRailExpanded,
    ) {
        navigationRailState.collapse()
    }
    val navigateToTopLevelPage: (Int) -> Unit = { page ->
        performHaptic()
        if (compactNavigationRail) navigationRailState.collapse()
        if (page != committedTopLevelPage) {
            committedTopLevelPage = page
            onTopLevelPageChanged(page)
            val previousTransition = topLevelTransitionJob
            topLevelTransitionJob = coroutineScope.launch {
                val currentTransition = coroutineContext.job
                try {
                    previousTransition?.cancelAndJoin()
                    topLevelContentAlpha.snapTo(1f)
                    if (useNavigationRail) {
                        topLevelContentAlpha.animateTo(
                            targetValue = 0f,
                            animationSpec = motionScheme.defaultEffectsSpec<Float>(),
                        )
                        pagerState.scrollToPage(page)
                        topLevelContentAlpha.animateTo(
                            targetValue = 1f,
                            animationSpec = motionScheme.defaultEffectsSpec<Float>(),
                        )
                    } else {
                        animateMiuixPagerToPage(pagerState, page)
                    }
                } finally {
                    if (useNavigationRail) {
                        withContext(NonCancellable) {
                            topLevelContentAlpha.snapTo(1f)
                        }
                    }
                    if (topLevelTransitionJob == currentTransition) {
                        if (latestWindowFocused && pagerState.currentPage != page) {
                            committedTopLevelPage = pagerState.currentPage
                            onTopLevelPageChanged(pagerState.currentPage)
                        }
                        topLevelTransitionJob = null
                    }
                }
            }
        }
    }

    LaunchedEffect(intentToProcess) {
        if (intentToProcess?.getBooleanExtra("show_update_download", false) == true) {
            committedTopLevelPage = 3
            if (compactNavigationRail) navigationRailState.collapse()
            onTopLevelPageChanged(3)
            pagerState.scrollToPage(3)
            (context as? com.Badnng.moe.activity.MainActivity)?.intentToProcess = null
        }
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
        if (navigationRailAvailable && !compactNavigationRail) {
            MiuixHomeNavigationRail(
                state = navigationRailState,
                currentPage = navigationSelectedPage,
                onPageSelected = navigateToTopLevelPage,
                modifier = Modifier.fillMaxHeight(),
            )
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
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = topLevelContentAlpha.value },
                    userScrollEnabled = !useNavigationRail && isWindowFocused,
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
                                onQrDialogVisibilityChange = { isQrDialogVisible = it },
                                onNavigateToRecognitionCorrection = {
                                    onNavigateToSettingsSubPage(SettingsPage.RecognitionCorrection)
                                },
                                onNavigateToOrderDetail = onNavigateToOrderDetail,
                                onNavigateToGroupDetail = onNavigateToGroupDetail
                            )
                            1 -> MiuixRulesScreen(
                                bottomLayoutInfo = bottomLayoutInfo,
                                onExpandNavigationRail = onExpandNavigationRail,
                                onNavigateToSubPage = onNavigateToRuleSubPage,
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
                            3 -> AboutSettingsContent(
                                performHaptic = performHaptic,
                                bottomPadding = bottomLayoutInfo.pageContentBottomPadding,
                                onNavigateToCredits = {
                                    onNavigateToSettingsSubPage(SettingsPage.Credits)
                                },
                                onNavigateToSponsor = {
                                    onNavigateToSettingsSubPage(SettingsPage.Sponsor)
                                },
                                onNavigateToBackup = {
                                    onNavigateToSettingsSubPage(SettingsPage.Backup)
                                },
                                onNavigateToDeveloperOptions = {
                                    onNavigateToSettingsSubPage(SettingsPage.Developer)
                                },
                                showBackButton = false,
                                onExpandNavigationRail = onExpandNavigationRail,
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
                !isQrDialogVisible &&
                !isScrollingDown &&
                !isImeVisible &&
                !useNavigationRail &&
                !useFloatingNavBar,
            hideImmediately = isQrDialogVisible,
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
                        .onGloballyPositioned(updateNavigationBarMeasurement),
                    color = barColor
                ) {
                    NavigationBarItem(
                        selected = navigationSelectedPage == 0,
                        onClick = { navigateToTopLevelPage(0) },
                        icon = MiuixIcons.Regular.Home,
                        label = "主页"
                    )
                    NavigationBarItem(
                        selected = navigationSelectedPage == 1,
                        onClick = { navigateToTopLevelPage(1) },
                        icon = MiuixIcons.Regular.Edit,
                        label = "规则"
                    )
                    NavigationBarItem(
                        selected = navigationSelectedPage == 2,
                        onClick = { navigateToTopLevelPage(2) },
                        icon = MiuixIcons.Regular.Settings,
                        label = "设置"
                    )
                    NavigationBarItem(
                        selected = navigationSelectedPage == 3,
                        onClick = { navigateToTopLevelPage(3) },
                        icon = MiuixIcons.Regular.Info,
                        label = "关于"
                    )
                }
            }
        }

        // 悬浮底栏
        NavigationOverlayVisibility(
            visible = !isEditMode &&
                !isManaging &&
                !isQrDialogVisible &&
                !isScrollingDown &&
                !isImeVisible &&
                useFloatingNavBar &&
                !isFolded,
            hideImmediately = isQrDialogVisible,
        ) {
            if (isIosLikeFloatingBar) {
                val navigationItems = remember {
                    listOf(
                        NavigationItem(label = "主页", icon = MiuixIcons.Regular.Home),
                        NavigationItem(label = "规则", icon = MiuixIcons.Regular.Edit),
                        NavigationItem(label = "设置", icon = MiuixIcons.Regular.Settings),
                        NavigationItem(label = "关于", icon = MiuixIcons.Regular.Info),
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
                        selectedIndex = navigationSelectedPage,
                        onItemClick = { page ->
                            navigateToTopLevelPage(page)
                        },
                        backdrop = backdrop,
                        isBlurActive = blurEnabled,
                        isDark = isInDarkTheme,
                        modifier = iosBarModifier.onGloballyPositioned(updateNavigationBarMeasurement),
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
                            .onGloballyPositioned(updateNavigationBarMeasurement),
                        color = floatingBarColor,
                        horizontalAlignment = animatedHorizontalAlignment,
                        horizontalOutSidePadding = 24.dp
                    ) {
                        FloatingNavigationBarItem(
                            selected = navigationSelectedPage == 0,
                            onClick = { navigateToTopLevelPage(0) },
                            icon = MiuixIcons.Regular.Home,
                            label = "主页"
                        )
                        FloatingNavigationBarItem(
                            selected = navigationSelectedPage == 1,
                            onClick = { navigateToTopLevelPage(1) },
                            icon = MiuixIcons.Regular.Edit,
                            label = "规则"
                        )
                        FloatingNavigationBarItem(
                            selected = navigationSelectedPage == 2,
                            onClick = { navigateToTopLevelPage(2) },
                            icon = MiuixIcons.Regular.Settings,
                            label = "设置"
                        )
                        FloatingNavigationBarItem(
                            selected = navigationSelectedPage == 3,
                            onClick = { navigateToTopLevelPage(3) },
                            icon = MiuixIcons.Regular.Info,
                            label = "关于"
                        )
                    }
                }
            }
        }

        // 添加记录底部弹窗
        val addOrderViewModel: OrderViewModel = viewModel(factory = orderViewModelFactory)
        com.Badnng.moe.ui.component.AddOrderBottomSheet(
            show = showBottomSheet,
            viewModel = addOrderViewModel,
            onDismiss = { showBottomSheet = false }
        )
        } // 内容 Box
        } // Row

        // 窄大屏窗口使用叠加式 NavigationRail，避免展开/收起时改变 Pager 的可用宽度。
        AnimatedVisibility(
            visible = compactNavigationRail && navigationRailExpanded,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { navigationRailState.collapse() },
            )
        }
        if (compactNavigationRail && navigationRailAvailable) {
            MiuixHomeNavigationRail(
                state = navigationRailState,
                currentPage = navigationSelectedPage,
                onPageSelected = navigateToTopLevelPage,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight(),
            )
        }

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

private const val MIUIX_LARGE_SCREEN_MIN_WIDTH_DP = 700
private const val MIUIX_FIXED_NAVIGATION_RAIL_MIN_WIDTH_DP = 900
private const val MIUIX_PANE_ROLE_METADATA = "miuix_home_pane_role"

private suspend fun animateMiuixPagerToPage(
    pagerState: androidx.compose.foundation.pager.PagerState,
    targetPage: Int,
) {
    pagerState.scroll(MutatePriority.UserInput) {
        val distance = kotlin.math.abs(targetPage - pagerState.currentPage).coerceAtLeast(2)
        val duration = 100 * distance + 100
        val layoutInfo = pagerState.layoutInfo
        val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
        val currentDistanceInPages =
            targetPage - pagerState.currentPage - pagerState.currentPageOffsetFraction
        val scrollPixels = currentDistanceInPages * pageSize

        var previousValue = 0f
        animate(
            initialValue = 0f,
            targetValue = scrollPixels,
            animationSpec = tween(
                durationMillis = duration,
                easing = EaseInOut,
            ),
        ) { currentValue, _ ->
            previousValue += scrollBy(currentValue - previousValue)
        }
    }

    if (pagerState.currentPage != targetPage) {
        pagerState.scrollToPage(targetPage)
    }
}

private data class MiuixBottomBarMeasurement(
    val navigationBarTopInRootPx: Int,
    val rootBottomInRootPx: Int,
    val rootWidthPx: Int,
)

@Composable
private fun MiuixHomeNavigationRail(
    state: NavigationRailState,
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        state = state,
        modifier = modifier,
        color = MiuixTheme.colorScheme.surface,
        showDivider = state.isExpanded,
        minWidth = 0.dp,
        expandContentDescription = "展开侧边导航",
        collapseContentDescription = "收起侧边导航",
    ) {
        NavigationRailItem(
            selected = currentPage == 0,
            onClick = { onPageSelected(0) },
            icon = MiuixIcons.Regular.Home,
            label = "主页",
        )
        NavigationRailItem(
            selected = currentPage == 1,
            onClick = { onPageSelected(1) },
            icon = MiuixIcons.Regular.Edit,
            label = "规则",
        )
        NavigationRailItem(
            selected = currentPage == 2,
            onClick = { onPageSelected(2) },
            icon = MiuixIcons.Regular.Settings,
            label = "设置",
        )
        NavigationRailItem(
            selected = currentPage == 3,
            onClick = { onPageSelected(3) },
            icon = MiuixIcons.Regular.Info,
            label = "关于",
        )
    }
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
    hideImmediately: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (hideImmediately) return
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
    onNavigate: (SettingsPage) -> Unit = {},
    onOpenCorrectionDraft: (String) -> Unit = {},
    correctionOrderId: String? = null,
    supportingPane: Boolean = false,
) {
    val title = when (page) {
        SettingsPage.Preference -> "偏好设置"
        SettingsPage.Permission -> "权限与保活"
        SettingsPage.Screenshot -> "截图方式"
        SettingsPage.Recognition -> "识别方式"
        SettingsPage.CustomPrompt -> "自定义 Prompt"
        SettingsPage.RecognitionCorrection -> if (correctionOrderId == null) "纠正识别" else "创建纠正规则"
        SettingsPage.KeepAlive -> "保活设置"
        SettingsPage.WearableSync -> "手表同步"
        SettingsPage.Storage -> "清理空间"
        SettingsPage.About -> "关于"
        SettingsPage.Backup -> "备份与恢复"
        SettingsPage.Sponsor -> "赞助"
        SettingsPage.NotificationApps -> "通知识别应用管理"
        SettingsPage.Credits -> "致谢"
        SettingsPage.Developer -> "开发者选项"
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
            onNavigateToSponsor = { onNavigate(SettingsPage.Sponsor) },
            onNavigateToBackup = { onNavigate(SettingsPage.Backup) },
            onNavigateToDeveloperOptions = { onNavigate(SettingsPage.Developer) },
            onBack = onBack,
            supportingPane = supportingPane,
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
                                        if (supportingPane) {
                                            MiuixIcons.Regular.Close
                                        } else {
                                            MiuixIcons.Regular.Back
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
                                SettingsPage.Recognition -> com.Badnng.moe.ui.screen.settings.RecognitionSettingsContent(
                                    performHaptic,
                                    topBarHeight,
                                    scrollState,
                                    onNavigateToPromptEditor = { onNavigate(SettingsPage.CustomPrompt) },
                                )
                                SettingsPage.CustomPrompt -> com.Badnng.moe.ui.screen.settings.PromptEditorContent(
                                    performHaptic,
                                    topBarHeight,
                                )
                                SettingsPage.RecognitionCorrection -> if (correctionOrderId == null) {
                                    com.Badnng.moe.ui.component.RecognitionCorrectionRouteContent(
                                        isMiuix = true,
                                        onBack = onBack,
                                        modifier = Modifier.fillMaxSize().padding(top = topBarHeight),
                                        onOpenDraft = onOpenCorrectionDraft,
                                    )
                                } else {
                                    com.Badnng.moe.ui.component.RecognitionCorrectionEditorRouteContent(
                                        orderId = correctionOrderId,
                                        isMiuix = true,
                                        onBack = onBack,
                                        modifier = Modifier.fillMaxSize().padding(top = topBarHeight),
                                    )
                                }
                                SettingsPage.Permission -> com.Badnng.moe.ui.screen.settings.PermissionSettingsContent(performHaptic, topBarHeight, scrollState)
                                SettingsPage.Preference -> com.Badnng.moe.ui.screen.settings.PreferenceSettingsContent(performHaptic, onNavigate, topBarHeight, scrollState)
                                SettingsPage.KeepAlive -> com.Badnng.moe.ui.screen.settings.KeepAliveSettingsContent(performHaptic, topBarHeight, scrollState)
                                SettingsPage.Storage -> com.Badnng.moe.ui.screen.settings.StorageSettingsContent(performHaptic, prefs, topBarHeight + 26.dp, scrollState)
                                SettingsPage.Backup -> com.Badnng.moe.ui.screen.settings.BackupSettingsContent(performHaptic, topBarHeight)
                                SettingsPage.Sponsor -> com.Badnng.moe.ui.screen.settings.SponsorSettingsContent(topBarHeight, scrollState)
                                SettingsPage.NotificationApps -> com.Badnng.moe.ui.screen.settings.NotificationAppsSettingsContent(
                                    performHaptic = performHaptic,
                                    topPadding = topBarHeight + 8.dp,
                                    showSystemApps = showSystemApps
                                )
                                SettingsPage.Credits -> com.Badnng.moe.ui.screen.settings.CreditsSettingsContent(performHaptic, topBarHeight, scrollState)
                                SettingsPage.Developer -> com.Badnng.moe.ui.screen.settings.DeveloperSettingsContent(performHaptic, topBarHeight, scrollState)
                                SettingsPage.WearableSync -> com.Badnng.moe.ui.screen.settings.WearableSyncSettingsContent(performHaptic, topBarHeight, scrollState)
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
