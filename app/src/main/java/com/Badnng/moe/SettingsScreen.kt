package com.Badnng.moe.screens

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

enum class SettingsPage {
    Main, Preference, Permission, Screenshot, ControlCenter, About
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onSubPageStatusChange: (Boolean) -> Unit = {}
) {
    var currentPage by remember { mutableStateOf(SettingsPage.Main) }
    
    // 用于记录预测性返回的进度
    var backProgress by remember { mutableFloatStateOf(0f) }
    var isPredictiveBackInProgress by remember { mutableStateOf(false) }

    // 监听页面变化，通知父组件（用于隐藏底栏）
    LaunchedEffect(currentPage) {
        onSubPageStatusChange(currentPage != SettingsPage.Main)
    }

    // 适配预测性返回手势 (Android 14+)
    PredictiveBackHandler(enabled = currentPage != SettingsPage.Main) { backEvent: Flow<androidx.activity.BackEventCompat> ->
        isPredictiveBackInProgress = true
        try {
            backEvent.collect { event ->
                backProgress = event.progress
            }
            // 手势完成，切换状态
            currentPage = SettingsPage.Main
        } catch (e: CancellationException) {
            // 手势取消
        } finally {
            isPredictiveBackInProgress = false
            backProgress = 0f
        }
    }

    // 计算缩放和位移：直接使用 backProgress 以保证零延迟响应
    val currentScale = if (isPredictiveBackInProgress) 1f - (backProgress * 0.08f) else 1f
    val currentTranslationX = if (isPredictiveBackInProgress) backProgress * 100f else 0f
    // 圆角从 0 变到 32dp
    val currentCornerRadius = if (isPredictiveBackInProgress) (backProgress * 32).dp else 0.dp

    Box(modifier = modifier.fillMaxSize()) {
        // 1. 底层：主设置列表
        MainSettingsList(onNavigate = { currentPage = it })

        // 2. 顶层：二级页面覆盖层
        AnimatedVisibility(
            visible = currentPage != SettingsPage.Main,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut()
        ) {
            val title = when (currentPage) {
                SettingsPage.Preference -> "偏好设置"
                SettingsPage.Permission -> "权限设置"
                SettingsPage.Screenshot -> "截图方式"
                SettingsPage.ControlCenter -> "添加到控制中心"
                SettingsPage.About -> "关于"
                else -> ""
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = currentScale
                        scaleY = currentScale
                        translationX = currentTranslationX
                        // 应用动态圆角裁剪
                        shape = RoundedCornerShape(currentCornerRadius)
                        clip = true
                    }
                    // 在侧滑时添加一个非常微弱的边框，增强边缘感
                    .border(
                        width = if (isPredictiveBackInProgress) 1.dp else 0.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = backProgress),
                        shape = RoundedCornerShape(currentCornerRadius)
                    )
                    .background(MaterialTheme.colorScheme.background)
            ) {
                SubPage(title) { currentPage = SettingsPage.Main }
            }
        }
    }
}

@Composable
fun MainSettingsList(onNavigate: (SettingsPage) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "设置",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsListItem(title = "偏好设置", description = "管理自行习惯的设置", onClick = { onNavigate(SettingsPage.Preference) })
        SettingsListItem(title = "权限设置", description = "管理此App授予的权限", onClick = { onNavigate(SettingsPage.Permission) })
        SettingsListItem(title = "截图方式", description = "管理App截图的方式", onClick = { onNavigate(SettingsPage.Screenshot) })
        SettingsListItem(title = "添加到控制中心", description = null, onClick = { onNavigate(SettingsPage.ControlCenter) })
        SettingsListItem(title = "关于", description = null, onClick = { onNavigate(SettingsPage.About) })

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubPage(title: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "正在开发中...",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun SettingsListItem(title: String, description: String?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        ListItem(
            headlineContent = { Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
            supportingContent = if (description != null) {
                { Text(text = description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) }
            } else null,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}
