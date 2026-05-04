package com.Badnng.moe.ui.screen.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.Badnng.moe.helper.BatteryOptimizationHelper
import com.Badnng.moe.ui.component.PermissionItem
import com.Badnng.moe.ui.component.PreferenceSection
import com.Badnng.moe.ui.component.PreferenceSwitchItem
import kotlinx.coroutines.delay

@Composable
fun KeepAliveSettingsContent(performHaptic: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var keepAliveEnabled by remember { mutableStateOf(prefs.getBoolean("keep_alive_enabled", false)) }
    var isIgnoringBattery by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 检测电池优化白名单状态
    fun checkBatteryOptimization() {
        isIgnoringBattery = BatteryOptimizationHelper.isGranted(context)
    }

    LaunchedEffect(Unit) {
        while (true) {
            checkBatteryOptimization()
            delay(2000)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkBatteryOptimization()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        // 说明卡片
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "开启保活后，应用切到后台时会自动隐藏卡片并提示正在后台运行，防止系统清理导致通知失效。部分设备可能需要额外设置。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    lineHeight = 18.sp
                )
            }
        }

        PreferenceSection(title = "基础设置") {
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            ) {
                PreferenceSwitchItem(
                    title = "启用保活",
                    description = "开启后切到后台时自动隐藏卡片并提示",
                    checked = keepAliveEnabled,
                    onCheckedChange = { enabled ->
                        performHaptic()
                        keepAliveEnabled = enabled
                        prefs.edit().putBoolean("keep_alive_enabled", enabled).apply()
                    }
                )
            }
        }

        PreferenceSection(title = "电池优化") {
            PermissionItem(
                title = "忽略电池优化",
                description = if (isIgnoringBattery) "已加入电池优化白名单，应用不会被系统休眠策略限制"
                else "加入电池优化白名单，防止系统休眠时清理应用",
                isGranted = isIgnoringBattery,
                actionButton = if (!isIgnoringBattery) {
                    {
                        Button(
                            onClick = {
                                performHaptic()
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                }
                            },
                            shape = RoundedCornerShape(15.dp),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Icon(Icons.Default.BatterySaver, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("去设置")
                        }
                    }
                } else null
            )
        }

        PreferenceSection(title = "锁定后台") {
            Text(
                text = "在最近任务界面锁定应用，防止被系统一键清理：",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "锁定方法",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "1. 打开最近任务界面（多任务键或手势上滑悬停）\n2. 找到澎湃记卡片\n3. 长按卡片后点击卡片上的锁图标/下滑卡片使其变为锁定状态",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "锁定后卡片会显示锁图标，不会被一键清理",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        PreferenceSection(title = "厂商后台管理") {
            Text(
                text = "不同厂商有不同的后台管理策略，请根据你的设备品牌进行设置：",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            VendorKeepAliveItem(
                vendor = "HyperOS",
                steps = listOf("设置 → 应用设置 → 应用管理 → 澎湃记", "省电策略 → 无限制", "自启动 → 开启"),
                performHaptic = performHaptic
            )

            VendorKeepAliveItem(
                vendor = "ColorOS",
                steps = listOf("设置 → 应用管理 → 澎湃记", "电池 → 后台冻结 → 关闭", "自启动 → 开启"),
                performHaptic = performHaptic
            )

            VendorKeepAliveItem(
                vendor = "OriginOS",
                steps = listOf("设置 → 更多设置 → 权限管理 → 澎湃记", "自启动 → 开启", "后台弹出界面 → 允许"),
                performHaptic = performHaptic
            )

            VendorKeepAliveItem(
                vendor = "OneUI",
                steps = listOf("设置 → 应用程序 → 澎湃记", "电池 → 不受限制"),
                performHaptic = performHaptic
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun VendorKeepAliveItem(vendor: String, steps: List<String>, performHaptic: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        onClick = { performHaptic(); expanded = !expanded },
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = vendor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    steps.forEachIndexed { index, step ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "${index + 1}.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = step,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
