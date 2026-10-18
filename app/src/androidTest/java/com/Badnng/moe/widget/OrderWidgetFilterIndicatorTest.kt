package com.Badnng.moe.widget

import android.animation.LayoutTransition
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.Badnng.moe.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@RunWith(AndroidJUnit4::class)
class OrderWidgetFilterIndicatorTest {
    @Test
    fun remoteViewsInflatesAndReusesOneIndicatorForAllSixDirections() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val colors = OrderWidgetAppearance.fromPalettes(true, "system",
                miuixWidgetPalette(lightColorScheme()), miuixWidgetPalette(darkColorScheme()))
            val itemSize = DpSize(48.dp, 24.dp)
            val host = FrameLayout(context)
            val root = createWidgetFilterIndicator(context, OrderWidgetFilter.ALL, colors, itemSize)
                .apply(context, host) as LinearLayout
            host.addView(root)
            root.layoutDirection = View.LAYOUT_DIRECTION_LTR
            val transition = checkNotNull(root.layoutTransition)
            assertTrue(transition.isTransitionTypeEnabled(LayoutTransition.CHANGE_APPEARING))
            assertTrue(transition.isTransitionTypeEnabled(LayoutTransition.CHANGE_DISAPPEARING))
            // 此测试核对实际 RemoteViews 白名单、reapply 和最终位置，不以计时断言模拟桌面帧率。
            root.layoutTransition = null
            val indicator = root.findViewById<ImageView>(R.id.widget_filter_indicator)
            val density = context.resources.displayMetrics.density
            fun layout() {
                val width = (144 * density).toInt()
                val height = (28 * density).toInt()
                host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                host.layout(0, 0, width, height)
            }
            layout()
            // 0→1→2→0→2→1→0，包含所有有向切换，也包含重复点击。
            for (filter in listOf(OrderWidgetFilter.FOOD, OrderWidgetFilter.EXPRESS,
                OrderWidgetFilter.ALL, OrderWidgetFilter.EXPRESS, OrderWidgetFilter.FOOD,
                OrderWidgetFilter.ALL, OrderWidgetFilter.ALL)) {
                createWidgetFilterIndicator(context, filter, colors, itemSize).reapply(context, root)
                layout()
                assertSame(indicator, root.findViewById<ImageView>(R.id.widget_filter_indicator))
                assertEquals(filter.ordinal * indicator.width, indicator.left)
            }
        }
    }
}
