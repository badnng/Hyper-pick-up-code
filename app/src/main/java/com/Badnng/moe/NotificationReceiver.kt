package com.Badnng.moe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val database = OrderDatabase.getDatabase(context)
        val orderDao = database.orderDao()
        val orderGroupDao = database.orderGroupDao()
        val notificationHelper = NotificationHelper(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    "ACTION_MARK_COMPLETED" -> {
                        val orderId = intent.getStringExtra("order_id") ?: return@launch
                        orderDao.markAsCompleted(orderId, System.currentTimeMillis())
                        notificationHelper.cancelNotification(orderId)
                    }

                    "ACTION_MARK_GROUP_COMPLETED" -> {
                        val groupId = intent.getLongExtra("group_id", -1L)
                        if (groupId == -1L) return@launch

                        val completedTime = System.currentTimeMillis()
                        orderGroupDao.markGroupAsCompleted(groupId, completedTime)
                        orderGroupDao.markAllOrdersInGroupCompleted(groupId, completedTime)
                        notificationHelper.cancelGroupNotification(groupId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
