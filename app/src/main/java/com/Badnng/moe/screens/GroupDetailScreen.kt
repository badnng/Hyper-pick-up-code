package com.Badnng.moe.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.Badnng.moe.OrderEntity
import com.Badnng.moe.OrderGroup
import com.Badnng.moe.R
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
    var showFullScreen by remember { mutableStateOf(false) }
    val completedCount = orders.count { it.isCompleted }
    val totalCount = orders.size

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
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
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(padding.calculateTopPadding()))
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(15.dp),
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
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (group.screenshotPath.isNotEmpty() && File(group.screenshotPath).exists()) {
                        AsyncImage(
                            model = File(group.screenshotPath),
                            contentDescription = "原图",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showFullScreen = true },
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "点击截图可查看全图",
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

    if (showFullScreen && group.screenshotPath.isNotEmpty()) {
        FullScreenImageDialog(imagePath = group.screenshotPath) {
            showFullScreen = false
        }
    }
}

@Composable
private fun GroupOrderCard(
    order: OrderEntity,
    onMarkCompleted: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

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
        modifier = Modifier.fillMaxWidth()
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
                SelectionContainer {
                    Text(
                        text = order.fullText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
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
