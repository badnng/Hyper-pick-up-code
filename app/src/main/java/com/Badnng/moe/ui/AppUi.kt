package com.Badnng.moe.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.Badnng.moe.ui.component.GroupPosition

/**
 * UI 兼容层接口。所有新功能必须通过 LocalAppUi.current 调用组件，
 * 确保 MD3E / Miuix 双 UI 同步显示。
 */
data class AppUi(
    // 设置分组
    val settingsGroup: @Composable (modifier: Modifier, content: @Composable ColumnScope.() -> Unit) -> Unit,
    val settingsGroupItem: @Composable (title: String, description: String?, position: GroupPosition, onClick: () -> Unit, trailing: @Composable (() -> Unit)?) -> Unit,
    val settingsGroupSwitchItem: @Composable (title: String, description: String?, position: GroupPosition, checked: Boolean, onCheckedChange: (Boolean) -> Unit) -> Unit,
    // 设置列表
    val settingsListItem: @Composable (title: String, description: String?, onClick: () -> Unit) -> Unit,
    // 分区标题
    val preferenceSection: @Composable (title: String, content: @Composable () -> Unit) -> Unit,
    // 权限项
    val permissionItem: @Composable (title: String, description: String, isGranted: Boolean, actionButton: @Composable (() -> Unit)?) -> Unit,
    // 截图模式选择
    val captureModeItem: @Composable (title: String, description: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) -> Unit,
    // 选择芯片
    val choiceChip: @Composable (label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) -> Unit,
    // 独立开关项
    val preferenceSwitchItem: @Composable (title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) -> Unit,
    // 通知识别应用页顶栏菜单
    val notificationAppsTopBarAction: @Composable (showSystemApps: Boolean, onShowSystemAppsChange: (Boolean) -> Unit, performHaptic: () -> Unit) -> Unit,
)

val LocalAppUi = staticCompositionLocalOf<AppUi> { error("No AppUi provided") }
