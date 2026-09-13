package com.Badnng.moe.ui.screen.miuix

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 主页卡片 → 识别详情的一镜到底转场。
 *
 * 方案对齐「大狗记（Pronto）」逆向结论（reverse_work/pronto/report/CARD_EXPAND.md）：
 * **共享 GraphicsLayer 移交 + 单个进度 t 驱动的双层合成**，而不是官方 SharedTransitionLayout。
 *
 * ```
 * 列表卡片把自己的那份 Compose 交给叠加层实时渲染
 *   ↓ 点击时把「那张卡的同一份 Compose」连同源矩形交给控制器
 * 叠加层在一个 DrawScope 里按进度 t 逐帧算：容器圆角矩形 / 内容缩放 / 内容 alpha / 卡片面底色
 *   ↓ t 由 spring(dampingRatio = 1f) 驱动（展开 220f / 收起 300f，临界阻尼、速度不回零）
 * 背景整块下沉 scale = 1 - 0.15t，再叠加与 BottomSheet 同参数的模糊
 * ```
 *
 * 卡片内容的连续性不是插值画出来的，而是把列表里那张卡的**同一份 Compose** 原封不动搬到叠加层
 * 继续绘制，所以永远不闪断；插值的只有它外面的容器矩形、圆角与透明度。
 *
 * 转场是**一条条会话**而不是全局单例状态：第一条卡片还在播退出动画时，用户可以点另一个位置的
 * 卡片开始它自己的铺开（并行动画），两条会话各自持有几何、卡片内容与进度，互不干扰。
 *
 * 叠加层必须挂在 `NavDisplay` 之外（与它同级、其后绘制）：放进 `HomeRoute.Main` 入口里会被
 * `NavDisplayEffects.dimAmount` 整层压暗，而详情入口在叠加层可见时是空实现，结果就是灰屏。
 *
 * 只在手机 / 平板竖屏（非三段式大屏）启用：三段式并排由 MiuixHomeScreen 的 supportingPane
 * 分支负责，那里不会提供 [LocalCardMorphSource]，卡片也就不会登记几何信息。
 */
data class CardMorphBounds(
    val rect: Rect,
    val cornerRadius: Dp,
)

/**
 * 一镜到底的 key 前缀：同一套登记表与会话列表要同时容纳「订单卡片」与「订单组卡片」两种源卡片，
 * 于是 key 统一带上类型前缀——详情叠加层按前缀决定画哪一页详情，导航回调按前缀决定入栈哪个路由。
 */
internal const val OrderMorphKeyPrefix = "order:"
internal const val GroupMorphKeyPrefix = "group:"

/** 订单卡片的转场 key：主页列表卡片与订单组详情里的子订单卡片共用同一套。 */
internal fun orderMorphKey(orderId: String): String = "$OrderMorphKeyPrefix$orderId"

/** 订单组卡片的转场 key。 */
internal fun groupMorphKey(groupId: Long): String = "$GroupMorphKeyPrefix$groupId"

/** 卡片登记自身几何信息与卡片内容的入口；只在非三段式模式下提供。 */
class CardMorphSource internal constructor(
    private val onRegister: (String, String?, () -> CardMorphBounds?, () -> (@Composable () -> Unit)?) -> Unit,
    private val onUnregister: (String) -> Unit,
) {
    /**
     * 卡片在布局完成后登记自己。
     * 收回动画进行中点在别的卡片上时要能开出它自己的转场，而事件没法「转发」给背景里的主页
     * （Compose 的命中测试只走最上层命中的那条路径），所以叠加层按坐标查这张表自己发起。
     *
     * [ownerKey] 记录「这张卡片画在哪条会话的详情内容里」：主页列表里的卡片是 null；
     * 订单组详情页（可能正由叠加层绘制）里的子订单卡片，值就是那条组详情会话的 key。
     */
    fun register(
        key: String,
        ownerKey: String?,
        bounds: () -> CardMorphBounds?,
        card: () -> (@Composable () -> Unit)?,
    ) = onRegister(key, ownerKey, bounds, card)

    fun unregister(key: String) = onUnregister(key)
}

val LocalCardMorphSource = compositionLocalOf<CardMorphSource?> { null }

/**
 * 「当前正在绘制详情内容的那条会话」标记，由 [MiuixCardMorphOverlay] 给每条会话的 content 提供。
 *
 * 用途只有一个：让控制器能区分「被点卡片在详情页里面」（订单组详情里的子订单卡片——可以再开
 * 一条会话做并行动画）与「被点卡片在详情页背后」（主页列表卡片——详情铺满时收不到点击，
 * 但程序化调用必须挡住）。见 [CardMorphController.beginExpand]。
 */
internal val LocalCardMorphOwnerKey = compositionLocalOf<String?> { null }

/**
 * 「哪些订单组卡片当前处于展开态」——由主页提供，**列表里的卡片和叠加层里的镜像卡片读同一份**。
 *
 * 为什么必须共享：叠加层里的卡片不是列表那张卡的同一个实例，而是把注册进来的
 * `session.card` lambda **重新组合一遍**（见 [CardMorphLayer]）。而
 * `MiuixOrderGroupCard` 的展开态原本是它自己的 `rememberSaveable`，两个实例互不可见 ⇒
 * 叠加层那份永远画**收起态**。转场期间容器矩形严格包含源卡片矩形，于是收起态卡片会被
 * 一点点露出来，用户看到的是「卡片只剩几行、下半身空白」，返回静止后这份收起态副本还会
 * 叠在列表那张展开卡之上（真机 N 轮：两张卡 bounds 完全相同、副本在上层）。
 *
 * 默认空集：没有提供者的场合（详情页里的卡片、大屏三段式）一律按「未展开」处理，
 * 与改动前的行为一致。
 */
internal val LocalCardMorphExpandedGroups = compositionLocalOf<Set<Long>> { emptySet() }

/** 与 miuix `Card` 的默认圆角保持一致（转场起始帧要和真实卡片完全重合）。 */
private val CardMorphCornerRadius = 16.dp

/**
 * 把这张卡的几何信息与「它长什么样」登记给一镜到底控制器。
 *
 * 登记而不是「点击时移交」的原因：收回动画进行中点到**另一张**卡片时要能开出它自己的转场，
 * 而 Compose 的命中测试只走最上层命中的那条路径，叠加层上的撤回点击层没法把事件转发给背景里的
 * 主页；所以由叠加层按坐标查这张表，自己发起并行动画（大狗记逆向里的 CardLayerRegistry 同款思路）。
 *
 * 主页订单卡片、主页订单组卡片、订单组详情页里的子订单卡片都用它，[morphKey] 走
 * [orderMorphKey] / [groupMorphKey]；`LocalCardMorphSource` 为 null（三段式大屏）时直接透传内容。
 */
@Composable
internal fun CardMorphCard(morphKey: String, content: @Composable () -> Unit) {
    val source = LocalCardMorphSource.current
    if (source == null) {
        content()
        return
    }
    // 这张卡片画在哪条会话的详情内容里（主页列表里 = null，组详情页 = 那条组会话的 key）。
    val ownerKey = LocalCardMorphOwnerKey.current
    val boundsState = remember { mutableStateOf<CardMorphBounds?>(null) }
    val latestContent = rememberUpdatedState(content)
    Box(
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                // 这里刻意不用 coordinates.boundsInRoot()：它会把矩形裁进根布局范围，
                // 卡片贴在屏幕底部（下半截在屏幕外）时量到的高度会短一截（本机实测短 50~90px）。
                // 叠加层里的「源卡片层」按这个尺寸摆盒子，卡片就被塞进一个更矮的盒子里，
                // 卡内带弹性的那一行会被压扁——就是「小白条下方按钮被挤压变形」。
                // 改用 positionInRoot + size，拿到未裁剪的真实矩形。
                val position = coordinates.positionInRoot()
                val size = coordinates.size
                val next = CardMorphBounds(
                    rect = Rect(
                        left = position.x,
                        top = position.y,
                        right = position.x + size.width,
                        bottom = position.y + size.height,
                    ),
                    cornerRadius = CardMorphCornerRadius,
                )
                if (boundsState.value != next) boundsState.value = next
            },
    ) {
        content()
    }
    DisposableEffect(source, morphKey, ownerKey) {
        source.register(
            key = morphKey,
            ownerKey = ownerKey,
            bounds = { boundsState.value },
            card = { latestContent.value },
        )
        onDispose { source.unregister(morphKey) }
    }
}

/** 展开（入栈）：临界阻尼，刚度按体感放慢（逆向原值 380，用户反馈偏快）。 */
private val CardMorphExpandSpring = spring<Float>(
    dampingRatio = 1.0f,
    stiffness = 220f,
    visibilityThreshold = 0.001f,
)

/** 收起（出栈）：刚度更高，回程更快（与放慢后的展开保持同一比例）。 */
private val CardMorphCollapseSpring = spring<Float>(
    dampingRatio = 1.0f,
    stiffness = 300f,
    visibilityThreshold = 0.001f,
)

/**
 * 内容淡入区间：t 到 [CONTENT_FADE_END] 时完全显影。
 *
 * 窗口从 [0.10, 0.35] 收到 [0.15, 0.45]：这段窗口在 `CardMorphExpandSpring`（stiffness 220）下
 * 只有约 8 帧@120Hz / 4 帧@60Hz，源卡片与详情页两份完全不同的内容在这几帧里互相穿透（双影）。
 * 起点推到 0.15 让「卡片先稳住」再开始换页，终点放到 0.45 让换页更平缓。
 * 注意 [SOURCE_LAYER_EXIT] = 0.4 与 [RADIUS_RAMP_END] = 0.5 的既有语义不变。
 */
private const val CONTENT_FADE_START = 0.15f
private const val CONTENT_FADE_END = 0.45f

/** 源卡片层退场点：t 超过它之后内容已经完全接管。 */
private const val SOURCE_LAYER_EXIT = 0.4f

/**
 * 源卡片层的**最大绘制进度**：t 到达它之后连同休息态一起不再组合源卡片层。
 *
 * 阈值仍然贴着 1（数值与原来的 0.999f 相同），不缩小它：收回动画走的是同一段进度倒着走，
 * `t` 从 1 降回 0.999 这一段必须保持「能挂回源卡片层」的路径畅通（退回落点 + 撤回点击目标）。
 * 这处改动的实质只是把「休息态不画源卡片」写成显式常量，免得后面有人把它当成可以顺手放宽的开关。
 */
private const val SOURCE_LAYER_MAX_PROGRESS = 0.999f

/** 圆角长到屏幕圆角所用的进度：t=0 是卡片圆角，t≥它之后是屏幕圆角。 */
private const val RADIUS_RAMP_END = 0.5f

/**
 * 屏幕圆角收回成直角（0）所用的起点进度：t≤它保持屏幕圆角，t=1 时圆角恰好为 0。
 *
 * 末段必须收到 0 的原因：动画停下来的那一帧就是详情页的**静止态**，而这一帧依旧由叠加层绘制
 * （会话留在列表里、NavDisplay 的详情入口被 overlayVisible 守卫留空）。真实详情页是**直角满屏**的
 * ——它不会被裁到屏幕圆角，四角由面板自己切；容器若停在屏幕圆角上，四角就会露出圆角外那四块背景
 * （真机静止帧实测：四角 = 背景底色 60，圆弧半径 133 ↔ 系统上报圆角 134）。
 * 收到 0 之后，叠加层画出来就是一整块直角满屏的页面，与真实详情页逐像素一致，四角不再露底。
 * 收 0 的这一段半径始终 ≤ 屏幕圆角，多画出来的部分全落在系统/面板圆角之内，设备上看不出形状变化。
 */
private const val RADIUS_SQUARE_START = 0.8f

/**
 * 主页**底部悬浮元素**淡出所用的进度：t=0 完全显示，t≥它之后完全隐去。
 *
 * 覆盖两处：底栏（导航栏）与「添加 + 身份码」那个悬浮工具栏。两者都贴在屏幕底部，
 * 卡片容器展开时底边会从中扫过，把图标拦腰切一半留在容器外。
 * 顶栏不参与淡出（用户 2026-09-12 明确：要的是底栏，不是顶栏——顶栏由容器直接盖过去）。
 * 取 0.6 是掐着容器底边到达它们的时刻算的：容器底边扫到 y≈2400px 处约在 t=0.80，
 * 所以 0.6 之前淡完，既不会被边缘切到，又能让淡出占到整段动画（展开约 350ms）的前 200ms
 * ——之前取 0.3 时只有 100ms，快到肉眼看不出是「渐隐」，更像直接消失。
 */
private const val HOME_BOTTOM_FADE_END = 0.6f

/**
 * 收回动画播完之后，底部这些元素重新显现所用的时间。
 * 时序是「退出动画走完 → 才出现」，所以这段淡入发生在转场会话已经结束之后，不挂在进度上；
 * 否则收回时进度一掉回 0.6 以下，它们就会在退出动画中途冒出来。
 */
private const val HOME_BOTTOM_REVEAL_MILLIS = 200

/** 内容相对容器的上浮距离（逆向下沉动效里的内容位移）。 */
private val ContentDrift = 12.dp

/**
 * 系统没上报屏幕圆角时的兜底值。
 * 只在「显示器真的没有圆角」或 insets 尚未就绪时生效；正常机型都能从 WindowInsets 读到真实值
 * （本机 1200x2670@480 是 134px = 44.7dp）。
 */
private val FallbackScreenCornerRadius = 40.dp

/**
 * 物理屏幕的圆角半径（px），直接读系统上报值；拿不到返回 0，由调用方决定兜底。
 */
@Composable
private fun rememberScreenCornerRadiusPx(): Float {
    val view = LocalView.current
    var radius by remember(view) { mutableStateOf(0f) }
    SideEffect {
        if (radius <= 0f) {
            val reported = runCatching {
                view.rootWindowInsets
                    ?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)
                    ?.radius
            }.getOrNull() ?: 0
            if (reported > 0) radius = reported.toFloat()
        }
    }
    return radius
}

/**
 * 一次「卡片 ↔ 详情」的形变会话。
 *
 * 之所以是一条条会话而不是全局单例状态：两条会话可以同时在跑（一条在收回、一条在铺开），
 * 各自持有几何、卡片内容与进度，互不干扰。
 */
class CardMorphSession internal constructor(
    /** 带前缀的转场 key（`order:` / `group:`）：既是叠加层里详情内容的 key，也是「同一条卡片再次点击 = 撤回」的判据。 */
    val key: String,
    /** 被点卡片的几何信息；进度为 0 时叠加层与之完全重合。 */
    val bounds: CardMorphBounds,
    /**
     * 「卡片快照」——就是列表里那张卡的同一份 Compose。叠加层直接实时渲染它，
     * 所以内容是原样的文字与图标。
     * 不走「录制绘图层 / 定格位图」是因为：miuix 的 backdrop 录制拿不到 Miuix `Card`
     * 内部（squircleSurface 走的是嵌套层）的文字与图标，定格出来只剩一张白底卡片。
     */
    val card: @Composable () -> Unit,
    internal val animation: Animatable<Float, *>,
) {
    /** 0f=卡片状态，1f=全屏详情；动画与手势都写它，绘制只读它。 */
    internal val progressState = mutableStateOf(0f)

    /** 当前进度；只在布局 / 绘制阶段读取。 */
    val progress: Float get() = progressState.value

    /**
     * 正在播放收回动画。
     * 此时点击可以「撤回」：从**当前进度**继续铺回详情，而不是从头重播——
     * 也就是 Pronto 那种可打断续播（打断动画后进度和速度都接着走）。
     */
    var collapsing by mutableStateOf(false)
        internal set

    internal var settleJob: Job? = null
    internal var popRequested = false
    internal var pushRequested = false
}

/** 可见卡片在控制器里的登记项：几何 + 「这张卡长什么样」+「它画在哪条会话的内容里」。 */
private class CardMorphRegistration(
    val ownerKey: String?,
    val bounds: () -> CardMorphBounds?,
    val card: () -> (@Composable () -> Unit)?,
)

/** 形变会话的持有者；由 [rememberCardMorphController] 创建。 */
class CardMorphController internal constructor(
    private val scope: CoroutineScope,
) {
    /** 进行中的会话；可能同时两条（第一条在收回、第二条在铺开 = 并行动画）。 */
    val sessions = mutableStateListOf<CardMorphSession>()

    /** 可见卡片登记表，供收回途中按坐标找回被点的卡片。 */
    private val visibleCards = mutableMapOf<String, CardMorphRegistration>()

    /**
     * 是否允许发起转场。BottomSheet / 长按菜单已经占用了同一层背景采样与遮罩，
     * 此时再叠加一次就是双重模糊，因此直接不发起。
     */
    var enabled: Boolean = true

    /** 收回动画结束后真正出栈。 */
    var onRequestPop: (() -> Unit)? = null

    /**
     * 铺开动画结束时才把详情入口入栈（参数是这条会话自己的订单 id）。
     * 转场全程不入栈：详情一旦入栈，NavDisplay 会销毁主页入口，背景采样层的录制节点随之 detach，
     * 模糊就采样不到东西（整屏只剩压暗的纯灰）；而且换栈会让返回回调重新注册、抢走正在进行的返回手势。
     */
    var onRequestPush: ((String) -> Unit)? = null

    /** 叠加层是否参与绘制：只要还有会话在跑就参与。 */
    val overlayVisible: Boolean get() = sessions.isNotEmpty()

    /**
     * 底部悬浮元素的「重现」系数：常态 1，收回动画走完后从 0 淡到 1。
     * 见 [homeBottomAlpha]，它只在没有任何会话（转场已彻底结束）时被读到。
     */
    private val bottomReveal = Animatable(1f)

    /** 卡片布局时登记自己。 */
    fun registerCard(
        key: String,
        ownerKey: String?,
        bounds: () -> CardMorphBounds?,
        card: () -> (@Composable () -> Unit)?,
    ) {
        visibleCards[key] = CardMorphRegistration(ownerKey, bounds, card)
    }

    fun unregisterCard(key: String) {
        visibleCards.remove(key)
    }

    /**
     * 布局切到大屏双栏（或有别的布局接管详情展示）时调用，清掉残留会话。
     * 双栏没有一镜到底叠加层，会话没人收尾会永久驻留：之后 [beginExpand] 命中残留会话
     * 直接 return（卡片再也点不开），[homeBottomAlpha] 也会被它按在 0（底栏不再淡回来）。
     */
    fun abandonSessions() {
        sessions.forEach { it.settleJob?.cancel() }
        sessions.clear()
        scope.launch { bottomReveal.snapTo(1f) }
    }

    /** 这个坐标上是哪张可见卡片（收回途中点容器外 = 开它自己的转场）。 */
    fun cardAt(position: Offset): String? =
        visibleCards.entries.lastOrNull { (_, item) -> item.bounds()?.rect?.contains(position) == true }?.key

    /** 这次点击能不能走一镜到底（卡片已登记、且没有别的模态层占用背景采样）。 */
    fun canMorph(key: String): Boolean = enabled && visibleCards.containsKey(key)

    /**
     * 主页**底部悬浮元素**的淡出系数：1 = 正常显示，0 = 完全隐去。
     * 用在两处：底栏（导航栏）、「添加 + 身份码」的悬浮工具栏。
     *
     * 卡片是从列表中间长到整屏的，容器底边展开时会从这两处扫过，把它们拦腰切一半留在容器外面，
     * 看起来就是「元素被裁掉了」。让它们先淡出，比让容器边缘去切一刀干净得多。
     * 顶栏不参与淡出（用户明确只要底部的这些）：顶栏由容器直接盖过去。
     *
     * 时序：
     * - 打开：随进度淡出，t ≥ [HOME_BOTTOM_FADE_END] 时完全隐去；
     * - 退出：整段收回动画期间保持隐藏，等动画真的走完（会话结束、叠加层已撤掉）才由
     *   [bottomReveal] 淡回来。所以这里不能按进度反算——否则收回时进度一掉回 0.6 以下，
     *   它们会在退出动画中途就冒出来。
     * 多条会话同时在跑时取最靠前的那条，与背景模糊的进度口径保持一致。
     */
    fun homeBottomAlpha(): Float {
        val front = sessions.maxByOrNull { it.progress } ?: return bottomReveal.value
        if (front.collapsing) return 0f
        return bottomReveal.value * (1f - (front.progress / HOME_BOTTOM_FADE_END).coerceIn(0f, 1f))
    }

    /**
     * 进入详情：从卡片状态铺开到全屏；铺满后才真正入栈。
     *
     * 弹簧要等叠加层真的画出第一帧才起步：叠加层首帧需要现场组合整页详情（其中还有一次
     * 数据库读取），这一帧大概率超时。若此时弹簧已经在跑，第一帧画出来时进度已经跳到中途，
     * 观感就是「先闪一下主页，然后突然开始铺开」。t=0 那一帧与卡片、主页完全重合，看不见，
     * 用它来热身最安全；已经有别的会话在跑时叠加层早就在位，不用再热身。
     */
    fun beginExpand(key: String) {
        if (!enabled) return
        // 同一条卡片再次点击 = 撤回（收回没走完时点回自己那张卡）。
        sessions.firstOrNull { it.key == key }?.let { existing ->
            if (existing.collapsing) reverseCollapse(existing)
            return
        }
        // 已经有铺满静止的那条时不再开新的：那一刻详情页盖在最上面，用户看不到别的卡片。
        // （被它挡住的主页卡片收不到点击，走不到这里；这里是防一手程序化调用。）
        // 唯一例外是「被点的卡片就登记在那条静止会话自己的详情内容里」——订单组详情里的子订单
        // 卡片就是这种情况：此时第二条会话叠在它上面铺开，是设计允许的并行动画。
        val resting = restingSession()
        if (resting != null && visibleCards[key]?.ownerKey != resting.key) return
        val registration = visibleCards[key] ?: return
        val bounds = registration.bounds() ?: return
        val card = registration.card() ?: return
        val session = CardMorphSession(key, bounds, card, Animatable(0f))
        sessions.add(session)
        session.settleJob = scope.launch {
            // 无条件等两帧：每条会话的详情内容都是**按转场 key 新组合**的（里面有 runBlocking 的
            // 数据库读取），首帧一定超时；只有等它真的画出来（t=0 与卡片、主页完全重合，看不见），
            // 弹簧才起步。之前只在「叠加层第一次出现」时热身，收回刚结束、会话还没被移除的那一小段
            // 里再次点开就会少了这两帧，进度一上来就跳到中途，手感上就是「顿一下」。
            // 组详情里的子订单卡片尤其如此：那两帧同样用来等新的一页详情组合出来。
            withFrameNanos { }
            withFrameNanos { }
            runSettle(session, 1f)
        }
    }

    /** 铺满且静止的那条（详情态）。 */
    private fun restingSession(): CardMorphSession? =
        sessions.firstOrNull { !it.collapsing && it.progressState.value >= 0.999f }

    /**
     * 返回手势 / 返回键作用的那条会话：**画在最上面的那条**（叠加层按列表顺序绘制，最后一条压在最上）。
     *
     * 不能取「进度最大的那条」：订单组详情里再点开一张子订单卡片时，两条会话都停在 t=1，
     * 进度无法区分上下，返回就会收掉底下那条（栈顶弹出的却是上面那条的路由）。
     */
    private fun gestureTarget(): CardMorphSession? =
        sessions.lastOrNull { !it.collapsing } ?: sessions.lastOrNull()

    /**
     * **本次返回手势**锁定的那条会话：`Started → Progressed → Cancelled/Pressed` 全程只认它。
     *
     * 不能每个回调各自调一次 [gestureTarget]（它的过滤条件是 `!it.collapsing`）：一次返回键在
     * 预测性返回的设备上是 `Started(progress=0) → Progressed(0) → Pressed` 这一串，
     * [dragProgress] 会先把最上面那条置 `collapsing=true`，[beginCollapse] 再调 [gestureTarget]
     * 就把它滤掉、拿到**下面那条**会话——多会话时表现为「按一次返回键把两层详情一起出栈」，
     * 画面仍停在最上面那条会话的详情上（看着像没反应），再按一次就直接退到桌面。
     */
    private var gestureSession: CardMorphSession? = null

    /**
     * 本次返回手势是否已经开跑（收到过 `Started`），用来丢弃手势外的进度回调。
     *
     * 实测（同一台机器、同一个返回键）：`Started(0.0) → Progressed(0.0) → Cancelled → Progressed(0.0)`。
     * 取消之后系统仍会补一发 `Progressed`，此时再让 [dragProgress] 去 [gestureTarget] 重新挑一条，
     * 挑到的就是**下面那条**会话（上面那条刚被取消、还停在 t=1 但已 `collapsing`），
     * 于是它把组详情也出栈——和最初那个 bug 同源：谁都不该在手势中途重新挑目标。
     */
    private var gestureRunning = false

    /**
     * 预测性返回手势开始（`handleOnBackStarted`）：开一轮新手势。
     *
     * 只记状态、先不动进度：这一发回调的 progress 恒为 0（手指还没动），真正的接管发生在
     * 第一次 [dragProgress]。
     */
    fun beginGesture() {
        gestureRunning = true
        gestureSession = null
    }

    /**
     * 当前手势锁定的会话；它已经播完收回、从列表里退场时视为没有锁定。
     *
     * 不校验「还在列表里」会拿到一条已经 removed 的会话：它的 `popRequested` 仍是 true，
     * `detachDetail` 于是什么都不做——返回键按下去既不收卡片也不出栈，直接卡死。
     */
    private fun pinnedSession(): CardMorphSession? {
        val pinned = gestureSession ?: return null
        if (pinned !in sessions) {
            gestureSession = null
            gestureRunning = false
            return null
        }
        return pinned
    }

    /**
     * 收回动画没走完时点击：**撤回**，从当前进度继续铺回详情。
     * 进度与速度都沿用当前这一帧的（`Animatable` 不会被打断重置），所以看起来是同一个
     * 动画掉头继续，而不是重新播一遍。
     */
    fun reverseCollapse(session: CardMorphSession? = null) {
        val target = session?.takeIf { it.collapsing }
            ?: gestureTarget()?.takeIf { it.collapsing }
            ?: return
        // 收回开始时详情入口已经出栈，铺回满屏时由收尾重新入栈（onRequestPush 自带防重）。
        target.pushRequested = false
        target.popRequested = false
        settle(target, 1f)
    }

    /**
     * 离开详情：先让叠加层接管（出栈），再收回卡片。
     * 真正的详情页是普通导航入口，收回时把它出栈，主页入口回来继续供背景采样与卡片登记；
     * 出栈那一帧叠加层正好盖满整屏，动作本身看不见。
     */
    fun beginCollapse() {
        // 手势里已经钉住目标就沿用它（Started 定下来的那条），否则自己挑画在最上面的那条。
        // 这里**不能**清掉锁定：一次返回键在预测性返回设备上是 Started → Pressed 两次回调，
        // 清掉的话 Pressed 会重新挑一条（collapsing 的已被 [gestureTarget] 滤掉）→ 收错会话。
        gestureRunning = false
        val session = pinnedSession() ?: gestureTarget()
        if (session == null || session.progressState.value <= 0f) {
            gestureSession = null
            onRequestPop?.invoke()
            return
        }
        detachDetail(session)
        settle(session, 0f)
    }

    /** 叠加层接管详情：把真正的详情入口出栈。 */
    private fun detachDetail(session: CardMorphSession) {
        if (!session.popRequested) {
            session.popRequested = true
            onRequestPop?.invoke()
        }
    }

    /**
     * 预测性返回手势：手指位置直接驱动进度。
     * [dismissed] 是系统给的 BackEvent.progress——0 表示手势刚起，1 表示已经拉过提交阈值、
     * 上一页应该完整露出来。所以它和转场进度 t 是**反向**的：手势拉满时 t 必须回到 0
     * （主页 + 卡片），写反了就会出现「拉到 100% 还停在识别详情」。
     */
    fun dragProgress(dismissed: Float) {
        // 手势已经被取消/收尾，却还收到进度回调（见 [gestureRunning]）：直接丢弃，
        // 否则会按 [gestureTarget] 重新挑一条目标会话，把下面那层详情也一起出栈。
        if (!gestureRunning) return
        // 目标在**第一次**回调时钉住，之后整个手势只认它：后续回调再按 [gestureTarget] 重算，
        // 会因为下面刚写的 collapsing=true 而挑到另一条会话（见 [gestureSession]）。
        val session = pinnedSession() ?: (gestureTarget()?.also { gestureSession = it }) ?: return
        // **手势期间绝不出栈**：detachDetail 会把真正的详情入口弹掉，导航层随即开始播自己的
        // pop 转场，系统判定这个返回手势已失效而补一发 onBackCancelled —— 于是
        // handleOnBackPressed 永远不来、beginCollapse 不执行，表现为「静止态侧滑完全没反应」。
        // 出栈改由真的提交手势时的 [beginCollapse] 做（那时叠加层已经盖满整屏，动作看不见）。
        //
        // 被拉下去那一帧的画面不需要真的出栈：叠加层本来就盖在最上面，
        // 缩下去的过程中背后露出来的正是自己的 CardMorphBackdrop，不是未模糊也未压暗的主页。
        //
        // collapsing 仍然表达「这次返回已经在进行中」：系统在手指刚触到边缘、
        // 还没产生位移时给的 progress 就是 0，所以用 `> 0f` 而不是恒 true，
        // 避免一次「按下又松开、根本没滑动」的手势把底栏提前藏掉（那种情况本就该回到原状）。
        session.collapsing = dismissed > 0f
        session.settleJob?.cancel()
        session.settleJob = null
        // 手势期间只写同步状态，不碰 Animatable，避免和 settle 的动画互相取消。
        session.progressState.value = (1f - dismissed).coerceIn(0f, 1f)
    }

    /**
     * 预测性返回手势取消：弹回全屏详情。
     *
     * 出栈现在只发生在 [beginCollapse]，所以取消时栈里那条详情入口本来就在，
     * 只需把两个标记复位、把进度弹回 1。之所以仍然复位 `pushRequested`，
     * 是为了防「`cancelGesture` 与 `beginCollapse` 同帧交错」时漏掉一次入栈。
     */
    fun cancelGesture() {
        if (!gestureRunning) return
        gestureRunning = false
        val session = pinnedSession() ?: return
        gestureSession = null
        session.pushRequested = false
        session.popRequested = false
        settle(session, 1f)
    }

    /**
     * 收束到目标进度。
     *
     * 起始值以会话的同步进度为准：手势拖拽只改同步状态，Animatable 可能与它不一致，
     * 此时先 snapTo 对齐。只有在真的不一致时才对齐，是为了保住「展开中途改主意」的帧级
     * 速度——Pronto 的收起同样是把当前速度原样带进新弹簧，而不是从头再跑。
     */
    private fun settle(session: CardMorphSession, target: Float) {
        // collapsing 只表示「这条会话正在播收回动画」：任何新的收束或拖拽都会先把它清掉，
        // 否则动画被打断后撤回点击层会留在屏幕上抢事件。
        session.collapsing = target <= 0f
        session.settleJob?.cancel()
        session.settleJob = scope.launch { runSettle(session, target) }
    }

    private suspend fun runSettle(session: CardMorphSession, target: Float) {
        val current = session.progressState.value.coerceIn(0f, 1f)
        if (session.animation.value != current) session.animation.snapTo(current)
        session.animation.animateTo(
            targetValue = target,
            animationSpec = if (target >= 1f) CardMorphExpandSpring else CardMorphCollapseSpring,
        ) {
            session.progressState.value = value
        }
        session.progressState.value = target
        if (target <= 0f) {
            // 收回结束：这条会话退场，叠加层随之撤掉它的容器与撤回点击层，主页恢复常态。
            session.collapsing = false
            sessions.remove(session)
            if (gestureSession === session) gestureSession = null
            if (sessions.isEmpty()) {
                // 收回动画真的播完、叠加层也撤掉之后，底部这些元素才淡回来。
                // 这里是唯一让它们重新显现的地方：先 snapTo(0) 保证衔接（会话期间它恒为 0），
                // 再淡到 1，观感是「退出动画结束 → 它们浮现」，而不是动画中途往回涨。
                bottomReveal.snapTo(0f)
                bottomReveal.animateTo(1f, animationSpec = tween(HOME_BOTTOM_REVEAL_MILLIS))
            }
        } else if (!session.pushRequested) {
            // 铺满（或手势取消后弹回全屏）：入栈真正的详情入口。
            // 入口自己不绘制（overlayVisible 期间留空），页面仍由叠加层渲染，避免画两份。
            // 会话留在列表里是关键：详情内容的组合状态得以保留，返回手势第一帧就是现成的，
            // 不必再花一帧重新组合整页——那一帧正是「先闪一下识别详情，然后突然回主页」的来源。
            session.pushRequested = true
            session.popRequested = false
            onRequestPush?.invoke(session.key)
        }
    }
}

/** 创建主页唯一的转场控制器（会话可有多条，控制器只有一个）。 */
@Composable
fun rememberCardMorphController(): CardMorphController {
    val scope = rememberCoroutineScope()
    return remember(scope) { CardMorphController(scope) }
}

/**
 * 预测性返回 / 返回键处理：手势期间直接驱动转场进度，动画收完才真正出栈。
 *
 * [LocalCardMorphSource] 为空（三段式大屏）时整个处理链都不挂载，保证大屏行为不变。
 */
@Composable
fun MiuixCardMorphDismissBackHandler(
    controller: CardMorphController,
    enabled: Boolean,
    predictiveBackEnabled: Boolean,
    morphAvailable: Boolean,
    registrationKey: Any? = null,
) {
    // 不能用 LocalCardMorphSource 判断：它由主页入口提供，而详情入栈后入口已被 NavDisplay 销毁，
    // CompositionLocal 会变成 null，手势就永远接管不到。
    if (!morphAvailable) return

    // 这里不用 Compose 的 PredictiveBackHandler：它和 NavDisplay 内部的返回回调一样在组合期注册，
    // 而系统按「后注册者优先」派发，NavDisplay 会随返回栈变化重新注册，于是手势被它抢走
    // （表现为拖拽全程没有任何反应，松手也只是走它的 onBack）。
    // 改成自己在 LaunchedEffect 里挂载：组合与副作用都跑完之后才注册，稳定排在它后面；
    // registrationKey 变化（栈深改变）时重新挂载，继续保住这个顺序。
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher ?: return
    val handleBack = rememberUpdatedState { controller.beginCollapse() }
    val callback = remember(dispatcher, predictiveBackEnabled) {
        if (predictiveBackEnabled) {
            object : OnBackPressedCallback(false) {
                override fun handleOnBackStarted(backEvent: BackEventCompat) {
                    controller.beginGesture()
                }

                override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                    controller.dragProgress(backEvent.progress)
                }

                override fun handleOnBackCancelled() {
                    controller.cancelGesture()
                }

                override fun handleOnBackPressed() {
                    handleBack.value()
                }
            }
        } else {
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    handleBack.value()
                }
            }
        }
    }
    LaunchedEffect(dispatcher, callback, enabled, registrationKey) {
        callback.isEnabled = enabled
        if (enabled) {
            callback.remove()
            dispatcher.addCallback(callback)
        }
    }
    DisposableEffect(dispatcher, callback) {
        onDispose { callback.remove() }
    }
}

/**
 * 单帧形变几何：位置 / 尺寸 / 内容缩放 / 内容透明度都由进度 t 决定。
 *
 * [sinkScale] 是「这条会话被上层会话盖住多少」带来的下沉量（`1 - 0.15 * cover`），
 * 与 [CardMorphBackdrop] 背景下沉用的是同一个表达式：被盖住的那层详情页要整页绕**屏幕中心**
 * 缩到 85%（矩形、圆角、内容一起缩），否则它只是一张盖着毛玻璃的整屏大图，
 * 和主页卡片铺开时「背景下沉」的观感对不上。
 * 缩放只作用在容器几何上，**源卡片层不受它影响**（源卡片是收回途中的撤回点击目标）。
 *
 * 这里**没有**、也不要再加「展开态卡片高出屏幕」的补偿项：
 * 源卡片的盒子本来就布局在容器原点（`:size(cardWidthDp, cardHeightDp)`），
 * 「把它摆回原位」所需的偏移恰好就是 [curY]；再补一次等于把静止态锚点整体平移。
 * 详见 build/morphfix/issue3-plan-fix.md §1。
 */
private class MorphFrame(
    val curX: Float,
    val curY: Float,
    val curW: Float,
    val curH: Float,
    val radius: Float,
    val contentScale: Float,
    val contentAlpha: Float,
    val sinkScale: Float,
)

/**
 * [sink] 为下层会话的下沉系数（栈顶会话恒传 1f = 不下沉）。
 * 矩形与圆角一起按 [sink] 做绕屏幕中心的相似变换，因此容器路径、裁剪路径、
 * 内容层的可见边界三者始终重合（`clip(w·s, r·s) ≡ scale(s) ∘ clip(w, r)`），
 * 不会在圆角处露出容器的 `destinationColor` 底色。
 */
private fun morphFrame(
    from: Rect,
    fullWidth: Float,
    fullHeight: Float,
    sourceRadius: Float,
    targetRadius: Float,
    t: Float,
    sink: Float = 1f,
): MorphFrame {
    val w0 = from.width
    val h0 = from.height
    // 下沉：绕屏幕中心把容器整体缩到 sink（屏幕上与被盖住的那层完全同构）。
    val sinkScale = sink.coerceIn(0.05f, 1f)
    val cx = fullWidth / 2f
    val cy = fullHeight / 2f
    val rawW = w0 + (fullWidth - w0) * t
    val rawH = h0 + (fullHeight - h0) * t
    val curW = rawW * sinkScale
    val curH = rawH * sinkScale
    // 内容缩放**只按卡片自己的宽度比**（w0 / fullWidth），铺开过程中回到 1。
    //
    // 不能再取 max(w0/fullWidth, h0/fullHeight)：那是「按卡片更窄的那一边起步」，
    // 而订单组卡片一旦**就地展开**（内含若干子订单行），h0 可以远大于整屏，
    // max() 就翻到高度分支——详情内容与源卡片被按「先缩到卡片高度贴合视口高度、
    // 再随容器长回去」处理，卡片横向只剩 w0 * (fullHeight / h0)，
    // 于是展开态与收起态走了两套完全不同的几何语义，
    // 观感就是「卡片先缩一下再一边糊一边涨」。
    // 只按宽度比则恒定是「卡片正文 1:1 → 全屏」这一种语义，与卡片高度无关。
    //
    // 与 sink 无关：下沉量由 [MorphFrame.sinkScale] 单独承载，在这里乘进去会被算两遍。
    val startScale = if (fullWidth > 0f) (w0 / fullWidth).coerceIn(0f, 1f) else 1f
    return MorphFrame(
        curX = cx + (from.left * (1f - t) - cx) * sinkScale,
        curY = cy + (from.top * (1f - t) - cy) * sinkScale,
        curW = curW,
        curH = curH,
        // 圆角：卡片圆角 →（前一半长完）屏幕圆角 →（末段收回 0）直角，整体再随下沉量收缩。
        // 中段维持屏幕圆角：容器四角与屏幕四角重合，观感是「窗口本身在长大」，
        // 也不会像线性收到 0 那样提前变成方角。
        // 末段必须收到 0：停下来的那一帧仍由叠加层绘制（原因见 [RADIUS_SQUARE_START]），
        // 容器若是圆角，四角就会露出圆角外的背景；真实详情页是直角满屏，必须与它完全一致。
        // 再乘 [sinkScale]：容器被上层盖住时圆角要跟着一起缩，否则圆角处会透出下层底色（直角硬边）。
        radius = (sourceRadius + (targetRadius - sourceRadius) * (t / RADIUS_RAMP_END).coerceAtMost(1f)) *
            (1f - ((t - RADIUS_SQUARE_START) / (1f - RADIUS_SQUARE_START)).coerceIn(0f, 1f)) *
            sinkScale,
        contentScale = startScale + (1f - startScale) * t,
        contentAlpha = ((t - CONTENT_FADE_START) / (CONTENT_FADE_END - CONTENT_FADE_START)).coerceIn(0f, 1f),
        sinkScale = sinkScale,
    )
}

/**
 * 卡片 → 详情的叠加层。
 *
 * 结构（同一个 Box 内，自下而上）：
 * 1. 背景：整块下沉（scale = 1 - 0.15t）后取样，叠加与 BottomSheet 完全一致的模糊与压暗；
 * 2. 每条会话一个形变容器：一个 DrawScope 里逐帧合成「圆角容器 + 内容 + 源卡片」，全部在裁剪区内；
 * 3. 收回中的会话在最上面盖一层整屏点击层，负责「撤回」与「点别的卡片开并行动画」。
 *
 * 内容本身是普通 Compose 布局（整屏测一次），只靠 graphicsLayer 做缩放 / 位移 / 淡入，
 * 因此铺开过程只失效绘制，不会反复测量详情页。
 *
 * [surfaceColor] 是卡片底色（t=0 的容器底色），[destinationColor] 是详情页底色（t=1）。
 * [content] 按会话的转场 key 实例化详情内容——两条会话同时在场时各画各的。
 */
@Composable
fun MiuixCardMorphOverlay(
    controller: CardMorphController,
    backdrop: LayerBackdrop?,
    surfaceColor: Color,
    destinationColor: Color,
    content: @Composable (key: String) -> Unit,
) {
    if (controller.sessions.isEmpty()) return
    val density = LocalDensity.current
    // 容器圆角从卡片圆角长到**屏幕圆角**，再在末段收回 0：铺开过程四角是圆的（与屏幕四角重合），
    // 停下来的那一帧则是**直角满屏**，与真实详情页（直角铺满）逐像素一致，四角不会留缝。
    val screenRadiusPx = rememberScreenCornerRadiusPx()
    val targetRadiusPx = if (screenRadiusPx > 0f) screenRadiusPx else with(density) { FallbackScreenCornerRadius.toPx() }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { overlaySize = it },
    ) {
        // ① 背景：下沉 + 模糊 + 压暗。进度取「最靠前的那条会话」（Pronto §3.3 的互补进度），
        // 并行动画时背景跟着最展开的那条走，不会两条互相拉扯。
        CardMorphBackdrop(backdrop = backdrop, surfaceColor = destinationColor) {
            controller.sessions.maxOfOrNull { it.progress } ?: 0f
        }

        // ② 每条会话一个形变容器：先入场的在下，最后点开的压在最上面。
        val topSession = controller.sessions.lastOrNull()
        controller.sessions.forEach { session ->
            key("morph-layer-${session.key}") {
                // 被上面那条会话盖住的程度：只有并行动画才会非 0（组详情里点开子订单卡片）。
                // 下面那条要跟着「模糊 + 压暗」，理由见 CardMorphLayer 的 coverProgress。
                // 用闭包而不是当场算成 Float：进度要在这里读出来才会建立快照依赖，
                // 否则重组一次之后这个值就冻在当时的进度上（CardMorphBackdrop 同理）。
                CardMorphLayer(
                    session = session,
                    overlaySize = overlaySize,
                    surfaceColor = surfaceColor,
                    destinationColor = destinationColor,
                    targetRadiusPx = targetRadiusPx,
                    backdrop = backdrop,
                    coverProgressProvider = {
                        if (session === topSession) {
                            0f
                        } else {
                            controller.sessions
                                .dropWhile { it !== session }
                                .drop(1)
                                .maxOfOrNull { it.progress } ?: 0f
                        }
                    },
                ) {
                    // 打上「这条内容属于哪条会话」的标记：组详情页里的子订单卡片据此才能再开一条
                    // 会话做并行动画（见 CardMorphController.beginExpand）。
                    CompositionLocalProvider(LocalCardMorphOwnerKey provides session.key) {
                        content(session.key)
                    }
                }
            }
        }

        // ③ 正在收回的会话：整屏接住点击。
        //    容器矩形内 = 「撤回」（从当前进度继续铺回详情）；
        //    矩形外 = 另一张卡片（第一条还在退回时，点别处开第二条 = 并行动画）。
        //    这里自己按坐标查登记表，而不是让事件穿透到背景里的主页：Compose 的命中测试只走
        //    「最上层命中的那条路径」，叠加层只要挂一个整屏点击节点，背景卡片就永远收不到事件。
        //
        //    判定用 collapseInteractive() 而不是裸的 collapsing：这一层是**整屏**的，只要挂上就吞掉
        //    全屏点击，而 collapsing 这个标记可能停在 true 而收回其实早已结束（见该函数的说明）。
        //    收回真的在跑时二者完全等价；收回不在跑时整层不挂，点击直接落到列表里那张真卡上。
        val collapsingSessions = controller.sessions.filter { it.collapseInteractive() }
        if (collapsingSessions.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(overlaySize, targetRadiusPx) {
                        detectTapGestures { position ->
                            val owner = collapsingSessions.lastOrNull { session ->
                                sessionContains(session, position, overlaySize, targetRadiusPx)
                            }
                            if (owner != null) {
                                controller.reverseCollapse(owner)
                            } else {
                                controller.cardAt(position)?.let { controller.beginExpand(it) }
                            }
                        }
                    },
            )
        }
    }
}

/**
 * 一条会话的形变容器：圆角矩形 + 详情内容 + 源卡片，全部按这条会话自己的进度合成。
 *
 * [coverProgressProvider] 是「这条会话被上面那条盖住多少」：并行动画里它下面那层的详情页
 * 必须跟着模糊 + 压暗（量级与 [CardMorphBackdrop] 一致），否则上面那张卡片是在一张**清晰**
 * 的整页详情上铺开的，和主页/组卡片铺开时「背景下沉模糊」的观感对不上。
 *
 * 只模糊详情内容、不碰源卡片：收回途中点卡片要「撤回」，那张卡片必须还是清楚的。
 * 模糊是本层内容自己的 RenderEffect（`graphicsLayer { renderEffect = BlurEffect(...) }`），
 * 不采样任何 backdrop，因此与 `MiuixHomeScreen` 挂 layerBackdrop 的那棵子树互不相交，
 * 不构成 AGENTS.md 里禁止的递归采样。
 */
@Composable
private fun CardMorphLayer(
    session: CardMorphSession,
    overlaySize: IntSize,
    surfaceColor: Color,
    destinationColor: Color,
    targetRadiusPx: Float,
    backdrop: LayerBackdrop?,
    coverProgressProvider: () -> Float,
    content: @Composable () -> Unit,
) {
    val from = session.bounds.rect
    val density = LocalDensity.current
    val sourceRadiusPx = with(density) { session.bounds.cornerRadius.toPx() }
    val cardWidthDp = with(density) { from.width.toDp() }
    val cardHeightDp = with(density) { from.height.toDp() }
    // 卡片只在转场前半段参与触摸判定；后半个身位已经淡出，不能继续挡住详情页。
    //
    // 还要求这条会话的收回**真的在跑**（collapseInteractive）：这一层挡住的是真卡的整块矩形，
    // 只靠 `collapsing` 标记时，一段早已结束、标记却没被清掉的收回会让它一直压在真卡上，
    // 列表卡的「收起 / 展开查看详情」按钮就永远点不到（同 ③ 的整屏点击层）。
    // 收回进行中它照旧生效（含可打断续播），撤回语义不变。
    val cardInteractive by remember {
        derivedStateOf { session.progress < SOURCE_LAYER_EXIT && session.collapseInteractive() }
    }
    // 铺满之后不再组合卡片（它在 t≥0.4 就全透明了），只在收回时重新挂回来。
    // 休息态（progress = 1.0，会话仍留在 sessions 里由叠加层独占绘制）**必须**不挂：
    // 叠加层里的卡片是列表那张卡的**另一份 Compose 实例**，它的
    // `rememberSaveable { mutableStateOf(false) }` 与列表那份各持一份状态，
    // 所以它画出来永远是**收起态**。位置正确时它被真卡完全盖住、alpha 也已是 0，
    // 看不出差别；但它会作为真实语义节点留在 a11y 树里（真机 ui-J5.xml 实测到两张卡的节点）。
    val cardVisible by remember { derivedStateOf { session.progress < SOURCE_LAYER_MAX_PROGRESS } }
    val isDarkTheme = MiuixTheme.colorScheme.background.luminance() < 0.5f
    // 被上面那条会话盖住时，本层内容要跟外层背景一样「模糊 + 压暗」，否则叠在上面的详情卡片
    // 周围仍是一页清晰可读的下层详情，观感与主页卡片进入时的不一致。
    //
    // 必须糊本层内容（renderEffect 的 BlurEffect），**不能**用 `textureBlur(backdrop)`：
    // backdrop 里录的是**根层**（主页）的内容，采样它等于把主页快照画到下层的详情内容上——
    // 下层那页不是被糊了，而是被整个换成了主页，看起来就完全没模糊（真机逐帧已确认）。
    // 也不能给本层再挂一个 layerBackdrop 去采样自己：本层内容与模糊层同处一棵子树，会构成递归渲染。
    val coverBrightness = if (isDarkTheme) -0.3f else -0.5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                val t = session.progress.coerceIn(0f, 1f)
                // 被上层会话盖住多少 → 整页下沉多少，量与 CardMorphBackdrop 的
                // `scale = 1 - 0.15t` 严格一致（同一个 0.15，不新增常量）。
                // 在绘制阶段读，避免每帧把整棵详情子树重组一遍。
                val cover = coverProgressProvider().coerceIn(0f, 1f)
                val sink = 1f - 0.15f * cover
                val frame = morphFrame(from, size.width, size.height, sourceRadiusPx, targetRadiusPx, t, sink)
                if (frame.curW <= 0.01f || frame.curH <= 0.01f) return@drawWithContent

                val container = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(
                                frame.curX,
                                frame.curY,
                                frame.curX + frame.curW,
                                frame.curY + frame.curH,
                            ),
                            cornerRadius = CornerRadius(frame.radius, frame.radius),
                        ),
                    )
                }
                // 容器底色：从卡片底色渐变到详情页底色，它就是铺开过程中的「卡片面」。
                drawPath(path = container, color = lerp(surfaceColor, destinationColor, t))

                clipPath(container) {
                    // 内容在下层，带 12dp 上浮，早期完全透明（此时看到的是源卡片）。
                    this@drawWithContent.drawContent()
                }
            },
    ) {
        // 详情内容：整屏测一次，之后只改图层变换（缩放 / 居中偏移 / 上浮 / 淡入）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = session.progress.coerceIn(0f, 1f)
                    // 被上层盖住时本层要与容器同步绕屏幕中心下沉（量与 CardMorphBackdrop 一致），
                    // 同一段进度也驱动下面的模糊。在图层块（绘制阶段）里读进度，
                    // 避免每帧把这一整棵子树重组一遍。
                    val cover = coverProgressProvider().coerceIn(0f, 1f)
                    val sink = 1f - 0.15f * cover
                    val frame = morphFrame(from, size.width, size.height, sourceRadiusPx, targetRadiusPx, t, sink)
                    // contentScale 只含 t（morphFrame 已把 sink 从它里面摘出去），
                    // 这里再乘上同一个 sink 才是最终缩放——只乘一次。
                    val contentScale = frame.contentScale * frame.sinkScale
                    scaleX = contentScale
                    scaleY = contentScale
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = (frame.curW - size.width * contentScale) / 2f
                    // 垂直锚点 = 容器顶边 frame.curY（详情页第 0 行像素落在容器顶边）。
                    // transformOrigin 是**左上角**（见上面那行），绕左上角缩放不会让顶边位移，
                    // 所以这里**不需要**任何 (1 - contentScale) 项——上一轮按「绕中心缩放」多减了
                    // size.height·(1-contentScale)/2，把内容整体抬高（真机实测首帧 +32px）。
                    // 剩下的 12dp 是原有的上浮，随 contentAlpha 淡出；t=1 时 curY=0、alpha=1，
                    // 本式归 0，与真实详情页顶格重合（问题①的静止态）。
                    // 与问题②/第一轮的关系：cover=0 时 curY == size.height·(1-contentScale)/2，
                    // 本式因此**代数恒等**于问题②那一版 `translationY`——真正删掉的只有 srcOffsetY。
                    translationY = frame.curY + ContentDrift.toPx() * (1f - frame.contentAlpha)
                    alpha = frame.contentAlpha
                    if (cover > 0.001f) {
                        val radius = 56f * cover
                        renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                    }
                }
                // 压暗单独一层，与背景层的 brightness 补偿同一个量级。
                .drawWithContent {
                    drawContent()
                    val cover = coverProgressProvider().coerceIn(0f, 1f)
                    val dim = (-coverBrightness * cover).coerceIn(0f, 1f)
                    if (dim > 0.001f) drawRect(Color.Black.copy(alpha = dim))
                },
        ) {
            content()
        }

        // 源卡片：实时渲染列表里那张卡的**同一份 Compose**，靠图层变换摆到容器里的卡片位置。
        // 整段都在容器的圆角裁剪内，所以放大过程中四个圆角就是卡片的四个圆角。
        if (cardVisible) {
            Box(
                modifier = Modifier
                    .size(cardWidthDp, cardHeightDp)
                    .graphicsLayer {
                        val t = session.progress.coerceIn(0f, 1f)
                        val fullWidth = if (overlaySize.width > 0) overlaySize.width.toFloat() else size.width
                        val fullHeight = if (overlaySize.height > 0) overlaySize.height.toFloat() else size.height
                        val frame = morphFrame(from, fullWidth, fullHeight, sourceRadiusPx, targetRadiusPx, t)
                        val w0 = from.width
                        val srcScale = if (w0 > 0f) 1f + (fullWidth / w0 - 1f) * t else 1f
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = srcScale
                        scaleY = srcScale
                        translationX = frame.curX + (frame.curW - w0 * srcScale) / 2f
                        // 源卡片**只能**有 curY 这一个垂直位移，不许再叠加任何补偿项。
                        // 卡片盒子本来就布局在容器原点、宽高都取自 from，所以 curY 就是它相对容器
                        // 的精确落点；加任何常量下推都会让它在 t=0 与列表里那张真卡错位
                        // （真机实测：展开卡底边超屏 617px，加了补偿之后整卡下移 617px，
                        //  起始帧出现空白带、收回后留下一张错位的「幽灵卡」）。
                        translationY = frame.curY
                        alpha = (1f - frame.contentAlpha).coerceIn(0f, 1f)
                    }
                    .then(if (cardInteractive) Modifier.blockMorphCardTouches() else Modifier),
            ) {
                session.card.invoke()
            }
        }
    }
}

/**
 * 「这条会话的收回**正在进行**」：撤回点击层与源卡片触摸拦截层是否生效的**唯一**判据。
 *
 * 为什么不能直接用 `collapsing`：它只是「请求收回」这个状态标记，**没有任何东西保证它会被清掉**。
 * 真正的清理只发生在 `runSettle` 目标为 0 的收尾里（`sessions.remove` + `collapsing = false`），
 * 而收尾要等弹簧跑完、协程真的执行到那一行：转场被系统返回手势打断、帧时钟停摆（屏幕熄灭/丢焦点）、
 * 渲染停顿都可能让 `collapsing` 长时间停在 true。叠加层只要看到这个标记就挂上整屏点击层，
 * 于是一段早就播完（甚至从没真正开跑）的转场会一直把点击吸进 [CardMorphController.reverseCollapse]
 * ——用户看到的是「卡片已经收回、画面静止」，点按钮却进了组详情。
 *
 * 改成要求「收束协程还在跑」：动画结束那一帧清理就执行（`settleJob` 随即变为完成），
 * 再晚到来的点击一律穿透给列表里那张真卡；而收回途中（含可打断续播）协程始终活跃，撤回语义一字不变。
 * 它不改变任何绘制：镜像卡片层 / 容器几何 / 圆角 / 进度一律照旧，只决定「谁接住这一下点击」。
 */
private fun CardMorphSession.collapseInteractive(): Boolean =
    collapsing && settleJob?.isActive == true

/** 这个坐标是否落在该会话当前的容器矩形里（撤回点击层的判据）。 */
private fun sessionContains(
    session: CardMorphSession,
    position: Offset,
    overlaySize: IntSize,
    targetRadiusPx: Float,
): Boolean {
    if (overlaySize.width <= 0 || overlaySize.height <= 0) return false
    val frame = morphFrame(
        from = session.bounds.rect,
        fullWidth = overlaySize.width.toFloat(),
        fullHeight = overlaySize.height.toFloat(),
        sourceRadius = 0f,
        targetRadius = targetRadiusPx,
        t = session.progress.coerceIn(0f, 1f),
    )
    return position.x >= frame.curX && position.x <= frame.curX + frame.curW &&
        position.y >= frame.curY && position.y <= frame.curY + frame.curH
}

/**
 * 转场中的卡片不接受点击：它和列表里那张卡是同一份实现（带点击回调），
 * 不拦住的话转场途中的误触会再触发一次入栈。
 */
private fun Modifier.blockMorphCardTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

/**
 * 铺开过程中的背景：整块下沉（Pronto 的 background sinking，scale = 1 - 0.15t）后取样，
 * 再叠加与 `MiuixModalScrim` 完全相同的模糊参数与黑色压暗，因此观感与 BottomSheet 一致。
 *
 * 拆成独立函数是为了让「按进度重组」只发生在这一层：模糊半径必须在组合期确定，
 * 若写在 [MiuixCardMorphOverlay] 主体里，详情内容会跟着每帧重组。
 */
@Composable
private fun CardMorphBackdrop(
    backdrop: LayerBackdrop?,
    surfaceColor: Color,
    progressProvider: () -> Float,
) {
    val progress = progressProvider().coerceIn(0f, 1f)
    val isDarkTheme = MiuixTheme.colorScheme.background.luminance() < 0.5f

    if (backdrop != null) {
        val baseBrightness = if (isDarkTheme) -0.3f else -0.5f
        // ① 整屏底色垫在最外层，**不参与下沉**。
        // 下沉后模糊层只剩 85%，四周必然露出一圈；这一圈在原版里是窗口底色（列表页缩小后露出
        // 应用背景），所以这里也必须垫一层页面底色，并且压到跟模糊层同一个调子——模糊层内部的
        // `brightness = baseBrightness × p` 起的就是这个作用，这里用等量黑代替。
        // 早期版本把底色写在下沉层**内部**，那一圈于是完全透明：背后真正的、未模糊也未压暗的
        // 主页从四周透出来，而且越到后面露得越宽——看起来就是「模糊背景上压了一块矩形遮罩」。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(surfaceColor)
                .background(Color.Black.copy(alpha = (-baseBrightness * progress).coerceIn(0f, 1f))),
        )
        // ② 定格的主页：下沉 + 模糊 + 提亮压暗，始终不透明。
        // t=0 时它就是一张未模糊、未压暗的主页快照，卡片就叠在它上面
        // （这正是 Pronto 的做法——toImageBitmap 定格后整块下沉）。
        //
        // 四个角必须是圆角：下沉后整块只剩 85%，四角从屏幕边上收进来，直角会像一块硬切的黑矩形。
        // 半径恒等于**屏幕圆角**（不随进度从 0 长起来——那样动画早期四角仍近似直角）：
        // t=0 时该层与整屏完全重合，半径正好等于系统给窗口裁的圆角，看不出差别；
        // 之后它一边缩小、四角一边整体内收，视觉上就是"窗口本身缩小了"。
        // graphicsLayer 的 scale 会把形状一起缩小，所以这里按 sink 反向补偿，保持视觉半径不变。
        val density = LocalDensity.current
        val screenRadiusPx = rememberScreenCornerRadiusPx()
        val sinkScale = 1f - 0.15f * progress
        val resolvedRadiusPx = if (screenRadiusPx > 0f) {
            screenRadiusPx
        } else {
            with(density) { FallbackScreenCornerRadius.toPx() }
        }
        val sunkShape = RoundedCornerShape(
            with(density) { (resolvedRadiusPx / sinkScale).toDp() },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 整块下沉：Pronto 的 background sinking，scale = 1 - 0.15t（下限 0.85）。
                    scaleX = sinkScale
                    scaleY = sinkScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
                // 底色也按同一个圆角画：不然方角会在圆角外露出一圈硬边。
                .background(color = surfaceColor, shape = sunkShape)
                .textureBlur(
                    backdrop = backdrop,
                    shape = sunkShape,
                    blurRadius = 56f * progress,
                    colors = BlurDefaults.blurColors(
                        brightness = baseBrightness * progress,
                        contrast = 1f + 0.2f * progress,
                        saturation = 1f + 0.08f * progress,
                    ),
                ),
        )
        // 黑色压暗单独一层：与 MiuixModalScrim 一致，随进度加深。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * progress)),
        )
    } else {
        val fallbackAlpha = if (isDarkTheme) 0.28f else 0.36f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(surfaceColor)
                .background(Color(0xFF5A5A5A).copy(alpha = fallbackAlpha * progress)),
        )
    }
}
