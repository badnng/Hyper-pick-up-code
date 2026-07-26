package com.Badnng.moe.ui.miuix.effect

import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.cos
import kotlin.math.sin

internal class BgEffectPainter {

    private val shader = RuntimeShader(OS3_BG_FRAG)
    private val brush = ShaderBrush(shader)

    // Cache
    private var lastWidth = 0f
    private var lastHeight = 0f
    private var lastAnimTime = 0f
    private var lastColorStage = -1f
    private var lastDeviceType: DeviceType? = null
    private var lastIsDark = false

    private val pointsBuffer = FloatArray(12) // 4 vec3
    private val pointsAnimBuffer = FloatArray(8) // 4 vec2
    private val colorsBuffer = FloatArray(16) // 4 vec4

    fun getBrush(): Brush = brush

    fun updateResolution(width: Float, height: Float) {
        if (width == lastWidth && height == lastHeight) return
        lastWidth = width
        lastHeight = height
        shader.setFloatUniform("uResolution", width, height)
    }

    fun updateAnimTime(time: Float) {
        if (time == lastAnimTime) return
        lastAnimTime = time
        shader.setFloatUniform("uAnimTime", time)
    }

    fun updateBoundIfNeeded(drawHeight: Float, totalHeight: Float, totalWidth: Float) {
        val heightRatio = drawHeight / totalHeight
        if (totalWidth <= totalHeight) {
            shader.setFloatUniform(
                "uBound",
                0f,
                1f - heightRatio,
                1f,
                heightRatio,
            )
        } else {
            val aspectRatio = totalWidth / totalHeight
            val contentCenterY = 1f - heightRatio / 2f
            shader.setFloatUniform(
                "uBound",
                0f,
                contentCenterY - aspectRatio / 2f,
                1f,
                aspectRatio,
            )
        }
    }

    fun updatePresetIfNeeded(deviceType: DeviceType, isDark: Boolean) {
        if (deviceType == lastDeviceType && isDark == lastIsDark) return
        lastDeviceType = deviceType
        lastIsDark = isDark
        val preset = BgEffectConfig.get(deviceType, isDark)

        // Set points as vec3 array (4 points) - must set all at once
        System.arraycopy(preset.points, 0, pointsBuffer, 0, 12)
        shader.setFloatUniform("uPoints", pointsBuffer)

        // Set static uniforms
        shader.setFloatUniform("uTranslateY", 0f)
        shader.setFloatUniform("uAlphaMulti", 1f)
        shader.setFloatUniform("uNoiseScale", 1.5f)
        shader.setFloatUniform("uPointRadiusMulti", 1f)
        shader.setFloatUniform("uLightOffset", preset.lightOffset)
        shader.setFloatUniform("uSaturateOffset", preset.saturateOffset)
    }

    fun updateColors(preset: BgEffectConfig.Config, stage: Float) {
        val intStage = stage.toInt()
        val fraction = stage - intStage
        // 每帧都更新颜色以确保插值平滑（不缓存整数阶段）
        lastColorStage = stage
        val idx1 = intStage % 3
        val idx2 = (intStage + 1) % 3

        val colors1 = when (idx1) {
            0 -> preset.colors1
            1 -> preset.colors2
            else -> preset.colors3
        }
        val colors2 = when (idx2) {
            0 -> preset.colors1
            1 -> preset.colors2
            else -> preset.colors3
        }

        // Interpolate colors
        for (i in 0 until 16) {
            colorsBuffer[i] = colors1[i] + (colors2[i] - colors1[i]) * fraction
        }
        // Set colors as vec4 array (4 colors) - must set all at once
        shader.setFloatUniform("uColors", colorsBuffer)
    }

    fun updatePointsAnim(time: Float, preset: BgEffectConfig.Config) {
        val pointOffset = preset.pointOffset
        for (i in 0 until 4) {
            val baseX = preset.points[i * 3]
            val baseY = preset.points[i * 3 + 1]
            val angle = time * (1f + i * 0.3f) * pointOffset
            pointsAnimBuffer[i * 2] = baseX + sin(angle) * 0.1f
            pointsAnimBuffer[i * 2 + 1] = baseY + cos(angle) * 0.1f
        }
        // Set pointsAnim as vec2 array (4 points) - must set all at once
        shader.setFloatUniform("uPointsAnim", pointsAnimBuffer)
    }
}
