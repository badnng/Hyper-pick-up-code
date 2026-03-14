package com.Badnng.moe.screens

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Badnng.moe.MainActivity
import com.Badnng.moe.OrderEntity
import com.Badnng.moe.OrderViewModel
import com.Badnng.moe.R
import com.Badnng.moe.NotificationHelper
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

@Composable
fun CaptureScreen(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    backdrop: com.kyant.backdrop.Backdrop,
    onEditModeChange: (Boolean) -> Unit = {},
    onNavigateToDetail: (OrderEntity) -> Unit = {}
) {
    val viewModel: OrderViewModel = viewModel()
    val incompleteOrders by viewModel.incompleteOrders.collectAsState()
    val completedOrders by viewModel.completedOrders.collectAsState()

    CaptureScreenContent(
        incompleteOrders = incompleteOrders,
        completedOrders = completedOrders,
        onMarkCompleted = { viewModel.markAsCompleted(it) },
        onMarkMultipleCompleted = { ids ->
            ids.forEach { viewModel.markAsCompleted(it) }
        },
        onDeleteOrder = { viewModel.deleteOrder(it) },
        onDeleteMultiple = { ids ->
            ids.forEach { id ->
                val order = (incompleteOrders + completedOrders).find { it.id == id }
                order?.let { viewModel.deleteOrder(it) }
            }
        },
        onClearAllCompleted = { viewModel.deleteCompletedOrders() },
        onEditModeChange = onEditModeChange,
        onNavigateToDetail = onNavigateToDetail,
        modifier = modifier,
        bottomPadding = bottomPadding,
        backdrop = backdrop
    )
}

@Composable
fun CaptureScreenContent(
    incompleteOrders: List<OrderEntity>,
    completedOrders: List<OrderEntity>,
    onMarkCompleted: (String) -> Unit,
    onMarkMultipleCompleted: (Set<String>) -> Unit,
    onDeleteOrder: (OrderEntity) -> Unit,
    onDeleteMultiple: (Set<String>) -> Unit,
    onClearAllCompleted: () -> Unit,
    onEditModeChange: (Boolean) -> Unit,
    onNavigateToDetail: (OrderEntity) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    backdrop: com.kyant.backdrop.Backdrop
) {
    var showCompletedOnly by remember { mutableStateOf(false) }
    var orderToDelete by remember { mutableStateOf<OrderEntity?>(null) }
    var selectedOrderForQr by remember { mutableStateOf<OrderEntity?>(null) }
    var highlightOrderId by remember { mutableStateOf<String?>(null) }

    var isEditMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showMultiDeleteConfirm by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    var showCategoryFilters by remember { mutableStateOf(false) }
    var selectedCategories by remember { mutableStateOf(setOf("餐食", "饮品", "快递")) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    val hapticEnabled = remember(prefs) { prefs.getBoolean("haptic_enabled", true) }

    val performHaptic = {
        if (hapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val completedListState = rememberLazyListState()
    val activity = context as? MainActivity

    LaunchedEffect(isEditMode) {
        onEditModeChange(isEditMode)
    }

    LaunchedEffect(activity?.intentToProcess, incompleteOrders, completedOrders) {
        val intent = activity?.intentToProcess
        if (intent?.hasExtra("highlight_order_id") == true) {
            val orderId = intent.getStringExtra("highlight_order_id")
            val isOrderCompleted = completedOrders.any { it.id == orderId }
            val isOrderIncomplete = incompleteOrders.any { it.id == orderId }

            if (isOrderCompleted || isOrderIncomplete) {
                showCompletedOnly = isOrderCompleted
                highlightOrderId = orderId
                delay(500)
                highlightOrderId = null
                if (intent.getBooleanExtra("show_qr_detail", false) == false) {
                    activity.intentToProcess = null
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Top))
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "澎湃记", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 16.dp))

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusButton(
                        selected = !showCompletedOnly,
                        label = "待取",
                        count = incompleteOrders.size,
                        onClick = {
                            if (!isEditMode) {
                                performHaptic()
                                if (!showCompletedOnly) {
                                    showCategoryFilters = !showCategoryFilters
                                } else {
                                    showCompletedOnly = false
                                }
                            }
                        }
                    )
                    StatusButton(
                        selected = showCompletedOnly,
                        label = "已取",
                        count = completedOrders.size,
                        onClick = {
                            if (!isEditMode) {
                                performHaptic()
                                showCompletedOnly = true
                                showCategoryFilters = false
                            }
                        }
                    )
                }

                IconButton(onClick = {
                    performHaptic()
                    isEditMode = !isEditMode
                    if (!isEditMode) selectedIds = emptySet()
                }) {
                    Icon(if (isEditMode) Icons.Default.Close else Icons.Default.SettingsSuggest, contentDescription = "管理", tint = if (isEditMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }

            AnimatedVisibility(
                visible = showCategoryFilters && !showCompletedOnly,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("餐食", "饮品", "快递").forEach { category ->
                        FilterChip(
                            label = category,
                            selected = selectedCategories.contains(category),
                            onClick = {
                                performHaptic()
                                selectedCategories = if (selectedCategories.contains(category)) {
                                    selectedCategories - category
                                } else {
                                    selectedCategories + category
                                }
                            }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isEditMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val currentOrders = if (showCompletedOnly) completedOrders else incompleteOrders
                    TextButton(onClick = {
                        performHaptic()
                        if (selectedIds.size == currentOrders.size) selectedIds = emptySet()
                        else selectedIds = currentOrders.map { it.id }.toSet()
                    }) {
                        Text(if (selectedIds.size == currentOrders.size) "取消全选" else "全选")
                    }
                    Spacer(Modifier.weight(1f))

                    if (selectedIds.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 新增按钮：选中未完成为已完成
                            if (!showCompletedOnly) {
                                Button(
                                    onClick = {
                                        performHaptic()
                                        onMarkMultipleCompleted(selectedIds)
                                        selectedIds = emptySet()
                                        isEditMode = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(15.dp)
                                ) {
                                    Text("选中未完成为已完成")
                                }
                            }

                            Button(
                                onClick = { performHaptic(); showMultiDeleteConfirm = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(15.dp)
                            ) {
                                Text("删除(${selectedIds.size})")
                            }
                        }
                    } else if (showCompletedOnly && completedOrders.isNotEmpty()) {
                        OutlinedButton(onClick = { performHaptic(); showClearAllConfirm = true }, shape = RoundedCornerShape(15.dp)) {
                            Text("清空全部")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Crossfade(
                targetState = showCompletedOnly to selectedCategories,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "listTransition",
                animationSpec = tween(300)
            ) { (isCompletedOnly, categories) ->
                val currentOrders = remember(isCompletedOnly, categories, incompleteOrders, completedOrders) {
                    val baseList = if (isCompletedOnly) completedOrders else incompleteOrders
                    if (isCompletedOnly) baseList else baseList.filter { categories.contains(it.orderType) }
                }

                val currentState = if (isCompletedOnly) {
                    completedListState
                } else {
                    remember(categories) { LazyListState() }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (currentOrders.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "暂无数据", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                    } else {
                        LazyColumn(
                            state = currentState,
                            modifier = Modifier.fillMaxSize().verticalScrollbar(currentState, 6.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding + 32.dp, start = 16.dp, end = 16.dp)
                        ) {
                            items(items = currentOrders, key = { it.id }) { order ->
                                Row(
                                    modifier = Modifier.animateItem(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AnimatedVisibility(
                                        visible = isEditMode,
                                        enter = expandHorizontally() + fadeIn(),
                                        exit = shrinkHorizontally() + fadeOut()
                                    ) {
                                        Checkbox(
                                            checked = selectedIds.contains(order.id),
                                            onCheckedChange = { checked ->
                                                performHaptic()
                                                if (checked == true) selectedIds += order.id
                                                else selectedIds -= order.id
                                            },
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }

                                    OrderCard(
                                        order = order,
                                        onMarkCompleted = { performHaptic(); onMarkCompleted(order.id) },
                                        onDelete = { performHaptic(); orderToDelete = order },
                                        onShowQr = { performHaptic(); selectedOrderForQr = order },
                                        isCompleted = isCompletedOnly,
                                        isHighlighted = highlightOrderId == order.id,
                                        isEditMode = isEditMode,
                                        onNavigateToDetail = onNavigateToDetail,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (orderToDelete != null) {
            DeleteConfirmDialog(onDismiss = { orderToDelete = null }, onConfirm = { orderToDelete?.let { onDeleteOrder(it) }; orderToDelete = null })
        }

        if (showMultiDeleteConfirm) {
            DeleteConfirmDialog(title = "确认删除所选？", description = "确定要删除选中的 ${selectedIds.size} 条记录吗？该操作不可撤销！", onDismiss = { showMultiDeleteConfirm = false }, onConfirm = { onDeleteMultiple(selectedIds); selectedIds = emptySet(); isEditMode = false; showMultiDeleteConfirm = false })
        }

        if (showClearAllConfirm) {
            DeleteConfirmDialog(title = "确认清空？", description = "确定要清空所有已完成的记录吗？该操作不可撤销！", onDismiss = { showClearAllConfirm = false }, onConfirm = { onClearAllCompleted(); isEditMode = false; showClearAllConfirm = false })
        }

        if (selectedOrderForQr != null) {
            QrCodeDialog(order = selectedOrderForQr!!, onDismiss = { selectedOrderForQr = null })
        }
    }
}

@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.height(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(text = label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
fun QrCodeDialog(order: OrderEntity, onDismiss: () -> Unit) {
    val qrBitmap = remember(order.qrCodeData) { if (!order.qrCodeData.isNullOrEmpty()) { try { generateQrCode(order.qrCodeData, 512) } catch (e: Exception) { null } } else null }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        modifier = Modifier.fillMaxWidth(0.8f),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(text = "取餐码", fontSize = 12.sp, color = Color.Gray, letterSpacing = 1.2.sp)
                Text(text = order.takeoutCode, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text(text = "请向商家出示此码", fontSize = 12.sp, color = Color.Gray.copy(alpha = 0.6f), fontWeight = FontWeight.Medium)
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.fillMaxWidth(0.7f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                    if (qrBitmap != null) { Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "二维码", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                    else { Text(text = "暂无数据", color = Color.Gray, fontSize = 12.sp) }
                }
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(32.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false)
    )
}

fun generateQrCode(content: String, size: Int): Bitmap {
    val hints: MutableMap<EncodeHintType, Any> = EnumMap(EncodeHintType::class.java)
    hints[EncodeHintType.CHARACTER_SET] = "UTF-8"; hints[EncodeHintType.MARGIN] = 0
    val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val width = bitMatrix.width; val height = bitMatrix.height
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) { pixels[offset + x] = if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}

@Composable
fun OrderCard(
    order: OrderEntity,
    onMarkCompleted: () -> Unit,
    onDelete: () -> Unit,
    onShowQr: () -> Unit,
    isCompleted: Boolean,
    isHighlighted: Boolean = false,
    isEditMode: Boolean = false,
    modifier: Modifier = Modifier,
    onNavigateToDetail: (OrderEntity) -> Unit = {}
) {
    val context = LocalContext.current
    val timeStr = remember(order.createdAt) { val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault()); sdf.format(Date(order.createdAt)) }
    val brandIcon = remember(order.brandName, order.orderType) {
        val resName = when (order.brandName) {
            "麦当劳" -> "ic_mcdonalds"; "肯德基", "KFC" -> "ic_kfc"; "瑞幸" -> "ic_luckin"; "喜茶" -> "ic_heytea"; "星巴克" -> "ic_starbucks"; "霸王茶姬" -> "ic_chagee"; "古茗" -> "ic_goodme"; "蜜雪冰城" -> "ic_mixue"; else -> null
        }
        val resId = if (resName != null) context.resources.getIdentifier(resName, "drawable", context.packageName) else 0
        if (resId != 0) resId else when (order.orderType) {
            "饮品" -> R.drawable.ic_drink
            "快递" -> R.drawable.ic_package
            else -> R.drawable.ic_restaurant
        }
    }
    val highlightColor by animateColorAsState(targetValue = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else Color.Transparent, animationSpec = tween(1000), label = "highlight")

    Card(
        modifier = modifier.fillMaxWidth().clickable { onNavigateToDetail(order) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(2.dp, highlightColor),
        shape = RoundedCornerShape(15.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = painterResource(id = brandIcon), contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.Unspecified)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        if (order.orderType == "快递") {
                            Text(text = "取件码", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = order.takeoutCode, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            if (!order.pickupLocation.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                    Spacer(Modifier.width(4.dp))
                                    Text(text = "取件位置", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                }
                                Text(text = order.pickupLocation!!, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        } else {
                            Text(text = order.brandName ?: "取餐码", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = order.takeoutCode, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!order.qrCodeData.isNullOrEmpty()) { IconButton(onClick = onShowQr) { Icon(Icons.Default.QrCode, "二维码", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), modifier = Modifier.size(24.dp)) } }
                    if (isCompleted) { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp)) }
                }
            }
            Text(text = "时间: $timeStr", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) )

            AnimatedVisibility(
                visible = !isEditMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!isCompleted) { FilledTonalButton(onClick = onMarkCompleted, modifier = Modifier.weight(1f), shape = RoundedCornerShape(15.dp)) { Text("完成") } }
                        OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error), shape = RoundedCornerShape(15.dp)) { Text("删除") }
                    }

                    if (!isCompleted) {
                        FilledTonalButton(
                            onClick = { NotificationHelper(context).showPromotedLiveUpdate(order) },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Icon(Icons.Default.NotificationAdd, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("再次推送实时通知", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusButton(selected: Boolean, label: String, count: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(15.dp), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(36.dp).widthIn(min = 80.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) { Text(text = "$label $count", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun DeleteConfirmDialog(title: String = "确认删除？", description: String = "删除后将无法找回此条记录，确定要继续吗？", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = title, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            }
        },
        text = { Text(text = description, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(15.dp)) {
                    Text("取消", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onConfirm, modifier = Modifier.weight(1f), border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error), shape = RoundedCornerShape(15.dp)) {
                    Text("删除", fontWeight = FontWeight.Bold)
                }
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

fun Modifier.verticalScrollbar(state: LazyListState, width: Dp = 6.dp, color: Color = Color.Gray): Modifier = composed {
    drawWithContent {
        drawContent()
        val layoutInfo = state.layoutInfo
        if ((state.canScrollForward || state.canScrollBackward) && layoutInfo.visibleItemsInfo.isNotEmpty()) {
            val viewportHeight = size.height; val totalItemsCount = layoutInfo.totalItemsCount
            val averageItemHeight = layoutInfo.visibleItemsInfo.sumOf { it.size } / layoutInfo.visibleItemsInfo.size.toFloat()
            val totalContentHeight = (averageItemHeight * totalItemsCount) + (12.dp.toPx() * (totalItemsCount - 1)) + layoutInfo.beforeContentPadding + layoutInfo.afterContentPadding
            val scrollOffset = state.firstVisibleItemIndex * (averageItemHeight + 12.dp.toPx()) + state.firstVisibleItemScrollOffset
            val scrollbarHeight = ((viewportHeight / totalContentHeight) * viewportHeight).coerceIn(32.dp.toPx(), viewportHeight)
            val scrollProgress = (scrollOffset / (totalContentHeight - viewportHeight).coerceAtLeast(1f)).coerceIn(0f, 1f)
            drawRoundRect(color = color.copy(alpha = 0.5f), topLeft = Offset(size.width - width.toPx() - 2.dp.toPx(), (viewportHeight - scrollbarHeight) * scrollProgress), size = Size(width.toPx(), scrollbarHeight), cornerRadius = CornerRadius(width.toPx() / 2))
        }
    }
}