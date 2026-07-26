@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.Badnng.moe.ui.screen.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.recognition.OnlineRecognitionPreferences
import com.Badnng.moe.ui.LocalAppUi
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun PromptEditorContent(
    performHaptic: () -> Unit,
    topPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val appUi = LocalAppUi.current
    val defaultPrompt = remember(context) { OnlineRecognitionPreferences.defaultPrompt(context) }
    val initialPrompt = remember(context, defaultPrompt) {
        OnlineRecognitionPreferences.effectivePrompt(context, defaultPrompt)
    }
    val editorState = rememberTextFieldState(initialText = initialPrompt)
    val promptText = editorState.text.toString()
    val lineCount = remember(promptText) { logicalLineStarts(promptText).size }

    LaunchedEffect(editorState, context, defaultPrompt) {
        if (editorState.text.isBlank() &&
            OnlineRecognitionPreferences.customPrompt(context) == null
        ) {
            editorState.setTextAndPlaceCursorAtEnd(defaultPrompt)
        }
    }
    LaunchedEffect(editorState, context, defaultPrompt) {
        snapshotFlow { editorState.text.toString() }
            .distinctUntilChanged()
            .collect { prompt ->
                OnlineRecognitionPreferences.saveCustomPrompt(context, prompt, defaultPrompt)
            }
    }
    DisposableEffect(editorState, context, defaultPrompt) {
        onDispose {
            OnlineRecognitionPreferences.saveCustomPrompt(
                context,
                editorState.text.toString(),
                defaultPrompt,
            )
        }
    }

    val restoreDefault = {
        performHaptic()
        OnlineRecognitionPreferences.clearCustomPrompt(context)
        editorState.setTextAndPlaceCursorAtEnd(defaultPrompt)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding, start = 12.dp, end = 12.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .imePadding()
            .padding(bottom = 12.dp),
    ) {
        appUi.promptEditor(
            lineCount,
            restoreDefault,
            Modifier.fillMaxSize(),
        ) { visuals ->
            LineNumberedPromptEditor(
                text = promptText,
                textStyle = visuals.textStyle,
                lineNumberColor = visuals.lineNumberColor,
                dividerColor = visuals.dividerColor,
                cursorColor = visuals.cursorColor,
                editorBackground = visuals.editorBackground,
                state = editorState,
            )
        }
    }
}

@Composable
private fun LineNumberedPromptEditor(
    text: String,
    textStyle: TextStyle,
    lineNumberColor: Color,
    dividerColor: Color,
    cursorColor: Color,
    editorBackground: Color,
    state: androidx.compose.foundation.text.input.TextFieldState,
) {
    val scrollState = rememberScrollState()
    val textMeasurer = rememberTextMeasurer()
    val lineStarts = remember(text) { logicalLineStarts(text) }
    val lineNumberStyle = remember(lineNumberColor) {
        TextStyle(
            color = lineNumberColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    BasicTextField(
        state = state,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(editorBackground)
            .semantics { contentDescription = "自定义 Prompt 编辑器" },
        textStyle = textStyle,
        lineLimits = TextFieldLineLimits.MultiLine(),
        cursorBrush = SolidColor(cursorColor),
        scrollState = scrollState,
        onTextLayout = { getResult -> textLayoutResult = getResult() },
        decorator = TextFieldDecorator { innerTextField ->
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
            ) {
                Canvas(
                    modifier = Modifier
                        .width(LINE_NUMBER_GUTTER_WIDTH)
                        .fillMaxHeight()
                        .padding(vertical = EDITOR_VERTICAL_PADDING),
                ) {
                    drawLine(
                        color = dividerColor,
                        start = Offset(size.width - 1.dp.toPx(), 0f),
                        end = Offset(size.width - 1.dp.toPx(), size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                    val layout = textLayoutResult ?: return@Canvas
                    lineStarts.forEachIndexed { index, offset ->
                        val safeOffset = offset.coerceIn(0, text.length)
                        val visualLine = layout.getLineForOffset(safeOffset)
                        val lineTop = layout.getLineTop(visualLine) - scrollState.value
                        val lineBottom = layout.getLineBottom(visualLine) - scrollState.value
                        if (lineBottom < 0f || lineTop > size.height) return@forEachIndexed
                        val measured = textMeasurer.measure(
                            text = (index + 1).toString(),
                            style = lineNumberStyle,
                        )
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                x = size.width - measured.size.width - 8.dp.toPx(),
                                y = lineTop + (lineBottom - lineTop - measured.size.height) / 2f,
                            ),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(
                            start = 12.dp,
                            end = 12.dp,
                            top = EDITOR_VERTICAL_PADDING,
                            bottom = EDITOR_VERTICAL_PADDING,
                        ),
                ) {
                    innerTextField()
                }
            }
        },
    )
}

internal fun logicalLineStarts(text: String): List<Int> = buildList {
    add(0)
    text.forEachIndexed { index, character ->
        if (character == '\n') add(index + 1)
    }
}

private val LINE_NUMBER_GUTTER_WIDTH = 48.dp
private val EDITOR_VERTICAL_PADDING = 12.dp
