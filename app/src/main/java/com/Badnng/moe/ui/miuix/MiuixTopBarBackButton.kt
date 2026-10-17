package com.Badnng.moe.ui.miuix

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 只采样页面内容层，不能在按钮所在的顶栏上重新录制同一 backdrop。
internal val LocalMiuixTopBarBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
internal val LocalMiuixTopBarCollapseProgress = staticCompositionLocalOf<() -> Float> { { 0f } }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MiuixTopBarBackButton(
    onClick: () -> Unit,
    supportingPane: Boolean,
) {
    val colorMixingAllowed = rememberMiuixIconColorMixingAllowed()
    val backdrop = LocalMiuixTopBarBackdrop.current.takeIf { colorMixingAllowed }
    val collapseProgress = LocalMiuixTopBarCollapseProgress.current
    // 组合期只观察是否完全收起；逐帧的阴影透明度在绘制阶段读取。
    val showShadow by remember(collapseProgress) {
        derivedStateOf { collapseProgress() < 0.999f }
    }
    val buttonSize = 44.dp
    // 半径取边长的一半，Miuix Squircle 的着色器会自行过渡为正圆。
    val cornerRadius = buttonSize / 2
    // 仅供模糊、阴影和高光匹配圆形轮廓；底座及点击反馈裁剪由 squircleSurface 绘制。
    val effectShape = CircleShape
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed) 1.12f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
        label = "TopBarBackButtonScale",
    )
    val colorScheme = MiuixTheme.colorScheme
    val isDarkTheme = colorScheme.surface.luminance() < 0.5f
    val buttonColor = if (isDarkTheme) colorScheme.surfaceContainer else Color.White
    val iconColor = colorScheme.onSurface
    val glassAlpha = if (isDarkTheme) 0.88f else 0.78f
    val highlightAlpha = if (isDarkTheme) 0.18f else 1f
    val shadowColor = if (isDarkTheme) colorScheme.surface else colorScheme.onSurface
    val shadowAlpha = if (isDarkTheme) 0.4f else 0.16f
    val shadowModifier = if (showShadow) {
        Modifier.dropShadow(shape = effectShape) {
            radius = 6.dp.toPx()
            // 正面来光：阴影居中衬托按钮，四周均匀扩散。
            offset = Offset.Zero
            color = shadowColor
            alpha = shadowAlpha * (1f - collapseProgress().coerceIn(0f, 1f))
        }
    } else {
        // 完全收起后移除阴影节点，不保留透明阴影的绘制开销。
        Modifier
    }
    val glassModifier = if (backdrop != null) {
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = effectShape,
            blurRadius = 12f,
            noiseCoefficient = 0f,
            colors = BlurDefaults.blurColors(
                blendColors = listOf(BlendColorEntry(buttonColor.copy(alpha = glassAlpha))),
                saturation = 1.1f,
            ),
        )
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .size(buttonSize)
            // 动画只更新绘制层：底座、模糊、高光和箭头一起缩放，不挤动标题。
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .then(shadowModifier)
            .squircleSurface(
                color = if (backdrop == null) buttonColor else Color.Transparent,
                cornerRadius = cornerRadius,
            )
            .then(glassModifier)
            .drawWithCache {
                val outline = effectShape.createOutline(size, layoutDirection, this)
                val highlightBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = highlightAlpha),
                        Color.White.copy(alpha = 0.2f * highlightAlpha),
                        Color.White.copy(alpha = 0.7f * highlightAlpha),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                val softStroke = Stroke(width = 5.dp.toPx())
                val innerStroke = Stroke(width = 3.dp.toPx())
                val edgeStroke = Stroke(width = 2.dp.toPx())
                onDrawWithContent {
                    drawContent()
                    // 柔光沿圆形轮廓绘制；外半边由 squircleSurface 裁掉，内边缘保持 1dp。
                    drawOutline(outline, highlightBrush, alpha = 0.08f, style = softStroke)
                    drawOutline(outline, highlightBrush, alpha = 0.14f, style = innerStroke)
                    drawOutline(outline, highlightBrush, alpha = 0.85f, style = edgeStroke)
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (supportingPane) MiuixIcons.Regular.Close else MiuixIcons.Regular.Back,
            contentDescription = if (supportingPane) "关闭" else "返回",
            tint = iconColor,
            modifier = Modifier.size(27.dp),
        )
    }
}
