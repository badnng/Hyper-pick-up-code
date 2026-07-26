package com.Badnng.moe.ui.screen.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.Badnng.moe.helper.BackupManager
import com.Badnng.moe.helper.BackupOptions
import com.Badnng.moe.helper.BackupPreview
import com.Badnng.moe.helper.RestoreOrderPolicy
import com.Badnng.moe.helper.RestoreReport
import com.Badnng.moe.helper.RestoreSelection
import com.Badnng.moe.helper.StagedBackup
import com.Badnng.moe.ui.LocalAppUi
import com.Badnng.moe.ui.component.GroupPosition
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun BackupSettingsContent(
    performHaptic: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appUi = LocalAppUi.current
    var includeOrders by remember { mutableStateOf(true) }
    var includeSettings by remember { mutableStateOf(true) }
    var includeRules by remember { mutableStateOf(true) }
    var includeScreenshots by remember { mutableStateOf(false) }
    var includeApiKeys by remember { mutableStateOf(false) }
    var pendingBackupOptions by remember { mutableStateOf<BackupOptions?>(null) }
    var stagedBackup by remember { mutableStateOf<StagedBackup?>(null) }
    var restoreOrders by remember { mutableStateOf(false) }
    var restoreSettings by remember { mutableStateOf(false) }
    var restoreRules by remember { mutableStateOf(false) }
    var restoreScreenshots by remember { mutableStateOf(false) }
    var restoreApiKeys by remember { mutableStateOf(false) }
    var restorePolicy by remember { mutableStateOf(RestoreOrderPolicy.MERGE_KEEP_LOCAL) }
    var isBusy by remember { mutableStateOf(false) }
    var passwordDialogMode by remember { mutableStateOf<PasswordDialogMode?>(null) }
    var restoreReport by remember { mutableStateOf<RestoreReport?>(null) }
    var restorePassword by remember { mutableStateOf<String?>(null) }

    val currentStagedBackup by rememberUpdatedState(stagedBackup)
    DisposableEffect(Unit) {
        onDispose { currentStagedBackup?.close() }
    }

    fun updatePreview(staged: StagedBackup) {
        stagedBackup?.close()
        stagedBackup = staged
        val preview = staged.preview
        restoreOrders = preview.orderCount > 0 || preview.groupCount > 0
        restoreSettings = preview.settingsCount > 0
        restoreRules = preview.ruleFileCount > 0
        restoreScreenshots = preview.screenshotCount > 0
        restoreApiKeys = false
        restorePolicy = RestoreOrderPolicy.MERGE_KEEP_LOCAL
        restoreReport = null
        restorePassword = null
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val options = pendingBackupOptions
        if (uri == null || options == null) {
            isBusy = false
            pendingBackupOptions = null
            restorePassword = null
        } else {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        BackupManager.createBackup(context, output, options, restorePassword)
                    } ?: error("无法写入备份文件")
                    Toast.makeText(context, "备份已创建", Toast.LENGTH_SHORT).show()
                } catch (error: Exception) {
                    Toast.makeText(context, "备份失败：${error.message}", Toast.LENGTH_LONG).show()
                } finally {
                    restorePassword = null
                    pendingBackupOptions = null
                    isBusy = false
                }
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            isBusy = true
            scope.launch {
                try {
                    val staged = context.contentResolver.openInputStream(uri)?.use { input ->
                        BackupManager.inspectBackup(context, input)
                    } ?: error("无法读取备份文件")
                    updatePreview(staged)
                } catch (error: Exception) {
                    Toast.makeText(context, "备份预检失败：${error.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isBusy = false
                }
            }
        }
    }

    fun startBackup(password: String?) {
        val options = BackupOptions(
            includeOrders = includeOrders,
            includeSettings = includeSettings,
            includeRules = includeRules,
            includeScreenshots = includeOrders && includeScreenshots,
            includeApiKeys = includeApiKeys,
        )
        pendingBackupOptions = options
        restorePassword = password
        isBusy = true
        createDocumentLauncher.launch(BackupManager.generateBackupFileName())
    }

    fun startRestore(password: String?) {
        val staged = stagedBackup ?: return
        isBusy = true
        scope.launch {
            try {
                val report = BackupManager.restoreBackup(
                    context = context,
                    stagedBackup = staged,
                    selection = RestoreSelection(
                        restoreOrders = restoreOrders,
                        restoreSettings = restoreSettings,
                        restoreRules = restoreRules,
                        restoreScreenshots = restoreOrders && restoreScreenshots,
                        restoreApiKeys = restoreApiKeys,
                        orderPolicy = restorePolicy,
                    ),
                    password = password,
                )
                restoreReport = report
                Toast.makeText(context, report.summary(), Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                val detail = error.message ?: "未知错误"
                val message = if (restoreApiKeys && detail.contains("密码")) {
                    "恢复失败：$detail。可以关闭 API 密钥选项后继续恢复其他数据。"
                } else {
                    "恢复失败：$detail"
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } finally {
                restorePassword = null
                isBusy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = topPadding)
            .navigationBarsPadding()
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        appUi.preferenceSection("创建备份") {
            appUi.settingsGroup(Modifier) {
                appUi.settingsGroupSwitchItem(
                    "订单与订单组",
                    "备份全部取餐码、订单组与识别诊断信息",
                    GroupPosition.First,
                    includeOrders,
                ) {
                    performHaptic()
                    includeOrders = it
                    if (!it) includeScreenshots = false
                }
                appUi.settingsGroupSwitchItem(
                    "设置",
                    "备份可跨设备使用的界面与功能设置",
                    GroupPosition.Middle,
                    includeSettings,
                ) {
                    performHaptic()
                    includeSettings = it
                }
                appUi.settingsGroupSwitchItem(
                    "全部规则源",
                    "备份本地、自定义及在线规则源",
                    GroupPosition.Middle,
                    includeRules,
                ) {
                    performHaptic()
                    includeRules = it
                }
                appUi.settingsGroupSwitchItem(
                    "截图文件",
                    if (includeOrders) "按内容去重后打包，可能显著增加体积" else "需要先选择订单与订单组",
                    GroupPosition.Middle,
                    includeScreenshots,
                ) {
                    if (includeOrders) {
                        performHaptic()
                        includeScreenshots = it
                    }
                }
                appUi.settingsGroupSwitchItem(
                    "API 密钥",
                    "使用自定义密码加密，仅密钥区受密码保护",
                    GroupPosition.Last,
                    includeApiKeys,
                ) {
                    performHaptic()
                    includeApiKeys = it
                }
            }
        }

        appUi.primaryActionButton(
            if (isBusy) "处理中" else "创建备份",
            !isBusy && (includeOrders || includeSettings || includeRules || includeApiKeys),
        ) {
            performHaptic()
            if (includeApiKeys) passwordDialogMode = PasswordDialogMode.Create else startBackup(null)
        }

        appUi.preferenceSection("恢复数据") {
            appUi.settingsGroup(Modifier) {
                appUi.settingsGroupItem(
                    "选择备份文件",
                    stagedBackup?.preview?.let(::previewSummary) ?: "支持新版与旧版 .backup 文件",
                    GroupPosition.Single,
                    {
                        performHaptic()
                        if (!isBusy) openDocumentLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    },
                    null,
                )
            }
        }

        stagedBackup?.preview?.let { preview ->
            BackupPreviewContent(
                preview = preview,
                restoreOrders = restoreOrders,
                onRestoreOrdersChange = {
                    performHaptic()
                    restoreOrders = it
                    if (!it) restoreScreenshots = false
                },
                restoreSettings = restoreSettings,
                onRestoreSettingsChange = {
                    performHaptic()
                    restoreSettings = it
                },
                restoreRules = restoreRules,
                onRestoreRulesChange = {
                    performHaptic()
                    restoreRules = it
                },
                restoreScreenshots = restoreScreenshots,
                onRestoreScreenshotsChange = {
                    if (restoreOrders) {
                        performHaptic()
                        restoreScreenshots = it
                    }
                },
                restoreApiKeys = restoreApiKeys,
                onRestoreApiKeysChange = {
                    performHaptic()
                    restoreApiKeys = it
                },
                restorePolicy = restorePolicy,
                onRestorePolicyChange = {
                    performHaptic()
                    restorePolicy = it
                },
            )
            appUi.primaryActionButton(
                if (isBusy) "正在恢复" else "确认恢复",
                !isBusy && (restoreOrders || restoreSettings || restoreRules || restoreApiKeys),
            ) {
                performHaptic()
                if (restoreApiKeys && preview.hasApiKeys) {
                    passwordDialogMode = PasswordDialogMode.Restore
                } else {
                    startRestore(null)
                }
            }
        }

        restoreReport?.let { report -> appUi.messageBlock(restoreResultText(report), false) }
    }

    passwordDialogMode?.let { mode ->
        appUi.securePasswordDialog(
            mode == PasswordDialogMode.Create,
            performHaptic,
            {
                passwordDialogMode = null
            },
            { password ->
                passwordDialogMode = null
                if (mode == PasswordDialogMode.Create) startBackup(password) else startRestore(password)
            },
        )
    }
}

@Composable
private fun BackupPreviewContent(
    preview: BackupPreview,
    restoreOrders: Boolean,
    onRestoreOrdersChange: (Boolean) -> Unit,
    restoreSettings: Boolean,
    onRestoreSettingsChange: (Boolean) -> Unit,
    restoreRules: Boolean,
    onRestoreRulesChange: (Boolean) -> Unit,
    restoreScreenshots: Boolean,
    onRestoreScreenshotsChange: (Boolean) -> Unit,
    restoreApiKeys: Boolean,
    onRestoreApiKeysChange: (Boolean) -> Unit,
    restorePolicy: RestoreOrderPolicy,
    onRestorePolicyChange: (RestoreOrderPolicy) -> Unit,
) {
    val appUi = LocalAppUi.current
    val restoreOptions = buildList {
        if (preview.orderCount > 0 || preview.groupCount > 0) add(
            RestoreToggleOption(
                title = "订单与订单组",
                description = buildString {
                    append("${preview.orderCount} 条订单 · ${preview.groupCount} 个组")
                    if (preview.conflictingOrderCount > 0) {
                        append(" · ${preview.conflictingOrderCount} 条与本机冲突")
                    }
                },
                checked = restoreOrders,
                onCheckedChange = onRestoreOrdersChange,
            ),
        )
        if (preview.settingsCount > 0) add(
            RestoreToggleOption(
                title = "设置",
                description = "${preview.settingsCount} 项，将按目标设备能力自动调整",
                checked = restoreSettings,
                onCheckedChange = onRestoreSettingsChange,
            ),
        )
        if (preview.ruleFileCount > 0) add(
            RestoreToggleOption(
                title = "规则源",
                description = "${preview.ruleFileCount} 个文件，提交成功后重新加载",
                checked = restoreRules,
                onCheckedChange = onRestoreRulesChange,
            ),
        )
        if (preview.screenshotCount > 0) add(
            RestoreToggleOption(
                title = "截图文件",
                description = "${preview.screenshotCount} 个去重文件，需要同时恢复订单",
                checked = restoreScreenshots,
                onCheckedChange = onRestoreScreenshotsChange,
            ),
        )
        if (preview.hasApiKeys) add(
            RestoreToggleOption(
                title = "API 密钥",
                description = "默认不恢复，需要输入创建备份时的密码",
                checked = restoreApiKeys,
                onCheckedChange = onRestoreApiKeysChange,
            ),
        )
    }
    appUi.preferenceSection("恢复预览") {
        appUi.settingsGroup(Modifier) {
            restoreOptions.forEachIndexed { index, option ->
                appUi.settingsGroupSwitchItem(
                    option.title,
                    option.description,
                    groupPosition(index, restoreOptions.size),
                    option.checked,
                    option.onCheckedChange,
                )
            }
        }
    }
    if (restoreOrders) {
        appUi.preferenceSection("订单冲突") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                appUi.choiceChip(
                    "合并并保留本机",
                    restorePolicy == RestoreOrderPolicy.MERGE_KEEP_LOCAL,
                    { onRestorePolicyChange(RestoreOrderPolicy.MERGE_KEEP_LOCAL) },
                    Modifier.weight(1f),
                )
                appUi.choiceChip(
                    "以备份覆盖",
                    restorePolicy == RestoreOrderPolicy.REPLACE_ALL,
                    { onRestorePolicyChange(RestoreOrderPolicy.REPLACE_ALL) },
                    Modifier.weight(1f),
                )
            }
        }
    }
    if (restoreOrders && restorePolicy == RestoreOrderPolicy.REPLACE_ALL) {
        appUi.messageBlock(
            "覆盖模式会先删除本机全部订单和订单组，再写入备份内容。此操作无法在应用内撤销。",
            true,
        )
    }
    if (preview.compatibilityAdjustments.isNotEmpty()) {
        appUi.messageBlock(
            buildString {
                appendLine("目标设备兼容性调整")
                append(preview.compatibilityAdjustments.joinToString("\n") { "· $it" })
            },
            false,
        )
    }
    if (preview.warnings.isNotEmpty()) {
        appUi.messageBlock(
            preview.warnings.joinToString("\n") { "· $it" },
            true,
        )
    }
}

private data class RestoreToggleOption(
    val title: String,
    val description: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
)

private fun groupPosition(index: Int, count: Int): GroupPosition = when {
    count <= 1 -> GroupPosition.Single
    index == 0 -> GroupPosition.First
    index == count - 1 -> GroupPosition.Last
    else -> GroupPosition.Middle
}

@Composable
internal fun MiuixMessageBlock(text: String, isError: Boolean) {
    Card(modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
        MiuixText(
            text = text,
            modifier = Modifier.padding(16.dp),
            color = if (isError) {
                top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.error
            } else {
                top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary
            },
        )
    }
}

@Composable
internal fun Md3eMessageBlock(text: String, isError: Boolean) {
    androidx.compose.material3.Surface(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            color = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        )
    }
}

@Composable
internal fun MiuixPrimaryActionButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    MiuixTextButton(
        text = text,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(50.dp),
        colors = ButtonDefaults.textButtonColorsPrimary(),
    )
}

@Composable
internal fun Md3ePrimaryActionButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(15.dp),
    ) {
        Text(text)
    }
}

@Composable
internal fun MiuixSecurePasswordDialog(
    requireConfirmation: Boolean,
    performHaptic: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val valid = password.length >= 8 && (!requireConfirmation || password == confirmation)
    var showDialog by remember { mutableStateOf(true) }
    var confirmedPassword by remember { mutableStateOf<String?>(null) }
    WindowDialog(
        title = if (requireConfirmation) "设置备份密码" else "输入备份密码",
        show = showDialog,
        onDismissRequest = {
            performHaptic()
            showDialog = false
        },
        onDismissFinished = {
            confirmedPassword?.let(onConfirm) ?: onDismiss()
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MiuixTextField(
                value = password,
                onValueChange = { password = it },
                label = "至少 8 位",
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
            )
            if (requireConfirmation) {
                MiuixTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = "再次输入密码",
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixTextButton(
                    "取消",
                    onClick = {
                        performHaptic()
                        showDialog = false
                    },
                    modifier = Modifier.weight(1f),
                )
                MiuixTextButton(
                    "确定",
                    onClick = {
                        performHaptic()
                        confirmedPassword = password
                        showDialog = false
                    },
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
internal fun Md3eSecurePasswordDialog(
    requireConfirmation: Boolean,
    performHaptic: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val valid = password.length >= 8 && (!requireConfirmation || password == confirmation)
    AlertDialog(
        onDismissRequest = {
            performHaptic()
            onDismiss()
        },
        title = { Text(if (requireConfirmation) "设置备份密码" else "输入备份密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("至少 8 位") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    singleLine = true,
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text("再次输入密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    performHaptic()
                    onConfirm(password)
                },
                enabled = valid,
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    performHaptic()
                    onDismiss()
                },
            ) { Text("取消") }
        },
        shape = RoundedCornerShape(15.dp),
    )
}

private fun restoreResultText(report: RestoreReport): String = buildString {
    append(report.summary())
    appendLine()
    append("设置 ${report.restoredSettings} 项 · 规则 ${report.restoredRuleFiles} 个 · ")
    append("截图 ${report.restoredScreenshots} 个 · 密钥 ${report.restoredApiKeys} 个")
    if (report.adjustments.isNotEmpty()) {
        appendLine()
        append(report.adjustments.joinToString("\n") { "· $it" })
    }
    append("\n需要重新加载界面的设置将在下次启动时生效，应用不会自动重启。")
}

private fun previewSummary(preview: BackupPreview): String {
    val date = if (preview.createdAt > 0L) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(preview.createdAt))
    } else "时间未知"
    val size = formatBytes(preview.archiveSizeBytes)
    return buildString {
        append(if (preview.isLegacy) "旧版备份" else "V${preview.formatVersion}")
        append(" · 应用 ${preview.appVersion} · $date · $size")
        if (preview.conflictingOrderCount > 0) {
            append(" · ${preview.conflictingOrderCount} 条冲突")
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", bytes / 1024f / 1024f)
    bytes >= 1024L -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024f)
    else -> "$bytes B"
}

private enum class PasswordDialogMode { Create, Restore }
