package com.Badnng.moe.ui.screen.miuix

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.blur.layerBackdrop
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.Badnng.moe.R
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.ui.miuix.miuixScrollModifiers
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

@Composable
fun MiuixOrderDetailScreen(
    order: OrderEntity,
    onBack: () -> Unit,
    supportingPane: Boolean = false,
) {
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    var showFullScreen by remember { mutableStateOf(false) }
    val backdrop = com.Badnng.moe.ui.miuix.rememberMiuixBackdrop()
    val blurEnabled = backdrop != null

    Scaffold(
        topBar = {
            val topBarColor = if (blurEnabled) Color.Transparent else MiuixTheme.colorScheme.surface
            com.Badnng.moe.ui.miuix.MiuixBlurredBar(backdrop = backdrop, blurEnabled = blurEnabled) {
                TopAppBar(
                    title = "识别详情",
                    color = topBarColor,
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                if (supportingPane) {
                                    MiuixIcons.Regular.Close
                                } else {
                                    MiuixIcons.Regular.Back
                                },
                                contentDescription = if (supportingPane) "关闭" else "返回",
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .miuixScrollModifiers(topAppBarScrollBehavior)
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // 基本信息
            SmallTitle(text = "基本信息")
            Card(
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                colors = CardDefaults.defaultColors(MiuixTheme.colorScheme.surfaceContainer)
            ) {
                BasicComponent(
                    title = "来源应用",
                    summary = order.sourceApp ?: "无数据"
                )
                BasicComponent(
                    title = "来源包名",
                    summary = order.sourcePackage ?: "暂无记录"
                )
                BasicComponent(
                    title = "识别类型",
                    summary = order.orderType
                )
                if (!order.brandName.isNullOrEmpty()) {
                    BasicComponent(
                        title = "品牌",
                        summary = order.brandName
                    )
                }
            }
            // 原文记录
            SmallTitle(text = "原文记录")
            Box(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                MiuixFullTextCodeBlock(text = order.fullText)
            }

            // 截图副本
            SmallTitle(text = "截图副本")
            Card(
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                colors = CardDefaults.defaultColors(MiuixTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!order.screenshotPath.isNullOrEmpty() && File(order.screenshotPath).exists()) {
                        AsyncImage(
                            model = File(order.screenshotPath),
                            contentDescription = "原图",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp)
                                .squircleClip(12.dp)
                                .clickable { showFullScreen = true },
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "点击截图可查看全图",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.note),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp).alpha(0.2f),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "暂无图片数据",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
            Spacer(modifier = Modifier.height(32.dp))
        }
        }
    }

    if (showFullScreen && !order.screenshotPath.isNullOrEmpty()) {
        MiuixFullScreenImageDialog(imagePath = order.screenshotPath) {
            showFullScreen = false
        }
    }
}

@Composable
private fun MiuixFullTextCodeBlock(text: String?) {
    var expanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Card(
        colors = CardDefaults.defaultColors(MiuixTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "LOG_CONTENT",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Row {
                    IconButton(onClick = { text?.let { clipboardManager.setText(AnnotatedString(it)) } }, modifier = Modifier.size(32.dp)) {
                        Icon(MiuixIcons.Regular.Copy, null, Modifier.size(16.dp))
                    }
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (expanded) MiuixIcons.Regular.ExpandLess else MiuixIcons.Regular.ExpandMore,
                            null,
                            Modifier.size(20.dp)
                        )
                    }
                }
            }

            SelectionContainer {
                Text(
                    text = text ?: "旧版数据或未记录",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp).animateContentSize()
                )
            }
        }
    }
}

@Composable
private fun MiuixFullScreenImageDialog(imagePath: String, onDismiss: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.parent as? DialogWindowProvider)?.window
        window?.let {
            val insetsController = WindowCompat.getInsetsController(it, view)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.let {
                val insetsController = WindowCompat.getInsetsController(it, view)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = File(imagePath),
                contentDescription = "全图",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
