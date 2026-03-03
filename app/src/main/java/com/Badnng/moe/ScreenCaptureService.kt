package com.Badnng.moe

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val recognitionHelper = TextRecognitionHelper()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra<Intent>("data")
        }

        if (resultCode == Activity.RESULT_OK && data != null) {
            startForeground(1001, createNotification())
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            
            if (mediaProjection != null) {
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        stopSelf()
                    }
                }, android.os.Handler(android.os.Looper.getMainLooper()))
                
                startCaptureLoop()
            } else {
                stopSelf()
            }
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startCaptureLoop() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        try {
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, null
            )
        } catch (e: Exception) {
            stopSelf()
            return
        }

        scope.launch {
            while (isActive) {
                delay(1500)
                val image = imageReader?.acquireLatestImage() ?: continue
                
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * image.width
                
                val bitmap = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                
                val cleanBitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                image.close()

                val (code, qr, type) = recognitionHelper.recognizeAll(cleanBitmap)
                
                // 只要识别到取餐码，就创建订单并停止服务
                if (code != null) {
                    val order = OrderEntity(
                        takeoutCode = code,
                        qrCodeData = qr,
                        screenshotPath = ScreenshotHelper(applicationContext).saveBitmap(cleanBitmap),
                        recognizedText = "自动识别",
                        orderType = type
                    )
                    
                    OrderDatabase.getDatabase(applicationContext).orderDao().insert(order)
                    
                    withContext(Dispatchers.Main) {
                        NotificationHelper(applicationContext).showPromotedLiveUpdate(order)
                        Toast.makeText(applicationContext, "识别成功：$code", Toast.LENGTH_SHORT).show()
                    }
                    
                    // 关键：识别到后立即停止识别循环并关闭服务
                    stopSelf()
                    break
                }
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = "capture_service"
        val manager = getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "识别中", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("正在扫描屏幕")
            .setContentText("请保持在订单页面")
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
