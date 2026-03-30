package com.Badnng.moe.screens

import android.app.AppOpsManager
import android.app.StatusBarManager
import android.app.usage.StorageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import com.Badnng.moe.CaptureTileService
import com.Badnng.moe.NotificationHelper
import com.Badnng.moe.R
import com.Badnng.moe.UpdateHelper
import com.Badnng.moe.UpdateInfo
import com.Badnng.moe.UpdateDialog
import com.Badnng.moe.UpdateProgressDialog
import com.Badnng.moe.BackupHelper
import com.Badnng.moe.AccessibilityShortcutHelper
import com.Badnng.moe.OrderDatabase
import java.io.File

enum class SettingsPage {
    Main, Preference, Permission, Screenshot, KeepAlive, Storage, About
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onSubPageStatusChange: (Boolean) -> Unit = {}
) {
    var currentPage by remember { mutableStateOf(SettingsPage.Main) }
    var previousPage by remember { mutableStateOf(SettingsPage.Main) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var isPredictiveBackInProgress by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    val performHaptic = {
        if (prefs.getBoolean("haptic_enabled", true)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(currentPage) {
        onSubPageStatusChange(currentPage != SettingsPage.Main)
        if (currentPage != SettingsPage.Main) previousPage = currentPage
    }

    PredictiveBackHandler(enabled = currentPage != SettingsPage.Main) { backEvent: Flow<androidx.activity.BackEventCompat> ->
        isPredictiveBackInProgress = true
        try {
            backEvent.collect { event ->
                backProgress = event.progress
                backSwipeEdge = event.swipeEdge
            }
            performHaptic()
            currentPage = SettingsPage.Main
        } catch (e: CancellationException) {
            currentPage = previousPage
        } finally {
            isPredictiveBackInProgress = false
            backProgress = 0f
        }
    }

    val currentScale = if (isPredictiveBackInProgress) 1f - (backProgress * 0.08f) else 1f
    val currentTranslationX = if (isPredictiveBackInProgress) {
        val multiplier = if (backSwipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
        backProgress * 100f * multiplier
    } else 0f
    val currentCornerRadius = if (isPredictiveBackInProgress) (backProgress * 32).dp else 0.dp

    Box(modifier = modifier.fillMaxSize()) {
        MainSettingsList(onNavigate = { performHaptic(); currentPage = it })

        AnimatedVisibility(
            visible = currentPage != SettingsPage.Main,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut()
        ) {
            val displayPage = if (currentPage != SettingsPage.Main) currentPage else previousPage
            val title = when (displayPage) {
                SettingsPage.Preference -> "偏好设置"
                SettingsPage.Permission -> "权限与保活"
                SettingsPage.Screenshot -> "截图方式"
                SettingsPage.KeepAlive -> "保活设置"
                SettingsPage.Storage -> "清理空间"
                SettingsPage.About -> "关于"
                else -> ""
            }
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = currentScale; scaleY = currentScale; translationX = currentTranslationX; shape = RoundedCornerShape(currentCornerRadius); clip = true }.border(width = if (isPredictiveBackInProgress) 1.dp else 0.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = backProgress), shape = RoundedCornerShape(currentCornerRadius)).background(MaterialTheme.colorScheme.background)) {
                SubPage(title, displayPage, performHaptic) { performHaptic(); currentPage = SettingsPage.Main }
            }
        }
    }
}

@Composable
fun MainSettingsList(onNavigate: (SettingsPage) -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val performHaptic = {
        if (prefs.getBoolean("haptic_enabled", true)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Top)).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom))) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "设置", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(24.dp))
        SettingsListItem(title = "偏好设置", description = "管理自行习惯的设置", onClick = { onNavigate(SettingsPage.Preference) })
        SettingsListItem(title = "权限与保活", description = "管理权限和防止系统清理后台", onClick = { onNavigate(SettingsPage.Permission) })
        SettingsListItem(title = "截图方式", description = "管理App截图的方式", onClick = { onNavigate(SettingsPage.Screenshot) })

        SettingsListItem(
            title = "添加到控制中心",
            description = "将“截图识别”磁贴添加到控制中心快捷栏",
            onClick = { performHaptic(); requestAddTile(context) }
        )

        SettingsListItem(title = "清理空间", description = "管理App占用的缓存与截图空间", onClick = { onNavigate(SettingsPage.Storage) })
        SettingsListItem(title = "关于", description = "应用信息与开源许可", onClick = { onNavigate(SettingsPage.About) })
        Spacer(modifier = Modifier.height(100.dp))
    }
}

private fun requestAddTile(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val statusBarManager = context.getSystemService(Context.STATUS_BAR_SERVICE) as StatusBarManager
        statusBarManager.requestAddTileService(
            ComponentName(context, CaptureTileService::class.java),
            "截图识别",
            Icon.createWithResource(context, R.drawable.note),
            {}, {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubPage(title: String, page: SettingsPage, performHaptic: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Top))) {
        TopAppBar(title = { Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
        when (page) {
            SettingsPage.Screenshot -> ScreenshotSettingsContent(performHaptic)
            SettingsPage.Permission -> PermissionSettingsContent(performHaptic)
            SettingsPage.Preference -> PreferenceSettingsContent(performHaptic)
            SettingsPage.KeepAlive -> KeepAliveSettingsContent(performHaptic)
            SettingsPage.Storage -> StorageSettingsContent(performHaptic, prefs)
            SettingsPage.About -> AboutSettingsContent(performHaptic)
            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text = "正在开发中...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) }
        }
    }
}

@Composable
fun AboutSettingsContent(performHaptic: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val versionName = remember { getVersionName(context) }
    val versionCode = remember { getVersionCode(context) }

    var showUpdateDialog by remember { mutableStateOf(false) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }
    val pausedFlag = remember { AtomicBoolean(false) }
    var isChecking by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val notificationHelper = remember { NotificationHelper(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        Surface(
            modifier = Modifier.size(86.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.abouttopicon),
                    contentDescription = "Logo",
                    modifier = Modifier.size(66.dp)
                )
            }
        }


        Spacer(Modifier.height(16.dp))
        Text(text = "澎湃记", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(text = "版本 $versionName($versionCode)", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))

        Spacer(Modifier.height(24.dp))

        // 检查更新按钮
        val networkUpdateEnabled = prefs.getBoolean("network_update_enabled", false)
        val updateChannel = prefs.getString("update_channel", "stable") ?: "stable"

        if (networkUpdateEnabled) {
            Button(
                onClick = {
                    performHaptic()
                    isChecking = true
                    coroutineScope.launch {
                        val info = UpdateHelper.checkUpdate(updateChannel == "dev")
                        isChecking = false
                        if (info != null) {
                            val localVersion = UpdateHelper.getCurrentVersionCode(context)
                            android.util.Log.d("UpdateCheck", "本地版本号: $localVersion")
                            android.util.Log.d("UpdateCheck", "远程版本号: ${info.versionCode}")
                            android.util.Log.d("UpdateCheck", "版本比较: ${info.versionCode} > $localVersion = ${info.versionCode > localVersion}")

                            if (info.versionCode > localVersion) {
                                android.util.Log.d("UpdateCheck", "发现新版本")

                                // 检查是否正在下载
                                if (UpdateHelper.isDownloading) {
                                    android.util.Log.d("UpdateCheck", "正在下载中，显示下载进度弹窗")
                                    updateInfo = info
                                    showProgressDialog = true
                                }
                                // 检查是否已下载
                                else if (UpdateHelper.downloadedFile != null && UpdateHelper.downloadedFile!!.exists()) {
                                    android.util.Log.d("UpdateCheck", "已下载完成，直接安装")
                                    UpdateHelper.installUpdate(context, UpdateHelper.downloadedFile!!)
                                }
                                // 显示更新弹窗
                                else {
                                    android.util.Log.d("UpdateCheck", "显示更新弹窗")
                                    updateInfo = info
                                    showUpdateDialog = true
                                }
                            } else {
                                android.util.Log.d("UpdateCheck", "当前已是最新版本")
                                UpdateHelper.showNoUpdateToast(context)
                            }
                        } else {
                            android.util.Log.e("UpdateCheck", "获取更新信息失败")
                        }
                    }
                },
                shape = RoundedCornerShape(15.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !isChecking
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.Default.SystemUpdate, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isChecking) "检查中..." else "检查更新")
            }
        }

        Spacer(Modifier.height(48.dp))

        PreferenceSection(title = "致谢") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OssItem("Jetpack Compose", "现代化声明式 UI 框架", "https://developer.android.com/jetpack/compose", performHaptic)
                OssItem("Material Design 3", "Google 现代设计语言规范", "https://m3.material.io", performHaptic)
                OssItem("ML Kit", "Google 强大的设备端机器学习 SDK", "https://developers.google.com/ml-kit", performHaptic)
                OssItem("Shizuku", "利用系统 API 实现高级权限调用", "https://shizuku.rikka.app", performHaptic)
                OssItem("ZXing", "高效的二维码生成与处理库", "https://github.com/zxing/zxing", performHaptic)
                OssItem("Room", "官方高性能 SQLite 数据库封装", "https://developer.android.com/training/data-storage/room", performHaptic)
                OssItem("Coil", "现代化的 Android 图片加载库", "https://coil-kt.github.io/coil/", performHaptic)
                OssItem("Kyant Backdrop", "优雅的毛玻璃与层级模糊效果实现", "https://github.com/Kyant0/AndroidLiquidGlass", performHaptic)
                OssItem("Paddle Lite" , "使用深度识别算法在本地进行OCR识别" , "https://www.paddlepaddle.org.cn/paddle/paddlelite", performHaptic)
                OssItem("Paddle4Android" , "不需要学习原理即可一键在Android上引入OCR识别" , "https://github.com/equationl/paddleocr4android", performHaptic)
            }
        }

        Spacer(Modifier.height(48.dp))

        // 备份与恢复
        PreferenceSection(title = "备份与恢复") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 备份相关状态
                var isBackingUp by remember { mutableStateOf(false) }
                var isRestoring by remember { mutableStateOf(false) }
                var pendingBackupData by remember { mutableStateOf<ByteArray?>(null) }

                // 创建备份文件的launcher
                val createBackupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
                ) { uri ->
                    uri?.let {
                        coroutineScope.launch {
                            try {
                                pendingBackupData?.let { data ->
                                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                        outputStream.write(data)
                                    }
                                    android.widget.Toast.makeText(context, "备份成功！", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("Backup", "保存备份失败", e)
                                android.widget.Toast.makeText(context, "保存备份失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            } finally {
                                pendingBackupData = null
                            }
                        }
                    }
                }

                // 恢复备份的launcher
                val restoreBackupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        isRestoring = true
                        coroutineScope.launch {
                            try {
                                val backupData = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                    inputStream.readBytes()
                                } ?: throw Exception("无法读取备份文件")

                                val restoredData = BackupHelper.restoreBackup(context, backupData)

                                // 恢复设置
                                val editor = prefs.edit()
                                restoredData.settings.forEach { (key, value) ->
                                    when (value) {
                                        is Boolean -> editor.putBoolean(key, value)
                                        is String -> editor.putString(key, value)
                                        is Int -> editor.putInt(key, value)
                                        is Long -> editor.putLong(key, value)
                                        is Float -> editor.putFloat(key, value)
                                    }
                                }
                                editor.apply()

                                // 恢复订单数据
                                val database = OrderDatabase.getDatabase(context)
                                val orderDao = database.orderDao()
                                restoredData.orders.forEach { order ->
                                    val existingOrder = orderDao.getOrderById(order.id)
                                    if (existingOrder == null) {
                                        orderDao.insert(order)
                                    }
                                }

                                android.widget.Toast.makeText(context, "恢复成功！共恢复 ${restoredData.orders.size} 条取餐码", android.widget.Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                android.util.Log.e("Backup", "恢复备份失败", e)
                                android.widget.Toast.makeText(context, "恢复备份失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            } finally {
                                isRestoring = false
                            }
                        }
                    }
                }

                // 备份卡片
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "备份数据", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(text = "备份取餐码和设置到压缩包", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                performHaptic()
                                isBackingUp = true
                                coroutineScope.launch {
                                    try {
                                        // 获取订单数据
                                        val database = OrderDatabase.getDatabase(context)
                                        val orders = database.orderDao().getAllOrdersList()

                                        // 获取设置数据
                                        val settingsMap = mutableMapOf<String, Any?>()
                                        val allPrefs = prefs.all
                                        allPrefs.forEach { (key, value) ->
                                            settingsMap[key] = value
                                        }

                                        // 创建备份
                                        val backupData = BackupHelper.createBackup(context, orders, settingsMap)
                                        pendingBackupData = backupData

                                        // 使用ActivityResultLauncher保存文件
                                        val fileName = BackupHelper.generateBackupFileName()
                                        createBackupLauncher.launch(fileName)

                                    } catch (e: Exception) {
                                        android.util.Log.e("Backup", "备份失败", e)
                                        android.widget.Toast.makeText(context, "备份失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        isBackingUp = false
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isBackingUp
                        ) {
                            if (isBackingUp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("备份")
                            }
                        }
                    }
                }

                // 恢复卡片
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "恢复数据", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(text = "从备份文件恢复取餐码和设置", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                performHaptic()
                                restoreBackupLauncher.launch(arrayOf("*/*"))
                            },
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isRestoring
                        ) {
                            if (isRestoring) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("恢复")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(64.dp))
        val currentYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
        Text(
            text = "Made with ❤️ by Badnng and Vibe Codding\n© $currentYear 澎湃记",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(32.dp))
    }

    // 更新弹窗
    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo!!,
            onDismiss = { showUpdateDialog = false },
            onInstall = {
                showUpdateDialog = false
                showProgressDialog = true
                isPaused = false
                pausedFlag.set(false)
                coroutineScope.launch {
                    val file = UpdateHelper.downloadUpdate(
                        context = context,
                        updateInfo = updateInfo!!,
                        onProgress = {
                            downloadProgress = it
                            if (!showProgressDialog) {
                                notificationHelper.showUpdateDownloadNotification(
                                    versionName = updateInfo!!.versionName,
                                    progress = it,
                                    isPaused = pausedFlag.get()
                                )
                            }
                        },
                        isPaused = { pausedFlag.get() }
                    )
                    showProgressDialog = false
                    notificationHelper.cancelUpdateDownloadNotification()
                    if (file != null) {
                        UpdateHelper.installUpdate(context, file)
                    } else {
                        if (!UpdateHelper.consumePausedStop()) {
                            android.widget.Toast.makeText(context, "下载失败或更新包已失效", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }

    // 下载进度弹窗
    if (showProgressDialog) {
        UpdateProgressDialog(
            progress = downloadProgress,
            isPaused = isPaused,
            onPause = {
                isPaused = true
                pausedFlag.set(true)
                updateInfo?.let {
                    notificationHelper.showUpdateDownloadNotification(it.versionName, downloadProgress, true)
                }
            },
            onResume = {
                isPaused = false
                pausedFlag.set(false)
                updateInfo?.let {
                    notificationHelper.showUpdateDownloadNotification(it.versionName, downloadProgress, false)
                }
            },
            onCancel = {
                showProgressDialog = false
                updateInfo?.let {
                    notificationHelper.showUpdateDownloadNotification(it.versionName, downloadProgress, isPaused)
                }
            }
        )
    }
}

@Composable
fun OssItem(name: String, desc: String, url: String, performHaptic: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Surface(
        onClick = {
            performHaptic()
            uriHandler.openUri(url)
        },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(2.dp))
                Text(text = desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            }
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

private fun getVersionName(context: Context): String {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }
}

private fun getVersionCode(context: Context): Long {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    } catch (e: Exception) {
        0L
    }
}

@Composable
fun StorageSettingsContent(performHaptic: () -> Unit, prefs: android.content.SharedPreferences) {
    val context = LocalContext.current
    var appSize by remember { mutableLongStateOf(0L) }       // App 本身（APK + 代码）
    var totalSize by remember { mutableLongStateOf(0L) }     // 总占用
    var cacheSize by remember { mutableLongStateOf(0L) }     // 缓存
    var screenshotSize by remember { mutableLongStateOf(0L) } // 截图数据
    var downloadSize by remember { mutableLongStateOf(0L) }   // 下载文件

    fun refreshSizes() {
        // 使用 StorageStatsManager 获取完整存储统计
        try {
            val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val appInfo = packageInfo.applicationInfo ?: throw NullPointerException("ApplicationInfo is null")
            // 使用反射获取 myUserHandle（系统 API）
            val userHandle = UserHandle::class.java.getDeclaredMethod("myUserHandle").invoke(null) as UserHandle
            val storageStats = storageStatsManager.queryStatsForPackage(
                appInfo.storageUuid,
                context.packageName,
                userHandle
            )
            appSize = storageStats.appBytes  // APK + native libs
            totalSize = storageStats.appBytes + storageStats.dataBytes  // 总占用
            cacheSize = storageStats.cacheBytes
        } catch (e: Exception) {
            // 降级方案：手动计算各部分大小
            // APK 大小
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            val apkFile = File(appInfo.sourceDir)
            appSize = if (apkFile.exists()) apkFile.length() else 0L

            // 数据目录大小
            val dataDir = context.filesDir.parentFile
            totalSize = appSize + getFolderSize(dataDir)

            // 缓存大小
            cacheSize = getFolderSize(context.cacheDir)
        }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

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

        Spacer(Modifier.height(28.dp))

        // 卡片式图例
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
                    description = "APK 安装包"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                StorageLegendRow(
                    color = screenshotColor,
                    label = "截图",
                    size = formatFileSize(screenshotSize),
                    description = "识别截图"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                StorageLegendRow(
                    color = downloadColor,
                    label = "下载",
                    size = formatFileSize(downloadSize),
                    description = "更新包等"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                StorageLegendRow(
                    color = cacheColor,
                    label = "缓存",
                    size = formatFileSize(cacheSize),
                    description = "临时文件"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                StorageLegendRow(
                    color = otherColor,
                    label = "其他",
                    size = formatFileSize(otherSize),
                    description = "数据库等"
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        PreferenceSection(title = "清理操作") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StorageActionCard(title = "清理系统缓存", description = "删除 App 运行产生的临时文件", size = formatFileSize(cacheSize), onClear = { performHaptic(); deleteFolderContents(context.cacheDir); refreshSizes() })
                StorageActionCard(title = "清理识别截图", description = "删除保存在本地的识别原始截图 (不影响已生成的记录)", size = formatFileSize(screenshotSize), onClear = { performHaptic(); deleteFolderContents(File(context.filesDir, "screenshots")); refreshSizes() })
                StorageActionCard(title = "清理下载文件", description = "删除下载的更新包等文件", size = formatFileSize(downloadSize), onClear = { performHaptic(); deleteFolderContents(File(context.filesDir, "downloads")); refreshSizes() })
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun StorageLegendRow(color: Color, label: String, size: String, description: String) {
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
                Text(text = label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        Text(
            text = size,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun StorageActionCard(title: String, description: String, size: String, onClear: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(15.dp),
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
            Button(onClick = onClear, shape = RoundedCornerShape(12.dp)) { Text("立即清理") }
        }
    }
}

private fun getFolderSize(file: File?): Long {
    if (file == null || !file.exists()) return 0L
    if (file.isFile) return file.length()
    var size = 0L
    file.listFiles()?.forEach { size += getFolderSize(it) }
    return size
}

private fun deleteFolderContents(file: File) {
    file.listFiles()?.forEach {
        if (it.isDirectory) deleteFolderContents(it)
        it.delete()
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Composable
fun PreferenceSettingsContent(performHaptic: () -> Unit) {
    val context = LocalContext.current; val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var navAlignment by remember { mutableStateOf(prefs.getString("nav_alignment", "center") ?: "center") }
    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }
    var monetEnabled by remember { mutableStateOf(prefs.getBoolean("monet_enabled", true)) }
    var amoledPureBlack by remember { mutableStateOf(prefs.getBoolean("amoled_pure_black", false)) }
    var hapticEnabled by remember { mutableStateOf(prefs.getBoolean("haptic_enabled", true)) }
    var showOnboardingOnNextLaunch by remember { mutableStateOf(prefs.getBoolean("show_onboarding_on_next_launch", false)) }
    var customHue by remember { mutableFloatStateOf(260f) }
    var selectedColorInt by remember { mutableIntStateOf(prefs.getInt("theme_color", Color(0xFF6750A4).toArgb())) }
    var networkUpdateEnabled by remember { mutableStateOf(prefs.getBoolean("network_update_enabled", false)) }
    var updateChannel by remember { mutableStateOf(prefs.getString("update_channel", "stable") ?: "stable") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom)),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        PreferenceSection(title = "底栏位置") { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("left" to "靠左", "center" to "居中", "right" to "靠右").forEach { (key, label) -> ChoiceChip(label = label, selected = navAlignment == key, onClick = { performHaptic(); navAlignment = key; prefs.edit().putString("nav_alignment", key).apply() }, modifier = Modifier.weight(1f)) } } }

        PreferenceSection(title = "交互反馈") {
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            ) {
                PreferenceSwitchItem(
                    title = "震动反馈",
                    description = "开启后点击按钮、切换分类时会有触感反馈",
                    checked = hapticEnabled,
                    onCheckedChange = {
                        hapticEnabled = it
                        prefs.edit().putBoolean("haptic_enabled", it).apply()
                        performHaptic()
                    }
                )
            }
        }

        PreferenceSection(title = "外观设置") {
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            ) {
                PreferenceSwitchItem(
                    title = "莫奈取色 (Dynamic Color)",
                    description = "开启后主题色将跟随系统壁纸自动变化",
                    checked = monetEnabled,
                    onCheckedChange = {
                        performHaptic()
                        monetEnabled = it
                        prefs.edit().putBoolean("monet_enabled", it).apply()
                    }
                )
            }
        }
        AnimatedVisibility(visible = !monetEnabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) { PreferenceSection(title = "自定义主题色") { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Text("滑动调节色相", style = MaterialTheme.typography.bodySmall); Slider(value = customHue, onValueChange = { customHue = it }, valueRange = 0f..360f, modifier = Modifier.fillMaxWidth()); val previewColor = remember(customHue) { Color.hsv(customHue, 0.7f, 0.9f) }; Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(previewColor).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)); Button(onClick = { performHaptic(); selectedColorInt = previewColor.toArgb(); prefs.edit().putInt("theme_color", selectedColorInt).apply() }, shape = RoundedCornerShape(15.dp), modifier = Modifier.weight(1f).height(56.dp)) { Text("应用颜色") } } ; Text("MD3 建议色", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)); val md3Colors = listOf(0xFF6750A4, 0xFF006A60, 0xFF984061, 0xFF005AC1, 0xFF605D62, 0xFF3B6939); Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { md3Colors.forEach { colorLong -> val color = Color(colorLong); Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(color).border(width = if (selectedColorInt == color.toArgb()) 3.dp else 0.dp, color = MaterialTheme.colorScheme.primary, shape = CircleShape).clickable { performHaptic(); selectedColorInt = color.toArgb(); prefs.edit().putInt("theme_color", selectedColorInt).apply() }) } } } } }
        PreferenceSection(title = "显示模式") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("light" to "浅色", "dark" to "深色", "system" to "跟随系统").forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                performHaptic()
                                themeMode = key
                                prefs.edit().putString("theme_mode", key).apply()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = themeMode == key,
                            onClick = {
                                performHaptic()
                                themeMode = key
                                prefs.edit().putString("theme_mode", key).apply()
                            }
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(label, fontSize = 16.sp)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.padding(top = 8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                ) {
                    PreferenceSwitchItem(
                        title = "Amoled 纯黑深色",
                        description = "仅在深色模式生效，让背景和表面接近纯黑",
                        checked = amoledPureBlack,
                        onCheckedChange = {
                            performHaptic()
                            amoledPureBlack = it
                            prefs.edit().putBoolean("amoled_pure_black", it).apply()
                        }
                    )
                }
            }
        }

        PreferenceSection(title = "引导设置") {
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            ) {
                PreferenceSwitchItem(
                    title = "下次启动时打开引导页面",
                    description = "开启后，彻底停止App再启动会显示引导页面，完成引导后自动关闭",
                    checked = showOnboardingOnNextLaunch,
                    onCheckedChange = {
                        performHaptic()
                        showOnboardingOnNextLaunch = it
                        prefs.edit().putBoolean("show_onboarding_on_next_launch", it).apply()
                    }
                )
            }
        }

        PreferenceSection(title = "联网更新") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                ) {
                    PreferenceSwitchItem(
                        title = "联网更新",
                        description = "仅用于检测App新版本并下载，不用于其他用途",
                        checked = networkUpdateEnabled,
                        onCheckedChange = {
                            performHaptic()
                            networkUpdateEnabled = it
                            prefs.edit().putBoolean("network_update_enabled", it).apply()
                        }
                    )
                }

                AnimatedVisibility(
                    visible = networkUpdateEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "更新通道",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        CaptureModeItem(
                            title = "接收正式版更新",
                            description = "只接收稳定版本的更新",
                            selected = updateChannel == "stable",
                            onClick = {
                                performHaptic()
                                updateChannel = "stable"
                                prefs.edit().putString("update_channel", "stable").apply()
                            }
                        )
                        CaptureModeItem(
                            title = "接收测试版更新",
                            description = "接收所有版本的更新，包括测试版",
                            selected = updateChannel == "dev",
                            onClick = {
                                performHaptic()
                                updateChannel = "dev"
                                prefs.edit().putString("update_channel", "dev").apply()
                            }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun ScreenshotSettingsContent(performHaptic: () -> Unit) {
    val context = LocalContext.current; val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var captureMode by remember { mutableStateOf(prefs.getString("capture_mode", "media_projection") ?: "media_projection") }
    var volumeKeyShortcutEnabled by remember { mutableStateOf(prefs.getBoolean("volume_key_shortcut_enabled", false)) }
    var shizukuReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { shizukuReady = withContext(Dispatchers.IO) { isShizukuReady() }; if (!shizukuReady && captureMode == "shizuku") { captureMode = "media_projection"; prefs.edit().putString("capture_mode", "media_projection").apply() }; if (!shizukuReady && volumeKeyShortcutEnabled) { volumeKeyShortcutEnabled = false; prefs.edit().putBoolean("volume_key_shortcut_enabled", false).apply() }; delay(1500) } }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom)),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("选择截图技术方案", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        CaptureModeItem(title = "共享屏幕 (MediaProjection)", description = "默认方案，设备兼容性高，但每次使用磁贴需要屏幕共享授权确认", selected = captureMode == "media_projection", onClick = { performHaptic(); captureMode = "media_projection"; prefs.edit().putString("capture_mode", "media_projection").apply() })
        CaptureModeItem(title = "Shizuku 免授权", description = if (shizukuReady) "通过 Shizuku 后可实现免授权后台截图识别（推荐）" else "Shizuku 未就绪，此选项当前不可用。", selected = captureMode == "shizuku", enabled = shizukuReady, onClick = { if (shizukuReady) { performHaptic(); captureMode = "shizuku"; prefs.edit().putString("capture_mode", "shizuku").apply() } })
        Surface(
            onClick = {
                if (shizukuReady) {
                    performHaptic()
                    val targetEnabled = !volumeKeyShortcutEnabled
                    val success = if (targetEnabled) {
                        AccessibilityShortcutHelper.configureShortcutWithShizuku(context)
                    } else {
                        AccessibilityShortcutHelper.disableServiceWithShizuku(context)
                    }
                    if (success) {
                        volumeKeyShortcutEnabled = targetEnabled
                        prefs.edit().putBoolean("volume_key_shortcut_enabled", targetEnabled).apply()
                    }
                }
            },
            shape = RoundedCornerShape(15.dp),
            color = if (shizukuReady) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (shizukuReady) 0.65f else 0.35f)),
            enabled = shizukuReady
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "音量键快捷触发",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (shizukuReady) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = if (shizukuReady) "通过 Shizuku 一键配置无障碍快捷方式，启用后可使用音量键快捷触发截图识别" else "需要 Shizuku 已运行并授权后才可启用",
                        fontSize = 12.sp,
                        color = if (shizukuReady) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                Switch(
                    checked = volumeKeyShortcutEnabled,
                    onCheckedChange = null,
                    enabled = shizukuReady
                )
            }
        }
    }
}

@Composable
fun PermissionSettingsContent(performHaptic: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var hasNotificationPermission by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var hasUsageStatsPermission by remember { mutableStateOf(checkUsageStatsPermission(context)) }
    var shizukuReady by remember { mutableStateOf(false) }
    var keepAliveEnabled by remember { mutableStateOf(prefs.getBoolean("keep_alive_enabled", false)) }
    var isIgnoringBattery by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            hasNotificationPermission = NotificationManagerCompat.from(context).areNotificationsEnabled()
            hasUsageStatsPermission = checkUsageStatsPermission(context)
            shizukuReady = withContext(Dispatchers.IO) { isShizukuReady() }
            isIgnoringBattery = checkBatteryOptimization(context)
            delay(1500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom)),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        // 第一大类：权限设置
        PreferenceSection(title = "权限设置") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PermissionItem(
                    title = "通知权限",
                    description = "请授予该权限，该权限用于收取取餐码通知，如关闭/拒绝该权限将会无法收到此通知",
                    isGranted = hasNotificationPermission,
                    actionButton = if (!hasNotificationPermission) { {
                        Button(onClick = {
                            performHaptic()
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) }
                            context.startActivity(intent)
                        }, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Icon(Icons.Default.Build, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("去修复")
                        }
                    } } else null
                )

                PermissionItem(
                    title = "应用使用情况",
                    description = "此权限能更好的识别当前处在的app是哪个 brand，推荐授权！",
                    isGranted = hasUsageStatsPermission,
                    actionButton = if (!hasUsageStatsPermission) { {
                        Button(onClick = {
                            performHaptic()
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            try { context.startActivity(intent) } catch (e: Exception) { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                        }, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Icon(Icons.Default.Security, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("去授权")
                        }
                    } } else null
                )

                PermissionItem(
                    title = "Shizuku 运行状态",
                    description = "该软件用于免授权截图识别的必须条件，如无则无法使用免授权截图",
                    isGranted = shizukuReady,
                    actionButton = if (!shizukuReady) { {
                        Button(onClick = { performHaptic(); if (Shizuku.pingBinder()) { try { Shizuku.requestPermission(1001) } catch (e: Exception) {} } }, shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("如果Shizuku已运行请点我")
                        }
                    } } else null
                )
            }
        }

        // 第二大类：保活设置
        PreferenceSection(title = "保活设置") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 说明卡片
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "开启保活后，应用切到后台时会自动隐藏卡片并提示正在后台运行，防止系统清理导致通知失效。部分设备可能需要额外设置。",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            lineHeight = 18.sp
                        )
                    }
                }

                // 基础设置
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                ) {
                    PreferenceSwitchItem(
                        title = "启用保活",
                        description = "开启后切到后台时自动隐藏卡片并提示",
                        checked = keepAliveEnabled,
                        onCheckedChange = { enabled ->
                            performHaptic()
                            keepAliveEnabled = enabled
                            prefs.edit().putBoolean("keep_alive_enabled", enabled).apply()
                        }
                    )
                }

                // 电池优化
                PermissionItem(
                    title = "忽略电池优化",
                    description = if (isIgnoringBattery) "已加入电池优化白名单，应用不会被系统休眠策略限制"
                    else "加入电池优化白名单，防止系统休眠时清理应用",
                    isGranted = isIgnoringBattery,
                    actionButton = if (!isIgnoringBattery) {
                        {
                            Button(
                                onClick = {
                                    performHaptic()
                                    try {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    }
                                },
                                shape = RoundedCornerShape(15.dp),
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) {
                                Icon(Icons.Default.BatterySaver, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("去设置")
                            }
                        }
                    } else null
                )

                // 锁定后台
                Text(
                    text = "在最近任务界面锁定应用，防止被系统一键清理：",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "锁定方法",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "1. 打开最近任务界面（多任务键或手势上滑悬停）\n2. 找到澎湃记卡片\n3. 长按卡片后点击卡片上的锁图标/下滑卡片使其变为锁定状态",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "锁定后卡片会显示锁图标，不会被一键清理",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // 厂商后台管理
                Text(
                    text = "不同厂商有不同的后台管理策略，请根据你的设备品牌进行设置：",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                VendorKeepAliveItem(
                    vendor = "HyperOS",
                    steps = listOf("设置 → 应用设置 → 应用管理 → 澎湃记", "省电策略 → 无限制", "自启动 → 开启"),
                    performHaptic = performHaptic
                )

                VendorKeepAliveItem(
                    vendor = "ColorOS",
                    steps = listOf("设置 → 应用管理 → 澎湃记", "电池 → 后台冻结 → 关闭", "自启动 → 开启"),
                    performHaptic = performHaptic
                )

                VendorKeepAliveItem(
                    vendor = "OriginOS",
                    steps = listOf("设置 → 更多设置 → 权限管理 → 澎湃记", "自启动 → 开启", "后台弹出界面 → 允许"),
                    performHaptic = performHaptic
                )

                VendorKeepAliveItem(
                    vendor = "OneUI",
                    steps = listOf("设置 → 应用程序 → 澎湃记", "电池 → 不受限制"),
                    performHaptic = performHaptic
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun checkUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun checkBatteryOptimization(context: Context): Boolean {
    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
fun PreferenceSwitchItem(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val chipBorder = if (selected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    }
    Surface(onClick = onClick, shape = RoundedCornerShape(15.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), border = chipBorder, modifier = modifier) {
        Box(modifier = Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) { Text(label, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp) }
    }
}

@Composable
fun PreferenceSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        content()
    }
}

@Composable
fun PermissionItem(title: String, description: String, isGranted: Boolean, actionButton: @Composable (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Icon(imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel, contentDescription = null, tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFF44336), modifier = Modifier.size(28.dp))
            }
            Text(text = description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
            if (!isGranted) { Spacer(Modifier.height(4.dp)); actionButton?.invoke() }
        }
    }
}

@Composable
fun CaptureModeItem(title: String, description: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val modeBorder = if (selected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 0.7f else 0.35f))
    }
    Surface(onClick = { if (enabled) onClick() }, shape = RoundedCornerShape(15.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else if (!enabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), border = modeBorder, modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f)) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp)); Text(text = description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
            }
            RadioButton(selected = selected, onClick = { if (enabled) onClick() }, enabled = enabled)
        }
    }
}

@Composable
fun SettingsListItem(title: String, description: String?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(15.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        ListItem(headlineContent = { Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium) }, supportingContent = if (description != null) { { Text(text = description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) } } else null, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
    }
}

private fun isShizukuReady(): Boolean {
    return try { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (e: Exception) { false }
}

@Composable
fun KeepAliveSettingsContent(performHaptic: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var keepAliveEnabled by remember { mutableStateOf(prefs.getBoolean("keep_alive_enabled", false)) }
    var isIgnoringBattery by remember { mutableStateOf(false) }

    // 检测电池优化白名单状态
    fun checkBatteryOptimization() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    LaunchedEffect(Unit) {
        while (true) {
            checkBatteryOptimization()
            kotlinx.coroutines.delay(2000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom)),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        // 说明卡片
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "开启保活后，应用切到后台时会自动隐藏卡片并提示正在后台运行，防止系统清理导致通知失效。部分设备可能需要额外设置。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    lineHeight = 18.sp
                )
            }
        }

        PreferenceSection(title = "基础设置") {
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            ) {
                PreferenceSwitchItem(
                    title = "启用保活",
                    description = "开启后切到后台时自动隐藏卡片并提示",
                    checked = keepAliveEnabled,
                    onCheckedChange = { enabled ->
                        performHaptic()
                        keepAliveEnabled = enabled
                        prefs.edit().putBoolean("keep_alive_enabled", enabled).apply()
                    }
                )
            }
        }

        PreferenceSection(title = "电池优化") {
            PermissionItem(
                title = "忽略电池优化",
                description = if (isIgnoringBattery) "已加入电池优化白名单，应用不会被系统休眠策略限制"
                else "加入电池优化白名单，防止系统休眠时清理应用",
                isGranted = isIgnoringBattery,
                actionButton = if (!isIgnoringBattery) {
                    {
                        Button(
                            onClick = {
                                performHaptic()
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                }
                            },
                            shape = RoundedCornerShape(15.dp),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Icon(Icons.Default.BatterySaver, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("去设置")
                        }
                    }
                } else null
            )
        }

        PreferenceSection(title = "锁定后台") {
            Text(
                text = "在最近任务界面锁定应用，防止被系统一键清理：",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "锁定方法",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "1. 打开最近任务界面（多任务键或手势上滑悬停）\n2. 找到澎湃记卡片\n3. 长按卡片后点击卡片上的锁图标/下滑卡片使其变为锁定状态",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "锁定后卡片会显示锁图标，不会被一键清理",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        PreferenceSection(title = "厂商后台管理") {
            Text(
                text = "不同厂商有不同的后台管理策略，请根据你的设备品牌进行设置：",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            VendorKeepAliveItem(
                vendor = "HyperOS",
                steps = listOf("设置 → 应用设置 → 应用管理 → 澎湃记", "省电策略 → 无限制", "自启动 → 开启"),
                performHaptic = performHaptic
            )

            VendorKeepAliveItem(
                vendor = "ColorOS",
                steps = listOf("设置 → 应用管理 → 澎湃记", "电池 → 后台冻结 → 关闭", "自启动 → 开启"),
                performHaptic = performHaptic
            )

            VendorKeepAliveItem(
                vendor = "OriginOS",
                steps = listOf("设置 → 更多设置 → 权限管理 → 澎湃记", "自启动 → 开启", "后台弹出界面 → 允许"),
                performHaptic = performHaptic
            )

            VendorKeepAliveItem(
                vendor = "OneUI",
                steps = listOf("设置 → 应用程序 → 澎湃记", "电池 → 不受限制"),
                performHaptic = performHaptic
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun VendorKeepAliveItem(vendor: String, steps: List<String>, performHaptic: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        onClick = { performHaptic(); expanded = !expanded },
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = vendor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    steps.forEachIndexed { index, step ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "${index + 1}.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = step,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
