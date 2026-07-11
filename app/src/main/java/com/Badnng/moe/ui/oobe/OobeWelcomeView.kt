/*
 * Welcome-page layout, blur colors, and animation parameters are derived from
 * HyperCeiler provision at commit 7266aaa0d698ad10795381c5bf23651c2e1719d0.
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.Badnng.moe.ui.oobe

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Space
import com.Badnng.moe.R

internal class OobeWelcomeView(
    context: Context,
    playIntro: Boolean,
) : LinearLayout(context) {
    private val logoView = OobeCarvedLogoView(context)
    private val wordmarkView = OobeWordmarkView(context)
    private val nextLayout = FrameLayout(context)
    private val nextButton = ImageButton(context)
    private val nextArrow = ImageView(context)
    private val runningAnimators = mutableListOf<Animator>()

    private var backend = OobeVisualBackend.AndroidFallback
    private var darkTheme = false
    private var blurApplied = false
    private var introStarted = false
    private var introFinished = !playIntro || !ValueAnimator.areAnimatorsEnabled()
    private var startEnabled = true
    private var onStart: (View) -> Unit = {}
    private var onIntroFinished: () -> Unit = {}
    private var onBackendFailure: () -> Unit = {}
    private var onCircleYOffsetChanged: (Float) -> Unit = {}
    private var lastCircleYOffset = Float.NaN

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO

        addView(
            Space(context),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 30f),
        )

        logoView.apply {
            contentDescription = null
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(logoView, LinearLayout.LayoutParams(dp(90f), dp(90f)))

        val middle = RelativeLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        addView(
            middle,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 40f),
        )

        wordmarkView.apply {
            contentDescription = null
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        middle.addView(
            wordmarkView,
            RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_TOP)
                addRule(RelativeLayout.CENTER_HORIZONTAL)
                topMargin = dp(20f)
            },
        )

        nextButton.apply {
            setPadding(0, 0, 0, 0)
            scaleType = ImageView.ScaleType.FIT_XY
            tag = nextLayout
            isClickable = false
            isFocusable = false
            contentDescription = null
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        nextLayout.addView(
            nextButton,
            FrameLayout.LayoutParams(dp(70f), dp(70f), Gravity.CENTER),
        )

        nextArrow.apply {
            setImageResource(R.drawable.oobe_start_arrow)
            isClickable = false
            isFocusable = false
            contentDescription = null
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        nextLayout.addView(
            nextArrow,
            FrameLayout.LayoutParams(dp(29f), dp(20f), Gravity.CENTER),
        )
        nextLayout.apply {
            val padding = dp(4.5f)
            setPadding(padding, padding, padding, padding)
            contentDescription = "开始设置"
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            isFocusable = true
            setOnClickListener {
                if (introFinished && startEnabled) {
                    startEnabled = false
                    updateStartEnabled()
                    onStart(nextButton)
                }
            }
        }
        middle.addView(
            nextLayout,
            RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                addRule(RelativeLayout.CENTER_HORIZONTAL)
            },
        )

        addView(
            Space(context),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 20f),
        )

        if (introFinished) {
            showFinalState()
        } else {
            showInitialState()
            post(::startIntroAnimation)
        }
    }

    fun bind(
        backend: OobeVisualBackend,
        darkTheme: Boolean,
        enabled: Boolean,
        onStart: (View) -> Unit,
        onIntroFinished: () -> Unit,
        onBackendFailure: () -> Unit,
        onCircleYOffsetChanged: (Float) -> Unit,
    ) {
        val backendChanged = this.backend != backend
        val themeChanged = this.darkTheme != darkTheme
        this.onStart = onStart
        this.onIntroFinished = onIntroFinished
        this.onBackendFailure = onBackendFailure
        this.onCircleYOffsetChanged = onCircleYOffsetChanged
        this.darkTheme = darkTheme
        startEnabled = enabled
        updateStartEnabled()
        if (backendChanged || themeChanged) {
            clearBlur()
            this.backend = backend
            updateBackendVisuals()
            if (isAttachedToWindow) applyBlurIfNeeded()
        } else {
            updateBackendVisuals()
        }
        post(::dispatchCircleYOffset)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) dispatchCircleYOffset()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyBlurIfNeeded()
        post(::startIntroAnimation)
    }

    override fun onDetachedFromWindow() {
        runningAnimators.forEach(Animator::cancel)
        runningAnimators.clear()
        clearBlur()
        super.onDetachedFromWindow()
    }

    private fun updateBackendVisuals() {
        if (backend == OobeVisualBackend.HyperOsEnhanced) {
            logoView.setBaseColor(Color.WHITE)
            wordmarkView.imageTintList = ColorStateList.valueOf(Color.WHITE)
            nextButton.setBackgroundResource(R.drawable.oobe_start_button_hyperos)
            nextArrow.visibility = VISIBLE
        } else if (darkTheme) {
            logoView.setBaseColor(Color.WHITE)
            wordmarkView.imageTintList = ColorStateList.valueOf(Color.WHITE)
            nextButton.setBackgroundResource(R.drawable.oobe_start_button_fallback_dark)
            nextArrow.visibility = GONE
        } else {
            logoView.setBaseColor(Color.BLACK)
            wordmarkView.imageTintList = ColorStateList.valueOf(Color.BLACK)
            nextButton.setBackgroundResource(R.drawable.oobe_start_button_fallback)
            nextArrow.visibility = GONE
        }
    }

    private fun applyBlurIfNeeded() {
        if (backend != OobeVisualBackend.HyperOsEnhanced || blurApplied) return
        val logoPalette = if (darkTheme) {
            OobeBlurPalettes.MiuixDarkLogo
        } else {
            OobeBlurPalettes.HyperCeilerLightLogo
        }
        val buttonPalette = if (darkTheme) {
            OobeBlurPalettes.MiuixDarkLogo
        } else {
            OobeBlurPalettes.HyperCeilerLightWelcomeButton
        }
        val success = HyperOsBlurBridge.apply(this) &&
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
                nextButton,
                buttonPalette.colors,
                buttonPalette.modes,
            )
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
        HyperOsBlurBridge.clearViewBlur(nextButton)
        HyperOsBlurBridge.clear(this)
        blurApplied = false
    }

    private fun startIntroAnimation() {
        if (introStarted || introFinished || !isAttachedToWindow) return
        introStarted = true
        val logoScale = logoScaleAnimator(logoView)
        val wordmarkScale = logoScaleAnimator(wordmarkView)
        val logoAlpha = alphaAnimator(logoView)
        val wordmarkAlpha = alphaAnimator(wordmarkView)
        val buttonAnimator = ObjectAnimator.ofPropertyValuesHolder(
            nextLayout,
            PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.9f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9f, 1f),
        ).apply {
            duration = 450L
            startDelay = 1340L
            interpolator = CUBIC_OUT
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finishIntroAnimation()
                }
            })
        }
        runningAnimators += listOf(
            logoScale,
            wordmarkScale,
            logoAlpha,
            wordmarkAlpha,
            buttonAnimator,
        )
        runningAnimators.forEach(Animator::start)
    }

    private fun logoScaleAnimator(view: View): AnimatorSet {
        val first = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.5f, 0.95f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.5f, 0.95f),
        ).apply {
            duration = 440L
            interpolator = SIN_OUT
        }
        val second = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.95f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.95f, 1f),
        ).apply {
            duration = 700L
            interpolator = CUBIC_OUT
        }
        return AnimatorSet().apply { playSequentially(first, second) }
    }

    private fun alphaAnimator(view: View): ObjectAnimator =
        ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
            duration = 440L
            startDelay = 60L
            interpolator = SIN_OUT
        }

    private fun showInitialState() {
        logoView.alpha = 0f
        logoView.scaleX = 0.5f
        logoView.scaleY = 0.5f
        wordmarkView.alpha = 0f
        wordmarkView.scaleX = 0.5f
        wordmarkView.scaleY = 0.5f
        nextLayout.alpha = 0f
        nextLayout.scaleX = 0.9f
        nextLayout.scaleY = 0.9f
        updateStartEnabled()
    }

    private fun showFinalState() {
        logoView.alpha = 1f
        logoView.scaleX = 1f
        logoView.scaleY = 1f
        wordmarkView.alpha = 1f
        wordmarkView.scaleX = 1f
        wordmarkView.scaleY = 1f
        nextLayout.alpha = 1f
        nextLayout.scaleX = 1f
        nextLayout.scaleY = 1f
        updateStartEnabled()
    }

    private fun finishIntroAnimation() {
        if (introFinished) return
        introFinished = true
        showFinalState()
        onIntroFinished()
    }

    private fun updateStartEnabled() {
        val enabled = introFinished && startEnabled
        nextLayout.isEnabled = enabled
        nextLayout.isClickable = enabled
    }

    private fun dispatchCircleYOffset() {
        if (height <= 0 || wordmarkView.height <= 0) return
        val rootLocation = IntArray(2)
        val wordmarkLocation = IntArray(2)
        getLocationOnScreen(rootLocation)
        wordmarkView.getLocationOnScreen(wordmarkLocation)
        val wordmarkCenter =
            wordmarkLocation[1] - rootLocation[1] + wordmarkView.height / 2f
        val offset = (height / 2f - wordmarkCenter) / height
        if (offset != lastCircleYOffset) {
            lastCircleYOffset = offset
            onCircleYOffsetChanged(offset)
        }
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        val SIN_OUT = TimeInterpolator { input ->
            kotlin.math.sin(input * Math.PI.toFloat() / 2f)
        }
        val CUBIC_OUT = TimeInterpolator { input ->
            1f - (1f - input) * (1f - input) * (1f - input)
        }
    }
}
