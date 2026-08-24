package com.Badnng.moe.recognition

import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionCorrectionDetectorTest {
    @Test
    fun findsOnlyCodesNotProducedByRules() {
        val fullText = listOf(
            "取件码12-3-4567",
            "甲快递",
            "取件码23-4-5678",
            "乙快递",
            "取件码34-5-6789",
            "甲快递",
        ).joinToString("\n")

        val missing = RecognitionCorrectionDetector.findUnrecognizedCodes(
            fullText = fullText,
            recognizedCodes = listOf("12-3-4567", "34-5-6789"),
        )

        assertEquals(listOf("23-4-5678"), missing)
    }

    @Test
    fun keepsEveryRemainingCodeForContinuousCorrection() {
        val codes = listOf("12-3-4567", "23-4-5678", "34-5-6789", "45-6-7890")
        val fullText = codes.joinToString("\n") { "取件码$it\n示例快递" }

        val missing = RecognitionCorrectionDetector.findUnrecognizedCodes(
            fullText = fullText,
            recognizedCodes = listOf(codes.first()),
        )

        assertEquals(codes.drop(1), missing)
    }

    @Test
    fun ignoresUiTextThatOnlyMentionsSharingPickupCode() {
        val missing = RecognitionCorrectionDetector.findUnrecognizedCodes(
            fullText = "分享取餐码给好友 制作中，请耐心等待",
            recognizedCodes = emptyList(),
        )

        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun detectsVariableActionCode() {
        val missing = RecognitionCorrectionDetector.findUnrecognizedCodes(
            fullText = "打开示例链接或使用2468取件",
            recognizedCodes = emptyList(),
        )

        assertEquals(listOf("2468"), missing)
    }

    @Test
    fun detectsStandaloneSegmentedCodeInsideExpressDocument() {
        val recognizedCode = "12-3-4567"
        val standaloneCode = "56-7-8901"
        val fullText = listOf(
            "示例快递服务点",
            "甲快递${recognizedCode}收件人",
            "甲快递 123456789012345",
            standaloneCode,
            "收件人信息",
            "乙邮政 987654321098765",
        ).joinToString("\n")

        val missing = RecognitionCorrectionDetector.findUnrecognizedCodes(
            fullText = fullText,
            recognizedCodes = listOf(recognizedCode),
        )

        assertEquals(listOf(standaloneCode), missing)
    }

    @Test
    fun ignoresStandaloneSegmentedValuesWithoutExpressContext() {
        val fullText = listOf("会议编号", "26-8-2026", "房间编号", "1-2-301").joinToString("\n")

        val missing = RecognitionCorrectionDetector.findUnrecognizedCodes(
            fullText = fullText,
            recognizedCodes = emptyList(),
        )

        assertEquals(emptyList<String>(), missing)
    }
}