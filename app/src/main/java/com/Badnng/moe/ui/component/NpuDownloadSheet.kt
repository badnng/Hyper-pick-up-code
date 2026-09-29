package com.Badnng.moe.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.Badnng.moe.npu.NpuLibLoader
import com.Badnng.moe.npu.NpuModelDownloader
import com.Badnng.moe.ui.miuix.rememberMiuixStyle
import com.Badnng.moe.ui.theme.NonPredictiveBackInterceptor
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

/**
 * NPU 资源下载 BottomSheet(自动适配 Miuix / MD3E)
 *
 * @param show 是否显示
 * @param isMiuix 是否使用 Miuix 样式
 * @param arch HTP 架构号
 * @param progress 下载进度列表
 * @param isDownloading 是否正在下载
 * @param onDownload 点击下载按钮
 * @param onDismiss 关闭回调
 */
@Composable
fun NpuDownloadSheet(
    show: Boolean,
    isMiuix: Boolean,
    arch: Int,
    progress: List<NpuModelDownloader.FileProgress>,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    val performHaptic = {
        if (prefs.getBoolean("haptic_enabled", true)) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
    }

    if (isMiuix) {
        MiuixNpuDownloadSheet(
            show = show,
            arch = arch,
            progress = progress,
            isDownloading = isDownloading,
            onDownload = onDownload,
            onDismiss = onDismiss,
            performHaptic = performHaptic,
        )
    } else if (show) {
        Md3eNpuDownloadSheet(
            arch = arch,
            progress = progress,
            isDownloading = isDownloading,
            onDownload = onDownload,
            onDismiss = onDismiss,
            performHaptic = performHaptic,
        )
    }
}

// ═══════════════════════════════════════════
//  Miuix 实现
// ═══════════════════════════════════════════

@Composable
private fun MiuixNpuDownloadSheet(
    show: Boolean,
    arch: Int,
    progress: List<NpuModelDownloader.FileProgress>,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    performHaptic: () -> Unit,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val sheetHeightPx = remember { with(density) { configuration.screenHeightDp.dp.toPx() } }
    val blurProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    var dragProgress by remember { mutableStateOf(-1f) }

    // 兜底：即使宿主提前退出组合、WindowBottomSheet 收不到 show=false，
    // 也必须归零全局模糊进度，否则主页会被满强度遮罩盖死。
    DisposableEffect(Unit) {
        onDispose { BlurState.hide() }
    }

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
        title = "下载 NPU 加速资源",
        enableWindowDim = false,
        allowDismiss = !isDownloading,
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
                            dragProgress = (1f - (boxTop / sheetHeightPx).coerceIn(0f, 1f))
                        }
                    }
            )
        }

        val dismiss = LocalDismissState.current
        val indicationColor = MiuixTheme.colorScheme.onBackground
        val miuixIndication = remember(indicationColor) { MiuixIndication(color = indicationColor) }
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.LocalIndication provides miuixIndication
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                item {
                    MiuixText(
                        text = npuDownloadSummary(arch),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                    )
                }

                // 文件列表
                items(progress.size) { index ->
                    val item = progress[index]
                    NpuDownloadFileItem(
                        fileName = item.fileName,
                        progress = item.fraction,
                        status = item.status,
                        bytesRead = item.bytesRead,
                        totalBytes = item.totalBytes,
                    )
                    if (index < progress.size - 1) {
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // 如果还没开始下载,显示待下载文件列表
                if (progress.isEmpty()) {
                    item {
                        NpuPendingFileList(arch)
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MiuixButton(
                            onClick = { performHaptic(); dismiss?.invoke() },
                            modifier = Modifier.weight(1f),
                            colors = MiuixButtonDefaults.buttonColors(),
                            enabled = !isDownloading
                        ) {
                            MiuixText(if (isDownloading) "下载中..." else "取消")
                        }
                        MiuixButton(
                            onClick = { performHaptic(); onDownload() },
                            modifier = Modifier.weight(1f),
                            colors = MiuixButtonDefaults.buttonColorsPrimary(),
                            enabled = !isDownloading
                        ) {
                            MiuixText(if (isDownloading) "下载中..." else "开始下载")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NpuDownloadFileItem(
    fileName: String,
    progress: Float,
    status: NpuModelDownloader.FileProgress.Status,
    bytesRead: Long,
    totalBytes: Long,
) {
    MiuixCard(
        modifier = Modifier.fillMaxWidth(),
        colors = MiuixCardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiuixText(
                    text = fileName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                MiuixText(
                    text = when (status) {
                        NpuModelDownloader.FileProgress.Status.PENDING -> "等待中"
                        NpuModelDownloader.FileProgress.Status.DOWNLOADING -> "${(progress * 100).toInt()}%"
                        NpuModelDownloader.FileProgress.Status.DONE -> "完成"
                        NpuModelDownloader.FileProgress.Status.FAILED -> "失败"
                        NpuModelDownloader.FileProgress.Status.SKIPPED -> "已存在"
                    },
                    fontSize = 12.sp,
                    color = when (status) {
                        NpuModelDownloader.FileProgress.Status.DONE,
                        NpuModelDownloader.FileProgress.Status.SKIPPED -> MiuixTheme.colorScheme.primary
                        NpuModelDownloader.FileProgress.Status.FAILED -> MiuixTheme.colorScheme.error
                        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
            }

            if (status == NpuModelDownloader.FileProgress.Status.DOWNLOADING ||
                status == NpuModelDownloader.FileProgress.Status.DONE ||
                status == NpuModelDownloader.FileProgress.Status.SKIPPED) {
                MiuixLinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp)),
                    height = 6.dp,
                )
            }

            if (status == NpuModelDownloader.FileProgress.Status.DOWNLOADING && totalBytes > 0) {
                MiuixText(
                    text = "${formatBytes(bytesRead)} / ${formatBytes(totalBytes)}",
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun NpuPendingFileList(arch: Int) {
    val files = npuFileList(arch)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        files.forEach { (name, size) ->
            MiuixCard(
                modifier = Modifier.fillMaxWidth(),
                colors = MiuixCardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiuixText(
                        text = name,
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    MiuixText(
                        text = size,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
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
private fun Md3eNpuDownloadSheet(
    arch: Int,
    progress: List<NpuModelDownloader.FileProgress>,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    performHaptic: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    // 面板最长占屏幕 80%,超出部分交给文件列表自己滚动,避免窄屏/大字体下按钮被顶出屏。
    // 注意不要给根 Column 加 fillMaxHeight:那会把 sheet 撑到高度上限,未展开时也占掉大半屏。
    val sheetMaxHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.8f)
        .coerceAtLeast(320.dp)

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
                .heightIn(max = sheetMaxHeight)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "下载 NPU 加速资源",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = npuDownloadSummary(arch),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 文件列表:只有这一段可滚动,标题与按钮固定不动
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (progress.isNotEmpty()) {
                    progress.forEach { item ->
                        Md3eNpuDownloadFileItem(item)
                    }
                } else {
                    NpuPendingFileListMd3e(arch)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        performHaptic()
                        coroutineScope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(15.dp),
                    enabled = !isDownloading
                ) {
                    Text(if (isDownloading) "下载中..." else "取消")
                }
                Button(
                    onClick = { performHaptic(); onDownload() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(15.dp),
                    enabled = !isDownloading
                ) {
                    Text(if (isDownloading) "下载中..." else "开始下载")
                }
            }
        }
    }
}

@Composable
private fun Md3eNpuDownloadFileItem(item: NpuModelDownloader.FileProgress) {
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.fileName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = when (item.status) {
                        NpuModelDownloader.FileProgress.Status.PENDING -> "等待中"
                        NpuModelDownloader.FileProgress.Status.DOWNLOADING -> "${(item.fraction * 100).toInt()}%"
                        NpuModelDownloader.FileProgress.Status.DONE -> "完成"
                        NpuModelDownloader.FileProgress.Status.FAILED -> "失败"
                        NpuModelDownloader.FileProgress.Status.SKIPPED -> "已存在"
                    },
                    fontSize = 12.sp,
                    color = when (item.status) {
                        NpuModelDownloader.FileProgress.Status.DONE,
                        NpuModelDownloader.FileProgress.Status.SKIPPED -> MaterialTheme.colorScheme.primary
                        NpuModelDownloader.FileProgress.Status.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (item.status == NpuModelDownloader.FileProgress.Status.DOWNLOADING ||
                item.status == NpuModelDownloader.FileProgress.Status.DONE ||
                item.status == NpuModelDownloader.FileProgress.Status.SKIPPED) {
                if (item.fraction > 0) {
                    LinearWavyProgressIndicator(
                        progress = { item.fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                } else {
                    LinearWavyProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NpuPendingFileListMd3e(arch: Int) {
    val files = npuFileList(arch)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        files.forEach { (name, size) ->
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = size,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 架构专属文件大小未知时的占位符 —— 未知优于显示错误的 "0 B"。 */
private const val NPU_UNKNOWN_SIZE = "—"

/**
 * 待下载资源清单(文件名 -> 已格式化的大小文本),Miuix / MD3E 两个分支共用。
 *
 * 大小一律取自 [NpuLibLoader] 的真实字节数(与 [NpuModelDownloader] 的长度校验同一份数据),
 * 不再在 UI 里抄一份会漂移的字符串。
 *
 * [NpuLibLoader.archFileSizes] 返回 null(表中没有该架构,如未来的 V85)时,
 * 三个架构专属文件显示 [NPU_UNKNOWN_SIZE]:大小未知,总好过谎报 "0 B"。
 */
private fun npuFileList(arch: Int): List<Pair<String, String>> {
    val sizes = NpuLibLoader.archFileSizes(arch)
    return listOf(
        "libQnnHtp.so" to formatBytes(NpuLibLoader.HTP_LIB_SIZE),
        "libQnnSystem.so" to formatBytes(NpuLibLoader.SYSTEM_LIB_SIZE),
        "libQnnHtpV${arch}Stub.so" to (sizes?.stub?.let { formatBytes(it) } ?: NPU_UNKNOWN_SIZE),
        "libQnnHtpV${arch}Skel.so" to (sizes?.skel?.let { formatBytes(it) } ?: NPU_UNKNOWN_SIZE),
        "det_ctx.onnx" to (sizes?.detCtx?.let { formatBytes(it) } ?: NPU_UNKNOWN_SIZE),
    )
}

/**
 * 顶部说明文案,Miuix / MD3E 两个分支共用。
 *
 * 总和由同一套真实字节数算出(约 21.0~22.5 MB,随架构不同),不再写死 "约 25 MB"。
 * 大小未知(表中没有该架构)时不报数字,退回不带括号的文案。
 */
private fun npuDownloadSummary(arch: Int): String {
    val sizes = NpuLibLoader.archFileSizes(arch) ?: return "骁龙 NPU 加速需要下载以下资源"
    val total = NpuLibLoader.HTP_LIB_SIZE + NpuLibLoader.SYSTEM_LIB_SIZE +
        sizes.stub + sizes.skel + sizes.detCtx
    return "骁龙 NPU 加速需要下载以下资源(约 ${formatBytes(total)})"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
