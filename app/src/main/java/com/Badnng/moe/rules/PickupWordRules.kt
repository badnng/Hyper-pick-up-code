package com.Badnng.moe.rules

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 三类型词汇规则包：识别时按 「餐食定位词 / 快递定位词 / 辅助锚点词」 三种词汇定位取餐码区域。
 *
 * - 餐食定位词：取餐码/取餐号/取茶码/准备…（餐食/饮品取餐页）
 * - 快递定位词：取件码/驿站/丰巢/快递柜…（快递取件页）
 * - 辅助锚点词：快递超市/驿站地址…（只参与扩大框选范围，不是定位词汇）
 *
 * 识别流程：先判定页面类型（餐食/快递），再用对应类型词汇定位 → 裁剪 → 二次识别 → 提取取餐码。
 * 词汇不带分值权重（保持置信度排序，后续可扩展）。
 */
data class PickupWordRule(
    val id: String = UUID.randomUUID().toString(),
    val word: String = "",
    val enabled: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("word", word)
        put("enabled", enabled)
    }

    companion object {
        /**
         * 兼容旧数据：早期版本用 `map(::PickupWordRule)` 构建默认词条，
         * 关键词被误存进 [id] 字段而 [word] 为空。加载时若 word 为空且 id
         * 不是 UUID（像正常词汇文本），则把 id 迁移为 word 并重新生成 id。
         */
        fun fromJson(json: JSONObject): PickupWordRule {
            val id = json.optString("id")
            var word = json.optString("word", "")
            if (word.isBlank() && id.isNotBlank() && !id.matches(UUID_PATTERN)) {
                word = id
            }
            val finalId = if (id.isBlank() || id == word) UUID.randomUUID().toString() else id
            return PickupWordRule(
                id = finalId,
                word = word,
                enabled = json.optBoolean("enabled", true),
            )
        }

        private val UUID_PATTERN =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        fun list(jsonArray: JSONArray?): List<PickupWordRule> =
            if (jsonArray == null) emptyList()
            else (0 until jsonArray.length()).mapNotNull { jsonArray.optJSONObject(it)?.let(::fromJson) }
    }
}

/** 规则包内容：三种词汇 + 品牌表（品牌名与关键词，识别品牌用）。 */
data class PickupWordRulePack(
    val schemaVersion: Int = SCHEMA_VERSION,
    val name: String = "词汇识别规则",
    val updatedAt: Long = System.currentTimeMillis(),
    /** 餐食定位词 */
    val foodKeywords: List<PickupWordRule> = DEFAULT_FOOD_KEYWORDS.map { PickupWordRule(word = it) },
    /** 快递定位词 */
    val expressKeywords: List<PickupWordRule> = DEFAULT_EXPRESS_KEYWORDS.map { PickupWordRule(word = it) },
    /** 辅助锚点词（非定位词汇，仅扩大框选范围） */
    val auxiliaryKeywords: List<PickupWordRule> = DEFAULT_AUXILIARY_KEYWORDS.map { PickupWordRule(word = it) },
    /** 品牌表：name + 识别关键词 */
    val brands: List<PickupBrandRule> = emptyList(),
) {
    fun enabledWords(type: WordType): List<String> = when (type) {
        WordType.FOOD -> foodKeywords.filter { it.enabled && it.word.isNotBlank() }.map { it.word }
        WordType.EXPRESS -> expressKeywords.filter { it.enabled && it.word.isNotBlank() }.map { it.word }
        WordType.AUXILIARY -> auxiliaryKeywords.filter { it.enabled && it.word.isNotBlank() }.map { it.word }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("schema_version", schemaVersion)
        put("name", name)
        put("updated_at", updatedAt)
        put("food_keywords", JSONArray().apply { foodKeywords.forEach { put(it.toJson()) } })
        put("express_keywords", JSONArray().apply { expressKeywords.forEach { put(it.toJson()) } })
        put("auxiliary_keywords", JSONArray().apply { auxiliaryKeywords.forEach { put(it.toJson()) } })
        put("brands", JSONArray().apply { brands.forEach { put(it.toJson()) } })
    }

    companion object {
        const val SCHEMA_VERSION = 1

        /** 默认餐食定位词（与 demo 对齐） */
        val DEFAULT_FOOD_KEYWORDS = listOf(
            "取餐码", "取餐号", "取餐口令", "取餐编号",
            "取餐代码", "取单号", "取单码", "取餐序号", "取餐号码",
            "取茶码", "取茶号", "取茶口令", "啡快口令", "取单口令",
            "准备", "准备完毕", "已准备", "喜欢您再来",
        )

        /** 默认快递定位词 */
        val DEFAULT_EXPRESS_KEYWORDS = listOf(
            "取件码", "取件口令", "请凭", "丰巢", "驿站", "快递柜", "取货码",
            "到站包裹", "待取", "本人",
        )

        /** 默认辅助锚点词（仅扩大框选范围，非定位词汇） */
        val DEFAULT_AUXILIARY_KEYWORDS = listOf(
            "快递超市", "驿站地址", "自提地址", "收货地址", "取件地址",
        )

        fun fromJson(json: JSONObject): PickupWordRulePack = PickupWordRulePack(
            schemaVersion = json.optInt("schema_version", SCHEMA_VERSION),
            name = json.optString("name", "词汇识别规则"),
            updatedAt = json.optLong("updated_at", System.currentTimeMillis()),
            foodKeywords = PickupWordRule.list(json.optJSONArray("food_keywords"))
                .ifEmpty { DEFAULT_FOOD_KEYWORDS.map { PickupWordRule(word = it) } },
            expressKeywords = PickupWordRule.list(json.optJSONArray("express_keywords"))
                .ifEmpty { DEFAULT_EXPRESS_KEYWORDS.map { PickupWordRule(word = it) } },
            auxiliaryKeywords = PickupWordRule.list(json.optJSONArray("auxiliary_keywords"))
                .ifEmpty { DEFAULT_AUXILIARY_KEYWORDS.map { PickupWordRule(word = it) } },
            brands = PickupBrandRule.list(json.optJSONArray("brands")),
        )

        fun empty(): PickupWordRulePack = PickupWordRulePack(
            foodKeywords = DEFAULT_FOOD_KEYWORDS.map { PickupWordRule(word = it) },
            expressKeywords = DEFAULT_EXPRESS_KEYWORDS.map { PickupWordRule(word = it) },
            auxiliaryKeywords = DEFAULT_AUXILIARY_KEYWORDS.map { PickupWordRule(word = it) },
        )
    }
}

/** 品牌规则：名称 + 关键词（识别品牌）。 */
data class PickupBrandRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "新品牌",
    val enabled: Boolean = true,
    val keywords: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("enabled", enabled)
        put("keywords", JSONArray(keywords))
    }

    companion object {
        fun fromJson(json: JSONObject): PickupBrandRule = PickupBrandRule(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = json.optString("name", "新品牌"),
            enabled = json.optBoolean("enabled", true),
            keywords = json.optJSONArray("keywords").stringListArray(),
        )

        fun list(jsonArray: JSONArray?): List<PickupBrandRule> =
            if (jsonArray == null) emptyList()
            else (0 until jsonArray.length()).mapNotNull { jsonArray.optJSONObject(it)?.let(::fromJson) }
    }
}

/** 词汇类型。 */
enum class WordType(val displayName: String) {
    FOOD("餐食定位词"),
    EXPRESS("快递定位词"),
    AUXILIARY("辅助锚点词");

    companion object {
        fun fromJson(value: String): WordType =
            entries.firstOrNull { it.name.equals(value, true) } ?: FOOD
    }
}

private fun JSONArray?.stringListArray(): List<String> = if (this == null) emptyList() else
    (0 until length()).mapNotNull { optString(it).trim().takeIf(String::isNotBlank) }

/** 词汇规则存取（独立于 SimpleRulePack 的文件）。 */
class PickupWordRuleRepository(private val context: Context) {
    private val rulesDir = File(context.filesDir, "rules").apply { mkdirs() }
    private val ruleFile = File(rulesDir, "pickup_word_rules.json")

    @Volatile
    private var cache: PickupWordRulePack? = null

    suspend fun load(): PickupWordRulePack = withContext(Dispatchers.IO) {
        cache ?: synchronized(this@PickupWordRuleRepository) {
            cache ?: runCatching {
                if (!ruleFile.exists()) {
                    PickupWordRulePack.empty().also { saveBlocking(it) }
                } else {
                    PickupWordRulePack.fromJson(JSONObject(ruleFile.readText()))
                }
            }.getOrElse {
                Log.e(TAG, "词汇规则包损坏，回退默认", it)
                PickupWordRulePack.empty().also(::saveBlocking)
            }.also { cache = it }
        }.also { pack ->
            Log.d(
                "RecognitionMonitor",
                "词汇规则加载: 餐食=${pack.foodKeywords.size} 快递=${pack.expressKeywords.size} 辅助=${pack.auxiliaryKeywords.size}",
            )
        }
    }

    suspend fun save(pack: PickupWordRulePack) = withContext(Dispatchers.IO) {
        val draft = pack.copy(updatedAt = System.currentTimeMillis())
        saveBlocking(draft)
        cache = draft
    }

    fun current(): PickupWordRulePack? = cache

    suspend fun importJson(text: String): Result<PickupWordRulePack> = withContext(Dispatchers.IO) {
        runCatching {
            val imported = PickupWordRulePack.fromJson(JSONObject(text))
            saveBlocking(imported)
            cache = imported
            imported
        }
    }

    fun exportJson(pack: PickupWordRulePack): String = pack.toJson().toString(2)

    private fun saveBlocking(pack: PickupWordRulePack) = synchronized(FILE_LOCK) {
        ruleFile.writeText(pack.toJson().toString(2))
    }

    companion object {
        private const val TAG = "PickupWordRuleRepo"
        private val FILE_LOCK = Any()
    }
}