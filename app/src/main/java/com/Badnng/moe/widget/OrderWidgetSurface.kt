package com.Badnng.moe.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.unit.ColorProvider
import kotlin.math.ceil
import top.yukonga.miuix.kmp.squircle.addSquircleRect

internal val WidgetButtonShadowSpace = 3.dp

/**
 * Glance 不能使用 Compose 绘制 modifier，因此复用 Miuix 的公开路径 API 绘制背景。
 * 按实际尺寸生成，圆角始终是 dp 值，不能拉伸正方形 vector（会把大卡片圆角拉成几十 dp）。
 * 2 像素/dp 足以平滑边缘且限制 RemoteViews 的位图体积；颜色交给桌面的日/夜 tint。
 */
@Composable
internal fun GlanceModifier.widgetSurface(
    color: ColorProvider,
    miuix: Boolean,
    size: DpSize,
    radius: Dp = 15.dp,
): GlanceModifier {
    if (!miuix) return background(color).cornerRadius(radius)
    val bitmap = remember(size, radius) {
        widgetSurfaceBitmap(size, miuix = true, radius = radius)
    }
    return background(ImageProvider(bitmap), colorFilter = ColorFilter.tint(color))
}

internal fun widgetSurfaceBitmap(size: DpSize, miuix: Boolean, radius: Dp = 15.dp): Bitmap {
    val width = ceil(size.width.value * 2).toInt().coerceAtLeast(1)
    val height = ceil(size.height.value * 2).toInt().coerceAtLeast(1)
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        density = Bitmap.DENSITY_NONE
        val canvas = Canvas(this)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        if (miuix) {
            val path = Path().apply { addSquircleRect(width.toFloat(), height.toFloat(), radius.value * 2) }
            canvas.drawPath(path.asAndroidPath(), paint)
        } else {
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius.value * 2, radius.value * 2, paint)
        }
    }
}

/** 按钮表面与阴影分别着色，底部 3dp 留给投影，避免把橙色/红色按钮染黑。 */
@Composable
internal fun GlanceModifier.widgetButtonShadow(color: ColorProvider, miuix: Boolean, size: DpSize): GlanceModifier {
    val bitmap = remember(size, miuix) {
        val width = ceil(size.width.value * 2).toInt().coerceAtLeast(1)
        val bodyHeight = ceil(size.height.value * 2).toInt().coerceAtLeast(1)
        val height = bodyHeight + (WidgetButtonShadowSpace.value * 2).toInt()
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            density = Bitmap.DENSITY_NONE
            val canvas = Canvas(this)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = android.graphics.Color.WHITE
                setShadowLayer(4f, 0f, 2f, android.graphics.Color.WHITE)
            }
            if (miuix) {
                val path = Path().apply { addSquircleRect(width.toFloat(), bodyHeight.toFloat(), 30f) }
                canvas.drawPath(path.asAndroidPath(), paint)
            } else {
                canvas.drawRoundRect(0f, 0f, width.toFloat(), bodyHeight.toFloat(), 30f, 30f, paint)
            }
        }
    }
    return background(ImageProvider(bitmap), colorFilter = ColorFilter.tint(color))
}

/** 阴影和细边都绘制在选择器的原有边界内，随同一个 ImageView 平移。 */
internal fun widgetFilterIndicatorBitmap(
    size: DpSize,
    miuix: Boolean,
    fill: Color,
    shadow: Color,
    edge: Color,
): Bitmap {
    val width = ceil(size.width.value * 2).toInt().coerceAtLeast(1)
    val height = ceil(size.height.value * 2).toInt().coerceAtLeast(1)
    val innerWidth = (width - 4).coerceAtLeast(1).toFloat()
    val innerHeight = (height - 4).coerceAtLeast(1).toFloat()
    val path = if (miuix) {
        Path().apply { addSquircleRect(innerWidth, innerHeight, 30f) }.asAndroidPath()
    } else {
        android.graphics.Path().apply {
            addRoundRect(0f, 0f, innerWidth, innerHeight, 30f, 30f, android.graphics.Path.Direction.CW)
        }
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        density = Bitmap.DENSITY_NONE
        val canvas = Canvas(this)
        canvas.translate(2f, 1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fill.toArgb()
            setShadowLayer(2f, 0f, 1f, shadow.toArgb())
        }
        canvas.drawPath(path, paint)
        paint.clearShadowLayer()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        // 浅色用淡轮廓，深色用顶部更亮的细高光，避免暗色面板上阴影不可辨识。
        paint.shader = LinearGradient(0f, 0f, 0f, innerHeight,
            edge.copy(alpha = 0.16f).toArgb(), edge.copy(alpha = 0.04f).toArgb(), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
    }
}

/** 阴影位于分类栏自己的尺寸内，留出底部 2dp，避免扩大 2×2 的表头高度。 */
@Composable
internal fun GlanceModifier.widgetFilterShadow(color: ColorProvider, size: DpSize): GlanceModifier {
    val bitmap = remember(size) {
        val width = ceil(size.width.value * 2).toInt().coerceAtLeast(1)
        val height = ceil(size.height.value * 2).toInt().coerceAtLeast(1)
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            density = Bitmap.DENSITY_NONE
            val path = Path().apply {
                addSquircleRect((width - 8).coerceAtLeast(1).toFloat(),
                    (height - 6).coerceAtLeast(1).toFloat(), 30f)
            }
            val canvas = Canvas(this)
            canvas.translate(4f, 2f)
            canvas.drawPath(path.asAndroidPath(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = android.graphics.Color.WHITE
                setShadowLayer(3f, 0f, 2f, android.graphics.Color.WHITE)
            })
        }
    }
    // 单独着色阴影，不能和面板共用 tint，否则深色模式下会变成浅色光晕。
    return background(ImageProvider(bitmap), colorFilter = ColorFilter.tint(color))
}
