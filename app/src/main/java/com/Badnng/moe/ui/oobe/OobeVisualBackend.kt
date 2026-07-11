/*
 * HyperOS blur behavior is derived from HyperCeiler provision at commit
 * 7266aaa0d698ad10795381c5bf23651c2e1719d0.
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.Badnng.moe.ui.oobe

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.graphics.RuntimeShader
import android.os.Build
import android.provider.Settings
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.lang.reflect.Method
import java.util.Locale

internal enum class OobeVisualBackend {
    HyperOsEnhanced,
    AndroidFallback,
    StaticFallback,
}

@Composable
internal fun rememberOobeVisualBackend(): MutableState<OobeVisualBackend> {
    val context = LocalContext.current
    return remember(context.applicationContext) {
        mutableStateOf(OobeVisualBackendResolver.resolve(context.applicationContext))
    }
}

internal object OobeVisualBackendResolver {
    @Volatile
    private var cachedBackend: OobeVisualBackend? = null

    fun resolve(context: Context? = null): OobeVisualBackend = cachedBackend ?: synchronized(this) {
        cachedBackend ?: detectBackend(context).also { cachedBackend = it }
    }

    fun downgradeFromHyperOs(): OobeVisualBackend = synchronized(this) {
        val fallback = if (supportsRuntimeShader()) {
            OobeVisualBackend.AndroidFallback
        } else {
            OobeVisualBackend.StaticFallback
        }
        cachedBackend = fallback
        fallback
    }

    fun useStaticFallback(): OobeVisualBackend = synchronized(this) {
        OobeVisualBackend.StaticFallback.also { cachedBackend = it }
    }

    fun isHyperOsDevice(): Boolean = isHyperOsRuntime()

    private fun detectBackend(context: Context?): OobeVisualBackend {
        if (!ValueAnimator.areAnimatorsEnabled() || !supportsRuntimeShader()) {
            return OobeVisualBackend.StaticFallback
        }
        return if (
            isHyperOsRuntime() &&
            HyperOsBlurBridge.isSupported() &&
            (context == null || HyperOsBlurBridge.isEffectEnabled(context))
        ) {
            OobeVisualBackend.HyperOsEnhanced
        } else {
            OobeVisualBackend.AndroidFallback
        }
    }

    private fun supportsRuntimeShader(): Boolean = runCatching {
        RuntimeShader("half4 main(float2 position) { return half4(1.0); }")
    }.isSuccess

    private fun isHyperOsRuntime(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val brand = Build.BRAND.lowercase(Locale.ROOT)
        val xiaomiBrand = manufacturer in XIAOMI_BRANDS || brand in XIAOMI_BRANDS
        val hasMiuiProperty = listOf(
            "ro.mi.os.version.name",
            "ro.miui.ui.version.name",
            "ro.miui.ui.version.code",
        ).any { SystemPropertyReader.get(it).isNotBlank() }
        return hasMiuiProperty || xiaomiBrand
    }

    private val XIAOMI_BRANDS = setOf("xiaomi", "redmi", "poco")
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

internal object HyperOsBlurBridge {
    private const val BLUR_RADIUS = 50

    private val backgroundMode by lazy { findMethod("setMiBackgroundBlurMode", Integer.TYPE) }
    private val backgroundRadius by lazy { findMethod("setMiBackgroundBlurRadius", Integer.TYPE) }
    private val viewMode by lazy { findMethod("setMiViewBlurMode", Integer.TYPE) }
    private val addBlendColor by lazy {
        findMethod("addMiBackgroundBlendColor", Integer.TYPE, Integer.TYPE)
    }
    private val clearBlendColors by lazy {
        findMethod("clearMiBackgroundBlendColor") ?: findMethod("clearMiBackgroundBlendColors")
    }

    fun isSupported(): Boolean =
        backgroundMode != null &&
            backgroundRadius != null &&
            viewMode != null &&
            addBlendColor != null &&
            clearBlendColors != null

    fun isEffectEnabled(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (activityManager?.isLowRamDevice == true) return false

        val supportedProperty = SystemPropertyReader
            .get("persist.sys.background_blur_supported")
            .trim()
            .lowercase(Locale.ROOT)
        if (supportedProperty == "0" || supportedProperty == "false") return false

        return runCatching {
            Settings.Secure.getInt(
                context.contentResolver,
                "background_blur_enable",
                1,
            ) != 0
        }.getOrDefault(true)
    }

    fun apply(view: View): Boolean = runCatching {
        (backgroundMode ?: error("HyperOS background blur mode is unavailable"))
            .invoke(view, 1)
        (backgroundRadius ?: error("HyperOS background blur radius is unavailable"))
            .invoke(view, (BLUR_RADIUS * view.resources.displayMetrics.density).toInt())
        (viewMode ?: error("HyperOS view blur mode is unavailable"))
            .invoke(view, 0)
        true
    }.getOrDefault(false)

    fun clear(view: View) {
        runCatching { viewMode?.invoke(view, 0) }
        runCatching { backgroundMode?.invoke(view, 0) }
        runCatching { backgroundRadius?.invoke(view, 0) }
    }

    fun applyViewBlur(
        view: View,
        blendColors: IntArray,
        blendModes: IntArray,
    ): Boolean {
        if (blendColors.size != blendModes.size) return false
        return runCatching {
            clearBlendColors?.invoke(view)
            (viewMode ?: error("HyperOS view blur mode is unavailable"))
                .invoke(view, 3)
            blendColors.indices.forEach { index ->
                (addBlendColor ?: error("HyperOS blend color API is unavailable"))
                    .invoke(view, blendColors[index], blendModes[index])
            }
            true
        }.getOrDefault(false)
    }

    fun clearViewBlur(view: View) {
        runCatching { clearBlendColors?.invoke(view) }
        runCatching { viewMode?.invoke(view, 0) }
    }

    private fun findMethod(name: String, vararg parameterTypes: Class<*>): Method? = runCatching {
        View::class.java.getMethod(name, *parameterTypes).apply { isAccessible = true }
    }.getOrNull()
}
