package com.Badnng.moe.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.model.OCRRunResult
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * PP-OCRv6 Tiny 官方 Android SDK 封装。
 */
class PaddleOcrHelper private constructor(private val context: Context) {
    @Volatile
    private var ocr: PaddleOCR? = null
    private val initializationMutex = Mutex()
    private val recognitionMutex = Mutex()

    @Volatile
    private var initialized = false

    @Volatile
    private var lastInferenceTimeMs = -1L

    val isInitialized: Boolean get() = initialized

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
                Build.PRODUCT.contains("sdk") ||
                Build.PRODUCT.contains("vbox86p") ||
                Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu")
    }

    data class TextBlock(
        val text: String,
        val boundingBox: Rect?,
        val confidence: Float,
    )

    data class RecognizeResult(
        val fullText: String,
        val textBlocks: List<TextBlock>,
        val diagnosticResult: DiagnosticResult,
    )

    data class DiagnosticPoint(
        val x: Float,
        val y: Float,
    )

    data class DiagnosticTextBlock(
        val text: String,
        val confidence: Float,
        val points: List<DiagnosticPoint>,
        val recognitionTimeMs: Long?,
    )

    data class DiagnosticResult(
        val textBlocks: List<DiagnosticTextBlock>,
        val imageWidth: Int,
        val imageHeight: Int,
        val detectionTimeMs: Long,
        val recognitionTimeMs: Long,
        val totalTimeMs: Long,
    )

    suspend fun initAsync(): Boolean {
        if (isEmulator()) {
            Log.w(TAG, "检测到模拟器，跳过 PP-OCRv6 Tiny 初始化")
            return false
        }
        if (initialized) return true

        return initializationMutex.withLock {
            if (initialized) return@withLock true
            try {
                check(OpenCVUtils.init(context)) { "OpenCV 初始化失败" }
                val newOcr = PaddleOCR.create(
                    context = context,
                    config = PaddleOCRConfig(
                        detLimitSideLen = 64,
                        detLimitType = "min",
                        detMaxSideLimit = 2560,
                        detThresh = 0.2f,
                        detBoxThresh = 0.4f,
                        detUnclipRatio = 1.4f,
                        detMaxCandidates = 3000,
                        detUseDilation = false,
                        detScoreMode = "fast",
                        detBoxType = "quad",
                        // 保留 SDK 原始结果用于诊断日志，业务阈值在 parseResult() 中执行。
                        recScoreThresh = 0f,
                        recBatchSize = 1,
                    ),
                    engineConfig = EngineConfig(numThreads = 4),
                    detModelAssetPath = DET_MODEL_ASSET,
                    recModelAssetPath = REC_MODEL_ASSET,
                    recConfigAssetPath = REC_CONFIG_ASSET,
                )
                ocr = newOcr
                initialized = true
                Log.i(
                    TAG,
                    "PP-OCRv6 Tiny 初始化成功, coldLoad=${newOcr.coldLoadTimeMs}ms, " +
                        "recThreshold=$OCR_MIN_CONFIDENCE",
                )
                true
            } catch (error: Throwable) {
                ocr = null
                initialized = false
                Log.e(TAG, "PP-OCRv6 Tiny 初始化失败: ${error.message}", error)
                false
            }
        }
    }

    fun init(): Boolean = runBlocking { initAsync() }

    suspend fun recognizeAsync(bitmap: Bitmap): RecognizeResult? {
        if (!initialized && !initAsync()) return null
        return recognitionMutex.withLock {
            val currentOcr = ocr ?: return@withLock null
            try {
                val result = currentOcr.recognize(bitmap)
                lastInferenceTimeMs = result.totalTimeMs
                logRawResults(result)
                parseResult(result, bitmap.width, bitmap.height)
            } catch (error: Throwable) {
                Log.e(TAG, "PP-OCRv6 Tiny 识别失败: ${error.message}", error)
                null
            }
        }
    }

    fun recognize(bitmap: Bitmap): RecognizeResult? = runBlocking {
        recognizeAsync(bitmap)
    }

    suspend fun recognizeDiagnosticAsync(bitmap: Bitmap): DiagnosticResult? {
        if (!initialized && !initAsync()) return null
        return recognitionMutex.withLock {
            val currentOcr = ocr ?: return@withLock null
            try {
                val result = currentOcr.recognize(bitmap)
                lastInferenceTimeMs = result.totalTimeMs
                logRawResults(result)
                DiagnosticResult(
                    textBlocks = result.results.mapIndexed { index, line ->
                        DiagnosticTextBlock(
                            text = line.text,
                            confidence = line.confidence,
                            points = line.box.points.map { point ->
                                DiagnosticPoint(point.x, point.y)
                            },
                            recognitionTimeMs = result.perLineRecMs.getOrNull(index),
                        )
                    },
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    detectionTimeMs = result.detectionTimeMs,
                    recognitionTimeMs = result.recognitionTimeMs,
                    totalTimeMs = result.totalTimeMs,
                )
            } catch (error: Throwable) {
                Log.e(TAG, "PP-OCRv6 Tiny 诊断识别失败: ${error.message}", error)
                null
            }
        }
    }

    suspend fun recognizeBatch(bitmaps: List<Bitmap>): List<RecognizeResult?> =
        bitmaps.map { recognizeAsync(it) }

    private fun logRawResults(result: OCRRunResult) {
        Log.d(RAW_LOG_TAG, "PP-OCRv6 原始识别结果，共 ${result.results.size} 行")
        result.results.forEach { line ->
            val accuracy = String.format(
                Locale.US,
                "%.2f%%",
                line.confidence.coerceIn(0f, 1f) * 100f,
            )
            Log.d(RAW_LOG_TAG, "${line.text}\t准确率=$accuracy")
        }
    }

    private fun parseResult(result: OCRRunResult, imageWidth: Int, imageHeight: Int): RecognizeResult {
        val diagnosticResult = DiagnosticResult(
            textBlocks = result.results.mapIndexed { index, line ->
                DiagnosticTextBlock(
                    text = line.text,
                    confidence = line.confidence,
                    points = line.box.points.map { point -> DiagnosticPoint(point.x, point.y) },
                    recognitionTimeMs = result.perLineRecMs.getOrNull(index),
                )
            },
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            detectionTimeMs = result.detectionTimeMs,
            recognitionTimeMs = result.recognitionTimeMs,
            totalTimeMs = result.totalTimeMs,
        )
        val regions = result.results
            .asSequence()
            .filter { it.text.isNotBlank() }
            .mapNotNull { line ->
                val points = line.box.points
                if (points.size != 4) return@mapNotNull null
                OcrTextRegion(
                    text = line.text,
                    confidence = line.confidence,
                    left = points.minOf { it.x },
                    top = points.minOf { it.y },
                    right = points.maxOf { it.x },
                    bottom = points.maxOf { it.y },
                )
            }
            .toList()
        val lines = OcrReadingOrder.groupIntoLines(regions)
        val blocks = lines.flatten().map { region ->
            TextBlock(
                text = region.text,
                boundingBox = Rect(
                    floor(region.left).toInt(),
                    floor(region.top).toInt(),
                    ceil(region.right).toInt(),
                    ceil(region.bottom).toInt(),
                ),
                confidence = region.confidence,
            )
        }
        val fullText = OcrReadingOrder.buildFullText(lines)
        Log.i(
            TAG,
            "PP-OCRv6 Tiny 识别完成: accepted=${blocks.size}/${result.results.size}, " +
                "lines=${lines.size}, total=${result.totalTimeMs}ms",
        )
        return RecognizeResult(
            fullText = fullText,
            textBlocks = blocks,
            diagnosticResult = diagnosticResult,
        )
    }

    fun close() {
        runBlocking {
            recognitionMutex.withLock {
                initializationMutex.withLock {
                    val current = ocr
                    ocr = null
                    initialized = false
                    lastInferenceTimeMs = -1L
                    current?.release()
                }
            }
        }
    }

    fun getLastInferenceTime(): Long = lastInferenceTimeMs

    companion object {
        private const val TAG = "PaddleOcrHelper"
        private const val RAW_LOG_TAG = "PaddleOcrRaw"
        private const val DET_MODEL_ASSET = "models/ppocrv6_tiny/det/inference.onnx"
        private const val REC_MODEL_ASSET = "models/ppocrv6_tiny/rec/inference.onnx"
        private const val REC_CONFIG_ASSET = "models/ppocrv6_tiny/rec/inference.yml"

        @Volatile
        private var instance: PaddleOcrHelper? = null

        fun getInstance(context: Context): PaddleOcrHelper =
            instance ?: synchronized(this) {
                instance ?: PaddleOcrHelper(context.applicationContext).also { instance = it }
            }

        fun preInitAsync(context: Context) {
            Thread {
                runBlocking { getInstance(context).initAsync() }
            }.start()
        }
    }
}
