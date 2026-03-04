package com.Badnng.moe.screens

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import com.Badnng.moe.CaptureTileService
import com.Badnng.moe.R

enum class SettingsPage {
    Main, Preference, Permission, Screenshot, About
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onSubPageStatusChange: (Boolean) -> Unit = {}
) {
    var currentPage by remember { mutableStateOf(SettingsPage.Main) }
    var previousPage by remember { mutableStateOf(SettingsPage.Main) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var isPredictiveBackInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(currentPage) {
        onSubPageStatusChange(currentPage != SettingsPage.Main)
        if (currentPage != SettingsPage.Main) previousPage = currentPage
    }

    PredictiveBackHandler(enabled = currentPage != SettingsPage.Main) { backEvent: Flow<androidx.activity.BackEventCompat> ->
        isPredictiveBackInProgress = true
        try {
            backEvent.collect { event -> backProgress = event.progress }
            currentPage = SettingsPage.Main
        } catch (e: CancellationException) {
            currentPage = previousPage
        } finally {
            isPredictiveBackInProgress = false
            backProgress = 0f
        }
    }

    val currentScale = if (isPredictiveBackInProgress) 1f - (backProgress * 0.08f) else 1f
    val currentTranslationX = if (isPredictiveBackInProgress) backProgress * 100f else 0f
    val currentCornerRadius = if (isPredictiveBackInProgress) (backProgress * 32).dp else 0.dp

    Box(modifier = modifier.fillMaxSize()) {
        MainSettingsList(onNavigate = { currentPage = it })

        AnimatedVisibility(
            visible = currentPage != SettingsPage.Main,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut()
        ) {
            val displayPage = if (currentPage != SettingsPage.Main) currentPage else previousPage
            val title = when (displayPage) {
                SettingsPage.Preference -> "偏好设置"
                SettingsPage.Permission -> "权限设置"
                SettingsPage.Screenshot -> "截图方式"
                SettingsPage.About -> "关于"
                else -> ""
            }
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = currentScale; scaleY = currentScale; translationX = currentTranslationX; shape = RoundedCornerShape(currentCornerRadius); clip = true }.border(width = if (isPredictiveBackInProgress) 1.dp else 0.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = backProgress), shape = RoundedCornerShape(currentCornerRadius)).background(MaterialTheme.colorScheme.background)) {
                SubPage(title, displayPage) { currentPage = SettingsPage.Main }
            }
        }
    }
}

@Composable
fun MainSettingsList(onNavigate: (SettingsPage) -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "设置", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(24.dp))
        SettingsListItem(title = "偏好设置", description = "管理自行习惯的设置", onClick = { onNavigate(SettingsPage.Preference) })
        SettingsListItem(title = "权限设置", description = "管理此App授予的权限", onClick = { onNavigate(SettingsPage.Permission) })
        SettingsListItem(title = "截图方式", description = "管理App截图的方式", onClick = { onNavigate(SettingsPage.Screenshot) })
        
        SettingsListItem(
            title = "添加到控制中心", 
            description = "将“截图识别”磁贴添加到控制中心快捷栏",
            onClick = { requestAddTile(context) }
        )
        
        SettingsListItem(title = "关于", description = null, onClick = { onNavigate(SettingsPage.About) })
        Spacer(modifier = Modifier.height(100.dp))
    }
}

private fun requestAddTile(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val statusBarManager = context.getSystemService(Context.STATUS_BAR_SERVICE) as StatusBarManager
        statusBarManager.requestAddTileService(
            ComponentName(context, CaptureTileService::class.java),
            "截图识别",
            Icon.createWithResource(context, R.drawable.ic_launcher_foreground),
            {}, {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubPage(title: String, page: SettingsPage, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
        when (page) {
            SettingsPage.Screenshot -> ScreenshotSettingsContent()
            SettingsPage.Permission -> PermissionSettingsContent()
            SettingsPage.Preference -> PreferenceSettingsContent()
            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text = "正在开发中...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) }
        }
    }
}

@Composable
fun PreferenceSettingsContent() {
    val context = LocalContext.current; val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var navAlignment by remember { mutableStateOf(prefs.getString("nav_alignment", "center") ?: "center") }
    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }
    var monetEnabled by remember { mutableStateOf(prefs.getBoolean("monet_enabled", true)) }
    var customHue by remember { mutableFloatStateOf(260f) }
    var selectedColorInt by remember { mutableIntStateOf(prefs.getInt("theme_color", Color(0xFF6750A4).toArgb())) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        PreferenceSection(title = "底栏位置") { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("left" to "靠左", "center" to "居中", "right" to "靠右").forEach { (key, label) -> ChoiceChip(label = label, selected = navAlignment == key, onClick = { navAlignment = key; prefs.edit().putString("nav_alignment", key).apply() }, modifier = Modifier.weight(1f)) } } }
        PreferenceSection(title = "外观设置") { Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)) { PreferenceSwitchItem(title = "莫奈取色 (Dynamic Color)", description = "开启后主题色将跟随系统壁纸自动变化", checked = monetEnabled, onCheckedChange = { monetEnabled = it; prefs.edit().putBoolean("monet_enabled", it).apply() }) } }
        AnimatedVisibility(visible = !monetEnabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) { PreferenceSection(title = "自定义主题色") { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Text("滑动调节色相", style = MaterialTheme.typography.bodySmall); Slider(value = customHue, onValueChange = { customHue = it }, valueRange = 0f..360f, modifier = Modifier.fillMaxWidth()); val previewColor = remember(customHue) { Color.hsv(customHue, 0.7f, 0.9f) }; Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(previewColor).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)); Button(onClick = { selectedColorInt = previewColor.toArgb(); prefs.edit().putInt("theme_color", selectedColorInt).apply() }, shape = RoundedCornerShape(15.dp), modifier = Modifier.weight(1f).height(56.dp)) { Text("应用颜色") } } ; Text("MD3 建议色", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)); val md3Colors = listOf(0xFF6750A4, 0xFF006A60, 0xFF984061, 0xFF005AC1, 0xFF605D62, 0xFF3B6939); Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { md3Colors.forEach { colorLong -> val color = Color(colorLong); Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(color).border(width = if (selectedColorInt == color.toArgb()) 3.dp else 0.dp, color = MaterialTheme.colorScheme.primary, shape = CircleShape).clickable { selectedColorInt = color.toArgb(); prefs.edit().putInt("theme_color", selectedColorInt).apply() }) } } } } }
        PreferenceSection(title = "显示模式") { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { listOf("light" to "浅色", "dark" to "深色", "system" to "跟随系统").forEach { (key, label) -> Row(modifier = Modifier.fillMaxWidth().clickable { themeMode = key; prefs.edit().putString("theme_mode", key).apply() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = themeMode == key, onClick = null); Spacer(Modifier.width(12.dp)); Text(label, fontSize = 16.sp) } } } }
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun ScreenshotSettingsContent() {
    val context = LocalContext.current; val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var captureMode by remember { mutableStateOf(prefs.getString("capture_mode", "media_projection") ?: "media_projection") }
    var shizukuReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { shizukuReady = withContext(Dispatchers.IO) { isShizukuReady() }; if (!shizukuReady && captureMode == "shizuku") { captureMode = "media_projection"; prefs.edit().putString("capture_mode", "media_projection").apply() }; delay(1500) } }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("选择截图技术方案", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        CaptureModeItem(title = "共享屏幕 (MediaProjection)", description = "默认方案，设备兼容性高，但每次使用磁贴需要屏幕共享授权确认。", selected = captureMode == "media_projection", onClick = { captureMode = "media_projection"; prefs.edit().putString("capture_mode", "media_projection").apply() })
        CaptureModeItem(title = "Shizuku 永授权", description = if (shizukuReady) "通过 Shizuku 后可实现免授权后台截图识别。（推荐）" else "Shizuku 未就绪，此选项当前不可用。", selected = captureMode == "shizuku", enabled = shizukuReady, onClick = { if (shizukuReady) { captureMode = "shizuku"; prefs.edit().putString("capture_mode", "shizuku").apply() } })
    }
}

@Composable
fun PermissionSettingsContent() {
    val context = LocalContext.current; var hasNotificationPermission by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }; var shizukuReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { hasNotificationPermission = NotificationManagerCompat.from(context).areNotificationsEnabled(); shizukuReady = withContext(Dispatchers.IO) { isShizukuReady() }; delay(1500) } }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        PermissionItem(title = "通知权限", description = "请授予该权限，该权限用于收取取餐码通知，如关闭/拒绝该权限将会无法收到此通知", isGranted = hasNotificationPermission, actionButton = if (!hasNotificationPermission) { { Button(onClick = { val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) }; context.startActivity(intent) }, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth().height(56.dp)) { Icon(Icons.Default.Build, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("去修复") } } } else null)
        PermissionItem(title = "Shizuku 运行状态", description = "该软件用于免授权截图识别的必须条件，如无则无法使用免授权截图", isGranted = shizukuReady, actionButton = if (!shizukuReady) { { Button(onClick = { if (Shizuku.pingBinder()) { try { Shizuku.requestPermission(1001) } catch (e: Exception) {} } }, shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary), modifier = Modifier.fillMaxWidth().height(56.dp)) { Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("如果Shizuku已运行请点我") } } } else null)
    }
}

@Composable
fun PreferenceSwitchItem(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = RoundedCornerShape(15.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null, modifier = modifier) {
        Box(modifier = Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) { Text(label, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp) }
    }
}

@Composable
fun PreferenceSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        content()
    }
}

@Composable
fun PermissionItem(title: String, description: String, isGranted: Boolean, actionButton: @Composable (() -> Unit)? = null) {
    Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Icon(imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel, contentDescription = null, tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFF44336), modifier = Modifier.size(28.dp))
            }
            Text(text = description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
            if (!isGranted) { Spacer(Modifier.height(4.dp)); actionButton?.invoke() }
        }
    }
}

@Composable
fun CaptureModeItem(title: String, description: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = { if (enabled) onClick() }, shape = RoundedCornerShape(15.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else if (!enabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null, modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f)) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp)); Text(text = description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
            }
            RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        }
    }
}

@Composable
fun SettingsListItem(title: String, description: String?, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick), shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        ListItem(headlineContent = { Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium) }, supportingContent = if (description != null) { { Text(text = description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) } } else null, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
    }
}

private fun isShizukuReady(): Boolean {
    return try { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (e: Exception) { false }
}
