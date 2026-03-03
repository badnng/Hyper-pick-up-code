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
            
            val database = OrderDatabase.getDatabase(context)
            val orderDao = database.orderDao()
            val notificationHelper = NotificationHelper(context)

            CoroutineScope(Dispatchers.IO).launch {
                orderDao.markAsCompleted(orderId, System.currentTimeMillis())
                // 修复：传入 orderId 以取消对应的通知
                notificationHelper.cancelNotification(orderId)
            }
        }
    }
}
