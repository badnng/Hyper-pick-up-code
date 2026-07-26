package com.Badnng.moe.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionTextStructureTest {
    @Test
    fun codeNormalizationKeepsTrackingNumberAndNextPickupCodeOnSeparateLines() {
        val source = """
            盐城工学院南校区一食堂北菜鸟驿...绿色公益
            申通21-5-3607本人|斑*130****5914
            申通 773413322500502
            40-2-7253
            本人|王*130****5914
            中国邮政 9815041917035
        """.trimIndent()

        val normalized = RecognitionTextStructure.normalizeForCodeMatching(
            text = source,
            datetimePattern = "(?!)",
            spaceCollapsePattern = "(?!)",
            charRemovals = listOf("|"),
            corrections = emptyList(),
        )
        val codes = Regex("([A-Z0-9]+-[A-Z0-9]+-[A-Z0-9]+)")
            .findAll(normalized)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(listOf("21-5-3607", "40-2-7253"), codes)
        assertTrue(normalized.contains("773413322500502\n40-2-7253"))
    }

    @Test
    fun extractsLocationBeforeTrailingAddressLabel() {
        val candidates = RecognitionTextStructure.trailingLocationCandidates(
            lines = listOf(
                "星巴克臻选（三亚市三亚湾壹...>",
                "天涯区天涯镇三亚湾路8号联系地址",
                "我的订单",
            ),
            labels = listOf("联系地址", "门店地址"),
        )

        assertEquals(listOf("天涯区天涯镇三亚湾路8号"), candidates)
    }

    @Test
    fun usesPreviousLineWhenTrailingAddressLabelIsSeparateBlock() {
        val candidates = RecognitionTextStructure.trailingLocationCandidates(
            lines = listOf("天涯区天涯镇三亚湾路8号", "联系地址"),
            labels = listOf("联系地址"),
        )

        assertEquals(listOf("天涯区天涯镇三亚湾路8号"), candidates)
    }
}
