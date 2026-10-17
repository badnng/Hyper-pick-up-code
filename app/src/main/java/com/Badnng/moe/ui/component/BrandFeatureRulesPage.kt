@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.Badnng.moe.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Badnng.moe.recognition.BrandFeatureRecognition
import com.Badnng.moe.rules.BrandFeatureMatcher
import com.Badnng.moe.rules.BrandFeatureRule
import com.Badnng.moe.rules.BrandFeatureWord
import com.Badnng.moe.rules.PickupWordRulePack
import com.Badnng.moe.rules.PickupWordRuleRepository
import com.Badnng.moe.rules.RecognitionRuleEngine
import com.Badnng.moe.ui.LocalAppUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication

/** 品牌列表页：每个品牌内联输入框，样式与自定义取件地点保持一致。 */
@Composable
fun BrandFeatureRulesPage(
    contentPadding: PaddingValues,
    isMiuix: Boolean,
    performHaptic: () -> Unit,
    onOpenBrand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appUi = LocalAppUi.current
    val context = LocalContext.current
    val repository = remember { PickupWordRuleRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var pack by remember { mutableStateOf(PickupWordRulePack.empty()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var newBrand by rememberSaveable { mutableStateOf("") }
    var testText by rememberSaveable { mutableStateOf("") }
    var testType by rememberSaveable { mutableStateOf("饮品") }
    var testReport by rememberSaveable { mutableStateOf<String?>(null) }
    val revision by PickupWordRuleRepository.changes.collectAsStateWithLifecycle()

    LaunchedEffect(revision) {
        try {
            RecognitionRuleEngine.initialize(context.applicationContext)
            pack = repository.load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            pageError = "读取失败：${failure.message}"
        } finally {
            loading = false
        }
    }
    val brandNames = remember(pack, loading) { BrandFeatureRecognition.editableNames(pack) }

    fun saveBrandWords(brandName: String, updated: BrandFeatureRule?) {
        if (saving) return
        saving = true
        pageError = null
        scope.launch {
            try {
                val latest = repository.load()
                val remaining = latest.brandFeatures.filterNot {
                    BrandFeatureMatcher.normalize(it.name) == BrandFeatureMatcher.normalize(brandName)
                }
                pack = repository.saveBrandFeatures(
                    if (updated == null) remaining else remaining + updated
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                pageError = "保存失败：${failure.message}"
            } finally {
                saving = false
            }
        }
    }

    LazyColumn(
        modifier = modifier.brandFeaturePageInsets(),
        contentPadding = PaddingValues(top = contentPadding.calculateTopPadding()),
        verticalArrangement = if (isMiuix) Arrangement.Top else Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(12.dp)) }
        item {
            appUi.messageBlock("命中不同词更多的品牌优先，品牌名称也算一个词。同数时优先名称命中的品牌；仍无法区分则保留原结果。瑞幸使用内置专用算法。", false)
            Spacer(Modifier.height(12.dp))
        }
        if (loading) {
            item { appUi.messageBlock("正在读取品牌…", false) }
        } else {
            items(brandNames, key = { BrandFeatureMatcher.normalize(it) }) { brandName ->
                val rule = pack.brandFeatures.firstOrNull {
                    BrandFeatureMatcher.normalize(it.name) == BrandFeatureMatcher.normalize(brandName)
                }
                BrandInlineCard(
                    brandName = brandName,
                    rule = rule,
                    isMiuix = isMiuix,
                    saving = saving,
                    onAdd = { word ->
                        val current = rule ?: BrandFeatureRule(name = brandName)
                        saveBrandWords(brandName, current.copy(words = current.words + BrandFeatureWord(word)))
                    },
                    onRemove = { word ->
                        performHaptic()
                        val current = rule ?: BrandFeatureRule(name = brandName)
                        saveBrandWords(brandName, current.copy(words = current.words.filterNot { it.word == word }))
                    },
                    performHaptic = performHaptic,
                )
                Spacer(Modifier.height(12.dp))
            }
            item {
                Spacer(Modifier.height(4.dp))
                appUi.ruleTextInput(
                    "其他饮品、餐食品牌", newBrand, true,
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                ) { if (!saving) newBrand = it }
                Spacer(Modifier.height(12.dp))
                appUi.primaryActionButton(
                    if (saving) "正在添加…" else "添加品牌",
                    newBrand.isNotBlank() && !saving,
                ) {
                    performHaptic()
                    val name = newBrand.trim()
                    val existing = brandNames.firstOrNull {
                        BrandFeatureMatcher.normalize(it) == BrandFeatureMatcher.normalize(name)
                    }
                    if (existing != null) {
                        onOpenBrand(existing)
                    } else if (BrandFeatureMatcher.usesDedicatedRecognition(name)) {
                        pageError = "瑞幸使用内置专用算法，无需添加品牌特征词"
                    } else if (!BrandFeatureRecognition.isFoodBrandName(name)) {
                        pageError = "请填写具体的饮品或餐食品牌"
                    } else {
                        saving = true
                        scope.launch {
                            try {
                                pack = repository.saveBrandFeatures(
                                    pack.brandFeatures + BrandFeatureRule(name = name)
                                )
                                newBrand = ""
                                onOpenBrand(name)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                pageError = "保存失败：${failure.message}"
                            } finally {
                                saving = false
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            item {
                appUi.ruleTextInput(
                    "试识别文本", testText, false,
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                ) { testText = it; testReport = null }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("饮品", "餐食").forEach { type ->
                        appUi.choiceChip(type, testType == type, {
                            performHaptic(); testType = type; testReport = null
                        }, Modifier)
                    }
                }
                Spacer(Modifier.height(12.dp))
                appUi.primaryActionButton("试识别", testText.isNotBlank() && !saving) {
                    performHaptic()
                    scope.launch {
                        try {
                            pack = repository.load()
                            val ranked = BrandFeatureMatcher.rank(
                                testText, testType, pack.brandFeatures, BrandFeatureRecognition.knownNames(pack)
                            )
                            val winner = BrandFeatureMatcher.winner(ranked)
                            testReport = when {
                                ranked.isEmpty() -> "没有命中，保留原识别结果。"
                                winner == null -> "命中数相同，品牌名称也无法区分，保留原识别结果。\n\n"
                                else -> "判定品牌：${winner.name}\n\n"
                            } + ranked.joinToString("\n") { BrandFeatureRecognition.describe(it) }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            pageError = "试识别失败：${failure.message}"
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            testReport?.let { report ->
                item { appUi.messageBlock(report, false) }
            }
        }
        pageError?.let { message ->
            item { appUi.messageBlock(message, true) }
        }
        item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
    }
}

/** 品牌内联编辑卡片，样式与自定义取件地点保持一致。 */
@Composable
private fun BrandInlineCard(
    brandName: String,
    rule: BrandFeatureRule?,
    isMiuix: Boolean,
    saving: Boolean,
    onAdd: (word: String) -> Unit,
    onRemove: (word: String) -> Unit,
    performHaptic: () -> Unit,
) {
    var input by rememberSaveable(brandName) { mutableStateOf("") }
    var error by rememberSaveable(brandName) { mutableStateOf<String?>(null) }

    val words = rule?.words ?: emptyList()
    val enabled = !saving

    fun tryAdd() {
        performHaptic()
        val word = input.trim()
        error = when {
            word.isBlank() -> "请填写特征词"
            words.any { BrandFeatureMatcher.normalize(it.word) == BrandFeatureMatcher.normalize(word) } ->
                "该词已存在"
            else -> null
        }
        if (error == null) {
            onAdd(word)
            input = ""
        }
    }

    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp)
        .padding(bottom = 12.dp)

    if (isMiuix) {
        MiuixCard(modifier = cardModifier) {
            Column(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiuixText(
                    brandName,
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (words.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        words.forEach { word ->
                            MiuixWordChip(
                                label = word.word,
                                enabled = enabled,
                                onRemove = { onRemove(word.word) },
                            )
                        }
                    }
                }
                val inputMod = Modifier.fillMaxWidth().onPreviewKeyEvent { evt ->
                    if (enabled && evt.type == KeyEventType.KeyUp && evt.key == Key.Enter) {
                        tryAdd(); true
                    } else false
                }
                MiuixTextField(
                    value = input,
                    onValueChange = { if (enabled) input = it.replace("\n", "") },
                    label = "添加特征词",
                    modifier = inputMod,
                    singleLine = true,
                    trailingIcon = {
                        MiuixIconButton(onClick = { tryAdd() }, enabled = enabled) {
                            MiuixIcon(MiuixIcons.Regular.Add, contentDescription = "添加特征词")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { tryAdd() }),
                )
                if (error != null) {
                    MiuixText(
                        error!!,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                } else {
                    MiuixText(
                        "点加号或键盘完成添加，点击标签删除，修改即时保存。",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    } else {
        Surface(
            modifier = cardModifier,
            shape = RoundedCornerShape(15.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    brandName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (words.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        words.forEach { word ->
                            InputChip(
                                selected = false,
                                enabled = enabled,
                                onClick = { onRemove(word.word) },
                                label = {
                                    Text(
                                        word.word,
                                        modifier = Modifier.widthIn(max = 240.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除特征词 ${word.word}",
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                shape = RoundedCornerShape(15.dp),
                            )
                        }
                    }
                }
                val inputMod = Modifier.fillMaxWidth().onPreviewKeyEvent { evt ->
                    if (enabled && evt.type == KeyEventType.KeyUp && evt.key == Key.Enter) {
                        tryAdd(); true
                    } else false
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (enabled) input = it.replace("\n", "") },
                    enabled = enabled,
                    label = { Text("添加特征词") },
                    modifier = inputMod,
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { tryAdd() }, enabled = enabled) {
                            Icon(Icons.Default.Add, contentDescription = "添加特征词")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { tryAdd() }),
                    shape = RoundedCornerShape(15.dp),
                )
                if (error != null) {
                    Text(
                        error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        "点加号或键盘完成添加，点击标签删除，修改即时保存。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiuixWordChip(
    label: String,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val indicationColor = MiuixTheme.colorScheme.onSurface
    val indication = remember(indicationColor) { MiuixIndication(color = indicationColor) }
    Row(
        modifier = Modifier
            .squircleSurface(MiuixTheme.colorScheme.surfaceContainer, 15.dp)
            .squircleClip(15.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = indication,
                enabled = enabled,
                role = Role.Button,
                onClick = onRemove,
            )
            .semantics {
                role = Role.Button
                contentDescription = "删除特征词 $label"
            }
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiuixText(
            label,
            modifier = Modifier.widthIn(max = 240.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        MiuixIcon(
            MiuixIcons.Regular.Close,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 每个品牌都有独立导航目的地；添加、移除、开关操作直接保存。 */
@Composable
fun BrandFeatureBrandPage(
    brandName: String,
    contentPadding: PaddingValues,
    performHaptic: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appUi = LocalAppUi.current
    val context = LocalContext.current
    val repository = remember { PickupWordRuleRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var pack by remember { mutableStateOf(PickupWordRulePack.empty()) }
    var rule by remember(brandName) { mutableStateOf(BrandFeatureRule(name = brandName)) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var input by rememberSaveable(brandName) { mutableStateOf("") }
    var deletePending by rememberSaveable(brandName) { mutableStateOf(false) }

    LaunchedEffect(brandName) {
        try {
            pack = repository.load()
            rule = pack.brandFeatures.firstOrNull {
                BrandFeatureMatcher.normalize(it.name) == BrandFeatureMatcher.normalize(brandName)
            } ?: BrandFeatureRule(name = brandName)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "读取失败：${failure.message}"
        } finally {
            loading = false
        }
    }

    fun save(updated: BrandFeatureRule?, onSaved: () -> Unit = {}) {
        if (saving || loading || BrandFeatureMatcher.usesDedicatedRecognition(brandName)) return
        saving = true
        error = null
        scope.launch {
            try {
                val latest = repository.load()
                val remaining = latest.brandFeatures.filterNot {
                    BrandFeatureMatcher.normalize(it.name) == BrandFeatureMatcher.normalize(brandName)
                }
                pack = repository.saveBrandFeatures(if (updated == null) remaining else remaining + updated)
                if (updated != null) rule = updated
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = "保存失败：${failure.message}"
            } finally {
                saving = false
            }
        }
    }

    LazyColumn(
        modifier = modifier.brandFeaturePageInsets(),
        contentPadding = PaddingValues(top = contentPadding.calculateTopPadding()),
    ) {
        item { Spacer(Modifier.height(12.dp)) }
        if (loading) {
            item { appUi.messageBlock("正在读取特征词…", false) }
        } else if (BrandFeatureMatcher.usesDedicatedRecognition(brandName)) {
            item { appUi.messageBlock("瑞幸使用内置专用识别算法，不参与品牌特征词判定。", false) }
        } else {
            item {
                appUi.preferenceSwitchItem("启用特征词", "命中后判定为$brandName", rule.enabled) {
                    if (!saving) { performHaptic(); save(rule.copy(enabled = it)) }
                }
                Spacer(Modifier.height(12.dp))
            }
            item {
                Box(Modifier.padding(horizontal = 12.dp)) {
                    appUi.brandFeatureWordsEditor(
                        rule.words, input, !saving,
                        { input = it },
                        {
                            performHaptic()
                            val word = input.trim()
                            error = when {
                                word.isBlank() -> "请填写特征词"
                                rule.words.any {
                                    BrandFeatureMatcher.normalize(it.word) == BrandFeatureMatcher.normalize(word)
                                } -> "该词已存在"
                                else -> null
                            }
                            if (error == null) {
                                save(rule.copy(words = rule.words + BrandFeatureWord(word))) { input = "" }
                            }
                        },
                        { word ->
                            performHaptic()
                            save(rule.copy(words = rule.words.filterNot { it.word == word }))
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            error?.let { message ->
                item { appUi.messageBlock(message, true); Spacer(Modifier.height(12.dp)) }
            }
            item {
                appUi.messageBlock(
                    "品牌名称自动算作一个特征词，无需重复添加。命中不同词更多的品牌优先；同数时优先名称命中的品牌，仍分不清则保留原结果。重复出现或互相包含的词只计一次。",
                    false,
                )
                Spacer(Modifier.height(24.dp))
            }
            if (BrandFeatureRecognition.editableNames(pack.copy(brandFeatures = emptyList())).none {
                    BrandFeatureMatcher.normalize(it) == BrandFeatureMatcher.normalize(brandName)
                }) {
                item {
                    appUi.primaryActionButton(
                        if (deletePending) "确认删除此自定义品牌" else "删除自定义品牌",
                        !saving,
                    ) {
                        performHaptic()
                        if (deletePending) save(null, onBack) else deletePending = true
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
    }
}

@Composable
private fun Modifier.brandFeaturePageInsets(): Modifier = fillMaxSize()
    .windowInsetsPadding(
        WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Horizontal)
    )
    .imePadding()
