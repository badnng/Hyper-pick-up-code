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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Badnng.moe.MainActivity
import com.Badnng.moe.OrderEntity
import com.Badnng.moe.OrderViewModel
import com.Badnng.moe.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.text.SimpleDateFormat
import java.util.*
import android.view.ViewGroup
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay

@Composable
fun CaptureScreen(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    backdrop: com.kyant.backdrop.Backdrop,
    onEditModeChange: (Boolean) -> Unit = {}
) {
    val viewModel: OrderViewModel = viewModel()
    val incompleteOrders by viewModel.incompleteOrders.collectAsState()
    val completedOrders by viewModel.completedOrders.collectAsState()

    CaptureScreenContent(
        incompleteOrders = incompleteOrders,
        completedOrders = completedOrders,
        onMarkCompleted = { viewModel.markAsCompleted(it) },
        onDeleteOrder = { viewModel.deleteOrder(it) },
        onDeleteMultiple = { ids -> 
            ids.forEach { id -> 
                val order = (incompleteOrders + completedOrders).find { it.id == id }
                order?.let { viewModel.deleteOrder(it) }
            }
        },
        onClearAllCompleted = { viewModel.deleteCompletedOrders() },
        onEditModeChange = onEditModeChange,
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
    onDeleteOrder: (OrderEntity) -> Unit,
    onDeleteMultiple: (Set<String>) -> Unit,
    onClearAllCompleted: () -> Unit,
    onEditModeChange: (Boolean) -> Unit,
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

    val incompleteListState = rememberLazyListState()
    val completedListState = rememberLazyListState()
    val context = LocalContext.current
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
                
                val orders = if (isOrderCompleted) completedOrders else incompleteOrders
                val index = orders.indexOfFirst { it.id == orderId }
                if (index != -1) {
                    val state = if (isOrderCompleted) completedListState else incompleteListState
                    state.animateScrollToItem(index)
                }

                delay(3000)
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
                .statusBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "澎湃记", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 16.dp))

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusButton(selected = !showCompletedOnly, label = "待取", count = incompleteOrders.size, onClick = { if (!isEditMode) showCompletedOnly = false })
                    StatusButton(selected = showCompletedOnly, label = "已取", count = completedOrders.size, onClick = { if (!isEditMode) showCompletedOnly = true })
                }
                
                IconButton(onClick = { isEditMode = !isEditMode; if (!isEditMode) selectedIds = emptySet() }) {
                    Icon(if (isEditMode) Icons.Default.Close else Icons.Default.SettingsSuggest, contentDescription = "管理", tint = if (isEditMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
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
                        if (selectedIds.size == currentOrders.size) selectedIds = emptySet()
                        else selectedIds = currentOrders.map { it.id }.toSet()
                    }) {
                        Text(if (selectedIds.size == currentOrders.size) "取消全选" else "全选")
                    }
                    Spacer(Modifier.weight(1f))
                    if (selectedIds.isNotEmpty()) {
                        Button(onClick = { showMultiDeleteConfirm = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), shape = RoundedCornerShape(15.dp)) {
                            Text("删除(${selectedIds.size})")
                        }
                    } else if (showCompletedOnly && completedOrders.isNotEmpty()) {
                        OutlinedButton(onClick = { showClearAllConfirm = true }, shape = RoundedCornerShape(15.dp)) {
                            Text("清空已完成")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Crossfade(targetState = showCompletedOnly, modifier = Modifier.weight(1f).fillMaxWidth(), label = "list") { isCompletedOnly ->
                val currentOrders = if (isCompletedOnly) completedOrders else incompleteOrders
                val currentState = if (isCompletedOnly) completedListState else incompleteListState

                if (currentOrders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "暂无数据", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                } else {
                    LazyColumn(state = currentState, modifier = Modifier.fillMaxSize().verticalScrollbar(currentState, 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding + 32.dp, start = 16.dp, end = 16.dp)) {
                        items(items = currentOrders, key = { it.id }) { order ->
                            Row(modifier = Modifier.animateItem().animateContentSize(animationSpec = tween(400, easing = FastOutSlowInEasing)), verticalAlignment = Alignment.CenterVertically) {
                                AnimatedVisibility(
                                    visible = isEditMode,
                                    enter = expandHorizontally() + fadeIn(),
                                    exit = shrinkHorizontally() + fadeOut()
                                ) {
                                    Checkbox(
                                        checked = selectedIds.contains(order.id),
                                        onCheckedChange = { checked ->
                                            if (checked == true) selectedIds += order.id
                                            else selectedIds -= order.id
                                        },
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                                
                                OrderCard(
                                    order = order,
                                    onMarkCompleted = { onMarkCompleted(order.id) },
                                    onDelete = { orderToDelete = order },
                                    onShowQr = { selectedOrderForQr = order },
                                    isCompleted = isCompletedOnly,
                                    isHighlighted = highlightOrderId == order.id,
                                    isEditMode = isEditMode,
                                    modifier = Modifier.weight(1f)
                                )
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
            DeleteConfirmDialog(title = "确认删除所选", description = "确定要删除选中的 ${selectedIds.size} 条记录吗？", onDismiss = { showMultiDeleteConfirm = false }, onConfirm = { onDeleteMultiple(selectedIds); selectedIds = emptySet(); isEditMode = false; showMultiDeleteConfirm = false })
        }

        if (showClearAllConfirm) {
            DeleteConfirmDialog(title = "确认清空", description = "确定要清空所有已完成的记录吗？该操作不可撤销。", onDismiss = { showClearAllConfirm = false }, onConfirm = { onClearAllCompleted(); isEditMode = false; showClearAllConfirm = false })
        }

        if (selectedOrderForQr != null) {
            QrCodeDialog(order = selectedOrderForQr!!, onDismiss = { selectedOrderForQr = null })
        }
    }
}

@Composable
fun QrCodeDialog(order: OrderEntity, onDismiss: () -> Unit) {
    var animateTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animateTrigger = true }
    val bgAlpha by animateFloatAsState(targetValue = if (animateTrigger) 0.6f else 0f, label = "bg_alpha")
    val scale by animateFloatAsState(targetValue = if (animateTrigger) 1f else 0.8f, animationSpec = tween(350), label = "scale")
    val alpha by animateFloatAsState(targetValue = if (animateTrigger) 1f else 0f, animationSpec = tween(250), label = "alpha", finishedListener = { if (it == 0f) onDismiss() })
    val qrBitmap = remember(order.qrCodeData) { if (!order.qrCodeData.isNullOrEmpty()) { try { generateQrCode(order.qrCodeData, 512) } catch (e: Exception) { null } } else null }
    Dialog(onDismissRequest = { animateTrigger = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgAlpha)).clickable(remember { MutableInteractionSource() }, null) { animateTrigger = false }, contentAlignment = Alignment.Center) {
            Card(modifier = Modifier.padding(horizontal = 40.dp).fillMaxWidth().aspectRatio(1f).scale(scale).alpha(alpha).clickable(remember { MutableInteractionSource() }, null) { }, shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(12.dp)) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "取餐码", fontSize = 12.sp, color = Color.Gray, letterSpacing = 1.2.sp)
                        Text(text = order.takeoutCode, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        Text(text = "请向商家出示此码", fontSize = 12.sp, color = Color.Gray.copy(alpha = 0.6f), fontWeight = FontWeight.Medium)
                    }
                    Box(modifier = Modifier.fillMaxWidth(0.6f).aspectRatio(1f).background(Color.White), contentAlignment = Alignment.Center) {
                        if (qrBitmap != null) { Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "二维码", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                        else { Text(text = "暂无数据", color = Color.Gray, fontSize = 12.sp) }
                    }
                }
            }
        }
    }
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val timeStr = remember(order.createdAt) { val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault()); sdf.format(Date(order.createdAt)) }
    val brandIcon = remember(order.brandName, order.orderType) {
        val resName = when (order.brandName) {
            "麦当劳" -> "ic_mcdonalds"; "肯德基", "KFC" -> "ic_kfc"; "瑞幸" -> "ic_luckin"; "喜茶" -> "ic_heytea"; "星巴克" -> "ic_starbucks"; "霸王茶姬" -> "ic_chagee"; "古茗" -> "ic_goodme"; else -> null
        }
        val resId = if (resName != null) context.resources.getIdentifier(resName, "drawable", context.packageName) else 0
        if (resId != 0) resId else if (order.orderType == "饮品") R.drawable.ic_drink else R.drawable.ic_restaurant
    }
    val highlightColor by androidx.compose.animation.animateColorAsState(targetValue = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else Color.Transparent, animationSpec = tween(1000, easing = FastOutSlowInEasing), label = "highlight")
    
    Card(
        modifier = modifier.fillMaxWidth(),
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
                        Text(text = order.brandName ?: "取餐码", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = order.takeoutCode, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
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
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!isCompleted) { FilledTonalButton(onClick = onMarkCompleted, modifier = Modifier.weight(1f), shape = RoundedCornerShape(15.dp)) { Text("完成") } }
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error), shape = RoundedCornerShape(15.dp)) { Text("删除") }
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
fun DeleteConfirmDialog(title: String = "确认删除", description: String = "删除后将无法找回此条记录，确定要继续吗？", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var animateTrigger by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animateTrigger = true }
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window
        window?.let { w ->
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            WindowCompat.setDecorFitsSystemWindows(w, false)
            w.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
    }
    val bgAlpha by animateFloatAsState(targetValue = if (animateTrigger) 0.55f else 0f, animationSpec = tween(300), label = "bg_alpha")
    val cardScale by animateFloatAsState(targetValue = if (animateTrigger) 1f else 0.85f, animationSpec = tween(350, easing = FastOutSlowInEasing), label = "card_scale")
    val cardAlpha by animateFloatAsState(targetValue = if (animateTrigger) 1f else 0f, animationSpec = tween(250), label = "card_alpha", finishedListener = { if (it == 0f) { if (confirmed) onConfirm() else onDismiss() } })
    Dialog(onDismissRequest = { animateTrigger = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgAlpha)).clickable(remember { MutableInteractionSource() }, null) { animateTrigger = false }, contentAlignment = Alignment.Center) {
            Card(modifier = Modifier.fillMaxWidth(0.82f).wrapContentHeight().scale(cardScale).alpha(cardAlpha).clickable(remember { MutableInteractionSource() }, null) { }, shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = { animateTrigger = false }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(15.dp)) { Text("取消", fontWeight = FontWeight.Bold) }
                        OutlinedButton(onClick = { confirmed = true; animateTrigger = false }, modifier = Modifier.weight(1f), border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error), shape = RoundedCornerShape(15.dp)) { Text("删除", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

fun Modifier.verticalScrollbar(state: LazyListState, width: Dp = 6.dp, color: Color = Color.Gray): Modifier = composed {
    val alpha by animateFloatAsState(targetValue = if (state.isScrollInProgress) 0.8f else 0f, animationSpec = tween(if (state.isScrollInProgress) 150 else 500), label = "scrollbar_alpha")
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
            drawRoundRect(color = color.copy(alpha = alpha), topLeft = Offset(size.width - width.toPx() - 2.dp.toPx(), (viewportHeight - scrollbarHeight) * scrollProgress), size = Size(width.toPx(), scrollbarHeight), cornerRadius = CornerRadius(width.toPx() / 2))
        }
    }
}
