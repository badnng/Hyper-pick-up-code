package com.Badnng.moe.ui.miuix

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Card

internal val MiuixCardShadowRadius = 7.dp
internal val MiuixCardShadowOffsetY = 2.dp
internal const val MiuixCardShadowAlpha = 0.11f

/** 给浅色页面上的 Miuix 卡片一层柔和的底部阴影，保留卡片原有的 squircle 裁剪。 */
@Composable
fun Modifier.miuixReadableCardShadow(enabled: Boolean = true): Modifier {
    val colors = MiuixTheme.colorScheme
    if (!enabled || colors.surface.luminance() < 0.5f) return this

    return this.dropShadow(
        shape = RoundedCornerShape(16.dp),
        shadow = Shadow(
            radius = MiuixCardShadowRadius,
            offset = DpOffset(0.dp, MiuixCardShadowOffsetY),
            color = colors.onSurface,
            alpha = MiuixCardShadowAlpha,
        ),
    )
}

/** 设置页统一使用的卡片，沿用 Miuix Card 的点击和内容行为。 */
@Composable
fun MiuixReadableCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shadowedModifier = modifier.miuixReadableCardShadow()
    if (onClick == null) {
        Card(modifier = shadowedModifier, content = content)
    } else {
        Card(modifier = shadowedModifier, onClick = onClick, content = content)
    }
}
