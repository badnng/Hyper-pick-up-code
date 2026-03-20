package com.Badnng.moe

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String
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

    suspend fun checkUpdate(isDev: Boolean): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = if (isDev) DEV_URL else STABLE_URL
            Log.d(TAG, "开始检查更新 - 通道: ${if (isDev) "测试版" else "正式版"}")
            Log.d(TAG, "请求URL: $url")
            
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            
            Log.d(TAG, "HTTP响应码: ${response.code}")
            Log.d(TAG, "HTTP响应消息: ${response.message}")
            
            val body = response.body?.string()
            if (body == null) {
                Log.e(TAG, "响应体为空")
                return@withContext null
            }
            
            Log.d(TAG, "响应内容: $body")
            
            val json = JSONObject(body)
            val versionCode = json.getLong("versionCode")
            val versionName = json.getString("versionName")
            val releaseNotes = json.getString("releaseNotes")
            val downloadUrl = json.getString("downloadUrl")
            
            Log.d(TAG, "解析结果:")
            Log.d(TAG, "  - versionCode: $versionCode")
            Log.d(TAG, "  - versionName: $versionName")
            Log.d(TAG, "  - releaseNotes: $releaseNotes")
            Log.d(TAG, "  - downloadUrl: $downloadUrl")
            
            return@withContext UpdateInfo(
                versionCode = versionCode,
                versionName = versionName,
                releaseNotes = releaseNotes,
                downloadUrl = downloadUrl
            )
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败", e)
            e.printStackTrace()
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
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun downloadUpdate(
        context: Context,
        updateInfo: UpdateInfo,
        onProgress: (Float) -> Unit,
        isPaused: () -> Boolean
    ): File? = withContext(Dispatchers.IO) {
        try {
            val downloadUrl = DOWNLOAD_BASE_URL + updateInfo.downloadUrl
            val request = Request.Builder().url(downloadUrl).build()
            val response = client.newCall(request).execute()
            val body = response.body ?: return@withContext null
            val contentLength = body.contentLength()

            val downloadsDir = File(context.filesDir, "downloads")
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, "update_${updateInfo.versionName}.apk")

            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isPaused()) {
                            return@withContext null
                        }
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        onProgress(totalBytesRead.toFloat() / contentLength)
                    }
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun installUpdate(context: Context, file: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } else {
            Uri.fromFile(file)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法启动安装器", Toast.LENGTH_SHORT).show()
        }
    }

    fun showNoUpdateToast(context: Context) {
        Toast.makeText(context, "暂无新版本更新", Toast.LENGTH_SHORT).show()
    }
}