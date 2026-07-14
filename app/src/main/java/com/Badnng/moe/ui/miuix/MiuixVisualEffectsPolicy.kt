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
import dalvik.system.PathClassLoader
import java.lang.reflect.Method
import java.util.Locale

/**
 * Miuix 视觉效果的统一设备策略。
 *
 * HyperOS 侧沿用 HyperCeiler 的判断思路：MIUI Lite / Middle 策略、系统模糊能力和
 * 用户的系统模糊开关任一不满足时都停用模糊。其他系统按物理内存判断，标称 8 GB
 * 设备不会因为系统保留内存而被误判为低性能设备。
 */
object MiuixVisualEffectsPolicy {
    private const val MIN_NON_HYPER_OS_MEMORY_GIB = 8L
    private const val GIB_BYTES = 1024L * 1024L * 1024L

    @Volatile
    private var cachedDeviceProfile: DeviceProfile? = null

    fun allowsCostlyVisualEffects(context: Context): Boolean {
        val appContext = context.applicationContext
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
            usesMiuiLiteStrategy = isHyperOs && usesMiuiLiteStrategy(appContext),
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

    /** Mirrors fan.miuix LiteUtils.isCommonLiteStrategy(). */
    private fun usesMiuiLiteStrategy(context: Context): Boolean {
        val liteRom = readStaticBoolean("miui.os.Build", "IS_MIUI_LITE_VERSION") ||
            readStaticBoolean("miui.util.DeviceLevel", "IS_MIUI_LITE_VERSION")
        val liteStockPlus = SystemPropertyReader
            .get("ro.config.low_ram.support_miuilite_plus")
            .equals("true", ignoreCase = true)
        val middleVersion = readMiuiMiddleVersion(context)
        return liteRom || liteStockPlus || middleVersion >= 1
    }

    private fun readStaticBoolean(className: String, fieldName: String): Boolean = runCatching {
        Class.forName(className).getDeclaredField(fieldName).apply {
            isAccessible = true
        }.getBoolean(null)
    }.getOrDefault(false)

    private fun readMiuiMiddleVersion(context: Context): Int {
        val deviceLevelClass = runCatching {
            Class.forName("com.miui.performance.DeviceLevelUtils")
        }.getOrNull() ?: runCatching {
            val jarPath = if (Build.VERSION.SDK_INT > 33) {
                "/system_ext/framework/MiuiBooster.jar"
            } else {
                "/system/framework/MiuiBooster.jar"
            }
            PathClassLoader(jarPath, ClassLoader.getSystemClassLoader())
                .loadClass("com.miui.performance.DeviceLevelUtils")
        }.getOrNull() ?: return -1

        return runCatching {
            val instance = deviceLevelClass
                .getConstructor(Context::class.java)
                .newInstance(context)
            val result = deviceLevelClass
                .getDeclaredMethod("getMiuiMiddleVersion")
                .apply { isAccessible = true }
                .invoke(instance)
            (result as? Number)?.toInt() ?: -1
        }.getOrDefault(-1)
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
    var blurAllowed by remember(appContext) {
        mutableStateOf(
            MiuixVisualEffectsPolicy.allowsBlur(appContext),
        )
    }

    DisposableEffect(appContext) {
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
    return remember(context.applicationContext) {
        MiuixVisualEffectsPolicy.allowsCostlyVisualEffects(context.applicationContext)
    }
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
