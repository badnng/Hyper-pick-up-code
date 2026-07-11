package com.Badnng.moe.ui.oobe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.content.res.ColorStateList
import android.widget.ImageView
import com.Badnng.moe.R

/** Draws the app mark as a transparent engraving in a solid, blur-tinted tile. */
internal class OobeCarvedLogoView(context: Context) : ImageView(context) {
    private var logoMask: Bitmap? = null

    init {
        scaleType = ScaleType.FIT_XY
        contentDescription = null
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setBaseColor(color: Int) {
        imageTintList = ColorStateList.valueOf(color)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0) return
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            density = resources.displayMetrics.densityDpi
        }
        val canvas = Canvas(bitmap)
        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val cornerRadius = minOf(width, height) * CORNER_RADIUS_RATIO
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerRadius, cornerRadius, tilePaint)

        val markMask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        context.getDrawable(R.drawable.abouttopicon)?.mutate()?.apply {
            setTint(Color.WHITE)
            setBounds(0, 0, width, height)
            draw(Canvas(markMask))
        }
        canvas.drawBitmap(
            markMask,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            },
        )
        markMask.recycle()

        val previous = logoMask
        logoMask = bitmap
        setImageBitmap(bitmap)
        previous?.recycle()
    }

    private companion object {
        const val CORNER_RADIUS_RATIO = 0.25f
    }
}
