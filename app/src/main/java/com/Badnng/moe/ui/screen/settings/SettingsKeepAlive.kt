package com.Badnng.moe.ui.screen.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.ExpandLess as Md3eExpandLess
import androidx.compose.material.icons.filled.ExpandMore as Md3eExpandMore
import androidx.compose.material.icons.filled.Info as Md3eInfo
import androidx.compose.material.icons.filled.Lock as Md3eLock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.ui.component.PermissionItem
import com.Badnng.moe.ui.component.PreferenceSection
import com.Badnng.moe.ui.miuix.rememberMiuixStyle
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun KeepAliveSettingsContent(
    performHaptic: () -> Unit,
    topPadding: Dp = 0.dp,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState()
) {
    val context = LocalContext.current
    val isMiuix = rememberMiuixStyle()
    var isIgnoringBattery by remember { mutableStateOf(false) }

    fun checkBatteryOptimization() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    LaunchedEffect(Unit) {
        while (true) {
            checkBatteryOptimization()
            delay(2000)
        }
    }

    val openBatterySettings = {
        performHaptic()
        try {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (isMiuix) 0.dp else 16.dp)
            .verticalScroll(scrollState)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        verticalArrangement = Arrangement.spacedBy(if (isMiuix) 0.dp else 24.dp)
    ) {
        Spacer(Modifier.height(topPadding))

        if (isMiuix) {
            MiuixInfoCard()
        } else {
            Md3eInfoCard()
        }

        PreferenceSection(title = "电池优化") {
            PermissionItem(
                title = "忽略电池优化",
                description = if (isIgnoringBattery) {
                    "已加入电池优化白名单，应用不会被系统休眠策略限制"
                } else {
                    "加入电池优化白名单，防止系统休眠时清理应用"
                },
                isGranted = isIgnoringBattery,
                actionButton = if (isIgnoringBattery) null else {
                    {
                        if (isMiuix) {
                            MiuixButton(
                                onClick = openBatterySettings,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors = MiuixButtonDefaults.buttonColorsPrimary()
                            ) {
                                MiuixIcon(Icons.Default.BatterySaver, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                MiuixText("去设置")
                            }
                        } else {
                            Button(
                                onClick = openBatterySettings,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) {
                                Icon(Icons.Default.BatterySaver, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("去设置")
                            }
                        }
                    }
                }
            )
        }

        PreferenceSection(title = "锁定后台") {
            if (isMiuix) {
                MiuixLockBackgroundContent()
            } else {
                Md3eLockBackgroundContent()
            }
        }

        PreferenceSection(title = "厂商后台管理") {
            if (isMiuix) {
                MiuixText(
                    text = "不同厂商有不同的后台管理策略，请根据你的设备品牌进行设置：",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            } else {
                Text(
                    text = "不同厂商有不同的后台管理策略，请根据你的设备品牌进行设置：",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemedVendorKeepAliveItem(
                    vendor = "HyperOS",
                    steps = listOf("设置 → 应用设置 → 应用管理 → 澎湃记", "省电策略 → 无限制", "自启动 → 开启"),
                    isMiuix = isMiuix,
                    performHaptic = performHaptic
                )
                ThemedVendorKeepAliveItem(
                    vendor = "ColorOS",
                    steps = listOf("设置 → 应用管理 → 澎湃记", "电池 → 后台冻结 → 关闭", "自启动 → 开启"),
                    isMiuix = isMiuix,
                    performHaptic = performHaptic
                )
                ThemedVendorKeepAliveItem(
                    vendor = "OriginOS",
                    steps = listOf("设置 → 更多设置 → 权限管理 → 澎湃记", "自启动 → 开启", "后台弹出界面 → 允许"),
                    isMiuix = isMiuix,
                    performHaptic = performHaptic
                )
                ThemedVendorKeepAliveItem(
                    vendor = "OneUI",
                    steps = listOf("设置 → 应用程序 → 澎湃记", "电池 → 不受限制"),
                    isMiuix = isMiuix,
                    performHaptic = performHaptic
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun MiuixInfoCard() {
    MiuixCard(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiuixIcon(
                imageVector = MiuixIcons.Regular.Info,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            MiuixText(
                text = "开启保活后，应用切到后台时会自动隐藏卡片并提示正在后台运行，防止系统清理导致通知失效。部分设备可能需要额外设置。",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onPrimaryContainer,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun Md3eInfoCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Md3eInfo,
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
}

@Composable
private fun MiuixLockBackgroundContent() {
    MiuixText(
        text = "在最近任务界面锁定应用，防止被系统一键清理：",
        modifier = Modifier.padding(horizontal = 12.dp),
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    Spacer(Modifier.height(8.dp))
    MiuixCard(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MiuixText(
                text = "锁定方法",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.primary
            )
            MiuixText(
                text = "1. 打开最近任务界面（多任务键或手势上滑悬停）\n2. 找到澎湃记卡片\n3. 长按卡片后点击卡片上的锁图标/下滑卡片使其变为锁定状态",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiuixIcon(
                    imageVector = MiuixIcons.Regular.Lock,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                MiuixText(
                    text = "锁定后卡片会显示锁图标，不会被一键清理",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun Md3eLockBackgroundContent() {
    Text(
        text = "在最近任务界面锁定应用，防止被系统一键清理：",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
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
                    imageVector = Icons.Default.Md3eLock,
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

@Composable
fun VendorKeepAliveItem(
    vendor: String,
    steps: List<String>,
    performHaptic: () -> Unit
) {
    ThemedVendorKeepAliveItem(
        vendor = vendor,
        steps = steps,
        isMiuix = rememberMiuixStyle(),
        performHaptic = performHaptic
    )
}

@Composable
private fun ThemedVendorKeepAliveItem(
    vendor: String,
    steps: List<String>,
    isMiuix: Boolean,
    performHaptic: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val onClick = {
        performHaptic()
        expanded = !expanded
    }

    if (isMiuix) {
        MiuixCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiuixText(
                        text = vendor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.primary
                    )
                    MiuixIcon(
                        imageVector = if (expanded) MiuixIcons.Regular.ExpandLess else MiuixIcons.Regular.ExpandMore,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    MiuixVendorSteps(steps)
                }
            }
        }
    } else {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
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
                        imageVector = if (expanded) Icons.Default.Md3eExpandLess else Icons.Default.Md3eExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    Md3eVendorSteps(steps)
                }
            }
        }
    }
}

@Composable
private fun MiuixVendorSteps(steps: List<String>) {
    Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.Top) {
                MiuixText(
                    text = "${index + 1}.",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                MiuixText(
                    text = step,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun Md3eVendorSteps(steps: List<String>) {
    Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
