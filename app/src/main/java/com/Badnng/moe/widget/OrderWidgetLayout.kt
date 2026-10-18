package com.Badnng.moe.widget

import androidx.compose.ui.unit.DpSize

/**
 * 桌面格位不一定是正方形，2×2 的可用区域可能比同列文件夹略高。
 * 对接近方形的区域按宽度绘制方形卡片；明确的高版（如 3×4）仍用满高度。
 * 根据实际宽高判断，因此从其他预设缩成 2×2 时也采用同样的外形。
 */
internal fun widgetCardSize(available: DpSize): DpSize {
    val squareLike = available.height >= available.width && available.height <= available.width * 1.25f
    return if (squareLike) DpSize(available.width, available.width) else available
}
