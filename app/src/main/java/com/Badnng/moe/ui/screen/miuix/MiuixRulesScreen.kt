package com.Badnng.moe.ui.screen.miuix

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.Badnng.moe.ui.component.SimpleRuleCenterContent
import com.Badnng.moe.ui.component.SimpleRuleCenterPage
import com.Badnng.moe.ui.component.rememberSimpleRuleCenterState
import com.Badnng.moe.ui.miuix.MiuixBlurredBar
import com.Badnng.moe.ui.miuix.miuixScrollModifiers
import com.Badnng.moe.ui.miuix.rememberMiuixBackdrop
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
fun MiuixRulesScreen(
    bottomLayoutInfo: MiuixHomeBottomLayoutInfo,
    onExpandNavigationRail: (() -> Unit)? = null,
    onNavigateToSubPage: (SimpleRuleCenterPage) -> Unit = {},
    onShowMenu: ((position: androidx.compose.ui.geometry.Offset, rename: (() -> Unit)?, delete: (() -> Unit)?, export: (() -> Unit)?) -> Unit)? = null,
    onModalVisibilityChange: (Boolean) -> Unit = {},
) {
    val state = rememberSimpleRuleCenterState()
    val backdrop = rememberMiuixBackdrop()
    val scrollBehavior = MiuixScrollBehavior()

    DisposableEffect(Unit) {
        onModalVisibilityChange(false)
        onDispose { onModalVisibilityChange(false) }
    }

    Scaffold(
        topBar = {
            MiuixBlurredBar(backdrop = backdrop, blurEnabled = backdrop != null, progressive = false) {
                TopAppBar(
                    title = "规则",
                    color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        onExpandNavigationRail?.let { MiuixNavigationRailExpandButton(onClick = it) }
                    },
                )
            }
        },
    ) { innerPadding ->
        SimpleRuleCenterContent(
            state = state,
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = bottomLayoutInfo.pageContentBottomPadding,
            ),
            isMiuix = true,
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .miuixScrollModifiers(scrollBehavior),
            onOpenPage = onNavigateToSubPage,
        )
    }
}

@Composable
fun MiuixRuleSubPageScreen(
    page: SimpleRuleCenterPage,
    supportingPane: Boolean = false,
    onBack: () -> Unit,
    onNavigate: (SimpleRuleCenterPage) -> Unit,
    onReplace: (SimpleRuleCenterPage) -> Unit,
) {
    val state = rememberSimpleRuleCenterState(page)
    val backdrop = rememberMiuixBackdrop()
    val scrollBehavior = MiuixScrollBehavior()
    val blurEnabled = backdrop != null

    Scaffold(
        topBar = {
            MiuixBlurredBar(backdrop = backdrop, blurEnabled = blurEnabled) {
                TopAppBar(
                    title = state.title,
                    color = if (blurEnabled) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = if (supportingPane) MiuixIcons.Regular.Close else MiuixIcons.Regular.Back,
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
        SimpleRuleCenterContent(
            state = state,
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = navigationBarPadding + 24.dp,
            ),
            isMiuix = true,
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .miuixScrollModifiers(scrollBehavior),
            onOpenPage = onNavigate,
            onReplacePage = onReplace,
            onBackPage = onBack,
        )
    }
}