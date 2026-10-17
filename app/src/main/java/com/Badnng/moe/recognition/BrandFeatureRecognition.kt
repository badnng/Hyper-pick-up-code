package com.Badnng.moe.recognition

import android.content.Context
import com.Badnng.moe.ocr.PickupTwoPassEngine
import com.Badnng.moe.rules.BrandFeatureMatch
import com.Badnng.moe.rules.BrandFeatureMatcher
import com.Badnng.moe.rules.LuckinQrRule
import com.Badnng.moe.rules.PickupWordRulePack
import com.Badnng.moe.rules.PickupWordRuleRepository
import com.Badnng.moe.rules.RecognitionRuleEngine
import com.Badnng.moe.rules.SimpleRuleCategory
import com.Badnng.moe.rules.SimpleRuleRuntime
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object BrandFeatureRecognition {
    /** 页面列出应用内的饮品、餐食品牌和用户补充的品牌，不混入快递或分类占位名称。 */
    fun editableNames(pack: PickupWordRulePack): List<String> = (
        PickupTwoPassEngine.defaultBrandNames() +
            RecognitionRuleEngine.rules.brands.drink.map { it.name } +
            RecognitionRuleEngine.rules.brands.food.map { it.name } +
            SimpleRuleRuntime.current().brands.filter { it.category != SimpleRuleCategory.EXPRESS }.map { it.name } +
            pack.brands.map { it.name } +
            pack.brandFeatures.map { it.name }
        ).map(String::trim).filter(::isFoodBrandName).distinctBy(BrandFeatureMatcher::normalize)

    fun isFoodBrandName(name: String): Boolean {
        val normalized = BrandFeatureMatcher.normalize(name)
        val expressNames = RecognitionRuleEngine.rules.brands.express.flatMap { listOf(it.name) + it.aliases } +
            SimpleRuleRuntime.current().brands.filter { it.category == SimpleRuleCategory.EXPRESS }.map { it.name }
        return normalized.isNotEmpty() && !BrandFeatureMatcher.usesDedicatedRecognition(name) &&
            normalized !in setOf("餐食", "饮品", "快递", "其他", "未知") &&
            expressNames.none { BrandFeatureMatcher.normalize(it) == normalized }
    }

    /** 品牌名称自动作为一个特征词；其他词完全由用户配置。 */
    fun knownNames(pack: PickupWordRulePack): List<String> =
        (PickupTwoPassEngine.defaultBrandNames() +
            RecognitionRuleEngine.getDrinkBrands().map { it.name } +
            RecognitionRuleEngine.getFoodBrands().map { it.name } +
            SimpleRuleRuntime.current().brands.filter { it.enabled && it.category != SimpleRuleCategory.EXPRESS }
                .map { it.name } + pack.brands.filter { it.enabled }.map { it.name }).filter(::isFoodBrandName)

    suspend fun apply(
        context: Context,
        result: RoutedRecognitionResult,
        sourceText: String? = null,
    ): RoutedRecognitionResult {
        if (result.orders.none { it.type == "饮品" || it.type == "餐食" }) return result
        RecognitionRuleEngine.initialize(context)
        val pack = PickupWordRuleRepository(context).load()
        val knownNames = knownNames(pack)
        val evidence = JSONArray()
        val orders = result.orders.map { order ->
            if (preservesDedicatedResult(order.brand, order.qr)) return@map order
            val ranked = BrandFeatureMatcher.rank(
                // 文本入口保留原文依据，避免在线模型裁掉品牌词；图片使用识别出的文字。
                text = sourceText ?: order.fullText,
                type = order.type,
                rules = pack.brandFeatures,
                knownBrandNames = knownNames + listOfNotNull(order.brand),
            )
            val match = BrandFeatureMatcher.winner(ranked) ?: return@map order
            evidence.put(JSONObject().apply {
                put("code", order.code)
                put("name", match.name)
                put("strategy", "keyword_count")
                put("matched_count", match.hitCount)
                put("resolution", if (ranked.drop(1).any { it.hitCount == match.hitCount }) "brand_name" else "more_words")
                put("direct_name", match.directName)
                put("matched_words", JSONArray(match.matchedWords))
            })
            order.copy(brand = match.name)
        }
        if (evidence.length() == 0) return result
        val diagnostic = result.metadata.ocrDiagnosticData?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: JSONObject().put("version", 1)
        diagnostic.put("brand_features", evidence)
        return result.copy(
            orders = orders,
            metadata = result.metadata.copy(ocrDiagnosticData = diagnostic.toString()),
        )
    }

    fun preservesDedicatedResult(brand: String?, qr: String?): Boolean =
        BrandFeatureMatcher.usesDedicatedRecognition(brand) || LuckinQrRule.matches(qr)

    fun describe(match: BrandFeatureMatch): String =
        "${match.name} · 命中 ${match.hitCount} 个不同词${if (match.directName) "（品牌名称已命中）" else ""}：${match.matchedWords.joinToString("、")}"

    fun diagnosticLabel(data: String?, code: String, brand: String?): String? = runCatching {
        val array = data?.let { JSONObject(it).optJSONArray("brand_features") } ?: return@runCatching null
        (0 until array.length()).firstNotNullOfOrNull { index ->
            val item = array.optJSONObject(index) ?: return@firstNotNullOfOrNull null
            if (item.optString("code") != code || item.optString("name") != brand) return@firstNotNullOfOrNull null
            val words = item.optJSONArray("matched_words") ?: JSONArray()
            val matchedWords = (0 until words.length()).map { words.getString(it) }
            if (item.optString("strategy") != "keyword_count") {
                // 历史诊断仍展示当时记录的旧分值，不将旧百分比解释为新计数结果。
                val percent = String.format(Locale.ROOT, "%.2f", item.optDouble("confidence", 0.0) * 100)
                return@firstNotNullOfOrNull "${item.optString("name")} · 历史置信分 $percent%：${matchedWords.joinToString("、")}"
            }
            describe(BrandFeatureMatch(
                name = item.optString("name"),
                directName = item.optBoolean("direct_name"),
                matchedWords = matchedWords,
            )) + if (item.optString("resolution") == "brand_name") "；同数时按品牌名称确定" else ""
        }
    }.getOrNull()
}
