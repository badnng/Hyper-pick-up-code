package com.Badnng.moe.helper

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.Display
import android.view.Surface
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

        // 某些系统在“由应用决定刷新率”时会默认锁到 60Hz；这里主动请求当前屏幕支持的最高刷新率。
        applyMaxRefreshRate(activity, activity.display ?: window.decorView.display)

        // 某些系统会在窗口附着后覆盖一次状态栏图标样式，这里再补一遍。
        window.decorView.post {
            val c = WindowInsetsControllerCompat(window, window.decorView)
            c.isAppearanceLightStatusBars = !isDarkTheme
            c.isAppearanceLightNavigationBars = !isDarkTheme

            // 视图附着后 display 才稳定，补一遍刷新率请求。
            applyMaxRefreshRate(activity, activity.display ?: window.decorView.display)
        }
    }

    /**
     * 快速查看窗口会在原页面上绘制深色半透明遮罩，因此系统栏必须保持透明并使用浅色图标。
     */
    fun applyQuickViewEdgeToEdge(activity: Activity) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        fun applySystemBarAppearance() {
            WindowInsetsControllerCompat(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
                show(WindowInsetsCompat.Type.systemBars())
            }
        }

        applySystemBarAppearance()
        applyMaxRefreshRate(activity, activity.display ?: window.decorView.display)
        window.decorView.post {
            applySystemBarAppearance()
            applyMaxRefreshRate(activity, activity.display ?: window.decorView.display)
        }
    }

    private fun applyMaxRefreshRate(activity: Activity, display: Display?) {
        try {
            val d = display ?: return
            val supported = d.supportedModes.toList()
            if (supported.isEmpty()) return

            val currentMode = d.mode
            val sameRes = supported.filter {
                it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight
            }
            val best = sameRes.ifEmpty { supported }.maxByOrNull { it.refreshRate } ?: return

            val window = activity.window
            val lp = window.attributes
            var changed = false

            if (lp.preferredDisplayModeId != best.modeId) {
                lp.preferredDisplayModeId = best.modeId
                changed = true
            }
            if (lp.preferredRefreshRate != best.refreshRate) {
                lp.preferredRefreshRate = best.refreshRate
                changed = true
            }

            if (changed) {
                window.attributes = lp
            }

            // 部分系统的“由应用决定”更偏向于看 setFrameRate 提示，补一层兜底。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val method = window.javaClass.getMethod(
                        "setFrameRate",
                        java.lang.Float.TYPE,
                        Integer.TYPE,
                        Integer.TYPE
                    )
                    method.invoke(
                        window,
                        best.refreshRate,
                        Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                        Surface.CHANGE_FRAME_RATE_ALWAYS
                    )
                } catch (_: Exception) {
                    // ignore
                }
            }
        } catch (_: Exception) {
            // ignore
        }
    }

}
