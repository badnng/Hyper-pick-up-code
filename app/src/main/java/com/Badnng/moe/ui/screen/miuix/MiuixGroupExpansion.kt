package com.Badnng.moe.ui.screen.miuix

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clipScrollableContainer
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val GroupExpansionStaggerMillis = 80L
private const val GroupExpansionMaxStaggerMillis = 400L
private val GroupExpansionLiftDistance = 6.dp
private val GroupExpansionLayerOutsets = LayerOutsets(16.dp)
internal val GroupOrderContentPadding = 16.dp

/**
 * 卡片外壳、间距和内容托起同步进行，文字图标作为整体渐显，不参与尺寸压缩。
 * content 接收的 Modifier 应放在卡片内部的内容行上，已包含内容内边距。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> MiuixGroupExpansion(
    expanded: Boolean,
    items: List<T>,
    itemKey: (T) -> Any,
    onAnimationStateChange: (Boolean) -> Unit = {},
    content: @Composable (T, Modifier) -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    // 列表重建和详情镜像直接使用当前展开态，不重播入场。
    val initiallyExpanded = remember { expanded }
    val itemStates = items.map { item ->
        key(itemKey(item)) {
            remember { GroupExpansionItemState(initiallyExpanded) }
        }
    }
    val retainContent by remember(expanded, itemStates) {
        derivedStateOf {
            expanded || itemStates.any {
                it.progress.value > 0f || it.opacity.value > 0f ||
                    it.progress.isRunning || it.opacity.isRunning
            }
        }
    }
    val isAnimating by remember(expanded, itemStates) {
        derivedStateOf {
            val target = if (expanded) 1f else 0f
            itemStates.any {
                it.progress.isRunning || it.opacity.isRunning ||
                    it.progress.value != target || it.opacity.value != target
            }
        }
    }
    val latestOnAnimationStateChange by rememberUpdatedState(onAnimationStateChange)
    if (!LocalCardMorphIsMirror.current) {
        DisposableEffect(Unit) {
            onDispose { latestOnAnimationStateChange(false) }
        }
        LaunchedEffect(isAnimating) {
            // 最后一帧高度先完成布局，再恢复列表位移动画，避免把最后一次尺寸变化当成重排。
            // 快速反向会取消此等待；状态切换过程中不再先报告一次 false。
            if (!isAnimating) withFrameNanos { }
            latestOnAnimationStateChange(isAnimating)
        }
    }

    LaunchedEffect(expanded, itemStates, motionScheme) {
        val target = if (expanded) 1f else 0f
        // 复用一镜到底的临界阻尼弹簧：展开舒缓，收起稍快，末段自然减速。
        val spatialSpec = if (expanded) CardMorphExpandSpring else CardMorphCollapseSpring
        val lastIndex = itemStates.lastIndex.coerceAtLeast(0)
        val staggerSpan = minOf(lastIndex * GroupExpansionStaggerMillis, GroupExpansionMaxStaggerMillis)
        itemStates.forEachIndexed { index, state ->
            launch {
                if (state.progress.value == target && state.opacity.value == target) return@launch
                // 已在运动中的行立即反向；停在端点的行才依次启动，快速连点不重置进度。
                val atOppositeEnd = state.progress.value == 1f - target &&
                    state.opacity.value == 1f - target
                if (atOppositeEnd) {
                    val sequenceIndex = if (expanded) index else lastIndex - index
                    delay(staggerSpan * sequenceIndex / lastIndex.coerceAtLeast(1))
                }
                coroutineScope {
                    // 高度与显隐一起启动，不再等待预留空间或淡出完成才执行下一阶段。
                    launch { state.progress.animateTo(target, spatialSpec) }
                    launch {
                        state.opacity.animateTo(target, motionScheme.defaultEffectsSpec<Float>())
                    }
                }
            }
        }
    }

    if (!retainContent) return
    Column(modifier = Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            key(itemKey(item)) {
                val state = itemStates[index]
                val contentModifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
                        )
                        val progress = state.progress.value.coerceIn(0f, 1f)
                        val height = (placeable.height * progress).roundToInt()
                        val lift = (GroupExpansionLiftDistance.toPx() * (1f - progress)).roundToInt()
                        val padding = GroupOrderContentPadding.roundToPx()
                        // 最下面一行内容始终落在卡片内；空间不足时淡出整个内容块，不能拦腰裁字。
                        // 随底部内边距连续恢复可见度，外壳仍在运动时内容就开始出现，无阶段切换。
                        val contentBottom = placeable.height - padding + lift
                        val visibility = ((height - contentBottom).toFloat() / padding.coerceAtLeast(1))
                            .coerceIn(0f, 1f)
                        layout(placeable.width, height) {
                            placeable.placeRelativeWithLayer(0, lift) {
                                alpha = state.opacity.value.coerceIn(0f, 1f) * visibility
                                outsets = GroupExpansionLayerOutsets
                            }
                        }
                    }
                    .padding(GroupOrderContentPadding)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 只作行边界保护；卡片外壳根据内容行的实时高度绘制，不再裁切整张满高卡片。
                        .clipScrollableContainer(Orientation.Vertical)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(
                                constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
                            )
                            val progress = state.progress.value.coerceIn(0f, 1f)
                            val top = ((if (index == 0) 14.dp else 4.dp).toPx() * progress).roundToInt()
                            val bottom = ((if (index == items.lastIndex) 6.dp else 8.dp).toPx() * progress)
                                .roundToInt()
                            // 子卡片已返回当前高度，这里只同步收放行间距，不能再乘一次进度。
                            val height = top + placeable.height + bottom
                            layout(placeable.width, height) {
                                placeable.placeRelative(0, top)
                            }
                        }
                        .graphicsLayer {
                            // 完全收起时不留下零高度卡片的阴影；其余帧外壳始终跟随高度显示。
                            alpha = if (state.progress.value > 0f) 1f else 0f
                            outsets = GroupExpansionLayerOutsets
                        },
                ) {
                    content(item, contentModifier)
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
