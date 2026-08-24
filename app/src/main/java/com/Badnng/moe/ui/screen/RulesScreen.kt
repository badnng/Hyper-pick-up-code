package com.Badnng.moe.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.Badnng.moe.ui.component.Md3eNavigationRailExpandButton
import com.Badnng.moe.ui.component.SimpleRuleCenterContent
import com.Badnng.moe.ui.component.SimpleRuleCenterPage
import com.Badnng.moe.ui.component.SimpleRuleCenterState
import com.Badnng.moe.ui.component.rememberSimpleRuleCenterState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    modifier: Modifier = Modifier,
    onExpandNavigationRail: (() -> Unit)? = null,
    onShowMenu: ((position: androidx.compose.ui.geometry.Offset, rename: (() -> Unit)?, delete: (() -> Unit)?, export: (() -> Unit)?) -> Unit)? = null,
    onDismissMenu: (() -> Unit)? = null,
) {
    val state = rememberSimpleRuleCenterState()
    // 标题由页面内容在同一份状态中决定；规则包加载前显示通用标题。
    val isSubPage = state.canGoBack
    BackHandler(enabled = isSubPage) { state.back() }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (isSubPage) state.title else "规则") },
                navigationIcon = {
                    if (isSubPage) {
                        IconButton(onClick = { state.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                    } else {
                        onExpandNavigationRail?.let { Md3eNavigationRailExpandButton(onClick = it) }
                    }
                },
            )
        },
    ) { innerPadding ->
        SimpleRuleCenterContent(
            state = state,
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            isMiuix = false,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
