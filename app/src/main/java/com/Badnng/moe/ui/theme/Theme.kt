package com.Badnng.moe.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.Badnng.moe.ui.LocalAppUi
import com.Badnng.moe.ui.LocalIsMiuixUi
import com.Badnng.moe.ui.md3eAppUi
import com.Badnng.moe.ui.miuixAppUi
import com.Badnng.moe.ui.oobe.uiStyleSwitchTransform
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.MiuixIndication

private const val MIUIX_UI_STYLE = "miuix"
private const val MD3E_UI_STYLE = "md3e"
internal const val MD3E_MONET_ENABLED_KEY = "monet_enabled"
internal const val MIUIX_MONET_ENABLED_KEY = "miuix_monet_enabled"

private data class UiStyleScene(
    val style: String,
    val generation: Int,
) {
    val isMiuix: Boolean get() = style == MIUIX_UI_STYLE
}

private fun normalizedUiStyle(value: String?): String =
    if (value == MD3E_UI_STYLE) MD3E_UI_STYLE else MIUIX_UI_STYLE

@Composable
fun 澎湃记Theme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "system")) }
    var md3eMonetEnabled by remember {
        mutableStateOf(prefs.getBoolean(MD3E_MONET_ENABLED_KEY, true))
    }
    var miuixMonetEnabled by remember {
        mutableStateOf(prefs.getBoolean(MIUIX_MONET_ENABLED_KEY, false))
    }
    var amoledPureBlack by remember { mutableStateOf(prefs.getBoolean("amoled_pure_black", false)) }
    var seedColorInt by remember { mutableIntStateOf(prefs.getInt("theme_color", Color(0xFF6750A4).toArgb())) }
    val uiStyleState = remember {
        mutableStateOf(normalizedUiStyle(prefs.getString("ui_style", MIUIX_UI_STYLE)))
    }
    val uiStyleGeneration = remember { mutableIntStateOf(0) }
    var keyColorIndex by remember { mutableIntStateOf(prefs.getInt("key_color_index", 0)) }
    var predictiveBackEnabled by remember {
        mutableStateOf(prefs.getBoolean("predictive_back_enabled", true))
    }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                "theme_mode" -> themeMode = p.getString(key, "system")
                MD3E_MONET_ENABLED_KEY -> {
                    md3eMonetEnabled = p.getBoolean(key, true)
                }
                MIUIX_MONET_ENABLED_KEY -> {
                    miuixMonetEnabled = p.getBoolean(key, false)
                }
                "amoled_pure_black" -> amoledPureBlack = p.getBoolean(key, false)
                "theme_color" -> seedColorInt = p.getInt(key, Color(0xFF6750A4).toArgb())
                "ui_style" -> {
                    val nextStyle = normalizedUiStyle(p.getString(key, MIUIX_UI_STYLE))
                    if (nextStyle != uiStyleState.value) {
                        uiStyleState.value = nextStyle
                        uiStyleGeneration.intValue++
                    }
                }
                "key_color_index" -> keyColorIndex = p.getInt(key, 0)
                "predictive_back_enabled" -> predictiveBackEnabled = p.getBoolean(key, true)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    // ─── Miuix Key Color 预设（提前定义，供 Material3 和 Miuix 共用）───
    val miuixKeyColorPresets = listOf(
        null,  // 默认（使用 seedColorInt）
        Color(0xFF1976D2),  // 蓝色
        Color(0xFF7B1FA2),  // 紫色
        Color(0xFFD32F2F),  // 红色
        Color(0xFFFF6F00),  // 橙色
        Color(0xFF388E3C),  // 绿色
        Color(0xFF00838F),  // 青色
    )
    val miuixKeyColor = if (miuixMonetEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        null  // Monet 模式下不使用自定义 key color
    } else if (keyColorIndex > 0 && keyColorIndex < miuixKeyColorPresets.size) {
        miuixKeyColorPresets[keyColorIndex]
    } else {
        Color(seedColorInt)
    }

    val md3eDynamicColorScheme = if (
        md3eMonetEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        null
    }
    val miuixDynamicColorScheme = if (
        miuixMonetEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        null
    }
    val md3eColorScheme = (md3eDynamicColorScheme ?: ColorGenerator.seedToColorScheme(
        seedColor = seedColorInt,
        isDark = darkTheme,
    )).withAmoledPureBlack(darkTheme && amoledPureBlack)
    val miuixMaterialSeed = miuixKeyColor?.toArgb() ?: seedColorInt
    val miuixMaterialColorScheme = (miuixDynamicColorScheme ?: ColorGenerator.seedToColorScheme(
        seedColor = miuixMaterialSeed,
        isDark = darkTheme,
    )).withAmoledPureBlack(darkTheme && amoledPureBlack)

    // ─── Miuix ThemeController ───
    val miuixColorSchemeMode = when {
        miuixMonetEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            when (themeMode) {
                "light" -> ColorSchemeMode.MonetLight
                "dark" -> ColorSchemeMode.MonetDark
                else -> ColorSchemeMode.MonetSystem
            }
        }
        else -> when (themeMode) {
            "light" -> ColorSchemeMode.Light
            "dark" -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.System
        }
    }

    // 使用 remember + keys 重建 ThemeController
    val miuixController = remember(miuixColorSchemeMode, miuixKeyColor, darkTheme) {
        ThemeController(
            colorSchemeMode = miuixColorSchemeMode,
            keyColor = miuixKeyColor,
            isDark = darkTheme
        )
    }

    val targetScene = remember(uiStyleState.value, uiStyleGeneration.intValue) {
        UiStyleScene(
            style = uiStyleState.value,
            generation = uiStyleGeneration.intValue,
        )
    }
    val styleTransition = updateTransition(
        targetState = targetScene,
        label = "ui_style_transition",
    )
    val transitionBackground = if (targetScene.isMiuix) {
        if (darkTheme) Color(0xFF101114) else Color(0xFFF7F7FA)
    } else {
        md3eColorScheme.background
    }
    val transitionRunning = styleTransition.isRunning

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(transitionBackground),
    ) {
        styleTransition.AnimatedContent(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (transitionRunning) Modifier.clearAndSetSemantics { } else Modifier,
                ),
            transitionSpec = {
                uiStyleSwitchTransform() using SizeTransform(clip = false)
            },
        ) { scene ->
            key(scene.generation) {
                AppUiStyleTheme(
                    isMiuix = scene.isMiuix,
                    materialColorScheme = if (scene.isMiuix) {
                        miuixMaterialColorScheme
                    } else {
                        md3eColorScheme
                    },
                    miuixController = miuixController,
                    predictiveBackEnabled = predictiveBackEnabled,
                    content = content,
                )
            }
        }

        if (transitionRunning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .clearAndSetSemantics { },
            )
        }

        BackHandler(enabled = transitionRunning) { }
    }
}

private fun ColorScheme.withAmoledPureBlack(enabled: Boolean): ColorScheme =
    if (enabled) {
        copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color.Black,
        )
    } else {
        this
    }

@Composable
private fun AppUiStyleTheme(
    isMiuix: Boolean,
    materialColorScheme: ColorScheme,
    miuixController: ThemeController,
    predictiveBackEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val appUi = if (isMiuix) miuixAppUi else md3eAppUi

    CompositionLocalProvider(
        LocalAppUi provides appUi,
        LocalIsMiuixUi provides isMiuix,
    ) {
        if (isMiuix) {
            MiuixTheme(controller = miuixController) {
                MaterialExpressiveTheme(
                    colorScheme = materialColorScheme,
                    typography = Typography,
                ) {
                    val indicationColor = MiuixTheme.colorScheme.onBackground
                    val miuixIndication = remember(indicationColor) {
                        MiuixIndication(color = indicationColor)
                    }
                    CompositionLocalProvider(LocalIndication provides miuixIndication) {
                        AppBackGestureHost(
                            predictiveBackEnabled = predictiveBackEnabled,
                            content = content,
                        )
                    }
                }
            }
        } else {
            MaterialExpressiveTheme(
                colorScheme = materialColorScheme,
                typography = Typography,
            ) {
                AppBackGestureHost(
                    predictiveBackEnabled = predictiveBackEnabled,
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun AppBackGestureHost(
    predictiveBackEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val view = LocalView.current

    androidx.compose.foundation.layout.Box {
        content()
        NonPredictiveBackInterceptor(
            enabled = !predictiveBackEnabled,
            dispatcher = dispatcher,
            view = view,
        )
    }
}

@Composable
fun NonPredictiveBackInterceptor() {
    val context = LocalContext.current
    val predictiveBackEnabled = remember(context) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("predictive_back_enabled", true)
    }
    NonPredictiveBackInterceptor(
        enabled = !predictiveBackEnabled,
        dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher,
        view = LocalView.current,
    )
}

@Composable
private fun NonPredictiveBackInterceptor(
    enabled: Boolean,
    dispatcher: androidx.activity.OnBackPressedDispatcher?,
    view: android.view.View,
) {
    DisposableEffect(dispatcher, view, enabled) {
        if (dispatcher == null || !enabled) {
            return@DisposableEffect onDispose { }
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Forward only the completed event so lower callbacks do not receive gesture progress.
                isEnabled = false
                dispatcher.onBackPressed()
                view.post { isEnabled = true }
            }
        }
        dispatcher.addCallback(callback)
        onDispose { callback.remove() }
    }
}
