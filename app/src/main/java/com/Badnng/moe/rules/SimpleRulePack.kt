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
                val literal = template.substring(cursor, match.range.first)
                appendLiteral(literal)
                val codeTag = literal.trimEnd()
                // 快递短信里同一个位置会写「取件码 / 取货码 / 提货码」，
                // 模板统一按「取件码」书写即可，不必为每种叫法各写一条规则。
                if (codeTag.endsWith("取件码")) {
                    replace(length - "取件码".length, length, "(?:取件码|取货码|提货码)")
                }
                // OCR 的逻辑行会用空格连接；模板无需手动为每个换行补空格。
                // 冒号紧随标签是快递短信的常见写法（“取件码：1-2-3456”），统一允许：
                // 用户写模板时不必自己区分“取件码”和“取件码：”两种写法。
                append("\\s*[:：]?\\s*")
                // 快递短信既有“凭 6-8-1234 取件”，也有“请凭取件码A-2-7261前往”：
                // 「凭」后面可以再带一次取件码标签，模板里不必为此单写一条规则。
                if (codeTag.endsWith("凭")) append("(?:取件码|取货码|提货码)?\\s*")
                when (match.groupValues[1]) {
                    "code" -> appendCodePattern(match.groupValues[2], codeDigitsOnly)
                    "location" -> append("(.{1,100}?)")
                    // 用于吞掉链接、时间等会变化但不需要保存的片段，不创建捕获组。
                    "any" -> append("(?:.*?)")
                }
                cursor = match.range.last + 1
                // 码值后面常跟「取件码 868642 到美晨通讯」这样的空格 + 动作词，
                // 允许空格被吃掉（动作词由 extractCode 事后修剪），否则会因为
                // 捕获组吞掉空格而让这里的 \s* 无内容可匹配，整条模板直接不命中。
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
            "alnum" -> CODE_CHARACTER_CLASS
            else -> if (codeDigitsOnly) "[\\p{N}]" else CODE_CHARACTER_CLASS
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

    /**
     * 取件码允许出现的字符：字母（排除汉字）、数字，以及常见分隔符。
     *
     * 排除汉字是必需的：短信里「请凭取件码取件」这类句子在「取件码」后面并没有真正的码值，
     * 若允许汉字，模板会一路吞掉后面的中文，把「取件」当成取件码。
     *
     * 两个坑都在这一行里：
     * 1) 交集必须自己带一层方括号。写成 `[\p{L}&&[^\p{IsHan}]\p{N}]` 时，
     *    `&&` 会把它后面的并集一起吸进交集，结果只剩纯字母（数字与 `-` 全部不匹配）。
     * 2) 汉字用显式区间而不是 `\p{IsHan}`。Android 的 java.util.regex 走 ICU，
     *    对 `\p{IsHan}` 这种脚本名支持并不可靠，一旦解析失败 PatternSyntaxException
     *    会被上层的 runCatching 吞掉，表现成「模板莫名不命中」。
     */
    internal const val CODE_CHARACTER_CLASS =
        "[[\\p{L}&&[^\\u4E00-\\u9FFF\\u3400-\\u4DBF\\uF900-\\uFAFF\\u3000-\\u303F\\uFF00-\\uFFEF]]\\p{N}.#_-]"

    /**
     * 取件码后面紧跟着的常见动词/助词。
     *
     * 短信原文形如「凭取件码868642到美晨通讯第1组…」，码值字符类把汉字挡在外面之后，
     * 捕获到的会是「868642到」，需要把尾部的「到」剪掉。只收录真正会紧贴码值出现的词，
     * 避免误伤取件码本身（例如顺丰的「SF8899」不需要动）。
     */
    private val TRAILING_ACTION_WORDS = listOf(
        "请", "到", "取", "领", "凭", "在", "于", "去", "至", "往", "为", "的",
    )

    /**
     * 剪掉码值末尾的动作词。
     *
     * 循环处理是为了兼容「838642到请」这类叠词写法；同时至少保留一位字符，
     * 以防整串都是动作词时把码值清空。
     */
    internal fun trimTrailingActionWords(value: String): String {
        var result = value
        while (result.length > 1) {
            val hit = TRAILING_ACTION_WORDS.firstOrNull { result.endsWith(it) } ?: break
            result = result.dropLast(hit.length)
        }
        return result
    }
}

class SimpleRuleRepository(private val context: Context) {
    private val rulesDir = File(context.filesDir, "rules").apply { mkdirs() }
    private val ruleFile = File(rulesDir, "simple_rules_v4.json")
    private val migrationMarker = File(rulesDir, ".simple_rules_v4_migrated")

    suspend fun load(): SimpleRulePack = withContext(Dispatchers.IO) {
        migrateOnce()
        if (!ruleFile.exists()) saveBlocking(BuiltInPack.fallback(context))
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

    /**
     * 追加「内置规则包」：把内置品牌并入当前规则，不删除用户已建的品牌与模板。
     *
     * 之所以是追加而不是覆盖：内置包的价值是提供开箱可用的取件码模板（尤其是
     * SMS/NOTIFICATION/TEXT 三种来源），而用户自建的品牌往往是按自己的快递公司写的。
     * 覆盖会连带丢掉这些成果，也可能让识别立刻退化。
     */
    suspend fun importBuiltInPack(): Result<SimpleRulePack> = withContext(Dispatchers.IO) {
        runCatching {
            val builtIn = BuiltInPack.load(context)
            require(builtIn.brands.isNotEmpty()) { "内置规则包为空" }
            val current = runCatching {
                SimpleRulePack.fromJson(JSONObject(ruleFile.readText()), validate = false)
            }.getOrElse { SimpleRulePack.empty() }
            val existingIds = current.brands.mapTo(mutableSetOf()) { it.id }
            val existingNames = current.brands.mapTo(mutableSetOf()) { it.name }
            val merged = builtIn.brands.map { brand ->
                // 模板 id 也要去重：内置包早期版本里「凭码取件」「凭码领取」共用过同一个 id，
                // 直接并入会留下重复 id 的模板，规则中心里编辑时容易串。
                val seenTemplateIds = current.brands
                    .flatMap { it.templates }
                    .mapTo(mutableSetOf()) { it.id }
                val templates = brand.templates.map { template ->
                    if (template.id in seenTemplateIds) {
                        template.copy(id = java.util.UUID.randomUUID().toString())
                            .also { seenTemplateIds += it.id }
                    } else {
                        template.also { seenTemplateIds += it.id }
                    }
                }
                brand.copy(
                    id = brand.id.takeIf { it !in existingIds } ?: java.util.UUID.randomUUID().toString(),
                    name = brand.name.takeIf { it !in existingNames } ?: "${brand.name}（内置）",
                    templates = templates,
                )
            }
            val result = current.copy(brands = current.brands + merged)
            saveBlocking(result)
            SimpleRuleRuntime.replace(result)
            result
        }
    }

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

object BuiltInPack {
    private const val TAG = "SimpleRuleRepository"
    private const val ASSET_NAME = "default_rules.json"

    /** 读取内置规则包（assets/default_rules.json）。读取失败时回退为空包，不阻断首次启动。 */
    fun load(context: Context): SimpleRulePack = runCatching {
        val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        SimpleRulePack.fromJson(JSONObject(json), validate = false)
    }.getOrElse {
        Log.e(TAG, "内置规则包读取失败，回退为空包", it)
        SimpleRulePack.empty()
    }

    /** 首次运行播种：内置包为空时退回空包，避免写出一份 0 品牌的规则文件。 */
    fun fallback(context: Context): SimpleRulePack =
        load(context).takeIf { it.brands.isNotEmpty() } ?: SimpleRulePack.empty()
}

/**
 * 内置标签抓码：不依赖用户规则包的兜底。
 *
 * 用户规则包里的模板可以选择适用来源（图片/文本/短信/通知），一旦某个品牌只配了图片，
 * 短信与通知就会「品牌命中但无模板可用」而返回零结果。这组标签规则内置在代码里，
 * 只要文本出现标签就抓后面紧邻的码值，因此短信、通知、划词都能开箱可用且不被用户改坏。
 *
 * 刻意不抓「没有标签的裸码」：那样会把验证码、电话号码、订单号一并当成取件码。
 */
private val BUILT_IN_EXPRESS_LABELS: SimpleRulePack by lazy {
    val code = "{{code:alnum:1-40}}"
    fun template(id: String, name: String, text: String) = SimpleTemplateRule(
        id = id,
        name = name,
        template = text,
        sources = setOf(SimpleRuleSource.TEXT, SimpleRuleSource.SMS, SimpleRuleSource.NOTIFICATION),
    )
    SimpleRulePack(
        name = "内置快递标签",
        brands = listOf(
            SimpleBrandRule(
                id = "builtin-express-label",
                category = SimpleRuleCategory.EXPRESS,
                name = "快递",
                keywords = listOf("取件码", "取货码", "提货码"),
                templates = listOf(
                    template("builtin-express-label-t1", "取件码标签", "取件码$code"),
                    template("builtin-express-label-t2", "取货码标签", "取货码$code"),
                    template("builtin-express-label-t3", "提货码标签", "提货码$code"),
                    // 「请凭5-5-2531领取」这类没有「取件码」三字的写法。码值限定 4 位以上，
                    // 是为了不把「凭取件码取件」里的「取件码」当成取件码。
                    template("builtin-express-label-t4", "凭码领取", "凭{{code:alnum:4-40}}领取"),
                    template("builtin-express-label-t5", "凭码取件", "凭{{code:alnum:4-40}}取件"),
                ),
            ),
        ),
    )
}

/** 用内置标签规则扫描全文，返回去重后的命中。 */
private fun builtInLabelMatches(text: String): List<SimpleRuleMatch> {
    val brand = BUILT_IN_EXPRESS_LABELS.brands.first()
    val results = mutableListOf<SimpleRuleMatch>()
    for (rule in brand.templates) {
        if (!rule.enabled) continue
        for (code in extractLabeledCodes(text, rule.template)) {
            results += SimpleRuleMatch(
                code = code,
                location = null,
                brand = brand.name,
                category = brand.category,
                brandRuleId = brand.id,
                templateRuleId = rule.id,
                templateRuleName = rule.name,
            )
        }
    }
    return results.distinctBy { it.code }
}

/**
 * 从 [template] 取标签文字，在 [text] 里找标签，然后逐字符吃掉后面的码值。
 *
 * 刻意不用正则：码值边界是「遇到第一个非字母数字字符」，逐字符扫描比正则更直观，
 * 也避免依赖平台正则引擎对字符类交集等写法的支持差异。
 * 标签后的冒号、空格先跳过；紧跟在码值后面的动作词（到/请/领…）会被剪掉。
 */
private fun extractLabeledCodes(text: String, template: String): List<String> {
    val placeholderAt = template.indexOf("{{")
    if (placeholderAt <= 0) return emptyList()
    val label = template.substring(0, placeholderAt).trim()
    if (label.isEmpty()) return emptyList()
    // 标签变体：写「取件码」时同时认「取货码」「提货码」。
    val labels = if (label.endsWith("取件码")) {
        listOf(label, label.dropLast(3) + "取货码", label.dropLast(3) + "提货码")
    } else {
        listOf(label)
    }
    val minLength = Regex("\\{\\{code:alnum:(\\d+)-\\d+\\}\\}").find(template)?.groupValues?.get(1)?.toIntOrNull() ?: 1

    val codes = mutableListOf<String>()
    for (candidate in labels) {
        var index = text.indexOf(candidate)
        while (index >= 0) {
            var cursor = index + candidate.length
            // 标签与码值之间允许冒号/空格。
            while (cursor < text.length && (text[cursor].isWhitespace() || text[cursor] == ':' || text[cursor] == '：')) cursor++
            val start = cursor
            while (cursor < text.length && isCodeCharacter(text[cursor])) cursor++
            val raw = text.substring(start, cursor)
            val code = SimpleRuleTemplateCompiler.trimTrailingActionWords(raw)
                .trim(',', '，', ':', '：', '.', '。')
            if (code.length >= minLength && code.any(Char::isDigit)) codes += code
            index = text.indexOf(candidate, index + candidate.length)
        }
    }
    return codes
}

/** 码值允许出现的字符：非表意文字的字母或数字，以及常见分隔符。 */
private fun isCodeCharacter(c: Char): Boolean =
    c.isLetterOrDigit() && !Character.isIdeographic(c.code) || c in ".#_-"

object SimpleRuleRuntime {
    private const val LOG_TAG = "RecognitionMonitor"

    /** 同一品牌内，匹配起点字符距离小于该值即视为同一处的重复命中（收敛多框；取值保守，避免误并多件） */
    private const val MIN_MATCH_GAP_CHARS = 6

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
                    // 短信里码值后面常常紧跟动作词：「取件码868642到美晨通讯」。
                    // 码值字符类排除了汉字，所以这里拿到的会是「868642到」这类结果，
                    // 需要把尾部的动作词剪掉——它显然不是取件码的一部分。
                    val code = match.groups[codeGroupIndex]?.value
                        ?.trim()
                        ?.trim(',', '，', ':', '：')
                        ?.let(SimpleRuleTemplateCompiler::trimTrailingActionWords)
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
        // 收敛：同品牌且匹配起点几乎重合的多次命中视为同一处，只保留最靠前的一个（不设数量上限）。
        val ordered = results.sortedBy { it.first }
        val pruned = mutableListOf<Pair<Int, SimpleRuleMatch>>()
        for (entry in ordered) {
            val duplicated = pruned.any { kept ->
                kept.second.brand == entry.second.brand &&
                    kotlin.math.abs(kept.first - entry.first) < MIN_MATCH_GAP_CHARS
            }
            if (duplicated) {
                logDebug("模板结果收敛: brand=${entry.second.brand}, code=${entry.second.code}, reason=同品牌同一处重复命中")
                continue
            }
            pruned += entry
        }
        val uniqueResults = pruned.map { it.second }.distinctBy { it.code }
        logDebug("规则识别结束: matchedBrands=${matchedBrands.size}, 候选=${ordered.size}, results=${uniqueResults.size}")
        if (uniqueResults.isNotEmpty()) return uniqueResults

        // 内置兜底：规则包是用户可改的，但「取件码」这类标签抓码不该依赖用户配置。
        // 只要文本里出现标签，就直接取标签后面紧邻的码值，品牌没命中、或命中了却没配
        // 当前来源（短信/通知/文本）都能生效——这正是「短信/通知单独开规则」想要的效果。
        val fallback = builtInLabelMatches(normalized)
        if (fallback.isEmpty()) {
            logDebug("内置标签兜底: 无命中, 文本长度=${normalized.length}")
        } else {
            logDebug("内置标签兜底命中: ${fallback.joinToString { "${it.code}(${it.brand})" }}")
        }
        return fallback
    }
}

private fun JSONArray?.stringList(): List<String> = if (this == null) emptyList() else
    (0 until length()).mapNotNull { optString(it).trim().takeIf(String::isNotBlank) }

private fun JSONArray?.objectList(): List<JSONObject> = if (this == null) emptyList() else
    (0 until length()).mapNotNull { optJSONObject(it) }
