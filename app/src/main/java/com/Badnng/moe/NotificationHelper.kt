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
    private val notificationId = 1001

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "订单实况更新",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "用于在状态栏和锁屏显示订单实时进度"
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * 发送完全符合 Android 16+ 标准的“提升级”实况通知
     */
    fun showPromotedLiveUpdate(orderId: String, takeoutCode: String) {
        // 1. “已完成”操作：发送广播
        val completeIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = "ACTION_MARK_COMPLETED"
            putExtra("order_id", orderId)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context, orderId.hashCode(), completeIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. “查看取餐码”操作：打开应用
        val viewIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val viewPendingIntent = PendingIntent.getActivity(
            context, 0, viewIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(context, channelId)
            .setContentTitle("取餐提醒")
            .setContentText("订单号: $takeoutCode")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true) 
            // 改用 BigTextStyle 以取消进度条（那根线）
            .setStyle(Notification.BigTextStyle().bigText("订单号: $takeoutCode"))
            .addAction(Notification.Action.Builder(null, "已完成", completePendingIntent).build())
            .addAction(Notification.Action.Builder(null, "查看取餐码", viewPendingIntent).build())
            
        // Android 16 (API 36) 核心提升 API
        if (Build.VERSION.SDK_INT >= 36) {
            val extras = Bundle()
            // EXTRA_REQUEST_PROMOTED_ONGOING 对应 key 为 "android.requestPromotedOngoing"
            extras.putBoolean("android.requestPromotedOngoing", true)
            builder.addExtras(extras)
            
            // 设置状态栏文本
            builder.setShortCriticalText("取件: $takeoutCode")
        } else {
            // 旧版本兼容
            builder.setCategory(Notification.CATEGORY_PROGRESS)
        }

        notificationManager.notify(notificationId, builder.build())
    }

    fun cancelNotification() {
        notificationManager.cancel(notificationId)
    }
}