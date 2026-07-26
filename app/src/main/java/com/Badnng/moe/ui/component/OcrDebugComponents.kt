package com.Badnng.moe.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch as Md3eSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.Badnng.moe.ocr.PaddleOcrHelper
import com.Badnng.moe.ocr.OcrDiagnosticsPreferences
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class OcrDebugUiState(
    val result: PaddleOcrHelper.DiagnosticResult? = null,
    val errorMessage: String? = null,
)

private val debugBoxColors = listOf(
    Color(0xFF00A86B),
    Color(0xFFE53935),
    Color(0xFFF57C00),
    Color(0xFF8E24AA),
    Color(0xFF008C9E),
    Color(0xFF1976D2),
)

fun visibleOcrDebugBlocks(
    result: PaddleOcrHelper.DiagnosticResult,
    hideLowConfidence: Boolean,
): List<PaddleOcrHelper.DiagnosticTextBlock> = if (hideLowConfidence) {
    result.textBlocks.filter { it.confidence >= OcrDiagnosticsPreferences.MIN_CONFIDENCE }
} else {
    result.textBlocks
}

@Composable
fun OcrAnnotatedImage(
    imageModel: Any,
    imageWidth: Int,
    imageHeight: Int,
    blocks: List<PaddleOcrHelper.DiagnosticTextBlock>,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    Box(
        modifier = modifier
            .clip(shape)
            .clipToBounds()
            .semantics { contentDescription = "带 OCR 检测框的识别截图" },
    ) {
        AsyncImage(
            model = imageModel,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scaleX = size.width / imageWidth.coerceAtLeast(1)
            val scaleY = size.height / imageHeight.coerceAtLeast(1)
            blocks.forEachIndexed { index, block ->
                if (block.points.size < 4) return@forEachIndexed
                val color = debugBoxColors[index % debugBoxColors.size]
                val points = block.points.map { point ->
                    Offset(point.x * scaleX, point.y * scaleY)
                }
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { point -> lineTo(point.x, point.y) }
                    close()
                }
                drawPath(path, color, style = Stroke(2.dp.toPx()))

                val label = textMeasurer.measure(
                    text = AnnotatedString((index + 1).toString()),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                val labelWidth = label.size.width + 8.dp.toPx()
                val labelHeight = label.size.height + 4.dp.toPx()
                val left = points.minOf { it.x }
                    .coerceIn(0f, (size.width - labelWidth).coerceAtLeast(0f))
                val top = (points.minOf { it.y } - labelHeight).coerceAtLeast(0f)
                drawRoundRect(
                    color = color.copy(alpha = 0.94f),
                    topLeft = Offset(left, top),
                    size = Size(labelWidth, labelHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                )
                drawText(
                    textLayoutResult = label,
                    topLeft = Offset(left + 4.dp.toPx(), top + 2.dp.toPx()),
                )
            }
        }
    }
}

@Composable
fun Md3eOcrDebugSection(
    state: OcrDebugUiState,
    hideLowConfidence: Boolean,
    onToggleLowConfidence: () -> Unit,
    onCopyAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "本地 OCR 调试",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        when {
            state.errorMessage != null -> Surface(
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Text(state.errorMessage, modifier = Modifier.padding(16.dp))
            }
            state.result != null -> {
                val visibleBlocks = visibleOcrDebugBlocks(state.result, hideLowConfidence)
                Md3eTimingRow(state.result)
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("屏蔽低识别率内容", fontWeight = FontWeight.Medium)
                            Text(
                                "隐藏低于 93% 的原始识别内容",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Md3eSwitch(
                            checked = hideLowConfidence,
                            onCheckedChange = { onToggleLowConfidence() },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "识别内容 (${visibleBlocks.size}/${state.result.textBlocks.size})",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onCopyAll) { Text("复制全部") }
                }
                visibleBlocks.forEachIndexed { index, block ->
                    Md3eResultRow(index, block)
                }
            }
        }
    }
}

@Composable
fun MiuixOcrDebugSection(
    state: OcrDebugUiState,
    hideLowConfidence: Boolean,
    onToggleLowConfidence: () -> Unit,
    onCopyAll: () -> Unit,
) {
    Column {
        SmallTitle(text = "本地 OCR 调试")
        when {
            state.errorMessage != null -> MiuixCard(modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                MiuixText(
                    state.errorMessage,
                    modifier = Modifier.padding(16.dp),
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.body2,
                )
            }
            state.result != null -> Column(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val visibleBlocks = visibleOcrDebugBlocks(state.result, hideLowConfidence)
                MiuixTimingRow(state.result)
                MiuixCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            MiuixText(
                                "屏蔽低识别率内容",
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            MiuixText(
                                "隐藏低于 93% 的原始识别内容",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        MiuixSwitch(
                            checked = hideLowConfidence,
                            onCheckedChange = { onToggleLowConfidence() },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiuixText(
                        "识别内容 (${visibleBlocks.size}/${state.result.textBlocks.size})",
                        modifier = Modifier.weight(1f),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                    )
                    MiuixTextButton(text = "复制全部", onClick = onCopyAll)
                }
                visibleBlocks.forEachIndexed { index, block ->
                    MiuixResultRow(index, block)
                }
            }
        }
    }
}

@Composable
private fun Md3eTimingRow(result: PaddleOcrHelper.DiagnosticResult) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Md3eTimingItem("检测", result.detectionTimeMs, Modifier.weight(1f))
        Md3eTimingItem("识别", result.recognitionTimeMs, Modifier.weight(1f))
        Md3eTimingItem("总计", result.totalTimeMs, Modifier.weight(1f))
    }
}

@Composable
private fun Md3eTimingItem(label: String, timeMs: Long, modifier: Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(15.dp)) {
        Column(modifier = Modifier.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${timeMs}ms", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MiuixTimingRow(result: PaddleOcrHelper.DiagnosticResult) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MiuixTimingItem("检测", result.detectionTimeMs, Modifier.weight(1f))
        MiuixTimingItem("识别", result.recognitionTimeMs, Modifier.weight(1f))
        MiuixTimingItem("总计", result.totalTimeMs, Modifier.weight(1f))
    }
}

@Composable
private fun MiuixTimingItem(label: String, timeMs: Long, modifier: Modifier) {
    MiuixCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            MiuixText(label, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            MiuixText("${timeMs}ms", style = MiuixTheme.textStyles.body1, color = MiuixTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun Md3eResultRow(index: Int, block: PaddleOcrHelper.DiagnosticTextBlock) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}.", modifier = Modifier.width(34.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(block.text.ifBlank { "（空白）" }, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Text(
                formatConfidence(block.confidence),
                color = confidenceColor(block.confidence, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.error),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun MiuixResultRow(index: Int, block: PaddleOcrHelper.DiagnosticTextBlock) {
    MiuixCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            MiuixText(
                "${index + 1}.",
                modifier = Modifier.width(34.dp),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            MiuixText(
                block.text.ifBlank { "（空白）" },
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            MiuixText(
                formatConfidence(block.confidence),
                style = MiuixTheme.textStyles.body2,
                color = confidenceColor(block.confidence, MiuixTheme.colorScheme.primary, MiuixTheme.colorScheme.error),
            )
        }
    }
}

private fun formatConfidence(confidence: Float): String =
    String.format(Locale.US, "%.2f%%", confidence.coerceIn(0f, 1f) * 100f)

private fun confidenceColor(confidence: Float, high: Color, low: Color): Color = when {
    confidence >= 0.93f -> high
    confidence < 0.5f -> low
    else -> Color(0xFFF57C00)
}
