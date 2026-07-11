/*
 * Derived from HyperCeiler provision at commit
 * 7266aaa0d698ad10795381c5bf23651c2e1719d0.
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.Badnng.moe.ui.oobe

import android.content.Context
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import com.Badnng.moe.ui.miuix.effect.BgEffectConfig
import com.Badnng.moe.ui.miuix.effect.DeviceType
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

@Composable
internal fun OobeGlowBackground(
    backendState: MutableState<OobeVisualBackend>,
    showOpeningCircle: Boolean,
    darkTheme: Boolean,
    circleYOffset: Float = 0.1f,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val backend = backendState.value
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val deviceType = if (configuration.screenWidthDp >= 600) DeviceType.PAD else DeviceType.PHONE
    val shaderSource = remember(backend) {
        if (backend == OobeVisualBackend.StaticFallback) {
            null
        } else {
            runCatching {
                val source = context.resources.openRawResource(com.Badnng.moe.R.raw.oobe_glow)
                    .bufferedReader()
                    .use { it.readText() }
                RuntimeShader(source)
                source
            }.getOrNull()
        }
    }

    LaunchedEffect(shaderSource, backend) {
        if (shaderSource == null && backend != OobeVisualBackend.StaticFallback) {
            backendState.value = OobeVisualBackendResolver.useStaticFallback()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (darkTheme && shaderSource == null) {
                    ComposeColor(0xFF101114)
                } else {
                    ComposeColor.Transparent
                },
            ),
    ) {
        if (shaderSource == null) {
            Image(
                painter = painterResource(com.Badnng.moe.R.drawable.oobe_logo_image_bg),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = if (darkTheme) 0.62f else 1f,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AndroidView(
                factory = { viewContext ->
                    OobeGlowRenderLayout(
                        context = viewContext,
                        shaderSource = shaderSource,
                        darkTheme = darkTheme,
                        deviceType = deviceType,
                        onFailure = {
                            backendState.value = OobeVisualBackendResolver.useStaticFallback()
                        },
                    )
                },
                update = { glowView ->
                    glowView.setTheme(darkTheme, deviceType)
                    glowView.setOpeningCircleVisible(showOpeningCircle)
                    glowView.setCircleYOffset(circleYOffset)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        content()
    }
}

/** Matches HyperCeiler's RenderViewLayout: render at 20% and scale the View back up. */
private class OobeGlowRenderLayout(
    context: Context,
    shaderSource: String,
    darkTheme: Boolean,
    deviceType: DeviceType,
    onFailure: () -> Unit,
) : ViewGroup(context) {
    private val renderTarget = View(context).apply {
        setBackgroundColor(Color.BLACK)
        contentDescription = null
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val controller = OobeGlowController(
        target = renderTarget,
        shaderSource = shaderSource,
        darkTheme = darkTheme,
        deviceType = deviceType,
        onFailure = onFailure,
    )

    init {
        addView(renderTarget)
    }

    fun setOpeningCircleVisible(visible: Boolean) {
        controller.setOpeningCircleVisible(visible)
    }

    fun setTheme(darkTheme: Boolean, deviceType: DeviceType) {
        controller.setTheme(darkTheme, deviceType)
    }

    fun setCircleYOffset(offset: Float) {
        controller.setCircleYOffset(offset)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
        val childWidth = ceil(measuredWidth * CHILD_SCALE).toInt()
        val childHeight = ceil(measuredHeight * CHILD_SCALE).toInt()
        renderTarget.measure(
            MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val childWidth = ceil(width * CHILD_SCALE).toInt()
        val childHeight = ceil(height * CHILD_SCALE).toInt()
        val childLeft = ((width - childWidth) * 0.5f).toInt()
        val childTop = ((height - childHeight) * 0.5f).toInt()
        renderTarget.scaleX = 1f / CHILD_SCALE
        renderTarget.scaleY = 1f / CHILD_SCALE
        renderTarget.layout(
            childLeft,
            childTop,
            childLeft + childWidth,
            childTop + childHeight,
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        controller.start()
    }

    override fun onDetachedFromWindow() {
        controller.stop()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val CHILD_SCALE = 0.2f
    }
}

private class OobeGlowController(
    private val target: View,
    private val shaderSource: String,
    private var darkTheme: Boolean,
    private var deviceType: DeviceType,
    private val onFailure: () -> Unit,
) : Runnable {
    private var painter: OobeGlowPainter? = null
    private var time = 0f
    private var timeDirection = 1f
    private var lastGlobalTime = 0L
    private var openingCircleVisible = true
    private var circleYOffset = 0.1f
    private var failureReported = false

    fun start() {
        if (painter != null) return
        runCatching {
            OobeGlowPainter(shaderSource).also {
                it.setTheme(darkTheme, deviceType)
                it.setOpeningCircleVisible(openingCircleVisible)
                it.setCircleYOffset(circleYOffset)
            }
        }.onSuccess {
            painter = it
            lastGlobalTime = System.nanoTime()
            time = 0f
            target.post(this)
        }.onFailure {
            reportFailure()
        }
    }

    override fun run() {
        val currentPainter = painter ?: return
        runCatching {
            tickPingPong()
            currentPainter.setAnimTime(time)
            currentPainter.setResolution(target.width.toFloat(), target.height.toFloat())
            target.setRenderEffect(currentPainter.createRenderEffect())
            target.postDelayed(this, FRAME_DELAY_MILLIS)
        }.onFailure {
            reportFailure()
        }
    }

    fun setOpeningCircleVisible(visible: Boolean) {
        openingCircleVisible = visible
        painter?.setOpeningCircleVisible(visible)
    }

    fun setTheme(darkTheme: Boolean, deviceType: DeviceType) {
        this.darkTheme = darkTheme
        this.deviceType = deviceType
        painter?.setTheme(darkTheme, deviceType)
    }

    fun setCircleYOffset(offset: Float) {
        circleYOffset = offset
        painter?.setCircleYOffset(offset)
    }

    fun stop() {
        target.removeCallbacks(this)
        painter = null
        target.setRenderEffect(null)
    }

    private fun tickPingPong() {
        val globalTime = System.nanoTime()
        val deltaTime = ((globalTime - lastGlobalTime) * 1.0E-9).toFloat()
        time += deltaTime * timeDirection
        if (timeDirection > 0f && time >= 120f) {
            timeDirection = -1f
        } else if (timeDirection < 0f && time <= 2f) {
            timeDirection = 1f
        }
        lastGlobalTime = globalTime
    }

    private fun reportFailure() {
        stop()
        if (!failureReported) {
            failureReported = true
            target.post { onFailure() }
        }
    }

    private companion object {
        const val FRAME_DELAY_MILLIS = 16L
    }
}

private class OobeGlowPainter(source: String) {
    private val shader = RuntimeShader(source)
    private val darkColors = FloatArray(16)
    private var darkPreset: BgEffectConfig.Config? = null
    private var lastDarkTheme: Boolean? = null
    private var lastDeviceType: DeviceType? = null

    init {
        shader.setFloatUniform("uScale2", 0.82f)
        shader.setFloatUniform("uSpeed2", 0.49f)
        shader.setFloatUniform("uColorInMin", 0.3f)
        shader.setFloatUniform("uColorInMax", 1f)
        shader.setFloatUniform("uColorOutMin", 0.3f)
        shader.setFloatUniform("uColorOutMax", 0.86f)
        shader.setFloatUniform("uColorMidPoint", 0.47f)
        shader.setFloatUniform("uUseOklab", 1f)
        shader.setFloatUniform("uColorBlack", 0.961f, 0.157f, 0.157f)
        shader.setFloatUniform("uColorMid", 0.604f, 0.659f, 0.961f)
        shader.setFloatUniform("uColorWhite", 0.302f, 0.29f, 0.843f)
        shader.setFloatUniform("uScale", 1.3f)
        shader.setFloatUniform("uSpeed", 0.4f)
        shader.setFloatUniform("uBrightnessInMin", 0.25f)
        shader.setFloatUniform("uBrightnessInMax", 1f)
        shader.setFloatUniform("uBrightnessOutMin", 0.25f)
        shader.setFloatUniform("uBrightnessOutMax", 1f)
        shader.setFloatUniform("uShowCircle", 1f)
        shader.setFloatUniform("uCircleThickness", 0.4f)
        shader.setFloatUniform("uCircleFinalRadius", 1f)
        shader.setFloatUniform("uCircleYOffset", 0.1f)
        shader.setFloatUniform("uCircleSpeed", 0.9f)
        shader.setFloatUniform("uCircleColorFreq", 1f)
        shader.setFloatUniform("uCircleColorSpeed", 0f)
        shader.setFloatUniform("uCircleEasing", 1.4f)
        shader.setFloatUniform("uCircleAnimationOffset", 0f)
        shader.setFloatUniform("uMaskDelay", 0.3f)
        shader.setFloatUniform("uMaskThickness", 0.3f)
        shader.setFloatUniform("uCircleScreenBlend", 1f)
        shader.setFloatUniform("uCircleAddBlend", 0.04f)
        shader.setFloatUniform("uCircleColorOffset", 0.25f)
        shader.setFloatUniform("uCircleUVDistort", 0f)
        shader.setFloatUniform("uColorToDistortWidthRatio", 0.6f)
        shader.setFloatUniform("uDistortStartTime", 0.2f)
        shader.setFloatUniform("uDistortEndTime", 0.3f)
        shader.setFloatUniform("uDistortStart", 0f)
        shader.setFloatUniform("uDistortEnd", 1f)
        shader.setFloatUniform("uStripeFrequency", 0f)
        shader.setFloatUniform("uStripeStrengthX", 0f)
        shader.setFloatUniform("uStripeStrengthY", 0f)
        shader.setFloatUniform("uStripeUVDistort", 0f)
    }

    fun createRenderEffect(): RenderEffect = RenderEffect.createShaderEffect(shader)

    fun setAnimTime(value: Float) {
        updateDarkPalette(value)
        shader.setFloatUniform("uTime", value)
    }

    fun setTheme(darkTheme: Boolean, deviceType: DeviceType) {
        if (lastDarkTheme == darkTheme && lastDeviceType == deviceType) return
        lastDarkTheme = darkTheme
        lastDeviceType = deviceType

        if (darkTheme) {
            darkPreset = BgEffectConfig.get(deviceType, isDark = true)
            shader.setFloatUniform("uColorOutMin", 0.16f)
            shader.setFloatUniform("uColorOutMax", 0.84f)
            shader.setFloatUniform("uColorMidPoint", 0.5f)
            shader.setFloatUniform("uBrightnessOutMin", 0.02f)
            shader.setFloatUniform("uBrightnessOutMax", 0.22f)
            updateDarkPalette(0f)
        } else {
            darkPreset = null
            shader.setFloatUniform("uColorOutMin", 0.3f)
            shader.setFloatUniform("uColorOutMax", 0.86f)
            shader.setFloatUniform("uColorMidPoint", 0.47f)
            shader.setFloatUniform("uColorBlack", 0.961f, 0.157f, 0.157f)
            shader.setFloatUniform("uColorMid", 0.604f, 0.659f, 0.961f)
            shader.setFloatUniform("uColorWhite", 0.302f, 0.29f, 0.843f)
            shader.setFloatUniform("uBrightnessOutMin", 0.25f)
            shader.setFloatUniform("uBrightnessOutMax", 1f)
        }
    }

    fun setResolution(width: Float, height: Float) {
        shader.setFloatUniform("uResolution", width, height)
    }

    fun setOpeningCircleVisible(visible: Boolean) {
        shader.setFloatUniform("uShowCircle", if (visible) 1f else 0f)
    }

    fun setCircleYOffset(value: Float) {
        shader.setFloatUniform("uCircleYOffset", value)
    }

    private fun updateDarkPalette(time: Float) {
        val preset = darkPreset ?: return
        val stage = time / preset.colorInterpPeriod
        val wholeStage = floor(stage).toInt()
        val fraction = stage - wholeStage
        val easedFraction = fraction * fraction * (3f - 2f * fraction)
        val first = colorStage(preset, wholeStage % 3)
        val second = colorStage(preset, (wholeStage + 1) % 3)

        for (index in darkColors.indices) {
            darkColors[index] = first[index] + (second[index] - first[index]) * easedFraction
        }

        setCompositedColor("uColorBlack", colorIndex = 0)
        setCompositedMidColor("uColorMid", firstColorIndex = 1, secondColorIndex = 3)
        setCompositedColor("uColorWhite", colorIndex = 2)
    }

    private fun colorStage(preset: BgEffectConfig.Config, stage: Int): FloatArray =
        when (stage) {
            0 -> preset.colors1
            1 -> preset.colors2
            else -> preset.colors3
        }

    private fun setCompositedColor(uniform: String, colorIndex: Int) {
        shader.setFloatUniform(
            uniform,
            compositedComponent(colorIndex, component = 0),
            compositedComponent(colorIndex, component = 1),
            compositedComponent(colorIndex, component = 2),
        )
    }

    private fun setCompositedMidColor(
        uniform: String,
        firstColorIndex: Int,
        secondColorIndex: Int,
    ) {
        shader.setFloatUniform(
            uniform,
            (compositedComponent(firstColorIndex, 0) +
                compositedComponent(secondColorIndex, 0)) * 0.5f,
            (compositedComponent(firstColorIndex, 1) +
                compositedComponent(secondColorIndex, 1)) * 0.5f,
            (compositedComponent(firstColorIndex, 2) +
                compositedComponent(secondColorIndex, 2)) * 0.5f,
        )
    }

    private fun compositedComponent(colorIndex: Int, component: Int): Float {
        val offset = colorIndex * 4
        val alpha = darkColors[offset + 3].coerceIn(0f, 1f)
        val surface = when (component) {
            0 -> 16f / 255f
            1 -> 17f / 255f
            else -> 20f / 255f
        }
        return darkColors[offset + component] * alpha + surface * (1f - alpha)
    }
}

internal fun Modifier.oobeCircularReveal(
    progress: () -> Float,
    center: () -> Offset,
): Modifier = this.then(
    Modifier.drawWithContent {
        val origin = center()
        val maxRadius = maxOf(
            hypot(origin.x, origin.y),
            hypot(size.width - origin.x, origin.y),
            hypot(origin.x, size.height - origin.y),
            hypot(size.width - origin.x, size.height - origin.y),
        )
        val radius = maxRadius * progress().coerceIn(0f, 1f)
        val path = Path().apply {
            addOval(Rect(origin - Offset(radius, radius), origin + Offset(radius, radius)))
        }
        clipPath(path) { this@drawWithContent.drawContent() }
    },
)
