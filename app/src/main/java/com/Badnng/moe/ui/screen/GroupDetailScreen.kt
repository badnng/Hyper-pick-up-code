package com.Badnng.moe.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.Badnng.moe.activity.MainActivity
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.data.db.OrderGroup
import com.Badnng.moe.helper.BrandIconResolver
import com.Badnng.moe.helper.ScreenshotStorage
import com.Badnng.moe.ui.component.formatOrderTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    group: OrderGroup,
    orders: List<OrderEntity>,
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
    val completedCount = orders.count(OrderEntity::isCompleted)
    val totalCount = orders.size
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("订单组详情", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { performHaptic(); onBack() }) {
                        Icon(
                            imageVector = if (supportingPane) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (supportingPane) "关闭" else "返回",
                        )
                    }
                },
                actions = {
                    if (completedCount < totalCount) {
                        TextButton(onClick = { performHaptic(); onMarkAllCompleted() }) {
                            Icon(Icons.Default.Done, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("全部完成")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = bottomPadding + if (supportingPane) 24.dp else 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            screenshotPaths.firstOrNull()?.let { path ->
                item(key = "groupScreenshot") {
                    Md3eGroupScreenshot(
                        path = path,
                        screenshotCount = screenshotPaths.size,
                        onClick = { performHaptic(); fullScreenImagePath = path },
                    )
                }
            }
            item(key = "groupSummary") {
                Md3eGroupSummary(group, completedCount, totalCount, progress)
            }
            item { Md3eGroupSectionTitle("订单列表") }
            if (orders.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Text(
                            text = "暂无订单",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                }
            } else {
                items(orders, key = OrderEntity::id) { order ->
                    Md3eGroupOrderItem(
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
                item { Md3eGroupSectionTitle("身份码快捷入口") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { performHaptic(); openTaobaoIdentityEntry(context) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp),
                        ) {
                            Text("淘宝身份码")
                        }
                        OutlinedButton(
                            onClick = { performHaptic(); openPddIdentityEntry(context) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp),
                        ) {
                            Text("拼多多身份码")
                        }
                    }
                }
            }
            item { Md3eGroupSectionTitle("来源信息") }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Md3eGroupInfoRow("来源应用", group.sourceApp ?: "未记录")
                        Md3eGroupInfoRow("来源包名", group.sourcePackage ?: "未记录")
                        Md3eGroupInfoRow("创建时间", formatOrderTime(group.createdAt))
                        Md3eGroupInfoRow("订单组 ID", group.id.toString(), monospace = true)
                    }
                }
            }
            item { Md3eGroupSectionTitle("原文记录") }
            item {
                val originalText = group.recognizedText.ifBlank { "未记录" }
                Surface(
                    onClick = { performHaptic(); originalExpanded = !originalExpanded },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (originalExpanded) "收起原文" else "展开原文",
                                style = MaterialTheme.typography.titleMedium,
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
                                Icon(Icons.Default.ContentCopy, contentDescription = "复制原文")
                            }
                        }
                        SelectionContainer {
                            Text(
                                text = originalText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (originalExpanded) Int.MAX_VALUE else 5,
                                overflow = TextOverflow.Ellipsis,
                            )
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
private fun Md3eGroupScreenshot(path: String, screenshotCount: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val ratio = remember(context, path) {
        ScreenshotStorage.decodeBounds(context, path)
            ?.let { (width, height) -> width.toFloat() / height.coerceAtLeast(1) }
            ?.coerceIn(0.45f, 2.4f)
            ?: (4f / 3f)
    }
    Column {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val previewHeight = (maxWidth / ratio).coerceIn(160.dp, 320.dp)
            AsyncImage(
                model = ScreenshotStorage.imageModel(path),
                contentDescription = "组识别截图",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeight)
                    .clip(RoundedCornerShape(15.dp))
                    .clickable(onClick = onClick),
                contentScale = ContentScale.Fit,
            )
        }
        if (screenshotCount > 1) {
            Text(
                text = "共 $screenshotCount 张截图，子订单详情中可分别查看",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun Md3eGroupSummary(
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (customIcon != null) {
                    Image(customIcon.asImageBitmap(), null, modifier = Modifier.size(44.dp))
                } else {
                    Image(painterResource(fallbackIcon), null, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${group.brandName ?: group.orderType} · $totalCount 个订单",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (completedCount == totalCount && totalCount > 0) "已完成" else "进行中",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
            Row {
                Text(
                    text = "完成进度",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$completedCount / $totalCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

@Composable
private fun Md3eGroupOrderItem(
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
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text(
                        text = order.takeoutCode,
                        fontSize = 23.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (order.isCompleted) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!order.pickupLocation.isNullOrBlank()) {
                    Text(
                        text = order.pickupLocation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (order.isCompleted) "已完成" else "未完成",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (order.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!order.isCompleted) {
                        IconButton(onClick = onMarkCompleted) {
                            Icon(Icons.Default.Done, contentDescription = "标记完成")
                        }
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "查看识别详情",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Md3eGroupSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun Md3eGroupInfoRow(label: String, value: String, monospace: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(1f),
        )
    }
}

internal fun openTaobaoIdentityEntry(context: Context) {
    (context as? MainActivity)?.clearNotificationLaunchState()
    val packageName = "com.taobao.taobao"
    val target = "https://pages-fast.m.taobao.com/wow/z/uniapp/1100333/last-mile-fe/m-end-school-tab/home"
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, ("tbopen://m.taobao.com/tbopen/index.html?h5Url=" + Uri.encode(target)).toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return
    } catch (_: Exception) {
    }
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, target.toUri())
                .setClassName(packageName, "com.taobao.browser.BrowserActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: Exception) {
    }
}

internal fun openPddIdentityEntry(context: Context) {
    (context as? MainActivity)?.clearNotificationLaunchState()
    val packageName = "com.xunmeng.pinduoduo"
    for (uri in listOf(
        "pinduoduo://com.xunmeng.pinduoduo/mdkd/package",
        "pinduoduo://com.xunmeng.pinduoduo/",
        "pinduoduo://",
    )) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        } catch (_: Exception) {
        }
    }
    try {
        context.packageManager.getLaunchIntentForPackage(packageName)?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    } catch (_: Exception) {
    }
}
