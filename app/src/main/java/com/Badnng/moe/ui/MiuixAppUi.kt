package com.Badnng.moe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.Badnng.moe.ui.miuix.MiuixCaptureModeItem
import com.Badnng.moe.ui.miuix.MiuixChoiceChip
import com.Badnng.moe.ui.miuix.MiuixPermissionItem
import com.Badnng.moe.ui.miuix.MiuixPreferenceSection
import com.Badnng.moe.ui.miuix.MiuixPreferenceSwitchItem
import com.Badnng.moe.ui.miuix.MiuixSettingsGroup
import com.Badnng.moe.ui.miuix.MiuixSettingsGroupItem
import com.Badnng.moe.ui.miuix.MiuixSettingsGroupSwitchItem
import com.Badnng.moe.ui.miuix.MiuixSettingsListItem
import com.Badnng.moe.ui.component.MiuixBlockedWordsEditor
import com.Badnng.moe.ui.component.MiuixPromptEditor
import com.Badnng.moe.ui.component.MiuixOrderDetailContent
import com.Badnng.moe.ui.screen.settings.MiuixMessageBlock
import com.Badnng.moe.ui.screen.settings.MiuixPrimaryActionButton
import com.Badnng.moe.ui.screen.settings.MiuixSecurePasswordDialog
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Close
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
    promptEditor = { lineCount, onRestoreDefault, modifier, editor ->
        MiuixPromptEditor(lineCount, onRestoreDefault, modifier, editor)
    },
    blockedWordsEditor = { state, performHaptic ->
        MiuixBlockedWordsEditor(state, performHaptic)
    },
    orderDetailContent = { state, actions, modifier ->
        MiuixOrderDetailContent(state, actions, modifier)
    },
    fullScreenImageCloseButton = { onClick ->
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.52f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            top.yukonga.miuix.kmp.basic.IconButton(
                onClick = onClick,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    MiuixIcons.Regular.Close,
                    contentDescription = "关闭大图",
                    tint = Color.White,
                )
            }
        }
    },
    primaryActionButton = { text, enabled, onClick ->
        MiuixPrimaryActionButton(text, enabled, onClick)
    },
    messageBlock = { text, isError ->
        MiuixMessageBlock(text, isError)
    },
    securePasswordDialog = { requireConfirmation, performHaptic, onDismiss, onConfirm ->
        MiuixSecurePasswordDialog(requireConfirmation, performHaptic, onDismiss, onConfirm)
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
