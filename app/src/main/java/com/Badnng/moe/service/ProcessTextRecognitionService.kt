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
import com.Badnng.moe.ocr.RecognitionResult
import com.Badnng.moe.recognition.RecognizedOrderFactory
import com.Badnng.moe.recognition.RecognitionCorrectionDetector
import com.Badnng.moe.recognition.RecognitionCorrectionStore
import com.Badnng.moe.recognition.RecognitionExecutionMetadata
import com.Badnng.moe.recognition.RecognitionRouter
import com.Badnng.moe.recognition.RecognitionTrigger
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
        val routedResult = RecognitionRouter(applicationContext).recognizeText(
            selectedText,
            trigger = RecognitionTrigger.PROCESS_TEXT,
        )
        val results = routedResult.orders

        Log.d("ProcessTextRecognition", "识别结果：${results.size}个, codes=${results.map { it.code }}")
        results.forEach { r ->
            AppLogger.recognition("code=${r.code}, type=${r.type}, brand=${r.brand}, pickup=${r.pickupLocation}")
        }

        val unrecognizedExplicitCodes = RecognitionCorrectionDetector.findUnrecognizedCodes(
            fullText = selectedText,
            recognizedCodes = results.mapNotNull { it.code },
        )

        if (results.isEmpty()) {
            val draftSaved = saveCorrectionDraft(selectedText, results, routedResult.metadata)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    applicationContext,
                    if (draftSaved) "识别失败，已加入纠正识别" else "未识别到取件码",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            return
        }

        val partialDraftSaved = if (unrecognizedExplicitCodes.isNotEmpty()) {
            saveCorrectionDraft(selectedText, emptyList(), routedResult.metadata)
        } else {
            false
        }

        val db = OrderDatabase.getDatabase(applicationContext)
        val orderDao = db.orderDao()
        val groupDao = db.orderGroupDao()
        val insertedOrders = mutableListOf<OrderEntity>()

        for (result in results) {
            if (result.code == null) continue
            val order = RecognizedOrderFactory.fromRecognition(
                result = result,
                metadata = routedResult.metadata,
                screenshotPath = "",
                recognizedText = selectedText,
                sourceApp = "文字选择",
            ) ?: continue
            orderDao.insert(order)
            insertedOrders.add(order)
            com.Badnng.moe.wearable.WearableSyncManager.notifyOrderSaved(applicationContext, order)
        }

        if (insertedOrders.isEmpty()) {
            val draftSaved = saveCorrectionDraft(selectedText, results, routedResult.metadata)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    applicationContext,
                    if (draftSaved) "识别失败，已加入纠正识别" else "未识别到取件码",
                    Toast.LENGTH_SHORT,
                ).show()
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
                Toast.makeText(
                    applicationContext,
                    if (partialDraftSaved) "部分取件码未识别，已加入纠正识别" else "新识别取件码已自动整理",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } else {
            refreshedOrders.forEach { order ->
                notificationHelper.showPromotedLiveUpdate(order, order.brandName)
            }
            val firstCode = refreshedOrders.firstOrNull()?.takeoutCode
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    applicationContext,
                    if (partialDraftSaved) "部分取件码未识别，已加入纠正识别"
                    else if (firstCode != null) "识别成功：$firstCode" else "识别成功",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    
    private suspend fun saveCorrectionDraft(
        selectedText: String,
        results: List<RecognitionResult>,
        metadata: RecognitionExecutionMetadata,
    ): Boolean {
        val draftResult = results.firstOrNull { it.code == null }?.copy(fullText = selectedText)
            ?: RecognitionResult(
                code = null,
                qr = null,
                type = "餐食",
                brand = null,
                fullText = selectedText,
            )
        return RecognitionCorrectionStore.saveTextDraft(
            context = applicationContext,
            result = draftResult,
            metadata = metadata,
            recognizedText = selectedText,
            sourceApp = "文字选择",
        )
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
