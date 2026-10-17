package com.Badnng.moe.ui.screen.miuix

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clipScrollableContainer
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// 借用外层卡片的 16dp 内边距绘制阴影，不改变子卡片的测量宽度和位置。
private val GroupExpansionLayerOutsets = LayerOutsets(16.dp)

/** 外层高度由各子卡片的进度合成，子卡片保持完整尺寸，只沿竖向展开和收回。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> MiuixGroupExpansion(
    expanded: Boolean,
    items: List<T>,
    itemKey: (T) -> Any,
    content: @Composable (T) -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    // 已展开的列表重建或详情转场镜像直接显示终态，不重播入场动画。
    val initiallyExpanded = remember { expanded }
    val itemStates = items.map { item ->
        key(itemKey(item)) { remember { GroupExpansionItemState(initiallyExpanded) } }
    }
    val retainContent by remember(expanded, itemStates) {
        derivedStateOf {
            expanded || itemStates.any {
                it.progress.value > 0f || it.opacity.value > 0f ||
                    it.progress.isRunning || it.opacity.isRunning
            }
        }
    }

    LaunchedEffect(expanded, itemStates, motionScheme) {
        val target = if (expanded) 1f else 0f
        val lastIndex = itemStates.lastIndex
        val staggerSpanMillis = minOf(lastIndex.coerceAtLeast(0) * 40L, 160L)
        itemStates.forEachIndexed { index, state ->
            launch {
                if (state.progress.value == target && state.opacity.value == target) return@launch
                // 展开从上往下、收起从下往上；中途反向接着当前值，不重新排队。
                when {
                    expanded && state.progress.value == 0f && state.opacity.value == 0f ->
                        delay(24L + staggerSpanMillis * index / lastIndex.coerceAtLeast(1))
                    !expanded && state.progress.value == 1f && state.opacity.value == 1f ->
                        delay(staggerSpanMillis * (lastIndex - index) / lastIndex.coerceAtLeast(1))
                }
                coroutineScope {
                    launch {
                        state.progress.animateTo(target, motionScheme.defaultSpatialSpec<Float>())
                    }
                    launch {
                        state.opacity.animateTo(target, motionScheme.defaultEffectsSpec<Float>())
                    }
                }
            }
        }
    }

    if (!retainContent) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 上下继续跟随展开高度裁剪，左右留出阴影空间，并保留裁剪区的点击边界。
            .clipScrollableContainer(Orientation.Vertical),
    ) {
        items.forEachIndexed { index, item ->
            key(itemKey(item)) {
                val state = itemStates[index]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 收回的下方卡片滑到上方卡片背后，不盖住尚未收起的内容。
                        .zIndex(-index.toFloat())
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(
                                constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
                            )
                            val progress = state.progress.value.coerceIn(0f, 1f)
                            // 首项含展开按钮后的 8dp 间隔，随内容一起收完，避免末帧跳高。
                            val leading = (if (index == 0) 14.dp else 12.dp).toPx()
                            val trailing = (if (index == items.lastIndex) 6.dp else 0.dp).toPx()
                            val visibleBottom = ((leading + placeable.height) * progress).roundToInt()
                            val height = ((leading + placeable.height + trailing) * progress).roundToInt()
                            layout(placeable.width, height) {
                                // 子卡片底边和占用高度共用进度，外层不会抢先裁掉底部。
                                // x 始终为 0；完整宽度、文字和圆角不参与缩放或横向过渡。
                                placeable.placeRelative(x = 0, y = visibleBottom - placeable.height)
                            }
                        }
                        .graphicsLayer {
                            alpha = state.opacity.value.coerceIn(0f, 1f)
                            // 淡入淡出的离屏图层也需要留白，否则仍会把卡片边缘阴影裁掉。
                            outsets = GroupExpansionLayerOutsets
                        },
                ) {
                    content(item)
                }
            }
        }
    }
}

@Stable
private class GroupExpansionItemState(initiallyVisible: Boolean) {
    val progress = Animatable(if (initiallyVisible) 1f else 0f, visibilityThreshold = 0.001f).apply {
        updateBounds(lowerBound = 0f, upperBound = 1f)
    }
    val opacity = Animatable(if (initiallyVisible) 1f else 0f)
}
