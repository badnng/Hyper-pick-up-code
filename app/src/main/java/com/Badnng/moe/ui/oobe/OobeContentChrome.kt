/*
 * Page chrome dimensions and typography are derived from HyperCeiler
 * provision at commit 7266aaa0d698ad10795381c5bf23651c2e1719d0.
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.Badnng.moe.ui.oobe

import com.Badnng.moe.R
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
        backIcon.setOnClickListener { onBack() }
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}

internal class OobePrimaryButtonView(context: Context) : FrameLayout(context) {
    private val labelView = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        typeface = Typeface.create(Typeface.DEFAULT, 500, false)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        isClickable = false
        isFocusable = false
    }

    init {
        minimumHeight = dp(50f)
        isFocusable = true
        addView(
            labelView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    fun bind(label: String, enabled: Boolean, onClick: () -> Unit) {
        labelView.text = label
        isEnabled = enabled
        isClickable = enabled
        alpha = if (enabled) 1f else 0.5f
        background = createBackground()
        contentDescription = label
        setOnClickListener(if (enabled) View.OnClickListener { onClick() } else null)
    }

    private fun createBackground(): RippleDrawable {
        val content = roundedRect(0xFF3482FF.toInt())
        val mask = roundedRect(Color.WHITE)
        return RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, mask)
    }

    private fun roundedRect(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(15f).toFloat()
        setColor(color)
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
