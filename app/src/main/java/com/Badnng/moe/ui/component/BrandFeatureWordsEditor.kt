@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.Badnng.moe.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.Badnng.moe.rules.BrandFeatureWord
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication

/** 由 AppUi 调用，与自定义取件地点保持相同的加号输入、标签删除交互。 */
@Composable
fun BrandFeatureWordsEditor(
    words: List<BrandFeatureWord>,
    input: String,
    enabled: Boolean,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    isMiuix: Boolean,
) {
    val add = { if (enabled) onAdd() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (words.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                words.forEach { word ->
                    val label = word.word
                    if (isMiuix) {
                        val interactions = remember { MutableInteractionSource() }
                        val indicationColor = MiuixTheme.colorScheme.onSurface
                        val indication = remember(indicationColor) { MiuixIndication(color = indicationColor) }
                        Row(
                            Modifier.squircleSurface(MiuixTheme.colorScheme.surfaceContainer, 15.dp)
                                .clickable(interactions, indication, enabled = enabled, role = Role.Button) { onRemove(word.word) }
                                .semantics { contentDescription = "删除特征词 $label" }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MiuixText(label, modifier = Modifier.widthIn(max = 240.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.width(6.dp))
                            MiuixIcon(MiuixIcons.Regular.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        InputChip(
                            selected = false,
                            enabled = enabled,
                            onClick = { onRemove(word.word) },
                            label = { Text(label, Modifier.widthIn(max = 240.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = { Icon(Icons.Default.Close, "删除特征词 ${word.word}", Modifier.size(16.dp)) },
                            shape = RoundedCornerShape(15.dp),
                        )
                    }
                }
            }
        }
        val inputModifier = Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
            if (enabled && event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                add()
                true
            } else false
        }
        if (isMiuix) {
            MiuixTextField(
                value = input, onValueChange = { if (enabled) onInputChange(it.replace("\n", "")) },
                label = "添加特征词", modifier = inputModifier, singleLine = true,
                trailingIcon = { MiuixIconButton(onClick = add, enabled = enabled) { MiuixIcon(MiuixIcons.Regular.Add, "添加特征词") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
            )
            MiuixText("点加号或键盘完成添加，点击标签删除，修改即时保存。", style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        } else {
            OutlinedTextField(
                value = input, onValueChange = { onInputChange(it.replace("\n", "")) },
                enabled = enabled, label = { Text("添加特征词") }, modifier = inputModifier, singleLine = true,
                trailingIcon = { IconButton(onClick = add, enabled = enabled) { Icon(Icons.Default.Add, "添加特征词") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }), shape = RoundedCornerShape(15.dp),
            )
            Text("点加号或键盘完成添加，点击标签删除，修改即时保存。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
