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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.helper.BrandIconResolver
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
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.utils.MiuixIndication

sealed interface SimpleRuleCenterPage {
    data object Root : SimpleRuleCenterPage
    data class WordCategory(val type: WordType) : SimpleRuleCenterPage
    data class Category(val category: SimpleRuleCategory) : SimpleRuleCenterPage
    data class CreateBrand(val category: SimpleRuleCategory) : SimpleRuleCenterPage
    data class Brand(val brandId: String) : SimpleRuleCenterPage
    data class CreateTemplate(val brandId: String) : SimpleRuleCenterPage
    data class Template(val brandId: String, val templateId: String) : SimpleRuleCenterPage
    data object BlockedWords : SimpleRuleCenterPage
    data object CustomLocations : SimpleRuleCenterPage
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
    is SimpleRuleCenterPage.WordCategory -> type.displayName
    is SimpleRuleCenterPage.Category -> category.displayName
    is SimpleRuleCenterPage.CreateBrand -> "添加${category.displayName}品牌"
    is SimpleRuleCenterPage.Brand -> pack.brands.firstOrNull { it.id == brandId }?.name ?: "品牌规则"
    is SimpleRuleCenterPage.CreateTemplate -> "添加识别模板"
    is SimpleRuleCenterPage.Template -> pack.brands.firstOrNull { it.id == brandId }
        ?.templates?.firstOrNull { it.id == templateId }?.name ?: "识别模板"
    SimpleRuleCenterPage.BlockedWords -> "自定义屏蔽词"
    SimpleRuleCenterPage.CustomLocations -> "自定义取件地点"
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
    pageOverride: SimpleRuleCenterPage? = null,
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
    val wordRepo = remember { PickupWordRuleRepository(context.applicationContext) }
    var wordPack by remember { mutableStateOf(PickupWordRulePack.empty()) }
    var pack by remember { mutableStateOf(SimpleRulePack.empty()) }
    var loading by remember { mutableStateOf(true) }
    var deletingRuleKey by remember { mutableStateOf<String?>(null) }
    var creatingRuleKey by remember { mutableStateOf<String?>(null) }
    var pendingSaveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var showBuiltInRestoreConfirm by remember { mutableStateOf(false) }

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
        wordPack = wordRepo.load()
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
                    .fold(onSuccess = { wordRepo.importJson(it) }, onFailure = { Result.failure(it) })
            }
            result.onSuccess {
                wordPack = it
                state.reset()
                Toast.makeText(context, "已导入词汇规则", Toast.LENGTH_SHORT).show()
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
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(wordRepo.exportJson(wordPack)) }
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

    if (showBuiltInRestoreConfirm) {
        val confirmRestore: () -> Unit = {
            performHaptic()
            showBuiltInRestoreConfirm = false
            // 规则写盘由 importBuiltInPack 自己负责，取消在途的防抖保存，
            // 否则它可能用旧快照覆盖刚追加进来的内置品牌。
            pendingSaveJob?.cancel()
            scope.launch {
                repository.importBuiltInPack()
                    .onSuccess { restored ->
                        pack = restored
                        Toast.makeText(
                            context,
                            "已追加内置识别规则，共 ${restored.brands.size} 个品牌",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    .onFailure {
                        Toast.makeText(context, "追加失败：${it.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }
        if (isMiuix) {
            top.yukonga.miuix.kmp.overlay.OverlayDialog(
                title = "追加内置识别规则",
                summary = "把内置的快递、餐食、饮品取件码模板追加到当前规则，短信、通知、划词都能用；你已建的品牌和模板会原样保留，不会被覆盖。",
                show = true,
                onDismissRequest = { showBuiltInRestoreConfirm = false },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MiuixTextButton(
                        text = "取消",
                        onClick = { showBuiltInRestoreConfirm = false },
                        modifier = Modifier.weight(1f),
                    )
                    MiuixButton(
                        onClick = confirmRestore,
                        modifier = Modifier.weight(1f),
                        colors = MiuixButtonDefaults.buttonColors(),
                    ) {
                        MiuixText("恢复", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        } else {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showBuiltInRestoreConfirm = false },
                title = { Text("追加内置识别规则") },
                text = { Text("把内置的快递、餐食、饮品取件码模板追加到当前规则，短信、通知、划词都能用；你已建的品牌和模板会原样保留，不会被覆盖。") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = confirmRestore) { Text("追加") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showBuiltInRestoreConfirm = false }) { Text("取消") }
                },
            )
        }
    }

    when (val page = pageOverride ?: state.page) {
        SimpleRuleCenterPage.Root -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = if (isMiuix) Arrangement.Top else Arrangement.spacedBy(12.dp),
        ) {
            item {
                RuleSectionTitle("识别规则", isMiuix)
                appUi.settingsGroup(if (isMiuix) Modifier else Modifier.padding(horizontal = 12.dp)) {
                    WordType.entries.forEachIndexed { index, type ->
                        val count = wordPack.enabledWords(type).size
                        appUi.settingsGroupItem(
                            type.displayName,
                            if (count == 0) "暂无词汇（点击添加）" else "$count 个定位词",
                            groupPosition(index, WordType.entries.size),
                            { performHaptic(); openPage(SimpleRuleCenterPage.WordCategory(type)) },
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
                    val customLocationsText = prefs.getString("custom_pickup_locations", "") ?: ""
                    val customLocationsCount = customLocationsText.split(",").map { it.trim() }.filter { it.isNotBlank() }.size
                    appUi.settingsGroupItem(
                        "自定义取件地点",
                        if (customLocationsCount == 0) "未设置" else "$customLocationsCount 个关键词",
                        GroupPosition.Middle,
                        { performHaptic(); openPage(SimpleRuleCenterPage.CustomLocations) },
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
                        "导入词汇规则包（餐食/快递定位词）",
                        GroupPosition.First,
                        { performHaptic(); importLauncher.launch(arrayOf("application/json", "text/plain")) },
                        if (isMiuix) null else ({ Icon(Icons.Default.FileUpload, contentDescription = null) }),
                    )
                    appUi.settingsGroupItem(
                        "导出规则",
                        "导出词汇规则（三类型词汇 + 品牌表）",
                        GroupPosition.Middle,
                        { performHaptic(); exportLauncher.launch("澎湃记词汇规则-v${PickupWordRulePack.SCHEMA_VERSION}.json") },
                        if (isMiuix) null else ({ Icon(Icons.Default.FileDownload, contentDescription = null) }),
                    )
                    appUi.settingsGroupItem(
                        "追加内置识别规则",
                        "补齐快递、餐食、饮品的取件码模板（保留你已建的品牌）",
                        GroupPosition.Last,
                        { performHaptic(); showBuiltInRestoreConfirm = true },
                        if (isMiuix) null else ({ Icon(Icons.Default.Restore, contentDescription = null) }),
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }

        is SimpleRuleCenterPage.WordCategory -> WordCategoryPage(
            type = page.type,
            isMiuix = isMiuix,
            contentPadding = contentPadding,
            performHaptic = performHaptic,
            onBack = { backPage() },
            modifier = modifier,
        )

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

        SimpleRuleCenterPage.CustomLocations -> CustomLocationsPage(
            isMiuix = isMiuix,
            contentPadding = contentPadding,
            performHaptic = performHaptic,
            modifier = modifier,
        )

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
private fun CustomLocationsPage(
    isMiuix: Boolean,
    contentPadding: PaddingValues,
    performHaptic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberCustomPickupLocationsEditorState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = if (isMiuix) Arrangement.Top else Arrangement.spacedBy(12.dp),
    ) {
        item {
            RuleSectionTitle("自定义取件地点", isMiuix)
            Box(Modifier.padding(horizontal = 12.dp)) {
                if (isMiuix) {
                    MiuixCustomPickupLocationsEditor(state, performHaptic)
                } else {
                    Md3eCustomPickupLocationsEditor(state, performHaptic)
                }
            }
        }
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
            Column {
                Box(if (isMiuix) Modifier else Modifier.padding(horizontal = 12.dp)) {
                    LocalAppUi.current.primaryActionButton("添加图标规则", true) {
                        performHaptic(); mappings = mappings + BrandIconResolver.IconMapping("", "")
                        BrandIconResolver.saveCustomMappings(context, mappings)
                    }
                }
                RuleHelp(
                    "品牌名称包含对应关键词时使用该图标。多个关键词使用逗号分隔。",
                    isMiuix,
                    Modifier
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp),
                )
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

/**
 * 单个类型的词汇管理页（餐食定位词 / 快递定位词 / 辅助锚点词）。
 * 交互与「自定义取件地点」一致：词汇显示为标签（chips），点击标签删除；
 * 底部输入框 + 尾部添加按钮（回车 / 完成键同样添加），label 带数量统计。
 * 识别时先判定页面类型（餐食/快递），再用对应类型词汇定位裁剪二次识别。
 */
@Composable
private fun WordCategoryPage(
    type: WordType,
    isMiuix: Boolean,
    contentPadding: PaddingValues,
    performHaptic: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { PickupWordRuleRepository(context.applicationContext) }
    var pack by remember { mutableStateOf(PickupWordRulePack.empty()) }
    var loading by remember { mutableStateOf(true) }
    var pendingSaveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var newWord by remember { mutableStateOf("") }

    fun save(newPack: PickupWordRulePack) {
        pack = newPack
        pendingSaveJob?.cancel()
        pendingSaveJob = scope.launch {
            kotlinx.coroutines.delay(250L)
            runCatching { repo.save(newPack) }
                .onFailure { Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    val words = when (type) {
        WordType.FOOD -> pack.foodKeywords
        WordType.EXPRESS -> pack.expressKeywords
        WordType.AUXILIARY -> pack.auxiliaryKeywords
    }

    fun addWord() {
        val trimmed = newWord.trim()
        if (trimmed.isBlank()) return
        if (words.any { it.word.equals(trimmed, ignoreCase = true) }) {
            Toast.makeText(context, "该词汇已存在", Toast.LENGTH_SHORT).show()
            return
        }
        if (words.size >= MAX_WORDS_PER_TYPE) {
            Toast.makeText(context, "最多添加 $MAX_WORDS_PER_TYPE 条${type.displayName}", Toast.LENGTH_SHORT).show()
            return
        }
        performHaptic()
        val rule = PickupWordRule(word = trimmed)
        val updated = when (type) {
            WordType.FOOD -> pack.copy(foodKeywords = pack.foodKeywords + rule)
            WordType.EXPRESS -> pack.copy(expressKeywords = pack.expressKeywords + rule)
            WordType.AUXILIARY -> pack.copy(auxiliaryKeywords = pack.auxiliaryKeywords + rule)
        }
        save(updated)
        newWord = ""
    }

    fun removeWord(ruleId: String) {
        performHaptic()
        val updated = when (type) {
            WordType.FOOD -> pack.copy(foodKeywords = pack.foodKeywords.filterNot { it.id == ruleId })
            WordType.EXPRESS -> pack.copy(expressKeywords = pack.expressKeywords.filterNot { it.id == ruleId })
            WordType.AUXILIARY -> pack.copy(auxiliaryKeywords = pack.auxiliaryKeywords.filterNot { it.id == ruleId })
        }
        save(updated)
    }

    LaunchedEffect(Unit) {
        pack = repo.load()
        loading = false
    }

    BackHandler(enabled = true) { onBack() }

    if (loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("正在读取词汇…")
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = if (isMiuix) Arrangement.Top else Arrangement.spacedBy(12.dp),
    ) {
        item {
            RuleSectionTitle(type.displayName, isMiuix)
            Box(Modifier.padding(horizontal = 12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (words.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            words.forEach { rule ->
                                if (isMiuix) {
                                    MiuixWordChip(rule.word) { removeWord(rule.id) }
                                } else {
                                    Md3eWordChip(rule.word) { removeWord(rule.id) }
                                }
                            }
                        }
                    }
                    Column {
                        if (isMiuix) {
                            MiuixTextField(
                                value = newWord,
                                onValueChange = { newWord = it.replace("\n", "") },
                                label = "添加${type.displayName}（${words.size}/$MAX_WORDS_PER_TYPE）",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                                            addWord()
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                trailingIcon = {
                                    MiuixIconButton(onClick = { addWord() }) {
                                        MiuixIcon(
                                            imageVector = top.yukonga.miuix.kmp.icon.MiuixIcons.Regular.Add,
                                            contentDescription = "添加${type.displayName}",
                                        )
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { addWord() }),
                            )
                        } else {
                            OutlinedTextField(
                                value = newWord,
                                onValueChange = { newWord = it.replace("\n", "") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                                            addWord()
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                label = { Text("添加${type.displayName}") },
                                supportingText = {
                                    Text("${words.size}/$MAX_WORDS_PER_TYPE")
                                },
                                trailingIcon = {
                                    IconButton(onClick = { addWord() }) {
                                        Icon(Icons.Default.Add, contentDescription = "添加${type.displayName}")
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { addWord() }),
                                shape = RoundedCornerShape(15.dp),
                            )
                        }
                        Text(
                            text = when (type) {
                                WordType.FOOD -> "添加餐食定位词，识别文本中包含这些词时作为锚点定位取餐码区域。点击标签可删除。"
                                WordType.EXPRESS -> "添加快递定位词，识别文本中包含这些词时作为锚点定位取件码区域。点击标签可删除。"
                                WordType.AUXILIARY -> "添加辅助锚点词，识别时命中这些词会扩大框选范围。点击标签可删除。"
                            },
                            style = if (isMiuix) top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2 else MaterialTheme.typography.bodySmall,
                            color = ruleSecondaryTextColor(isMiuix),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** 词汇标签（MD3E）：点击即删除，样式与「自定义取件地点」一致。 */
@Composable
private fun Md3eWordChip(
    word: String,
    onRemove: () -> Unit,
) {
    InputChip(
        selected = false,
        onClick = onRemove,
        label = {
            Text(
                text = word,
                modifier = Modifier.widthIn(max = 240.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "删除词汇 $word",
                modifier = Modifier.size(16.dp),
            )
        },
        shape = RoundedCornerShape(15.dp),
    )
}

/** 词汇标签（Miuix）：点击即删除，样式与「自定义取件地点」一致。 */
@Composable
private fun MiuixWordChip(
    word: String,
    onRemove: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val indicationColor = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface
    val indication = remember(indicationColor) { MiuixIndication(color = indicationColor) }
    Row(
        modifier = Modifier
            .squircleSurface(top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surfaceContainer, 15.dp)
            .squircleClip(15.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = indication,
                role = Role.Button,
                onClick = onRemove,
            )
            .semantics {
                role = Role.Button
                contentDescription = "删除词汇 $word"
            }
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiuixText(
            text = word,
            modifier = Modifier.widthIn(max = 240.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2,
            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        MiuixIcon(
            imageVector = top.yukonga.miuix.kmp.icon.MiuixIcons.Regular.Close,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 每个分类最多可添加的词汇条数（与「自定义取件地点」上限一致）。 */
private const val MAX_WORDS_PER_TYPE = 50
