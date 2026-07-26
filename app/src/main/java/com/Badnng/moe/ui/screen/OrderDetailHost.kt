package com.Badnng.moe.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.helper.ScreenshotStorage
import com.Badnng.moe.ocr.OcrDiagnosticSnapshotCodec
import com.Badnng.moe.ocr.OcrDiagnosticsPreferences
import com.Badnng.moe.ocr.PaddleOcrHelper
import com.Badnng.moe.recognition.SecureApiKeyStore
import com.Badnng.moe.ui.LocalAppUi
import com.Badnng.moe.ui.component.OcrDebugUiState
import com.Badnng.moe.ui.component.OrderDetailActions
import com.Badnng.moe.ui.component.OrderDetailUiState
import com.Badnng.moe.ui.component.OrderDiagnosticReportFormatter
import com.Badnng.moe.ui.component.ScreenshotCornerPercents
import com.Badnng.moe.ui.component.orderOriginalText
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun OrderDetailHost(
    order: OrderEntity,
    bottomSpacing: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val haptic = LocalHapticFeedback.current
    val preferences = remember(context) {
        context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    }
    val screenshotExists = remember(context, order.screenshotPath) {
        ScreenshotStorage.exists(context, order.screenshotPath)
    }
    val screenshotAspectRatio = remember(context, order.screenshotPath, screenshotExists) {
        if (!screenshotExists) {
            4f / 3f
        } else {
            ScreenshotStorage.decodeBounds(context, order.screenshotPath)
                ?.let { (width, height) -> width.toFloat() / height }
                ?: 4f / 3f
        }
    }
    val screenshotPreviewMaxHeight = remember(configuration.screenHeightDp) {
        (configuration.screenHeightDp * 0.50f).dp.coerceIn(240.dp, 460.dp)
    }
    val screenshotCornerPercents = rememberDisplayCornerPercents()
    val ocrDebugEnabled = remember(context, order.id) {
        preferences.getBoolean(OcrDiagnosticsPreferences.DETAILS_ENABLED_KEY, false)
    }
    val ocrDebugState = remember(order.id, order.ocrDiagnosticData, screenshotExists, ocrDebugEnabled) {
        if (!ocrDebugEnabled || !screenshotExists) {
            null
        } else if (order.ocrDiagnosticData.isNullOrBlank()) {
            OcrDebugUiState(errorMessage = "该订单未保存 OCR 调试数据")
        } else {
            OcrDiagnosticSnapshotCodec.decode(order.ocrDiagnosticData)?.let { result ->
                OcrDebugUiState(result = result)
            } ?: OcrDebugUiState(errorMessage = "OCR 调试数据无法解析")
        }
    }
    var fullTextExpanded by remember(order.id) { mutableStateOf(false) }
    var technicalExpanded by remember(order.id) { mutableStateOf(false) }
    var hideLowConfidenceOcr by remember(order.id) { mutableStateOf(true) }
    var showFullScreen by remember(order.id) { mutableStateOf(false) }
    fun copy(label: String, value: String) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(label, value))
    }

    val performHaptic = {
        if (preferences.getBoolean("haptic_enabled", true)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    val appVersion = remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "未知" }
    }
    val knownSecrets = remember(context, order.id) {
        SecureApiKeyStore(context).exportApiKeys().values.toList()
    }
    val report = remember(order, appVersion, screenshotExists, knownSecrets) {
        OrderDiagnosticReportFormatter.build(
            order = order,
            appVersion = appVersion,
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            screenshotExists = screenshotExists,
            knownSecrets = knownSecrets,
        )
    }

    LocalAppUi.current.orderDetailContent(
        OrderDetailUiState(
            order = order,
            fullTextExpanded = fullTextExpanded,
            technicalExpanded = technicalExpanded,
            screenshotExists = screenshotExists,
            screenshotAspectRatio = screenshotAspectRatio,
            screenshotPreviewMaxHeight = screenshotPreviewMaxHeight,
            screenshotCornerPercents = screenshotCornerPercents,
            bottomSpacing = bottomSpacing,
            ocrDebugState = ocrDebugState,
            hideLowConfidenceOcr = hideLowConfidenceOcr,
        ),
        OrderDetailActions(
            onCopyCode = { copy("取餐码", order.takeoutCode) },
            onCopyOriginal = {
                orderOriginalText(order)?.let { copy("识别原文", it) }
            },
            onCopyDiagnostics = { copy("澎湃记识别诊断", report) },
            onCopyOcrDebug = {
                ocrDebugState?.result?.let { result ->
                    copy("本地 OCR 原始结果", formatOcrDebugResult(result))
                }
            },
            onToggleOcrLowConfidence = {
                hideLowConfidenceOcr = !hideLowConfidenceOcr
            },
            onToggleFullText = { fullTextExpanded = !fullTextExpanded },
            onToggleTechnical = { technicalExpanded = !technicalExpanded },
            onShowImage = { if (screenshotExists) showFullScreen = true },
            performHaptic = performHaptic,
        ),
        modifier,
    )

    if (showFullScreen && screenshotExists) {
        FullScreenImageDialog(order.screenshotPath) {
            showFullScreen = false
        }
    }
}

private fun formatOcrDebugResult(result: PaddleOcrHelper.DiagnosticResult): String = buildString {
    appendLine("PP-OCRv6 Tiny 原始识别结果")
    appendLine("检测耗时: ${result.detectionTimeMs} ms")
    appendLine("识别耗时: ${result.recognitionTimeMs} ms")
    appendLine("总耗时: ${result.totalTimeMs} ms")
    appendLine()
    result.textBlocks.forEachIndexed { index, block ->
        val confidence = String.format(
            Locale.US,
            "%.2f%%",
            block.confidence.coerceIn(0f, 1f) * 100f,
        )
        appendLine("${index + 1}. ${block.text}\t准确率=$confidence")
    }
}.trimEnd()

@Composable
private fun rememberDisplayCornerPercents(): ScreenshotCornerPercents {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    var corners by remember(view) { mutableStateOf(ScreenshotCornerPercents()) }

    LaunchedEffect(view, configuration.screenWidthDp, configuration.screenHeightDp) {
        withFrameNanos { }
        val insets = view.rootWindowInsets
        val windowWidth = view.width.takeIf { it > 0 }
            ?: view.resources.displayMetrics.widthPixels.coerceAtLeast(1)

        fun percent(position: Int): Int {
            val radius = insets?.getRoundedCorner(position)?.radius ?: 0
            return if (radius > 0) {
                (radius * 100f / windowWidth).roundToInt().coerceIn(2, 24)
            } else {
                4
            }
        }

        corners = ScreenshotCornerPercents(
            topLeft = percent(RoundedCorner.POSITION_TOP_LEFT),
            topRight = percent(RoundedCorner.POSITION_TOP_RIGHT),
            bottomRight = percent(RoundedCorner.POSITION_BOTTOM_RIGHT),
            bottomLeft = percent(RoundedCorner.POSITION_BOTTOM_LEFT),
        )
    }
    return corners
}
