package com.Badnng.moe.ui.screen.miuix

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import top.yukonga.miuix.kmp.nav.transition.navDirectionalTransition
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
import com.Badnng.moe.rules.WordType
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
import top.yukonga.miuix.kmp.blur.LayerBackdrop
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
    WordCategory,
    BlockedWords,
    CustomLocations,
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
    data class OrderDetail(val orderId: String) : HomeRoute

    @Serializable
    data class GroupDetail(val groupId: Long) : HomeRoute
}

private fun SimpleRuleCenterPage.toHomeRoute(): HomeRoute.RuleSubPage = when (this) {
    SimpleRuleCenterPage.Root -> error("规则主页不能作为二级路由")
    is SimpleRuleCenterPage.WordCategory -> HomeRoute.RuleSubPage(
        kind = RuleSubPageKind.WordCategory,
        category = type.name,
    )
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
    SimpleRuleCenterPage.CustomLocations -> HomeRoute.RuleSubPage(kind = RuleSubPageKind.CustomLocations)
    SimpleRuleCenterPage.CustomIcons -> HomeRoute.RuleSubPage(kind = RuleSubPageKind.CustomIcons)
}

private fun HomeRoute.RuleSubPage.toRulePage(): SimpleRuleCenterPage = when (kind) {
    RuleSubPageKind.Category -> SimpleRuleCenterPage.Category(SimpleRuleCategory.valueOf(category))
    RuleSubPageKind.CreateBrand -> SimpleRuleCenterPage.CreateBrand(SimpleRuleCategory.valueOf(category))
    RuleSubPageKind.Brand -> SimpleRuleCenterPage.Brand(brandId)
    RuleSubPageKind.CreateTemplate -> SimpleRuleCenterPage.CreateTemplate(brandId)
    RuleSubPageKind.Template -> SimpleRuleCenterPage.Template(brandId, templateId)
    RuleSubPageKind.WordCategory -> SimpleRuleCenterPage.WordCategory(WordType.valueOf(category))
    RuleSubPageKind.BlockedWords -> SimpleRuleCenterPage.BlockedWords
    RuleSubPageKind.CustomLocations -> SimpleRuleCenterPage.CustomLocations
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
    val isPortrait = configuration.screenHeightDp > configuration.screenWidthDp
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
    // 折叠屏展开后即使竖屏也按大屏处理；普通平板竖屏仍走手机逻辑。
    val isFoldableExpanded = foldingFeature != null && !isFolded
    val isLargeScreenWindow = configuration.screenWidthDp >= MIUIX_LARGE_SCREEN_MIN_WIDTH_DP &&
        (!isPortrait || isFoldableExpanded)
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

    // 平板竖屏使用底栏；横屏大屏才使用侧边导航栏。
    val supportsNavigationRail =
        isLargeScreenWindow && !useFloatingNavBar
    val compactNavigationRail = supportsNavigationRail &&
        configuration.screenWidthDp < MIUIX_FIXED_NAVIGATION_RAIL_MIN_WIDTH_DP
    // 大屏横屏侧栏模式下恢复主内容 + 二级页并排：固定侧栏展开时为三段式，
    // 折叠时为两段式；小屏/平板竖屏/悬浮底栏仍由 NavDisplay 全屏呈现。
    val supportsSupportingPane = supportsNavigationRail
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
    // 主页卡片 → 识别详情的一镜到底转场；只在小屏（非三段式）模式下接入。
    val cardMorph = rememberCardMorphController()
    // 订单组卡片的展开态提升到这里：列表里的卡片与叠加层里的镜像卡片必须读同一份，
    // 否则叠加层那份会画出收起态卡片（它的 rememberSaveable 是实例私有的），
    // 转场期间露出「只剩几行」的假卡片。见 LocalCardMorphExpandedGroups 的注释。
    val expandedGroupIds = remember { mutableStateOf(setOf<Long>()) }
    // 只能出栈「真正的详情入口」：铺开途中详情还没入栈，返回键此时也会走到收回逻辑，
    // 不加这个判断就会把主页入口本身弹掉（栈空 → 整屏空白）。
    // 订单与订单组两条一镜到底链路都可能停在栈顶，两者都要能出栈。
    cardMorph.onRequestPop = {
        val top = backStack.lastOrNull()
        if (top is HomeRoute.OrderDetail || top is HomeRoute.GroupDetail) backStack.removeLastOrNull()
    }
    // 铺开动画结束才入栈；入栈与叠加层退场同帧完成。按 key 前缀决定入哪条详情：
    // `order:` → 识别详情，`group:` → 订单组详情。
    // 加一层防重：同一路由入栈两次会让 NavDisplay 抛 Duplicate contentKey 直接杀进程。
    cardMorph.onRequestPush = { key ->
        when {
            key.startsWith(GroupMorphKeyPrefix) -> {
                val groupId = key.removePrefix(GroupMorphKeyPrefix).toLongOrNull()
                if (groupId != null && backStack.lastOrNull() !is HomeRoute.GroupDetail) {
                    backStack.add(HomeRoute.GroupDetail(groupId))
                }
            }
            else -> {
                val orderId = key.removePrefix(OrderMorphKeyPrefix)
                if (backStack.lastOrNull() !is HomeRoute.OrderDetail) {
                    backStack.add(HomeRoute.OrderDetail(orderId))
                }
            }
        }
    }
    val closeDetailPane = {
        while (backStack.size > 1) backStack.removeLastOrNull()
    }
    // 大屏双栏是「主内容 + 详情面板」：面板已经显示这条订单时再入栈毫无收益——
    // 栈里会堆同一 id 的 OrderDetail，而面板内容按 route 复用、画面毫无变化（看着像点击没反应），
    // 关闭却要按同样多次返回键，多按一次直接把应用退出到桌面。
    // 因此：同一条订单 → 不动；换成另一条 → 替换栈顶（与规则子页的 onReplace 一致）。
    // 所有详情入口（主页卡片、面板内详情、作品组详情里的子订单）都必须走这里。
    val pushOrderDetail: (String) -> Unit = { orderId ->
        val top = backStack.lastOrNull()
        when {
            top is HomeRoute.OrderDetail && top.orderId == orderId -> Unit
            top is HomeRoute.OrderDetail -> {
                backStack.removeLastOrNull()
                backStack.add(HomeRoute.OrderDetail(orderId))
            }
            else -> backStack.add(HomeRoute.OrderDetail(orderId))
        }
    }
    // supportsSupportingPane 必须进 remember 的 key：折叠屏/大屏在启动瞬间可能先按竖屏（小屏）
    // 上报一次窗口尺寸，只用 cardMorph 做 key 会把「非空 source」永久冻结——之后即使已经在两栏
    // 布局里，卡片点击仍然走一镜到底分支；而两栏没有叠加层给会话收尾，会话永久驻留，
    // beginExpand 命中残留会话直接 return，表现就是「进过一次之后再也点不进去」。
    val cardMorphSource = remember(cardMorph, supportsSupportingPane) {
        if (supportsSupportingPane) null else CardMorphSource(cardMorph::registerCard, cardMorph::unregisterCard)
    }
    // 从手机布局切进大屏双栏时清掉残留会话（双栏没有叠加层，没人收尾）。
    LaunchedEffect(supportsSupportingPane) {
        if (supportsSupportingPane) cardMorph.abandonSessions()
    }
    // 打开识别详情：手机只发起转场，入栈由控制器在铺开结束时回调（onRequestPush）；
    // 三段式大屏 / 卡片没登记时直接入栈，行为与之前一致。
    // 分支必须按**当前**布局判定，不能只看 cardMorphSource 这个被 remember 固化的对象：
    // 两栏布局下卡片一律直接入栈，绝不走一镜到底（那条路径在双栏没有叠加层）。
    // 主页订单卡片与订单组详情里的子订单卡片共用这一条。
    val openOrderDetail: (String) -> Unit = { orderId ->
        val key = orderMorphKey(orderId)
        if (!supportsSupportingPane && cardMorphSource != null && cardMorph.canMorph(key)) {
            cardMorph.beginExpand(key)
        } else {
            pushOrderDetail(orderId)
        }
    }
    // 主页模态层（BottomSheet / 长按菜单 / 卡片一镜到底）共用的背景采样层。
    // 必须与底栏自身使用的 backdrop 分开，避免 layerBackdrop / textureBlur 递归；
    // 由这里创建是为了让一镜到底叠加层能挂在 NavDisplay 之外——放进导航入口会被
    // NavDisplayEffects.dimAmount 整层压暗（详情入口此时是空实现，结果就是灰屏）。
    val homeOverlayBackdrop = com.Badnng.moe.ui.miuix.rememberMiuixBackdrop()
    // 卡片一镜到底专用的背景采样层：录制必须从主页出现的第一帧就常驻挂在内容区上，
    // 否则转场第一帧才挂上去时 backdrop 里还没有内容，模糊会一直画不出东西（只剩压暗）。
    val cardMorphBackdrop = com.Badnng.moe.ui.miuix.rememberMiuixBackdrop()
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

    val mainContent: @Composable () -> Unit = {
        MiuixMainContent(
            modifier = if (supportsSupportingPane) Modifier.fillMaxSize() else latestModifier,
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
            forceLargeScreen = isFoldableExpanded,
            cardMorph = cardMorph,
            expandedGroupIds = expandedGroupIds,
            cardMorphOrderId = (backStack.lastOrNull() as? HomeRoute.OrderDetail)?.orderId,
            homeOverlayBackdrop = homeOverlayBackdrop,
            cardMorphBackdrop = cardMorphBackdrop,
            onTopLevelPageChanged = {
                // 主内容/二级页并排时只更新主内容，二级页保持原位。
                // 一镜到底进行中时叠加层正在绘制详情，此刻清栈会把它的订单 id 一起清掉（表现为空白页），
                // 因此转场期间不动返回栈，等转场收完再按正常路径处理。
                if (!latestSupportsSupportingPane && !cardMorph.overlayVisible) closeDetailPane()
            },
            onNavigateToSettingsSubPage = { page ->
                backStack.add(HomeRoute.SettingsSubPage(page))
            },
            onNavigateToRuleSubPage = { page ->
                backStack.add(page.toHomeRoute())
            },
            onNavigateToOrderDetail = openOrderDetail,
            onNavigateToGroupDetail = { groupId ->
                // 与订单详情同构：手机走一镜到底（订单组卡片 → 订单组详情），
                // 三段式大屏/未登记该卡片时按原来的方式直接入栈。
                val key = groupMorphKey(groupId)
                if (!supportsSupportingPane && cardMorphSource != null && cardMorph.canMorph(key)) {
                    cardMorph.beginExpand(key)
                } else {
                    backStack.add(HomeRoute.GroupDetail(groupId))
                }
            },
        )
    }

    // 一镜到底的登记入口（LocalCardMorphSource）提到 NavDisplay 与叠加层之外：
    // 组详情页既可能由 NavDisplay 入口绘制、也可能正由叠加层绘制，两处的子订单卡片都要能登记几何；
    // 只包在 mainContent 里的话，详情页拿到的就是 null（子卡片点不出动画）。
    // supportsSupportingPane 为 true 时 cardMorphSource 本身就是 null，语义与之前完全一致（大屏不做一镜到底）。
    CompositionLocalProvider(LocalCardMorphSource provides cardMorphSource) {
    if (supportsSupportingPane) {
        val detailTarget = backStack.lastOrNull()
            ?.takeIf { it != HomeRoute.Main }
            ?.let { MiuixHomeDetailTarget(route = it as HomeRoute, depth = backStack.size - 1) }
        MiuixSupportingPaneLayout(
            modifier = latestModifier,
            detailTarget = detailTarget,
            detailContent = { target ->
                MiuixHomeDetailContent(
                    target = target,
                    supportingPane = true,
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToSettingsSubPage = { page ->
                        backStack.add(HomeRoute.SettingsSubPage(page))
                    },
                    onNavigateToRuleSubPage = { page ->
                        backStack.add(page.toHomeRoute())
                    },
                    onReplaceRuleSubPage = { page ->
                        backStack.removeLastOrNull()
                        backStack.add(page.toHomeRoute())
                    },
                    onOpenOrderDetail = { orderId ->
                        pushOrderDetail(orderId)
                    },
                )
            },
            mainContent = mainContent,
        )
        if (predictiveBackEnabled) {
            PredictiveBackHandler(enabled = backStack.size > 1) {
                try {
                    it.collect { }
                    backStack.removeLastOrNull()
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // 手势取消，保持当前页面
                }
            }
        } else {
            BackHandler(enabled = backStack.size > 1) {
                backStack.removeLastOrNull()
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = {
                // 一镜到底进行中时返回由叠加层负责（先收回卡片再出栈）。
                if (!cardMorph.overlayVisible && backStack.size > 1) backStack.removeLastOrNull()
            },
            transition = if (predictiveBackEnabled) {
                NavTransitions.MiuixDefault
            } else {
                navDirectionalTransition(
                    push = NavTransitions.MiuixDefault,
                    pop = NavTransitions.MiuixDefault,
                    predictivePop = NavTransitions.None,
                )
            },
            effects = NavDisplayEffects(
                enableCornerClip = true,
                // 一镜到底进行中，栈顶详情入口是空实现，底层主页不该被导航层再压暗一次：
                // 转场自己画的遮罩随进度淡入，双重压暗会让起始帧明显偏灰。
                dimAmount = if (cardMorph.overlayVisible) 0f else 0.5f,
                blockInputDuringTransition = false,
            ),
        ) {
            entry<HomeRoute.Main> {
                mainContent()
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
                )
            }
            entry<HomeRoute.OrderDetail>(
                transition = NavTransitions.None,
            ) { route ->
                // 卡片叠加层独占绘制时，底层入口留空，避免同一页绘制两份。
                // 还必须要求「这条路由仍是栈顶」：详情出栈发生在收回动画开始时（叠加层正盖满整屏），
                // 而 NavDisplay 会把退场中的入口再留几帧——叠加层撤掉的那一帧正好是这几帧之一，
                // 只判断 overlayVisible 就会让这一帧画出整页详情（偶现「闪一下识别详情」）。
                if (!cardMorph.overlayVisible && backStack.lastOrNull() == route) {
                    MiuixOrderDetailContent(
                        orderId = route.orderId,
                        supportingPane = latestSupportsSupportingPane,
                        // 这一页可能是「一镜到底」铺开后的落点：返回按钮也走收回动画。
                        onBack = {
                            if (cardMorphSource != null && cardMorph.canMorph(orderMorphKey(route.orderId))) {
                                cardMorph.beginCollapse()
                            } else {
                                backStack.removeLastOrNull()
                            }
                        },
                    )
                }
            }
            entry<HomeRoute.GroupDetail>(
                // 必须与 OrderDetail 入口一致地关掉导航层自己的转场：
                // 一镜到底期间叠加层独占绘制（下面那个 if 恒为假），这条路线的任何转场都只会
                // 出现在「收回动画开始时把手势中途出栈」和「动画收完叠加层撤场」这两帧上——
                // 结果是同一个返回手势被导航层与一镜到底两套驱动同时消费：导航层开始播 pop 转场，
                // 系统判定手势失效并 onBackCancelled，我们的 handleOnBackPressed 永远不来，
                // 表现为「静止态侧滑完全没反应」；取消后又把路由重新入栈，就是「主页从左边往右走」。
                // 组详情这一层永远由叠加层负责，因此和 OrderDetail 一样给 None。
                transition = NavTransitions.None,
            ) { route ->
                // 与 OrderDetail 入口同一套守卫：
                // ① 叠加层独占绘制时（组卡片一镜到底铺开中/静止态/收回中）底层入口留空，避免画两份；
                // ② 还必须是栈顶：出栈发生在收回动画开始时（叠加层正盖满整屏），
                //    而 NavDisplay 会把退场中的入口再留几帧，只判断 overlayVisible 会闪一下整页。
                if (!cardMorph.overlayVisible && backStack.lastOrNull() == route) {
                    MiuixGroupDetailRouteContent(
                        groupId = route.groupId,
                        supportingPane = latestSupportsSupportingPane,
                        // 这一页可能是「一镜到底」铺开后的落点：返回按钮也走收回动画。
                        onBack = {
                            if (cardMorphSource != null && cardMorph.canMorph(groupMorphKey(route.groupId))) {
                                cardMorph.beginCollapse()
                            } else {
                                backStack.removeLastOrNull()
                            }
                        },
                        onOpenOrder = { order -> openOrderDetail(order.id) },
                        onMarkAllCompletedDone = { backStack.removeLastOrNull() },
                    )
                }
            }
        }
        // 卡片 → 识别详情一镜到底叠加层：NavDisplay 的兄弟节点，且在它之后绘制。
        // 不能放进 HomeRoute.Main 入口：导航层会按 dimAmount 把非栈顶入口整层压暗，
        // 而栈顶详情入口在叠加层可见时是空实现，那样整屏只剩压暗层，就是「灰屏」。
        if (cardMorph.overlayVisible) {
            // 叠加层那一份：必须一起包。叠加层是 NavDisplay 的兄弟节点，不在 pager 子树里，
            // 只包列表侧的话，镜像卡片会读到默认空集 → 仍然画收起态 → 本方案失效。
            CompositionLocalProvider(LocalCardMorphExpandedGroups provides expandedGroupIds.value) {
            MiuixCardMorphOverlay(
                controller = cardMorph,
                backdrop = cardMorphBackdrop,
                // 起点是卡片底色，终点是详情页底色：容器底色随进度在两者之间过渡。
                surfaceColor = MiuixTheme.colorScheme.surfaceContainer,
                destinationColor = MiuixTheme.colorScheme.surface,
            ) { key ->
                // key 来自会话自己：并行动画时同时有两条会话，各画各的详情页。
                // 前缀决定画哪一页：`order:` → 识别详情，`group:` → 订单组详情。
                if (key.startsWith(GroupMorphKeyPrefix)) {
                    key.removePrefix(GroupMorphKeyPrefix).toLongOrNull()?.let { groupId ->
                        MiuixGroupDetailRouteContent(
                            groupId = groupId,
                            supportingPane = false,
                            onBack = { cardMorph.beginCollapse() },
                            // 组详情里的子订单卡片：能一镜到底就再开一条会话（叠在组详情上，
                            // 引擎按「并行动画」处理），否则按原来的方式入栈。
                            onOpenOrder = { order -> openOrderDetail(order.id) },
                            onMarkAllCompletedDone = { cardMorph.beginCollapse() },
                        )
                    }
                } else {
                    MiuixOrderDetailContent(
                        orderId = key.removePrefix(OrderMorphKeyPrefix),
                        supportingPane = false,
                        onBack = { cardMorph.beginCollapse() },
                    )
                }
            }
            } // ← 新增：闭合 A2 的 LocalCardMorphExpandedGroups（叠加层侧）
        }
        // 一镜到底期间由卡片叠加层接管返回手势。
        // 必须放在 NavDisplay 之后：返回回调按注册顺序定优先级，NavDisplay 自己也会注册一个，
        // 放在它前面就会被它抢走，手势事件永远到不了这里的 dragProgress。
        // registrationKey 带上栈深：详情出栈/入栈后重新挂载我们的返回回调，
        // 保证它始终排在 NavDisplay 自己注册的那个之后（系统按后注册者优先派发）。
        MiuixCardMorphDismissBackHandler(
            controller = cardMorph,
            // 只有「一镜到底正在占用当前屏幕」时才由它接管返回：
            // · 详情静止态 / 收回动画中：栈顶就是 OrderDetail 或 GroupDetail；
            // · 铺开动画中（详情还没入栈）：栈顶是 Main 但叠加层已经在跑。
            // 不能写成 backStack.size > 1：那样普通二级页（规则 / 设置…）也会被它接管，
            // 而它没有会话时只会弹详情，于是手势被吞掉、页面纹丝不动（只能点返回箭头）。
            enabled = cardMorph.overlayVisible &&
                (backStack.lastOrNull() == HomeRoute.Main ||
                    backStack.lastOrNull() is HomeRoute.OrderDetail ||
                    backStack.lastOrNull() is HomeRoute.GroupDetail),
            predictiveBackEnabled = predictiveBackEnabled,
            morphAvailable = cardMorphSource != null,
            registrationKey = backStack.size,
        )
        } // Box：NavDisplay + 卡片转场叠加层
    }
    } // CompositionLocalProvider(LocalCardMorphSource)
}

private data class MiuixHomeDetailTarget(
    val route: HomeRoute,
    val depth: Int,
) {
    val contentKey: Any get() = route to depth
}

/** 识别详情内容：三段式并排与手机全屏共用同一份实现。 */
@Composable
private fun MiuixOrderDetailContent(
    orderId: String,
    supportingPane: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val order = remember(orderId) {
        runBlocking { OrderDatabase.getDatabase(context).orderDao().getOrderById(orderId) }
    }
    if (order != null) {
        com.Badnng.moe.ui.screen.miuix.MiuixOrderDetailScreen(
            order = order,
            supportingPane = supportingPane,
            onBack = onBack,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MiuixSupportingPaneLayout(
    modifier: Modifier = Modifier,
    detailTarget: MiuixHomeDetailTarget?,
    detailContent: @Composable (MiuixHomeDetailTarget) -> Unit,
    mainContent: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val motionScheme = MaterialTheme.motionScheme
        val detailPaneWidth = (maxWidth * 0.42f).coerceIn(360.dp, 600.dp)
        val saveableStateHolder = rememberSaveableStateHolder()
        var retainedTarget by remember { mutableStateOf(detailTarget) }
        if (detailTarget != null) {
            SideEffect { retainedTarget = detailTarget }
        }
        val renderedTarget = detailTarget ?: retainedTarget
        // 面板宽度必须由状态直接决定。之前宽度只由 AnimatedVisibility 的 expand/shrinkHorizontally
        // 产生：关闭时状态先变 null、尺寸节点还在收缩，这个窗口里状态又变回非 null 时尺寸动画不再重播，
        // 面板就退化成一个「宽度恒为 0 却仍然 visible」的布局节点——状态是打开、屏幕上一个像素都没有，
        // 再点卡片也看不出任何变化（用户看到的「第二次进不去」）。
        // 现在宽度始终朝 detailPaneWidth + 1.dp 收敛，不存在「打开但 0 宽」这一态。
        val paneOpen = detailTarget != null
        val paneWidth by animateDpAsState(
            targetValue = if (paneOpen) detailPaneWidth + 1.dp else 0.dp,
            animationSpec = motionScheme.defaultSpatialSpec<Dp>(),
            label = "miuixDetailPaneWidth",
        )
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                mainContent()
            }
            // 宽度收完后真正退出组合，避免面板内容长期与 SaveableStateProvider 共存。
            if (paneWidth > 0.dp) {
                Row(
                    modifier = Modifier
                        .width(paneWidth)
                        .fillMaxHeight()
                        .clipToBounds(),
                ) {
                    Spacer(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    )
                    // requiredWidth：面板内容保持满宽、被外层裁切着「露出来」，
                    // 而不是在展开过程中被外层约束压扁（width() 会被父约束压缩）。
                    Box(
                        modifier = Modifier
                            .requiredWidth(detailPaneWidth)
                            .fillMaxHeight()
                            .background(MiuixTheme.colorScheme.surface),
                    ) {
                        renderedTarget?.let { target ->
                            MiuixAnimatedDetailContent(
                                target = target,
                                detailContent = detailContent,
                                saveableStateHolder = saveableStateHolder,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MiuixAnimatedDetailContent(
    target: MiuixHomeDetailTarget,
    detailContent: @Composable (MiuixHomeDetailTarget) -> Unit,
    saveableStateHolder: SaveableStateHolder,
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
        label = "miuixDetailNavigation",
    ) { renderedTarget ->
        saveableStateHolder.SaveableStateProvider(renderedTarget.contentKey.toString()) {
            detailContent(renderedTarget)
        }
    }
}

@Composable
private fun MiuixHomeDetailContent(
    target: MiuixHomeDetailTarget,
    supportingPane: Boolean,
    onBack: () -> Unit,
    onNavigateToSettingsSubPage: (SettingsPage) -> Unit,
    onNavigateToRuleSubPage: (SimpleRuleCenterPage) -> Unit,
    onReplaceRuleSubPage: (SimpleRuleCenterPage) -> Unit,
    onOpenOrderDetail: (String) -> Unit,
) {
    when (val route = target.route) {
        is HomeRoute.RuleSubPage -> com.Badnng.moe.ui.screen.miuix.MiuixRuleSubPageScreen(
            page = route.toRulePage(),
            supportingPane = supportingPane,
            onBack = onBack,
            onNavigate = onNavigateToRuleSubPage,
            onReplace = onReplaceRuleSubPage,
        )
        is HomeRoute.SettingsSubPage -> MiuixSettingsSubPageDirect(
            page = route.page,
            supportingPane = supportingPane,
            onBack = onBack,
            onNavigate = onNavigateToSettingsSubPage,
        )
        is HomeRoute.OrderDetail -> {
            MiuixOrderDetailContent(
                orderId = route.orderId,
                supportingPane = supportingPane,
                onBack = onBack,
            )
        }
        is HomeRoute.GroupDetail -> {
            MiuixGroupDetailRouteContent(
                groupId = route.groupId,
                supportingPane = supportingPane,
                onBack = onBack,
                onOpenOrder = { order -> onOpenOrderDetail(order.id) },
                onMarkAllCompletedDone = onBack,
            )
        }
        HomeRoute.Main -> Unit
    }
}

/**
 * 订单组详情：三段式并排 / NavDisplay 入口 / 一镜到底叠加层三处共用同一份实现。
 *
 * 抽出来是为了让「哪一页详情、读哪张表、全部完成后去哪」只有一份：三处各写一遍的话，
 * 数据读取与回调语义迟早漂移（例如叠加层里漏掉 markAllCompleted 的收尾）。
 */
@Composable
private fun MiuixGroupDetailRouteContent(
    groupId: Long,
    supportingPane: Boolean,
    onBack: () -> Unit,
    onOpenOrder: (OrderEntity) -> Unit,
    /** 「全部完成」之后去哪：并排面板与普通入口是出栈，一镜到底叠加层是收回卡片。 */
    onMarkAllCompletedDone: () -> Unit,
) {
    val context = LocalContext.current
    val db = remember { OrderDatabase.getDatabase(context) }
    val group = remember(groupId) {
        runBlocking { db.orderGroupDao().getGroupById(groupId) }
    }
    val orders by db.orderGroupDao()
        .getOrdersByGroupId(groupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val completedCount = orders.count { it.isCompleted }
    val totalCount = orders.size
    if (group != null) {
        com.Badnng.moe.ui.screen.miuix.MiuixGroupDetailScreen(
            group = group,
            orders = orders,
            completedCount = completedCount,
            totalCount = totalCount,
            supportingPane = supportingPane,
            onBack = onBack,
            onOpenOrder = onOpenOrder,
            onMarkOrderCompleted = { order ->
                runBlocking {
                    db.orderDao().markAsCompleted(order.id, System.currentTimeMillis())
                }
            },
            onMarkAllCompleted = {
                runBlocking {
                    val now = System.currentTimeMillis()
                    db.orderGroupDao().markGroupAsCompleted(groupId, now)
                    db.orderGroupDao().markAllOrdersInGroupCompleted(groupId, now)
                }
                onMarkAllCompletedDone()
            }
        )
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
    forceLargeScreen: Boolean = false,
    cardMorph: CardMorphController,
    /** 订单组卡片的展开态：列表侧与叠加层侧必须共用同一份（见 LocalCardMorphExpandedGroups）。 */
    expandedGroupIds: MutableState<Set<Long>>,
    /** 栈顶识别详情的订单 ID，供一镜到底叠加层渲染；非详情页为 null。 */
    cardMorphOrderId: String?,
    /** 主页模态层共用的背景采样层，由外层创建（叠加层要挂在导航之外）。 */
    homeOverlayBackdrop: LayerBackdrop?,
    /** 卡片一镜到底专用的背景采样层；录制常驻挂在内容区上。 */
    cardMorphBackdrop: LayerBackdrop?,
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
    val isPortrait = configuration.screenHeightDp > configuration.screenWidthDp
    // 折叠屏展开后即使竖屏也按大屏处理；普通平板竖屏仍走手机逻辑。
    val isLargeScreen = configuration.screenWidthDp >= MIUIX_LARGE_SCREEN_MIN_WIDTH_DP &&
        (!isPortrait || forceLargeScreen)
    val useNavigationRail = isLargeScreen && !useFloatingNavBar
    val isIosLikeFloatingBar = floatingNavBarStyle == MiuixFloatingNavigationBarStyle.IosLike
    val effectiveNavAlignment = if (isIosLikeFloatingBar && !isLargeScreen) {
        "center"
    } else {
        currentNavAlignment
    }
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
    val blurEnabled = backdrop != null
    val animatedMenuAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (rulesMenuShow) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
        label = "rulesMenuScrimAlpha",
    )
    val sheetProgress = com.Badnng.moe.ui.component.BlurState.progress.floatValue
    // 卡片一镜到底复用同一层背景采样：铺开过程中露出的背景与 BottomSheet 一致。
    // 但遮罩本身只由 BottomSheet / 长按菜单驱动，转场自己画的那份不能再叠一层。
    val homeOverlayProgress = maxOf(sheetProgress, animatedMenuAlpha)
    // BottomSheet / 长按菜单已经占用了同一块模糊遮罩，此时不发起卡片转场，避免双重模糊。
    cardMorph.enabled = homeOverlayProgress <= 0.01f
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
        // 一镜到底转场需要一张「只含主页内容」的背景快照：转场叠加层与底栏都不能被录进去，
        // 否则叠加层自己的 RenderEffect 会引用包含它自身的层，hwui 遍历 RenderNode 时成环爆栈。
        // 卡片转场用独立的 cardMorphBackdrop，录制常驻挂在内层内容 Box 上；
        // 这里这层只服务 BottomSheet / 长按菜单（整层采样，含底栏与侧栏）。
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
                .fillMaxHeight()
                .then(
                    // 一镜到底的背景采样常驻挂载，保证转场第一帧就有可采样内容；
                    // LocalCardMorphSource 为 null（三段式大屏）时不挂，行为与之前一致。
                    if (cardMorphBackdrop != null && LocalCardMorphSource.current != null) {
                        Modifier.layerBackdrop(cardMorphBackdrop)
                    } else {
                        Modifier
                    },
                ),
        ) {
        // Scaffold 内容层（layerBackdrop 只应用到内容，不包含底栏）
        Scaffold(
            modifier = Modifier.fillMaxSize(),
        ) { _ ->
            Box(modifier = if (backdrop != null) Modifier.fillMaxSize().layerBackdrop(backdrop) else Modifier.fillMaxSize()) {
                // 列表那一份：组卡片从这里拿到自己的展开态。
                CompositionLocalProvider(LocalCardMorphExpandedGroups provides expandedGroupIds.value) {
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
                                onNavigateToOrderDetail = onNavigateToOrderDetail,
                                onNavigateToGroupDetail = onNavigateToGroupDetail,
                                // 「添加 + 身份码」悬浮工具栏贴在底部，转场时和底栏一起淡出。
                                bottomFade = cardMorph::homeBottomAlpha,
                                // 组卡片展开态：列表与叠加层共用一份（见 LocalCardMorphExpandedGroups）。
                                expandedGroupIds = expandedGroupIds.value,
                                onExpandedGroupsChange = { expandedGroupIds.value = it },
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
                } // ← 新增：闭合 A2 的 LocalCardMorphExpandedGroups
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
            // 卡片一镜到底铺开时容器底边会扫过底栏，把图标拦腰切一半留在容器外（看着像被裁掉），
            // 所以底栏随转场进度先淡出；退出时整个收回动画期间保持隐藏，动画走完才淡回来
            // （时序由 homeBottomAlpha 内部保证，这里只读绘制期系数，不参与重组）。
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = cardMorph.homeBottomAlpha() },
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
            // 同上（悬浮 / iOS 两种样式共用这一处）：一镜到底铺开时先淡出，避免被容器底边切一半，
            // 退出时等收回动画走完再淡回来。
            modifier = Modifier.graphicsLayer { alpha = cardMorph.homeBottomAlpha() },
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
    supportingPane: Boolean = false,
) {
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