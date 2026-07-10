package com.Badnng.moe.ui.screen.settings

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch as Md3eSwitch
import androidx.compose.material3.Text as Md3eText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.Badnng.moe.service.NotificationListenerRecognitionService
import com.Badnng.moe.ui.miuix.rememberMiuixStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationAppsSettingsContent(
    performHaptic: () -> Unit,
    topPadding: Dp = 0.dp,
    showSystemApps: Boolean = false
) {
    val context = LocalContext.current
    val isMiuix = rememberMiuixStyle()
    var appList by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var enabledApps by remember { mutableStateOf(emptyMap<String, Boolean>()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchText by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(showSystemApps) {
        isLoading = true
        val (apps, enabled) = withContext(Dispatchers.IO) {
            val apps = NotificationListenerRecognitionService.getAllInstalledApps(
                context = context,
                includeSystemApps = showSystemApps
            ).map { it.packageName to it.label }
            apps to NotificationListenerRecognitionService.getEnabledApps(context)
        }
        appList = apps
        enabledApps = enabled
        isLoading = false
    }

    val filteredApps = remember(appList, searchText) {
        if (searchText.isBlank()) {
            appList
        } else {
            appList.filter { (packageName, label) ->
                label.contains(searchText, ignoreCase = true) ||
                        packageName.contains(searchText, ignoreCase = true)
            }
        }
    }
    val activeApps = filteredApps.filter { (packageName, _) -> enabledApps[packageName] == true }
    val inactiveApps = filteredApps.filter { (packageName, _) -> enabledApps[packageName] != true }
    val onToggle: (String, Boolean) -> Unit = { packageName, enabled ->
        performHaptic()
        NotificationListenerRecognitionService.setAppEnabled(context, packageName, enabled)
        enabledApps = NotificationListenerRecognitionService.getEnabledApps(context)
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isMiuix) {
                InfiniteProgressIndicator(modifier = Modifier.size(48.dp))
            } else {
                ContainedLoadingIndicator(
                    modifier = Modifier.size(48.dp),
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    containerShape = MaterialTheme.shapes.large,
                    polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons
                )
            }
        }
    } else if (isMiuix) {
        MiuixNotificationAppsList(
            activeApps = activeApps,
            inactiveApps = inactiveApps,
            searchText = searchText,
            searchExpanded = searchExpanded,
            topPadding = topPadding,
            onSearchTextChange = { searchText = it },
            onSearchExpandedChange = { searchExpanded = it },
            onToggle = onToggle
        )
    } else {
        Md3eNotificationAppsList(
            activeApps = activeApps,
            inactiveApps = inactiveApps,
            searchText = searchText,
            topPadding = topPadding,
            onSearchTextChange = { searchText = it },
            onToggle = onToggle
        )
    }
}

@Composable
private fun MiuixNotificationAppsList(
    activeApps: List<Pair<String, String>>,
    inactiveApps: List<Pair<String, String>>,
    searchText: String,
    searchExpanded: Boolean,
    topPadding: Dp,
    onSearchTextChange: (String) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onToggle: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(
            top = topPadding,
            bottom = 16.dp + WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
        )
    ) {
        item {
            SearchBar(
                modifier = Modifier.padding(horizontal = 12.dp),
                inputField = {
                    InputField(
                        query = searchText,
                        onQueryChange = onSearchTextChange,
                        onSearch = { onSearchExpandedChange(false) },
                        expanded = searchExpanded,
                        onExpandedChange = onSearchExpandedChange,
                        label = "搜索应用名称或包名"
                    )
                },
                expanded = searchExpanded,
                onExpandedChange = onSearchExpandedChange
            ) {}
        }
        item {
            MiuixCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiuixIcon(
                        imageVector = MiuixIcons.Regular.Info,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    MiuixText(
                        text = "选择需要监听通知的应用，开启后将自动识别这些应用通知中的取件码和取餐码",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onPrimaryContainer,
                        lineHeight = 18.sp
                    )
                }
            }
        }
        if (activeApps.isNotEmpty()) {
            item { SmallTitle(text = "已启用 (${activeApps.size})") }
            items(activeApps, key = { it.first }) { (packageName, label) ->
                MiuixAppToggleItem(
                    context = context,
                    packageName = packageName,
                    label = label,
                    enabled = true,
                    onToggle = { onToggle(packageName, it) }
                )
            }
        }
        if (inactiveApps.isNotEmpty()) {
            item { SmallTitle(text = "未启用 (${inactiveApps.size})") }
            items(inactiveApps, key = { it.first }) { (packageName, label) ->
                MiuixAppToggleItem(
                    context = context,
                    packageName = packageName,
                    label = label,
                    enabled = false,
                    onToggle = { onToggle(packageName, it) }
                )
            }
        }
    }
}

@Composable
private fun Md3eNotificationAppsList(
    activeApps: List<Pair<String, String>>,
    inactiveApps: List<Pair<String, String>>,
    searchText: String,
    topPadding: Dp,
    onSearchTextChange: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                top = topPadding,
                bottom = 16.dp + WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
            )
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                ) {
                    Md3eText(
                        text = "选择需要监听通知的应用，开启后将自动识别这些应用通知中的取件码和取餐码",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        lineHeight = 18.sp
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = onSearchTextChange,
                    placeholder = { Md3eText("搜索应用名称或包名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
            }
            if (activeApps.isNotEmpty()) {
                item {
                    Md3eText(
                        text = "已启用 (${activeApps.size})",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(activeApps, key = { it.first }) { (packageName, label) ->
                    Md3eAppToggleItem(
                        context = context,
                        packageName = packageName,
                        label = label,
                        enabled = true,
                        onToggle = { onToggle(packageName, it) }
                    )
                }
            }
            if (inactiveApps.isNotEmpty()) {
                item {
                    Md3eText(
                        text = "未启用 (${inactiveApps.size})",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(inactiveApps, key = { it.first }) { (packageName, label) ->
                    Md3eAppToggleItem(
                        context = context,
                        packageName = packageName,
                        label = label,
                        enabled = false,
                        onToggle = { onToggle(packageName, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MiuixAppToggleItem(
    context: Context,
    packageName: String,
    label: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val icon = rememberApplicationIcon(context, packageName)
    MiuixCard(modifier = Modifier.padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon?.let {
                Image(
                    bitmap = it.toBitmap(192, 192).asImageBitmap(),
                    contentDescription = label,
                    modifier = Modifier.size(40.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                MiuixText(
                    text = label,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
                MiuixText(
                    text = packageName,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            MiuixSwitch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun Md3eAppToggleItem(
    context: Context,
    packageName: String,
    label: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val icon = rememberApplicationIcon(context, packageName)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon?.let {
                Image(
                    bitmap = it.toBitmap(192, 192).asImageBitmap(),
                    contentDescription = label,
                    modifier = Modifier.size(40.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Md3eText(text = label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Md3eText(
                    text = packageName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            Md3eSwitch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun rememberApplicationIcon(context: Context, packageName: String): Drawable? {
    var icon by remember(packageName) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(packageName) {
        icon = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
        }
    }
    return icon
}
