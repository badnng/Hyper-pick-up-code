package com.Badnng.moe.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class PromptEditorVisuals(
    val textStyle: TextStyle,
    val lineNumberColor: Color,
    val dividerColor: Color,
    val cursorColor: Color,
    val editorBackground: Color,
)

@Composable
fun Md3ePromptEditor(
    lineCount: Int,
    onRestoreDefault: () -> Unit,
    modifier: Modifier,
    editor: @Composable (PromptEditorVisuals) -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$lineCount 行",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRestoreDefault) {
                    Text("恢复默认")
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                editor(
                    PromptEditorVisuals(
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 22.sp,
                        ),
                        lineNumberColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        dividerColor = MaterialTheme.colorScheme.outlineVariant,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        editorBackground = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        }
    }
}

@Composable
fun MiuixPromptEditor(
    lineCount: Int,
    onRestoreDefault: () -> Unit,
    modifier: Modifier,
    editor: @Composable (PromptEditorVisuals) -> Unit,
) {
    MiuixCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixText(
                    text = "$lineCount 行",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.weight(1f))
                MiuixTextButton(
                    text = "恢复默认",
                    onClick = onRestoreDefault,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                editor(
                    PromptEditorVisuals(
                        textStyle = MiuixTheme.textStyles.body2.copy(
                            color = MiuixTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 22.sp,
                        ),
                        lineNumberColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        dividerColor = MiuixTheme.colorScheme.outline.copy(alpha = 0.45f),
                        cursorColor = MiuixTheme.colorScheme.primary,
                        editorBackground = MiuixTheme.colorScheme.surfaceContainer,
                    ),
                )
            }
        }
    }
}
