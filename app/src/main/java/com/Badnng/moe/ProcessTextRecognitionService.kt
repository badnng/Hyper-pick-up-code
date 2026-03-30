package com.Badnng.moe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProcessTextRecognitionService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        
        val selectedText = intent?.getStringExtra("selectedText")
        
        if (!selectedText.isNullOrBlank()) {
            scope.launch {
                try {
                    processText(selectedText)
                } catch (e: Exception) {
                    Log.e("ProcessTextRecognition", "Error processing text", e)
                } finally {
                    stopSelf()
                }
            }
        } else {
            stopSelf()
        }
        
        return START_NOT_STICKY
    }
    
    private suspend fun processText(selectedText: String) {
        val helper = TextRecognitionHelper(applicationContext)
        // recognizeFromText 不需要 OCR 初始化
        val result = helper.recognizeFromText(selectedText)
        helper.close()
        
        Log.d("ProcessTextRecognition", "Result: code=${result.code}, type=${result.type}, brand=${result.brand}")
        
        if (result.code != null) {
            val order = OrderEntity(
                takeoutCode = result.code,
                qrCodeData = result.qr,
                screenshotPath = "",
                recognizedText = selectedText,
                orderType = result.type,
                brandName = result.brand,
                fullText = result.fullText,
                pickupLocation = result.pickupLocation,
                sourceApp = "文字选择"
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
    }
    
    private fun createNotification(): Notification {
        val channelId = "process_text_recognition"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "划词识别", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("正在识别文字")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    companion object {
        private const val NOTIFICATION_ID = 1003
    }
}
