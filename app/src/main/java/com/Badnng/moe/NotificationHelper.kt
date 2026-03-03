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

    fun showPromotedLiveUpdate(order: OrderEntity, detectedBrand: String? = null) {
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
            putExtra("highlight_order_id", order.id)
            putExtra("from_notification", true)
        }
        val viewPendingIntent = PendingIntent.getActivity(
            context, order.id.hashCode() + 1, viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val qrDetailIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("highlight_order_id", order.id)
            putExtra("show_qr_detail", true)
            putExtra("order_id", order.id)
            putExtra("from_notification", true)
        }
        val qrDetailPendingIntent = PendingIntent.getActivity(
            context, order.id.hashCode() + 2, qrDetailIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val brandToUse = detectedBrand ?: order.brandName
        val iconRes = getBrandIcon(brandToUse, order.orderType)

        val builder = Notification.Builder(context, channelId)
            .setContentTitle("取餐提醒 - ${brandToUse ?: "新订单"}")
            .setContentText("取餐码: ${order.takeoutCode}")
            .setSmallIcon(iconRes)
            .setOngoing(true) 
            .setContentIntent(viewPendingIntent)
            .setStyle(Notification.BigTextStyle().bigText("取餐码: ${order.takeoutCode}"))
            .addAction(Notification.Action.Builder(null, "已完成", completePendingIntent).build())
            
        if (!order.qrCodeData.isNullOrEmpty()) {
             builder.addAction(Notification.Action.Builder(null, "展示二维码", qrDetailPendingIntent).build())
        }
            
        if (Build.VERSION.SDK_INT >= 35) {
            val extras = Bundle()
            extras.putBoolean("android.requestPromotedOngoing", true)
            builder.addExtras(extras)
            try {
                if (Build.VERSION.SDK_INT >= 36) {
                    builder.setShortCriticalText("${brandToUse ?: "取餐"}: ${order.takeoutCode}")
                }
            } catch (e: Exception) {}
        }

        notificationManager.notify(order.id.hashCode(), builder.build())
    }

    private fun getBrandIcon(brandName: String?, orderType: String): Int {
        // 关键修复：直接显式映射 R.drawable，不使用反射以避免混淆失效
        return when (brandName) {
            "麦当劳" -> R.drawable.ic_mcdonalds
            "肯德基", "KFC" -> R.drawable.ic_kfc
            "瑞幸" -> R.drawable.ic_luckin
            "喜茶" -> R.drawable.ic_heytea
            "星巴克" -> R.drawable.ic_starbucks
            "霸王茶姬" -> R.drawable.ic_chagee
            "古茗" -> R.drawable.ic_goodme
            else -> if (orderType == "饮品") R.drawable.ic_drink else R.drawable.ic_restaurant
        }
    }

    fun cancelNotification(orderId: String) {
        notificationManager.cancel(orderId.hashCode())
    }
}
