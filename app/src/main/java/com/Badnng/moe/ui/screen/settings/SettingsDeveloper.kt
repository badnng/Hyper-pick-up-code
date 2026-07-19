package com.Badnng.moe.ui.screen.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.Badnng.moe.ui.component.GroupPosition
import com.Badnng.moe.ui.component.PreferenceSection
import com.Badnng.moe.ui.component.SettingsGroup
import com.Badnng.moe.ui.component.SettingsGroupItem
import com.Badnng.moe.ui.component.SettingsGroupSwitchItem
import com.Badnng.moe.ui.miuix.MIUIX_FORCE_LOW_END_DEVICE_STANDARD_KEY
import com.Badnng.moe.ui.miuix.MiuixSettingsLazyColumn
import com.Badnng.moe.ui.miuix.rememberMiuixStyle

@Composable
fun DeveloperSettingsContent(
    performHaptic: () -> Unit,
    topPadding: Dp = 0.dp,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }
    var forceLowEndDeviceStandard by remember {
        mutableStateOf(
            prefs.getBoolean(MIUIX_FORCE_LOW_END_DEVICE_STANDARD_KEY, false),
        )
    }

    val deviceSection: @Composable () -> Unit = {
        PreferenceSection(title = "设备兼容") {
            SettingsGroup {
                SettingsGroupSwitchItem(
                    title = "使用低级设备标准",
                    description = "强制关闭 Miuix 高开销动态效果与模糊；关闭后恢复自动判断",
                    position = GroupPosition.Single,
                    checked = forceLowEndDeviceStandard,
                    onCheckedChange = { enabled ->
                        performHaptic()
                        forceLowEndDeviceStandard = enabled
                        prefs.edit()
                            .putBoolean(MIUIX_FORCE_LOW_END_DEVICE_STANDARD_KEY, enabled)
                            .apply()
                    },
                )
            }
        }
    }
    val diagnosticsSection: @Composable () -> Unit = {
        PreferenceSection(title = "调试") {
            SettingsGroup {
                SettingsGroupItem(
                    title = "点击自动崩溃",
                    description = "立即触发测试崩溃，用于检查异常日志",
                    position = GroupPosition.Single,
                    onClick = {
                        performHaptic()
                        throw RuntimeException("Test crash triggered from developer options")
                    },
                )
            }
        }
    }
    val sections = listOf(deviceSection, diagnosticsSection)

    if (rememberMiuixStyle()) {
        MiuixSettingsLazyColumn(
            sections = sections,
            contentPadding = PaddingValues(top = topPadding, bottom = 32.dp),
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Spacer(modifier = Modifier.height(topPadding))
            sections.forEach { it() }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
