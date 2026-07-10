package com.Badnng.moe.ui.screen.miuix

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.Badnng.moe.R
import com.Badnng.moe.service.CaptureTileService
import com.Badnng.moe.ui.screen.settings.SettingsPage
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixSettingsScreen(
    bottomLayoutInfo: MiuixHomeBottomLayoutInfo,
    onNavigateToSubPage: (SettingsPage) -> Unit = {}
) {
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "设置",
                color = MiuixTheme.colorScheme.surface,
                scrollBehavior = topAppBarScrollBehavior
            )
        }
    ) { innerPadding ->
        val lazyListState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = bottomLayoutInfo.pageContentBottomPadding
            )
        ) {
            item {
                SmallTitle(text = "常规")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = "偏好设置",
                        summary = "管理自行习惯的设置",
                        onClick = { onNavigateToSubPage(SettingsPage.Preference) }
                    )
                    ArrowPreference(
                        title = "权限与保活",
                        summary = "管理权限和防止系统清理后台",
                        onClick = { onNavigateToSubPage(SettingsPage.Permission) }
                    )
                    ArrowPreference(
                        title = "截图方式",
                        summary = "管理App截图的方式",
                        onClick = { onNavigateToSubPage(SettingsPage.Screenshot) }
                    )
                    ArrowPreference(
                        title = "清理空间",
                        summary = "管理App占用的缓存与截图空间",
                        onClick = { onNavigateToSubPage(SettingsPage.Storage) }
                    )
                    ArrowPreference(
                        title = "添加到控制中心",
                        summary = "将「截图识别」磁贴添加到控制中心快捷栏",
                        onClick = { requestAddTile(context) }
                    )
                }
            }

            item {
                SmallTitle(text = "其他")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = "关于",
                        summary = "应用信息与开源许可",
                        onClick = { onNavigateToSubPage(SettingsPage.About) }
                    )
                    ArrowPreference(
                        title = "赞助",
                        summary = "支持项目持续更新",
                        onClick = { onNavigateToSubPage(SettingsPage.Sponsor) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(lazyListState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            trackPadding = PaddingValues(top = innerPadding.calculateTopPadding(), bottom = bottomLayoutInfo.pageContentBottomPadding),
        )
        }
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
