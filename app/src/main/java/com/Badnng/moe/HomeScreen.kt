package com.Badnng.moe

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    val orders by viewModel.orders.collectAsState()
    var selectedOrderForQr by remember { mutableStateOf<OrderEntity?>(null) }
    var detailOrder by remember { mutableStateOf<OrderEntity?>(null) }
    var previousDetailOrder by remember { mutableStateOf<OrderEntity?>(null) }
    var isFromNotification by remember { mutableStateOf(false) }
    var isManaging by remember { mutableStateOf(false) }

    var backProgress by remember { mutableFloatStateOf(0f) }
    var isPredictiveBackInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(detailOrder) {
        if (detailOrder != null) previousDetailOrder = detailOrder
    }

    PredictiveBackHandler(enabled = detailOrder != null) { backEvent: Flow<androidx.activity.BackEventCompat> ->
        isPredictiveBackInProgress = true
        try {
            backEvent.collect { event -> backProgress = event.progress }
            detailOrder = null
        } catch (e: CancellationException) {
            detailOrder = previousDetailOrder
        } finally {
            isPredictiveBackInProgress = false
            backProgress = 0f
        }
    }

    val currentScale = if (isPredictiveBackInProgress) 1f - (backProgress * 0.08f) else 1f
    val currentTranslationX = if (isPredictiveBackInProgress) backProgress * 100f else 0f
    val currentCornerRadius = if (isPredictiveBackInProgress) (backProgress * 32).dp else 0.dp

    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var navAlignment by remember { mutableStateOf(prefs.getString("nav_alignment", "center") ?: "center") }
    
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "nav_alignment") navAlignment = p.getString(key, "center") ?: "center"
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val activity = context as? MainActivity
    
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
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }

    var isSettingsSubPageOpen by remember { mutableStateOf(false) }
    val isUiHidden = isSettingsSubPageOpen || isManaging
    
    val uiAlpha by animateFloatAsState(
        targetValue = if (isUiHidden) 0f else 1f,
        label = "uiAlpha"
    )

    Box(modifier = modifier.fillMaxSize().background(backgroundColor)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                val alignment = when (navAlignment) {
                    "left" -> Alignment.BottomStart
                    "right" -> Alignment.BottomEnd
                    else -> Alignment.BottomCenter
                }
                val barWidth = if (navAlignment == "center") 275.dp else 250.dp
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(uiAlpha)
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    contentAlignment = alignment
                ) {
                    Box(
                        modifier = Modifier
                            .width(barWidth)
                            .height(64.dp)
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
                            val isHomeSelected = pagerState.currentPage == 0
                            val homeIconSize by animateDpAsState(if (isHomeSelected) 28.dp else 24.dp, label = "hSize")
                            NavigationBarItem(icon = { Icon(Icons.Default.Home, null, Modifier.size(homeIconSize)) }, label = { Text("识别", fontSize = 12.sp) }, selected = isHomeSelected, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer, selectedIconColor = MaterialTheme.colorScheme.primary, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)))
                            
                            val isLogSelected = pagerState.currentPage == 1
                            val logIconSize by animateDpAsState(if (isLogSelected) 28.dp else 24.dp, label = "lSize")
                            NavigationBarItem(icon = { Icon(Icons.Default.List, null, Modifier.size(logIconSize)) }, label = { Text("日志", fontSize = 12.sp) }, selected = isLogSelected, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer, selectedIconColor = MaterialTheme.colorScheme.primary, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)))
                            
                            val isSettingsSelected = pagerState.currentPage == 2
                            val settingsIconSize by animateDpAsState(if (isSettingsSelected) 28.dp else 24.dp, label = "sSize")
                            NavigationBarItem(icon = { Icon(Icons.Default.Settings, null, Modifier.size(settingsIconSize)) }, label = { Text("设置", fontSize = 12.sp) }, selected = isSettingsSelected, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } }, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer, selectedIconColor = MaterialTheme.colorScheme.primary, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)))
                        }
                    }
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = pagerState.currentPage == 0 && !isManaging && !isSettingsSubPageOpen,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    FloatingActionButton(
                        onClick = { showBottomSheet = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(15.dp),
                        modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp, end = 24.dp)
                    ) {
                        Icon(Icons.Default.Add, "添加", Modifier.size(32.dp))
                    }
                }
            }
        ) { _ ->
            Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1, userScrollEnabled = !isManaging && !isSettingsSubPageOpen && detailOrder == null) { page ->
                    when (page) {
                        0 -> CaptureScreen(modifier = Modifier.fillMaxSize(), bottomPadding = 100.dp, backdrop = backdrop, onEditModeChange = { isManaging = it }, onNavigateToDetail = { detailOrder = it })
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

        if (showBottomSheet) {
            ModalBottomSheet(onDismissRequest = { showBottomSheet = false }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface, dragHandle = { BottomSheetDefaults.DragHandle() }, shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp)) {
                BottomSheetContent(viewModel) { showBottomSheet = false }
            }
        }

        if (selectedOrderForQr != null) {
            QrCodeDialog(order = selectedOrderForQr!!, onDismiss = { 
                selectedOrderForQr = null
                if (isFromNotification) { activity?.handleBackToPrevious(); isFromNotification = false }
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
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("餐食", "饮品")
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val photoPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                
                val helper = TextRecognitionHelper()
                val result = helper.recognizeAll(bitmap)
                
                // 强制全量替换
                text = result.code ?: ""
                detectedQrData = result.qr
                orderType = result.type
                brandName = result.brand
                
                helper.close()
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(horizontal = 24.dp).padding(bottom = 32.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("添加记录", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
        
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = text, 
                onValueChange = { text = it }, 
                label = { Text("输入取餐码") }, 
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { 
                    IconButton(onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { 
                        Icon(if (detectedQrData != null) Icons.Default.QrCodeScanner else Icons.Default.PhotoLibrary, contentDescription = "选择图片识别", tint = if (detectedQrData != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) 
                    } 
                }
            )
            
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = orderType, 
                    onValueChange = {}, 
                    readOnly = true, 
                    label = { Text("餐品类别") }, 
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, 
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { selectionOption ->
                        DropdownMenuItem(text = { Text(selectionOption) }, onClick = { orderType = selectionOption; expanded = false })
                    }
                }
            }
            
            if (detectedQrData != null) { 
                Text(text = "已识别到二维码信息", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp)) 
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(15.dp)) {
                    Text("取消")
                }
                Button(onClick = { 
                    viewModel.addOrder(OrderEntity(takeoutCode = text, qrCodeData = detectedQrData, screenshotPath = "", recognizedText = "手动输入", orderType = orderType, brandName = brandName))
                    onDismiss()
                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(15.dp)) {
                    Text("添加")
                }
            }
        }
    }
}
