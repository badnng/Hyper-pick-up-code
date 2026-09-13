package com.Badnng.moe.ui.screen.miuix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.ui.miuix.MiuixBlurredBar
import com.Badnng.moe.ui.miuix.miuixScrollModifiers
import com.Badnng.moe.ui.miuix.rememberMiuixBackdrop
import com.Badnng.moe.ui.screen.OrderDetailHost
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixOrderDetailScreen(
    order: OrderEntity,
    onBack: () -> Unit,
    supportingPane: Boolean = false,
) {
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberMiuixBackdrop()
    val blurEnabled = backdrop != null

    Scaffold(
        topBar = {
            MiuixBlurredBar(
                backdrop = backdrop,
                blurEnabled = blurEnabled,
                blurRadius = 42f,
                blendAlpha = 0.62f,
            ) {
                TopAppBar(
                    title = "识别详情",
                    color = if (blurEnabled) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = if (supportingPane) {
                                    MiuixIcons.Regular.Close
                                } else {
                                    MiuixIcons.Regular.Back
                                },
                                contentDescription = if (supportingPane) "关闭" else "返回",
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        val navigationBarPadding = WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()
        Box(
            modifier = if (backdrop != null) {
                Modifier.fillMaxSize().layerBackdrop(backdrop)
            } else {
                Modifier.fillMaxSize()
            },
        ) {
            OrderDetailHost(
                order = order,
                bottomSpacing = navigationBarPadding + if (supportingPane) 24.dp else 32.dp,
                // 顶栏高度交给列表的 contentPadding，不能写成 Modifier.padding(top = ...)：
                // 后者会把列表视口压到顶栏下沿，内容滑到那里就被裁掉、永远进不到顶栏下面，
                // 顶栏那层 ProgressiveBlur 也就一直是空的（用户报的「往上滑没有被模糊」）。
                topSpacing = innerPadding.calculateTopPadding(),
                modifier = Modifier
                    .fillMaxSize()
                    .miuixScrollModifiers(topAppBarScrollBehavior),
            )
        }
    }
}
