package com.Badnng.moe.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import com.Badnng.moe.R

/** 保持同一个原生布局和控件 ID，RemoteViews reapply 后由 LayoutTransition 移动选择器。 */
@Composable
internal fun WidgetFilterIndicator(
    filter: OrderWidgetFilter,
    colors: OrderWidgetAppearance,
    itemSize: DpSize,
    modifier: GlanceModifier,
) {
    val context = LocalContext.current
    val remoteViews = remember(context, filter, colors, itemSize) {
        createWidgetFilterIndicator(context, filter, colors, itemSize)
    }
    AndroidRemoteViews(remoteViews, modifier)
}

internal fun createWidgetFilterIndicator(
    context: Context,
    filter: OrderWidgetFilter,
    colors: OrderWidgetAppearance,
    itemSize: DpSize,
): RemoteViews = RemoteViews(context.packageName, R.layout.widget_filter_indicator).apply {
    val indicator = R.id.widget_filter_indicator
    fun icon(night: Int): Icon {
        val config = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        }
        val hostContext = context.createConfigurationContext(config)
        return Icon.createWithBitmap(widgetFilterIndicatorBitmap(itemSize, colors.miuix,
            fill = colors.selected.getColor(hostContext), shadow = colors.shadow.getColor(hostContext),
            edge = colors.text.getColor(hostContext)))
    }
    setViewLayoutWidth(indicator, itemSize.width.value, TypedValue.COMPLEX_UNIT_DIP)
    setViewLayoutHeight(indicator, itemSize.height.value, TypedValue.COMPLEX_UNIT_DIP)
    // 分别提供浅/深色图片，不能整体 tint，否则阴影和高光也会被染成填充色。
    setColorStateList(indicator, "setImageTintList", null)
    setIcon(indicator, "setImageIcon", icon(Configuration.UI_MODE_NIGHT_NO), icon(Configuration.UI_MODE_NIGHT_YES))
    // 只在选中项变化时发生显隐；订单刷新和重复点击同一分类不会重播动画。
    listOf(R.id.widget_filter_offset_first, R.id.widget_filter_offset_second).forEachIndexed { index, id ->
        setViewLayoutWidth(id, itemSize.width.value, TypedValue.COMPLEX_UNIT_DIP)
        setViewVisibility(id, if (filter.ordinal > index) View.VISIBLE else View.GONE)
    }
}
