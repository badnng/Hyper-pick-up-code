package com.Badnng.moe

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.Badnng.moe.helper.AppMemoryPressureState
import com.Badnng.moe.helper.AppLogger
import com.Badnng.moe.helper.FairMemoryManager
import com.Badnng.moe.rules.RecognitionRuleEngine
import com.Badnng.moe.wearable.WearableSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HyperNoteApp : Application(), Application.ActivityLifecycleCallbacks {
    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        AppMemoryPressureState.initialize(this)
        FairMemoryManager.initialize(this)
        registerActivityLifecycleCallbacks(this)
        AppLogger.app("Application onCreate, process=${android.os.Process.myPid()}")
        // 进程因开机广播、保活服务或系统回收后恢复时，若用户已开启手表同步，
        // 立即开始节点发现和监听注册，不等待主页或 ViewModel 创建。
        // XMS SDK 初始化/节点发现可能触发 Binder 调用，不能阻塞 Activity 首帧。
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            runCatching {
                WearableSyncManager.getInstance(applicationContext).ensureWearChannel()
            }
        }
        // 预热规则引擎，确保短信/通知广播到达时引擎已就绪
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            if (!RecognitionRuleEngine.isInitialized) {
                RecognitionRuleEngine.initialize(applicationContext)
            }
        }
    }

    override fun onTerminate() {
        AppLogger.app("Application onTerminate")
        AppLogger.flush()
        super.onTerminate()
    }

    override fun onLowMemory() {
        AppLogger.app("Application onLowMemory")
        FairMemoryManager.onLowMemory()
        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        AppLogger.app("Application onTrimMemory level=$level")
        FairMemoryManager.onTrimMemory(level)
        super.onTrimMemory(level)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        AppLogger.app("Activity created: ${activity.javaClass.simpleName}")
    }

    override fun onActivityStarted(activity: Activity) {
        val enteringForeground = startedActivityCount == 0
        startedActivityCount += 1
        if (enteringForeground) {
            AppMemoryPressureState.onAppForegrounded()
        }
        AppLogger.app("Activity started: ${activity.javaClass.simpleName}")
    }

    override fun onActivityResumed(activity: Activity) {
        AppLogger.app("Activity resumed: ${activity.javaClass.simpleName}")
    }

    override fun onActivityPaused(activity: Activity) {
        AppLogger.app("Activity paused: ${activity.javaClass.simpleName}")
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        if (startedActivityCount == 0) {
            AppMemoryPressureState.onAppBackgrounded()
        }
        AppLogger.app("Activity stopped: ${activity.javaClass.simpleName}")
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        AppLogger.app("Activity saveInstanceState: ${activity.javaClass.simpleName}")
    }

    override fun onActivityDestroyed(activity: Activity) {
        AppLogger.app("Activity destroyed: ${activity.javaClass.simpleName}")
    }
}
