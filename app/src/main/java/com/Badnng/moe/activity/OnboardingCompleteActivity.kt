package com.Badnng.moe.activity

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.Badnng.moe.R
import com.Badnng.moe.helper.EdgeToEdgeHelper
import com.Badnng.moe.ui.oobe.OobeHomeReadiness
import com.Badnng.moe.ui.screen.OnboardingScreen
import com.Badnng.moe.ui.theme.ColorGenerator
import com.Badnng.moe.ui.theme.MD3E_MONET_ENABLED_KEY

class OnboardingCompleteActivity : ComponentActivity() {
    private var flowFinished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.applyGestureEdgeToEdge(this)
        setContent {
            OnboardingScreen(
                showWelcome = false,
                startAtComplete = true,
                onComplete = ::finishFlow,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        EdgeToEdgeHelper.applyGestureEdgeToEdge(this)
    }

    private fun finishFlow() {
        if (!OobeHomeReadiness.isReady || flowFinished) return
        flowFinished = true
        getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_completed", true)
            .putBoolean("show_onboarding_on_next_launch", false)
            .apply()
        setResult(RESULT_OK)
        finishIntoPreparedHome()
    }

    private fun finishIntoPreparedHome() {
        val transitionBackground = resolveHomeTransitionBackground()
        overrideActivityTransition(
            Activity.OVERRIDE_TRANSITION_CLOSE,
            R.anim.oobe_home_enter_hold,
            R.anim.oobe_completion_exit,
            transitionBackground,
        )
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(
            R.anim.oobe_home_enter_hold,
            R.anim.oobe_completion_exit,
            transitionBackground,
        )
    }

    private fun resolveHomeTransitionBackground(): Int {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val darkTheme = when (prefs.getString("theme_mode", "system")) {
            "light" -> false
            "dark" -> true
            else -> systemDark
        }

        if (prefs.getString("ui_style", "miuix") != "md3e") {
            return if (darkTheme) 0xFF101114.toInt() else 0xFFF7F7FA.toInt()
        }
        if (darkTheme && prefs.getBoolean("amoled_pure_black", false)) {
            return Color.BLACK
        }

        val colorScheme = if (prefs.getBoolean(MD3E_MONET_ENABLED_KEY, true)) {
            if (darkTheme) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
        } else {
            ColorGenerator.seedToColorScheme(
                seedColor = prefs.getInt("theme_color", 0xFF6750A4.toInt()),
                isDark = darkTheme,
            )
        }
        return colorScheme.background.toArgb()
    }
}
