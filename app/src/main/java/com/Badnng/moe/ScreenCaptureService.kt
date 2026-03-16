package com.Badnng.moe

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku
import java.io.File
import java.io.FileOutputStream

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val useShizuku = intent?.getBooleanExtra("use_shizuku", false) ?: false

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val type = if (useShizuku) {
                    if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                }
                startForeground(1001, createNotification(), type)
            } else {
                startForeground(1001, createNotification())
            }
        } catch (e: Exception) {
            Log.e("CaptureLog", "启动前台服务失败", e)
            stopSelf()
            return START_NOT_STICKY
        }

        if (useShizuku) {
            startShizukuCaptureSingleTry()
        } else {
            val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
            val data = if (Build.VERSION.SDK_INT >= 33) {
                intent?.getParcelableExtra("data", Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra<Intent>("data")
            }

            if (resultCode == Activity.RESULT_OK && data != null) {
                val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = projectionManager.getMediaProjection(resultCode, data)
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() { stopSelf() }
                }, android.os.Handler(android.os.Looper.getMainLooper()))
                startMediaProjectionCaptureSingleTry()
            } else {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startShizukuCaptureSingleTry() {
        scope.launch {
            delay(800)
            val pkg = getForegroundPackageName(applicationContext)
            val appName = pkg?.let { getAppName(applicationContext, it) }
            try {
                val bitmap = captureShizukuScreenshot()
                if (bitmap != null) {
                    val cropped = cropStatusBar(bitmap)
                    // 识别完成后再停止服务
                    recognizeAndStop(cropped, appName, pkg)
                } else {
                    stopSelf()
                }
            } catch (e: Exception) {
                stopSelf()
            }
        }
    }

    private fun captureShizukuScreenshot(): Bitmap? {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("screencap", "-p"), null, null) as rikka.shizuku.ShizukuRemoteProcess
            val bitmap = BitmapFactory.decodeStream(process.inputStream)
            process.waitFor()
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun getForegroundPackageName(context: Context): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 10,
            time
        )
        return stats
            ?.filter { it.lastTimeUsed > 0 && it.packageName != packageName && it.packageName != "com.android.systemui" }
            ?.maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }

    private fun getAppName(context: Context, packageName: String): String {
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun startMediaProjectionCaptureSingleTry() {
        val metrics = resources.displayMetrics
        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)

        try {
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, null
            )
        } catch (e: Exception) {
            Log.e("CaptureLog", "创建显示器失败", e)
            stopSelf()
            return
        }

        scope.launch {
            delay(800)
            val pkg = getForegroundPackageName(applicationContext)
            val appName = pkg?.let { getAppName(applicationContext, it) }
            try {
                val image = imageReader?.acquireLatestImage()
                if (image == null) {
                    stopSelf()
                    return@launch
                }

                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * image.width
                val bitmap = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                val cleanBitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                image.close()

                val cropped = cropStatusBar(cleanBitmap)
                // 识别完成后再停止服务
                recognizeAndStop(cropped, appName, pkg)
            } catch (e: Exception) {
                stopSelf()
            }
        }
    }

    private fun cropStatusBar(src: Bitmap): Bitmap {
        val statusBarHeight = 150
        val sideMargin = (src.width * 0.05).toInt()
        val targetWidth = (src.width * 0.9).toInt()
        val targetHeight = (src.height * 0.81).toInt()
        return if (src.height > statusBarHeight + targetHeight && src.width > sideMargin + targetWidth) {
            Bitmap.createBitmap(
                src,
                sideMargin,       // x: 从左边 10% 处开始
                statusBarHeight,  // y: 从状态栏高度处开始
                targetWidth,      // width: 宽度为原图的 80%
                targetHeight
            )
        } else {
            src
        }
    }

    private fun recognizeAndStop(bitmap: Bitmap, sourceApp: String?, sourcePkg: String?) {
        scope.launch {
            try {
                Log.d("CaptureLog", "开始识别...")
                val helper = TextRecognitionHelper(applicationContext)
                val result = helper.recognizeAll(bitmap, sourceApp, sourcePkg)
                helper.close()

                if (result.code != null) {
                    val screenshotFile = File(filesDir, "screenshots/${System.currentTimeMillis()}.png")
                    screenshotFile.parentFile?.mkdirs()
                    FileOutputStream(screenshotFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

                    val order = OrderEntity(
                        takeoutCode = result.code,
                        qrCodeData = result.qr,
                        screenshotPath = screenshotFile.absolutePath,
                        recognizedText = "自动识别",
                        orderType = result.type,
                        brandName = result.brand,
                        fullText = result.fullText,
                        sourceApp = sourceApp,
                        sourcePackage = sourcePkg,
                        pickupLocation = result.pickupLocation
                    )
                    OrderDatabase.getDatabase(applicationContext).orderDao().insert(order)
                    withContext(Dispatchers.Main) {
                        NotificationHelper(applicationContext).showPromotedLiveUpdate(order, result.brand)
                        Toast.makeText(applicationContext, "识别成功：${result.code}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.d("CaptureLog", "未识别到取件码")
                }
            } catch (e: Exception) {
                Log.e("CaptureLog", "识别异常", e)
            } finally {
                bitmap.recycle()
                // 识别完成后停止服务
                stopSelf()
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = "capture_service"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "正在识别", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("正在扫描屏幕")
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        virtualDisplay?.release()
        mediaProjection?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}