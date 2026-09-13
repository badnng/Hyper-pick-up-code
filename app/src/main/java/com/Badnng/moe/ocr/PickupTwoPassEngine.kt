package com.Badnng.moe.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.Badnng.moe.rules.PickupWordRulePack
import com.Badnng.moe.rules.PickupWordRuleRepository
import com.Badnng.moe.rules.WordType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 两步识别引擎（移植自 demo）：
 *
 * 1. Pass1：PaddleOcrHelper 全图识别（置信度沿用原项目 OCR_MIN_CONFIDENCE=0.90）；
 * 2. 类型判定：用规则包的 餐食定位词 / 快递定位词 分别匹配，谁命中的块多页面就是什么类型；
 * 3. 定位：以对应类型词汇为锚点（辅助锚点词只扩大框选范围，不是定位词汇），
 *    多关键词按位置聚类，每组独立框选 + Pass2 二次识别；
 *    相邻簇的框互不吞并（一卡一框）——快递取件码列表一屏多卡多码时，
 *    前一张卡片的裁剪区不得向下吃到下一张卡片的取件码；
 * 4. 提取：从裁剪区识别结果提取取餐码（置信度排序，去重）。
 */
class PickupTwoPassEngine(private val context: Context) {

    data class Blk(
        val text: String,
        val confidence: Float,
        val rect: Rect,
    ) {
        val centerX: Float get() = (rect.left + rect.right) / 2f
        val centerY: Float get() = (rect.top + rect.bottom) / 2f
        val height: Float get() = rect.height().toFloat().coerceAtLeast(1f)
    }

    data class BrandHit(
        val name: String,
        val keyword: String,
        val confidence: Float,
        val rect: Rect?,
        val area: String,
    )

    data class KeywordHit(
        val keyword: String,
        val block: Blk,
    )

    data class CodeResult(
        val code: String,
        val confidence: Float,
        val fromCrop: Boolean,
        val matchedRect: Rect?,
        val slogan: String? = null,
        /** 候选评分（页面级裁决用，越高越可信） */
        val score: Int = 0,
        /** 所属定位词聚类下标（同一簇视为同一订单区域） */
        val clusterIndex: Int = -1,
        /** 锚点行高，用于判定候选之间的垂直独立性 */
        val anchorHeight: Int = 0,
    )

    data class CropInfo(
        val rect: Rect,
        val bitmap: Bitmap?,
        /** 裁剪区内的候选行（Pass2 结果 + Pass2 漏识时补回的 Pass1 块），坐标已统一为全图坐标 */
        val lines: List<Blk>,
        val pass2TotalMs: Long,
    )

    data class EngineResult(
        val pass1Lines: List<Blk>,
        val pass1TotalMs: Long,
        val fullText: String,
        val brand: BrandHit?,
        val keywordHits: List<KeywordHit>,
        val crops: List<CropInfo>,
        val codes: List<CodeResult>,
        /**
         * 页面级裁决后应建单的候选：默认只留主候选，仅当次候选与已选候选空间独立
         * （不同聚类簇 + 垂直分离足够）且评分接近时才保留，**不设数量上限**。
         * 用于抑制「数据一杂就多框」；[codes] 仍保留全部候选供诊断。
         */
        val selectedCodes: List<CodeResult> = codes,
        /** 页面类型：餐食 / 快递（词汇引擎先判定类型再识别） */
        val pageType: String,
        val totalMs: Long,
    )

    private val paddleOcr = PaddleOcrHelper.getInstance(context)
    private val ruleRepo = PickupWordRuleRepository(context)
    private val mutex = Mutex()

    /** 当前生效的词汇规则（内存缓存，识别时直接读）。 */
    @Volatile
    private var rulePack: PickupWordRulePack? = null

    suspend fun ensureRulesLoaded(): PickupWordRulePack {
        rulePack?.let { return it }
        return mutex.withLock {
            rulePack ?: ruleRepo.load().also { rulePack = it }
        }
    }

    suspend fun reloadRules() {
        rulePack = ruleRepo.load()
    }

    suspend fun run(
        bitmap: Bitmap,
        keepCropBitmap: Boolean = true,
        existingPass1: PaddleOcrHelper.RecognizeResult? = null,
    ): EngineResult {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val pack = ensureRulesLoaded()

        // Pass1：全图识别（PaddleOcrHelper 内部按 OCR_MIN_CONFIDENCE=0.90 过滤并按阅读顺序排列）
        val pass1 = existingPass1 ?: paddleOcr.recognizeAsync(bitmap)
        val pass1Lines = (pass1?.textBlocks ?: emptyList()).map { tb ->
            Blk(tb.text.trim(), tb.confidence, tb.boundingBox ?: Rect())
        }.filter { it.rect.width() > 0 && it.rect.height() > 0 }
        val fullText = buildReadingOrderText(pass1Lines)

        // 品牌定位
        val brand = detectBrand(pass1Lines, fullText, bitmap.width, bitmap.height)

        // ═══ 类型判定：餐食 or 快递 ═══
        val foodWords = pack.enabledWords(WordType.FOOD)
        val expressWords = pack.enabledWords(WordType.EXPRESS)
        val auxiliaryWords = pack.enabledWords(WordType.AUXILIARY)
        val foodHits = findKeywordBlocks(pass1Lines, foodWords)
        val expressHits = findKeywordBlocks(pass1Lines, expressWords)
        val isExpressPage = expressHits.size > foodHits.size
        val activeWords = if (isExpressPage) expressWords else foodWords
        val keywordHits = if (isExpressPage) expressHits else foodHits
        Log.d(
            "RecognitionMonitor",
            "词汇引擎类型判定: ${if (isExpressPage) "快递" else "餐食"} (餐食命中=${foodHits.size}, 快递命中=${expressHits.size}, 辅助锚点=${auxiliaryWords.size})",
        )
        Log.d(
            "RecognitionMonitor",
            "词汇引擎关键词: ${keywordHits.joinToString { hit -> "${hit.keyword}@[${hit.block.rect.left},${hit.block.rect.top}]" }}",
        )

        // 聚类
        val groups = clusterHits(keywordHits)

        // 快递页：辅助锚点词块（仅扩大框选，不进关键词）
        val auxBlocks = if (isExpressPage && auxiliaryWords.isNotEmpty()) {
            findKeywordBlocks(pass1Lines, auxiliaryWords).map { it.block }
        } else {
            emptyList()
        }

        // 一卡一框：先把每个簇的裁剪区算出来（纯几何），再让相邻簇互不吞并。
        // 快递取件码列表一屏有多张卡片、每张卡片一个取件码，而向下扩展的深度是按
        // 「图高比例」给的（vDepth = max(6×行高, 15%图高)）：前一张卡片的裁剪区会一路
        // 吃到下一张卡片的码行/图标块上。实测菜鸟列表（21-5-3607 与 40-2-7253 两张卡）
        // 两个码被框进同一个裁剪区，而一个裁剪区只出一个码，整页就只识别到一个。
        val plannedCrops = groups.map { group ->
            val gTop = group.minOf { it.block.rect.top }
            val gBottom = group.maxOf { it.block.rect.bottom }
            val gH = (gBottom - gTop).toFloat().coerceAtLeast(40f)
            val extraAnchors = if (isExpressPage && auxBlocks.isNotEmpty()) {
                auxBlocks.filter { addr ->
                    addr.rect.top <= gBottom + (gH * 4f).toInt() && addr.rect.bottom >= gTop - (gH * 4f).toInt()
                }
            } else {
                emptyList()
            }
            computeCropRect(
                group, pass1Lines, bitmap.width, bitmap.height,
                allowCompact = extraAnchors.isEmpty(),
                extraBlocks = extraAnchors,
            )
        }

        // 每组：裁剪 + Pass2 二次识别 + 提取
        val crops = mutableListOf<CropInfo>()
        val codes = mutableListOf<CodeResult>()
        for ((clusterIndex, group) in groups.withIndex()) {
            val planned = plannedCrops[clusterIndex] ?: continue
            // 下界被下一个簇的裁剪区上界截断 → 相邻簇的框永不重叠，一个框只覆盖一张卡片。
            // 仅在截断后仍框得住本簇自己的定位词行时才截断（boundary >= 本簇行底），
            // 否则两簇区域本就交错，强行截断会把本簇内容切掉。
            val nextTop = plannedCrops.getOrNull(clusterIndex + 1)?.top
            val groupBottom = group.maxOf { it.block.rect.bottom }
            val cropRect = if (nextTop != null && nextTop - 1 >= groupBottom && planned.bottom > nextTop - 1) {
                Rect(planned.left, planned.top, planned.right, nextTop - 1)
            } else {
                planned
            }
            Log.d(
                "RecognitionMonitor",
                "词汇引擎裁剪区: [${cropRect.left},${cropRect.top},${cropRect.right},${cropRect.bottom}] 锚点=${group.minByOrNull { it.block.rect.top }?.block?.text}",
            )

            val cropBitmap = Bitmap.createBitmap(
                bitmap, cropRect.left, cropRect.top,
                cropRect.width(), cropRect.height(),
            )
            val pass2 = paddleOcr.recognizeAsync(cropBitmap)
            if (!keepCropBitmap) cropBitmap.recycle()
            // Pass2 的坐标是相对裁剪图的，统一偏移回全图坐标：
            // 否则 matchedRect 与锚点/图高不在同一坐标系，评分与跨簇间距都会被算错。
            val pass2Lines = (pass2?.textBlocks ?: emptyList()).map { tb ->
                val local = tb.boundingBox ?: Rect()
                Blk(
                    tb.text.trim(),
                    tb.confidence,
                    Rect(
                        local.left + cropRect.left,
                        local.top + cropRect.top,
                        local.right + cropRect.left,
                        local.bottom + cropRect.top,
                    ),
                )
            }.filter { it.rect.width() > 0 && it.rect.height() > 0 }

            // Pass2 是对裁剪区的增益，不是否决权：裁剪区内 Pass1 已通过阈值、Pass2 却漏识的块补回来。
            // 同一行两次识别的置信度会波动（如「取件码7-5-5009」Pass1=91.97% / Pass2=89.59%），
            // 只按 Pass2 判定会让已通过闸门的行凭空消失，进而落到 scanCodeRegex 扫出无关数字。
            val cropLines = pass2Lines + pass1Lines
                .filter { cropRect.contains(it.rect.centerX().toInt(), it.rect.centerY().toInt()) }
                .filter { p1 ->
                    val clean = p1.text.replace(" ", "").replace("\n", "")
                    pass2Lines.none { it.text.replace(" ", "").replace("\n", "") == clean }
                }

            // 与已有裁剪区大面积重叠则跳过
            val newArea = cropRect.width().toFloat() * cropRect.height()
            val overlapRatio = crops.maxOfOrNull { existing ->
                val inter = Rect(existing.rect)
                if (!inter.intersect(cropRect)) 0f
                else (inter.width().toFloat() * inter.height()) / newArea.coerceAtLeast(1f)
            } ?: 0f
            if (overlapRatio > 0.6f) continue

            val cropInfo = CropInfo(
                rect = cropRect,
                bitmap = if (keepCropBitmap) cropBitmap else null,
                lines = cropLines,
                pass2TotalMs = pass2?.diagnosticResult?.totalTimeMs ?: 0L,
            )
            crops.add(cropInfo)

            var c = extractCodeFromCrop(cropInfo, activeWords)
            if (c != null) {
                val anchor = group.minByOrNull { it.block.rect.top }?.block
                val anchorText = anchor?.text
                val sl = if (anchor != null && c.matchedRect != null &&
                    anchor.rect.top >= c.matchedRect.bottom - (c.matchedRect.height() * 0.2f).toInt() &&
                    anchor.rect.top <= c.matchedRect.bottom + max((c.matchedRect.height() * 4f).toInt(), 80) &&
                    anchorText != null &&
                    anchorText.any { it in '\u4e00'..'\u9fa5' } &&
                    activeWords.none { anchorText.replace(" ", "") == it } &&
                    STATUS_WORDS.none { anchorText.replace(" ", "") == it }
                ) anchorText else null
                c = c.copy(
                    slogan = sl,
                    clusterIndex = clusterIndex,
                    anchorHeight = anchor?.height?.toInt() ?: 0,
                    score = scoreCandidate(c, anchor, cropInfo, bitmap.height, activeWords),
                )
                codes.add(c)
                Log.d(
                    "RecognitionMonitor",
                    "词汇引擎提取: code=${c.code} conf=${c.confidence} slogan=${sl ?: "无"}",
                )
            }
            if (codes.size >= MAX_CODES) break
        }

        // 兜底：无关键词/裁剪失败时回退 Pass1 全文（单码）
        if (codes.isEmpty()) {
            extractCodeFromFull(pass1Lines)?.let { full ->
                codes.add(full)
            }
        }

        // 去重：同码保留最高置信度，按位置排序
        val dedupCodes = codes
            .sortedByDescending { it.confidence }
            .distinctBy { it.code }
            .sortedBy { it.matchedRect?.top ?: Int.MAX_VALUE }

        // 页面级裁决：默认只留主候选，抑制「数据一杂就多框」（全部候选仍保留在 codes 中供诊断）
        val selectedCodes = selectCodes(dedupCodes)
        Log.d(
            "RecognitionMonitor",
            "候选裁决: 全部=${dedupCodes.joinToString { "${it.code}(分${it.score}/簇${it.clusterIndex})" }} " +
                "→ 选中=${selectedCodes.joinToString { it.code }}",
        )

        return EngineResult(
            pass1Lines = pass1Lines,
            pass1TotalMs = pass1?.diagnosticResult?.totalTimeMs ?: 0L,
            fullText = fullText,
            brand = brand,
            keywordHits = keywordHits,
            crops = crops,
            codes = dedupCodes,
            selectedCodes = selectedCodes,
            pageType = if (isExpressPage) "快递" else "餐食",
            totalMs = android.os.SystemClock.elapsedRealtime() - startedAt,
        )
    }

    // ─────────── 品牌定位（规则包品牌表 + 内置默认品牌） ───────────

    private fun detectBrand(blocks: List<Blk>, fullText: String, imageWidth: Int, imageHeight: Int): BrandHit? {
        val lower = fullText.lowercase()
        val pack = rulePack
        val brandDefs = (pack?.brands?.filter { it.enabled } ?: emptyList())
            .map { it.name to it.keywords }
            .ifEmpty { DEFAULT_BRANDS }

        var best: BrandHit? = null
        var bestScore = 0
        for ((name, keywords) in brandDefs) {
            var score = 0
            var matchedKeyword: String? = null
            var matchedBlock: Blk? = null
            for (keyword in keywords) {
                if (lower.contains(keyword.lowercase())) {
                    score += keyword.length * 2 + 4
                    if (matchedKeyword == null) matchedKeyword = keyword
                    else if (keyword.length > matchedKeyword.length) matchedKeyword = keyword
                }
            }
            val kw = matchedKeyword ?: continue
            val block = blocks
                .filter { it.text.lowercase().contains(kw.lowercase()) }
                .maxByOrNull { it.confidence }
            if (block != null) score += 8
            if (score > bestScore) {
                val area = block?.let {
                    when {
                        it.centerY < imageHeight * 0.25f -> "顶部"
                        it.centerY < imageHeight * 0.6f -> "中部"
                        else -> "底部"
                    }
                } ?: "未定位"
                best = BrandHit(name, kw, block?.confidence ?: 0f, block?.rect, area)
                bestScore = score
            }
        }
        return best
    }

    // ─────────── 关键词定位 ───────────

    private fun findKeywordBlocks(blocks: List<Blk>, keywords: List<String>): List<KeywordHit> {
        return blocks.mapNotNull { block ->
            val clean = block.text.replace(" ", "").replace("\n", "")
            val keyword = keywords.firstOrNull { clean.contains(it) }
            if (keyword != null) KeywordHit(keyword, block) else null
        }
    }

    private fun containsKeyword(clean: String, keywords: List<String>): Boolean =
        keywords.any { clean.contains(it) }

    /** 关键词聚类（多取餐码支持）。 */
    private fun clusterHits(hits: List<KeywordHit>): List<List<KeywordHit>> {
        if (hits.isEmpty()) return emptyList()
        val sorted = hits.sortedBy { it.block.rect.top }
        val groups = mutableListOf<MutableList<KeywordHit>>()
        var cur = mutableListOf(sorted[0])
        var curBottom = sorted[0].block.rect.bottom
        val curTop = sorted[0].block.rect.top
        for (i in 1 until sorted.size) {
            val h = sorted[i]
            val groupH = (curBottom - curTop).toFloat().coerceAtLeast(cur[0].block.height)
            val gap = max(groupH * 2f, 40f)
            if (h.block.rect.top <= curBottom + gap) {
                cur.add(h)
                curBottom = max(curBottom, h.block.rect.bottom)
            } else {
                groups.add(cur)
                cur = mutableListOf(h)
            }
        }
        groups.add(cur)
        return groups
    }

    // ─────────── 裁剪区域计算（移植自 demo） ───────────

    private fun computeCropRect(
        hits: List<KeywordHit>,
        blocks: List<Blk>,
        imageWidth: Int,
        imageHeight: Int,
        allowCompact: Boolean = true,
        extraBlocks: List<Blk> = emptyList(),
    ): Rect? {
        val anchor = hits.minByOrNull { it.block.rect.top }?.block ?: return null
        val anchorH = anchor.height
        val anchorY = anchor.rect.top + anchorH / 2f

        // 紧凑模式：关键词块自身已带码值（如「取件码2-1-5028」）。
        // 必须逐 token 通过码值校验，不能只看「关键词后面有没有数字」——
        // 否则「待取件 2025-05-30 15:08:34」的日期/时间会被当成码值，
        // 使裁剪区只框锚点行 + 下方两行，漏掉更下方的真正取件码行。
        val embeddedCode = hits
            .firstOrNull { hit ->
                anchor.text.replace(" ", "").replace("\n", "").contains(hit.keyword)
            }
            ?.let { hit ->
                anchor.text.substringAfter(hit.keyword)
                    .split(' ', '\n', '\t', '，', ',', '、', '；', ';')
                    .firstNotNullOfOrNull { token -> validateAndNormalize(token) }
            }
        if (allowCompact && embeddedCode != null) {
            fun sameRowInline(b: Blk): Boolean {
                val overlap = min(anchor.rect.bottom, b.rect.bottom) - max(anchor.rect.top, b.rect.top)
                val ratio = overlap.toFloat().coerceAtLeast(0f) / min(anchorH, b.height)
                val centerDist = abs(b.centerY - anchorY)
                return ratio >= 0.35f || centerDist <= max(anchorH, b.height) * 0.5f
            }
            val rowBlocks2 = blocks.filter { sameRowInline(it) && it !== anchor } + anchor
            val union2 = Rect()
            rowBlocks2.forEach { b -> union2.union(b.rect) }
            val below2 = blocks.filter { b ->
                b.rect.top > anchor.rect.bottom - (anchorH * 0.5f).toInt() &&
                    b.rect.top <= anchor.rect.bottom + (anchorH * 2f).toInt() &&
                    b.rect.right >= union2.left - anchorH.toInt() &&
                    b.rect.left <= union2.right + anchorH.toInt()
            }
            below2.forEach { union2.union(it.rect) }
            if (union2.isEmpty) union2.set(anchor.rect)
            val padCells = (anchorH * 0.6f).toInt().coerceAtLeast(4)
            val result = Rect(
                (union2.left - padCells).coerceAtLeast(0),
                (union2.top - padCells).coerceAtLeast(0),
                (union2.right + padCells).coerceAtMost(imageWidth),
                (union2.bottom + padCells).coerceAtMost(imageHeight),
            )
            return if (result.width() < 2 || result.height() < 2) null else result
        }

        fun sameRow(b: Blk): Boolean {
            val overlap = min(anchor.rect.bottom, b.rect.bottom) - max(anchor.rect.top, b.rect.top)
            val ratio = overlap.toFloat().coerceAtLeast(0f) / min(anchorH, b.height)
            val centerDist = abs(b.centerY - anchorY)
            return ratio >= 0.35f || centerDist <= max(anchorH, b.height) * 0.5f
        }
        fun horizontalNear(b: Blk): Boolean =
            b.rect.right >= anchor.rect.left - (anchorH * 4f).toInt() &&
                b.rect.left <= anchor.rect.right + (anchorH * 4f).toInt()

        val rowBlocks = blocks.filter { sameRow(it) && horizontalNear(it) } + anchor
        val rowLeft = rowBlocks.minOf { it.rect.left }
        val rowRight = rowBlocks.maxOf { it.rect.right }

        val hExpandRight = max(anchorH, imageWidth * 0.2f)
        val hExpandLeft = max(anchorH, imageWidth * 0.12f)
        val vDepth = max(anchorH * 6f, imageHeight * 0.15f)
        val vDepthUp = max(anchorH * 3f, 48f)

        val belowBlocks = blocks.filter { b ->
            b.rect.top > anchor.rect.bottom - (anchorH * 0.5f).toInt() &&
                b.rect.right >= rowLeft - (hExpandLeft).toInt() &&
                b.rect.left <= rowRight + (hExpandRight).toInt() &&
                b.rect.top <= anchor.rect.bottom + vDepth.toInt()
        }
        val aboveBlocks = blocks.filter { b ->
            b.rect.bottom < anchor.rect.top + (anchorH * 0.5f).toInt() &&
                b.rect.bottom >= anchor.rect.top - vDepthUp.toInt() &&
                b.rect.right >= rowLeft - (hExpandLeft).toInt() &&
                b.rect.left <= rowRight + (hExpandRight).toInt()
        }

        val union = Rect()
        (rowBlocks + belowBlocks + aboveBlocks).forEach { b -> union.union(b.rect) }
        hits.forEach { hit -> union.union(hit.block.rect) }
        extraBlocks.forEach { b -> union.union(b.rect) }
        if (union.isEmpty) union.set(anchor.rect)

        val padX = anchorH.toInt().coerceAtLeast(4)
        val padY = (anchorH * 0.7f).toInt().coerceAtLeast(4)
        val result = Rect(
            (union.left - padX).coerceAtLeast(0),
            (union.top - padY).coerceAtLeast(0),
            (union.right + padX).coerceAtMost(imageWidth),
            (union.bottom + padY).coerceAtMost(imageHeight),
        )
        return if (result.width() < 2 || result.height() < 2) null else result
    }

    // ─────────── 候选评分与页面级裁决（抑制「数据一杂就多框」） ───────────

    /**
     * 单候选评分：以 OCR 置信度为基座，叠加「前缀证据 / 紧凑模式 / 垂直位置 / 锚点距离」。
     * 分数只用于同一次识别内部的候选排序与剪枝，不改变候选的码值。
     */
    private fun scoreCandidate(
        candidate: CodeResult,
        anchor: Blk?,
        crop: CropInfo,
        imageHeight: Int,
        activeWords: List<String>,
    ): Int {
        var score = (candidate.confidence * 100f).toInt()

        // 前缀证据：码值所在行自身带定位词（如「取餐码 1234」），最可靠
        val codeLine = crop.lines
            .firstOrNull { it.text.replace(" ", "").contains(candidate.code) }
            ?.text?.replace(" ", "")?.replace("\n", "")
        if (codeLine != null && activeWords.any { codeLine.contains(it) }) score += 30

        // 紧凑模式：锚点块自身就带码（关键词与码同一行）
        if (anchor != null) {
            val anchorClean = anchor.text.replace(" ", "").replace("\n", "")
            if (activeWords.any { anchorClean.contains(it) } && anchorClean.contains(candidate.code)) score += 15
        }

        candidate.matchedRect?.let { rect ->
            // 垂直位置：主取餐码多在中部，贴顶/贴底次之
            if (imageHeight > 0) {
                val ratio = rect.centerY().toFloat() / imageHeight
                score += when {
                    ratio in 0.25f..0.75f -> 12
                    ratio < 0.25f -> 6
                    else -> 3
                }
            }
            // 距锚点过远：候选应紧邻定位词，否则多半是邻卡的码
            if (anchor != null) {
                val rowH = anchor.height.coerceAtLeast(1f)
                val gap = when {
                    rect.top >= anchor.rect.bottom -> (rect.top - anchor.rect.bottom).toFloat()
                    rect.bottom <= anchor.rect.top -> (anchor.rect.top - rect.bottom).toFloat()
                    else -> 0f
                }
                if (gap > rowH * 4f) score -= 20
            }
        }

        // 短码误命中概率更高（1-3 位数字在很多页面都会出现）
        if (candidate.code.length < 4) score -= 6
        return score
    }

    /**
     * 页面级裁决：默认只保留主候选；仅当另一候选与**已选候选**空间独立
     * （不同聚类簇 + 垂直分离 ≥ max(2.5×行高, 120px)）且评分不低于主候选的
     * [MIN_SCORE_RATIO] 时才保留，**不设数量上限** —— 真多件全部保留，冗余命中被收敛。
     */
    private fun selectCodes(candidates: List<CodeResult>): List<CodeResult> {
        if (candidates.size <= 1) return candidates
        val ranked = candidates.sortedWith(
            compareByDescending<CodeResult> { it.score }.thenByDescending { it.confidence },
        )
        val primary = ranked.first()
        val selected = mutableListOf(primary)
        for (candidate in ranked.drop(1)) {
            if (selected.any { it.code.equals(candidate.code, ignoreCase = true) }) continue
            if (candidate.score < primary.score * MIN_SCORE_RATIO) continue
            if (selected.none { isIndependent(it, candidate) }) continue
            selected += candidate
        }
        return selected.sortedBy { it.matchedRect?.top ?: Int.MAX_VALUE }
    }

    /** 两个候选是否来自不同订单区域：必须属于不同聚类簇，且垂直方向互不重叠、间距足够。 */
    private fun isIndependent(a: CodeResult, b: CodeResult): Boolean {
        if (a.clusterIndex >= 0 && a.clusterIndex == b.clusterIndex) return false
        val ra = a.matchedRect ?: return false
        val rb = b.matchedRect ?: return false
        val vGap = when {
            ra.bottom <= rb.top -> rb.top - ra.bottom
            rb.bottom <= ra.top -> ra.top - rb.bottom
            else -> -1
        }
        if (vGap < 0) return false
        val rowH = max(a.anchorHeight, b.anchorHeight).coerceAtLeast(MIN_ROW_HEIGHT_PX)
        return vGap >= max((rowH * INDEPENDENT_GAP_RATIO).toInt(), MIN_INDEPENDENT_GAP_PX)
    }

    // ─────────── 取餐码提取（移植自 demo，置信度排序） ───────────

    private fun extractCodeFromCrop(crop: CropInfo, activeWords: List<String>): CodeResult? {
        val lines = crop.lines
        val hits = findKeywordBlocks(lines, activeWords)
        val candidates = mutableListOf<CodeCandidate>()
        val hitRects = hits.map { it.block.rect }

        /** 候选所在行是否与定位词同行（含定位词块自身）：同行即视为带标签证据。 */
        fun sameRowAsHit(rect: Rect): Boolean = hitRects.any { hit ->
            val overlap = min(hit.bottom, rect.bottom) - max(hit.top, rect.top)
            val ratio = overlap.toFloat().coerceAtLeast(0f) /
                min(hit.height(), rect.height()).coerceAtLeast(1)
            ratio >= 0.35f ||
                abs((hit.top + hit.bottom) / 2f - (rect.top + rect.bottom) / 2f) <=
                max(hit.height(), rect.height()) * 0.5f
        }

        fun addCandidate(raw: String, confidence: Float, source: Rect?) {
            if (CONTACT_CONTEXT_WORDS.any { raw.contains(it) }) return
            val code = validateAndNormalize(raw) ?: return
            candidates.add(CodeCandidate(code, confidence, source != null && sameRowAsHit(source)))
        }

        if (hits.isNotEmpty()) {
            for (hit in hits) {
                val clean = hit.block.text.replace(" ", "").replace("\n", "")
                val after = clean.substringAfter(hit.keyword).trimStart(':', '：', ' ', '，', ',')
                if (after.isNotBlank() && isAcceptableLength(after)) {
                    addCandidate(after, hit.block.confidence, hit.block.rect)
                }
                val before = clean.substringBefore(hit.keyword).trimEnd(':', '：', ' ', '，', ',')
                if (before.isNotBlank() && isAcceptableLength(before)) {
                    addCandidate(before, hit.block.confidence, hit.block.rect)
                }
            }
            val anchor = hits.minByOrNull { it.block.rect.top }!!.block
            val vNear = max(anchor.height * 12f, 96f)
            val nearby = lines.filter { line ->
                (abs(line.centerY - anchor.centerY) <= max(anchor.height, line.height) * 1.2f) ||
                    (line.rect.bottom >= anchor.rect.top - vNear.toInt() && line.rect.top <= anchor.rect.bottom + vNear.toInt())
            }
            for (line in nearby) {
                val clean = line.text.replace(" ", "").replace("\n", "")
                if (!containsKeyword(clean, activeWords)) addCandidate(clean, line.confidence, line.rect)
            }
        } else {
            for (line in lines) {
                val clean = line.text.replace(" ", "").replace("\n", "")
                if (!containsKeyword(clean, activeWords)) addCandidate(clean, line.confidence, line.rect)
            }
        }

        val all = if (candidates.isNotEmpty()) {
            candidates
        } else {
            scanCodeRegex(lines).map { CodeCandidate(it.first, it.second, false) }
        }
        // 先取「带定位词证据」的候选（如「取件码7-5-5009」优先于同框的裸数字「8310」手机尾号），
        // 无带证据候选时才回退到置信度最高者（「取件码」与码值分行的版面）。
        val best = all.filter { it.labeled }.maxByOrNull { it.confidence }
            ?: all.maxByOrNull { it.confidence }
            ?: return null
        return CodeResult(
            code = best.code,
            confidence = best.confidence,
            fromCrop = true,
            matchedRect = lines.firstOrNull { it.text.replace(" ", "").contains(best.code) }?.rect
                ?: crop.rect,
        )
    }

    /** 裁剪区候选：code 为归一化后的码值；labeled 表示该候选紧邻定位词（前缀/同行证据）。 */
    private data class CodeCandidate(
        val code: String,
        val confidence: Float,
        val labeled: Boolean,
    )

    /** Pass1 全文兜底（无关键词/无法裁剪时）。 */
    private fun extractCodeFromFull(blocks: List<Blk>): CodeResult? {
        val rawCandidates = scanCodeRegex(blocks)
        val best = rawCandidates.maxByOrNull { it.second } ?: return null
        return CodeResult(
            code = best.first,
            confidence = best.second,
            fromCrop = false,
            matchedRect = blocks.firstOrNull { it.text.replace(" ", "").contains(best.first) }?.rect,
        )
    }

    private fun scanCodeRegex(blocks: List<Blk>): List<Pair<String, Float>> {
        val result = mutableListOf<Pair<String, Float>>()
        val digit = Regex("[0-9]{4,8}")
        val alnum = Regex("[A-Za-z0-9#-]{3,12}")
        val queue = Regex("[A-Za-z]{1,3}[0-9]{1,3}")
        val shortPickupNum = Regex("^[0-9]{1,3}$")
        val slogan = Regex("[A-Za-z0-9]{1,6}[.．][\\u4e00-\\u9fa5A-Za-z0-9]{2,24}")
        val timeLike = Regex("^\\d{1,2}([::：]\\d{2})+$")
        val dateLike = Regex("^\\d{4}[-/年]\\d{1,2}[-/月]?\\d{1,2}日?$")
        val phoneLike = Regex("^1[3-9]\\d{9}$")
        val mobileFull = Regex("1[3-9]\\d{9}")

        for (block in blocks) {
            val text = block.text.replace(" ", "").replace("\n", "")
            if (CONTACT_CONTEXT_WORDS.any { text.contains(it) }) continue
            if (mobileFull.containsMatchIn(text)) continue
            if (text.isNotBlank() && shortPickupNum.matches(text) && text.toInt() !in 1900..2099) {
                result.add(text to block.confidence)
                continue
            }
            if (text.count { it.isDigit() } > 12) continue
            for (p in listOf(digit, alnum, queue, slogan)) {
                val m = p.find(text) ?: continue
                val v = m.value
                if (timeLike.matches(v) || dateLike.matches(v) || phoneLike.matches(v)) continue
                if (v.length == 4 && v.all { it.isDigit() } && v.toInt() in 1900..2099) continue
                if (v.all { it.isLetter() } && v.length <= 6) continue
                result.add(v to block.confidence)
            }
        }
        return result
    }

    private fun isSloganLike(s: String): Boolean =
        Regex("^[A-Za-z0-9]{1,6}[.．][\\u4e00-\\u9fa5A-Za-z0-9]{2,24}$").matches(s)

    private fun isAcceptableLength(s: String): Boolean =
        s.length <= 12 || (s.length <= 30 && isSloganLike(s))

    private fun validateAndNormalize(raw: String): String? {
        var v = raw.trim().trim(':', '：', '，', ',', '.', '。', '#', '-', ' ')
        if (v.isEmpty() || v.length > 30) return null
        if (v.any { it.isWhitespace() }) return null
        if (v.contains("小时") || v.contains("分钟") || v.contains("有效") ||
            v.contains("已使用") || v.contains("等待") || v.contains("请到") ||
            v.contains("门店") || v.contains("验券") || v.contains("大屏叫号")
        ) {
            return null
        }
        val timeLike = Regex("^\\d{1,2}([::：]\\d{2})+$")
        val dateLike = Regex("^\\d{4}[-/年]\\d{1,2}[-/月]?\\d{1,2}日?$")
        val phoneLike = Regex("^1[3-9]\\d{9}$")
        if (timeLike.matches(v) || dateLike.matches(v) || phoneLike.matches(v)) return null
        if (v.length == 4 && v.all { it.isDigit() } && v.toInt() in 1900..2099) return null
        if (v.all { it.isDigit() }) return v
        if (v.length < 3) return null
        if (v.all { it.isLetter() } && v.length <= 6) return null
        val hasChinese = v.any { it in '\u4e00'..'\u9fa5' }
        if (hasChinese && !isSloganLike(v)) return null
        if (v.length > 12 && !isSloganLike(v)) return null
        if (v.none { it.isLetterOrDigit() }) return null
        return v
    }

    // ─────────── 全文构建 ───────────

    private fun buildReadingOrderText(blocks: List<Blk>): String {
        if (blocks.isEmpty()) return ""
        val sorted = blocks.sortedWith(compareBy({ it.rect.top }, { it.rect.left }))
        val sb = StringBuilder()
        var prev: Blk? = null
        for (b in sorted) {
            if (prev != null) {
                val sameRow = abs(b.centerY - prev.centerY) <= max(prev.height, b.height) * 0.6f
                if (!sameRow) sb.append('\n')
            }
            sb.append(b.text)
            prev = b
        }
        return sb.toString()
    }

    companion object {
        const val TAG = "PickupTwoPassEngine"
        private const val MAX_CODES = 20

        /** 次候选相对主候选的最低评分比，低于此值视为误命中直接丢弃 */
        private const val MIN_SCORE_RATIO = 0.85f
        /** 判定候选互相独立的垂直分离：max(锚点行高 × 倍数, 最小像素) */
        private const val INDEPENDENT_GAP_RATIO = 2.5f
        private const val MIN_INDEPENDENT_GAP_PX = 120
        private const val MIN_ROW_HEIGHT_PX = 24

        /** 联系方式上下文词（电话等数字不是取件码） */
        private val CONTACT_CONTEXT_WORDS = listOf(
            "电话", "投诉", "代理点", "拨打", "联系", "热线", "座机", "号码",
        )

        /** 物流/取餐状态词（slogan 不应是这些） */
        private val STATUS_WORDS = listOf(
            "待取件", "派件中", "运输中", "已签收", "已取件", "待收件", "已送达", "配送中",
        )

        /** 内置默认品牌表（规则包无品牌时使用） */
        private val DEFAULT_BRANDS = listOf(
            "瑞幸咖啡" to listOf("瑞幸", "luckin", "小蓝杯"),
            "星巴克" to listOf("星巴克", "starbucks", "啡快"),
            "库迪咖啡" to listOf("库迪", "cotti"),
            "霸王茶姬" to listOf("霸王茶姬", "chagee"),
            "喜茶" to listOf("喜茶", "heytia", "heetea"),
            "奈雪的茶" to listOf("奈雪的茶", "奈雪"),
            "蜜雪冰城" to listOf("蜜雪冰城", "蜜雪", "幸运咖"),
            "沪上阿姨" to listOf("沪上阿姨"),
            "古茗" to listOf("古茗"),
            "茶百道" to listOf("茶百道"),
            "益禾堂" to listOf("益禾堂"),
            "书亦烧仙草" to listOf("书亦烧仙草", "书亦"),
            "CoCo都可" to listOf("coco都可", "coco"),
            "一点点" to listOf("一点点", "1点点"),
            "茶话弄" to listOf("茶话弄"),
            "茉莉奶白" to listOf("茉莉奶白"),
            "甜啦啦" to listOf("甜啦啦"),
            "麦当劳" to listOf("麦当劳", "mcdonald's", "mcdonald", "麦咖啡"),
            "肯德基" to listOf("肯德基", "kfc"),
            "汉堡王" to listOf("汉堡王", "burger king"),
            "塔斯汀" to listOf("塔斯汀"),
            "华莱士" to listOf("华莱士"),
            "德克士" to listOf("德克士"),
            "必胜客" to listOf("必胜客", "pizza hut"),
            "达美乐" to listOf("达美乐"),
            "老乡鸡" to listOf("老乡鸡"),
            "大米先生" to listOf("大米先生"),
            "美团外卖" to listOf("美团外卖", "美团"),
            "饿了么" to listOf("饿了么"),
        )
    }
}