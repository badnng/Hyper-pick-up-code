package com.Badnng.moe.rules

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

data class BrandFeatureWord(val word: String) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("word", word)
    }
}

/** 独立于取餐码模板，仅用于饮品、餐食结果的品牌判定。 */
data class BrandFeatureRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val words: List<BrandFeatureWord> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("enabled", enabled)
        put("words", JSONArray().apply { words.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(json: JSONObject): BrandFeatureRule = BrandFeatureRule(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = json.optString("name").trim(),
            enabled = json.optBoolean("enabled", true),
            words = json.optJSONArray("words")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val word = item.optString("word").trim().takeIf(String::isNotBlank)
                        ?: return@mapNotNull null
                    // 兼容旧规则包：保留词汇，旧 confidence 字段不再参与识别。
                    BrandFeatureWord(word)
                }
            }.orEmpty(),
        )

        fun list(array: JSONArray?): List<BrandFeatureRule> = array?.let {
            val ids = HashSet<String>()
            (0 until it.length()).mapNotNull { index ->
                it.optJSONObject(index)?.let(::fromJson)?.takeIf { rule -> rule.name.isNotBlank() }
                    ?.let { rule ->
                        if (ids.add(rule.id)) rule else rule.copy(id = UUID.randomUUID().toString())
                    }
            }
        }.orEmpty()
    }
}

data class BrandFeatureMatch(
    val name: String,
    val directName: Boolean,
    val matchedWords: List<String>,
) {
    val hitCount: Int get() = matchedWords.size
}

object BrandFeatureMatcher {
    fun normalize(value: String): String = value.filterNot(Char::isWhitespace).lowercase(Locale.ROOT)

    /** 瑞幸继续使用已有专用识别，不参与自定义品牌特征词竞争。 */
    fun usesDedicatedRecognition(name: String?): Boolean = name != null && normalize(name) in
        setOf("瑞幸", "瑞幸咖啡", "luckin", "luckincoffee", "luckin咖啡")

    /**
     * 每个不同词算一票，品牌名称也算一票。重复、大小写/空白变体及包含关系的词
     * 只算一次，避免同一证据重复计数。先比较票数，同票再看品牌名称是否出现。
     * 名称排序仅用于稳定展示；真正决定结果必须使用 [winner]，无法区分时不猜品牌。
     */
    fun rank(
        text: String,
        type: String,
        rules: List<BrandFeatureRule>,
        knownBrandNames: List<String> = emptyList(),
    ): List<BrandFeatureMatch> {
        if (type != "饮品" && type != "餐食") return emptyList()
        val normalized = normalize(text)
        if (normalized.isEmpty()) return emptyList()
        val active = rules.filter { it.enabled && normalize(it.name).isNotEmpty() && !usesDedicatedRecognition(it.name) }
        val names = (active.map { it.name.trim() } + knownBrandNames.map(String::trim))
            .filter { normalize(it).isNotEmpty() && normalize(it) !in GENERIC_NAMES && !usesDedicatedRecognition(it) }
            .distinctBy(::normalize)
        val candidates = names.mapNotNull { name ->
            val words = (listOf(name) + active.filter { normalize(it.name) == normalize(name) }
                .flatMap { it.words }.map { it.word })
                .map(String::trim)
                .filter { normalize(it).isNotEmpty() && normalized.contains(normalize(it)) }
                .distinctBy(::normalize)
                .sortedWith(compareByDescending<String> { normalize(it).length }.thenBy(::normalize))
            val independent = mutableListOf<String>()
            words.forEach { candidate ->
                if (independent.none { normalize(it).contains(normalize(candidate)) }) {
                    independent += candidate
                }
            }
            if (independent.isEmpty()) null else BrandFeatureMatch(
                name = name,
                directName = normalized.contains(normalize(name)),
                matchedWords = independent,
            )
        }
        return candidates.sortedWith(
            compareByDescending<BrandFeatureMatch> { it.hitCount }
                .thenByDescending { it.directName }
                .thenBy { normalize(it.name) },
        )
    }

    fun winner(ranked: List<BrandFeatureMatch>): BrandFeatureMatch? {
        val best = ranked.firstOrNull() ?: return null
        val tied = ranked.drop(1).any { it.hitCount == best.hitCount && it.directName == best.directName }
        return best.takeUnless { tied }
    }

    private val GENERIC_NAMES = setOf("餐食", "饮品", "快递", "其他", "未知")
}
