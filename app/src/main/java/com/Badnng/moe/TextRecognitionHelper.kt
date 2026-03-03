package com.Badnng.moe

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.tasks.await

class TextRecognitionHelper {
    private val textRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private val barcodeScanner = BarcodeScanning.getClient()

    private val drinkBrands = listOf("星巴克", "瑞幸", "喜茶", "奈雪", "霸王茶姬", "茶百道", "蜜雪冰城", "一点点", "古茗", "Manner", "山楂奶绿", "取茶", "奶茶")

    suspend fun recognizeAll(bitmap: Bitmap): Triple<String?, String?, String> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val textResult = try { textRecognizer.process(image).await() } catch (e: Exception) { null }
        val barcodeResult = try { barcodeScanner.process(image).await() } catch (e: Exception) { null }

        val fullText = textResult?.text ?: ""
        
        // 屏蔽带有 http/https 的二维码网址
        var qrCode = barcodeResult?.firstOrNull()?.rawValue
        if (qrCode != null && (qrCode.contains("http://", ignoreCase = true) || qrCode.contains("https://", ignoreCase = true))) {
            qrCode = null
        }

        var takeoutCode: String? = null
        val blocks = textResult?.textBlocks ?: emptyList()
        
        // 1. 寻找关键字引导的号码
        var keywordRect: android.graphics.Rect? = null
        val keywords = listOf("取餐号", "取餐码", "取件码", "取茶号", "券码")
        
        outer@for (block in blocks) {
            val text = block.text.replace(" ", "").replace(":", "").replace("：", "")
            for (kw in keywords) {
                if (text.contains(kw)) {
                    keywordRect = block.boundingBox
                    val codeMatch = Regex("[A-Z0-9]{3,6}").find(text.substringAfter(kw))
                    if (codeMatch != null) {
                        takeoutCode = codeMatch.value
                        break@outer
                    }
                }
            }
        }

        // 2. 在关键字附近的块找
        if (takeoutCode == null && keywordRect != null) {
            for (block in blocks) {
                val box = block.boundingBox ?: continue
                if ((box.top >= keywordRect.top && box.top <= keywordRect.bottom + 300) || 
                    (box.left >= keywordRect.right && box.left <= keywordRect.right + 400 && box.centerY() in keywordRect.top..keywordRect.bottom)) {
                    val text = block.text.replace(" ", "").replace("\n", "")
                    if (!isDistraction(text)) {
                        val match = Regex("[A-Z]?\\d{3,6}").find(text) 
                        if (match != null) {
                            takeoutCode = match.value
                            break
                        }
                    }
                }
            }
        }

        // 3. 找全屏权重最高的独立数字块
        if (takeoutCode == null) {
            val pattern = Regex("(?<![a-zA-Z0-9])([A-Z]?\\d{3,5})(?![a-zA-Z0-9])")
            val candidates = blocks.mapNotNull { block ->
                val text = block.text.replace(" ", "").replace("\n", "")
                if (isDistraction(text)) return@mapNotNull null
                val match = pattern.find(text)
                if (match != null) {
                    val value = match.value
                    val weight = (block.boundingBox?.width() ?: 0) * value.length
                    value to weight
                } else null
            }.sortedByDescending { it.second }
            takeoutCode = candidates.firstOrNull()?.first
        }

        var category = "餐食"
        for (brand in drinkBrands) {
            if (fullText.contains(brand, ignoreCase = true)) {
                category = "饮品"
                break
            }
        }

        Log.d("RecognitionMonitor", "------------------------------------")
        Log.d("RecognitionMonitor", "Full Text: ${fullText.replace("\n", " ")}")
        Log.d("RecognitionMonitor", "Extracted Code: $takeoutCode")
        Log.d("RecognitionMonitor", "QR Data: $qrCode")
        Log.d("RecognitionMonitor", "Category: $category")
        Log.d("RecognitionMonitor", "------------------------------------")
        
        return Triple(takeoutCode, qrCode, category)
    }

    private fun isDistraction(text: String): Boolean {
        val lowerText = text.lowercase()
        return lowerText.contains("ml") || lowerText.contains("g") || lowerText.contains("元") || 
               lowerText.contains("¥") || lowerText.contains(":") || lowerText.contains("/") || text.length > 8
    }

    private fun android.graphics.Rect.centerY() = (top + bottom) / 2

    fun close() {
        textRecognizer.close()
        barcodeScanner.close()
    }
}
