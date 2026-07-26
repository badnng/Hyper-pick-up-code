package com.Badnng.moe.helper

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

class ScreenshotHelper(private val context: Context) {
    fun saveScreenshot(imageBitmap: ImageBitmap): String {
        return ScreenshotStorage.saveBitmap(context, imageBitmap.asAndroidBitmap())
    }

    fun saveBitmap(bitmap: Bitmap): String {
        return ScreenshotStorage.saveBitmap(context, bitmap)
    }

    fun deleteScreenshot(filePath: String) {
        ScreenshotStorage.delete(context, filePath)
    }

    fun getStorageStats(): ScreenshotStorage.Stats = ScreenshotStorage.readStats(context)
}
