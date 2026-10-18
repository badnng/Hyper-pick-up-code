package com.Badnng.moe.widget

import android.content.Context
import android.graphics.Bitmap
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.helper.BrandIconResolver

/** 在 IO 线程读取，每个品牌只保留一份缩略图，避免原尺寸图片撑大 RemoteViews。 */
internal fun loadWidgetBrandIcons(context: Context, orders: List<OrderEntity>): Map<String, Bitmap> = buildMap {
    orders.mapNotNull { it.brandName?.takeIf(String::isNotBlank) }.distinct().forEach { brand ->
        val icon = try {
            BrandIconResolver.resolveCustomIconBitmap(context, brand, useCache = false)?.let { original ->
                val longestEdge = maxOf(original.width, original.height)
                if (longestEdge <= 64) original else {
                    try {
                        Bitmap.createScaledBitmap(original,
                            (original.width * 64L / longestEdge).toInt().coerceAtLeast(1),
                            (original.height * 64L / longestEdge).toInt().coerceAtLeast(1), true)
                    } finally {
                        // useCache=false 返回独立位图，不回收应用内界面共享的缓存。
                        original.recycle()
                    }
                }
            }
        } catch (_: Exception) {
            // 图标文件丢失或损坏时仍显示订单，由渲染层使用内置图标。
            null
        }
        if (icon != null) put(brand, icon)
    }
}
