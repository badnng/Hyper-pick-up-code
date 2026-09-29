package com.Badnng.moe.npu

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import java.io.File
import java.util.Locale

/**
 * NPU 可用性状态码。UI 据此决定开关是否可点,以及用什么文案解释原因。
 */
enum class NpuStatusCode {
    /** 可以启用 NPU。 */
    AVAILABLE,

    /** 不是高通骁龙平台。 */
    NOT_QUALCOMM,

    /** 是高通平台,但不在骁龙 8 系移动平台白名单内。 */
    SOC_NOT_WHITELISTED,

    /** 无法识别 SoC 型号(ROM 屏蔽了型号信息)。 */
    SOC_UNKNOWN,

    /** 设备 ABI 不含 arm64-v8a,QNN 运行时无法加载。 */
    ABI_UNSUPPORTED,

    /** QNN 运行时未下载到应用私有目录。 */
    RUNTIME_NOT_DOWNLOADED,

    /** 缺少本机所需 HTP 架构的运行时库。 */
    HTP_ARCH_LIB_MISSING,

    /** 缺少 det 模型文件(det_ctx.onnx)。 */
    MODEL_NOT_FOUND,
}

/**
 * 一次 NPU 能力探测的结果。
 *
 * @param status      机器可读状态
 * @param message     面向用户的中文提示
 * @param detail      诊断细节(SoC 型号、架构、缺失的文件等),用于日志和开发者面板
 * @param socModel    识别到的 SoC 型号,例如 SM8650
 * @param socName     对应的市场名称,例如「骁龙 8 Gen 3」
 * @param htpArch     需要的 Hexagon HTP 架构号,例如 75
 */
data class NpuCapability(
    val status: NpuStatusCode,
    val message: String,
    val detail: String,
    val socModel: String?,
    val socName: String?,
    val htpArch: Int?,
) {
    val supported: Boolean get() = status == NpuStatusCode.AVAILABLE

    companion object {
        internal fun of(
            status: NpuStatusCode,
            message: String,
            detail: String = "",
            socModel: String? = null,
            socName: String? = null,
            htpArch: Int? = null,
        ) = NpuCapability(status, message, detail, socModel, socName, htpArch)
    }
}

/**
 * 骁龙 8 系移动平台 → Hexagon HTP 架构的白名单条目。
 *
 * HTP 架构号必须与 qnn-runtime 实际提供的 `libQnnHtpV{arch}Stub.so` 对应,
 * 否则设备上无法启动 HTP 后端。
 */
internal data class SnapdragonEntry(
    /** SoC 型号,匹配时忽略大小写与后缀(如 -AB / P)。 */
    val socModels: List<String>,
    /** `ro.board.platform` 平台代号。 */
    val platformCodenames: List<String>,
    /** 市场名称,仅用于提示文案。 */
    val marketingName: String,
    /** Hexagon HTP 架构号。 */
    val htpArch: Int,
)

/**
 * 高通 Hexagon NPU(QNN HTP 后端)可用性探测。
 *
 * 设计原则:**任何一项不满足就明确拒绝**,不允许"看起来能开但实际跑不起来"的状态。
 * 依次检查:平台厂商 → SoC 白名单 → ABI → QNN 运行时是否已下载 → 本机 HTP 架构库是否存在
 * → det 模型文件是否存在。
 *
 * 白名单只覆盖骁龙 8 系移动平台(865/870/888/8 Gen 1/8+ Gen 1/8 Gen 2/8 Gen 3/
 * 8s Gen 3/8 Elite/8s Gen 4/8 Elite Gen 5)。7 系、6 系、4 系、XR、车机、X Elite 等
 * 一律视为不支持,即使它们同样带有 Hexagon NPU。
 */
object NpuSupport {

    /** 白名单表。注意同一 HTP 架构会对应多个 SoC 型号。 */
    private val WHITELIST: List<SnapdragonEntry> = listOf(
        SnapdragonEntry(
            socModels = listOf("SM8250", "SM8250-AB", "SM8250-AC"),
            platformCodenames = listOf("kona"),
            marketingName = "骁龙 865 / 865+ / 870",
            htpArch = 68,
        ),
        SnapdragonEntry(
            socModels = listOf("SM8350", "SM8350-AB", "SM8350-AC"),
            platformCodenames = listOf("lahaina"),
            marketingName = "骁龙 888 / 888+",
            htpArch = 68,
        ),
        SnapdragonEntry(
            socModels = listOf("SM8450"),
            platformCodenames = listOf("taro"),
            marketingName = "骁龙 8 Gen 1",
            htpArch = 69,
        ),
        SnapdragonEntry(
            socModels = listOf("SM8475", "SM8475-AB"),
            platformCodenames = listOf("cape"),
            marketingName = "骁龙 8+ Gen 1",
            htpArch = 69,
        ),
        SnapdragonEntry(
            socModels = listOf("SM8550", "SM8550-AB", "SM8550-AC"),
            platformCodenames = listOf("kalama"),
            marketingName = "骁龙 8 Gen 2",
            htpArch = 73,
        ),
        SnapdragonEntry(
            socModels = listOf("SM8650", "SM8650-AB", "SM8650-AC"),
            platformCodenames = listOf("pineapple"),
            marketingName = "骁龙 8 Gen 3",
            htpArch = 75,
        ),
        SnapdragonEntry(
            socModels = listOf("SM8635"),
            platformCodenames = emptyList(),
            marketingName = "骁龙 8s Gen 3",
            htpArch = 75,
        ),
        SnapdragonEntry(
            socModels = listOf("SM8750", "SM8750-AB", "SM8750-3-AB"),
            platformCodenames = listOf("sun", "sunrise"),
            marketingName = "骁龙 8 Elite",
            htpArch = 79,
        ),
        SnapdragonEntry(
            socModels = listOf("SM8735"),
            platformCodenames = emptyList(),
            marketingName = "骁龙 8s Gen 4",
            htpArch = 79,
        ),
        SnapdragonEntry(
            socModels = listOf("SM8850", "SM8850-AB", "SM8845"),
            platformCodenames = listOf("sunrise2", "canoe"),
            marketingName = "骁龙 8 Elite Gen 5",
            htpArch = 81,
        ),
    )

    private const val TAG = "NpuSupport"

    /** NPU 模型资源所在目录(相对于 filesDir)。 */
    const val NPU_MODEL_DIR = "models/npu"

    /**
     * [NPU_MODEL_DIR] 的别名,取值同为 `"models/npu"`。
     *
     * 模型既可能被**下载**到 `filesDir/<同路径>`,也可能**内置**在 assets,
     * [resolveModelPath] 用同一套路径约定解析两者,所以仓库里不存在
     * "assets 版路径"与"filesDir 版路径"的区别。保留这个名字只为与
     * `com.paddle.ocr.engine.ORTSessionManager` 的调用口径一致。
     */
    const val NPU_MODEL_ASSET_DIR = NPU_MODEL_DIR

    /** det 的 EPContext 模型文件名(固定 shape,已离线编译)。 */
    const val DET_CTX_NAME = "det_ctx.onnx"

    /**
     * 把 `ADSP_LIBRARY_PATH` 指向**Skel 所在的那个目录** —— **越早调用越好**。
     *
     * 解析顺序:**外载目录(`filesDir/npu_libs/<abi>/`)优先,回落 `nativeLibraryDir`**。
     * 前者有 Skel 说明走的是"so 外置"方案(见 [NpuLibLoader]),后者是传统的内置方案。
     *
     * ## 为什么必须这么做(真机实测得出的结论)
     *
     * QNN 的 DSP 侧算子库 `libQnnHtpV{arch}Skel.so` 由 **DSP 进程(cdsprpcd)按文件路径**
     * 打开,它读不到 APK 内部,也不走 `dlopen` 的搜索路径。而不少设备
     * (实测小米 houji / SM8650)在系统里**预设**了:
     *
     * ```
     * ADSP_LIBRARY_PATH=/odm/lib/rfsa/adsp;/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp;...
     * ```
     *
     * ORT 检测到该变量已存在就会**沿用它**,于是 DSP 加载的是**系统自带那份** Skel,
     * 其版本与 APK 内的 host 侧 `qnn-runtime 2.50` 不一致,导致:
     *
     * ```
     * QNN SetupBackend failed  Failed to create device.
     * Error: QNN_DEVICE_ERROR_INVALID_CONFIG: Invalid config values
     * ```
     *
     * 后果非常隐蔽:ORT 不会让建会话失败,只是把算子悄悄分回 CPU,
     * 于是界面显示 `backend=NPU` 而实际跑在 CPU 上。
     *
     * 真机对照(骁龙 8 Gen 3):
     *
     * | ADSP_LIBRARY_PATH | 结果 |
     * |---|---|
     * | 系统预设值(不设置) | 回落 CPU |
     * | 指向应用私有目录(`filesDir/npu_libs/arm64-v8a`,so 外置方案) | **整图接管 HTP,det 74ms** |
     *
     * ## 时机很关键
     *
     * FastRPC 在**首次连接 DSP 时**读取该变量并缓存。若此前已有任何代码触发过
     * DSP 连接,后续再设就无效 —— 所以应当在 `Application.onCreate()` 里
     * 最先调用本方法,而不是等到建 ORT 会话时才设。
     *
     * @param arch 本机 HTP 架构号;为 null 时按目录内容推断(外置目录里任意 Skel 即可)。
     * @return 最终生效的值;失败时返回原值(不抛异常,失败不致命但 NPU 可能不可用)
     */
    fun ensureAdspLibraryPath(context: Context, arch: Int? = null): String {
        // 1) so 外置方案:优先指向应用私有目录里那份 Skel
        val external = NpuLibLoader.skelSearchDir(context, arch)
        val expanded = context.applicationContext.applicationInfo.nativeLibraryDir ?: ""
        val target = external ?: expanded

        if (target.isBlank()) return System.getenv("ADSP_LIBRARY_PATH").orEmpty()
        val current = System.getenv("ADSP_LIBRARY_PATH").orEmpty()
        // 已经正好是目标目录就不必重设
        if (current == target) return current
        // ⚠️ **只设目标目录,不要把系统路径拼进来。**
        // 实测(骁龙 8 Gen 3)拼接系统路径后 QNN 仍会去系统目录找到不匹配的 Skel 而失败;
        // 单独指向目标目录才能稳定接管(见类文档里的对照表)。
        return try {
            Os.setenv("ADSP_LIBRARY_PATH", target, true)
            Log.i(
                TAG,
                "ADSP_LIBRARY_PATH -> $target" +
                    "(${if (external != null) "外载目录" else "nativeLibraryDir"};" +
                    "原值:${current.ifBlank { "<空>" }})",
            )
            target
        } catch (t: Throwable) {
            Log.w(TAG, "设置 ADSP_LIBRARY_PATH 失败,NPU 可能回落到系统 Skel", t)
            current
        }
    }

    /**
     * 把"逻辑路径"解析成实际可读路径,支持**按需下载**:
     *
     * 先在应用私有目录里找同名文件(下载下来的模型放这里,例如
     * `filesDir/models/npu/det_ctx.onnx`),找不到再回落到 assets。
     * 返回 null 表示两处都没有。
     *
     * 于是"内置"与"下载"用的是同一套路径约定,切换不需要改代码。
     */
    fun resolveModelPath(context: Context, filePath: String): String? {
        val downloaded = File(context.applicationContext.filesDir, filePath)
        if (downloaded.isFile && downloaded.length() > 0) return downloaded.absolutePath
        return if (assetExists(context, filePath)) filePath else null
    }

    /**
     * det 的 EPContext 模型文件路径(私有目录优先,assets 兜底)。
     */
    fun detCtxPath(context: Context, arch: Int): String? =
        resolveModelPath(context, "$NPU_MODEL_DIR/ctx/$arch/$DET_CTX_NAME")

    /**
     * **模型指纹** = 模型字节的 `sha256` 前 6 字节(12 位小写十六进制)。
     *
     * 解析顺序与 [resolveModelPath] 一致:**先 filesDir、后 assets**。
     * 找不到模型时返回 null(调用方应视为"无指纹")。
     *
     * @return 12 位小写 hex;读取或摘要失败返回 null(不抛异常)
     */
    fun modelFingerprint(context: Context, assetPath: String): String? = runCatching {
        val app = context.applicationContext
        val downloaded = File(app.filesDir, assetPath)
        val bytes = if (downloaded.isFile) {
            downloaded.readBytes()
        } else {
            app.assets.open(assetPath).use { it.readBytes() }
        }
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.digest(bytes).take(6).joinToString("") { "%02x".format(it) }
    }.getOrNull()

    /**
     * QNN context binary 的**端侧**缓存目录,默认 `filesDir/npu_cache`。
     *
     * 仓库默认走 ctx-only(直接加载分发下来的 `det_ctx.onnx`),正常不会往这里写东西;
     * 保留本函数是为了让 [com.paddle.ocr.engine.ORTSessionManager] 在
     * "模型本身不是 EPContext" 的兜底路径上仍有落盘位置。
     */
    fun contextCacheDir(context: Context, dirName: String = "npu_cache"): File =
        File(context.applicationContext.filesDir, dirName)

    /**
     * **已安装的预编译 ctx** 的实际路径(私有目录优先,assets 兜底)。
     *
     * ## 仓库走 ctx-only,文件名固定,不带指纹
     *
     * 与 demo 不同,澎湃记**不做端侧编译**(不打包 `libQnnHtpPrepare.so`,省 79.81 MB),
     * 分发下来的 ctx 就是最终产物、也就是"模型"本身,文件名固定为
     * `<model>_ctx.onnx`,由 [resolveModelPath] 统一解析:
     *
     * - 下载到 `filesDir/models/npu/ctx/<arch>/<model>_ctx.onnx` → 返回**绝对路径**
     * - 或内置在 `assets/models/npu/ctx/<arch>/<model>_ctx.onnx` → 返回**相对 assets 路径**
     * - 两处都没有 → 返回 null
     *
     * 因此 `model = "det"` 命中的就是 [detCtxPath];`model = "rec"` 目前没有对应文件,
     * 返回 null(rec 固定在 CPU,不需要 NPU 模型)。
     *
     * 注意:**这里绝不能让调用方再去触发端侧编译** —— 加载 ctx 时若带着已存在的
     * 同名文件走 `ep.context_enable=1`,ORT 会报
     * `Failed to generate EP context model since the file already exists`。
     *
     * @param arch   本机 HTP 架构号(v75 的 ctx 不能用在 v73 上,所以路径里必须带 arch)
     * @param model  模型标签,目前只有 `"det"`
     * @param shapeKey 模型指纹。**ctx-only 下不参与路径拼接**(没有端侧编译就没有指纹名),
     *        保留该参数只为与调用方签名兼容。
     */
    @Suppress("UNUSED_PARAMETER")
    fun installedCtxPath(context: Context, arch: Int, model: String, shapeKey: String? = null): String? =
        resolveModelPath(context, "$NPU_MODEL_DIR/ctx/$arch/${model}_ctx.onnx")

    /**
     * 探测本机 + 本包能否启用 NPU。
     *
     * @param context 任意 Context
     * @param requireModel 是否要求 det 模型文件已存在。
     */
    fun detect(context: Context, requireModel: Boolean = true): NpuCapability {
        val socModel = readSocModel()
        val platform = readPlatformCodename()
        val hardware = (Build.HARDWARE ?: "").lowercase(Locale.ROOT)
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase(Locale.ROOT)
        val board = (Build.BOARD ?: "").lowercase(Locale.ROOT)

        // 1) 是否高通平台
        val qualcommSignals = listOf(manufacturer, hardware, board, platform ?: "")
            .any { it.contains("qcom") || it.contains("qualcomm") }
        if (!qualcommSignals) {
            return NpuCapability.of(
                status = NpuStatusCode.NOT_QUALCOMM,
                message = "本机不是高通骁龙平台,NPU 加速不可用",
                detail = "manufacturer=$manufacturer hardware=$hardware board=$board platform=$platform",
            )
        }

        // 2) 白名单匹配
        val matched = matchWhitelist(socModel, platform)
        if (matched == null) {
            val unknown = socModel.isNullOrBlank() && platform.isNullOrBlank()
            return NpuCapability.of(
                status = if (unknown) NpuStatusCode.SOC_UNKNOWN else NpuStatusCode.SOC_NOT_WHITELISTED,
                message = if (unknown) {
                    "无法识别 SoC 型号,为安全起见不启用 NPU"
                } else {
                    "当前 SoC(${socModel ?: platform})不在骁龙 8 系支持白名单内,NPU 加速不可用"
                },
                detail = "socModel=$socModel platform=$platform hardware=$hardware",
                socModel = socModel,
            )
        }

        val arch = matched.htpArch
        val socName = matched.marketingName

        // 3) ABI 必须是 64 位 arm
        val abis = Build.SUPPORTED_ABIS?.toList().orEmpty()
        if (!abis.contains("arm64-v8a")) {
            return NpuCapability.of(
                status = NpuStatusCode.ABI_UNSUPPORTED,
                message = "设备 ABI 不含 arm64-v8a,QNN 运行时无法加载",
                detail = "supportedAbis=${abis.joinToString(",")}",
                socModel = socModel,
                socName = socName,
                htpArch = arch,
            )
        }

        // 4) QNN 运行时是否已下载到私有目录
        val externalReady = NpuLibLoader.isReady(context, arch)
        if (!externalReady) {
            val missing = NpuLibLoader.missingLibs(context, arch)
            return NpuCapability.of(
                status = NpuStatusCode.RUNTIME_NOT_DOWNLOADED,
                message = "QNN 运行时未下载到应用目录,需要下载后才能启用 NPU",
                detail = "缺少=${missing}; " +
                    "外载目录=${NpuLibLoader.externalDir(context).absolutePath} " +
                    "现有=${NpuLibLoader.presentLibs(context)}",
                socModel = socModel,
                socName = socName,
                htpArch = arch,
            )
        }

        // 5) 本机所需 HTP 架构的运行时库是否存在
        val stubName = "libQnnHtpV${arch}Stub.so"
        val haveStub = File(NpuLibLoader.externalDir(context), stubName).isFile
        if (!haveStub) {
            return NpuCapability.of(
                status = NpuStatusCode.HTP_ARCH_LIB_MISSING,
                message = "缺少骁龙 $socName 所需的 HTP v$arch 运行时库($stubName)",
                detail = "本机需要 v$arch, " +
                    "外载目录现有=${NpuLibLoader.presentLibs(context)}",
                socModel = socModel,
                socName = socName,
                htpArch = arch,
            )
        }

        // 6) det 模型文件(EPContext)是否存在
        if (requireModel) {
            val hasDet = detCtxPath(context, arch) != null
            if (!hasDet) {
                return NpuCapability.of(
                    status = NpuStatusCode.MODEL_NOT_FOUND,
                    message = "缺少 NPU det 模型文件(det_ctx.onnx)",
                    detail = "请先下载 det_ctx.onnx 到 filesDir/$NPU_MODEL_DIR/ctx/$arch/",
                    socModel = socModel,
                    socName = socName,
                    htpArch = arch,
                )
            }
        }

        return NpuCapability.of(
            status = NpuStatusCode.AVAILABLE,
            message = "$socName(Hexagon HTP v$arch)可用",
            detail = "soc=$socModel arch=$arch",
            socModel = socModel,
            socName = socName,
            htpArch = arch,
        )
    }

    /**
     * 由 SoC 型号 / 平台代号反查 HTP 架构(**白名单口径**)。
     *
     * 供 [NpuDeviceQuery] 在"设备直查失败"时兜底,也是 [resolveHtpArch] 的第一来源。
     * 表外机型返回 null。
     */
    fun htpArchForSoc(socModel: String?, platform: String?): Int? =
        matchWhitelist(socModel, platform)?.htpArch

    /**
     * 解析本机 HTP 架构 —— **全局唯一入口**。
     *
     * 优先白名单(与 [detect] 的结论、以及 QNN provider 的 `htp_arch` 选项同源),
     * 只有白名单查不到时才回落到设备自报(表外机型/诊断场景)。
     *
     * ⚠️ 绝不要直接用 [NpuDeviceQuery.query] 的 htpArch 去决定下载哪个架构的库:
     * 真机实测(SM8475)设备里可能同时存在多套 Skel(v68 遗留 + v69 本体),
     * 目录列举顺序会让结论随机化,与 [detect] 的白名单结论不一致 ——
     * 表现为"文件已下载却永远提示未下载"的死循环。
     *
     * @param deviceInfo 已经查过的设备信息(可选)。传入可复用已有证据,并让
     *        "白名单与设备自报不一致"的诊断日志一并打出;传 null 时只在确实需要
     *        回落到设备自报时才真的去查一次。
     * @return 本机 HTP 架构号;白名单与设备自报都给出不了一致结论时返回 null
     */
    fun resolveHtpArch(deviceInfo: NpuDeviceQuery.Info? = null): Int? {
        val whitelist = matchWhitelist(readSocModel(), readPlatformCodename())?.htpArch
        if (whitelist != null) {
            // 白名单命中,但设备里找不到该架构的 Skel:多半是 ROM 只留了遗留库,
            // 或证据被 SELinux 挡住。**仍以白名单为准**(与 detect() 同源),
            // 只补一条可检索的日志,便于真机排查。
            val deviceArches = deviceInfo
                ?.archEvidence
                ?.filter { it.authoritative }
                ?.map { it.arch }
                ?.distinct()
                .orEmpty()
            if (deviceArches.isNotEmpty() && whitelist !in deviceArches) {
                val evidenceText = deviceInfo
                    ?.archEvidence
                    ?.joinToString { "v${it.arch}[${it.kind}]" }
                    .orEmpty()
                Log.w(
                    TAG,
                    "架构证据不一致:白名单 v$whitelist,设备自报 $deviceArches —— " +
                        "以白名单为准;证据=$evidenceText",
                )
            }
            return whitelist
        }
        // 表外机型:只能信设备自报。存在多个权威架构时拒绝猜(singleOrNull),
        // 让调用方拿到 null 而不是一个随目录顺序漂移的答案。
        return (deviceInfo ?: NpuDeviceQuery.query())
            .archEvidence
            .filter { it.authoritative }
            .map { it.arch }
            .distinct()
            .singleOrNull()
    }

    // ------------------------------------------------------------------ 内部实现

    private fun matchWhitelist(socModel: String?, platform: String?): SnapdragonEntry? {
        val normalizedSoc = socModel?.uppercase(Locale.ROOT)?.trim()
        if (!normalizedSoc.isNullOrEmpty()) {
            // 去掉厂商后缀后按前缀匹配,兼容 SM8650P / SM8650-AB / SM8650X 这类变体
            WHITELIST.firstOrNull { entry ->
                entry.socModels.any { candidate ->
                    normalizedSoc == candidate || normalizedSoc.startsWith("$candidate-")
                }
            }?.let { return it }
        }
        val normalizedPlatform = platform?.lowercase(Locale.ROOT)?.trim()
        if (!normalizedPlatform.isNullOrEmpty()) {
            WHITELIST.firstOrNull { entry ->
                entry.platformCodenames.any { it == normalizedPlatform }
            }?.let { return it }
        }
        return null
    }

    /**
     * 读取 SoC 型号。来源按可靠性排序:
     * Build.SOC_MODEL(API 31+) → 系统属性 ro.soc.model → /proc/cpuinfo 的 Hardware 行。
     */
    private fun readSocModel(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { Build.SOC_MODEL }.getOrNull()
                ?.takeIf { it.isNotBlank() && it != "unknown" }
                ?.let { return it }
        }
        readSystemProperty("ro.soc.model")?.let { return it }
        readSystemProperty("ro.chipname")?.let { return it }
        readSystemProperty("ro.board.platform")?.let {
            if (it.startsWith("sm", ignoreCase = true) || it.startsWith("msm", ignoreCase = true)) return it
        }
        return readCpuInfoHardware()
    }

    /** 读取平台代号,例如 kalama / pineapple / taro。 */
    private fun readPlatformCodename(): String? =
        readSystemProperty("ro.board.platform")
            ?: readSystemProperty("ro.hardware")
            ?: readSystemProperty("ro.product.board")

    /**
     * 反射读取系统属性。这是隐藏 API,部分 ROM 会拦截,失败时静默返回 null,
     * 因此它只作为 Build.* 之外的补充来源。
     */
    private fun readSystemProperty(key: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        (get.invoke(null, key) as? String)?.trim()?.takeIf { it.isNotEmpty() && it != "unknown" }
    }.getOrNull()

    private fun readCpuInfoHardware(): String? = runCatching {
        val proc = File("/proc/cpuinfo")
        if (!proc.canRead()) return@runCatching null
        proc.readLines()
            .firstOrNull { it.startsWith("Hardware", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun assetExists(context: Context, path: String): Boolean = runCatching {
        context.assets.open(path).use { true }
    }.getOrDefault(false)
}
