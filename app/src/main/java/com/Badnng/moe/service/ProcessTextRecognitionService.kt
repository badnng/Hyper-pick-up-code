package com.Badnng.moe.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.Badnng.moe.helper.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.Badnng.moe.R
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.helper.DailyExpressGroupingHelper
import com.Badnng.moe.helper.NotificationHelper
import com.Badnng.moe.recognition.RecognitionRouter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProcessTextRecognitionService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.service("ProcessTextRecognitionService onStartCommand")
        startForeground(NOTIFICATION_ID, createNotification())

        val selectedText = intent?.getStringExtra("selectedText")

        if (!selectedText.isNullOrBlank()) {
            AppLogger.service("ProcessTextRecognitionService text length=${selectedText.length}")
            scope.launch {
                try {
                    processText(selectedText)
                } catch (e: Exception) {
                    Log.e("ProcessTextRecognition", "Error processing text", e)
                    AppLogger.service("ProcessTextRecognitionService error: ${e.message}")
                }
                AppLogger.service("ProcessTextRecognitionService stopping")
                stopSelf()
            }
        } else {
            AppLogger.service("ProcessTextRecognitionService no text, stopping")
            stopSelf()
        }
        
        return START_NOT_STICKY
    }
    
    private suspend fun processText(selectedText: String) {
        val results = RecognitionRouter(applicationContext).recognizeText(selectedText).orders

        Log.d("ProcessTextRecognition", "识别结果：${results.size}个, codes=${results.map { it.code }}")
        results.forEach { r ->
            AppLogger.recognition("code=${r.code}, type=${r.type}, brand=${r.brand}, pickup=${r.pickupLocation}")
        }

        if (results.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, "未识别到取件码", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val db = OrderDatabase.getDatabase(applicationContext)
        val orderDao = db.orderDao()
        val groupDao = db.orderGroupDao()
        val insertedOrders = mutableListOf<OrderEntity>()

        for (result in results) {
            if (result.code == null) continue
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
            orderDao.insert(order)
            insertedOrders.add(order)
        }

        if (insertedOrders.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, "未识别到取件码", Toast.LENGTH_SHORT).show()
            }
            return
        }

        DailyExpressGroupingHelper.regroupPendingExpressByDay(orderDao, groupDao, this)

        val notificationHelper = NotificationHelper(applicationContext)
        val refreshedOrders = insertedOrders.mapNotNull { orderDao.getOrderById(it.id) }
        val groupedIds = refreshedOrders.mapNotNull { it.groupId }.toSet()

        if (groupedIds.isNotEmpty()) {
            groupedIds.forEach { groupId ->
                val group = groupDao.getGroupById(groupId) ?: return@forEach
                val groupOrders = orderDao.getAllOrdersList()
                    .filter { it.groupId == groupId && !it.isCompleted }
                    .sortedByDescending { it.createdAt }
                if (groupOrders.size >= 2) {
                    groupOrders.forEach { notificationHelper.cancelNotification(it.id) }
                    groupDao.updateOrderCount(groupId, groupOrders.size)
                    notificationHelper.showGroupNotification(group.copy(orderCount = groupOrders.size), groupOrders)
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, "新识别取件码已自动整理", Toast.LENGTH_SHORT).show()
            }
        } else {
            refreshedOrders.forEach { order ->
                notificationHelper.showPromotedLiveUpdate(order, order.brandName)
            }
            val firstCode = refreshedOrders.firstOrNull()?.takeoutCode
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, if (firstCode != null) "识别成功：$firstCode" else "识别成功", Toast.LENGTH_SHORT).show()
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
