package com.Badnng.moe.ui.miuix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.Badnng.moe.ui.component.GroupPosition
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ─── 设置分组容器 ───
// 参考 Miuix 示例应用：Card(modifier = Modifier.padding(12.dp))

@Composable
fun MiuixSettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.padding(horizontal = 12.dp),
        content = content
    )
}

// ─── 设置分组项（可点击） ───
// 无 trailing 时用 ArrowPreference（带箭头），有 trailing 时用 BasicComponent

@Composable
fun MiuixSettingsGroupItem(
    title: String,
    description: String?,
    position: GroupPosition,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)?
) {
    if (trailing != null) {
        BasicComponent(
            title = title,
            summary = description,
            onClick = onClick,
            endActions = { trailing() }
        )
    } else {
        ArrowPreference(
            title = title,
            summary = description,
            onClick = onClick
        )
    }
}

// ─── 设置分组开关项 ───
// 直接使用 SwitchPreference，不需要额外包装

@Composable
fun MiuixSettingsGroupSwitchItem(
    title: String,
    description: String?,
    position: GroupPosition,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SwitchPreference(
        title = title,
        summary = description,
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

// ─── 设置列表项 ───

@Composable
fun MiuixSettingsListItem(
    title: String,
    description: String?,
    onClick: () -> Unit
) {
    ArrowPreference(
        title = title,
        summary = description,
        onClick = onClick
    )
}

// ─── 分区标题 ───
// 直接使用 SmallTitle，默认样式即可

@Composable
fun MiuixPreferenceSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        SmallTitle(text = title)
        content()
    }
}

// ─── 权限项 ───

@Composable
fun MiuixPermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    actionButton: @Composable (() -> Unit)?
) {
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.headline1,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (isGranted) MiuixIcons.Regular.Ok else MiuixIcons.Regular.Close,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFF44336),
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = description,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            if (!isGranted) {
                Spacer(Modifier.height(4.dp))
                actionButton?.invoke()
            }
        }
    }
}

// ─── 截图模式选择项 ───

@Composable
fun MiuixCaptureModeItem(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    RadioButtonPreference(
        title = title,
        summary = description,
        selected = selected,
        onClick = { if (enabled) onClick() },
        radioButtonLocation = RadioButtonLocation.End,
        enabled = enabled,
        modifier = if (!enabled) Modifier.alpha(0.5f) else Modifier
    )
}

// ─── 选择芯片 ───

@Composable
fun MiuixChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(15.dp),
        color = if (selected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 14.dp),
            style = MiuixTheme.textStyles.button,
            color = if (selected) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

// ─── 独立开关项 ───

@Composable
fun MiuixPreferenceSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SwitchPreference(
        title = title,
        summary = description,
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}
