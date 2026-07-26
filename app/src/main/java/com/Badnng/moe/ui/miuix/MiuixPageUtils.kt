package com.Badnng.moe.ui.miuix

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.Badnng.moe.ui.LocalIsMiuixUi
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

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

@Composable
fun MiuixBlurredBar(
    backdrop: LayerBackdrop?,
    blurEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = if (blurEnabled && backdrop != null) {
            Modifier
                .fillMaxWidth()
                .textureBlur(
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
            // 无模糊时顶栏仍应是一块完整的不透明表面，不能只给 TopAppBar 本体上色，
            // 否则其下方的 TabRow 区域会直接露出内容背景形成留白。
            Modifier
                .fillMaxWidth()
                .background(MiuixTheme.colorScheme.surface)
        },
    ) {
        content()
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
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        color = color,
        scrollBehavior = scrollBehavior,
        navigationIcon = navigationIcon,
        actions = actions,
    )
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
