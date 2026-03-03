package com.Badnng.moe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

class NotificationHelper(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "promoted_live_update_channel"
    private val orderNotificationId = 2001 // 关键：使用独立 ID，避免被 Service 销毁

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "订单实况更新",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "用于在状态栏和锁屏显示订单实时进度"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showPromotedLiveUpdate(order: OrderEntity) {
        val completeIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = "ACTION_MARK_COMPLETED"
            putExtra("order_id", order.id)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context, order.id.hashCode(), completeIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val viewIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("show_qr_detail", true)
            putExtra("order_id", order.id)
            putExtra("from_notification", true)
        }
        val viewPendingIntent = PendingIntent.getActivity(
            context, order.id.hashCode() + 1, viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val iconRes = if (order.orderType == "饮品") R.drawable.ic_drink else R.drawable.ic_restaurant

        val builder = Notification.Builder(context, channelId)
            .setContentTitle("取餐提醒")
            .setContentText("取餐码: ${order.takeoutCode}")
            .setSmallIcon(iconRes)
            .setOngoing(true) 
            .setStyle(Notification.BigTextStyle().bigText("取餐码: ${order.takeoutCode}"))
            .addAction(Notification.Action.Builder(null, "已完成", completePendingIntent).build())
            
        if (!order.qrCodeData.isNullOrEmpty()) {
             builder.addAction(Notification.Action.Builder(null, "展示二维码", viewPendingIntent).build())
             builder.setContentIntent(viewPendingIntent)
        }
            
        // 针对 Android 15 (API 35) 及以上版本的实况窗优化
        if (Build.VERSION.SDK_INT >= 35) {
            val extras = Bundle()
            extras.putBoolean("android.requestPromotedOngoing", true)
            builder.addExtras(extras)
            try {
                // 如果是 Android 16 (API 36) 预览版或更高
                if (Build.VERSION.SDK_INT >= 36) {
                    builder.setShortCriticalText("取餐: ${order.takeoutCode}")
                }
            } catch (e: Exception) {}
        }

        notificationManager.notify(orderNotificationId, builder.build())
    }

    fun cancelNotification() {
        notificationManager.cancel(orderNotificationId)
    }
}
