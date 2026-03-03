package com.Badnng.moe

import android.app.*
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
import java.io.InputStream

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val recognitionHelper = TextRecognitionHelper()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val useShizuku = intent?.getBooleanExtra("use_shizuku", false) ?: false
        
        // 关键修复：Android 14+ 必须显式指定前台服务类型
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1001, 
                createNotification(), 
                if (useShizuku) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(1001, createNotification())
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
            delay(1500)
            val bitmap = captureShizukuScreenshot()
            if (bitmap != null) {
                val cropped = cropStatusBar(bitmap)
                val result = processRecognize(cropped)
                if (!result) stopSelf()
            } else {
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

    private fun startMediaProjectionCaptureSingleTry() {
        val metrics = resources.displayMetrics
        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        
        try {
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, null
            )
        } catch (e: Exception) {
            stopSelf()
            return
        }

        scope.launch {
            delay(1500)
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
            val result = processRecognize(cropped)
            if (!result) stopSelf()
        }
    }

    private fun cropStatusBar(src: Bitmap): Bitmap {
        return try {
            val statusBarHeight = 150
            if (src.height > statusBarHeight) {
                Bitmap.createBitmap(src, 0, statusBarHeight, src.width, src.height - statusBarHeight)
            } else src
        } catch (e: Exception) {
            src
        }
    }

    private suspend fun processRecognize(bitmap: Bitmap): Boolean {
        val result = recognitionHelper.recognizeAll(bitmap)
        if (result.code != null) {
            val order = OrderEntity(
                takeoutCode = result.code,
                qrCodeData = result.qr,
                screenshotPath = ScreenshotHelper(applicationContext).saveBitmap(bitmap),
                recognizedText = "自动识别",
                orderType = result.type,
                brandName = result.brand
            )
            OrderDatabase.getDatabase(applicationContext).orderDao().insert(order)
            withContext(Dispatchers.Main) {
                NotificationHelper(applicationContext).showPromotedLiveUpdate(order, result.brand)
                Toast.makeText(applicationContext, "识别成功：${result.code}", Toast.LENGTH_SHORT).show()
            }
            stopSelf()
            return true
        }
        return false
    }

    private fun createNotification(): Notification {
        val channelId = "capture_service"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "识别中", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("正在扫描屏幕")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        virtualDisplay?.release()
        mediaProjection?.stop()
        recognitionHelper.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
