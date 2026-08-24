package com.Badnng.moe.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.helper.NotificationHelper
import com.Badnng.moe.helper.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduledNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SCHEDULED_NOTIFICATION = "com.Badnng.moe.ACTION_SCHEDULED_NOTIFICATION"
        const val ACTION_SCHEDULED_NOTIFICATION_FIRED = "com.Badnng.moe.SCHEDULED_NOTIFICATION_FIRED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SCHEDULED_NOTIFICATION) return

        val requestCode = intent.getIntExtra("request_code", -1)
        val isGroup = intent.getBooleanExtra("is_group", false)

        NotificationScheduler.removeScheduledTime(context, requestCode)
        context.sendBroadcast(Intent(ACTION_SCHEDULED_NOTIFICATION_FIRED).setPackage(context.packageName))

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = OrderDatabase.getDatabase(context)
                if (isGroup) {
                    val groupId = intent.getLongExtra("group_id", -1)
                    if (groupId > 0) {
                        val group = db.orderGroupDao().getGroupById(groupId)
                        if (group != null) {
                            val orders = db.orderDao().getAllOrdersList().filter {
                                it.groupId == groupId && !it.isCompleted
                            }
                            if (orders.isNotEmpty() && !group.isCompleted) {
                                NotificationHelper(context).showGroupNotification(
                                    group.copy(orderCount = orders.size),
                                    orders,
                                )
                            } else {
                                NotificationHelper(context).cancelGroupNotification(groupId)
                            }
                        }
                    }
                } else {
                    val orderId = intent.getStringExtra("order_id") ?: return@launch
                    val order = db.orderDao().getOrderById(orderId)
                    if (order != null && !order.isCompleted) {
                        NotificationHelper(context).showPromotedLiveUpdate(order)
                    } else {
                        NotificationHelper(context).cancelNotification(orderId)
                    }
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
