package com.Badnng.moe.ui.screen.miuix

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.state.ToggleableState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.data.db.OrderGroup
import com.Badnng.moe.helper.BrandIconResolver
import com.Badnng.moe.ui.miuix.miuixScrollModifiers
import com.Badnng.moe.ui.screen.openTaobaoIdentityEntry
import com.Badnng.moe.ui.screen.openPddIdentityEntry
import com.Badnng.moe.viewmodel.OrderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Alarm
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MiuixCaptureScreen(
    bottomLayoutInfo: MiuixHomeBottomLayoutInfo,
    onExpandNavigationRail: (() -> Unit)? = null,
    onScrollStateChange: (Boolean) -> Unit = {},
    onEditModeChange: (Boolean) -> Unit = {},
    onAddClick: () -> Unit = {},
    navAlignment: String = "center",
    useFloatingNavBar: Boolean = false,
    onNavigateToOrderDetail: (String) -> Unit = {},
    onNavigateToGroupDetail: (Long) -> Unit = {}
) {
    val viewModel: OrderViewModel = viewModel()
    val incompleteOrders by viewModel.incompleteOrders.collectAsStateWithLifecycle()
    val completedOrders by viewModel.completedOrders.collectAsStateWithLifecycle()
    val incompleteGroups by viewModel.incompleteGroups.collectAsStateWithLifecycle()
    val completedGroups by viewModel.completedGroups.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val hapticEnabled = remember(prefs) { prefs.getBoolean("haptic_enabled", true) }

    val performHaptic = {
        if (hapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // Tab 状态: 0=待取, 1=已取
    var selectedTab by remember { mutableIntStateOf(0) }

    // 分类筛选（待取 tab 点击展开子 TabRow）
    var showCategoryTabs by remember { mutableStateOf(false) }
    val categoryTabs = listOf("全部", "餐食", "饮品", "快递")
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val selectedCategory = categoryTabs[selectedCategoryIndex]

    // 过滤后的数据
    val filteredIncompleteGroups = remember(incompleteGroups, selectedCategory) {
        if (selectedCategory == "全部") incompleteGroups
        else incompleteGroups.filter { it.orderType == selectedCategory }
    }
    val filteredIncompleteOrders = remember(incompleteOrders, selectedCategory) {
        if (selectedCategory == "全部") incompleteOrders
        else incompleteOrders.filter { it.orderType == selectedCategory }
    }
    val standaloneCompletedOrders = remember(completedOrders) {
        completedOrders.filter { it.groupId == null }
    }

    // 多选模式
    var isEditMode by remember { mutableStateOf(false) }
    LaunchedEffect(isEditMode) {
        onEditModeChange(isEditMode)
    }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var selectedGroupIds by remember { mutableStateOf(setOf<Long>()) }

    val currentSelectableOrders = if (selectedTab == 0) {
        filteredIncompleteOrders.filter { it.groupId == null }
    } else {
        standaloneCompletedOrders
    }
    val currentSelectableGroups = if (selectedTab == 0) {
        filteredIncompleteGroups
    } else {
        completedGroups
    }
    val currentOrderIds = currentSelectableOrders.mapTo(mutableSetOf()) { it.id }
    val currentGroupIds = currentSelectableGroups.mapTo(mutableSetOf()) { it.id }
    val selectedCurrentOrderCount = currentOrderIds.count { it in selectedIds }
    val selectedCurrentGroupCount = currentGroupIds.count { it in selectedGroupIds }
    val hasCurrentSelectableItems = currentOrderIds.isNotEmpty() || currentGroupIds.isNotEmpty()
    val hasSelection = selectedIds.isNotEmpty() || selectedGroupIds.isNotEmpty()

    val density = LocalDensity.current
    var editActionToolbarHeightPx by remember { mutableIntStateOf(0) }
    var editSelectionToolbarHeightPx by remember { mutableIntStateOf(0) }
    val editActionToolbarHeight = if (editActionToolbarHeightPx > 0) {
        with(density) { editActionToolbarHeightPx.toDp() }
    } else {
        MiuixHomeBottomLayoutDefaults.EstimatedEditToolbarHeight
    }
    val editSelectionToolbarHeight = if (editSelectionToolbarHeightPx > 0) {
        with(density) { editSelectionToolbarHeightPx.toDp() }
    } else {
        MiuixHomeBottomLayoutDefaults.EstimatedEditToolbarHeight
    }
    val editActionBottomPadding = bottomLayoutInfo.editActionBottomPadding
    val editSelectionBottomPadding by animateDpAsState(
        targetValue = editActionBottomPadding + if (hasSelection) {
            editActionToolbarHeight + MiuixHomeBottomLayoutDefaults.EditToolbarGap
        } else {
            0.dp
        },
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 320f),
        label = "editSelectionToolbarBottomPadding",
    )
    val editContentBottomPadding = editSelectionBottomPadding +
        (if (hasCurrentSelectableItems) editSelectionToolbarHeight else 0.dp) +
        MiuixHomeBottomLayoutDefaults.ContentSpacing
    val pageContentBottomPadding = if (isEditMode) {
        editContentBottomPadding
    } else {
        bottomLayoutInfo.pageContentBottomPadding
    }

    // 监听广播刷新
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {}
        }
        val filter = IntentFilter("com.Badnng.moe.REFRESH_ORDERS")
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior()

    // 顶栏采样层统一受 Miuix 视觉性能策略控制。
    val backdrop = com.Badnng.moe.ui.miuix.rememberMiuixBackdrop()
    val blurEnabled = backdrop != null
    val lazyListState = rememberLazyListState()
    val isListScrolling by remember {
        derivedStateOf { lazyListState.isScrollInProgress }
    }
    LaunchedEffect(isListScrolling) {
        onScrollStateChange(isListScrolling)
    }
    DisposableEffect(Unit) {
        onDispose { onScrollStateChange(false) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            val topBarColor = if (blurEnabled) Color.Transparent else MiuixTheme.colorScheme.surface
            com.Badnng.moe.ui.miuix.MiuixBlurredBar(backdrop = backdrop, blurEnabled = blurEnabled) {
                Column {
                    TopAppBar(
                        title = "澎湃记",
                        color = topBarColor,
                        scrollBehavior = topAppBarScrollBehavior,
                        navigationIcon = {
                            onExpandNavigationRail?.let {
                                MiuixNavigationRailExpandButton(onClick = it)
                            }
                        },
                        actions = {
                            // 多选模式切换按钮
                            IconButton(onClick = {
                                performHaptic()
                                isEditMode = !isEditMode
                                if (!isEditMode) {
                                    selectedIds = emptySet()
                                    selectedGroupIds = emptySet()
                                }
                            }) {
                                Icon(
                                    if (isEditMode) MiuixIcons.Regular.Close else MiuixIcons.Regular.ListView,
                                    contentDescription = "管理",
                                    tint = MiuixTheme.colorScheme.onSurface
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                // 使用带轮廓样式，避免 Miuix 0.9.3 默认 TabRow 的直接切换行为。
                TabRowWithContour(
                    tabs = listOf("待取", "已取"),
                    selectedTabIndex = selectedTab,
                    onTabSelected = { tabIndex ->
                        performHaptic()
                        if (tabIndex == 0 && selectedTab == 0) {
                            showCategoryTabs = !showCategoryTabs
                        } else {
                            selectedTab = tabIndex
                            if (tabIndex == 1) showCategoryTabs = false
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                // 分类子 TabRow（Content of tab，使用 TabRowWithContour 样式）
                AnimatedVisibility(
                    visible = showCategoryTabs && selectedTab == 0,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    TabRowWithContour(
                        tabs = categoryTabs,
                        selectedTabIndex = selectedCategoryIndex,
                        onTabSelected = {
                            performHaptic()
                            selectedCategoryIndex = it
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // 底部渐变遮罩，让 tab 区域和内容过渡更自然
                val gradientStartColor = if (blurEnabled) Color.Transparent else MiuixTheme.colorScheme.surface
                val gradientEndColor = Color.Transparent
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(gradientStartColor, gradientEndColor)
                            )
                        )
                )
            }
            }
        },
    ) { innerPadding ->
        // 将 backdrop 应用到内容区域，这样顶栏才能采样到背后的内容
        val contentModifier = if (backdrop != null) {
            Modifier.fillMaxSize().layerBackdrop(backdrop)
        } else {
            Modifier.fillMaxSize()
        }

        Box(modifier = contentModifier) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .miuixScrollModifiers(topAppBarScrollBehavior),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = pageContentBottomPadding,
                start = 12.dp,
                end = 12.dp
            )
        ) {
            if (selectedTab == 0) {
                // 待取页面
                if (filteredIncompleteGroups.isNotEmpty()) {
                    items(items = filteredIncompleteGroups, key = { "group_${it.id}" }) { group ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .animateItem(
                                    placementSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
                                )
                                .padding(bottom = 12.dp)
                        ) {
                            // 多选框（左侧，动画显示）
                            Box(modifier = Modifier.width(if (isEditMode) 40.dp else 0.dp)) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = isEditMode,
                                    enter = expandHorizontally() + fadeIn(),
                                    exit = shrinkHorizontally() + fadeOut()
                                ) {
                                    Checkbox(
                                        state = if (selectedGroupIds.contains(group.id)) ToggleableState.On else ToggleableState.Off,
                                        onClick = {
                                            performHaptic()
                                            selectedGroupIds = if (selectedGroupIds.contains(group.id)) {
                                                selectedGroupIds - group.id
                                            } else {
                                                selectedGroupIds + group.id
                                            }
                                        },
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }
                            MiuixOrderGroupCard(
                                group = group,
                                viewModel = viewModel,
                                onClick = { onNavigateToGroupDetail(group.id) },
                                onMarkAllCompleted = {
                                    performHaptic()
                                    viewModel.markGroupAsCompleted(group.id)
                                },
                                onDeleteGroup = {
                                    performHaptic()
                                    viewModel.deleteGroup(group)
                                },
                                isEditMode = isEditMode,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 单个订单（不属于任何组的）
                val standaloneOrders = filteredIncompleteOrders.filter { it.groupId == null }
                if (standaloneOrders.isNotEmpty()) {
                    items(items = standaloneOrders, key = { it.id }) { order ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .animateItem(
                                    placementSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
                                )
                                .padding(bottom = 12.dp)
                        ) {
                            // 多选框（左侧，动画显示）
                            Box(modifier = Modifier.width(if (isEditMode) 40.dp else 0.dp)) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = isEditMode,
                                    enter = expandHorizontally() + fadeIn(),
                                    exit = shrinkHorizontally() + fadeOut()
                                ) {
                                    Checkbox(
                                        state = if (selectedIds.contains(order.id)) ToggleableState.On else ToggleableState.Off,
                                        onClick = {
                                            performHaptic()
                                            selectedIds = if (selectedIds.contains(order.id)) {
                                                selectedIds - order.id
                                            } else {
                                                selectedIds + order.id
                                            }
                                        },
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }
                            MiuixOrderCard(
                                order = order,
                                onClick = { onNavigateToOrderDetail(order.id) },
                                onMarkCompleted = {
                                    performHaptic()
                                    viewModel.markAsCompleted(order.id)
                                },
                                onDelete = {
                                    performHaptic()
                                    viewModel.deleteOrder(order)
                                },
                                isEditMode = isEditMode,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 空状态
                if (standaloneOrders.isEmpty() && filteredIncompleteGroups.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无待取订单",
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                }
            } else {
                // 已取页面
                if (completedGroups.isNotEmpty()) {
                    item {
                        SmallTitle(text = "已完成订单组")
                    }
                    items(items = completedGroups, key = { "completed_group_${it.id}" }) { group ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .animateItem(
                                    placementSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
                                )
                                .padding(bottom = 12.dp)
                        ) {
                            Box(modifier = Modifier.width(if (isEditMode) 40.dp else 0.dp)) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = isEditMode,
                                    enter = androidx.compose.animation.expandHorizontally() + androidx.compose.animation.fadeIn(),
                                    exit = androidx.compose.animation.shrinkHorizontally() + androidx.compose.animation.fadeOut()
                                ) {
                                    Checkbox(
                                        state = if (selectedGroupIds.contains(group.id)) ToggleableState.On else ToggleableState.Off,
                                        onClick = {
                                            performHaptic()
                                            selectedGroupIds = if (selectedGroupIds.contains(group.id)) {
                                                selectedGroupIds - group.id
                                            } else {
                                                selectedGroupIds + group.id
                                            }
                                        },
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }
                            MiuixOrderGroupCard(
                                group = group,
                                viewModel = viewModel,
                                onClick = { },
                                onMarkAllCompleted = { },
                                onDeleteGroup = {
                                    performHaptic()
                                    viewModel.deleteGroup(group)
                                },
                                isCompleted = true,
                                isEditMode = isEditMode
                            )
                        }
                    }
                }

                if (standaloneCompletedOrders.isNotEmpty()) {
                    item {
                        SmallTitle(text = "已完成订单")
                    }
                    items(items = standaloneCompletedOrders, key = { "completed_${it.id}" }) { order ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .animateItem(
                                    placementSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
                                )
                                .padding(bottom = 12.dp)
                        ) {
                            Box(modifier = Modifier.width(if (isEditMode) 40.dp else 0.dp)) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = isEditMode,
                                    enter = androidx.compose.animation.expandHorizontally() + androidx.compose.animation.fadeIn(),
                                    exit = androidx.compose.animation.shrinkHorizontally() + androidx.compose.animation.fadeOut()
                                ) {
                                    Checkbox(
                                        state = if (selectedIds.contains(order.id)) ToggleableState.On else ToggleableState.Off,
                                        onClick = {
                                            performHaptic()
                                            selectedIds = if (selectedIds.contains(order.id)) {
                                                selectedIds - order.id
                                            } else {
                                                selectedIds + order.id
                                            }
                                        },
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }
                            MiuixOrderCard(
                                order = order,
                                onClick = { },
                                onMarkCompleted = { },
                                onDelete = {
                                    performHaptic()
                                    viewModel.deleteOrder(order)
                                },
                                isCompleted = true,
                                isEditMode = isEditMode
                            )
                        }
                    }
                }

                // 空状态
                if (standaloneCompletedOrders.isEmpty() && completedGroups.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无已取订单",
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                }
            }
        }
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(lazyListState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            trackPadding = PaddingValues(top = innerPadding.calculateTopPadding(), bottom = pageContentBottomPadding),
        )
        }
    }

    // 删除确认弹窗状态
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 全选按钮（编辑模式下显示，底部 FloatingToolbar 上方）
    AnimatedVisibility(
        visible = isEditMode && hasCurrentSelectableItems,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = editSelectionBottomPadding)
    ) {
        FloatingToolbar(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 420.dp)
                .onSizeChanged { editSelectionToolbarHeightPx = it.height },
            color = MiuixTheme.colorScheme.surface,
            cornerRadius = 20.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (currentOrderIds.isNotEmpty()) {
                    MiuixSelectionScopeButton(
                        title = "订单",
                        selectedCount = selectedCurrentOrderCount,
                        totalCount = currentOrderIds.size,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            performHaptic()
                            selectedIds = if (selectedCurrentOrderCount == currentOrderIds.size) {
                                selectedIds - currentOrderIds
                            } else {
                                selectedIds + currentOrderIds
                            }
                        },
                    )
                }
                if (currentGroupIds.isNotEmpty()) {
                    MiuixSelectionScopeButton(
                        title = "订单组",
                        selectedCount = selectedCurrentGroupCount,
                        totalCount = currentGroupIds.size,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            performHaptic()
                            selectedGroupIds = if (selectedCurrentGroupCount == currentGroupIds.size) {
                                selectedGroupIds - currentGroupIds
                            } else {
                                selectedGroupIds + currentGroupIds
                            }
                        },
                    )
                }
            }
        }
    }

    // 删除确认弹窗（OverlayDialog）
    if (showDeleteConfirm) {
        top.yukonga.miuix.kmp.overlay.OverlayDialog(
            title = "确认删除",
            summary = "确定要删除选中的 ${selectedIds.size + selectedGroupIds.size} 条记录吗？此操作不可撤销。",
            show = showDeleteConfirm,
            onDismissRequest = { showDeleteConfirm = false }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = { showDeleteConfirm = false },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        performHaptic()
                        selectedIds.forEach { id ->
                            incompleteOrders.find { it.id == id }?.let { viewModel.deleteOrder(it) }
                                ?: completedOrders.find { it.id == id }?.let { viewModel.deleteOrder(it) }
                        }
                        selectedGroupIds.forEach { id ->
                            incompleteGroups.find { it.id == id }?.let { viewModel.deleteGroup(it) }
                                ?: completedGroups.find { it.id == id }?.let { viewModel.deleteGroup(it) }
                        }
                        selectedIds = emptySet()
                        selectedGroupIds = emptySet()
                        showDeleteConfirm = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("删除", color = MiuixTheme.colorScheme.error)
                }
            }
        }
    }

    // FloatingToolbar：编辑模式底部操作栏
    AnimatedVisibility(
        visible = isEditMode && (selectedIds.isNotEmpty() || selectedGroupIds.isNotEmpty()),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = editActionBottomPadding)
    ) {
        FloatingToolbar(
            modifier = Modifier.onSizeChanged { editActionToolbarHeightPx = it.height },
            color = MiuixTheme.colorScheme.primaryContainer,
            cornerRadius = 16.dp,
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (selectedTab != 1) {
                    IconButton(onClick = {
                        performHaptic()
                        selectedIds.forEach { viewModel.markAsCompleted(it) }
                        selectedGroupIds.forEach { viewModel.markGroupAsCompleted(it) }
                        selectedIds = emptySet()
                        selectedGroupIds = emptySet()
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Ok,
                            contentDescription = "完成",
                            tint = Color.White,
                        )
                    }
                }
                IconButton(onClick = {
                    performHaptic()
                    showDeleteConfirm = true
                }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Delete,
                        contentDescription = "删除",
                        tint = Color.White,
                    )
                }
            }
        }
    }

    // 主页：添加 + 身份码按钮
    // 悬浮底栏开启时：竖排，跟随底栏位置
    // 悬浮底栏关闭时：横排，靠右
    val isVertical = useFloatingNavBar
    val targetToolbarBias = when {
        !useFloatingNavBar -> 1f
        navAlignment == "left" -> -1f
        else -> 1f
    }
    val animatedToolbarBias by animateFloatAsState(
        targetValue = targetToolbarBias,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 260f),
        label = "homeToolbarBias"
    )
    val toolbarAlignment = if (useFloatingNavBar) {
        BiasAlignment(animatedToolbarBias, 1f)
    } else {
        Alignment.BottomEnd
    }
    val toolbarPadding = if (useFloatingNavBar) {
        PaddingValues(
            start = 24.dp,
            end = 24.dp,
            bottom = bottomLayoutInfo.toolbarBottomPadding
        )
    } else {
        PaddingValues(end = 16.dp, bottom = bottomLayoutInfo.toolbarBottomPadding)
    }

    AnimatedVisibility(
        visible = !isEditMode && selectedTab == 0 && !isListScrolling,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(toolbarAlignment)
            .padding(toolbarPadding)
    ) {
        FloatingToolbar(
            color = MiuixTheme.colorScheme.primaryContainer,
            cornerRadius = 16.dp,
        ) {
            if (isVertical) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                IconButton(onClick = { performHaptic(); onAddClick() }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Add,
                        contentDescription = "添加",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { performHaptic(); openTaobaoIdentityEntry(context) }) {
                    Box {
                        Icon(
                            imageVector = MiuixIcons.Regular.Scan,
                            contentDescription = "淘宝身份码",
                            tint = Color.White
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .align(Alignment.BottomEnd)
                                .background(Color(0xFFFF8A00), CircleShape)
                        )
                    }
                }
                IconButton(onClick = { performHaptic(); openPddIdentityEntry(context) }) {
                    Box {
                        Icon(
                            imageVector = MiuixIcons.Regular.Scan,
                            contentDescription = "拼多多身份码",
                            tint = Color.White
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .align(Alignment.BottomEnd)
                                .background(Color(0xFFE53935), CircleShape)
                        )
                    }
                }
                }
            } else {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = { performHaptic(); onAddClick() }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Add,
                            contentDescription = "添加",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { performHaptic(); openTaobaoIdentityEntry(context) }) {
                        Box {
                            Icon(
                                imageVector = MiuixIcons.Regular.Scan,
                                contentDescription = "淘宝身份码",
                                tint = Color.White
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .align(Alignment.BottomEnd)
                                    .background(Color(0xFFFF8A00), CircleShape)
                            )
                        }
                    }
                    IconButton(onClick = { performHaptic(); openPddIdentityEntry(context) }) {
                        Box {
                            Icon(
                                imageVector = MiuixIcons.Regular.Scan,
                                contentDescription = "拼多多身份码",
                                tint = Color.White
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .align(Alignment.BottomEnd)
                                    .background(Color(0xFFE53935), CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
    } // Box
}

@Composable
private fun MiuixSelectionScopeButton(
    title: String,
    selectedCount: Int,
    totalCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allSelected = totalCount > 0 && selectedCount == totalCount
    val partiallySelected = selectedCount in 1 until totalCount
    val containerColor = when {
        allSelected -> MiuixTheme.colorScheme.primary
        partiallySelected -> MiuixTheme.colorScheme.primaryContainer
        else -> MiuixTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        allSelected -> MiuixTheme.colorScheme.onPrimary
        partiallySelected -> MiuixTheme.colorScheme.onPrimaryContainer
        else -> MiuixTheme.colorScheme.onSurface
    }
    val status = when {
        allSelected -> "已全选 · 点击取消"
        partiallySelected -> "已选 $selectedCount/$totalCount · 点击全选"
        else -> "$totalCount 项 · 点击全选"
    }

    Button(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        colors = ButtonDefaults.buttonColors(
            color = containerColor,
            contentColor = contentColor,
        ),
        cornerRadius = 15.dp,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (allSelected) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Ok,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = contentColor,
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Text(
                    text = title,
                    color = contentColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = status,
                color = contentColor.copy(alpha = 0.72f),
                fontSize = 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MiuixOrderGroupCard(
    group: OrderGroup,
    viewModel: OrderViewModel,
    onClick: () -> Unit,
    onMarkAllCompleted: () -> Unit,
    onDeleteGroup: () -> Unit = {},
    isCompleted: Boolean = false,
    isEditMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val hapticEnabled = remember(prefs) { prefs.getBoolean("haptic_enabled", true) }
    val performHaptic = {
        if (hapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    val groupOrders by viewModel.getOrdersByGroupId(group.id).collectAsStateWithLifecycle()

    // 定时状态
    val groupRequestCode = remember(group.id) { com.Badnng.moe.helper.NotificationScheduler.getGroupRequestCode(group.id) }
    var isScheduled by remember { mutableStateOf(com.Badnng.moe.helper.NotificationScheduler.isScheduled(context, groupRequestCode)) }
    var showTimePicker by remember { mutableStateOf(false) }

    val brandIconRes = remember(group.brandName, group.orderType) {
        BrandIconResolver.resolveBuiltinFallbackResId(context, group.brandName, group.orderType)
    }

    var groupIconBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(group.brandName, group.iconResName) {
        withContext(Dispatchers.IO) {
            if (group.iconResName != null) {
                val customResId = context.resources.getIdentifier(group.iconResName, "drawable", context.packageName)
                if (customResId != 0) return@withContext
            }
            var bmp = BrandIconResolver.resolveCustomIconBitmap(context, group.brandName)
            if (bmp == null) { delay(200); bmp = BrandIconResolver.resolveCustomIconBitmap(context, group.brandName) }
            if (bmp != null) groupIconBitmap = bmp
        }
    }

    var isExpanded by rememberSaveable(group.id) { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 顶部：图标 + 组名
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (groupIconBitmap != null) {
                    Image(
                        bitmap = groupIconBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(
                        painter = painterResource(id = brandIconRes),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color.Unspecified
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = group.name,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.primary
                )
            }

            // 包含订单数
            Text(
                text = "包含 ${groupOrders.size} 个取件码",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )

            // 取件位置（从组内订单中获取第一个非空位置）
            val location = groupOrders.firstOrNull { !it.pickupLocation.isNullOrBlank() }?.pickupLocation
            if (!location.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Location,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MiuixTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = location,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1
                    )
                }
            }

            // 展开/折叠按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .squircleSurface(
                        if (isExpanded) MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        else Color.Transparent,
                        12.dp,
                    )
                    .clickable {
                        performHaptic()
                        if (!isEditMode) isExpanded = !isExpanded
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isExpanded) "收起" else "展开查看详情",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.primary
                    )
                    val arrowRotation by animateFloatAsState(
                        targetValue = if (isExpanded) 180f else 0f
                    )
                    Icon(
                        MiuixIcons.Regular.ExpandMore,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(arrowRotation)
                    )
                }
            }

            // 展开后显示精简订单信息，避免组卡片内再次嵌套完整卡片和操作按钮
            AnimatedVisibility(
                visible = isExpanded && !isEditMode,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    groupOrders.forEachIndexed { index, order ->
                        MiuixGroupedOrderRow(order = order)
                        if (index < groupOrders.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 4.dp),
                                color = MiuixTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            // 操作按钮（非编辑模式下显示）
            if (!isEditMode) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!isCompleted) {
                            Button(
                                onClick = onMarkAllCompleted,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) {
                                Text(
                                    text = "全部完成",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                        Button(
                            onClick = onDeleteGroup,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors()
                        ) {
                            Text(
                                text = "删除组",
                                color = MiuixTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    // 再次推送实时通知 + 定时按钮
                    if (!isCompleted) {
                        val notificationHelper = com.Badnng.moe.helper.NotificationHelper(context)
                        Row(
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    performHaptic()
                                    notificationHelper.showGroupNotification(group, groupOrders)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "再次推送实时通知",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    maxLines = 1
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .height(44.dp)
                                    .width(44.dp)
                                    .squircleSurface(
                                        if (isScheduled) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
                                        16.dp,
                                    )
                                    .clickable {
                                        performHaptic()
                                        if (isScheduled) {
                                            com.Badnng.moe.helper.NotificationScheduler.cancel(context, groupRequestCode)
                                            isScheduled = false
                                            android.widget.Toast.makeText(context, "已取消定时", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            showTimePicker = true
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Regular.Alarm,
                                    contentDescription = "定时",
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 定时选择弹窗
    com.Badnng.moe.ui.component.MiuixScheduledNotificationSheet(
        show = showTimePicker,
        onDismiss = { showTimePicker = false },
        onSchedule = { triggerAtMillis ->
            com.Badnng.moe.helper.NotificationScheduler.scheduleGroup(context, group, triggerAtMillis)
            isScheduled = true
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = triggerAtMillis }
            val timeStr2 = String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
            android.widget.Toast.makeText(context, "已设置 $timeStr2 推送", android.widget.Toast.LENGTH_SHORT).show()
            showTimePicker = false
        }
    )
}

@Composable
private fun MiuixGroupedOrderRow(order: OrderEntity) {
    val timeStr = remember(order.createdAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(order.createdAt))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (order.orderType == "快递") "取件码" else (order.brandName ?: "取餐码"),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Text(
                text = order.takeoutCode,
                style = MiuixTheme.textStyles.title1,
                fontWeight = FontWeight.Bold,
                color = if (order.isCompleted) {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                } else {
                    MiuixTheme.colorScheme.primary
                }
            )
            if (!order.pickupLocation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Location,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MiuixTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = order.pickupLocation,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "时间: $timeStr",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.75f)
            )
        }
        if (order.isCompleted) {
            Text(
                text = "已完成",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MiuixOrderCard(
    order: OrderEntity,
    onClick: () -> Unit,
    onMarkCompleted: () -> Unit,
    onDelete: () -> Unit,
    isCompleted: Boolean = false,
    isEditMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val timeStr = remember(order.createdAt) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        sdf.format(Date(order.createdAt))
    }

    val brandIconRes = remember(order.brandName, order.orderType) {
        BrandIconResolver.resolveBuiltinFallbackResId(context, order.brandName, order.orderType)
    }

    // 定时状态
    val orderRequestCode = remember(order.id) { com.Badnng.moe.helper.NotificationScheduler.getOrderRequestCode(order.id) }
    var isScheduled by remember { mutableStateOf(com.Badnng.moe.helper.NotificationScheduler.isScheduled(context, orderRequestCode)) }
    var showTimePicker by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val hapticEnabled = remember(prefs) { prefs.getBoolean("haptic_enabled", true) }
    val performHaptic = {
        if (hapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    var orderIconBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(order.brandName) {
        withContext(Dispatchers.IO) {
            var bmp = BrandIconResolver.resolveCustomIconBitmap(context, order.brandName)
            if (bmp == null) {
                delay(200)
                bmp = BrandIconResolver.resolveCustomIconBitmap(context, order.brandName)
            }
            if (bmp != null) orderIconBitmap = bmp
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 顶部：图标 + 取餐码 + 二维码按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (orderIconBitmap != null) {
                        Image(
                            bitmap = orderIconBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = brandIconRes),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color.Unspecified
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (order.orderType == "快递") "取件码" else (order.brandName ?: "取餐码"),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Text(
                            text = order.takeoutCode,
                            style = MiuixTheme.textStyles.title1,
                            fontWeight = FontWeight.ExtraBold,
                            color = MiuixTheme.colorScheme.primary
                        )
                        // 取件位置
                        if (!order.pickupLocation.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = MiuixIcons.Regular.Location,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MiuixTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = order.pickupLocation,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                // QR码按钮
                if (!order.qrCodeData.isNullOrEmpty()) {
                    IconButton(onClick = { /* TODO: 显示QR码 */ }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Scan,
                            contentDescription = "二维码",
                            modifier = Modifier.size(24.dp),
                            tint = MiuixTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // 时间
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "时间: $timeStr",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )

            // 操作按钮（非编辑模式且未完成时显示）
            if (!isEditMode && !isCompleted) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onMarkCompleted,
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColorsPrimary()
                    ) {
                        Text(
                            text = "完成",
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    Button(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors()
                    ) {
                        Text(
                            text = "删除",
                            color = MiuixTheme.colorScheme.error,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // 再次推送实时通知 + 定时按钮
                val notificationHelper = com.Badnng.moe.helper.NotificationHelper(context)
                Row(
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { notificationHelper.showPromotedLiveUpdate(order) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary()
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "再次推送实时通知",
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .width(44.dp)
                            .squircleSurface(
                                if (isScheduled) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
                                16.dp,
                            )
                            .clickable {
                                performHaptic()
                                if (isScheduled) {
                                    com.Badnng.moe.helper.NotificationScheduler.cancel(context, orderRequestCode)
                                    isScheduled = false
                                    android.widget.Toast.makeText(context, "已取消定时", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    showTimePicker = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Alarm,
                            contentDescription = "定时",
                            modifier = Modifier.size(20.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    // 定时选择弹窗
    com.Badnng.moe.ui.component.MiuixScheduledNotificationSheet(
        show = showTimePicker,
        onDismiss = { showTimePicker = false },
        onSchedule = { triggerAtMillis ->
            com.Badnng.moe.helper.NotificationScheduler.schedule(context, order, triggerAtMillis)
            isScheduled = true
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = triggerAtMillis }
            val timeStr2 = String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
            android.widget.Toast.makeText(context, "已设置 $timeStr2 推送", android.widget.Toast.LENGTH_SHORT).show()
            showTimePicker = false
        }
    )
}
