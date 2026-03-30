package com.Badnng.moe

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

object RootHelper {
    private const val TAG = "RootHelper"

    fun hasRootAccess(): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", "id").start()
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Root not available", e)
            false
        }
    }

    fun captureScreenshot(): Bitmap? {
        return try {
            val process = ProcessBuilder("su", "-c", "screencap -p").start()
            val bitmap = BitmapFactory.decodeStream(process.inputStream)
            val code = process.waitFor()
            if (code == 0) bitmap else null
        } catch (e: Exception) {
            Log.e(TAG, "Root screenshot failed", e)
            null
        }
    }
}
