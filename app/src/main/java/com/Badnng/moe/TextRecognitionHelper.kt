package com.Badnng.moe

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class TextRecognitionHelper(private val context: Context) {
    // 保留 ML Kit 条码扫描
    private val barcodeScanner = BarcodeScanning.getClient()

    // PaddleOCR 文字识别
    private val paddleOcr = PaddleOcrHelper.getInstance(context)

    private val drinkBrands = listOf("星巴克", "瑞幸", "喜茶", "奈雪", "霸王茶姬", "茶百道", "蜜雪冰城", "一点点", "古茗", "Manner", "山楂奶绿", "取茶", "奶茶", "茶颜悦色")
    private val foodBrands = listOf("麦当劳", "肯德基", "KFC", "汉堡王", "塔斯汀", "老乡鸡", "华莱士")
    private val homePageKeywords = listOf("我的", "首页", "会员码", "到店取餐", "点单", "会员", "我的订单")

    /**
     * 初始化 OCR 引擎（需要在应用启动时调用）
     */
    fun initOcr(): Boolean {
        return paddleOcr.init()
    }

    suspend fun recognizeAll(bitmap: Bitmap, sourceApp: String? = null, sourcePkg: String? = null): RecognitionResult {
        Log.d("RecognitionMonitor", "=== recognizeAll 开始 ===")

        // 确保 OCR 已初始化（最多等待 3 秒）
        var waitCount = 0
        while (!paddleOcr.isInitialized && waitCount < 30) {
            kotlinx.coroutines.delay(100)
            waitCount++
        }
        if (!paddleOcr.isInitialized) {
            Log.e("RecognitionMonitor", "OCR 未初始化，尝试同步初始化")
            paddleOcr.init()
        }

        // 使用 PaddleOCR 进行文字识别
        val ocrResult = paddleOcr.recognize(bitmap)
        Log.d("RecognitionMonitor", "OCR result: ${if (ocrResult != null) "not null, blocks=${ocrResult.textBlocks.size}" else "NULL"}")
        val rawFullText = ocrResult?.fullText ?: ""
        val textBlocks = ocrResult?.textBlocks ?: emptyList()

        // 保留 ML Kit 条码扫描
        val image = InputImage.fromBitmap(bitmap, 0)
        val barcodeResult = try {
            withContext(Dispatchers.Main) {
                barcodeScanner.process(image).await()
            }
        } catch (e: Exception) { null }

        val mergedText = cleanChineseText(rawFullText)
        Log.d("RecognitionMonitor", "rawFullText length=${rawFullText.length}, mergedText length=${mergedText.length}")
        Log.d("RecognitionMonitor", "mergedText preview=${mergedText.take(100)}")

        val hasTakeoutKeywords = mergedText.contains("取餐") || mergedText.contains("取茶") ||
                mergedText.contains("取件") || mergedText.contains("取性") ||
                mergedText.contains("验证码") || mergedText.contains("券码") ||
                mergedText.contains("订单") || mergedText.contains("准备完毕") ||
                mergedText.contains("领取") || mergedText.contains("取件码") ||
                mergedText.contains("取養")

        val homePageElementCount = homePageKeywords.count { mergedText.contains(it) }
        // 快递页面不做首页判断（快递页不存在首页误判问题，强行跳过会漏掉取件码）
        // 同时提高阈值到3，避免"我的包裹"这类文字误触发
        val isLikelyHomePage = homePageElementCount >= 3

        var qrCode = barcodeResult?.firstOrNull()?.rawValue
        if (qrCode != null && (qrCode.contains("http://", ignoreCase = true) || qrCode.contains("https://", ignoreCase = true))) {
            qrCode = null
        }

        var takeoutCode: String? = null
        var pickupLocation: String? = null

        var detectedBrand: String? = when (sourcePkg) {
            "com.mcdonalds.gma.cn" -> "麦当劳"
            "com.yek.android.kfc.activitys" -> "肯德基"
            "com.lucky.luckyclient" -> "瑞幸"
            "com.mxbc.mxsa" -> "蜜雪冰城"
            "com.starbucks.cn" -> "星巴克"
            "com.heyteago" -> "喜茶"
            else -> null
        }

        // 检测到啡快口令，品牌直接设为星巴克
        if (mergedText.contains("啡快口令")) {
            detectedBrand = "星巴克"
        } else if (detectedBrand == null) {
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

        // 先判断 category，再根据 category 走不同的识别路径
        var category = "餐食"
        if (drinkBrands.contains(detectedBrand) || mergedText.contains("奶茶") || mergedText.contains("咖啡")) {
            category = "饮品"
        } else if (mergedText.contains("取件") || mergedText.contains("取性") || mergedText.contains("快递") || mergedText.contains("包裹") || mergedText.contains("待取件") || mergedText.contains("丰巢")) {
            category = "快递"
        }

        if (!isLikelyHomePage || category == "快递") {
            takeoutCode = if (category == "快递") {
                extractExpressCode(textBlocks, mergedText)
            } else {
                extractFoodCode(textBlocks, mergedText, detectedBrand, qrCode)
            }
        }

        if (detectedBrand == "瑞幸" && qrCode == null) {
            takeoutCode = null
        }

        pickupLocation = findPickupLocation(mergedText, textBlocks)

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

    // ─────────── 快递取件码识别 ───────────
    private fun extractExpressCode(
        blocks: List<PaddleOcrHelper.TextBlock>,
        mergedText: String
    ): String? {
        // 扩展关键词：支持"取件码"、"请凭"、"靖凭"(OCR错误)等
        val expressKeywords = listOf("取件码", "取性码", "请凭", "靖凭", "凭")

        // 第一步：精确从"取件码:"后截取
        for (block in blocks) {
            val text = block.text.replace("\n", "")
                .replace(Regex("\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}"), "") // 清理日期时间（保留空格再清）
                .replace(" ", "")
            Log.d("RecognitionMonitor", "ExpressBlock: [$text]")
            val matchedKeyword = expressKeywords.firstOrNull { text.contains(it) } ?: continue
            val afterKeyword = text.substringAfter(matchedKeyword).trimStart(':', '：', ' ')
            Log.d("RecognitionMonitor", "AfterKeyword: [$afterKeyword]")
            
            // 🚀 优化：支持更多格式的快递取件码
            // 1. 三段式连字符（A-2-7261, 4-3-958, 10-3-0221）
            val dashMatch3 = Regex("([A-Z0-9]+-[A-Z0-9]+-[A-Z0-9]+)").find(afterKeyword)
            // 2. 两段式连字符（ZT-20001, A1-12, ZT20001）
            val dashMatch2 = Regex("([A-Z]{1,3}[0-9]{0,3}-[0-9]{3,8}|[A-Z0-9]{1,4}-[A-Z0-9]{3,8})").find(afterKeyword)
            // 3. 纯数字（39359）
            val numMatch = Regex("(?<![0-9])([0-9]{4,8})(?![0-9])").find(afterKeyword)
            // 4. 字母数字混合（ZT20001, A1121111）
            val alphaNumMatch = Regex("([A-Z]{1,3}[0-9]{4,8}|[A-Z][0-9]{6,10})").find(afterKeyword)
            // 5. 通用模式（字母数字和连字符的组合）
            val alphaMatch = Regex("[A-Z0-9-]{3,12}").find(afterKeyword)
            
            Log.d("RecognitionMonitor", "dashMatch3=${dashMatch3?.value} dashMatch2=${dashMatch2?.value} numMatch=${numMatch?.value} alphaNumMatch=${alphaNumMatch?.value} alphaMatch=${alphaMatch?.value}")
            
            val match = dashMatch3 ?: dashMatch2 ?: numMatch ?: alphaNumMatch ?: alphaMatch
            if (match != null && !isInvalidExpressCode(match.value)) {
                return match.value
            }
        }

        // 第二步：兜底——在含快递关键词的文本中按权重筛选
        val hasExpressKeyword = mergedText.contains("取件码") || mergedText.contains("取性码") ||
                mergedText.contains("请凭") || mergedText.contains("靖凭")
        if (!hasExpressKeyword) return null
        val candidates = blocks.flatMap { block ->
            val text = block.text.replace(" ", "").replace("\n", "")
            // 🚀 优化：支持更多格式的快递取件码正则表达式
            val pattern = Regex("(?<![a-zA-Z0-9-])(" +
                "[0-9]{4,8}|" +  // 纯数字 4-8 位
                "[A-Z0-9]+-[A-Z0-9]+-[A-Z0-9]+|" +  // 三段式连字符
                "[A-Z]{1,3}[0-9]{0,3}-[0-9]{3,8}|" +  // 两段式：字母-数字
                "[A-Z0-9]{1,4}-[A-Z0-9]{3,8}|" +  // 两段式：字母数字-字母数字
                "[A-Z]{1,3}[0-9]{4,8}|" +  // 字母开头+数字
                "[A-Z][0-9]{6,10}|" +  // 单字母+数字
                "[A-Z0-9][A-Z0-9-]{2,11}" +  // 通用模式
                ")(?![a-zA-Z0-9-])")
            pattern.findAll(text).mapNotNull { match ->
                val value = match.value
                if (isInvalidExpressCode(value)) return@mapNotNull null
                var weight = (block.boundingBox?.width() ?: 0) * value.length
                // 给带连字符的格式更高权重
                if (value.contains("-")) weight *= 20
                // 给字母开头的格式更高权重
                if (value.any { it.isLetter() }) weight *= 5
                value to weight
            }.toList()
        }.sortedByDescending { it.second }
        return candidates.firstOrNull()?.first
    }

    private fun isInvalidExpressCode(value: String): Boolean {
        // 过滤年份
        if (value.startsWith("202") && value.length == 4) return true
        // 过滤纯日期 03-10 及其拼接产生的垃圾
        if (Regex("^\\d{2}-\\d{2,6}$").matches(value)) return true
        // 过滤超长快递单号（快递取件码一般不超过12位）
        if (value.length > 12) return true
        return false
    }

    // ─────────── 餐食/饮品取餐码识别 ───────────
    private fun extractFoodCode(
        blocks: List<PaddleOcrHelper.TextBlock>,
        mergedText: String,
        detectedBrand: String?,
        qrCode: String?
    ): String? {
        // 星巴克啡快口令识别：格式为 "数字.文字" 如 "17.超常发挥"
        if (detectedBrand == "星巴克" || mergedText.contains("啡快口令")) {
            for (block in blocks) {
                val text = block.text.replace(" ", "").replace("\n", "")
                // 匹配 "数字.文字" 或 "数字．文字" 格式
                val starbucksMatch = Regex("(\\d{1,3}[.．][\\u4e00-\\u9fa5]{2,10})").find(text)
                if (starbucksMatch != null) {
                    return starbucksMatch.value
                }
            }
        }

        val foodKeywords = listOf("取餐号", "取餐码", "取茶号", "券码", "订单号", "取性码", "取養号")
        val hasFoodKeywords = mergedText.contains("取餐") || mergedText.contains("取茶") ||
                mergedText.contains("验证码") || mergedText.contains("券码") ||
                mergedText.contains("订单") || mergedText.contains("准备完毕") ||
                mergedText.contains("领取") || mergedText.contains("取養")

        var targetKeywordRect: android.graphics.Rect? = null

        // 第一步：精确从关键词后截取
        for (block in blocks) {
            val text = block.text.replace(" ", "").replace("\n", "")
            val matchedKeyword = foodKeywords.firstOrNull { text.contains(it) } ?: continue
            targetKeywordRect = block.boundingBox
            val afterKeyword = text.substringAfter(matchedKeyword).trimStart(':', '：', ' ')
            val match = Regex("[A-Z0-9]{3,10}").find(afterKeyword)
            if (match != null && !foodKeywords.any { it.contains(match.value) || match.value.contains(it) }) {
                if (!isInvalidFoodCode(match.value, afterKeyword, detectedBrand)) {
                    return match.value
                }
            }
        }

        // 第二步：在关键词附近 block 中搜索
        if (targetKeywordRect != null) {
            val candidates = blocks.mapNotNull { block ->
                val box = block.boundingBox ?: return@mapNotNull null
                val text = block.text.replace(" ", "").replace("\n", "")
                if (Regex("^[A-Z0-9]{3,10}$").matches(text) && !isInvalidFoodCode(text, text, detectedBrand)) {
                    val dist = Math.abs((box.top + box.bottom) / 2 - (targetKeywordRect!!.top + targetKeywordRect!!.bottom) / 2)
                    if (dist < 400) text to dist else null
                } else null
            }.sortedBy { it.second }
            candidates.firstOrNull()?.first?.let { return it }
        }

        // 第三步：全文兜底按权重搜索
        if (!hasFoodKeywords) return null
        val pattern = Regex("(?<![a-zA-Z0-9])([A-Z0-9]{3,10})(?![a-zA-Z0-9])")
        val candidates = blocks.flatMap { block ->
            val text = block.text.replace(" ", "").replace("\n", "")
            pattern.findAll(text).mapNotNull { match ->
                val value = match.value
                if (isInvalidFoodCode(value, text, detectedBrand)) return@mapNotNull null
                var weight = (block.boundingBox?.width() ?: 0) * value.length
                if (detectedBrand == "肯德基" || detectedBrand == "麦当劳") {
                    if (value.length >= 4 && value.any { it.isLetter() }) weight *= 20
                    if (value.length == 5 && value.all { it.isDigit() }) weight *= 5
                }
                if (detectedBrand == "喜茶" && value.length == 4 && value.all { it.isDigit() }) weight *= 5
                value to weight
            }.toList()
        }.sortedByDescending { it.second }
        return candidates.firstOrNull()?.first
    }

    private fun isInvalidFoodCode(value: String, context: String, detectedBrand: String?): Boolean {
        if (value.startsWith("202") && value.length == 4) return true
        if (context.contains(":") || context.contains("/")) {
            if (value.all { it.isDigit() || it == ':' || it == '/' }) return true
        }
        if (context.contains("时间") || context.contains("日期") || context.contains("预计获得") || context.contains("积分")) return true
        val lowerContext = context.lowercase()
        val distractions = listOf("ml", "g", "元", "¥", "购", "券", "赢", "送", "补贴", "减", "满", "起", "合计", "实付")
        if (distractions.any { lowerContext.contains(it) && lowerContext.indexOf(it) in (lowerContext.indexOf(value) - 2)..(lowerContext.indexOf(value) + value.length + 2) }) return true
        return false
    }

    private fun findPickupLocation(mergedText: String, blocks: List<PaddleOcrHelper.TextBlock>): String? {
        // 移除"至"，因为它太短容易误匹配（如"己放至代收点"）
        val startKeywords = listOf("已到", "已至", "到达", "到了", "在", "于", "己到", "前往", "送到", "前住")
        val targetKeywords = listOf("服务站", "驿站", "自提点", "快递站", "菜鸟站", "代收点", "代点", "丰巢柜", "快递柜", "智能柜", "门面", "邮政大厅", "大厅")
        val stopKeywords = listOf("领取", "取件", "查看", "请凭", "靖凭", "如有", "如有疑问", "取您的", "复制")

        // 辅助函数：检查是否为垃圾匹配（包含快递单号等）
        fun isGarbageMatch(location: String): Boolean {
            // 匹配到"代收点(快递单号"这种格式，不是真正的地点
            if (location.contains("代收点(") || location.contains("代收点（")) return true
            // 包含超长数字串（快递单号）
            if (Regex("\\d{10,}").containsMatchIn(location)) return true
            return false
        }

        // 辅助函数：计算地点质量分数（包含的目标关键词数量）
        fun locationScore(location: String): Int {
            var score = location.length
            // 每包含一个目标关键词加分
            for (keyword in targetKeywords) {
                if (location.contains(keyword)) score += 20
            }
            return score
        }

        val candidates = mutableListOf<Pair<String, Int>>()

        // 最高优先级：匹配"地址:"后面内容，贪婪匹配到目标关键词为止
        val addressPattern = Regex("地址[:：\\s]*(.{4,80}?(?:${targetKeywords.joinToString("|")}))")
        val addressMatch = addressPattern.find(mergedText)
        if (addressMatch != null) {
            val loc = truncateLocation(addressMatch.groupValues[1])
            if (!isGarbageMatch(loc)) candidates.add(loc to locationScore(loc) + 1000)
        }
        // 次级：地址后无目标关键词时，按标点截断
        val addressFallback = Regex("地址[:：\\s]*([^,，。！!?；;.\\n]{4,60})")
        val fallbackMatch = addressFallback.find(mergedText)
        if (fallbackMatch != null) {
            val candidate = truncateLocation(fallbackMatch.groupValues[1])
            if (candidate.length > 8 && !isGarbageMatch(candidate)) {
                candidates.add(candidate to locationScore(candidate) + 500)
            }
        }

        // 匹配 startKeywords + targetKeywords
        val locWithTargetPattern = Regex("(?:${startKeywords.joinToString("|")})([^,，。！!?;？\\s]{2,60}?(?:${targetKeywords.joinToString("|")}))")
        for (match in locWithTargetPattern.findAll(mergedText)) {
            val loc = truncateLocation(match.groupValues[1])
            if (!isGarbageMatch(loc)) candidates.add(loc to locationScore(loc) + 100)
        }

        // 匹配 startKeywords + content + (?=stopKeywords)
        val locToVerbPattern = Regex("(?:${startKeywords.joinToString("|")})([^,，。！!?;？\\s]{4,60}?)(?=${stopKeywords.joinToString("|")})")
        val locMatch2 = locToVerbPattern.find(mergedText)
        if (locMatch2 != null) {
            val loc = truncateLocation(locMatch2.groupValues[1])
            if (!isGarbageMatch(loc)) candidates.add(loc to locationScore(loc))
        }

        // 从 blocks 中找包含目标关键词的文本
        for (block in blocks) {
            val text = block.text.replace("\n", "").replace(" ", "")
            if (targetKeywords.any { text.contains(it) }) {
                val loc = truncateLocation(text)
                if (!isGarbageMatch(loc)) candidates.add(loc to locationScore(loc))
            }
        }

        // 返回分数最高的候选
        return candidates.maxByOrNull { it.second }?.first
    }

    private fun cleanChineseText(text: String): String {
        return text
            .replace(Regex("\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}"), "") // 清理"03-10 10:36"格式（空格分隔才清理）
            .replace(Regex("(?<=[\\u4e00-\\u9fa5A-Z0-9-])\\s+(?=[\\u4e00-\\u9fa5])"), "")
            .replace("\n", "")
            .replace("|", "")
            .replace("包 裹", "包裹")
            .replace("己到", "已到")
            .replace("己至", "已至")
            .replace("取性码", "取件码")
            .replace("前住", "前往")
            .replace("取養号", "取餐号")
            .replace("靖凭", "请凭") // OCR错误纠正
            .replace("冰域", "冰城")
    }

    private fun truncateLocation(location: String): String {
        val stopKeywords = listOf("请凭", "靖凭", "取件", "领取", "复制", "查看", "日已接收", "已接收", "日已签收", "日已到", "点击", "联系", "如有", "如有疑问", "取您的")
        var result = location
        // 分号通常是地址段的分隔符，直接截断
        val semicolonIdx = result.indexOf(';')
        if (semicolonIdx != -1) result = result.substring(0, semicolonIdx)
        for (stop in stopKeywords) {
            val index = result.indexOf(stop)
            if (index != -1) result = result.substring(0, index)
        }
        return result.replace("[,，。！!?;？;|\\s]+$".toRegex(), "")
    }

    // ─────────── 纯文字识别（用于划选文字处理） ───────────
    fun recognizeFromText(text: String): RecognitionResult {
        val mergedText = cleanChineseText(text)

        // 品牌识别
        var detectedBrand: String? = null
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
        if (detectedBrand == "KFC") detectedBrand = "肯德基"
        
        // 检测到啡快口令，品牌直接设为星巴克
        if (mergedText.contains("啡快口令")) {
            detectedBrand = "星巴克"
        }


        // 类型判断
        var category = "餐食"
        if (drinkBrands.contains(detectedBrand) || mergedText.contains("奶茶") || mergedText.contains("咖啡")) {
            category = "饮品"
        } else if (mergedText.contains("取件") || mergedText.contains("取性") || mergedText.contains("快递") ||
            mergedText.contains("包裹") || mergedText.contains("待取件") || mergedText.contains("丰巢")) {
            category = "快递"
        }

        // 提取取件码
        val takeoutCode = if (category == "快递") {
            extractExpressCodeFromText(mergedText)
        } else {
            extractFoodCodeFromText(mergedText, detectedBrand)
        }

        // 提取取货地点
        val pickupLocation = findPickupLocation(mergedText, emptyList())

        Log.d("ProcessTextRecognition", "------------------------------------")
        Log.d("ProcessTextRecognition", "Input Text: $mergedText")
        Log.d("ProcessTextRecognition", "Extracted Code: $takeoutCode")
        Log.d("ProcessTextRecognition", "Category: $category")
        Log.d("ProcessTextRecognition", "Brand: $detectedBrand")
        Log.d("ProcessTextRecognition", "Pickup Location: $pickupLocation")
        Log.d("ProcessTextRecognition", "------------------------------------")

        return RecognitionResult(takeoutCode, null, category, detectedBrand, text, pickupLocation)
    }

    private fun extractExpressCodeFromText(mergedText: String): String? {
        val expressKeywords = listOf("取件码", "取性码", "请凭", "靖凭", "凭")
        val hasExpressKeyword = mergedText.contains("取件码") || mergedText.contains("取性码") ||
                mergedText.contains("请凭") || mergedText.contains("靖凭")

        // 第一步：精确从关键词后截取
        val matchedKeyword = expressKeywords.firstOrNull { mergedText.contains(it) }
        if (matchedKeyword != null) {
            val afterKeyword = mergedText.substringAfter(matchedKeyword).trimStart(':', '：', ' ')
            
            // 🚀 优化：支持更多格式的快递取件码
            // 1. 三段式连字符（A-2-7261, 4-3-958, 10-3-0221）
            val dashMatch3 = Regex("([A-Z0-9]+-[A-Z0-9]+-[A-Z0-9]+)").find(afterKeyword)
            // 2. 两段式连字符（ZT-20001, A1-12, ZT20001）
            val dashMatch2 = Regex("([A-Z]{1,3}[0-9]{0,3}-[0-9]{3,8}|[A-Z0-9]{1,4}-[A-Z0-9]{3,8})").find(afterKeyword)
            // 3. 纯数字（39359）
            val numMatch = Regex("(?<![0-9])([0-9]{4,8})(?![0-9])").find(afterKeyword)
            // 4. 字母数字混合（ZT20001, A1121111）
            val alphaNumMatch = Regex("([A-Z]{1,3}[0-9]{4,8}|[A-Z][0-9]{6,10})").find(afterKeyword)
            // 5. 通用模式（字母数字和连字符的组合）
            val alphaMatch = Regex("[A-Z0-9-]{3,12}").find(afterKeyword)
            
            val match = dashMatch3 ?: dashMatch2 ?: numMatch ?: alphaNumMatch ?: alphaMatch
            if (match != null && !isInvalidExpressCode(match.value)) {
                return match.value
            }
        }

        // 第二步：兜底——按权重筛选
        if (!hasExpressKeyword) return null
        // 🚀 优化：支持更多格式的快递取件码正则表达式
        val pattern = Regex("(?<![a-zA-Z0-9-])(" +
            "[0-9]{4,8}|" +  // 纯数字 4-8 位
            "[A-Z0-9]+-[A-Z0-9]+-[A-Z0-9]+|" +  // 三段式连字符
            "[A-Z]{1,3}[0-9]{0,3}-[0-9]{3,8}|" +  // 两段式：字母-数字
            "[A-Z0-9]{1,4}-[A-Z0-9]{3,8}|" +  // 两段式：字母数字-字母数字
            "[A-Z]{1,3}[0-9]{4,8}|" +  // 字母开头+数字
            "[A-Z][0-9]{6,10}|" +  // 单字母+数字
            "[A-Z0-9][A-Z0-9-]{2,11}" +  // 通用模式
            ")(?![a-zA-Z0-9-])")
        val candidates = pattern.findAll(mergedText).mapNotNull { match ->
            val value = match.value
            if (isInvalidExpressCode(value)) return@mapNotNull null
            var weight = value.length
            // 给带连字符的格式更高权重
            if (value.contains("-")) weight *= 20
            // 给字母开头的格式更高权重
            if (value.any { it.isLetter() }) weight *= 5
            value to weight
        }.sortedByDescending { it.second }
        return candidates.firstOrNull()?.first
    }

    private fun extractFoodCodeFromText(mergedText: String, detectedBrand: String?): String? {
        val foodKeywords = listOf("取餐号", "取餐码", "取茶号", "券码", "订单号", "取性码", "取養号")
        val hasFoodKeywords = mergedText.contains("取餐") || mergedText.contains("取茶") ||
                mergedText.contains("验证码") || mergedText.contains("券码") ||
                mergedText.contains("订单") || mergedText.contains("准备完毕") ||
                mergedText.contains("领取") || mergedText.contains("取養")

        // 第一步：精确从关键词后截取
        val matchedKeyword = foodKeywords.firstOrNull { mergedText.contains(it) }
        if (matchedKeyword != null) {
            val afterKeyword = mergedText.substringAfter(matchedKeyword).trimStart(':', '：', ' ')
            val match = Regex("[A-Z0-9]{3,10}").find(afterKeyword)
            if (match != null && !foodKeywords.any { it.contains(match.value) || match.value.contains(it) }) {
                if (!isInvalidFoodCode(match.value, afterKeyword, detectedBrand)) {
                    return match.value
                }
            }
        }

        // 第二步：全文兜底按权重搜索
        if (!hasFoodKeywords) return null
        val pattern = Regex("(?<![a-zA-Z0-9])([A-Z0-9]{3,10})(?![a-zA-Z0-9])")
        val candidates = pattern.findAll(mergedText).mapNotNull { match ->
            val value = match.value
            if (isInvalidFoodCode(value, mergedText, detectedBrand)) return@mapNotNull null
            var weight = value.length
            if (detectedBrand == "肯德基" || detectedBrand == "麦当劳") {
                if (value.length >= 4 && value.any { it.isLetter() }) weight *= 20
                if (value.length == 5 && value.all { it.isDigit() }) weight *= 5
            }
            if (detectedBrand == "喜茶" && value.length == 4 && value.all { it.isDigit() }) weight *= 5
            value to weight
        }.sortedByDescending { it.second }
        return candidates.firstOrNull()?.first
    }

    fun close() {
        barcodeScanner.close()
        // 不要关闭 paddleOcr，因为它是单例，应该保持初始化状态
    }
}

data class RecognitionResult(val code: String?, val qr: String?, val type: String, val brand: String?, val fullText: String, val pickupLocation: String? = null)