package com.Badnng.moe.ui.screen.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.helper.AppLogger
import com.Badnng.moe.helper.ScreenshotStorage
import com.Badnng.moe.helper.UpdateHelper
import com.Badnng.moe.ui.miuix.MiuixSettingsLazyColumn
import com.Badnng.moe.ui.miuix.rememberMiuixStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.HorizontalDivider as MiuixHorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val STORAGE_CONTENT_MAX_WIDTH_DP = 760

private enum class CleanupCategory(
    val title: String,
    val description: String,
    val icon: ImageVector,
) {
    Cache(
        title = "临时缓存",
        description = "运行缓存和临时处理文件，可自动重新生成",
        icon = Icons.Default.Cached,
    ),
    Screenshots(
        title = "识别截图",
        description = "识别时保留的原始图片，不影响已有订单",
        icon = Icons.Default.Image,
    ),
    Logs(
        title = "诊断日志",
        description = "运行、识别、更新和崩溃日志及其压缩包",
        icon = Icons.Default.Description,
    ),
    Downloads(
        title = "更新安装包",
        description = "已下载或未完成的应用更新文件",
        icon = Icons.Default.SystemUpdateAlt,
    ),
}

private data class StorageBucket(
    val size: Long = 0L,
    val fileCount: Int = 0,
)

private data class StorageSnapshot(
    val app: StorageBucket = StorageBucket(),
    val retainedData: StorageBucket = StorageBucket(),
    val cache: StorageBucket = StorageBucket(),
    val screenshots: StorageBucket = StorageBucket(),
    val logs: StorageBucket = StorageBucket(),
    val downloads: StorageBucket = StorageBucket(),
) {
    val cleanableSize: Long
        get() = cache.size + screenshots.size + logs.size + downloads.size

    val totalSize: Long
        get() = app.size + retainedData.size + cleanableSize

    fun bucket(category: CleanupCategory): StorageBucket = when (category) {
        CleanupCategory.Cache -> cache
        CleanupCategory.Screenshots -> screenshots
        CleanupCategory.Logs -> logs
        CleanupCategory.Downloads -> downloads
    }
}

private data class StorageClearResult(
    val success: Boolean,
    val message: String,
)

@Composable
@Suppress("UNUSED_PARAMETER")
fun StorageSettingsContent(
    performHaptic: () -> Unit,
    prefs: android.content.SharedPreferences,
    topPadding: Dp = 0.dp,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isMiuix = rememberMiuixStyle()
    var snapshot by remember { mutableStateOf(StorageSnapshot()) }
    var isLoading by remember { mutableStateOf(true) }
    var cleaningCategory by remember { mutableStateOf<CleanupCategory?>(null) }

    suspend fun refreshSnapshot() {
        snapshot = withContext(Dispatchers.IO) { readStorageSnapshot(context) }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        refreshSnapshot()
    }

    fun clearCategory(category: CleanupCategory) {
        if (cleaningCategory != null || snapshot.bucket(category).size == 0L) return
        performHaptic()
        cleaningCategory = category
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    clearStorageCategory(context, category)
                }
            }.getOrElse {
                StorageClearResult(false, "清理失败，请稍后重试")
            }
            runCatching { refreshSnapshot() }
            cleaningCategory = null
            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
        }
    }

    val overviewSection: @Composable () -> Unit = {
        StorageOverviewCard(
            snapshot = snapshot,
            isLoading = isLoading,
            isMiuix = isMiuix,
        )
    }
    val cleanupSection: @Composable () -> Unit = {
        StorageCleanupList(
            snapshot = snapshot,
            cleaningCategory = cleaningCategory,
            isMiuix = isMiuix,
            onClear = ::clearCategory,
        )
    }
    val retainedSection: @Composable () -> Unit = {
        StorageRetainedList(snapshot = snapshot, isMiuix = isMiuix)
    }
    val sections = listOf(overviewSection, cleanupSection, retainedSection)

    if (isMiuix) {
        MiuixSettingsLazyColumn(
            sections = sections,
            contentPadding = PaddingValues(top = topPadding, bottom = 24.dp),
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(topPadding))
            sections.forEach { it() }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StorageOverviewCard(
    snapshot: StorageSnapshot,
    isLoading: Boolean,
    isMiuix: Boolean,
) {
    val retainedColor = if (isMiuix) {
        MiuixTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val cacheColor = Color(0xFF4F8F6B)
    val screenshotColor = Color(0xFF4F7CAC)
    val logColor = Color(0xFFD08A36)
    val downloadColor = Color(0xFFB46A8A)
    val overviewContent: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    StorageText(
                        text = "总占用",
                        isMiuix = isMiuix,
                        secondary = true,
                        fontSize = 13.sp,
                    )
                    StorageText(
                        text = if (isLoading) "正在统计" else formatFileSize(snapshot.totalSize),
                        isMiuix = isMiuix,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    StorageText(
                        text = "可清理",
                        isMiuix = isMiuix,
                        secondary = true,
                        fontSize = 13.sp,
                    )
                    StorageText(
                        text = if (isLoading) "--" else formatFileSize(snapshot.cleanableSize),
                        isMiuix = isMiuix,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isMiuix) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            StorageBreakdownBar(
                totalSize = snapshot.totalSize,
                segments = listOf(
                    (snapshot.app.size + snapshot.retainedData.size) to retainedColor,
                    snapshot.cache.size to cacheColor,
                    snapshot.screenshots.size to screenshotColor,
                    snapshot.logs.size to logColor,
                    snapshot.downloads.size to downloadColor,
                ),
                trackColor = if (isMiuix) {
                    MiuixTheme.colorScheme.outline.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                },
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                StorageLegend(
                    label = "应用及数据",
                    color = retainedColor,
                    isMiuix = isMiuix,
                    modifier = Modifier.weight(1f),
                )
                StorageLegend(
                    label = "可清理内容",
                    color = cacheColor,
                    isMiuix = isMiuix,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    StorageCenteredContainer {
        if (isMiuix) {
            MiuixCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                overviewContent()
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                overviewContent()
            }
        }
    }
}

@Composable
private fun StorageBreakdownBar(
    totalSize: Long,
    segments: List<Pair<Long, Color>>,
    trackColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(trackColor),
    ) {
        if (totalSize > 0L) {
            segments.forEach { (size, color) ->
                if (size > 0L) {
                    Box(
                        modifier = Modifier
                            .weight((size.toDouble() / totalSize.toDouble()).toFloat().coerceAtLeast(0.002f))
                            .fillMaxSize()
                            .background(color),
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageLegend(
    label: String,
    color: Color,
    isMiuix: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        StorageText(
            text = label,
            isMiuix = isMiuix,
            secondary = true,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun StorageCleanupList(
    snapshot: StorageSnapshot,
    cleaningCategory: CleanupCategory?,
    isMiuix: Boolean,
    onClear: (CleanupCategory) -> Unit,
) {
    StorageSectionTitle("可清理内容", isMiuix)
    StorageCenteredContainer {
        if (isMiuix) {
            MiuixCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Column {
                    CleanupCategory.entries.forEachIndexed { index, category ->
                        StorageCleanupRow(
                            category = category,
                            bucket = snapshot.bucket(category),
                            isCleaning = cleaningCategory == category,
                            anotherCategoryIsCleaning = cleaningCategory != null && cleaningCategory != category,
                            isMiuix = true,
                            onClear = { onClear(category) },
                        )
                        if (index != CleanupCategory.entries.lastIndex) {
                            MiuixHorizontalDivider(
                                modifier = Modifier.padding(start = 76.dp),
                                color = MiuixTheme.colorScheme.outline.copy(alpha = 0.22f),
                            )
                        }
                    }
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column {
                    CleanupCategory.entries.forEachIndexed { index, category ->
                        StorageCleanupRow(
                            category = category,
                            bucket = snapshot.bucket(category),
                            isCleaning = cleaningCategory == category,
                            anotherCategoryIsCleaning = cleaningCategory != null && cleaningCategory != category,
                            isMiuix = false,
                            onClear = { onClear(category) },
                        )
                        if (index != CleanupCategory.entries.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 76.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageCleanupRow(
    category: CleanupCategory,
    bucket: StorageBucket,
    isCleaning: Boolean,
    anotherCategoryIsCleaning: Boolean,
    isMiuix: Boolean,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StorageIconContainer(
            icon = category.icon,
            isMiuix = isMiuix,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            StorageText(
                text = category.title,
                isMiuix = isMiuix,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            StorageText(
                text = category.description,
                isMiuix = isMiuix,
                secondary = true,
                fontSize = 12.sp,
            )
            StorageText(
                text = "${formatFileSize(bucket.size)} · ${bucket.fileCount} 个文件",
                isMiuix = isMiuix,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isMiuix) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        Spacer(Modifier.width(12.dp))
        when {
            isCleaning -> {
                if (isMiuix) {
                    MiuixIcon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = "正在清理${category.title}",
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = "正在清理${category.title}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            bucket.size == 0L -> StorageText(
                text = "无需清理",
                isMiuix = isMiuix,
                secondary = true,
                fontSize = 12.sp,
            )
            isMiuix -> MiuixButton(
                onClick = { if (!anotherCategoryIsCleaning) onClear() },
                cornerRadius = 15.dp,
                colors = MiuixButtonDefaults.buttonColors(),
            ) {
                MiuixIcon(
                    imageVector = MiuixIcons.Regular.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(6.dp))
                MiuixText("清理")
            }
            else -> FilledTonalButton(
                onClick = onClear,
                enabled = !anotherCategoryIsCleaning,
                shape = RoundedCornerShape(15.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("清理")
            }
        }
    }
}

@Composable
private fun StorageRetainedList(snapshot: StorageSnapshot, isMiuix: Boolean) {
    StorageSectionTitle("保留内容", isMiuix)
    StorageCenteredContainer {
        if (isMiuix) {
            MiuixCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Column {
                    StorageRetainedRow(
                        icon = Icons.Default.Android,
                        title = "应用本体",
                        description = "基础安装包与功能组件",
                        bucket = snapshot.app,
                        isMiuix = true,
                    )
                    MiuixHorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp),
                        color = MiuixTheme.colorScheme.outline.copy(alpha = 0.22f),
                    )
                    StorageRetainedRow(
                        icon = Icons.Default.Storage,
                        title = "应用数据",
                        description = "订单、设置、API 密钥、自定义图标和识别规则",
                        bucket = snapshot.retainedData,
                        isMiuix = true,
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column {
                    StorageRetainedRow(
                        icon = Icons.Default.Android,
                        title = "应用本体",
                        description = "基础安装包与功能组件",
                        bucket = snapshot.app,
                        isMiuix = false,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    )
                    StorageRetainedRow(
                        icon = Icons.Default.Storage,
                        title = "应用数据",
                        description = "订单、设置、API 密钥、自定义图标和识别规则",
                        bucket = snapshot.retainedData,
                        isMiuix = false,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun StorageRetainedRow(
    icon: ImageVector,
    title: String,
    description: String,
    bucket: StorageBucket,
    isMiuix: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StorageIconContainer(icon = icon, isMiuix = isMiuix)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            StorageText(
                text = title,
                isMiuix = isMiuix,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            StorageText(
                text = description,
                isMiuix = isMiuix,
                secondary = true,
                fontSize = 12.sp,
            )
        }
        StorageText(
            text = formatFileSize(bucket.size),
            isMiuix = isMiuix,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StorageIconContainer(icon: ImageVector, isMiuix: Boolean) {
    val background = if (isMiuix) {
        MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val foreground = if (isMiuix) {
        MiuixTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (isMiuix) {
            MiuixIcon(
                imageVector = icon,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(23.dp),
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(23.dp),
            )
        }
    }
}

@Composable
private fun StorageSectionTitle(text: String, isMiuix: Boolean) {
    StorageCenteredContainer {
        if (isMiuix) {
            MiuixText(
                text = text,
                modifier = Modifier.padding(start = 28.dp, top = 18.dp, bottom = 6.dp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        } else {
            Text(
                text = text,
                modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun StorageCenteredContainer(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = STORAGE_CONTENT_MAX_WIDTH_DP.dp)
                .fillMaxWidth(),
        ) {
            content()
        }
    }
}

@Composable
private fun StorageText(
    text: String,
    isMiuix: Boolean,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
) {
    if (isMiuix) {
        MiuixText(
            text = text,
            modifier = modifier,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = when {
                color != Color.Unspecified -> color
                secondary -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                else -> MiuixTheme.colorScheme.onSurface
            },
        )
    } else {
        Text(
            text = text,
            modifier = modifier,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = when {
                color != Color.Unspecified -> color
                secondary -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

private fun readStorageSnapshot(context: Context): StorageSnapshot {
    val appInfo = context.applicationInfo
    val apkFiles = buildList {
        add(File(appInfo.sourceDir))
        appInfo.splitSourceDirs?.forEach { add(File(it)) }
    }
    val app = StorageBucket(
        size = apkFiles.sumOf { getFolderSize(it) },
        fileCount = apkFiles.count { it.exists() },
    )

    val internalCache = context.cacheDir
    val externalCache = context.externalCacheDir
    val screenshotDir = File(context.filesDir, "screenshots")
    val logsDir = File(context.filesDir, "logs")
    val downloadsDir = File(context.filesDir, "downloads")

    val cache = bucketOf(internalCache, externalCache)
    val legacyScreenshots = bucketOf(screenshotDir)
    val publicScreenshots = ScreenshotStorage.readStats(context)
    val screenshots = StorageBucket(
        size = legacyScreenshots.size + publicScreenshots.size,
        fileCount = legacyScreenshots.fileCount + publicScreenshots.fileCount,
    )
    val logs = bucketOf(logsDir)
    val downloads = bucketOf(downloadsDir)
    val internalDataSize = getFolderSize(context.filesDir.parentFile)
    val internalDataFileCount = countFiles(context.filesDir.parentFile)
    val internalCacheFileCount = countFiles(internalCache)
    val retainedDataSize = (
        internalDataSize - getFolderSize(internalCache) - legacyScreenshots.size - logs.size - downloads.size
    ).coerceAtLeast(0L)
    val retainedDataFileCount = (
        internalDataFileCount - internalCacheFileCount - legacyScreenshots.fileCount -
            logs.fileCount - downloads.fileCount
    ).coerceAtLeast(0)

    return StorageSnapshot(
        app = app,
        retainedData = StorageBucket(
            size = retainedDataSize,
            fileCount = retainedDataFileCount,
        ),
        cache = cache,
        screenshots = screenshots,
        logs = logs,
        downloads = downloads,
    )
}

private fun bucketOf(vararg files: File?): StorageBucket = StorageBucket(
    size = files.sumOf { getFolderSize(it) },
    fileCount = files.sumOf { countFiles(it) },
)

private fun countFiles(file: File?): Int {
    if (file == null || !file.exists()) return 0
    if (file.isFile) return 1
    return file.listFiles()?.sumOf(::countFiles) ?: 0
}

private suspend fun clearStorageCategory(
    context: Context,
    category: CleanupCategory,
): StorageClearResult = when (category) {
    CleanupCategory.Cache -> {
        val success = clearDirectories(context.cacheDir, context.externalCacheDir)
        StorageClearResult(success, if (success) "临时缓存已清理" else "部分缓存无法清理")
    }
    CleanupCategory.Screenshots -> {
        val success = ScreenshotStorage.deleteAll(context) &&
            clearDirectories(File(context.filesDir, "screenshots"))
        StorageClearResult(success, if (success) "识别截图已清理" else "部分截图无法清理")
    }
    CleanupCategory.Logs -> {
        val success = AppLogger.clearLogs(context)
        StorageClearResult(success, if (success) "诊断日志已清理" else "部分日志无法清理")
    }
    CleanupCategory.Downloads -> {
        if (UpdateHelper.isDownloading) {
            StorageClearResult(false, "更新文件正在下载，暂时无法清理")
        } else {
            val success = clearDirectories(File(context.filesDir, "downloads"))
            StorageClearResult(success, if (success) "更新安装包已清理" else "部分更新文件无法清理")
        }
    }
}

private fun clearDirectories(vararg directories: File?): Boolean {
    var success = true
    directories.filterNotNull().forEach { directory ->
        directory.listFiles()?.forEach { child ->
            if (!child.deleteRecursively()) success = false
        }
    }
    return success
}
