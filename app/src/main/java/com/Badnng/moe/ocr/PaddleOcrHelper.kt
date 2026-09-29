package com.Badnng.moe.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import com.Badnng.moe.npu.NpuAccelerator
import com.Badnng.moe.npu.NpuDeviceQuery
import com.Badnng.moe.npu.NpuSupport
import com.paddle.ocr.AccelBackend
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.model.OCRRunResult
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * PP-OCRv6 Tiny 官方 Android SDK 封装。
 *
 * ## ⚠️ NPU 会话的空闲释放纪律(真机确诊,改前必读)
 *
 * QNN 会话**一旦销毁,本进程内就再也建不起来**。真机 912a4148(小米 23046RP50C /
 * 骁龙 8+ Gen 1 / SM8475 / HTP v69)同一进程(PID 22650 全程未杀)内的复现链:
 * ```
 * 12:41:49 RUN A 首次识别 → backend=NPU strict=true  ✅
 * 12:42:50 I PaddleOcrHelper: 资源释放完成, reason=idle-60000ms
 * 12:42:58 RUN B 再次识别 → backend=CPU(回退)  ❌
 * 12:45:33 RUN D 再次识别 → backend=CPU(回退)  ❌ 永不恢复
 * ```
 * 根因是 FastRPC 的 domain 3 设备在进程内只建立一次,`domain_deinit` 并不真正复位,
 * 于是重建时 `apps_dev_init failed for domain 3, errno File exists` 必然失败
 * (完整错误链与机理见 [NpuAccelerator] 类头 KDoc)。
 *
 * 因此本类对"销毁会话"这件事分两种口径:
 * - **主动省内存的空闲释放**(idle-[IDLE_RELEASE_DELAY_MS]ms):若当前会话跑在 NPU 上,
 *   直接**跳过**,让 NPU 保持常驻 —— 因为对 NPU 路径来说"释放"是不可逆操作,释放了
 *   就回不去,省下的内存换来的是永久降级。CPU 路径照旧释放,省内存能力不变。
 * - **不得不销毁**(识别运行时失败降级、内存压力下的显式 [close]):照常销毁,但销毁前
 *   先调 [NpuAccelerator.markUnusableForProcess] 置位进程级标记,让后续识别**直接**走
 *   CPU,而不是每次白跑一遍注定失败的 strict + non-strict 建图并刷屏错误日志。
 *
 * 详细权衡见 [releaseResourcesLocked] 与 [NpuAccelerator.markUnusableForProcess]。
 */
class PaddleOcrHelper private constructor(private val context: Context) {
    @Volatile
    private var ocr: PaddleOCR? = null
    private val initializationMutex = Mutex()
    private val recognitionMutex = Mutex()
    private val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val idleReleaseLock = Any()
    private var idleReleaseJob: Job? = null

    @Volatile
    private var initialized = false

    @Volatile
    private var lastInferenceTimeMs = -1L

    /** NPU 是否当前活跃(初始化成功且未降级)。 */
    @Volatile
    private var npuActive = false

    /**
     * 本机 HTP 架构号(由 [NpuSupport.resolveHtpArch] 解析:白名单优先,设备自报兜底)。
     */
    private var htpArch: Int? = null

    val isInitialized: Boolean get() = initialized

    /** NPU 是否当前活跃。 */
    fun isNpuActive(): Boolean = npuActive

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

    /**
     * 解析 ORT CPU 推理线程数(`numThreads`,可调性能项)。
     *
     * - **缺省 4**:与引入本配置项之前硬编码的 `numThreads = 4` 完全一致 —— 未设置
     *   [KEY_OCR_NUM_THREADS] 时行为不得有任何变化。
     * - **越界回落**:合法区间 [MIN_OCR_NUM_THREADS]..[MAX_OCR_NUM_THREADS]。越界
     *   (0、负数、超过 8)一律**回落到缺省 4**(而不是夹到边界值)并打一条 `Log.w`
     *   说明:越界值通常是配置写坏或外来篡改,此时"回到已知良好的缺省"比"猜测用户
     *   想要边界值"更安全,也让日志与行为一致。
     * - 取值只在此处(建会话时)读取一次,与 [PaddleOCR] 会话同生命周期;改值需重启进程生效。
     */
    private fun resolveNumThreads(): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // getInt 在 key 存了非 int 类型时会抛 ClassCastException;此处兜底回落缺省,
        // 不让一个配置项把整个 OCR 初始化打断。
        val raw = runCatching { prefs.getInt(KEY_OCR_NUM_THREADS, DEFAULT_OCR_NUM_THREADS) }
            .getOrElse { error ->
                Log.w(
                    TAG,
                    "OCR CPU 线程数读取失败(${error.javaClass.simpleName})," +
                        "回落缺省 $DEFAULT_OCR_NUM_THREADS",
                )
                DEFAULT_OCR_NUM_THREADS
            }
        val inRange = raw in MIN_OCR_NUM_THREADS..MAX_OCR_NUM_THREADS
        val threads = if (inRange) raw else DEFAULT_OCR_NUM_THREADS
        val source = when {
            !inRange -> "非法值 $raw,已回落缺省"
            prefs.contains(KEY_OCR_NUM_THREADS) -> "prefs:$KEY_OCR_NUM_THREADS"
            else -> "缺省(未设置)"
        }
        if (!inRange) {
            Log.w(
                TAG,
                "OCR CPU 线程数配置越界: $raw 不在 " +
                    "$MIN_OCR_NUM_THREADS..$MAX_OCR_NUM_THREADS 内," +
                    "回落缺省 $DEFAULT_OCR_NUM_THREADS",
            )
        }
        Log.i(TAG, "OCR CPU 线程数=$threads (来源=$source)")
        return threads
    }

    suspend fun initAsync(): Boolean {
        if (isEmulator()) {
            Log.w(TAG, "检测到模拟器，跳过 PP-OCRv6 Tiny 初始化")
            return false
        }
        cancelIdleRelease()
        if (initialized) {
            scheduleIdleRelease()
            return true
        }

        return initializationMutex.withLock {
            if (initialized) return@withLock true
            try {
                check(OpenCVUtils.init(context)) { "OpenCV 初始化失败" }

                // NPU 加速:如果用户已开启,尝试初始化
                npuActive = false
                htpArch = null
                if (NpuAccelerator.isEnabled(context)) {
                    // 架构走 NpuSupport.resolveHtpArch()(白名单优先、设备自报兜底):
                    // 设备自报在"多套 Skel 并存"的真机上会随目录列举顺序漂移,与就绪判定
                    // (NpuSupport.detect)的口径不一致,会加载到错架构的库。deviceInfo 留给
                    // resolveHtpArch 复用它已有的证据(并打出不一致的诊断日志)。
                    val deviceInfo = NpuDeviceQuery.query()
                    val arch = NpuSupport.resolveHtpArch(deviceInfo)
                    if (arch != null) {
                        htpArch = arch
                        npuActive = NpuAccelerator.tryInitNpu(context, arch)
                        if (npuActive) {
                            Log.i(TAG, "NPU 加速已启用, arch=$arch")
                        } else {
                            Log.w(TAG, "NPU 加速初始化失败,降级为 CPU: ${NpuAccelerator.getLastError()}")
                        }
                    } else {
                        Log.w(TAG, "无法识别设备 HTP 架构,NPU 加速不可用")
                    }
                }

                // 恒传 CPU 的 assets 原始路径:NPU 的 det 模型由
                // ORTSessionManager.resolvePlan 自行解析(内部用 NpuSupport.installedCtxPath
                // 查找 EPContext 模型),不需要外部告知。而所有回退路径(NPU 初始化失败、
                // 严格模式失败、QNN 建图失败)都会用这里传入的路径建 CPU 会话,
                // 所以必须是原始 assets 路径,否则回退时会拿 ctx 模型再崩一次。
                val detModelPath = DET_MODEL_ASSET

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
                    engineConfig = EngineConfig(
                        numThreads = resolveNumThreads(),
                        accel = if (npuActive) AccelBackend.NPU else AccelBackend.CPU,
                    ),
                    detModelAssetPath = detModelPath,
                    recModelAssetPath = REC_MODEL_ASSET,
                    recConfigAssetPath = REC_CONFIG_ASSET,
                )
                ocr = newOcr
                initialized = true
                // 以引擎实际生效的后端为准:请求了 NPU 也可能在内部(SoC 不在白名单、
                // 缺少 ctx 模型、QNN 建图失败)静默回落到 CPU,不能只信本地标记,
                // 否则会把 CPU 会话误报成 NPU,后续的 NPU 失败降级判定也会失效。
                npuActive = newOcr.activeBackend == AccelBackend.NPU
                Log.i(
                    TAG,
                    "PP-OCRv6 Tiny 初始化成功, coldLoad=${newOcr.coldLoadTimeMs}ms, " +
                        "recThreshold=$OCR_MIN_CONFIDENCE, npuActive=$npuActive, detModel=$detModelPath",
                )
                scheduleIdleRelease()
                true
            } catch (error: Throwable) {
                ocr = null
                initialized = false
                npuActive = false
                Log.e(TAG, "PP-OCRv6 Tiny 初始化失败: ${error.message}", error)
                false
            }
        }
    }

    fun init(): Boolean = runBlocking { initAsync() }

    suspend fun recognizeAsync(bitmap: Bitmap): RecognizeResult? {
        cancelIdleRelease()
        return recognitionMutex.withLock {
            try {
                if (!initialized && !initAsync()) return@withLock null
                val currentOcr = ocr ?: return@withLock null
                val result = try {
                    currentOcr.recognize(bitmap)
                } catch (error: Throwable) {
                    // NPU 识别失败,自动降级为 CPU 并重试
                    if (currentOcr.activeBackend == AccelBackend.NPU) {
                        Log.w(TAG, "NPU 识别失败,自动降级为 CPU: ${error.message}")
                        npuActive = false
                        // 释放当前实例,用 CPU 重新初始化。
                        // 此处已在 recognitionMutex 内，必须用 Locked 版本，
                        // 否则 Mutex 重入会永久挂起。
                        //
                        // 这里**不再**调 markNpuFailed()：它的语义是"会话还在,只是这次推理
                        // 失败",会把 initAttempted 复位以便下次重试。可我们在紧接着的下一行
                        // 就把 QNN 会话销毁了,而会话销毁在本进程内不可逆(真机证据见类头 KDoc),
                        // 复位 initAttempted 等于允许后续每次识别都重跑一遍注定失败的
                        // strict + non-strict 建图,白烧 CPU 时间并刷屏 FastRPC 错误日志。
                        // 置位进程级标记后,后续识别直接走 CPU,一次到位。
                        releaseResourcesLocked("npu-fallback", cancelScheduledRelease = false)
                        if (initAsync()) {
                            ocr?.recognize(bitmap)
                        } else {
                            null
                        }
                    } else {
                        throw error
                    }
                }
                result?.let {
                    lastInferenceTimeMs = it.totalTimeMs
                    logRawResults(it)
                    parseResult(it, bitmap.width, bitmap.height)
                }
            } catch (error: Throwable) {
                Log.e(TAG, "PP-OCRv6 Tiny 识别失败: ${error.message}", error)
                null
            } finally {
                scheduleIdleRelease()
            }
        }
    }

    fun recognize(bitmap: Bitmap): RecognizeResult? = runBlocking {
        recognizeAsync(bitmap)
    }

    suspend fun recognizeDiagnosticAsync(bitmap: Bitmap): DiagnosticResult? {
        cancelIdleRelease()
        return recognitionMutex.withLock {
            try {
                if (!initialized && !initAsync()) return@withLock null
                val currentOcr = ocr ?: return@withLock null
                val result = try {
                    currentOcr.recognize(bitmap)
                } catch (error: Throwable) {
                    // NPU 诊断识别失败,自动降级为 CPU 并重试
                    if (currentOcr.activeBackend == AccelBackend.NPU) {
                        Log.w(TAG, "NPU 诊断识别失败,自动降级为 CPU: ${error.message}")
                        npuActive = false
                        // 此处已在 recognitionMutex 内，必须用 Locked 版本，
                        // 否则 Mutex 重入会永久挂起。
                        // 与 recognizeAsync 同理:会话即将被销毁,置位进程级标记而非
                        // 复位 initAttempted,避免后续每次识别重复白跑注定失败的建图。
                        releaseResourcesLocked("npu-fallback-diagnostic", cancelScheduledRelease = false)
                        if (initAsync()) {
                            ocr?.recognize(bitmap)
                        } else {
                            null
                        }
                    } else {
                        throw error
                    }
                }
                result?.let {
                    lastInferenceTimeMs = it.totalTimeMs
                    logRawResults(it)
                    DiagnosticResult(
                        textBlocks = it.results.mapIndexed { index, line ->
                            DiagnosticTextBlock(
                                text = line.text,
                                confidence = line.confidence,
                                points = line.box.points.map { point ->
                                    DiagnosticPoint(point.x, point.y)
                                },
                                recognitionTimeMs = it.perLineRecMs.getOrNull(index),
                            )
                        },
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height,
                        detectionTimeMs = it.detectionTimeMs,
                        recognitionTimeMs = it.recognitionTimeMs,
                        totalTimeMs = it.totalTimeMs,
                    )
                }
            } catch (error: Throwable) {
                Log.e(TAG, "PP-OCRv6 Tiny 诊断识别失败: ${error.message}", error)
                null
            } finally {
                scheduleIdleRelease()
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
        // 分阶段耗时明细（纯日志埋点，不参与任何业务逻辑）：
        // perLineRecMs 可能长达数十个元素，只输出「个数/总和/最大值」避免刷屏。
        // 所有列表都先判空再用 maxOrNull()，日志代码不允许抛异常打断识别流程。
        val perLineRecMs = result.perLineRecMs
        val perLineRecText = if (perLineRecMs.isEmpty()) {
            "无"
        } else {
            "n=${perLineRecMs.size} sum=${perLineRecMs.sum()}ms max=${perLineRecMs.maxOrNull()}ms"
        }
        val detShapeText = if (result.detInputShape.isEmpty()) "无" else result.detInputShape.toString()
        val recShapesText = if (result.recInputShapes.isEmpty()) "无" else result.recInputShapes.toString()
        Log.i(
            TAG,
            "PP-OCRv6 Tiny 耗时分解: " +
                "total=${result.totalTimeMs}ms " +
                "det=${result.detectionTimeMs}ms" +
                "(预处理=${result.detPreprocessMs}/推理=${result.detInferenceMs}/后处理=${result.detPostprocessMs}) " +
                "rec=${result.recognitionTimeMs}ms" +
                "(预处理=${result.recPreprocessMs}/推理=${result.recInferenceMs}/后处理=${result.recPostprocessMs}) " +
                "overhead=${result.pipelineOverheadMs}ms " +
                "detShape=$detShapeText " +
                "recShapes=$recShapesText " +
                "lines=${result.lineCount} " +
                "perLineRec: $perLineRecText",
        )
        return RecognizeResult(
            fullText = fullText,
            textBlocks = blocks,
            diagnosticResult = diagnosticResult,
        )
    }

    fun close(reason: String = "explicit"): Boolean = runBlocking {
        releaseResources(reason, cancelScheduledRelease = true)
    }

    private fun cancelIdleRelease() {
        synchronized(idleReleaseLock) {
            idleReleaseJob?.cancel()
            idleReleaseJob = null
        }
    }

    private fun scheduleIdleRelease() {
        synchronized(idleReleaseLock) {
            idleReleaseJob?.cancel()
            idleReleaseJob = releaseScope.launch {
                delay(IDLE_RELEASE_DELAY_MS)
                releaseResources(
                    reason = "idle-${IDLE_RELEASE_DELAY_MS}ms",
                    cancelScheduledRelease = false,
                    // 定时释放是"省内存"性质,不是"必须腾地方":会话跑在 NPU 上时跳过,
                    // 避免把本进程唯一一次 QNN 会话销毁掉而导致永久降级(真机证据见类头 KDoc)。
                    // CPU 路径不受影响,照旧释放。
                    allowNpuDestroy = false,
                )
            }
        }
    }

    private suspend fun releaseResources(
        reason: String,
        cancelScheduledRelease: Boolean,
        allowNpuDestroy: Boolean = true,
    ): Boolean {
        if (cancelScheduledRelease) cancelIdleRelease()
        return recognitionMutex.withLock {
            releaseResourcesLocked(reason, cancelScheduledRelease, allowNpuDestroy)
        }
    }

    /**
     * [releaseResources] 的锁体实现，**调用方必须已持有 [recognitionMutex]**。
     *
     * 降级路径（recognizeAsync / recognizeDiagnosticAsync）已经在 withLock 内部，
     * 不能再调 [releaseResources]——[Mutex] 不可重入，重入会永久挂起。
     *
     * @param allowNpuDestroy 是否允许销毁**跑在 NPU 上**的会话。
     *   - false：仅"省内存"性质的定时释放走这条。此时若会话在 NPU 上则**直接跳过**，
     *     因为销毁不可逆（见类头 KDoc 真机复现链）——省下的只是 ORT 会话内存，
     *     而 FastRPC 设备状态与已 `System.load` 的 QNN 主机库无论如何都回收不了，
     *     代价却是本进程内永久失去 NPU，"省内存"与"NPU 常驻"在此不可兼得。
     *   - true：识别失败降级、内存压力下的显式 [close]。这类场景内存/正确性优先，
     *     照常销毁，但销毁前会置位 [NpuAccelerator.markUnusableForProcess]，
     *     让后续识别直接走 CPU，而不是每次白跑一遍注定失败的建图。
     */
    private suspend fun releaseResourcesLocked(
        reason: String,
        cancelScheduledRelease: Boolean,
        allowNpuDestroy: Boolean = true,
    ): Boolean {
        return initializationMutex.withLock initializationLock@{
            if (cancelScheduledRelease) cancelIdleRelease()
            val current = ocr ?: return@initializationLock false

            // 以**会话实例自身**的实际后端为准，而不是读 npuActive 标记：
            // npu-fallback 路径会先把 npuActive 置为 false 再销毁会话，
            // 若这里读标记就会漏判"正在销毁一个 NPU 会话"，标记也就落不下去。
            // PaddleOCR.activeBackend 是引擎实际生效的后端，不会说谎。
            val currentOnNpu = current.activeBackend == AccelBackend.NPU

            // 判定放在锁内（而不是 scheduleIdleRelease 的排期时刻）：从排期到定时器
            // 触发之间会话状态可能已经变化，只有在这里读到的才是权威状态。
            //
            // 保留前提是"NPU 仍然被需要"：用户把开关关掉后必须照常销毁，
            // 否则会出现"设置里已关闭 NPU、识别却仍在跑 NPU"的相反故障。
            // 此时销毁会置位进程级标记（本进程不再尝试 NPU），而用户既然已关闭，
            // 本来也不会再用 NPU，两者一致。
            if (!allowNpuDestroy && currentOnNpu && NpuAccelerator.isEnabled(context)) {
                Log.i(
                    TAG,
                    "跳过会话释放（保留 NPU 常驻）: reason=$reason —— 当前会话跑在 NPU 上," +
                        "销毁后本进程无法重建 QNN 会话" +
                        "(真机证据: apps_dev_init failed for domain 3, errno File exists)",
                )
                return@initializationLock false
            }

            // 即将销毁 NPU 会话：先置位进程级"不可再用"标记，再销毁。
            // 顺序很关键 —— 销毁动作本身不可逆，标记必须在会话消失前落下，
            // 否则下一次 initAsync() 会重新尝试 QNN 建图，白跑两轮注定失败的会话创建
            // （strict 一次 + non-strict 一次）并刷出满屏 FastRPC 错误日志。
            if (currentOnNpu) {
                NpuAccelerator.markUnusableForProcess(reason)
                npuActive = false
            }
            ocr = null
            initialized = false
            lastInferenceTimeMs = -1L
            val startedAt = android.os.SystemClock.elapsedRealtime()
            runCatching {
                // 一旦开始释放就必须完成，避免新识别取消空闲任务后泄漏旧模型。
                withContext(NonCancellable) { current.release() }
            }
                .onSuccess {
                    Log.i(
                        TAG,
                        "PP-OCRv6 Tiny 资源释放完成, reason=$reason, " +
                            "elapsed=${android.os.SystemClock.elapsedRealtime() - startedAt}ms",
                    )
                }
                .onFailure { error ->
                    Log.e(TAG, "PP-OCRv6 Tiny 资源释放失败, reason=$reason", error)
                }
                .isSuccess
        }
    }

    /**
     * 放弃 NPU(进程级,不可逆)。
     *
     * ⚠️ 只有**真正用上过 NPU** 的进程才需要调用 [NpuAccelerator.release]:它会置位
     * 进程级"不可再用"标记。若一个纯 CPU 进程(用户没开 NPU、或本机不支持)也照样调用,
     * 就等于白白把 NPU 钉死到进程结束 —— 用户随后在设置里打开 NPU 也会被这个标记拦掉。
     * 所以这里先用"helper 侧活跃标记 或 加速器侧活跃标记"判断 NPU 是否真的在用。
     *
     * 生产代码目前零调用(见 [NpuAccelerator.release] 的说明)。
     */
    fun release() {
        val npuEngaged = npuActive || NpuAccelerator.isNpuActive()
        npuActive = false
        htpArch = null
        if (npuEngaged) NpuAccelerator.release()
    }

    fun getLastInferenceTime(): Long = lastInferenceTimeMs

    companion object {
        private const val TAG = "PaddleOcrHelper"
        private const val RAW_LOG_TAG = "PaddleOcrRaw"
        private const val DET_MODEL_ASSET = "models/ppocrv6_tiny/det/inference.onnx"
        private const val REC_MODEL_ASSET = "models/ppocrv6_tiny/rec/inference.onnx"
        private const val REC_CONFIG_ASSET = "models/ppocrv6_tiny/rec/inference.yml"
        private const val IDLE_RELEASE_DELAY_MS = 60_000L

        /** 与 [NpuAccelerator] 共用的 settings 文件名(同 `settings.xml`)。 */
        private const val PREFS_NAME = "settings"

        /**
         * ORT CPU 推理线程数(`EngineConfig.numThreads`),**可调性能项**。
         *
         * 缺省 4 的理由:引入本配置项之前该值就是硬编码的 4,保持缺省即保持既有行为,
         * 未设置该 key 的用户体验不产生任何变化。
         */
        private const val KEY_OCR_NUM_THREADS = "ocr_num_threads"
        private const val DEFAULT_OCR_NUM_THREADS = 4

        /** 线程数合法区间(越界回落到 [DEFAULT_OCR_NUM_THREADS])。 */
        private const val MIN_OCR_NUM_THREADS = 1
        private const val MAX_OCR_NUM_THREADS = 8

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

        fun releaseIfCreated(reason: String): Boolean = instance?.close(reason) ?: false
    }
}
