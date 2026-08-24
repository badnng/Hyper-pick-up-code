package com.Badnng.moe.ui.screen.miuix

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.Badnng.moe.R
import com.Badnng.moe.service.CaptureTileService
import com.Badnng.moe.ui.miuix.MiuixBlurredBar
import com.Badnng.moe.ui.miuix.miuixScrollModifiers
import com.Badnng.moe.ui.miuix.rememberMiuixBackdrop
import com.Badnng.moe.ui.screen.settings.SettingsPage
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixSettingsScreen(
    bottomLayoutInfo: MiuixHomeBottomLayoutInfo,
    onExpandNavigationRail: (() -> Unit)? = null,
    onNavigateToSubPage: (SettingsPage) -> Unit = {},
) {
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberMiuixBackdrop()
    val blurEnabled = backdrop != null

    Scaffold(
        topBar = {
            MiuixBlurredBar(backdrop = backdrop, blurEnabled = blurEnabled, progressive = false) {
                TopAppBar(
                    title = "设置",
                    color = if (blurEnabled) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        onExpandNavigationRail?.let {
                            MiuixNavigationRailExpandButton(onClick = it)
                        }
                    },
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .miuixScrollModifiers(topAppBarScrollBehavior),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = bottomLayoutInfo.pageContentBottomPadding
            )
        ) {
            item {
                SmallTitle(text = "常规")
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
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
                        title = "识别方式",
                        summary = "选择离线或在线多模态识别",
                        onClick = { onNavigateToSubPage(SettingsPage.Recognition) }
                    )
                    ArrowPreference(
                        title = "手表同步",
                        summary = "未完成取餐码同步到小米手表",
                        onClick = { onNavigateToSubPage(SettingsPage.WearableSync) }
                    )
                    ArrowPreference(
                        title = "清理空间",
                        summary = "管理缓存、截图、日志与更新文件",
                        onClick = { onNavigateToSubPage(SettingsPage.Storage) }
                    )
                    ArrowPreference(
                        title = "添加到控制中心",
                        summary = "将「截图识别」磁贴添加到控制中心快捷栏",
                        onClick = { requestAddTile(context) }
                    )
                }
            }
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
