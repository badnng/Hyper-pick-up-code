package com.Badnng.moe.ui.screen.miuix

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.data.db.OrderGroup
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixGroupDetailScreen(
    group: OrderGroup,
    orders: List<OrderEntity>,
    completedCount: Int,
    totalCount: Int,
    onBack: () -> Unit,
    onMarkAllCompleted: () -> Unit
) {
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = group.name,
                color = MiuixTheme.colorScheme.surface,
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Regular.Back, contentDescription = "返回")
                    }
                },
                actions = {
                    if (completedCount < totalCount) {
                        IconButton(onClick = onMarkAllCompleted) {
                            Icon(MiuixIcons.Regular.Ok, "全部完成")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            SmallTitle(text = "订单列表")
            Card {
                orders.forEach { order ->
                    ArrowPreference(
                        title = order.orderType,
                        summary = order.takeoutCode,
                        onClick = { }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
