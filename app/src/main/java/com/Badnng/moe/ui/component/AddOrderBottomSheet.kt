package com.Badnng.moe.ui.component

import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.helper.ImageSourceMetadataResolver
import com.Badnng.moe.helper.ScreenshotStorage
import com.Badnng.moe.ocr.RecognitionResult
import com.Badnng.moe.recognition.RecognizedOrderFactory
import com.Badnng.moe.recognition.RecognitionExecutionMetadata
import com.Badnng.moe.recognition.RecognitionRouter
import com.Badnng.moe.recognition.RecognitionCorrectionDetector
import com.Badnng.moe.recognition.RecognitionCorrectionStore
import com.Badnng.moe.recognition.OnlineRecognitionPreferences
import com.Badnng.moe.recognition.RecognitionTrigger
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
import com.Badnng.moe.ui.theme.NonPredictiveBackInterceptor

@Composable
fun AddOrderBottomSheet(
    show: Boolean,
    viewModel: OrderViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDarkTheme = MiuixTheme.colorScheme.background.luminance() < 0.5f

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
        NonPredictiveBackInterceptor()
        SyncWindowBottomSheetStatusBar(isDarkTheme = isDarkTheme)

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
        var recognizedFullText by remember { mutableStateOf<String?>(null) }
        var imageSourceApp by remember { mutableStateOf<String?>(null) }
        var imageSourcePackage by remember { mutableStateOf<String?>(null) }
        var recognitionMetadata by remember { mutableStateOf<RecognitionExecutionMetadata?>(null) }
        var additionalRecognizedResults by remember { mutableStateOf(emptyList<RecognitionResult>()) }
        val options = listOf("餐食", "饮品", "快递")
        var screenshotPath by remember { mutableStateOf<String?>(null) }

        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    val imageSource = ImageSourceMetadataResolver.resolve(context, uri)
                    imageSourceApp = imageSource.appName ?: imageSource.packageName
                    imageSourcePackage = imageSource.packageName
                    recognizedFullText = null
                    recognitionMetadata = null
                    additionalRecognizedResults = emptyList()
                    screenshotPath = null
                    val originalBitmap = if (Build.VERSION.SDK_INT >= 28) {
                        android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri))
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                    val routedResult = RecognitionRouter(context).recognizeImage(
                        originalBitmap,
                        imageSourceApp,
                        imageSourcePackage,
                        RecognitionTrigger.IMPORTED_IMAGE,
                    )
                    val successfulResults = routedResult.orders
                        .filter { it.code != null }
                        .distinctBy { it.code }
                    val result = successfulResults.firstOrNull() ?: routedResult.orders.firstOrNull()
                    additionalRecognizedResults = successfulResults.drop(1)
                    recognitionMetadata = routedResult.metadata
                    text = result?.code ?: ""
                    detectedQrData = result?.qr
                    recognizedFullText = result?.fullText?.takeIf { it.isNotBlank() }
                    result?.let {
                        orderType = it.type
                        brandName = it.brand
                        pickupLocation = it.pickupLocation
                    }
                    if (result?.code != null) {
                        val savedScreenshotPath = ScreenshotStorage.saveBitmap(
                            context,
                            originalBitmap,
                            namePrefix = "导入图片",
                        )
                        screenshotPath = savedScreenshotPath
                        val unrecognizedExplicitCodes = RecognitionCorrectionDetector.findUnrecognizedCodes(
                            fullText = result.fullText,
                            recognizedCodes = successfulResults.mapNotNull { it.code },
                        )
                        val partialDraftSaved = if (unrecognizedExplicitCodes.isNotEmpty()) {
                            RecognitionCorrectionStore.saveImageDraft(
                                context = context,
                                bitmap = originalBitmap,
                                result = result.copy(code = null, brand = null, pickupLocation = null),
                                metadata = routedResult.metadata,
                                recognizedText = "导入图片（部分待纠正）",
                                sourceApp = imageSourceApp,
                                sourcePackage = imageSourcePackage,
                                screenshotPrefix = "导入待纠正",
                                existingScreenshotPath = savedScreenshotPath,
                            )
                        } else {
                            false
                        }
                        when {
                            partialDraftSaved -> Toast.makeText(
                                context,
                                "部分取件码未识别，已加入纠正识别",
                                Toast.LENGTH_SHORT,
                            ).show()
                            successfulResults.size > 1 -> Toast.makeText(
                                context,
                                "识别到 ${successfulResults.size} 个取件码，添加时将一并保存",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } else if (result != null) {
                        val saved = RecognitionCorrectionStore.saveImageDraft(
                            context = context,
                            bitmap = originalBitmap,
                            result = result,
                            metadata = routedResult.metadata,
                            recognizedText = "导入图片（待纠正）",
                            sourceApp = imageSourceApp,
                            sourcePackage = imageSourcePackage,
                            screenshotPrefix = "导入待纠正",
                        )
                        if (saved) {
                            Toast.makeText(context, "识别失败，已加入纠正识别", Toast.LENGTH_SHORT).show()
                        }
                    }
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
                            val hasImportedImage = screenshotPath != null
                            val order = if (hasImportedImage && recognitionMetadata != null) {
                                RecognizedOrderFactory.fromValues(
                                    takeoutCode = text,
                                    metadata = recognitionMetadata!!,
                                    qrCodeData = detectedQrData,
                                    screenshotPath = screenshotPath.orEmpty(),
                                    recognizedText = "图片识别",
                                    orderType = orderType,
                                    brandName = brandName,
                                    sourceApp = imageSourceApp ?: "图片识别",
                                    sourcePackage = imageSourcePackage,
                                    fullText = recognizedFullText,
                                    pickupLocation = pickupLocation,
                                )
                            } else {
                                RecognizedOrderFactory.manual(
                                    takeoutCode = text,
                                    qrCodeData = detectedQrData,
                                    orderType = orderType,
                                    brandName = brandName,
                                    fullText = recognizedFullText,
                                    pickupLocation = pickupLocation,
                                )
                            }
                            viewModel.addOrder(order)
                            val metadata = recognitionMetadata
                            val sharedScreenshotPath = screenshotPath
                            if (metadata != null && sharedScreenshotPath != null) {
                                additionalRecognizedResults
                                    .filterNot { it.code == text }
                                    .mapNotNull { result ->
                                        RecognizedOrderFactory.fromRecognition(
                                            result = result,
                                            metadata = metadata,
                                            screenshotPath = sharedScreenshotPath,
                                            recognizedText = "图片识别",
                                            sourceApp = imageSourceApp ?: "图片识别",
                                            sourcePackage = imageSourcePackage,
                                        )
                                    }
                                    .forEach(viewModel::addOrder)
                            }
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

@Composable
private fun SyncWindowBottomSheetStatusBar(isDarkTheme: Boolean) {
    val view = LocalView.current

    androidx.compose.runtime.DisposableEffect(view, isDarkTheme) {
        val applySystemBarAppearance = Runnable {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@Runnable
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = !isDarkTheme
        }

        applySystemBarAppearance.run()
        view.post(applySystemBarAppearance)

        onDispose {
            view.removeCallbacks(applySystemBarAppearance)
        }
    }
}
