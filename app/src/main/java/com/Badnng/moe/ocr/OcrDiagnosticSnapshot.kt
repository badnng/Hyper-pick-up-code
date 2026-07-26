package com.Badnng.moe.ocr

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object OcrDiagnosticsPreferences {
    const val DETAILS_ENABLED_KEY = "ocr_debug_details_enabled"
    const val MIN_CONFIDENCE = OCR_MIN_CONFIDENCE

    fun shouldCapture(context: Context): Boolean = preferences(context)
        .getBoolean(DETAILS_ENABLED_KEY, false)

    private fun preferences(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
}

object OcrDiagnosticSnapshotCodec {
    fun encode(result: PaddleOcrHelper.DiagnosticResult): String = JSONObject().apply {
        put("version", FORMAT_VERSION)
        put("imageWidth", result.imageWidth)
        put("imageHeight", result.imageHeight)
        put("detectionTimeMs", result.detectionTimeMs)
        put("recognitionTimeMs", result.recognitionTimeMs)
        put("totalTimeMs", result.totalTimeMs)
        put("textBlocks", JSONArray().apply {
            result.textBlocks.forEach { block ->
                put(JSONObject().apply {
                    put("text", block.text)
                    put("confidence", block.confidence.toDouble())
                    put("recognitionTimeMs", block.recognitionTimeMs ?: JSONObject.NULL)
                    put("points", JSONArray().apply {
                        block.points.forEach { point ->
                            put(JSONArray().apply {
                                put(point.x.toDouble())
                                put(point.y.toDouble())
                            })
                        }
                    })
                })
            }
        })
    }.toString()

    fun decode(value: String?): PaddleOcrHelper.DiagnosticResult? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(value)
            val blocksJson = root.getJSONArray("textBlocks")
            val blocks = buildList {
                for (index in 0 until blocksJson.length()) {
                    val block = blocksJson.getJSONObject(index)
                    val pointsJson = block.getJSONArray("points")
                    val points = buildList {
                        for (pointIndex in 0 until pointsJson.length()) {
                            val point = pointsJson.getJSONArray(pointIndex)
                            if (point.length() >= 2) {
                                add(
                                    PaddleOcrHelper.DiagnosticPoint(
                                        x = point.getDouble(0).toFloat(),
                                        y = point.getDouble(1).toFloat(),
                                    ),
                                )
                            }
                        }
                    }
                    add(
                        PaddleOcrHelper.DiagnosticTextBlock(
                            text = block.optString("text"),
                            confidence = block.optDouble("confidence", 0.0).toFloat(),
                            points = points,
                            recognitionTimeMs = if (block.isNull("recognitionTimeMs")) {
                                null
                            } else {
                                block.optLong("recognitionTimeMs")
                            },
                        ),
                    )
                }
            }
            PaddleOcrHelper.DiagnosticResult(
                textBlocks = blocks,
                imageWidth = root.getInt("imageWidth").coerceAtLeast(1),
                imageHeight = root.getInt("imageHeight").coerceAtLeast(1),
                detectionTimeMs = root.optLong("detectionTimeMs"),
                recognitionTimeMs = root.optLong("recognitionTimeMs"),
                totalTimeMs = root.optLong("totalTimeMs"),
            )
        }.getOrNull()
    }

    private const val FORMAT_VERSION = 1
}
