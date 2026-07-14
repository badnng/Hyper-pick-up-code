/*
 * Page chrome dimensions and typography are derived from HyperCeiler
 * provision at commit 7266aaa0d698ad10795381c5bf23651c2e1719d0.
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.Badnng.moe.ui.oobe

import com.Badnng.moe.R
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

internal class OobeContentHeaderView(context: Context) : LinearLayout(context) {
    private val backIcon = ImageView(context)
    private val previewImage = ImageView(context)
    private val titleView = TextView(context)
    private val subtitleView = TextView(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.TRANSPARENT)

        val actionBar = LinearLayout(context).apply {
            orientation = VERTICAL
            minimumHeight = dp(56f)
            setPadding(dp(20f), dp(8f), dp(20f), dp(8f))
        }
        addView(
            actionBar,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(50f)
            },
        )

        backIcon.apply {
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = "上一步"
            isFocusable = true
            isClickable = true
            setImageResource(R.drawable.oobe_miuix_back)
            installOobeMiuixPressFeedback()
        }
        actionBar.addView(backIcon, LayoutParams(dp(40f), dp(40f)))

        previewImage.apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = null
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(
            previewImage,
            LayoutParams(dp(70f), dp(70f)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(10f)
                bottomMargin = dp(8f)
            },
        )

        val titleLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(35f), 0, dp(35f), dp(30f))
        }
        addView(
            titleLayout,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )

        titleView.apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            minHeight = dp(42f)
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            typeface = Typeface.DEFAULT
            textAlignment = TEXT_ALIGNMENT_CENTER
        }
        titleLayout.addView(
            titleView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )

        subtitleView.apply {
            gravity = Gravity.CENTER
            minHeight = dp(50f)
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT
            textAlignment = TEXT_ALIGNMENT_CENTER
        }
        titleLayout.addView(
            subtitleView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4f)
                bottomMargin = dp(8f)
            },
        )
    }

    fun bind(
        title: String,
        subtitle: String,
        previewDrawable: Int,
        darkTheme: Boolean,
        onBack: () -> Unit,
    ) {
        titleView.text = title
        subtitleView.text = subtitle
        subtitleView.visibility = if (subtitle.isBlank()) GONE else VISIBLE
        previewImage.setImageResource(previewDrawable)
        val primaryText = if (darkTheme) Color.WHITE else Color.BLACK
        val secondaryText = if (darkTheme) 0x99FFFFFF.toInt() else 0x80000000.toInt()
        val navigationIcon = if (darkTheme) 0xD9FFFFFF.toInt() else 0xB3000000.toInt()
        titleView.setTextColor(primaryText)
        subtitleView.setTextColor(secondaryText)
        backIcon.imageTintList = ColorStateList.valueOf(navigationIcon)
        backIcon.setOnClickListener {
            backIcon.performOobeMiuixClickFeedback()
            onBack()
        }
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}

internal class OobePrimaryButtonView(context: Context) : FrameLayout(context) {
    private val buttonFill = roundedRect(ENABLED_BACKGROUND_COLOR)
    private val argbEvaluator = ArgbEvaluator()
    private val labelView = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(ENABLED_LABEL_COLOR)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        typeface = Typeface.create(Typeface.DEFAULT, 500, false)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        isClickable = false
        isFocusable = false
    }
    private var enabledState: Boolean? = null
    private var colorAnimator: ValueAnimator? = null
    private var currentBackgroundColor = ENABLED_BACKGROUND_COLOR
    private var currentLabelColor = ENABLED_LABEL_COLOR

    init {
        minimumHeight = dp(50f)
        isFocusable = true
        background = buttonFill
        installOobeMiuixPressFeedback()
        addView(
            labelView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    fun bind(label: String, enabled: Boolean, onClick: () -> Unit) {
        labelView.text = label
        isEnabled = enabled
        isClickable = enabled
        alpha = 1f
        contentDescription = label
        setOnClickListener(
            if (enabled) {
                View.OnClickListener {
                    performOobeMiuixClickFeedback()
                    onClick()
                }
            } else {
                null
            },
        )

        val previousState = enabledState
        enabledState = enabled
        when {
            previousState == null -> applyEnabledColors(enabled)
            previousState != enabled -> animateEnabledColors(enabled)
        }
    }

    override fun onDetachedFromWindow() {
        colorAnimator?.cancel()
        colorAnimator = null
        super.onDetachedFromWindow()
    }

    private fun animateEnabledColors(enabled: Boolean) {
        colorAnimator?.cancel()
        val targetBackground = if (enabled) ENABLED_BACKGROUND_COLOR else DISABLED_BACKGROUND_COLOR
        val targetLabel = if (enabled) ENABLED_LABEL_COLOR else DISABLED_LABEL_COLOR
        if (!isAttachedToWindow || !ValueAnimator.areAnimatorsEnabled()) {
            applyColors(targetBackground, targetLabel)
            return
        }

        val startBackground = currentBackgroundColor
        val startLabel = currentLabelColor
        colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ENABLED_COLOR_TRANSITION_MILLIS
            interpolator = ENABLED_COLOR_INTERPOLATOR
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                applyColors(
                    backgroundColor = argbEvaluator.evaluate(
                        fraction,
                        startBackground,
                        targetBackground,
                    ) as Int,
                    labelColor = argbEvaluator.evaluate(
                        fraction,
                        startLabel,
                        targetLabel,
                    ) as Int,
                )
            }
            start()
        }
    }

    private fun applyEnabledColors(enabled: Boolean) {
        colorAnimator?.cancel()
        colorAnimator = null
        applyColors(
            backgroundColor = if (enabled) ENABLED_BACKGROUND_COLOR else DISABLED_BACKGROUND_COLOR,
            labelColor = if (enabled) ENABLED_LABEL_COLOR else DISABLED_LABEL_COLOR,
        )
    }

    private fun applyColors(backgroundColor: Int, labelColor: Int) {
        currentBackgroundColor = backgroundColor
        currentLabelColor = labelColor
        buttonFill.setColor(backgroundColor)
        labelView.setTextColor(labelColor)
    }

    private fun roundedRect(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(15f).toFloat()
        setColor(color)
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        val ENABLED_BACKGROUND_COLOR = 0xFF3482FF.toInt()
        val DISABLED_BACKGROUND_COLOR = 0x803482FF.toInt()
        val ENABLED_LABEL_COLOR = 0xFFFFFFFF.toInt()
        val DISABLED_LABEL_COLOR = 0x99FFFFFF.toInt()
        const val ENABLED_COLOR_TRANSITION_MILLIS = 240L

        val ENABLED_COLOR_INTERPOLATOR = PathInterpolator(0.2f, 0f, 0f, 1f)
    }
}
