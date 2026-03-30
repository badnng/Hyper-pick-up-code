package com.Badnng.moe

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Badnng.moe.MainActivity
import com.Badnng.moe.OrderEntity
import com.Badnng.moe.OrderViewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.lens
import kotlinx.coroutines.launch
import com.Badnng.moe.screens.CaptureScreen
import com.Badnng.moe.screens.SettingsScreen
import com.Badnng.moe.screens.QrCodeDialog
import com.Badnng.moe.screens.LogScreen
import com.Badnng.moe.screens.OrderDetailScreen
import com.Badnng.moe.screens.GroupDetailScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    intentToProcess: Intent? = null
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val viewModel: OrderViewModel = viewModel()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val orders by viewModel.orders.collectAsState()
    val orderGroups by viewModel.orderGroups.collectAsState()
    var selectedOrderForQr by remember { mutableStateOf<OrderEntity?>(null) }
    var detailOrder by remember { mutableStateOf<OrderEntity?>(null) }
    var detailGroup by remember { mutableStateOf<OrderGroup?>(null) }
    var previousDetailOrder by remember { mutableStateOf<OrderEntity?>(null) }
    var previousDetailGroup by remember { mutableStateOf<OrderGroup?>(null) }
    var isFromNotification by remember { mutableStateOf(false) }
    var isManaging by remember { mutableStateOf(false) }
    var groupOrders by remember { mutableStateOf<List<OrderEntity>>(emptyList()) }

    var backProgress by remember { mutableFloatStateOf(0f) }
    var backSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var isPredictiveBackInProgress by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var navAlignment by remember { mutableStateOf(prefs.getString("nav_alignment", "center") ?: "center") }
    // 关键修复：hapticEnabled 现在是实时响应的状态
    var hapticEnabled by remember { mutableStateOf(prefs.getBoolean("haptic_enabled", true)) }
    var amoledPureBlack by remember { mutableStateOf(prefs.getBoolean("amoled_pure_black", false)) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                "nav_alignment" -> navAlignment = p.getString(key, "center") ?: "center"
                "haptic_enabled" -> hapticEnabled = p.getBoolean(key, true)
                "amoled_pure_black" -> amoledPureBlack = p.getBoolean(key, false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val performHaptic = {
        if (hapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(detailOrder) {
        detailOrder?.let {
            previousDetailOrder = it
        }
    }

    LaunchedEffect(detailGroup) {
        detailGroup?.let {
            previousDetailGroup = it
        }
    }

    LaunchedEffect(detailGroup) {
        detailGroup?.let { group ->
            viewModel.getOrdersByGroupId(group.id).collect { orders ->
                groupOrders = orders
            }
        }
    }


    PredictiveBackHandler(enabled = detailOrder != null || detailGroup != null) { backEvent: Flow<androidx.activity.BackEventCompat> ->
        isPredictiveBackInProgress = true
        try {
            backEvent.collect { event ->
                backProgress = event.progress
                backSwipeEdge = event.swipeEdge
            }
            if (detailGroup != null) {
                detailGroup = null
            } else {
                detailOrder = null
            }
        } catch (e: CancellationException) {
            if (previousDetailGroup != null && detailGroup == null) {
                detailGroup = previousDetailGroup
            } else {
                detailOrder = previousDetailOrder
            }
        } finally {
            isPredictiveBackInProgress = false
            backProgress = 0f
        }
    }

    val activity = context as? MainActivity

    // 主页面按返回键时，从最近任务移除卡片
    BackHandler(enabled = detailOrder == null && detailGroup == null) {
        activity?.finishAndRemoveTask()
    }

    val currentScale = if (isPredictiveBackInProgress) 1f - (backProgress * 0.08f) else 1f
    val currentTranslationX = if (isPredictiveBackInProgress) {
        val multiplier = if (backSwipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
        backProgress * 100f * multiplier
    } else 0f
    val currentCornerRadius = if (isPredictiveBackInProgress) (backProgress * 32).dp else 0.dp

    LaunchedEffect(intentToProcess, orders) {
        if (intentToProcess?.getBooleanExtra("show_qr_detail", false) == true) {
            val orderId = intentToProcess.getStringExtra("order_id")
            val order = orders.find { it.id == orderId }
            if (order != null) {
                selectedOrderForQr = order
                isFromNotification = intentToProcess.getBooleanExtra("from_notification", false)
                activity?.intentToProcess = null
            }
        }
        if (intentToProcess?.hasExtra("highlight_order_id") == true) {
            detailOrder = null // 自动关闭详情页回到列表
            coroutineScope.launch { pagerState.animateScrollToPage(0) }
        }
    }

    LaunchedEffect(intentToProcess, orderGroups) {
        if (intentToProcess?.getBooleanExtra("show_group_detail", false) == true) {
            detailOrder = null
            detailGroup = null
            coroutineScope.launch { pagerState.animateScrollToPage(0) }
        }
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer
    val isDarkPalette = backgroundColor.luminance() < 0.5f
    val usePureBlackHomeBackground = amoledPureBlack && isDarkPalette
    val backgroundBrush = remember(
        backgroundColor,
        primaryColor,
        secondaryContainerColor,
        isDarkPalette,
        usePureBlackHomeBackground
    ) {
        if (usePureBlackHomeBackground) {
            Brush.verticalGradient(listOf(Color.Black, Color.Black))
        } else if (isDarkPalette) {
            Brush.verticalGradient(
                listOf(
                    backgroundColor,
                    primaryColor.copy(alpha = 0.22f),
                    backgroundColor
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    backgroundColor,
                    primaryColor.copy(alpha = 0.12f),
                    secondaryContainerColor.copy(alpha = 0.18f)
                )
            )
        }
    }
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }

    var isSettingsSubPageOpen by remember { mutableStateOf(false) }
    val isUiHidden = isSettingsSubPageOpen || isManaging

    Box(modifier = modifier.fillMaxSize().background(backgroundBrush)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                val fontScale = LocalDensity.current.fontScale
                val largeFont = fontScale >= 1.2f
                val alignment = when (navAlignment) {
                    "left" -> Alignment.BottomStart
                    "right" -> Alignment.BottomEnd
                    else -> Alignment.BottomCenter
                }
                val barWidth = if (navAlignment == "center") 275.dp else 250.dp
                val barHeight = if (largeFont) 72.dp else 64.dp

                // 核心修复：使用 AnimatedVisibility 彻底移除隐藏时的底栏，防止点击穿透
                AnimatedVisibility(
                    visible = !isUiHidden,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom))
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        contentAlignment = alignment
                    ) {
                        Box(
                            modifier = Modifier
                                .width(barWidth)
                                .height(barHeight)
                                .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f)), RoundedCornerShape(20.dp))
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { RoundedCornerShape(20.dp) },
                                    effects = { vibrancy(); blur(16.dp.toPx()); lens(20.dp.toPx(), 40.dp.toPx()) },
                                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.1f)) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            NavigationBar(containerColor = Color.Transparent, modifier = Modifier.fillMaxSize(), windowInsets = WindowInsets(0, 0, 0, 0)) {
                                NavigationBarItem(
                                    icon = { val s by animateDpAsState(if (pagerState.currentPage == 0) 28.dp else 24.dp); Icon(Icons.Default.Home, null, Modifier.size(s)) },
                                    label = { Text("主页", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    selected = pagerState.currentPage == 0,
                                    alwaysShowLabel = !largeFont,
                                    onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer, selectedIconColor = MaterialTheme.colorScheme.primary, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                )
                                NavigationBarItem(
                                    icon = { val s by animateDpAsState(if (pagerState.currentPage == 1) 28.dp else 24.dp); Icon(Icons.Default.List, null, Modifier.size(s)) },
                                    label = { Text("日志", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    selected = pagerState.currentPage == 1,
                                    alwaysShowLabel = !largeFont,
                                    onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                                    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer, selectedIconColor = MaterialTheme.colorScheme.primary, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                )
                                NavigationBarItem(
                                    icon = { val s by animateDpAsState(if (pagerState.currentPage == 2) 28.dp else 24.dp); Icon(Icons.Default.Settings, null, Modifier.size(s)) },
                                    label = { Text("设置", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    selected = pagerState.currentPage == 2,
                                    alwaysShowLabel = !largeFont,
                                    onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                                    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer, selectedIconColor = MaterialTheme.colorScheme.primary, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = pagerState.currentPage == 0 && !isUiHidden,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    FloatingActionButton(
                        onClick = { performHaptic(); showBottomSheet = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(15.dp),
                        modifier = Modifier.padding(bottom = 8.dp, end = 24.dp)
                    ) {
                        Icon(Icons.Default.Add, "添加", Modifier.size(32.dp))
                    }
                }
            }
        ) { _ ->
            Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1, userScrollEnabled = !isManaging && !isSettingsSubPageOpen && detailOrder == null && detailGroup == null) { page ->
                    when (page) {
                        0 -> CaptureScreen(modifier = Modifier.fillMaxSize(), bottomPadding = 100.dp, backdrop = backdrop, onEditModeChange = { isManaging = it }, onNavigateToDetail = { detailItem ->
                            when (detailItem) {
                                is OrderEntity -> detailOrder = detailItem
                                is OrderGroup -> detailGroup = detailItem
                            }
                        })
                        1 -> LogScreen(modifier = Modifier.fillMaxSize())
                        2 -> SettingsScreen(modifier = Modifier.fillMaxSize(), onSubPageStatusChange = { isSettingsSubPageOpen = it })
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = detailOrder != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut()
        ) {
            val displayOrder = detailOrder ?: previousDetailOrder
            if (displayOrder != null) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = currentScale; scaleY = currentScale; translationX = currentTranslationX; shape = RoundedCornerShape(currentCornerRadius); clip = true }.border(width = if (isPredictiveBackInProgress) 1.dp else 0.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = backProgress), shape = RoundedCornerShape(currentCornerRadius)).background(MaterialTheme.colorScheme.background)) {
                    OrderDetailScreen(order = displayOrder, onBack = { detailOrder = null })
                }
            }
        }

        AnimatedVisibility(
            visible = detailGroup != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut()
        ) {
            val displayGroup = detailGroup ?: previousDetailGroup
            if (displayGroup != null) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = currentScale; scaleY = currentScale; translationX = currentTranslationX; shape = RoundedCornerShape(currentCornerRadius); clip = true }.border(width = if (isPredictiveBackInProgress) 1.dp else 0.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = backProgress), shape = RoundedCornerShape(currentCornerRadius)).background(MaterialTheme.colorScheme.background)) {
                    GroupDetailScreen(
                        group = displayGroup,
                        orders = groupOrders,
                        onBack = { detailGroup = null },
                        onMarkAllCompleted = {
                            val completedAt = System.currentTimeMillis()
                            groupOrders = groupOrders.map {
                                if (it.isCompleted) it else it.copy(isCompleted = true, completedAt = completedAt)
                            }
                            detailGroup = displayGroup.copy(
                                isCompleted = true,
                                completedAt = completedAt,
                                orderCount = groupOrders.size
                            )
                            previousDetailGroup = detailGroup
                            viewModel.markGroupAsCompleted(displayGroup.id)
                        },
                        onMarkOrderCompleted = { order ->
                            groupOrders = groupOrders.map {
                                if (it.id == order.id) it.copy(isCompleted = true, completedAt = System.currentTimeMillis()) else it
                            }
                            viewModel.markAsCompleted(order.id)
                        }
                    )
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(onDismissRequest = { showBottomSheet = false }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface, dragHandle = { BottomSheetDefaults.DragHandle() }, shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp)) {
                BottomSheetContent(viewModel) { showBottomSheet = false }
            }
        }

        if (selectedOrderForQr != null) {
            QrCodeDialog(order = selectedOrderForQr!!, onDismiss = {
                selectedOrderForQr = null
                if (isFromNotification) { 
                    activity?.moveTaskToBack(true)
                    isFromNotification = false 
                }
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetContent(viewModel: OrderViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var detectedQrData by remember { mutableStateOf<String?>(null) }
    var orderType by remember { mutableStateOf("餐食") }
    var brandName by remember { mutableStateOf<String?>(null) }
    var pickupLocation by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("餐食", "饮品", "快递")
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    var hapticEnabled by remember { mutableStateOf(prefs.getBoolean("haptic_enabled", true)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "haptic_enabled") hapticEnabled = p.getBoolean(key, true)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val performHaptic = {
        if (hapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var screenshotPath by remember { mutableStateOf<String?>(null) }

    fun cropStatusBar(src: Bitmap): Bitmap {
        val statusBarHeight = 150
        val sideMargin = (src.width * 0.02).toInt()
        val targetWidth = (src.width * 0.96).toInt()
        val targetHeight = (src.height * 0.81).toInt()
        return if (src.height > statusBarHeight + targetHeight && src.width > sideMargin + targetWidth) {
            Bitmap.createBitmap(src, sideMargin, statusBarHeight, targetWidth, targetHeight)
        } else {
            src
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }

                val bitmap = cropStatusBar(originalBitmap)

                val helper = TextRecognitionHelper(context)
                helper.initOcr() // 初始化 PaddleOCR
                val result = helper.recognizeAll(bitmap)

                text = result.code ?: ""
                detectedQrData = result.qr
                orderType = result.type
                brandName = result.brand
                pickupLocation = result.pickupLocation

                // 保存裁剪后的图片
                if (result.code != null) {
                    val screenshotFile = java.io.File(context.filesDir, "screenshots/manual_${System.currentTimeMillis()}.png")
                    screenshotFile.parentFile?.mkdirs()
                    val outputStream = java.io.FileOutputStream(screenshotFile)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.close()
                    screenshotPath = screenshotFile.absolutePath
                }

                helper.close()
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(horizontal = 24.dp).padding(bottom = 32.dp).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom)), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("添加记录", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("输入取餐码/取件码") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { performHaptic(); photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(if (detectedQrData != null) Icons.Default.QrCodeScanner else Icons.Default.PhotoLibrary, contentDescription = "选择图片识别", tint = if (detectedQrData != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = orderType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("类别") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { selectionOption ->
                        DropdownMenuItem(text = { Text(selectionOption) }, onClick = { performHaptic(); orderType = selectionOption; expanded = false })
                    }
                }
            }

            if (detectedQrData != null) {
                Text(text = "已识别到二维码信息", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { performHaptic(); onDismiss() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(15.dp)) {
                    Text("取消")
                }
                Button(onClick = {
                    performHaptic()
                    viewModel.addOrder(OrderEntity(
                        takeoutCode = text,
                        qrCodeData = detectedQrData,
                        screenshotPath = screenshotPath ?: "",
                        recognizedText = "手动输入",
                        orderType = orderType,
                        brandName = brandName,
                        pickupLocation = pickupLocation
                    ))
                    onDismiss()
                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(15.dp)) {
                    Text("添加")
                }
            }
        }
    }
}
