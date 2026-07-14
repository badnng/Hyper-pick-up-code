package com.Badnng.moe.ui.screen.miuix

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Miuix 主页底部覆盖层的统一布局信息。
 *
 * [navigationBarTopFromBottom] 由实际布局坐标测量，已包含系统底部 Insets、
 * 悬浮底栏自身高度与视觉偏移，页面不再分别维护固定的底部 dp 值。
 */
@Immutable
data class MiuixHomeBottomLayoutInfo(
    val safeBottomInset: Dp,
    val navigationBarTopFromBottom: Dp,
) {
    val pageContentBottomPadding: Dp
        get() = navigationBarTopFromBottom + MiuixHomeBottomLayoutDefaults.ContentSpacing

    val toolbarBottomPadding: Dp
        get() = navigationBarTopFromBottom + MiuixHomeBottomLayoutDefaults.ToolbarGap

    val editActionBottomPadding: Dp
        get() = safeBottomInset + MiuixHomeBottomLayoutDefaults.EditOverlayEdgeSpacing
}

internal object MiuixHomeBottomLayoutDefaults {
    val ContentSpacing = 16.dp
    val ToolbarGap = 16.dp
    val EditOverlayEdgeSpacing = 8.dp
    val EditToolbarGap = 12.dp
    val EstimatedEditToolbarHeight = 56.dp
    val FloatingBarVerticalOffset = 10.dp
    val LargeScreenFloatingBarVerticalOffset = 20.dp
    val IosLikeBarHeight = 64.dp
    val IosLikeBottomSpacing = 8.dp
    val IosLikeNoInsetBottomSpacing = 36.dp
    val IosLikeLargeScreenMaxWidth = 440.dp
}
