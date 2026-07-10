package com.Badnng.moe.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.Badnng.moe.R
import com.Badnng.moe.activity.MainActivity
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.data.db.OrderGroup

import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    group: OrderGroup,
    orders: List<OrderEntity>,
    onBack: () -> Unit,
    onMarkAllCompleted: () -> Unit,
    onMarkOrderCompleted: (OrderEntity) -> Unit
) {
    val context = LocalContext.current
    var showFullScreen by remember { mutableStateOf(false) }
    var fullScreenImagePath by remember { mutableStateOf("") }
    val completedCount = orders.count { it.isCompleted }
    val totalCount = orders.size
    val screenshotPaths = remember(group.screenshotPath, orders) {
        val fromOrders = orders
            .map { it.screenshotPath }
            .filter { it.isNotBlank() && File(it).exists() }
        if (fromOrders.isNotEmpty()) {
            fromOrders.distinct()
        } else if (group.screenshotPath.isNotBlank() && File(group.screenshotPath).exists()) {
            listOf(group.screenshotPath)
        } else {
            emptyList()
        }
    }
    val screenshotPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { screenshotPaths.size.coerceAtLeast(1) }
    )

    val surfaceColor = MaterialTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val canBlur = isRenderEffectSupported()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 内容层
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp))
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.brandName?.let { InfoItem("品牌", it) }
                    InfoItem("识别类型", group.orderType)
                    InfoItem("识别数量", "$totalCount 个取件码")
                    InfoItem("完成进度", "$completedCount/$totalCount")
                    InfoItem("来源应用", group.sourceApp ?: "无数据")
                    InfoItem("来源包名", group.sourcePackage ?: "暂无记录")
                }
            }

            if (group.orderType == "快递") {
                Spacer(modifier = Modifier.height(12.dp))
                Text("身份码快捷入口", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { openTaobaoIdentityEntry(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("打开淘宝身份码")
                    }
                    OutlinedButton(
                        onClick = { openPddIdentityEntry(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("打开拼多多身份码")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("原文记录", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            FullTextCodeBlock(text = group.recognizedText)

            Spacer(modifier = Modifier.height(16.dp))
            Text("取件码列表", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                orders.forEach { order ->
                    GroupOrderCard(
                        order = order,
                        onMarkCompleted = { onMarkOrderCompleted(order) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("截图副本", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (screenshotPaths.isNotEmpty()) {
                        HorizontalPager(
                            state = screenshotPagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp)
                        ) { page ->
                            val path = screenshotPaths[page]
                            AsyncImage(
                                model = File(path),
                                contentDescription = "截图副本 ${page + 1}",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        fullScreenImagePath = path
                                        showFullScreen = true
                                    },
                                contentScale = ContentScale.Fit
                            )
                        }
                        if (screenshotPaths.size > 1) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                screenshotPaths.forEachIndexed { index, _ ->
                                    val selected = screenshotPagerState.currentPage == index
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .size(if (selected) 8.dp else 6.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                            )
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (screenshotPaths.size > 1) "左右滑动查看截图，点击可全屏"
                            else "点击截图可查看全图",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.note),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp).alpha(0.2f),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "暂无图片数据",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    if (showFullScreen && fullScreenImagePath.isNotBlank()) {
        FullScreenImageDialog(imagePath = fullScreenImagePath) {
            showFullScreen = false
        }
    }

    // 毛玻璃 TopAppBar 覆盖层
    if (canBlur) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .textureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = 80f,
                        colors = BlurColors(brightness = -0.2f)
                    )
                    .frostedGlassMask()
            )
            TopAppBar(
                title = { Text(group.name, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (completedCount < totalCount) {
                        TextButton(onClick = onMarkAllCompleted) {
                            Icon(Icons.Default.Done, null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("全部完成")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    } else {
        Surface(
            color = surfaceColor.copy(alpha = 0.9f),
            tonalElevation = 2.dp
        ) {
            TopAppBar(
                title = { Text(group.name, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (completedCount < totalCount) {
                        TextButton(onClick = onMarkAllCompleted) {
                            Icon(Icons.Default.Done, null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("全部完成")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    }
    }
}

internal fun openTaobaoIdentityEntry(context: Context) {
    (context as? MainActivity)?.clearNotificationLaunchState()
    val pkg = "com.taobao.taobao"
    val lastmile = "https://pages-fast.m.taobao.com/wow/z/uniapp/1100333/last-mile-fe/m-end-school-tab/home"
    val candidates = listOf(
        "tbopen://m.taobao.com/tbopen/index.html?h5Url=" + Uri.encode(lastmile)
    )
    for (u in candidates) {
        try {
            val i = Intent(Intent.ACTION_VIEW, u.toUri())
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            return
        } catch (_: Exception) {
        }
    }
    try {
        val i = Intent(Intent.ACTION_VIEW, lastmile.toUri())
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        i.setClassName(pkg, "com.taobao.browser.BrowserActivity")
        context.startActivity(i)
    } catch (_: Exception) {
    }
}

internal fun openPddIdentityEntry(context: Context) {
    (context as? MainActivity)?.clearNotificationLaunchState()
    val pkg = "com.xunmeng.pinduoduo"
    val schemes = listOf(
        "pinduoduo://com.xunmeng.pinduoduo/mdkd/package",
        "pinduoduo://com.xunmeng.pinduoduo/",
        "pinduoduo://"
    )
    for (u in schemes) {
        try {
            val i = Intent(Intent.ACTION_VIEW, u.toUri())
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            return
        } catch (_: Exception) {
        }
    }
    try {
        val i = context.packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    } catch (_: Exception) {
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GroupOrderCard(
    order: OrderEntity,
    onMarkCompleted: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val motionScheme = MaterialTheme.motionScheme
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (order.isCompleted) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (order.isCompleted) {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = motionScheme.defaultSpatialSpec<IntSize>())
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.takeoutCode,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textDecoration = if (order.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (order.isCompleted) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    order.brandName?.let {
                        Text(
                            text = it,
                            fontSize = 12.sp,
                            textDecoration = if (order.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    if (!order.pickupLocation.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = order.pickupLocation!!,
                            fontSize = 12.sp,
                            textDecoration = if (order.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(order.takeoutCode)) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, "复制", Modifier.size(18.dp))
                    }

                    if (!order.isCompleted) {
                        IconButton(
                            onClick = onMarkCompleted,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Done, "完成", Modifier.size(18.dp))
                        }
                    } else {
                        Icon(
                            Icons.Default.Done,
                            "已完成",
                            Modifier.size(24.dp).alpha(0.5f),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (order.fullText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                // 展开/收起按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExpanded) "收起原文" else "展开查看原文",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val arrowRotation by animateFloatAsState(
                        targetValue = if (isExpanded) 180f else 0f,
                        animationSpec = motionScheme.defaultSpatialSpec<Float>()
                    )
                    Icon(
                        Icons.Default.ExpandMore,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp).rotate(arrowRotation)
                    )
                }
                // 展开内容
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec<Float>()) +
                            expandVertically(animationSpec = motionScheme.defaultSpatialSpec<IntSize>()),
                    exit = fadeOut(animationSpec = motionScheme.defaultEffectsSpec<Float>()) +
                           shrinkVertically(animationSpec = motionScheme.defaultSpatialSpec<IntSize>())
                ) {
                    SelectionContainer {
                        Text(
                            text = order.fullText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 1.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.frostedGlassMask(): Modifier = this.drawWithContent {
    drawContent()
    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.Black,
                0.55f to Color.Black,
                1f to Color.Transparent
            )
        ),
        blendMode = BlendMode.DstIn
    )
}
