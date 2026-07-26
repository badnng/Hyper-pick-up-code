package com.Badnng.moe.ui.component

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.recognition.OnlineRecognitionCatalog
import com.Badnng.moe.recognition.OnlineRecognitionProvider
import com.Badnng.moe.recognition.RecognitionInputType
import com.Badnng.moe.recognition.RecognitionMode
import com.Badnng.moe.recognition.RecognitionTrigger
import com.Badnng.moe.recognition.RecognitionDiagnosticRedactor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val LEGACY_DIAGNOSTIC_VALUE = "旧版数据未记录"
const val UNRECORDED_VALUE = "未记录"

data class OrderDetailUiState(
    val order: OrderEntity,
    val fullTextExpanded: Boolean,
    val technicalExpanded: Boolean,
    val screenshotExists: Boolean,
    val screenshotAspectRatio: Float,
    val screenshotPreviewMaxHeight: Dp,
    val screenshotCornerPercents: ScreenshotCornerPercents,
    val bottomSpacing: Dp,
    val ocrDebugState: OcrDebugUiState? = null,
    val hideLowConfidenceOcr: Boolean = true,
)

data class ScreenshotCornerPercents(
    val topLeft: Int = 4,
    val topRight: Int = 4,
    val bottomRight: Int = 4,
    val bottomLeft: Int = 4,
)

fun ScreenshotCornerPercents.toRoundedCornerShape(): RoundedCornerShape = RoundedCornerShape(
    topStart = CornerSize(topLeft),
    topEnd = CornerSize(topRight),
    bottomEnd = CornerSize(bottomRight),
    bottomStart = CornerSize(bottomLeft),
)

data class OrderDetailActions(
    val onCopyCode: () -> Unit,
    val onCopyOriginal: () -> Unit,
    val onCopyDiagnostics: () -> Unit,
    val onCopyOcrDebug: () -> Unit,
    val onToggleOcrLowConfidence: () -> Unit,
    val onToggleFullText: () -> Unit,
    val onToggleTechnical: () -> Unit,
    val onShowImage: () -> Unit,
    val onShareScreenshot: () -> Unit,
    val performHaptic: () -> Unit,
)

fun recognitionModeLabel(order: OrderEntity): String {
    val mode = RecognitionMode.fromKey(order.recognitionMode) ?: return LEGACY_DIAGNOSTIC_VALUE
    return if (mode == RecognitionMode.ONLINE && order.recognitionUsedOfflineFallback == true) {
        "在线识别 → 离线降级"
    } else {
        mode.displayName
    }
}

fun recognitionInputLabel(order: OrderEntity): String =
    RecognitionInputType.fromKey(order.recognitionInputType)?.displayName ?: LEGACY_DIAGNOSTIC_VALUE

fun recognitionTriggerLabel(order: OrderEntity): String =
    RecognitionTrigger.fromKey(order.recognitionTrigger)?.displayName ?: LEGACY_DIAGNOSTIC_VALUE

fun recognitionProviderLabel(order: OrderEntity): String {
    return when (RecognitionMode.fromKey(order.recognitionMode)) {
        RecognitionMode.OFFLINE, RecognitionMode.MANUAL -> "不适用"
        RecognitionMode.ONLINE -> order.recognitionProvider?.let {
            OnlineRecognitionProvider.entries.firstOrNull { provider -> provider.key == it }?.displayName ?: it
        } ?: LEGACY_DIAGNOSTIC_VALUE
        null -> LEGACY_DIAGNOSTIC_VALUE
    }
}

fun recognitionModelLabel(order: OrderEntity): String {
    return when (RecognitionMode.fromKey(order.recognitionMode)) {
        RecognitionMode.OFFLINE, RecognitionMode.MANUAL -> "不适用"
        RecognitionMode.ONLINE -> {
            val modelId = order.recognitionModel ?: return LEGACY_DIAGNOSTIC_VALUE
            val provider = OnlineRecognitionProvider.entries.firstOrNull {
                it.key == order.recognitionProvider
            }
            provider?.let {
                runCatching {
                    OnlineRecognitionCatalog.modelsFor(it)
                        .firstOrNull { model -> model.id == modelId }
                        ?.displayName
                }.getOrNull()
            } ?: modelId
        }
        null -> LEGACY_DIAGNOSTIC_VALUE
    }
}

fun recognitionDurationLabel(order: OrderEntity): String = order.recognitionDurationMs?.let { duration ->
    if (duration >= 1_000L) {
        String.format(Locale.ROOT, "%d ms（%.2f s）", duration, duration / 1_000f)
    } else {
        "$duration ms"
    }
} ?: LEGACY_DIAGNOSTIC_VALUE

fun recognitionFallbackLabel(order: OrderEntity): String = when (order.recognitionUsedOfflineFallback) {
    true -> "是"
    false -> "否"
    null -> LEGACY_DIAGNOSTIC_VALUE
}

fun recognitionErrorSummaryLabel(order: OrderEntity): String =
    recognitionOptionalValue(order, order.recognitionError)

fun recognitionErrorDetailLabel(order: OrderEntity): String =
    recognitionOptionalValue(order, order.recognitionErrorDetail)

fun orderOriginalText(order: OrderEntity): String? {
    order.fullText?.takeIf { it.isNotBlank() }?.let { return it }
    return order.recognizedText.takeIf {
        it.isNotBlank() && it !in GENERIC_RECOGNIZED_TEXT
    }
}

fun formatOrderTime(value: Long?): String = value?.let {
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it))
} ?: "未完成"

object OrderDiagnosticReportFormatter {
    fun build(
        order: OrderEntity,
        appVersion: String,
        deviceName: String,
        androidVersion: String,
        screenshotExists: Boolean,
        knownSecrets: Collection<String> = emptyList(),
    ): String {
        val report = buildString {
            appendLine("澎湃记识别诊断 v1")
            appendLine("应用版本: $appVersion")
            appendLine("设备: $deviceName")
            appendLine("Android: $androidVersion")
            appendLine()
            appendLine("[订单]")
            appendLine("订单 ID: ${order.id}")
            appendLine("分组 ID: ${order.groupId ?: "无"}")
            appendLine("状态: ${if (order.isCompleted) "已完成" else "未完成"}")
            appendLine("创建时间: ${formatOrderTime(order.createdAt)}")
            appendLine("完成时间: ${formatOrderTime(order.completedAt)}")
            appendLine("类型: ${order.orderType}")
            appendLine("品牌: ${order.brandName.orUnrecordedValue()}")
            appendLine("取餐码/取件码: ${order.takeoutCode}")
            appendLine("取件位置: ${order.pickupLocation.orUnrecordedValue()}")
            appendLine()
            appendLine("[来源]")
            appendLine("来源应用: ${order.sourceApp.orUnrecordedValue()}")
            appendLine("来源包名: ${order.sourcePackage.orUnrecordedValue()}")
            appendLine("触发方式: ${recognitionTriggerLabel(order)}")
            appendLine("输入类型: ${recognitionInputLabel(order)}")
            appendLine()
            appendLine("[识别执行]")
            appendLine("识别路径: ${recognitionModeLabel(order)}")
            appendLine("供应商: ${recognitionProviderLabel(order)}")
            appendLine("模型: ${recognitionModelLabel(order)}")
            appendLine("总耗时: ${recognitionDurationLabel(order)}")
            appendLine("离线降级: ${recognitionFallbackLabel(order)}")
            appendLine("错误摘要: ${recognitionErrorSummaryLabel(order)}")
            appendLine("错误详情:")
            appendLine(recognitionErrorDetailLabel(order))
            appendLine()
            appendLine("[二维码原始数据]")
            appendLine(order.qrCodeData.orUnrecordedValue())
            appendLine()
            appendLine("[原文]")
            appendLine(
                orderOriginalText(order) ?: if (order.recognitionMode == null) {
                    LEGACY_DIAGNOSTIC_VALUE
                } else {
                    UNRECORDED_VALUE
                },
            )
            appendLine()
            appendLine("[截图]")
            appendLine("文件存在: ${if (screenshotExists) "是" else "否"}")
            appendLine("路径: ${order.screenshotPath.ifBlank { "无" }}")
        }.trimEnd()
        return RecognitionDiagnosticRedactor.redact(report, knownSecrets).orEmpty()
    }
}

private fun recognitionOptionalValue(order: OrderEntity, value: String?): String =
    value?.takeIf(String::isNotBlank)
        ?: if (order.recognitionMode == null) LEGACY_DIAGNOSTIC_VALUE else "无"

private fun String?.orUnrecordedValue(): String = this?.takeIf { it.isNotBlank() } ?: UNRECORDED_VALUE

private val GENERIC_RECOGNIZED_TEXT = setOf(
    "自动识别",
    "分享识别",
    "图片识别",
    "手动输入",
)
