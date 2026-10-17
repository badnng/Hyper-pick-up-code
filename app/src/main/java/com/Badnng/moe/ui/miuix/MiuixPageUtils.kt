package com.Badnng.moe.ui.miuix

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.Badnng.moe.ui.LocalIsMiuixUi
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlurEffect
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.math.roundToInt

@Composable
fun rememberMiuixStyle(): Boolean {
    val providedStyle = LocalIsMiuixUi.current
    if (providedStyle != null) return providedStyle

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var uiStyle by remember { mutableStateOf(prefs.getString("ui_style", "miuix") ?: "miuix") }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "ui_style") uiStyle = p.getString(key, "miuix") ?: "miuix"
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return uiStyle == "miuix"
}

@Composable
fun rememberMiuixBackdrop(): LayerBackdrop? {
    return rememberAllowedMiuixBackdrop(rememberMiuixBlurAllowed())
}

@Composable
fun rememberMiuixIconColorMixingBackdrop(): LayerBackdrop? {
    return rememberAllowedMiuixBackdrop(rememberMiuixIconColorMixingAllowed())
}

@Composable
private fun rememberAllowedMiuixBackdrop(allowed: Boolean): LayerBackdrop? {
    if (!allowed || !isRenderEffectSupported() || !isRuntimeShaderSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/**
 * [collapseProgress] 从展开时的 0 变到完全收起时的 1，仅在绘制阶段读取。
 * 不提供折叠进度的固定顶栏保持正常亮度。
 */
@Composable
fun MiuixBlurredBar(
    backdrop: LayerBackdrop?,
    blurEnabled: Boolean,
    progressive: Boolean = true,
    blurRadius: Float = if (progressive) 18f else 20f,
    blendAlpha: Float = if (progressive) 0.14f else 0.8f,
    collapseProgress: () -> Float = { 0f },
    content: @Composable () -> Unit,
) {
    val lowEndDevice = rememberMiuixDevicePerformanceTier() == MiuixDevicePerformanceTier.Low
    val activeBackdrop = if (blurEnabled && !lowEndDevice) backdrop else null
    val blurModifier = if (activeBackdrop != null) {
        if (progressive) {
            // miuix-blur 0.9.4-rc01 按像素半径选择采样倍率：中间层 sigma = radiusPx * 0.45 * 0.4，
            // sigma² >= 90.25 时进入 4 倍降采样。限制到 52px，让高密度屏也最多使用 2 倍采样，
            // 避免小字号文字在模糊过渡区被放大成块；单纯关闭 noise 无法解决这种采样伪影。
            val maxRadius = minOf(18f, 52f / LocalDensity.current.density)
            val colorScheme = MiuixTheme.colorScheme
            val dimColor = if (colorScheme.surface.luminance() < 0.5f) {
                colorScheme.surface
            } else {
                colorScheme.onSurface
            }
            val gradient = remember {
                ProgressiveBlur.Top.copy(
                    startFraction = 0.15f,
                    endFraction = 1f,
                    curve = 1.25f,
                )
            }
            Modifier
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = activeBackdrop,
                    shape = { RectangleShape },
                    // 与 effects 共用渐变，保留库的全分辨率清晰端采样。
                    progressiveGradient = gradient,
                    effects = {
                        // 只在绘制阶段读折叠进度，展开保持亮色，收起时逐渐压暗。
                        val collapsed = collapseProgress().coerceIn(0f, 1f)
                        progressiveTextureBlurEffect(
                            blurRadiusX = blurRadius.coerceIn(0f, maxRadius),
                            gradient = gradient,
                            noiseCoefficient = 0f,
                            colors = BlurColors(
                                blendColors = listOf(
                                    BlendColorEntry(
                                        color = colorScheme.surface.copy(alpha = 0.52f * (1f - collapsed)),
                                    ),
                                    BlendColorEntry(
                                        color = dimColor.copy(alpha = blendAlpha.coerceIn(0f, 1f) * collapsed),
                                        mode = BlurBlendMode.Multiply,
                                    ),
                                ),
                            ),
                        )
                    },
                )
        } else {
            Modifier
                .fillMaxWidth()
                .textureBlur(
                    backdrop = activeBackdrop,
                    shape = RectangleShape,
                    blurRadius = blurRadius,
                    colors = BlurDefaults.blurColors(
                        blendColors = listOf(
                            BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(blendAlpha)),
                        ),
                    ),
                )
        }
    } else if (lowEndDevice) {
        val colorScheme = MiuixTheme.colorScheme
        val surfaceColor = colorScheme.surface
        val dimColor = if (surfaceColor.luminance() < 0.5f) surfaceColor else colorScheme.onSurface
        Modifier
            .fillMaxWidth()
            .drawWithCache {
                // 低档只绘制主题色透明度渐变，不录制、采样背景，也不创建模糊效果。
                fun overlayGradient(color: Color) = Brush.verticalGradient(
                    0f to color.copy(alpha = 0.96f),
                    0.15f to color.copy(alpha = 0.94f),
                    0.5f to color.copy(alpha = 0.86f),
                    0.72f to color.copy(alpha = 0.55f),
                    0.9f to color.copy(alpha = 0.12f),
                    1f to color.copy(alpha = 0f),
                    endY = size.height,
                )
                val surfaceGradient = overlayGradient(surfaceColor)
                val dimGradient = overlayGradient(dimColor)
                onDrawBehind {
                    drawRect(surfaceGradient)
                    // 收起时只叠一层浅灰色，进度留在绘制阶段读取。
                    val dimAlpha = blendAlpha.coerceIn(0f, 0.1f) * collapseProgress().coerceIn(0f, 1f)
                    if (dimAlpha > 0f) drawRect(dimGradient, alpha = dimAlpha)
                }
            }
    } else {
        // 系统关闭模糊等其他情况仍由外层提供完整表面，覆盖顶栏和 TabRow。
        Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surface)
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if ((activeBackdrop != null && progressive) || lowEndDevice) {
                        // 只延长背景的绘制范围，顶栏测量高度、列表顶距和点击区域保持原样。
                        // 在测量阶段跟随折叠高度，不把逐帧尺寸写回组合状态。
                        Modifier.layout { measurable, constraints ->
                            val featherHeight = 16.dp.roundToPx()
                            val placeable = measurable.measure(constraints.offset(vertical = featherHeight))
                            layout(placeable.width, placeable.height - featherHeight) {
                                placeable.placeRelative(0, 0)
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .then(blurModifier),
        )
        CompositionLocalProvider(
            LocalMiuixTopBarBackdrop provides activeBackdrop,
            LocalMiuixTopBarCollapseProgress provides collapseProgress,
        ) {
            content()
        }
    }
}

/**
 * Miuix 弹层共用的全屏背景。设备允许时使用 Backdrop 模糊；低性能设备或平台不支持
 * RenderEffect 时使用灰色半透明遮罩，避免每个页面各自维护一套降级逻辑。
 */
@Composable
fun MiuixModalScrim(
    backdrop: LayerBackdrop?,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val visibleProgress = progress.coerceIn(0f, 1f)
    if (visibleProgress <= 0.01f) return

    if (backdrop != null) {
        val isDarkTheme = MiuixTheme.colorScheme.background.luminance() < 0.5f
        val baseBrightness = if (isDarkTheme) -0.3f else -0.5f
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .textureBlur(
                    backdrop = backdrop,
                    shape = RectangleShape,
                    blurRadius = 56f * visibleProgress,
                    colors = BlurDefaults.blurColors(
                        brightness = baseBrightness * visibleProgress,
                        contrast = 1f + 0.2f * visibleProgress,
                        saturation = 1f + 0.08f * visibleProgress,
                    ),
                )
                .graphicsLayer(alpha = visibleProgress),
        )
    } else {
        val isDarkTheme = MiuixTheme.colorScheme.background.luminance() < 0.5f
        val fallbackAlpha = if (isDarkTheme) 0.28f else 0.36f
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Color(0xFF5A5A5A).copy(alpha = fallbackAlpha * visibleProgress),
                ),
        )
    }
}

@Composable
fun MiuixTopBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    color: Color = MiuixTheme.colorScheme.surface,
    expandedTitleTopPadding: Dp = 0.dp,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (expandedTitleTopPadding <= 0.dp) {
        TopAppBar(
            title = title,
            color = color,
            scrollBehavior = scrollBehavior,
            navigationIcon = navigationIcon,
            actions = actions,
        )
        return
    }

    val extraTitleSpace = remember(scrollBehavior, expandedTitleTopPadding) {
        { expandedTitleTopPadding * (1f - scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)) }
    }
    Box(Modifier.fillMaxWidth()) {
        // 0.9.4-rc01 未开放大标题顶部间距。保留原标题的测量、折叠和无障碍语义，
        // 只把可见的大标题下移；bottomContent 同步占位，让 Scaffold 带动正文下移。
        TopAppBar(
            title = title,
            color = color,
            largeTitleColor = Color.Transparent,
            scrollBehavior = scrollBehavior,
            navigationIcon = navigationIcon,
            actions = actions,
            bottomContent = {
                Spacer(
                    Modifier.layout { measurable, constraints ->
                        val height = extraTitleSpace().roundToPx()
                            .coerceIn(constraints.minHeight, constraints.maxHeight)
                        val placeable = measurable.measure(
                            constraints.copy(minHeight = height, maxHeight = height),
                        )
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(0, 0)
                        }
                    },
                )
            },
        )
        Box(
            Modifier
                .matchParentSize()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                .padding(horizontal = TopAppBarDefaults.TitlePadding),
        ) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = MiuixTheme.textStyles.title1.fontSize,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .clearAndSetSemantics { }
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .offset {
                        IntOffset(
                            x = 0,
                            y = TopAppBarDefaults.CollapsedHeight.roundToPx() +
                                scrollBehavior.state.heightOffset.roundToInt() +
                                extraTitleSpace().roundToPx(),
                        )
                    }
                    .graphicsLayer {
                        alpha = 1f - (scrollBehavior.state.collapsedFraction * 3f).coerceIn(0f, 1f)
                    },
            )
        }
    }
}

fun Modifier.miuixScrollModifiers(
    scrollBehavior: ScrollBehavior,
): Modifier = this
    .scrollEndHaptic()
    .overScrollVertical()
    .nestedScroll(scrollBehavior.nestedScrollConnection)
    .fillMaxHeight()

@Composable
fun MiuixPageContainer(
    backdrop: LayerBackdrop?,
    lazyListState: LazyListState,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    content: LazyListScope.() -> Unit,
) {
    Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.miuixScrollModifiers(scrollBehavior),
            contentPadding = contentPadding,
        ) {
            content()
        }
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(lazyListState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            trackPadding = contentPadding,
        )
    }
}

/**
 * 设置二级页共用的懒加载容器。每个 [sections] 元素对应一个独立 Lazy item，
 * 只拆页面区块，不拆区块内部的 Miuix Card。
 */
@Composable
fun MiuixSettingsLazyColumn(
    sections: List<@Composable () -> Unit>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        sections.forEachIndexed { index, section ->
            item(key = index) { section() }
        }
    }
}
