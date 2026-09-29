// Copyright (c) 2026 PaddlePaddle Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.paddle.ocr.engine

import ai.onnxruntime.NodeInfo
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.util.Log
import com.Badnng.moe.npu.NpuCapability
import com.Badnng.moe.npu.NpuLibLoader
import com.Badnng.moe.npu.NpuSupport
import com.paddle.ocr.AccelBackend
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.model.OCRError
import java.io.File
import java.nio.FloatBuffer

/**
 * ORT 会话管理。
 *
 * 三个后端共用同一份 Java API 与同一个 `onnxruntime-android-qnn` AAR,差别只在 SessionOptions:
 * - CPU    : 默认 EP,行为与改造前一致
 * - NNAPI  : `addNnapi()`,零额外原生库
 * - NPU    : `addQnn()`,指向 libQnnHtp.so,跑在 Hexagon 上
 *
 * NPU 路径的三点特殊性:
 * 1. QNN HTP **不支持动态 shape**,必须使用固定 shape 的模型。澎湃记走的是
 *    **ctx-only** 路线:直接加载**已经离线编译好的 EPContext 模型**
 *    (`models/npu/ctx/<arch>/det_ctx.onnx`),端侧不做任何图编译。
 * 2. 因为不做端侧编译,APK 里**不需要** `libQnnHtpPrepare.so`(省 79.81 MB);
 *    代价是 det 模型必须由分发端提供预编译 ctx。
 * 3. 会话建立后通过 [detFixedInputShape] / [recFixedInputShape] 把模型固化的尺寸
 *    暴露给预处理层 —— 固定 shape 的会话只接受那一个尺寸,传别的尺寸会直接抛异常。
 */
class ORTSessionManager(
    context: Context,
    private val config: EngineConfig,
) {
    private val appContext = context.applicationContext
    private var env: OrtEnvironment? = null
    private var detSession: OrtSession? = null
    private var recSession: OrtSession? = null
    private var detInputName: String = "x"
    private var recInputName: String = "x"

    var coldLoadTimeMs: Long = 0
        private set

    /** 实际生效的后端。请求 NPU 但条件不满足时会落回 CPU,以本字段为准。 */
    var activeBackend: AccelBackend = AccelBackend.CPU
        private set

    /** 后端决策的说明文案,可直接展示给用户。 */
    var backendNote: String = ""
        private set

    /** NPU 能力探测结果(仅在请求过 NPU 时非空)。 */
    var npuCapability: NpuCapability? = null
        private set

    /** 检测模型被固化的输入 shape;为 null 表示模型仍是动态 shape(CPU/NNAPI 路径)。 */
    var detFixedInputShape: LongArray? = null
        private set

    /** 识别模型被固化的输入 shape;为 null 表示动态 shape。 */
    var recFixedInputShape: LongArray? = null
        private set

    private data class SessionPlan(
        val backend: AccelBackend,
        /** 可直接读取的模型路径:assets 相对路径,或应用私有目录里的绝对路径(按需下载)。 */
        val detModel: String,
        val recModel: String,
        val capability: NpuCapability?,
        /** 是否需要(且允许)在端侧编译并缓存 QNN 图。 */
        val useContextCache: Boolean,
        val note: String,
        /**
         * 是否要求**整图**落在 HTP 上(`session.disable_cpu_ep_fallback=1`)。
         *
         * 这是判断"QNN 是否真的接管"的唯一可靠手段:若开启后建会话失败,
         * 说明有节点被分回 CPU,此时会以 [strictQnn]=false 重建并如实标注。
         */
        val strictQnn: Boolean = false,
        /**
         * 两个模型**各自**实际使用的后端。
         *
         * 之所以要分开,是因为实测(骁龙 8 Gen 3)两个模型的结论完全相反:
         * det 上 HTP 快 5.4×,rec 则是 CPU 在所有宽度上都更快。
         * 所以"NPU 开关"不能是全局的,必须按模型路由。
         *
         * 为 null 表示跟随 [backend](CPU/NNAPI 路径,两者本来就一致)。
         */
        val detBackend: AccelBackend? = null,
        val recBackend: AccelBackend? = null,
    ) {
        fun backendOf(tag: String): AccelBackend =
            (if (tag == "det") detBackend else recBackend) ?: backend
    }

    fun loadModels(detAssetPath: String, recAssetPath: String) {
        val loadStart = System.currentTimeMillis()
        env = OrtEnvironment.getEnvironment()

        val plan = resolvePlan(detAssetPath, recAssetPath)
        val attempts = buildList {
            add(plan)
            // NPU 严格校验失败时,放宽为"允许部分算子回落 CPU"再试一次,
            // 这样既不会把可用性丢掉,也不会谎报成完整 NPU。
            if (plan.backend == AccelBackend.NPU && plan.strictQnn) {
                add(
                    plan.copy(
                        strictQnn = false,
                        note = "NPU(HTP v${plan.capability?.htpArch ?: "?"},部分算子回落 CPU)",
                    ),
                )
            }
        }

        var lastError: Throwable? = null
        for (candidate in attempts) {
            try {
                openSessions(candidate)
                activeBackend = candidate.backend
                backendNote = candidate.note
                coldLoadTimeMs = System.currentTimeMillis() - loadStart
                Log.i(
                    TAG,
                    "会话就绪 backend=$activeBackend strict=${candidate.strictQnn} " +
                        "detShape=${detFixedInputShape?.joinToString()} " +
                        "recShape=${recFixedInputShape?.joinToString()} " +
                        "耗时=${coldLoadTimeMs}ms note=$backendNote",
                )
                return
            } catch (t: Throwable) {
                lastError = t
                closeSessions()
                Log.w(TAG, "以 strict=${candidate.strictQnn} 建会话失败,尝试下一方案", t)
            }
        }

        // 全部方案失败:按配置决定是否整体回退 CPU
        val error = lastError ?: IllegalStateException("no session plan")
        val canFallback = plan.backend != AccelBackend.CPU && config.npu.allowFallbackToCpu
        if (!canFallback) throw error
        Log.w(TAG, "后端 ${plan.backend} 建会话失败,回退 CPU", error)
        val cpuPlan = SessionPlan(
            backend = AccelBackend.CPU,
            detModel = detAssetPath,
            recModel = recAssetPath,
            capability = plan.capability,
            useContextCache = false,
            note = "已回退 CPU(${plan.backend} 初始化失败:${error.message ?: error.javaClass.simpleName})",
        )
        openSessions(cpuPlan)
        activeBackend = AccelBackend.CPU
        backendNote = cpuPlan.note
        coldLoadTimeMs = System.currentTimeMillis() - loadStart
        Log.i(TAG, "会话就绪 backend=CPU(回退) 耗时=${coldLoadTimeMs}ms note=$backendNote")
    }

    fun runDetection(input: FloatArray, shape: LongArray): Pair<FloatArray, LongArray> {
        val session = detSession
            ?: throw OCRError.ModelLoadFailed("detection", Exception("Session not initialized"))
        val ortEnv = env
            ?: throw OCRError.ModelLoadFailed("detection", Exception("Environment not initialized"))
        return runSession(ortEnv, session, detInputName, input, shape, "detection")
    }

    fun runRecognition(input: FloatArray, shape: LongArray): Pair<FloatArray, LongArray> {
        val session = recSession
            ?: throw OCRError.ModelLoadFailed("recognition", Exception("Session not initialized"))
        val ortEnv = env
            ?: throw OCRError.ModelLoadFailed("recognition", Exception("Environment not initialized"))
        return runSession(ortEnv, session, recInputName, input, shape, "recognition")
    }

    fun release() {
        closeSessions()
        env = null
    }

    // ------------------------------------------------------------------ 后端决策

    private fun resolvePlan(detAssetPath: String, recAssetPath: String): SessionPlan {
        if (config.accel != AccelBackend.NPU) {
            val note = when (config.accel) {
                AccelBackend.NNAPI -> "使用 NNAPI 后端"
                else -> ""
            }
            return SessionPlan(config.accel, detAssetPath, recAssetPath, null, false, note)
        }

        val capability = NpuSupport.detect(appContext)
        npuCapability = capability
        if (!capability.supported) {
            Log.i(TAG, "NPU 不可用:${capability.status} / ${capability.detail}")
            return SessionPlan(
                backend = AccelBackend.CPU,
                detModel = detAssetPath,
                recModel = recAssetPath,
                capability = capability,
                useContextCache = false,
                note = "NPU 未启用:${capability.message}",
            )
        }

        val arch = capability.htpArch!!
        // 仓库走 **ctx-only**:预编译的 det_ctx.onnx 本身就是模型,
        // 直接加载即可,端侧不做任何图编译(所以不需要 libQnnHtpPrepare.so)。
        // 它由 NpuModelDownloader 下载到 filesDir,或随包内置在 assets,
        // resolveModelPath 统一解析两条路,返回绝对路径或 assets 相对路径。
        val rawDet = NpuSupport.installedCtxPath(appContext, arch, "det")
        // rec 固定在 CPU(实测所有宽度 CPU 都更快,且能用回动态 shape 保精度),
        // 所以这里恒为 null,不需要 NPU 模型。
        val rawRec: String? = null

        // ---- 按模型分别路由(核心决策)----
        //
        // 实测(骁龙 8 Gen 3,统一输入 shape):
        //   det [1,3,2688,1216]  CPU 359ms → HTP  67ms   快 5.4×
        //   rec [1,3,48,1280]    CPU  15ms → HTP  25ms   慢 1.7×
        // rec 在所有宽度上都是 CPU 更快,所以默认只把 det 放上 HTP。
        // 好处是双份的:速度更快,而且 rec 能用回**原版动态 shape 模型**,
        // 长文本行不再被压扁,精度也回到原版水平。
        val detWantsHtp = config.npu.detOnHtp
        val recWantsHtp = config.npu.recOnHtp

        if (!detWantsHtp && !recWantsHtp) {
            return SessionPlan(
                backend = AccelBackend.CPU,
                detModel = detAssetPath,
                recModel = recAssetPath,
                capability = capability,
                useContextCache = false,
                note = "NPU 未启用:配置里已关闭两个模型的 HTP 路由",
            )
        }

        if (detWantsHtp && rawDet == null) {
            return SessionPlan(
                backend = AccelBackend.CPU,
                detModel = detAssetPath,
                recModel = recAssetPath,
                capability = capability,
                useContextCache = false,
                note = "NPU 未启用:缺少预编译的 det NPU 模型(models/npu/ctx/$arch/det_ctx.onnx)",
            )
        }
        if (recWantsHtp && rawRec == null) {
            return SessionPlan(
                backend = AccelBackend.CPU,
                detModel = detAssetPath,
                recModel = recAssetPath,
                capability = capability,
                useContextCache = false,
                note = "NPU 未启用:缺少预编译的 rec NPU 模型(models/npu/ctx/$arch/rec_ctx.onnx)",
            )
        }

        // rec 不上 HTP 时用**原版动态模型**;它的预处理会自动走 preprocessBatch
        // (无固定 shape),因此长文本行不会被压缩。
        val recModel = if (recWantsHtp) rawRec!! else recAssetPath
        val note = if (recWantsHtp) {
            "NPU(HTP v$arch,det+rec 都在 HTP 上)"
        } else {
            "NPU(HTP v$arch,仅 det 在 HTP 上;rec 留在 CPU 以保精度与速度)"
        }

        return SessionPlan(
            backend = AccelBackend.NPU,
            detModel = rawDet!!,
            recModel = recModel,
            capability = capability,
            useContextCache = true,
            note = note,
            strictQnn = config.npu.verifyFullOffload,
            detBackend = AccelBackend.NPU,
            recBackend = if (recWantsHtp) AccelBackend.NPU else AccelBackend.CPU,
        )
    }

    // ------------------------------------------------------------------ 会话建立

    private fun openSessions(plan: SessionPlan) {
        val ortEnv = env
            ?: throw OCRError.ModelLoadFailed("OCR", Exception("Environment not initialized"))
        try {
            detSession = createSession(ortEnv, plan, plan.detModel, "det")
        } catch (t: Throwable) {
            throw OCRError.ModelLoadFailed("detection", t)
        }
        try {
            recSession = createSession(ortEnv, plan, plan.recModel, "rec")
        } catch (t: Throwable) {
            detSession?.close()
            detSession = null
            throw OCRError.ModelLoadFailed("recognition", t)
        }

        detInputName = firstInputName(detSession!!)
        recInputName = firstInputName(recSession!!)
        detFixedInputShape = fixedShapeOf(detSession!!, detInputName)
        recFixedInputShape = fixedShapeOf(recSession!!, recInputName)
    }

    /**
     * 每个模型单独一份 SessionOptions:`ep.context_file_path` 是挂在 session options 上的,
     * 两个模型共用一份会导致 context 缓存互相覆盖。
     */
    private fun createSession(
        ortEnv: OrtEnvironment,
        plan: SessionPlan,
        modelPath: String,
        tag: String,
    ): OrtSession {
        val opts = buildSessionOptions(plan, tag)
        try {
            // configureQnn 若登记了要直接加载的 ctx(分发下来的,或端侧缓存),
            // 这里就**直接加载它本身**(它就是 EPContext onnx,由 libQnnSystem.so 读取),
            // 而不是再拿原始模型去触发一次编译 —— 后者会因文件已存在而失败。
            val effective = pendingCachedContext.remove(tag) ?: modelPath
            if (effective != modelPath) {
                Log.i(TAG, "[$tag] 以现成的 context 作为模型加载:$effective")
            }
            return ortEnv.createSession(readModel(effective), opts)
        } finally {
            opts.close()
        }
    }

    /**
     * ctx 命中时,由 [configureQnn] 写入、[createSession] 消费的一次性意图。
     * 放在实例上是为了不改动 createSession 的签名(它由多条路径调用)。
     */
    private val pendingCachedContext = HashMap<String, String>()

    private fun buildSessionOptions(plan: SessionPlan, tag: String): OrtSession.SessionOptions {
        val opts = OrtSession.SessionOptions()
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        opts.setIntraOpNumThreads(config.numThreads)
        // 按**模型各自的**后端来配,而不是整个 plan 的后端:
        // det 走 HTP 而 rec 留在 CPU 是有意为之(见 NpuOptions.recOnHtp 的实测表)。
        when (plan.backendOf(tag)) {
            AccelBackend.CPU -> {
                if (plan.backend == AccelBackend.NPU) {
                    Log.i(TAG, "[$tag] 该模型按实测结论留在 CPU(未启用 QNN)")
                }
            }
            AccelBackend.NNAPI -> opts.addNnapi()
            AccelBackend.NPU -> configureQnn(opts, plan, tag)
        }
        return opts
    }

    /**
     * 把 `ADSP_LIBRARY_PATH` 指到**Skel 所在的那个目录**。
     *
     * 委托给 [NpuSupport.ensureAdspLibraryPath]:外载目录
     * (`filesDir/npu_libs/<abi>/`)优先,回落 `nativeLibraryDir`。
     * 完整原理、真机对照表与三个必要条件见该方法的文档。
     *
     * 这里只需记住一件事:**必须在 QNN 初始化之前调用**
     * (FastRPC 首次连 DSP 时读取并缓存该变量)。
     */
    private fun ensureAdspLibraryPath(arch: Int? = null): String =
        NpuSupport.ensureAdspLibraryPath(appContext, arch)

    private fun configureQnn(opts: OrtSession.SessionOptions, plan: SessionPlan, tag: String) {
        val npu = config.npu
        val arch = plan.capability?.htpArch

        // 必须在 QNN 初始化之前设置,见 ensureAdspLibraryPath 的说明。
        // 传入 arch 让它在"外载目录"里校验本机那一份 Skel 是否真的在。
        ensureAdspLibraryPath(arch)

        val providerOptions = LinkedHashMap<String, String>()
        // backend_path 的取值规则(2026-09-20 真机实测):
        //  - **so 外置**(filesDir/npu_libs/<abi>/ 里那份齐备)→ 传**绝对路径**。
        //    传裸文件名 libQnnHtp.so 时链接器只会在 nativeLibraryDir 里找,
        //    外载那份根本不会被用到,表现为"明明下载了却仍然回落 CPU"。
        //  - 传统内置 → 传裸文件名,由 Android 链接器在应用库目录内解析。
        providerOptions["backend_path"] = NpuLibLoader.backendPath(appContext, arch ?: 0)
        arch?.let { providerOptions["htp_arch"] = it.toString() }
        providerOptions["htp_performance_mode"] = npu.performanceMode
        providerOptions["enable_htp_fp16_precision"] = if (npu.enableFp16Precision) "1" else "0"
        providerOptions["htp_graph_finalization_optimization_mode"] =
            npu.graphFinalizationOptimizationMode.toString()
        npu.vtcmMb?.let { providerOptions["vtcm_mb"] = it.toString() }
        opts.addQnn(providerOptions)

        // 严格模式:要求整图落在 HTP。若仍有节点分给 CPU,建会话会失败,
        // 上层据此降级为非严格模式并如实标注,避免"显示 NPU 实跑 CPU"。
        if (plan.strictQnn || npu.disableCpuEpFallback) {
            // 注意:Java API 里是 addConfigEntry,不是 addSessionConfigEntry
            opts.addConfigEntry("session.disable_cpu_ep_fallback", "1")
        }

        if (plan.useContextCache && npu.contextCacheEnabled) {
            // 模型指纹:ctx-only 下只用于 ③ 的端侧缓存文件名,`installedCtxPath` 不消费它。
            val shapeKey = modelShapeKey(plan, tag)

            // ── ① 分发下来的预编译 ctx(**仓库的主路径**)──────────────────────
            //
            // 澎湃记走 ctx-only:模型就是 EPContext 本身,直接加载,**绝不编译**。
            // 之所以把这一步放在最前面,是因为在 external 模式(不打包
            // libQnnHtpPrepare.so)下,一旦走到端侧编译分支必然失败并回落 CPU。
            //
            // 文件名固定为 `<model>_ctx.onnx`(不带指纹,见 NpuSupport.installedCtxPath),
            // ctx 与 HTP 架构强绑定(v75 的不能用在 v73 上),所以路径里必须带 arch。
            val installedCtx = NpuSupport.installedCtxPath(appContext, arch ?: 0, tag, shapeKey)
            if (installedCtx != null) {
                Log.i(
                    TAG,
                    "[$tag] 使用预编译的 QNN context:$installedCtx,直接加载,不编译",
                )
                pendingCachedContext[tag] = installedCtx
                return
            }

            // ── ② 兜底:模型路径**本身就是** EPContext,直接当模型加载 ────────
            //
            // 正常流程不会走到这里:能用 NPU 时 ① 必然命中(detect() 已按
            // `models/npu/ctx/<arch>/det_ctx.onnx` 拦住"没有 ctx"的情况)。
            // 但如果模型是绕过 ① 直接传进来的(例如路径以 `_ctx.onnx` 结尾、
            // 但不在约定目录里),这里必须拦住,否则会带着**已存在的 ctx 文件**
            // 去开 `ep.context_enable=1`,ORT 会报
            //   Failed to generate EP context model since the file already exists
            // 于是 QNN 建图失败 → 算子全部静默回落 CPU(界面却仍显示 NPU)。
            val tagModel = if (tag == "det") plan.detModel else plan.recModel
            if (tagModel.endsWith("_ctx.onnx", ignoreCase = true) && File(tagModel).isAbsolute) {
                Log.i(
                    TAG,
                    "[$tag] 模型本身已是 EPContext($tagModel),直接加载,不触发端侧编译",
                )
                pendingCachedContext[tag] = tagModel
                return
            }

            // ── ③ 最后才考虑端侧自己编译 ──────────────────────────────────────
            //
            // ⚠️ 这条路径**只在 APK 打包了 libQnnHtpPrepare.so 时**才可能成功。
            // 澎湃记默认不打包该库(省 79.81 MB),所以 ① 未命中时这里大概率失败,
            // 随后由 loadModels 的非严格重试/CPU 回退兜住。保留它是为了让
            // "临时把 Prepare 库塞进来做验证"这件事仍然可行。
            val cacheDir = NpuSupport.contextCacheDir(appContext, npu.contextCacheDirName)
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                Log.w(TAG, "无法创建 QNN context 缓存目录:${cacheDir.absolutePath}")
                return
            }

            // 缓存文件名带上**模型输入 shape 指纹**。
            //
            // 否则换了模型但文件名不变,旧 ctx 会被当成新模型的缓存加载 ——
            // 它内部固化的仍是旧 shape 的图,轻则精度异常,重则建图失败后静默回落 CPU。
            // ctx-only 下拿不到指纹时 shapeKey 是 "u",只影响这个缓存文件名,无害。
            val ctxFile = File(cacheDir, "${tag}_htp${arch ?: 0}_${shapeKey}_ctx.onnx")

            // ⚠️ 已存在缓存时必须**当模型加载**,绝不能再让 ORT 去生成一次。
            // 真机实测:若带着已存在的 ctx 文件再走 `ep.context_enable=1` +
            // 同一个 `ep.context_file_path` 建会话,ORT 会报
            //   Failed to generate EP context model since the file '...' already exists
            // 这个 bug 的症状极具迷惑性:第一次开启 NPU 是好的,第二次启动起必然失败。
            if (ctxFile.isFile && ctxFile.length() > 0) {
                Log.i(
                    TAG,
                    "[$tag] 复用已缓存的 QNN context:${ctxFile.absolutePath}" +
                        "(${ctxFile.length() / 1024}KB),直接加载,不再编译",
                )
                pendingCachedContext[tag] = ctxFile.absolutePath
                return
            }

            // 顺手清掉同一 tag 下**旧指纹**的 ctx,避免缓存目录无限堆积
            // (每个指纹都几十 MB)。只删本 tag 前缀的文件,不影响另一个模型。
            cacheDir.listFiles()
                ?.filter {
                    it.name.startsWith("${tag}_htp") &&
                        it.name.endsWith("_ctx.onnx") &&
                        it.name != ctxFile.name
                }
                ?.forEach { stale ->
                    Log.i(TAG, "[$tag] 清理旧指纹的 context 缓存:${stale.name}")
                    stale.delete()
                }

            // embed 模式让 ORT 把 context 二进制嵌进生成的 onnx,避免两个文件走散
            opts.addConfigEntry("ep.context_enable", "1")
            opts.addConfigEntry("ep.context_embed_mode", "1")
            opts.addConfigEntry("ep.context_file_path", ctxFile.absolutePath)
            Log.i(
                TAG,
                "[$tag] QNN 图将端侧编译并缓存到 ${ctxFile.absolutePath};" +
                    "该文件等价于 PC 端 build_qnn_context.py 的产物,可直接取出复用",
            )
        }
    }

    /**
     * 模型内容的短指纹,用作 ctx 缓存文件名的一部分。
     *
     * 用**模型字节哈希**而不是"读 shape":后者要额外建一次 ORT 会话
     * (还会泄漏 `SessionOptions`),而模型内容一变 shape 必然变,
     * 哈希的代价只是一次几 MB 的内存摘要,更简单也更不容易出错。
     *
     * 实现已收敛到 [NpuSupport.modelFingerprint] —— 分发端要按同一算法算文件名,
     * 两处各写一份迟早漂移。
     *
     * ctx-only 下模型是 `det_ctx.onnx`,`modelFingerprint` 照样算得出来(它只读字节);
     * 只有连文件都读不到时才会退回 `"u"`。
     */
    private fun modelShapeKey(plan: SessionPlan, tag: String): String {
        val path = if (tag == "det") plan.detModel else plan.recModel
        return NpuSupport.modelFingerprint(appContext, path)
            ?: run {
                Log.w(TAG, "[$tag] 计算模型指纹失败,ctx 缓存键退回 unknown:$path")
                "u"
            }
    }

    private fun closeSessions() {
        try {
            detSession?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "关闭 detection 会话失败", t)
        } finally {
            detSession = null
            try {
                recSession?.close()
            } catch (t: Throwable) {
                Log.w(TAG, "关闭 recognition 会话失败", t)
            } finally {
                recSession = null
                detFixedInputShape = null
                recFixedInputShape = null
            }
        }
    }

    // ------------------------------------------------------------------ 工具方法

    private fun firstInputName(session: OrtSession): String = try {
        session.inputNames.iterator().next()
    } catch (t: Throwable) {
        throw OCRError.ModelLoadFailed("model", t)
    }

    /**
     * 读取模型输入张量的固定 shape。任意一维 <= 0(动态维)即返回 null,
     * 表示该模型不能走 QNN HTP。
     */
    private fun fixedShapeOf(session: OrtSession, inputName: String): LongArray? = try {
        val nodeInfo: NodeInfo? = session.inputInfo[inputName]
        val tensorInfo = nodeInfo?.info as? TensorInfo
        val shape = tensorInfo?.shape
        if (shape == null || shape.isEmpty() || shape.any { it <= 0 }) null else shape
    } catch (t: Throwable) {
        Log.w(TAG, "读取输入 shape 失败:$inputName", t)
        null
    }

    /**
     * 读取模型:绝对路径按文件读(按需下载的模型,或端侧产出的 ctx),
     * 相对路径按 assets 读(内置模型)。
     *
     * ⚠️ 这个分支是 NPU 能起来的前提:`PaddleOcrHelper` 会把
     * `NpuAccelerator.resolveDetModelPath()` 返回的 **filesDir 绝对路径**传进来,
     * 早期只走 `assets.open()` 时必然抛 [OCRError.ModelNotFound],NPU 永远起不来。
     */
    private fun readModel(modelPath: String): ByteArray = try {
        val file = File(modelPath)
        if (file.isAbsolute) {
            file.readBytes()
        } else {
            appContext.assets.open(modelPath).use { it.readBytes() }
        }
    } catch (t: Throwable) {
        throw OCRError.ModelNotFound(modelPath, t)
    }

    private fun runSession(
        ortEnv: OrtEnvironment,
        session: OrtSession,
        inputName: String,
        input: FloatArray,
        shape: LongArray,
        modelName: String,
    ): Pair<FloatArray, LongArray> {
        val tensor = try {
            OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(input), shape)
        } catch (t: Throwable) {
            throw OCRError.InferenceFailed(modelName, t)
        }
        val result = try {
            try {
                session.run(mapOf(inputName to tensor))
            } catch (t: Throwable) {
                throw OCRError.InferenceFailed(modelName, t)
            }
        } finally {
            tensor.close()
        }

        return try {
            try {
                val outputName = session.outputNames.iterator().next()
                val ortValue = result.get(outputName)
                    .orElseThrow { Exception("No output tensor found") }
                val outputTensor = ortValue as? OnnxTensor
                    ?: throw Exception("Output is not an ONNX tensor")
                Pair(copyFloatBuffer(outputTensor.floatBuffer), outputTensor.info.shape)
            } catch (t: Throwable) {
                throw OCRError.InferenceFailed(modelName, t)
            }
        } finally {
            result.close()
        }
    }

    private fun copyFloatBuffer(buffer: FloatBuffer): FloatArray {
        val duplicate = buffer.duplicate()
        duplicate.rewind()
        val output = FloatArray(duplicate.remaining())
        duplicate.get(output)
        return output
    }

    private companion object {
        const val TAG = "ORTSessionManager"
    }
}
