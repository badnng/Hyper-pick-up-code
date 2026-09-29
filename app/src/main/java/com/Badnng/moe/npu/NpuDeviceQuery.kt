package com.Badnng.moe.npu

import android.os.Build
import android.util.Log
import java.io.File
import java.util.Locale

/**
 * 设备 NPU(Hexagon HTP)架构的直查 API —— 直接问设备,不查映射表。
 *
 * 与 [NpuSupport.detect] 的分工:
 * - [NpuSupport.detect]:SoC 型号 → HTP 架构白名单映射表,表外机型判为不支持
 * - 本 API:设备自己报出来的架构,表外机型照样能查到
 *
 * 权威来源:厂商分区里 DSP 侧算子库的文件名。
 * 高通会把 DSP 侧算子库按架构命名后放在设备上,例如:
 * - /odm/lib/rfsa/adsp/libQnnHtpV75Skel.so → 设备真实架构 v75
 * - /vendor/lib/rfsa/adsp/libSnpeHtpV75Skel.so → 旧 SNPE 命名,同一信号
 *
 * 为什么 Skel 才是权威信号而不是 Stub:
 * - Stub(/odm/lib64)是主机侧转发库,厂商会把多个架构一起装
 * - Skel(/odm/lib/rfsa/adsp)是 DSP 侧算子库,只装本机 DSP 真正那一份
 *
 * 读取权限(SELinux):这些路径对普通应用不一定可读。
 * 本 API 对每个来源都独立 runCatching,读不到就静默跳过。
 */
object NpuDeviceQuery {

    private const val TAG = "NpuDeviceQuery"

    /** DSP 侧搜索目录。Skel 文件名是权威答案。 */
    private val DSP_DIRS = listOf(
        "/odm/lib/rfsa/adsp",
        "/vendor/lib/rfsa/adsp",
        "/odm/lib/rfsa/cdsp",
        "/vendor/lib/rfsa/cdsp",
        "/system/lib/rfsa/adsp",
    )

    /** 主机侧目录。这里只会出现 Stub(多架构并存),仅作旁证。 */
    private val HOST_DIRS = listOf(
        "/odm/lib64",
        "/vendor/lib64",
        "/system/lib64",
    )

    /** /sys/devices/soc0 下的 SoC 基本信息(部分字段可能被 SELinux 拦)。 */
    private val SOC_FILES = listOf(
        "soc_id", "family", "machine", "chip_id", "chip_family",
        "platform_version", "revision", "hw_platform",
    )

    /** libQnnHtpV75Skel.so / libSnpeHtpV75Skel.so */
    private val SKEL_RE = Regex("""^lib(Qnn|Snpe)HtpV(\d+)Skel\.so$""")

    /** libQnnHtpV75Stub.so */
    private val STUB_RE = Regex("""^libQnnHtpV(\d+)Stub\.so$""")

    enum class ArchSource {
        /** DSP 侧 libQnnHtpV{arch}Skel.so —— 权威。 */
        DSP_SKEL,

        /** DSP 侧 libSnpeHtpV{arch}Skel.so —— 旧 SNPE 命名,同等权威。 */
        SNPE_DSP_SKEL,

        /** 主机侧 Stub —— 只说明"系统装了哪些",不说明本机是哪个。 */
        HOST_STUB,

        /** 白名单映射兜底,非设备自报。 */
        WHITELIST,
    }

    /** @param path 证据文件路径;白名单兜底时是解释文本。 */
    data class Evidence(val arch: Int, val kind: ArchSource, val path: String) {
        /** 是否属于"设备自报"级别(只有 DSP 侧才算)。 */
        val authoritative: Boolean
            get() = kind == ArchSource.DSP_SKEL || kind == ArchSource.SNPE_DSP_SKEL
    }

    /**
     * 设备 NPU 查询结果。
     *
     * ⚠️ [htpArch] 是**设备自报**结论,**不用于决策**(决定下载/加载哪个架构请用
     * [NpuSupport.resolveHtpArch])。多套 Skel 并存时本字段优先与白名单对齐,
     * 但白名单才是唯一权威 —— 真机上两者可能不一致,详见 [query] 的说明。
     *
     * @param htpArch      结论:Hexagon HTP 架构号(如 75);无法确定时为 null
     * @param archEvidence 全部证据(含旁证与兜底),按可信度排序
     * @param socModel     Build.SOC_MODEL / ro.soc.model,如 SM8650
     * @param socId        /sys/devices/soc0/soc_id
     * @param socFamily    /sys/devices/soc0/family,通常为 "Snapdragon"
     * @param machine      /sys/devices/soc0/machine
     * @param platform     ro.board.platform
     * @param socProps     其它读到的系统属性(诊断用)
     * @param socFiles     成功读到的 /sys/devices/soc0/ 下的字段
     * @param scannedDirs  扫描过的目录及可读性
     * @param isQualcomm   是否高通平台
     */
    data class Info(
        val htpArch: Int?,
        val archEvidence: List<Evidence>,
        val socModel: String?,
        val socId: Int?,
        val socFamily: String?,
        val machine: String?,
        val platform: String?,
        val socProps: Map<String, String>,
        val socFiles: Map<String, String>,
        val scannedDirs: Map<String, Boolean>,
        val isQualcomm: Boolean,
    ) {
        /** 结论是否来自设备自报(DSP 侧 Skel),而非映射表兜底。 */
        val archFromDevice: Boolean
            get() = archEvidence.any { it.authoritative && it.arch == htpArch }

        /** 证据是否自相矛盾(出现了多个 DSP 侧架构)。 */
        val archAmbiguous: Boolean
            get() = archEvidence.filter { it.authoritative }.map { it.arch }.distinct().size > 1

        /** 一句话摘要,可直接展示在诊断面板。 */
        val summary: String
            get() = buildString {
                append(if (isQualcomm) "高通骁龙" else "非高通平台")
                socModel?.let { append("($it)") }
                append(" · ")
                if (htpArch == null) {
                    append("Hexagon 架构未识别")
                } else {
                    append("Hexagon HTP v$htpArch")
                    append(if (archFromDevice) "(设备自报)" else "(映射表推断)")
                    if (archAmbiguous) {
                        // 真机排查靠这行:明确列出设备里到底有几套 Skel。
                        val all = archEvidence.filter { it.authoritative }.map { it.arch }.distinct()
                        append(" ⚠ 证据不一致(设备自报 ${all.joinToString("/") { "v$it" }})")
                    }
                }
                socFamily?.let { append(" · $it") }
            }
    }

    /**
     * 直查设备 NPU 架构。
     *
     * 无参、无副作用、可在任意线程调用;单次调用只做十几次文件系统读取,开销可忽略。
     * 任何一步失败都不会抛异常。
     */
    fun query(): Info {
        val evidence = mutableListOf<Evidence>()
        val scanned = linkedMapOf<String, Boolean>()

        // ① DSP 侧:权威信号
        for (dir in DSP_DIRS) {
            scanned[dir] = scanDsp(dir, evidence)
        }
        // ② 主机侧:旁证
        for (dir in HOST_DIRS) {
            scanned[dir] = scanHost(dir, evidence)
        }

        // ③ /sys/devices/soc0/*
        val socFiles = linkedMapOf<String, String>()
        for (name in SOC_FILES) {
            readText("/sys/devices/soc0/$name")?.let { socFiles[name] = it }
        }

        // ④ 系统属性
        val socModel = buildSocModel()
        val platform = sysProp("ro.board.platform") ?: sysProp("ro.hardware")
        val props = linkedMapOf<String, String>()
        for (key in listOf(
            "ro.soc.model", "ro.soc.manufacturer", "ro.board.platform",
            "ro.hardware", "ro.product.board", "ro.chipname",
            "ro.boot.vendor.qspa.npu", "ro.mi.os.soc.vendor",
        )) {
            sysProp(key)?.let { props[key] = it }
        }

        val manufacturer = (Build.MANUFACTURER ?: "").lowercase(Locale.ROOT)
        val hardware = (Build.HARDWARE ?: "").lowercase(Locale.ROOT)
        val board = (Build.BOARD ?: "").lowercase(Locale.ROOT)
        val isQualcomm = listOf(manufacturer, hardware, board, platform ?: "")
            .any { it.contains("qcom") || it.contains("qualcomm") } ||
            socFiles["family"]?.contains("Snapdragon", ignoreCase = true) == true

        // ⑤ 结论:优先设备自报,其次白名单兜底
        //
        // ⚠️ 本结论是「设备自报」,**不构成决策依据**。要决定"下载/加载哪个架构"
        // 请一律走 [NpuSupport.resolveHtpArch](白名单优先)。
        //
        // 真机实测(小米 23046RP50C / SM8475 / taro):`/vendor/lib/rfsa/adsp` 里
        // **同时存在** `libSnpeHtpV68Skel.so` 与 `libSnpeHtpV69Skel.so`,而目录列举顺序
        // 把 v68 排在前面 —— 盲取 `firstOrNull()` 得出 v68,与就绪判定
        // ([NpuSupport.detect] 白名单 = v69)冲突,表现为"文件已下载却永远提示未下载"的死循环。
        // 因此**多套 Skel 并存时优先选与白名单一致的那个**,只有白名单也查不到
        // (表外机型)时才退回目录顺序。
        val whitelistArch = NpuSupport.htpArchForSoc(socModel, platform)
        val deviceArches = evidence.filter { it.authoritative }.map { it.arch }.distinct()
        val deviceArch = when {
            deviceArches.isEmpty() -> null
            deviceArches.size == 1 -> deviceArches.first()
            whitelistArch != null && deviceArches.contains(whitelistArch) -> {
                Log.w(
                    TAG,
                    "检测到多套 DSP Skel $deviceArches,已按白名单取 v$whitelistArch" +
                        "(决策请用 NpuSupport.resolveHtpArch)",
                )
                whitelistArch
            }
            else -> deviceArches.first()
        }
        val htpArch = deviceArch
            ?: evidence.filter { it.kind == ArchSource.HOST_STUB }.map { it.arch }.distinct().singleOrNull()
            ?: whitelistArch
                ?.also { evidence += Evidence(it, ArchSource.WHITELIST, "白名单映射(soc=$socModel, platform=$platform)") }

        val info = Info(
            htpArch = htpArch,
            archEvidence = evidence.sortedBy { it.kind.ordinal },
            socModel = socModel,
            socId = socFiles["soc_id"]?.let { parseSocId(it) },
            socFamily = socFiles["family"],
            machine = socFiles["machine"],
            platform = platform,
            socProps = props,
            socFiles = socFiles,
            scannedDirs = scanned,
            isQualcomm = isQualcomm,
        )
        Log.i(TAG, "query -> ${info.summary} | evidence=${evidence.size} | socId=${info.socId}")
        return info
    }

    /**
     * 诊断用的原始转储(逐来源列出读到了什么)。
     */
    fun rawDump(): String = buildString {
        val info = query()
        appendLine("== NpuDeviceQuery ==")
        appendLine("结论: ${info.summary}")
        appendLine("htpArch=${info.htpArch} fromDevice=${info.archFromDevice} ambiguous=${info.archAmbiguous}")
        appendLine("-- 架构证据 --")
        if (info.archEvidence.isEmpty()) appendLine("  (无)")
        info.archEvidence.forEach { appendLine("  v${it.arch} [${it.kind}] ${it.path}") }
        appendLine("-- 目录可读性 --")
        info.scannedDirs.forEach { (dir, ok) -> appendLine("  ${if (ok) "OK  " else "FAIL"} $dir") }
        appendLine("-- /sys/devices/soc0 --")
        if (info.socFiles.isEmpty()) appendLine("  (全部不可读)")
        info.socFiles.forEach { (k, v) -> appendLine("  $k = $v") }
        appendLine("-- 系统属性 --")
        info.socProps.forEach { (k, v) -> appendLine("  $k = $v") }
        appendLine("-- Build --")
        appendLine("  MANUFACTURER=${Build.MANUFACTURER} HARDWARE=${Build.HARDWARE} BOARD=${Build.BOARD}")
        appendLine("  SOC_MODEL=${runCatching { Build.SOC_MODEL }.getOrNull()} SDK=${Build.VERSION.SDK_INT}")
        appendLine("  SUPPORTED_ABIS=${Build.SUPPORTED_ABIS.joinToString(",")}")
    }

    // ------------------------------------------------------------------ 内部实现

    /**
     * 扫 DSP 侧目录,收集 Skel 证据。
     *
     * @return 该目录是否可列(用于诊断区分"没有文件"与"读不到")
     */
    private fun scanDsp(dir: String, out: MutableList<Evidence>): Boolean {
        val names = File(dir).list() ?: return false
        for (name in names) {
            val m = SKEL_RE.matchEntire(name) ?: continue
            val arch = m.groupValues[2].toIntOrNull() ?: continue
            val kind = if (m.groupValues[1] == "Snpe") ArchSource.SNPE_DSP_SKEL else ArchSource.DSP_SKEL
            out += Evidence(arch, kind, "$dir/$name")
        }
        return true
    }

    /** 扫主机侧目录,收集 Stub 旁证。 */
    private fun scanHost(dir: String, out: MutableList<Evidence>): Boolean {
        val names = File(dir).list() ?: return false
        for (name in names) {
            val m = STUB_RE.matchEntire(name) ?: continue
            val arch = m.groupValues[1].toIntOrNull() ?: continue
            out += Evidence(arch, ArchSource.HOST_STUB, "$dir/$name")
        }
        return true
    }

    /**
     * soc_id 在部分平台是十六进制(0x557)格式,这里两种都试。
     *
     * 注意:不要把 soc_id 映射成架构 —— 那需要一份未经验证的编号表,
     * 而 Skel 文件名已经给出确定答案,再引入猜测只会添乱。
     */
    private fun parseSocId(raw: String): Int? {
        val s = raw.trim().removePrefix("0x").removePrefix("0X")
        return s.toIntOrNull() ?: s.toIntOrNull(16)
    }

    /** 按可靠性顺序读 SoC 型号:Build.SOC_MODEL → 属性。 */
    private fun buildSocModel(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { Build.SOC_MODEL }.getOrNull()
                ?.takeIf { it.isNotBlank() && it != "unknown" }
                ?.let { return it }
        }
        sysProp("ro.soc.model")?.let { return it }
        sysProp("ro.chipname")?.let { return it }
        return null
    }

    /** 反射读系统属性(隐藏 API,ROM 可能拦截,失败静默返回 null)。 */
    private fun sysProp(key: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        (get.invoke(null, key) as? String)?.trim()?.takeIf { it.isNotEmpty() && it != "unknown" }
    }.getOrNull()

    private fun readText(path: String): String? = runCatching {
        val f = File(path)
        if (!f.canRead()) return@runCatching null
        f.readText().trim().takeIf { it.isNotEmpty() }
    }.getOrNull()
}
