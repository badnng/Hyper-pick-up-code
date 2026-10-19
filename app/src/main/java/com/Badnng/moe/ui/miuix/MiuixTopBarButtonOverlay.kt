package com.Badnng.moe.ui.miuix

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

private class TopBarButtonOverlayState {
    var hostCoordinates: LayoutCoordinates? = null
    var buttonCoordinates: LayoutCoordinates? = null
    var layer by mutableStateOf<GraphicsLayer?>(null)
    var offset by mutableStateOf<Offset?>(null)

    fun updateOffset() {
        val host = hostCoordinates?.takeIf { it.isAttached }
        val button = buttonCoordinates?.takeIf { it.isAttached }
        offset = if (host != null && button != null) {
            host.localPositionOf(button, Offset.Zero)
        } else {
            null
        }
    }
}

private val LocalTopBarButtonOverlay = staticCompositionLocalOf<TopBarButtonOverlayState?> { null }

/** 将按钮绘制在 TopAppBar 的 clipToBounds 外，布局、触摸和无障碍节点仍留在导航槽位。 */
@Composable
internal fun MiuixTopBarButtonOverlay(content: @Composable () -> Unit) {
    val overlay = remember { TopBarButtonOverlayState() }
    Box(
        modifier = Modifier
            .onGloballyPositioned {
                overlay.hostCoordinates = it
                overlay.updateOffset()
            }
            .drawWithContent {
                drawContent()
                val layer = overlay.layer
                val offset = overlay.offset
                if (layer != null && offset != null) {
                    translate(offset.x, offset.y) { drawLayer(layer) }
                }
            },
    ) {
        CompositionLocalProvider(LocalTopBarButtonOverlay provides overlay) {
            content()
        }
    }
}

@Composable
internal fun rememberMiuixTopBarButtonOverlayModifier(): Modifier {
    val overlay = LocalTopBarButtonOverlay.current ?: return Modifier
    val layer = rememberGraphicsLayer()
    DisposableEffect(overlay, layer) {
        overlay.layer = layer
        onDispose {
            if (overlay.layer === layer) {
                overlay.layer = null
                overlay.buttonCoordinates = null
                overlay.offset = null
            }
        }
    }
    return Modifier
        .onGloballyPositioned {
            overlay.buttonCoordinates = it
            overlay.updateOffset()
        }
        .drawWithContent {
            // 在缩放、阴影和圆形裁剪之前录制，避免把越界阴影再次裁进按钮大小的图层。
            // 只录制不在原槽位绘制，由顶栏外层统一回放，防止阴影叠加。
            layer.record { this@drawWithContent.drawContent() }
        }
}
