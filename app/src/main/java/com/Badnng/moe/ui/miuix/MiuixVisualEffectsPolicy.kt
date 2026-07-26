/*
 * Miuix visual-effects policy derived from the original HyperCeiler device strategy.
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.Badnng.moe.ui.miuix

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.Badnng.moe.helper.AppMemoryPressureState
import java.lang.reflect.Method

const val MIUIX_FORCE_LOW_END_DEVICE_STANDARD_KEY = "miuix_force_low_end_device_standard"

enum class MiuixDevicePerformanceTier {
    Low,
    Medium,
    High,
}

/**
 * Miuix 视觉效果的统一设备策略。
 *
 * HyperOS 使用系统 Computility CPU Level 分档；非 HyperOS 不做性能降级。
 * 设备档位只控制模糊和 OOBE 箭头转场，常规动画与动态背景对所有档位开放。
 */
object MiuixVisualEffectsPolicy {
    private const val COMPUTILITY_CPU_LEVEL_PROPERTY = "persist.sys.computility.cpulevel"

    @Volatile
    private var cachedDeviceProfile: DeviceProfile? = null

    @Suppress("UNUSED_PARAMETER")
    fun allowsCostlyVisualEffects(context: Context): Boolean =
        !AppMemoryPressureState.active

    fun allowsBlur(context: Context): Boolean {
        val appContext = context.applicationContext
        if (AppMemoryPressureState.active) return false
        if (devicePerformanceTier(appContext) == MiuixDevicePerformanceTier.Low) return false
        return !isHyperOsDevice() ||
            (systemBlurSupported() && systemBlurEnabled(appContext))
    }

    fun allowsIconColorMixing(context: Context): Boolean {
        if (AppMemoryPressureState.active) return false
        return devicePerformanceTier(context) != MiuixDevicePerformanceTier.Low
    }

    fun allowsOobeArrowTransition(context: Context): Boolean =
        isHyperOsDevice() &&
            devicePerformanceTier(context) != MiuixDevicePerformanceTier.Low

    fun devicePerformanceTier(context: Context): MiuixDevicePerformanceTier {
        val appContext = context.applicationContext
        if (usesForcedLowEndDeviceStandard(appContext)) {
            return MiuixDevicePerformanceTier.Low
        }
        val profile = deviceProfile()
        if (!profile.isHyperOs) return MiuixDevicePerformanceTier.High
        return when (profile.cpuLevel) {
            null -> MiuixDevicePerformanceTier.Medium
            in Int.MIN_VALUE..4 -> MiuixDevicePerformanceTier.Low
            5 -> MiuixDevicePerformanceTier.Medium
            else -> MiuixDevicePerformanceTier.High
        }
    }

    fun cpuLevel(): Int? = deviceProfile().cpuLevel

    fun isHyperOsDevice(): Boolean = isHyperOsRuntime()

    fun usesForcedLowEndDeviceStandard(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean(MIUIX_FORCE_LOW_END_DEVICE_STANDARD_KEY, false)

    private fun deviceProfile(): DeviceProfile =
        cachedDeviceProfile ?: synchronized(this) {
            cachedDeviceProfile ?: detectDeviceProfile().also {
                cachedDeviceProfile = it
            }
        }

    private fun detectDeviceProfile(): DeviceProfile {
        val isHyperOs = isHyperOsRuntime()
        return DeviceProfile(
            isHyperOs = isHyperOs,
            cpuLevel = if (isHyperOs) {
                SystemPropertyReader.get(COMPUTILITY_CPU_LEVEL_PROPERTY)
                    .trim()
                    .toIntOrNull()
            } else {
                null
            },
        )
    }

    private fun isHyperOsRuntime(): Boolean {
        val hasHyperOsProperty = HYPER_OS_VERSION_PROPERTIES.any {
            SystemPropertyReader.get(it).isNotBlank()
        }
        val hasComputilityProfile = SystemPropertyReader
            .get(COMPUTILITY_CPU_LEVEL_PROPERTY)
            .isNotBlank()
        return hasHyperOsProperty || hasComputilityProfile
    }

    private fun systemBlurSupported(): Boolean = SystemPropertyReader
        .get("persist.sys.background_blur_supported")
        .trim()
        .let { value -> value == "1" || value.equals("true", ignoreCase = true) }

    private fun systemBlurEnabled(context: Context): Boolean = runCatching {
        Settings.Secure.getInt(
            context.contentResolver,
            "background_blur_enable",
            0,
        ) == 1
    }.getOrDefault(false)

    private data class DeviceProfile(
        val isHyperOs: Boolean,
        val cpuLevel: Int?,
    )

    private val HYPER_OS_VERSION_PROPERTIES = listOf(
        "ro.mi.os.version.name",
        "ro.mi.os.version.incremental",
        "ro.mi.os.version.code",
    )
}

@Composable
fun rememberMiuixDevicePerformanceTier(): MiuixDevicePerformanceTier {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val prefs = remember(appContext) {
        appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }
    var tier by remember(appContext) {
        mutableStateOf(MiuixVisualEffectsPolicy.devicePerformanceTier(appContext))
    }

    DisposableEffect(appContext, prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == MIUIX_FORCE_LOW_END_DEVICE_STANDARD_KEY) {
                tier = MiuixVisualEffectsPolicy.devicePerformanceTier(appContext)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    return tier
}

@Composable
fun rememberMiuixBlurAllowed(): Boolean {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val memoryPressureActive = AppMemoryPressureState.active
    val prefs = remember(appContext) {
        appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }
    var blurAllowed by remember(appContext, memoryPressureActive) {
        mutableStateOf(
            MiuixVisualEffectsPolicy.allowsBlur(appContext),
        )
    }

    DisposableEffect(appContext, prefs, memoryPressureActive) {
        val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == MIUIX_FORCE_LOW_END_DEVICE_STANDARD_KEY) {
                blurAllowed = MiuixVisualEffectsPolicy.allowsBlur(appContext)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                blurAllowed = MiuixVisualEffectsPolicy.allowsBlur(appContext)
            }
        }
        val registered = MiuixVisualEffectsPolicy.isHyperOsDevice() && runCatching {
            appContext.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor("background_blur_enable"),
                false,
                observer,
            )
        }.isSuccess

        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
            if (registered) {
                runCatching { appContext.contentResolver.unregisterContentObserver(observer) }
            }
        }
    }

    return blurAllowed
}

@Composable
fun rememberMiuixIconColorMixingAllowed(): Boolean {
    val tier = rememberMiuixDevicePerformanceTier()
    val memoryPressureActive = AppMemoryPressureState.active
    return !memoryPressureActive && tier != MiuixDevicePerformanceTier.Low
}

@Composable
fun rememberMiuixVisualEffectsAllowed(): Boolean {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val memoryPressureActive = AppMemoryPressureState.active
    val prefs = remember(appContext) {
        appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }
    var visualEffectsAllowed by remember(appContext, memoryPressureActive) {
        mutableStateOf(MiuixVisualEffectsPolicy.allowsCostlyVisualEffects(appContext))
    }

    DisposableEffect(appContext, prefs, memoryPressureActive) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == MIUIX_FORCE_LOW_END_DEVICE_STANDARD_KEY) {
                visualEffectsAllowed = MiuixVisualEffectsPolicy.allowsCostlyVisualEffects(appContext)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    return visualEffectsAllowed
}

private object SystemPropertyReader {
    private val getMethod: Method? by lazy {
        runCatching {
            Class.forName("android.os.SystemProperties")
                .getDeclaredMethod("get", String::class.java)
                .apply { isAccessible = true }
        }.getOrNull()
    }

    fun get(key: String): String = runCatching {
        getMethod?.invoke(null, key) as? String ?: ""
    }.getOrDefault("")
}
