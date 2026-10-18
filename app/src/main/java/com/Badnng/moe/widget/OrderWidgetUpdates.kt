package com.Badnng.moe.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.util.Log
import androidx.annotation.Keep
import androidx.collection.intSetOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.room.InvalidationTracker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.Badnng.moe.BuildConfig
import com.Badnng.moe.data.db.OrderDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** 监听数据库提交而不是某个识别入口，覆盖服务、通知、手表以及备份恢复的直接 DAO 写入。 */
object OrderWidgetUpdates {
    private const val WORK_NAME = "order-widget-refresh"
    private const val PREVIEW_DESIGN_VERSION = 5
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val monitoringMutex = Mutex()
    private val previewMutex = Mutex()
    private var observer: InvalidationTracker.Observer? = null
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var configurationCallbacks: ComponentCallbacks? = null
    private var wallpaperReceiver: BroadcastReceiver? = null

    private val receivers = listOf(
        SquareOrderWidgetReceiver::class.java, WideOrderWidgetReceiver::class.java, TallOrderWidgetReceiver::class.java,
    )

    private fun widgetIds(context: Context): List<Int> {
        val manager = AppWidgetManager.getInstance(context)
        return receivers.flatMap { manager.getAppWidgetIds(ComponentName(context, it)).toList() }
    }

    fun start(context: Context, removedIds: Set<Int> = emptySet()) {
        val app = context.applicationContext
        scope.launch {
            try {
                monitoringMutex.withLock {
                    val present = widgetIds(app).any { it !in removedIds }
                    val settings = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    if (present && observer == null) {
                        observer = object : InvalidationTracker.Observer("orders", "order_groups") {
                            override fun onInvalidated(tables: Set<String>) = enqueue(app)
                        }.also { OrderDatabase.getDatabase(app).invalidationTracker.addObserver(it) }
                        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                            if (key == null || key == "custom_brand_icons" || key in OrderWidgetAppearance.preferenceKeys) enqueue(app)
                        }.also(settings::registerOnSharedPreferenceChangeListener)
                        configurationCallbacks = object : ComponentCallbacks {
                            override fun onConfigurationChanged(newConfig: Configuration) = enqueue(app)
                            override fun onLowMemory() = Unit
                        }.also(app::registerComponentCallbacks)
                        wallpaperReceiver = object : BroadcastReceiver() {
                            override fun onReceive(context: Context, intent: Intent) = enqueue(app)
                        }.also {
                            app.registerReceiver(it, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED), Context.RECEIVER_NOT_EXPORTED)
                        }
                        // 进程恢复时补偿上次被系统终止前未完成的刷新。
                        enqueue(app, replace = false)
                    } else if (!present && observer != null) {
                        observer?.let { OrderDatabase.getDatabase(app).invalidationTracker.removeObserver(it) }
                        preferenceListener?.let(settings::unregisterOnSharedPreferenceChangeListener)
                        configurationCallbacks?.let(app::unregisterComponentCallbacks)
                        wallpaperReceiver?.let(app::unregisterReceiver)
                        observer = null
                        preferenceListener = null
                        configurationCallbacks = null
                        wallpaperReceiver = null
                        WorkManager.getInstance(app).cancelUniqueWork(WORK_NAME)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("OrderWidget", "Unable to monitor widget changes", error)
            }
        }
    }

    fun enqueue(context: Context, replace: Boolean = true) {
        if (widgetIds(context).isEmpty()) return
        // 把防抖放到持久化工作队列中，不依赖 Application 协程等待期间进程仍存活。
        val request = OneTimeWorkRequestBuilder<OrderWidgetRefreshWorker>()
            .setInitialDelay(250, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request,
        )
    }

    suspend fun refreshInstance(context: Context, id: Int) {
        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(id) ?: return
        val widget = when (info.provider.className) {
            SquareOrderWidgetReceiver::class.java.name -> SquareOrderWidget()
            WideOrderWidgetReceiver::class.java.name -> WideOrderWidget()
            TallOrderWidgetReceiver::class.java.name -> TallOrderWidget()
            else -> return
        }
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(id)
        updateAppWidgetState(context, glanceId) { state ->
            state[WidgetRevision] = (state[WidgetRevision] ?: 0L) + 1L
        }
        widget.update(context, glanceId)
    }

    suspend fun refreshAll(context: Context) {
        var failure: Exception? = null
        widgetIds(context).forEach { id ->
            try {
                refreshInstance(context, id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failure = error // 一个已删除/异常实例不能阻止其他实例刷新。
            }
        }
        failure?.let { throw it }
    }

    /** Android 15+ 系统预览。没有放置小组件时也在打开应用后生成，不额外常驻监听。 */
    fun publishPreviews(context: Context) {
        val app = context.applicationContext
        scope.launch {
            previewMutex.withLock {
                try {
                    val settings = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    val signature = "$PREVIEW_DESIGN_VERSION:${BuildConfig.VERSION_CODE}:" +
                        app.resources.configuration.uiMode + ":" +
                        OrderWidgetAppearance.preferenceKeys.sorted().joinToString { "${it}=${settings.all[it]}" }
                    val metadata = app.getSharedPreferences("order_widget_previews", Context.MODE_PRIVATE)
                    val installed = AppWidgetManager.getInstance(app).installedProviders.associateBy { it.provider }
                    val manager = GlanceAppWidgetManager(app)
                    receivers.forEach { receiver ->
                        val info = installed[ComponentName(app, receiver)] ?: return@forEach
                        if (metadata.getString(receiver.name, null) == signature &&
                            info.generatedPreviewCategories and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0) {
                            return@forEach
                        }
                        val result = manager.setWidgetPreviews(receiver.kotlin,
                            intSetOf(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN))
                        if (result == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) {
                            metadata.edit().putString(receiver.name, signature).apply()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w("OrderWidget", "Unable to publish widget previews", error)
                }
            }
        }
    }
}

@Keep
class OrderWidgetRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        OrderWidgetUpdates.refreshAll(applicationContext)
        OrderWidgetUpdates.publishPreviews(applicationContext)
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.w("OrderWidget", "Unable to refresh widgets", error)
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
