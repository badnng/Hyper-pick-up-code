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
    /**
     * 编码完整诊断快照（OCR 明细 + 词汇引擎摘要）。
     * OCR 明细受「识别详情」开关控制；词汇引擎摘要任何引擎识别都保存，
     * 保证订单/分组能看到引擎到底提取了哪些码（解决「组没有诊断信息」）。
     */
    fun encodeWithEngine(
        ocr: PaddleOcrHelper.DiagnosticResult?,
        engine: EngineRecognitionSummary?,
    ): String? {
        if (ocr == null && engine == null) return null
        val root = JSONObject().apply { put("version", FORMAT_VERSION) }
        if (ocr != null) root.put("ocr", encodeOcrObject(ocr))
        if (engine != null) root.put("engine", engine.toJson())
        return root.toString()
    }

    fun encode(result: PaddleOcrHelper.DiagnosticResult): String = JSONObject().apply {
        put("version", FORMAT_VERSION)
        put("ocr", encodeOcrObject(result))
    }.toString()

    private fun encodeOcrObject(result: PaddleOcrHelper.DiagnosticResult): JSONObject = JSONObject().apply {
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
    }

    fun decode(value: String?): PaddleOcrHelper.DiagnosticResult? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(value)
            val ocr = if (root.has("ocr")) root.getJSONObject("ocr") else root
            val blocksJson = ocr.getJSONArray("textBlocks")
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
                imageWidth = ocr.getInt("imageWidth").coerceAtLeast(1),
                imageHeight = ocr.getInt("imageHeight").coerceAtLeast(1),
                detectionTimeMs = ocr.optLong("detectionTimeMs"),
                recognitionTimeMs = ocr.optLong("recognitionTimeMs"),
                totalTimeMs = ocr.optLong("totalTimeMs"),
            )
        }.getOrNull()
    }

    /** 读取快照中的词汇引擎摘要（无则返回 null）。 */
    fun decodeEngine(value: String?): EngineRecognitionSummary? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(value)
            if (!root.has("engine")) null else EngineRecognitionSummary.fromJson(root.getJSONObject("engine"))
        }.getOrNull()
    }

    private const val FORMAT_VERSION = 1
}

/** 词汇引擎识别摘要（随订单诊断保存，用于展示引擎定位/裁剪/提取结果）。 */
data class EngineRecognitionSummary(
    val pageType: String,
    val brand: String?,
    val keywordHitCount: Int,
    val cropCount: Int,
    val codeCount: Int,
    val codes: List<String>,
    val totalMs: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("pageType", pageType)
        put("brand", brand ?: JSONObject.NULL)
        put("keywordHitCount", keywordHitCount)
        put("cropCount", cropCount)
        put("codeCount", codeCount)
        put("codes", JSONArray(codes))
        put("totalMs", totalMs)
    }

    companion object {
        fun fromJson(json: JSONObject): EngineRecognitionSummary = EngineRecognitionSummary(
            pageType = json.optString("pageType", ""),
            brand = if (json.isNull("brand")) null else json.optString("brand"),
            keywordHitCount = json.optInt("keywordHitCount", 0),
            cropCount = json.optInt("cropCount", 0),
            codeCount = json.optInt("codeCount", 0),
            codes = buildList {
                val arr = json.optJSONArray("codes")
                if (arr != null) {
                    for (i in 0 until arr.length()) add(arr.optString(i))
                }
            },
            totalMs = json.optLong("totalMs", 0L),
        )
    }
}
