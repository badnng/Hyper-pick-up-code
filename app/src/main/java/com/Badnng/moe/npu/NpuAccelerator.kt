package com.Badnng.moe.npu

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * NPU 加速管理器。
 *
 * 负责:
 * 1. 用户偏好读写(NPU 开关状态)
 * 2. NPU 运行时初始化(加载 so、配置 ORT QNN EP)
 * 3. 自动降级:任何一步失败都回落 CPU,确保识别流程不断
 * 4. 资源释放:遵守 PaddleOcrHelper 的空闲释放规则
 *
 * ## 降级策略
 *
 * NPU 加速是**尽力而为**的优化,不是硬依赖。任何环节失败都静默降级为 CPU:
 * - 非高通平台 → CPU
 * - SoC 不在白名单 → CPU
 * - QNN 运行时未下载 → 提示用户下载,不自动启用
 * - 下载后加载失败 → CPU(下次识别再试)
 * - 识别过程中 NPU 报错 → CPU(下次识别再试)
 *
 * ## 与 PaddleOcrHelper 的关系
 *
 * [com.Badnng.moe.ocr.PaddleOcrHelper] 在 initAsync() 时调用 [tryInitNpu],
 * 如果返回 true 则使用 NPU 加速的模型路径,否则使用默认 CPU 路径。
 * 识别过程中如果 NPU 抛异常,PaddleOcrHelper 会自动降级为 CPU 重试。
 *
 * ## ⚠️ 进程级不可逆:QNN 会话销毁后**无法重建**(真机确诊)
 *
 * 这是本类最重要的一条事实,任何"释放 NPU 资源以便稍后重建"的想法都是错的。
 *
 * 真机 912a4148(小米 23046RP50C / 骁龙 8+ Gen 1 / SM8475 / HTP v69)同一进程
 * (PID 22650 全程未杀)内的复现链:
 * ```
 * 12:41:49 RUN A 首次识别 → backend=NPU strict=true  ✅
 * 12:42:50 I PaddleOcrHelper: 资源释放完成, reason=idle-60000ms
 * 12:42:58 RUN B 再次识别 → backend=CPU(回退)  ❌
 * 12:45:33 RUN D 再次识别 → backend=CPU(回退)  ❌ 永不恢复
 * ```
 * 关键错误串(FastRPC 设备在进程内只建立一次,`domain_deinit` 并不真正复位):
 * ```
 * E adsprpc/fastrpc_apps_user.c:3201: apps_dev_init failed for domain 3, errno File exists, ioErr -1
 * E adsprpc/fastrpc_apps_user.c:3331: open_dev (-1) failed for domain 3 (errno File exists)
 * E adsprpc/fastrpc_apps_user.c:1496: remote_handle64_open failed for
 *     file:///libQnnHtpV69Skel.so?...&_dom=cdsp (errno File exists)
 * E onnxruntime: qnn_execution_provider.cc:1046 GetCapability] QNN SetupBackend failed
 *     Failed to create device. Error: QNN_DEVICE_ERROR_INVALID_CONFIG: Invalid config values
 * ```
 *
 * 机理:第一次成功建 QNN 会话时 FastRPC 打开了 domain 3 的设备节点,此后
 * `apps_dev_init` 一律以 `errno File exists`(EEXIST)失败;而 QNN 主机侧库经
 * `System.load` 加载后**进程内不可卸载**,无法把这份静态状态复位。
 * 结论:**本进程内第二次 QNN 设备初始化必然失败**。
 *
 * 因此 [release] 不是"重置状态以便重建",而是**放弃本进程内的 NPU** ——
 * 它会把 [unusableForProcess] 置位。同理,凡是"销毁过 NPU 会话还想重建"的路径
 * (空闲释放、内存压力释放、识别运行时降级)都必须置位该标记,否则只会让每次
 * 识别都白跑一遍必然失败的 strict + non-strict 建图,并刷出满屏错误日志。
 */
object NpuAccelerator {

    private const val TAG = "NpuAccelerator"
    private const val PREFS_NAME = "settings"
    private const val KEY_NPU_ENABLED = "npu_acceleration_enabled"

    /**
     * [unusableForProcess] 置位后统一的失败文案。
     *
     * 措辞里带"重启应用"是必要的:这是**唯一**的恢复手段,任何时候都不能让用户
     * 以为"关掉再打开开关"有用(见 [setEnabled] 的说明)。
     */
    private const val UNUSABLE_MESSAGE =
        "本进程已释放过 QNN 会话,NPU 无法重建(重启应用后可恢复)"

    /** 与 [UNUSABLE_MESSAGE] 配套的日志后缀,便于日志检索。 */
    private const val UNUSABLE_LOG_SUFFIX = "NPU 在本进程内不可再用"

    /** 是否已尝试初始化 NPU(无论成功与否)。 */
    @Volatile
    private var initAttempted = false

    /** NPU 是否当前活跃(初始化成功且未降级)。 */
    @Volatile
    private var npuActive = false

    /**
     * 本进程内 NPU 是否已被判定为**不可再用**(QNN 会话一旦销毁即置位)。
     *
     * 与 [initAttempted] 的区别是本标记**不是"本次已试过"的缓存,而是一条不可逆事实**:
     * 本进程已经销毁过 QNN 会话,因此再次 `QNN SetupBackend` 必然失败
     * (`apps_dev_init failed for domain 3, errno File exists`,详见类头 KDoc 的真机证据)。
     *
     * 置位后 [tryInitNpu] 直接返回 false,不再白跑一遍必然失败的建图;
     * 唯一能清掉它的是 [reset](仅测试调用)—— 生产环境里"恢复"只能是**重启进程**。
     */
    @Volatile
    private var unusableForProcess = false

    /** 最近一次初始化失败的错误信息(用于诊断)。 */
    @Volatile
    private var lastInitError: String? = null

    /** 初始化互斥锁,防止并发初始化。 */
    private val initMutex = Mutex()

    /** 已加载的 QNN 主机侧库(防止重复加载)。 */
    @Volatile
    private var hostLibsLoaded = false

    /**
     * 本进程内已成功 `System.load` 过的库绝对路径。
     *
     * `System.load` 加载的本地库在**同一进程内无法卸载**,重复加载同一路径会抛
     * `UnsatisfiedLinkError`("Library ... already loaded in another classloader")
     * 或触发 native 层重复初始化崩溃。
     *
     * 因此**不能靠 [hostLibsLoaded] 去重**:该标记只是实例层面的"本次已加载"状态,
     * 语义上随时可能被后续改动重置 —— 目前唯一把它重置为 false 的是 [reset](仅测试
     * 调用,生产代码零调用);生产路径上的 [markUnusableForProcess] / [release]
     * (60 秒空闲释放等场景)**并不**重置它。但这只是"当前恰好如此",并非任何保证:
     * 一旦它将来被别处重置,[loadHostLibs] 就会再次执行,若无本集合兜底就会重复
     * `System.load` 同一个库。本集合是**进程级事实**,永远不清空。
     */
    private val loadedLibs: MutableSet<String> =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /** 用户是否开启了 NPU 加速。 */
    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NPU_ENABLED, false)
    }

    /**
     * 保存用户 NPU 开关偏好。
     *
     * 无论开或关都会重置初始化标记:用户显式切换即代表"要求重新判定",
     * 旧的 [initAttempted] 结果已过期。若只在关闭时重置,会出现
     * "曾成功启用 → 清缓存 → 重新下载 → 重新开启"时 [tryInitNpu] 被幂等守卫
     * 直接拦掉、NPU 永久起不来的问题。
     *
     * ⚠️ 但**不重置** [unusableForProcess]:那是进程级物理事实(见类头 KDoc),
     * 用户拨动开关并不能让 FastRPC 设备复位。若这里清掉它,用户"关→开"就会
     * 触发一次注定失败的 QNN 建图,把错误日志又刷一遍却仍然回落 CPU。
     * 这时只打一条可检索的说明日志,并在 [getLastError] 上留下原因。
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_NPU_ENABLED, enabled).apply()
        Log.i(TAG, "NPU 加速开关: $enabled")
        // 无论开或关都重置初始化标记:用户显式切换即为"要求重新判定",
        // 旧的 initAttempted 结果已过期。否则会出现"曾成功启用 → 清缓存 → 重新下载 → 重新开启"
        // 时 tryInitNpu 被幂等守卫直接拦掉、NPU 永久起不来的问题。
        initAttempted = false
        npuActive = false
        if (enabled && unusableForProcess) {
            lastInitError = UNUSABLE_MESSAGE
            Log.w(
                TAG,
                "用户重新开启 NPU,但本进程已销毁过 QNN 会话,$UNUSABLE_LOG_SUFFIX" +
                    "(真机证据: apps_dev_init failed for domain 3, errno File exists)。" +
                    "本次仍按 CPU 运行,重启应用后可恢复 NPU。",
            )
        }
    }

    /** 当前 NPU 是否活跃(已初始化成功且未降级)。 */
    fun isNpuActive(): Boolean = npuActive

    /**
     * 本进程内 NPU 是否因"QNN 会话已被销毁"而永久不可再用。
     *
     * 详见类头 KDoc 的真机复现链。为 true 时 [tryInitNpu] 会直接短路返回 false,
     * 不再白跑一遍注定失败的建图;恢复 NPU 的唯一手段是重启进程。
     */
    fun isUnusableForProcess(): Boolean = unusableForProcess

    /**
     * 声明"本进程销毁过一个真正跑在 NPU 上的 QNN 会话",此后 NPU 不可再用。
     *
     * 由 [com.Badnng.moe.ocr.PaddleOcrHelper] 在销毁 NPU 会话前调用(空闲释放、
     * 内存压力释放、识别运行时降级三条路径都走这里)。
     *
     * 之所以必须**在销毁前**显式声明,是因为销毁动作本身不可逆:FastRPC 的 domain 3
     * 设备节点一旦建立就不会被 `domain_deinit` 真正释放,而 QNN 主机库经 `System.load`
     * 后进程内不可卸载,所以"再建一次"必然撞上陈旧的 device fd。
     *
     * 幂等:重复调用只打第一条日志,避免刷屏。
     */
    fun markUnusableForProcess(reason: String) {
        npuActive = false
        if (unusableForProcess) return
        unusableForProcess = true
        lastInitError = UNUSABLE_MESSAGE
        Log.w(
            TAG,
            "NPU 会话已销毁,本进程内 NPU 不可再重建(reason=$reason)。$UNUSABLE_LOG_SUFFIX" +
                "后续识别直接走 CPU,不再尝试 QNN 建图;重启应用可恢复。",
        )
    }

    /** 最近一次初始化失败的错误信息。 */
    fun getLastError(): String? = lastInitError

    /**
     * 尝试初始化 NPU 加速。
     *
     * 这个方法幂等:重复调用不会重复初始化。
     *
     * @param context 任意 Context
     * @param arch 本机 HTP 架构号(由 [NpuSupport.detect] 或 [NpuDeviceQuery.query] 得出)
     * @return true 表示 NPU 可用,false 表示应降级为 CPU
     */
    suspend fun tryInitNpu(context: Context, arch: Int): Boolean = initMutex.withLock {
        // 进程级不可逆事实优先于幂等缓存:一旦销毁过 QNN 会话,再建必然失败
        // (`apps_dev_init failed for domain 3, errno File exists`),直接回落 CPU,
        // 不再浪费一次失败的 strict 建图 + 一次失败的 non-strict 建图。
        if (unusableForProcess) {
            Log.w(TAG, "跳过 NPU 初始化:$UNUSABLE_LOG_SUFFIX(无需重试,重启应用可恢复)")
            return@withLock false
        }
        if (initAttempted) {
            return@withLock npuActive
        }

        initAttempted = true
        lastInitError = null

        try {
            val appContext = context.applicationContext

            // 1) 确保 ADSP_LIBRARY_PATH 已设置
            NpuSupport.ensureAdspLibraryPath(appContext, arch)

            // 2) 检查运行时库是否齐备
            if (!NpuLibLoader.isReady(appContext, arch)) {
                val missing = NpuLibLoader.missingLibs(appContext, arch)
                lastInitError = "QNN 运行时未下载完整,缺少: $missing"
                Log.w(TAG, "NPU 初始化失败: $lastInitError")
                return@withLock false
            }

            // 3) 检查 det 模型文件是否存在
            val detCtx = NpuSupport.detCtxPath(appContext, arch)
            if (detCtx == null) {
                lastInitError = "NPU det 模型文件未找到: models/npu/ctx/$arch/det_ctx.onnx"
                Log.w(TAG, "NPU 初始化失败: $lastInitError")
                return@withLock false
            }

            // 4) 加载 QNN 主机侧库(绝对路径 System.load)
            if (!hostLibsLoaded) {
                loadHostLibs(appContext, arch)
                hostLibsLoaded = true
            }

            // 5) 标记 NPU 可用
            npuActive = true
            Log.i(TAG, "NPU 加速初始化成功, arch=$arch, detCtx=$detCtx")
            true
        } catch (t: Throwable) {
            lastInitError = t.message ?: t.javaClass.simpleName
            Log.e(TAG, "NPU 初始化异常: $lastInitError", t)
            npuActive = false
            false
        }
    }

    /**
     * 标记 NPU 为不可用(识别过程中报错时调用)。
     *
     * 同时重置 [initAttempted],否则 [tryInitNpu] 的幂等守卫会直接返回 false,
     * 导致降级后 NPU **永远不会再被尝试**。
     * 重置后,下次识别会重新走一遍初始化流程(如果用户仍开启 NPU)。
     *
     * ⚠️ 本方法**只**适用于"QNN 会话本身仍然健康、只是这一次推理失败"的场景,
     * 例如某张图触发了算子异常。这类失败下次可能自愈,所以保留重试语义。
     *
     * 若失败源于**会话已被销毁**(空闲释放 / 内存压力释放),调用方必须改用
     * [markUnusableForProcess]:那种情况下重置 [initAttempted] 是有害的 ——
     * FastRPC 设备状态已不可复位,重试注定失败,只会让每次识别都白跑一遍
     * 失败的 strict + non-strict 建图并刷屏错误日志。
     * [com.Badnng.moe.ocr.PaddleOcrHelper] 的降级路径正是按这个口径区分的。
     */
    fun markNpuFailed(error: String?) {
        npuActive = false
        initAttempted = false
        lastInitError = error
        Log.w(TAG, "NPU 识别失败,降级为 CPU(下次识别将重新尝试初始化): $error")
    }

    /**
     * 重置初始化状态(用于测试或强制重试)。
     *
     * 会一并清掉 [unusableForProcess]。**仅供测试**(androidTest 里每个用例都要从
     * 干净状态起步);生产代码不应调用 —— "本进程销毁过 QNN 会话"是物理事实,
     * 清掉标记并不会让 FastRPC 设备复位,只会换来一次注定失败的建图。
     */
    fun reset() {
        initAttempted = false
        npuActive = false
        lastInitError = null
        hostLibsLoaded = false
        unusableForProcess = false
    }

    /**
     * 获取 NPU 感知的 det 模型路径。
     *
     * 如果 NPU 活跃,返回 EPContext 模型路径(det_ctx.onnx);
     * 否则返回默认 CPU 模型路径(原始 det onnx)。
     *
     * @param context 任意 Context
     * @param arch 本机 HTP 架构号
     * @param defaultDetPath 默认 CPU det 模型路径(assets 相对路径)
     */
    fun resolveDetModelPath(context: Context, arch: Int, defaultDetPath: String): String {
        if (!npuActive) return defaultDetPath
        return NpuSupport.detCtxPath(context, arch) ?: defaultDetPath
    }

    /**
     * 获取 ORT QNN EP 的 backend_path。
     *
     * 如果 NPU 活跃,返回外载目录中 libQnnHtp.so 的绝对路径;
     * 否则返回 null(使用默认 CPU 后端)。
     */
    fun getBackendPath(context: Context, arch: Int): String? {
        if (!npuActive) return null
        return NpuLibLoader.backendPath(context.applicationContext, arch)
    }

    /**
     * 释放 NPU 资源。
     *
     * ## ⚠️ 这不是"可重建的软释放",而是"本进程内放弃 NPU"
     *
     * 旧注释写的是"QNN 库本身由系统管理,不需要显式卸载" —— 这句话**只对了一半**:
     * 库确实不需要也无法卸载(见 [loadedLibs]),但真机证明**重建会失败**。
     *
     * 真机 912a4148(SM8475 / HTP v69)同一进程内:
     * 首次识别 `backend=NPU strict=true` 正常,空闲释放后再识别立刻变成
     * `backend=CPU(回退)`,且此后永不恢复,报错为
     * `apps_dev_init failed for domain 3, errno File exists`。
     * 详见类头 KDoc 的完整复现链。
     *
     * 所以本方法会置位 [unusableForProcess]:它表达的是"**本进程已经销毁过 QNN 会话,
     * 别指望再建**"。调用后 [tryInitNpu] 将直接返回 false(不再白跑必然失败的建图),
     * 直到进程重启为止。
     *
     * 生产代码目前**零调用**(仅 [PaddleOcrHelper.release] 这个本身也无人调用的入口会用);
     * 真正的释放路径走 [markUnusableForProcess]。
     */
    fun release() {
        npuActive = false
        initAttempted = false
        markUnusableForProcess(reason = "release")
        Log.i(TAG, "NPU 资源已释放:$UNUSABLE_LOG_SUFFIX(重启应用可恢复)")
    }

    // ------------------------------------------------------------------ 内部实现

    /**
     * 进程级幂等的 `System.load` 包装。
     *
     * 同一个绝对路径在本进程内只会真正加载一次:第二次调用直接跳过,避免
     * Android 抛 `UnsatisfiedLinkError` 或 native 层重复初始化崩溃。
     * 加载失败时会把路径从 [loadedLibs] 移除,允许下次重试。
     *
     * 之所以必须放在这里而不是依赖 [hostLibsLoaded]:后者是实例状态,可能被重置
     * (目前仅 [reset] 会重置它,且 [reset] 只为测试服务;生产路径的 [release] 不重置它),
     * 而 `System.load` 的结果在进程内不可撤销、无法卸载。用与实例状态无关的
     * [loadedLibs] 做进程级去重,才能防御未来对 [hostLibsLoaded] 重置语义的任何改动。
     */
    private fun loadLibOnce(path: String) {
        if (!loadedLibs.add(path)) {
            Log.d(TAG, "库已在进程中加载过,跳过: $path")
            return
        }
        try {
            System.load(path)
            Log.d(TAG, "已加载 $path")
        } catch (t: Throwable) {
            // 加载失败(或已被其它 classloader 加载)则允许下次重试
            loadedLibs.remove(path)
            throw t
        }
    }

    /**
     * 按依赖顺序加载 QNN 主机侧库。
     *
     * 实测必须按顺序:System → Stub → Htp。
     * System 提供 QnnCommon 基础设施;Stub 依赖 libcdsprpc.so(系统库);
     * Htp 是主后端库,依赖前两者。
     *
     * 实际的 `System.load` 走 [loadLibOnce],保证即使本方法被重复调用
     * (调用方 [tryInitNpu] 只在 [hostLibsLoaded] 为 false 时才进入本方法;而该标记
     * 在 [reset] 后会被清掉、未来也可能被其它路径重置)也不会重复加载同一个库 ——
     * `System.load` 的结果在进程内不可撤销,重复加载无法撤销、只能靠加载前拦截。
     */
    private fun loadHostLibs(context: Context, arch: Int) {
        val dir = NpuLibLoader.externalDir(context)

        // 1) libQnnSystem.so —— 基础设施
        val systemLib = File(dir, "libQnnSystem.so")
        if (systemLib.isFile) {
            loadLibOnce(systemLib.absolutePath)
        } else {
            throw IllegalStateException("缺少 ${systemLib.name}")
        }

        // 2) libQnnHtpV{arch}Stub.so —— 主机侧转发库,依赖 libcdsprpc.so
        val stubLib = File(dir, "libQnnHtpV${arch}Stub.so")
        if (stubLib.isFile) {
            loadLibOnce(stubLib.absolutePath)
        } else {
            throw IllegalStateException("缺少 ${stubLib.name}")
        }

        // 3) libQnnHtp.so —— 主后端库
        val htpLib = File(dir, "libQnnHtp.so")
        if (htpLib.isFile) {
            loadLibOnce(htpLib.absolutePath)
        } else {
            throw IllegalStateException("缺少 ${htpLib.name}")
        }

        // 4) 设置可执行权限(以防万一)
        NpuLibLoader.makeExecutable(systemLib)
        NpuLibLoader.makeExecutable(stubLib)
        NpuLibLoader.makeExecutable(htpLib)
    }
}
