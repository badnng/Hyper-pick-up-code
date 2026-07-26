package com.Badnng.moe.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.Badnng.moe.R
import com.Badnng.moe.helper.BrandIconResolver
import com.Badnng.moe.helper.ScreenshotStorage

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Md3eOrderDetailContent(
    state: OrderDetailUiState,
    actions: OrderDetailActions,
    modifier: Modifier = Modifier,
) {
    val order = state.order
    val motionScheme = MaterialTheme.motionScheme
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = state.bottomSpacing,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (state.screenshotExists) {
            item { Md3eScreenshotSection(state, actions) }
        }
        state.ocrDebugState?.let { debugState ->
            item {
                Md3eOcrDebugSection(
                    state = debugState,
                    hideLowConfidence = state.hideLowConfidenceOcr,
                    onToggleLowConfidence = {
                        actions.performHaptic()
                        actions.onToggleOcrLowConfidence()
                    },
                    onCopyAll = {
                        actions.performHaptic()
                        actions.onCopyOcrDebug()
                    },
                )
            }
        }
        item { Md3eOrderSummary(state, actions) }
        item {
            Button(
                onClick = {
                    actions.performHaptic()
                    actions.onCopyDiagnostics()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(15.dp),
            ) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("复制完整诊断信息")
            }
        }
        item {
            Md3eDetailSection("识别结果") {
                Md3eDetailRow("取件位置", order.pickupLocation ?: UNRECORDED_VALUE)
                Md3eDetailRow("来源应用", order.sourceApp ?: UNRECORDED_VALUE)
                Md3eDetailRow("来源包名", order.sourcePackage ?: UNRECORDED_VALUE)
                Md3eDetailRow("触发方式", recognitionTriggerLabel(order))
            }
        }
        item {
            Md3eDetailSection("识别诊断") {
                Md3eDetailRow("识别路径", recognitionModeLabel(order), emphasize = true)
                Md3eDetailRow("输入类型", recognitionInputLabel(order))
                Md3eDetailRow("供应商", recognitionProviderLabel(order))
                Md3eDetailRow("模型", recognitionModelLabel(order))
                Md3eDetailRow("总耗时", recognitionDurationLabel(order))
                Md3eDetailRow("离线降级", recognitionFallbackLabel(order))
            }
        }
        if (order.recognitionUsedOfflineFallback == true || !order.recognitionError.isNullOrBlank()) {
            item {
                Md3eDiagnosticWarning(
                    error = order.recognitionError,
                    usedOfflineFallback = order.recognitionUsedOfflineFallback == true,
                )
            }
        }
        item {
            Md3eOriginalTextSection(
                text = orderOriginalText(order),
                expanded = state.fullTextExpanded,
                onCopy = {
                    actions.performHaptic()
                    actions.onCopyOriginal()
                },
                onToggle = {
                    actions.performHaptic()
                    actions.onToggleFullText()
                },
            )
        }
        item {
            Surface(
                onClick = {
                    actions.performHaptic()
                    actions.onToggleTechnical()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "更多技术信息",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = if (state.technicalExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (state.technicalExpanded) "收起技术信息" else "展开技术信息",
                        )
                    }
                    AnimatedVisibility(
                        visible = state.technicalExpanded,
                        enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                            expandVertically(animationSpec = motionScheme.defaultSpatialSpec<IntSize>()),
                        exit = fadeOut(motionScheme.defaultEffectsSpec()) +
                            shrinkVertically(animationSpec = motionScheme.defaultSpatialSpec<IntSize>()),
                    ) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Md3eDetailRow("订单 ID", order.id, monospace = true)
                            Md3eDetailRow("分组 ID", order.groupId?.toString() ?: "无")
                            Md3eDetailRow("二维码数据", order.qrCodeData ?: UNRECORDED_VALUE, monospace = true)
                            Md3eDetailRow("截图路径", order.screenshotPath.ifBlank { "无" }, monospace = true)
                            if (!order.recognitionErrorDetail.isNullOrBlank()) {
                                Md3eDetailRow("错误详情", order.recognitionErrorDetail, monospace = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Md3eOrderSummary(state: OrderDetailUiState, actions: OrderDetailActions) {
    val context = LocalContext.current
    val order = state.order
    val customBitmap = remember(order.brandName) {
        BrandIconResolver.resolveCustomIconBitmap(context, order.brandName)
    }
    val fallbackIcon = remember(order.brandName, order.orderType) {
        BrandIconResolver.resolveBuiltinFallbackResId(context, order.brandName, order.orderType)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (customBitmap != null) {
                            Image(
                                bitmap = customBitmap.asImageBitmap(),
                                contentDescription = order.brandName,
                                modifier = Modifier.size(36.dp),
                            )
                        } else {
                            Image(
                                painter = painterResource(fallbackIcon),
                                contentDescription = order.brandName ?: order.orderType,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.brandName ?: "未记录品牌",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "${order.orderType} · ${if (order.isCompleted) "已完成" else "未完成"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatOrderTime(order.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.takeoutCode,
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = {
                        actions.performHaptic()
                        actions.onCopyCode()
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "复制取餐码",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun Md3eDetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp).semantics { heading() },
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), content = content)
        }
    }
}

@Composable
private fun Md3eDetailRow(
    label: String,
    value: String,
    emphasize: Boolean = false,
    monospace: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(82.dp),
        )
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun Md3eDiagnosticWarning(error: String?, usedOfflineFallback: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = when {
                    usedOfflineFallback && error != null -> "在线识别异常，已执行离线降级"
                    usedOfflineFallback -> "已执行离线降级"
                    else -> "识别异常"
                },
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = error ?: "在线识别未完成，订单由离线识别生成。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Md3eOriginalTextSection(
    text: String?,
    expanded: Boolean,
    onCopy: () -> Unit,
    onToggle: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "原文记录",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp).semantics { heading() },
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SOURCE_TEXT",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onCopy, enabled = text != null, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制原文")
                    }
                    IconButton(onClick = onToggle, modifier = Modifier.size(48.dp)) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "收起原文" else "展开原文",
                        )
                    }
                }
                SelectionContainer {
                    Text(
                        text = text ?: LEGACY_DIAGNOSTIC_VALUE,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 19.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.animateContentSize(motionScheme.defaultSpatialSpec<IntSize>()),
                    )
                }
            }
        }
    }
}

@Composable
private fun Md3eScreenshotSection(state: OrderDetailUiState, actions: OrderDetailActions) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val previewWidth = minOf(
            maxWidth,
            state.screenshotPreviewMaxHeight * state.screenshotAspectRatio,
        )
        val previewHeight = previewWidth / state.screenshotAspectRatio
        val shape = state.screenshotCornerPercents.toRoundedCornerShape()
        val imageModifier = Modifier
            .width(previewWidth)
            .height(previewHeight)
            .clip(shape)
            .clickable {
                actions.performHaptic()
                actions.onShowImage()
            }
        val debugResult = state.ocrDebugState?.result
        if (debugResult != null) {
            OcrAnnotatedImage(
                imageModel = ScreenshotStorage.imageModel(state.order.screenshotPath),
                imageWidth = debugResult.imageWidth,
                imageHeight = debugResult.imageHeight,
                blocks = visibleOcrDebugBlocks(debugResult, state.hideLowConfidenceOcr),
                shape = shape,
                modifier = imageModifier,
            )
        } else {
            AsyncImage(
                model = ScreenshotStorage.imageModel(state.order.screenshotPath),
                contentDescription = "识别截图，点击查看大图",
                modifier = imageModifier,
                contentScale = ContentScale.Fit,
            )
        }
    }
}
