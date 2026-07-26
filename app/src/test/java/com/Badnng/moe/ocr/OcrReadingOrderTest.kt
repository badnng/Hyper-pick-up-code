package com.Badnng.moe.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrReadingOrderTest {
    @Test
    fun groupsByVisualLineAndSortsInNaturalReadingOrder() {
        val regions = listOf(
            region("右", 0.99f, 200f, 104f, 230f, 124f),
            region("第二行", 0.98f, 12f, 142f, 92f, 164f),
            region("左", 0.97f, 10f, 100f, 40f, 120f),
        )

        val lines = OcrReadingOrder.groupIntoLines(regions)

        assertEquals(listOf(listOf("左", "右"), listOf("第二行")), lines.map { line -> line.map { it.text } })
        assertEquals("左右\n第二行", OcrReadingOrder.buildFullText(lines))
    }

    @Test
    fun removesRecognitionResultsBelowNinetyThreePercent() {
        val regions = listOf(
            region("保留", 0.93f, 10f, 10f, 60f, 30f),
            region("删除", 0.9299f, 70f, 10f, 120f, 30f),
        )

        val lines = OcrReadingOrder.groupIntoLines(regions)

        assertEquals(listOf(listOf("保留")), lines.map { line -> line.map { it.text } })
    }

    @Test
    fun keepsNearbyButNonOverlappingRowsSeparate() {
        val regions = listOf(
            region("第一行", 0.99f, 10f, 10f, 100f, 30f),
            region("第二行", 0.99f, 10f, 34f, 100f, 54f),
        )

        val lines = OcrReadingOrder.groupIntoLines(regions)

        assertEquals(2, lines.size)
    }

    private fun region(
        text: String,
        confidence: Float,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) = OcrTextRegion(text, confidence, left, top, right, bottom)
}
