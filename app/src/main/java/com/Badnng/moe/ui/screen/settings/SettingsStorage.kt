package com.Badnng.moe.ui.screen.settings

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.ui.component.PreferenceSection
import com.Badnng.moe.ui.miuix.rememberMiuixStyle
import java.io.File
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.HorizontalDivider as MiuixHorizontalDivider
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun StorageSettingsContent(performHaptic: () -> Unit, prefs: android.content.SharedPreferences, topPadding: androidx.compose.ui.unit.Dp = 0.dp, scrollState: androidx.compose.foundation.ScrollState = androidx.compose.foundation.rememberScrollState()) {
    val context = LocalContext.current
    var appSize by remember { mutableLongStateOf(0L) }       // App 本身（APK + 代码）
    var totalSize by remember { mutableLongStateOf(0L) }     // 总占用
    var cacheSize by remember { mutableLongStateOf(0L) }     // 缓存
    var screenshotSize by remember { mutableLongStateOf(0L) } // 截图数据
    var downloadSize by remember { mutableLongStateOf(0L) }   // 下载文件

    @SuppressLint("NewApi")
    fun refreshSizes() {
        // 手动计算各部分大小
        // APK 大小
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        val apkFile = File(appInfo.sourceDir)
        appSize = if (apkFile.exists()) apkFile.length() else 0L

        // 数据目录大小
        val dataDir = context.filesDir.parentFile
        totalSize = appSize + getFolderSize(dataDir)

        // 缓存大小
        cacheSize = getFolderSize(context.cacheDir)

        screenshotSize = getFolderSize(File(context.filesDir, "screenshots"))
        downloadSize = getFolderSize(File(context.filesDir, "downloads"))
    }

    LaunchedEffect(Unit) { refreshSizes() }

    // 计算各部分比例：App本身、截图、缓存、下载、其他
    val otherSize = (totalSize - appSize - screenshotSize - cacheSize - downloadSize).coerceAtLeast(0L)
    val appRatio = if (totalSize > 0) appSize.toFloat() / totalSize else 0f
    val screenshotRatio = if (totalSize > 0) screenshotSize.toFloat() / totalSize else 0f
    val cacheRatio = if (totalSize > 0) cacheSize.toFloat() / totalSize else 0f
    val downloadRatio = if (totalSize > 0) downloadSize.toFloat() / totalSize else 0f

    // 使用同色系渐变色彩 - 更和谐
    val appColor = Color(0xFF6750A4)       // 深紫 - 应用
    val screenshotColor = Color(0xFF9A82DB) // 中紫 - 截图
    val downloadColor = Color(0xFFB39DDB)   // 浅紫 - 下载
    val cacheColor = Color(0xFFE8DEF8)      // 浅紫 - 缓存
    val otherColor = Color(0xFF79747E)      // 灰色 - 其他

    val isMiuix = rememberMiuixStyle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (isMiuix) 0.dp else 16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(topPadding))

        // 圆形进度条
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 32.dp.toPx()
                val gap = 2f  // 分段间隙
                var startAngle = -90f

                // 绘制各分段（按顺序：应用、截图、下载、缓存、其他）
                val segments = listOf(
                    appRatio to appColor,
                    screenshotRatio to screenshotColor,
                    downloadRatio to downloadColor,
                    cacheRatio to cacheColor,
                    (1f - appRatio - screenshotRatio - downloadRatio - cacheRatio) to otherColor
                )

                segments.forEach { (ratio, color) ->
                    if (ratio > 0.001f) {
                        val sweep = 360f * ratio - gap
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                        startAngle += 360f * ratio
                    }
                }
            }

            // 中心内容
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isMiuix) {
                    MiuixText(
                        text = formatFileSize(totalSize),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    MiuixText(
                        text = "总占用",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                } else {
                    Text(
                        text = formatFileSize(totalSize),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "总占用",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // 卡片式图例
        if (isMiuix) {
            MiuixCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StorageLegendRow(
                        color = appColor,
                        label = "应用",
                        size = formatFileSize(appSize),
                        description = "APK 安装包",
                        isMiuix = isMiuix
                    )
                    MiuixHorizontalDivider(color = MiuixTheme.colorScheme.outline.copy(alpha = 0.3f))
                    StorageLegendRow(
                        color = screenshotColor,
                        label = "截图",
                        size = formatFileSize(screenshotSize),
                        description = "识别截图",
                        isMiuix = isMiuix
                    )
                    MiuixHorizontalDivider(color = MiuixTheme.colorScheme.outline.copy(alpha = 0.3f))
                    StorageLegendRow(
                        color = downloadColor,
                        label = "下载",
                        size = formatFileSize(downloadSize),
                        description = "更新包等",
                        isMiuix = isMiuix
                    )
                    MiuixHorizontalDivider(color = MiuixTheme.colorScheme.outline.copy(alpha = 0.3f))
                    StorageLegendRow(
                        color = cacheColor,
                        label = "缓存",
                        size = formatFileSize(cacheSize),
                        description = "临时文件",
                        isMiuix = isMiuix
                    )
                    MiuixHorizontalDivider(color = MiuixTheme.colorScheme.outline.copy(alpha = 0.3f))
                    StorageLegendRow(
                        color = otherColor,
                        label = "其他",
                        size = formatFileSize(otherSize),
                        description = "数据库等",
                        isMiuix = isMiuix
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StorageLegendRow(
                        color = appColor,
                        label = "应用",
                        size = formatFileSize(appSize),
                        description = "APK 安装包",
                        isMiuix = isMiuix
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    StorageLegendRow(
                        color = screenshotColor,
                        label = "截图",
                        size = formatFileSize(screenshotSize),
                        description = "识别截图",
                        isMiuix = isMiuix
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    StorageLegendRow(
                        color = downloadColor,
                        label = "下载",
                        size = formatFileSize(downloadSize),
                        description = "更新包等",
                        isMiuix = isMiuix
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    StorageLegendRow(
                        color = cacheColor,
                        label = "缓存",
                        size = formatFileSize(cacheSize),
                        description = "临时文件",
                        isMiuix = isMiuix
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    StorageLegendRow(
                        color = otherColor,
                        label = "其他",
                        size = formatFileSize(otherSize),
                        description = "数据库等",
                        isMiuix = isMiuix
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        if (isMiuix) {
            Column {
                StorageActionCard(title = "清理系统缓存", description = "删除 App 运行产生的临时文件", size = formatFileSize(cacheSize), isMiuix = isMiuix, onClear = { performHaptic(); deleteFolderContents(context.cacheDir); refreshSizes() })
                StorageActionCard(title = "清理识别截图", description = "删除保存在本地的识别原始截图\n(不影响已生成的记录)", size = formatFileSize(screenshotSize), isMiuix = isMiuix, onClear = { performHaptic(); deleteFolderContents(File(context.filesDir, "screenshots")); refreshSizes() })
                StorageActionCard(title = "清理下载文件", description = "删除下载的更新包等文件", size = formatFileSize(downloadSize), isMiuix = isMiuix, onClear = { performHaptic(); deleteFolderContents(File(context.filesDir, "downloads")); refreshSizes() })
            }
        } else {
            PreferenceSection(title = "清理操作") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StorageActionCard(title = "清理系统缓存", description = "删除 App 运行产生的临时文件", size = formatFileSize(cacheSize), isMiuix = isMiuix, onClear = { performHaptic(); deleteFolderContents(context.cacheDir); refreshSizes() })
                    StorageActionCard(title = "清理识别截图", description = "删除保存在本地的识别原始截图\n(不影响已生成的记录)", size = formatFileSize(screenshotSize), isMiuix = isMiuix, onClear = { performHaptic(); deleteFolderContents(File(context.filesDir, "screenshots")); refreshSizes() })
                    StorageActionCard(title = "清理下载文件", description = "删除下载的更新包等文件", size = formatFileSize(downloadSize), isMiuix = isMiuix, onClear = { performHaptic(); deleteFolderContents(File(context.filesDir, "downloads")); refreshSizes() })
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun StorageLegendRow(color: Color, label: String, size: String, description: String, isMiuix: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                if (isMiuix) {
                    MiuixText(text = label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    MiuixText(
                        text = description,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f)
                    )
                } else {
                    Text(text = label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
        if (isMiuix) {
            MiuixText(
                text = size,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
        } else {
            Text(
                text = size,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun StorageActionCard(title: String, description: String, size: String, isMiuix: Boolean = false, onClear: () -> Unit) {
    if (isMiuix) {
        MiuixCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    MiuixText(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    MiuixText(text = description, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    MiuixText(text = "占用: $size", fontSize = 13.sp, color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                }
                MiuixButton(onClick = onClear, cornerRadius = 12.dp, colors = MiuixButtonDefaults.buttonColorsPrimary()) { MiuixText("立即清理") }
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(text = description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "占用: $size", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                }
                Button(onClick = onClear) { Text("立即清理") }
            }
        }
    }
}
