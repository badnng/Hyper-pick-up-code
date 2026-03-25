package com.Badnng.moe

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val md5: String? = null
)

object UpdateHelper {
    private const val TAG = "UpdateHelper"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val STABLE_URL = "https://badnng.dpdns.org/https://raw.githubusercontent.com/badnng/Hyper-pick-up-code/refs/heads/master/Stable.json"
    private const val DEV_URL = "https://badnng.dpdns.org/https://raw.githubusercontent.com/badnng/Hyper-pick-up-code/refs/heads/master/Dev.json"
    private const val DOWNLOAD_BASE_URL = "https://badnng.dpdns.org/"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    @Volatile
    var isDownloading = false
        private set

    @Volatile
    var currentDownloadingVersion: UpdateInfo? = null
        private set

    @Volatile
    var downloadedFile: File? = null
        private set

    fun setDownloadingState(downloading: Boolean, version: UpdateInfo? = null, file: File? = null) {
        isDownloading = downloading
        currentDownloadingVersion = if (downloading) version else null
        downloadedFile = if (downloading) null else file
        Log.d(TAG, "下载状态更新: isDownloading=$downloading, version=${version?.versionName}, file=${file?.name}")
    }

    suspend fun checkUpdate(isDev: Boolean): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = if (isDev) DEV_URL else STABLE_URL
            Log.d(TAG, "开始检查更新: channel=${if (isDev) "dev" else "stable"}")
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "检查更新失败: http=${response.code}")
                return@withContext null
            }

            val body = response.body?.string()?.trim()
            if (body.isNullOrEmpty()) {
                Log.e(TAG, "检查更新失败: empty body")
                return@withContext null
            }

            val json = JSONObject(body)
            val versionCode = json.optLong("versionCode", -1L)
            val versionName = json.optString("versionName").trim()
            val releaseNotes = json.optString("releaseNotes").trim()
            val downloadUrl = json.optString("downloadUrl").trim()
            val md5 = json.optString("md5").trim().ifEmpty { null }

            if (versionCode <= 0L || versionName.isEmpty() || downloadUrl.isEmpty()) {
                Log.e(TAG, "更新信息无效: versionCode=$versionCode, versionName=$versionName, downloadUrl=$downloadUrl")
                return@withContext null
            }

            UpdateInfo(
                versionCode = versionCode,
                versionName = versionName,
                releaseNotes = releaseNotes,
                downloadUrl = downloadUrl,
                md5 = md5
            )
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败", e)
            null
        }
    }

    fun getCurrentVersionCode(context: Context): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            0L
        }
    }

    suspend fun downloadUpdate(
        context: Context,
        updateInfo: UpdateInfo,
        onProgress: (Float) -> Unit,
        isPaused: () -> Boolean
    ): File? = withContext(Dispatchers.IO) {
        var file: File? = null
        try {
            setDownloadingState(true, updateInfo)
            onProgress(0f)

            val rawDownloadUrl = updateInfo.downloadUrl.trim()
            if (rawDownloadUrl.isEmpty()) {
                Log.e(TAG, "下载失败: empty download url")
                return@withContext null
            }

            val downloadUrl = resolveProxiedDownloadUrl(rawDownloadUrl) ?: return@withContext null

            val response = client.newCall(Request.Builder().url(downloadUrl).build()).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "下载失败: http=${response.code}, url=$downloadUrl")
                return@withContext null
            }

            val body = response.body ?: run {
                Log.e(TAG, "下载失败: empty response body")
                return@withContext null
            }

            val contentType = body.contentType()?.toString().orEmpty()
            val contentLength = body.contentLength()
            if (contentLength == 0L) {
                Log.e(TAG, "下载失败: empty file")
                return@withContext null
            }

            val downloadsDir = File(context.filesDir, "downloads")
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            file = File(downloadsDir, "update_${updateInfo.versionName}.apk")
            if (file.exists()) file.delete()

            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isPaused()) {
                            Log.d(TAG, "下载被暂停")
                            file.delete()
                            return@withContext null
                        }
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        val progress = if (contentLength > 0L) {
                            (totalBytesRead.toDouble() / contentLength.toDouble()).toFloat()
                        } else {
                            0f
                        }.coerceIn(0f, 1f)
                        onProgress(progress)
                    }
                }
            }

            if (!file.exists() || file.length() <= 0L) {
                Log.e(TAG, "下载失败: file missing or empty")
                file.delete()
                return@withContext null
            }

            if (!matchesMd5(file, updateInfo.md5)) {
                Log.e(TAG, "下载失败: md5 mismatch, expected=${updateInfo.md5}, actual=${calculateMd5(file)}")
                file.delete()
                return@withContext null
            }

            onProgress(1f)
            Log.d(TAG, "下载完成: ${file.name}")
            setDownloadingState(false, null, file)
            file
        } catch (e: Exception) {
            Log.e(TAG, "下载失败", e)
            file?.delete()
            null
        } finally {
            if (isDownloading) {
                setDownloadingState(false)
            }
        }
    }

    fun installUpdate(context: Context, file: File) {
        if (!file.exists() || file.length() <= 0L) {
            Toast.makeText(context, "更新包无效或已失效", Toast.LENGTH_SHORT).show()
            downloadedFile = null
            return
        }

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } else {
            Uri.fromFile(file)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "无法启动安装器", Toast.LENGTH_SHORT).show()
        }
    }

    fun showNoUpdateToast(context: Context) {
        Toast.makeText(context, "暂无新版本更新", Toast.LENGTH_SHORT).show()
    }

    private fun matchesMd5(file: File, expectedMd5: String?): Boolean {
        if (expectedMd5.isNullOrBlank()) return true
        val actualMd5 = calculateMd5(file) ?: return false
        return actualMd5.equals(expectedMd5.trim(), ignoreCase = true)
    }

    private fun resolveProxiedDownloadUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return null

        val suffix = when {
            trimmed.startsWith(DOWNLOAD_BASE_URL) -> trimmed.removePrefix(DOWNLOAD_BASE_URL)
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
                trimmed.substringAfter("://").substringAfter("/", "")
            }
            else -> trimmed.trimStart('/')
        }.trimStart('/')

        if (suffix.isEmpty()) return null
        return DOWNLOAD_BASE_URL + suffix
    }

    private fun calculateMd5(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "计算MD5失败", e)
            null
        }
    }
}
