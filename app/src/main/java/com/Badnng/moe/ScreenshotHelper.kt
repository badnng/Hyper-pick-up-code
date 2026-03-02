package com.Badnng.moe

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import java.io.FileOutputStream

class ScreenshotHelper(private val context: Context) {

    private val screenshotDir = File(context.filesDir, "screenshots").apply {
        if (!exists()) mkdirs()
    }

    fun saveScreenshot(imageBitmap: ImageBitmap): String {
        val bitmap = imageBitmap.asAndroidBitmap()
        val filename = "screenshot_${System.currentTimeMillis()}.png"
        val file = File(screenshotDir, filename)

        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }

        return file.absolutePath
    }

    fun saveBitmap(bitmap: Bitmap): String {
        val filename = "screenshot_${System.currentTimeMillis()}.png"
        val file = File(screenshotDir, filename)

        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }

        return file.absolutePath
    }

    fun deleteScreenshot(filePath: String) {
        File(filePath).delete()
    }

    fun getAllScreenshots(): List<File> {
        return screenshotDir.listFiles()?.toList() ?: emptyList()
    }
}