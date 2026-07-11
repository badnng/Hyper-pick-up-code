/*
 * Animation timings are derived from HyperCeiler provision at commit
 * 7266aaa0d698ad10795381c5bf23651c2e1719d0.
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.Badnng.moe.ui.oobe

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

internal const val OOBE_PAGE_TRANSITION_MILLIS = 350
internal const val OOBE_EXIT_MILLIS = 360
internal const val OOBE_HOME_ENTER_MILLIS = 619

internal val OobeAccelerateDecelerateEasing = Easing { fraction ->
    (cos((fraction + 1f) * PI).toFloat() / 2f) + 0.5f
}

internal val OobeSinOutEasing = Easing { fraction ->
    sin(fraction * PI.toFloat() / 2f)
}

internal val OobeCubicOutEasing = Easing { fraction ->
    1f - (1f - fraction) * (1f - fraction) * (1f - fraction)
}

internal val OobeQuartOutEasing = Easing { fraction ->
    val inverse = 1f - fraction
    1f - inverse * inverse * inverse * inverse
}

internal val OobeHomeSpringEasing = Easing { fraction ->
    if (fraction <= 0f) return@Easing 0f
    if (fraction >= 1f) return@Easing 1f
    val dampingRatio = 0.65f
    val angularFrequency = 10.5f
    val dampedFrequency = angularFrequency * sqrt(1f - dampingRatio * dampingRatio)
    val envelope = exp((-dampingRatio * angularFrequency * fraction).toDouble()).toFloat()
    1f - envelope * (
        cos(dampedFrequency * fraction) +
            dampingRatio / sqrt(1f - dampingRatio * dampingRatio) * sin(dampedFrequency * fraction)
        )
}

private val OobeSineInOutEasing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)

internal fun uiStyleSwitchTransform(): ContentTransform =
    ((scaleIn(
        initialScale = 1.4f,
        animationSpec = tween(OOBE_HOME_ENTER_MILLIS, easing = OobeHomeSpringEasing),
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = 230,
            delayMillis = 60,
            easing = OobeSineInOutEasing,
        ),
    )).togetherWith(
        fadeOut(
            animationSpec = tween(OOBE_EXIT_MILLIS, easing = OobeSineInOutEasing),
        ),
    )).apply {
        targetContentZIndex = 1f
    }

internal fun oobeToHomeTransform(): ContentTransform =
    (scaleIn(
        initialScale = 1.3f,
        animationSpec = tween(OOBE_HOME_ENTER_MILLIS, easing = OobeHomeSpringEasing),
    ) + fadeIn(
        animationSpec = tween(durationMillis = 230, delayMillis = 60, easing = OobeSinOutEasing),
    )).togetherWith(
        scaleOut(
            targetScale = 0.8f,
            animationSpec = tween(OOBE_EXIT_MILLIS, easing = OobeHomeSpringEasing),
        ) + fadeOut(
            animationSpec = tween(OOBE_EXIT_MILLIS, easing = OobeSinOutEasing),
        ),
    )

internal fun homeToOobeTransform(): ContentTransform =
    (fadeIn(tween(OOBE_EXIT_MILLIS)) + scaleIn(initialScale = 0.96f))
        .togetherWith(ExitTransition.None)
