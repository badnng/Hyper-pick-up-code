package com.Badnng.moe.ui

import androidx.compose.runtime.Composable
import com.Badnng.moe.ui.miuix.MiuixCaptureModeItem
import com.Badnng.moe.ui.miuix.MiuixChoiceChip
import com.Badnng.moe.ui.miuix.MiuixPermissionItem
import com.Badnng.moe.ui.miuix.MiuixPreferenceSection
import com.Badnng.moe.ui.miuix.MiuixPreferenceSwitchItem
import com.Badnng.moe.ui.miuix.MiuixSettingsGroup
import com.Badnng.moe.ui.miuix.MiuixSettingsGroupItem
import com.Badnng.moe.ui.miuix.MiuixSettingsGroupSwitchItem
import com.Badnng.moe.ui.miuix.MiuixSettingsListItem
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu

val miuixAppUi = AppUi(
    settingsGroup = { modifier, content ->
        MiuixSettingsGroup(modifier, content)
    },
    settingsGroupItem = { title, description, position, onClick, trailing ->
        MiuixSettingsGroupItem(title, description, position, onClick, trailing)
    },
    settingsGroupSwitchItem = { title, description, position, checked, onCheckedChange ->
        MiuixSettingsGroupSwitchItem(title, description, position, checked, onCheckedChange)
    },
    settingsListItem = { title, description, onClick ->
        MiuixSettingsListItem(title, description, onClick)
    },
    preferenceSection = { title, content ->
        MiuixPreferenceSection(title, content)
    },
    permissionItem = { title, description, isGranted, actionButton ->
        MiuixPermissionItem(title, description, isGranted, actionButton)
    },
    captureModeItem = { title, description, selected, enabled, onClick ->
        MiuixCaptureModeItem(title, description, selected, enabled, onClick)
    },
    choiceChip = { label, selected, onClick, modifier ->
        MiuixChoiceChip(label, selected, onClick, modifier)
    },
    preferenceSwitchItem = { title, description, checked, onCheckedChange ->
        MiuixPreferenceSwitchItem(title, description, checked, onCheckedChange)
    },
    notificationAppsTopBarAction = { showSystemApps, onShowSystemAppsChange, performHaptic ->
        MiuixNotificationAppsTopBarAction(showSystemApps, onShowSystemAppsChange, performHaptic)
    },
)

@Composable
private fun MiuixNotificationAppsTopBarAction(
    showSystemApps: Boolean,
    onShowSystemAppsChange: (Boolean) -> Unit,
    performHaptic: () -> Unit
) {
    OverlayIconDropdownMenu(
        entry = DropdownEntry(
            items = listOf(
                DropdownItem(
                    text = "显示系统应用",
                    selected = showSystemApps,
                    onClick = {
                        performHaptic()
                        onShowSystemAppsChange(!showSystemApps)
                    }
                )
            )
        ),
        collapseOnSelection = true,
        onExpandedChange = { expanded ->
            if (expanded) performHaptic()
        }
    ) {
        Icon(
            imageVector = MiuixIcons.Regular.More,
            contentDescription = "更多选项"
        )
    }
}
