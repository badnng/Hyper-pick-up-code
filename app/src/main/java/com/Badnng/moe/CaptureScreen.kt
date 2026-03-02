package com.Badnng.moe.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.Badnng.moe.OrderEntity
import com.Badnng.moe.OrderViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.vibrancy
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CaptureScreen(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    backdrop: com.kyant.backdrop.Backdrop
) {
    val viewModel: OrderViewModel = viewModel()
    val incompleteOrders by viewModel.incompleteOrders.collectAsState()
    val completedOrders by viewModel.completedOrders.collectAsState()

    var showCompletedOnly by remember { mutableStateOf(false) }
    var orderToDelete by remember { mutableStateOf<OrderEntity?>(null) }

    val currentOrders by remember(showCompletedOnly, incompleteOrders, completedOrders) {
        derivedStateOf { if (showCompletedOnly) completedOrders else incompleteOrders }
    }

    val listState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "澎湃记",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusButton(
                    selected = !showCompletedOnly,
                    label = "待取",
                    count = incompleteOrders.size,
                    onClick = { showCompletedOnly = false }
                )
                StatusButton(
                    selected = showCompletedOnly,
                    label = "已取",
                    count = completedOrders.size,
                    onClick = { showCompletedOnly = true }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (currentOrders.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (showCompletedOnly) "暂无已完成单据" else "暂无待取单据",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScrollbar(listState, width = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = bottomPadding + 32.dp,
                        start = 16.dp,
                        end = 16.dp
                    )
                ) {
                    items(
                        items = currentOrders,
                        key = { it.id }
                    ) { order ->
                        Box(Modifier.animateItem()) {
                            OrderCard(
                                order = order,
                                onMarkCompleted = { viewModel.markAsCompleted(order.id) },
                                onDelete = { orderToDelete = order },
                                isCompleted = showCompletedOnly
                            )
                        }
                    }
                }
            }
        }

        // 删除确认弹窗
        if (orderToDelete != null) {
            DeleteConfirmDialog(
                onDismiss = { orderToDelete = null },
                onConfirm = {
                    orderToDelete?.let { viewModel.deleteOrder(it) }
                    orderToDelete = null
                }
            )
        }
    }
}

@Composable
fun DeleteConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var animateTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animateTrigger = true }

    // 背景渐变 Alpha
    val bgAlpha by animateFloatAsState(
        targetValue = if (animateTrigger) 0.55f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "bg_alpha"
    )

    // 卡片缩放动画
    val cardScale by animateFloatAsState(
        targetValue = if (animateTrigger) 1f else 0.85f,
        animationSpec = tween(durationMillis = 350, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "card_scale"
    )

    // 卡片透明度动画
    val cardAlpha by animateFloatAsState(
        targetValue = if (animateTrigger) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "card_alpha"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = bgAlpha)), // 渐变黑色半透明背景
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .wrapContentHeight()
                    .scale(cardScale) // 中间弹出缩放
                    .alpha(cardAlpha), // 渐变弹出
                shape = RoundedCornerShape(15.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "确认删除",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "删除后将无法找回此条记录，确定要继续吗？",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(15.dp)
                        ) {
                            Text("取消", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(15.dp)
                        ) {
                            Text("删除", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// 其余辅助函数保持不变
@Composable
fun StatusButton(selected: Boolean, label: String, count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(15.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(36.dp).widthIn(min = 80.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(text = "$label $count", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun OrderCard(
    order: OrderEntity,
    onMarkCompleted: () -> Unit,
    onDelete: () -> Unit,
    isCompleted: Boolean
) {
    val timeStr = remember(order.createdAt) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        sdf.format(Date(order.createdAt))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(15.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "取餐码",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = order.takeoutCode,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (isCompleted) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Text(
                text = "时间: $timeStr",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isCompleted) {
                    FilledTonalButton(
                        onClick = onMarkCompleted,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Text("完成")
                    }
                }

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Text("删除")
                }
            }
        }
    }
}

/**
 * 垂直滚动条 Modifier
 */
fun Modifier.verticalScrollbar(
    state: LazyListState,
    width: Dp = 6.dp,
    color: Color = Color.Gray
): Modifier = composed {
    val alpha by animateFloatAsState(
        targetValue = if (state.isScrollInProgress) 0.8f else 0f,
        animationSpec = tween(durationMillis = if (state.isScrollInProgress) 150 else 500),
        label = "scrollbar_alpha"
    )

    drawWithContent {
        drawContent()

        val layoutInfo = state.layoutInfo
        val canScroll = state.canScrollForward || state.canScrollBackward
        val visibleItemsInfo = layoutInfo.visibleItemsInfo

        if (canScroll && visibleItemsInfo.isNotEmpty()) {
            val viewportHeight = size.height
            val totalItemsCount = layoutInfo.totalItemsCount
            val averageItemHeight = visibleItemsInfo.sumOf { it.size } / visibleItemsInfo.size.toFloat()
            val spacingPx = 12.dp.toPx()
            val totalContentHeight = (averageItemHeight * totalItemsCount) +
                    (spacingPx * (totalItemsCount - 1)) +
                    layoutInfo.beforeContentPadding +
                    layoutInfo.afterContentPadding

            val scrollOffset = state.firstVisibleItemIndex * (averageItemHeight + spacingPx) +
                    state.firstVisibleItemScrollOffset

            val scrollbarHeight = ((viewportHeight / totalContentHeight) * viewportHeight)
                .coerceIn(32.dp.toPx(), viewportHeight)

            val maxScrollOffset = (totalContentHeight - viewportHeight).coerceAtLeast(1f)
            val scrollProgress = (scrollOffset / maxScrollOffset).coerceIn(0f, 1f)
            val scrollbarOffsetY = (viewportHeight - scrollbarHeight) * scrollProgress

            val xOffset = size.width - width.toPx() - 2.dp.toPx()

            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(xOffset, scrollbarOffsetY),
                size = Size(width.toPx(), scrollbarHeight),
                cornerRadius = CornerRadius(width.toPx() / 2)
            )
        }
    }
}
