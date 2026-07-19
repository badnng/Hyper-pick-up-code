package com.Badnng.moe.rules

import org.json.JSONArray
import org.json.JSONObject
import com.Badnng.moe.helper.AppLogger

// ─────────── Online Rule Source ───────────

data class OnlineRuleSource(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val enabled: Boolean = true,
    val updateIntervalMinutes: Int = 1440,
    val lastUpdated: Long = 0,
    val lastEtag: String? = null,
    val lastModified: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("url", url)
        put("enabled", enabled)
        put("update_interval_minutes", updateIntervalMinutes)
        android.util.Log.d("RuleModels", "OnlineRuleSource.toJson: name=$name updateIntervalMinutes=$updateIntervalMinutes")
        AppLogger.update("RuleModels OnlineRuleSource.toJson: name=$name, interval=${updateIntervalMinutes}min")
        put("last_updated", lastUpdated)
        put("last_etag", lastEtag ?: "")
        put("last_modified", lastModified ?: "")
    }

    companion object {
        fun fromJson(json: JSONObject): OnlineRuleSource {
            // 优先读分钟字段，兼容旧的小时字段
            val minutes = if (json.has("update_interval_minutes")) {
                json.optInt("update_interval_minutes", 1440)
            } else {
                json.optInt("update_interval_hours", 24) * 60
            }
            android.util.Log.d("RuleModels", "OnlineRuleSource.fromJson: name=${json.optString("name","")} has_minutes=${json.has("update_interval_minutes")} minutes=$minutes raw=${json.opt("update_interval_minutes")}")
            AppLogger.update("RuleModels OnlineRuleSource.fromJson: name=${json.optString("name","")}, minutes=$minutes")
            return OnlineRuleSource(
                id = json.optString("id", ""),
                name = json.optString("name", ""),
                url = json.optString("url", ""),
                enabled = json.optBoolean("enabled", true),
                updateIntervalMinutes = minutes,
                lastUpdated = json.optLong("last_updated", 0),
                lastEtag = json.optString("last_etag", "").ifBlank { null },
                lastModified = json.optString("last_modified", "").ifBlank { null }
            )
        }
    }
}

enum class RuleSourceType {
    LOCAL,
    ONLINE
}

data class LocalRuleSourceConfig(
    val id: String = "local",
    val name: String = "本地规则",
    val displayName: String = "",  // 导入的JSON文件名
    val enabled: Boolean = true,
    val isCustomized: Boolean = false,
    val lastImportTime: Long = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("display_name", displayName)
        put("enabled", enabled)
        put("is_customized", isCustomized)
        put("last_import_time", lastImportTime)
    }

    companion object {
        fun fromJson(json: JSONObject): LocalRuleSourceConfig = LocalRuleSourceConfig(
            id = json.optString("id", "local"),
            name = json.optString("name", "本地规则"),
            displayName = json.optString("display_name", ""),
            enabled = json.optBoolean("enabled", true),
            isCustomized = json.optBoolean("is_customized", false),
            lastImportTime = json.optLong("last_import_time", 0)
        )
    }
}

data class LocalCustomSource(
    val id: String = "",
    val displayName: String = "",
    val jsonPackage: String = "",
    val enabled: Boolean = true,
    val lastImportTime: Long = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("display_name", displayName)
        put("json_package", jsonPackage)
        put("enabled", enabled)
        put("last_import_time", lastImportTime)
    }

    companion object {
        fun fromJson(json: JSONObject): LocalCustomSource = LocalCustomSource(
            id = json.optString("id", ""),
            displayName = json.optString("display_name", ""),
            jsonPackage = json.optString("json_package", ""),
            enabled = json.optBoolean("enabled", true),
            lastImportTime = json.optLong("last_import_time", 0)
        )
    }
}

data class RuleSystemConfig(
    val activeSourceId: String = "local",
    val localConfig: LocalRuleSourceConfig = LocalRuleSourceConfig(),
    val localCustomSources: List<LocalCustomSource> = emptyList(),
    val onlineSources: List<OnlineRuleSource> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("active_source_id", activeSourceId)
        put("local_config", localConfig.toJson())
        val customArr = JSONArray()
        localCustomSources.forEach { customArr.put(it.toJson()) }
        put("local_custom_sources", customArr)
        val arr = JSONArray()
        onlineSources.forEach { arr.put(it.toJson()) }
        put("online_sources", arr)
    }

    companion object {
        fun fromJson(json: JSONObject): RuleSystemConfig {
            val activeId = json.optString("active_source_id", "local")
            val onlineCount = json.optJSONArray("online_sources")?.length() ?: 0
            AppLogger.update("RuleModels RuleSystemConfig.fromJson: activeSource=$activeId, onlineSources=$onlineCount")
            return RuleSystemConfig(
            activeSourceId = activeId,
            localConfig = json.optJSONObject("local_config")?.let {
                LocalRuleSourceConfig.fromJson(it)
            } ?: LocalRuleSourceConfig(),
            localCustomSources = json.optJSONArray("local_custom_sources")?.let { arr ->
                (0 until arr.length()).map { LocalCustomSource.fromJson(arr.getJSONObject(it)) }
            } ?: emptyList(),
            onlineSources = json.optJSONArray("online_sources")?.let { arr ->
                (0 until arr.length()).map { OnlineRuleSource.fromJson(arr.getJSONObject(it)) }
            } ?: emptyList()
            )
        }
    }
}

// ─────────── Top Level ───────────

data class RecognitionParams(
    val patternPrioritySkip: Int = 3,
    val brandContextWindow: Int = 100,
    val codeContextWindow: Int = 200
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("pattern_priority_skip", patternPrioritySkip)
        put("brand_context_window", brandContextWindow)
        put("code_context_window", codeContextWindow)
    }

    companion object {
        fun fromJson(json: JSONObject): RecognitionParams = RecognitionParams(
            patternPrioritySkip = json.optInt("pattern_priority_skip", 3),
            brandContextWindow = json.optInt("brand_context_window", 100),
            codeContextWindow = json.optInt("code_context_window", 200)
        )
    }
}

data class RecognitionRules(
    val schemaVersion: Int = 1,
    val appVersion: String = "",
    val updatedAt: String = "",
    val description: String = "",
    val jsonPackage: String = "",
    val brands: BrandConfig = BrandConfig(),
    val codeExtraction: CodeExtractionConfig = CodeExtractionConfig(),
    val categoryDetection: CategoryDetectionConfig = CategoryDetectionConfig(),
    val homepageDetection: HomepageDetectionConfig = HomepageDetectionConfig(),
    val textCleaning: TextCleaningConfig = TextCleaningConfig(),
    val validation: ValidationConfig = ValidationConfig(),
    val scoring: ScoringConfig = ScoringConfig(),
    val pickupLocation: PickupLocationConfig = PickupLocationConfig(),
    val recognitionParams: RecognitionParams = RecognitionParams()
) {
    /** 原始 JSON 文本，用于导出时不丢失原始格式 */
    @Transient
    var rawJson: String? = null

    /** 与内置规则合并，缺失的部分自动补全 */
    fun mergeWithBuiltIn(builtIn: RecognitionRules): RecognitionRules = copy(
        brands = if (brands.drink.isEmpty() && brands.food.isEmpty() && brands.express.isEmpty()) builtIn.brands else brands,
        codeExtraction = codeExtraction.mergeWithBuiltIn(builtIn.codeExtraction),
        categoryDetection = categoryDetection,
        homepageDetection = homepageDetection,
        textCleaning = textCleaning,
        validation = validation,
        scoring = scoring,
        pickupLocation = pickupLocation
    ).also { it.rawJson = this.rawJson }
    fun toJson(): JSONObject = JSONObject().apply {
        put("schema_version", schemaVersion)
        put("app_version", appVersion)
        put("updated_at", updatedAt)
        put("description", description)
        put("json_package", jsonPackage)
        put("jsonpackage", jsonPackage)
        put("brands", brands.toJson())
        put("code_extraction", codeExtraction.toJson())
        put("category_detection", categoryDetection.toJson())
        put("homepage_detection", homepageDetection.toJson())
        put("text_cleaning", textCleaning.toJson())
        put("validation", validation.toJson())
        put("scoring", scoring.toJson())
        put("pickup_location", pickupLocation.toJson())
        put("recognition_params", recognitionParams.toJson())
    }

    companion object {
        fun fromJson(json: JSONObject, rawJson: String? = null): RecognitionRules {
            val schema = json.optInt("schema_version", 1)
            val pkg = json.optString("json_package", "").ifEmpty { json.optString("jsonpackage", "") }
            AppLogger.update("RuleModels RecognitionRules.fromJson: schema=$schema, package=$pkg, brands=${json.optJSONObject("brands") != null}")
            return RecognitionRules(
                schemaVersion = schema,
                appVersion = json.optString("app_version", ""),
                updatedAt = json.optString("updated_at", ""),
                description = json.optString("description", ""),
                jsonPackage = pkg,
                brands = json.optJSONObject("brands")?.let { BrandConfig.fromJson(it) } ?: BrandConfig(),
                codeExtraction = json.optJSONObject("code_extraction")?.let { CodeExtractionConfig.fromJson(it) } ?: CodeExtractionConfig(),
                categoryDetection = json.optJSONObject("category_detection")?.let { CategoryDetectionConfig.fromJson(it) } ?: CategoryDetectionConfig(),
                homepageDetection = json.optJSONObject("homepage_detection")?.let { HomepageDetectionConfig.fromJson(it) } ?: HomepageDetectionConfig(),
                textCleaning = json.optJSONObject("text_cleaning")?.let { TextCleaningConfig.fromJson(it) } ?: TextCleaningConfig(),
                validation = json.optJSONObject("validation")?.let { ValidationConfig.fromJson(it) } ?: ValidationConfig(),
                scoring = json.optJSONObject("scoring")?.let { ScoringConfig.fromJson(it) } ?: ScoringConfig(),
                pickupLocation = json.optJSONObject("pickup_location")?.let { PickupLocationConfig.fromJson(it) } ?: PickupLocationConfig(),
                recognitionParams = json.optJSONObject("recognition_params")?.let { RecognitionParams.fromJson(it) } ?: RecognitionParams()
            ).also { it.rawJson = rawJson }
        }
    }
}

// ─────────── Brands ───────────

data class BrandConfig(
    val drink: List<BrandDefinition> = emptyList(),
    val food: List<BrandDefinition> = emptyList(),
    val express: List<BrandDefinition> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("drink", JSONArray(drink.map { it.toJson() }))
        put("food", JSONArray(food.map { it.toJson() }))
        put("express", JSONArray(express.map { it.toJson() }))
    }

    companion object {
        fun fromJson(json: JSONObject): BrandConfig = BrandConfig(
            drink = json.optJSONArray("drink")?.let { arr -> (0 until arr.length()).map { BrandDefinition.fromJson(arr.getJSONObject(it)) } } ?: emptyList(),
            food = json.optJSONArray("food")?.let { arr -> (0 until arr.length()).map { BrandDefinition.fromJson(arr.getJSONObject(it)) } } ?: emptyList(),
            express = json.optJSONArray("express")?.let { arr -> (0 until arr.length()).map { BrandDefinition.fromJson(arr.getJSONObject(it)) } } ?: emptyList()
        )
    }
}

data class BrandDefinition(
    val name: String,
    val aliases: List<String> = emptyList(),
    val category: String = "餐食",
    val packageName: String? = null,
    val keywords: List<String> = emptyList(),
    val qrPattern: String? = null,
    val scoringOverrides: Map<String, Int> = emptyMap(),
    val specialRules: Map<String, Boolean> = emptyMap(),
    val enabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        if (aliases.isNotEmpty()) put("aliases", JSONArray(aliases))
        put("category", category)
        packageName?.let { put("package_name", it) }
        if (keywords.isNotEmpty()) put("keywords", JSONArray(keywords))
        qrPattern?.let { put("qr_pattern", it) }
        if (scoringOverrides.isNotEmpty()) {
            val obj = JSONObject()
            scoringOverrides.forEach { (k, v) -> obj.put(k, v) }
            put("scoring_overrides", obj)
        }
        if (specialRules.isNotEmpty()) {
            val obj = JSONObject()
            specialRules.forEach { (k, v) -> obj.put(k, v) }
            put("special_rules", obj)
        }
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(json: JSONObject): BrandDefinition = BrandDefinition(
            name = json.getString("name"),
            aliases = json.optJSONArray("aliases")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
            category = json.optString("category", "餐食"),
            packageName = json.optString("package_name", null),
            keywords = json.optJSONArray("keywords")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
            qrPattern = json.opt("qr_pattern") as? String,
            scoringOverrides = json.optJSONObject("scoring_overrides")?.let { obj ->
                obj.keys().asSequence().associateWith { obj.getInt(it) }
            } ?: emptyMap(),
            specialRules = json.optJSONObject("special_rules")?.let { obj ->
                obj.keys().asSequence().associateWith { obj.getBoolean(it) }
            } ?: emptyMap(),
            enabled = json.optBoolean("enabled", true)
        )
    }
}

// ─────────── Code Extraction ───────────

data class CodeExtractionConfig(
    val express: ExpressExtraction = ExpressExtraction(),
    val food: FoodExtraction = FoodExtraction()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("express", express.toJson())
        put("food", food.toJson())
    }

    fun mergeWithBuiltIn(builtIn: CodeExtractionConfig): CodeExtractionConfig = copy(
        express = express.mergeWithBuiltIn(builtIn.express),
        food = food.mergeWithBuiltIn(builtIn.food)
    )

    companion object {
        fun fromJson(json: JSONObject): CodeExtractionConfig = CodeExtractionConfig(
            express = json.optJSONObject("express")?.let { ExpressExtraction.fromJson(it) } ?: ExpressExtraction(),
            food = json.optJSONObject("food")?.let { FoodExtraction.fromJson(it) } ?: FoodExtraction()
        )
    }
}

data class ExpressExtraction(
    val triggerKeywords: List<String> = listOf("取件码", "取性码", "请凭", "靖凭"),
    val patterns: List<ExtractionPattern> = emptyList(),
    val fallbackPattern: FallbackPattern? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("trigger_keywords", JSONArray(triggerKeywords))
        put("patterns", JSONArray(patterns.map { it.toJson() }))
        fallbackPattern?.let { put("fallback_pattern", it.toJson()) }
    }

    fun mergeWithBuiltIn(builtIn: ExpressExtraction): ExpressExtraction = copy(
        patterns = if (patterns.isEmpty()) builtIn.patterns else patterns,
        fallbackPattern = fallbackPattern ?: builtIn.fallbackPattern
    )

    companion object {
        fun fromJson(json: JSONObject): ExpressExtraction = ExpressExtraction(
            triggerKeywords = json.optJSONArray("trigger_keywords")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: listOf("取件码", "取性码", "请凭", "靖凭"),
            patterns = json.optJSONArray("patterns")?.let { arr -> (0 until arr.length()).map { ExtractionPattern.fromJson(arr.getJSONObject(it)) } } ?: emptyList(),
            fallbackPattern = json.optJSONObject("fallback_pattern")?.let { FallbackPattern.fromJson(it) }
        )
    }
}

data class FoodExtraction(
    val triggerKeywords: List<String> = listOf("取餐", "取茶", "验证码", "券码", "订单", "准备完毕", "领取", "取单", "取货", "取養"),
    val hintKeywords: List<String> = listOf("取单码", "取单号", "取餐号", "取餐码", "取茶号", "取货码", "券码", "订单号", "取性码", "取養号", "待取餐", "当前订单"),
    val queueKeywords: List<String> = listOf("叫号", "取号", "过号", "排队", "迎宾台", "到店就餐", "还需等待", "桌安排", "预约号码", "卡座", "排号", "取餐号"),
    val queueThreshold: Int = 2,
    val genericCodePattern: String = "[A-Z0-9]{3,10}",
    val standaloneCodePattern: String = "^[A-Z0-9]{3,10}$",
    val patterns: FoodPatterns = FoodPatterns(),
    val starbucksPattern: StarbucksPattern? = null,
    val nearbyBlockDistance: Int = 400,
    val keywordContextRange: Int = 6,
    val shortCodeLength: Int = 3
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("trigger_keywords", JSONArray(triggerKeywords))
        put("hint_keywords", JSONArray(hintKeywords))
        put("queue_keywords", JSONArray(queueKeywords))
        put("queue_threshold", queueThreshold)
        put("generic_code_pattern", genericCodePattern)
        put("standalone_code_pattern", standaloneCodePattern)
        put("patterns", patterns.toJson())
        starbucksPattern?.let { put("starbucks_pattern", it.toJson()) }
        put("nearby_block_distance", nearbyBlockDistance)
        put("keyword_context_range", keywordContextRange)
        put("short_code_length", shortCodeLength)
    }

    fun mergeWithBuiltIn(builtIn: FoodExtraction): FoodExtraction = copy(
        patterns = patterns.mergeWithBuiltIn(builtIn.patterns),
        starbucksPattern = starbucksPattern ?: builtIn.starbucksPattern
    )

    companion object {
        fun fromJson(json: JSONObject): FoodExtraction = FoodExtraction(
            triggerKeywords = json.optJSONArray("trigger_keywords")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: listOf("取餐", "取茶", "验证码", "券码", "订单", "准备完毕", "领取", "取单", "取货", "取養"),
            hintKeywords = json.optJSONArray("hint_keywords")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: listOf("取单码", "取单号", "取餐号", "取餐码", "取茶号", "取货码", "券码", "订单号", "取性码", "取養号", "待取餐", "当前订单"),
            queueKeywords = json.optJSONArray("queue_keywords")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: listOf("叫号", "取号", "过号", "排队", "迎宾台", "到店就餐", "还需等待", "桌安排"),
            queueThreshold = json.optInt("queue_threshold", 2),
            genericCodePattern = json.optString("generic_code_pattern", "[A-Z0-9]{3,10}"),
            standaloneCodePattern = json.optString("standalone_code_pattern", "^[A-Z0-9]{3,10}$"),
            patterns = json.optJSONObject("patterns")?.let { FoodPatterns.fromJson(it) } ?: FoodPatterns(),
            starbucksPattern = json.optJSONObject("starbucks_pattern")?.let { StarbucksPattern.fromJson(it) },
            nearbyBlockDistance = json.optInt("nearby_block_distance", 400),
            keywordContextRange = json.optInt("keyword_context_range", 6),
            shortCodeLength = json.optInt("short_code_length", 3)
        )
    }
}

data class FoodPatterns(
    val queuePatterns: List<QueuePattern> = emptyList(),
    val hashCodePattern: ExtractionPattern? = null,
    val sloganPattern: ExtractionPattern? = null,
    val keywordPattern: KeywordPattern? = null,
    val fallbackPattern: ExtractionPattern? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("queue_patterns", JSONArray(queuePatterns.map { it.toJson() }))
        hashCodePattern?.let { put("hash_code_pattern", it.toJson()) }
        sloganPattern?.let { put("slogan_pattern", it.toJson()) }
        keywordPattern?.let { put("keyword_pattern", it.toJson()) }
        fallbackPattern?.let { put("fallback_pattern", it.toJson()) }
    }

    fun mergeWithBuiltIn(builtIn: FoodPatterns): FoodPatterns = copy(
        queuePatterns = if (queuePatterns.isEmpty()) builtIn.queuePatterns else queuePatterns,
        hashCodePattern = hashCodePattern ?: builtIn.hashCodePattern,
        sloganPattern = sloganPattern ?: builtIn.sloganPattern,
        keywordPattern = keywordPattern ?: builtIn.keywordPattern,
        fallbackPattern = fallbackPattern ?: builtIn.fallbackPattern
    )

    companion object {
        fun fromJson(json: JSONObject): FoodPatterns = FoodPatterns(
            queuePatterns = json.optJSONArray("queue_patterns")?.let { arr -> (0 until arr.length()).map { QueuePattern.fromJson(arr.getJSONObject(it)) } } ?: emptyList(),
            hashCodePattern = json.optJSONObject("hash_code_pattern")?.let { ExtractionPattern.fromJson(it) },
            sloganPattern = json.optJSONObject("slogan_pattern")?.let { ExtractionPattern.fromJson(it) },
            keywordPattern = json.optJSONObject("keyword_pattern")?.let { KeywordPattern.fromJson(it) },
            fallbackPattern = json.optJSONObject("fallback_pattern")?.let { ExtractionPattern.fromJson(it) }
        )
    }
}

data class ExtractionPattern(
    val id: String,
    val regex: String,
    val priority: Int = 5,
    val description: String = "",
    val weightMultiplier: Int = 1,
    val enabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("regex", regex)
        put("priority", priority)
        put("description", description)
        put("weight_multiplier", weightMultiplier)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(json: JSONObject): ExtractionPattern = ExtractionPattern(
            id = json.getString("id"),
            regex = json.getString("regex"),
            priority = json.optInt("priority", 5),
            description = json.optString("description", ""),
            weightMultiplier = json.optInt("weight_multiplier", 1),
            enabled = json.optBoolean("enabled", true)
        )
    }
}

data class QueuePattern(
    val id: String,
    val regex: String,
    val description: String = "",
    val codeLengthMin: Int = 2,
    val codeLengthMax: Int = 5,
    val requireMixed: Boolean = true,
    val enabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("regex", regex)
        put("description", description)
        put("code_length_min", codeLengthMin)
        put("code_length_max", codeLengthMax)
        put("require_mixed", requireMixed)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(json: JSONObject): QueuePattern = QueuePattern(
            id = json.getString("id"),
            regex = json.getString("regex"),
            description = json.optString("description", ""),
            codeLengthMin = json.optInt("code_length_min", 2),
            codeLengthMax = json.optInt("code_length_max", 5),
            requireMixed = json.optBoolean("require_mixed", true),
            enabled = json.optBoolean("enabled", true)
        )
    }
}

data class FallbackPattern(
    val regex: String,
    val requiresTrigger: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("regex", regex)
        put("requires_trigger", requiresTrigger)
    }

    companion object {
        fun fromJson(json: JSONObject): FallbackPattern = FallbackPattern(
            regex = json.getString("regex"),
            requiresTrigger = json.optBoolean("requires_trigger", true)
        )
    }
}

data class KeywordPattern(
    val id: String = "keyword_code",
    val forwardRegex: String = "",
    val reverseRegex: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("forward_regex", forwardRegex)
        put("reverse_regex", reverseRegex)
    }

    companion object {
        fun fromJson(json: JSONObject): KeywordPattern = KeywordPattern(
            id = json.optString("id", "keyword_code"),
            forwardRegex = json.optString("forward_regex", ""),
            reverseRegex = json.optString("reverse_regex", "")
        )
    }
}

data class StarbucksPattern(
    val regex: String = "(\\d{1,3}[.．][\\u4e00-\\u9fa5]{2,10})",
    val triggerKeywords: List<String> = listOf("啡快口令"),
    val triggerBrand: String = "星巴克",
    val description: String = "星巴克啡快口令"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("regex", regex)
        put("trigger_keywords", JSONArray(triggerKeywords))
        put("trigger_brand", triggerBrand)
        put("description", description)
    }

    companion object {
        fun fromJson(json: JSONObject): StarbucksPattern = StarbucksPattern(
            regex = json.optString("regex", "(\\d{1,3}[.．][\\u4e00-\\u9fa5]{2,10})"),
            triggerKeywords = json.optJSONArray("trigger_keywords")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: listOf("啡快口令"),
            triggerBrand = json.optString("trigger_brand", "星巴克"),
            description = json.optString("description", "星巴克啡快口令")
        )
    }
}

// ─────────── Category Detection ───────────

data class CategoryDetectionConfig(
    val defaultCategory: String = "餐食",
    val drinkTriggers: DrinkTriggers = DrinkTriggers(),
    val expressTriggers: ExpressTriggers = ExpressTriggers()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("default_category", defaultCategory)
        put("drink_triggers", drinkTriggers.toJson())
        put("express_triggers", expressTriggers.toJson())
    }

    companion object {
        fun fromJson(json: JSONObject): CategoryDetectionConfig = CategoryDetectionConfig(
            defaultCategory = json.optString("default_category", "餐食"),
            drinkTriggers = json.optJSONObject("drink_triggers")?.let { DrinkTriggers.fromJson(it) } ?: DrinkTriggers(),
            expressTriggers = json.optJSONObject("express_triggers")?.let { ExpressTriggers.fromJson(it) } ?: ExpressTriggers()
        )
    }
}

data class DrinkTriggers(
    val brandBased: Boolean = true,
    val textKeywords: List<String> = listOf("奶茶", "咖啡")
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("brand_based", brandBased)
        put("text_keywords", JSONArray(textKeywords))
    }

    companion object {
        fun fromJson(json: JSONObject): DrinkTriggers = DrinkTriggers(
            brandBased = json.optBoolean("brand_based", true),
            textKeywords = json.optJSONArray("text_keywords")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: listOf("奶茶", "咖啡")
        )
    }
}

data class ExpressTriggers(
    val textKeywords: List<String> = listOf("取件", "取性", "快递", "包裹", "待取件", "丰巢"),
    val brandInText: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("text_keywords", JSONArray(textKeywords))
        put("brand_in_text", brandInText)
    }

    companion object {
        fun fromJson(json: JSONObject): ExpressTriggers = ExpressTriggers(
            textKeywords = json.optJSONArray("text_keywords")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: listOf("取件", "取性", "快递", "包裹", "待取件", "丰巢"),
            brandInText = json.optBoolean("brand_in_text", true)
        )
    }
}

// ─────────── Homepage Detection ───────────

data class HomepageDetectionConfig(
    val keywords: List<String> = listOf("我的", "首页", "会员码", "到店取餐", "点单", "会员", "我的订单"),
    val threshold: Int = 3
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("keywords", JSONArray(keywords))
        put("threshold", threshold)
    }

    companion object {
        fun fromJson(json: JSONObject): HomepageDetectionConfig = HomepageDetectionConfig(
            keywords = json.optJSONArray("keywords")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: listOf("我的", "首页", "会员码", "到店取餐", "点单", "会员", "我的订单"),
            threshold = json.optInt("threshold", 3)
        )
    }
}

// ─────────── Text Cleaning ───────────

data class TextCleaningConfig(
    val datetimePattern: String = "\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}",
    val corrections: List<TextCorrection> = emptyList(),
    val charRemovals: List<String> = listOf("|"),
    val spaceCollapse: String = "(?<=[\\u4e00-\\u9fa5A-Z0-9-])\\s+(?=[\\u4e00-\\u9fa5])",
    val trailingPunctuation: String = "[,，。！!?;？;|\\s]+$"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("datetime_pattern", datetimePattern)
        put("corrections", JSONArray(corrections.map { it.toJson() }))
        put("char_removals", JSONArray(charRemovals))
        put("space_collapse", spaceCollapse)
        put("trailing_punctuation", trailingPunctuation)
    }

    companion object {
        fun fromJson(json: JSONObject): TextCleaningConfig = TextCleaningConfig(
            datetimePattern = json.optString("datetime_pattern", "\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}"),
            corrections = json.optJSONArray("corrections")?.let { arr -> (0 until arr.length()).map { TextCorrection.fromJson(arr.getJSONObject(it)) } } ?: emptyList(),
            charRemovals = json.optJSONArray("char_removals")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: listOf("|"),
            spaceCollapse = json.optString("space_collapse", "(?<=[\\u4e00-\\u9fa5A-Z0-9-])\\s+(?=[\\u4e00-\\u9fa5])"),
            trailingPunctuation = json.optString("trailing_punctuation", "[,，。！!?;？;|\\s]+$")
        )
    }
}

data class TextCorrection(
    val from: String,
    val to: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("from", from)
        put("to", to)
    }

    companion object {
        fun fromJson(json: JSONObject): TextCorrection = TextCorrection(
            from = json.getString("from"),
            to = json.getString("to")
        )
    }
}

// ─────────── Validation ───────────

data class ValidationConfig(
    val expressCode: ExpressValidation = ExpressValidation(),
    val foodCode: FoodValidation = FoodValidation()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("express_code", expressCode.toJson())
        put("food_code", foodCode.toJson())
    }

    companion object {
        fun fromJson(json: JSONObject): ValidationConfig = ValidationConfig(
            expressCode = json.optJSONObject("express_code")?.let { ExpressValidation.fromJson(it) } ?: ExpressValidation(),
            foodCode = json.optJSONObject("food_code")?.let { FoodValidation.fromJson(it) } ?: FoodValidation()
        )
    }
}

data class ExpressValidation(
    val maxLength: Int = 12,
    val maxReverseDistance: Int = 80,
    val maxCodeKeywordGap: Int = 30,
    val codeSeparators: List<String> = listOf("、", "，", ",", " "),
    val rejectAllLetters: Boolean = true,
    val rejectPhonePattern: String = "^1\\d{10}$",
    val rejectYearPrefix: String = "202",
    val rejectYearLength: Int = 4,
    val rejectDatePattern: String = "^(0?[1-9]|1[0-2])-(0?[1-9]|[12]\\d|3[01])$",
    val rejectTimeContexts: List<String> = listOf(":", "："),
    val rejectTimeKeywords: List<String> = listOf("时间", "日期", "时", "分", "秒", "KB/s", "kb/s"),
    val phoneTail: PhoneTailConfig = PhoneTailConfig(),
    val threeSegment: ThreeSegmentConfig? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("max_length", maxLength)
        put("max_reverse_distance", maxReverseDistance)
        put("max_code_keyword_gap", maxCodeKeywordGap)
        put("code_separators", org.json.JSONArray(codeSeparators))
        put("reject_all_letters", rejectAllLetters)
        put("reject_phone_pattern", rejectPhonePattern)
        put("reject_year_prefix", rejectYearPrefix)
        put("reject_year_length", rejectYearLength)
        put("reject_date_pattern", rejectDatePattern)
        put("reject_time_contexts", org.json.JSONArray(rejectTimeContexts))
        put("reject_time_keywords", org.json.JSONArray(rejectTimeKeywords))
        put("phone_tail", phoneTail.toJson())
        threeSegment?.let { put("three_segment", it.toJson()) }
    }

    companion object {
        fun fromJson(json: JSONObject): ExpressValidation = ExpressValidation(
            maxLength = json.optInt("max_length", 12),
            maxReverseDistance = json.optInt("max_reverse_distance", 80),
            maxCodeKeywordGap = json.optInt("max_code_keyword_gap", 30),
            codeSeparators = json.optJSONArray("code_separators")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: listOf("、", "，", ",", " "),
            rejectAllLetters = json.optBoolean("reject_all_letters", true),
            rejectPhonePattern = json.optString("reject_phone_pattern", "^1\\d{10}$"),
            rejectYearPrefix = json.optString("reject_year_prefix", "202"),
            rejectYearLength = json.optInt("reject_year_length", 4),
            rejectDatePattern = json.optString("reject_date_pattern", "^(0?[1-9]|1[0-2])-(0?[1-9]|[12]\\d|3[01])$"),
            rejectTimeContexts = json.optJSONArray("reject_time_contexts")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: listOf(":", "："),
            rejectTimeKeywords = json.optJSONArray("reject_time_keywords")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: listOf("时间", "日期", "时", "分", "秒", "KB/s", "kb/s"),
            phoneTail = json.optJSONObject("phone_tail")?.let { PhoneTailConfig.fromJson(it) } ?: PhoneTailConfig(),
            threeSegment = json.optJSONObject("three_segment")?.let { ThreeSegmentConfig.fromJson(it) }
        )
    }
}

data class ThreeSegmentConfig(
    val pattern: String = "([A-Z0-9]+-[A-Z0-9]+-[A-Z0-9]+)",
    val lastSegmentLength: Int = 4,
    val lastSegmentPattern: String = "[0-9]{4}",
    val description: String = "三段式取件码第三段必须是4位数字"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("pattern", pattern)
        put("last_segment_length", lastSegmentLength)
        put("last_segment_pattern", lastSegmentPattern)
        put("description", description)
    }

    companion object {
        fun fromJson(json: JSONObject): ThreeSegmentConfig = ThreeSegmentConfig(
            pattern = json.optString("pattern", "([A-Z0-9]+-[A-Z0-9]+-[A-Z0-9]+)"),
            lastSegmentLength = json.optInt("last_segment_length", 4),
            lastSegmentPattern = json.optString("last_segment_pattern", "[0-9]{4}"),
            description = json.optString("description", "三段式取件码第三段必须是4位数字")
        )
    }
}

data class PhoneTailConfig(
    val length: Int = 4,
    val digitsOnly: Boolean = true,
    val maskPattern: String = "\\*{2,}",
    val contextKeywords: List<String> = listOf("本人", "手机", "手机号", "电话", "联系", "收件人", "尾号")
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("length", length)
        put("digits_only", digitsOnly)
        put("mask_pattern", maskPattern)
        put("context_keywords", JSONArray(contextKeywords))
    }

    companion object {
        fun fromJson(json: JSONObject): PhoneTailConfig = PhoneTailConfig(
            length = json.optInt("length", 4),
            digitsOnly = json.optBoolean("digits_only", true),
            maskPattern = json.optString("mask_pattern", "\\*{2,}"),
            contextKeywords = json.optJSONArray("context_keywords")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: listOf("本人", "手机", "手机号", "电话", "联系", "收件人", "尾号")
        )
    }
}

data class FoodValidation(
    val rejectYearPrefix: String = "202",
    val rejectYearLength: Int = 4,
    val rejectTimeContexts: List<String> = listOf(":", "/"),
    val rejectTimeKeywords: List<String> = listOf("时间", "日期", "预计获得", "积分"),
    val distractionWords: List<String> = listOf("ml", "g", "元", "¥", "购", "券", "赢", "送", "补贴", "减", "满", "起", "合计", "实付"),
    val distractionRange: Int = 2
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("reject_year_prefix", rejectYearPrefix)
        put("reject_year_length", rejectYearLength)
        put("reject_time_contexts", JSONArray(rejectTimeContexts))
        put("reject_time_keywords", JSONArray(rejectTimeKeywords))
        put("distraction_words", JSONArray(distractionWords))
        put("distraction_range", distractionRange)
    }

    companion object {
        fun fromJson(json: JSONObject): FoodValidation = FoodValidation(
            rejectYearPrefix = json.optString("reject_year_prefix", "202"),
            rejectYearLength = json.optInt("reject_year_length", 4),
            rejectTimeContexts = json.optJSONArray("reject_time_contexts")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: listOf(":", "/"),
            rejectTimeKeywords = json.optJSONArray("reject_time_keywords")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: listOf("时间", "日期", "预计获得", "积分"),
            distractionWords = json.optJSONArray("distraction_words")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: listOf("ml", "g", "元", "¥", "购", "券", "赢", "送", "补贴", "减", "满", "起", "合计", "实付"),
            distractionRange = json.optInt("distraction_range", 2)
        )
    }
}

// ─────────── Scoring ───────────

data class ScoringConfig(
    val brandDetection: BrandScoring = BrandScoring(),
    val expressCode: ExpressScoring = ExpressScoring(),
    val foodCode: FoodScoring = FoodScoring()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("brand_detection", brandDetection.toJson())
        put("express_code", expressCode.toJson())
        put("food_code", foodCode.toJson())
    }

    companion object {
        fun fromJson(json: JSONObject): ScoringConfig = ScoringConfig(
            brandDetection = json.optJSONObject("brand_detection")?.let { BrandScoring.fromJson(it) } ?: BrandScoring(),
            expressCode = json.optJSONObject("express_code")?.let { ExpressScoring.fromJson(it) } ?: ExpressScoring(),
            foodCode = json.optJSONObject("food_code")?.let { FoodScoring.fromJson(it) } ?: FoodScoring()
        )
    }
}

data class BrandScoring(
    val keywordMatchWeight: Int = 4,
    val colonMatchWeight: Int = 1,
    val exactMatchWeight: Int = 15
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("keyword_match_weight", keywordMatchWeight)
        put("colon_match_weight", colonMatchWeight)
        put("exact_match_weight", exactMatchWeight)
    }

    companion object {
        fun fromJson(json: JSONObject): BrandScoring = BrandScoring(
            keywordMatchWeight = json.optInt("keyword_match_weight", 4),
            colonMatchWeight = json.optInt("colon_match_weight", 1),
            exactMatchWeight = json.optInt("exact_match_weight", 15)
        )
    }
}

data class ExpressScoring(
    val baseByBboxWidth: Boolean = true,
    val dashMultiplier: Int = 20,
    val letterMultiplier: Int = 5
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("base_by_bbox_width", baseByBboxWidth)
        put("dash_multiplier", dashMultiplier)
        put("letter_multiplier", letterMultiplier)
    }

    companion object {
        fun fromJson(json: JSONObject): ExpressScoring = ExpressScoring(
            baseByBboxWidth = json.optBoolean("base_by_bbox_width", true),
            dashMultiplier = json.optInt("dash_multiplier", 20),
            letterMultiplier = json.optInt("letter_multiplier", 5)
        )
    }
}

data class FoodScoring(
    val baseByBboxWidth: Boolean = true,
    val brandOverrides: Map<String, BrandFoodScoring> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("base_by_bbox_width", baseByBboxWidth)
        val obj = JSONObject()
        brandOverrides.forEach { (k, v) -> obj.put(k, v.toJson()) }
        put("brand_overrides", obj)
    }

    companion object {
        fun fromJson(json: JSONObject): FoodScoring = FoodScoring(
            baseByBboxWidth = json.optBoolean("base_by_bbox_width", true),
            brandOverrides = json.optJSONObject("brand_overrides")?.let { obj ->
                obj.keys().asSequence().associateWith { BrandFoodScoring.fromJson(obj.getJSONObject(it)) }
            } ?: emptyMap()
        )
    }
}

data class BrandFoodScoring(
    val lengthGte4LetterMultiplier: Int = 1,
    val length5DigitMultiplier: Int = 1,
    val length4DigitMultiplier: Int = 1
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("length_gte_4_letter_multiplier", lengthGte4LetterMultiplier)
        put("length_5_digit_multiplier", length5DigitMultiplier)
        put("length_4_digit_multiplier", length4DigitMultiplier)
    }

    companion object {
        fun fromJson(json: JSONObject): BrandFoodScoring = BrandFoodScoring(
            lengthGte4LetterMultiplier = json.optInt("length_gte_4_letter_multiplier", 1),
            length5DigitMultiplier = json.optInt("length_5_digit_multiplier", 1),
            length4DigitMultiplier = json.optInt("length_4_digit_multiplier", 1)
        )
    }
}

// ─────────── Pickup Location ───────────

data class PickupLocationScoringConfig(
    val targetKeywordBonus: Int = 20,
    val addressBonus: Int = 1000,
    val addressFallbackBonus: Int = 500,
    val addressFallbackMinLength: Int = 8,
    val locWithTargetBonus: Int = 100
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("target_keyword_bonus", targetKeywordBonus)
        put("address_bonus", addressBonus)
        put("address_fallback_bonus", addressFallbackBonus)
        put("address_fallback_min_length", addressFallbackMinLength)
        put("loc_with_target_bonus", locWithTargetBonus)
    }

    companion object {
        fun fromJson(json: JSONObject): PickupLocationScoringConfig = PickupLocationScoringConfig(
            targetKeywordBonus = json.optInt("target_keyword_bonus", 20),
            addressBonus = json.optInt("address_bonus", 1000),
            addressFallbackBonus = json.optInt("address_fallback_bonus", 500),
            addressFallbackMinLength = json.optInt("address_fallback_min_length", 8),
            locWithTargetBonus = json.optInt("loc_with_target_bonus", 100)
        )
    }
}

data class PickupLocationConfig(
    val startKeywords: List<String> = listOf("已到", "已至", "到达", "到了", "在", "于", "己到", "前往", "送到", "前住", "到", "至"),
    val targetKeywords: List<String> = listOf("服务站", "驿站", "菜鸟驿", "菜鸟驿站", "自提点", "快递站", "菜鸟站", "代收点", "代点", "丰巢柜", "快递柜", "智能柜", "门面", "邮政大厅", "大厅"),
    val stopKeywords: List<String> = listOf("领取", "取件", "查看", "请凭", "靖凭", "如有", "如有疑问", "取您的", "复制"),
    val garbagePatterns: List<String> = listOf("代收点(", "代收点（", "\\d{10,}"),
    val proximityThreshold: Int = 400,
    val storeNameKeywords: List<String> = listOf("超市", "便利店", "商店", "小卖部", "驿站", "服务站"),
    val storeNameBonus: Int = 800,
    val storeNameMaxLen: Int = 12,
    val locationPrefixMarkers: List<String> = listOf("学院", "小区", "大厦", "花园", "广场", "商场", "市场", "医院", "学校", "大学", "中学", "小学", "幼儿园", "酒店", "宾馆", "饭店", "餐厅", "超市", "便利店", "驿站", "服务站"),
    val scoring: PickupLocationScoringConfig = PickupLocationScoringConfig()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("start_keywords", JSONArray(startKeywords))
        put("target_keywords", JSONArray(targetKeywords))
        put("stop_keywords", JSONArray(stopKeywords))
        put("garbage_patterns", JSONArray(garbagePatterns))
        put("proximity_threshold", proximityThreshold)
        put("store_name_keywords", JSONArray(storeNameKeywords))
        put("store_name_bonus", storeNameBonus)
        put("store_name_max_len", storeNameMaxLen)
        put("location_prefix_markers", JSONArray(locationPrefixMarkers))
        put("scoring", scoring.toJson())
    }

    companion object {
        fun fromJson(json: JSONObject): PickupLocationConfig = PickupLocationConfig(
            startKeywords = json.optJSONArray("start_keywords")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: listOf("已到", "已至", "到达", "到了", "在", "于", "己到", "前往", "送到", "前住", "到", "至"),
            targetKeywords = json.optJSONArray("target_keywords")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: listOf("服务站", "驿站", "菜鸟驿", "菜鸟驿站", "自提点", "快递站", "菜鸟站", "代收点", "代点", "丰巢柜", "快递柜", "智能柜", "门面", "邮政大厅", "大厅"),
            stopKeywords = json.optJSONArray("stop_keywords")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: listOf("领取", "取件", "查看", "请凭", "靖凭", "如有", "如有疑问", "取您的", "复制"),
            garbagePatterns = json.optJSONArray("garbage_patterns")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: listOf("代收点(", "代收点（", "\\d{10,}"),
            proximityThreshold = json.optInt("proximity_threshold", 400),
            storeNameKeywords = json.optJSONArray("store_name_keywords")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: listOf("超市", "便利店", "商店", "小卖部", "驿站", "服务站"),
            storeNameBonus = json.optInt("store_name_bonus", 800),
            storeNameMaxLen = json.optInt("store_name_max_len", 12),
            locationPrefixMarkers = json.optJSONArray("location_prefix_markers")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: listOf("学院", "小区", "大厦", "花园", "广场", "商场", "市场", "医院", "学校", "大学", "中学", "小学", "幼儿园", "酒店", "宾馆", "饭店", "餐厅", "超市", "便利店", "驿站", "服务站"),
            scoring = json.optJSONObject("scoring")?.let { PickupLocationScoringConfig.fromJson(it) } ?: PickupLocationScoringConfig()
        )
    }
}
