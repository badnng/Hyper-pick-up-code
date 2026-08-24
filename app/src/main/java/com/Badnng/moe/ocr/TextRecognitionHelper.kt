package com.Badnng.moe.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.Badnng.moe.rules.RecognitionRuleEngine
import com.Badnng.moe.rules.SimpleRuleMatch
import com.Badnng.moe.rules.SimpleRuleRuntime
import com.Badnng.moe.rules.SimpleRuleSource
import com.Badnng.moe.rules.LuckinQrRule
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class TextRecognitionHelper(private val context: Context) {
    // 保留 ML Kit 条码扫描（Google Play Services 不可用时为 null）
    private val barcodeScanner = try {
        BarcodeScanning.getClient()
    } catch (e: Exception) {
        Log.w("TextRecognitionHelper", "BarcodeScanning not available", e)
        null
    }

    // PaddleOCR 文字识别
    val paddleOcr = PaddleOcrHelper.getInstance(context)

    private val engine = RecognitionRuleEngine
    private val drinkBrands get() = engine.getAllDrinkNames()
    private val foodBrands get() = engine.getAllFoodNames()
    private val expressBrandKeywords get() = engine.getAllExpressKeywords()
    private val homePageKeywords get() = engine.getHomepageKeywords()

    /** 可选的 OCR 预热；正常识别会按需自动初始化。 */
    suspend fun initOcr(): Boolean = paddleOcr.initAsync()

    /**
     * OCR 结果数据类，用于在多个识别方法间复用
     */
    data class OcrResult(
        val rawFullText: String,
        val textBlocks: List<PaddleOcrHelper.TextBlock>,
        val mergedText: String,
        val correctedBlocks: List<PaddleOcrHelper.TextBlock>,
        val diagnosticResult: PaddleOcrHelper.DiagnosticResult?,
        val simpleRuleMatches: List<SimpleRuleMatch>? = null,
    )

    suspend fun recognizeAll(bitmap: Bitmap, sourceApp: String? = null, sourcePkg: String? = null, existingOcr: OcrResult? = null): Pair<RecognitionResult, OcrResult> {
        Log.d("RecognitionMonitor", "=== recognizeAll 开始 ===")

        // 使用已有的 OCR 结果或重新识别
        val ocrResult: OcrResult
        val barcodeResult: List<com.google.mlkit.vision.barcode.common.Barcode>?

        if (existingOcr != null) {
            ocrResult = existingOcr
            barcodeResult = null
            Log.d("RecognitionMonitor", "复用已有 OCR 结果")
        } else {
            val rawOcr = paddleOcr.recognizeAsync(bitmap)
            Log.d("RecognitionMonitor", "OCR result: ${if (rawOcr != null) "not null, blocks=${rawOcr.textBlocks.size}" else "NULL"}")
            val rawFullText = rawOcr?.fullText ?: ""
            val textBlocks = rawOcr?.textBlocks ?: emptyList()
            val mergedText = cleanChineseText(rawFullText)
            val correctedBlocks = textBlocks.map { block ->
                block.copy(text = applyCorrections(block.text))
            }
            ocrResult = OcrResult(
                rawFullText = rawFullText,
                textBlocks = textBlocks,
                mergedText = mergedText,
                correctedBlocks = correctedBlocks,
                diagnosticResult = rawOcr?.diagnosticResult,
            )

            // 保留 ML Kit 条码扫描
            barcodeResult = barcodeScanner?.let { scanner ->
                val image = InputImage.fromBitmap(bitmap, 0)
                try {
                    withContext(Dispatchers.Main) {
                        scanner.process(image).await()
                    }
                } catch (e: Exception) { null }
            }
        }

        val rawFullText = ocrResult.rawFullText
        val textBlocks = ocrResult.textBlocks
        val mergedText = ocrResult.mergedText
        val correctedBlocks = ocrResult.correctedBlocks
        Log.d("RecognitionMonitor", "rawFullText length=${rawFullText.length}, mergedText length=${mergedText.length}")
        Log.d("RecognitionMonitor", "rawFullText: $rawFullText")
        Log.d("RecognitionMonitor", "mergedText: $mergedText")

        val homePageElementCount = homePageKeywords.count { mergedText.contains(it) }
        // 快递页面不做首页判断（快递页不存在首页误判问题，强行跳过会漏掉取件码）
        // 同时提高阈值到3，避免"我的包裹"这类文字误触发
        val isLikelyHomePage = homePageElementCount >= engine.rules.homepageDetection.threshold

        var qrCode = barcodeResult?.firstOrNull()?.rawValue
        if (qrCode != null && (qrCode.contains("http://", ignoreCase = true) || qrCode.contains("https://", ignoreCase = true))) {
            qrCode = null
        }

        val simplePack = SimpleRuleRuntime.ensureLoaded(context)
        if (simplePack.schemaVersion == com.Badnng.moe.rules.SimpleRulePack.SCHEMA_VERSION) {
            val matches = SimpleRuleRuntime.recognizeCurrent(
                rawText = rawFullText,
                source = SimpleRuleSource.IMAGE,
                sourcePackage = sourcePkg,
                qrData = qrCode,
            )
            val first = matches.firstOrNull()
            val builtInLuckinQrHit = LuckinQrRule.matches(qrCode)
            Log.d("RecognitionMonitor", "SimpleRule match: brand=${first?.brand}, brandRuleId=${first?.brandRuleId}, template=${first?.templateRuleName}, templateId=${first?.templateRuleId}, code=${first?.code}, location=${first?.location}")
            return RecognitionResult(
                code = first?.code,
                qr = qrCode,
                type = if (builtInLuckinQrHit) LuckinQrRule.CATEGORY else first?.category?.resultType ?: "餐食",
                brand = if (builtInLuckinQrHit) LuckinQrRule.BRAND_NAME else first?.brand,
                fullText = rawFullText,
                pickupLocation = first?.location,
            ) to ocrResult.copy(simpleRuleMatches = matches)
        }
        var takeoutCode: String? = null
        var pickupLocation: String? = null

        var detectedBrand: String? = if (LuckinQrRule.matches(qrCode)) {
            LuckinQrRule.BRAND_NAME
        } else {
            sourcePkg?.let { engine.getBrandByPackage(it) }
        }

        // QR 码模式匹配品牌（如瑞幸的 Base64 短码）
        if (detectedBrand == null && qrCode != null) {
            Log.d("RecognitionMonitor", "QR品牌匹配: qrCode=$qrCode, 品牌数=${engine.getAllBrands().size}")
            for (brandDef in engine.getAllBrands()) {
                Log.d("RecognitionMonitor", "  品牌=${brandDef.name}, qrPattern=${brandDef.qrPattern}")
                if (brandDef.qrPattern != null && Regex(brandDef.qrPattern).matches(qrCode)) {
                    detectedBrand = brandDef.name
                    Log.d("RecognitionMonitor", "  QR品牌命中: ${brandDef.name}")
                    break
                }
            }
        }

        // 检测到啡快口令，品牌直接设为星巴克
        val starbucksPattern = engine.rules.codeExtraction.food.starbucksPattern
        if (starbucksPattern != null && starbucksPattern.triggerKeywords.any { mergedText.contains(it) }) {
            detectedBrand = starbucksPattern.triggerBrand
        } else if (detectedBrand == null) {
            val brandHits = mutableMapOf<String, Int>()
            val scoringConfig = engine.rules.scoring.brandDetection
            for (brand in engine.getDrinkBrands() + engine.getFoodBrands()) {
                if (brand.keywords.isNotEmpty() && brand.keywords.any { mergedText.contains(it) }) {
                    val score = brand.scoringOverrides["exact_match_weight"] ?: scoringConfig.exactMatchWeight
                    brandHits[brand.name] = (brandHits[brand.name] ?: 0) + score
                }
                if (mergedText.contains(brand.name, ignoreCase = true)) {
                    val colonScore = if (Regex("${Regex.escape(brand.name)}[:：]\\d").containsMatchIn(mergedText)) scoringConfig.colonMatchWeight else scoringConfig.keywordMatchWeight
                    brandHits[brand.name] = (brandHits[brand.name] ?: 0) + colonScore
                }
                for (alias in brand.aliases) {
                    if (mergedText.contains(alias, ignoreCase = true)) {
                        val colonScore = if (Regex("${Regex.escape(alias)}[:：]\\d").containsMatchIn(mergedText)) scoringConfig.colonMatchWeight else scoringConfig.keywordMatchWeight
                        brandHits[brand.name] = (brandHits[brand.name] ?: 0) + colonScore
                    }
                }
            }
            detectedBrand = brandHits.maxByOrNull { it.value }?.key
        }

        // 先判断 category，再根据 category 走不同的识别路径
        val catConfig = engine.rules.categoryDetection
        var category = catConfig.defaultCategory
        if ((catConfig.drinkTriggers.brandBased && drinkBrands.contains(detectedBrand)) ||
            catConfig.drinkTriggers.textKeywords.any { mergedText.contains(it) }
        ) {
            category = "饮品"
        } else if (
            catConfig.expressTriggers.textKeywords.any { mergedText.contains(it) } ||
            (catConfig.expressTriggers.brandInText && expressBrandKeywords.any { mergedText.contains(it) })
        ) {
            category = "快递"
        }

        var matchId: String? = null

        if (!isLikelyHomePage || category == "快递") {
            if (category == "快递") {
                takeoutCode = extractExpressCode(correctedBlocks, mergedText)
            } else {
                val result = extractFoodCode(correctedBlocks, mergedText, detectedBrand, qrCode)
                takeoutCode = result.first
                matchId = result.second
            }
        }

        if (detectedBrand == "瑞幸" && qrCode == null) {
            takeoutCode = null
        }

        pickupLocation = findPickupLocation(mergedText, correctedBlocks)

        Log.d("RecognitionMonitor", "------------------------------------")
        Log.d("RecognitionMonitor", "Source App: $sourceApp")
        Log.d("RecognitionMonitor", "Source Package: $sourcePkg")
        Log.d("RecognitionMonitor", "Full Text: $mergedText")
        Log.d("RecognitionMonitor", "Extracted Code: $takeoutCode")
        Log.d("RecognitionMonitor", "MatchId: $matchId")
        Log.d("RecognitionMonitor", "QR Data: $qrCode")
        Log.d("RecognitionMonitor", "Category: $category")
        Log.d("RecognitionMonitor", "Brand: $detectedBrand")
        Log.d("RecognitionMonitor", "Pickup Location: $pickupLocation")
        Log.d("RecognitionMonitor", "------------------------------------")

        val result = RecognitionResult(takeoutCode, qrCode, category, detectedBrand, rawFullText, pickupLocation)
        return result to ocrResult
    }

    // ─────────── 快递取件码识别 ───────────
    private fun extractExpressCode(
        blocks: List<PaddleOcrHelper.TextBlock>,
        mergedText: String
    ): String? {
        val expressKeywords = engine.getExpressTriggerKeywords()

        fun pickCandidate(source: String, contextText: String): String? {
            // 使用引擎中的正则模式按优先级匹配
            for (pattern in engine.getExpressPatterns()) {
                val compiled = engine.getCompiledPattern(pattern.id) ?: continue
                val match = compiled.find(source)
                if (match != null) {
                    val value = match.value
                    if (isInvalidExpressCode(value)) continue
                    if (isLikelyPhoneTail(value, contextText)) continue
                    return value
                }
            }
            return null
        }

        // 第一步：精确从"取件码:"后截取
        val datetimePatternStr = engine.rules.textCleaning.datetimePattern
        val datetimeRegex = Regex(datetimePatternStr)
        for (i in blocks.indices) {
            val block = blocks[i]
            val text = block.text.replace("\n", "")
                .replace(datetimeRegex, "")
                .replace(" ", "")
            Log.d("RecognitionMonitor", "ExpressBlock: [$text]")
            val matchedKeyword = expressKeywords.firstOrNull { text.contains(it) } ?: continue
            val afterKeyword = text.substringAfter(matchedKeyword).trimStart(':', '：', ' ')
            Log.d("RecognitionMonitor", "AfterKeyword: [$afterKeyword]")

            val fromSameBlock = pickCandidate(afterKeyword, text)
            if (fromSameBlock != null) return fromSameBlock

            if (afterKeyword.isBlank()) {
                for (lookAhead in 1..3) {
                    val next = blocks.getOrNull(i + lookAhead) ?: break
                    val nextText = next.text.replace("\n", "").replace(" ", "")
                    val fromNext = pickCandidate(nextText, nextText)
                    if (fromNext != null) return fromNext
                }
            }
        }

        // 第二步：兜底——在含快递关键词的文本中按权重筛选
        val hasExpressKeyword = expressKeywords.any { mergedText.contains(it) }
        val hasExpressBrand = expressBrandKeywords.any { mergedText.contains(it) }
        if (hasExpressKeyword || !hasExpressBrand) return null
        val fallbackPattern = engine.getCompiledPattern("express_fallback") ?: return null
        val scoringConfig = engine.rules.scoring.expressCode
        val candidates = mutableListOf<Pair<String, Int>>()
        for (block in blocks) {
            val text = block.text.replace(" ", "").replace("\n", "")
            for (match in fallbackPattern.findAll(text)) {
                val value = match.value
                if (isInvalidExpressCode(value)) continue
                if (isLikelyPhoneTail(value, text)) continue
                var weight = (block.boundingBox?.width() ?: 0) * value.length
                if (value.contains("-")) weight *= scoringConfig.dashMultiplier
                if (value.any { it.isLetter() }) weight *= scoringConfig.letterMultiplier
                candidates.add(value to weight)
            }
        }
        candidates.sortByDescending { it.second }
        return candidates.firstOrNull()?.first
    }

    private fun isInvalidExpressCode(value: String, context: String = ""): Boolean {
        val vc = engine.rules.validation.expressCode
        if (vc.rejectAllLetters && value.all { it.isLetter() }) return true
        if (Regex(vc.rejectPhonePattern).matches(value)) return true
        if (value.startsWith(vc.rejectYearPrefix) && value.length == vc.rejectYearLength) return true
        if (Regex(vc.rejectDatePattern).matches(value)) return true
        if (value.length > vc.maxLength) return true
        // 时间相关拒绝
        if (context.isNotEmpty()) {
            if (vc.rejectTimeContexts.any { context.contains(it) }) {
                if (value.all { it.isDigit() || it == ':' || it == '：' }) return true
            }
            if (vc.rejectTimeKeywords.any { context.contains(it) }) return true
        }
        // 三段式取件码第三段必须符合配置的长度和模式
        val tsConfig = vc.threeSegment
        if (tsConfig != null && Regex(tsConfig.pattern).matches(value)) {
            val lastSeg = value.substringAfterLast("-")
            if (!Regex(tsConfig.lastSegmentPattern).matches(lastSeg)) return true
        }
        return false
    }

    private fun isLikelyPhoneTail(value: String, contextText: String): Boolean {
        val ptConfig = engine.rules.validation.expressCode.phoneTail
        if (value.length != ptConfig.length) return false
        if (ptConfig.digitsOnly && !value.all { it.isDigit() }) return false

        val escaped = Regex.escape(value)
        if (Regex("${ptConfig.maskPattern}$escaped").containsMatchIn(contextText)) return true

        val keywords = ptConfig.contextKeywords.joinToString("|")
        val phoneContextPattern = Regex(
            "($keywords)[^\\n]{0,12}(\\*{0,6})$escaped"
        )
        if (phoneContextPattern.containsMatchIn(contextText)) return true

        return false
    }

    // ─────────── 餐食/饮品取餐码识别 ───────────
    private fun extractFoodCode(
        blocks: List<PaddleOcrHelper.TextBlock>,
        mergedText: String,
        detectedBrand: String?,
        qrCode: String?
    ): Pair<String?, String?> {
        val foodConfig = engine.rules.codeExtraction.food

        // 餐饮排队叫号场景（如"小桌 A3"）优先提取短码，避免被通用取餐码规则漏掉。
        val queueKeywords = engine.getQueueKeywords()
        val queueHitCount = queueKeywords.count { mergedText.contains(it) }
        if (queueHitCount >= foodConfig.queueThreshold) {
            val queuePatterns = engine.getQueuePatterns()
            data class QueueCandidate(val code: String, val patternId: String, val distance: Int)

            fun pickQueueCodeWithDistance(text: String): List<QueueCandidate> {
                val normalized = text.replace(" ", "").replace("\n", "")
                val candidates = mutableListOf<QueueCandidate>()
                for (qp in queuePatterns) {
                    val compiled = engine.getCompiledPattern(qp.id) ?: continue
                    val match = compiled.find(normalized) ?: continue
                    val code = match.groupValues.getOrElse(2) { match.groupValues.getOrElse(1) { "" } }
                    if (code.length in qp.codeLengthMin..qp.codeLengthMax &&
                        (!qp.requireMixed || (code.any { it.isLetter() } && code.any { it.isDigit() }))) {
                        val matchStart = match.range.first
                        var minDist = normalized.length
                        for (kw in queueKeywords) {
                            val kwIdx = normalized.indexOf(kw)
                            if (kwIdx >= 0) {
                                val dist = if (kwIdx < matchStart) matchStart - kwIdx else kwIdx - matchStart
                                if (dist < minDist) minDist = dist
                            }
                        }
                        candidates.add(QueueCandidate(code, qp.id, minDist))
                    }
                }
                return candidates
            }

            // 先在 blocks 中找（独立文本块，匹配更精确），找不到再用 mergedText 兜底
            val blockCandidates = mutableListOf<QueueCandidate>()
            for (block in blocks) {
                blockCandidates.addAll(pickQueueCodeWithDistance(block.text))
            }
            if (blockCandidates.isNotEmpty()) {
                val best = blockCandidates.minByOrNull { it.distance }!!
                return best.code to best.patternId
            }
            val mergedCandidates = pickQueueCodeWithDistance(mergedText)
            if (mergedCandidates.isNotEmpty()) {
                val best = mergedCandidates.minByOrNull { it.distance }!!
                return best.code to best.patternId
            }
        }

        // 口令型取餐码全局优先（如 M707.你的脚步有力量），很多页面会把口令放在"取餐码"前面。
        val foodHintKeywords = engine.getFoodHintKeywords()
        val sloganCompiled = engine.getCompiledPattern("slogan_code")
        if (sloganCompiled != null && foodHintKeywords.any { mergedText.contains(it) }) {
            val fromBlocks = blocks.asSequence()
                .map { it.text.replace(" ", "").replace("\n", "") }
                .mapNotNull { txt -> sloganCompiled.find(txt)?.groupValues?.get(1) }
                .firstOrNull()
            if (fromBlocks != null && !isInvalidFoodCode(fromBlocks, mergedText, detectedBrand)) {
                return fromBlocks to "slogan_code"
            }
            val fromMerged = sloganCompiled.find(mergedText)?.groupValues?.get(1)
            if (fromMerged != null && !isInvalidFoodCode(fromMerged, mergedText, detectedBrand)) {
                return fromMerged to "slogan_code"
            }
        }

        // 星巴克啡快口令识别
        val starbucksPattern = foodConfig.starbucksPattern
        val starbucksCompiled = engine.getCompiledPattern("starbucks")
        if (starbucksCompiled != null && starbucksPattern != null) {
            val isStarbucksTrigger = detectedBrand == starbucksPattern.triggerBrand ||
                starbucksPattern.triggerKeywords.any { mergedText.contains(it) }
            if (isStarbucksTrigger) {
                for (block in blocks) {
                    val text = block.text.replace(" ", "").replace("\n", "")
                    val starbucksMatch = starbucksCompiled.find(text)
                    if (starbucksMatch != null) {
                        return starbucksMatch.value to "starbucks"
                    }
                }
            }
        }

        val foodKeywords = engine.getFoodHintKeywords()
        val hasFoodKeywords = engine.getFoodTriggerKeywords().any { mergedText.contains(it) }

        // # 开头的取餐码优先（如 #2017）
        val hashCodeCompiled = engine.getCompiledPattern("hash_code")
        if (hashCodeCompiled != null && hasFoodKeywords) {
            for (block in blocks) {
                val text = block.text.replace(" ", "").replace("\n", "")
                val match = hashCodeCompiled.find(text)
                if (match != null) {
                    val code = match.groupValues[1]
                    if (!isInvalidFoodCode(code, text, detectedBrand)) {
                        return code to "hash_code"
                    }
                }
            }
        }

        val keywordPatternConfig = foodConfig.patterns.keywordPattern
        val forwardCompiled = keywordPatternConfig?.let { engine.getCompiledPattern("${it.id}_forward") }
        val reverseCompiled = keywordPatternConfig?.let { engine.getCompiledPattern("${it.id}_reverse") }

        val targetKeywordRects = mutableListOf<android.graphics.Rect>()

        // 第一步：精确从关键词后截取
        for (block in blocks) {
            val text = applyCorrections(block.text).replace(" ", "").replace("\n", "")
            if (forwardCompiled != null) {
                val forwardMatch = forwardCompiled.find(text)
                if (forwardMatch != null) {
                    val code = forwardMatch.groupValues.getOrElse(1) { forwardMatch.value }
                    if (!isInvalidFoodCode(code, text, detectedBrand)) {
                        return code to "keyword_code_forward"
                    }
                }
            }
            if (reverseCompiled != null) {
                val reverseMatch = reverseCompiled.find(text)
                if (reverseMatch != null) {
                    val code = reverseMatch.groupValues.getOrElse(1) { reverseMatch.value }
                    if (!isInvalidFoodCode(code, text, detectedBrand)) {
                        return code to "keyword_code_reverse"
                    }
                }
            }
            val matchedKeyword = foodKeywords.firstOrNull { text.contains(it) } ?: continue
            block.boundingBox?.let(targetKeywordRects::add)
            val afterKeyword = text.substringAfter(matchedKeyword).trimStart(':', '：', ' ')
            // 口令型取餐码（如 M707.你的脚步有力量）优先提取完整文本
            if (sloganCompiled != null) {
                val sloganCodeMatch = sloganCompiled.find(afterKeyword)
                if (sloganCodeMatch != null) {
                    val sloganCode = sloganCodeMatch.groupValues[1]
                    if (!isInvalidFoodCode(sloganCode, afterKeyword, detectedBrand)) {
                        return sloganCode to "slogan_code"
                    }
                }
            }
            val genericPattern = Regex(foodConfig.genericCodePattern)
            val match = genericPattern.find(afterKeyword)
            if (match != null && !foodKeywords.any { it.contains(match.value) || match.value.contains(it) }) {
                if (!isInvalidFoodCode(match.value, afterKeyword, detectedBrand)) {
                    return match.value to "generic_code"
                }
            }
        }

        // 第二步：在关键词附近 block 中搜索
        if (targetKeywordRects.isNotEmpty()) {
            val standalonePattern = Regex(foodConfig.standaloneCodePattern)
            val candidates = blocks.mapNotNull { block ->
                val box = block.boundingBox ?: return@mapNotNull null
                val text = applyCorrections(block.text).replace(" ", "").replace("\n", "")
                if (standalonePattern.matches(text) && !isInvalidFoodCode(text, text, detectedBrand)) {
                    val blockCenterY = (box.top + box.bottom) / 2
                    val dist = targetKeywordRects.minOf { keywordRect ->
                        Math.abs(blockCenterY - (keywordRect.top + keywordRect.bottom) / 2)
                    }
                    if (dist < foodConfig.nearbyBlockDistance) text to dist else null
                } else null
            }.sortedBy { it.second }
            candidates.firstOrNull()?.first?.let { return it to "standalone_code" }
        }

        // 第三步：全文兜底按权重搜索
        if (!hasFoodKeywords) return null to null
        val fallbackPattern = engine.getCompiledPattern("food_fallback") ?: return null to null
        val foodScoring = engine.rules.scoring.foodCode
        val brandScoring = foodScoring.brandOverrides[detectedBrand]
        val candidates = blocks.flatMap { block ->
            val text = applyCorrections(block.text).replace(" ", "").replace("\n", "")
            fallbackPattern.findAll(text).mapNotNull { match ->
                val value = match.groupValues.getOrElse(1) { match.value }
                if (value.length == foodConfig.shortCodeLength && value.all { it.isDigit() }) {
                    val aroundStart = (match.range.first - foodConfig.keywordContextRange).coerceAtLeast(0)
                    val aroundEnd = (match.range.last + foodConfig.keywordContextRange).coerceAtMost(text.lastIndex)
                    val around = text.substring(aroundStart, aroundEnd + 1)
                    val blockCenterY = block.boundingBox?.let { (it.top + it.bottom) / 2 }
                    val nearKeywordBlock = blockCenterY != null && targetKeywordRects.any { keywordRect ->
                        Math.abs(blockCenterY - (keywordRect.top + keywordRect.bottom) / 2) <
                            foodConfig.nearbyBlockDistance
                    }
                    val nearKeyword = nearKeywordBlock || foodKeywords.any { around.contains(it) }
                    if (!nearKeyword) return@mapNotNull null
                }
                if (isInvalidFoodCode(value, text, detectedBrand)) return@mapNotNull null
                var weight = (block.boundingBox?.width() ?: 0) * value.length
                if (brandScoring != null) {
                    if (value.length >= 4 && value.any { it.isLetter() }) weight *= brandScoring.lengthGte4LetterMultiplier
                    if (value.length == 5 && value.all { it.isDigit() }) weight *= brandScoring.length5DigitMultiplier
                    if (value.length == 4 && value.all { it.isDigit() }) weight *= brandScoring.length4DigitMultiplier
                }
                value to weight
            }.toList()
        }.sortedByDescending { it.second }
        return candidates.firstOrNull()?.first to "food_fallback"
    }

    private fun isInvalidFoodCode(value: String, context: String, detectedBrand: String?): Boolean {
        val vc = engine.rules.validation.foodCode
        if (value.startsWith(vc.rejectYearPrefix) && value.length == vc.rejectYearLength) return true
        if (vc.rejectTimeContexts.any { context.contains(it) }) {
            if (value.all { it.isDigit() || it == ':' || it == '/' }) return true
        }
        if (vc.rejectTimeKeywords.any { context.contains(it) }) return true
        val lowerContext = context.lowercase()
        val lowerValue = value.lowercase()
        if (vc.distractionWords.any { word ->
                lowerContext.contains(word) && lowerContext.indexOf(word) in (lowerContext.indexOf(lowerValue) - vc.distractionRange)..(lowerContext.indexOf(lowerValue) + value.length + vc.distractionRange)
            }) return true
        return false
    }

    private fun findPickupLocation(mergedText: String, blocks: List<PaddleOcrHelper.TextBlock>): String? {
        // 优先匹配用户自定义地点关键词（SharedPreferences）
        val customLocationsStr = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            .getString("custom_pickup_locations", "") ?: ""
        if (customLocationsStr.isNotBlank()) {
            val customKeywords = customLocationsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
            for (keyword in customKeywords) {
                if (mergedText.contains(keyword)) {
                    return keyword
                }
            }
        }

        val plConfig = engine.rules.pickupLocation
        val startKeywords = plConfig.startKeywords
        val targetKeywords = plConfig.targetKeywords
        val stopKeywords = plConfig.stopKeywords

        fun isGarbageMatch(location: String): Boolean {
            for (pattern in plConfig.garbagePatterns) {
                if (location.contains(pattern)) return true
            }
            return false
        }

        val locScoring = plConfig.scoring
        fun locationScore(location: String): Int {
            var score = location.length
            for (keyword in targetKeywords) {
                if (location.contains(keyword)) score += locScoring.targetKeywordBonus
            }
            return score
        }

        val candidates = mutableListOf<Pair<String, Int>>()

        val locationLines = if (blocks.isNotEmpty()) {
            blocks.map { it.text }
        } else {
            mergedText.lines()
        }
        RecognitionTextStructure.trailingLocationCandidates(
            lines = locationLines,
            labels = plConfig.trailingLabels,
        ).forEach { rawCandidate ->
            val candidate = truncateLocation(rawCandidate, trimLeadingNoise = false)
            if (
                candidate.length > locScoring.addressFallbackMinLength &&
                !isGarbageMatch(candidate)
            ) {
                candidates.add(candidate to locationScore(candidate) + locScoring.addressBonus)
            }
        }

        val addressPattern = Regex("地址[:：\\s]*(.{4,80}?(?:${targetKeywords.joinToString("|")}))")
        val addressMatch = addressPattern.find(mergedText)
        if (addressMatch != null) {
            val loc = truncateLocation(addressMatch.groupValues[1])
            if (!isGarbageMatch(loc)) candidates.add(loc to locationScore(loc) + locScoring.addressBonus)
        }
        val addressFallback = Regex("地址[:：\\s]*([^,，。！!?；;.\\n]{4,60})")
        val fallbackMatch = addressFallback.find(mergedText)
        if (fallbackMatch != null) {
            val candidate = truncateLocation(fallbackMatch.groupValues[1])
            if (candidate.length > locScoring.addressFallbackMinLength && !isGarbageMatch(candidate)) {
                candidates.add(candidate to locationScore(candidate) + locScoring.addressFallbackBonus)
            }
        }

        val locWithTargetPattern = Regex("(?:${startKeywords.joinToString("|")})([^,，。！!?;？\\s]{2,60}?(?:${targetKeywords.joinToString("|")}))")
        for (match in locWithTargetPattern.findAll(mergedText)) {
            val loc = truncateLocation(match.groupValues[1])
            if (!isGarbageMatch(loc)) candidates.add(loc to locationScore(loc) + locScoring.locWithTargetBonus)
        }

        val locToVerbPattern = Regex(
            "(?:${startKeywords.joinToString("|")})([^,，。！!?;？\\s]{2,60}?)(?=[,，。！!?;？\\s]*(?:${stopKeywords.joinToString("|")}))"
        )
        for (match in locToVerbPattern.findAll(mergedText)) {
            val loc = truncateLocation(match.groupValues[1])
            if (!isGarbageMatch(loc)) candidates.add(loc to locationScore(loc))
        }

        // OCR 文本框已经按自然阅读顺序排列，这里连接后续两行以覆盖跨行地点。
        if (blocks.size >= 2) {
            val readingOrderBlocks = blocks.map { block ->
                block.text.replace("\n", "").replace(" ", "")
            }
            val codePatterns = engine.getExpressPatterns().filter {
                it.priority <= engine.rules.recognitionParams.patternPrioritySkip
            }
            val maxCodeLocationGap = engine.rules.validation.expressCode.maxCodeKeywordGap

            for (index in readingOrderBlocks.indices) {
                val currentText = readingOrderBlocks[index]
                if (currentText.isBlank()) continue

                val codeMatches = codePatterns.flatMap { pattern ->
                    val compiled = engine.getCompiledPattern(pattern.id) ?: return@flatMap emptyList()
                    compiled.findAll(currentText).mapNotNull { match ->
                        val group = if (match.groups.size > 1) match.groups[1] else null
                        val code = group?.value ?: match.value
                        val range = group?.range ?: match.range
                        if (isInvalidExpressCode(code) || isLikelyPhoneTail(code, currentText)) {
                            null
                        } else {
                            Triple(pattern.priority, code, range)
                        }
                    }.toList()
                }.sortedWith(compareBy({ it.third.first }, { it.first }))

                for ((_, _, codeRange) in codeMatches) {
                    val reconstructedText = buildString {
                        append(currentText.substring(codeRange.last + 1))
                        for (offset in 1..2) {
                            readingOrderBlocks.getOrNull(index + offset)?.let(::append)
                        }
                    }
                    val locationMatch = locToVerbPattern.find(reconstructedText) ?: continue
                    if (locationMatch.range.first > maxCodeLocationGap) continue

                    val loc = truncateLocation(
                        locationMatch.groupValues[1],
                        trimLeadingNoise = false,
                    )
                    if (!isGarbageMatch(loc)) {
                        candidates.add(
                            loc to locationScore(loc) + locScoring.addressBonus
                        )
                    }
                    break
                }
            }
        }

        val storeKeywords = plConfig.storeNameKeywords
        val storeBonus = plConfig.storeNameBonus
        val storeMaxLen = plConfig.storeNameMaxLen
        for (block in blocks) {
            val text = block.text.replace("\n", "").replace(" ", "")
            if (targetKeywords.any { text.contains(it) }) {
                // Standalone OCR blocks preserve venue qualifiers that merged-text prefix
                // trimming can otherwise discard.
                val loc = truncateLocation(text, trimLeadingNoise = false)
                if (!isGarbageMatch(loc)) {
                    val standaloneStoreBonus = if (
                        loc.length in 3..storeMaxLen && storeKeywords.any { loc.endsWith(it) }
                    ) {
                        storeBonus + locScoring.locWithTargetBonus
                    } else {
                        0
                    }
                    candidates.add(loc to locationScore(loc) + standaloneStoreBonus)
                }
            }
        }

        // 优先提取长度受限的店名（超市/便利店等），避免返回整段长地址。
        val escapedStoreKeywords = storeKeywords
            .filter { it.isNotBlank() }
            .sortedByDescending { it.length }
            .map { Regex.escape(it) }
        if (escapedStoreKeywords.isNotEmpty()) {
            val shortestKeywordLength = storeKeywords
                .filter { it.isNotBlank() }
                .minOf { it.length }
            val maxPrefixLength = (storeMaxLen - shortestKeywordLength).coerceAtLeast(1)
            val storePattern = Regex(
                "[\\u4e00-\\u9fa5]{1,$maxPrefixLength}(?:${escapedStoreKeywords.joinToString("|")})"
            )
            for (match in storePattern.findAll(mergedText)) {
                val storeName = match.value
                if (storeName.length <= storeMaxLen && !isGarbageMatch(storeName)) {
                    candidates.add(storeName to locationScore(storeName) + storeBonus)
                }
            }
        }

        return candidates.maxByOrNull { it.second }?.first
    }

    private fun cleanChineseText(text: String): String {
        val cleanConfig = engine.rules.textCleaning
        var result = text
            .replace(Regex(cleanConfig.datetimePattern), "")
            .replace(Regex(cleanConfig.spaceCollapse), "")
            .replace("\n", "")
        for (removal in cleanConfig.charRemovals) {
            result = result.replace(removal, "")
        }
        for ((from, to) in engine.getTextCorrections()) {
            result = result.replace(from, to)
        }
        return result
    }

    private fun cleanCodeMatchingText(text: String): String {
        val cleanConfig = engine.rules.textCleaning
        return RecognitionTextStructure.normalizeForCodeMatching(
            text = text,
            datetimePattern = cleanConfig.datetimePattern,
            spaceCollapsePattern = cleanConfig.spaceCollapse,
            charRemovals = cleanConfig.charRemovals,
            corrections = engine.getTextCorrections(),
        )
    }

    private fun applyCorrections(text: String): String {
        var result = text
        for ((from, to) in engine.getTextCorrections()) {
            result = result.replace(from, to)
        }
        return result
    }

    private fun truncateLocation(
        location: String,
        trimLeadingNoise: Boolean = true,
    ): String {
        val stopKeywords = engine.rules.pickupLocation.stopKeywords
        val trailingPunctuation = Regex(engine.rules.textCleaning.trailingPunctuation)
        var result = location
        // 分号通常是地址段的分隔符，直接截断
        val semicolonIdx = result.indexOf(';')
        if (semicolonIdx != -1) result = result.substring(0, semicolonIdx)
        for (stop in stopKeywords) {
            val index = result.indexOf(stop)
            if (index != -1) result = result.substring(0, index)
        }
        // 从第一个地名前缀标记开始截取（去掉前面的杂字）
        val prefixMarkers = engine.rules.pickupLocation.locationPrefixMarkers
        if (trimLeadingNoise && prefixMarkers.isNotEmpty()) {
            val firstMarkerIdx = result.indexOfAny(prefixMarkers)
            if (firstMarkerIdx > 0) {
                result = result.substring(firstMarkerIdx)
            }
        }
        return result.replace(trailingPunctuation, "")
    }

    // ─────────── 纯文字识别（用于划选文字处理） ───────────
    fun recognizeFromText(text: String, source: SimpleRuleSource = SimpleRuleSource.TEXT): List<RecognitionResult> {
        val mergedText = cleanChineseText(text)
        val simplePack = SimpleRuleRuntime.current()
        if (simplePack.schemaVersion == com.Badnng.moe.rules.SimpleRulePack.SCHEMA_VERSION) {
            return SimpleRuleRuntime.recognizeCurrent(text, source).map { match ->
                RecognitionResult(
                    code = match.code,
                    qr = null,
                    type = match.category.resultType,
                    brand = match.brand,
                    fullText = text,
                    pickupLocation = match.location,
                )
            }
        }
        val codeMatchingText = cleanCodeMatchingText(text)
        Log.d("ExpressExtract", "recognizeFromText: engine patterns=${engine.getExpressPatterns().size}, sourceId=${engine.currentSourceId}")

        // 第一步：品牌识别
        var detectedBrand: String? = null
        val brandHits = mutableMapOf<String, Int>()
        val scoringConfig = engine.rules.scoring.brandDetection
        for (brand in engine.getDrinkBrands() + engine.getFoodBrands()) {
            if (brand.keywords.isNotEmpty() && brand.keywords.any { mergedText.contains(it) }) {
                val score = brand.scoringOverrides["exact_match_weight"] ?: scoringConfig.exactMatchWeight
                brandHits[brand.name] = (brandHits[brand.name] ?: 0) + score
            }
            if (mergedText.contains(brand.name, ignoreCase = true)) {
                val colonScore = if (Regex("${Regex.escape(brand.name)}[:：]\\d").containsMatchIn(mergedText)) scoringConfig.colonMatchWeight else scoringConfig.keywordMatchWeight
                brandHits[brand.name] = (brandHits[brand.name] ?: 0) + colonScore
            }
            for (alias in brand.aliases) {
                if (mergedText.contains(alias, ignoreCase = true)) {
                    val colonScore = if (Regex("${Regex.escape(alias)}[:：]\\d").containsMatchIn(mergedText)) scoringConfig.colonMatchWeight else scoringConfig.keywordMatchWeight
                    brandHits[brand.name] = (brandHits[brand.name] ?: 0) + colonScore
                }
            }
        }
        detectedBrand = brandHits.maxByOrNull { it.value }?.key
        Log.d("ExpressExtract", "[text] 第一步品牌: detectedBrand=$detectedBrand")

        // 检测到啡快口令，品牌直接设为星巴克
        val starbucksPattern = engine.rules.codeExtraction.food.starbucksPattern
        if (starbucksPattern != null && starbucksPattern.triggerKeywords.any { mergedText.contains(it) }) {
            detectedBrand = starbucksPattern.triggerBrand
        }

        // 第二步：类型判断
        val catConfig = engine.rules.categoryDetection
        var category = catConfig.defaultCategory
        if ((catConfig.drinkTriggers.brandBased && drinkBrands.contains(detectedBrand)) ||
            catConfig.drinkTriggers.textKeywords.any { mergedText.contains(it) }
        ) {
            category = "饮品"
        } else if (
            catConfig.expressTriggers.textKeywords.any { mergedText.contains(it) } ||
            (catConfig.expressTriggers.brandInText && expressBrandKeywords.any { mergedText.contains(it) })
        ) {
            category = "快递"
        }
        Log.d("ExpressExtract", "[text] 第二步分类: category=$category")

        // 第三步：提取取件码
        val pickupLocation = findPickupLocation(mergedText, emptyList())
        val results = if (category == "快递") {
            val codes = extractExpressCodeFromText(mergedText, codeMatchingText)
            Log.d("ExpressExtract", "[text] 第三步提取: codes=$codes")
            codes.map { code ->
                val brand = findBrandForCode(code, mergedText)
                RecognitionResult(code, null, category, brand ?: detectedBrand, text, pickupLocation)
            }
        } else {
            val result = extractFoodCodeFromText(mergedText, detectedBrand)
            Log.d("ExpressExtract", "[text] 第三步提取: code=${result.first}, matchId=${result.second}")
            if (result.first != null) {
                listOf(RecognitionResult(result.first, null, category, detectedBrand, text, pickupLocation))
            } else {
                emptyList()
            }
        }

        for (r in results) {
            Log.d("ProcessTextRecognition", "------------------------------------")
            Log.d("ProcessTextRecognition", "Input Text: $mergedText")
            Log.d("ProcessTextRecognition", "Extracted Code: ${r.code}")
            Log.d("ProcessTextRecognition", "Category: ${r.type}")
            Log.d("ProcessTextRecognition", "Brand: ${r.brand}")
            Log.d("ProcessTextRecognition", "Pickup Location: ${r.pickupLocation}")
            Log.d("ProcessTextRecognition", "------------------------------------")
        }

        return results
    }

    private fun extractExpressCodeFromText(
        mergedText: String,
        codeMatchingText: String = mergedText,
    ): List<String> {
        val expressKeywords = engine.getExpressTriggerKeywords()
        val hasExpressKeyword = expressKeywords.any { mergedText.contains(it) }
        val hasExpressBrand = expressBrandKeywords.any { mergedText.contains(it) }
        val contextualMatches = findExpressCodesNearKeywords(mergedText)
        val configuredThreeSegmentMatches = findConfiguredThreeSegmentCodes(codeMatchingText).map { (code, range) ->
            val mergedIndex = mergedText.indexOf(code)
            code to if (mergedIndex >= 0) {
                mergedIndex until (mergedIndex + code.length)
            } else {
                range
            }
        }
        val highConfidenceMatches = (contextualMatches + configuredThreeSegmentMatches)
            .distinctBy { it.first }
            .sortedBy { it.second.first }
        if (highConfidenceMatches.isNotEmpty()) {
            val codes = highConfidenceMatches.map { it.first }
            Log.d("ExpressExtract", "[text] 关键词/三段码命中: $codes")
            return codes
        }

        // 第二步：兜底——按权重筛选
        if (hasExpressKeyword || !hasExpressBrand) return emptyList()
        val fallbackPattern = engine.getCompiledPattern("express_fallback") ?: return emptyList()
        val scoringConfig = engine.rules.scoring.expressCode
        val candidates = fallbackPattern.findAll(mergedText).mapNotNull { match ->
            val value = match.value
            if (isInvalidExpressCode(value)) return@mapNotNull null
            if (isLikelyPhoneTail(value, mergedText)) return@mapNotNull null
            var weight = value.length
            if (value.contains("-")) weight *= scoringConfig.dashMultiplier
            if (value.any { it.isLetter() }) weight *= scoringConfig.letterMultiplier
            value to weight
        }.sortedByDescending { it.second }.toList()
        Log.d("ExpressExtract", "[text] 第二步兜底: ${candidates.map { it.first }}")
        return listOfNotNull(candidates.firstOrNull()?.first)
    }

    private fun findExpressCodesNearKeywords(mergedText: String): List<Pair<String, IntRange>> {
        val keywords = engine.getExpressTriggerKeywords()
        val patterns = engine.getExpressPatterns()
        val validation = engine.rules.validation.expressCode
        val found = mutableListOf<Pair<String, IntRange>>()

        fun addIfValid(code: String, range: IntRange, patternId: String) {
            if (code.isBlank() || isInvalidExpressCode(code) || isLikelyPhoneTail(code, mergedText)) return
            if (found.any { (existing, _) -> existing == code || code in existing }) return
            found += code to range
            Log.d("ExpressExtract", "[context] 命中: code=$code, pattern=$patternId, range=$range")
        }

        for (keyword in keywords) {
            var searchIndex = 0
            while (true) {
                val keywordIndex = mergedText.indexOf(keyword, searchIndex)
                if (keywordIndex < 0) break
                val keywordEnd = keywordIndex + keyword.length
                var cursor = keywordEnd
                while (cursor < mergedText.length &&
                    (mergedText[cursor].isWhitespace() || mergedText[cursor] == ':' || mergedText[cursor] == '：')
                ) {
                    cursor++
                }

                var firstCode = true
                while (cursor < mergedText.length) {
                    val remaining = mergedText.substring(cursor)
                    val allowedGap = if (firstCode) validation.maxCodeKeywordGap else 0
                    val candidate = patterns.mapNotNull { pattern ->
                        val match = engine.getCompiledPattern(pattern.id)?.find(remaining)
                            ?: return@mapNotNull null
                        val group = if (match.groups.size > 1) match.groups[1] else null
                        val code = group?.value ?: match.value
                        val range = group?.range ?: match.range
                        if (code.isBlank() || range.first > allowedGap) return@mapNotNull null
                        Triple(pattern, code, range)
                    }.minWithOrNull(
                        compareBy<Triple<com.Badnng.moe.rules.ExtractionPattern, String, IntRange>>(
                            { it.third.first },
                            { it.first.priority },
                        )
                    ) ?: break

                    val codeRange = (cursor + candidate.third.first)..(cursor + candidate.third.last)
                    addIfValid(candidate.second, codeRange, candidate.first.id)
                    cursor = codeRange.last + 1
                    firstCode = false

                    val beforeSeparators = cursor
                    while (cursor < mergedText.length &&
                        (mergedText[cursor].isWhitespace() ||
                            mergedText[cursor].toString() in validation.codeSeparators)
                    ) {
                        cursor++
                    }
                    if (cursor == beforeSeparators) break
                }

                // “12345取件码”只对语义上表示码名称的关键词做反向识别。
                if (keyword.endsWith("码")) {
                    val reverseStart = (keywordIndex - validation.maxReverseDistance).coerceAtLeast(0)
                    val beforeText = mergedText.substring(reverseStart, keywordIndex)
                        .trimEnd(':', '：', ' ')
                    val reverseCandidate = patterns
                        .filter { it.priority <= engine.rules.recognitionParams.patternPrioritySkip }
                        .mapNotNull { pattern ->
                            val match = engine.getCompiledPattern(pattern.id)
                                ?.findAll(beforeText)
                                ?.lastOrNull()
                                ?: return@mapNotNull null
                            val group = if (match.groups.size > 1) match.groups[1] else null
                            val code = group?.value ?: match.value
                            val range = group?.range ?: match.range
                            val gap = beforeText.length - range.last - 1
                            if (code.isBlank() || gap > validation.maxCodeKeywordGap) {
                                return@mapNotNull null
                            }
                            Triple(pattern, code, range)
                        }
                        .minWithOrNull(
                            compareBy<Triple<com.Badnng.moe.rules.ExtractionPattern, String, IntRange>>(
                                { beforeText.length - it.third.last - 1 },
                                { it.first.priority },
                            )
                        )
                    if (reverseCandidate != null) {
                        val range = (reverseStart + reverseCandidate.third.first)..
                            (reverseStart + reverseCandidate.third.last)
                        addIfValid(reverseCandidate.second, range, reverseCandidate.first.id)
                    }
                }

                searchIndex = keywordEnd
            }
        }

        return found.sortedBy { it.second.first }
    }

    /**
     * 使用规则文件中的三段码校验配置收集全文匹配项。
     * 三段码本身结构明确，不依赖每个卡片都能被 OCR 识别出“取件码”提示词。
     */
    private fun findConfiguredThreeSegmentCodes(
        mergedText: String,
    ): List<Pair<String, IntRange>> {
        val config = engine.rules.validation.expressCode.threeSegment ?: return emptyList()
        val pattern = runCatching { Regex(config.pattern) }.getOrNull() ?: return emptyList()
        val found = mutableListOf<Pair<String, IntRange>>()

        for (match in pattern.findAll(mergedText)) {
            val group = if (match.groups.size > 1) match.groups[1] else null
            val code = group?.value ?: match.value
            val range = group?.range ?: match.range
            val contextStart = maxOf(0, range.first - 20)
            val contextEnd = minOf(mergedText.length, range.last + 21)
            val nearbyContext = mergedText.substring(contextStart, contextEnd)
            if (isInvalidExpressCode(code, nearbyContext)) continue
            if (isLikelyPhoneTail(code, mergedText)) continue
            if (found.any { (existing, _) -> existing == code }) continue

            found += code to range
            Log.d("ExpressExtract", "[three-segment] 命中: code=$code, range=$range")
        }

        return found
    }

    private fun extractFoodCodeFromText(mergedText: String, detectedBrand: String?): Pair<String?, String?> {
        val foodKeywords = engine.getFoodHintKeywords()
        val foodTriggerKeywords = engine.getFoodTriggerKeywords()
        val hasFoodKeywords = foodTriggerKeywords.any { mergedText.contains(it) }

        // # 开头的取餐码优先（如 #2017）
        val hashCodeCompiled = engine.getCompiledPattern("hash_code")
        if (hashCodeCompiled != null && hasFoodKeywords) {
            val match = hashCodeCompiled.find(mergedText)
            if (match != null) {
                val code = match.groupValues[1]
                if (!isInvalidFoodCode(code, mergedText, detectedBrand)) {
                    return code to "hash_code"
                }
            }
        }

        // 口令型取餐码全局优先（如 M707.你的脚步有力量）
        if (hasFoodKeywords) {
            val sloganPattern = engine.getCompiledPattern("slogan_code")
            if (sloganPattern != null) {
                val sloganCode = sloganPattern.find(mergedText)?.groupValues?.get(1)
                if (sloganCode != null && !isInvalidFoodCode(sloganCode, mergedText, detectedBrand)) {
                    return sloganCode to "slogan_code"
                }
            }
        }

        // 使用引擎中的关键词正则
        val keywordPatternConfig = engine.rules.codeExtraction.food.patterns.keywordPattern
        if (keywordPatternConfig != null) {
            val forwardRegex = engine.getCompiledPattern("${keywordPatternConfig.id}_forward")
            if (forwardRegex != null) {
                val forwardMatch = forwardRegex.find(mergedText)
                if (forwardMatch != null) {
                    val code = forwardMatch.groupValues[1]
                    if (!isInvalidFoodCode(code, mergedText, detectedBrand)) return code to "keyword_code_forward"
                }
            }
            val reverseRegex = engine.getCompiledPattern("${keywordPatternConfig.id}_reverse")
            if (reverseRegex != null) {
                val reverseMatch = reverseRegex.find(mergedText)
                if (reverseMatch != null) {
                    val code = reverseMatch.groupValues[1]
                    if (!isInvalidFoodCode(code, mergedText, detectedBrand)) return code to "keyword_code_reverse"
                }
            }
        }

        // 第一步：精确从关键词后截取
        val matchedKeyword = foodKeywords.firstOrNull { mergedText.contains(it) }
        if (matchedKeyword != null) {
            val afterKeyword = mergedText.substringAfter(matchedKeyword).trimStart(':', '：', ' ')
            // 口令型取餐码（如 M707.你的脚步有力量）优先提取完整文本
            val sloganPattern = engine.getCompiledPattern("slogan_code")
            if (sloganPattern != null) {
                val sloganCodeMatch = sloganPattern.find(afterKeyword)
                if (sloganCodeMatch != null) {
                    val sloganCode = sloganCodeMatch.groupValues[1]
                    if (!isInvalidFoodCode(sloganCode, afterKeyword, detectedBrand)) {
                        return sloganCode to "slogan_code"
                    }
                }
            }
            val match = Regex(engine.rules.codeExtraction.food.genericCodePattern).find(afterKeyword)
            if (match != null && !foodKeywords.any { it.contains(match.value) || match.value.contains(it) }) {
                if (!isInvalidFoodCode(match.value, afterKeyword, detectedBrand)) {
                    return match.value to "generic_code"
                }
            }
        }

        // 第二步：全文兜底按权重搜索
        if (!hasFoodKeywords) return null to null
        val fallbackPattern = engine.getCompiledPattern("food_fallback") ?: return null to null
        val foodScoring = engine.rules.scoring.foodCode
        val brandScoring = foodScoring.brandOverrides[detectedBrand]
        val foodConfig = engine.rules.codeExtraction.food
        val candidates = fallbackPattern.findAll(mergedText).mapNotNull { match ->
            val value = match.value
            if (value.length == foodConfig.shortCodeLength && value.all { it.isDigit() }) {
                val aroundStart = (match.range.first - foodConfig.keywordContextRange).coerceAtLeast(0)
                val aroundEnd = (match.range.last + foodConfig.keywordContextRange).coerceAtMost(mergedText.lastIndex)
                val around = mergedText.substring(aroundStart, aroundEnd + 1)
                val nearKeyword = foodKeywords.any { around.contains(it) }
                if (!nearKeyword) return@mapNotNull null
            }
            if (isInvalidFoodCode(value, mergedText, detectedBrand)) return@mapNotNull null
            var weight = value.length
            if (brandScoring != null) {
                if (value.length >= 4 && value.any { it.isLetter() }) weight *= brandScoring.lengthGte4LetterMultiplier
                if (value.length == 5 && value.all { it.isDigit() }) weight *= brandScoring.length5DigitMultiplier
                if (value.length == 4 && value.all { it.isDigit() }) weight *= brandScoring.length4DigitMultiplier
            }
            value to weight
        }.sortedByDescending { it.second }
        return candidates.firstOrNull()?.first to "food_fallback"
    }

    /**
     * 多取件码识别 - 用于识别一张图片中的多个快递取件码
     */
    suspend fun recognizeMultipleCodes(bitmap: Bitmap, sourceApp: String? = null, sourcePkg: String? = null): MultiRecognitionResult {
        Log.d("RecognitionMonitor", "=== recognizeMultipleCodes 开始 ===")

        // PaddleOCR 会按需初始化，并在空闲后自动释放。
        val ocrResult = paddleOcr.recognizeAsync(bitmap)
        val rawFullText = ocrResult?.fullText ?: ""
        val textBlocks = ocrResult?.textBlocks ?: emptyList()

        // 保留 ML Kit 条码扫描
        val barcodeResult = barcodeScanner?.let { scanner ->
            val image = InputImage.fromBitmap(bitmap, 0)
            try {
                withContext(Dispatchers.Main) {
                    scanner.process(image).await()
                }
            } catch (e: Exception) { null }
        }

        val mergedText = cleanChineseText(rawFullText)
        val simplePack = SimpleRuleRuntime.current()
        if (simplePack.schemaVersion == com.Badnng.moe.rules.SimpleRulePack.SCHEMA_VERSION) {
            val qr = barcodeResult?.firstOrNull()?.rawValue?.takeIf {
                !it.startsWith("http://", ignoreCase = true) && !it.startsWith("https://", ignoreCase = true)
            }
            val matches = SimpleRuleRuntime.recognizeCurrent(rawFullText, SimpleRuleSource.IMAGE, sourcePkg, qr)
            val orders = matches.map {
                RecognitionResult(it.code, qr, it.category.resultType, it.brand, rawFullText, it.location)
            }
            return MultiRecognitionResult(orders, orders.size > 1)
        }

        return recognizeMultipleCodesFromResult(rawFullText, textBlocks, mergedText, sourceApp, sourcePkg)
    }

    /**
     * 多取件码识别 - 复用已有 OCR 结果
     */
    fun recognizeMultipleCodesFromResult(
        rawFullText: String,
        textBlocks: List<PaddleOcrHelper.TextBlock>,
        mergedText: String,
        sourceApp: String? = null,
        sourcePkg: String? = null,
        qrData: String? = null,
        simpleRuleMatches: List<SimpleRuleMatch>? = null,
    ): MultiRecognitionResult {
        Log.d("RecognitionMonitor", "=== recognizeMultipleCodesFromResult 开始 ===")
        Log.d("RecognitionMonitor", "多取件码识别 - 全文: ${mergedText.take(500)}")

        val simplePack = SimpleRuleRuntime.current()
        if (simplePack.schemaVersion == com.Badnng.moe.rules.SimpleRulePack.SCHEMA_VERSION) {
            val matches = simpleRuleMatches ?: SimpleRuleRuntime.recognizeCurrent(
                rawText = rawFullText,
                source = SimpleRuleSource.IMAGE,
                sourcePackage = sourcePkg,
                qrData = qrData,
            )
            val orders = matches.map { match ->
                RecognitionResult(
                    code = match.code,
                    qr = qrData,
                    type = match.category.resultType,
                    brand = match.brand,
                    fullText = rawFullText,
                    pickupLocation = match.location,
                )
            }.distinctBy { it.code }
            Log.d("RecognitionMonitor", "简化规则多取件码识别完成，共识别到 ${orders.size} 个取件码")
            return MultiRecognitionResult(orders, orders.size > 1)
        }

        // 查找所有取件码和对应的快递品牌
        val orders = mutableListOf<RecognitionResult>()

        fun addOrderIfValid(code: String, range: IntRange) {
            if (orders.any { it.code == code }) return
            val contextStart = maxOf(0, range.first - 20)
            val contextEnd = minOf(mergedText.length, range.last + 21)
            val nearbyContext = mergedText.substring(contextStart, contextEnd)
            val invalid = isInvalidExpressCode(code, nearbyContext)
            val phoneTail = isLikelyPhoneTail(code, mergedText)
            Log.d("RecognitionMonitor", "[addOrder] code=$code, invalid=$invalid, phoneTail=$phoneTail")
            if (!invalid && !phoneTail) {
                val brand = findBrandForCode(code, mergedText, range)
                val pickupLocation = findPickupLocation(mergedText, textBlocks)
                val order = RecognitionResult(
                    code = code, qr = null, type = "快递",
                    brand = brand, fullText = rawFullText, pickupLocation = pickupLocation
                )
                orders.add(order)
                Log.d("RecognitionMonitor", "识别到取件码: $code, 品牌: $brand")
            }
        }

        // 方法1：基于触发关键词上下文查找，避免把地址中的门牌号当成取件码
        val enginePatterns = engine.getExpressPatterns()
        Log.d("RecognitionMonitor", "express patterns: ${enginePatterns.map { it.id }}")
        for ((code, range) in findExpressCodesNearKeywords(mergedText)) {
            addOrderIfValid(code, range)
        }
        val lineAwareText = cleanCodeMatchingText(rawFullText)
        val threeSegmentMatches = (
            findConfiguredThreeSegmentCodes(mergedText) +
                findConfiguredThreeSegmentCodes(lineAwareText)
            ).distinctBy { it.first }
        for ((code, originalRange) in threeSegmentMatches) {
            val mergedIndex = mergedText.indexOf(code)
            val range = if (mergedIndex >= 0) {
                mergedIndex until (mergedIndex + code.length)
            } else {
                originalRange
            }
            addOrderIfValid(code, range)
        }
        Log.d("RecognitionMonitor", "关键词及三段码扫描完成，找到 ${orders.size} 个取件码")

        // 方法2：如果方法1找到的取件码不足，尝试基于快递品牌名称查找
        val hasExpressTrigger = engine.getExpressTriggerKeywords().any { mergedText.contains(it) }
        if (orders.isEmpty() && !hasExpressTrigger) {
            val expressBrands = engine.getExpressBrands()
            for (brandDef in expressBrands) {
                val brandNames = listOf(brandDef.name) + brandDef.aliases
                val matchedName = brandNames.firstOrNull { mergedText.contains(it) } ?: continue
                val brandIndex = mergedText.indexOf(matchedName)
                val brandWindow = engine.rules.recognitionParams.brandContextWindow
                val nearbyText = mergedText.substring(
                    maxOf(0, brandIndex - brandWindow / 2),
                    minOf(mergedText.length, brandIndex + matchedName.length + brandWindow)
                )
                for (pattern in enginePatterns) {
                    val compiled = engine.getCompiledPattern(pattern.id) ?: continue
                    for (codeMatch in compiled.findAll(nearbyText)) {
                        val code = codeMatch.groupValues.getOrElse(1) { codeMatch.value }
                        if (!isInvalidExpressCode(code, nearbyText) && !isLikelyPhoneTail(code, mergedText)) {
                            val pickupLocation = findPickupLocation(mergedText, textBlocks)
                            orders.add(RecognitionResult(
                                code = code, qr = null, type = "快递",
                                brand = brandDef.name, fullText = rawFullText, pickupLocation = pickupLocation
                            ))
                            Log.d("RecognitionMonitor", "基于品牌找到取件码: $code, 品牌: ${brandDef.name}")
                        }
                    }
                }
            }
        }

        // 方法3：无触发关键词时，基于快递品牌上下文提取兜底码
        if (expressBrandKeywords.any { mergedText.contains(it) }) {
            val pickupLocation = findPickupLocation(mergedText, textBlocks)
            val fallbackPattern = engine.getCompiledPattern("express_fallback")
            if (fallbackPattern != null) {
                for (m in fallbackPattern.findAll(mergedText)) {
                    val code = m.groupValues.getOrElse(1) { m.value }
                    val ctxStart = maxOf(0, m.range.first - 20)
                    val ctxEnd = minOf(mergedText.length, m.range.last + 20)
                    if (isInvalidExpressCode(code, mergedText.substring(ctxStart, ctxEnd))) continue
                    if (isLikelyPhoneTail(code, mergedText)) continue
                    val brand = findBrandForCode(code, mergedText, m.range)
                    orders.add(RecognitionResult(
                        code = code, qr = null, type = "快递",
                        brand = brand, fullText = rawFullText, pickupLocation = pickupLocation
                    ))
                }
            }
        }

        // 去重
        val uniqueOrders = orders.distinctBy { it.code }

        Log.d("RecognitionMonitor", "多取件码识别完成，共识别到 ${uniqueOrders.size} 个取件码")

        return MultiRecognitionResult(
            orders = uniqueOrders,
            hasMultipleCodes = uniqueOrders.size > 1
        )
    }
    
    /**
     * 根据取件码位置查找对应的快递品牌
     */
    private fun findBrandForCode(code: String, text: String, codeRange: IntRange = 0..text.length): String? {
        val expressBrands = engine.getExpressBrands()
        val codeWindow = engine.rules.recognitionParams.codeContextWindow
        val startIndex = maxOf(0, codeRange.first - codeWindow)
        val endIndex = minOf(text.length, codeRange.last + codeWindow)
        val nearbyText = text.substring(startIndex, endIndex)

        // 优先匹配品牌名
        for (brand in expressBrands) {
            if (nearbyText.contains(brand.name)) return brand.name
        }
        // 再匹配别名
        for (brand in expressBrands) {
            if (brand.aliases.any { nearbyText.contains(it) }) return brand.name
        }
        return "快递"
    }
    
    fun close() {
        barcodeScanner?.close()
        // PaddleOCR 为进程级单例，由 PaddleOcrHelper 统一执行并发安全的空闲释放。
    }
}

data class RecognitionResult(val code: String?, val qr: String?, val type: String, val brand: String?, val fullText: String, val pickupLocation: String? = null)

/**
 * 多取件码识别结果
 */
data class MultiRecognitionResult(
    val orders: List<RecognitionResult>,
    val hasMultipleCodes: Boolean
)
