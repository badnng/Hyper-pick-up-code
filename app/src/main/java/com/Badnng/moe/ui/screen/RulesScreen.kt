package com.Badnng.moe.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.Badnng.moe.helper.BrandIconResolver
import com.Badnng.moe.rules.*

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import java.io.BufferedReader
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    modifier: Modifier = Modifier,
    onShowMenu: ((position: androidx.compose.ui.geometry.Offset, rename: (() -> Unit)?, delete: (() -> Unit)?, export: (() -> Unit)?) -> Unit)? = null,
    onDismissMenu: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }

    val performHaptic = {
        if (prefs.getBoolean("haptic_enabled", true)) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
    }

    var rules by remember { mutableStateOf(RecognitionRuleEngine.rules) }
    var builtinRules by remember { mutableStateOf(RecognitionRules()) }
    var localConfig by remember { mutableStateOf(LocalRuleSourceConfig()) }
    var localCustomSources by remember { mutableStateOf<List<LocalCustomSource>>(emptyList()) }
    var customSourceRules by remember { mutableStateOf<Map<String, RecognitionRules>>(emptyMap()) }
    var onlineSources by remember { mutableStateOf<List<OnlineRuleSource>>(emptyList()) }
    var onlineSourceRules by remember { mutableStateOf<Map<String, RecognitionRules>>(emptyMap()) }
    var activeSourceId by remember { mutableStateOf("local") }

    var localExpanded by remember { mutableStateOf(true) }
    var onlineExpanded by remember { mutableStateOf(false) }

    var showAddSourceDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<OnlineRuleSource?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var deletingCustomSourceId by remember { mutableStateOf<String?>(null) }
    var pendingImportRules by remember { mutableStateOf<RecognitionRules?>(null) }
    var pendingImportFileName by remember { mutableStateOf("") }
    var pendingImportName by remember { mutableStateOf("") }

    var renamingTarget by remember { mutableStateOf<String?>(null) }
    var renamingName by remember { mutableStateOf("") }

    val singleExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(it)?.use { os ->
                            os.write(RecognitionRuleEngine.exportToJson(rules).toByteArray())
                        }
                    }
                    Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.bufferedReader()?.use(BufferedReader::readText) ?: ""
                    }
                    val fileName = withContext(Dispatchers.IO) {
                        val cursor = context.contentResolver.query(it, null, null, null, null)
                        cursor?.use { c ->
                            if (c.moveToFirst()) {
                                val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (nameIndex >= 0) c.getString(nameIndex) else null
                            } else null
                        } ?: "自定义JSON规则"
                    }
                    val result = RecognitionRuleEngine.importFromJson(json)
                    result.fold(
                        onSuccess = { importedRules ->
                            val jsonPkg = importedRules.jsonPackage
                            if (jsonPkg.isNotBlank()) {
                                // 检查是否有相同 jsonPackage 的源
                                val existing = RecognitionRuleEngine.findExistingSourceByPackage(context, jsonPkg)
                                if (existing != null) {
                                    // 直接覆盖，保持原名
                                    RecognitionRuleEngine.overwriteLocalCustomSource(context, existing.id, importedRules)
                                    val (local, custom, online) = RecognitionRuleEngine.loadAllSourcesSystem(context)
                                    localConfig = local
                                    localCustomSources = custom
                                    onlineSources = online
                                    customSourceRules = customSourceRules + (existing.id to importedRules)
                                    rules = RecognitionRuleEngine.rules
                                    Toast.makeText(context, "已覆盖更新「${existing.displayName}」", Toast.LENGTH_SHORT).show()
                                } else {
                                    // 新源，弹出重命名对话框
                                    val name = fileName.substringBeforeLast(".")
                                    pendingImportRules = importedRules
                                    pendingImportFileName = fileName
                                    pendingImportName = name
                                }
                            } else {
                                // 无 jsonPackage，弹出重命名对话框
                                val name = fileName.substringBeforeLast(".")
                                pendingImportRules = importedRules
                                pendingImportFileName = fileName
                                pendingImportName = name
                            }
                        },
                        onFailure = { e ->
                            Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                } catch (e: Exception) {
                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(it)?.use { os ->
                            os.write(RecognitionRuleEngine.exportToJson(rules).toByteArray())
                        }
                    }
                    Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val repo = com.Badnng.moe.rules.RuleRepository(context)
        val config = repo.loadSystemConfig()
        localConfig = config.localConfig
        localCustomSources = config.localCustomSources
        onlineSources = config.onlineSources
        activeSourceId = config.activeSourceId
        // 加载内置规则
        builtinRules = repo.loadBuiltInRules()
        // 加载每个自定义源的规则详情
        val rulesMap = mutableMapOf<String, RecognitionRules>()
        config.localCustomSources.forEach { source ->
            repo.loadLocalCustomRulesById(source.id)?.let { rulesMap[source.id] = it }
        }
        customSourceRules = rulesMap
        // 加载每个在线源的规则详情
        val onlineRulesMap = mutableMapOf<String, RecognitionRules>()
        android.util.Log.d("RulesScreen", "在线源数量: ${config.onlineSources.size}")
        config.onlineSources.forEach { source ->
            val loaded = repo.loadOnlineRulesById(source.id)
            android.util.Log.d("RulesScreen", "加载在线源规则 [${source.id}]: ${if (loaded != null) "成功" else "无缓存"}")
            loaded?.let { onlineRulesMap[source.id] = it }
        }
        onlineSourceRules = onlineRulesMap
    }

    // 从后台管理器读取倒计时
    val countdowns by com.Badnng.moe.rules.RuleAutoUpdateManager.countdowns.collectAsStateWithLifecycle()

    // 后台更新后刷新UI
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000L)
            val repo = com.Badnng.moe.rules.RuleRepository(context)
            val config = repo.loadSystemConfig()
            if (config.onlineSources != onlineSources) {
                onlineSources = config.onlineSources
                val updatedMap = mutableMapOf<String, RecognitionRules>()
                config.onlineSources.forEach { s ->
                    repo.loadOnlineRulesById(s.id)?.let { updatedMap[s.id] = it }
                }
                onlineSourceRules = updatedMap
                if (config.activeSourceId != activeSourceId) {
                    activeSourceId = config.activeSourceId
                    rules = RecognitionRuleEngine.rules
                }
            }
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val listState = rememberLazyListState()
    var isScrolled by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                isScrolled = index > 0 || offset > 30
            }
    }

    val animatedBrightness by animateFloatAsState(
        targetValue = if (isScrolled) -0.1f else 0f,
        animationSpec = tween(300),
        label = "brightness"
    )

    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val canBlur = isRenderEffectSupported()
    Box(modifier = modifier.fillMaxSize()) {
        // 内容层：延伸到顶栏下方
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            val bottomInsets =
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp,
                    bottom = bottomInsets + 100.dp
                )
            ) {
                    item {
                        SectionCard(
                            title = "本地规则",
                            subtitle = "${1 + localCustomSources.size} 个规则源",
                            expanded = localExpanded,
                            onToggle = { performHaptic(); localExpanded = !localExpanded; if (localExpanded) onlineExpanded = false }
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                RuleSourceRow(
                                    name = "内置默认规则",
                                    subtitle = "应用自带规则",
                                    enabled = true,
                                    isActive = activeSourceId == "builtin",
                                    performHaptic = performHaptic,
                                    showSwitch = false,
                                    expandable = true,
                                    details = listOf(
                                        "饮品品牌" to builtinRules.brands.drink.size,
                                        "餐食品牌" to builtinRules.brands.food.size,
                                        "快递关键词" to builtinRules.brands.express.size,
                                        "取件码触发词" to builtinRules.codeExtraction.express.triggerKeywords.size,
                                        "取餐码触发词" to builtinRules.codeExtraction.food.triggerKeywords.size,
                                        "首页关键词" to builtinRules.homepageDetection.keywords.size,
                                        "OCR 纠错" to builtinRules.textCleaning.corrections.size
                                    ),
                                    onLongPress = { offset ->
                                        performHaptic()
                                        onShowMenu?.invoke(
                                            offset,
                                            null,
                                            null,
                                            { singleExportLauncher.launch("builtin_rules.json") }
                                        )
                                    }
                                )

                                localCustomSources.forEachIndexed { index, source ->
                                    val sourceRules = customSourceRules[source.id]
                                    RuleSourceRow(
                                        name = source.displayName.ifBlank { "自定义JSON规则" },
                                        subtitle = "从文件导入的规则",
                                        enabled = source.enabled,
                                        isActive = activeSourceId == "local_custom_${source.id}",
                                        performHaptic = performHaptic,
                                        expandable = sourceRules != null,
                                        details = sourceRules?.let { r ->
                                            listOf(
                                                "饮品品牌" to r.brands.drink.size,
                                                "餐食品牌" to r.brands.food.size,
                                                "快递关键词" to r.brands.express.size,
                                                "取件码触发词" to r.codeExtraction.express.triggerKeywords.size,
                                                "取餐码触发词" to r.codeExtraction.food.triggerKeywords.size,
                                                "首页关键词" to r.homepageDetection.keywords.size,
                                                "OCR 纠错" to r.textCleaning.corrections.size
                                            )
                                        },
                                        onLongPress = { offset ->
                                            performHaptic()
                                            onShowMenu?.invoke(
                                                offset,
                                                {
                                                    renamingTarget = "local_custom_${source.id}"
                                                    renamingName = source.displayName.ifBlank { "自定义JSON规则" }
                                                },
                                                { deletingCustomSourceId = source.id },
                                                { singleExportLauncher.launch("${source.displayName}.json") }
                                            )
                                        },
                                        onToggle = { enabled ->
                                            scope.launch {
                                                if (enabled) {
                                                    RecognitionRuleEngine.activateExclusiveSource(
                                                        context,
                                                        "local_custom_${source.id}"
                                                    )
                                                    activeSourceId = "local_custom_${source.id}"
                                                } else {
                                                    RecognitionRuleEngine.toggleLocalCustomSource(
                                                        context,
                                                        source.id,
                                                        false
                                                    )
                                                    activeSourceId = "builtin"
                                                }
                                                val (local, custom, online) = RecognitionRuleEngine.loadAllSourcesSystem(
                                                    context
                                                )
                                                localConfig = local
                                                localCustomSources = custom
                                                onlineSources = online
                                                rules = RecognitionRuleEngine.rules
                                            }
                                        }
                                    )
                                }

                                ActionButton("导入JSON规则") {
                                    performHaptic()
                                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                                }
                                ActionButton("导出当前规则") {
                                    performHaptic()
                                    exportLauncher.launch("recognition_rules.json")
                                }
                                ActionButton("恢复默认", isDestructive = true) {
                                    performHaptic()
                                    showResetDialog = true
                                }
                            }
                        }
                    }

                    item {
                        SectionCard(
                            title = "在线规则",
                            subtitle = "${onlineSources.size} 个规则源",
                            expanded = onlineExpanded,
                            onToggle = { performHaptic(); onlineExpanded = !onlineExpanded; if (onlineExpanded) localExpanded = false }
                        ) {
                            if (onlineSources.isEmpty()) {
                                Text(
                                    text = "暂无在线规则源",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    onlineSources.forEachIndexed { index, source ->
                                        val sourceRules = onlineSourceRules[source.id]
                                        val cd = countdowns[source.id]
                                        val subtitleText = if (source.enabled && cd != null) {
                                            "${source.url}\n${cd}分钟后更新"
                                        } else source.url
                                        RuleSourceRow(
                                            name = source.name,
                                            subtitle = subtitleText,
                                            enabled = source.enabled,
                                            isActive = activeSourceId == source.id,
                                            performHaptic = performHaptic,
                                            expandable = true,
                                            details = sourceRules?.let { r ->
                                                listOf(
                                                    "饮品品牌" to r.brands.drink.size,
                                                    "餐食品牌" to r.brands.food.size,
                                                    "快递关键词" to r.brands.express.size,
                                                    "取件码触发词" to r.codeExtraction.express.triggerKeywords.size,
                                                    "取餐码触发词" to r.codeExtraction.food.triggerKeywords.size,
                                                    "首页关键词" to r.homepageDetection.keywords.size,
                                                    "OCR 纠错" to r.textCleaning.corrections.size
                                                )
                                            },
                                            noDataText = "暂无缓存，请先点击更新按钮获取规则",
                                            extraAction = {
                                                var isUpdating by remember { mutableStateOf(false) }
                                                IconButton(
                                                    onClick = {
                                                        performHaptic()
                                                        isUpdating = true
                                                        scope.launch {
                                                            val updater = RuleOnlineUpdater(context)
                                                            val result = updater.fetchAndSaveSource(source)
                                                            result.fold(
                                                                onSuccess = { (updated, newSource) ->
                                                                    val newSources = onlineSources.toMutableList()
                                                                        .apply { set(index, newSource) }
                                                                    RecognitionRuleEngine.saveOnlineSources(newSources)
                                                                    if (updated && activeSourceId == source.id) {
                                                                        RecognitionRuleEngine.reload(context)
                                                                        rules = RecognitionRuleEngine.rules
                                                                    }
                                                                    val (local, custom, online) = RecognitionRuleEngine.loadAllSourcesSystem(context)
                                                                    localConfig = local
                                                                    localCustomSources = custom
                                                                    onlineSources = online
                                                                    // 刷新在线源规则详情
                                                                    val repo = com.Badnng.moe.rules.RuleRepository(context)
                                                                    val updatedMap = mutableMapOf<String, RecognitionRules>()
                                                                    online.forEach { s ->
                                                                        val loaded = repo.loadOnlineRulesById(s.id)
                                                                        android.util.Log.d("RulesScreen", "更新后加载在线源规则 [${s.id}]: ${if (loaded != null) "成功(品牌数:${loaded.brands.drink.size + loaded.brands.food.size + loaded.brands.express.size})" else "失败"}")
                                                                        loaded?.let { updatedMap[s.id] = it }
                                                                    }
                                                                    onlineSourceRules = updatedMap
                                                                    Toast.makeText(context, if (updated) "已更新" else "无需更新", Toast.LENGTH_SHORT).show()
                                                                    // 手动更新后重置倒计时
                                                                    com.Badnng.moe.rules.RuleAutoUpdateManager.resetCountdown(source.id, source.updateIntervalMinutes)
                                                                },
                                                                onFailure = { e ->
                                                                    Toast.makeText(context, "更新失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                                                }
                                                            )
                                                            isUpdating = false
                                                        }
                                                    },
                                                    enabled = !isUpdating
                                                ) {
                                                    if (isUpdating) {
                                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                                    } else {
                                                        Icon(Icons.Default.Refresh, contentDescription = "更新", modifier = Modifier.size(20.dp))
                                                    }
                                                }
                                            },
                                            onLongPress = { offset ->
                                                performHaptic()
                                                onShowMenu?.invoke(
                                                    offset,
                                                    {
                                                        editingSource = source
                                                    },
                                                    {
                                                        scope.launch {
                                                            val newSources = onlineSources.toMutableList()
                                                                .apply { removeAt(index) }
                                                            RecognitionRuleEngine.saveOnlineSources(newSources)
                                                            if (activeSourceId == source.id) {
                                                                RecognitionRuleEngine.switchActiveSource("builtin", context)
                                                                activeSourceId = "builtin"
                                                            }
                                                            val (local, custom, online) = RecognitionRuleEngine.loadAllSourcesSystem(context)
                                                            localConfig = local
                                                            localCustomSources = custom
                                                            onlineSources = online
                                                            onlineSourceRules = onlineSourceRules - source.id
                                                            rules = RecognitionRuleEngine.rules
                                                            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    { singleExportLauncher.launch("${source.name}.json") }
                                                )
                                            },
                                            onToggle = { enabled ->
                                                scope.launch {
                                                    if (enabled) {
                                                        RecognitionRuleEngine.activateExclusiveSource(
                                                            context,
                                                            source.id
                                                        )
                                                        activeSourceId = source.id
                                                    } else {
                                                        val updated = source.copy(enabled = false)
                                                        val newSources = onlineSources.toMutableList()
                                                            .apply { set(index, updated) }
                                                        RecognitionRuleEngine.saveOnlineSources(
                                                            newSources
                                                        )
                                                        RecognitionRuleEngine.switchActiveSource(
                                                            "builtin",
                                                            context
                                                        )
                                                        activeSourceId = "builtin"
                                                    }
                                                    val (local, custom, online) = RecognitionRuleEngine.loadAllSourcesSystem(
                                                        context
                                                    )
                                                    localConfig = local
                                                    localCustomSources = custom
                                                    onlineSources = online
                                                    rules = RecognitionRuleEngine.rules
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = { performHaptic(); showAddSourceDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("添加规则源")
                            }
                        }
                    }

                    // 自定义取件地点
                    item {
                        var customLocationsExpanded by remember { mutableStateOf(false) }
                        val customLocationsPrefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
                        var customLocationsText by remember { mutableStateOf(customLocationsPrefs.getString("custom_pickup_locations", "") ?: "") }
                        val keywordCount = customLocationsText.split(",").map { it.trim() }.filter { it.isNotBlank() }.size

                        SectionCard(
                            title = "自定义取件地点",
                            subtitle = if (keywordCount > 0) "$keywordCount 个关键词" else "未设置",
                            expanded = customLocationsExpanded,
                            onToggle = { performHaptic(); customLocationsExpanded = !customLocationsExpanded }
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "当识别文本中包含以下关键词时，直接将其作为取件地点。多个关键词用逗号分隔，留空则不生效。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = customLocationsText,
                                    onValueChange = { customLocationsText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("输入自定义取件地点关键词，用逗号分隔") },
                                    minLines = 2,
                                    maxLines = 5,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Button(
                                    onClick = {
                                        performHaptic()
                                        customLocationsPrefs.edit().putString("custom_pickup_locations", customLocationsText).apply()
                                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("保存")
                                }
                            }
                        }
                    }

                    // 自定义品牌图标
                    item {
                        var brandIconExpanded by remember { mutableStateOf(false) }
                        var brandIconMappings by remember { mutableStateOf(BrandIconResolver.getCustomMappings(context)) }
                        var pendingImageIndex by remember { mutableIntStateOf(-1) }
                        val imagePickerLauncher = rememberLauncherForActivityResult(
                            contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                        ) { uri ->
                            if (uri != null && pendingImageIndex >= 0) {
                                val savedPath = BrandIconResolver.saveCustomIcon(context, uri)
                                if (savedPath != null) {
                                    brandIconMappings = brandIconMappings.toMutableList().apply {
                                        set(pendingImageIndex, BrandIconResolver.IconMapping(savedPath, this[pendingImageIndex].keywords))
                                    }
                                }
                                pendingImageIndex = -1
                            }
                        }

                        SectionCard(
                            title = "自定义品牌图标",
                            subtitle = if (brandIconMappings.isNotEmpty()) "${brandIconMappings.size} 条规则" else "未设置",
                            expanded = brandIconExpanded,
                            onToggle = { performHaptic(); brandIconExpanded = !brandIconExpanded }
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "当品牌名称包含关键词时显示自定义图标。点击图标从相册选择，图片不小于 224x224px。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                brandIconMappings.forEachIndexed { index, mapping ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Surface(
                                                    onClick = {
                                                        performHaptic()
                                                        pendingImageIndex = index
                                                        imagePickerLauncher.launch("image/*")
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (mapping.iconPath.isNotEmpty()) Color.Transparent
                                                        else MaterialTheme.colorScheme.surfaceVariant,
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        if (mapping.iconPath.isNotEmpty()) {
                                                            AsyncImage(
                                                                model = java.io.File(mapping.iconPath),
                                                                contentDescription = null,
                                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                                            )
                                                        } else {
                                                            Icon(
                                                                Icons.Default.Add,
                                                                contentDescription = "选择图标",
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }

                                                OutlinedTextField(
                                                    value = mapping.keywords,
                                                    onValueChange = { newKeywords ->
                                                        brandIconMappings = brandIconMappings.toMutableList().apply {
                                                            set(index, BrandIconResolver.IconMapping(mapping.iconPath, newKeywords))
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    label = { Text("关键词") },
                                                    placeholder = { Text("品牌A,品牌B") },
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                                    )
                                                )

                                                IconButton(
                                                    onClick = {
                                                        performHaptic()
                                                        BrandIconResolver.saveCustomMappings(context, brandIconMappings)
                                                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                                                    }
                                                ) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "保存",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        performHaptic()
                                                        if (mapping.iconPath.isNotEmpty()) {
                                                            BrandIconResolver.deleteCustomIcon(mapping.iconPath)
                                                        }
                                                        brandIconMappings = brandIconMappings.toMutableList().apply { removeAt(index) }
                                                    }
                                                ) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "删除",
                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        performHaptic()
                                        brandIconMappings = brandIconMappings + BrandIconResolver.IconMapping("", "")
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("添加规则")
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "规则版本: ${rules.schemaVersion} | 更新时间: ${rules.updatedAt}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
        }

        // 毛玻璃 TopAppBar 覆盖层（仅 MD3E 模式）
        if (canBlur) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // 背景模糊层（带渐隐 mask）
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .textureBlur(
                            backdrop = backdrop,
                            shape = RectangleShape,
                            blurRadius = 80f,
                            colors = top.yukonga.miuix.kmp.blur.BlurColors(brightness = animatedBrightness)
                        )
                        .frostedGlassMask()
                )
                // 内容层（不受 mask 影响）
                TopAppBar(
                    title = {
                        AnimatedVisibility(
                            visible = !isScrolled,
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut() + slideOutVertically()
                        ) {
                            Text("识别规则", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    windowInsets = WindowInsets.statusBars
                )
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 3.dp
            ) {
                LargeTopAppBar(
                    title = { Text("识别规则", style = MaterialTheme.typography.headlineLarge) },
                    colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = Color.Transparent),
                    windowInsets = WindowInsets.statusBars
                )
            }
        }
    }

    // 添加/编辑在线源对话框
            if (showAddSourceDialog || editingSource != null) {
                OnlineSourceDialog(
                    source = editingSource,
                    performHaptic = performHaptic,
                    onDismiss = {
                        performHaptic()
                        showAddSourceDialog = false
                        editingSource = null
                    },
                    onSave = { newSource: OnlineRuleSource ->
                        android.util.Log.d("RulesScreen", "OnlineSourceDialog onSave: name=${newSource.name}, updateIntervalMinutes=${newSource.updateIntervalMinutes}")
                        scope.launch {
                            val newSources = if (editingSource != null) {
                                onlineSources.map { if (it.id == newSource.id) newSource else it }
                            } else {
                                onlineSources + newSource
                            }
                            RecognitionRuleEngine.saveOnlineSources(newSources)
                            val config = com.Badnng.moe.rules.RuleRepository(context).loadSystemConfig()
                            localConfig = config.localConfig
                            localCustomSources = config.localCustomSources
                            onlineSources = config.onlineSources
                            activeSourceId = config.activeSourceId
                            config.onlineSources.forEach { s ->
                                android.util.Log.d("RulesScreen", "保存后加载: ${s.name} [${s.id}] updateIntervalMinutes=${s.updateIntervalMinutes}")
                            }
                            // 保存后重置倒计时
                            if (newSource.enabled) {
                                com.Badnng.moe.rules.RuleAutoUpdateManager.resetCountdown(newSource.id, newSource.updateIntervalMinutes)
                            }
                            showAddSourceDialog = false
                            editingSource = null
                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // 恢复默认确认对话框
            if (showResetDialog) {
                AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = { Text("恢复默认规则") },
                    text = { Text("确定要删除所有自定义规则并恢复默认值吗？此操作不可撤销。") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                performHaptic()
                                scope.launch {
                                    RecognitionRuleEngine.resetLocalDefaultRules(context)
                                    val (local, custom, online) = RecognitionRuleEngine.loadAllSourcesSystem(
                                        context
                                    )
                                    localConfig = local
                                    localCustomSources = custom
                                    onlineSources = online
                                    if (activeSourceId == "local") {
                                        rules = RecognitionRuleEngine.rules
                                    }
                                    showResetDialog = false
                                    Toast.makeText(context, "已恢复默认规则", Toast.LENGTH_SHORT)
                                        .show()
                                }
                            }
                        ) {
                            Text("确定")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { performHaptic(); showResetDialog = false }) {
                            Text("取消")
                        }
                    }
                )
            }

            // 重命名对话框
            if (renamingTarget != null) {
                AlertDialog(
                    onDismissRequest = { renamingTarget = null },
                    title = { Text("重命名规则") },
                    text = {
                        OutlinedTextField(
                            value = renamingName,
                            onValueChange = { renamingName = it },
                            label = { Text("规则名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                performHaptic()
                                scope.launch {
                                    val target = renamingTarget ?: return@launch
                                    if (target == "local") {
                                        val config =
                                            RecognitionRuleEngine.loadAllSourcesSystem(context).first
                                        val newConfig = config.copy(displayName = renamingName)
                                        val repo = com.Badnng.moe.rules.RuleRepository(context)
                                        val sysConfig = repo.loadSystemConfig()
                                        repo.saveSystemConfig(sysConfig.copy(localConfig = newConfig))
                                    } else if (target.startsWith("local_custom_")) {
                                        val sourceId = target.removePrefix("local_custom_")
                                        RecognitionRuleEngine.renameLocalCustomSource(context, sourceId, renamingName)
                                    } else {
                                        val newSources = onlineSources.map {
                                            if (it.id == target) it.copy(name = renamingName) else it
                                        }
                                        RecognitionRuleEngine.saveOnlineSources(newSources)
                                    }
                                    val (local, custom, online) = RecognitionRuleEngine.loadAllSourcesSystem(
                                        context
                                    )
                                    localConfig = local
                                    localCustomSources = custom
                                    onlineSources = online
                                    renamingTarget = null
                                    Toast.makeText(context, "已重命名", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("确定")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { performHaptic(); renamingTarget = null }) {
                            Text("取消")
                        }
                    }
                )
            }

            // 导入重命名对话框
            if (pendingImportRules != null) {
                AlertDialog(
                    onDismissRequest = {
                        pendingImportRules = null
                        pendingImportFileName = ""
                        pendingImportName = ""
                    },
                    title = { Text("导入规则") },
                    text = {
                        OutlinedTextField(
                            value = pendingImportName,
                            onValueChange = { pendingImportName = it },
                            label = { Text("规则名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        val isDuplicateName = localCustomSources.any {
                            it.displayName == pendingImportName.trim()
                        }
                        TextButton(
                            onClick = {
                                performHaptic()
                                if (isDuplicateName) {
                                    Toast.makeText(context, "已存在同名规则「${pendingImportName.trim()}」", Toast.LENGTH_SHORT).show()
                                    return@TextButton
                                }
                                scope.launch {
                                    val rulesToImport = pendingImportRules ?: return@launch
                                    val newId = RecognitionRuleEngine.importLocalCustomSource(
                                        context,
                                        rulesToImport,
                                        pendingImportName.ifBlank { pendingImportFileName },
                                        rulesToImport.jsonPackage
                                    )
                                    val (local, custom, online) = RecognitionRuleEngine.loadAllSourcesSystem(context)
                                    localConfig = local
                                    localCustomSources = custom
                                    onlineSources = online
                                    customSourceRules = customSourceRules + (newId to rulesToImport)
                                    rules = RecognitionRuleEngine.rules
                                    pendingImportRules = null
                                    pendingImportFileName = ""
                                    pendingImportName = ""
                                    Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = pendingImportName.isNotBlank() && !isDuplicateName
                        ) {
                            Text("确定")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            performHaptic()
                            pendingImportRules = null
                            pendingImportFileName = ""
                            pendingImportName = ""
                        }) {
                            Text("取消")
                        }
                    }
                )
            }

            // 删除自定义规则源确认对话框
            if (deletingCustomSourceId != null) {
                val sourceId = deletingCustomSourceId!!
                val source = localCustomSources.firstOrNull { it.id == sourceId }
                AlertDialog(
                    onDismissRequest = { deletingCustomSourceId = null },
                    title = { Text("删除规则源") },
                    text = { Text("确定要删除「${source?.displayName?.ifBlank { "自定义JSON规则" } ?: "自定义JSON规则"}」吗？此操作不可撤销。") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                performHaptic()
                                scope.launch {
                                    RecognitionRuleEngine.deleteLocalCustomSource(context, sourceId)
                                    val (local, custom, online) = RecognitionRuleEngine.loadAllSourcesSystem(context)
                                    localConfig = local
                                    localCustomSources = custom
                                    onlineSources = online
                                    customSourceRules = customSourceRules - sourceId
                                    activeSourceId = RecognitionRuleEngine.currentSourceId
                                    rules = RecognitionRuleEngine.rules
                                    deletingCustomSourceId = null
                                    Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("确定")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { performHaptic(); deletingCustomSourceId = null }) {
                            Text("取消")
                        }
                    }
                )
            }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val motionScheme = MaterialTheme.motionScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .animateContentSize(animationSpec = motionScheme.defaultSpatialSpec<IntSize>()),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onToggle() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                val arrowRotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    animationSpec = motionScheme.defaultSpatialSpec<Float>()
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(arrowRotation)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec<Float>()) +
                        expandVertically(animationSpec = motionScheme.defaultSpatialSpec<IntSize>()),
                exit = fadeOut(animationSpec = motionScheme.defaultEffectsSpec<Float>()) +
                       shrinkVertically(animationSpec = motionScheme.defaultSpatialSpec<IntSize>())
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    content = content
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RuleSourceRow(
    name: String,
    subtitle: String,
    enabled: Boolean,
    isActive: Boolean,
    performHaptic: () -> Unit,
    onToggle: ((Boolean) -> Unit)? = null,
    showSwitch: Boolean = true,
    expandable: Boolean = false,
    details: List<Pair<String, Int>>? = null,
    noDataText: String? = null,
    extraAction: (@Composable () -> Unit)? = null,
    onLongPress: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null
) {
    val motionScheme = MaterialTheme.motionScheme
    var expanded by remember { mutableStateOf(false) }
    var globalPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .animateContentSize(animationSpec = motionScheme.defaultSpatialSpec<IntSize>())
            .onGloballyPositioned { coordinates ->
                globalPosition = coordinates.positionInWindow()
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (expandable) {
                            performHaptic()
                            expanded = !expanded
                        }
                    },
                    onLongPress = { offset ->
                        performHaptic()
                        onLongPress?.invoke(
                            androidx.compose.ui.geometry.Offset(
                                globalPosition.x + offset.x,
                                globalPosition.y + offset.y
                            )
                        )
                    }
                )
            },
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
                if (showSwitch && onToggle != null) {
                    Switch(
                        checked = enabled,
                        onCheckedChange = { performHaptic(); onToggle(it) }
                    )
                }
                extraAction?.invoke()
                if (expandable) {
                    val arrowRotation by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        animationSpec = motionScheme.defaultSpatialSpec<Float>()
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).rotate(arrowRotation)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded && expandable,
                enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec<Float>()) +
                        expandVertically(animationSpec = motionScheme.defaultSpatialSpec<IntSize>()),
                exit = fadeOut(animationSpec = motionScheme.defaultEffectsSpec<Float>()) +
                       shrinkVertically(animationSpec = motionScheme.defaultSpatialSpec<IntSize>())
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                ) {
                    if (details != null) {
                        details.forEach { (label, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$value",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else if (noDataText != null) {
                        Text(
                            text = noDataText,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isDestructive) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = if (isDestructive) ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        ) else ButtonDefaults.outlinedButtonColors()
    ) {
        Text(text)
    }
}

@Composable
private fun OnlineSourceDialog(
    source: OnlineRuleSource?,
    performHaptic: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (OnlineRuleSource) -> Unit
) {
    var name by remember(source?.id) { mutableStateOf(source?.name ?: "") }
    var url by remember(source?.id) { mutableStateOf(source?.url ?: "") }
    var autoUpdate by remember(source?.id) { mutableStateOf(source?.enabled ?: false) }
    var updateIntervalMinutes by remember(source?.id) {
        android.util.Log.d("RulesScreen", "OnlineSourceDialog init: source=${source?.name}, id=${source?.id}, updateIntervalMinutes=${source?.updateIntervalMinutes}")
        mutableStateOf("${source?.updateIntervalMinutes ?: 1440}")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (source != null) "修改配置" else "添加规则源") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    placeholder = { Text("例如: 社区规则包") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://example.com/rules.json") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "自动更新",
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp
                    )
                    Switch(
                        checked = autoUpdate,
                        onCheckedChange = { performHaptic(); autoUpdate = it }
                    )
                }
                OutlinedTextField(
                    value = updateIntervalMinutes,
                    onValueChange = { updateIntervalMinutes = it.filter { c -> c.isDigit() } },
                    label = { Text("更新间隔 (分钟)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    enabled = autoUpdate
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && url.isNotBlank()) {
                        performHaptic()
                        val intervalMinutes = maxOf(1, updateIntervalMinutes.toIntOrNull() ?: 1440)
                        val sourceToSave = if (source != null) {
                            source.copy(name = name.trim(), url = url.trim(), enabled = autoUpdate, updateIntervalMinutes = intervalMinutes)
                        } else {
                            OnlineRuleSource(
                                id = UUID.randomUUID().toString(),
                                name = name.trim(),
                                url = url.trim(),
                                enabled = autoUpdate,
                                updateIntervalMinutes = intervalMinutes
                            )
                        }
                        onSave(sourceToSave)
                    }
                },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = { performHaptic(); onDismiss() }) {
                Text("取消")
            }
        }
    )
}

private fun Modifier.frostedGlassMask(): Modifier = this.drawWithContent {
    drawContent()
    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.Black,
                0.55f to Color.Black,
                1f to Color.Transparent
            )
        ),
        blendMode = BlendMode.DstIn
    )
}
