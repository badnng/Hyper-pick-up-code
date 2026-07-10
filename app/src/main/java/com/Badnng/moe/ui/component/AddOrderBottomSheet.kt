package com.Badnng.moe.ui.component

import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.ocr.TextRecognitionHelper
import com.Badnng.moe.viewmodel.OrderViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.utils.MiuixIndication
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
fun AddOrderBottomSheet(
    show: Boolean,
    viewModel: OrderViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 模糊进度：Animatable 驱动开/关动画，拖拽时 snapTo 覆盖
    var showSheet by remember { mutableStateOf(show) }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val sheetHeightPx = remember { with(density) { configuration.screenHeightDp.dp.toPx() } }
    val blurProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    var dragProgress by remember { mutableFloatStateOf(-1f) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { blurProgress.value }
            .collect { BlurState.updateProgress(it) }
    }

    // 打开动画
    androidx.compose.runtime.LaunchedEffect(show) {
        if (show) {
            showSheet = true
            BlurState.show()
            dragProgress = -1f
            blurProgress.snapTo(0f)
            blurProgress.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 300f)
            )
        }
    }

    // 拖拽时 snapTo 覆盖
    androidx.compose.runtime.LaunchedEffect(dragProgress) {
        if (dragProgress in 0f..1f) {
            blurProgress.snapTo(dragProgress)
        }
    }

    // 关闭时淡出模糊
    androidx.compose.runtime.LaunchedEffect(showSheet) {
        if (!showSheet) {
            blurProgress.snapTo(blurProgress.value)
            blurProgress.animateTo(
                targetValue = 0f,
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 300f)
            )
        }
    }

    WindowBottomSheet(
        show = showSheet,
        title = "添加记录",
        enableWindowDim = false,
        allowDismiss = true,
        enableNestedScroll = true,
        onDismissRequest = { showSheet = false },
        onDismissFinished = {
            BlurState.hide()
            onDismiss()
        }
    ) {
        // 追踪 Sheet 拖拽位置
        if (showSheet) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.dp)
                    .onGloballyPositioned { coords ->
                        val boxTop = coords.localToWindow(androidx.compose.ui.geometry.Offset(0f, 0f)).y
                        dragProgress = (1f - (boxTop / sheetHeightPx).coerceIn(0f, 1f))
                    }
            )
        }

        val dismiss = LocalDismissState.current

        var text by remember { mutableStateOf("") }
        var detectedQrData by remember { mutableStateOf<String?>(null) }
        var orderType by remember { mutableStateOf("餐食") }
        var brandName by remember { mutableStateOf<String?>(null) }
        var pickupLocation by remember { mutableStateOf<String?>(null) }
        val options = listOf("餐食", "饮品", "快递")
        var screenshotPath by remember { mutableStateOf<String?>(null) }

        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    val originalBitmap = if (Build.VERSION.SDK_INT >= 28) {
                        android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri))
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                    val statusBarHeight = 150
                    val sideMargin = (originalBitmap.width * 0.02).toInt()
                    val targetWidth = (originalBitmap.width * 0.96).toInt()
                    val targetHeight = (originalBitmap.height * 0.81).toInt()
                    val bitmap = if (originalBitmap.height > statusBarHeight + targetHeight && originalBitmap.width > sideMargin + targetWidth) {
                        Bitmap.createBitmap(originalBitmap, sideMargin, statusBarHeight, targetWidth, targetHeight)
                    } else originalBitmap

                    val helper = TextRecognitionHelper(context)
                    helper.initOcr()
                    val (result, _) = helper.recognizeAll(bitmap)
                    text = result.code ?: ""
                    detectedQrData = result.qr
                    orderType = result.type
                    brandName = result.brand
                    pickupLocation = result.pickupLocation
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

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = "输入取餐码/取件码",
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(
                                if (detectedQrData != null) MiuixIcons.Regular.Scan else MiuixIcons.Regular.Photos,
                                contentDescription = "选择图片识别"
                            )
                        }
                    }
                )
                Card(
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    WindowDropdownPreference(
                        title = "类别",
                        items = options,
                        selectedIndex = options.indexOf(orderType).coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            orderType = options[index]
                        }
                    )
                }
                if (detectedQrData != null) {
                    Text("已识别到二维码信息", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        text = "取消",
                        onClick = { dismiss?.invoke() },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "添加",
                        onClick = {
                            viewModel.addOrder(
                                OrderEntity(
                                    takeoutCode = text,
                                    qrCodeData = detectedQrData,
                                    screenshotPath = screenshotPath ?: "",
                                    recognizedText = "手动输入",
                                    orderType = orderType,
                                    brandName = brandName,
                                    pickupLocation = pickupLocation
                                )
                            )
                            dismiss?.invoke()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
