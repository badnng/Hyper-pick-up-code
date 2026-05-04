package com.Badnng.moe.helper

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process

object BatteryOptimizationHelper {
    private const val OPSTR_RUN_ANY_IN_BACKGROUND = "android:run_any_in_background"

    enum class Status(val isGranted: Boolean) {
        UNRESTRICTED(true),
        OPTIMIZED(false),
        RESTRICTED(false),
        UNKNOWN(false)
    }

    fun getStatus(context: Context): Status {
        val packageName = context.packageName
        val uid = Process.myUid()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        val allowlisted = runCatching {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        }.getOrDefault(false)

        val rawRunAnyMode = runCatching {
            if (Build.VERSION.SDK_INT >= 36) {
                appOpsManager.checkOpRawNoThrow(
                    OPSTR_RUN_ANY_IN_BACKGROUND,
                    uid,
                    packageName,
                    null
                )
            } else {
                @Suppress("DEPRECATION")
                appOpsManager.unsafeCheckOpRawNoThrow(
                    OPSTR_RUN_ANY_IN_BACKGROUND,
                    uid,
                    packageName
                )
            }
        }.getOrDefault(AppOpsManager.MODE_DEFAULT)

        val resolvedRunAnyMode = runCatching {
            appOpsManager.checkOpNoThrow(
                OPSTR_RUN_ANY_IN_BACKGROUND,
                uid,
                packageName
            )
        }.getOrDefault(rawRunAnyMode)

        val backgroundRestricted = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                activityManager.isBackgroundRestricted
            } else {
                false
            }
        }.getOrDefault(false)

        return when {
            allowlisted -> Status.UNRESTRICTED
            rawRunAnyMode == AppOpsManager.MODE_ALLOWED && !backgroundRestricted -> Status.UNRESTRICTED
            rawRunAnyMode == AppOpsManager.MODE_IGNORED ||
                resolvedRunAnyMode == AppOpsManager.MODE_IGNORED ||
                backgroundRestricted -> Status.RESTRICTED
            rawRunAnyMode == AppOpsManager.MODE_DEFAULT ||
                resolvedRunAnyMode == AppOpsManager.MODE_ALLOWED -> Status.OPTIMIZED
            else -> Status.UNKNOWN
        }
    }

    fun isGranted(context: Context): Boolean = getStatus(context).isGranted
}
