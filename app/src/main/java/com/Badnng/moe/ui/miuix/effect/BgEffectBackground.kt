package com.Badnng.moe.ui.miuix.effect

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.Badnng.moe.ui.miuix.rememberMiuixVisualEffectsAllowed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.floor

/**
 * Miuix 风格的动态背景效果
 * 参考示例项目的 BgEffectBackground 实现
 */
@Composable
fun BgEffectBackground(
    dynamicBackground: Boolean = true,
    modifier: Modifier = Modifier,
    bgModifier: Modifier = Modifier,
    isFullSize: Boolean = false,
    alpha: () -> Float = { 1f },
    content: @Composable (BoxScope.() -> Unit),
) {
    val visualEffectsAllowed = rememberMiuixVisualEffectsAllowed()
    val runtimeShaderSupported = remember { isRuntimeShaderSupported() }
    val shaderSupported = visualEffectsAllowed && runtimeShaderSupported
    if (!shaderSupported) {
        Box(modifier = modifier, content = content)
        return
    }

    Box(modifier = modifier) {
        val surface = MiuixTheme.colorScheme.surface
        val configuration = LocalConfiguration.current
        val deviceType = if (configuration.screenWidthDp >= 600) DeviceType.PAD else DeviceType.PHONE
        val context = LocalContext.current
        val appPrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
        var themeMode by remember { mutableStateOf(appPrefs.getString("theme_mode", "system") ?: "system") }
        DisposableEffect(appPrefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                if (key == "theme_mode") themeMode = p.getString(key, "system") ?: "system"
            }
            appPrefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { appPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        val isDarkTheme = when (themeMode) {
            "light" -> false
            "dark" -> true
            else -> isSystemInDarkTheme()
        }
        val painter = remember { BgEffectPainter() }

        val preset = remember(deviceType, isDarkTheme) {
            BgEffectConfig.get(deviceType, isDarkTheme)
        }

        val colorStage = remember { Animatable(0f) }

        LaunchedEffect(dynamicBackground, preset) {
            if (!dynamicBackground) return@LaunchedEffect
            val animatesColors = preset.colors1 !== preset.colors2 || preset.colors2 !== preset.colors3
            if (!animatesColors) return@LaunchedEffect

            var targetStage = floor(colorStage.value) + 1f
            while (isActive) {
                delay((preset.colorInterpPeriod * 500).toLong())
                colorStage.animateTo(
                    targetValue = targetStage,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 35f),
                )
                targetStage += 1f
            }
        }

        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .then(bgModifier)
                .bgEffectDraw(
                    painter = painter,
                    preset = preset,
                    deviceType = deviceType,
                    isDarkTheme = isDarkTheme,
                    surface = surface,
                    isFullSize = isFullSize,
                    playing = dynamicBackground,
                    colorStage = { colorStage.value },
                    alpha = alpha,
                ),
        )
        content()
    }
}
