package com.Badnng.moe

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

object EdgeToEdgeHelper {
    fun applyGestureEdgeToEdge(activity: Activity) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val prefs = activity.getSharedPreferences("settings", Activity.MODE_PRIVATE)
        val themeMode = prefs.getString("theme_mode", "system") ?: "system"
        val isSystemNight = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDarkTheme = when (themeMode) {
            "dark" -> true
            "light" -> false
            else -> isSystemNight
        }
        controller.isAppearanceLightStatusBars = !isDarkTheme
        controller.isAppearanceLightNavigationBars = !isDarkTheme
        controller.show(WindowInsetsCompat.Type.systemBars())

        // 某些系统会在窗口附着后覆盖一次状态栏图标样式，这里再补一遍。
        window.decorView.post {
            val c = WindowInsetsControllerCompat(window, window.decorView)
            c.isAppearanceLightStatusBars = !isDarkTheme
            c.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }
}
