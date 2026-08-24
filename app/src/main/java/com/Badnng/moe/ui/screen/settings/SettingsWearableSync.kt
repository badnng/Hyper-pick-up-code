package com.Badnng.moe.ui.screen.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Badnng.moe.ui.LocalAppUi
import com.Badnng.moe.ui.component.GroupPosition
import com.Badnng.moe.ui.component.PreferenceSection
import com.Badnng.moe.ui.component.SettingsGroup
import com.Badnng.moe.ui.component.SettingsGroupItem
import com.Badnng.moe.ui.component.SettingsGroupSwitchItem
import com.Badnng.moe.ui.miuix.MiuixSettingsLazyColumn
import com.Badnng.moe.ui.miuix.rememberMiuixStyle
import com.Badnng.moe.wearable.WearableSyncManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「手表同步」设置页（Plan §5.5）。
 *
 * 展示：同步开关 / 连接状态 / 设备信息 / 授权与重试 / 最近同步时间。
 * 全部通过 LocalAppUi 抽象组件（SettingsGroup 系列 / messageBlock）实现，MD3E/Miuix 自动兼容。
 * 开关与连接状态区分：管理器的 [WearableSyncManager.State] 才是真实状态，开关仅表示用户偏好。
 *
 * ⚠️ 开关开启时进入页面会自动触发一次 [WearableSyncManager.refreshNode] 校准真实连接状态
 * （开关关闭时不触发，避免无谓的连接/发现副作用）；用户点击「重新查找手表设备」等显式操作仍可随时重查。
 */
@Composable
fun WearableSyncSettingsContent(
    performHaptic: () -> Unit,
    topPadding: Dp = 0.dp,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState()
) {
    val context = LocalContext.current
    val appUi = LocalAppUi.current
    val manager = rememberManager(context)
    val enabled by manager.enabled.collectAsStateWithLifecycle()
    val state by manager.state.collectAsStateWithLifecycle()

    // 开关开启时进入页面自动校准一次真实连接状态（消除陈旧的「未连接」显示）
    LaunchedEffect(Unit) {
        if (enabled) manager.refreshNode()
    }

    val isMiuix = rememberMiuixStyle()
    val sections = buildList<@Composable () -> Unit> {
        add {
            PreferenceSection(title = "手表同步") {
                SettingsGroupSwitchItem(
                    title = "同步到小米手表",
                    description = if (enabled) "已开启，未完成取餐码将推送到手表" else "开启后将未完成的取餐码同步到小米手表",
                    position = GroupPosition.Single,
                    checked = enabled,
                    onCheckedChange = { v ->
                        performHaptic()
                        manager.setEnabled(v)
                    }
                )
            }
        }

        add {
            PreferenceSection(title = "连接状态") {
                if (!state.sdkIntegrated) {
                    SettingsGroup {
                        SettingsGroupItem(
                            title = "Mi Fitness 服务未连接",
                            description = "Mi Fitness 服务未安装或无法连接，请安装/启动后重启应用再开启同步",
                            position = GroupPosition.Single,
                            onClick = { performHaptic() }
                        )
                    }
                } else {
                    SettingsGroup {
                        SettingsGroupItem(
                            title = "连接状态",
                            description = connectionText(state, enabled),
                            position = GroupPosition.First,
                            onClick = { performHaptic(); manager.refreshNode() }
                        )
                        SettingsGroupItem(
                            title = "设备",
                            description = state.deviceName ?: state.nodeId ?: "未发现已连接的手表",
                            position = GroupPosition.Middle,
                            onClick = { performHaptic(); manager.refreshNode() }
                        )
                        SettingsGroupItem(
                            title = "设备权限",
                            description = if (state.permissionGranted) "已授权" else "未授权，开启同步前需授权",
                            position = GroupPosition.Last,
                            onClick = {
                                performHaptic()
                                manager.requestPermission()
                            }
                        )
                    }
                }
            }
        }

        if (state.sdkIntegrated) {
            add {
                PreferenceSection(title = "待同步") {
                    SettingsGroup {
                        SettingsGroupItem(
                            title = "未完成取餐码",
                            description = "${state.pendingOrdersCount} 条待同步",
                            position = GroupPosition.Single,
                            onClick = { performHaptic() }
                        )
                    }
                }
            }
        }

        add {
            PreferenceSection(title = "操作") {
                SettingsGroup {
                    SettingsGroupItem(
                        title = "重新查找手表设备",
                        description = "断线后重新发现连接的设备（一次仅支持一个设备）",
                        position = GroupPosition.Single,
                        onClick = {
                            performHaptic()
                            manager.refreshNode()
                        }
                    )
                }
            }
        }

        if (state.lastSyncAt > 0L) {
            add {
                PreferenceSection(title = "最近同步") {
                    SettingsGroup {
                        SettingsGroupItem(
                            title = "全量快照版本",
                            description = "#${state.lastSnapshotVersion}",
                            position = GroupPosition.First,
                            onClick = { performHaptic() }
                        )
                        SettingsGroupItem(
                            title = "同步时间",
                            description = formatTime(state.lastSyncAt),
                            position = GroupPosition.Middle,
                            onClick = { performHaptic() }
                        )
                        SettingsGroupItem(
                            title = "最近回执",
                            description = lastAckText(state),
                            position = GroupPosition.Last,
                            onClick = { performHaptic() }
                        )
                    }
                }
            }
        }

        if (state.lastError != null) {
            add {
                // 遵循 LocalAppUi 双 UI 抽象，不直接使用 Material3 的 Text/Color
                appUi.messageBlock(state.lastError ?: "", true)
            }
        }
    }

    if (isMiuix) {
        MiuixSettingsLazyColumn(
            sections = sections,
            contentPadding = PaddingValues(
                top = topPadding,
                bottom = 32.dp +
                    WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding(),
            ),
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(topPadding))
            sections.forEach { it() }
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun rememberManager(context: android.content.Context): WearableSyncManager =
    androidx.compose.runtime.remember(context) { WearableSyncManager.getInstance(context) }

private fun connectionText(state: WearableSyncManager.State, enabled: Boolean): String {
    if (!enabled) return "已关闭，未同步"
    if (!state.sdkIntegrated) return "Mi Fitness 服务未安装/无法连接"
    if (state.connected) return "已连接"
    if (state.discovering) return "正在查找设备…"
    return "未连接"
}

private fun formatTime(ts: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts))

private fun lastAckText(state: WearableSyncManager.State): String {
    val ackId = state.lastAckOrderId
    return if (!ackId.isNullOrBlank()) {
        "订单 ${ackId.take(8)} · ${formatTime(state.lastAckAt)}"
    } else {
        "暂无"
    }
}
