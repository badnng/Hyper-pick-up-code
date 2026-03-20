package com.Badnng.moe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareRecognitionService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        
        val imageUri = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("imageUri", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("imageUri")
        }
        
        if (imageUri != null) {
            scope.launch {
                try {
                    processImage(imageUri)
                } catch (e: Exception) {
                    Log.e("ShareRecognition", "Error processing image", e)
                } finally {
                    stopSelf()
                }
            }
        } else {
            stopSelf()
        }
        
        return START_NOT_STICKY
    }
    
    private suspend fun processImage(imageUri: Uri) {
        val inputStream = contentResolver.openInputStream(imageUri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        
        if (bitmap != null) {
            val croppedBitmap = cropStatusBar(bitmap)

            val helper = TextRecognitionHelper(applicationContext)
            val result = helper.recognizeAll(croppedBitmap)
            helper.close()
            
            Log.d("ShareRecognition", "Result: code=${result.code}, type=${result.type}, brand=${result.brand}")
            
            if (result.code != null) {
                val screenshotFile = java.io.File(filesDir, "screenshots/shared_${System.currentTimeMillis()}.png")
                screenshotFile.parentFile?.mkdirs()
                val outputStream = java.io.FileOutputStream(screenshotFile)
                croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.close()
                
                val order = OrderEntity(
                    takeoutCode = result.code,
                    qrCodeData = result.qr,
                    screenshotPath = screenshotFile.absolutePath,
                    recognizedText = "分享识别",
                    orderType = result.type,
                    brandName = result.brand,
                    fullText = result.fullText,
                    pickupLocation = result.pickupLocation,
                    sourceApp = "分享识别"
                )
                
                OrderDatabase.getDatabase(applicationContext).orderDao().insert(order)
                NotificationHelper(applicationContext).showPromotedLiveUpdate(order, result.brand)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "识别成功：${result.code}", Toast.LENGTH_SHORT).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "未识别到取件码", Toast.LENGTH_SHORT).show()
                }
            }
            
            bitmap.recycle()
            if (croppedBitmap != bitmap) croppedBitmap.recycle()
        }
    }
    
    private fun cropStatusBar(src: Bitmap): Bitmap {
        val statusBarHeight = 150
        val sideMargin = (src.width * 0.02).toInt()
        val targetWidth = (src.width * 0.92).toInt()
        val targetHeight = (src.height * 0.81).toInt()
        return if (src.height > statusBarHeight + targetHeight && src.width > sideMargin + targetWidth) {
            Bitmap.createBitmap(src, sideMargin, statusBarHeight, targetWidth, targetHeight)
        } else {
            src
        }
    }
    
    private fun createNotification(): Notification {
        val channelId = "share_recognition"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "分享识别", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("正在识别截图")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    companion object {
        private const val NOTIFICATION_ID = 1002
    }
}