package com.Badnng.moe.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Binder
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import com.Badnng.moe.ocr.PaddleOcrHelper
import java.util.concurrent.atomic.AtomicBoolean

/** Handles HyperOS fair-memory warnings without blocking the main thread. */
object FairMemoryManager {
    const val ACTION_TRIM = "itgsa.intent.action.TRIM"
    const val ACTION_KILL = "itgsa.intent.action.KILL"

    // Debug builds can exercise the real cleanup path without a system Binder callback.
    const val DEBUG_SIMULATION_KEY = "hypernote.debug_fair_memory_simulation"

    private const val TAG = "FairMemory"
    private const val PREFS_NAME = "fair_memory"
    private const val KEY_COMMON = "common"
    private const val KEY_EXTRA = "extra"
    private const val KEY_NOTIFY_TYPE = "notifyType"
    private const val KEY_NOTIFY_ID = "notifyId"
    private const val KEY_REASON = "reason"
    private const val KEY_ACTION = "action"
    private const val KEY_CALLBACK = "callback"
    private const val KEY_REPLY = "reply"
    private const val RESULT_HANDLED = 0
    private const val RESULT_NOT_HANDLED = 1
    private const val NOTIFY_TYPE_PSS = 1000
    private const val NOTIFY_TYPE_HEAP = 2000

    private val initialized = AtomicBoolean(false)
    private lateinit var appContext: Context
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null

    private val debugCallback = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != IBinder.FIRST_CALL_TRANSACTION) {
                return super.onTransact(code, data, reply, flags)
            }
            val notifyType = data.readInt()
            val notifyId = data.readInt()
            val result = data.readInt()
            val extra = data.readBundle(FairMemoryManager::class.java.classLoader)
            Log.i(
                TAG,
                "debug callback received notifyType=$notifyType, notifyId=$notifyId, " +
                    "result=$result, reply=${extra?.getString(KEY_REPLY)}",
            )
            return true
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handleFairMemoryBroadcast(context, intent)
        }
    }

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return

        appContext = context.applicationContext
        val thread = HandlerThread(TAG).apply { start() }
        workerThread = thread
        workerHandler = Handler(thread.looper)

        runCatching {
            appContext.registerReceiver(
                receiver,
                IntentFilter().apply {
                    addAction(ACTION_TRIM)
                    addAction(ACTION_KILL)
                },
                null,
                workerHandler,
                Context.RECEIVER_EXPORTED,
            )
        }.onSuccess {
            Log.i(TAG, "receiver registered")
        }.onFailure { error ->
            Log.e(TAG, "receiver registration failed", error)
            initialized.set(false)
            workerHandler = null
            workerThread = null
            thread.quitSafely()
        }
    }

    fun onTrimMemory(level: Int) {
        dispatch {
            val severe = level in 10..19 || level >= 60
            performCleanup(
                event = MemoryEvent(
                    broadcastAction = "android.onTrimMemory",
                    operation = "trim",
                    notifyType = NOTIFY_TYPE_PSS,
                    notifyId = level,
                    reason = "Android trim level=$level",
                ),
                disableVisualEffects = severe,
            )
        }
    }

    fun onLowMemory() {
        dispatch {
            performCleanup(
                event = MemoryEvent(
                    broadcastAction = "android.onLowMemory",
                    operation = "trim",
                    notifyType = NOTIFY_TYPE_PSS,
                    notifyId = -1,
                    reason = "Android low memory callback",
                ),
                disableVisualEffects = true,
            )
        }
    }

    private fun handleFairMemoryBroadcast(context: Context, intent: Intent) {
        val broadcastAction = intent.action
        if (broadcastAction != ACTION_TRIM && broadcastAction != ACTION_KILL) return

        val common = intent.getBundleExtra(KEY_COMMON)
        val systemCallback = common?.getBinder(KEY_CALLBACK)
        val debugSimulation = isDebuggable(context) &&
            intent.getBooleanExtra(DEBUG_SIMULATION_KEY, false)
        val callback = systemCallback ?: if (debugSimulation) debugCallback else null

        // The official protocol always supplies a callback Binder. Requiring it prevents
        // arbitrary third-party broadcasts from degrading the UI in release builds.
        if (callback == null) {
            Log.w(TAG, "ignored $broadcastAction without callback Binder")
            return
        }

        val event = parseEvent(intent, common)
        val result = runCatching {
            performCleanup(event, disableVisualEffects = true)
        }.fold(
            onSuccess = { RESULT_HANDLED },
            onFailure = { error ->
                Log.e(TAG, "cleanup failed action=$broadcastAction", error)
                persistResult(event, RESULT_NOT_HANDLED, memorySnapshot(), 0)
                RESULT_NOT_HANDLED
            },
        )

        val replyData = Bundle().apply {
            putString(
                KEY_REPLY,
                if (result == RESULT_HANDLED) "Hyper Note memory cleanup completed" else
                    "Hyper Note memory cleanup failed",
            )
        }
        val callbackSent = reply(callback, event, result, replyData)
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("last_callback_sent", callbackSent)
            .putBoolean("last_debug_simulation", debugSimulation)
            .commit()
        Log.i(
            TAG,
            "callback action=${event.operation}, notifyId=${event.notifyId}, " +
                "result=$result, sent=$callbackSent, debug=$debugSimulation",
        )
    }

    private fun parseEvent(intent: Intent, common: Bundle?): MemoryEvent {
        val broadcastAction = intent.action.orEmpty()
        val operation = common?.getString(KEY_ACTION)
            ?.lowercase()
            ?.takeIf { it == "trim" || it == "kill" }
            ?: if (broadcastAction == ACTION_KILL) "kill" else "trim"
        val defaultType = if (intent.getBundleExtra(KEY_EXTRA)?.containsKey("heapCapacity") == true) {
            NOTIFY_TYPE_HEAP
        } else {
            NOTIFY_TYPE_PSS
        }
        return MemoryEvent(
            broadcastAction = broadcastAction,
            operation = operation,
            notifyType = common?.getInt(KEY_NOTIFY_TYPE, defaultType)
                ?: intent.getIntExtra(KEY_NOTIFY_TYPE, defaultType),
            notifyId = common?.getInt(KEY_NOTIFY_ID, 0)
                ?: intent.getIntExtra(KEY_NOTIFY_ID, 0),
            reason = common?.getString(KEY_REASON)
                ?: intent.getStringExtra(KEY_REASON)
                ?: "Unspecified memory pressure",
            reported = ReportedMemory.from(intent.getBundleExtra(KEY_EXTRA)),
        )
    }

    private fun performCleanup(
        event: MemoryEvent,
        disableVisualEffects: Boolean,
    ) {
        val startedAt = SystemClock.elapsedRealtime()
        val before = memorySnapshot()
        val clearedIcons = BrandIconResolver.clearMemoryCache()
        val ocrReleased = if (disableVisualEffects) {
            PaddleOcrHelper.releaseIfCreated(
                reason = "memory-${event.operation}-${event.notifyId}",
            )
        } else {
            false
        }
        if (disableVisualEffects) {
            AppMemoryPressureState.enterPressureMode(
                allowRecovery = event.operation != "kill",
            )
        }

        if (event.operation == "kill") {
            // Orders and settings are already transactionally persisted by Room/SharedPreferences.
            // Flush the remaining process-local log writer before the system terminates us.
            AppLogger.flush()
        }

        persistResult(event, RESULT_HANDLED, before, clearedIcons)
        Log.i(
            TAG,
            "handled action=${event.operation}, notifyType=${event.notifyType}, " +
                "notifyId=${event.notifyId}, reason=${event.reason}, " +
                "pssKb=${before.pssKb}, heapAllocKb=${before.heapAllocKb}, " +
                "reportedPssKb=${event.reported.pssKb}, " +
                "reportedPssLimitKb=${event.reported.pssLimitKb}, " +
                "reportedHeapKb=${event.reported.heapAllocKb}, " +
                "reportedHeapLimitKb=${event.reported.heapCapacityKb}, " +
                "clearedIcons=$clearedIcons, ocrReleased=$ocrReleased, " +
                "visualEffectsDisabled=$disableVisualEffects, " +
                "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
        )
    }

    private fun persistResult(
        event: MemoryEvent,
        result: Int,
        memory: MemorySnapshot,
        clearedIcons: Int,
    ) {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("last_broadcast_action", event.broadcastAction)
            .putString("last_operation", event.operation)
            .putInt("last_notify_type", event.notifyType)
            .putInt("last_notify_id", event.notifyId)
            .putString("last_reason", event.reason)
            .putLong("last_pss_kb", memory.pssKb)
            .putLong("last_heap_alloc_kb", memory.heapAllocKb)
            .putInt("last_cleared_icon_count", clearedIcons)
            .putInt("last_result", result)
            .putLong("last_handled_at", System.currentTimeMillis())
            .commit()
    }

    private fun reply(
        callback: IBinder,
        event: MemoryEvent,
        result: Int,
        extra: Bundle,
    ): Boolean {
        val data = Parcel.obtain()
        return try {
            data.writeInt(event.notifyType)
            data.writeInt(event.notifyId)
            data.writeInt(result)
            data.writeBundle(extra)
            callback.transact(
                IBinder.FIRST_CALL_TRANSACTION,
                data,
                null,
                IBinder.FLAG_ONEWAY,
            )
        } catch (error: Exception) {
            Log.e(TAG, "callback failed notifyId=${event.notifyId}", error)
            false
        } finally {
            data.recycle()
        }
    }

    private fun memorySnapshot(): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        return MemorySnapshot(
            pssKb = runCatching { Debug.getPss() }.getOrDefault(-1L),
            heapAllocKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L,
        )
    }

    private fun dispatch(block: () -> Unit) {
        val handler = workerHandler
        if (handler != null) {
            handler.post(block)
        } else {
            Log.w(TAG, "worker unavailable; memory callback ignored")
        }
    }

    private fun isDebuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private data class MemoryEvent(
        val broadcastAction: String,
        val operation: String,
        val notifyType: Int,
        val notifyId: Int,
        val reason: String,
        val reported: ReportedMemory = ReportedMemory(),
    )

    private data class ReportedMemory(
        val heapAllocKb: Int = -1,
        val heapCapacityKb: Int = -1,
        val pssKb: Int = -1,
        val pssLimitKb: Int = -1,
    ) {
        companion object {
            fun from(bundle: Bundle?): ReportedMemory {
                if (bundle == null) return ReportedMemory()
                val heapAlloc = when {
                    bundle.containsKey("heapAlloc") -> bundle.getInt("heapAlloc")
                    bundle.containsKey("heapSize") -> bundle.getInt("heapSize")
                    else -> -1
                }
                return ReportedMemory(
                    heapAllocKb = heapAlloc,
                    heapCapacityKb = bundle.getInt("heapCapacity", -1),
                    pssKb = bundle.getInt("pss", -1),
                    pssLimitKb = bundle.getInt("pssLimit", -1),
                )
            }
        }
    }

    private data class MemorySnapshot(
        val pssKb: Long,
        val heapAllocKb: Long,
    )
}
