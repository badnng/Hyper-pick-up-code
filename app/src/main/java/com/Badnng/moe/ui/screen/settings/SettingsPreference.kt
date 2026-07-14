package com.Badnng.moe.ui.screen.settings

import android.content.Context
import android.os.VibratorManager
import com.Badnng.moe.ui.theme.Md3Presets
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.draw.drawWithContent
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import com.Badnng.moe.helper.SuperIslandHelper
import com.Badnng.moe.privacy.PrivacyConsent
import com.Badnng.moe.ui.theme.MD3E_MONET_ENABLED_KEY
import com.Badnng.moe.ui.theme.MIUIX_MONET_ENABLED_KEY
import com.Badnng.moe.ui.miuix.MIUIX_FLOATING_NAV_BAR_STYLE_KEY
import com.Badnng.moe.ui.miuix.MiuixFloatingNavigationBarStyle
import com.Badnng.moe.ui.miuix.MiuixSettingsLazyColumn
import com.Badnng.moe.ui.miuix.rememberMiuixStyle
import com.Badnng.moe.ui.component.CaptureModeItem
import com.Badnng.moe.ui.component.ChoiceChip
import com.Badnng.moe.ui.component.GroupPosition
import com.Badnng.moe.ui.component.PreferenceSection
import com.Badnng.moe.ui.component.PreferenceSwitchItem
import com.Badnng.moe.ui.component.PrivacyConsentBottomSheet
import com.Badnng.moe.ui.component.SettingsGroup
import com.Badnng.moe.ui.component.SettingsGroupItem
import com.Badnng.moe.ui.component.SettingsGroupSwitchItem
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PreferenceSettingsContent(performHaptic: () -> Unit, onNavigate: (SettingsPage) -> Unit = {}, topPadding: androidx.compose.ui.unit.Dp = 0.dp, scrollState: androidx.compose.foundation.ScrollState = androidx.compose.foundation.rememberScrollState()) {
    val context = LocalContext.current; val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val configuration = LocalConfiguration.current
    val isLargeScreen = configuration.screenWidthDp >= 700
    val isFoldableDevice = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
    }
    val hasVibrator = remember(context) {
        context.getSystemService(VibratorManager::class.java)
            ?.defaultVibrator
            ?.hasVibrator() == true
    }
    var navAlignment by remember { mutableStateOf(prefs.getString("nav_alignment", "center") ?: "center") }
    var largeScreenNavAdaptiveEnabled by remember {
        mutableStateOf(prefs.getBoolean("large_screen_nav_adaptive_enabled", true))
    }
    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }
    var md3eMonetEnabled by remember {
        mutableStateOf(prefs.getBoolean(MD3E_MONET_ENABLED_KEY, true))
    }
    var miuixMonetEnabled by remember {
        mutableStateOf(prefs.getBoolean(MIUIX_MONET_ENABLED_KEY, false))
    }
    var amoledPureBlack by remember { mutableStateOf(prefs.getBoolean("amoled_pure_black", false)) }
    var hapticEnabled by remember { mutableStateOf(prefs.getBoolean("haptic_enabled", true)) }
    var predictiveBackEnabled by remember {
        mutableStateOf(prefs.getBoolean("predictive_back_enabled", true))
    }
    var showOnboardingOnNextLaunch by remember { mutableStateOf(prefs.getBoolean("show_onboarding_on_next_launch", false)) }
    var customHue by remember { mutableFloatStateOf(260f) }
    var selectedColorInt by remember { mutableIntStateOf(prefs.getInt("theme_color", Color(0xFF6750A4).toArgb())) }
    var networkUpdateEnabled by remember {
        mutableStateOf(PrivacyConsent.isNetworkUpdateEnabled(prefs))
    }
    var showNetworkPrivacyDialog by remember { mutableStateOf(false) }
    var updateChannel by remember { mutableStateOf(prefs.getString("update_channel", "stable") ?: "stable") }
    var notificationType by remember { mutableStateOf(prefs.getString("notification_type", "native") ?: "native") }
    var smsRecognitionEnabled by remember { mutableStateOf(prefs.getBoolean("sms_recognition_enabled", false)) }
    var notificationListenerEnabled by remember { mutableStateOf(prefs.getBoolean("notification_listener_recognition_enabled", false)) }
    var notificationListenerPermissionReady by remember { mutableStateOf(com.Badnng.moe.service.NotificationListenerRecognitionService.isNotificationListenerEnabled(context)) }
    val uiStyle = remember(prefs) { prefs.getString("ui_style", "miuix") ?: "miuix" }
    var useFloatingNavBar by remember { mutableStateOf(prefs.getBoolean("use_floating_nav_bar", false)) }
    var floatingNavBarStyle by remember {
        mutableStateOf(
            MiuixFloatingNavigationBarStyle.fromPreference(
                prefs.getString(MIUIX_FLOATING_NAV_BAR_STYLE_KEY, null)
            )
        )
    }
    var largeScreenNavAdaptive by remember { mutableStateOf(prefs.getBoolean("large_screen_nav_adaptive_enabled", true)) }
    var keyColorIndex by remember { mutableIntStateOf(prefs.getInt("key_color_index", 0)) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            smsRecognitionEnabled = true
            prefs.edit().putBoolean("sms_recognition_enabled", true).apply()
        } else {
            Toast.makeText(context, "需要短信权限才能开启短信识别", Toast.LENGTH_SHORT).show()
        }
    }

    val runSmsRecognitionTest: () -> Unit = {
        val hasSmsPermissions =
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECEIVE_SMS,
            ) == PackageManager.PERMISSION_GRANTED &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_SMS,
                ) == PackageManager.PERMISSION_GRANTED

        if (!smsRecognitionEnabled || !hasSmsPermissions) {
            Toast.makeText(
                context,
                "请先开启短信识别并授予短信权限",
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            val testSms = "【丰巢】凭取件码88306313至XX丰巢柜取您的包裹，超时将收费"
            val intent = android.content.Intent(
                context,
                com.Badnng.moe.service.SmsRecognitionService::class.java,
            ).apply {
                putExtra("smsText", testSms)
                putExtra("sender", "内置测试文本")
                putExtra(com.Badnng.moe.service.SmsRecognitionService.EXTRA_TEST_MODE, true)
            }
            context.startService(intent)
            Toast.makeText(context, "已开始识别内置测试文本", Toast.LENGTH_SHORT).show()
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        PrivacyConsent.discardLegacyNetworkUpdatePreference(prefs)
        while (true) {
            notificationListenerPermissionReady = com.Badnng.moe.service.NotificationListenerRecognitionService.isNotificationListenerEnabled(context)
            kotlinx.coroutines.delay(2000)
        }
    }

    val isMiuix = rememberMiuixStyle()
    val requestNetworkUpdateChange: (Boolean) -> Unit = { enabled ->
        performHaptic()
        when {
            !enabled -> {
                networkUpdateEnabled = false
                PrivacyConsent.setNetworkUpdateEnabled(prefs, false)
            }
            PrivacyConsent.isAccepted(prefs) -> {
                networkUpdateEnabled = true
                PrivacyConsent.setNetworkUpdateEnabled(prefs, true)
            }
            else -> showNetworkPrivacyDialog = true
        }
    }

    // Miuix 颜色模式索引计算
    val colorModeLabels = listOf("跟随系统", "浅色", "深色", "莫奈取色(自动)", "莫奈取色(浅色)", "莫奈取色(深色)")
    val currentColorModeIndex = when {
        !miuixMonetEnabled && themeMode == "system" -> 0
        !miuixMonetEnabled && themeMode == "light" -> 1
        !miuixMonetEnabled && themeMode == "dark" -> 2
        miuixMonetEnabled && themeMode == "system" -> 3
        miuixMonetEnabled && themeMode == "light" -> 4
        miuixMonetEnabled && themeMode == "dark" -> 5
        else -> 0
    }

    var autoGroupEnabled by remember {
        mutableStateOf(prefs.getBoolean("auto_group_enabled", true))
    }

    val bottomBarSection: @Composable () -> Unit = {
        if (isMiuix) {
            SmallTitle(text = "底栏设置")
            MiuixCard(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                SwitchPreference(
                    title = "悬浮底栏",
                    summary = "大屏设备关闭时使用可展开的侧边导航",
                    checked = useFloatingNavBar,
                    onCheckedChange = {
                        performHaptic()
                        useFloatingNavBar = it
                        prefs.edit().putBoolean("use_floating_nav_bar", it).apply()
                    }
                )
                AnimatedVisibility(
                    visible = useFloatingNavBar,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OverlayDropdownPreference(
                        title = "悬浮底栏样式",
                        items = listOf("默认", "iOS-like"),
                        selectedIndex = if (
                            floatingNavBarStyle == MiuixFloatingNavigationBarStyle.IosLike
                        ) 1 else 0,
                        onSelectedIndexChange = { index ->
                            performHaptic()
                            floatingNavBarStyle = if (index == 1) {
                                MiuixFloatingNavigationBarStyle.IosLike
                            } else {
                                MiuixFloatingNavigationBarStyle.Default
                            }
                            prefs.edit()
                                .putString(
                                    MIUIX_FLOATING_NAV_BAR_STYLE_KEY,
                                    floatingNavBarStyle.preferenceValue
                                )
                                .apply()
                        }
                    )
                }
            }
            AnimatedVisibility(
                visible = useFloatingNavBar && (
                    floatingNavBarStyle == MiuixFloatingNavigationBarStyle.Default || isLargeScreen
                ),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    MiuixCard(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        if (isLargeScreen) {
                            SwitchPreference(
                                title = "底栏自适应",
                                summary = "大屏设备下滑切换底栏位置",
                                checked = largeScreenNavAdaptive,
                                onCheckedChange = {
                                    performHaptic()
                                    largeScreenNavAdaptive = it
                                    prefs.edit().putBoolean("large_screen_nav_adaptive_enabled", it).apply()
                                }
                            )
                        }
                        AnimatedVisibility(
                            visible = !isLargeScreen || !largeScreenNavAdaptive,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            OverlayDropdownPreference(
                                title = "底栏位置",
                                items = listOf("靠左", "居中", "靠右"),
                                selectedIndex = when (navAlignment) {
                                    "left" -> 0
                                    "right" -> 2
                                    else -> 1
                                },
                                onSelectedIndexChange = { index ->
                                    performHaptic()
                                    val alignment = listOf("left", "center", "right")[index]
                                    navAlignment = alignment
                                    prefs.edit().putString("nav_alignment", alignment).apply()
                                }
                            )
                        }
                    }
                }
            }
        } else {
            PreferenceSection(title = "底栏位置") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isLargeScreen || isFoldableDevice) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                        ) {
                            PreferenceSwitchItem(
                                title = "底栏自适应",
                                description = "根据主页纵向滑动区域自动切换底栏到左/中/右（仅 大屏/折叠 设备生效）",
                                checked = largeScreenNavAdaptiveEnabled,
                                onCheckedChange = {
                                    largeScreenNavAdaptiveEnabled = it
                                    prefs.edit().putBoolean("large_screen_nav_adaptive_enabled", it).apply()
                                    performHaptic()
                                }
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = !(isLargeScreen || isFoldableDevice) || !largeScreenNavAdaptiveEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("left" to "靠左", "center" to "居中", "right" to "靠右").forEach { (key, label) ->
                                ChoiceChip(
                                    label = label,
                                    selected = navAlignment == key,
                                    onClick = {
                                        performHaptic()
                                        navAlignment = key
                                        prefs.edit().putString("nav_alignment", key).apply()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val interactionSection: @Composable () -> Unit = {
        PreferenceSection(title = "交互设置") {
            SettingsGroup {
                if (hasVibrator) {
                    SettingsGroupSwitchItem(
                        title = "震动反馈",
                        description = "开启后点击按钮、切换分类时会有触感反馈",
                        position = GroupPosition.First,
                        checked = hapticEnabled,
                        onCheckedChange = {
                            hapticEnabled = it
                            prefs.edit().putBoolean("haptic_enabled", it).apply()
                            performHaptic()
                        }
                    )
                }
                SettingsGroupSwitchItem(
                    title = "预测性返回手势",
                    description = "返回时显示跟随手势的缩放与位移动画",
                    position = if (hasVibrator) GroupPosition.Last else GroupPosition.Single,
                    checked = predictiveBackEnabled,
                    onCheckedChange = {
                        performHaptic()
                        predictiveBackEnabled = it
                        prefs.edit().putBoolean("predictive_back_enabled", it).apply()
                    }
                )
            }
        }
    }

    val appearanceSection: @Composable () -> Unit = {
        if (isMiuix) {
            // Miuix 模式：外观设置（颜色模式 + 界面风格）
            SmallTitle(text = "外观设置")
            MiuixCard(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                OverlayDropdownPreference(
                    title = "颜色模式",
                    summary = colorModeLabels[currentColorModeIndex],
                    items = colorModeLabels,
                    selectedIndex = currentColorModeIndex,
                    onSelectedIndexChange = { index ->
                        performHaptic()
                        val (newThemeMode, newMonetEnabled) = when (index) {
                            0 -> "system" to false
                            1 -> "light" to false
                            2 -> "dark" to false
                            3 -> "system" to true
                            4 -> "light" to true
                            5 -> "dark" to true
                            else -> "system" to false
                        }
                        themeMode = newThemeMode
                        miuixMonetEnabled = newMonetEnabled
                        prefs.edit()
                            .putString("theme_mode", newThemeMode)
                            .putBoolean(MIUIX_MONET_ENABLED_KEY, newMonetEnabled)
                            .apply()
                    }
                )
                AnimatedVisibility(visible = currentColorModeIndex in 3..5) {
                    OverlayDropdownPreference(
                        title = "自定义主题色",
                        items = listOf("默认", "蓝色", "紫色", "红色", "橙色", "绿色", "青色"),
                        selectedIndex = keyColorIndex,
                        onSelectedIndexChange = { index ->
                            performHaptic()
                            keyColorIndex = index
                            prefs.edit().putInt("key_color_index", index).apply()
                        }
                    )
                }
                OverlayDropdownPreference(
                    title = "界面风格",
                    items = listOf("Material 3 Expressive", "Miuix UI"),
                    selectedIndex = if (uiStyle == "miuix") 1 else 0,
                    onSelectedIndexChange = { index ->
                        performHaptic()
                        val newStyle = if (index == 1) "miuix" else "md3e"
                        prefs.edit().putString("ui_style", newStyle).apply()
                    }
                )
            }
        } else {
            // MD3E 模式
            PreferenceSection(title = "外观设置") {
                SettingsGroup {
                    SettingsGroupSwitchItem(
                        title = "莫奈取色 (Dynamic Color)",
                        description = "开启后主题色将跟随系统壁纸自动变化",
                        position = GroupPosition.Single,
                        checked = md3eMonetEnabled,
                        onCheckedChange = {
                            performHaptic()
                            md3eMonetEnabled = it
                            prefs.edit().putBoolean(MD3E_MONET_ENABLED_KEY, it).apply()
                        }
                    )
                }
            }
            AnimatedVisibility(visible = !md3eMonetEnabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) { PreferenceSection(title = "自定义主题色") { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Text("滑动调节色相", style = MaterialTheme.typography.bodySmall); Slider(value = customHue, onValueChange = { customHue = it }, valueRange = 0f..360f, modifier = Modifier.fillMaxWidth()); val previewColor = remember(customHue) { Color.hsv(customHue, 0.7f, 0.9f) }; Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(previewColor).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)); Button(onClick = { performHaptic(); selectedColorInt = previewColor.toArgb(); prefs.edit().putInt("theme_color", selectedColorInt).apply() }, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).height(56.dp)) { Text("应用颜色") } } ; Text("MD3 建议色", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)); val md3Presets = Md3Presets; val gap = 2.5f; val cr = 4f; FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) { md3Presets.forEach { preset -> Box(modifier = Modifier.size(36.dp).clip(CircleShape).drawWithContent { val w = size.width; val h = size.height; val midX = w / 2; val midY = h / 2; drawPath(androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(midX + gap, gap, w - gap, h - gap, androidx.compose.ui.geometry.CornerRadius(0f), androidx.compose.ui.geometry.CornerRadius(cr), androidx.compose.ui.geometry.CornerRadius(cr), androidx.compose.ui.geometry.CornerRadius(0f))) }, preset.primary); drawPath(androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(gap, gap, midX - gap * 0.5f, midY - gap * 0.5f, androidx.compose.ui.geometry.CornerRadius(0f), androidx.compose.ui.geometry.CornerRadius(0f), androidx.compose.ui.geometry.CornerRadius(0f), androidx.compose.ui.geometry.CornerRadius(cr))) }, preset.secondary); drawPath(androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(gap, midY + gap * 0.5f, midX - gap * 0.5f, h - gap, androidx.compose.ui.geometry.CornerRadius(0f), androidx.compose.ui.geometry.CornerRadius(cr), androidx.compose.ui.geometry.CornerRadius(0f), androidx.compose.ui.geometry.CornerRadius(0f))) }, preset.tertiary) }.border(width = if (selectedColorInt == preset.seed.toArgb()) 3.dp else 0.dp, color = if (selectedColorInt == preset.seed.toArgb()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape = CircleShape).clickable { performHaptic(); selectedColorInt = preset.seed.toArgb(); prefs.edit().putInt("theme_color", selectedColorInt).apply() }) } } } } }
            PreferenceSection(title = "显示模式") {
                SettingsGroup {
                    val themeOptions = listOf("light" to "浅色", "dark" to "深色", "system" to "跟随系统")
                    themeOptions.forEachIndexed { index, (key, label) ->
                        val pos = when (index) {
                            0 -> GroupPosition.First
                            themeOptions.lastIndex -> GroupPosition.Middle
                            else -> GroupPosition.Middle
                        }
                        SettingsGroupItem(
                            title = label,
                            position = pos,
                            onClick = {
                                performHaptic()
                                themeMode = key
                                prefs.edit().putString("theme_mode", key).apply()
                            },
                            trailing = { RadioButton(selected = themeMode == key, onClick = null) }
                        )
                    }
                    SettingsGroupSwitchItem(
                        title = "Amoled 纯黑深色",
                        description = "仅在深色模式生效，让背景和表面接近纯黑",
                        position = GroupPosition.Last,
                        checked = amoledPureBlack,
                        onCheckedChange = {
                            performHaptic()
                            amoledPureBlack = it
                            prefs.edit().putBoolean("amoled_pure_black", it).apply()
                        }
                    )
                }
            }
            PreferenceSection(title = "界面风格") {
                SettingsGroup {
                    SettingsGroupItem(
                        title = "Material 3 Expressive",
                        description = "Google Material You 设计风格",
                        position = GroupPosition.First,
                        onClick = {
                            performHaptic()
                            prefs.edit().putString("ui_style", "md3e").apply()
                        },
                        trailing = { RadioButton(selected = uiStyle == "md3e", onClick = null) }
                    )
                    SettingsGroupItem(
                        title = "Miuix UI",
                        description = "小米 HyperOS 设计风格",
                        position = GroupPosition.Last,
                        onClick = {
                            performHaptic()
                            prefs.edit().putString("ui_style", "miuix").apply()
                        },
                        trailing = { RadioButton(selected = uiStyle == "miuix", onClick = null) }
                    )
                }
            }
        }
    }

    val featureSection: @Composable () -> Unit = {
        if (!isMiuix) {
            PreferenceSection(title = "订单管理") {
                SettingsGroup {
                    SettingsGroupSwitchItem(
                        title = "快递自动合并",
                        description = "自动将同一天的快递订单合并为一个组",
                        position = GroupPosition.Single,
                        checked = autoGroupEnabled,
                        onCheckedChange = {
                            performHaptic()
                            autoGroupEnabled = it
                            prefs.edit().putBoolean("auto_group_enabled", it).apply()
                        }
                    )
                }
            }
        }

        if (isMiuix) {
            SmallTitle(text = "功能设置")
            MiuixCard(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                SwitchPreference(
                    title = "快递自动合并",
                    summary = "自动将同一天的快递订单合并为一个组",
                    checked = autoGroupEnabled,
                    onCheckedChange = {
                        performHaptic()
                        autoGroupEnabled = it
                        prefs.edit().putBoolean("auto_group_enabled", it).apply()
                    }
                )
                SwitchPreference(
                    title = "短信识别取件码",
                    summary = "自动识别收到的短信中的快递取件码和取餐码",
                    checked = smsRecognitionEnabled,
                    onCheckedChange = { newValue ->
                        performHaptic()
                        if (newValue) {
                            smsPermissionLauncher.launch(arrayOf(
                                android.Manifest.permission.RECEIVE_SMS,
                                android.Manifest.permission.READ_SMS
                            ))
                        } else {
                            smsRecognitionEnabled = false
                            prefs.edit().putBoolean("sms_recognition_enabled", false).apply()
                        }
                    }
                )
                ArrowPreference(
                    title = "测试短信识别",
                    summary = "使用内置示例文本验证识别流程",
                    onClick = {
                        performHaptic()
                        runSmsRecognitionTest()
                    }
                )
            }
            MiuixCard(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                SwitchPreference(
                    title = "通知识别取件码",
                    summary = "自动识别其他应用通知中的取件码和取餐码",
                    checked = notificationListenerEnabled,
                    onCheckedChange = { newValue ->
                        performHaptic()
                        if (newValue && !notificationListenerPermissionReady) {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            context.startActivity(intent)
                            Toast.makeText(context, "请在系统设置中启用澎湃记的通知监听", Toast.LENGTH_LONG).show()
                        } else {
                            notificationListenerEnabled = newValue
                            prefs.edit().putBoolean("notification_listener_recognition_enabled", newValue).apply()
                        }
                    }
                )
                AnimatedVisibility(
                    visible = notificationListenerEnabled && notificationListenerPermissionReady,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        ArrowPreference(
                            title = "管理应用",
                            onClick = {
                                performHaptic()
                                onNavigate(SettingsPage.NotificationApps)
                            }
                        )
                        ArrowPreference(
                            title = "测试通知识别",
                            onClick = {
                                performHaptic()
                                val testText = "【美团外卖】您的餐已准备好，取餐码 A1234，请到店取餐"
                                com.Badnng.moe.service.NotificationListenerRecognitionService.testNotificationRecognition(context, testText)
                                Toast.makeText(context, "已发送测试通知识别", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        } else {
            PreferenceSection(title = "短信识别") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsGroup {
                        SettingsGroupSwitchItem(
                            title = "短信识别取件码",
                            description = "自动识别收到的短信中的快递取件码和取餐码",
                            position = GroupPosition.First,
                            checked = smsRecognitionEnabled,
                            onCheckedChange = { newValue ->
                                performHaptic()
                                if (newValue) {
                                    smsPermissionLauncher.launch(arrayOf(
                                        android.Manifest.permission.RECEIVE_SMS,
                                        android.Manifest.permission.READ_SMS
                                    ))
                                } else {
                                    smsRecognitionEnabled = false
                                    prefs.edit().putBoolean("sms_recognition_enabled", false).apply()
                                }
                            }
                        )
                        SettingsGroupItem(
                            title = "测试短信识别",
                            description = "使用内置示例文本验证识别流程",
                            position = GroupPosition.Last,
                            onClick = {
                                performHaptic()
                                runSmsRecognitionTest()
                            }
                        )
                    }
                }
            }

            PreferenceSection(title = "通知识别") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsGroup {
                        SettingsGroupSwitchItem(
                            title = "通知识别取件码",
                            description = "自动识别其他应用（如外卖、快递App）通知中的取件码和取餐码",
                            position = GroupPosition.Single,
                            checked = notificationListenerEnabled,
                            onCheckedChange = { newValue ->
                                performHaptic()
                                if (newValue && !notificationListenerPermissionReady) {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    context.startActivity(intent)
                                    Toast.makeText(context, "请在系统设置中启用澎湃记的通知监听", Toast.LENGTH_LONG).show()
                                } else {
                                    notificationListenerEnabled = newValue
                                    prefs.edit().putBoolean("notification_listener_recognition_enabled", newValue).apply()
                                }
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = notificationListenerEnabled && notificationListenerPermissionReady,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsGroup {
                                SettingsGroupItem(
                                    title = "管理应用",
                                    position = GroupPosition.First,
                                    onClick = {
                                        performHaptic()
                                        onNavigate(SettingsPage.NotificationApps)
                                    }
                                )
                                SettingsGroupItem(
                                    title = "测试通知识别",
                                    position = GroupPosition.Last,
                                    onClick = {
                                        performHaptic()
                                        val testText = "【美团外卖】您的餐已准备好，取餐码 A1234，请到店取餐"
                                        com.Badnng.moe.service.NotificationListenerRecognitionService.testNotificationRecognition(context, testText)
                                        Toast.makeText(context, "已发送测试通知识别", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val onboardingSection: @Composable () -> Unit = {
        PreferenceSection(title = "引导设置") {
            SettingsGroup {
                SettingsGroupSwitchItem(
                    title = "下次启动时打开引导页面",
                    description = "开启后，彻底停止App再启动会显示引导页面，完成引导后自动关闭",
                    position = GroupPosition.Single,
                    checked = showOnboardingOnNextLaunch,
                    onCheckedChange = {
                        performHaptic()
                        showOnboardingOnNextLaunch = it
                        prefs.edit().putBoolean("show_onboarding_on_next_launch", it).apply()
                    }
                )
            }
        }
    }

    val notificationTypeSection: @Composable () -> Unit = {
        if (isMiuix) {
            val isIslandSupported = com.Badnng.moe.helper.SuperIslandHelper.isDeviceSupported(context)
            SmallTitle(text = "通知类型")
            MiuixCard(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                OverlayDropdownPreference(
                    title = "通知类型",
                    entries = listOf(
                        top.yukonga.miuix.kmp.basic.DropdownEntry(
                            items = listOf(
                                top.yukonga.miuix.kmp.basic.DropdownItem(
                                    text = "安卓原生通知",
                                    selected = notificationType == "native",
                                    onClick = {
                                        performHaptic()
                                        notificationType = "native"
                                        prefs.edit().putString("notification_type", "native").apply()
                                    }
                                ),
                                top.yukonga.miuix.kmp.basic.DropdownItem(
                                    text = "小米超级岛",
                                    selected = notificationType == "island",
                                    enabled = isIslandSupported,
                                    onClick = {
                                        performHaptic()
                                        notificationType = "island"
                                        prefs.edit().putString("notification_type", "island").apply()
                                    }
                                )
                            )
                        )
                    )
                )
            }
            AnimatedVisibility(
                visible = notificationType == "island",
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    top.yukonga.miuix.kmp.basic.Button(
                        onClick = {
                            performHaptic()
                            SuperIslandHelper.sendTestNotification(context)
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        MiuixIcon(
                            imageVector = MiuixIcons.Regular.Promotions,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        MiuixText(
                            text = "测试超级岛通知",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    MiuixCard(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MiuixTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MiuixText(
                                text = "⚠",
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            MiuixText(
                                text = "小米超级岛功能仅接入，无白名单。如被非法滥用，与此应用无关，开发者不承担任何责任，也不会提供绕过方法。",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.error,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        } else {
            PreferenceSection(title = "通知类型") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaptureModeItem(
                        title = "安卓原生通知",
                        description = "使用系统原生通知样式（推荐）",
                        selected = notificationType == "native",
                        onClick = {
                            performHaptic()
                            notificationType = "native"
                            prefs.edit().putString("notification_type", "native").apply()
                        }
                    )
                    CaptureModeItem(
                        title = "小米超级岛",
                        description = "在 HyperOS 设备上使用超级岛样式（需要设备支持）",
                        selected = notificationType == "island",
                        enabled = SuperIslandHelper.isDeviceSupported(context),
                        onClick = {
                            performHaptic()
                            notificationType = "island"
                            prefs.edit().putString("notification_type", "island").apply()
                        }
                    )
                }
            }

            // 超级岛测试区域（MD3E 模式）
            AnimatedVisibility(
                visible = notificationType == "island",
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = {
                            performHaptic()
                            SuperIslandHelper.sendTestNotification(context)
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "测试超级岛通知",
                            modifier = Modifier.padding(16.dp),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "⚠ 小米超级岛功能仅接入，无白名单。如被非法滥用，与此应用无关，开发者不承担任何责任，也不会提供绕过方法。",
                            modifier = Modifier.padding(12.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }

    val networkUpdateSection: @Composable () -> Unit = {
        if (isMiuix) {
            SmallTitle(text = "联网更新")
            MiuixCard(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                SwitchPreference(
                    title = "联网更新",
                    summary = "仅用于检测App新版本并下载，不用于其他用途",
                    checked = networkUpdateEnabled,
                    onCheckedChange = requestNetworkUpdateChange,
                )
                AnimatedVisibility(
                        visible = networkUpdateEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        OverlayDropdownPreference(
                            title = "更新通道",
                            items = listOf("正式版", "测试版"),
                            selectedIndex = if (updateChannel == "dev") 1 else 0,
                            onSelectedIndexChange = { idx ->
                                performHaptic()
                                val v = if (idx == 1) "dev" else "stable"
                                updateChannel = v
                                prefs.edit().putString("update_channel", v).apply()
                            }
                        )
                    }
            }
        } else {
            PreferenceSection(title = "联网更新") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsGroup {
                        SettingsGroupSwitchItem(
                            title = "联网更新",
                            description = "仅用于检测App新版本并下载，不用于其他用途",
                            position = GroupPosition.Single,
                            checked = networkUpdateEnabled,
                            onCheckedChange = requestNetworkUpdateChange,
                        )
                    }

                    AnimatedVisibility(
                        visible = networkUpdateEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "更新通道",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            CaptureModeItem(
                                title = "接收正式版更新",
                                description = "只接收稳定版本的更新",
                                selected = updateChannel == "stable",
                                onClick = {
                                    performHaptic()
                                    updateChannel = "stable"
                                    prefs.edit().putString("update_channel", "stable").apply()
                                }
                            )
                            CaptureModeItem(
                                title = "接收测试版更新",
                                description = "接收所有版本的更新，包括测试版",
                                selected = updateChannel == "dev",
                                onClick = {
                                    performHaptic()
                                    updateChannel = "dev"
                                    prefs.edit().putString("update_channel", "dev").apply()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    val sections = listOf(
        bottomBarSection,
        interactionSection,
        appearanceSection,
        featureSection,
        onboardingSection,
        notificationTypeSection,
        networkUpdateSection,
    )
    if (isMiuix) {
        MiuixSettingsLazyColumn(
            sections = sections,
            contentPadding = PaddingValues(
                top = topPadding,
                bottom = 48.dp +
                    WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding(),
            ),
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                ),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Spacer(Modifier.height(topPadding))
            sections.forEach { it() }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
    PrivacyConsentBottomSheet(
        show = showNetworkPrivacyDialog,
        isMiuix = isMiuix,
        title = "启用联网更新",
        onDismiss = {
            performHaptic()
            showNetworkPrivacyDialog = false
        },
        onConfirm = {
            performHaptic()
            PrivacyConsent.accept(prefs)
            PrivacyConsent.setNetworkUpdateEnabled(prefs, true)
            networkUpdateEnabled = true
            showNetworkPrivacyDialog = false
        },
    )
}
