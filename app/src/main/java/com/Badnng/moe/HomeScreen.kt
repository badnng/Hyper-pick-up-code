package com.Badnng.moe.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val viewModel: OrderViewModel = viewModel()
    val context = LocalContext.current
    val orders by viewModel.orders.collectAsState()
    var selectedOrderForQr by remember { mutableStateOf<OrderEntity?>(null) }
    var isFromNotification by remember { mutableStateOf(false) }

    val activity = context as? MainActivity
    LaunchedEffect(activity?.intentToProcess, orders) {
        val intent = activity?.intentToProcess
        if (intent?.getBooleanExtra("show_qr_detail", false) == true) {
            val orderId = intent.getStringExtra("order_id")
            val order = orders.find { it.id == orderId }
            if (order != null) {
                selectedOrderForQr = order
                isFromNotification = intent.getBooleanExtra("from_notification", false)
                activity.intentToProcess = null 
            }
        }
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }

    var isSettingsSubPageOpen by remember { mutableStateOf(false) }
    val uiAlpha by animateFloatAsState(
        targetValue = if (isSettingsSubPageOpen) 0f else 1f,
        label = "uiAlpha"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(uiAlpha)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(min = 200.dp, max = 240.dp)
                        .height(64.dp)
                        .border(
                            BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f)),
                            RoundedCornerShape(20.dp)
                        )
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(20.dp) },
                            effects = {
                                vibrancy()
                                blur(16.dp.toPx())
                                lens(20.dp.toPx(), 40.dp.toPx())
                            },
                            onDrawSurface = {
                                drawRect(Color.White.copy(alpha = 0.1f))
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        modifier = Modifier.fillMaxSize(),
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    ) {
                        val isHomeSelected = pagerState.currentPage == 0
                        val homeIconSize by animateDpAsState(if (isHomeSelected) 28.dp else 24.dp, label = "hSize")

                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Home, null, Modifier.size(homeIconSize)) },
                            label = { Text("识别", fontSize = 12.sp) },
                            selected = isHomeSelected,
                            onClick = { if (!isSettingsSubPageOpen) coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )

                        val isSettingsSelected = pagerState.currentPage == 1
                        val settingsIconSize by animateDpAsState(if (isSettingsSelected) 28.dp else 24.dp, label = "sSize")

                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Settings, null, Modifier.size(settingsIconSize)) },
                            label = { Text("设置", fontSize = 12.sp) },
                            selected = isSettingsSelected,
                            onClick = { if (!isSettingsSubPageOpen) coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (!isSettingsSubPageOpen) showBottomSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(15.dp),
                modifier = Modifier.alpha(uiAlpha).navigationBarsPadding().padding(bottom = 8.dp, end = 24.dp)
            ) {
                Icon(Icons.Default.Add, "添加", Modifier.size(32.dp))
            }
        }
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        ) {
            Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = !isSettingsSubPageOpen
                ) { page ->
                    when (page) {
                        0 -> CaptureScreen(
                            modifier = Modifier.fillMaxSize(),
                            bottomPadding = 100.dp,
                            backdrop = backdrop
                        )
                        1 -> SettingsScreen(
                            modifier = Modifier.fillMaxSize(),
                            onSubPageStatusChange = { isSettingsSubPageOpen = it }
                        )
                    }
                }
            }

            if (showBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showBottomSheet = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = { BottomSheetDefaults.DragHandle() },
                    shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp)
                ) {
                    BottomSheetContent(viewModel) { showBottomSheet = false }
                }
            }

            if (selectedOrderForQr != null) {
                QrCodeDialog(order = selectedOrderForQr!!, onDismiss = { 
                    selectedOrderForQr = null
                    if (isFromNotification) {
                        activity?.handleBackToPrevious()
                        isFromNotification = false
                    }
                })
            }
        }
    }
}

@Composable
fun BottomSheetContent(viewModel: OrderViewModel, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.35f).padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("添加记录", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ActionButton(Icons.Default.PhotoCamera, "截图识别", onClick = { onDismiss() })
            var showManualInput by remember { mutableStateOf(false) }
            ActionButton(Icons.Default.TextFields, "手动输入", onClick = { showManualInput = true })
            if (showManualInput) {
                ManualInputDialog(onDismiss = { showManualInput = false }, onConfirm = { code, qrData, type ->
                    viewModel.addOrder(OrderEntity(takeoutCode = code, qrCodeData = qrData, screenshotPath = "", recognizedText = "手动输入", orderType = type))
                    showManualInput = false
                    onDismiss()
                })
            }
        }
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
        Surface(onClick = onClick, shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualInputDialog(onDismiss: () -> Unit, onConfirm: (String, String?, String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var detectedQrData by remember { mutableStateOf<String?>(null) }
    var orderType by remember { mutableStateOf("餐食") }
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("餐食", "饮品")
    val context = LocalContext.current
    
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
            } else {
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            
            val image = InputImage.fromBitmap(bitmap, 0)
            val scanner = BarcodeScanning.getClient()
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        detectedQrData = barcode.rawValue
                        break
                    }
                }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动输入取餐码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("输入取餐码") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }) {
                            Icon(
                                if (detectedQrData != null) Icons.Default.QrCodeScanner else Icons.Default.PhotoLibrary,
                                contentDescription = "识别二维码",
                                tint = if (detectedQrData != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = orderType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("餐品类别") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        options.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    orderType = selectionOption
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                if (detectedQrData != null) {
                    Text(
                        text = "已识别到二维码信息",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(text, detectedQrData, orderType) }) { Text("添加") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
