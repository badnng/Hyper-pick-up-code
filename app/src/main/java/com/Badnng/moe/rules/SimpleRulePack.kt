package com.Badnng.moe.rules

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 简化规则包：整个规则包只按 餐食 / 饮品 / 快递 分类，分类内再按品牌组织。
 * 不存在权重、规则源合并或隐式全局词库。
 */
enum class SimpleRuleCategory(val displayName: String, val resultType: String) {
    FOOD("餐食", "餐食"),
    DRINK("饮品", "饮品"),
    EXPRESS("快递", "快递");

    companion object {
        fun fromJson(value: String): SimpleRuleCategory =
            entries.firstOrNull { it.name.equals(value, true) } ?: FOOD
    }
}

enum class SimpleRuleSource(val displayName: String) {
    IMAGE("图片"),
    TEXT("文本"),
    SMS("短信"),
    NOTIFICATION("通知");

    companion object {
        fun fromJson(value: String): SimpleRuleSource? = entries.firstOrNull { it.name.equals(value, true) }
    }
}

data class SimpleTemplateRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "识别模板",
    val enabled: Boolean = true,
    val template: String = "{{code}}",
    val codeDigitsOnly: Boolean = false,
    val excludedWords: List<String> = emptyList(),
    val sources: Set<SimpleRuleSource> = emptySet(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("enabled", enabled)
        put("template", template)
        put("code_digits_only", codeDigitsOnly)
        put("excluded_words", JSONArray(excludedWords))
        put("sources", JSONArray(sources.map { it.name }))
    }

    companion object {
        fun fromJson(json: JSONObject): SimpleTemplateRule = SimpleTemplateRule(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = json.optString("name", "识别模板"),
            enabled = json.optBoolean("enabled", true),
            template = json.optString("template", "{{code}}"),
            codeDigitsOnly = json.optBoolean("code_digits_only", false),
            excludedWords = json.optJSONArray("excluded_words").stringList(),
            sources = json.optJSONArray("sources").stringList().mapNotNull(SimpleRuleSource::fromJson).toSet(),
        )
    }
}

data class SimpleBrandRule(
    val id: String = UUID.randomUUID().toString(),
    val category: SimpleRuleCategory,
    val name: String = "新品牌",
    val enabled: Boolean = true,
    val keywords: List<String> = emptyList(),
    val packageNames: List<String> = emptyList(),
    val qrPatterns: List<String> = emptyList(),
    val templates: List<SimpleTemplateRule> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("category", category.name)
        put("name", name)
        put("enabled", enabled)
        put("keywords", JSONArray(keywords))
        put("package_names", JSONArray(packageNames))
        put("qr_patterns", JSONArray(qrPatterns))
        put("templates", JSONArray().apply { templates.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(json: JSONObject): SimpleBrandRule = SimpleBrandRule(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            category = SimpleRuleCategory.fromJson(json.optString("category")),
            name = json.optString("name", "新品牌"),
            enabled = json.optBoolean("enabled", true),
            keywords = json.optJSONArray("keywords").stringList(),
            packageNames = json.optJSONArray("package_names").stringList(),
            qrPatterns = json.optJSONArray("qr_patterns").stringList(),
            templates = json.optJSONArray("templates").objectList().map(SimpleTemplateRule::fromJson),
        )
    }
}

data class SimpleRulePack(
    val schemaVersion: Int = SCHEMA_VERSION,
    val name: String = "我的识别规则",
    val updatedAt: Long = System.currentTimeMillis(),
    val brands: List<SimpleBrandRule> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("schema_version", schemaVersion)
        put("name", name)
        put("updated_at", updatedAt)
        put("brands", JSONArray().apply { brands.forEach { put(it.toJson()) } })
    }

    companion object {
        const val SCHEMA_VERSION = 4

        fun empty(): SimpleRulePack = SimpleRulePack(brands = emptyList())

        fun fromJson(json: JSONObject, validate: Boolean = true): SimpleRulePack {
            require(json.optInt("schema_version") == SCHEMA_VERSION) {
                "仅支持规则格式 v$SCHEMA_VERSION"
            }
            val brands = json.optJSONArray("brands").objectList().map(SimpleBrandRule::fromJson)
            if (validate) {
                require(brands.map { it.id }.distinct().size == brands.size) { "品牌规则 ID 重复" }
                brands.forEach { brand ->
                    require(brand.name.isNotBlank()) { "品牌名称不能为空" }
                    require(brand.templates.map { it.id }.distinct().size == brand.templates.size) {
                        "${brand.name} 的模板 ID 重复"
                    }
                    brand.templates.forEach { template ->
                        require(SimpleRuleTemplateCompiler.hasCodePlaceholder(template.template)) {
                            "${brand.name} / ${template.name} 必须包含 {{code}} 或带参数的 {{code:...}}"
                        }
                        SimpleRuleTemplateCompiler.compile(template.template, template.codeDigitsOnly)
                    }
                    brand.qrPatterns.forEach { Regex(it) }
                }
            }
            return SimpleRulePack(
                schemaVersion = SCHEMA_VERSION,
                name = json.optString("name", "我的识别规则"),
                updatedAt = json.optLong("updated_at", System.currentTimeMillis()),
                brands = brands,
            )
        }
    }
}

data class SimpleRuleMatch(
    val code: String,
    val location: String?,
    val brand: String,
    val category: SimpleRuleCategory,
    val brandRuleId: String,
    val templateRuleId: String,
    val templateRuleName: String,
)

object SimpleRuleTemplateCompiler {
    private val placeholder = Regex("\\{\\{(code|location|any)(?::([^{}]+))?\\}\\}")

    fun compile(template: String, codeDigitsOnly: Boolean = false): Regex {
        require(countCodePlaceholders(template) == 1) { "模板必须且只能包含一个 {{code}} 或带参数的 {{code:...}}" }
        require(countLocationPlaceholders(template) <= 1) { "模板最多包含一个 {{location}}" }
        val pattern = buildString {
            append("(?is)")
            var cursor = 0
            placeholder.findAll(template).forEach { match ->
                appendLiteral(template.substring(cursor, match.range.first))
                // OCR 的逻辑行会用空格连接；模板无需手动为每个换行补空格。
                append("\\s*")
                when (match.groupValues[1]) {
                    "code" -> appendCodePattern(match.groupValues[2], codeDigitsOnly)
                    "location" -> append("(.{1,100}?)")
                    // 用于吞掉链接、时间等会变化但不需要保存的片段，不创建捕获组。
                    "any" -> append("(?:.*?)")
                }
                cursor = match.range.last + 1
                append("\\s*")
            }
            appendLiteral(template.substring(cursor))
        }
        return Regex(pattern)
    }

    fun findMatches(
        regex: Regex,
        input: String,
        preferNestedMatch: Boolean = true,
    ): List<MatchResult> {
        if (!preferNestedMatch) return regex.findAll(input).toList()
        val overlapping = mutableListOf<MatchResult>()
        var startIndex = 0
        while (startIndex <= input.length) {
            val match = regex.find(input, startIndex) ?: break
            overlapping += match
            startIndex = match.range.first + 1
        }
        // {{any}} 可能让较早的码跨过很长内容后才碰到结束锚点。
        // 若一个更靠后的匹配完全落在该范围内，则保留更局部的匹配，避免选中前一单的码。
        return overlapping.filter { candidate ->
            overlapping.none { nested ->
                nested !== candidate &&
                    nested.range.first > candidate.range.first &&
                    nested.range.last <= candidate.range.last &&
                    nested.range.count() < candidate.range.count()
            }
        }
    }

    fun hasCodePlaceholder(template: String): Boolean = countCodePlaceholders(template) > 0

    fun countCodePlaceholders(template: String): Int =
        placeholder.findAll(template).count { it.groupValues[1] == "code" }

    fun countLocationPlaceholders(template: String): Int =
        placeholder.findAll(template).count { it.groupValues[1] == "location" }

    fun codeGroupIndex(template: String): Int = captureGroupIndex(template, "code")
        ?: error("模板不包含 {{code}}")

    fun locationGroupIndex(template: String): Int? = captureGroupIndex(template, "location")

    private fun captureGroupIndex(template: String, target: String): Int? {
        var captureIndex = 0
        placeholder.findAll(template).forEach { match ->
            val kind = match.groupValues[1]
            if (kind == "code" || kind == "location") {
                captureIndex += 1
                if (kind == target) return captureIndex
            }
        }
        return null
    }

    private fun StringBuilder.appendCodePattern(optionsText: String, codeDigitsOnly: Boolean) {
        val options = optionsText.split(':').map(String::trim).filter(String::isNotBlank)
        val mode = options.firstOrNull()?.lowercase()?.takeIf { it == "digits" || it == "alnum" }
        require(options.isEmpty() || mode != null) {
            "{{code}} 参数仅支持 digits 或 alnum，例如 {{code:digits:4}}"
        }
        require(options.size <= 2) { "{{code}} 参数过多" }
        val length = options.getOrNull(1)?.let(::parseLengthRange) ?: (1..40)
        val characterClass = when (mode) {
            "digits" -> "[\\p{N}]"
            "alnum" -> "[\\p{L}\\p{N}.#_-]"
            else -> if (codeDigitsOnly) "[\\p{N}]" else "[\\p{L}\\p{N}.#_-]"
        }
        val quantifier = if (length.first == length.last) {
            "{${length.first}}"
        } else {
            "{${length.first},${length.last}}?"
        }
        append("(").append(characterClass).append(quantifier).append(")")
    }

    private fun parseLengthRange(value: String): IntRange {
        val exact = value.toIntOrNull()
        if (exact != null) {
            require(exact in 1..40) { "{{code}} 长度必须在 1 到 40 之间" }
            return exact..exact
        }
        val parts = value.split('-', limit = 2)
        require(parts.size == 2) { "{{code}} 长度应写成 4 或 4-8" }
        val min = parts[0].toIntOrNull()
        val max = parts[1].toIntOrNull()
        require(min != null && max != null && min in 1..40 && max in min..40) {
            "{{code}} 长度范围必须在 1 到 40 之间"
        }
        return min..max
    }

    private fun StringBuilder.appendLiteral(value: String) {
        if (value.isEmpty()) return
        val parts = value.split(Regex("\\s+"))
        parts.forEachIndexed { index, part ->
            if (index > 0) append("\\s*")
            append(Regex.escape(part))
        }
    }
}

class SimpleRuleRepository(private val context: Context) {
    private val rulesDir = File(context.filesDir, "rules").apply { mkdirs() }
    private val ruleFile = File(rulesDir, "simple_rules_v4.json")
    private val migrationMarker = File(rulesDir, ".simple_rules_v4_migrated")

    suspend fun load(): SimpleRulePack = withContext(Dispatchers.IO) {
        migrateOnce()
        if (!ruleFile.exists()) saveBlocking(SimpleRulePack.empty())
        runCatching { SimpleRulePack.fromJson(JSONObject(ruleFile.readText()), validate = false) }
            .getOrElse {
                Log.e(TAG, "规则包损坏，回退为空规则包", it)
                SimpleRulePack.empty().also(::saveBlocking)
            }
    }

    suspend fun save(pack: SimpleRulePack) = withContext(Dispatchers.IO) {
        val draft = pack.copy(updatedAt = System.currentTimeMillis())
        saveBlocking(draft)
        SimpleRuleRuntime.replace(draft)
    }

    suspend fun importJson(text: String): Result<SimpleRulePack> = withContext(Dispatchers.IO) {
        runCatching {
            val imported = SimpleRulePack.fromJson(JSONObject(text), validate = true)
            saveBlocking(imported)
            SimpleRuleRuntime.replace(imported)
            imported
        }
    }

    fun exportJson(pack: SimpleRulePack): String = pack.toJson().toString(2)

    private fun saveBlocking(pack: SimpleRulePack) = synchronized(FILE_LOCK) {
        val temp = File(rulesDir, "simple_rules_v4.json.tmp")
        temp.writeText(pack.toJson().toString(2))
        try {
            java.nio.file.Files.move(
                temp.toPath(),
                ruleFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(
                temp.toPath(),
                ruleFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        }
        Unit
    }

    private fun migrateOnce() {
        if (migrationMarker.exists()) return
        listOf(
            "rules.json",
            "local_custom_rules.json",
            "online_cache.json",
            "online_cache_meta.json",
            "online_sources.json",
            "config.json",
        ).forEach { File(rulesDir, it).delete() }
        File(rulesDir, "local_custom").deleteRecursively()
        saveBlocking(SimpleRulePack.empty())
        migrationMarker.writeText(SimpleRulePack.SCHEMA_VERSION.toString())
    }

    private companion object {
        const val TAG = "SimpleRuleRepository"
        val FILE_LOCK = Any()
    }
}

object SimpleRuleRuntime {
    private const val LOG_TAG = "RecognitionMonitor"

    private fun logDebug(message: String) {
        runCatching { Log.d(LOG_TAG, message) }
    }

    private fun logWarn(message: String) {
        runCatching { Log.w(LOG_TAG, message) }
    }
    private val mutex = Mutex()
    @Volatile private var loaded = false
    @Volatile private var pack: SimpleRulePack = SimpleRulePack.empty()

    suspend fun ensureLoaded(context: Context): SimpleRulePack {
        if (loaded) return pack
        mutex.withLock {
            if (!loaded) {
                pack = SimpleRuleRepository(context.applicationContext).load()
                loaded = true
            }
        }
        return pack
    }

    fun current(): SimpleRulePack = pack

    internal fun replace(newPack: SimpleRulePack) {
        pack = newPack
        loaded = true
    }

    suspend fun recognize(
        context: Context,
        rawText: String,
        source: SimpleRuleSource,
        sourcePackage: String? = null,
        qrData: String? = null,
    ): List<SimpleRuleMatch> {
        ensureLoaded(context)
        return recognizeCurrent(rawText, source, sourcePackage, qrData)
    }

    /** 同步匹配入口，供划词/手动输入等本身不是 suspend 的入口使用。 */
    fun recognizeCurrent(
        rawText: String,
        source: SimpleRuleSource,
        sourcePackage: String? = null,
        qrData: String? = null,
    ): List<SimpleRuleMatch> {
        val currentPack = pack
        val normalized = rawText.lineSequence().map(String::trim).filter(String::isNotBlank).joinToString(" ")
        logDebug("规则包: name=${currentPack.name}, schema=${currentPack.schemaVersion}, brands=${currentPack.brands.size}, source=${source.name}")
        if (normalized.isBlank()) {
            logDebug("规则未执行: OCR 原文为空")
            return emptyList()
        }

        val builtInLuckinQrHit = LuckinQrRule.matches(qrData)
        val orderedBrands = if (builtInLuckinQrHit) {
            // 内置二维码只允许进入瑞幸自己的模板，避免 OCR 中出现其他品牌词时
            // 错用其他品牌模板提取码值；二维码本身绝不作为取餐码。
            currentPack.brands.filter { it.name == LuckinQrRule.BRAND_NAME }
        } else {
            currentPack.brands
        }
        if (builtInLuckinQrHit && orderedBrands.isEmpty()) {
            logDebug("内置瑞幸二维码命中，但当前规则包没有启用名称为瑞幸的品牌模板")
        }
        val matchedBrands = mutableListOf<Pair<SimpleBrandRule, String>>()
        for (candidate in orderedBrands) {
            if (!candidate.enabled) {
                logDebug("品牌规则跳过: name=${candidate.name}, id=${candidate.id}, reason=已停用")
                continue
            }
            val packageHit = sourcePackage?.takeIf { pkg -> candidate.packageNames.any { it.equals(pkg, true) } }
            val qrHit = qrData?.let { qr ->
                candidate.qrPatterns.firstOrNull { pattern ->
                    runCatching { Regex(pattern).containsMatchIn(qr) }.getOrDefault(false)
                }
            }
            val keywordHit = (candidate.keywords + candidate.name)
                .filter(String::isNotBlank)
                .firstOrNull { normalized.contains(it, ignoreCase = true) }
            val reason = when {
                builtInLuckinQrHit && candidate.name == LuckinQrRule.BRAND_NAME -> "内置瑞幸二维码规则"
                packageHit != null -> "来源包名:$packageHit"
                qrHit != null -> "二维码正则:$qrHit"
                keywordHit != null -> "关键词:$keywordHit"
                else -> null
            }
            if (reason != null) {
                matchedBrands += candidate to reason
                logDebug(
                    "品牌规则命中: name=${candidate.name}, id=${candidate.id}, category=${candidate.category.displayName}, reason=$reason, templates=${candidate.templates.size}",
                )
            } else {
                logDebug(
                    "品牌规则未命中: name=${candidate.name}, id=${candidate.id}, keywords=${candidate.keywords.joinToString("|")}",
                )
            }
        }

        if (matchedBrands.isEmpty()) {
            logDebug("品牌锁定失败: 没有品牌规则命中")
            return emptyList()
        }

        val results = mutableListOf<Pair<Int, SimpleRuleMatch>>()
        for ((matchedBrand, _) in matchedBrands) {
            val brandResultStart = results.size
            for (rule in matchedBrand.templates) {
                if (!rule.enabled) {
                    logDebug("模板跳过: brand=${matchedBrand.name}, name=${rule.name}, id=${rule.id}, reason=已停用")
                    continue
                }
                if (rule.sources.isNotEmpty() && source !in rule.sources) {
                    logDebug("模板跳过: brand=${matchedBrand.name}, name=${rule.name}, id=${rule.id}, reason=来源不适用, configured=${rule.sources.joinToString { it.name }}, actual=${source.name}")
                    continue
                }
                val excludedWord = rule.excludedWords.firstOrNull { normalized.contains(it, ignoreCase = true) }
                if (excludedWord != null) {
                    logDebug("模板跳过: brand=${matchedBrand.name}, name=${rule.name}, id=${rule.id}, reason=命中排除词:$excludedWord")
                    continue
                }
                val compiled = runCatching {
                    SimpleRuleTemplateCompiler.compile(rule.template, rule.codeDigitsOnly)
                }
                    .onFailure { logWarn("模板编译失败: brand=${matchedBrand.name}, name=${rule.name}, id=${rule.id}, template=${rule.template}, errorType=${it::class.java.name}, error=${it.message ?: "无消息"}") }
                    .getOrNull() ?: continue
                val matches = SimpleRuleTemplateCompiler.findMatches(
                    regex = compiled,
                    input = normalized,
                    // {{location}} 需要保留锚点前的完整内容；局部化会把省略号末尾的“.”误当成位置。
                    preferNestedMatch = SimpleRuleTemplateCompiler.countLocationPlaceholders(rule.template) == 0,
                )
                if (matches.isEmpty()) {
                    logDebug("模板未命中: brand=${matchedBrand.name}, name=${rule.name}, id=${rule.id}, template=${rule.template}")
                    continue
                }
                val codeGroupIndex = SimpleRuleTemplateCompiler.codeGroupIndex(rule.template)
                val locationGroupIndex = SimpleRuleTemplateCompiler.locationGroupIndex(rule.template)
                for ((matchIndex, match) in matches.withIndex()) {
                    val code = match.groups[codeGroupIndex]?.value?.trim()?.trim(',', '，', ':', '：')
                        ?.takeIf(String::isNotBlank)
                    if (code == null) {
                        logDebug("模板结果丢弃: brand=${matchedBrand.name}, name=${rule.name}, id=${rule.id}, match=${matchIndex + 1}/${matches.size}, reason=code为空")
                        continue
                    }
                    val location = locationGroupIndex
                        ?.let { match.groups[it]?.value?.trim() }
                        ?.takeIf { value -> value.isNotBlank() && value.any(Char::isLetterOrDigit) }
                    logDebug(
                        "模板规则命中: brand=${matchedBrand.name}, brandId=${matchedBrand.id}, name=${rule.name}, templateId=${rule.id}, match=${matchIndex + 1}/${matches.size}, template=${rule.template}, codeDigitsOnly=${rule.codeDigitsOnly}, code=$code, location=${location ?: "无"}",
                    )
                    results += match.range.first to SimpleRuleMatch(
                        code = code,
                        location = location,
                        brand = matchedBrand.name,
                        category = matchedBrand.category,
                        brandRuleId = matchedBrand.id,
                        templateRuleId = rule.id,
                        templateRuleName = rule.name,
                    )
                }
            }
            val brandResultCount = results.size - brandResultStart
            if (brandResultCount == 0) {
                logDebug("品牌规则执行结束: brand=${matchedBrand.name}, 没有模板产出结果")
            } else {
                logDebug("品牌规则执行结束: brand=${matchedBrand.name}, results=$brandResultCount")
            }
        }
        val uniqueResults = results
            .sortedBy { it.first }
            .map { it.second }
            .distinctBy { it.code }
        logDebug("规则识别结束: matchedBrands=${matchedBrands.size}, results=${uniqueResults.size}")
        return uniqueResults
    }
}

private fun JSONArray?.stringList(): List<String> = if (this == null) emptyList() else
    (0 until length()).mapNotNull { optString(it).trim().takeIf(String::isNotBlank) }

private fun JSONArray?.objectList(): List<JSONObject> = if (this == null) emptyList() else
    (0 until length()).mapNotNull { optJSONObject(it) }
