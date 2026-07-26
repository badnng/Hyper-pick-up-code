package com.Badnng.moe.recognition

import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.ocr.RecognitionResult

enum class RecognitionMode(val key: String, val displayName: String) {
    OFFLINE("offline", "离线识别"),
    ONLINE("online", "在线识别"),
    MANUAL("manual", "手动录入");

    companion object {
        fun fromKey(key: String?): RecognitionMode? = entries.firstOrNull { it.key == key }
    }
}

enum class RecognitionInputType(val key: String, val displayName: String) {
    IMAGE("image", "图片"),
    TEXT("text", "文本"),
    MANUAL("manual", "手动输入");

    companion object {
        fun fromKey(key: String?): RecognitionInputType? = entries.firstOrNull { it.key == key }
    }
}

enum class RecognitionTrigger(val key: String, val displayName: String) {
    SCREEN_CAPTURE("screen_capture", "屏幕识别"),
    SHARED_IMAGE("shared_image", "分享图片"),
    IMPORTED_IMAGE("imported_image", "导入图片"),
    SMS("sms", "短信识别"),
    TEST_SMS("test_sms", "测试短信"),
    NOTIFICATION("notification", "通知识别"),
    TEST_NOTIFICATION("test_notification", "测试通知"),
    PROCESS_TEXT("process_text", "文字选择"),
    MANUAL("manual", "手动添加");

    companion object {
        fun fromKey(key: String?): RecognitionTrigger? = entries.firstOrNull { it.key == key }
    }
}

data class RecognitionExecutionMetadata(
    val mode: RecognitionMode,
    val inputType: RecognitionInputType,
    val trigger: RecognitionTrigger,
    val providerKey: String? = null,
    val modelId: String? = null,
    val usedOfflineFallback: Boolean = false,
    val errorSummary: String? = null,
    val errorDetail: String? = null,
    val durationMs: Long = 0L,
    val ocrDiagnosticData: String? = null,
)

object RecognizedOrderFactory {
    fun fromRecognition(
        result: RecognitionResult,
        metadata: RecognitionExecutionMetadata,
        screenshotPath: String,
        recognizedText: String,
        sourceApp: String? = null,
        sourcePackage: String? = null,
        groupId: Long? = null,
    ): OrderEntity? {
        val code = result.code ?: return null
        return fromValues(
            takeoutCode = code,
            qrCodeData = result.qr,
            screenshotPath = screenshotPath,
            recognizedText = recognizedText,
            orderType = result.type,
            brandName = result.brand,
            sourceApp = sourceApp,
            sourcePackage = sourcePackage,
            fullText = result.fullText,
            pickupLocation = result.pickupLocation,
            groupId = groupId,
            metadata = metadata,
        )
    }

    fun fromValues(
        takeoutCode: String,
        metadata: RecognitionExecutionMetadata,
        qrCodeData: String? = null,
        screenshotPath: String = "",
        recognizedText: String,
        orderType: String = "餐食",
        brandName: String? = null,
        sourceApp: String? = null,
        sourcePackage: String? = null,
        fullText: String? = null,
        pickupLocation: String? = null,
        groupId: Long? = null,
    ): OrderEntity = OrderEntity(
            takeoutCode = takeoutCode,
            qrCodeData = qrCodeData,
            screenshotPath = screenshotPath,
            recognizedText = recognizedText,
            orderType = orderType,
            brandName = brandName,
            sourceApp = sourceApp,
            sourcePackage = sourcePackage,
            fullText = fullText,
            pickupLocation = pickupLocation,
            groupId = groupId,
            recognitionMode = metadata.mode.key,
            recognitionInputType = metadata.inputType.key,
            recognitionTrigger = metadata.trigger.key,
            recognitionProvider = metadata.providerKey,
            recognitionModel = metadata.modelId,
            recognitionUsedOfflineFallback = metadata.usedOfflineFallback,
            recognitionError = metadata.errorSummary,
            recognitionErrorDetail = metadata.errorDetail,
            recognitionDurationMs = metadata.durationMs,
            ocrDiagnosticData = metadata.ocrDiagnosticData,
        )

    fun manual(
        takeoutCode: String,
        qrCodeData: String? = null,
        screenshotPath: String = "",
        recognizedText: String = "手动输入",
        orderType: String = "餐食",
        brandName: String? = null,
        sourceApp: String? = "手动添加",
        sourcePackage: String? = null,
        fullText: String? = null,
        pickupLocation: String? = null,
    ): OrderEntity = fromValues(
        takeoutCode = takeoutCode,
        qrCodeData = qrCodeData,
        screenshotPath = screenshotPath,
        recognizedText = recognizedText,
        orderType = orderType,
        brandName = brandName,
        sourceApp = sourceApp,
        sourcePackage = sourcePackage,
        fullText = fullText,
        pickupLocation = pickupLocation,
        metadata = RecognitionExecutionMetadata(
            mode = RecognitionMode.MANUAL,
            inputType = RecognitionInputType.MANUAL,
            trigger = RecognitionTrigger.MANUAL,
        ),
    )
}
