package com.Badnng.moe.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.Badnng.moe.ui.component.GroupPosition
import com.Badnng.moe.ui.component.Md3eCaptureModeItem
import com.Badnng.moe.ui.component.Md3eChoiceChip
import com.Badnng.moe.ui.component.Md3ePermissionItem
import com.Badnng.moe.ui.component.Md3ePreferenceSection
import com.Badnng.moe.ui.component.Md3ePreferenceSwitchItem
import com.Badnng.moe.ui.component.Md3eSettingsGroup
import com.Badnng.moe.ui.component.Md3eSettingsGroupItem
import com.Badnng.moe.ui.component.Md3eSettingsGroupSwitchItem
import com.Badnng.moe.ui.component.Md3eSettingsListItem
import com.Badnng.moe.ui.component.Md3eBlockedWordsEditor
import com.Badnng.moe.ui.component.Md3ePromptEditor
import com.Badnng.moe.ui.component.Md3eOrderDetailContent
import com.Badnng.moe.ui.screen.settings.Md3eMessageBlock
import com.Badnng.moe.ui.screen.settings.Md3ePrimaryActionButton
import com.Badnng.moe.ui.screen.settings.Md3eSecurePasswordDialog

val md3eAppUi = AppUi(
    settingsGroup = { modifier, content -> Md3eSettingsGroup(modifier, content) },
    settingsGroupItem = { title, description, position, onClick, trailing ->
        Md3eSettingsGroupItem(title, description, position, onClick, trailing)
    },
    settingsGroupSwitchItem = { title, description, position, checked, onCheckedChange ->
        Md3eSettingsGroupSwitchItem(title, description, position, checked, onCheckedChange)
    },
    settingsListItem = { title, description, onClick ->
        Md3eSettingsListItem(title, description, onClick)
    },
    preferenceSection = { title, content ->
        Md3ePreferenceSection(title, content)
    },
    permissionItem = { title, description, isGranted, actionButton ->
        Md3ePermissionItem(title, description, isGranted, actionButton)
    },
    captureModeItem = { title, description, selected, enabled, onClick ->
        Md3eCaptureModeItem(title, description, selected, enabled, onClick)
    },
    choiceChip = { label, selected, onClick, modifier ->
        Md3eChoiceChip(label, selected, onClick, modifier)
    },
    preferenceSwitchItem = { title, description, checked, onCheckedChange ->
        Md3ePreferenceSwitchItem(title, description, checked, onCheckedChange)
    },
    notificationAppsTopBarAction = { showSystemApps, onShowSystemAppsChange, performHaptic ->
        Md3eNotificationAppsTopBarAction(showSystemApps, onShowSystemAppsChange, performHaptic)
    },
    promptEditor = { lineCount, onRestoreDefault, modifier, editor ->
        Md3ePromptEditor(lineCount, onRestoreDefault, modifier, editor)
    },
    blockedWordsEditor = { state, performHaptic ->
        Md3eBlockedWordsEditor(state, performHaptic)
    },
    orderDetailContent = { state, actions, modifier ->
        Md3eOrderDetailContent(state, actions, modifier)
    },
    fullScreenImageCloseButton = { onClick ->
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        ) {
            IconButton(onClick = onClick) {
                Icon(Icons.Default.Close, contentDescription = "关闭大图")
            }
        }
    },
    primaryActionButton = { text, enabled, onClick ->
        Md3ePrimaryActionButton(text, enabled, onClick)
    },
    messageBlock = { text, isError ->
        Md3eMessageBlock(text, isError)
    },
    securePasswordDialog = { requireConfirmation, performHaptic, onDismiss, onConfirm ->
        Md3eSecurePasswordDialog(requireConfirmation, performHaptic, onDismiss, onConfirm)
    },
)

@Composable
private fun Md3eNotificationAppsTopBarAction(
    showSystemApps: Boolean,
    onShowSystemAppsChange: (Boolean) -> Unit,
    performHaptic: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = {
                performHaptic()
                expanded = true
            }
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "更多选项"
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("显示系统应用") },
                leadingIcon = {
                    Checkbox(
                        checked = showSystemApps,
                        onCheckedChange = null
                    )
                },
                onClick = {
                    performHaptic()
                    onShowSystemAppsChange(!showSystemApps)
                    expanded = false
                }
            )
        }
    }
}
