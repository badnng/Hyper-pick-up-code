package com.Badnng.moe.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider as DayNightColors
import com.Badnng.moe.ui.theme.ColorGenerator
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.ThemeController

/** 桌面有独立的 Glance 控件树，统一在此将应用的主题偏好转换成语义色。 */
internal data class OrderWidgetAppearance(
    val miuix: Boolean,
    val background: ColorProvider,
    val surface: ColorProvider,
    val text: ColorProvider,
    val secondaryText: ColorProvider,
    val accent: ColorProvider,
    val selected: ColorProvider,
    val onSelected: ColorProvider,
    val button: ColorProvider,
    val onButton: ColorProvider,
    val filterTrack: ColorProvider,
    val divider: ColorProvider,
    val shadow: ColorProvider,
    val taobao: WidgetButtonColors,
    val pdd: WidgetButtonColors,
) {
    companion object {
        val preferenceKeys = setOf(
            "ui_style", "theme_mode", "theme_color", "key_color_index",
            "monet_enabled", "miuix_monet_enabled", "amoled_pure_black",
        )

        @Composable
        fun read(context: Context, revision: Long): OrderWidgetAppearance {
            // SharedPreferences 不是 Compose 状态；让刷新修订号参与组合，避免订单未变时
            // 强跳过优化跳过此函数，导致切换 UI 风格后仍保留旧颜色。
            val prefs = remember(context, revision) {
                context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            }
            val miuix = prefs.getString("ui_style", "miuix") != "md3e"
            val mode = prefs.getString("theme_mode", "system")
            val monet = prefs.getBoolean(if (miuix) "miuix_monet_enabled" else "monet_enabled", !miuix)
            val pureBlack = prefs.getBoolean("amoled_pure_black", false)
            // 与应用 ThemeController 一致：普通 Light/Dark 使用 Miuix 原生语义色，
            // Monet 才使用壁纸取色。不能把 Material ColorScheme 当作 Miuix 调色板。
            if (miuix) {
                val light = ThemeController(
                    colorSchemeMode = if (monet) ColorSchemeMode.MonetLight else ColorSchemeMode.Light,
                ).currentColors()
                val dark = ThemeController(
                    colorSchemeMode = if (monet) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark,
                ).currentColors()
                return fromPalettes(true, mode, miuixWidgetPalette(light),
                    miuixWidgetPalette(dark, pureBlack))
            }
            val seed = prefs.getInt("theme_color", 0xFF6750A4.toInt())
            fun palette(dark: Boolean): WidgetPalette {
                val colors = if (monet) {
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else ColorGenerator.seedToColorScheme(seed, dark)
                return WidgetPalette(
                    background = if (dark && pureBlack) Color.Black else colors.surfaceContainerLow,
                    surface = colors.surfaceContainer,
                    text = colors.onSurface,
                    secondaryText = colors.onSurfaceVariant,
                    accent = colors.primary,
                    selected = colors.primaryContainer,
                    onSelected = colors.onPrimaryContainer,
                    button = colors.secondaryContainer,
                    onButton = colors.onSecondaryContainer,
                    filterTrack = Color.Transparent,
                    divider = colors.outlineVariant,
                )
            }
            return fromPalettes(false, mode, palette(false), palette(true))
        }

        internal fun fromPalettes(
            miuix: Boolean, mode: String?, light: WidgetPalette, dark: WidgetPalette,
        ): OrderWidgetAppearance {
            // 将日/夜两套颜色一并发送给桌面，应用进程退出后系统切换深色也能更新。
            fun color(value: (WidgetPalette) -> Color): ColorProvider = when (mode) {
                "light" -> ColorProvider(value(light))
                "dark" -> ColorProvider(value(dark))
                else -> DayNightColors(value(light), value(dark))
            }
            fun dayNight(day: Color, night: Color): ColorProvider = when (mode) {
                "light" -> ColorProvider(day)
                "dark" -> ColorProvider(night)
                else -> DayNightColors(day, night)
            }
            return OrderWidgetAppearance(
                miuix = miuix,
                background = color { it.background },
                surface = color { it.surface },
                text = color { it.text },
                secondaryText = color { it.secondaryText },
                accent = color { it.accent },
                selected = color { it.selected },
                onSelected = color { it.onSelected },
                button = color { it.button },
                onButton = color { it.onButton },
                filterTrack = color { it.filterTrack },
                divider = color { it.divider },
                shadow = dayNight(Color.Black.copy(alpha = 0.20f), Color.Black.copy(alpha = 0.50f)),
                // 平台身份色独立于应用主题色；浅色用淡底深字，深色用深底亮字。
                taobao = WidgetButtonColors(
                    background = dayNight(Color(0xFFFFE5CE), Color(0xFF4D2C12)),
                    content = dayNight(Color(0xFF8F3D00), Color(0xFFFFBC80)),
                ),
                pdd = WidgetButtonColors(
                    background = dayNight(Color(0xFFFFE0E0), Color(0xFF4F2024)),
                    content = dayNight(Color(0xFF9F1C1C), Color(0xFFFFB4B4)),
                ),
            )
        }
    }
}

internal data class WidgetButtonColors(val background: ColorProvider, val content: ColorProvider)

internal data class WidgetPalette(
    val background: Color,
    val surface: Color,
    val text: Color,
    val secondaryText: Color,
    val accent: Color,
    val selected: Color,
    val onSelected: Color,
    val button: Color,
    val onButton: Color,
    val filterTrack: Color,
    val divider: Color,
)

/** Miuix 卡片、分段选择器和普通按钮各用自己的 token，避免整块染成 MD3 色块。 */
internal fun miuixWidgetPalette(colors: Colors, pureBlack: Boolean = false) = WidgetPalette(
    background = if (pureBlack) Color.Black else colors.surfaceContainer,
    surface = colors.surfaceContainer,
    text = colors.onSurface,
    secondaryText = colors.onSurfaceVariantSummary,
    accent = colors.primary,
    selected = colors.surfaceContainer,
    onSelected = colors.onBackground,
    button = colors.secondaryVariant,
    onButton = colors.onSecondaryVariant,
    filterTrack = colors.surface,
    divider = colors.dividerLine,
)
