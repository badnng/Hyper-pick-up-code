package com.Badnng.moe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
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

                    "ACTION_OPEN_TAOBAO_IDENTITY" -> {
                        openTaobaoIdentityEntry(context)
                    }

                    "ACTION_OPEN_PDD_IDENTITY" -> {
                        openPddIdentityEntry(context)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun openTaobaoIdentityEntry(context: Context) {
        val pkg = "com.taobao.taobao"
        val lastmile = "https://pages-fast.m.taobao.com/wow/z/uniapp/1100333/last-mile-fe/m-end-school-tab/home"
        val candidates = listOf(
            "tbopen://m.taobao.com/tbopen/index.html?h5Url=" + Uri.encode(lastmile)
        )
        for (u in candidates) {
            try {
                val i = Intent(Intent.ACTION_VIEW, u.toUri())
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                return
            } catch (_: Exception) {
            }
        }
        try {
            val i = Intent(Intent.ACTION_VIEW, lastmile.toUri())
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            i.setClassName(pkg, "com.taobao.browser.BrowserActivity")
            context.startActivity(i)
        } catch (_: Exception) {
        }
    }

    private fun openPddIdentityEntry(context: Context) {
        val pkg = "com.xunmeng.pinduoduo"
        val schemes = listOf(
            "pinduoduo://com.xunmeng.pinduoduo/mdkd/package",
            "pinduoduo://com.xunmeng.pinduoduo/",
            "pinduoduo://"
        )
        for (u in schemes) {
            try {
                val i = Intent(Intent.ACTION_VIEW, u.toUri())
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                return
            } catch (_: Exception) {
            }
        }
        try {
            val i = context.packageManager.getLaunchIntentForPackage(pkg)
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            }
        } catch (_: Exception) {
        }
    }
}
