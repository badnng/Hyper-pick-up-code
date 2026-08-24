package com.Badnng.moe.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionTextStructureTest {
    @Test
    fun codeNormalizationKeepsTrackingNumberAndNextPickupCodeOnSeparateLines() {
        val firstCode = "12-3-4567"
        val secondCode = "56-7-8901"
        val source = listOf(
            "示例快递服务点",
            "甲快递${firstCode}收件人",
            "甲快递 123456789012345",
            secondCode,
            "收件人信息",
            "乙快递 987654321098765",
        ).joinToString("\n")

        val normalized = RecognitionTextStructure.normalizeForCodeMatching(
            text = source,
            datetimePattern = "(?!)",
            spaceCollapsePattern = "(?!)",
            charRemovals = emptyList(),
            corrections = emptyList(),
        )
        val codes = Regex("([A-Z0-9]+-[A-Z0-9]+-[A-Z0-9]+)")
            .findAll(normalized)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(listOf(firstCode, secondCode), codes)
        assertTrue(normalized.contains("123456789012345\n$secondCode"))
    }

    @Test
    fun extractsLocationBeforeTrailingAddressLabel() {
        val candidates = RecognitionTextStructure.trailingLocationCandidates(
            lines = listOf("示例门店", "示例路88号联系地址", "订单列表"),
            labels = listOf("联系地址", "门店地址"),
        )

        assertEquals(listOf("示例路88号"), candidates)
    }

    @Test
    fun usesPreviousLineWhenTrailingAddressLabelIsSeparateBlock() {
        val candidates = RecognitionTextStructure.trailingLocationCandidates(
            lines = listOf("示例路88号", "联系地址"),
            labels = listOf("联系地址"),
        )

        assertEquals(listOf("示例路88号"), candidates)
    }
}