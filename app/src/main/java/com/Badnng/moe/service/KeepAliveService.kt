package com.Badnng.moe.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.Badnng.moe.R
import com.Badnng.moe.activity.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class KeepAliveService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KeepAliveService onCreate")
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 进程被系统拉起后自动恢复穿戴通道（注册消息监听，保证后台能收到手表心跳 ping 并回 pong）。
        // XMS 初始化与类加载不能在服务主线程触碰（会与 SDK 回调线程的初始化锁竞争，
        // 实测冷启动时主线程被阻塞约 3.3s，期间 UI 无法交互）；全部交给后台协程。
        serviceScope.launch {
            Log.d(
                TAG,
                "KeepAliveService onStartCommand: wearableEnabled=" +
                    com.Badnng.moe.wearable.WearableSyncManager
                        .getInstance(applicationContext).enabled.value,
            )
            // XMS 初始化、节点发现和 Binder 查询不能占用服务主线程，否则从后台进入主页
            // 时首帧虽然已经显示，触摸事件仍会被延迟处理。
            runCatching {
                com.Badnng.moe.wearable.WearableSyncManager
                    .getInstance(applicationContext)
                    .ensureWearChannel()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 任务被清除后用前台服务方式重新拉起；普通 startService 在 Android 8+
        // 的后台场景可能被系统拒绝，导致手表监听无法恢复。
        val restartIntent = Intent(applicationContext, KeepAliveService::class.java)
        runCatching {
            ContextCompat.startForegroundService(applicationContext, restartIntent)
        }.onFailure { error ->
            Log.w(TAG, "任务移除后重启保活服务失败: ${error.message}", error)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // 这里只结束保活通知服务。穿戴 MessageApi listener 属于
        // WearableSyncManager 生命周期，不能因 MainActivity.onResume 隐藏通知而注销。
        Log.d(TAG, "KeepAliveService onDestroy")
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "后台保活", NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            nm.createNotificationChannel(channel)
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val customView = RemoteViews(packageName, R.layout.notification_keep_alive).apply {
            setTextViewText(R.id.notification_title, "澎湃记正在后台运行")
            setTextViewText(R.id.notification_text, "保持短信读取、磁贴识别等功能可用")
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(customView)
            .build()
    }

    companion object {
        private const val TAG = "WearableSyncService"
        private const val NOTIFICATION_ID = 9999
        private const val CHANNEL_ID = "keep_alive"

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }

        fun showNotification(context: Context) {
            start(context)
        }

        fun hideNotification(context: Context) {
            // 手表同步开启时，KeepAliveService 同时承担穿戴监听保活。
            // MainActivity.onResume() 隐藏普通后台通知不能把穿戴监听一起停掉。
            // 直接读偏好，避免在主线程构造 WearableSyncManager（XMS 类加载/初始化
            // 会与 SDK 回调线程的初始化锁竞争，冷启动时主线程曾被实测阻塞约 3.3s）。
            val wearableEnabled = context.applicationContext
                .getSharedPreferences("wearable_sync", Context.MODE_PRIVATE)
                .getBoolean("wearable_sync_enabled", false)
            if (wearableEnabled) {
                return
            }
            stop(context)
        }

        /** 手表同步关闭后，在没有其他后台通知消费者时停止服务。 */
        fun stopIfNoConsumer(context: Context) {
            val appContext = context.applicationContext
            val persistentNotification = appContext
                .getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("persistent_notification_enabled", true)
            val wearableEnabled = appContext
                .getSharedPreferences("wearable_sync", Context.MODE_PRIVATE)
                .getBoolean("wearable_sync_enabled", false)
            if (!persistentNotification && !wearableEnabled) {
                stop(appContext)
            }
        }
    }
}
