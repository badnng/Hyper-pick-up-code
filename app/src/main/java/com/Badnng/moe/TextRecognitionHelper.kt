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

    private val drinkBrands = listOf("星巴克", "瑞幸", "喜茶", "奈雪", "霸王茶姬", "茶百道", "蜜雪冰城", "一点点", "古茗", "Manner", "山楂奶绿", "取茶", "奶茶", "茶颜悦色")
    private val foodBrands = listOf("麦当劳", "肯德基", "KFC", "汉堡王", "塔斯汀", "老乡鸡", "华莱士")
    private val homePageKeywords = listOf("我的", "首页", "会员码", "我", "到店取餐", "点单", "会员", "我的订单")

    suspend fun recognizeAll(bitmap: Bitmap): RecognitionResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val textResult = try { textRecognizer.process(image).await() } catch (e: Exception) { null }
        val barcodeResult = try { barcodeScanner.process(image).await() } catch (e: Exception) { null }

        val fullText = textResult?.text ?: ""
        
        // 1. 语境判定
        val hasTakeoutKeywords = fullText.contains("取餐") || fullText.contains("取茶") || 
                                 fullText.contains("取件") || fullText.contains("验证码") || 
                                 fullText.contains("券码") || fullText.contains("订单") ||
                                 fullText.contains("准备完毕")
        
        val homePageElementCount = homePageKeywords.count { fullText.contains(it) }
        val isLikelyHomePage = homePageElementCount >= 2

        var qrCode = barcodeResult?.firstOrNull()?.rawValue
        if (qrCode != null && (qrCode.contains("http://", ignoreCase = true) || qrCode.contains("https://", ignoreCase = true))) {
            qrCode = null
        }

        var takeoutCode: String? = null
        val blocks = textResult?.textBlocks ?: emptyList()
        
        // 品牌检测逻辑 (提前进行以辅助识别)
        var detectedBrand: String? = null
        val brandHits = mutableMapOf<String, Int>()
        if (fullText.contains("熊猫币") || fullText.contains("葫芦")) brandHits["古茗"] = 15
        if (fullText.contains("喜茶GO")) brandHits["喜茶"] = 15
        for (brand in drinkBrands + foodBrands) {
            if (fullText.contains(brand, ignoreCase = true)) {
                val score = if (Regex("$brand[:：]\\d").containsMatchIn(fullText)) 1 else 4
                brandHits[brand] = (brandHits[brand] ?: 0) + score
            }
        }
        detectedBrand = brandHits.maxByOrNull { it.value }?.key
        if (detectedBrand == "KFC") detectedBrand = "肯德基"

        if (!isLikelyHomePage) {
            // 2. 精准定位
            var targetKeywordRect: android.graphics.Rect? = null
            val preciseKeywords = listOf("取餐号", "取餐码", "取茶号", "券码", "订单号")
            
            for (block in blocks) {
                val text = block.text.replace(" ", "")
                if (preciseKeywords.any { text.contains(it) }) {
                    targetKeywordRect = block.boundingBox
                    val match = Regex("[A-Z0-9]{3,6}").find(text)
                    if (match != null && !preciseKeywords.any { it.contains(match.value) || match.value.contains(it) }) {
                        takeoutCode = match.value
                        break
                    }
                }
            }

            // 3. 邻域搜索
            if (takeoutCode == null && targetKeywordRect != null) {
                val candidates = blocks.mapNotNull { block ->
                    val box = block.boundingBox ?: return@mapNotNull null
                    val text = block.text.replace(" ", "").replace("\n", "")
                    
                    if (Regex("^[A-Z0-9]{3,6}$").matches(text)) {
                        val dist = Math.abs((box.top + box.bottom)/2 - (targetKeywordRect!!.top + targetKeywordRect!!.bottom)/2)
                        if (dist < 400) text to dist else null
                    } else null
                }.sortedBy { it.second }
                takeoutCode = candidates.firstOrNull()?.first
            }

            // 4. 权重兜底
            if (takeoutCode == null && hasTakeoutKeywords) {
                val pattern = Regex("(?<![a-zA-Z0-9])([A-Z0-9]{3,5})(?![a-zA-Z0-9])")
                val candidates = blocks.flatMap { block ->
                    val text = block.text.replace(" ", "").replace("\n", "")
                    pattern.findAll(text).mapNotNull { match ->
                        val value = match.value
                        if (isInvalidValue(value, text)) return@mapNotNull null
                        
                        var weight = (block.boundingBox?.width() ?: 0) * value.length
                        // 麦当劳 5 位码权重极大
                        if (detectedBrand == "麦当劳" && value.length == 5 && value.all { it.isDigit() }) weight *= 5
                        
                        value to weight
                    }.toList()
                }.sortedByDescending { it.second }
                takeoutCode = candidates.firstOrNull()?.first
            }
        }

        var category = "餐食"
        if (drinkBrands.contains(detectedBrand) || fullText.contains("奶茶") || fullText.contains("咖啡")) category = "饮品"

        Log.d("RecognitionMonitor", "------------------------------------")
        Log.d("RecognitionMonitor", "Full Text: ${fullText.replace("\n", " ")}")
        Log.d("RecognitionMonitor", "Extracted Code: $takeoutCode")
        Log.d("RecognitionMonitor", "QR Data: $qrCode")
        Log.d("RecognitionMonitor", "Category: $category")
        Log.d("RecognitionMonitor", "Brand: $detectedBrand")
        Log.d("RecognitionMonitor", "Home Elements Count: $homePageElementCount")
        Log.d("RecognitionMonitor", "------------------------------------")
        
        return RecognitionResult(takeoutCode, qrCode, category, detectedBrand)
    }

    private fun isInvalidValue(value: String, context: String): Boolean {
        if (value.startsWith("202") && value.length == 4) return true // 年份排除
        val lowerContext = context.lowercase()
        // 排除常见的促销/广告/金额干扰
        val distractions = listOf("ml", "g", "元", "¥", "购", "券", "赢", "送", "补贴", "减", "满", "起")
        if (distractions.any { lowerContext.contains(it) && lowerContext.indexOf(it) in (lowerContext.indexOf(value)-2)..(lowerContext.indexOf(value)+value.length+2) }) return true
        if (lowerContext.contains(":") || lowerContext.contains("/") || lowerContext.contains("年") || lowerContext.contains("月") || lowerContext.contains("日")) {
            // 如果包含这些，且 value 只是其中一部分，排除
            if (context.length > value.length + 5) return true
        }
        return false
    }

    fun close() {
        textRecognizer.close()
        barcodeScanner.close()
    }
}

data class RecognitionResult(val code: String?, val qr: String?, val type: String, val brand: String?)
