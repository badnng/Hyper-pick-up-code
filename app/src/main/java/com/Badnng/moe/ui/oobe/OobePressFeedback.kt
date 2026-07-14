package com.Badnng.moe.ui.oobe

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import java.util.WeakHashMap

/**
 * OOBE 统一的 Miuix 整体按压反馈。
 *
 * 点击区域先按组件形状裁剪，再绘制覆盖整个组件的按压层，避免反馈越过圆角。
 */
@Composable
internal fun Modifier.oobeMiuixPressFeedback(
    shape: Shape,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val clickProgress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val motionScheme = MaterialTheme.motionScheme
    val pressProgress by animateFloatAsState(
        targetValue = if (enabled && pressed) 1f else 0f,
        animationSpec = motionScheme.defaultEffectsSpec<Float>(),
        label = "oobe_miuix_press_feedback",
    )
    val darkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val overlayColor = if (darkSurface) Color.White else Color.Black
    val overlayAlpha = if (darkSurface) 0.10f else 0.08f

    return this
        .clip(shape)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = {
                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    // 快速点击也先完整显示反馈，再按 Effects 动画淡出。
                    clickProgress.snapTo(1f)
                    clickProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = motionScheme.defaultEffectsSpec<Float>(),
                    )
                }
                onClick()
            },
        )
        .drawWithContent {
            drawContent()
            val progress = maxOf(pressProgress, clickProgress.value)
            if (progress > 0f) {
                drawRect(overlayColor.copy(alpha = overlayAlpha * progress))
            }
        }
}

/** Applies the same bounded whole-surface feedback to the View-based OOBE chrome. */
internal fun View.installOobeMiuixPressFeedback(
    cornerRadiusDp: Float = 15f,
    oval: Boolean = false,
    insetDp: Float = 0f,
) {
    val cornerRadiusPx = cornerRadiusDp * resources.displayMetrics.density
    val insetPx = insetDp * resources.displayMetrics.density
    val spec = OobeViewFeedbackSpec(cornerRadiusPx, oval, insetPx)
    viewFeedbackSpecs[this] = spec

    setOnTouchListener { target, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (target.isEnabled) showOobeMiuixPressFeedback(target, spec)
            }
            MotionEvent.ACTION_UP -> hideOobeMiuixPressFeedback(
                target,
                startDelay = CLICK_FEEDBACK_HOLD_MILLIS,
            )
            MotionEvent.ACTION_CANCEL -> hideOobeMiuixPressFeedback(target)
            MotionEvent.ACTION_MOVE -> {
                if (event.x < 0f || event.x > target.width.toFloat() ||
                    event.y < 0f || event.y > target.height.toFloat()
                ) {
                    hideOobeMiuixPressFeedback(target)
                }
            }
        }
        false
    }
}

/** Keeps a full click pulse visible after ACTION_UP, even if the click disables the View. */
internal fun View.performOobeMiuixClickFeedback() {
    val spec = viewFeedbackSpecs[this] ?: return
    if (width <= 0 || height <= 0) return

    val state = viewFeedbackStates.getOrPut(this) { OobeViewFeedbackState() }
    ensureFeedbackDrawable(this, spec, state).alpha = 255
    hideOobeMiuixPressFeedback(this, CLICK_FEEDBACK_HOLD_MILLIS)
}

private fun showOobeMiuixPressFeedback(
    target: View,
    spec: OobeViewFeedbackSpec,
) {
    if (target.width <= 0 || target.height <= 0) return
    val state = viewFeedbackStates.getOrPut(target) { OobeViewFeedbackState() }
    val drawable = ensureFeedbackDrawable(target, spec, state)
    animateFeedback(
        target = target,
        state = state,
        drawable = drawable,
        targetAlpha = 255,
        durationMillis = PRESS_ENTER_DURATION_MILLIS.toLong(),
    )
}

private fun hideOobeMiuixPressFeedback(
    target: View,
    startDelay: Long = 0L,
) {
    val state = viewFeedbackStates[target] ?: return
    val drawable = state.drawable ?: return
    animateFeedback(
        target = target,
        state = state,
        drawable = drawable,
        targetAlpha = 0,
        durationMillis = PRESS_EXIT_DURATION_MILLIS.toLong(),
        startDelay = startDelay,
        removeOnEnd = true,
    )
}

private fun ensureFeedbackDrawable(
    target: View,
    spec: OobeViewFeedbackSpec,
    state: OobeViewFeedbackState,
): GradientDrawable {
    state.drawable?.let { drawable ->
        updateFeedbackBounds(target, spec, drawable)
        return drawable
    }
    return GradientDrawable().apply {
        shape = if (spec.oval) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
        if (!spec.oval) cornerRadius = spec.cornerRadiusPx
        setColor(PRESS_OVERLAY_COLOR)
        alpha = 0
        updateFeedbackBounds(target, spec, this)
        target.overlay.add(this)
        state.drawable = this
    }
}

private fun updateFeedbackBounds(
    target: View,
    spec: OobeViewFeedbackSpec,
    drawable: GradientDrawable,
) {
    val inset = spec.insetPx.toInt()
    drawable.setBounds(
        inset,
        inset,
        (target.width - inset).coerceAtLeast(inset),
        (target.height - inset).coerceAtLeast(inset),
    )
}

private fun animateFeedback(
    target: View,
    state: OobeViewFeedbackState,
    drawable: GradientDrawable,
    targetAlpha: Int,
    durationMillis: Long,
    startDelay: Long = 0L,
    removeOnEnd: Boolean = false,
) {
    val previousAnimator = state.animator
    state.animator = null
    previousAnimator?.cancel()
    val animator = ValueAnimator.ofInt(drawable.alpha, targetAlpha).apply {
        duration = durationMillis
        this.startDelay = startDelay
        interpolator = CLICK_FEEDBACK_INTERPOLATOR
        addUpdateListener { valueAnimator ->
            drawable.alpha = valueAnimator.animatedValue as Int
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (state.animator !== animation) return
                state.animator = null
                if (removeOnEnd) {
                    target.overlay.remove(drawable)
                    if (state.drawable === drawable) state.drawable = null
                }
            }
        })
    }
    state.animator = animator
    animator.start()
}

private data class OobeViewFeedbackSpec(
    val cornerRadiusPx: Float,
    val oval: Boolean,
    val insetPx: Float,
)

private data class OobeViewFeedbackState(
    var drawable: GradientDrawable? = null,
    var animator: ValueAnimator? = null,
)

private val viewFeedbackSpecs = WeakHashMap<View, OobeViewFeedbackSpec>()
private val viewFeedbackStates = WeakHashMap<View, OobeViewFeedbackState>()
private const val PRESS_OVERLAY_COLOR = 0x1F808080
private const val PRESS_ENTER_DURATION_MILLIS = 70
private const val PRESS_EXIT_DURATION_MILLIS = 180
private const val CLICK_FEEDBACK_HOLD_MILLIS = 90L
private val CLICK_FEEDBACK_INTERPOLATOR = PathInterpolator(0.2f, 0f, 0f, 1f)
