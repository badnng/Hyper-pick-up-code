package com.Badnng.moe.ui.oobe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.ImageView
import kotlin.math.ceil

/** Renders the bold, tightly spaced wordmark as an alpha mask for HyperOS blending. */
internal class OobeWordmarkView(context: Context) : ImageView(context) {
    private var maskBitmap: Bitmap? = null

    init {
        scaleType = ScaleType.CENTER
        adjustViewBounds = true
        contentDescription = null
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        rebuildMask()
    }

    private fun rebuildMask() {
        val metrics = resources.displayMetrics
        val padding = ceil(metrics.density).toInt().coerceAtLeast(1)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = Color.WHITE
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 35f, metrics)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val fontMetrics = paint.fontMetrics
        val width = ceil(paint.measureText(WORDMARK)).toInt() + padding * 2
        val height = ceil(fontMetrics.descent - fontMetrics.ascent).toInt() + padding * 2
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            density = metrics.densityDpi
        }
        Canvas(bitmap).drawText(
            WORDMARK,
            padding.toFloat(),
            padding - fontMetrics.ascent,
            paint,
        )

        val previous = maskBitmap
        maskBitmap = bitmap
        setImageBitmap(bitmap)
        previous?.recycle()
    }

    private companion object {
        const val WORDMARK = "澎湃记"
    }
}
