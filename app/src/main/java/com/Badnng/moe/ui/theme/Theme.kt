package com.Badnng.moe.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

@Composable
fun 澎湃记Theme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    
    // 使用 State 封装配置，并添加监听器实现实时刷新
    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "system")) }
    var monetEnabled by remember { mutableStateOf(prefs.getBoolean("monet_enabled", true)) }
    var seedColorInt by remember { mutableIntStateOf(prefs.getInt("theme_color", Color(0xFF6750A4).toArgb())) }

    // 监听 SharedPreferences 的变化
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                "theme_mode" -> themeMode = p.getString(key, "system")
                "monet_enabled" -> monetEnabled = p.getBoolean(key, true)
                "theme_color" -> seedColorInt = p.getInt(key, Color(0xFF6750A4).toArgb())
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        monetEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            val seedColor = Color(seedColorInt)
            if (darkTheme) {
                darkColorScheme(primary = seedColor, secondaryContainer = seedColor.copy(alpha = 0.2f))
            } else {
                lightColorScheme(primary = seedColor, secondaryContainer = seedColor.copy(alpha = 0.1f))
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}