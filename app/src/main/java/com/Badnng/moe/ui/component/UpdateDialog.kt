package com.Badnng.moe.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.helper.UpdateInfo
import com.Badnng.moe.ui.miuix.rememberMiuixStyle
import com.Badnng.moe.ui.theme.NonPredictiveBackInterceptor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator as MiuixLinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication
import top.yukonga.miuix.kmp.window.WindowBottomSheet

// ═══════════════════════════════════════════
//  兼容层：自动切换 Miuix / MD3E
// ═══════════════════════════════════════════

/**
 * 更新确认弹窗（自动适配 Miuix / MD3E）
 */
@Composable
fun UpdateSheet(
    show: Boolean,
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onInstall: () -> Unit
) {
    val isMiuix = rememberMiuixStyle()
    if (isMiuix) {
        MiuixUpdateSheet(show = show, updateInfo = updateInfo, onDismiss = onDismiss, onInstall = onInstall)
    } else if (show) {
        Md3eUpdateSheet(updateInfo = updateInfo, onDismiss = onDismiss, onInstall = onInstall)
    }
}

/**
 * 更新进度弹窗（自动适配 Miuix / MD3E）
 * @param progress 进度 0f..1f，null 表示不确定状态
 */
@Composable
fun UpdateProgressSheet(
    show: Boolean,
    updateInfo: UpdateInfo,
    progress: Float?,
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit
) {
    val isMiuix = rememberMiuixStyle()
    if (isMiuix) {
        MiuixUpdateProgressSheet(show = show, updateInfo = updateInfo, progress = progress, isPaused = isPaused, onPause = onPause, onResume = onResume, onDismiss = onDismiss)
    } else if (show) {
        Md3eUpdateProgressSheet(
            updateInfo = updateInfo,
            progress = progress,
            isPaused = isPaused,
            onPause = onPause,
            onResume = onResume,
            onDismiss = onDismiss,
        )
    }
}

// ═══════════════════════════════════════════
//  Miuix 实现
// ═══════════════════════════════════════════

@Composable
private fun MiuixUpdateSheet(
    show: Boolean,
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onInstall: () -> Unit
) {
    val releaseNotesHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.30f)
        .coerceIn(160.dp, 360.dp)
    // 模糊进度：Animatable 驱动开/关动画，拖拽时 snapTo 覆盖
    val density = androidx.compose.ui.platform.LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val sheetHeightPx = remember { with(density) { configuration.screenHeightDp.dp.toPx() } }
    val blurProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    var dragProgress by remember { androidx.compose.runtime.mutableFloatStateOf(-1f) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { blurProgress.value }
            .collect { BlurState.updateProgress(it) }
    }

    androidx.compose.runtime.LaunchedEffect(show) {
        if (show) {
            BlurState.show()
            blurProgress.snapTo(0f)
            blurProgress.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 300f)
            )
        } else {
            blurProgress.snapTo(blurProgress.value)
            blurProgress.animateTo(
                targetValue = 0f,
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 300f)
            )
        }
    }

    androidx.compose.runtime.LaunchedEffect(dragProgress) {
        if (dragProgress in 0f..1f) {
            blurProgress.snapTo(dragProgress)
        }
    }


    WindowBottomSheet(
        show = show,
        title = "发现新版本",
        enableWindowDim = false,
        allowDismiss = true,
        enableNestedScroll = true,
        onDismissRequest = onDismiss,
        onDismissFinished = { BlurState.hide() }
    ) {
        NonPredictiveBackInterceptor()
        // 追踪 Sheet 拖拽位置
        if (show) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.dp)
                    .onGloballyPositioned { coords ->
                        if (show) {
                            val boxTop = coords.localToWindow(androidx.compose.ui.geometry.Offset(0f, 0f)).y
                            dragProgress = (1f - (boxTop / sheetHeightPx).coerceIn(0f, 1f))
                        }
                    }
            )
        }

        val dismiss = LocalDismissState.current
        val indicationColor = MiuixTheme.colorScheme.onBackground
        val miuixIndication = remember(indicationColor) { MiuixIndication(color = indicationColor) }
        CompositionLocalProvider(
            androidx.compose.foundation.LocalIndication provides miuixIndication
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                item {
                    MiuixText(
                        text = updateInfo.versionName,
                        style = MiuixTheme.textStyles.headline1,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                    )
                }
                item {
                    MiuixCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(releaseNotesHeight)
                            .padding(bottom = 12.dp),
                        colors = MiuixCardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            MiuixText(
                                text = updateInfo.releaseNotes,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface,
                                lineHeight = 20.sp,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MiuixButton(
                            onClick = { dismiss?.invoke() },
                            modifier = Modifier.weight(1f),
                            colors = MiuixButtonDefaults.buttonColors()
                        ) {
                            MiuixText("取消")
                        }
                        MiuixButton(
                            onClick = onInstall,
                            modifier = Modifier.weight(1f),
                            colors = MiuixButtonDefaults.buttonColorsPrimary()
                        ) {
                            MiuixText("更新")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixUpdateProgressSheet(
    show: Boolean,
    updateInfo: UpdateInfo,
    progress: Float?,
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit
) {
    val releaseNotesHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.30f)
        .coerceIn(160.dp, 360.dp)
    val density2 = androidx.compose.ui.platform.LocalDensity.current
    val configuration2 = androidx.compose.ui.platform.LocalConfiguration.current
    val sheetHeightPx2 = remember { with(density2) { configuration2.screenHeightDp.dp.toPx() } }
    val blurProgress2 = remember { androidx.compose.animation.core.Animatable(0f) }
    var dragProgress2 by remember { androidx.compose.runtime.mutableFloatStateOf(-1f) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { blurProgress2.value }
            .collect { BlurState.updateProgress(it) }
    }

    androidx.compose.runtime.LaunchedEffect(show) {
        if (show) {
            BlurState.show()
            blurProgress2.snapTo(0f)
            blurProgress2.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 300f)
            )
        } else {
            blurProgress2.snapTo(blurProgress2.value)
            blurProgress2.animateTo(
                targetValue = 0f,
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 300f)
            )
        }
    }

    androidx.compose.runtime.LaunchedEffect(dragProgress2) {
        if (dragProgress2 in 0f..1f) {
            blurProgress2.snapTo(dragProgress2)
        }
    }


    WindowBottomSheet(
        show = show,
        title = "正在更新",
        enableWindowDim = false,
        allowDismiss = false,
        enableNestedScroll = true,
        onDismissRequest = onDismiss,
        onDismissFinished = { BlurState.hide() }
    ) {
        NonPredictiveBackInterceptor()
        if (show) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.dp)
                    .onGloballyPositioned { coords ->
                        if (show) {
                            val boxTop = coords.localToWindow(androidx.compose.ui.geometry.Offset(0f, 0f)).y
                            dragProgress2 = (1f - (boxTop / sheetHeightPx2).coerceIn(0f, 1f))
                        }
                    }
            )
        }

        val dismiss = LocalDismissState.current
        val indicationColor = MiuixTheme.colorScheme.onBackground
        val miuixIndication = remember(indicationColor) { MiuixIndication(color = indicationColor) }
        CompositionLocalProvider(
            androidx.compose.foundation.LocalIndication provides miuixIndication
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                item {
                    MiuixText(
                        text = updateInfo.versionName,
                        style = MiuixTheme.textStyles.headline1,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                    )
                }
                item {
                    MiuixCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(releaseNotesHeight)
                            .padding(bottom = 12.dp),
                        colors = MiuixCardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            MiuixText(
                                text = updateInfo.releaseNotes,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface,
                                lineHeight = 20.sp,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (progress != null) {
                            MiuixText(
                                text = "${(progress * 100).toInt()}%",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        } else {
                            MiuixText(
                                text = "正在获取更新信息...",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }

                        MiuixLinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp)),
                            height = 8.dp,
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MiuixButton(
                            onClick = { dismiss?.invoke() },
                            modifier = Modifier.weight(1f),
                            colors = MiuixButtonDefaults.buttonColors()
                        ) {
                            MiuixText("后台更新")
                        }
                        MiuixButton(
                            onClick = { if (isPaused) onResume() else onPause() },
                            modifier = Modifier.weight(1f),
                            colors = MiuixButtonDefaults.buttonColorsPrimary()
                        ) {
                            MiuixText(if (isPaused) "继续" else "暂停")
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
//  MD3E 实现
// ═══════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Md3eUpdateSheet(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onInstall: () -> Unit
) {
    val largeFont = LocalDensity.current.fontScale >= 1.2f
    val releaseNotesHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.30f)
        .coerceIn(160.dp, 360.dp)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    var installEnabled by remember(updateInfo.versionCode) { mutableStateOf(false) }

    // 避免“检查更新”按钮的同一次触摸抬起事件落到刚出现的更新按钮上。
    LaunchedEffect(updateInfo.versionCode) {
        delay(400)
        installEnabled = true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        NonPredictiveBackInterceptor()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "发现新版本",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = updateInfo.versionName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(releaseNotesHeight),
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = updateInfo.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (largeFont) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(15.dp),
                    ) {
                        Text("暂不更新", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = {
                            installEnabled = false
                            coroutineScope.launch {
                                sheetState.hide()
                                onInstall()
                            }
                        },
                        enabled = installEnabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(15.dp),
                    ) {
                        Text("立即更新", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(15.dp),
                    ) {
                        Text("暂不更新", maxLines = 1)
                    }
                    Button(
                        onClick = {
                            installEnabled = false
                            coroutineScope.launch {
                                sheetState.hide()
                                onInstall()
                            }
                        },
                        enabled = installEnabled,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(15.dp),
                    ) {
                        Text("立即更新", maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Md3eUpdateProgressSheet(
    updateInfo: UpdateInfo,
    progress: Float?,
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit
) {
    val largeFont = LocalDensity.current.fontScale >= 1.2f
    val releaseNotesHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.30f)
        .coerceIn(160.dp, 360.dp)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        NonPredictiveBackInterceptor()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "正在更新",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = updateInfo.versionName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(releaseNotesHeight),
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = updateInfo.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = when {
                        isPaused -> "下载已暂停"
                        progress == null -> "正在连接下载服务器..."
                        else -> "${(progress * 100).toInt()}%"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (progress == null) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
            if (largeFont) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(15.dp),
                    ) {
                        Text("后台更新", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = if (isPaused) onResume else onPause,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(15.dp),
                    ) {
                        Text(if (isPaused) "继续" else "暂停", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(15.dp),
                    ) {
                        Text("后台更新")
                    }
                    Button(
                        onClick = if (isPaused) onResume else onPause,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(15.dp),
                    ) {
                        Text(if (isPaused) "继续" else "暂停")
                    }
                }
            }
        }
    }
}
