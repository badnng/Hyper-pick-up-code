package com.Badnng.moe.ui.component

import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.recognition.RecognitionInputType
import com.Badnng.moe.recognition.RecognitionMode
import com.Badnng.moe.recognition.RecognitionTrigger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderDiagnosticReportFormatterTest {
    @Test
    fun oldOrderUsesLegacyValueForExecutionFields() {
        val report = reportFor(baseOrder())

        assertTrue(report.contains("识别路径: $LEGACY_DIAGNOSTIC_VALUE"))
        assertTrue(report.contains("供应商: $LEGACY_DIAGNOSTIC_VALUE"))
        assertTrue(report.contains("总耗时: $LEGACY_DIAGNOSTIC_VALUE"))
    }

    @Test
    fun onlineSuccessKeepsCapturedProviderModelAndNoError() {
        val report = reportFor(
            baseOrder().copy(
                recognitionMode = RecognitionMode.ONLINE.key,
                recognitionInputType = RecognitionInputType.IMAGE.key,
                recognitionTrigger = RecognitionTrigger.SHARED_IMAGE.key,
                recognitionProvider = "zhipu",
                recognitionModel = "glm-4.6v-flashx",
                recognitionUsedOfflineFallback = false,
                recognitionDurationMs = 1280,
            ),
        )

        assertTrue(report.contains("识别路径: 在线识别"))
        assertTrue(report.contains("供应商: 智谱开放平台"))
        assertTrue(report.contains("模型: GLM-4.6V-FlashX"))
        assertTrue(report.contains("错误摘要: 无"))
    }

    @Test
    fun offlineOrderMarksProviderAndModelNotApplicable() {
        val report = reportFor(
            baseOrder().copy(
                recognitionMode = RecognitionMode.OFFLINE.key,
                recognitionInputType = RecognitionInputType.IMAGE.key,
                recognitionTrigger = RecognitionTrigger.SCREEN_CAPTURE.key,
                recognitionUsedOfflineFallback = false,
                recognitionDurationMs = 640,
            ),
        )

        assertTrue(report.contains("识别路径: 离线识别"))
        assertTrue(report.contains("供应商: 不适用"))
        assertTrue(report.contains("模型: 不适用"))
    }

    @Test
    fun onlineFallbackContainsFullCapturedError() {
        val apiKey = "custom-secret-value"
        val response = "{\"detail\":\"Unsupported parameter\",\"api_key\":\"$apiKey\"}"
        val report = reportFor(
            baseOrder().copy(
                recognitionMode = RecognitionMode.ONLINE.key,
                recognitionInputType = RecognitionInputType.TEXT.key,
                recognitionTrigger = RecognitionTrigger.NOTIFICATION.key,
                recognitionProvider = "custom",
                recognitionModel = "vision-model",
                recognitionUsedOfflineFallback = true,
                recognitionError = "HTTP 400",
                recognitionErrorDetail = response,
                recognitionDurationMs = 2527,
            ),
            knownSecrets = listOf(apiKey),
        )

        assertTrue(report.contains("识别路径: 在线识别 → 离线降级"))
        assertTrue(report.contains("Unsupported parameter"))
        assertFalse(report.contains(apiKey))
        assertTrue(report.contains("[REDACTED]"))
    }

    @Test
    fun manualOrderMarksProviderAndModelNotApplicable() {
        val report = reportFor(
            baseOrder().copy(
                recognitionMode = RecognitionMode.MANUAL.key,
                recognitionInputType = RecognitionInputType.MANUAL.key,
                recognitionTrigger = RecognitionTrigger.MANUAL.key,
                recognitionUsedOfflineFallback = false,
                recognitionDurationMs = 0,
            ),
        )

        assertTrue(report.contains("识别路径: 手动录入"))
        assertTrue(report.contains("供应商: 不适用"))
        assertTrue(report.contains("模型: 不适用"))
    }

    private fun baseOrder() = OrderEntity(
        id = "order-test",
        takeoutCode = "A1024",
        screenshotPath = "",
        recognizedText = "自动识别",
        createdAt = 1_700_000_000_000,
        orderType = "餐食",
        brandName = "测试品牌",
    )

    private fun reportFor(
        order: OrderEntity,
        knownSecrets: Collection<String> = emptyList(),
    ): String = OrderDiagnosticReportFormatter.build(
        order = order,
        appVersion = "test",
        deviceName = "test-device",
        androidVersion = "test-android",
        screenshotExists = false,
        knownSecrets = knownSecrets,
    )
}
