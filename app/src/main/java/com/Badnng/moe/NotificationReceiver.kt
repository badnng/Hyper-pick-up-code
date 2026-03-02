package com.Badnng.moe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "ACTION_MARK_COMPLETED") {
            val orderId = intent.getStringExtra("order_id") ?: return
            
            // 使用协程在 IO 线程更新数据库
            val database = OrderDatabase.getDatabase(context)
            val orderDao = database.orderDao()
            val notificationHelper = NotificationHelper(context)

            CoroutineScope(Dispatchers.IO).launch {
                orderDao.markAsCompleted(orderId, System.currentTimeMillis())
                // 更新成功后取消通知
                notificationHelper.cancelNotification()
            }
        }
    }
}