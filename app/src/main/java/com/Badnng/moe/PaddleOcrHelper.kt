package com.Badnng.moe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import com.equationl.ncnnandroidppocr.OCR
import com.equationl.ncnnandroidppocr.bean.Device
import com.equationl.ncnnandroidppocr.bean.DrawModel
import com.equationl.ncnnandroidppocr.bean.ImageSize
import com.equationl.ncnnandroidppocr.bean.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PaddleOCR 封装类 (ncnn 版本，支持 16KB 页面大小)
 */
class PaddleOcrHelper private constructor(private val context: Context) {

    private var ocr: OCR? = null
    private val initialized = AtomicBoolean(false)
    private val initializing = AtomicBoolean(false)
    @Volatile private var isRecognizing = false

    val isInitialized: Boolean get() = initialized.get()

    data class TextBlock(
        val text: String,
        val boundingBox: Rect?,
        val confidence: Float
    )

    data class RecognizeResult(
        val fullText: String,
        val textBlocks: List<TextBlock>
    )

    /**
     * 异步初始化，推荐在 Application.onCreate() 或 Activity.onCreate() 时调用
     */
    suspend fun initAsync(
        modelType: ModelType = ModelType.Mobile,
        imageSize: ImageSize = ImageSize.Size720,
        device: Device = Device.CPU
    ): Boolean = withContext(Dispatchers.IO) {
        synchronized(this@PaddleOcrHelper) {
            if (initialized.get()) {
                Log.d(TAG, "PaddleOCR 已初始化，跳过")
                return@withContext true
            }

            if (!initializing.compareAndSet(false, true)) {
                Log.d(TAG, "PaddleOCR 正在初始化，等待...")
                // 等待其他线程初始化完成
                while (!initialized.get()) {
                    Thread.sleep(50)
                }
                return@withContext true
            }
        }

        Log.d(TAG, "开始初始化 PaddleOCR (ncnn)...")

        return@withContext try {
            val newOcr = OCR()
            val success = newOcr.initModelFromAssert(
                context.assets,
                modelType,      // 可配置
                imageSize,      // 可配置
                device          // 可配置
            )

            synchronized(this@PaddleOcrHelper) {
                ocr = newOcr
                initialized.set(success)
            }

            if (success) {
                Log.i(TAG, "PaddleOCR (ncnn) 初始化成功!")
            } else {
                Log.e(TAG, "PaddleOCR (ncnn) 初始化失败")
                initializing.set(false)
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "PaddleOCR (ncnn) 初始化异常: ${e.message}", e)
            initializing.set(false)
            false
        }
    }

    /**
     * 同步初始化，用于快速初始化场景
     */
    fun init(
        modelType: ModelType = ModelType.Mobile,
        imageSize: ImageSize = ImageSize.Size720,
        device: Device = Device.CPU
    ): Boolean {
        synchronized(this@PaddleOcrHelper) {
            if (initialized.get()) return true

            if (initializing.get()) {
                Log.d(TAG, "PaddleOCR 正在初始化，等待...")
            }
        }

        // 等待初始化完成
        while (initializing.get() && !initialized.get()) {
            Thread.sleep(50)
        }

        if (initialized.get()) return true

        synchronized(this@PaddleOcrHelper) {
            if (initialized.get()) return true

            Log.d(TAG, "同步初始化 PaddleOCR (ncnn)...")
            return try {
                val newOcr = OCR()

                // 🚀 优化：预热模型（如果 ncnn 支持）
                val success = newOcr.initModelFromAssert(
                    context.assets,
                    modelType,
                    imageSize,
                    device
                )
                ocr = newOcr
                initialized.set(success)

                if (success) {
                    Log.i(TAG, "PaddleOCR (ncnn) 初始化成功!")
                }
                success
            } catch (e: Exception) {
                Log.e(TAG, "PaddleOCR (ncnn) 初始化异常: ${e.message}", e)
                false
            }
        }
    }

    /**
     * 🚀 核心优化：识别方法 - 复用已初始化的 OCR 实例
     */
    suspend fun recognizeAsync(bitmap: Bitmap): RecognizeResult? = withContext(Dispatchers.Default) {
        recognize(bitmap)
    }

    fun recognize(bitmap: Bitmap): RecognizeResult? {
        val currentOcr: OCR?
        synchronized(this@PaddleOcrHelper) {
            currentOcr = ocr
        }

        if (currentOcr == null || !initialized.get()) {
            Log.e(TAG, "PaddleOCR 未初始化，无法识别")
            return null
        }

        // 🚀 优化：预处理图片，确保格式为 ARGB_8888
        val processedBitmap = if (bitmap.width > 960 || bitmap.height > 960) {
            val scale = 960f / maxOf(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            Log.d(TAG, "缩小图片: ${bitmap.width}x${bitmap.height} -> ${newWidth}x${newHeight}")
            // 创建缩放后的 bitmap，并确保格式为 ARGB_8888
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            // 如果不是 ARGB_8888 格式，转换为 ARGB_8888
            if (scaledBitmap.config != Bitmap.Config.ARGB_8888) {
                val argbBitmap = scaledBitmap.copy(Bitmap.Config.ARGB_8888, false)
                scaledBitmap.recycle()
                argbBitmap
            } else {
                scaledBitmap
            }
        } else {
            // 确保原始 bitmap 也是 ARGB_8888 格式
            if (bitmap.config != Bitmap.Config.ARGB_8888) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }
        }

        Log.d(TAG, "开始识别图片: ${processedBitmap.width}x${processedBitmap.height}")

        // 🚀 优化：更智能的并发控制
        var waitCount = 0
        while (isRecognizing && waitCount < 300) {  // 最多等待 30 秒
            Thread.sleep(100)
            waitCount++
        }
        if (isRecognizing) {
            Log.e(TAG, "PaddleOCR 上一轮识别超时，强制重置")
            isRecognizing = false
        }
        isRecognizing = true

        return try {
            // 🚀 核心：复用 OCR 实例，无需重新初始化
            val result = currentOcr.detectBitmap(processedBitmap, DrawModel.None)

            if (result == null) {
                Log.e(TAG, "PaddleOCR (ncnn) 识别返回 null")
                return null
            }

            val parsedResult = parseResult(result)
            Log.i(TAG, "PaddleOCR (ncnn) 识别成功: ${parsedResult.textBlocks.size} 个文本块, 耗时: ${result.inferenceTime}ms")

            parsedResult
        } catch (e: Exception) {
            Log.e(TAG, "PaddleOCR (ncnn) 识别异常: ${e.message}", e)
            null
        } finally {
            isRecognizing = false
        }
    }

    /**
     * 🚀 优化：批量识别方法（如果需要）
     */
    suspend fun recognizeBatch(bitmaps: List<Bitmap>): List<RecognizeResult?> = withContext(Dispatchers.Default) {
        bitmaps.map { recognize(it) }
    }

    private fun parseResult(result: com.equationl.ncnnandroidppocr.bean.OcrResult): RecognizeResult {
        val textBlocks = mutableListOf<TextBlock>()
        val fullTextBuilder = StringBuilder()

        result.textLines.forEach { line ->
            val text = line.text ?: ""
            val confidence = line.confidence ?: -1f

            val boundingBox = if (line.points.isNotEmpty()) {
                pointsToRect(line.points)
            } else {
                null
            }

            if (text.isNotEmpty()) {
                textBlocks.add(TextBlock(text, boundingBox, confidence))
            }
        }

        // 按照从下到上的顺序排序文字块（根据 boundingBox 的 bottom 坐标降序）
        val sortedTextBlocks = textBlocks.sortedByDescending { it.boundingBox?.bottom ?: 0 }

        // 按照排序后的顺序拼接全文
        sortedTextBlocks.forEachIndexed { index, textBlock ->
            if (index > 0) {
                fullTextBuilder.append("\n")
            }
            fullTextBuilder.append(textBlock.text)
        }

        Log.i(TAG, "解析完成: ${sortedTextBlocks.size} 个有效文本块，从下到上排序")

        return RecognizeResult(fullTextBuilder.toString(), sortedTextBlocks)
    }

    private fun pointsToRect(points: List<Point>): Rect? {
        if (points.isEmpty()) return null

        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE

        points.forEach { point ->
            minX = minOf(minX, point.x)
            minY = minOf(minY, point.y)
            maxX = maxOf(maxX, point.x)
            maxY = maxOf(maxY, point.y)
        }

        return if (minX != Int.MAX_VALUE) {
            Rect(minX, minY, maxX, maxY)
        } else {
            null
        }
    }

    /**
     * 🚀 优化：优雅关闭 - 可在 Application.onDestroy() 调用
     */
    fun close() {
        synchronized(this@PaddleOcrHelper) {
            Log.d(TAG, "释放 PaddleOCR 资源")
            ocr?.release()
            ocr = null
            initialized.set(false)
            initializing.set(false)
        }
    }

    /**
     * 获取当前 OCR 实例的推理时间（用于性能监控）
     */
    fun getLastInferenceTime(): Long {
        // 如果 ncnn OCR 支持获取最后一次推理时间
        return ocr?.let {
            // 可能需要在 OCR 类中添加获取时间的方法
            -1L // 暂时返回 -1
        } ?: -1L
    }

    companion object {
        private const val TAG = "PaddleOcrHelper"

        @Volatile
        private var instance: PaddleOcrHelper? = null
        private val lock = Any()

        fun getInstance(context: Context): PaddleOcrHelper {
            return instance ?: synchronized(lock) {
                instance ?: PaddleOcrHelper(context.applicationContext).also {
                    instance = it
                }
            }
        }

        /**
         * 🚀 优化：预初始化（推荐在 Application 中调用）
         */
        fun preInitAsync(context: Context) {
            Thread {
                val helper = getInstance(context)
                helper.init(
                    modelType = ModelType.Mobile,
                    imageSize = ImageSize.Size720,
                    device = Device.CPU
                )
            }.start()
        }
    }
}