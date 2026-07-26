package com.Badnng.moe.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
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
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiuixOrderDetailContent(
    state: OrderDetailUiState,
    actions: OrderDetailActions,
    modifier: Modifier = Modifier,
) {
    val order = state.order
    val motionScheme = MaterialTheme.motionScheme
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 12.dp,
            bottom = state.bottomSpacing,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.screenshotExists) {
            item { MiuixScreenshotSection(state, actions) }
        }
        state.ocrDebugState?.let { debugState ->
            item {
                MiuixOcrDebugSection(
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
        item { MiuixOrderSummary(state, actions) }
        item {
            TextButton(
                text = "复制完整诊断信息",
                onClick = {
                    actions.performHaptic()
                    actions.onCopyDiagnostics()
                },
                modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
        item {
            MiuixDetailSection("识别结果") {
                MiuixDetailRow("取件位置", order.pickupLocation ?: UNRECORDED_VALUE)
                MiuixDetailRow("来源应用", order.sourceApp ?: UNRECORDED_VALUE)
                MiuixDetailRow("来源包名", order.sourcePackage ?: UNRECORDED_VALUE)
                MiuixDetailRow("触发方式", recognitionTriggerLabel(order))
            }
        }
        item {
            MiuixDetailSection("识别诊断") {
                MiuixDetailRow("识别路径", recognitionModeLabel(order), emphasize = true)
                MiuixDetailRow("输入类型", recognitionInputLabel(order))
                MiuixDetailRow("供应商", recognitionProviderLabel(order))
                MiuixDetailRow("模型", recognitionModelLabel(order))
                MiuixDetailRow("总耗时", recognitionDurationLabel(order))
                MiuixDetailRow("离线降级", recognitionFallbackLabel(order))
            }
        }
        if (order.recognitionUsedOfflineFallback == true || !order.recognitionError.isNullOrBlank()) {
            item {
                MiuixDiagnosticWarning(
                    error = order.recognitionError,
                    usedOfflineFallback = order.recognitionUsedOfflineFallback == true,
                )
            }
        }
        item {
            MiuixOriginalTextSection(
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
            Column {
                SmallTitle(text = "更多技术信息")
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                        .squircleClip(15.dp)
                        .clickable {
                            actions.performHaptic()
                            actions.onToggleTechnical()
                        },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (state.technicalExpanded) "收起技术信息" else "展开技术信息",
                                style = MiuixTheme.textStyles.headline2,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = if (state.technicalExpanded) {
                                    MiuixIcons.Regular.ExpandLess
                                } else {
                                    MiuixIcons.Regular.ExpandMore
                                },
                                contentDescription = if (state.technicalExpanded) "收起" else "展开",
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
                                MiuixDetailRow("订单 ID", order.id, monospace = true)
                                MiuixDetailRow("分组 ID", order.groupId?.toString() ?: "无")
                                MiuixDetailRow("二维码数据", order.qrCodeData ?: UNRECORDED_VALUE, monospace = true)
                                MiuixDetailRow("截图路径", order.screenshotPath.ifBlank { "无" }, monospace = true)
                                if (!order.recognitionErrorDetail.isNullOrBlank()) {
                                    MiuixDetailRow("错误详情", order.recognitionErrorDetail, monospace = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixOrderSummary(state: OrderDetailUiState, actions: OrderDetailActions) {
    val context = LocalContext.current
    val order = state.order
    val customBitmap = remember(order.brandName) {
        BrandIconResolver.resolveCustomIconBitmap(context, order.brandName)
    }
    val fallbackIcon = remember(order.brandName, order.orderType) {
        BrandIconResolver.resolveBuiltinFallbackResId(context, order.brandName, order.orderType)
    }
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .squircleSurface(MiuixTheme.colorScheme.surfaceContainer, 15.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(52.dp)
                        .squircleSurface(MiuixTheme.colorScheme.surface, 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
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
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.brandName ?: "未记录品牌",
                        style = MiuixTheme.textStyles.headline1,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    Text(
                        text = "${order.orderType} · ${if (order.isCompleted) "已完成" else "未完成"}",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Text(
                    text = formatOrderTime(order.createdAt),
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
                        color = MiuixTheme.colorScheme.primary,
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
                        MiuixIcons.Regular.Copy,
                        contentDescription = "复制取餐码",
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiuixDetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        SmallTitle(text = title)
        Card(modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), content = content)
        }
    }
}

@Composable
private fun MiuixDetailRow(
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
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(82.dp),
        )
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                style = MiuixTheme.textStyles.body2,
                fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                color = if (emphasize) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun MiuixDiagnosticWarning(error: String?, usedOfflineFallback: Boolean) {
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .squircleSurface(MiuixTheme.colorScheme.errorContainer, 15.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = when {
                    usedOfflineFallback && error != null -> "在线识别异常，已执行离线降级"
                    usedOfflineFallback -> "已执行离线降级"
                    else -> "识别异常"
                },
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = error ?: "在线识别未完成，订单由离线识别生成。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MiuixOriginalTextSection(
    text: String?,
    expanded: Boolean,
    onCopy: () -> Unit,
    onToggle: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    Column {
        SmallTitle(text = "原文记录")
        Card(modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SOURCE_TEXT",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onCopy, enabled = text != null, modifier = Modifier.size(48.dp)) {
                        Icon(MiuixIcons.Regular.Copy, contentDescription = "复制原文")
                    }
                    IconButton(onClick = onToggle, modifier = Modifier.size(48.dp)) {
                        Icon(
                            if (expanded) MiuixIcons.Regular.ExpandLess else MiuixIcons.Regular.ExpandMore,
                            contentDescription = if (expanded) "收起原文" else "展开原文",
                        )
                    }
                }
                SelectionContainer {
                    Text(
                        text = text ?: LEGACY_DIAGNOSTIC_VALUE,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
private fun MiuixScreenshotSection(state: OrderDetailUiState, actions: OrderDetailActions) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
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
        TextButton(
            text = "分享原图",
            onClick = {
                actions.performHaptic()
                actions.onShareScreenshot()
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}
