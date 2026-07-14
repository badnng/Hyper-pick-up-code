package com.Badnng.moe.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.Badnng.moe.R
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.helper.DailyExpressGroupingHelper
import com.Badnng.moe.helper.NotificationHelper
import com.Badnng.moe.recognition.RecognitionRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmsRecognitionService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val smsText = intent?.getStringExtra("smsText")
        val sender = intent?.getStringExtra("sender") ?: "未知"
        val testMode = intent?.getBooleanExtra(EXTRA_TEST_MODE, false) == true

        if (testMode && !canRunSmsTest()) {
            Log.w("SmsRecognition", "测试短信被拒绝：短信识别未开启或权限不完整")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // 真实短信由广播在后台拉起，需要前台服务保证处理完成；设置页测试由前台
        // Activity 主动触发，不创建“正在识别短信”的临时通知。
        if (!testMode) {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        if (!smsText.isNullOrBlank()) {
            Log.d(
                "SmsRecognition",
                "Service 开始处理${if (testMode) "测试" else ""}短信：" +
                    "sender=$sender, length=${smsText.length}",
            )
            scope.launch {
                try {
                    processSms(smsText, sender, forceOffline = testMode)
                } catch (e: Exception) {
                    Log.e("SmsRecognition", "处理短信失败", e)
                }
                stopSelf()
            }
        } else {
            Log.d("SmsRecognition", "Service 无短信内容，停止")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private suspend fun processSms(
        smsText: String,
        sender: String,
        forceOffline: Boolean = false,
    ) {
        val router = RecognitionRouter(applicationContext)
        val results = if (forceOffline) {
            // 与“测试通知识别”一致，测试入口只验证本地识别链路，不消耗在线额度。
            router.recognizeTextOffline(smsText)
        } else {
            router.recognizeText(smsText).orders
        }

        Log.d("SmsRecognition", "识别结果：${results.size}个, codes=${results.map { it.code }}")

        if (results.isEmpty()) return

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
                recognizedText = smsText,
                orderType = result.type,
                brandName = result.brand,
                fullText = result.fullText,
                pickupLocation = result.pickupLocation,
                sourceApp = "短信识别",
                sourcePackage = sender
            )
            orderDao.insert(order)
            insertedOrders.add(order)
        }

        if (insertedOrders.isEmpty()) return

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
        }

        refreshedOrders.filter { it.groupId == null }.forEach { order ->
            notificationHelper.showPromotedLiveUpdate(order, order.brandName)
        }
    }

    private fun createNotification(): Notification {
        val channelId = "sms_recognition"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "短信识别", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("正在识别短信")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    private fun canRunSmsTest(): Boolean {
        val enabled = getSharedPreferences("settings", MODE_PRIVATE)
            .getBoolean("sms_recognition_enabled", false)
        return enabled &&
            checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_TEST_MODE = "smsRecognitionTestMode"
        private const val NOTIFICATION_ID = 1004
    }
}
