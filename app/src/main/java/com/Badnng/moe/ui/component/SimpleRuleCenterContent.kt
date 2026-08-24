package com.Badnng.moe.ui.component

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.helper.BrandIconResolver
import com.Badnng.moe.recognition.RecognitionCorrectionDetector
import com.Badnng.moe.recognition.RecognitionInputType
import com.Badnng.moe.rules.*
import com.Badnng.moe.ui.LocalAppUi
import com.Badnng.moe.ui.component.GroupPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.squircle.squircleSurface

sealed interface SimpleRuleCenterPage {
    data object Root : SimpleRuleCenterPage
    data class Category(val category: SimpleRuleCategory) : SimpleRuleCenterPage
    data class CreateBrand(val category: SimpleRuleCategory) : SimpleRuleCenterPage
    data class Brand(val brandId: String) : SimpleRuleCenterPage
    data class CreateTemplate(val brandId: String) : SimpleRuleCenterPage
    data class Template(val brandId: String, val templateId: String) : SimpleRuleCenterPage
    data object BlockedWords : SimpleRuleCenterPage
    data object CustomIcons : SimpleRuleCenterPage
}

@Stable
class SimpleRuleCenterState(initialPage: SimpleRuleCenterPage = SimpleRuleCenterPage.Root) {
    var stack by mutableStateOf<List<SimpleRuleCenterPage>>(listOf(initialPage))
        private set

    val page: SimpleRuleCenterPage get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1
    var title by mutableStateOf(initialPage.title(SimpleRulePack.empty()))
        private set

    fun updateTitle(value: String) { title = value }

    fun open(page: SimpleRuleCenterPage) { stack = stack + page }
    fun replaceCurrent(page: SimpleRuleCenterPage) { stack = stack.dropLast(1) + page }
    fun back(): Boolean {
        if (!canGoBack) return false
        stack = stack.dropLast(1)
        return true
    }

    fun reset() { stack = listOf(SimpleRuleCenterPage.Root) }
}

@Composable
fun rememberSimpleRuleCenterState(
    initialPage: SimpleRuleCenterPage = SimpleRuleCenterPage.Root,
): SimpleRuleCenterState = remember(initialPage) { SimpleRuleCenterState(initialPage) }

fun SimpleRuleCenterPage.title(pack: SimpleRulePack): String = when (this) {
    SimpleRuleCenterPage.Root -> "规则"
    is SimpleRuleCenterPage.Category -> category.displayName
    is SimpleRuleCenterPage.CreateBrand -> "添加${category.displayName}品牌"
    is SimpleRuleCenterPage.Brand -> pack.brands.firstOrNull { it.id == brandId }?.name ?: "品牌规则"
    is SimpleRuleCenterPage.CreateTemplate -> "添加识别模板"
    is SimpleRuleCenterPage.Template -> pack.brands.firstOrNull { it.id == brandId }
        ?.templates?.firstOrNull { it.id == templateId }?.name ?: "识别模板"
    SimpleRuleCenterPage.BlockedWords -> "自定义屏蔽词"
    SimpleRuleCenterPage.CustomIcons -> "自定义图标"
}

@Composable
fun SimpleRuleCenterContent(
    state: SimpleRuleCenterState,
    contentPadding: PaddingValues,
    isMiuix: Boolean,
    modifier: Modifier = Modifier,
    onOpenPage: ((SimpleRuleCenterPage) -> Unit)? = null,
    onReplacePage: ((SimpleRuleCenterPage) -> Unit)? = null,
    onBackPage: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val appUi = LocalAppUi.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val performHaptic = {
        if (prefs.getBoolean("haptic_enabled", true)) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
    }
    fun openPage(page: SimpleRuleCenterPage) {
        onOpenPage?.invoke(page) ?: state.open(page)
    }
    fun replacePage(page: SimpleRuleCenterPage) {
        onReplacePage?.invoke(page) ?: state.replaceCurrent(page)
    }
    fun backPage() {
        onBackPage?.invoke() ?: state.back()
    }
    val repository = remember { SimpleRuleRepository(context.applicationContext) }
    var pack by remember { mutableStateOf(SimpleRulePack.empty()) }
    var loading by remember { mutableStateOf(true) }
    var deletingRuleKey by remember { mutableStateOf<String?>(null) }
    var creatingRuleKey by remember { mutableStateOf<String?>(null) }
    var pendingSaveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun save(newPack: SimpleRulePack) {
        // 先更新界面，再取消上一次待写入任务并重新防抖，避免旧快照覆盖新规则。
        val updatedPack = newPack.copy(updatedAt = System.currentTimeMillis())
        pack = updatedPack
        pendingSaveJob?.cancel()
        pendingSaveJob = scope.launch {
            kotlinx.coroutines.delay(250L)
            runCatching { repository.save(updatedPack) }
                .onFailure { Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    fun deleteAndBack(ruleKey: String, newPack: SimpleRulePack) {
        if (deletingRuleKey != null) return
        deletingRuleKey = ruleKey
        pendingSaveJob?.cancel()
        pendingSaveJob = null
        val updatedPack = newPack.copy(updatedAt = System.currentTimeMillis())
        scope.launch {
            runCatching { repository.save(updatedPack) }
                .onSuccess {
                    pack = updatedPack
                    backPage()
                }
                .onFailure {
                    Toast.makeText(context, "删除失败：${it.message}", Toast.LENGTH_LONG).show()
                }
            deletingRuleKey = null
        }
    }

    /**
     * 创建品牌/模板后必须先落盘再跳转。规则页在 Miuix 下每个二级页是独立入口，
     * 新页面进入（或返回）时会重新从磁盘加载规则包；若沿用防抖的 [save]，
     * 目标页面会先于 250ms 防抖写盘读到旧数据，导致品牌/模板详情页空白，
     * 且页面销毁时防抖任务还会被取消、数据丢失。
     */
    fun saveAndNavigate(ruleKey: String, newPack: SimpleRulePack, onSaved: () -> Unit) {
        if (creatingRuleKey != null) return
        creatingRuleKey = ruleKey
        pendingSaveJob?.cancel()
        pendingSaveJob = null
        val updatedPack = newPack.copy(updatedAt = System.currentTimeMillis())
        scope.launch {
            runCatching { repository.save(updatedPack) }
                .onSuccess {
                    pack = updatedPack
                    onSaved()
                }
                .onFailure {
                    Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_LONG).show()
                }
            creatingRuleKey = null
        }
    }

    LaunchedEffect(Unit) {
        pack = repository.load()
        state.updateTitle(state.page.title(pack))
        loading = false
    }
    LaunchedEffect(state.page, pack) { state.updateTitle(state.page.title(pack)) }

    BackHandler(enabled = onBackPage == null && state.canGoBack) { backPage() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("无法读取文件") }
                    .fold(onSuccess = { repository.importJson(it) }, onFailure = { Result.failure(it) })
            }
            result.onSuccess {
                pack = it
                state.reset()
                Toast.makeText(context, "已导入 ${it.brands.size} 个品牌规则", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(repository.exportJson(pack)) }
                        ?: error("无法写入文件")
                }
            }.onSuccess { Toast.makeText(context, "规则已导出", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    if (loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isMiuix) top.yukonga.miuix.kmp.basic.Text("正在读取规则…") else Text("正在读取规则…")
        }
        return
    }

    when (val page = state.page) {
        SimpleRuleCenterPage.Root -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = if (isMiuix) Arrangement.Top else Arrangement.spacedBy(12.dp),
        ) {
            item {
                RuleSectionTitle("识别规则", isMiuix)
                appUi.settingsGroup(if (isMiuix) Modifier else Modifier.padding(horizontal = 12.dp)) {
                    SimpleRuleCategory.entries.forEachIndexed { index, category ->
                        val count = pack.brands.count { it.category == category }
                        appUi.settingsGroupItem(
                            category.displayName,
                            if (count == 0) "暂无品牌规则" else "$count 个品牌",
                            groupPosition(index, SimpleRuleCategory.entries.size),
                            { performHaptic(); openPage(SimpleRuleCenterPage.Category(category)) },
                            null,
                        )
                    }
                }
            }
            item {
                RuleSectionTitle("辅助规则", isMiuix)
                appUi.settingsGroup(if (isMiuix) Modifier else Modifier.padding(horizontal = 12.dp)) {
                    appUi.settingsGroupItem(
                        "自定义图标",
                        "为识别结果设置品牌图标",
                        GroupPosition.First,
                        { performHaptic(); openPage(SimpleRuleCenterPage.CustomIcons) },
                        null,
                    )
                    val blockedCount = rememberBlockedWordsEditorState().words.size
                    appUi.settingsGroupItem(
                        "自定义屏蔽词",
                        if (blockedCount == 0) "未设置" else "$blockedCount 个词条",
                        GroupPosition.Last,
                        { performHaptic(); openPage(SimpleRuleCenterPage.BlockedWords) },
                        null,
                    )
                }
            }
            item {
                RuleSectionTitle("规则数据", isMiuix)
                appUi.settingsGroup(if (isMiuix) Modifier else Modifier.padding(horizontal = 12.dp)) {
                    appUi.settingsGroupItem(
                        "导入规则",
                        "仅支持新的完整规则包 v${SimpleRulePack.SCHEMA_VERSION}",
                        GroupPosition.First,
                        { performHaptic(); importLauncher.launch(arrayOf("application/json", "text/plain")) },
                        if (isMiuix) null else ({ Icon(Icons.Default.FileUpload, contentDescription = null) }),
                    )
                    appUi.settingsGroupItem(
                        "导出规则",
                        "导出后可交给应用内置",
                        GroupPosition.Last,
                        { performHaptic(); exportLauncher.launch("澎湃记识别规则-v${SimpleRulePack.SCHEMA_VERSION}.json") },
                        if (isMiuix) null else ({ Icon(Icons.Default.FileDownload, contentDescription = null) }),
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }

        is SimpleRuleCenterPage.Category -> CategoryPage(
            category = page.category,
            pack = pack,
            isMiuix = isMiuix,
            contentPadding = contentPadding,
            onOpen = { openPage(SimpleRuleCenterPage.Brand(it)) },
            onAdd = { openPage(SimpleRuleCenterPage.CreateBrand(page.category)) },
            performHaptic = performHaptic,
            modifier = modifier,
        )

        is SimpleRuleCenterPage.CreateBrand -> CreateBrandPage(
            category = page.category,
            isMiuix = isMiuix,
            contentPadding = contentPadding,
            onCreate = { brand ->
                // 保存成功后返回上一页（分类品牌列表），列表会重新加载并展示新品牌。
                saveAndNavigate(
                    ruleKey = "brand:${brand.id}",
                    newPack = pack.copy(brands = pack.brands + brand),
                ) {
                    backPage()
                }
            },
            performHaptic = performHaptic,
            modifier = modifier,
        )

        is SimpleRuleCenterPage.Brand -> BrandPage(
            brandId = page.brandId,
            pack = pack,
            isMiuix = isMiuix,
            contentPadding = contentPadding,
            onOpenTemplate = { openPage(SimpleRuleCenterPage.Template(page.brandId, it)) },
            onAddTemplate = { openPage(SimpleRuleCenterPage.CreateTemplate(page.brandId)) },
            onChange = ::save,
            deleteEnabled = deletingRuleKey == null,
            onDeleted = {
                deleteAndBack(
                    ruleKey = "brand:${page.brandId}",
                    newPack = pack.copy(brands = pack.brands.filterNot { it.id == page.brandId }),
                )
            },
            performHaptic = performHaptic,
            modifier = modifier,
        )

        is SimpleRuleCenterPage.CreateTemplate -> CreateTemplatePage(
            brandId = page.brandId,
            pack = pack,
            isMiuix = isMiuix,
            contentPadding = contentPadding,
            onCreate = { template ->
                val brand = pack.brands.firstOrNull { it.id == page.brandId } ?: return@CreateTemplatePage
                saveAndNavigate(
                    ruleKey = "template:${template.id}",
                    newPack = pack.updateBrand(brand.copy(templates = brand.templates + template)),
                ) {
                    replacePage(SimpleRuleCenterPage.Template(page.brandId, template.id))
                }
            },
            performHaptic = performHaptic,
            modifier = modifier,
        )

        is SimpleRuleCenterPage.Template -> TemplatePage(
            brandId = page.brandId,
            templateId = page.templateId,
            pack = pack,
            isMiuix = isMiuix,
            contentPadding = contentPadding,
            onChange = ::save,
            deleteEnabled = deletingRuleKey == null,
            onDeleted = {
                val brand = pack.brands.firstOrNull { it.id == page.brandId } ?: return@TemplatePage
                deleteAndBack(
                    ruleKey = "template:${page.templateId}",
                    newPack = pack.updateBrand(
                        brand.copy(templates = brand.templates.filterNot { it.id == page.templateId }),
                    ),
                )
            },
            performHaptic = performHaptic,
            modifier = modifier,
        )

        SimpleRuleCenterPage.BlockedWords -> {
            val blockedState = rememberBlockedWordsEditorState()
            LazyColumn(modifier.fillMaxSize(), contentPadding = contentPadding) {
                item {
                    RuleSectionTitle("过滤短信与通知", isMiuix)
                    Box(Modifier.padding(horizontal = 12.dp)) {
                        appUi.blockedWordsEditor(blockedState, performHaptic)
                    }
                }
            }
        }

        SimpleRuleCenterPage.CustomIcons -> CustomIconsPage(
            isMiuix = isMiuix,
            contentPadding = contentPadding,
            performHaptic = performHaptic,
            modifier = modifier,
        )
    }
}

@Composable
private fun CategoryPage(
    category: SimpleRuleCategory,
    pack: SimpleRulePack,
    isMiuix: Boolean,
    contentPadding: PaddingValues,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    performHaptic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appUi = LocalAppUi.current
    val brands = pack.brands.filter { it.category == category }
    LazyColumn(modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = if (isMiuix) Arrangement.Top else Arrangement.spacedBy(12.dp)) {
        item {
            RuleSectionTitle("${category.displayName}品牌", isMiuix)
            if (brands.isEmpty()) {
                EmptyRuleHint("暂无品牌，点击下方按钮添加", isMiuix)
            } else {
                appUi.settingsGroup(if (isMiuix) Modifier else Modifier.padding(horizontal = 12.dp)) {
                    brands.forEachIndexed { index, brand ->
                        appUi.settingsGroupItem(
                            brand.name,
                            "${brand.keywords.size} 个关键词 · ${brand.templates.size} 个模板",
                            groupPosition(index, brands.size),
                            { performHaptic(); onOpen(brand.id) },
                            null,
                        )
                    }
                }
            }
        }
        item {
            Box(if (isMiuix) Modifier else Modifier.padding(horizontal = 12.dp)) {
                LocalAppUi.current.primaryActionButton("添加${category.displayName}品牌", true) {
                    performHaptic()
                    onAdd()
                }
            }
        }
    }
}

@Composable
private fun CreateBrandPage(
    category: SimpleRuleCategory,
    isMiuix: Boolean,
    contentPadding: PaddingValues,
    onCreate: (SimpleBrandRule) -> Unit,
    performHaptic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var keywords by rememberSaveable { mutableStateOf("") }
    var packageNames by rememberSaveable { mutableStateOf("") }
    var qrPatterns by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = if (isMiuix) Arrangement.Top else Arrangement.spacedBy(12.dp),
    ) {
        item {
            RuleSectionTitle("新建${category.displayName}品牌", isMiuix)
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RuleTextField("品牌名称", name, isMiuix, singleLine = true) { name = it }
                RuleTextField("固定关键词（每行一个）", keywords, isMiuix, minLines = 3) { keywords = it }
                RuleTextField("来源包名（每行一个，可不填）", packageNames, isMiuix, minLines = 2) { packageNames = it }
                RuleTextField("二维码正则（每行一个，可不填）", qrPatterns, isMiuix, minLines = 2) { qrPatterns = it }
                RuleHelp("创建完成后返回品牌列表，之后可进入该品牌详情添加专属识别模板。", isMiuix)
            }
        }
        item {
            Box(if (isMiuix) Modifier else Modifier.padding(horizontal = 12.dp)) {
                LocalAppUi.current.primaryActionButton("创建品牌", name.isNotBlank()) {
                    performHaptic()
                    onCreate(
                        SimpleBrandRule(
                            category = category,
                            name = name.trim(),
                            keywords = keywords.linesClean(),
                            packageNames = packageNames.linesClean(),
                            qrPatterns = qrPatterns.linesClean(),
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandPage(
    brandId: String,
    pack: SimpleRulePack,
    isMiuix: Boolean,
    contentPadding: PaddingValues,
    onOpenTemplate: (String) -> Unit,
    onAddTemplate: () -> Unit,
    onChange: (SimpleRulePack) -> Unit,
    deleteEnabled: Boolean,
    onDeleted: () -> Unit,
    performHaptic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appUi = LocalAppUi.current
    val brand = pack.brands.firstOrNull { it.id == brandId } ?: return
    fun update(value: SimpleBrandRule) = onChange(pack.updateBrand(value))

    LazyColumn(modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = if (isMiuix) Arrangement.Top else Arrangement.spacedBy(12.dp)) {
        item {
            RuleSectionTitle("品牌", isMiuix)
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RuleTextField("品牌名称", brand.name, isMiuix, singleLine = true) { update(brand.copy(name = it)) }
                appUi.preferenceSwitchItem("启用品牌规则", "关闭后不会参与识别", brand.enabled) {
                    performHaptic(); update(brand.copy(enabled = it))
                }
            }
        }
        item {
            RuleSectionTitle("品牌识别", isMiuix)
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RuleTextField("固定关键词（每行一个）", brand.keywords.joinToString("\n"), isMiuix, minLines = 3) {
                    update(brand.copy(keywords = it.linesClean()))
                }
                RuleTextField("来源包名（每行一个，可不填）", brand.packageNames.joinToString("\n"), isMiuix, minLines = 2) {
                    update(brand.copy(packageNames = it.linesClean()))
                }
                RuleTextField("二维码正则（每行一个，可不填）", brand.qrPatterns.joinToString("\n"), isMiuix, minLines = 2) {
                    update(brand.copy(qrPatterns = it.linesClean()))
                }
                RuleHelp("命中品牌名称、任一固定关键词、来源包名或二维码规则后，只执行该品牌下方的模板。", isMiuix)
            }
        }
        item {
            RuleSectionTitle("识别模板", isMiuix)
            if (brand.templates.isEmpty()) {
                EmptyRuleHint("暂无模板", isMiuix)
            } else {
                appUi.settingsGroup(if (isMiuix) Modifier else Modifier.padding(horizontal = 12.dp)) {
                    brand.templates.forEachIndexed { index, template ->
                        appUi.settingsGroupItem(
                            template.name,
                            template.template,
                            groupPosition(index, brand.templates.size),
                            { performHaptic(); onOpenTemplate(template.id) },
                            null,
                        )
                    }
                }
            }
        }
        item {
            Box(if (isMiuix) Modifier.padding(bottom = 12.dp) else Modifier.padding(horizontal = 12.dp)) {
                appUi.primaryActionButton("添加识别模板", true) {
                    performHaptic()
                    onAddTemplate()
                }
            }
        }
        item {
            RuleDangerButton("删除此品牌", isMiuix, deleteEnabled) { performHaptic(); onDeleted() }
        }
    }
}

@Composable
private fun CreateTemplatePage(
    brandId: String,
    pack: SimpleRulePack,
    isMiuix: Boolean,
    contentPadding: PaddingValues,
    onCreate: (SimpleTemplateRule) -> Unit,
    performHaptic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appUi = LocalAppUi.current
    val brand = pack.brands.firstOrNull { it.id == brandId } ?: return
    var name by rememberSaveable { mutableStateOf("") }
    var templateText by rememberSaveable { mutableStateOf("") }
    var codeDigitsOnly by rememberSaveable { mutableStateOf(false) }
    var excludedWords by rememberSaveable { mutableStateOf("") }
    var sources by remember { mutableStateOf(emptySet<SimpleRuleSource>()) }
    val codePlaceholderCount = SimpleRuleTemplateCompiler.countCodePlaceholders(templateText)
    val locationPlaceholderCount = SimpleRuleTemplateCompiler.countLocationPlaceholders(templateText)
    val templateSyntaxValid = runCatching {
        SimpleRuleTemplateCompiler.compile(templateText, codeDigitsOnly)
    }.isSuccess
    val valid = name.isNotBlank() && codePlaceholderCount == 1 && locationPlaceholderCount <= 1 && templateSyntaxValid

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = if (isMiuix) Arrangement.Top else Arrangement.spacedBy(12.dp),
    ) {
        item {
            RuleSectionTitle("${brand.name} · 新模板", isMiuix)
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RuleTextField("模板名称", name, isMiuix, singleLine = true) { name = it }
                RuleTextField("匹配模板", templateText, isMiuix, minLines = 3) { templateText = it }
                RuleHelp(
                    "支持 {{code}}、{{code:digits:4}}（固定 4 位数字）、{{code:alnum:4-8}}（4-8 位字母数字）、{{location}} 和 {{any}}（忽略变化内容）。",
                    isMiuix,
                )
                appUi.preferenceSwitchItem("仅识别数字", "开启后 {{code}} 不会匹配文字或字母", codeDigitsOnly) {
                    performHaptic(); codeDigitsOnly = it
                }
                RuleTextField("排除词（每行一个）", excludedWords, isMiuix, minLines = 2) { excludedWords = it }
            }
        }
        item {
            RuleSectionTitle("适用来源", isMiuix)
            FlowRow(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SimpleRuleSource.entries.forEach { source ->
                    val selected = source in sources
                    appUi.choiceChip(source.displayName, selected, {
                        performHaptic()
                        sources = if (selected) sources - source else sources + source
                    }, Modifier)
                }
            }
            RuleHelp("未选择时适用于全部来源", isMiuix, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
        item {
            Box(if (isMiuix) Modifier else Modifier.padding(horizontal = 12.dp)) {
                appUi.primaryActionButton("创建模板", valid) {
                    performHaptic()
                    onCreate(
                        SimpleTemplateRule(
                            name = name.trim(),
                            template = templateText.trim(),
                            codeDigitsOnly = codeDigitsOnly,
                            excludedWords = excludedWords.linesClean(),
                            sources = sources,
                        )
                    )
                }
            }
        }
    }
}
@Composable
private fun TemplatePage(
    brandId: String,
    templateId: String,
    pack: SimpleRulePack,
    isMiuix: Boolean,
    contentPadding: PaddingValues,
    onChange: (SimpleRulePack) -> Unit,
    deleteEnabled: Boolean,
    onDeleted: () -> Unit,
    performHaptic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appUi = LocalAppUi.current
    val brand = pack.brands.firstOrNull { it.id == brandId } ?: return
    val template = brand.templates.firstOrNull { it.id == templateId } ?: return
    fun update(value: SimpleTemplateRule) = onChange(pack.updateBrand(brand.copy(
        templates = brand.templates.map { if (it.id == templateId) value else it }
    )))

    LazyColumn(modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = if (isMiuix) Arrangement.Top else Arrangement.spacedBy(12.dp)) {
        item {
            RuleSectionTitle("模板内容", isMiuix)
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RuleTextField("模板名称", template.name, isMiuix, singleLine = true) { update(template.copy(name = it)) }
                appUi.preferenceSwitchItem("启用模板", "关闭后保留但不执行", template.enabled) {
                    performHaptic(); update(template.copy(enabled = it))
                }
                RuleTextField("匹配模板", template.template, isMiuix, minLines = 3) { update(template.copy(template = it)) }
                RuleHelp(
                    "参数示例：{{code:digits:4}} 固定提取 4 位数字，{{code:alnum:4-8}} 提取 4-8 位字母数字，{{any}} 忽略链接等变化内容。",
                    isMiuix,
                )
                appUi.preferenceSwitchItem("仅识别数字", "开启后 {{code}} 不会匹配文字或字母", template.codeDigitsOnly) {
                    performHaptic(); update(template.copy(codeDigitsOnly = it))
                }
                RuleTextField("排除词（每行一个）", template.excludedWords.joinToString("\n"), isMiuix, minLines = 2) {
                    update(template.copy(excludedWords = it.linesClean()))
                }
            }
        }
        item {
            RuleSectionTitle("适用来源", isMiuix)
            FlowRow(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SimpleRuleSource.entries.forEach { source ->
                    val selected = source in template.sources
                    appUi.choiceChip(source.displayName, selected, {
                        performHaptic()
                        update(template.copy(sources = if (selected) template.sources - source else template.sources + source))
                    }, Modifier)
                }
            }
            RuleHelp(
                if (template.sources.isEmpty()) "未选择时适用于全部来源" else "仅在已选择的来源中执行",
                isMiuix,
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        item { RuleDangerButton("删除此模板", isMiuix, deleteEnabled) { performHaptic(); onDeleted() } }
    }
}

@Composable
private fun CustomIconsPage(
    isMiuix: Boolean,
    contentPadding: PaddingValues,
    performHaptic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var mappings by remember { mutableStateOf(BrandIconResolver.getCustomMappings(context)) }
    var pickingIndex by remember { mutableIntStateOf(-1) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val index = pickingIndex
        if (uri != null && index in mappings.indices) {
            BrandIconResolver.saveCustomIcon(context, uri)?.let { path ->
                val old = mappings[index]
                mappings = mappings.toMutableList().also { it[index] = old.copy(iconPath = path) }
                BrandIconResolver.saveCustomMappings(context, mappings)
            }
        }
        pickingIndex = -1
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = if (isMiuix) Arrangement.Top else Arrangement.spacedBy(12.dp)) {
        item {
            RuleSectionTitle("图标规则", isMiuix)
            RuleHelp("品牌名称包含对应关键词时使用该图标。多个关键词使用逗号分隔。", isMiuix, Modifier.padding(horizontal = 12.dp))
        }
        items(mappings.indices.toList(), key = { index -> "$index-${mappings[index].iconPath}" }) { index ->
            val mapping = mappings[index]
            RuleSurface(isMiuix, Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = if (isMiuix) {
                            Modifier
                                .size(58.dp)
                                .then(
                                    Modifier.squircleSurface(
                                        ruleMutedColor(true),
                                        15.dp,
                                    ),
                                )
                                .clickable { performHaptic(); pickingIndex = index; picker.launch("image/*") }
                        } else {
                            Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(ruleMutedColor(false))
                                .clickable { performHaptic(); pickingIndex = index; picker.launch("image/*") }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        val bitmap = remember(mapping.iconPath) { mapping.iconPath.takeIf(String::isNotBlank)?.let { BitmapFactory.decodeFile(it) } }
                        if (bitmap != null) {
                            Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize())
                        } else if (isMiuix) {
                            top.yukonga.miuix.kmp.basic.Icon(
                                top.yukonga.miuix.kmp.icon.MiuixIcons.Regular.Photos,
                                contentDescription = "选择图标",
                            )
                        } else {
                            Icon(Icons.Default.Image, contentDescription = "选择图标")
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    RuleTextField("品牌关键词", mapping.keywords, isMiuix, Modifier.weight(1f), singleLine = true) { value ->
                        mappings = mappings.toMutableList().also { it[index] = mapping.copy(keywords = value) }
                        BrandIconResolver.saveCustomMappings(context, mappings)
                    }
                    val deleteMapping = {
                        performHaptic(); BrandIconResolver.deleteCustomIcon(mapping.iconPath)
                        mappings = mappings.filterIndexed { i, _ -> i != index }
                        BrandIconResolver.saveCustomMappings(context, mappings)
                    }
                    if (isMiuix) {
                        top.yukonga.miuix.kmp.basic.IconButton(onClick = deleteMapping) {
                            top.yukonga.miuix.kmp.basic.Icon(
                                top.yukonga.miuix.kmp.icon.MiuixIcons.Regular.Delete,
                                contentDescription = "删除图标规则",
                            )
                        }
                    } else {
                        IconButton(onClick = deleteMapping) {
                            Icon(Icons.Default.Delete, contentDescription = "删除图标规则")
                        }
                    }
                }
            }
        }
        item {
            Box(if (isMiuix) Modifier else Modifier.padding(horizontal = 12.dp)) {
                LocalAppUi.current.primaryActionButton("添加图标规则", true) {
                    performHaptic(); mappings = mappings + BrandIconResolver.IconMapping("", "")
                    BrandIconResolver.saveCustomMappings(context, mappings)
                }
            }
        }
    }
}

@Composable
private fun RuleTextField(
    label: String,
    value: String,
    isMiuix: Boolean,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    onValueChange: (String) -> Unit,
) {
    if (isMiuix) {
        top.yukonga.miuix.kmp.basic.TextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = modifier.fillMaxWidth(),
            singleLine = singleLine,
        )
    } else {
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { androidx.compose.material3.Text(label) },
            modifier = modifier.fillMaxWidth(),
            singleLine = singleLine,
            minLines = minLines,
            shape = RoundedCornerShape(15.dp),
        )
    }
}

@Composable
private fun RuleSectionTitle(text: String, isMiuix: Boolean) {
    if (isMiuix) {
        top.yukonga.miuix.kmp.basic.SmallTitle(text = text)
    } else {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ruleSecondaryTextColor(false), modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
    }
}

@Composable
private fun EmptyRuleHint(text: String, isMiuix: Boolean) {
    RuleSurface(isMiuix, Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
        if (isMiuix) top.yukonga.miuix.kmp.basic.Text(text, style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body1, color = ruleSecondaryTextColor(true), modifier = Modifier.fillMaxWidth().padding(20.dp))
        else Text(text, color = ruleSecondaryTextColor(false), modifier = Modifier.fillMaxWidth().padding(20.dp))
    }
}

@Composable
private fun RuleHelp(text: String, isMiuix: Boolean, modifier: Modifier = Modifier) {
    if (isMiuix) top.yukonga.miuix.kmp.basic.Text(text, style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2, color = ruleSecondaryTextColor(true), modifier = modifier)
    else Text(text, fontSize = 13.sp, lineHeight = 19.sp, color = ruleSecondaryTextColor(false), modifier = modifier)
}

@Composable
private fun RuleDangerButton(text: String, isMiuix: Boolean, enabled: Boolean, onClick: () -> Unit) {
    if (isMiuix) {
        top.yukonga.miuix.kmp.basic.Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth(),
            colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(),
        ) { top.yukonga.miuix.kmp.basic.Text(text) }
    } else {
        androidx.compose.material3.TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
            Text(text, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun RuleSurface(isMiuix: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (isMiuix) {
        top.yukonga.miuix.kmp.basic.Card(modifier = modifier) { content() }
    } else {
        androidx.compose.material3.Surface(
            modifier = modifier,
            shape = RoundedCornerShape(15.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            content = content,
        )
    }
}

@Composable private fun ruleSurfaceColor(isMiuix: Boolean) = if (isMiuix) top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainer
@Composable private fun ruleMutedColor(isMiuix: Boolean) = if (isMiuix) top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceVariant
@Composable private fun ruleTextColor(isMiuix: Boolean) = if (isMiuix) top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface
@Composable private fun ruleSecondaryTextColor(isMiuix: Boolean) = if (isMiuix) top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary else MaterialTheme.colorScheme.onSurfaceVariant

private fun groupPosition(index: Int, size: Int): GroupPosition = when {
    size <= 1 -> GroupPosition.Single
    index == 0 -> GroupPosition.First
    index == size - 1 -> GroupPosition.Last
    else -> GroupPosition.Middle
}

private fun String.linesClean(): List<String> = lineSequence().map(String::trim).filter(String::isNotBlank).distinct().toList()

private fun SimpleRulePack.updateBrand(brand: SimpleBrandRule): SimpleRulePack = copy(
    brands = brands.map { if (it.id == brand.id) brand else it },
)










@Composable
private fun CorrectionSurface(
    isMiuix: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (isMiuix) {
        val colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
            top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surfaceContainer,
        )
        if (onClick == null) {
            top.yukonga.miuix.kmp.basic.Card(
                modifier = modifier,
                colors = colors,
                content = { content() },
            )
        } else {
            top.yukonga.miuix.kmp.basic.Card(
                modifier = modifier,
                colors = colors,
                onClick = onClick,
                content = { content() },
            )
        }
    } else {
        androidx.compose.material3.Surface(
            modifier = modifier.then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
            shape = RoundedCornerShape(15.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            content = content,
        )
    }
}

@Composable
fun RecognitionCorrectionRouteContent(
    isMiuix: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDraft: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val orderViewModelFactory = remember(context) {
        ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as android.app.Application,
        )
    }
    val viewModel: com.Badnng.moe.viewmodel.OrderViewModel = viewModel(factory = orderViewModelFactory)
    val drafts by viewModel.ruleCorrectionDrafts.collectAsStateWithLifecycle()
    RecognitionCorrectionPage(
        drafts = drafts,
        isMiuix = isMiuix,
        onBack = onBack,
        onCorrectionApplied = viewModel::applyRuleCorrection,
        onDelete = viewModel::deleteOrder,
        modifier = modifier,
        showTopBar = false,
        onOpenDraft = onOpenDraft,
    )
}
private fun correctionRuleSource(draft: com.Badnng.moe.data.db.OrderEntity): SimpleRuleSource =
    if (draft.recognitionInputType == RecognitionInputType.TEXT.key) {
        SimpleRuleSource.TEXT
    } else {
        SimpleRuleSource.IMAGE
    }

private data class CorrectionSaveResult(
    val correctedOrders: List<com.Badnng.moe.data.db.OrderEntity>,
    val remainingCodes: List<String>,
)

private fun buildCorrectedOrders(
    draft: com.Badnng.moe.data.db.OrderEntity,
    matches: List<SimpleRuleMatch>,
): List<com.Badnng.moe.data.db.OrderEntity> {
    val now = System.currentTimeMillis()
    return matches.mapIndexed { index, matched ->
        draft.copy(
            id = UUID.randomUUID().toString(),
            takeoutCode = matched.code,
            pickupLocation = matched.location,
            brandName = matched.brand,
            orderType = matched.category.resultType,
            needsRuleCorrection = false,
            recognizedText = "纠正规则识别",
            isCompleted = false,
            completedAt = null,
            createdAt = now + index,
            groupId = null,
        )
    }
}

private data class CorrectionBrandSuggestion(
    val brandName: String,
    val keyword: String,
    val category: SimpleRuleCategory,
)

private fun findCorrectionBrandSuggestion(lines: List<String>, targetLine: Int): CorrectionBrandSuggestion? {
    if (lines.isEmpty() || targetLine !in lines.indices) return null
    val forwardEnd = (targetLine + 4).coerceAtMost(lines.lastIndex)
    val backwardStart = (targetLine - 4).coerceAtLeast(0)
    val searchOrder = buildList {
        addAll(targetLine..forwardEnd)
        if (targetLine > backwardStart) addAll((targetLine - 1) downTo backwardStart)
    }
    for (lineIndex in searchOrder) {
        val line = lines[lineIndex]
        val hit = SimpleRuleRuntime.current().brands
            .asSequence()
            .filter { it.enabled }
            .flatMap { brand ->
                (brand.keywords + brand.name).asSequence()
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .filter { line.contains(it, ignoreCase = true) }
                    .map { keyword -> brand to keyword }
            }
            .maxByOrNull { (_, keyword) -> keyword.length }
        if (hit != null) {
            return CorrectionBrandSuggestion(hit.first.name, hit.second, hit.first.category)
        }
    }
    return null
}

private fun correctionCodePlaceholder(code: String, digitsOnly: Boolean): String = when {
    code.isBlank() -> "{{code}}"
    digitsOnly && code.all(Char::isDigit) -> "{{code:digits:${code.length}}}"
    else -> "{{code:alnum:${code.length}}}"
}

private fun findNearestKeywordLine(lines: List<String>, targetLine: Int, keyword: String): Int? {
    if (keyword.isBlank() || targetLine !in lines.indices) return null
    val forward = (targetLine..(targetLine + 4).coerceAtMost(lines.lastIndex))
        .firstOrNull { lines[it].contains(keyword, ignoreCase = true) }
    if (forward != null) return forward
    return ((targetLine - 1) downTo (targetLine - 4).coerceAtLeast(0))
        .firstOrNull { lines[it].contains(keyword, ignoreCase = true) }
}
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RecognitionCorrectionPage(
    drafts: List<com.Badnng.moe.data.db.OrderEntity>,
    isMiuix: Boolean,
    onBack: () -> Unit,
    onCorrectionApplied: (
        draft: com.Badnng.moe.data.db.OrderEntity,
        correctedOrders: List<com.Badnng.moe.data.db.OrderEntity>,
        keepDraft: Boolean,
    ) -> Unit,
    onDelete: (com.Badnng.moe.data.db.OrderEntity) -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    onOpenDraft: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SimpleRuleRepository(context.applicationContext) }
    val orderDao = remember { OrderDatabase.getDatabase(context.applicationContext).orderDao() }
    val correctionHaptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val correctionPrefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    val performCorrectionHaptic = {
        if (correctionPrefs.getBoolean("haptic_enabled", true)) {
            correctionHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
    }
    var selectedDraft by remember { mutableStateOf<com.Badnng.moe.data.db.OrderEntity?>(null) }
    var editorRevision by remember { mutableIntStateOf(0) }
    var targetCorrectionCode by remember { mutableStateOf<String?>(null) }
    var editorBackProgress by remember { mutableFloatStateOf(0f) }
    var editorBackEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    val predictiveBackEnabled = correctionPrefs.getBoolean("predictive_back_enabled", true)

    LaunchedEffect(selectedDraft?.id) {
        val draft = selectedDraft ?: run {
            targetCorrectionCode = null
            return@LaunchedEffect
        }
        val rawText = draft.fullText.orEmpty()
        val source = correctionRuleSource(draft)
        val recognizedCodes = SimpleRuleRuntime.recognizeCurrent(
            rawText = rawText,
            source = source,
            sourcePackage = draft.sourcePackage,
            qrData = draft.qrCodeData,
        ).map { it.code } + orderDao.getRecognizedCodesByText(rawText)
        targetCorrectionCode = RecognitionCorrectionDetector.findUnrecognizedCodes(rawText, recognizedCodes).firstOrNull()
        editorRevision += 1
    }

    PredictiveBackHandler(enabled = onOpenDraft == null && predictiveBackEnabled && selectedDraft != null) { events: Flow<BackEventCompat> ->
        try {
            events.collect { event ->
                editorBackProgress = event.progress
                editorBackEdge = event.swipeEdge
            }
            selectedDraft = null
        } catch (_: CancellationException) {
            // 手势取消时保留编辑内容，只复位视觉进度。
        } finally {
            editorBackProgress = 0f
        }
    }
    BackHandler(enabled = onOpenDraft == null && !predictiveBackEnabled && selectedDraft != null) {
        selectedDraft = null
    }

    Column(modifier.fillMaxSize().background(if (isMiuix) top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surface else MaterialTheme.colorScheme.background)) {
        if (showTopBar) {
            if (isMiuix) {
                top.yukonga.miuix.kmp.basic.TopAppBar(
                    title = if (selectedDraft == null) "纠正识别" else "创建纠正规则",
                    color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surface,
                    navigationIcon = {
                        top.yukonga.miuix.kmp.basic.IconButton(onClick = { performCorrectionHaptic(); if (selectedDraft != null) selectedDraft = null else onBack() }) {
                            top.yukonga.miuix.kmp.basic.Icon(
                                imageVector = top.yukonga.miuix.kmp.icon.MiuixIcons.Regular.Back,
                                contentDescription = "返回",
                            )
                        }
                    },
                )
            } else {
                androidx.compose.material3.TopAppBar(
                    title = { Text(if (selectedDraft == null) "纠正识别" else "创建纠正规则") },
                    navigationIcon = {
                        IconButton(onClick = { performCorrectionHaptic(); if (selectedDraft != null) selectedDraft = null else onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                )
            }

        }

        val draft = selectedDraft
        if (draft == null) {
            if (drafts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isMiuix) {
                            top.yukonga.miuix.kmp.basic.Text(
                                "暂无待纠正内容",
                                style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body1,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(8.dp))
                            top.yukonga.miuix.kmp.basic.Text(
                                "主动图片识别失败后会保留在这里",
                                style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2,
                                color = ruleSecondaryTextColor(true),
                            )
                        } else {
                            Text("暂无待纠正内容", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("主动图片识别失败后会保留在这里", color = ruleSecondaryTextColor(false))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = if (isMiuix) PaddingValues(top = 12.dp) else PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = if (isMiuix) Arrangement.Top else Arrangement.spacedBy(10.dp),
                ) {
                    items(drafts, key = { it.id }) { item ->
                        val openDraft = {
                            performCorrectionHaptic()
                            onOpenDraft?.invoke(item.id) ?: run { selectedDraft = item }
                        }
                        CorrectionSurface(
                            isMiuix = isMiuix,
                            modifier = if (isMiuix) {
                                Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()
                            } else {
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).clickable(onClick = openDraft)
                            },
                            onClick = if (isMiuix) openDraft else null,
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        val subtitle = listOfNotNull(item.brandName, item.orderType)
                                            .joinToString(" · ")
                                            .ifBlank { "未识别品牌" }
                                        if (isMiuix) {
                                            top.yukonga.miuix.kmp.basic.Text(
                                                "识别失败",
                                                style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body1,
                                                fontWeight = FontWeight.Bold,
                                                color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary,
                                            )
                                            top.yukonga.miuix.kmp.basic.Text(
                                                subtitle,
                                                style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2,
                                                color = ruleSecondaryTextColor(true),
                                            )
                                        } else {
                                            Text("识别失败", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Text(subtitle, color = ruleSecondaryTextColor(false))
                                        }
                                    }
                                    if (isMiuix) {
                                        top.yukonga.miuix.kmp.basic.IconButton(onClick = { performCorrectionHaptic(); onDelete(item) }) {
                                            top.yukonga.miuix.kmp.basic.Icon(top.yukonga.miuix.kmp.icon.MiuixIcons.Regular.Delete, contentDescription = "删除", tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.error)
                                        }
                                    } else {
                                        IconButton(onClick = { performCorrectionHaptic(); onDelete(item) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                val previewText = item.fullText.orEmpty().lineSequence()
                                    .filter(String::isNotBlank)
                                    .take(4)
                                    .joinToString("\n")
                                if (isMiuix) {
                                    top.yukonga.miuix.kmp.basic.Text(
                                        previewText,
                                        style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body1,
                                        maxLines = 4,
                                        color = ruleTextColor(true),
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    top.yukonga.miuix.kmp.basic.Text(
                                        "点击使用原文创建规则",
                                        style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body1,
                                        color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                } else {
                                    Text(previewText, maxLines = 4, color = ruleTextColor(false))
                                    Spacer(Modifier.height(10.dp))
                                    Text("点击使用原文创建规则", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            RecognitionCorrectionEditor(
                draft = draft,
                isMiuix = isMiuix,
                performHaptic = performCorrectionHaptic,
                resetKey = editorRevision,
                targetCode = targetCorrectionCode,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val direction = if (editorBackEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                        translationX = editorBackProgress * 96.dp.toPx() * direction
                        val scale = 1f - editorBackProgress * 0.08f
                        scaleX = scale
                        scaleY = scale
                        clip = editorBackProgress > 0f
                        shape = RoundedCornerShape((editorBackProgress * 32f).dp)
                    }
                    .background(
                        if (isMiuix) top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.background,
                    ),
                onSave = { brandName, keyword, category, template, templateName, codeDigitsOnly ->
                    scope.launch {
                        runCatching {
                            val rawText = draft.fullText.orEmpty()
                            val ruleSource = correctionRuleSource(draft)
                            require(rawText.contains(keyword, ignoreCase = true)) { "品牌关键词必须存在于本次 OCR 原文中" }
                            val normalized = rawText.lineSequence().map(String::trim).filter(String::isNotBlank).joinToString(" ")
                            require(SimpleRuleTemplateCompiler.compile(template, codeDigitsOnly).containsMatchIn(normalized)) { "当前模板无法匹配这次 OCR 原文" }

                            val previousCodes = mutableSetOf<String>().apply {
                                addAll(
                                    SimpleRuleRuntime.recognizeCurrent(
                                        rawText = rawText,
                                        source = ruleSource,
                                        sourcePackage = draft.sourcePackage,
                                        qrData = draft.qrCodeData,
                                    ).map { it.code },
                                )
                                addAll(orderDao.getRecognizedCodesByText(rawText))
                            }
                            val current = repository.load()
                            val existing = current.brands.firstOrNull { it.name.equals(brandName, true) }
                            val newTemplate = SimpleTemplateRule(
                                name = templateName.ifBlank { "${brandName}纠正规则" },
                                template = template,
                                codeDigitsOnly = codeDigitsOnly,
                                sources = setOf(ruleSource),
                            )
                            val brand = if (existing == null) {
                                SimpleBrandRule(
                                    category = category,
                                    name = brandName,
                                    keywords = listOf(keyword),
                                    templates = listOf(newTemplate),
                                )
                            } else {
                                existing.copy(
                                    category = category,
                                    keywords = (existing.keywords + keyword).map(String::trim).filter(String::isNotBlank).distinct(),
                                    templates = (existing.templates + newTemplate).distinctBy { it.template to it.sources },
                                )
                            }
                            val updatedPack = if (existing == null) {
                                current.copy(brands = current.brands + brand)
                            } else {
                                current.copy(brands = current.brands.map { if (it.id == existing.id) brand else it })
                            }
                            repository.save(updatedPack)
                            val updatedMatches = SimpleRuleRuntime.recognizeCurrent(
                                rawText = rawText,
                                source = ruleSource,
                                sourcePackage = draft.sourcePackage,
                                qrData = draft.qrCodeData,
                            )
                            val newMatches = updatedMatches.filter { it.code !in previousCodes }
                            require(newMatches.isNotEmpty()) { "规则已保存，但重新识别仍未得到新的取餐码" }
                            CorrectionSaveResult(
                                correctedOrders = buildCorrectedOrders(draft, newMatches),
                                remainingCodes = RecognitionCorrectionDetector.findUnrecognizedCodes(
                                    fullText = rawText,
                                    recognizedCodes = previousCodes + updatedMatches.map { it.code },
                                ),
                            )
                        }.onSuccess { result ->
                            val keepDraft = result.remainingCodes.isNotEmpty()
                            onCorrectionApplied(draft, result.correctedOrders, keepDraft)
                            if (keepDraft) {
                                targetCorrectionCode = result.remainingCodes.firstOrNull()
                                editorRevision += 1
                            } else {
                                selectedDraft = null
                            }
                            Toast.makeText(
                                context,
                                if (keepDraft) "已新增${result.correctedOrders.size}个取件码，仍有${result.remainingCodes.size}个待纠正"
                                else "规则已保存，新增${result.correctedOrders.size}个取件码",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }.onFailure {
                            Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
            )
        }
    }
}

@Composable
fun RecognitionCorrectionEditorRouteContent(
    orderId: String,
    isMiuix: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val orderViewModelFactory = remember(context) {
        ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as android.app.Application,
        )
    }
    val viewModel: com.Badnng.moe.viewmodel.OrderViewModel = viewModel(factory = orderViewModelFactory)
    val orderDao = remember { OrderDatabase.getDatabase(context.applicationContext).orderDao() }
    val drafts by viewModel.ruleCorrectionDrafts.collectAsStateWithLifecycle()
    val draft = drafts.firstOrNull { it.id == orderId }
    val scope = rememberCoroutineScope()
    val repository = remember { SimpleRuleRepository(context.applicationContext) }
    var editorRevision by remember(orderId) { mutableIntStateOf(0) }
    var targetCorrectionCode by remember(orderId) { mutableStateOf<String?>(null) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    val performHaptic = {
        if (prefs.getBoolean("haptic_enabled", true)) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
    }


    LaunchedEffect(draft?.id) {
        val currentDraft = draft ?: run {
            targetCorrectionCode = null
            return@LaunchedEffect
        }
        val rawText = currentDraft.fullText.orEmpty()
        val source = correctionRuleSource(currentDraft)
        val recognizedCodes = SimpleRuleRuntime.recognizeCurrent(
            rawText = rawText,
            source = source,
            sourcePackage = currentDraft.sourcePackage,
            qrData = currentDraft.qrCodeData,
        ).map { it.code } + orderDao.getRecognizedCodesByText(rawText)
        targetCorrectionCode = RecognitionCorrectionDetector.findUnrecognizedCodes(rawText, recognizedCodes).firstOrNull()
        editorRevision += 1
    }


    if (draft == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyRuleHint("该待纠正记录已不存在", isMiuix)
        }
        return
    }

    RecognitionCorrectionEditor(
        draft = draft,
        isMiuix = isMiuix,
        performHaptic = performHaptic,
        resetKey = editorRevision,
        targetCode = targetCorrectionCode,
        modifier = modifier.fillMaxSize(),
        onSave = { brandName, keyword, category, template, templateName, codeDigitsOnly ->
            scope.launch {
                runCatching {
                    val rawText = draft.fullText.orEmpty()
                    val ruleSource = correctionRuleSource(draft)
                    require(rawText.contains(keyword, ignoreCase = true)) { "品牌关键词必须存在于本次 OCR 原文中" }
                    val normalized = rawText.lineSequence().map(String::trim).filter(String::isNotBlank).joinToString(" ")
                    require(SimpleRuleTemplateCompiler.compile(template, codeDigitsOnly).containsMatchIn(normalized)) { "当前模板无法匹配这次 OCR 原文" }
                    val previousCodes = mutableSetOf<String>().apply {
                        addAll(
                            SimpleRuleRuntime.recognizeCurrent(
                                rawText = rawText,
                                source = ruleSource,
                                sourcePackage = draft.sourcePackage,
                                qrData = draft.qrCodeData,
                            ).map { it.code },
                        )
                        addAll(orderDao.getRecognizedCodesByText(rawText))
                    }
                    val current = repository.load()
                    val existing = current.brands.firstOrNull { it.name.equals(brandName, true) }
                    val newTemplate = SimpleTemplateRule(
                        name = templateName.ifBlank { "${brandName}纠正规则" },
                        template = template,
                        codeDigitsOnly = codeDigitsOnly,
                        sources = setOf(ruleSource),
                    )
                    val brand = if (existing == null) {
                        SimpleBrandRule(category = category, name = brandName, keywords = listOf(keyword), templates = listOf(newTemplate))
                    } else {
                        existing.copy(
                            category = category,
                            keywords = (existing.keywords + keyword).map(String::trim).filter(String::isNotBlank).distinct(),
                            templates = (existing.templates + newTemplate).distinctBy { it.template to it.sources },
                        )
                    }
                    val updatedPack = if (existing == null) {
                        current.copy(brands = current.brands + brand)
                    } else {
                        current.copy(brands = current.brands.map { if (it.id == existing.id) brand else it })
                    }
                    repository.save(updatedPack)
                    val updatedMatches = SimpleRuleRuntime.recognizeCurrent(
                        rawText = rawText,
                        source = ruleSource,
                        sourcePackage = draft.sourcePackage,
                        qrData = draft.qrCodeData,
                    )
                    val newMatches = updatedMatches.filter { it.code !in previousCodes }
                    require(newMatches.isNotEmpty()) { "规则已保存，但重新识别仍未得到新的取餐码" }
                    CorrectionSaveResult(
                        correctedOrders = buildCorrectedOrders(draft, newMatches),
                        remainingCodes = RecognitionCorrectionDetector.findUnrecognizedCodes(
                            fullText = rawText,
                            recognizedCodes = previousCodes + updatedMatches.map { it.code },
                        ),
                    )
                }.onSuccess { result ->
                    val keepDraft = result.remainingCodes.isNotEmpty()
                    viewModel.applyRuleCorrection(draft, result.correctedOrders, keepDraft)
                    Toast.makeText(
                        context,
                        if (keepDraft) "已新增${result.correctedOrders.size}个取件码，仍有${result.remainingCodes.size}个待纠正"
                        else "规则已保存，新增${result.correctedOrders.size}个取件码",
                        Toast.LENGTH_SHORT,
                    ).show()
                    if (keepDraft) {
                        targetCorrectionCode = result.remainingCodes.firstOrNull()
                        editorRevision += 1
                    } else {
                        onBack()
                    }
                }.onFailure {
                    Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        },
    )
}
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RecognitionCorrectionEditor(
    draft: com.Badnng.moe.data.db.OrderEntity,
    isMiuix: Boolean,
    performHaptic: () -> Unit,
    resetKey: Int,
    targetCode: String?,
    modifier: Modifier = Modifier,
    onSave: (String, String, SimpleRuleCategory, String, String, Boolean) -> Unit,
) {
    val lines = remember(draft.id) { draft.fullText.orEmpty().lineSequence().map(String::trim).filter(String::isNotBlank).toList() }
    fun initialCodeLine(): Int {
        targetCode?.let { code ->
            lines.indexOfFirst { it.contains(code) }.takeIf { it >= 0 }?.let { return it }
        }
        val keywordIndex = lines.indices.firstOrNull { index ->
            index > 0 &&
                lines[index - 1].contains(Regex("取(餐|茶|件|货)?(码|号)|取单口令|排队号")) &&
                lines[index].any(Char::isDigit)
        }
        if (keywordIndex != null) return keywordIndex
        return lines.indexOfFirst { line ->
            line.length in 1..40 && line.any(Char::isDigit) &&
                line.count { it.isLetterOrDigit() || it in ".#_-" } >= line.length * 0.7
        }.coerceAtLeast(0)
    }
    val initialLine = initialCodeLine()
    val initialBrandSuggestion = remember(draft.id, resetKey, targetCode) {
        findCorrectionBrandSuggestion(lines, initialLine)
    }
    var category by rememberSaveable(draft.id, resetKey) {
        mutableStateOf(initialBrandSuggestion?.category ?: SimpleRuleCategory.entries.firstOrNull { it.resultType == draft.orderType } ?: SimpleRuleCategory.FOOD)
    }
    var brandName by rememberSaveable(draft.id, resetKey) { mutableStateOf(initialBrandSuggestion?.brandName ?: draft.brandName.orEmpty()) }
    var keyword by rememberSaveable(draft.id, resetKey) { mutableStateOf(initialBrandSuggestion?.keyword ?: draft.brandName.orEmpty()) }
    var selectedLine by rememberSaveable(draft.id, resetKey) { mutableIntStateOf(initialLine) }
    fun suggestedCodeSample(index: Int): String = lines.getOrNull(index)
        ?.let { line -> Regex("[A-Za-z0-9][A-Za-z0-9.#_-]{0,39}").findAll(line).map { it.value }.filter { value -> value.any(Char::isDigit) }.maxByOrNull(String::length) }
        .orEmpty()
    var codeSample by rememberSaveable(draft.id, resetKey) { mutableStateOf(targetCode ?: suggestedCodeSample(selectedLine)) }
    var useBrandAnchor by rememberSaveable(draft.id, resetKey) { mutableStateOf(true) }
    var templateName by rememberSaveable(draft.id, resetKey) { mutableStateOf("纠正规则") }
    var codeDigitsOnly by rememberSaveable(draft.id, resetKey) { mutableStateOf(codeSample.isNotBlank() && codeSample.all(Char::isDigit)) }
    var template by rememberSaveable(draft.id, resetKey) { mutableStateOf("") }

    fun suggestedTemplate(index: Int): String {
        val placeholder = correctionCodePlaceholder(codeSample, codeDigitsOnly)
        if (lines.isEmpty() || !useBrandAnchor || keyword.isBlank()) return placeholder
        val keywordLine = findNearestKeywordLine(lines, index, keyword) ?: return placeholder
        if (keywordLine < index) return "$keyword\n{{any}}\n$placeholder"
        if (keywordLine > index) return "$placeholder\n{{any}}\n$keyword"
        val sourceLine = lines[index]
        val keywordBeforeCode = sourceLine.indexOf(keyword, ignoreCase = true) <= sourceLine.indexOf(codeSample)
        return if (keywordBeforeCode) "$keyword{{any}}$placeholder" else "$placeholder{{any}}$keyword"
    }
    LaunchedEffect(selectedLine, resetKey) {
        codeSample = suggestedCodeSample(selectedLine)
    }
    LaunchedEffect(selectedLine, codeSample, useBrandAnchor, codeDigitsOnly, keyword, resetKey) {
        template = suggestedTemplate(selectedLine)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = if (isMiuix) PaddingValues(top = 12.dp) else PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = if (isMiuix) Arrangement.Top else Arrangement.spacedBy(12.dp),
    ) {
        item {
            RuleSectionTitle("规则归属", isMiuix)
            if (isMiuix) {
                RuleTextField(
                    "品牌名称",
                    brandName,
                    true,
                    Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                ) { brandName = it }
                RuleTextField(
                    "品牌关键词",
                    keyword,
                    true,
                    Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                ) { keyword = it }
                RuleHelp(
                    "关键词必须出现在原文中，例如茶百道、ChaPanda、熊猫币。后续检测到该词即可锁定品牌。",
                    true,
                    Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp),
                )
                FlowRow(
                    Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SimpleRuleCategory.entries.forEach { item ->
                        LocalAppUi.current.choiceChip(
                            item.displayName,
                            category == item,
                            { performHaptic(); category = item },
                            Modifier.weight(1f),
                        )
                    }
                }
            } else {
                CorrectionSurface(false, Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        RuleTextField("品牌名称", brandName, false) { brandName = it }
                        RuleTextField("品牌关键词", keyword, false) { keyword = it }
                        RuleHelp("关键词必须出现在原文中，例如茶百道、ChaPanda、熊猫币。后续检测到该词即可锁定品牌。", false)
                        FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SimpleRuleCategory.entries.forEach { item ->
                                LocalAppUi.current.choiceChip(item.displayName, category == item, { performHaptic(); category = item }, Modifier)
                            }
                        }
                    }
                }
            }
        }
        item {
            RuleSectionTitle("选择取餐码所在行", isMiuix)
            RuleHelp("点击实际码值所在行。系统会提取行内数字作为建议，也可以在下方手动指定正确码值。", isMiuix, if (isMiuix) Modifier.padding(horizontal = 24.dp, vertical = 8.dp) else Modifier)
        }
        items(lines.indices.toList(), key = { it }) { index ->
            val selected = index == selectedLine
            val selectLine = { performHaptic(); selectedLine = index }
            CorrectionSurface(
                isMiuix = isMiuix,
                modifier = if (isMiuix) {
                    Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()
                } else {
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).clickable(onClick = selectLine)
                },
                onClick = if (isMiuix) selectLine else null,
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isMiuix) {
                        top.yukonga.miuix.kmp.basic.Text(
                            "${index + 1}",
                            color = ruleSecondaryTextColor(true),
                            modifier = Modifier.width(36.dp),
                        )
                        top.yukonga.miuix.kmp.basic.Text(
                            lines[index],
                            modifier = Modifier.weight(1f),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (selected) {
                            top.yukonga.miuix.kmp.basic.Text(
                                "{{code}}",
                                color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        Text("${index + 1}", color = ruleSecondaryTextColor(false), modifier = Modifier.width(36.dp))
                        Text(lines[index], Modifier.weight(1f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        if (selected) Text("{{code}}", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item {
            RuleSectionTitle("本次正确结果", isMiuix)
            if (isMiuix) {
                RuleTextField(
                    "正确取餐码/取件码",
                    codeSample,
                    true,
                    Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                    singleLine = true,
                ) { codeSample = it }
                RuleHelp(
                    "如果码值嵌在同一行，例如“取餐号2”，填写 2 后会生成“取餐号{{code}}”。",
                    true,
                    Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp),
                )
            } else {
                CorrectionSurface(false, Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        RuleTextField("正确取餐码/取件码", codeSample, false, singleLine = true) { codeSample = it }
                        RuleHelp("如果码值嵌在同一行，例如“取餐号2”，填写 2 后会生成“取餐号{{code}}”。", false)
                    }
                }
            }
        }
        item {
            RuleSectionTitle("模板", isMiuix)
            if (isMiuix) {
                CorrectionSurface(true, Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        LocalAppUi.current.preferenceSwitchItem("使用品牌锚点", "只保存品牌关键词与码值相对位置，不复制原文", useBrandAnchor) { performHaptic(); useBrandAnchor = it }
                        LocalAppUi.current.preferenceSwitchItem("仅识别数字", "开启后 {{code}} 不会匹配文字或字母", codeDigitsOnly) { performHaptic(); codeDigitsOnly = it }
                    }
                }
                RuleTextField(
                    "模板名称",
                    templateName,
                    true,
                    Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                ) { templateName = it }
                RuleTextField(
                    "匹配模板",
                    template,
                    true,
                    Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                    minLines = 4,
                ) { template = it }
                RuleHelp(
                    "可使用 {{code:digits:4}} 限定 4 位数字；用 {{any}} 跳过链接等变化内容。例如：已放{{location}}，点击{{any}}或使用{{code:digits:4}}取件。",
                    true,
                    Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp),
                )
            } else {
                CorrectionSurface(false, Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        LocalAppUi.current.preferenceSwitchItem("使用品牌锚点", "只保存品牌关键词与码值相对位置，不复制原文", useBrandAnchor) { performHaptic(); useBrandAnchor = it }
                        LocalAppUi.current.preferenceSwitchItem("仅识别数字", "开启后 {{code}} 不会匹配文字或字母", codeDigitsOnly) { performHaptic(); codeDigitsOnly = it }
                        RuleTextField("模板名称", templateName, false) { templateName = it }
                        RuleTextField("匹配模板", template, false, minLines = 4) { template = it }
                        RuleHelp("可使用 {{code:digits:4}} 限定 4 位数字；用 {{any}} 跳过链接等变化内容。例如：已放{{location}}，点击{{any}}或使用{{code:digits:4}}取件。", false)
                    }
                }
            }
        }
        item {
            LocalAppUi.current.primaryActionButton(
                "保存规则并重新识别",
                brandName.isNotBlank() && keyword.isNotBlank() && codeSample.isNotBlank() &&
                    SimpleRuleTemplateCompiler.hasCodePlaceholder(template) &&
                    runCatching { SimpleRuleTemplateCompiler.compile(template, codeDigitsOnly) }.isSuccess,
            ) { performHaptic(); onSave(brandName.trim(), keyword.trim(), category, template.trim(), templateName.trim(), codeDigitsOnly) }
        }
        item { Spacer(if (isMiuix) Modifier.height(24.dp).navigationBarsPadding() else Modifier.height(24.dp)) }
    }
}
