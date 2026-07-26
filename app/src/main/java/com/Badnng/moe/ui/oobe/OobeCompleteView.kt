/*
 * Completion-page layout, blur colors, and animation parameters are derived
 * from HyperCeiler provision at commit 7266aaa0d698ad10795381c5bf23651c2e1719d0.
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.Badnng.moe.ui.oobe

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Space
import android.widget.TextView
import com.Badnng.moe.R

internal class OobeCompleteView(
    context: Context,
    playIntro: Boolean,
) : LinearLayout(context) {
    private val logoWrapper = LinearLayout(context)
    private val logoView = OobeCarvedLogoView(context)
    private val wordmarkView = OobeWordmarkView(context)
    private val stateTextView = TextView(context)
    private val uiStyleHintView = TextView(context)
    private val nextView = FrameLayout(context)
    private val buttonBackground = View(context)
    private val actionLabelView = TextView(context)
    private val runningAnimators = mutableListOf<Animator>()
    private val startIntroRunnable = Runnable(::startIntroAnimation)

    private var backend = OobeVisualBackend.AndroidFallback
    private var darkTheme = false
    private var blurApplied = false
    private var introStarted = false
    private var introFinished = !playIntro || !ValueAnimator.areAnimatorsEnabled()
    private var actionEnabled = true
    private var onComplete: () -> Unit = {}
    private var onBackendFailure: () -> Unit = {}

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.TRANSPARENT)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO

        addView(
            Space(context),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 18f),
        )

        val logoContent = RelativeLayout(context)
        addView(
            logoContent,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 40f),
        )

        logoWrapper.apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        logoContent.addView(
            logoWrapper,
            RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        logoView.apply {
            contentDescription = null
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        logoWrapper.addView(logoView, LinearLayout.LayoutParams(dp(90f), dp(90f)))

        wordmarkView.apply {
            contentDescription = null
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        logoWrapper.addView(
            wordmarkView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(20f)
            },
        )

        stateTextView.apply {
            text = "设置完成"
            gravity = Gravity.CENTER
            setTextColor(0xBFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT
            includeFontPadding = false
            letterSpacing = 0f
            setSingleLine(true)
        }
        logoWrapper.addView(
            stateTextView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(30f) },
        )

        uiStyleHintView.apply {
            text = if (
                context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .getString("ui_style", "miuix") == "md3e"
            ) {
                "当前使用 Material 3 Expressive UI\n可在「偏好设置 > 界面风格」中切换"
            } else {
                "可在「偏好设置 > 界面风格」中切换为\nMaterial 3 Expressive UI"
            }
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT
            includeFontPadding = false
            letterSpacing = 0f
            maxLines = 2
            setLineSpacing(0f, 1.1f)
        }
        logoWrapper.addView(
            uiStyleHintView,
            LinearLayout.LayoutParams(dp(320f), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12f)
            },
        )

        val nextContainer = FrameLayout(context).apply {
            setPadding(dp(20f), 0, dp(20f), 0)
            addOnLayoutChangeListener { view, left, _, right, _, _, _, _, _ ->
                val availableWidth = right - left - view.paddingLeft - view.paddingRight
                val targetWidth = availableWidth.coerceAtMost(dp(336f)).coerceAtLeast(0)
                val params = nextView.layoutParams as? FrameLayout.LayoutParams
                if (params != null && params.width != targetWidth) {
                    params.width = targetWidth
                    nextView.layoutParams = params
                }
            }
        }
        logoContent.addView(
            nextContainer,
            RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                bottomMargin = dp(44f)
            },
        )

        nextView.apply {
            contentDescription = "进入澎湃记"
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            isFocusable = true
            installOobeMiuixPressFeedback()
            setOnClickListener {
                performOobeMiuixClickFeedback()
                if (introFinished && actionEnabled) {
                    actionEnabled = false
                    updateActionEnabled()
                    onComplete()
                }
            }
        }
        nextContainer.addView(
            nextView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50f),
                Gravity.CENTER_HORIZONTAL,
            ),
        )

        buttonBackground.setBackgroundResource(R.drawable.oobe_complete_button_background)
        nextView.addView(
            buttonBackground,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        nextView.addView(
            actionLabelView.apply {
                text = "进入澎湃记"
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                typeface = Typeface.DEFAULT
                includeFontPadding = false
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )

        addView(
            Space(context),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.2f),
        )

        if (introFinished) {
            showFinalState()
        } else {
            showInitialState()
        }
    }

    fun bind(
        backend: OobeVisualBackend,
        darkTheme: Boolean,
        actionLabel: String,
        enabled: Boolean,
        onComplete: () -> Unit,
        onBackendFailure: () -> Unit,
    ) {
        val backendChanged = this.backend != backend
        val themeChanged = this.darkTheme != darkTheme
        this.onComplete = onComplete
        this.onBackendFailure = onBackendFailure
        this.darkTheme = darkTheme
        actionLabelView.text = actionLabel
        nextView.contentDescription = actionLabel
        actionEnabled = enabled
        updateActionEnabled()
        if (backendChanged || themeChanged) {
            clearBlur()
            this.backend = backend
            updateBackendVisuals()
            if (isAttachedToWindow) applyBlurIfNeeded()
        } else {
            updateBackendVisuals()
        }
    }

    private fun updateBackendVisuals() {
        val mixesLogoColor = backend == OobeVisualBackend.HyperOsEnhanced ||
            backend == OobeVisualBackend.HyperOsIconMixing
        if (mixesLogoColor || darkTheme) {
            logoView.setBaseColor(Color.WHITE)
            wordmarkView.imageTintList = ColorStateList.valueOf(Color.WHITE)
        } else {
            logoView.setBaseColor(Color.BLACK)
            wordmarkView.imageTintList = ColorStateList.valueOf(Color.BLACK)
        }
        if (backend == OobeVisualBackend.HyperOsEnhanced || darkTheme) {
            stateTextView.setTextColor(0xBFFFFFFF.toInt())
            uiStyleHintView.setTextColor(0x99FFFFFF.toInt())
        } else {
            stateTextView.setTextColor(0xBF000000.toInt())
            uiStyleHintView.setTextColor(0x8A000000.toInt())
        }
        buttonBackground.backgroundTintList = if (backend != OobeVisualBackend.HyperOsEnhanced && darkTheme) {
            ColorStateList.valueOf(0xFF3482FF.toInt())
        } else {
            null
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyBlurIfNeeded()
        removeCallbacks(startIntroRunnable)
        postDelayed(startIntroRunnable, COMPLETE_INTRO_DELAY_MILLIS)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(startIntroRunnable)
        runningAnimators.forEach(Animator::cancel)
        runningAnimators.clear()
        clearBlur()
        super.onDetachedFromWindow()
    }

    private fun applyBlurIfNeeded() {
        if (
            backend != OobeVisualBackend.HyperOsEnhanced &&
            backend != OobeVisualBackend.HyperOsIconMixing
        ) return
        if (blurApplied) return
        val logoPalette = if (darkTheme) {
            OobeBlurPalettes.MiuixDarkLogo
        } else {
            OobeBlurPalettes.HyperCeilerLightLogo
        }
        val buttonPalette = if (darkTheme) {
            OobeBlurPalettes.MiuixDarkGlass
        } else {
            OobeBlurPalettes.HyperCeilerLightCompleteButton
        }
        val statePalette = if (darkTheme) {
            OobeBlurPalettes.MiuixDarkLogo
        } else {
            OobeBlurPalettes.HyperCeilerLightStateText
        }
        val success = when (backend) {
            OobeVisualBackend.HyperOsEnhanced -> HyperOsBlurBridge.apply(this) &&
                HyperOsBlurBridge.applyViewBlur(
                    logoView,
                    logoPalette.colors,
                    logoPalette.modes,
                ) &&
                HyperOsBlurBridge.applyViewBlur(
                    wordmarkView,
                    logoPalette.colors,
                    logoPalette.modes,
                ) &&
                HyperOsBlurBridge.applyViewBlur(
                    buttonBackground,
                    buttonPalette.colors,
                    buttonPalette.modes,
                ) &&
                HyperOsBlurBridge.applyViewBlur(
                    stateTextView,
                    statePalette.colors,
                    statePalette.modes,
                ) &&
                HyperOsBlurBridge.applyViewBlur(
                    uiStyleHintView,
                    statePalette.colors,
                    statePalette.modes,
                )
            OobeVisualBackend.HyperOsIconMixing ->
                HyperOsBlurBridge.applyViewBlur(
                    logoView,
                    logoPalette.colors,
                    logoPalette.modes,
                ) && HyperOsBlurBridge.applyViewBlur(
                    wordmarkView,
                    logoPalette.colors,
                    logoPalette.modes,
                )
            else -> false
        }
        if (success) {
            blurApplied = true
        } else {
            clearBlur()
            onBackendFailure()
        }
    }

    private fun clearBlur() {
        HyperOsBlurBridge.clearViewBlur(logoView)
        HyperOsBlurBridge.clearViewBlur(wordmarkView)
        HyperOsBlurBridge.clearViewBlur(buttonBackground)
        HyperOsBlurBridge.clearViewBlur(stateTextView)
        HyperOsBlurBridge.clearViewBlur(uiStyleHintView)
        HyperOsBlurBridge.clear(this)
        blurApplied = false
    }

    private fun startIntroAnimation() {
        if (introStarted || introFinished || !isAttachedToWindow) return
        introStarted = true
        val logoAnimator = ObjectAnimator.ofPropertyValuesHolder(
            logoWrapper,
            PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 100f, 0f),
        ).apply {
            duration = 1500L
            interpolator = QUART_OUT
        }
        val buttonAnimator = ObjectAnimator.ofFloat(nextView, View.ALPHA, 0f, 1f).apply {
            duration = 450L
            startDelay = 1000L
            interpolator = SIN_OUT
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finishIntroAnimation()
                }
            })
        }
        runningAnimators += logoAnimator
        runningAnimators += buttonAnimator
        runningAnimators.forEach(Animator::start)
    }

    private fun showInitialState() {
        logoWrapper.alpha = 0f
        logoWrapper.translationY = 100f
        nextView.alpha = 0f
        updateActionEnabled()
    }

    private fun showFinalState() {
        logoWrapper.alpha = 1f
        logoWrapper.translationY = 0f
        nextView.alpha = 1f
        updateActionEnabled()
    }

    private fun finishIntroAnimation() {
        if (introFinished) return
        introFinished = true
        updateActionEnabled()
    }

    private fun updateActionEnabled() {
        val enabled = introFinished && actionEnabled
        nextView.isEnabled = enabled
        nextView.isClickable = enabled
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val COMPLETE_INTRO_DELAY_MILLIS = 300L

        val SIN_OUT = TimeInterpolator { input ->
            kotlin.math.sin(input * Math.PI.toFloat() / 2f)
        }
        val QUART_OUT = TimeInterpolator { input ->
            val inverse = 1f - input
            1f - inverse * inverse * inverse * inverse
        }
    }
}
