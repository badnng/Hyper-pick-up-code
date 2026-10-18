package com.Badnng.moe.helper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.data.db.OrderGroup
import com.Badnng.moe.receiver.ScheduledNotificationReceiver
import org.json.JSONArray
import org.json.JSONObject

object NotificationScheduler {

    private const val PREFS_NAME = "scheduled_notifications"

    fun schedule(context: Context, order: OrderEntity, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = getPendingIntent(context, order.id.hashCode(), order, false, null)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
        saveScheduledTime(context, order.id.hashCode(), triggerAtMillis)
    }

    fun scheduleGroup(context: Context, group: OrderGroup, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = "group_${group.id}".hashCode()
        val pendingIntent = getPendingIntent(context, requestCode, null, true, group.id)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
        saveScheduledTime(context, requestCode, triggerAtMillis)
    }

    fun cancel(context: Context, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduledNotificationReceiver::class.java).apply {
            // action 参与 PendingIntent 身份匹配，必须与 schedule/getPendingIntent 一致。
            action = ScheduledNotificationReceiver.ACTION_SCHEDULED_NOTIFICATION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        removeScheduledTime(context, requestCode)
    }

    fun isScheduled(context: Context, requestCode: Int): Boolean {
        return getScheduledTime(context, requestCode) > 0
    }

    fun getScheduledTime(context: Context, requestCode: Int): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(requestCode.toString(), 0)
    }

    fun getOrderRequestCode(orderId: String): Int = orderId.hashCode()

    fun getGroupRequestCode(groupId: Long): Int = "group_$groupId".hashCode()

    private fun getPendingIntent(
        context: Context,
        requestCode: Int,
        order: OrderEntity?,
        isGroup: Boolean,
        groupId: Long?
    ): PendingIntent {
        val intent = Intent(context, ScheduledNotificationReceiver::class.java).apply {
            action = ScheduledNotificationReceiver.ACTION_SCHEDULED_NOTIFICATION
            putExtra("request_code", requestCode)
            putExtra("is_group", isGroup)
            if (order != null) {
                putExtra("order_id", order.id)
            }
            if (groupId != null) {
                putExtra("group_id", groupId)
            }
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun saveScheduledTime(context: Context, requestCode: Int, triggerAtMillis: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(requestCode.toString(), triggerAtMillis).apply()
    }

    fun removeScheduledTime(context: Context, requestCode: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(requestCode.toString()).apply()
    }
}
