package com.Badnng.moe.helper

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Process-wide signal used to release costly UI resources after a memory warning. */
object AppMemoryPressureState {
    private const val TAG = "FairMemory"
    private const val RECOVERY_QUIET_PERIOD_MS = 60_000L
    private const val BACKGROUND_CONFIRMATION_MS = 1_000L

    private val mainHandler by lazy {
        Handler(Looper.getMainLooper())
    }

    private var appContext: Context? = null
    private var appInForeground = false
    private var backgroundConfirmed = false
    private var backgroundObservedAfterPressure = false
    private var recoveryAllowed = false
    private var recoveryReady = false
    private var lastPressureAt = 0L

    var active by mutableStateOf(false)
        private set

    private val markRecoveryReady = object : Runnable {
        override fun run() {
            if (!active || !recoveryAllowed) return

            val quietDuration = SystemClock.elapsedRealtime() - lastPressureAt
            if (quietDuration >= RECOVERY_QUIET_PERIOD_MS) {
                recoveryReady = true
                Log.i(TAG, "visual effects recovery is ready; waiting for next foreground")
            } else {
                mainHandler.postDelayed(
                    this,
                    RECOVERY_QUIET_PERIOD_MS - quietDuration,
                )
            }
        }
    }

    private val confirmBackground = Runnable {
        if (appInForeground) return@Runnable
        backgroundConfirmed = true
        if (active && recoveryAllowed) {
            backgroundObservedAfterPressure = true
        }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun enterPressureMode(allowRecovery: Boolean = true) {
        runOnMain {
            val wasActive = active
            active = true
            recoveryAllowed = if (wasActive) recoveryAllowed && allowRecovery else allowRecovery
            recoveryReady = false
            lastPressureAt = SystemClock.elapsedRealtime()
            backgroundObservedAfterPressure = backgroundConfirmed
            mainHandler.removeCallbacks(markRecoveryReady)
            if (recoveryAllowed) {
                mainHandler.postDelayed(markRecoveryReady, RECOVERY_QUIET_PERIOD_MS)
            }
            Log.i(
                TAG,
                "visual effects disabled; recoveryAllowed=$recoveryAllowed, " +
                    "appInForeground=$appInForeground",
            )
        }
    }

    fun onAppForegrounded() {
        runOnMain {
            mainHandler.removeCallbacks(confirmBackground)
            appInForeground = true
            backgroundConfirmed = false

            val hasRecoveryBoundary = backgroundObservedAfterPressure
            backgroundObservedAfterPressure = false
            if (!active || !recoveryAllowed || !recoveryReady || !hasRecoveryBoundary) {
                return@runOnMain
            }

            if (hasMemoryHeadroom()) {
                active = false
                recoveryAllowed = false
                recoveryReady = false
                mainHandler.removeCallbacks(markRecoveryReady)
                Log.i(TAG, "visual effects restored at foreground boundary")
            } else {
                Log.i(TAG, "visual effects recovery deferred: insufficient memory headroom")
            }
        }
    }

    fun onAppBackgrounded() {
        runOnMain {
            appInForeground = false
            backgroundConfirmed = false
            mainHandler.removeCallbacks(confirmBackground)
            mainHandler.postDelayed(confirmBackground, BACKGROUND_CONFIRMATION_MS)
        }
    }

    private fun hasMemoryHeadroom(): Boolean {
        val context = appContext ?: return false
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val memoryInfo = ActivityManager.MemoryInfo()
        return runCatching {
            activityManager.getMemoryInfo(memoryInfo)
            !memoryInfo.lowMemory &&
                (memoryInfo.threshold <= 0L || memoryInfo.availMem / 2L >= memoryInfo.threshold)
        }.getOrDefault(false)
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
