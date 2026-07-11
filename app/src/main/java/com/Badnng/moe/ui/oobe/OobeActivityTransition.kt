/*
 * The HyperOS activity transition call and circular PixelCopy capture follow
 * HyperCeiler provision at commit 7266aaa0d698ad10795381c5bf23651c2e1719d0.
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.Badnng.moe.ui.oobe

import android.app.Activity
import android.app.ActivityOptions
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import kotlin.math.min

internal object OobeActivityTransition {
    fun createLaunchOptions(
        activity: Activity,
        source: View,
        useHyperOsTransition: Boolean,
        hyperOsBlurEnabled: Boolean,
        onReady: (Bundle?) -> Unit,
    ) {
        if (source.width <= 0 || source.height <= 0) {
            onReady(null)
            return
        }
        val handler = Handler(Looper.getMainLooper())
        captureCircularBitmap(activity, source, handler) { bitmap ->
            val hyperOsOptions = if (useHyperOsTransition && bitmap != null) {
                createHyperOsOptions(
                    source = source,
                    bitmap = bitmap,
                    handler = handler,
                    foregroundColor = if (hyperOsBlurEnabled) 0x00FFFFFF else 0x99000000.toInt(),
                )
            } else {
                null
            }
            if (bitmap != null && hyperOsOptions == null && !bitmap.isRecycled) {
                bitmap.recycle()
            }
            val options = hyperOsOptions ?: runCatching {
                ActivityOptions.makeScaleUpAnimation(
                    source,
                    0,
                    0,
                    source.width,
                    source.height,
                )
            }.getOrNull()
            onReady(options?.toBundle())
        }
    }

    private fun createHyperOsOptions(
        source: View,
        bitmap: Bitmap,
        handler: Handler,
        foregroundColor: Int,
    ): ActivityOptions? {
        val location = IntArray(2)
        source.getLocationInWindow(location)
        val sourceContainer = source.tag as? View ?: source
        val onExitStarted = Runnable { sourceContainer.visibility = View.INVISIBLE }
        val onExitFinished = Runnable {
            source.visibility = View.VISIBLE
            sourceContainer.visibility = View.VISIBLE
        }
        val commonParameterTypes = arrayOf<Class<*>>(
            View::class.java,
            Bitmap::class.java,
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            java.lang.Float.TYPE,
            Handler::class.java,
            Runnable::class.java,
            Runnable::class.java,
            Runnable::class.java,
            Runnable::class.java,
        )
        val commonArguments = arrayOf<Any?>(
            source,
            bitmap,
            location[0],
            location[1],
            ((source.width - source.paddingLeft - source.paddingRight) / 2).coerceAtLeast(1),
            foregroundColor,
            1f,
            handler,
            onExitStarted,
            onExitFinished,
            null,
            null,
        )

        return invokeHiddenActivityOptions(
            methodName = "makeScaleUpDown",
            parameterTypes = commonParameterTypes + Integer.TYPE,
            arguments = commonArguments + ANIM_LAUNCH_ACTIVITY_FROM_ROUNDED_VIEW,
        ) ?: invokeHiddenActivityOptions(
            methodName = "makeScaleUpAnimationFromRoundedView",
            parameterTypes = commonParameterTypes,
            arguments = commonArguments,
        )
    }

    private fun invokeHiddenActivityOptions(
        methodName: String,
        parameterTypes: Array<Class<*>>,
        arguments: Array<out Any?>,
    ): ActivityOptions? = runCatching {
        ActivityOptions::class.java
            .getMethod(methodName, *parameterTypes)
            .apply { isAccessible = true }
            .invoke(null, *arguments) as? ActivityOptions
    }.getOrNull()

    private fun captureCircularBitmap(
        activity: Activity,
        source: View,
        handler: Handler,
        onReady: (Bitmap?) -> Unit,
    ) {
        val location = IntArray(2)
        source.getLocationInWindow(location)
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0 || activity.isFinishing || activity.isDestroyed) {
            onReady(null)
            return
        }
        val bitmap = runCatching {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }.getOrNull() ?: run {
            onReady(null)
            return
        }
        val area = Rect(
            location[0],
            location[1],
            location[0] + width,
            location[1] + height,
        )
        runCatching {
            PixelCopy.request(
                activity.window,
                area,
                bitmap,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        onReady(cropToCircle(bitmap))
                    } else {
                        bitmap.recycle()
                        onReady(null)
                    }
                },
                handler,
            )
        }.onFailure {
            bitmap.recycle()
            onReady(null)
        }
    }

    private fun cropToCircle(source: Bitmap): Bitmap {
        val size = min(source.width, source.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bounds = Rect(0, 0, size, size)
        canvas.drawARGB(0, 0, 0, 0)
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, null, bounds, paint)
        paint.xfermode = null
        source.recycle()
        return output
    }

    private const val ANIM_LAUNCH_ACTIVITY_FROM_ROUNDED_VIEW = 102
}
