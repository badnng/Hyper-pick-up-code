package com.Badnng.moe.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.Badnng.moe.rules.RecognitionRuleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d("WearableSyncService", "收到 BOOT_COMPLETED，启动后台恢复服务")
        KeepAliveService.start(context)
        // 开机后立即预热规则引擎
        scope.launch {
            if (!RecognitionRuleEngine.isInitialized) {
                RecognitionRuleEngine.initialize(context.applicationContext)
            }
        }
    }
}
