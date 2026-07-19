package com.Badnng.moe.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import com.Badnng.moe.ui.miuix.rememberMiuixBackdrop
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.textureBlur

/**
 * BottomSheet 模糊背景遮罩
 * 替换默认的灰色半透明背景
 * 使用 spring 动画匹配 BottomSheet 的展开/收起时长
 */
@Composable
fun BlurBackdrop(show: Boolean) {
    val backdrop = rememberMiuixBackdrop()

    val animatedAlpha by animateFloatAsState(
        targetValue = if (show) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 300f)
    )

    if (animatedAlpha <= 0f) return

    if (backdrop != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .textureBlur(
                    backdrop = backdrop,
                    shape = RectangleShape,
                    blurRadius = 56f * animatedAlpha,
                    colors = BlurDefaults.blurColors(
                        brightness = 0.18f * animatedAlpha,
                        contrast = 1f + 0.2f * animatedAlpha,
                        saturation = 1f + 0.08f * animatedAlpha,
                    ),
                )
                .graphicsLayer(alpha = animatedAlpha),
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF5A5A5A).copy(alpha = 0.36f * animatedAlpha)),
        )
    }
}
