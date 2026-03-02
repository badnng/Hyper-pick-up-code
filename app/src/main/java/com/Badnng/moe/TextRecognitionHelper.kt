package com.Badnng.moe

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.tasks.await

class TextRecognitionHelper {

    // 修复：使用 ChineseTextRecognizerOptions.Builder()
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    suspend fun recognizeText(bitmap: Bitmap): String {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await() // 需要 play-services 协程库支持

            val recognizedText = StringBuilder()
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    recognizedText.append(line.text).append("\n")
                }
            }

            recognizedText.toString().trim()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun extractTakeoutCodes(text: String): List<String> {
        val regex = Regex("\\d{4,6}")
        return regex.findAll(text).map { it.value }.toList()
    }

    fun extractMostLikelyTakeoutCode(text: String): String? {
        val codes = extractTakeoutCodes(text)
        if (codes.isEmpty()) return null
        return codes.sortedByDescending { it.length }.firstOrNull()
    }

    fun close() {
        recognizer.close()
    }
}
