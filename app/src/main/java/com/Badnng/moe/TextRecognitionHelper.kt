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

    suspend fun recognizeAll(bitmap: Bitmap, sourceApp: String? = null, sourcePkg: String? = null): RecognitionResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val textResult = try { textRecognizer.process(image).await() } catch (e: Exception) { null }
        val barcodeResult = try { barcodeScanner.process(image).await() } catch (e: Exception) { null }

        val rawFullText = textResult?.text ?: ""
        val mergedText = cleanChineseText(rawFullText)
        
        val hasTakeoutKeywords = mergedText.contains("取餐") || mergedText.contains("取茶") || 
                                 mergedText.contains("取件") || mergedText.contains("取性") ||
                                 mergedText.contains("验证码") || mergedText.contains("券码") || 
                                 mergedText.contains("订单") || mergedText.contains("准备完毕") ||
                                 mergedText.contains("领取") || mergedText.contains("取件码") ||
                                 mergedText.contains("取養")
        
        val homePageElementCount = homePageKeywords.count { mergedText.contains(it) }
        val isLikelyHomePage = homePageElementCount >= 2

        var qrCode = barcodeResult?.firstOrNull()?.rawValue
        if (qrCode != null && (qrCode.contains("http://", ignoreCase = true) || qrCode.contains("https://", ignoreCase = true))) {
            qrCode = null
        }

        var takeoutCode: String? = null
        var pickupLocation: String? = null
        val blocks = textResult?.textBlocks ?: emptyList()
        
        var detectedBrand: String? = when (sourcePkg) {
            "com.mcdonalds.gma.cn" -> "麦当劳"
            "com.yek.android.kfc.activitys" -> "肯德基"
            "com.lucky.luckyclient" -> "瑞幸"
            "com.mxbc.mxsa" -> "蜜雪冰城"
            "com.starbucks.cn" -> "星巴克"
            "com.heyteago" -> "喜茶"
            else -> null
        }

        if (detectedBrand == null) {
            val brandHits = mutableMapOf<String, Int>()
            if (mergedText.contains("熊猫币") || mergedText.contains("葫芦")) brandHits["古茗"] = 15
            if (mergedText.contains("喜茶GO")) brandHits["喜茶"] = 15
            for (brand in drinkBrands + foodBrands) {
                if (mergedText.contains(brand, ignoreCase = true)) {
                    val score = if (Regex("$brand[:：]\\d").containsMatchIn(mergedText)) 1 else 4
                    brandHits[brand] = (brandHits[brand] ?: 0) + score
                }
            }
            detectedBrand = brandHits.maxByOrNull { it.value }?.key
        }

        if (detectedBrand == "KFC") detectedBrand = "肯德基"

        if (!isLikelyHomePage) {
            var targetKeywordRect: android.graphics.Rect? = null
            val preciseKeywords = listOf("取餐号", "取餐码", "取茶号", "券码", "订单号", "取件码", "取性码", "取件", "取養号")
            
            for (block in blocks) {
                val text = block.text.replace(" ", "")
                if (preciseKeywords.any { text.contains(it) }) {
                    targetKeywordRect = block.boundingBox
                    val match = Regex("[A-Z0-9-]{3,10}").find(text)
                    if (match != null && !preciseKeywords.any { it.contains(match.value) || match.value.contains(it) }) {
                        if (!isInvalidValue(match.value, text)) {
                            takeoutCode = match.value
                            break
                        }
                    }
                }
            }

            if (takeoutCode == null && targetKeywordRect != null) {
                val candidates = blocks.mapNotNull { block ->
                    val box = block.boundingBox ?: return@mapNotNull null
                    val text = block.text.replace(" ", "").replace("\n", "")
                    
                    if (Regex("^[A-Z0-9-]{3,10}$").matches(text) && !isInvalidValue(text, text)) {
                        val dist = Math.abs((box.top + box.bottom)/2 - (targetKeywordRect!!.top + targetKeywordRect!!.bottom)/2)
                        if (dist < 400) text to dist else null
                    } else null
                }.sortedBy { it.second }
                takeoutCode = candidates.firstOrNull()?.first
            }

            if (takeoutCode == null && hasTakeoutKeywords) {
                val pattern = Regex("(?<![a-zA-Z0-9])([A-Z0-9]{3,10})(?![a-zA-Z0-9])")
                val candidates = blocks.flatMap { block ->
                    val text = block.text.replace(" ", "").replace("\n", "")
                    pattern.findAll(text).mapNotNull { match ->
                        val value = match.value
                        if (isInvalidValue(value, text)) return@mapNotNull null
                        
                        var weight = (block.boundingBox?.width() ?: 0) * value.length
                        
                        if (detectedBrand == "肯德基" || detectedBrand == "麦当劳") {
                            if (value.length >= 4 && value.any { it.isLetter() }) weight *= 20
                            if (value.length == 5 && value.all { it.isDigit() }) weight *= 5
                        }
                        
                        if (detectedBrand == "喜茶" && value.length == 4 && value.all { it.isDigit() }) weight *= 5
                        
                        value to weight
                    }.toList()
                }.sortedByDescending { it.second }
                takeoutCode = candidates.firstOrNull()?.first
            }
        }

        if (detectedBrand == "瑞幸" && qrCode == null) {
            takeoutCode = null
        }

        var category = "餐食"
        if (drinkBrands.contains(detectedBrand) || mergedText.contains("奶茶") || mergedText.contains("咖啡")) {
            category = "饮品"
        } else if (mergedText.contains("取件") || mergedText.contains("取性") || mergedText.contains("快递") || mergedText.contains("包裹") || mergedText.contains("待取件") || mergedText.contains("丰巢")) {
            category = "快递"
        }

        pickupLocation = findPickupLocation(mergedText, blocks)

        Log.d("RecognitionMonitor", "------------------------------------")
        Log.d("RecognitionMonitor", "Source App: $sourceApp")
        Log.d("RecognitionMonitor", "Source Package: $sourcePkg")
        Log.d("RecognitionMonitor", "Full Text: $mergedText")
        Log.d("RecognitionMonitor", "Extracted Code: $takeoutCode")
        Log.d("RecognitionMonitor", "QR Data: $qrCode")
        Log.d("RecognitionMonitor", "Category: $category")
        Log.d("RecognitionMonitor", "Brand: $detectedBrand")
        Log.d("RecognitionMonitor", "Pickup Location: $pickupLocation")
        Log.d("RecognitionMonitor", "------------------------------------")
        
        return RecognitionResult(takeoutCode, qrCode, category, detectedBrand, rawFullText, pickupLocation)
    }

    private fun findPickupLocation(mergedText: String, blocks: List<com.google.mlkit.vision.text.Text.TextBlock>): String? {
        val startKeywords = listOf("已到", "已至", "到达", "到了", "在", "于", "己到", "前往", "送到", "至", "前住")
        val targetKeywords = listOf("服务站", "驿站", "自提点", "快递站", "菜鸟站", "代收点", "代点", "丰巢柜", "快递柜", "智能柜", "门面")
        val stopKeywords = listOf("领取", "取件", "查看", "请凭", "如有", "如有疑问", "取您的")
        
        val locWithTargetPattern = Regex("(?:${startKeywords.joinToString("|")})([^,，。！!?;？\\s]{2,60}?(?:${targetKeywords.joinToString("|")}))")
        val locMatch1 = locWithTargetPattern.find(mergedText)
        if (locMatch1 != null) return truncateLocation(locMatch1.groupValues[1])
        
        val locToVerbPattern = Regex("(?:${startKeywords.joinToString("|")})([^,，。！!?;？\\s]{4,60}?)(?=${stopKeywords.joinToString("|")})")
        val locMatch2 = locToVerbPattern.find(mergedText)
        if (locMatch2 != null) return truncateLocation(locMatch2.groupValues[1])
        
        return blocks.asSequence()
            .map { it.text.replace("\n", "").replace(" ", "") }
            .filter { text -> targetKeywords.any { text.contains(it) } }
            .maxByOrNull { it.length }
            ?.let { truncateLocation(it) }
    }

    private fun cleanChineseText(text: String): String {
        return text.replace(Regex("(?<=[\\u4e00-\\u9fa5A-Z0-9-])\\s+(?=[\\u4e00-\\u9fa5])"), "")
            .replace("\n", "")
            .replace("|", "")
            .replace("包 裏", "包裹")
            .replace("己到", "已到")
            .replace("己至", "已至")
            .replace("取性码", "取件码")
            .replace("前住", "前往")
            .replace("取養号", "取餐号")
    }

    private fun truncateLocation(location: String): String {
        val stopKeywords = listOf("请凭", "取件", "领取", "复制", "查看", "日已接收", "日已签收", "日已到", "点击", "联系", "如有", "如有疑问", "取您的")
        var result = location
        for (stop in stopKeywords) {
            val index = result.indexOf(stop)
            if (index != -1) {
                result = result.substring(0, index)
            }
        }
        return result.replace("[,，。！!?;？;\\s]+$".toRegex(), "")
    }

    private fun isInvalidValue(value: String, context: String): Boolean {
        if (value.startsWith("202") && value.length == 4) return true 
        if (context.contains(":") || context.contains("/")) {
            if (value.all { it.isDigit() || it == ':' || it == '/' }) return true
        }
        if (context.contains("时间") || context.contains("日期") || context.contains("预计获得") || context.contains("积分")) return true

        val lowerContext = context.lowercase()
        val distractions = listOf("ml", "g", "元", "¥", "购", "券", "赢", "送", "补贴", "减", "满", "起", "合计", "实付")
        if (distractions.any { lowerContext.contains(it) && lowerContext.indexOf(it) in (lowerContext.indexOf(value)-2)..(lowerContext.indexOf(value)+value.length+2) }) return true
        return false
    }

    fun close() {
        textRecognizer.close()
        barcodeScanner.close()
    }
}

data class RecognitionResult(val code: String?, val qr: String?, val type: String, val brand: String?, val fullText: String, val pickupLocation: String? = null)
