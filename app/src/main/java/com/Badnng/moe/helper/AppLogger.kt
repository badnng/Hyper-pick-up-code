package com.Badnng.moe.helper

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object AppLogger {

    private const val TAG = "AppLogger"
    private const val LOG_DIR = "logs"
    private const val RETENTION_DAYS = 3
    private const val MAX_APP_LOG_SIZE = 6L * 1024 * 1024 // 6MB

    // 识别相关 tag，匹配的行写入 recognition.log
    private val RECOGNITION_TAGS = setOf(
        "ExpressExtract", "ProcessTextActivity", "ProcessTextRecognition",
        "RecognitionMonitor", "ShareReceiver", "ShareRecognition",
        "SmsRecognition", "PaddleOcrHelper"
    )
    // 更新相关 tag，匹配的行写入 update.log
    private val UPDATE_TAGS = setOf(
        "UpdateCheck", "RuleEngine", "RuleModels",
        "RuleOnlineUpdater", "RuleRepository", "RulesScreen"
    )

    private var crashWriter: BufferedWriter? = null

    private var currentDate: String = ""
    private var logsDir: File? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var logcatProcess: Process? = null
    private var logcatCaptureJob: Job? = null
    private val logcatLifecycleMutex = Mutex()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())

    // ==================== 初始化 ====================

    fun init(context: Context) {
        logsDir = File(context.filesDir, LOG_DIR).apply { mkdirs() }
        currentDate = dateFormat.format(Date())
        openWriters()

        // 延迟 5 秒后执行清理和压缩，避免启动时 IO 阻塞
        scope.launch {
            delay(5000)
            cleanupOldLogs(context)
            compressPreviousDayLogs(context)
        }

        // 设置崩溃处理器
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                crash(throwable, thread.name)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        app("AppLogger initialized, date=$currentDate")

        // 启动 logcat 捕获，记录全部系统日志
        startLogcatCapture()
    }

    // ==================== Logcat 捕获 ====================

    private fun startLogcatCapture() {
        logcatCaptureJob = scope.launch {
            try {
                Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor()

                val process = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-v", "time")
                )
                logcatProcess = process

                val reader = BufferedReader(InputStreamReader(process.inputStream), 8192)
                var dir = File(logsDir, currentDate).apply { mkdirs() }

                var appWriter = BufferedWriter(FileWriter(File(dir, "app.log"), true), 8192)
                var recogWriter = BufferedWriter(FileWriter(File(dir, "recognition.log"), true), 8192)
                var updateWriter = BufferedWriter(FileWriter(File(dir, "update.log"), true), 8192)

                try {
                    var line: String?
                    var lastDateCheck = System.currentTimeMillis()
                    var currentLogDate = currentDate

                    while (reader.readLine().also { line = it } != null) {
                        // 每 30 秒检查一次日期切换
                        val now = System.currentTimeMillis()
                        if (now - lastDateCheck > 30_000) {
                            lastDateCheck = now
                            val today = dateFormat.format(Date())
                            if (today != currentLogDate) {
                                // 日期切换，关闭旧 writer，打开新目录
                                appWriter.close()
                                recogWriter.close()
                                updateWriter.close()

                                currentLogDate = today
                                currentDate = today
                                dir = File(logsDir, today).apply { mkdirs() }
                                appWriter = BufferedWriter(FileWriter(File(dir, "app.log"), true), 8192)
                                recogWriter = BufferedWriter(FileWriter(File(dir, "recognition.log"), true), 8192)
                                updateWriter = BufferedWriter(FileWriter(File(dir, "update.log"), true), 8192)
                                Log.d(TAG, "logcat date switched to $today")
                            }
                        }

                        line?.let { l ->
                            // 全部写入 app.log
                            appWriter.write(l)
                            appWriter.newLine()
                            appWriter.flush()

                            // 按 tag 分流到 recognition.log / update.log
                            val tag = extractLogTag(l)
                            if (tag != null) {
                                if (tag in RECOGNITION_TAGS) {
                                    recogWriter.write(l)
                                    recogWriter.newLine()
                                    recogWriter.flush()
                                }
                                if (tag in UPDATE_TAGS) {
                                    updateWriter.write(l)
                                    updateWriter.newLine()
                                    updateWriter.flush()
                                }
                            }
                        }

                        if (File(dir, "app.log").length() > MAX_APP_LOG_SIZE) {
                            truncateFile(File(dir, "app.log"))
                        }
                    }
                } finally {
                    appWriter.close()
                    recogWriter.close()
                    updateWriter.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "logcat capture failed", e)
            }
        }
    }

    /** 从 logcat -v time 格式行中提取 tag，格式: "04-29 17:13:58.239 PID TID D Tag: message" */
    private fun extractLogTag(line: String): String? {
        return try {
            // logcat -v time 格式: "MM-DD HH:MM:SS.mmm PID TID LEVEL TAG: message"
            val parts = line.split(" ")
            if (parts.size >= 6) parts[5].removeSuffix(":") else null
        } catch (_: Exception) {
            null
        }
    }

    private fun truncateFile(file: File, keepBytes: Long = 4L * 1024 * 1024) {
        try {
            if (!file.exists() || file.length() <= MAX_APP_LOG_SIZE) return
            val lines = file.readLines()
            val totalChars = lines.sumOf { it.length + 1 }
            var skipChars = totalChars - keepBytes.toInt()
            if (skipChars <= 0) return

            val keptLines = mutableListOf<String>()
            for (line in lines) {
                skipChars -= line.length + 1
                if (skipChars <= 0) {
                    keptLines.add(line)
                }
            }

            file.writeText(keptLines.joinToString("\n") + "\n")
        } catch (e: Exception) {
            Log.e(TAG, "truncateFile failed", e)
        }
    }

    // ==================== 日志方法（保留接口兼容，实际由 logcat 捕获写入） ====================

    fun recognition(message: String) {
        checkDateSwitch()
    }

    fun app(message: String) {
        checkDateSwitch()
    }

    fun update(message: String) {
        checkDateSwitch()
    }

    fun service(message: String) {
        checkDateSwitch()
    }

    fun crash(throwable: Throwable, tag: String = "") {
        checkDateSwitch()
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val trace = sw.toString()
        val header = if (tag.isNotEmpty()) "[$tag] ${throwable.message}" else throwable.message ?: "Unknown crash"
        writeLog(crashWriter, "CRASH", "$header\n$trace")
    }

    // ==================== 内部写入 ====================

    private fun writeLog(writer: BufferedWriter?, level: String, message: String) {
        try {
            val time = timeFormat.format(Date())
            writer?.apply {
                write("[$time] [$level] $message\n")
                flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeLog failed: $level", e)
        }
    }

    private fun checkDateSwitch() {
        val today = dateFormat.format(Date())
        if (today != currentDate) {
            currentDate = today
            closeWriters()
            openWriters()
        }
    }

    private fun openWriters() {
        val dir = File(logsDir, currentDate).apply { mkdirs() }
        // recognition.log 和 update.log 由 logcat 捕获线程按 tag 分流写入
        // crash.log 仍由 crash() 方法直接写入
        crashWriter = getWriter(File(dir, "crash.log"))
    }

    private fun getWriter(file: File): BufferedWriter {
        return BufferedWriter(FileWriter(file, true), 8192)
    }

    private fun closeWriters() {
        try { crashWriter?.close() } catch (_: Exception) {}
        crashWriter = null
    }

    // ==================== 压缩 ====================

    fun flush() {
        try { crashWriter?.flush() } catch (_: Exception) {}
    }

    fun compressTodayLogs(context: Context): File? {
        val todayDir = File(logsDir, dateFormat.format(Date()))
        if (!todayDir.exists() || !todayDir.isDirectory) return null

        val logFiles = todayDir.listFiles { f -> f.extension == "log" && f.length() > 0 }
        if (logFiles.isNullOrEmpty()) return null

        val zipName = "${fileNameFormat.format(Date())}-com.Badnng.moe-Log.zip"
        val zipFile = File(logsDir, zipName)

        return try {
            ZipOutputStream(zipFile.outputStream()).use { zos ->
                zos.setLevel(Deflater.BEST_COMPRESSION)
                for (file in logFiles) {
                    val entry = ZipEntry(file.name)
                    zos.putNextEntry(entry)
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            // 压缩成功后删除原目录
            todayDir.deleteRecursively()
            zipFile
        } catch (e: Exception) {
            Log.e(TAG, "compressTodayLogs failed", e)
            zipFile.delete()
            null
        }
    }

    private fun compressPreviousDayLogs(context: Context) {
        val today = dateFormat.format(Date())
        val dirs = logsDir?.listFiles { f -> f.isDirectory && f.name != today } ?: return

        for (dir in dirs) {
            val logFiles = dir.listFiles { f -> f.extension == "log" && f.length() > 0 } ?: continue
            if (logFiles.isEmpty()) continue

            val zipName = "${dir.name}-${fileNameFormat.format(Date())}-com.Badnng.moe-Log.zip"
            val zipFile = File(logsDir, zipName)

            try {
                ZipOutputStream(zipFile.outputStream()).use { zos ->
                    zos.setLevel(Deflater.BEST_COMPRESSION)
                    for (file in logFiles) {
                        val entry = ZipEntry(file.name)
                        zos.putNextEntry(entry)
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
                dir.deleteRecursively()
            } catch (e: Exception) {
                Log.e(TAG, "compressPreviousDayLogs failed for ${dir.name}", e)
                zipFile.delete()
            }
        }
    }

    // ==================== 清理 ====================

    suspend fun clearLogs(context: Context): Boolean = withContext(Dispatchers.IO + NonCancellable) {
        logcatLifecycleMutex.withLock {
            val directory = File(context.filesDir, LOG_DIR)
            try {
                logcatProcess?.destroy()
                logcatCaptureJob?.cancelAndJoin()
                logcatCaptureJob = null
                logcatProcess = null
                closeWriters()

                val cleared = !directory.exists() || directory.deleteRecursively()
                logsDir = directory.apply { mkdirs() }
                currentDate = dateFormat.format(Date())
                openWriters()
                startLogcatCapture()
                cleared
            } catch (e: Exception) {
                Log.e(TAG, "clearLogs failed", e)
                logsDir = directory.apply { mkdirs() }
                currentDate = dateFormat.format(Date())
                closeWriters()
                openWriters()
                if (logcatCaptureJob?.isActive != true) startLogcatCapture()
                false
            }
        }
    }

    fun cleanupOldLogs(context: Context) {
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * 24L * 60 * 60 * 1000
        val files = logsDir?.listFiles() ?: return

        for (file in files) {
            if (file.lastModified() < cutoff) {
                file.deleteRecursively()
            }
        }
    }

    // ==================== 导出 ====================

    fun getTodayLogFiles(context: Context): List<File> {
        val today = dateFormat.format(Date())
        val dir = File(logsDir, today)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.extension == "log" && f.length() > 0 }?.toList() ?: emptyList()
    }

    fun getExportableFiles(context: Context): List<File> {
        val files = mutableListOf<File>()

        // 当天日志目录
        val today = dateFormat.format(Date())
        val todayDir = File(logsDir, today)
        if (todayDir.exists()) {
            todayDir.listFiles { f -> f.extension == "log" && f.length() > 0 }?.let { files.addAll(it) }
        }

        // 之前的压缩包
        logsDir?.listFiles { f -> f.extension == "zip" }?.let { files.addAll(it) }

        return files
    }
}
