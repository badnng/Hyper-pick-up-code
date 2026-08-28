package com.Badnng.moe.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.helper.DailyExpressGroupingHelper
import com.Badnng.moe.helper.NotificationHelper
import com.Badnng.moe.recognition.RecognizedOrderFactory
import com.Badnng.moe.recognition.RecognitionRouter
import com.Badnng.moe.recognition.RecognitionTextSource
import com.Badnng.moe.recognition.RecognitionTrigger
import com.Badnng.moe.service.SmsRecognitionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmsRecognitionReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("sms_recognition_enabled", false)
        ) {
            Log.d("SmsRecognition", "短信识别未开启，忽略系统短信广播")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: "未知"
        val fullText = messages.joinToString("") { it.messageBody ?: "" }
        if (fullText.isBlank()) return

        Log.d("SmsRecognition", "收到短信：$sender, 长度：${fullText.length}")

        // 优先启动前台服务（不受 10 秒限制）
        try {
            val serviceIntent = Intent(context, SmsRecognitionService::class.java).apply {
                putExtra("smsText", fullText)
                putExtra("sender", sender)
            }
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(serviceIntent) else context.startService(serviceIntent)
            return
        } catch (e: Exception) {
            Log.e("SmsRecognition", "启动服务失败，使用 goAsync 兜底: ${e.message}")
        }

        // 兜底：goAsync + 自处理（进程刚重建时 service 可能无法启动）
        val pendingResult = goAsync()
        scope.launch {
            try {
                processSms(context.applicationContext, fullText, sender)
            } catch (e: Exception) {
                Log.e("SmsRecognition", "处理短信失败", e)
            }
            pendingResult.finish()
        }
    }

    private suspend fun processSms(context: Context, smsText: String, sender: String) {
        withContext(Dispatchers.IO) {
            val routedResult = RecognitionRouter(context).recognizeText(
                smsText,
                RecognitionTextSource.Sms,
                RecognitionTrigger.SMS,
            )
            val results = routedResult.orders

            Log.d("SmsRecognition", "识别结果：${results.size}个, codes=${results.map { it.code }}")

            if (results.isEmpty()) return@withContext

            val db = OrderDatabase.getDatabase(context)
            val orderDao = db.orderDao()
            val groupDao = db.orderGroupDao()
            val insertedOrders = mutableListOf<OrderEntity>()

            for (result in results) {
                if (result.code == null) continue
                val order = RecognizedOrderFactory.fromRecognition(
                    result = result,
                    metadata = routedResult.metadata,
                    screenshotPath = "",
                    recognizedText = smsText,
                    sourceApp = "短信识别",
                    sourcePackage = sender,
                ) ?: continue
                orderDao.insert(order)
                insertedOrders.add(order)
                com.Badnng.moe.wearable.WearableSyncManager.notifyOrderSaved(context, order)
            }

            if (insertedOrders.isEmpty()) return@withContext

            DailyExpressGroupingHelper.regroupPendingExpressByDay(orderDao, groupDao, context)

            val notificationHelper = NotificationHelper(context)
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
            }

            refreshedOrders.filter { it.groupId == null }.forEach { order ->
                notificationHelper.showPromotedLiveUpdate(order, order.brandName)
            }
        }
    }
}
