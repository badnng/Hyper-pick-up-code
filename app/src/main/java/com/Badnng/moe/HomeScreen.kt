package com.Badnng.moe.screens

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Badnng.moe.OrderEntity
import com.Badnng.moe.OrderViewModel
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.lens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val viewModel: OrderViewModel = viewModel()

    // 1. 初始化 Backdrop
    val backgroundColor = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }

    var isSettingsSubPageOpen by remember { mutableStateOf(false) }

    // 关键：通过动画控制透明度，但不物理销毁，以支持预测性返回手势预览
    val uiAlpha by animateFloatAsState(
        targetValue = if (isSettingsSubPageOpen) 0f else 1f,
        label = "uiAlpha"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // 底栏放回 Scaffold 插槽，位置最稳，且通过 alpha 支持预览
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(uiAlpha)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(min = 200.dp, max = 240.dp)
                        .height(64.dp)
                        .border(
                            BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f)),
                            RoundedCornerShape(15.dp)
                        )
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(15.dp) },
                            effects = {
                                vibrancy()
                                blur(12.dp.toPx())
                                lens(16.dp.toPx(), 32.dp.toPx())
                            },
                            onDrawSurface = {
                                drawRect(Color.White.copy(alpha = 0.12f))
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        modifier = Modifier.fillMaxSize(),
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    ) {
                        val isHomeSelected = pagerState.currentPage == 0
                        val homeOffset by animateDpAsState(if (isHomeSelected) (-2).dp else 0.dp, label = "homeOffset")

                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Home, null, Modifier.offset(y = homeOffset)) },
                            label = { Text("识别", fontSize = 12.sp) },
                            selected = isHomeSelected,
                            onClick = { if (!isSettingsSubPageOpen) coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )

                        val isSettingsSelected = pagerState.currentPage == 1
                        val settingsOffset by animateDpAsState(if (isSettingsSelected) (-2).dp else 0.dp, label = "settingsOffset")

                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Settings, null, Modifier.offset(y = settingsOffset)) },
                            label = { Text("设置", fontSize = 12.sp) },
                            selected = isSettingsSelected,
                            onClick = { if (!isSettingsSubPageOpen) coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            // 加号按钮放回 Scaffold 官方槽位，位置绝对正确，不再偏移
            FloatingActionButton(
                onClick = { if (!isSettingsSubPageOpen) showBottomSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(15.dp),
                modifier = Modifier
                    .alpha(uiAlpha) // 支持同步预览
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp, end = 24.dp)
            ) {
                Icon(Icons.Default.Add, "添加", Modifier.size(32.dp))
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .consumeWindowInsets(innerPadding) // 正确处理内边距，解决警告
        ) {
            // 核心修复：仅捕获 Pager，防止循环渲染导致 SIGSEGV 崩溃
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = !isSettingsSubPageOpen
                ) { page ->
                    when (page) {
                        0 -> CaptureScreen(
                            modifier = Modifier.fillMaxSize(),
                            bottomPadding = 80.dp,
                            backdrop = backdrop
                        )
                        1 -> SettingsScreen(
                            modifier = Modifier.fillMaxSize(),
                            onSubPageStatusChange = { isSettingsSubPageOpen = it }
                        )
                    }
                }
            }

            if (showBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showBottomSheet = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = { BottomSheetDefaults.DragHandle() },
                    shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp)
                ) {
                    BottomSheetContent(viewModel) { showBottomSheet = false }
                }
            }
        }
    }
}

// 其余辅助函数保持不变...
@Composable
fun BottomSheetContent(viewModel: OrderViewModel, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.35f).padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("添加记录", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ActionButton(Icons.Default.PhotoCamera, "截图识别", onClick = { onDismiss() })
            var showManualInput by remember { mutableStateOf(false) }
            ActionButton(Icons.Default.TextFields, "手动输入", onClick = { showManualInput = true })
            if (showManualInput) {
                ManualInputDialog(onDismiss = { showManualInput = false }, onConfirm = { code ->
                    viewModel.addOrder(OrderEntity(takeoutCode = code, screenshotPath = "", recognizedText = "手动输入"))
                    showManualInput = false
                    onDismiss()
                })
            }
        }
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
        Surface(onClick = onClick, shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ManualInputDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("手动输入取餐码") }, text = { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("输入取餐码") }, singleLine = true) }, confirmButton = { Button(onClick = { onConfirm(text) }) { Text("添加") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}