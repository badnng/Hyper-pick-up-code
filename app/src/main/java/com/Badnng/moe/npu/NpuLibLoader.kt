package com.Badnng.moe.npu

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * QNN 运行时 `.so` 的外载(应用私有目录)装载器。
 *
 * ## 这是什么
 *
 * 把 QNN 的 4 个必需库从下载源挪到 `filesDir/npu_libs/<abi>/`,
 * 在运行时按绝对路径加载。这样 APK 里可以不带任何 Qnn 库(省 ~21 MiB raw /
 * ~6 MiB 压缩),代价是首次使用前需要把这些文件准备好(下载)。
 *
 * ## 真机实测结论(小米 houji / SM8650 / HTP v75 / targetSdk 35)
 *
 * | 环节 | 结果 | 证据 |
 * |---|---|---|
 * | 主机侧库 `System.load(绝对路径)` | ✅ 成功 | `dlopen failed` 未出现;`/proc/self/maps` 出现 `files/npu_libs/.../libQnnHtp.so` |
 * | ORT `backend_path` 传绝对路径 | ✅ 接受 | 严格模式下 `backend=NPU strict=true` |
 * | **Skel 由 DSP 从私有目录加载** | ✅ 成功 | `adsprpc: Successfully opened file .../npu_libs/arm64-v8a/./libQnnHtpV75Skel.so` |
 * | 对照:删掉 Skel | ✅ 必然失败 | `QNN_DEVICE_ERROR_INVALID_CONFIG`(证明因果,不是巧合) |
 *
 * ## 三个必须同时满足的条件(缺一不可,实测踩过)
 *
 * 1. **`ADSP_LIBRARY_PATH` 必须指向存放 Skel 的那个目录**。它只在进程内首次
 *    FastRPC 连接时被读取,所以要在任何 QNN/DSP 动作之前设置。
 * 2. **`backend_path` 走绝对路径**(`<dir>/libQnnHtp.so`)。传裸文件名
 *    `libQnnHtp.so` 时链接器会在 `nativeLibraryDir` 里找,外载那份不会被用到。
 * 3. **清单里必须有 `<uses-native-library android:name="libcdsprpc.so"/>`**。
 *    `libQnnHtpV{arch}Stub.so` 通过 DT_NEEDED 依赖厂商库 `libcdsprpc.so`;
 *    targetSdk >= 31 的应用若不显式声明,即使库在 `/vendor/etc/public.libraries.txt`
 *    里也解析不到,`dlopen` 会报
 *    `library "libcdsprpc.so" not found ... in namespace classloader-namespace`。
 *
 * ## 目录布局
 *
 * ```
 * filesDir/npu_libs/arm64-v8a/
 *     libQnnHtp.so
 *     libQnnSystem.so
 *     libQnnHtpV75Stub.so
 *     libQnnHtpV75Skel.so
 * ```
 *
 * 与 [NpuSupport.resolveModelPath] 的"先私有目录、后 assets"约定保持一致。
 */
object NpuLibLoader {

    private const val TAG = "NpuLibLoader"

    /** 外载根目录名(相对于 filesDir)。 */
    const val EXT_ROOT = "npu_libs"

    /**
     * 通用主机侧库 `libQnnHtp.so` 的字节数(所有 HTP 架构共用)。
     *
     * 与 [ARCH_FILE_SIZES] 一起供 [isReady] / [missingLibs] 做长度校验 —— 字节数是当前
     * **唯一可用**的完整性 / 版本判据(不引入 SHA-256、不新增任何依赖)。
     */
    const val HTP_LIB_SIZE = 3_978_976L

    /** 通用主机侧库 `libQnnSystem.so` 的字节数(所有 HTP 架构共用)。 */
    const val SYSTEM_LIB_SIZE = 4_068_024L

    /** 通用主机侧库名。 */
    private const val HTP_LIB_NAME = "libQnnHtp.so"
    private const val SYSTEM_LIB_NAME = "libQnnSystem.so"

    /** 所有 HTP 架构都可能用到的库。 */
    fun hostLibs(arch: Int): List<String> = listOf(
        SYSTEM_LIB_NAME,
        stubName(arch),
        HTP_LIB_NAME,
    )

    /** 主机侧 Stub 库名(DT_NEEDED 依赖厂商库 `libcdsprpc.so`)。 */
    private fun stubName(arch: Int): String = "libQnnHtpV${arch}Stub.so"

    /** DSP 侧算子库名。 */
    fun skelName(arch: Int): String = "libQnnHtpV${arch}Skel.so"

    /** 运行时所在的 ABI 目录名。QNN 只提供 arm64-v8a。 */
    fun abiDir(): String =
        Build.SUPPORTED_ABIS?.firstOrNull { it == "arm64-v8a" } ?: "arm64-v8a"

    /** 外载目录:`filesDir/npu_libs/<abi>/`。 */
    fun externalDir(context: Context): File =
        File(File(context.applicationContext.filesDir, EXT_ROOT), abiDir())

    /**
     * 某个库文件的**期望字节数**;未知(表里没有该架构 / 不是受管的库名)返回 null。
     *
     * 覆盖 [hostLibs] + [skelName] 返回的 4 个库:
     * - `libQnnHtp.so` -> [HTP_LIB_SIZE]
     * - `libQnnSystem.so` -> [SYSTEM_LIB_SIZE]
     * - `libQnnHtpV{arch}Stub.so` -> [ARCH_FILE_SIZES] 的 `stub`
     * - `libQnnHtpV{arch}Skel.so` -> [ARCH_FILE_SIZES] 的 `skel`
     *
     * 返回 null 表示**不做长度校验**(只退回"存在且长度 > 0"),以此保持对所有未知
     * 新架构的向后兼容 —— 宁可少校验,也不能让未来的 V85 因为表里没有而被判成"不可用"。
     *
     * @param name 库文件名(不含目录),如 `libQnnHtpV75Skel.so`
     * @param arch HTP 架构号
     */
    fun expectedSizeOf(name: String, arch: Int): Long? = when (name) {
        HTP_LIB_NAME -> HTP_LIB_SIZE
        SYSTEM_LIB_NAME -> SYSTEM_LIB_SIZE
        stubName(arch) -> archFileSizes(arch)?.stub
        skelName(arch) -> archFileSizes(arch)?.skel
        else -> null
    }

    /**
     * 判断外载目录里的运行时库是否齐备 —— **存在、非空、且长度与已知字节数一致**。
     *
     * ## 为什么必须校验大小(而不是只看"存在且非空")
     *
     * 磁盘上完全可能留着**旧版本**(例如上一版 QAIRT 2.42 的 `.so`)或**被截断 /
     * 损坏但长度非零**的文件。只判 `isFile && length() > 0` 的话:
     *
     * 1. [isReady] 返回 true -> `NpuSupport.detect()` 报 `AVAILABLE` -> 设置页**不再提示下载**;
     * 2. `tryInitNpu` 拿这份陈旧/损坏的库去初始化 -> QNN 报
     *    `Failed to create context from binary. Error code: 5000`;
     * 3. 上层捕获后**静默回落 CPU**。
     *
     * 结果是"用户开着 NPU、UI 显示 NPU 可用、实际全程跑在 CPU 上",且没有任何提示 ——
     * 属于最难排查的一类故障。文件长度是当前**唯一可用**的完整性 / 版本判据
     * (不引入 SHA-256、不新增任何依赖),所以必须纳入就绪判定。
     * [missingLibs] 同步把"大小不符"也算作缺失,让 UI 能提示用户重新下载。
     *
     * @param arch HTP 架构号
     * @return true 表示 4 个库全部存在、非空,且长度符合期望(无期望值的文件只判存在且非空)
     */
    fun isReady(context: Context, arch: Int): Boolean {
        val dir = externalDir(context)
        if (!dir.isDirectory) return false
        val names = hostLibs(arch) + skelName(arch)
        return names.all { libOk(File(dir, it), it, arch) }
    }

    /**
     * 外载目录里**缺少或不完整**哪些库(诊断用)。
     *
     * 除了"文件不存在",**存在但大小与期望值不符**的文件名也会被列出 ——
     * 这类文件同样不可用(陈旧版本 / 截断损坏),必须让 UI 提示用户重新下载。
     */
    fun missingLibs(context: Context, arch: Int): List<String> {
        val dir = externalDir(context)
        val names = hostLibs(arch) + skelName(arch)
        return names.filterNot { libOk(File(dir, it), it, arch) }
    }

    /**
     * 单个库是否可用:存在、非空,且(无期望字节数 或 长度 == 期望字节数)。
     *
     * 未知架构 / 未知库名的期望值为 null,只做"存在且非空"判定(向后兼容)。
     */
    private fun libOk(file: File, name: String, arch: Int): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val expected = expectedSizeOf(name, arch) ?: return true
        return file.length() == expected
    }

    /** 外载目录里实际存在的库名(便于诊断)。 */
    fun presentLibs(context: Context): List<String> =
        externalDir(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".so") }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()

    /**
     * 主机侧库的 `backend_path` 值。
     *
     * 外载就绪时返回**绝对路径**(必须 —— 传裸 soname 只会命中 `nativeLibraryDir`),
     * 否则回落到裸文件名,由 Android 链接器在应用库目录内解析。
     */
    fun backendPath(context: Context, arch: Int): String {
        val ext = File(externalDir(context), "libQnnHtp.so")
        return if (ext.isFile && ext.length() > 0) ext.absolutePath else "libQnnHtp.so"
    }

    /**
     * `ADSP_LIBRARY_PATH` 应该指向哪个目录 —— 也就是 **Skel 在哪**。
     *
     * 返回 null 表示外载目录里没有 Skel,调用方应回落到 `nativeLibraryDir`。
     */
    fun skelSearchDir(context: Context, arch: Int?): String? {
        val dir = externalDir(context)
        if (!dir.isDirectory) return null
        val hit = if (arch != null) {
            File(dir, skelName(arch)).isFile
        } else {
            // 架构未知(如 Application.onCreate 早期):只要目录里有任意 Skel 就先认它
            dir.listFiles()?.any { it.isFile && it.name.endsWith("Skel.so") } == true
        }
        return if (hit) dir.absolutePath else null
    }

    /** 删除外载目录(用于"释放空间"或强制重新下载)。 */
    fun clear(context: Context): Boolean {
        val dir = File(context.applicationContext.filesDir, EXT_ROOT)
        return if (dir.isDirectory) dir.deleteRecursively() else true
    }

    /** 外载目录占用字节数。 */
    fun usedBytes(context: Context): Long {
        val dir = File(context.applicationContext.filesDir, EXT_ROOT)
        return if (dir.isDirectory) {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else 0L
    }

    /** `.so` 必须可执行才能被 `dlopen`。 */
    internal fun makeExecutable(f: File) {
        runCatching { f.setReadable(true, false); f.setExecutable(true, false) }
    }

    private fun assetExists(context: Context, path: String): Boolean = runCatching {
        context.assets.open(path).use { true }
    }.getOrDefault(false)

    // ------------------------------------------------------------------ 架构文件大小表

    /** 架构专属文件的大小(字节)。 */
    internal data class ArchFileSizes(
        val stub: Long,
        val skel: Long,
        val detCtx: Long,
    )

    /**
     * HTP 架构 -> 架构专属文件大小表(字节)。
     *
     * `Stub.so` / `Skel.so` / `det_ctx.onnx` 的字节数随架构不同而不同。
     * 已与仓库 `qnn-runtime/` 下的真实文件逐一实测核对过。
     *
     * 两种用途,同一份数值,避免漂移:
     * - [NpuModelDownloader] 下载完整性校验(落盘长度 == 清单长度);
     * - [isReady] / [missingLibs] 就绪判定(磁盘上的库是否为本版本),
     *   见 [isReady] 的 KDoc 说明为什么必须校验。
     *
     * 表里没有的架构一律回退为"未知"(大小校验跳过,只判存在且非空),
     * 保证未知架构仍可下载、仍可被判定为就绪。
     */
    internal val ARCH_FILE_SIZES: Map<Int, ArchFileSizes> = mapOf(
        68 to ArchFileSizes(stub = 783_992, skel = 10_981_860, detCtx = 4_365_238),
        69 to ArchFileSizes(stub = 783_992, skel = 12_383_460, detCtx = 4_365_238),
        73 to ArchFileSizes(stub = 791_424, skel = 12_375_248, detCtx = 3_869_622),
        75 to ArchFileSizes(stub = 791_424, skel = 12_358_868, detCtx = 3_869_622),
        79 to ArchFileSizes(stub = 791_424, skel = 12_559_528, detCtx = 3_709_878),
        81 to ArchFileSizes(stub = 816_184, skel = 13_546_372, detCtx = 3_963_830),
    )

    /** 查架构专属文件大小;未知架构返回 null(调用方跳过大小校验)。 */
    internal fun archFileSizes(arch: Int): ArchFileSizes? = ARCH_FILE_SIZES[arch]

    // ------------------------------------------------------------------ 定向清理其它架构

    /** 架构专属库文件名:`libQnnHtpV{arch}Stub.so` / `libQnnHtpV{arch}Skel.so`。 */
    private val ARCH_LIB_NAME = Regex("""^libQnnHtpV(\d+)(Stub|Skel)\.so$""")

    /**
     * 删除**属于其它 HTP 架构**的架构专属文件,保留 [keepArch] 的那一份(以及通用库)。
     *
     * ## 为什么需要它
     *
     * [NpuModelDownloader] 的跳过逻辑按**文件名**判断"已存在",所以一旦用户之前下过
     * **错误架构**的整套文件(真机确实发生过:设备上报 v68 而白名单要求 v69),
     * 这些文件永远不会再被枚举,白占约 16 MB 且永远回收不了。
     *
     * ## 删什么、不删什么
     *
     * - 删:`externalDir/<abi>/libQnnHtpV{其它数字}Stub.so`、`...Skel.so`
     * - 删:`filesDir/models/npu/ctx/{其它数字}/` 整个目录(含其中的 `det_ctx.onnx`)
     * - **不删**:`libQnnHtp.so` / `libQnnSystem.so` —— 跨架构通用的 General 库,
     *   删了会破坏当前(以及任何)架构
     * - **不删**:本次 [keepArch] 自己的文件
     *
     * 不用 [clear] —— 它会连刚下载好的当前架构文件一起删掉。
     *
     * 只能在**下载成功之后**调用:下载失败时清理会把用户仅有的可用文件也删掉,更糟。
     *
     * @param keepArch 本次使用的 HTP 架构号,其文件一律保留
     * @return 被删除的文件与目录(便于调用方记录日志)
     */
    internal fun pruneOtherArches(context: Context, keepArch: Int): List<File> {
        val removed = mutableListOf<File>()

        // 1. 外载目录里其它架构的 Stub / Skel
        val libDir = externalDir(context)
        libDir.listFiles()?.forEach { f ->
            val arch = ARCH_LIB_NAME.matchEntire(f.name)
                ?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
            if (arch != keepArch && f.delete()) removed += f
        }

        // 2. 模型目录里其它架构的 ctx 子目录(目录名为纯数字)
        val ctxRoot = File(File(context.applicationContext.filesDir, NpuSupport.NPU_MODEL_DIR), "ctx")
        ctxRoot.listFiles()?.forEach { dir ->
            val arch = dir.name.toIntOrNull() ?: return@forEach
            if (arch != keepArch && dir.isDirectory && dir.deleteRecursively()) removed += dir
        }

        return removed
    }
}
