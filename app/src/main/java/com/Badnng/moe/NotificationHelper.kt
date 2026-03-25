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
    private val updateChannelId = "update_download_channel"
    private val updateNotificationId = 20260325

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

            val updateChannel = NotificationChannel(
                updateChannelId,
                "更新下载",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "用于显示更新包下载进度"
            }
            notificationManager.createNotificationChannel(updateChannel)
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

        val viewIntent = Intent(context, OrderQuickViewActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("order_id", order.id)
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
        val isExpress = order.orderType == "快递"
        val label = if (isExpress) "取件码" else "取餐码"

        val builder = Notification.Builder(context, channelId)
            .setContentTitle(if (isExpress) "快递待取 - ${brandToUse ?: "新包裹"}" else "取餐提醒 - ${brandToUse ?: "新订单"}")
            .setContentText("$label: ${order.takeoutCode}")
            .setSmallIcon(iconRes)
            .setOngoing(true)
            .setContentIntent(viewPendingIntent)
            .setStyle(Notification.BigTextStyle().bigText("$label: ${order.takeoutCode}"))
            .addAction(Notification.Action.Builder(null, "已完成", completePendingIntent).build())
            
        if (Build.VERSION.SDK_INT >= 35) {
            val extras = Bundle()
            extras.putBoolean("android.requestPromotedOngoing", true)
            builder.addExtras(extras)
            try {
                if (Build.VERSION.SDK_INT >= 36) {
                    builder.setShortCriticalText(" ${order.takeoutCode}")
                }
            } catch (e: Exception) {}
        }

        notificationManager.notify(order.id.hashCode(), builder.build())
    }

    private fun getBrandIcon(brandName: String?, orderType: String): Int {
        return when (brandName) {
            "麦当劳" -> R.drawable.ic_mcdonalds
            "肯德基", "KFC" -> R.drawable.ic_kfc
            "瑞幸" -> R.drawable.ic_luckin
            "喜茶" -> R.drawable.ic_heytea
            "星巴克" -> R.drawable.ic_starbucks
            "霸王茶姬" -> R.drawable.ic_chagee
            "古茗" -> R.drawable.ic_goodme
            "蜜雪冰城" -> R.drawable.ic_mixue
            else -> when (orderType) {
                "饮品" -> R.drawable.ic_drink
                "快递" -> R.drawable.ic_package
                else -> R.drawable.ic_restaurant
            }
        }
    }

    fun cancelNotification(orderId: String) {
        notificationManager.cancel(orderId.hashCode())
    }

    fun showGroupNotification(group: OrderGroup, orders: List<OrderEntity>) {
        // 组详情意图
        val groupDetailIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("highlight_group_id", group.id)
            putExtra("show_group_detail", true)
            putExtra("from_notification", true)
        }
        val groupDetailPendingIntent = PendingIntent.getActivity(
            context, group.id.hashCode(), groupDetailIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 全部完成意图
        val completeAllIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = "ACTION_MARK_GROUP_COMPLETED"
            putExtra("group_id", group.id)
        }
        val completeAllPendingIntent = PendingIntent.getBroadcast(
            context, group.id.hashCode() + 1000, completeAllIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completedCount = orders.count { it.isCompleted }
        val totalCount = orders.size
        val brandToUse = group.brandName
        val iconRes = getBrandIcon(brandToUse, group.orderType)
        val isExpress = group.orderType == "快递"
        val label = if (isExpress) "取件码" else "取餐码"

        // 构建通知文本
        val codesText = orders.take(3).joinToString(", ") { it.takeoutCode }
        val moreText = if (orders.size > 3) " 等${orders.size}件" else ""
        val contentText = "$label: $codesText$moreText"

        val builder = Notification.Builder(context, channelId)
            .setContentTitle(if (isExpress) "快递待取 - ${brandToUse ?: "新包裹"}" else "取餐提醒 - ${brandToUse ?: "新订单"}")
            .setContentText(contentText)
            .setSmallIcon(iconRes)
            .setOngoing(true)
            .setContentIntent(groupDetailPendingIntent)
            .setStyle(Notification.BigTextStyle().bigText("$contentText\n已完成 $completedCount/$totalCount"))
            .addAction(Notification.Action.Builder(null, "全部完成", completeAllPendingIntent).build())

        if (Build.VERSION.SDK_INT >= 35) {
            val extras = Bundle()
            extras.putBoolean("android.requestPromotedOngoing", true)
            builder.addExtras(extras)
            try {
                if (Build.VERSION.SDK_INT >= 36) {
                    builder.setShortCriticalText(" ${orders.size}件")
                }
            } catch (e: Exception) {}
        }

        notificationManager.notify(group.id.hashCode(), builder.build())
    }

    fun cancelGroupNotification(groupId: Long) {
        notificationManager.cancel(groupId.hashCode())
    }

    fun showUpdateDownloadNotification(versionName: String, progress: Float, isPaused: Boolean = false) {
        val clampedProgress = progress.coerceIn(0f, 1f)
        val percent = (clampedProgress * 100).toInt()
        val contentIntent = PendingIntent.getActivity(
            context,
            updateNotificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(context, updateChannelId)
            .setContentTitle(if (isPaused) "更新下载已暂停" else "正在后台下载更新")
            .setContentText("v$versionName  $percent%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setProgress(100, percent, false)

        if (Build.VERSION.SDK_INT >= 35) {
            val extras = Bundle()
            extras.putBoolean("android.requestPromotedOngoing", true)
            builder.addExtras(extras)
            try {
                if (Build.VERSION.SDK_INT >= 36) {
                    builder.setShortCriticalText(" $percent%")
                }
            } catch (_: Exception) {
            }
        }

        notificationManager.notify(updateNotificationId, builder.build())
    }

    fun cancelUpdateDownloadNotification() {
        notificationManager.cancel(updateNotificationId)
    }
}
