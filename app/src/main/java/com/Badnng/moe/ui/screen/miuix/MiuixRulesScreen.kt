package com.Badnng.moe.ui.screen.miuix

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import top.yukonga.miuix.kmp.blur.layerBackdrop
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.helper.BrandIconResolver
import com.Badnng.moe.rules.*
import com.Badnng.moe.ui.miuix.MiuixBlurredBar
import com.Badnng.moe.ui.miuix.miuixScrollModifiers
import com.Badnng.moe.ui.miuix.rememberMiuixBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.BufferedReader
import java.util.*

@Composable
fun MiuixRulesScreen(
    bottomLayoutInfo: MiuixHomeBottomLayoutInfo,
    onExpandNavigationRail: (() -> Unit)? = null,
    onShowMenu: ((position: androidx.compose.ui.geometry.Offset, rename: (() -> Unit)?, delete: (() -> Unit)?, export: (() -> Unit)?) -> Unit)? = null,
    onModalVisibilityChange: (Boolean) -> Unit = {},
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
    var renamingTarget by remember { mutableStateOf<String?>(null) }
    var renamingName by remember { mutableStateOf("") }
    var pendingImportRules by remember { mutableStateOf<RecognitionRules?>(null) }
    var pendingImportFileName by remember { mutableStateOf("") }
    var pendingImportName by remember { mutableStateOf("") }

    val hasVisibleModal = showResetDialog ||
        showAddSourceDialog ||
        editingSource != null ||
        renamingTarget != null ||
        deletingCustomSourceId != null ||
        pendingImportRules != null
    val currentOnModalVisibilityChange by rememberUpdatedState(onModalVisibilityChange)
    LaunchedEffect(hasVisibleModal) {
        currentOnModalVisibilityChange(hasVisibleModal)
    }
    DisposableEffect(Unit) {
        onDispose { currentOnModalVisibilityChange(false) }
    }


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
                                val existing = RecognitionRuleEngine.findExistingSourceByPackage(context, jsonPkg)
                                if (existing != null) {
                                    RecognitionRuleEngine.overwriteLocalCustomSource(context, existing.id, importedRules)
                                    val (local, custom, online) = RecognitionRuleEngine.loadAllSourcesSystem(context)
                                    localConfig = local
                                    localCustomSources = custom
                                    onlineSources = online
                                    customSourceRules = customSourceRules + (existing.id to importedRules)
                                    rules = RecognitionRuleEngine.rules
                                    Toast.makeText(context, "已覆盖更新「${existing.displayName}」", Toast.LENGTH_SHORT).show()
                                } else {
                                    val name = fileName.substringBeforeLast(".")
                                    pendingImportRules = importedRules
                                    pendingImportFileName = fileName
                                    pendingImportName = name
                                }
                            } else {
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
        builtinRules = repo.loadBuiltInRules()
        val rulesMap = mutableMapOf<String, RecognitionRules>()
        config.localCustomSources.forEach { source ->
            repo.loadLocalCustomRulesById(source.id)?.let { rulesMap[source.id] = it }
        }
        customSourceRules = rulesMap
        val onlineRulesMap = mutableMapOf<String, RecognitionRules>()
        config.onlineSources.forEach { source ->
            repo.loadOnlineRulesById(source.id)?.let { onlineRulesMap[source.id] = it }
        }
        onlineSourceRules = onlineRulesMap
    }

    val countdowns by com.Badnng.moe.rules.RuleAutoUpdateManager.countdowns.collectAsStateWithLifecycle()

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

    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberMiuixBackdrop()
    val blurEnabled = backdrop != null

    Scaffold(
        topBar = {
            MiuixBlurredBar(backdrop = backdrop, blurEnabled = blurEnabled) {
                TopAppBar(
                    title = "识别规则",
                    color = if (blurEnabled) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        onExpandNavigationRail?.let {
                            MiuixNavigationRailExpandButton(onClick = it)
                        }
                    },
                )
            }
        }
    ) { innerPadding ->
        val lazyListState = rememberLazyListState()
        Box(
            modifier = if (backdrop != null) {
                Modifier.fillMaxSize().layerBackdrop(backdrop)
            } else {
                Modifier.fillMaxSize()
            }
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .miuixScrollModifiers(topAppBarScrollBehavior),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = bottomLayoutInfo.pageContentBottomPadding
                )
            ) {
                // 本地规则标题
                item {
                    SmallTitle(text = "本地规则")
                }

                // 内置默认规则
                item {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
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
                    }
                }

                // 自定义规则源
                items(localCustomSources.size, key = { localCustomSources[it].id }) { index ->
                    val source = localCustomSources[index]
                    val sourceRules = customSourceRules[source.id]
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
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
                                        RecognitionRuleEngine.activateExclusiveSource(context, "local_custom_${source.id}")
                                        activeSourceId = "local_custom_${source.id}"
                                    } else {
                                        RecognitionRuleEngine.toggleLocalCustomSource(context, source.id, false)
                                        activeSourceId = "builtin"
                                    }
                                    val (local, custom, online) = RecognitionRuleEngine.loadAllSourcesSystem(context)
                                    localConfig = local
                                    localCustomSources = custom
                                    onlineSources = online
                                    rules = RecognitionRuleEngine.rules
                                }
                            }
                        )
                    }
                }

                // 操作按钮
                item {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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

                // 在线规则
                // 在线规则标题
                item {
                    SmallTitle(text = "在线规则")
                }

                // 空状态提示
                if (onlineSources.isEmpty()) {
                    item {
                        Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                            Text(
                                text = "暂无在线规则源",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                // 在线规则源列表
                items(onlineSources.size) { index ->
                    val source = onlineSources[index]
                    val sourceRules = onlineSourceRules[source.id]
                    val cd = countdowns[source.id]
                    val subtitleText = if (source.enabled && cd != null) {
                        "${source.url}\n${cd}分钟后更新"
                    } else source.url
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
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
                                                    val newSources = onlineSources.toMutableList().apply { set(index, newSource) }
                                                    RecognitionRuleEngine.saveOnlineSources(newSources)
                                                    if (updated && activeSourceId == source.id) {
                                                        RecognitionRuleEngine.reload(context)
                                                        rules = RecognitionRuleEngine.rules
                                                    }
                                                    val (local, custom, online) = RecognitionRuleEngine.loadAllSourcesSystem(context)
                                                    localConfig = local
                                                    localCustomSources = custom
                                                    onlineSources = online
                                                    val repo = com.Badnng.moe.rules.RuleRepository(context)
                                                    val updatedMap = mutableMapOf<String, RecognitionRules>()
                                                    online.forEach { s ->
                                                        repo.loadOnlineRulesById(s.id)?.let { updatedMap[s.id] = it }
                                                    }
                                                    onlineSourceRules = updatedMap
                                                    Toast.makeText(context, if (updated) "已更新" else "无需更新", Toast.LENGTH_SHORT).show()
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
                                        InfiniteProgressIndicator(modifier = Modifier.size(18.dp))
                                    } else {
                                        Icon(MiuixIcons.Regular.Refresh, contentDescription = "更新", modifier = Modifier.size(20.dp))
                                    }
                                }
                            },
                            onLongPress = { offset ->
                                performHaptic()
                                onShowMenu?.invoke(
                                    offset,
                                    { editingSource = source },
                                    {
                                        scope.launch {
                                            val newSources = onlineSources.toMutableList().apply { removeAt(index) }
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
                                    null
                                )
                            },
                            onToggle = { enabled ->
                                scope.launch {
                                    if (enabled) {
                                        RecognitionRuleEngine.activateExclusiveSource(context, source.id)
                                        activeSourceId = source.id
                                    } else {
                                        val updated = source.copy(enabled = false)
                                        val newSources = onlineSources.toMutableList().apply { set(index, updated) }
                                        RecognitionRuleEngine.saveOnlineSources(newSources)
                                        RecognitionRuleEngine.switchActiveSource("builtin", context)
                                        activeSourceId = "builtin"
                                    }
                                    val (local, custom, online) = RecognitionRuleEngine.loadAllSourcesSystem(context)
                                    localConfig = local
                                    localCustomSources = custom
                                    onlineSources = online
                                    rules = RecognitionRuleEngine.rules
                                }
                            }
                        )
                    }
                }

                // 添加规则源按钮
                item {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        Button(
                            onClick = { performHaptic(); showAddSourceDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                            colors = ButtonDefaults.buttonColorsPrimary()
                        ) {
                            Icon(MiuixIcons.Regular.Add, contentDescription = null, modifier = Modifier.size(18.dp))
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

                    SmallTitle(text = "自定义取件地点")
                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                        onClick = { performHaptic(); customLocationsExpanded = !customLocationsExpanded }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "自定义取件地点", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = if (keywordCount > 0) "$keywordCount 个关键词" else "未设置",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                                val arrowRotation by animateFloatAsState(
                                    targetValue = if (customLocationsExpanded) 180f else 0f,
                                    animationSpec = spring()
                                )
                                Icon(
                                    imageVector = MiuixIcons.Regular.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.rotate(arrowRotation)
                                )
                            }

                            AnimatedVisibility(
                                visible = customLocationsExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "当识别文本中包含以下关键词时，直接将其作为取件地点。多个关键词用逗号分隔，留空则不生效。",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                    TextField(
                                        value = customLocationsText,
                                        onValueChange = { customLocationsText = it },
                                        label = "输入自定义取件地点关键词，用逗号分隔",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            performHaptic()
                                            customLocationsPrefs.edit().putString("custom_pickup_locations", customLocationsText).apply()
                                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColorsPrimary()
                                    ) {
                                        Text("保存")
                                    }
                                }
                            }
                        }
                    }
                }

                // 自定义品牌图标
                item {
                    var brandIconExpanded by remember { mutableStateOf(false) }
                    var brandIconMappings by remember { mutableStateOf(BrandIconResolver.getCustomMappings(context)) }
                    var pendingImageIndex by remember { mutableIntStateOf(-1) }
                    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
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

                    SmallTitle(text = "自定义品牌图标")
                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                        onClick = { performHaptic(); brandIconExpanded = !brandIconExpanded }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "自定义品牌图标", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = if (brandIconMappings.isNotEmpty()) "${brandIconMappings.size} 条规则" else "未设置",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                                val arrowRotation by animateFloatAsState(
                                    targetValue = if (brandIconExpanded) 180f else 0f,
                                    animationSpec = spring()
                                )
                                Icon(
                                    imageVector = MiuixIcons.Regular.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.rotate(arrowRotation)
                                )
                            }

                            AnimatedVisibility(
                                visible = brandIconExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "当品牌名称包含关键词时显示自定义图标。点击图标从相册选择，图片不小于 224x224px。",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )

                                    brandIconMappings.forEachIndexed { index, mapping ->
                                        Card(
                                            colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                                                MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .squircleSurface(
                                                                if (mapping.iconPath.isNotEmpty()) Color.Transparent
                                                                else MiuixTheme.colorScheme.surfaceVariant,
                                                                8.dp,
                                                            )
                                                            .clickable {
                                                                performHaptic()
                                                                pendingImageIndex = index
                                                                imagePickerLauncher.launch("image/*")
                                                            }
                                                    ) {
                                                        if (mapping.iconPath.isNotEmpty()) {
                                                            coil.compose.AsyncImage(
                                                                model = java.io.File(mapping.iconPath),
                                                                contentDescription = null,
                                                                modifier = Modifier.fillMaxSize().squircleClip(8.dp)
                                                            )
                                                        } else {
                                                            Icon(
                                                                MiuixIcons.Regular.Add,
                                                                contentDescription = "选择图标",
                                                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                                            )
                                                        }
                                                    }

                                                    TextField(
                                                        value = mapping.keywords,
                                                        onValueChange = { newKeywords ->
                                                            brandIconMappings = brandIconMappings.toMutableList().apply {
                                                                set(index, BrandIconResolver.IconMapping(mapping.iconPath, newKeywords))
                                                            }
                                                        },
                                                        label = "关键词（品牌A,品牌B）",
                                                        modifier = Modifier.weight(1f)
                                                    )

                                                    IconButton(
                                                        onClick = {
                                                            performHaptic()
                                                            BrandIconResolver.saveCustomMappings(context, brandIconMappings)
                                                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                                                        }
                                                    ) {
                                                        Icon(Icons.Default.Save, contentDescription = "保存", modifier = Modifier.size(20.dp))
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            performHaptic()
                                                            brandIconMappings = brandIconMappings.toMutableList().apply { removeAt(index) }
                                                            BrandIconResolver.saveCustomMappings(context, brandIconMappings)
                                                        }
                                                    ) {
                                                        Icon(MiuixIcons.Regular.Delete, contentDescription = "删除", tint = Color.Red, modifier = Modifier.size(20.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            performHaptic()
                                            brandIconMappings = brandIconMappings.toMutableList().apply {
                                                add(BrandIconResolver.IconMapping("", ""))
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColorsPrimary()
                                    ) {
                                        Icon(MiuixIcons.Regular.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("添加规则")
                                    }
                                }
                            }
                        }
                    }
                }

                // 规则版本信息
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "规则版本: ${rules.schemaVersion} | 更新时间: ${rules.updatedAt}",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = PaddingValues(top = innerPadding.calculateTopPadding(), bottom = bottomLayoutInfo.pageContentBottomPadding),
            )
        }
    }

    // 恢复默认确认对话框
    if (showResetDialog) {
        OverlayDialog(
            title = "恢复默认规则",
            summary = "确定要删除所有自定义规则并恢复默认值吗？此操作不可撤销。",
            show = showResetDialog,
            onDismissRequest = { showResetDialog = false }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        performHaptic()
                        showResetDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        performHaptic()
                        scope.launch {
                            RecognitionRuleEngine.resetLocalDefaultRules(context)
                            val (local, custom, online) = RecognitionRuleEngine.loadAllSourcesSystem(context)
                            localConfig = local
                            localCustomSources = custom
                            onlineSources = online
                            rules = RecognitionRuleEngine.rules
                            showResetDialog = false
                            Toast.makeText(context, "已恢复默认规则", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("确定", color = MiuixTheme.colorScheme.error)
                }
            }
        }
    }

    // 自定义规则重命名。菜单回调只负责写入目标，这里必须实际承载对话框，
    // 否则点击主页顶层菜单后只会更新状态而不会显示任何操作界面。
    if (renamingTarget != null) {
        var showRenameDialog by remember { mutableStateOf(true) }
        WindowDialog(
            title = "重命名规则",
            show = showRenameDialog,
            onDismissRequest = { showRenameDialog = false },
            onDismissFinished = { renamingTarget = null },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = renamingName,
                    onValueChange = { renamingName = it },
                    label = "规则名称",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = "取消",
                        onClick = {
                            performHaptic()
                            showRenameDialog = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            performHaptic()
                            scope.launch {
                                val target = renamingTarget ?: return@launch
                                if (target == "local") {
                                    val config = RecognitionRuleEngine.loadAllSourcesSystem(context).first
                                    val newConfig = config.copy(displayName = renamingName)
                                    val repo = RuleRepository(context)
                                    val systemConfig = repo.loadSystemConfig()
                                    repo.saveSystemConfig(systemConfig.copy(localConfig = newConfig))
                                } else if (target.startsWith("local_custom_")) {
                                    RecognitionRuleEngine.renameLocalCustomSource(
                                        context,
                                        target.removePrefix("local_custom_"),
                                        renamingName,
                                    )
                                } else {
                                    RecognitionRuleEngine.saveOnlineSources(
                                        onlineSources.map {
                                            if (it.id == target) it.copy(name = renamingName) else it
                                        },
                                    )
                                }
                                val (local, custom, online) =
                                    RecognitionRuleEngine.loadAllSourcesSystem(context)
                                localConfig = local
                                localCustomSources = custom
                                onlineSources = online
                                showRenameDialog = false
                                Toast.makeText(context, "已重命名", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        enabled = renamingName.isNotBlank(),
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }

    // 删除本地自定义规则源确认对话框。
    deletingCustomSourceId?.let { sourceId ->
        var showDeleteDialog by remember { mutableStateOf(true) }
        val source = localCustomSources.firstOrNull { it.id == sourceId }
        WindowDialog(
            title = "删除规则源",
            show = showDeleteDialog,
            onDismissRequest = { showDeleteDialog = false },
            onDismissFinished = { deletingCustomSourceId = null },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "确定要删除「${source?.displayName?.ifBlank { "自定义JSON规则" } ?: "自定义JSON规则"}」吗？此操作不可撤销。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = "取消",
                        onClick = {
                            performHaptic()
                            showDeleteDialog = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            performHaptic()
                            scope.launch {
                                RecognitionRuleEngine.deleteLocalCustomSource(context, sourceId)
                                val (local, custom, online) =
                                    RecognitionRuleEngine.loadAllSourcesSystem(context)
                                localConfig = local
                                localCustomSources = custom
                                onlineSources = online
                                customSourceRules = customSourceRules - sourceId
                                activeSourceId = RecognitionRuleEngine.currentSourceId
                                rules = RecognitionRuleEngine.rules
                                showDeleteDialog = false
                                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(),
                    ) {
                        Text("确定", color = MiuixTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    // 添加/编辑在线源 WindowBottomSheet（始终在树中，由内部 showSheet 控制生命周期）
    if (showAddSourceDialog || editingSource != null) {
        OnlineSourceWindowSheet(
            source = editingSource,
            performHaptic = performHaptic,
            onDismiss = {
                showAddSourceDialog = false
                editingSource = null
            },
            onSave = { newSource ->
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

    // 导入重命名对话框
    if (pendingImportRules != null) {
        var showImportDialog by remember { mutableStateOf(true) }
        val isDuplicateName = localCustomSources.any { it.displayName == pendingImportName.trim() }
        val finishImportDialog = {
            pendingImportRules = null
            pendingImportFileName = ""
            pendingImportName = ""
        }
        WindowDialog(
            title = "导入规则",
            show = showImportDialog,
            onDismissRequest = { showImportDialog = false },
            onDismissFinished = finishImportDialog,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("规则名称", style = MiuixTheme.textStyles.body2)
                TextField(
                    value = pendingImportName,
                    onValueChange = { pendingImportName = it },
                    label = "规则名称",
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = {
                            performHaptic()
                            showImportDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            performHaptic()
                            scope.launch {
                                val rulesToImport = pendingImportRules ?: return@launch
                                val newId = RecognitionRuleEngine.importLocalCustomSource(context, rulesToImport, pendingImportName.ifBlank { pendingImportFileName }, rulesToImport.jsonPackage)
                                val (local, custom, online) = RecognitionRuleEngine.loadAllSourcesSystem(context)
                                localConfig = local
                                localCustomSources = custom
                                onlineSources = online
                                customSourceRules = customSourceRules + (newId to rulesToImport)
                                rules = RecognitionRuleEngine.rules
                                showImportDialog = false
                                Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        enabled = pendingImportName.isNotBlank() && !isDuplicateName
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

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
    var expanded by remember { mutableStateOf(false) }
    var globalPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .squircleSurface(
                if (isActive) MiuixTheme.colorScheme.primaryContainer else Color.Transparent,
                12.dp,
            )
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
            }
            .padding(horizontal = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = if (isActive) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.body2,
                    color = if (isActive) MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
                    animationSpec = spring()
                )
                Icon(
                    imageVector = MiuixIcons.Regular.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).rotate(arrowRotation)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded && expandable,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
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
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                            Text(
                                text = "$value",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else if (noDataText != null) {
                    Text(
                        text = noDataText,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
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
    if (isDestructive) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            colors = ButtonDefaults.buttonColors()
        ) {
            Text(text, color = MiuixTheme.colorScheme.error)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            colors = ButtonDefaults.buttonColorsPrimary()
        ) {
            Text(text)
        }
    }
}

@Composable
private fun OnlineSourceWindowSheet(
    source: OnlineRuleSource?,
    performHaptic: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (OnlineRuleSource) -> Unit
) {
    var name by remember(source?.id) { mutableStateOf(source?.name ?: "") }
    var url by remember(source?.id) { mutableStateOf(source?.url ?: "") }
    var autoUpdate by remember(source?.id) { mutableStateOf(source?.enabled ?: false) }
    var updateIntervalMinutes by remember(source?.id) { mutableStateOf("${source?.updateIntervalMinutes ?: 1440}") }

    var showSheet by remember { mutableStateOf(true) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val sheetHeightPx = remember { with(density) { configuration.screenHeightDp.dp.toPx() } }
    val blurProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    var dragProgress by remember { androidx.compose.runtime.mutableFloatStateOf(-1f) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { blurProgress.value }
            .collect { com.Badnng.moe.ui.component.BlurState.updateProgress(it) }
    }

    // 打开动画
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.Badnng.moe.ui.component.BlurState.show()
        blurProgress.snapTo(0f)
        blurProgress.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 300f)
        )
    }

    // 拖拽时 snapTo 覆盖
    androidx.compose.runtime.LaunchedEffect(dragProgress) {
        if (dragProgress in 0f..1f) {
            blurProgress.snapTo(dragProgress)
        }
    }

    // 关闭时淡出模糊
    androidx.compose.runtime.LaunchedEffect(showSheet) {
        if (!showSheet) {
            blurProgress.snapTo(blurProgress.value)
            blurProgress.animateTo(
                targetValue = 0f,
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 300f)
            )
        }
    }

    top.yukonga.miuix.kmp.window.WindowBottomSheet(
        show = showSheet,
        title = if (source != null) "修改配置" else "添加规则源",
        enableWindowDim = false,
        allowDismiss = true,
        enableNestedScroll = true,
        onDismissRequest = {
            showSheet = false
        },
        onDismissFinished = {
            com.Badnng.moe.ui.component.BlurState.hide()
            onDismiss()
        }
    ) {
        com.Badnng.moe.ui.theme.NonPredictiveBackInterceptor()
        if (showSheet) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.dp)
                    .onGloballyPositioned { coords ->
                        val boxTop = coords.localToWindow(androidx.compose.ui.geometry.Offset(0f, 0f)).y
                        dragProgress = (1f - (boxTop / sheetHeightPx).coerceIn(0f, 1f))
                    }
            )
        }

        val dismiss = top.yukonga.miuix.kmp.theme.LocalDismissState.current
        val indicationColor = MiuixTheme.colorScheme.onBackground
        val miuixIndication = remember(indicationColor) { top.yukonga.miuix.kmp.utils.MiuixIndication(color = indicationColor) }
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.LocalIndication provides miuixIndication
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextField(value = name, onValueChange = { name = it }, label = "名称", modifier = Modifier.fillMaxWidth())
                TextField(value = url, onValueChange = { url = it }, label = "URL", modifier = Modifier.fillMaxWidth())
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "自动更新", modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.body1)
                    Switch(checked = autoUpdate, onCheckedChange = { performHaptic(); autoUpdate = it })
                }
                TextField(
                    value = updateIntervalMinutes,
                    onValueChange = { updateIntervalMinutes = it.filter { c -> c.isDigit() } },
                    label = "更新间隔 (分钟)",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = autoUpdate
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { dismiss?.invoke() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors()
                    ) {
                        Text("取消")
                    }
                    Button(
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
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        enabled = name.isNotBlank() && url.isNotBlank()
                    ) {
                        Text("保存")
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
