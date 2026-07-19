package com.Badnng.moe.helper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.Badnng.moe.R
import com.Badnng.moe.activity.IdentityChooserActivity
import com.Badnng.moe.activity.MainActivity
import com.Badnng.moe.activity.OrderQuickViewActivity
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.data.db.OrderGroup
import com.Badnng.moe.receiver.NotificationReceiver

class NotificationHelper(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "promoted_live_update_channel"
    private val updateChannelId = "update_download_channel"
    private val updateNotificationId = 20260325
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun isIslandMode(): Boolean {
        return SuperIslandHelper.isDeviceSupported(context)
            && prefs.getString("notification_type", "native") == "island"
    }

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
        val isExpress = order.orderType == "快递"

        if (isIslandMode()) {
            val identityChooserPendingIntent = if (isExpress) {
                val identityChooserIntent = Intent(context, IdentityChooserActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("from_notification", true)
                }
                PendingIntent.getActivity(
                    context, order.id.hashCode() + 3000, identityChooserIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else null

            val islandNotification = SuperIslandHelper.buildPromotedNotification(
                context = context,
                channelId = channelId,
                order = order,
                brandName = brandToUse,
                isExpress = isExpress,
                completePendingIntent = completePendingIntent,
                viewPendingIntent = viewPendingIntent,
                identityChooserPendingIntent = identityChooserPendingIntent
            )
            notificationManager.notify(order.id.hashCode(), islandNotification)
        } else {
            val customIcon = getCustomIcon(brandToUse)
            val fallbackRes = BrandIconResolver.resolveBuiltinFallbackResId(context, brandToUse, order.orderType)
            val label = if (isExpress) "取件码" else "取餐码"

            val builder = Notification.Builder(context, channelId)
                .setContentTitle(if (isExpress) "快递待取 - ${brandToUse ?: "新包裹"}" else "取餐提醒 - ${brandToUse ?: "新订单"}")
                .setContentText("$label: ${order.takeoutCode}")
                .setSmallIcon(customIcon ?: android.graphics.drawable.Icon.createWithResource(context, fallbackRes))
                .setOngoing(true)
                .setContentIntent(viewPendingIntent)
                .setStyle(Notification.BigTextStyle().bigText("$label: ${order.takeoutCode}"))
                .addAction(Notification.Action.Builder(null, "已完成", completePendingIntent).build())

            if (isExpress) {
                val identityChooserIntent = Intent(context, IdentityChooserActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("from_notification", true)
                }
                val identityChooserPendingIntent = PendingIntent.getActivity(
                    context,
                    order.id.hashCode() + 3000,
                    identityChooserIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(Notification.Action.Builder(null, "身份码", identityChooserPendingIntent).build())
            }

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
    }

    private fun getCustomIcon(brandName: String?): android.graphics.drawable.Icon? {
        val bitmap = BrandIconResolver.resolveCustomIconBitmap(context, brandName) ?: return null
        return android.graphics.drawable.Icon.createWithBitmap(bitmap)
    }

    fun cancelNotification(orderId: String) {
        notificationManager.cancel(orderId.hashCode())
    }

    fun showGroupNotification(group: OrderGroup, orders: List<OrderEntity>) {
        // 先取消该组内所有单条订单的通知
        orders.forEach { cancelNotification(it.id) }

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
        val isExpress = group.orderType == "快递"

        if (isIslandMode()) {
            val identityChooserPendingIntent = if (isExpress) {
                val identityChooserIntent = Intent(context, IdentityChooserActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("from_notification", true)
                }
                PendingIntent.getActivity(
                    context, group.id.hashCode() + 2000, identityChooserIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else null

            val islandNotification = SuperIslandHelper.buildGroupNotification(
                context = context,
                channelId = channelId,
                group = group,
                orders = orders,
                isExpress = isExpress,
                completeAllPendingIntent = completeAllPendingIntent,
                groupDetailPendingIntent = groupDetailPendingIntent,
                identityChooserPendingIntent = identityChooserPendingIntent
            )
            notificationManager.notify(group.id.hashCode(), islandNotification)
        } else {
            val customIcon = getCustomIcon(brandToUse)
            val fallbackRes = BrandIconResolver.resolveBuiltinFallbackResId(context, brandToUse, group.orderType)
            val label = if (isExpress) "取件码" else "取餐码"
            val codesText = orders.take(3).joinToString(", ") { it.takeoutCode }
            val moreText = if (orders.size > 3) " 等${orders.size}件" else ""
            val contentText = "$label: $codesText$moreText"

            val builder = Notification.Builder(context, channelId)
                .setContentTitle(if (isExpress) "快递待取 - ${brandToUse ?: "新包裹"}" else "取餐提醒 - ${brandToUse ?: "新订单"}")
                .setContentText(contentText)
                .setSmallIcon(customIcon ?: android.graphics.drawable.Icon.createWithResource(context, fallbackRes))
                .setOngoing(true)
                .setContentIntent(groupDetailPendingIntent)
                .setStyle(Notification.BigTextStyle().bigText("$contentText\n已完成 $completedCount/$totalCount"))
                .addAction(Notification.Action.Builder(null, "全部完成", completeAllPendingIntent).build())

            if (isExpress) {
                val identityChooserIntent = Intent(context, IdentityChooserActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("from_notification", true)
                }
                val identityChooserPendingIntent = PendingIntent.getActivity(
                    context,
                    group.id.hashCode() + 2000,
                    identityChooserIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(Notification.Action.Builder(null, "身份码", identityChooserPendingIntent).build())
            }

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
    }

    fun cancelGroupNotification(groupId: Long) {
        notificationManager.cancel(groupId.hashCode())
    }

    private var lastNotifyTime = 0L
    private var lastNotifyPercent = -1

    fun showUpdateDownloadNotification(versionName: String, progress: Float?, isPaused: Boolean = false) {
        val now = System.currentTimeMillis()
        val clampedProgress = progress?.coerceIn(0f, 1f)
        val percent = clampedProgress?.let { (it * 100).toInt() }
        val progressKey = percent ?: -1
        // 限流：每 500ms 或进度变化 1% 才更新通知
        if (!isPaused && now - lastNotifyTime < 500 && progressKey == lastNotifyPercent) return
        lastNotifyTime = now
        lastNotifyPercent = progressKey

        val contentIntent = PendingIntent.getActivity(
            context,
            updateNotificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("show_update_download", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = if (isIslandMode() && clampedProgress != null) {
            SuperIslandHelper.buildUpdateDownloadNotification(
                context = context,
                channelId = updateChannelId,
                versionName = versionName,
                progress = clampedProgress,
                isPaused = isPaused,
                contentIntent = contentIntent,
                pauseResumeIntent = null
            )
        } else {
            val builder = Notification.Builder(context, updateChannelId)
                .setContentTitle(if (isPaused) "更新下载已暂停" else "正在后台下载更新")
                .setContentText(
                    if (percent == null) "v$versionName  正在连接下载服务器" else "v$versionName  $percent%"
                )
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setContentIntent(contentIntent)
                .setProgress(100, percent ?: 0, percent == null)

            if (Build.VERSION.SDK_INT >= 35) {
                val extras = Bundle()
                extras.putBoolean("android.requestPromotedOngoing", true)
                builder.addExtras(extras)
            }

            builder.build()
        }

        notificationManager.notify(updateNotificationId, notification)
    }

    fun cancelUpdateDownloadNotification() {
        notificationManager.cancel(updateNotificationId)
    }
}
