@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.Badnng.moe.ui.component

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add as Md3eAdd
import androidx.compose.material.icons.filled.Close as Md3eClose
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.Badnng.moe.recognition.RecognitionBlockedWordsPolicy
import com.Badnng.moe.recognition.RecognitionBlockedWordsPreferences
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication

@Stable
class BlockedWordsEditorState internal constructor(context: Context) {
    private val appContext = context.applicationContext

    var words by mutableStateOf(RecognitionBlockedWordsPreferences.load(appContext))
        private set

    var input by mutableStateOf("")

    fun addInput(): AddBlockedWordResult {
        val candidate = input.trim()
        if (candidate.isEmpty()) {
            input = ""
            return AddBlockedWordResult.Empty
        }
        if (words.any { it.equals(candidate, ignoreCase = true) }) {
            return AddBlockedWordResult.Duplicate
        }
        if (words.size >= RecognitionBlockedWordsPolicy.MAX_WORDS) {
            return AddBlockedWordResult.LimitReached
        }
        words = RecognitionBlockedWordsPreferences.save(appContext, words + candidate)
        input = ""
        return AddBlockedWordResult.Added
    }

    fun remove(word: String) {
        words = RecognitionBlockedWordsPreferences.save(
            appContext,
            words.filterNot { it == word },
        )
    }
}

enum class AddBlockedWordResult {
    Added,
    Empty,
    Duplicate,
    LimitReached,
}

@Composable
fun rememberBlockedWordsEditorState(): BlockedWordsEditorState {
    val context = LocalContext.current
    return remember(context) { BlockedWordsEditorState(context) }
}

@Composable
fun Md3eBlockedWordsEditor(
    state: BlockedWordsEditorState,
    performHaptic: () -> Unit,
) {
    val context = LocalContext.current
    val addWord = {
        performHaptic()
        showAddResult(context, state.addInput())
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "包含以下词语的短信或通知将直接忽略，不会上传或执行离线识别。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BlockedWordsEmptyOrFlow(
            words = state.words,
            emptyText = {
                Text(
                    text = "暂无屏蔽词",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            item = { word ->
                InputChip(
                    selected = false,
                    onClick = {
                        performHaptic()
                        state.remove(word)
                    },
                    label = {
                        Text(
                            text = word,
                            modifier = Modifier.widthIn(max = 240.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Md3eClose,
                            contentDescription = "删除屏蔽词 $word",
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    shape = RoundedCornerShape(15.dp),
                )
            },
        )
        OutlinedTextField(
            value = state.input,
            onValueChange = { state.input = it.replace("\n", "") },
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                        addWord()
                        true
                    } else {
                        false
                    }
                },
            label = { Text("添加屏蔽词") },
            supportingText = {
                Text("${state.words.size}/${RecognitionBlockedWordsPolicy.MAX_WORDS}")
            },
            trailingIcon = {
                IconButton(onClick = addWord) {
                    Icon(Icons.Default.Md3eAdd, contentDescription = "添加屏蔽词")
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addWord() }),
            shape = RoundedCornerShape(15.dp),
        )
    }
}

@Composable
fun MiuixBlockedWordsEditor(
    state: BlockedWordsEditorState,
    performHaptic: () -> Unit,
) {
    val context = LocalContext.current
    val addWord = {
        performHaptic()
        showAddResult(context, state.addInput())
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MiuixText(
            text = "包含以下词语的短信或通知将直接忽略，不会上传或执行离线识别。",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        BlockedWordsEmptyOrFlow(
            words = state.words,
            emptyText = {
                MiuixText(
                    text = "暂无屏蔽词",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            },
            item = { word ->
                MiuixBlockedWordChip(
                    word = word,
                    onRemove = {
                        performHaptic()
                        state.remove(word)
                    },
                )
            },
        )
        MiuixTextField(
            value = state.input,
            onValueChange = { state.input = it.replace("\n", "") },
            label = "添加屏蔽词（${state.words.size}/${RecognitionBlockedWordsPolicy.MAX_WORDS}）",
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                        addWord()
                        true
                    } else {
                        false
                    }
                },
            trailingIcon = {
                MiuixIconButton(onClick = addWord) {
                    MiuixIcon(MiuixIcons.Regular.Add, contentDescription = "添加屏蔽词")
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addWord() }),
        )
    }
}

@Composable
private fun BlockedWordsEmptyOrFlow(
    words: List<String>,
    emptyText: @Composable () -> Unit,
    item: @Composable (String) -> Unit,
) {
    if (words.isEmpty()) {
        emptyText()
    } else {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            words.forEach { word -> item(word) }
        }
    }
}

@Composable
private fun MiuixBlockedWordChip(
    word: String,
    onRemove: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val indicationColor = MiuixTheme.colorScheme.onSurface
    val indication = remember(indicationColor) { MiuixIndication(color = indicationColor) }

    Row(
        modifier = Modifier
            .squircleSurface(MiuixTheme.colorScheme.surfaceContainer, 15.dp)
            .squircleClip(15.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = indication,
                role = Role.Button,
                onClick = onRemove,
            )
            .semantics {
                role = Role.Button
                contentDescription = "删除屏蔽词 $word"
            }
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiuixText(
            text = word,
            modifier = Modifier.widthIn(max = 240.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        MiuixIcon(
            imageVector = MiuixIcons.Regular.Close,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun showAddResult(context: Context, result: AddBlockedWordResult) {
    val message = when (result) {
        AddBlockedWordResult.Added,
        AddBlockedWordResult.Empty -> return
        AddBlockedWordResult.Duplicate -> "该屏蔽词已存在"
        AddBlockedWordResult.LimitReached ->
            "最多添加 ${RecognitionBlockedWordsPolicy.MAX_WORDS} 条屏蔽词"
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
