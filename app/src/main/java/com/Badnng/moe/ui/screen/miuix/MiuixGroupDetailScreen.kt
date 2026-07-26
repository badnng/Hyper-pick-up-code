package com.Badnng.moe.ui.screen.miuix

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.data.db.OrderGroup
import com.Badnng.moe.helper.BrandIconResolver
import com.Badnng.moe.helper.ScreenshotStorage
import com.Badnng.moe.ui.component.formatOrderTime
import com.Badnng.moe.ui.miuix.MiuixBlurredBar
import com.Badnng.moe.ui.miuix.miuixScrollModifiers
import com.Badnng.moe.ui.miuix.rememberMiuixBackdrop
import com.Badnng.moe.ui.screen.FullScreenImageDialog
import com.Badnng.moe.ui.screen.openPddIdentityEntry
import com.Badnng.moe.ui.screen.openTaobaoIdentityEntry
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixGroupDetailScreen(
    group: OrderGroup,
    orders: List<OrderEntity>,
    completedCount: Int,
    totalCount: Int,
    onBack: () -> Unit,
    onMarkAllCompleted: () -> Unit,
    onMarkOrderCompleted: (OrderEntity) -> Unit,
    onOpenOrder: (OrderEntity) -> Unit,
    supportingPane: Boolean = false,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val preferences = remember(context) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }
    val hapticEnabled = remember(preferences) {
        preferences.getBoolean("haptic_enabled", true)
    }
    val performHaptic = {
        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberMiuixBackdrop()
    val blurEnabled = backdrop != null
    val screenshotPaths = remember(context, group.screenshotPath, orders) {
        val orderPaths = orders.map(OrderEntity::screenshotPath)
            .filter { ScreenshotStorage.exists(context, it) }
        when {
            orderPaths.isNotEmpty() -> orderPaths.distinct()
            ScreenshotStorage.exists(context, group.screenshotPath) -> listOf(group.screenshotPath)
            else -> emptyList()
        }
    }
    var fullScreenImagePath by remember(group.id) { mutableStateOf<String?>(null) }
    var originalExpanded by remember(group.id) { mutableStateOf(false) }
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f

    Scaffold(
        topBar = {
            MiuixBlurredBar(backdrop = backdrop, blurEnabled = blurEnabled) {
                TopAppBar(
                    title = "订单组详情",
                    color = if (blurEnabled) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { performHaptic(); onBack() }) {
                            Icon(
                                if (supportingPane) MiuixIcons.Regular.Close else MiuixIcons.Regular.Back,
                                contentDescription = if (supportingPane) "关闭" else "返回",
                            )
                        }
                    },
                    actions = {
                        if (completedCount < totalCount) {
                            IconButton(onClick = { performHaptic(); onMarkAllCompleted() }) {
                                Icon(MiuixIcons.Regular.Ok, "全部完成")
                            }
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Box(
            modifier = if (backdrop != null) {
                Modifier.fillMaxSize().layerBackdrop(backdrop)
            } else {
                Modifier.fillMaxSize()
            },
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .miuixScrollModifiers(topAppBarScrollBehavior)
                    .padding(top = innerPadding.calculateTopPadding()),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 12.dp,
                    bottom = bottomPadding + if (supportingPane) 24.dp else 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                screenshotPaths.firstOrNull()?.let { path ->
                    item(key = "groupScreenshot") {
                        MiuixGroupScreenshot(
                            path = path,
                            screenshotCount = screenshotPaths.size,
                            onClick = { performHaptic(); fullScreenImagePath = path },
                        )
                    }
                }
                item(key = "groupSummary") {
                    MiuixGroupSummary(
                        group = group,
                        completedCount = completedCount,
                        totalCount = totalCount,
                        progress = progress,
                    )
                }
                item { SmallTitle(text = "订单列表") }
                if (orders.isEmpty()) {
                    item {
                        Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                            Text(
                                text = "暂无订单",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(18.dp),
                            )
                        }
                    }
                } else {
                    items(orders, key = OrderEntity::id) { order ->
                        MiuixGroupOrderItem(
                            order = order,
                            onClick = { performHaptic(); onOpenOrder(order) },
                            onMarkCompleted = {
                                performHaptic()
                                onMarkOrderCompleted(order)
                            },
                        )
                    }
                }
                if (group.orderType == "快递") {
                    item { SmallTitle(text = "身份码快捷入口") }
                    item {
                        Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                            ArrowPreference(
                                title = "淘宝身份码",
                                summary = "打开淘宝菜鸟身份码",
                                onClick = { performHaptic(); openTaobaoIdentityEntry(context) },
                            )
                            ArrowPreference(
                                title = "拼多多身份码",
                                summary = "打开拼多多快递身份码",
                                onClick = { performHaptic(); openPddIdentityEntry(context) },
                            )
                        }
                    }
                }
                item { SmallTitle(text = "来源信息") }
                item {
                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MiuixGroupInfoRow("来源应用", group.sourceApp ?: "未记录")
                            MiuixGroupInfoRow("来源包名", group.sourcePackage ?: "未记录")
                            MiuixGroupInfoRow("创建时间", formatOrderTime(group.createdAt))
                            MiuixGroupInfoRow("订单组 ID", group.id.toString(), monospace = true)
                        }
                    }
                }
                item { SmallTitle(text = "原文记录") }
                item {
                    val originalText = group.recognizedText.ifBlank { "未记录" }
                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        onClick = { performHaptic(); originalExpanded = !originalExpanded },
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (originalExpanded) "收起原文" else "展开原文",
                                    style = MiuixTheme.textStyles.headline2,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = {
                                        performHaptic()
                                        context.getSystemService(ClipboardManager::class.java)
                                            ?.setPrimaryClip(ClipData.newPlainText("组识别原文", originalText))
                                    },
                                ) {
                                    Icon(MiuixIcons.Regular.Copy, contentDescription = "复制原文")
                                }
                            }
                            Spacer(Modifier.size(8.dp))
                            SelectionContainer {
                                Text(
                                    text = originalText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = if (originalExpanded) Int.MAX_VALUE else 5,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fullScreenImagePath?.let { path ->
        FullScreenImageDialog(path) { fullScreenImagePath = null }
    }
}

@Composable
private fun MiuixGroupScreenshot(path: String, screenshotCount: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val ratio = remember(context, path) {
        ScreenshotStorage.decodeBounds(context, path)
            ?.let { (width, height) -> width.toFloat() / height.coerceAtLeast(1) }
            ?.coerceIn(0.45f, 2.4f)
            ?: (4f / 3f)
    }
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        Card(onClick = onClick) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val previewHeight = (maxWidth / ratio).coerceIn(160.dp, 320.dp)
                AsyncImage(
                    model = ScreenshotStorage.imageModel(path),
                    contentDescription = "组识别截图",
                    modifier = Modifier.fillMaxWidth().height(previewHeight),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        if (screenshotCount > 1) {
            Text(
                text = "共 $screenshotCount 张截图，子订单详情中可分别查看",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun MiuixGroupSummary(
    group: OrderGroup,
    completedCount: Int,
    totalCount: Int,
    progress: Float,
) {
    val context = LocalContext.current
    val customIcon = remember(group.brandName) {
        BrandIconResolver.resolveCustomIconBitmap(context, group.brandName)
    }
    val fallbackIcon = remember(group.brandName, group.orderType) {
        BrandIconResolver.resolveBuiltinFallbackResId(context, group.brandName, group.orderType)
    }
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (customIcon != null) {
                    Image(
                        bitmap = customIcon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                } else {
                    Image(
                        painter = painterResource(fallbackIcon),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        style = MiuixTheme.textStyles.title1,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${group.brandName ?: group.orderType} · $totalCount 个订单",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Text(
                    text = if (completedCount == totalCount && totalCount > 0) "已完成" else "进行中",
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "完成进度",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$completedCount / $totalCount",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                height = 8.dp,
            )
        }
    }
}

@Composable
private fun MiuixGroupOrderItem(
    order: OrderEntity,
    onClick: () -> Unit,
    onMarkCompleted: () -> Unit,
) {
    val context = LocalContext.current
    val customIcon = remember(order.brandName) {
        BrandIconResolver.resolveCustomIconBitmap(context, order.brandName)
    }
    val fallbackIcon = remember(order.brandName, order.orderType) {
        BrandIconResolver.resolveBuiltinFallbackResId(context, order.brandName, order.orderType)
    }
    Card(modifier = Modifier.padding(horizontal = 12.dp), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (customIcon != null) {
                Image(customIcon.asImageBitmap(), null, modifier = Modifier.size(38.dp))
            } else {
                Image(painterResource(fallbackIcon), null, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = order.brandName ?: order.orderType,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                SelectionContainer {
                    Text(
                        text = order.takeoutCode,
                        fontSize = 23.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (order.isCompleted) {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        } else {
                            MiuixTheme.colorScheme.primary
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!order.pickupLocation.isNullOrBlank()) {
                    Text(
                        text = order.pickupLocation,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (order.isCompleted) "已完成" else "未完成",
                    style = MiuixTheme.textStyles.body2,
                    color = if (order.isCompleted) {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    } else {
                        MiuixTheme.colorScheme.primary
                    },
                )
                if (!order.isCompleted) {
                    IconButton(onClick = onMarkCompleted) {
                        Icon(MiuixIcons.Regular.Ok, contentDescription = "标记完成")
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixGroupInfoRow(label: String, value: String, monospace: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(1f),
        )
    }
}
