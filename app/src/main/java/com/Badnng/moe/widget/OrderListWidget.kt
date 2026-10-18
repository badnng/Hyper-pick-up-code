package com.Badnng.moe.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.Badnng.moe.R
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.helper.BrandIconResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext as ComposeLocalContext

internal val WidgetRevision = longPreferencesKey("refresh_revision")

/** 各尺寸的 Provider 共用内容，保留不同类型供系统识别默认尺寸和生成预览。 */
abstract class OrderListWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val revision = currentState<Preferences>()[WidgetRevision] ?: 0L
            // 活跃 Glance 会话里的 update 不会重新执行 provideGlance；用持久化修订号
            // 驱动查询，确保用户连续完成/切换分类时不会使用旧的捕获快照。
            val snapshot by produceState<WidgetSnapshot?>(null, revision, appWidgetId) {
                value = withContext(Dispatchers.IO) {
                    val filter = OrderWidgetPreferences(context).filter(appWidgetId)
                    try {
                        val orders = widgetOrders(OrderDatabase.getDatabase(context).orderDao()
                            .getIncompleteOrders().first(), filter)
                        WidgetSnapshot(
                            orders = orders,
                            filter = filter,
                            customBrandIcons = loadWidgetBrandIcons(context, orders),
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        WidgetSnapshot(emptyList(), filter, failed = true)
                    }
                }
            }
            WidgetContentWithTheme(snapshot, revision)
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        // 选择器使用固定示例，避免把真实取件码泄露到系统缓存的预览图。
        val sample = listOf(
            OrderEntity(id = "preview-food", takeoutCode = "A108", screenshotPath = "",
                recognizedText = "", brandName = "午餐", pickupLocation = "一楼取餐区"),
            OrderEntity(id = "preview-express", takeoutCode = "3-2068", screenshotPath = "",
                recognizedText = "", orderType = "快递", brandName = "菜鸟驿站", pickupLocation = "小区东门"),
        )
        val previewOrders = if (this is TallOrderWidget) sample + listOf(
            OrderEntity(id = "preview-food-extra", takeoutCode = "B26", screenshotPath = "",
                recognizedText = "", brandName = "饮品", pickupLocation = "二楼取餐区"),
            OrderEntity(id = "preview-express-extra", takeoutCode = "6-1059", screenshotPath = "",
                recognizedText = "", orderType = "快递", brandName = "快递服务站", pickupLocation = "北门"),
        ) else sample
        provideContent { WidgetContentWithTheme(WidgetSnapshot(previewOrders)) }
    }
}

// 预览按预设比例生成，独立于允许拖动到的最小尺寸；实际渲染仍使用 SizeMode.Exact。
class SquareOrderWidget : OrderListWidget() {
    override val previewSizeMode = SizeMode.Responsive(setOf(DpSize(160.dp, 160.dp)))
}

class WideOrderWidget : OrderListWidget() {
    override val previewSizeMode = SizeMode.Responsive(setOf(DpSize(320.dp, 160.dp)))
}

class TallOrderWidget : OrderListWidget() {
    override val previewSizeMode = SizeMode.Responsive(setOf(DpSize(240.dp, 320.dp)))
}

abstract class OrderWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        OrderWidgetUpdates.start(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        OrderWidgetUpdates.start(context)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        OrderWidgetUpdates.enqueue(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        OrderWidgetPreferences(context).remove(appWidgetIds)
        OrderWidgetUpdates.start(context, appWidgetIds.toSet())
        super.onDeleted(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        OrderWidgetUpdates.start(context)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        OrderWidgetPreferences(context).restore(oldWidgetIds, newWidgetIds)
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        // AppWidgetProvider 的恢复分发随后调用 onUpdate，重新读取数据库。
    }
}

class SquareOrderWidgetReceiver : OrderWidgetReceiver() {
    override val glanceAppWidget = SquareOrderWidget()
}

class WideOrderWidgetReceiver : OrderWidgetReceiver() {
    override val glanceAppWidget = WideOrderWidget()
}

class TallOrderWidgetReceiver : OrderWidgetReceiver() {
    override val glanceAppWidget = TallOrderWidget()
}

private data class WidgetSnapshot(
    val orders: List<OrderEntity>,
    val filter: OrderWidgetFilter = OrderWidgetFilter.ALL,
    val failed: Boolean = false,
    val customBrandIcons: Map<String, Bitmap> = emptyMap(),
)

@Composable
private fun WidgetContentWithTheme(snapshot: WidgetSnapshot?, revision: Long = 0L) {
    val context = LocalContext.current
    val cardSize = widgetCardSize(LocalSize.current)
    val home = actionStartActivity(OrderWidgetNavigation.home(context))
    // Miuix 的动态取色使用 Compose LocalContext；Glance 仅默认提供自己的 LocalContext。
    CompositionLocalProvider(ComposeLocalContext provides context) {
        val colors = OrderWidgetAppearance.read(context, revision)
        // 格位仍由桌面管理；仅把可见卡片贴底对齐，多出的顶部区域保持透明且可打开主页。
        Box(modifier = GlanceModifier.fillMaxSize().clickable(home), contentAlignment = Alignment.BottomCenter) {
            OrderWidgetContent(snapshot, colors, cardSize)
        }
    }
}

@Composable
private fun OrderWidgetContent(snapshot: WidgetSnapshot?, colors: OrderWidgetAppearance, size: DpSize) {
    val context = LocalContext.current
    val wide = size.width >= 240.dp
    val dense = size.height < 150.dp
    val horizontalInset = if (dense) 8.dp else 12.dp
    val verticalInset = if (dense) 6.dp else 10.dp
    val contentWidth = (size.width - horizontalInset * 2).coerceAtLeast(1.dp)
    val fontScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
    val filterHeight = maxOf(if (dense) 24.dp else 28.dp, (14 * fontScale + 6).dp)
    val footerHeight = maxOf(if (dense) 30.dp else 36.dp, ((if (wide) 14 else 24) * fontScale + 4).dp)
    val footerButtonSize = DpSize((contentWidth - 4.dp) / 2, footerHeight)
    val home = actionStartActivity(OrderWidgetNavigation.home(context))
    val orders = snapshot?.orders.orEmpty()
    val filter = snapshot?.filter ?: OrderWidgetFilter.ALL
    val count = if (snapshot == null || snapshot.failed) "—" else orders.size.toString()

    Column(
        modifier = GlanceModifier.fillMaxWidth().height(size.height).appWidgetBackground()
            .widgetSurface(colors.background, colors.miuix, size, radius = 24.dp)
            .clickable(home).padding(horizontal = horizontalInset)
            // 从底部留白中分出阴影空间，按钮内容和列表可用高度保持原有尺寸。
            .padding(top = verticalInset, bottom = verticalInset - WidgetButtonShadowSpace),
    ) {
        // 两行桌面高度也只占一行表头；窄尺寸用数字显示数量，完整含义保留在无障碍描述中。
        val showCountLabel = size.width >= 160.dp
        val countWidth = if (showCountLabel) 48.dp else 20.dp
        val filterWidth = if (wide) 156.dp else (contentWidth - countWidth - 4.dp).coerceAtLeast(1.dp)
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (showCountLabel) "待取 $count" else count,
                modifier = GlanceModifier.defaultWeight().clickable(home)
                    .semantics { contentDescription = "待取 $count 单" },
                style = TextStyle(color = colors.text, fontSize = if (wide) 15.sp else 13.sp,
                    fontWeight = FontWeight.Bold),
                maxLines = 1)
            Spacer(GlanceModifier.width(4.dp))
            WidgetFilters(filter, colors, DpSize(filterWidth, filterHeight), dense)
        }
        Spacer(GlanceModifier.height(if (dense) 2.dp else 4.dp))
        // 空状态也放在同一个 LazyColumn 中，保持根布局结构不变。否则分类切到空列表时，
        // 桌面会重新 inflate 整棵布局，原生选择器失去上一个位置而无法执行滑动过渡。
        LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            if (snapshot == null || snapshot.failed || orders.isEmpty()) {
                item(itemId = 0L) {
                    val bodyHeight = (size.height - verticalInset * 2 - filterHeight - footerHeight -
                        (if (dense) 4.dp else 8.dp)).coerceAtLeast(1.dp)
                    Box(
                        modifier = GlanceModifier.fillMaxWidth().height(bodyHeight)
                            .clickable(if (snapshot?.failed == true) actionRunCallback<RefreshWidgetOrdersAction>() else home),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when {
                                snapshot == null -> "正在加载…"
                                snapshot.failed -> "加载失败，点此重试"
                                else -> filter.emptyLabel
                            },
                            style = TextStyle(color = colors.secondaryText, fontSize = 12.sp,
                                textAlign = TextAlign.Center),
                        )
                    }
                }
            } else {
                items(orders, itemId = { widgetItemId(it.id) }) { order ->
                    WidgetOrderRow(order, order.brandName?.let { snapshot.customBrandIcons[it] },
                        wide, dense, colors, home)
                }
            }
        }
        Spacer(GlanceModifier.height(if (dense) 2.dp else 4.dp))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WidgetButton(if (wide) "淘宝身份码" else "淘宝\n身份码", actionStartActivity(OrderWidgetNavigation.identity(context, "taobao")),
                colors, colors.taobao, footerButtonSize, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(4.dp))
            WidgetButton(if (wide) "拼多多身份码" else "拼多多\n身份码", actionStartActivity(OrderWidgetNavigation.identity(context, "pdd")),
                colors, colors.pdd, footerButtonSize, GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun WidgetFilters(filter: OrderWidgetFilter, colors: OrderWidgetAppearance, size: DpSize, dense: Boolean) {
    // 阴影和表面各自一层，保持文字、点击区域与原来的分类栏总高度一致。
    val trackSize = if (colors.miuix) DpSize(size.width - 4.dp, size.height - 3.dp) else size
    Box(modifier = GlanceModifier.size(size.width, size.height)
        .then(if (colors.miuix) GlanceModifier.widgetFilterShadow(colors.shadow, size) else GlanceModifier)) {
        if (colors.miuix) {
            Box(GlanceModifier.padding(start = 2.dp, top = 1.dp)) {
                Box(GlanceModifier.size(trackSize.width, trackSize.height)
                    .widgetSurface(colors.filterTrack, true, trackSize)) {}
            }
        }
        WidgetFilterItems(filter, colors, size, dense)
    }
}

@Composable
private fun WidgetFilterItems(filter: OrderWidgetFilter, colors: OrderWidgetAppearance, size: DpSize, dense: Boolean) {
    val inset = if (colors.miuix) 2.dp else 0.dp
    val horizontalInset = if (colors.miuix) 4.dp else 0.dp
    val itemSize = DpSize((size.width - horizontalInset * 2) / 3, size.height - inset * 2)
    WidgetFilterIndicator(filter, colors, itemSize,
        GlanceModifier.size(size.width, size.height).padding(horizontal = horizontalInset))
    Row(modifier = GlanceModifier.size(size.width, size.height).padding(horizontal = horizontalInset),
        verticalAlignment = Alignment.CenterVertically) {
        OrderWidgetFilter.entries.forEach { option ->
            val selected = option == filter
            Box(
                modifier = GlanceModifier.defaultWeight().height(size.height).cornerRadius(15.dp)
                    .clickable(actionRunCallback<FilterWidgetOrdersAction>(actionParametersOf(WidgetFilter to option.name)))
                    .semantics { contentDescription = "${option.label}${if (option == filter) "，已选中" else ""}" },
                contentAlignment = Alignment.Center,
            ) {
                Box(GlanceModifier.fillMaxWidth().height(itemSize.height), contentAlignment = Alignment.Center) {
                    Text(option.label, style = TextStyle(
                        color = if (option == filter) colors.onSelected else colors.secondaryText,
                        fontSize = if (dense || size.width < 120.dp) 10.sp else 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    ), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun WidgetOrderRow(order: OrderEntity, customBrandIcon: Bitmap?, wide: Boolean, dense: Boolean,
    colors: OrderWidgetAppearance, home: Action) {
    // Miuix 是一张连续列表卡片；MD3E 保留独立的色块卡片，不再只换外框颜色。
    val rowBackground = if (colors.miuix) GlanceModifier
        else GlanceModifier.background(colors.surface).cornerRadius(15.dp)
    Column(GlanceModifier.fillMaxWidth().padding(bottom = if (dense) 2.dp else 4.dp)) {
        Row(
            modifier = rowBackground.fillMaxWidth().clickable(home)
                .padding(vertical = if (dense) 0.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier.size(if (dense) 36.dp else 40.dp)
                    .clickable(actionRunCallback<CompleteWidgetOrderAction>(actionParametersOf(WidgetOrderId to order.id)))
                    .semantics { contentDescription = "标记 ${order.takeoutCode} 已取" },
                contentAlignment = Alignment.Center,
            ) {
                Image(ImageProvider(R.drawable.ic_widget_complete), contentDescription = null,
                    modifier = GlanceModifier.size(if (colors.miuix) 21.dp else 24.dp),
                    colorFilter = ColorFilter.tint(colors.accent))
            }
            Column(modifier = GlanceModifier.defaultWeight().padding(end = 6.dp).clickable(home)) {
                Text(order.takeoutCode.ifBlank { "待取订单" }, style = TextStyle(
                    color = colors.text, fontSize = if (dense) 16.sp else if (wide) 20.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                ), maxLines = 2)
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val iconRes = BrandIconResolver.resolveBuiltinFallbackResId(LocalContext.current,
                        order.brandName, order.orderType)
                    val typeIcon = iconRes == R.drawable.ic_restaurant || iconRes == R.drawable.ic_drink ||
                        iconRes == R.drawable.ic_package
                    Image(
                        provider = customBrandIcon?.let { ImageProvider(it) } ?: ImageProvider(iconRes),
                        contentDescription = null,
                        modifier = GlanceModifier.size(if (dense) 14.dp else 16.dp).clickable(home),
                        // 品牌及自定义图标保留原色，通用类型图标跟随浅深色以保持可读。
                        colorFilter = if (customBrandIcon == null && typeIcon) ColorFilter.tint(colors.secondaryText) else null,
                    )
                    Spacer(GlanceModifier.width(4.dp))
                    Text(widgetOrderLabel(order), modifier = GlanceModifier.defaultWeight(), style = TextStyle(
                        color = colors.secondaryText, fontSize = if (dense) 10.sp else 11.sp,
                    ), maxLines = 1)
                }
            }
            if (wide && !order.pickupLocation.isNullOrBlank()) {
                Text(order.pickupLocation, modifier = GlanceModifier.defaultWeight().padding(end = 8.dp).clickable(home),
                    style = TextStyle(color = colors.secondaryText, fontSize = 12.sp), maxLines = 1)
            }
        }
        if (colors.miuix) {
            Box(GlanceModifier.fillMaxWidth().padding(start = if (dense) 36.dp else 40.dp)) {
                Spacer(GlanceModifier.fillMaxWidth().height(0.5.dp).background(colors.divider))
            }
        }
    }
}

@Composable
private fun WidgetButton(label: String, action: Action, appearance: OrderWidgetAppearance, colors: WidgetButtonColors,
    size: DpSize, modifier: GlanceModifier) {
    Box(modifier = modifier.height(size.height + WidgetButtonShadowSpace)
        .widgetButtonShadow(appearance.shadow, appearance.miuix, size).clickable(action)
        .semantics { contentDescription = label.replace("\n", "") }) {
        Box(modifier = GlanceModifier.fillMaxWidth().height(size.height)
            .widgetSurface(colors.background, appearance.miuix, size).clickable(action)
            .padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
            Text(label, style = TextStyle(color = colors.content, fontSize = 11.sp,
                fontWeight = if (appearance.miuix) FontWeight.Medium else FontWeight.Normal, textAlign = TextAlign.Center),
                maxLines = 2)
        }
    }
}

