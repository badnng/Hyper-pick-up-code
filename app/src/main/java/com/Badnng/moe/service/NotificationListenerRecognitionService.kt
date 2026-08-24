package com.Badnng.moe.service

import android.app.Notification
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.helper.DailyExpressGroupingHelper
import com.Badnng.moe.helper.NotificationHelper
import com.Badnng.moe.recognition.RecognizedOrderFactory
import com.Badnng.moe.recognition.RecognitionRouter
import com.Badnng.moe.recognition.RecognitionTextSource
import com.Badnng.moe.recognition.RecognitionTrigger
import com.Badnng.moe.wearable.WearableSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class NotificationListenerRecognitionService : NotificationListenerService() {

    private var scope: CoroutineScope? = null
    private lateinit var prefs: SharedPreferences
    private val dedupMap = ConcurrentHashMap<String, Long>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        Log.d("NotificationListener", "监听服务已连接")
    }

    override fun onListenerDisconnected() {
        scope?.cancel()
        scope = null
        super.onListenerDisconnected()
        Log.d("NotificationListener", "监听服务已断开")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (!prefs.getBoolean("notification_listener_recognition_enabled", false)) return
        if (sbn.packageName == packageName) return
        if (!isAppEnabled(sbn.packageName)) return
        if (isDuplicate(sbn)) return

        val combinedText = extractNotificationText(sbn)
        if (combinedText.isBlank()) return

        val appLabel = getAppLabel(sbn.packageName)
        Log.d("NotificationListener", "收到通知: pkg=${sbn.packageName}, app=$appLabel, textLen=${combinedText.length}")

        scope?.launch {
            try {
                processNotificationText(combinedText, sbn.packageName, appLabel)
            } catch (e: Exception) {
                Log.e("NotificationListener", "处理通知失败", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    private fun extractNotificationText(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras ?: return ""
        val parts = mutableListOf<String>()

        extras.getCharSequence(Notification.EXTRA_TITLE)?.let { parts.add(it.toString()) }
        extras.getCharSequence(Notification.EXTRA_TEXT)?.let { parts.add(it.toString()) }
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { parts.add(it.toString()) }
        extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.let { parts.add(it.toString()) }

        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.let { lines ->
            lines.filterNotNull().filter { it.isNotBlank() }.forEach { parts.add(it.toString()) }
        }

        return parts.joinToString(" ")
    }

    private fun isDuplicate(sbn: StatusBarNotification): Boolean {
        val text = extractNotificationText(sbn)
        val key = "${sbn.packageName}:${text.hashCode()}"
        val now = System.currentTimeMillis()
        val lastTime = dedupMap[key]
        if (lastTime != null && now - lastTime < 5000) return true
        dedupMap[key] = now
        if (dedupMap.size > 100) {
            val oldest = dedupMap.entries.sortedBy { it.value }.take(dedupMap.size - 80)
            oldest.forEach { dedupMap.remove(it.key) }
        }
        return false
    }

    private suspend fun processNotificationText(text: String, pkgName: String, appLabel: String) {
        val routedResult = RecognitionRouter(applicationContext).recognizeText(
            text,
            RecognitionTextSource.Notification,
            RecognitionTrigger.NOTIFICATION,
        )
        val results = routedResult.orders

        Log.d("NotificationListener", "识别结果: ${results.size}个, codes=${results.map { it.code }}")

        if (results.isEmpty()) return

        val db = OrderDatabase.getDatabase(applicationContext)
        val orderDao = db.orderDao()
        val groupDao = db.orderGroupDao()
        val insertedOrders = mutableListOf<OrderEntity>()

        for (result in results) {
            if (result.code == null) continue
            val order = RecognizedOrderFactory.fromRecognition(
                result = result,
                metadata = routedResult.metadata,
                screenshotPath = "",
                recognizedText = text,
                sourceApp = appLabel,
                sourcePackage = pkgName,
            ) ?: continue
            orderDao.insert(order)
            insertedOrders.add(order)
        }

        if (insertedOrders.isEmpty()) return

        DailyExpressGroupingHelper.regroupPendingExpressByDay(orderDao, groupDao, this)

        val notificationHelper = NotificationHelper(applicationContext)
        val wearableSync = WearableSyncManager.getInstance(applicationContext)
        val refreshedOrders = insertedOrders.mapNotNull { orderDao.getOrderById(it.id) }
        val groupedIds = refreshedOrders.mapNotNull { it.groupId }.toSet()

        if (groupedIds.isNotEmpty()) {
            groupedIds.forEach { groupId ->
                val group = groupDao.getGroupById(groupId) ?: return@forEach
                val groupOrders = orderDao.getAllOrdersList()
                    .filter { it.groupId == groupId && !it.isCompleted }
                    .sortedByDescending { it.createdAt }
                if (groupOrders.size >= 2) {
                    groupOrders.forEach { notificationHelper.cancelNotification(it.id) }
                    groupDao.updateOrderCount(groupId, groupOrders.size)
                    notificationHelper.showGroupNotification(group.copy(orderCount = groupOrders.size), groupOrders)
                    val type = groupOrders.firstOrNull()?.orderType?.takeIf { it.isNotBlank() } ?: "取餐码"
                    val firstOrder = groupOrders.first()
                    val groupTitle = firstOrder.brandName?.takeIf { it.isNotBlank() } ?: "新的${type}通知"
                    val hasQr = groupOrders.any { !it.qrCodeData.isNullOrBlank() }
                    val groupMessage = buildString {
                        append("${groupOrders.size}个$type")
                        if (hasQr) append("，可到手表端查看二维码")
                    }
                    wearableSync.sendNewOrderNotify(
                        firstOrder.id,
                        groupTitle,
                        groupMessage
                    )
                }
            }
        }

        refreshedOrders.filter { it.groupId == null }.forEach { order ->
            notificationHelper.showPromotedLiveUpdate(order, order.brandName)
            val title = order.brandName?.takeIf { it.isNotBlank() } ?: "新的取餐码"
            val message = buildString {
                append(order.takeoutCode)
                if (!order.qrCodeData.isNullOrBlank()) append("，二维码已同步，可到手表端查看")
            }
            wearableSync.sendNewOrderNotify(order.id, title, message)
        }
    }

    // ─────────── Per-App 开关管理 ───────────

    private fun isAppEnabled(pkgName: String): Boolean {
        return isAppEnabled(applicationContext, pkgName)
    }

    private fun getAppLabel(pkgName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(pkgName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            pkgName
        }
    }

    companion object {
        data class AppInfo(val packageName: String, val label: String)

        fun getAllInstalledApps(
            context: Context,
            includeSystemApps: Boolean = false
        ): List<AppInfo> {
            val pm = context.packageManager
            val selfPkg = context.packageName
            val systemAppFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
            return pm.getInstalledApplications(0)
                .filter { it.packageName != selfPkg }
                .filter { app ->
                    pm.getLaunchIntentForPackage(app.packageName) != null ||
                            includeSystemApps && (app.flags and systemAppFlags) != 0
                }
                .map { AppInfo(it.packageName, pm.getApplicationLabel(it).toString()) }
                .sortedBy { it.label.lowercase() }
        }

        fun getEnabledApps(context: Context): Map<String, Boolean> {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val appsJson = prefs.getString("notification_listener_apps", null) ?: return emptyMap()
            return try {
                val obj = JSONObject(appsJson)
                obj.keys().asSequence().associateWith { obj.getBoolean(it) }
            } catch (e: Exception) {
                emptyMap()
            }
        }

        fun isAppEnabled(context: Context, pkgName: String): Boolean {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val appsJson = prefs.getString("notification_listener_apps", null) ?: return false
            return try {
                JSONObject(appsJson).optBoolean(pkgName, false)
            } catch (e: Exception) {
                false
            }
        }

        fun setAppEnabled(context: Context, pkgName: String, enabled: Boolean) {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val appsJson = prefs.getString("notification_listener_apps", null)
            val obj = if (appsJson != null) {
                try { JSONObject(appsJson) } catch (e: Exception) { JSONObject() }
            } else {
                JSONObject()
            }
            obj.put(pkgName, enabled)
            prefs.edit().putString("notification_listener_apps", obj.toString()).apply()
        }

        fun addApp(context: Context, pkgName: String, enabled: Boolean) {
            setAppEnabled(context, pkgName, enabled)
        }

        fun isNotificationListenerEnabled(context: Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            )
            return flat?.contains(context.packageName) == true
        }

        fun testNotificationRecognition(context: Context, text: String) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val routedResult = RecognitionRouter(context).recognizeTextOffline(
                        text,
                        RecognitionTextSource.Notification,
                        RecognitionTrigger.TEST_NOTIFICATION,
                    )
                    val results = routedResult.orders

                    if (results.isEmpty()) {
                        android.util.Log.d("NotificationListener", "测试识别: 未识别到码")
                        return@launch
                    }

                    val db = OrderDatabase.getDatabase(context)
                    val orderDao = db.orderDao()
                    val groupDao = db.orderGroupDao()

                    for (result in results) {
                        if (result.code == null) continue
                        val order = RecognizedOrderFactory.fromRecognition(
                            result = result,
                            metadata = routedResult.metadata,
                            screenshotPath = "",
                            recognizedText = text,
                            sourceApp = "通知识别",
                            sourcePackage = "测试通知",
                        ) ?: continue
                        orderDao.insert(order)
                    }

                    DailyExpressGroupingHelper.regroupPendingExpressByDay(orderDao, groupDao, context)
                    android.util.Log.d("NotificationListener", "测试识别完成: codes=${results.map { it.code }}")
                } catch (e: Exception) {
                    android.util.Log.e("NotificationListener", "测试识别失败", e)
                }
            }
        }
    }
}
