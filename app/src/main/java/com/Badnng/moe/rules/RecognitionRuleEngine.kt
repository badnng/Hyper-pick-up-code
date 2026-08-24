package com.Badnng.moe.rules

import android.content.Context
import android.util.Log
import com.Badnng.moe.helper.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.Result

object RecognitionRuleEngine {

    private const val TAG = "RuleEngine"

    @Volatile
    private var _rules: RecognitionRules = RecognitionRules()
    val rules: RecognitionRules get() = _rules

    @Volatile
    private var activeSourceId: String = "local"
    val currentSourceId: String get() = activeSourceId

    @Volatile
    var isInitialized: Boolean = false
        private set

    private val compiledPatterns = mutableMapOf<String, Regex>()
    private var repository: RuleRepository? = null
    private val initMutex = Mutex()

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext
        initMutex.withLock {
            if (isInitialized) return@withLock
            doInitialize(context)
        }
    }

    private suspend fun doInitialize(context: Context) = withContext(Dispatchers.IO) {
        AppLogger.update("RuleEngine initialize start, activeSourceId=$activeSourceId")
        repository = RuleRepository(context)
        SimpleRuleRuntime.ensureLoaded(context)
        val config = repository!!.loadSystemConfig()
        activeSourceId = config.activeSourceId

        val result = repository!!.loadActiveRules(activeSourceId)
        result.fold(
            onSuccess = { rules ->
                _rules = rules
                precompilePatterns()
                isInitialized = true
                Log.d(TAG, "规则引擎初始化完成，激活源: $activeSourceId, express patterns: ${_rules.codeExtraction.express.patterns.size}, brands: drink=${_rules.brands.drink.size} food=${_rules.brands.food.size} express=${_rules.brands.express.size}")
                AppLogger.update("RuleEngine initialized: source=$activeSourceId, express=${_rules.codeExtraction.express.patterns.size}, brands: drink=${_rules.brands.drink.size} food=${_rules.brands.food.size} express=${_rules.brands.express.size}")
            },
            onFailure = { e ->
                Log.e(TAG, "加载激活规则源失败，回退到本地规则", e)
                AppLogger.update("RuleEngine init failed, fallback to local: ${e.message}")
                activeSourceId = "local"
                _rules = repository!!.loadLocalRules()
                precompilePatterns()
                isInitialized = true
            }
        )
    }

    suspend fun reload(context: Context) = withContext(Dispatchers.IO) {
        AppLogger.update("RuleEngine reload start, activeSourceId=$activeSourceId")
        repository = RuleRepository(context)
        SimpleRuleRuntime.ensureLoaded(context)
        val config = repository!!.loadSystemConfig()
        activeSourceId = config.activeSourceId

        val result = repository!!.loadActiveRules(activeSourceId)
        result.fold(
            onSuccess = { rules ->
                _rules = rules
                precompilePatterns()
                Log.d(TAG, "规则引擎重新加载完成")
                AppLogger.update("RuleEngine reloaded: source=$activeSourceId")
            },
            onFailure = { e ->
                Log.e(TAG, "重新加载失败，使用当前规则", e)
                AppLogger.update("RuleEngine reload failed: ${e.message}")
            }
        )
    }

    private fun precompilePatterns() {
        compiledPatterns.clear()

        _rules.codeExtraction.express.patterns.filter { it.enabled }.forEach { pattern ->
            try {
                compiledPatterns[pattern.id] = Regex(pattern.regex)
            } catch (e: Exception) {
                Log.e(TAG, "编译正则失败 '${pattern.id}': ${e.message}")
            }
        }

        _rules.codeExtraction.express.fallbackPattern?.let {
            try {
                compiledPatterns["express_fallback"] = Regex(it.regex)
            } catch (e: Exception) {
                Log.e(TAG, "编译兜底正则失败: ${e.message}")
            }
        }

        _rules.codeExtraction.food.patterns.queuePatterns.filter { it.enabled }.forEach { pattern ->
            try {
                compiledPatterns[pattern.id] = Regex(pattern.regex)
            } catch (e: Exception) {
                Log.e(TAG, "编译排队正则失败 '${pattern.id}': ${e.message}")
            }
        }

        _rules.codeExtraction.food.patterns.sloganPattern?.let {
            try {
                compiledPatterns[it.id] = Regex(it.regex)
            } catch (e: Exception) {
                Log.e(TAG, "编译口令正则失败: ${e.message}")
            }
        }

        _rules.codeExtraction.food.patterns.hashCodePattern?.let {
            try {
                compiledPatterns[it.id] = Regex(it.regex)
            } catch (e: Exception) {
                Log.e(TAG, "编译井号取餐码正则失败: ${e.message}")
            }
        }

        _rules.codeExtraction.food.patterns.keywordPattern?.let { kp ->
            if (kp.forwardRegex.isNotBlank()) {
                try {
                    compiledPatterns["${kp.id}_forward"] = Regex(kp.forwardRegex)
                } catch (e: Exception) {
                    Log.e(TAG, "编译关键词正向正则失败: ${e.message}")
                }
            }
            if (kp.reverseRegex.isNotBlank()) {
                try {
                    compiledPatterns["${kp.id}_reverse"] = Regex(kp.reverseRegex)
                } catch (e: Exception) {
                    Log.e(TAG, "编译关键词反向正则失败: ${e.message}")
                }
            }
        }

        _rules.codeExtraction.food.patterns.fallbackPattern?.let {
            try {
                compiledPatterns[it.id] = Regex(it.regex)
            } catch (e: Exception) {
                Log.e(TAG, "编译食物兜底正则失败: ${e.message}")
            }
        }

        _rules.codeExtraction.food.starbucksPattern?.let {
            try {
                compiledPatterns["starbucks"] = Regex(it.regex)
            } catch (e: Exception) {
                Log.e(TAG, "编译星巴克正则失败: ${e.message}")
            }
        }

        Log.d(TAG, "预编译完成，共 ${compiledPatterns.size} 个正则")
    }

    fun getCompiledPattern(id: String): Regex? = compiledPatterns[id]

    fun getAllDrinkNames(): List<String> = _rules.brands.drink.filter { it.enabled }.map { it.name }

    fun getAllFoodNames(): List<String> = _rules.brands.food.filter { it.enabled }.map { it.name }

    fun getAllExpressKeywords(): List<String> = _rules.brands.express.filter { it.enabled }
        .flatMap { listOf(it.name) + it.aliases }

    fun getDrinkBrands(): List<BrandDefinition> = _rules.brands.drink.filter { it.enabled }

    fun getFoodBrands(): List<BrandDefinition> = _rules.brands.food.filter { it.enabled }

    fun getExpressBrands(): List<BrandDefinition> = _rules.brands.express.filter { it.enabled }

    fun getAllBrands(): List<BrandDefinition> = getDrinkBrands() + getFoodBrands() + getExpressBrands()

    fun getBrandByPackage(packageName: String): String? {
        return getAllBrands().firstOrNull { it.packageName == packageName }?.name
    }

    fun getBrandByName(name: String): BrandDefinition? {
        return getAllBrands().firstOrNull { it.name == name || it.aliases.contains(name) }
    }

    fun getTextCorrections(): List<Pair<String, String>> {
        return _rules.textCleaning.corrections.map { it.from to it.to }
    }

    fun getHomepageKeywords(): List<String> = _rules.homepageDetection.keywords

    fun getHomepageThreshold(): Int = _rules.homepageDetection.threshold

    fun getExpressTriggerKeywords(): List<String> = _rules.codeExtraction.express.triggerKeywords

    fun getFoodTriggerKeywords(): List<String> = _rules.codeExtraction.food.triggerKeywords

    fun getFoodHintKeywords(): List<String> = _rules.codeExtraction.food.hintKeywords

    fun getQueueKeywords(): List<String> = _rules.codeExtraction.food.queueKeywords

    fun getQueueThreshold(): Int = _rules.codeExtraction.food.queueThreshold

    fun getExpressPatterns(): List<ExtractionPattern> {
        return _rules.codeExtraction.express.patterns.filter { it.enabled }.sortedBy { it.priority }
    }

    fun getQueuePatterns(): List<QueuePattern> {
        return _rules.codeExtraction.food.patterns.queuePatterns.filter { it.enabled }
    }

    suspend fun saveLocalRules(rules: RecognitionRules) {
        repository?.saveLocal(rules)
        _rules = rules
        precompilePatterns()
    }

    suspend fun deleteLocalRules(context: Context) {
        repository?.deleteLocal()
        reload(context)
    }

    suspend fun saveOnlineCache(rules: RecognitionRules, etag: String?, lastModified: String?) {
        repository?.saveOnlineCache(rules, etag, lastModified)
    }

    fun exportToJson(rules: RecognitionRules): String {
        return repository?.exportToJson(rules) ?: rules.toJson().toString(2)
    }

    // ─────────── Online Rule Sources ───────────

    suspend fun loadOnlineSources(): List<OnlineRuleSource> {
        return repository?.loadOnlineSources() ?: emptyList()
    }

    suspend fun saveOnlineSources(sources: List<OnlineRuleSource>) {
        repository?.saveOnlineSources(sources)
    }

    fun importFromJson(json: String): Result<RecognitionRules> {
        return repository?.importFromJson(json) ?: run {
            try {
                val rules = RecognitionRules.fromJson(org.json.JSONObject(json), rawJson = json)
                val validation = RuleValidator.validate(rules)
                if (!validation.isValid) {
                    Result.failure(Exception("规则验证失败: ${validation.errors.joinToString("; ")}"))
                } else {
                    Result.success(rules)
                }
            } catch (e: Exception) {
                Result.failure(Exception("JSON 解析失败: ${e.message}"))
            }
        }
    }

    suspend fun switchActiveSource(sourceId: String, context: Context): Boolean =
        withContext(Dispatchers.IO) {
            val repo = repository ?: RuleRepository(context).also { repository = it }
            val config = repo.loadSystemConfig()

            val isValid = when (sourceId) {
                "builtin" -> true
                "local" -> config.localConfig.enabled
                else -> {
                    if (sourceId.startsWith("local_custom_")) {
                        val id = sourceId.removePrefix("local_custom_")
                        config.localCustomSources.any { it.id == id && it.enabled }
                    } else {
                        config.onlineSources.any { it.id == sourceId && it.enabled }
                    }
                }
            }

            if (!isValid) {
                Log.w(TAG, "规则源不存在或未启用: $sourceId")
                return@withContext false
            }

            val newConfig = config.copy(activeSourceId = sourceId)
            repo.saveSystemConfig(newConfig)

            val result = repo.loadActiveRules(sourceId)
            result.fold(
                onSuccess = { rules ->
                    _rules = rules
                    activeSourceId = sourceId
                    precompilePatterns()
                    Log.d(TAG, "切换规则源成功: $sourceId")
                    true
                },
                onFailure = { e ->
                    Log.e(TAG, "切换规则源失败: $sourceId", e)
                    false
                }
            )
        }

    suspend fun importLocalCustomRules(context: Context, rules: RecognitionRules, displayName: String = "") =
        withContext(Dispatchers.IO) {
            val repo = repository ?: RuleRepository(context).also { repository = it }

            repo.saveLocalCustomRules(rules)

            val config = repo.loadSystemConfig()
            val newLocalConfig = config.localConfig.copy(
                isCustomized = true,
                displayName = displayName,
                lastImportTime = System.currentTimeMillis()
            )
            val newConfig = config.copy(localConfig = newLocalConfig)
            repo.saveSystemConfig(newConfig)

            if (activeSourceId == "local") {
                _rules = repo.loadLocalRules()
                precompilePatterns()
            }

            Log.d(TAG, "导入本地自定义规则成功")
        }

    suspend fun resetLocalDefaultRules(context: Context) = withContext(Dispatchers.IO) {
        val repo = repository ?: RuleRepository(context).also { repository = it }

        repo.deleteLocalCustomRules()

        val config = repo.loadSystemConfig()
        val newLocalConfig = config.localConfig.copy(
            isCustomized = false,
            lastImportTime = 0
        )
        val newConfig = config.copy(localConfig = newLocalConfig)
        repo.saveSystemConfig(newConfig)

        if (activeSourceId == "local") {
            _rules = repo.loadLocalRules()
            precompilePatterns()
        }

        Log.d(TAG, "恢复本地默认规则成功")
    }

    // ─────────── Multiple Local Custom Sources ───────────

    suspend fun importLocalCustomSource(context: Context, rules: RecognitionRules, displayName: String, jsonPackage: String = ""): String =
        withContext(Dispatchers.IO) {
            val repo = repository ?: RuleRepository(context).also { repository = it }
            val id = UUID.randomUUID().toString().take(8)

            repo.saveLocalCustomRulesById(id, rules)

            val config = repo.loadSystemConfig()
            val newSource = LocalCustomSource(
                id = id,
                displayName = displayName,
                jsonPackage = jsonPackage,
                enabled = false,
                lastImportTime = System.currentTimeMillis()
            )
            val newConfig = config.copy(
                localCustomSources = config.localCustomSources + newSource
            )
            repo.saveSystemConfig(newConfig)

            Log.d(TAG, "导入本地自定义规则源成功 [$id]")
            id
        }

    suspend fun overwriteLocalCustomSource(context: Context, existingId: String, rules: RecognitionRules) =
        withContext(Dispatchers.IO) {
            val repo = repository ?: RuleRepository(context).also { repository = it }

            repo.saveLocalCustomRulesById(existingId, rules)

            val config = repo.loadSystemConfig()
            val newSources = config.localCustomSources.map {
                if (it.id == existingId) it.copy(lastImportTime = System.currentTimeMillis()) else it
            }
            repo.saveSystemConfig(config.copy(localCustomSources = newSources))

            if (activeSourceId == "local_custom_$existingId") {
                _rules = rules
                precompilePatterns()
            }

            Log.d(TAG, "覆盖本地自定义规则源成功 [$existingId]")
        }

    suspend fun findExistingSourceByPackage(context: Context, jsonPackage: String): LocalCustomSource? =
        withContext(Dispatchers.IO) {
            if (jsonPackage.isBlank()) return@withContext null
            val repo = repository ?: RuleRepository(context).also { repository = it }
            val config = repo.loadSystemConfig()
            config.localCustomSources.firstOrNull { it.jsonPackage == jsonPackage }
        }

    suspend fun deleteLocalCustomSource(context: Context, sourceId: String) =
        withContext(Dispatchers.IO) {
            val repo = repository ?: RuleRepository(context).also { repository = it }

            repo.deleteLocalCustomRulesById(sourceId)

            val config = repo.loadSystemConfig()
            val wasActive = config.activeSourceId == "local_custom_$sourceId"
            val newActiveId = if (wasActive) "builtin" else config.activeSourceId
            // 如果删除的是当前激活的源，关闭所有自定义源的开关
            val newCustomSources = if (wasActive) {
                config.localCustomSources.filter { it.id != sourceId }.map { it.copy(enabled = false) }
            } else {
                config.localCustomSources.filter { it.id != sourceId }
            }
            val newConfig = config.copy(
                localCustomSources = newCustomSources,
                activeSourceId = newActiveId
            )
            repo.saveSystemConfig(newConfig)

            if (wasActive) {
                _rules = repo.loadBuiltInRules()
                activeSourceId = "builtin"
                precompilePatterns()
            }

            Log.d(TAG, "删除本地自定义规则源成功 [$sourceId]")
        }

    suspend fun toggleLocalCustomSource(context: Context, sourceId: String, enabled: Boolean) =
        withContext(Dispatchers.IO) {
            val repo = repository ?: RuleRepository(context).also { repository = it }
            val config = repo.loadSystemConfig()
            val newSources = config.localCustomSources.map {
                if (it.id == sourceId) it.copy(enabled = enabled) else it
            }
            val newActiveId = if (!enabled && config.activeSourceId == "local_custom_$sourceId") "builtin" else config.activeSourceId
            val newConfig = config.copy(localCustomSources = newSources, activeSourceId = newActiveId)
            repo.saveSystemConfig(newConfig)

            if (!enabled && activeSourceId == "local_custom_$sourceId") {
                _rules = repo.loadBuiltInRules()
                activeSourceId = "builtin"
                precompilePatterns()
            }
        }

    suspend fun activateExclusiveSource(context: Context, sourceId: String) =
        withContext(Dispatchers.IO) {
            val repo = repository ?: RuleRepository(context).also { repository = it }
            val config = repo.loadSystemConfig()

            // 关闭所有自定义源和在线源
            val newCustomSources = config.localCustomSources.map { it.copy(enabled = false) }
            val newOnlineSources = config.onlineSources.map { it.copy(enabled = false) }
            val newLocalConfig = config.localConfig.copy(enabled = false)

            // 只开启目标源
            val finalCustomSources = if (sourceId.startsWith("local_custom_")) {
                val id = sourceId.removePrefix("local_custom_")
                newCustomSources.map { if (it.id == id) it.copy(enabled = true) else it }
            } else newCustomSources

            val finalOnlineSources = if (!sourceId.startsWith("local_custom_") && sourceId != "builtin" && sourceId != "local") {
                newOnlineSources.map { if (it.id == sourceId) it.copy(enabled = true) else it }
            } else newOnlineSources

            val finalLocalConfig = if (sourceId == "local") newLocalConfig.copy(enabled = true) else newLocalConfig

            val newConfig = config.copy(
                localConfig = finalLocalConfig,
                localCustomSources = finalCustomSources,
                onlineSources = finalOnlineSources,
                activeSourceId = sourceId
            )
            repo.saveSystemConfig(newConfig)

            val result = repo.loadActiveRules(sourceId)
            result.fold(
                onSuccess = { rules ->
                    _rules = rules
                    activeSourceId = sourceId
                    precompilePatterns()
                    Log.d(TAG, "互斥切换成功: $sourceId")
                },
                onFailure = { e ->
                    Log.e(TAG, "互斥切换失败: $sourceId", e)
                }
            )
        }

    suspend fun renameLocalCustomSource(context: Context, sourceId: String, newName: String) =
        withContext(Dispatchers.IO) {
            val repo = repository ?: RuleRepository(context).also { repository = it }
            val config = repo.loadSystemConfig()
            val newSources = config.localCustomSources.map {
                if (it.id == sourceId) it.copy(displayName = newName) else it
            }
            val newConfig = config.copy(localCustomSources = newSources)
            repo.saveSystemConfig(newConfig)
        }

    suspend fun loadAllSourcesSystem(context: Context): Triple<LocalRuleSourceConfig, List<LocalCustomSource>, List<OnlineRuleSource>> =
        withContext(Dispatchers.IO) {
            val repo = repository ?: RuleRepository(context).also { repository = it }
            val config = repo.loadSystemConfig()
            Triple(config.localConfig, config.localCustomSources, config.onlineSources)
        }

    suspend fun toggleLocalSource(context: Context, enabled: Boolean) =
        withContext(Dispatchers.IO) {
            val repo = repository ?: RuleRepository(context).also { repository = it }
            val config = repo.loadSystemConfig()
            val newLocalConfig = config.localConfig.copy(enabled = enabled)
            val newActiveId = if (!enabled && config.activeSourceId == "local") "builtin" else config.activeSourceId
            val newConfig = config.copy(localConfig = newLocalConfig, activeSourceId = newActiveId)
            repo.saveSystemConfig(newConfig)

            if (!enabled && activeSourceId == "local") {
                _rules = repo.loadBuiltInRules()
                activeSourceId = "builtin"
                precompilePatterns()
            }
        }
}
