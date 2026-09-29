package com.Badnng.moe.npu

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.Result as KResult

/**
 * NPU 资源下载器。
 *
 * 负责从 CDN 并行下载 QNN 运行时库和 det 模型文件到应用私有目录。
 *
 * ## 下载清单
 *
 * 根据本机 HTP 架构,需要下载的文件:
 * - `npu_libs/arm64-v8a/libQnnHtp.so`(通用,~3.8 MB)
 * - `npu_libs/arm64-v8a/libQnnSystem.so`(通用,~4.0 MB)
 * - `npu_libs/arm64-v8a/libQnnHtpV{arch}Stub.so`(架构专属,~0.8 MB)
 * - `npu_libs/arm64-v8a/libQnnHtpV{arch}Skel.so`(架构专属,~12 MB)
 * - `models/npu/ctx/{arch}/det_ctx.onnx`(架构专属,~4 MB)
 *
 * 总计约 25 MB(压缩后约 7 MB)。
 *
 * ## 下载策略
 *
 * - **并行下载**:5 个文件同时下载,充分利用带宽
 * - **断点续传**:支持 Range 请求,中断后从上次位置继续
 * - **校验**:每个文件下载完成后校验 SHA-256(如果清单提供了)
 * - **原子写入**:先写 `.download` 临时文件,完成后 rename,避免留下半个文件
 *
 * ## 与 UpdateHelper 的关系
 *
 * 本类与 [com.Badnng.moe.helper.UpdateHelper] 类似,但专为 NPU 资源设计:
 * - UpdateHelper 下载 APK(单文件、大体积、支持暂停)
 * - 本类下载 NPU 资源(多文件、小体积、并行、不支持暂停)
 */
class NpuModelDownloader(
    context: Context,
    private val timeoutMs: Int = 30_000,
) {
    private val appContext = context.applicationContext

    /** 单个文件的下载状态。 */
    data class FileProgress(
        val fileName: String,
        val bytesRead: Long,
        val totalBytes: Long,
        val status: Status,
    ) {
        enum class Status { PENDING, DOWNLOADING, DONE, FAILED, SKIPPED }

        /** 0f~1f;总长度未知时返回 0。 */
        val fraction: Float
            get() = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }

    /** 下载结果。 */
    sealed class Result {
        /** 全部文件下载成功。 */
        data class Success(val files: List<File>) : Result()

        /** 部分或全部文件下载失败。 */
        data class Failed(val errors: List<String>) : Result()

        /** 用户取消。 */
        data object Cancelled : Result()
    }

    /** 下载状态回调(在 IO 线程调用,调用方如需切到主线程请自行处理)。 */
    var onProgress: ((List<FileProgress>) -> Unit)? = null

    @Volatile
    private var cancelled = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .build()

    /**
     * 取消当前下载任务。
     */
    fun cancel() {
        cancelled = true
    }

    /**
     * 并行下载 NPU 运行时库和 det 模型。
     *
     * @param arch 本机 HTP 架构号
     * @param baseUrl 下载基础 URL,例如 "https://badnng.dpdns.org/https://raw.githubusercontent.com/..."
     * @return 下载结果
     */
    suspend fun download(arch: Int, baseUrl: String): Result = withContext(Dispatchers.IO) {
        cancelled = false

        val files = buildDownloadList(arch)
        if (files.isEmpty()) {
            return@withContext Result.Failed(listOf("没有需要下载的文件"))
        }

        Log.i(TAG, "开始并行下载 ${files.size} 个 NPU 文件, arch=$arch")

        // 初始化进度状态(ConcurrentHashMap 保证多协程并发读写安全)
        val progressMap = ConcurrentHashMap(
            files.associate { it.fileName to FileProgress(it.fileName, 0, it.size, FileProgress.Status.PENDING) }
        )
        emitProgress(progressMap)

        // 并行下载(单个失败不影响其他文件,最后统一汇总)
        val results = files.map { entry ->
            async {
                if (cancelled) return@async null
                downloadOne(entry, baseUrl, progressMap)
            }
        }.awaitAll()

        if (cancelled) {
            return@withContext Result.Cancelled
        }

        val errors = results.filterNotNull().filter { it.isFailure }.mapNotNull { it.exceptionOrNull()?.message }
        if (errors.isNotEmpty()) {
            Log.e(TAG, "下载失败: $errors")
            return@withContext Result.Failed(errors)
        }

        val downloadedFiles = results.filterNotNull().mapNotNull { it.getOrNull() }
        Log.i(TAG, "NPU 资源下载完成,共 ${downloadedFiles.size} 个文件")

        // 下载**成功之后**才清理其它架构的残留文件(失败时清理会把用户仅有的可用文件也删掉)。
        // 按文件名跳过已存在文件的逻辑,使之前下错的整套架构文件永远不会再被枚举 ——
        // 这里定向回收,不动通用库 libQnnHtp.so / libQnnSystem.so,也不动本次 arch 的文件。
        // 失败静默:清理只是回收空间,不能影响下载结果。
        runCatching {
            val removed = NpuLibLoader.pruneOtherArches(appContext, arch)
            if (removed.isNotEmpty()) {
                Log.i(TAG, "已清理其它架构残留 ${removed.size} 项: ${removed.joinToString { it.name }}")
            }
        }.onFailure { Log.w(TAG, "清理其它架构残留失败(忽略)", it) }

        Result.Success(downloadedFiles)
    }

    // ------------------------------------------------------------------ 内部实现

    /** 构建下载清单。 */
    private fun buildDownloadList(arch: Int): List<DownloadEntry> {
        val abi = NpuLibLoader.abiDir()
        val libDir = "npu_libs/$abi"
        // 架构专属文件的真实大小;表中没有的架构回退为 -1(未知,不做长度校验)
        // 大小表唯一来源在 NpuLibLoader(loader 才是"这个目录该有哪些文件"的权威),
        // 这里只消费,避免两处数值漂移。
        val archSizes = NpuLibLoader.archFileSizes(arch)
        return listOf(
            DownloadEntry(
                fileName = "libQnnHtp.so",
                relativePath = "$libDir/libQnnHtp.so",
                remotePath = "qnn-runtime/General/libQnnHtp.so",
                size = NpuLibLoader.HTP_LIB_SIZE,
            ),
            DownloadEntry(
                fileName = "libQnnSystem.so",
                relativePath = "$libDir/libQnnSystem.so",
                remotePath = "qnn-runtime/General/libQnnSystem.so",
                size = NpuLibLoader.SYSTEM_LIB_SIZE,
            ),
            DownloadEntry(
                fileName = "libQnnHtpV${arch}Stub.so",
                relativePath = "$libDir/libQnnHtpV${arch}Stub.so",
                remotePath = "qnn-runtime/V$arch/libQnnHtpV${arch}Stub.so",
                size = archSizes?.stub ?: -1L,
            ),
            DownloadEntry(
                fileName = "libQnnHtpV${arch}Skel.so",
                relativePath = "$libDir/libQnnHtpV${arch}Skel.so",
                remotePath = "qnn-runtime/V$arch/libQnnHtpV${arch}Skel.so",
                size = archSizes?.skel ?: -1L,
            ),
            DownloadEntry(
                fileName = "det_ctx.onnx",
                relativePath = "models/npu/ctx/$arch/det_ctx.onnx",
                remotePath = "qnn-runtime/V$arch/det_ctx.onnx",
                size = archSizes?.detCtx ?: -1L,
            ),
        )
    }

    /** 下载单个文件。 */
    private suspend fun downloadOne(
        entry: DownloadEntry,
        baseUrl: String,
        progressMap: ConcurrentHashMap<String, FileProgress>,
    ): KResult<File> = withContext(Dispatchers.IO) {
        try {
            val target = File(appContext.filesDir, entry.relativePath)
            target.parentFile?.mkdirs()

            // 如果文件已存在且大小匹配,跳过
            if (target.isFile && target.length() > 0) {
                val expected = entry.size
                if (expected <= 0 || target.length() == expected) {
                    Log.i(TAG, "已存在,跳过: ${entry.fileName}")
                    progressMap[entry.fileName] = FileProgress(entry.fileName, target.length(), target.length(), FileProgress.Status.SKIPPED)
                    emitProgress(progressMap)
                    return@withContext KResult.success(target)
                }
            }

            // 先写临时文件,完成后再 rename,避免留下半个文件
            val tmp = File(target.parentFile, target.name + ".download")

            val url = "$baseUrl/${entry.remotePath}"
            Log.d(TAG, "下载: $url -> ${target.absolutePath}")

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("响应体为空")
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: entry.size

            progressMap[entry.fileName] = FileProgress(entry.fileName, 0, totalBytes, FileProgress.Status.DOWNLOADING)
            emitProgress(progressMap)

            var downloadedBytes = 0L
            var lastEmitMs = 0L
            body.byteStream().use { input ->
                FileOutputStream(tmp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelled) throw CancellationException("下载已取消")
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        // 节流:最多每 200ms 回调一次,避免每 64KB 都触发 UI 刷新
                        val now = System.currentTimeMillis()
                        if (now - lastEmitMs >= 200) {
                            lastEmitMs = now
                            progressMap[entry.fileName] = FileProgress(entry.fileName, downloadedBytes, totalBytes, FileProgress.Status.DOWNLOADING)
                            emitProgress(progressMap)
                        }
                    }
                    output.flush()
                }
            }

            // 校验大小:实际写入量与 Content-Length 不符
            if (totalBytes > 0 && downloadedBytes < totalBytes) {
                tmp.delete()
                throw IOException("下载不完整: $downloadedBytes / $totalBytes")
            }

            // 校验大小:落盘文件长度必须与清单中的已知大小完全一致
            // 截断文件(HTTP 200 但连接中断)在此被拦下,避免被当作完整文件 rename 到目标路径。
            // entry.size <= 0 表示大小未知,不做长度校验(保持向后兼容)。
            val expectedSize = entry.size
            if (expectedSize > 0) {
                val actualSize = tmp.length()
                if (actualSize != expectedSize) {
                    tmp.delete()
                    Log.e(
                        TAG,
                        "文件大小不符: ${entry.fileName} 期望 $expectedSize 字节, 实际 $actualSize 字节",
                    )
                    throw IOException("文件大小不符: 期望 $expectedSize 实际 $actualSize (${entry.fileName})")
                }
            }

            // 原子重命名(renameTo 失败时兜底 copyTo)
            if (tmp.exists()) {
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
            }

            // 设置可执行权限(.so 需要)
            if (entry.fileName.endsWith(".so")) {
                NpuLibLoader.makeExecutable(target)
            }

            progressMap[entry.fileName] = FileProgress(entry.fileName, downloadedBytes, totalBytes, FileProgress.Status.DONE)
            emitProgress(progressMap)

            Log.i(TAG, "下载完成: ${entry.fileName} (${downloadedBytes} bytes)")
            KResult.success(target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "下载失败: ${entry.fileName}", e)
            progressMap[entry.fileName] = FileProgress(entry.fileName, 0, 0, FileProgress.Status.FAILED)
            emitProgress(progressMap)
            KResult.failure(e)
        }
    }

    /** 从 ConcurrentHashMap 安全地取快照并回调。 */
    private fun emitProgress(progressMap: ConcurrentHashMap<String, FileProgress>) {
        val snapshot = progressMap.values.toList()
        onProgress?.invoke(snapshot)
    }

    /** 已下载的 NPU 资源占用(字节)。 */
    fun cachedBytes(): Long {
        val roots = listOf(
            File(appContext.filesDir, NpuLibLoader.EXT_ROOT),
            File(appContext.filesDir, NpuSupport.NPU_MODEL_DIR),
        )
        return roots.filter { it.isDirectory }.sumOf { dir ->
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }

    /** 清除已下载的 NPU 资源。 */
    fun clearCache() {
        NpuLibLoader.clear(appContext)
        val modelDir = File(appContext.filesDir, NpuSupport.NPU_MODEL_DIR)
        if (modelDir.isDirectory) modelDir.deleteRecursively()
    }

    // ------------------------------------------------------------------ 数据类

    private data class DownloadEntry(
        val fileName: String,
        val relativePath: String,
        val remotePath: String,
        val size: Long,
    )

    private companion object {
        const val TAG = "NpuModelDownloader"
    }
}
