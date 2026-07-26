package com.Badnng.moe.helper

import android.content.Context
import android.util.Log
import com.Badnng.moe.data.db.OrderDatabase
import java.io.File

object StorageCleanupHelper {
    private const val TAG = "StorageCleanup"
    private const val PREFS_NAME = "storage_cleanup"
    private const val KEY_PENDING_UPDATE_APKS = "pending_update_apks"
    private const val COMPLETED_SCREENSHOT_RETENTION_MS = 7L * 24L * 60L * 60L * 1000L

    fun markUpdateApkForCleanup(context: Context, apkPath: String) {
        if (apkPath.isBlank()) return
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val set = (prefs.getStringSet(KEY_PENDING_UPDATE_APKS, emptySet()) ?: emptySet()).toMutableSet()
            set.add(apkPath)
            prefs.edit().putStringSet(KEY_PENDING_UPDATE_APKS, set).apply()
        }.onFailure {
            Log.w(TAG, "markUpdateApkForCleanup failed: $apkPath", it)
        }
    }

    suspend fun runStartupCleanup(context: Context) {
        AppLogger.update("StorageCleanup runStartupCleanup start")
        runCatching { ScreenshotStorage.migrateLegacyScreenshots(context) }
            .onSuccess { result ->
                if (result.migratedFiles > 0 || result.failedFiles > 0) {
                    AppLogger.update(
                        "Screenshot migration: files=${result.migratedFiles}, " +
                            "orders=${result.updatedOrders}, groups=${result.updatedGroups}, " +
                            "failed=${result.failedFiles}",
                    )
                }
            }
            .onFailure { AppLogger.update("Screenshot migration failed: ${it.message}") }
        cleanupPendingUpdateApks(context)
        cleanupExpiredCompletedScreenshots(context)
        AppLogger.update("StorageCleanup runStartupCleanup done")
    }

    private fun cleanupPendingUpdateApks(context: Context) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val pending = (prefs.getStringSet(KEY_PENDING_UPDATE_APKS, emptySet()) ?: emptySet()).toMutableSet()
            if (pending.isEmpty()) return

            val remaining = mutableSetOf<String>()
            pending.forEach { path ->
                val file = File(path)
                val deleted = !file.exists() || file.delete()
                if (!deleted) remaining.add(path)
            }
            prefs.edit().putStringSet(KEY_PENDING_UPDATE_APKS, remaining).apply()
            Log.d(TAG, "cleanupPendingUpdateApks done: pending=${pending.size}, remaining=${remaining.size}")
        }.onFailure {
            Log.w(TAG, "cleanupPendingUpdateApks failed", it)
        }
    }

    private suspend fun cleanupExpiredCompletedScreenshots(context: Context) {
        runCatching {
            val cutoff = System.currentTimeMillis() - COMPLETED_SCREENSHOT_RETENTION_MS
            val db = OrderDatabase.getDatabase(context)
            val allOrders = db.orderDao().getAllOrdersList()

            val expiredCompletedPaths = allOrders
                .filter {
                    it.isCompleted &&
                        (it.completedAt ?: 0L) > 0L &&
                        (it.completedAt ?: 0L) <= cutoff &&
                        it.screenshotPath.isNotBlank()
                }
                .map { it.screenshotPath }
                .toSet()

            if (expiredCompletedPaths.isEmpty()) return

            val protectedPaths = allOrders
                .filter {
                    it.screenshotPath.isNotBlank() &&
                        (
                            !it.isCompleted ||
                                it.completedAt == null ||
                                it.completedAt > cutoff
                            )
                }
                .map { it.screenshotPath }
                .toSet()

            var deletedCount = 0
            expiredCompletedPaths.forEach { path ->
                if (path in protectedPaths) return@forEach
                if (ScreenshotStorage.exists(context, path) && ScreenshotStorage.delete(context, path)) {
                    deletedCount++
                }
            }

            Log.d(
                TAG,
                "cleanupExpiredCompletedScreenshots done: candidates=${expiredCompletedPaths.size}, deleted=$deletedCount"
            )
        }.onFailure {
            Log.w(TAG, "cleanupExpiredCompletedScreenshots failed", it)
        }
    }
}

