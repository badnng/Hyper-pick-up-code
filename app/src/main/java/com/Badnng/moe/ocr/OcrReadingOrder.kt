package com.Badnng.moe.ocr

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal const val OCR_MIN_CONFIDENCE = 0.93f

internal data class OcrTextRegion(
    val text: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerY: Float get() = (top + bottom) / 2f
    val height: Float get() = (bottom - top).coerceAtLeast(1f)
}

internal object OcrReadingOrder {
    private const val MIN_VERTICAL_OVERLAP = 0.45f
    private const val MAX_CENTER_DISTANCE_FACTOR = 0.55f

    fun groupIntoLines(regions: List<OcrTextRegion>): List<List<OcrTextRegion>> {
        if (regions.isEmpty()) return emptyList()

        val lines = mutableListOf<MutableLine>()
        regions
            .filter {
                it.confidence >= OCR_MIN_CONFIDENCE &&
                    it.text.isNotBlank() &&
                    it.right > it.left &&
                    it.bottom > it.top
            }
            .sortedWith(compareBy<OcrTextRegion>({ it.top }, { it.left }))
            .forEach { region ->
                val target = lines
                    .mapNotNull { line ->
                        val overlap = min(line.referenceBottom, region.bottom) -
                            max(line.referenceTop, region.top)
                        val overlapRatio = overlap.coerceAtLeast(0f) / min(line.height, region.height)
                        val centerDistance = abs(line.centerY - region.centerY)
                        val compatible = overlapRatio >= MIN_VERTICAL_OVERLAP ||
                            centerDistance <= max(line.height, region.height) * MAX_CENTER_DISTANCE_FACTOR
                        if (compatible) {
                            LineMatch(line, overlapRatio, centerDistance)
                        } else {
                            null
                        }
                    }
                    .sortedWith(
                        compareByDescending<LineMatch> { it.overlapRatio }
                            .thenBy { it.centerDistance },
                    )
                    .firstOrNull()
                    ?.line

                if (target == null) {
                    lines += MutableLine(region)
                } else {
                    target.add(region)
                }
            }

        return lines
            .sortedWith(compareBy<MutableLine>({ it.top }, { it.left }))
            .map { line -> line.regions.sortedBy(OcrTextRegion::left) }
    }

    fun buildFullText(lines: List<List<OcrTextRegion>>): String = lines
        .mapNotNull { line ->
            line.joinToString(separator = "") { it.text.trim() }.takeIf(String::isNotBlank)
        }
        .joinToString(separator = "\n")

    private data class LineMatch(
        val line: MutableLine,
        val overlapRatio: Float,
        val centerDistance: Float,
    )

    private class MutableLine(first: OcrTextRegion) {
        val regions = mutableListOf(first)
        val left: Float get() = regions.minOf { it.left }
        val top: Float get() = regions.minOf { it.top }
        val centerY: Float get() = regions.map(OcrTextRegion::centerY).average().toFloat()
        val height: Float
            get() {
                val heights = regions.map(OcrTextRegion::height).sorted()
                return heights[heights.size / 2].coerceAtLeast(1f)
            }
        val referenceTop: Float get() = centerY - height / 2f
        val referenceBottom: Float get() = centerY + height / 2f

        fun add(region: OcrTextRegion) {
            regions += region
        }
    }
}
