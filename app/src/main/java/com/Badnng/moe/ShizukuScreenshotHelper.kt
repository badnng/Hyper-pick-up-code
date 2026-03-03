package com.Badnng.moe

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.InputStream

class ShizukuScreenshotHelper {

    fun isShizukuAvailable(): Boolean {
        return try {
            if (Shizuku.pingBinder()) {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun captureScreenshot(): Bitmap? {
        if (!isShizukuAvailable()) return null
        
        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(null, arrayOf("screencap", "-p"), null, null) as rikka.shizuku.ShizukuRemoteProcess

            val inputStream: InputStream = process.inputStream
            val bitmap = BitmapFactory.decodeStream(inputStream)
            process.waitFor()
            bitmap
        } catch (e: Exception) {
            Log.e("ShizukuCapture", "截图失败", e)
            null
        }
    }
}
