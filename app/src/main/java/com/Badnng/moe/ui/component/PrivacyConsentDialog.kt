package com.Badnng.moe.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.privacy.PrivacyConsent
import com.Badnng.moe.ui.theme.NonPredictiveBackInterceptor
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
fun PrivacyConsentBottomSheet(
    show: Boolean,
    isMiuix: Boolean = true,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = "同意并启用",
) {
    val context = LocalContext.current
    val document = remember(context) { PrivacyConsent.loadPolicy(context) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val contentHeight = (configuration.screenHeightDp.dp * 0.66f)
        .coerceIn(360.dp, 620.dp)

    if (!isMiuix) {
        Md3PrivacyConsentBottomSheet(
            show = show,
            title = title,
            document = document.content,
            documentAvailable = document.isAvailable,
            contentHeight = contentHeight,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            confirmLabel = confirmLabel,
        )
        return
    }

    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    var showSheet by remember { mutableStateOf(show) }
    var confirmAfterDismiss by remember { mutableStateOf(false) }
    var dragProgress by remember { androidx.compose.runtime.mutableFloatStateOf(-1f) }
    val blurProgress = remember { androidx.compose.animation.core.Animatable(0f) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { blurProgress.value }
            .collect { BlurState.updateProgress(it) }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { BlurState.hide() }
    }
    androidx.compose.runtime.LaunchedEffect(show) {
        if (show) {
            showSheet = true
            confirmAfterDismiss = false
            BlurState.show()
            dragProgress = -1f
            blurProgress.snapTo(0f)
            blurProgress.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.85f,
                    stiffness = 300f,
                ),
            )
        }
    }
    androidx.compose.runtime.LaunchedEffect(showSheet) {
        if (!showSheet) {
            blurProgress.animateTo(
                targetValue = 0f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.85f,
                    stiffness = 300f,
                ),
            )
        }
    }
    androidx.compose.runtime.LaunchedEffect(dragProgress) {
        if (dragProgress in 0f..1f) {
            blurProgress.snapTo(dragProgress)
        }
    }
    WindowBottomSheet(
        show = showSheet,
        title = title,
        enableWindowDim = false,
        allowDismiss = true,
        enableNestedScroll = true,
        onDismissRequest = { showSheet = false },
        onDismissFinished = {
            BlurState.hide()
            if (confirmAfterDismiss) {
                confirmAfterDismiss = false
                onConfirm()
            } else {
                onDismiss()
            }
        },
    ) {
        NonPredictiveBackInterceptor()
        val dismiss = LocalDismissState.current
        if (showSheet) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.dp)
                    .onGloballyPositioned { coordinates ->
                        val sheetTop = coordinates.positionInWindow().y
                        dragProgress = 1f - (sheetTop / screenHeightPx).coerceIn(0f, 1f)
                    },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(contentHeight)
                .navigationBarsPadding()
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiuixText(
                text = "启用前请阅读《澎湃记用户协议与隐私说明》。只有明确同意后，相关联网功能才会开启。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                lineHeight = 20.sp,
            )
            SelectionContainer(modifier = Modifier.weight(1f)) {
                PrivacyMarkdownContent(
                    markdown = document.content,
                    isMiuix = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiuixTextButton(
                    text = "不同意",
                    onClick = {
                        if (dismiss != null) dismiss() else showSheet = false
                    },
                    modifier = Modifier.weight(1f),
                )
                MiuixButton(
                    onClick = {
                        confirmAfterDismiss = true
                        if (dismiss != null) dismiss() else showSheet = false
                    },
                    enabled = document.isAvailable,
                    modifier = Modifier.weight(1f),
                ) {
                    MiuixText(confirmLabel)
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun Md3PrivacyConsentBottomSheet(
    show: Boolean,
    title: String,
    document: String,
    documentAvailable: Boolean,
    contentHeight: androidx.compose.ui.unit.Dp,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String,
) {
    if (!show) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(contentHeight)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "启用前请阅读《澎湃记用户协议与隐私说明》。只有明确同意后，相关联网功能才会开启。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectionContainer(modifier = Modifier.weight(1f)) {
                PrivacyMarkdownContent(
                    markdown = document,
                    isMiuix = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Text("不同意")
                }
                Button(
                    onClick = onConfirm,
                    enabled = documentAvailable,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Text(confirmLabel)
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyBottomSheet(
    show: Boolean,
    isMiuix: Boolean,
    isAccepted: Boolean,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
    onRevoke: () -> Unit,
) {
    val context = LocalContext.current
    val document = remember(context) { PrivacyConsent.loadPolicy(context) }
    val contentHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.68f)
        .coerceIn(360.dp, 620.dp)

    if (isMiuix) {
        MiuixPrivacyPolicyBottomSheet(
            show = show,
            document = document.content,
            contentHeight = contentHeight,
            isAccepted = isAccepted,
            onDismiss = onDismiss,
            onAccept = onAccept,
            onRevoke = onRevoke,
        )
    } else if (show) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(contentHeight)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "用户协议与隐私说明",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (isAccepted) {
                        "当前已同意。撤销后将关闭在线识别与联网更新，并切换回离线识别。"
                    } else {
                        "当前未同意，在线识别与联网更新不会启用。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    PrivacyMarkdownContent(
                        markdown = document.content,
                        isMiuix = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (isAccepted) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = onRevoke,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text("撤销同意")
                        }
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp),
                        ) {
                            Text("关闭")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp),
                        ) {
                            Text("关闭")
                        }
                        Button(
                            onClick = onAccept,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp),
                        ) {
                            Text("同意")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixPrivacyPolicyBottomSheet(
    show: Boolean,
    document: String,
    contentHeight: androidx.compose.ui.unit.Dp,
    isAccepted: Boolean,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
    onRevoke: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    var showSheet by remember { mutableStateOf(show) }
    var dragProgress by remember { androidx.compose.runtime.mutableFloatStateOf(-1f) }
    val blurProgress = remember { androidx.compose.animation.core.Animatable(0f) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { blurProgress.value }
            .collect { BlurState.updateProgress(it) }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { BlurState.hide() }
    }
    androidx.compose.runtime.LaunchedEffect(show) {
        if (show) {
            showSheet = true
            BlurState.show()
            dragProgress = -1f
            blurProgress.snapTo(0f)
            blurProgress.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.85f,
                    stiffness = 300f,
                ),
            )
        }
    }
    androidx.compose.runtime.LaunchedEffect(showSheet) {
        if (!showSheet) {
            blurProgress.animateTo(
                targetValue = 0f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.85f,
                    stiffness = 300f,
                ),
            )
        }
    }
    androidx.compose.runtime.LaunchedEffect(dragProgress) {
        if (dragProgress in 0f..1f) blurProgress.snapTo(dragProgress)
    }
    WindowBottomSheet(
        show = showSheet,
        title = "用户协议与隐私说明",
        enableWindowDim = false,
        allowDismiss = true,
        enableNestedScroll = true,
        onDismissRequest = { showSheet = false },
        onDismissFinished = {
            BlurState.hide()
            onDismiss()
        },
    ) {
        NonPredictiveBackInterceptor()
        val dismiss = LocalDismissState.current
        if (showSheet) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.dp)
                    .onGloballyPositioned { coordinates ->
                        val sheetTop = coordinates.positionInWindow().y
                        dragProgress = 1f - (sheetTop / screenHeightPx).coerceIn(0f, 1f)
                    },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(contentHeight)
                .navigationBarsPadding()
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiuixText(
                text = if (isAccepted) {
                    "当前已同意。撤销后将关闭在线识别与联网更新，并切换回离线识别。"
                } else {
                    "当前未同意，在线识别与联网更新不会启用。"
                },
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                lineHeight = 20.sp,
            )
            SelectionContainer(modifier = Modifier.weight(1f)) {
                PrivacyMarkdownContent(
                    markdown = document,
                    isMiuix = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (isAccepted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MiuixButton(
                        onClick = onRevoke,
                        modifier = Modifier.weight(1f),
                        colors = MiuixButtonDefaults.buttonColors(),
                    ) {
                        MiuixText("撤销同意", color = MiuixTheme.colorScheme.error)
                    }
                    MiuixButton(
                        onClick = {
                            if (dismiss != null) dismiss() else showSheet = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = MiuixButtonDefaults.buttonColorsPrimary(),
                    ) {
                        MiuixText("关闭")
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MiuixButton(
                        onClick = {
                            if (dismiss != null) dismiss() else showSheet = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = MiuixButtonDefaults.buttonColors(),
                    ) {
                        MiuixText("关闭")
                    }
                    MiuixButton(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f),
                        colors = MiuixButtonDefaults.buttonColorsPrimary(),
                    ) {
                        MiuixText("同意")
                    }
                }
            }
        }
    }
}

private enum class PrivacyMarkdownKind {
    Heading1,
    Heading2,
    Heading3,
    Paragraph,
    Bullet,
    Quote,
    Rule,
    Space,
}

private data class PrivacyMarkdownBlock(
    val kind: PrivacyMarkdownKind,
    val text: String = "",
    val strong: Boolean = false,
    val url: String? = null,
)

@Composable
private fun PrivacyMarkdownContent(
    markdown: String,
    isMiuix: Boolean,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val blocks = remember(markdown) { parsePrivacyMarkdown(markdown) }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            count = blocks.size,
            key = { index -> index },
        ) { index ->
            val block = blocks[index]
            when (block.kind) {
                PrivacyMarkdownKind.Space -> Spacer(Modifier.height(4.dp))
                PrivacyMarkdownKind.Rule -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            if (isMiuix) {
                                MiuixTheme.colorScheme.outline.copy(alpha = 0.24f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            },
                        ),
                )
                else -> {
                    val displayedText = when (block.kind) {
                        PrivacyMarkdownKind.Bullet -> "•  ${block.text}"
                        PrivacyMarkdownKind.Quote -> "▎ ${block.text}"
                        else -> block.text
                    }
                    val textModifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (block.kind == PrivacyMarkdownKind.Bullet ||
                                block.kind == PrivacyMarkdownKind.Quote
                            ) {
                                Modifier.padding(start = 8.dp)
                            } else {
                                Modifier
                            },
                        )
                        .then(
                            if (block.url != null) {
                                Modifier.clickable { uriHandler.openUri(block.url) }
                            } else {
                                Modifier
                            },
                        )

                    if (isMiuix) {
                        val style = when (block.kind) {
                            PrivacyMarkdownKind.Heading1 -> MiuixTheme.textStyles.headline1
                            PrivacyMarkdownKind.Heading2 -> MiuixTheme.textStyles.title1
                            PrivacyMarkdownKind.Heading3 -> MiuixTheme.textStyles.body1
                            else -> MiuixTheme.textStyles.body2
                        }
                        MiuixText(
                            text = displayedText,
                            modifier = textModifier,
                            style = style,
                            fontWeight = if (
                                block.strong || block.kind == PrivacyMarkdownKind.Heading1 ||
                                block.kind == PrivacyMarkdownKind.Heading2 ||
                                block.kind == PrivacyMarkdownKind.Heading3
                            ) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                            color = when {
                                block.url != null -> MiuixTheme.colorScheme.primary
                                block.kind == PrivacyMarkdownKind.Quote ->
                                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                                else -> MiuixTheme.colorScheme.onSurface
                            },
                            lineHeight = when (block.kind) {
                                PrivacyMarkdownKind.Heading1 -> 30.sp
                                PrivacyMarkdownKind.Heading2 -> 26.sp
                                PrivacyMarkdownKind.Heading3 -> 23.sp
                                else -> 20.sp
                            },
                        )
                    } else {
                        Text(
                            text = displayedText,
                            modifier = textModifier,
                            style = when (block.kind) {
                                PrivacyMarkdownKind.Heading1 -> MaterialTheme.typography.headlineSmall
                                PrivacyMarkdownKind.Heading2 -> MaterialTheme.typography.titleLarge
                                PrivacyMarkdownKind.Heading3 -> MaterialTheme.typography.titleMedium
                                else -> MaterialTheme.typography.bodySmall
                            },
                            fontWeight = if (
                                block.strong || block.kind == PrivacyMarkdownKind.Heading1 ||
                                block.kind == PrivacyMarkdownKind.Heading2 ||
                                block.kind == PrivacyMarkdownKind.Heading3
                            ) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                            color = when {
                                block.url != null -> MaterialTheme.colorScheme.primary
                                block.kind == PrivacyMarkdownKind.Quote ->
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            lineHeight = when (block.kind) {
                                PrivacyMarkdownKind.Heading1 -> 30.sp
                                PrivacyMarkdownKind.Heading2 -> 26.sp
                                PrivacyMarkdownKind.Heading3 -> 23.sp
                                else -> 20.sp
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun parsePrivacyMarkdown(markdown: String): List<PrivacyMarkdownBlock> {
    val heading = Regex("""^(#{1,6})\s+(.+)$""")
    val bullet = Regex("""^[-*+]\s+(.+)$""")
    val numbered = Regex("""^(\d+[.)])\s+(.+)$""")
    val tableSeparator = Regex("""^\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?$""")
    val urlPattern = Regex("""https?://[^\s）)>]+""")

    return markdown.lineSequence().mapNotNull { sourceLine ->
        val trimmed = sourceLine.trim()
        if (trimmed.isEmpty()) {
            return@mapNotNull PrivacyMarkdownBlock(PrivacyMarkdownKind.Space)
        }
        if (trimmed == "---") {
            return@mapNotNull PrivacyMarkdownBlock(PrivacyMarkdownKind.Rule)
        }
        if (tableSeparator.matches(trimmed)) return@mapNotNull null

        val headingMatch = heading.matchEntire(trimmed)
        val bulletMatch = bullet.matchEntire(trimmed)
        val numberedMatch = numbered.matchEntire(trimmed)
        val startsStrong = trimmed.startsWith("**") || trimmed.startsWith("__")
        val kind: PrivacyMarkdownKind
        val rawText: String

        when {
            headingMatch != null -> {
                kind = when (headingMatch.groupValues[1].length) {
                    1 -> PrivacyMarkdownKind.Heading1
                    2 -> PrivacyMarkdownKind.Heading2
                    else -> PrivacyMarkdownKind.Heading3
                }
                rawText = headingMatch.groupValues[2]
            }
            bulletMatch != null -> {
                kind = PrivacyMarkdownKind.Bullet
                rawText = bulletMatch.groupValues[1]
            }
            numberedMatch != null -> {
                kind = PrivacyMarkdownKind.Paragraph
                rawText = "${numberedMatch.groupValues[1]} ${numberedMatch.groupValues[2]}"
            }
            trimmed.startsWith(">") -> {
                kind = PrivacyMarkdownKind.Quote
                rawText = trimmed.removePrefix(">").trimStart()
            }
            trimmed.startsWith("|") && trimmed.endsWith("|") -> {
                kind = PrivacyMarkdownKind.Paragraph
                rawText = trimmed.trim('|').split('|').joinToString(" ｜ ") { it.trim() }
            }
            else -> {
                kind = PrivacyMarkdownKind.Paragraph
                rawText = trimmed
            }
        }

        val text = cleanPrivacyMarkdownInline(rawText)
        PrivacyMarkdownBlock(
            kind = kind,
            text = text,
            strong = startsStrong,
            url = urlPattern.find(text)?.value,
        )
    }.toList()
}

private fun cleanPrivacyMarkdownInline(source: String): String {
    val markdownLink = Regex("""\[([^]]+)]\((https?://[^)]+)\)""")
    val angleLink = Regex("""<(https?://[^>]+)>""")
    return angleLink.replace(
        markdownLink.replace(source) { match ->
            "${match.groupValues[1]}（${match.groupValues[2]}）"
        },
    ) { match -> match.groupValues[1] }
        .replace("**", "")
        .replace("__", "")
        .replace("`", "")
        .trimEnd()
}
