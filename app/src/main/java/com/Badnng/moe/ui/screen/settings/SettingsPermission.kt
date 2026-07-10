package com.Badnng.moe.ui.screen.settings

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.Badnng.moe.helper.AccessibilityShortcutHelper
import com.Badnng.moe.service.KeepAliveService
import com.Badnng.moe.ui.component.GroupPosition
import com.Badnng.moe.ui.component.PermissionItem
import com.Badnng.moe.ui.component.PreferenceSection
import com.Badnng.moe.ui.component.SettingsGroup
import com.Badnng.moe.ui.component.SettingsGroupSwitchItem
import com.Badnng.moe.ui.miuix.rememberMiuixStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PermissionSettingsContent(performHaptic: () -> Unit, topPadding: androidx.compose.ui.unit.Dp = 0.dp, scrollState: androidx.compose.foundation.ScrollState = androidx.compose.foundation.rememberScrollState()) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var hasNotificationPermission by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var hasUsageStatsPermission by remember { mutableStateOf(checkUsageStatsPermission(context)) }
    var shizukuReady by remember { mutableStateOf(false) }
    var persistentNotificationEnabled by remember { mutableStateOf(prefs.getBoolean("persistent_notification_enabled", true)) }
    var isIgnoringBattery by remember { mutableStateOf(false) }
    var hasNotificationListenerPermission by remember { mutableStateOf(com.Badnng.moe.service.NotificationListenerRecognitionService.isNotificationListenerEnabled(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            hasNotificationPermission = NotificationManagerCompat.from(context).areNotificationsEnabled()
            hasUsageStatsPermission = checkUsageStatsPermission(context)
            shizukuReady = withContext(Dispatchers.IO) { isShizukuReady() }
            isIgnoringBattery = checkBatteryOptimization(context)
            hasNotificationListenerPermission = com.Badnng.moe.service.NotificationListenerRecognitionService.isNotificationListenerEnabled(context)
            delay(1500)
        }
    }

    val isMiuix = rememberMiuixStyle()

    // 预构建 actionButton lambdas 避免嵌套语法问题
    val notificationAction: (@Composable () -> Unit)? = if (!hasNotificationPermission) {{
        if (isMiuix) {
            MiuixButton(onClick = {
                performHaptic()
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) })
            }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = MiuixButtonDefaults.buttonColorsPrimary()) {
                MiuixIcon(Icons.Default.Build, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); MiuixText("去修复")
            }
        } else {
            Button(onClick = {
                performHaptic()
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) })
            }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.Build, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("去修复")
            }
        }
    }} else null

    val usageAction: (@Composable () -> Unit)? = if (!hasUsageStatsPermission) {{
        if (isMiuix) {
            MiuixButton(onClick = {
                performHaptic()
                try { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { data = Uri.parse("package:${context.packageName}") }) }
                catch (e: Exception) { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = MiuixButtonDefaults.buttonColorsPrimary()) {
                MiuixIcon(Icons.Default.Security, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); MiuixText("去授权")
            }
        } else {
            Button(onClick = {
                performHaptic()
                try { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { data = Uri.parse("package:${context.packageName}") }) }
                catch (e: Exception) { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.Security, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("去授权")
            }
        }
    }} else null

    val shizukuAction: (@Composable () -> Unit)? = if (!shizukuReady) {{
        if (isMiuix) {
            MiuixButton(onClick = { performHaptic(); if (Shizuku.pingBinder()) { try { Shizuku.requestPermission(1001) } catch (e: Exception) {} } }, colors = MiuixButtonDefaults.buttonColorsPrimary(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                MiuixIcon(MiuixIcons.Regular.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); MiuixText("如果Shizuku已运行请点我")
            }
        } else {
            Button(onClick = { performHaptic(); if (Shizuku.pingBinder()) { try { Shizuku.requestPermission(1001) } catch (e: Exception) {} } }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("如果Shizuku已运行请点我")
            }
        }
    }} else null

    val listenerAction: (@Composable () -> Unit)? = if (!hasNotificationListenerPermission) {{
        if (isMiuix) {
            MiuixButton(onClick = { performHaptic(); context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = MiuixButtonDefaults.buttonColorsPrimary()) {
                MiuixIcon(Icons.Default.Build, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); MiuixText("去授权")
            }
        } else {
            Button(onClick = { performHaptic(); context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.Build, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("去授权")
            }
        }
    }} else null

    val batteryAction: (@Composable () -> Unit)? = if (!isIgnoringBattery) {{
        if (isMiuix) {
            MiuixButton(onClick = {
                performHaptic()
                try { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") }) }
                catch (e: Exception) { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = MiuixButtonDefaults.buttonColorsPrimary()) {
                MiuixIcon(Icons.Default.BatterySaver, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); MiuixText("去设置")
            }
        } else {
            Button(onClick = {
                performHaptic()
                try { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") }) }
                catch (e: Exception) { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.BatterySaver, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("去设置")
            }
        }
    }} else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (isMiuix) 0.dp else 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(if (isMiuix) 0.dp else 32.dp)
    ) {
        Spacer(Modifier.height(topPadding))
        // 第一大类：权限设置
        PreferenceSection(title = "权限设置") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PermissionItem(title = "通知权限", description = "请授予该权限，该权限用于收取取餐码通知，如关闭/拒绝该权限将会无法收到此通知", isGranted = hasNotificationPermission, actionButton = notificationAction)
                PermissionItem(title = "应用使用情况", description = "此权限能更好的识别当前处在的app是哪个 brand，推荐授权！", isGranted = hasUsageStatsPermission, actionButton = usageAction)
                PermissionItem(title = "Shizuku 运行状态", description = "该软件用于免授权截图识别的必须条件，如无则无法使用免授权截图", isGranted = shizukuReady, actionButton = shizukuAction)
                PermissionItem(title = "通知监听权限", description = "用于自动识别外卖、快递等App通知中的取件码", isGranted = hasNotificationListenerPermission, actionButton = listenerAction)
            }
        }

        // 第二大类：保活设置
        PreferenceSection(title = "保活设置") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 说明卡片
                if (isMiuix) {
                    MiuixCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            MiuixIcon(imageVector = MiuixIcons.Regular.Info, contentDescription = null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            MiuixText(text = "开启后，应用在主页返回时会退出在后台并自动隐藏后台任务卡片。", fontSize = 13.sp, color = MiuixTheme.colorScheme.onPrimaryContainer, lineHeight = 18.sp)
                        }
                    }
                } else {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(text = "开启后，应用在主页返回时会退出在后台并自动隐藏后台任务卡片。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, lineHeight = 18.sp)
                        }
                    }
                }

                // 基础设置
                SettingsGroup {
                    SettingsGroupSwitchItem(title = "后台通知", description = "应用在后台时显示通知，帮助系统识别活跃应用以减少被杀概率", position = GroupPosition.Single, checked = persistentNotificationEnabled, onCheckedChange = { performHaptic(); persistentNotificationEnabled = it; prefs.edit().putBoolean("persistent_notification_enabled", it).apply(); if (it) KeepAliveService.showNotification(context) else KeepAliveService.hideNotification(context) })
                }

                // 电池优化
                PermissionItem(title = "忽略电池优化", description = if (isIgnoringBattery) "已加入电池优化白名单，应用不会被系统休眠策略限制" else "加入电池优化白名单，防止系统休眠时清理应用", isGranted = isIgnoringBattery, actionButton = batteryAction)

                // 锁定后台
                if (isMiuix) {
                    MiuixCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.3f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiuixText(text = "锁定方法", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.primary)
                            MiuixText(text = "1. 打开最近任务界面（多任务键或手势上滑悬停）\n2. 找到澎湃记卡片\n3. 长按卡片后点击卡片上的锁图标/下滑卡片使其变为锁定状态", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, lineHeight = 20.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MiuixIcon(imageVector = MiuixIcons.Regular.Lock, contentDescription = null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                MiuixText(text = "锁定后卡片会显示锁图标，不会被一键清理", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.8f))
                            }
                        }
                    }
                } else {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "锁定方法", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(text = "1. 打开最近任务界面（多任务键或手势上滑悬停）\n2. 找到澎湃记卡片\n3. 长按卡片后点击卡片上的锁图标/下滑卡片使其变为锁定状态", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(text = "锁定后卡片会显示锁图标，不会被一键清理", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                            }
                        }
                    }
                }

                // 厂商后台管理
                if (isMiuix) {
                    SmallTitle(text = "各系统设置方法")
                } else {
                    Text(text = "不同厂商有不同的后台管理策略，请根据你的设备品牌进行设置：", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                VendorKeepAliveItem(vendor = "HyperOS", steps = listOf("设置 → 应用设置 → 应用管理 → 澎湃记", "省电策略 → 无限制", "自启动 → 开启"), performHaptic = performHaptic)
                VendorKeepAliveItem(vendor = "ColorOS", steps = listOf("设置 → 应用管理 → 澎湃记", "电池 → 后台冻结 → 关闭", "自启动 → 开启"), performHaptic = performHaptic)
                VendorKeepAliveItem(vendor = "OriginOS", steps = listOf("设置 → 更多设置 → 权限管理 → 澎湃记", "自启动 → 开启", "后台弹出界面 → 允许"), performHaptic = performHaptic)
                VendorKeepAliveItem(vendor = "OneUI", steps = listOf("设置 → 应用程序 → 澎湃记", "电池 → 不受限制"), performHaptic = performHaptic)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun checkUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun checkBatteryOptimization(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}
