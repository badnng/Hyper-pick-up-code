package com.Badnng.moe

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import android.app.AppOpsManager
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Process
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

enum class OnboardingStep {
    Permissions,   // 权限设置
    Features       // 功能介绍
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var currentStep by remember { mutableStateOf(OnboardingStep.Permissions) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    // 震动辅助函数（使用Compose HapticFeedback API，与主页一致）
    val performHaptic = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // 权限状态
    var hasNotificationPermission by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var isIgnoringBattery by remember { mutableStateOf(false) }
    var hasUsageStatsPermission by remember { mutableStateOf(checkUsageStatsPermission(context)) }
    var shizukuReady by remember { mutableStateOf(false) }
    var keepAliveEnabled by remember { mutableStateOf(prefs.getBoolean("keep_alive_enabled", false)) }

    // 定期检查权限状态
    LaunchedEffect(Unit) {
        while (true) {
            hasNotificationPermission = NotificationManagerCompat.from(context).areNotificationsEnabled()
            isIgnoringBattery = checkBatteryOptimization(context)
            hasUsageStatsPermission = checkUsageStatsPermission(context)
            shizukuReady = withContext(Dispatchers.IO) { isShizukuReady() }
            keepAliveEnabled = prefs.getBoolean("keep_alive_enabled", false)
            delay(1500)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // 顶部标题栏 + 进度条 + 内容区域
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部标题栏
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "欢迎使用澎湃记",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (currentStep == OnboardingStep.Permissions) "第 1 步：权限设置" else "第 2 步：功能介绍",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            // 进度指示器（带动画）
            val animatedProgress by animateFloatAsState(
                targetValue = if (currentStep == OnboardingStep.Permissions) 0.5f else 1f,
                animationSpec = tween(durationMillis = 500),
                label = "progress_animation"
            )
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // 内容区域（底部留出按钮高度的空间，防止内容被遮住）
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = currentStep,
                    label = "step_transition",
                    transitionSpec = {
                        if (targetState.ordinal > initialState.ordinal) {
                            // 向前：从右滑入，向左滑出
                            slideInHorizontally(animationSpec = tween(300)) { it } togetherWith
                                    slideOutHorizontally(animationSpec = tween(300)) { -it }
                        } else {
                            // 向后：从左滑入，向右滑出
                            slideInHorizontally(animationSpec = tween(300)) { -it } togetherWith
                                    slideOutHorizontally(animationSpec = tween(300)) { it }
                        }
                    }
                ) { step ->
                    when (step) {
                        OnboardingStep.Permissions -> {
                            PermissionsStep(
                                hasNotificationPermission = hasNotificationPermission,
                                isIgnoringBattery = isIgnoringBattery,
                                hasUsageStatsPermission = hasUsageStatsPermission,
                                shizukuReady = shizukuReady,
                                keepAliveEnabled = keepAliveEnabled,
                                onToggleKeepAlive = {
                                    keepAliveEnabled = !keepAliveEnabled
                                    prefs.edit().putBoolean("keep_alive_enabled", keepAliveEnabled).apply()
                                }
                            )
                        }
                        OnboardingStep.Features -> {
                            FeaturesStep()
                        }
                    }
                }
            }
        }

        // 悬浮按钮，无背景，叠在内容上方
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            when (currentStep) {
                OnboardingStep.Permissions -> {
                    val allRequiredGranted = hasNotificationPermission && isIgnoringBattery
                    Button(
                        onClick = {
                            if (allRequiredGranted) {
                                performHaptic()
                                currentStep = OnboardingStep.Features
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (allRequiredGranted)
                                MaterialTheme.colorScheme.primary
                            else
                                Color(0xFFBDBDBD),
                            contentColor = if (allRequiredGranted)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                Color(0xFF616161)
                        )
                    ) {
                        Text(
                            text = "下一步",
                            fontSize = 16.sp
                        )
                    }
                }
                OnboardingStep.Features -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { 
                                performHaptic()
                                currentStep = OnboardingStep.Permissions 
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            border = BorderStroke(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = "上一步",
                                fontSize = 16.sp
                            )
                        }
                        Button(
                            onClick = {
                                performHaptic()
                                // 保存引导完成状态
                                prefs.edit().putBoolean("onboarding_completed", true).apply()
                                onComplete()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "我知道了",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsStep(
    hasNotificationPermission: Boolean,
    isIgnoringBattery: Boolean,
    hasUsageStatsPermission: Boolean,
    shizukuReady: Boolean,
    keepAliveEnabled: Boolean,
    onToggleKeepAlive: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // 必要权限说明
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
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
                    text = "请先完成必要权限设置，以确保应用正常运行。可选权限可以稍后在设置中开启。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    lineHeight = 18.sp
                )
            }
        }

        // 必要权限部分
        Text(
            text = "必要权限",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        PermissionCard(
            title = "通知权限",
            description = "用于收取取餐码通知，关闭后将无法收到取餐提醒",
            icon = Icons.Default.Notifications,
            isGranted = hasNotificationPermission,
            isRequired = true,
            onClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                context.startActivity(intent)
            }
        )

        PermissionCard(
            title = "忽略电池优化",
            description = "防止系统休眠时清理应用，确保后台正常运行",
            icon = Icons.Default.BatterySaver,
            isGranted = isIgnoringBattery,
            isRequired = true,
            onClick = {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(intent)
                }
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 可选权限部分
        Text(
            text = "可选权限（推荐）",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PermissionCard(
            title = "应用使用情况",
            description = "能更准确识别当前所在的 App，推荐开启",
            icon = Icons.Default.Apps,
            isGranted = hasUsageStatsPermission,
            isRequired = false,
            onClick = {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
        )

        PermissionCard(
            title = "Shizuku 运行状态",
            description = "用于免授权截图识别，需要安装 Shizuku 应用",
            icon = Icons.Default.Adb,
            isGranted = shizukuReady,
            isRequired = false,
            onClick = {
                if (Shizuku.pingBinder()) {
                    try {
                        Shizuku.requestPermission(1001)
                    } catch (e: Exception) {}
                }
            }
        )

        PermissionCard(
            title = "启用保活",
            description = "开启后切到后台时自动隐藏卡片并提示，防止系统清理",
            icon = Icons.Default.Lock,
            isGranted = keepAliveEnabled,
            isRequired = false,
            onClick = onToggleKeepAlive,
            isToggle = true
        )

        // 底部额外空白，防止按钮遮挡最后一个内容项
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    isRequired: Boolean,
    onClick: () -> Unit,
    isToggle: Boolean = false
) {
    Surface(
        onClick = if (!isToggle) onClick else { {} },
        shape = RoundedCornerShape(16.dp),
        color = if (isGranted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isGranted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // 内容
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isRequired) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.error
                        ) {
                            Text(
                                text = "必要",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            // 状态/操作
            if (isToggle) {
                Switch(
                    checked = isGranted,
                    onCheckedChange = { onClick() }
                )
            } else if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "已授权",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "去设置",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@SuppressLint("WrongConstant")
@Composable
private fun FeaturesStep() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // 说明
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "澎湃记支持三种识别方式，你可以根据使用场景选择最适合的方式。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    lineHeight = 18.sp
                )
            }
        }

        // 功能1：截图识别
        FeatureCard(
            title = "截图识别",
            subtitle = "常用做法",
            description = "通过控制中心磁贴快速截图识别，识别完成后自动通知你取餐码信息。",
            icon = Icons.Default.CameraAlt,
            steps = listOf(
                "下拉打开控制中心",
                "点击「截图识别」磁贴",
                "应用自动截图并识别",
                "收到取餐码通知"
            ),
            actionLabel = "添加快捷设置",
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val statusBarManager = context.getSystemService(Context.STATUS_BAR_SERVICE) as StatusBarManager
                    statusBarManager.requestAddTileService(
                        ComponentName(context, CaptureTileService::class.java),
                        "截图识别",
                        Icon.createWithResource(context, R.drawable.note),
                        {},
                        {}
                    )
                }
            }
        )

        // 功能2：划词识别
        FeatureCard(
            title = "划词识别",
            subtitle = "识别率最高",
            description = "在特定页面选择文字后，点击右上角菜单选择「识别取餐码」，适合大量文字或短信记录。",
            icon = Icons.Default.TextFields,
            steps = listOf(
                "长按选择文字",
                "点击右上角「...」菜单",
                "选择「识别取餐码」",
                "自动提取并保存"
            ),
            actionLabel = null,
            onAction = null
        )

        // 功能3：分享识别
        FeatureCard(
            title = "分享识别",
            subtitle = "更方便",
            description = "部分机型截图后可以进行分享，点击分享/发送后选择澎湃记，即可自动识别。",
            icon = Icons.Default.Share,
            steps = listOf(
                "截图后点击分享按钮",
                "在分享列表中选择澎湃记",
                "应用自动识别截图内容",
                "生成取餐码通知"
            ),
            actionLabel = null,
            onAction = null
        )

        // 底部额外空白，防止按钮遮挡最后一个内容项
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    description: String,
    icon: ImageVector,
    steps: List<String>,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = subtitle,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 描述
            Text(
                text = description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            // 步骤
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.Top) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = step,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // 操作按钮
            if (actionLabel != null && onAction != null) {
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

// 辅助函数
private fun checkUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun checkBatteryOptimization(context: Context): Boolean {
    @Suppress("DEPRECATION")
    return try {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    } catch (e: Exception) {
        false
    }
}

private fun isShizukuReady(): Boolean {
    return try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }
}