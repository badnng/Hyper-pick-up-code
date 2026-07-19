/*
 * HyperOS Lite strategy is derived from HyperCeiler/fan.miuix DeviceUtils.
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.Badnng.moe.ui.miuix

import android.app.ActivityManager
import android.content.Context
import android.database.ContentObserver
import android.os.Build
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
import java.util.Locale

const val MIUIX_FORCE_LOW_END_DEVICE_STANDARD_KEY = "miuix_force_low_end_device_standard"

/**
 * Miuix 视觉效果的统一设备策略。
 *
 * HyperOS 侧沿用 HyperCeiler 的 MIUI Lite 判断思路；Middle 设备不视为低性能设备，
 * 避免高性能平板仅因系统设备等级被关闭视觉效果。其他系统按物理内存判断，标称
 * 8 GB 设备不会因为系统保留内存而被误判为低性能设备。
 */
object MiuixVisualEffectsPolicy {
    private const val MIN_NON_HYPER_OS_MEMORY_GIB = 8L
    private const val GIB_BYTES = 1024L * 1024L * 1024L

    @Volatile
    private var cachedDeviceProfile: DeviceProfile? = null

    fun allowsCostlyVisualEffects(context: Context): Boolean {
        val appContext = context.applicationContext
        if (AppMemoryPressureState.active) return false
        if (usesForcedLowEndDeviceStandard(appContext)) return false
        val profile = deviceProfile(appContext)
        if (profile.lowRamDevice) return false

        return if (profile.isHyperOs) {
            !profile.usesMiuiLiteStrategy
        } else {
            profile.advertisedMemoryGiB >= MIN_NON_HYPER_OS_MEMORY_GIB
        }
    }

    fun allowsBlur(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!allowsCostlyVisualEffects(appContext)) return false
        return !isHyperOsDevice() ||
            (systemBlurSupported() && systemBlurEnabled(appContext))
    }

    fun isHyperOsDevice(): Boolean = isHyperOsRuntime()

    fun usesForcedLowEndDeviceStandard(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean(MIUIX_FORCE_LOW_END_DEVICE_STANDARD_KEY, false)

    private fun deviceProfile(context: Context): DeviceProfile =
        cachedDeviceProfile ?: synchronized(this) {
            cachedDeviceProfile ?: detectDeviceProfile(context).also {
                cachedDeviceProfile = it
            }
        }

    private fun detectDeviceProfile(context: Context): DeviceProfile {
        val appContext = context.applicationContext
        val isHyperOs = isHyperOsRuntime()
        val activityManager = appContext
            .getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        if (activityManager != null) {
            runCatching { activityManager.getMemoryInfo(memoryInfo) }
        }
        val advertisedMemoryGiB = if (memoryInfo.totalMem > 0L) {
            (memoryInfo.totalMem + GIB_BYTES - 1L) / GIB_BYTES
        } else {
            // 无法读取内存时不武断关闭效果，仍交由平台能力检查兜底。
            Long.MAX_VALUE
        }

        return DeviceProfile(
            isHyperOs = isHyperOs,
            usesMiuiLiteStrategy = isHyperOs && usesMiuiLiteStrategy(),
            lowRamDevice = activityManager?.isLowRamDevice == true,
            advertisedMemoryGiB = advertisedMemoryGiB,
        )
    }

    private fun isHyperOsRuntime(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val brand = Build.BRAND.lowercase(Locale.ROOT)
        val xiaomiBrand = manufacturer in XIAOMI_BRANDS || brand in XIAOMI_BRANDS
        val hasMiuiProperty = MIUI_VERSION_PROPERTIES.any {
            SystemPropertyReader.get(it).isNotBlank()
        }
        val hasMiuiRuntime = runCatching { Class.forName("miui.os.Build") }.isSuccess
        return hasMiuiProperty || hasMiuiRuntime || xiaomiBrand
    }

    /** 仅识别明确的 MIUI Lite / Lite Plus 标记，不使用 Middle 设备等级。 */
    private fun usesMiuiLiteStrategy(): Boolean {
        val liteRom = readStaticBoolean("miui.os.Build", "IS_MIUI_LITE_VERSION") ||
            readStaticBoolean("miui.util.DeviceLevel", "IS_MIUI_LITE_VERSION")
        val liteStockPlus = SystemPropertyReader
            .get("ro.config.low_ram.support_miuilite_plus")
            .equals("true", ignoreCase = true)
        return liteRom || liteStockPlus
    }

    private fun readStaticBoolean(className: String, fieldName: String): Boolean = runCatching {
        Class.forName(className).getDeclaredField(fieldName).apply {
            isAccessible = true
        }.getBoolean(null)
    }.getOrDefault(false)

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
        val usesMiuiLiteStrategy: Boolean,
        val lowRamDevice: Boolean,
        val advertisedMemoryGiB: Long,
    )

    private val XIAOMI_BRANDS = setOf("xiaomi", "redmi", "poco")
    private val MIUI_VERSION_PROPERTIES = listOf(
        "ro.mi.os.version.name",
        "ro.miui.ui.version.name",
        "ro.miui.ui.version.code",
    )
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
