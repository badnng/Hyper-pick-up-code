package com.Badnng.moe.wearable

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.helper.NotificationHelper
import com.Badnng.moe.helper.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 手表同步核心管理器（Plan §5.3）。
 *
 * 职责：
 *  1. 订阅「未完成订单」变化（distinctUntilChanged + 500ms 防抖），序列化全量快照推送到手表；
 *  2. 收到手表 `hello`（打开即同步）→ 立即补发最新全量快照；
 *  3. 收到手表 `done` 回执 → 幂等调用 `markAsCompleted`（含重复/未知/过期兜底）；
 *  4. 节点发现、断线重查、授权、消息监听注册/注销；
 *  5. 通过状态 StateFlow 暴露给 UI（开关/连接/设备/最近同步时间）。
 *
 * ⚠️ 与具体穿戴 SDK 解耦：所有收发都经 [WearableSdkBridge]。SDK 缺失时用
 * [UnavailableWearableSdkBridge]，默认不同步、不发现、不连接（见 [enabled] 语义）。
 *
 * ⚠️ 同步开关默认关闭。关闭状态下：不发现节点、不注册监听、不推送，只暴露应用级状态；
 * 开启后才进行发现/授权/监听/推送。启用同步后 [refreshNode] 发现节点时会自动申请一次权限
 * （按节点去重）：Mi Fitness 需 Android 应用至少请求一次权限才会创建权限控制项，否则
 * 手表端 interconnect 会一直判定对端未安装/未授权。设置页「设备权限」按钮仍可手动触发。
 */
@OptIn(FlowPreview::class)
class WearableSyncManager private constructor(
    private val appContext: Context,
    bridge: WearableSdkBridge? = null,
) {
    // XMS SDK 初始化可能触发 Binder/类加载，延迟到真正开启同步的后台任务中。
    private val bridge: WearableSdkBridge by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        bridge ?: XiaomiWearableBridge(appContext)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 数据源，由 [OrderViewModel] 通过 [attachSource] 注入、[detachSource] 解绑。 */
    @Volatile
    var dataSource: WearableSyncSource? = null
        private set

    private val _enabled = MutableStateFlow(
        prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED)
    )
    /** 用户开关。与真实连接状态（[state]）严格区分。 */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _state = MutableStateFlow(State())
    /** 实时状态（连接/同步/最近同步时间/设备/错误）。 */
    val state: StateFlow<State> = _state.asStateFlow()

    private var ordersJob: Job? = null
    private var findNodeJob: Job? = null
    private var releaseJob: Job? = null
    /** 穿戴通道自愈轮询（监听丢失时重新发现节点并注册，保证后台能收到手表心跳 ping）。 */
    private var watchdogJob: Job? = null
    private var pushing = AtomicBoolean(false)
    /** 最近一次未完成订单快照的 orderId 集合；用于识别“新加入的取餐码”。 */
    private var lastKnownIncompleteIds: Set<String> = emptySet()
    private var orderSnapshotInitialized = false
    /** 已发送过系统通知的订单，防止多个识别入口（通知/截图/短信等）重复弹。 */
    private val notifiedOrderIds = HashSet<String>()
    /** interconnect 是单一顺序通道；快照、心跳、ready、done_result 必须串行提交。 */
    private val sendMutex = Mutex()
    /** 同一 requestId 的完成请求只执行业务一次，重发消息直接复用已计算结果。 */
    private val doneMutex = Mutex()
    private val doneResults = LinkedHashMap<String, CompletionResult>(DONE_RESULT_CACHE_SIZE)

    /**
     * 手表会把最后一次快照版本持久化到本地。版本号不能在手机进程重启后从 1 重新开始，
     * 否则手表会按旧版本过滤掉后续所有快照。以当前时间作为下界，兼容此前未持久化版本的安装。
     */
    private var latestSnapshotVersion = maxOf(
        prefs.getLong(KEY_SNAPSHOT_VERSION, 0L),
        System.currentTimeMillis(),
    )
    private var latestSnapshotDirty = false
    /** 上一轮分片发送期间到达的最新订单快照，发送结束后补发，避免新订单被丢弃。 */
    @Volatile
    private var pendingOrdersForPush: OrdersSnapshot? = null
    private var boundNodeId: String? = null
    /** 官方 ITEM_CONNECTION 订阅绑定的节点，和消息监听分开维护。 */
    private var connectionListenerNodeId: String? = null
    /** 最近一次确认节点存在的时间；服务短暂抖动时保留“已连接”显示。 */
    private var lastNodeSeenAt = 0L
    private var messageListenerBoundAt = 0L
    /** 已自动申请过通知权限的节点 id（去重：同一节点只自动申请一次）。 */
    private var autoNotifyPermRequestedFor: String? = null
    /** 已自动申请过设备管理权限的节点 id（去重，仅检测到手表端 rpk 已安装时自动申请）。 */
    private var autoDeviceManagerPermRequestedFor: String? = null
    /** 下次发现节点时强制 remove→add，修复 XMS 本地 registered 但远端回调失效。 */
    @Volatile
    private var forceMessageRebind = false

    // ═══════════ 数据源生命周期 ═══════════

    /**
     * 注入订单数据源。由 [OrderViewModel] 在 init 时调用。
     *
     * 解绑旧 source（避免 Application 单例永久持有已清理的 ViewModel），再绑定新 source；
     * 仅在开关开启时发现节点并绑定订单 collector；开关关闭时仅记录 SDK 可用性，不连接。
     */
    fun attachSource(source: WearableSyncSource) {
        if (dataSource === source) return
        log("绑定手表同步数据源: ${source::class.java.simpleName}")
        detachSource()
        forceMessageRebind = true
        dataSource = source
        syncOrdersCollection()
        // SDK 构造和节点发现涉及 Binder/反射，不能阻塞主页首帧。
        if (_enabled.value) {
            scope.launch {
                ensureStarted()
                refreshNodeIfEnabled()
            }
        }
    }

    /**
     * 解绑数据源并清理由其伴生的同步任务。
     * 由 [OrderViewModel] 的 `onCleared()` 调用，防止 Application 级单例泄漏已销毁的 ViewModel。
     */
    fun detachSource() {
        log("解绑手表同步数据源")
        ordersJob?.cancel()
        ordersJob = null
        dataSource = null
    }

    // ═══════════ 启动 ═══════════

    private fun ensureStarted() {
        if (_state.value.started) return
        _state.value = _state.value.copy(started = true)
        val sdkOk = runCatching { bridge.isSdkAvailable }.getOrDefault(false)
        val sdkError = runCatching { bridge.lastError }.getOrNull()
        updateState { s ->
            s.copy(
                sdkIntegrated = sdkOk,
                notifyPermissionEnabled = prefs.getBoolean(
                    KEY_NOTIFY_PERMISSION_ENABLED,
                    DEFAULT_PERMISSION_ENABLED,
                ),
                lastError = if (sdkOk) sdkError ?: s.lastError else (sdkError ?: "未安装 Mi Fitness 穿戴服务"),
            )
        }
        startWatchdog()
    }

    /**
     * 穿戴通道自愈轮询：每 [WATCHDOG_RETRY_MS]（5s）检查本地监听状态；仅当消息监听或
     * 连接监听被官方 ServiceApi 的断线/重连回调标记为失效时，重新发现节点并注册。
     * Mi Fitness 服务断开/重连、进程被系统拉起等场景都会导致监听丢失，此时手表发的
     * 心跳 ping 手机端收不到；监听丢失则重新发现节点并注册监听。
     *
     * 生命周期：在 [ensureStarted]（仅首次）与 [setEnabled](true)（关闭再开启后恢复）中启动；
     * 在 [stop] 与 [setEnabled](false) 中取消。startWatchdog 幂等，不会重复启动。
     */
    private fun startWatchdog() {
        if (watchdogJob != null) return
        watchdogJob = scope.launch {
            while (isActive) {
                // 只轮询本地布尔状态，已注册时不会发起 Binder 查询；保持 5s 检查可让
                // Mi Fitness 服务重连后的消息监听尽快恢复，而不是额外等待 30s。
                delay(WATCHDOG_RETRY_MS)
                if (!_enabled.value) continue
                val listenerRegistered = runCatching { bridge.isListenerRegistered }.getOrDefault(false)
                val connectionListenerRegistered = runCatching {
                    bridge.isConnectionListenerRegistered
                }.getOrDefault(false)
                if (!listenerRegistered || !connectionListenerRegistered) {
                    // 监听丢失自愈：refreshNode 内部会重新发现节点并注册消息监听
                    refreshNode()
                }
            }
        }
    }

    /** 应用/服务结束时调用，注销监听与 collector。 */
    fun stop() {
        ordersJob?.cancel()
        ordersJob = null
        findNodeJob?.cancel()
        findNodeJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        releaseBridge()
        autoNotifyPermRequestedFor = null
        autoDeviceManagerPermRequestedFor = null
        _state.value = _state.value.copy(
            started = false,
            connected = false,
            transportConnected = false,
            nodeId = null,
            deviceName = null,
            permissionGranted = false,
            notifyPermissionGranted = false,
            deviceManagerPermissionGranted = false,
        )
        lastNodeSeenAt = 0L
        connectionListenerNodeId = null
    }

    // ═══════════ 订单流采集 ═══════════

    private fun syncOrdersCollection() {
        if (dataSource == null) return
        if (_enabled.value) {
            bindOrdersFlow()
        } else {
            ordersJob?.cancel()
            ordersJob = null
        }
    }

    private fun bindOrdersFlow() {
        ordersJob?.cancel()
        val source = dataSource ?: return
        ordersJob = scope.launch {
            combine(source.incompleteOrders, source.completedOrders) { incomplete, completed ->
                OrdersSnapshot(incomplete, completed)
            }
                .debounce(DEBOUNCE_MS) // 500ms 防抖，应用策略而非平台保证
                .collect { snapshot ->
                    latestSnapshotDirty = true
                    pendingOrdersForPush = snapshot

                    // 第一次快照只作为基线，不给历史订单补弹通知；
                    // 后续快照中新出现的 orderId 才触发系统通知。
                    if (!orderSnapshotInitialized) {
                        orderSnapshotInitialized = true
                        lastKnownIncompleteIds = snapshot.incomplete.map { it.id }.toSet()
                    } else {
                        val newOrders = snapshot.incomplete.filter { it.id !in lastKnownIncompleteIds }
                        lastKnownIncompleteIds = snapshot.incomplete.map { it.id }.toSet()
                        newOrders.groupBy { it.groupId }.forEach { (groupId, orders) ->
                            if (groupId != null && orders.size >= 2) {
                                val type = orders.first().orderType.takeIf { it.isNotBlank() } ?: "取餐码"
                                val brand = orders.first().brandName?.takeIf { it.isNotBlank() }
                                val groupTitle = brand ?: "新的${type}通知"
                                val hasQr = orders.any { !it.qrCodeData.isNullOrBlank() }
                                val groupMessage = buildString {
                                    append("${orders.size}个$type")
                                    if (hasQr) append("，可到手表端查看二维码")
                                }
                                sendNewOrderNotify(
                                    orders.first().id,
                                    groupTitle,
                                    groupMessage
                                )
                            } else {
                                orders.forEach { order ->
                                    val title = order.brandName?.takeIf { it.isNotBlank() } ?: "新的取餐码"
                                    val message = buildString {
                                        append(order.takeoutCode)
                                        if (!order.qrCodeData.isNullOrBlank()) {
                                            append("，二维码已同步，可到手表端查看")
                                        }
                                    }
                                    sendNewOrderNotify(order.id, title, message)
                                }
                            }
                        }
                    }

                    log("订单快照变化: incomplete=${snapshot.incomplete.size}, completed=${snapshot.completed.size}, connected=${isConnected()}, pushing=${pushing.get()}")
                    if (isConnected()) {
                        requestSnapshotPush(snapshot.incomplete, snapshot.completed)
                    } else {
                        updateState { it.copy(
                            pendingOrdersCount = snapshot.incomplete.size,
                        ) }
                    }
                }
        }
    }

    // ═══════════ 开关 ═══════════

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        ensureStarted()
        if (value) {
            // 开启：重新绑定 collector 并发现节点、补发最新快照
            bindOrdersFlow()
            refreshNodeIfEnabled()
            pushLatestIfReady()
            // 后台保活：确保 MIUI 不冻结进程，Binder 回调（手表心跳 ping）可达
            runCatching { com.Badnng.moe.service.KeepAliveService.start(appContext) }
                .onFailure { reportBridgeFailure("启动手表同步保活", it) }
            // 关闭再开启后恢复 watchdog 自愈（关闭时已取消）
            startWatchdog()
        } else {
            // 关闭：取消订单 collector、发现任务，在后台线程注销监听并释放 bridge 资源，
            // 避免在设置页主线程同步 Binder 调用
            ordersJob?.cancel()
            ordersJob = null
            findNodeJob?.cancel()
            findNodeJob = null
            watchdogJob?.cancel()
            watchdogJob = null
            releaseBridge()
            boundNodeId = null
            connectionListenerNodeId = null
            autoNotifyPermRequestedFor = null
            autoDeviceManagerPermRequestedFor = null
            updateState {
                it.copy(
                    discovering = false,
                    connected = false,
                    transportConnected = false,
                    nodeId = null,
                    deviceName = null,
                    permissionGranted = false,
                    notifyPermissionGranted = false,
                    deviceManagerPermissionGranted = false,
                )
            }
            lastNodeSeenAt = 0L
            com.Badnng.moe.service.KeepAliveService.stopIfNoConsumer(appContext)
        }
    }

    /**
     * 在后台线程注销消息监听并释放 bridge 连接资源，避免主线程同步 Binder 调用。
     *
     * ⚠️ release 是异步的，且 bridge 为共享单例：若用户「关闭后立即再开启」，异步 release 可能与
     * 随后的重建连接竞态（release 解绑新建连接）。因此用单一 [releaseJob] 串行化，并在
     * [refreshNode] 重建连接前 `join()` 等待其完成，保证 release 不会解绑新建立的连接。
     */
    private fun releaseBridge() {
        releaseJob?.cancel()
        releaseJob = scope.launch(Dispatchers.IO) {
            runCatching { bridge.removeMessageListener(appContext) }
                .onFailure { reportBridgeFailure("移除手表消息监听", it) }
            runCatching { bridge.release() }
                .onFailure { reportBridgeFailure("释放手表连接", it) }
        }
    }

    // ═══════════ 节点与管理 ═══════════

    private fun refreshNodeIfEnabled() {
        if (_enabled.value) refreshNode()
    }

    private fun requestSnapshotPush(
        orders: List<OrderEntity>,
        completedOrders: List<OrderEntity>,
    ) {
        val snapshot = OrdersSnapshot(orders, completedOrders)
        pendingOrdersForPush = snapshot
        scope.launch { pushSnapshot(snapshot) }
    }

    /** 主动查找已连接的手表节点（供 UI 重试/进入设置页时调用）。
     *  启用同步时若发现节点但未授权，会自动申请一次权限（按节点去重）。 */
    fun refreshNode() {
        if (!_enabled.value) {
            updateState { it.copy(lastError = "请先开启手表同步") }
            return
        }
        // 发现/重试循环进行中：直接复用，不取消重启（避免 watchdog 高频触发打断进行中的节点发现）
        if (findNodeJob?.isActive == true) return
        log("开始刷新手表节点")
        findNodeJob = scope.launch {
            while (isActive) {
                // 先等待可能仍在进行的异步 release 结束，再重建连接，避免 release 解绑新建连接
                releaseJob?.join()
                updateState { it.copy(discovering = true) }
                val nodeId = runBridgeCall("查找手表设备") {
                    withContext(Dispatchers.IO) { bridge.findNodeId(appContext) }
                }
                val notifyPermission = if (nodeId != null) {
                    runBridgeCall("查询手表通知权限") {
                        withContext(Dispatchers.IO) { bridge.isNotifyPermissionGranted }
                    } ?: false
                } else {
                    false
                }
                val deviceManagerPermission = if (nodeId != null) {
                    runBridgeCall("查询手表设备管理权限") {
                        withContext(Dispatchers.IO) { bridge.isDeviceManagerPermissionGranted }
                    } ?: false
                } else {
                    false
                }
                val watchAppInstalled = if (nodeId != null && !deviceManagerPermission) {
                    runBridgeCall("查询手表端快应用安装状态") {
                        withContext(Dispatchers.IO) { bridge.isWatchAppInstalled(appContext, nodeId) }
                    }
                } else {
                    null
                }
                val permission = notifyPermission && deviceManagerPermission
                val queriedConnection = if (nodeId != null) {
                    runBridgeCall("查询手表互联状态") {
                        withContext(Dispatchers.IO) {
                            bridge.queryConnection(appContext, nodeId)
                        }
                    }
                } else {
                    null
                }
                log("节点刷新结果: nodeId=$nodeId, notifyPermission=$notifyPermission, deviceManagerPermission=$deviceManagerPermission, watchAppInstalled=$watchAppInstalled, queriedConnection=$queriedConnection")
                // 记录重绑前节点（同节点免重绑需要与旧值比较）
                val prevNodeId = boundNodeId
                boundNodeId = nodeId
                updateState {
                    val now = System.currentTimeMillis()
                    if (nodeId != null) lastNodeSeenAt = now
                    val retainConnection = it.connected &&
                        now - lastNodeSeenAt <= NODE_LOSS_GRACE_MS
                    it.copy(
                        discovering = false,
                        nodeId = nodeId,
                        // 节点发现偶发超时/服务重连时不要立即把在线状态改成未连接。
                        // 连续超过宽限期仍未发现节点才降级，避免设置页和手表端状态闪断。
                        connected = nodeId != null || retainConnection,
                        transportConnected = queriedConnection ?: (nodeId != null),
                        deviceName = nodeId?.let { id -> it.deviceName ?: id.take(8) } ?: it.deviceName,
                        permissionGranted = nodeId != null && permission,
                        notifyPermissionGranted = nodeId != null && notifyPermission,
                        deviceManagerPermissionGranted = nodeId != null && deviceManagerPermission,
                        watchAppInstalled = watchAppInstalled ?: it.watchAppInstalled,
                        lastError = bridgeLastError(if (nodeId != null) it.lastError else "未发现已连接的手表"),
                    )
                }
                if (nodeId != null) {
                    // 同节点且监听已注册 → 跳过重绑，避免反复 remove/add 撞上 Mi Fitness 服务端广播窗口；
                    // 监听丢失（watchdog 触发）时 isListenerRegistered 为 false，仍会走重绑自愈
                    val listenerRegistered = runCatching {
                        bridge.isListenerRegistered
                    }.getOrDefault(false)
                    // 服务重启后 XMS 可能仍返回本地 registered=true，但远端 callback
                    // 已经失效；每次 manager 自启动/首次 attach 都强制走一次 remove/add。
                    val forceInitialRebind = forceMessageRebind || messageListenerBoundAt == 0L
                    val alreadyBound = prevNodeId == nodeId && listenerRegistered && !forceInitialRebind
                    log("消息监听绑定判断: nodeId=$nodeId, prevNodeId=$prevNodeId, registered=$listenerRegistered, alreadyBound=$alreadyBound, boundAt=$messageListenerBoundAt")
                    if (!alreadyBound) {
                        // 首次/服务重连后直接 add；仅节点切换且旧监听仍在时主动 remove。
                        // 若 AAR 仅残留进程内旧引用，bridge 会在 add 返回
                        // "you have registered" 后执行 remove -> add 自愈。
                        bindMessageListener(
                            nodeId,
                            forceRebind = listenerRegistered && (prevNodeId != nodeId || forceInitialRebind),
                        )
                    }
                    val connectionAlreadyBound = prevNodeId == nodeId &&
                        runCatching { bridge.isConnectionListenerRegistered }.getOrDefault(false)
                    log("连接状态监听绑定判断: nodeId=$nodeId, alreadyBound=$connectionAlreadyBound")
                    if (!connectionAlreadyBound) {
                        bindConnectionListener(nodeId)
                    }
                    // 通知权限不依赖手表端快应用，按「通知权限」开关自动申请；
                    // 设备管理权限（RPK 同步）跟随「同步到小米手表」总开关，仅在检测到手表端 rpk
                    // 已安装时自动申请（用户装好后重新发现节点即可补上）。
                    // 去重标记仅在申请成功后记录：失败的申请（用户未确认/被拒绝）允许下轮重试，
                    // 避免一次失败后通知权限永远无法授予、手表收不到提醒。
                    if (!notifyPermission &&
                        _state.value.notifyPermissionEnabled &&
                        autoNotifyPermRequestedFor != nodeId
                    ) {
                        requestNotifyPermission()
                        if (_state.value.notifyPermissionGranted) {
                            autoNotifyPermRequestedFor = nodeId
                        } else {
                            log("通知权限自动申请未成功，等待下轮刷新重试")
                        }
                    }
                    if (!deviceManagerPermission &&
                        watchAppInstalled == true &&
                        autoDeviceManagerPermRequestedFor != nodeId
                    ) {
                        requestDeviceManagerPermission()
                        if (_state.value.deviceManagerPermissionGranted) {
                            autoDeviceManagerPermRequestedFor = nodeId
                        } else {
                            log("设备管理权限自动申请未成功，等待下轮刷新重试")
                        }
                    }
                    pushLatestIfReady()
                    log("节点刷新完成: nodeId=$nodeId")
                    break
                } else {
                    // 节点丢失：重置自动授权去重标记，下次发现同节点可重新申请
                    autoNotifyPermRequestedFor = null
                    autoDeviceManagerPermRequestedFor = null
                    connectionListenerNodeId = null
                    // 失败快速重试：同一协程内 5s 后重试，无自取消问题；
                    // 重试循环进行中时外部 refreshNode() 调用会被「isActive 复用」挡下，无取消风暴
                    delay(WATCHDOG_RETRY_MS)
                }
            }
        }
    }

    /**
     * 申请设备权限（供设置页「授权」按钮手动触发）。
     * 顺序：先通知权限（NOTIFY，不依赖手表快应用），后设备管理权限
     * （DEVICE_MANAGER，依赖手表端已安装 rpk），避免未安装快应用时授权整体卡死。
     */
    fun requestPermission() {
        scope.launch {
            requestNotifyPermission()
            requestDeviceManagerPermission()
        }
    }

    /**
     * 开关「通知权限」：开启时自动申请 NOTIFY（不依赖手表端快应用），
     * 关闭后不再向手表发送系统通知。
     */
    fun setNotifyPermissionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_PERMISSION_ENABLED, enabled).apply()
        updateState { it.copy(notifyPermissionEnabled = enabled) }
        if (enabled) {
            scope.launch {
                if (!_state.value.notifyPermissionGranted) requestNotifyPermission()
            }
        } else {
            log("通知权限开关已关闭，不再向手表发送系统通知")
        }
    }

    /** 申请通知权限（NOTIFY）。不依赖手表端快应用，应优先申请。 */
    private suspend fun requestNotifyPermission() {
        val granted = runBridgeCall("申请手表通知权限") {
            withContext(Dispatchers.IO) { bridge.requestNotifyPermission(appContext) }
        } ?: false
        updateState {
            it.copy(
                notifyPermissionGranted = granted,
                permissionGranted = granted && it.deviceManagerPermissionGranted,
            )
        }
        if (granted) onPermissionGranted()
    }

    /** 申请设备管理权限（DEVICE_MANAGER）。依赖手表端快应用已安装。 */
    private suspend fun requestDeviceManagerPermission() {
        val granted = runBridgeCall("申请手表设备管理权限") {
            withContext(Dispatchers.IO) { bridge.requestDeviceManagerPermission(appContext) }
        } ?: false
        updateState {
            it.copy(
                deviceManagerPermissionGranted = granted,
                permissionGranted = it.notifyPermissionGranted && granted,
            )
        }
        if (granted) onPermissionGranted()
    }

    private fun onPermissionGranted() {
        // 授权成功后监听才真正可用；已注册时不要重复 remove/add，
        // 仅在监听确实丢失时补注册。
        boundNodeId?.let { nodeId ->
            val messageRegistered = runCatching {
                bridge.isListenerRegistered
            }.getOrDefault(false)
            if (!messageRegistered) bindMessageListener(nodeId)

            val connectionRegistered = runCatching {
                bridge.isConnectionListenerRegistered
            }.getOrDefault(false)
            if (!connectionRegistered) bindConnectionListener(nodeId)
        }
        pushLatestIfReady()
    }

    /** 新订单通知入口：同一 orderId 去重，避免截图/通知/短信等多个识别入口重复弹。 */
    fun sendNewOrderNotify(orderId: String, title: String, message: String) {
        if (orderId.isBlank()) {
            sendSystemNotify(title, message)
            return
        }
        synchronized(notifiedOrderIds) {
            if (!notifiedOrderIds.add(orderId)) {
                log("系统通知去重跳过: orderId=$orderId")
                return
            }
        }
        sendSystemNotify(title, message)
    }

    /**
     * 向手表发送一条系统通知（NotifyApi.sendNotify）。
     * 需要已开启同步、已发现节点、已授权；
     * 不再强制等待 connected 标志，因为 NotifyApi 走 Mi Fitness 系统通知通道，
     * 节点已发现时即可尝试发送。
     * 手表端快应用无感知，仅用于手表系统通知栏提醒。
     */
    fun sendSystemNotify(title: String, message: String) {
        if (!_enabled.value) {
            log("发送系统通知跳过: 手表同步开关未开启")
            return
        }
        if (!_state.value.notifyPermissionEnabled) {
            log("发送系统通知跳过: 通知权限开关未开启")
            return
        }
        if (boundNodeId == null) {
            // 自启动/恢复期节点可能尚未发现：不在此阻断，交给 bridge 在发送时
            // 自动补一次节点发现（findNodeId），避免提醒静默丢失。
            log("发送系统通知: 本地节点缓存为空，交由 SDK 自动发现后发送")
        }
        scope.launch {
            // 通知权限未授予时先补申请一次（NOTIFY 不依赖手表快应用）；
            // 随后照常尝试发送，避免权限状态滞后导致提醒静默丢失。
            if (!_state.value.notifyPermissionGranted) {
                log("发送系统通知前补申请通知权限")
                requestNotifyPermission()
            }
            val ok = sendMutex.withLock {
                runBridgeCall("发送手表系统通知") {
                    withContext(Dispatchers.IO) {
                        bridge.sendSystemNotify(appContext, title, message)
                    }
                } ?: false
            }
            log("发送系统通知结束: title=$title, ok=$ok, lastError=${bridge.lastError}")
        }
    }

    private fun bindMessageListener(nodeId: String, forceRebind: Boolean = false) {
        log("开始绑定消息监听: nodeId=$nodeId, forceRebind=$forceRebind")
        if (forceRebind) {
            runCatching {
                bridge.removeMessageListener(appContext)
            }.onFailure { error ->
                reportBridgeFailure("移除旧手表消息监听", error)
            }
        }
        if (!forceRebind && runCatching { bridge.isListenerRegistered }.getOrDefault(false)) {
            log("旧消息监听仍由 AAR 保持，跳过重复注册: nodeId=$nodeId")
            return
        }
        runCatching {
            bridge.setMessageListener(appContext) { bytes ->
                runCatching { handleIncoming(bytes) }
                    .onFailure { error -> reportBridgeFailure("处理手表消息", error) }
            }
        }.onFailure { error ->
            reportBridgeFailure("注册手表消息监听", error)
        }
        val registered = runCatching { bridge.isListenerRegistered }.getOrDefault(false)
        if (registered) messageListenerBoundAt = System.currentTimeMillis()
        if (registered) forceMessageRebind = false
        log("消息监听绑定结束: nodeId=$nodeId, registered=$registered, forceRebind=$forceRebind")
        if (registered) {
            // 对齐参考项目 InterHandshake 的“注册后主动发首包”：不能依赖手表首个
            // hello 恰好落在 listener 注册之后，后台冷启动时首包很容易早到而丢失。
            scope.launch {
                sendToWear("发送手机主动握手", WearableSyncProtocol.encodeHandshake(count = 0))
                sendToWear("初始化手表业务 ready", WearableSyncProtocol.encodeReady())
            }
        }
    }

    /** 按官方 NodeApi.subscribe(ITEM_CONNECTION) 订阅手表互联状态。 */
    private fun bindConnectionListener(nodeId: String) {
        log("开始绑定连接状态监听: nodeId=$nodeId")
        runCatching {
            bridge.removeConnectionListener(appContext)
        }.onFailure { error ->
            reportBridgeFailure("移除旧手表状态监听", error)
        }
        val registered = runCatching {
            bridge.setConnectionListener(appContext, nodeId) { changedNodeId, connected ->
                if (changedNodeId != nodeId) return@setConnectionListener
                updateState { it.copy(transportConnected = connected) }
            }
        }.onFailure { error ->
            reportBridgeFailure("注册手表状态监听", error)
        }.getOrDefault(false)
        connectionListenerNodeId = if (registered) nodeId else null
        log("连接状态监听绑定结束: nodeId=$nodeId, registered=$registered")
    }

    // ═══════════ 数据推送 ═══════════

    private fun isConnected(): Boolean = _state.value.connected

    private fun pushLatestIfReady() {
        if (!_enabled.value || !isConnected()) {
            log("跳过推送最新快照: enabled=${_enabled.value}, connected=${isConnected()}")
            return
        }
        val source = dataSource ?: run {
            log("跳过推送最新快照: dataSource=null")
            return
        }
        requestSnapshotPush(source.incompleteOrders.value, source.completedOrders.value)
    }

    private suspend fun pushSnapshot(snapshot: OrdersSnapshot) {
        // 手表只保留近期数据。手机端数据库可以有很长历史，但圆表不适合一次接收/渲染全部记录。
        // 按数据库流的时间顺序取最新项；hello 的 DAO 兜底也统一排序，避免旧记录占满手表缓存。
        val orders = snapshot.incomplete
            .sortedByDescending { it.createdAt }
            .take(MAX_WATCH_INCOMPLETE_ORDERS)
        val completedOrders = snapshot.completed
            .sortedWith(compareByDescending<OrderEntity> { it.completedAt ?: 0L }
                .thenByDescending { it.createdAt })
            .take(MAX_WATCH_COMPLETED_ORDERS)
        log("准备推送订单快照: incomplete=${snapshot.incomplete.size}->${orders.size}, completed=${snapshot.completed.size}->${completedOrders.size}, boundNodeId=$boundNodeId")
        if (!_enabled.value) {
            log("订单快照推送跳过: 同步开关已关闭")
            return
        }
        val nodeId = boundNodeId ?: run {
            updateState { it.copy(lastError = "未发现已连接的手表") }
            return
        }
        // boundNodeId 来自官方 NodeApi.getConnectedNodes()，它比包探测更能证明 SDK 通道可用。
        // 某些 Mi Fitness 版本不暴露可 resolveService 的 Action，不能因为 sdkIntegrated=false
        // 阻断已经发现节点的实际消息发送。
        if (!pushing.compareAndSet(false, true)) {
            pendingOrdersForPush = snapshot
            log("订单快照推送排队: 当前已有发送任务, incomplete=${orders.size}, completed=${completedOrders.size}")
            return
        }
        if (pendingOrdersForPush == snapshot) pendingOrdersForPush = null

        try {
            // 全量快照 + 版本号，官方 demo 式分片 JSON
            latestSnapshotVersion = nextSnapshotVersion()
            val frames = WearableSyncProtocol.encodeCodesFrames(
                orders,
                completedOrders,
                latestSnapshotVersion,
            )
            log("开始发送订单快照: nodeId=$nodeId, snapshotVersion=$latestSnapshotVersion, frames=${frames.size}, incomplete=${orders.size}, completed=${completedOrders.size}")
            val delivered = sendMutex.withLock {
                var allDelivered = true
                for ((index, frame) in frames.withIndex()) {
                    log("发送订单快照分片: ${index + 1}/${frames.size}, bytes=${frame.size}")
                    val ok = runBridgeCall("发送手表同步数据 ${index + 1}/${frames.size}") {
                        withContext(Dispatchers.IO) { bridge.sendMessage(appContext, frame) }
                    } ?: false
                    if (!ok) {
                        log("订单快照分片发送失败: ${index + 1}/${frames.size}, lastError=${bridge.lastError}")
                        allDelivered = false
                        break
                    }
                    delay(FRAME_DELAY_MS)
                }
                allDelivered
            }
            if (delivered) {
                latestSnapshotDirty = false
                lastNodeSeenAt = System.currentTimeMillis()
                updateState {
                    it.copy(
                        connected = true,
                        lastSyncAt = System.currentTimeMillis(),
                        lastSnapshotVersion = latestSnapshotVersion,
                        pendingOrdersCount = orders.size,
                        lastError = "",
                    )
                }
                log("订单快照发送完成: snapshotVersion=$latestSnapshotVersion, incomplete=${orders.size}, completed=${completedOrders.size}")
            } else {
                log("订单快照发送未完成: snapshotVersion=$latestSnapshotVersion, count=${orders.size}, lastError=${bridge.lastError}")
                updateState { it.copy(lastError = bridge.lastError ?: "消息发送失败") }
            }
        } finally {
            pushing.set(false)
            val retryOrders = pendingOrdersForPush
            pendingOrdersForPush = null
            if (retryOrders != null && retryOrders != snapshot && _enabled.value && isConnected()) {
                log("补发发送期间更新的最新订单快照: incomplete=${retryOrders.incomplete.size}, completed=${retryOrders.completed.size}")
                requestSnapshotPush(retryOrders.incomplete, retryOrders.completed)
            }
        }
    }

    // ═══════════ 消息接收 ═══════════

    private fun handleIncoming(bytes: ByteArray) {
        log("手表消息详情: ${describeBytes(bytes)}")
        if (!_enabled.value) {
            log("手表消息忽略: 同步开关已关闭, bytes=${bytes.size}")
            return
        }
        // 能收到手表消息本身就是在线证明，优先于一次可能超时的节点发现。
        lastNodeSeenAt = System.currentTimeMillis()
        updateState { it.copy(connected = true) }
        val source = dataSource
        val msg = WearableSyncProtocol.parseIncoming(bytes)
        log("手表消息解析结果: ${describeIncoming(msg)}, dataSource=${dataSource != null}")
        when (msg) {
            is WearableSyncProtocol.IncomingMessage.Handshake -> {
                if (msg.count in 0..2) {
                    updateState { it.copy(lastAckAt = System.currentTimeMillis()) }
                    if (msg.count < 2) {
                        scope.launch {
                            sendToWear(
                                "回复手表握手 count=${msg.count + 1}",
                                WearableSyncProtocol.encodeHandshake(msg.count + 1),
                            )
                        }
                    }
                    log("手表握手回调: count=${msg.count}, version=${msg.version}")
                } else {
                    log("忽略非法手表握手: count=${msg.count}")
                }
            }
            is WearableSyncProtocol.IncomingMessage.Hello -> {
                // ★ 打开即同步：立即补发最新全量快照。
                // App 后台 ViewModel 已销毁（dataSource 为 null）时直连 DAO 兜底，保证后台可应答
                scope.launch {
                    // 先给手表明确的业务层 ready，再发送较大的快照；手表据此区分
                    // 「底层通道已连接」和「手机监听/订单处理器确实已准备好」。
                    sendToWear("回复手表业务 ready", WearableSyncProtocol.encodeReady())
                    val snapshot = if (source != null) {
                        OrdersSnapshot(source.incompleteOrders.value, source.completedOrders.value)
                    } else {
                        val dao = OrderDatabase.getDatabase(appContext).orderDao()
                        val all = dao.getAllOrdersList()
                        OrdersSnapshot(
                            incomplete = all.filter { !it.isCompleted && !it.needsRuleCorrection },
                            completed = all.filter { it.isCompleted },
                        )
                    }
                    log("收到 hello，准备补发全量订单: incomplete=${snapshot.incomplete.size}, completed=${snapshot.completed.size}")
                    pushSnapshot(snapshot)
                }
            }
            is WearableSyncProtocol.IncomingMessage.Ping -> {
                // 闲时心跳：手表发 ping 证明在线（「检测到 App 在线」），手机端回 pong 并更新手表在线时间
                log("收到手表心跳 ping")
                updateState { it.copy(lastAckAt = System.currentTimeMillis()) }
                scope.launch {
                    var nodeId = boundNodeId
                    if (nodeId == null) {
                        // 节点信息丢失（Mi Fitness 服务断开/进程被拉起后尚未刷新）兜底：重新发现节点再回 pong
                        nodeId = runBridgeCall("心跳重新查找手表") {
                            withContext(Dispatchers.IO) { bridge.findNodeId(appContext) }
                        }
                        if (nodeId != null) {
                            boundNodeId = nodeId
                        }
                    }
                    if (nodeId == null) {
                        log("pong 回复失败：无节点")
                        return@launch
                    }
                    // 心跳只发送 pong。ready 属于业务握手消息，已在监听建立和 hello
                    // 路径发送；每次 ping 再附带 ready 会让性能有限的手表连续处理两条回包，
                    // 造成页面状态刷新抖动甚至短暂卡顿。
                    val ok = sendToWear("回复手表心跳", WearableSyncProtocol.encodePong())
                    // pong 回复失败不覆盖 lastError（心跳尽力而为，不影响主同步状态）
                    if (!ok) {
                        log("pong 回复失败")
                    } else {
                        log("pong 回复成功: nodeId=$nodeId")
                    }
                }
            }
            is WearableSyncProtocol.IncomingMessage.Done -> {
                // 手表按订单 ID 请求完成，处理后回传明确结果
                handleDone(msg.orderId, msg.requestId)
            }
            else -> {
                // 坏 JSON / 未知 type：兜底，不崩溃
                log("收到无法识别的手表消息: ${describeIncoming(msg)}")
                updateState { it.copy(lastError = "收到无法识别的消息") }
            }
        }
    }

    private fun handleDone(orderId: String, requestId: String?) {
        val normalizedOrderId = orderId.trim()
        if (normalizedOrderId.isBlank()) {
            log("手表完成请求忽略: orderId 为空, requestId=$requestId")
            return
        }
        log("手表完成请求开始: orderId=$normalizedOrderId, requestId=$requestId, dataSource=${dataSource != null}")
        updateState {
            it.copy(lastAckAt = System.currentTimeMillis(), lastAckOrderId = normalizedOrderId)
        }
        scope.launch {
            val result = doneMutex.withLock {
                val cacheKey = requestId?.takeIf { it.isNotBlank() }?.let {
                    "$normalizedOrderId:$it"
                }
                cacheKey?.let { doneResults[it] }?.let {
                    log("重复完成请求复用结果: orderId=$normalizedOrderId, requestId=$requestId")
                    return@withLock it
                }
                val computed = try {
                    val source = dataSource
                    // 优先走当前 ViewModel，保留通知取消/分组整理等业务副作用；
                    // 若数据源已解绑、过期或返回 false，直接回退到同一 Room 数据库，
                    // 确保手表回执不会因为 UI/ViewModel 生命周期而丢失。
                    val handledBySource = if (source != null) {
                        runCatching {
                            source.markAsCompletedFromWearable(normalizedOrderId)
                        }.onFailure { error ->
                            log("ViewModel 处理完成请求异常: orderId=$normalizedOrderId, error=${error::class.java.simpleName}:${error.message}")
                        }.getOrDefault(false)
                    } else {
                        false
                    }
                    val handledByDatabase = if (!handledBySource) {
                        markAsCompletedWithoutSource(normalizedOrderId)
                    } else {
                        false
                    }
                    val success = handledBySource || handledByDatabase
                    log("手表完成请求处理结果: orderId=$normalizedOrderId, viewModel=$handledBySource, databaseFallback=$handledByDatabase, success=$success")
                    if (success) {
                        CompletionResult(success = true, message = null)
                    } else {
                        CompletionResult(success = false, message = "手机端未找到该订单")
                    }
                } catch (error: Throwable) {
                    reportBridgeFailure("处理手表完成请求", error)
                    CompletionResult(success = false, message = "手机端更新订单失败")
                }
                if (cacheKey != null) {
                    if (doneResults.size >= DONE_RESULT_CACHE_SIZE) {
                        val iterator = doneResults.entries.iterator()
                        if (iterator.hasNext()) {
                            iterator.next()
                            iterator.remove()
                        }
                    }
                    doneResults[cacheKey] = computed
                }
                computed
            }

            if (!result.success) {
                updateState { it.copy(lastError = result.message) }
            }
            sendDoneResultToWear(normalizedOrderId, requestId, result)
        }
    }

    private suspend fun markAsCompletedWithoutSource(orderId: String): Boolean {
        val dao = OrderDatabase.getDatabase(appContext).orderDao()
        val order = dao.getOrderById(orderId)
        if (order == null) {
            log("数据库完成兜底未找到订单: orderId=$orderId")
            return false
        }
        log("数据库完成兜底查询: orderId=$orderId, isCompleted=${order.isCompleted}")
        // 兜底路径通常发生在应用退到后台、ViewModel 已销毁时，必须自行清理通知和闹钟。
        val notificationHelper = NotificationHelper(appContext)
        runCatching { notificationHelper.cancelNotification(orderId) }
        runCatching {
            NotificationScheduler.cancel(appContext, NotificationScheduler.getOrderRequestCode(orderId))
        }
        if (order.isCompleted) return true

        val groupId = order.groupId
        dao.markAsCompleted(orderId, System.currentTimeMillis())

        if (groupId != null) {
            val groupDao = OrderDatabase.getDatabase(appContext).orderGroupDao()
            val group = groupDao.getGroupById(groupId)
            if (group != null && !group.isCompleted) {
                val groupOrders = dao.getAllOrdersList().filter { it.groupId == groupId }
                val incompleteCount = groupOrders.count { !it.isCompleted }
                if (incompleteCount < 2) {
                    groupOrders.forEach { dao.update(it.copy(groupId = null)) }
                    groupDao.deleteGroup(group)
                    runCatching { notificationHelper.cancelGroupNotification(groupId) }
                    runCatching {
                        NotificationScheduler.cancel(
                            appContext,
                            NotificationScheduler.getGroupRequestCode(groupId),
                        )
                    }
                } else {
                    groupDao.updateOrderCount(groupId, incompleteCount)
                    val pendingOrders = groupOrders.filter { !it.isCompleted }
                    runCatching {
                        notificationHelper.showGroupNotification(
                            group.copy(orderCount = incompleteCount),
                            pendingOrders,
                        )
                    }
                }
            }
        }

        val completed = dao.getOrderById(orderId)?.isCompleted == true
        log("数据库完成兜底写入: orderId=$orderId, success=$completed")
        return completed
    }

    private suspend fun sendDoneResultToWear(
        orderId: String,
        requestId: String?,
        result: CompletionResult,
    ) {
        if (!_enabled.value || !isConnected() || orderId.isBlank()) {
            log("手表完成结果跳过: enabled=${_enabled.value}, connected=${isConnected()}, orderId=$orderId")
            return
        }
        log("准备回传手表完成结果: orderId=$orderId, requestId=$requestId, success=${result.success}, message=${result.message}")
        val delivered = sendToWear(
            "同步手表完成结果",
            WearableSyncProtocol.encodeDoneResult(
                orderId = orderId,
                success = result.success,
                message = result.message,
                requestId = requestId,
            ),
        )
        log("手表完成结果回传结束: orderId=$orderId, delivered=$delivered, lastError=${bridge.lastError}")
    }

    /** 在统一发送锁内提交一条消息；SDK Task 成功只代表提交成功，不代表业务已处理。 */
    private suspend fun sendToWear(action: String, bytes: ByteArray): Boolean =
        sendMutex.withLock {
            runBridgeCall(action) {
                withContext(Dispatchers.IO) { bridge.sendMessage(appContext, bytes) }
            } ?: false
        }

    private data class CompletionResult(
        val success: Boolean,
        val message: String?,
    )

    private data class OrdersSnapshot(
        val incomplete: List<OrderEntity>,
        val completed: List<OrderEntity>,
    )

    // ═══════════ 辅助 ═══════════

    private inline fun updateState(transform: (State) -> State) {
        _state.value = transform(_state.value)
    }

    private suspend fun <T> runBridgeCall(action: String, block: suspend () -> T): T? {
        return try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            reportBridgeFailure(action, error)
            null
        }
    }

    private fun reportBridgeFailure(action: String, error: Throwable) {
        Log.w(TAG, "${action}失败: ${error::class.java.simpleName}: ${error.message}", error)
        updateState { it.copy(lastError = "${action}失败，请检查 Mi Fitness 与手表连接") }
    }

    private fun bridgeLastError(fallback: String?): String? {
        return runCatching { bridge.lastError }.getOrNull() ?: fallback
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
    }

    private fun describeBytes(bytes: ByteArray): String {
        val text = bytes.toString(Charsets.UTF_8)
            .replace('\n', ' ')
            .replace('\r', ' ')
        val preview = if (text.length <= 512) text else text.take(512) + "..."
        val hexLength = minOf(bytes.size, 96)
        val hex = buildString(hexLength * 3) {
            for (i in 0 until hexLength) {
                if (i > 0) append(' ')
                append(bytes[i].toInt().and(0xff).toString(16).padStart(2, '0').uppercase())
            }
            if (bytes.size > hexLength) append(" ...")
        }
        return "bytes=${bytes.size}, utf8=$preview, hex=$hex"
    }

    private fun describeIncoming(message: WearableSyncProtocol.IncomingMessage): String =
        when (message) {
            is WearableSyncProtocol.IncomingMessage.Hello -> "Hello"
            is WearableSyncProtocol.IncomingMessage.Ping -> "Ping"
            is WearableSyncProtocol.IncomingMessage.Handshake ->
                "Handshake(count=${message.count}, version=${message.version})"
            is WearableSyncProtocol.IncomingMessage.Done ->
                "Done(orderId=${message.orderId}, requestId=${message.requestId})"
            is WearableSyncProtocol.IncomingMessage.Unsupported ->
                "Unsupported(type=${message.type})"
            is WearableSyncProtocol.IncomingMessage.Invalid -> "Invalid"
        }

    /**
     * 进程被系统拉起后恢复穿戴通道：确保已启动，若开关开启则重新发现节点并注册消息监听。
     * 由 [com.Badnng.moe.service.KeepAliveService] 在 START_STICKY 重启时调用；
     * 回 pong 不依赖订单数据源，因此无 dataSource 时也能保证后台心跳应答。
     */
    fun ensureWearChannel() {
        val listenerRegistered = if (_enabled.value) {
            runCatching { bridge.isListenerRegistered }.getOrDefault(false)
        } else {
            false
        }
        log("自启动恢复穿戴通道: enabled=${_enabled.value}, started=${_state.value.started}, listener=$listenerRegistered")
        if (!_enabled.value) {
            log("自启动跳过穿戴注册: 手表同步开关未开启")
            return
        }
        ensureStarted()
        forceMessageRebind = true
        refreshNode()
    }

    /**
     * 保留给真正的通信宿主销毁场景使用。
     *
     * KeepAliveService 只负责后台通知/进程保活，可能会在 MainActivity 回到前台时被
     * 正常停止；它的销毁不能等价于穿戴通信结束，否则每次打开手机 UI 都会删除官方
     * MessageApi listener，导致手表端状态短暂在线后又失联。真正结束同步只由
     * [setEnabled](false)、[stop] 或进程销毁路径负责。
     */
    fun onServiceDestroyed() {
        scope.launch(Dispatchers.IO) {
            runCatching { bridge.removeMessageListener(appContext) }
                .onFailure { error -> reportBridgeFailure("注销手表消息监听", error) }
            runCatching { bridge.removeConnectionListener(appContext) }
                .onFailure { error -> reportBridgeFailure("注销手表状态监听", error) }
        }
    }

    /** UI 可观察的同步状态。 */
    data class State(
        val started: Boolean = false,
        val sdkIntegrated: Boolean = true,
        val discovering: Boolean = false,
        val connected: Boolean = false,
        /** 手机端看到的官方 ITEM_CONNECTION 状态；与消息同步是否有待处理订单分开。 */
        val transportConnected: Boolean = false,
        val nodeId: String? = null,
        val deviceName: String? = null,
        val permissionGranted: Boolean = false,
        /** 通知权限（NOTIFY）是否已授予；不依赖手表端快应用。 */
        val notifyPermissionGranted: Boolean = false,
        /** 设备管理权限（DEVICE_MANAGER）是否已授予；依赖手表端快应用已安装。 */
        val deviceManagerPermissionGranted: Boolean = false,
        /** 用户是否开启「通知权限」开关；关闭后不再向手表发送系统通知。 */
        val notifyPermissionEnabled: Boolean = true,
        /** 手表端快应用（rpk）安装状态；null 表示未知。用于提示「需先安装快应用」。 */
        val watchAppInstalled: Boolean? = null,
        val pendingOrdersCount: Int = 0,
        val lastSyncAt: Long = 0L,
        val lastSnapshotVersion: Long = 0L,
        val lastAckAt: Long = 0L,
        val lastAckOrderId: String? = null,
        val lastError: String? = null,
    )

    companion object {
        private const val TAG = "WearableSyncManager"
        private const val PREFS_NAME = "wearable_sync"
        private const val KEY_ENABLED = "wearable_sync_enabled"
        private const val KEY_SNAPSHOT_VERSION = "wearable_snapshot_version"
        private const val KEY_NOTIFY_PERMISSION_ENABLED = "wearable_notify_permission_enabled"
        private const val DEFAULT_PERMISSION_ENABLED = true
        private const val DEFAULT_ENABLED = false
        private const val DEBOUNCE_MS = 500L
        /** 穿戴通道自愈快速重试间隔：监听未注册/节点发现失败时 5s 重试。 */
        private const val WATCHDOG_RETRY_MS = 5_000L
        /** Mi Fitness 服务重连/节点发现短暂失败时的连接状态保活窗口。 */
        private const val NODE_LOSS_GRACE_MS = 30_000L
        /** JSON 回退分片帧之间的发送间隔。XMS/手表端处理能力有限，必须给每帧留出缓冲时间。 */
        private const val FRAME_DELAY_MS = 180L
        /** 手表端仅展示近期订单，避免历史数据造成缓存和 list 同时膨胀。 */
        private const val MAX_WATCH_INCOMPLETE_ORDERS = 20
        private const val MAX_WATCH_COMPLETED_ORDERS = 10
        private const val DONE_RESULT_CACHE_SIZE = 64

        @Volatile
        private var instance: WearableSyncManager? = null

        /** Application 级单例。 */
        fun getInstance(context: Context): WearableSyncManager =
            instance ?: synchronized(this) {
                instance ?: WearableSyncManager(context.applicationContext).also { instance = it }
            }

        /**
         * 识别入库后的统一手表通知入口：任何识别方式保存订单后调用。
         *
         * 不依赖 OrderViewModel/attachSource：自启动进程（分享/识屏/短信/划词）里
         * 本管理器可能尚未绑定订单流，但系统通知链路（节点发现→授权→
         * NotifyApi.sendNotify）是独立的，此处可直接触达。
         */
        fun notifyOrderSaved(context: Context, order: OrderEntity) {
            val title = order.brandName?.takeIf { it.isNotBlank() }
                ?: "新的${order.orderType}通知"
            val message = buildString {
                append(order.takeoutCode)
                if (!order.qrCodeData.isNullOrBlank()) append("，二维码已同步，可到手表端查看")
            }
            getInstance(context).sendNewOrderNotify(order.id, title, message)
        }
    }

    private fun nextSnapshotVersion(): Long {
        val next = maxOf(latestSnapshotVersion + 1L, System.currentTimeMillis())
        latestSnapshotVersion = next
        prefs.edit().putLong(KEY_SNAPSHOT_VERSION, next).apply()
        log("生成单调递增快照版本: $next")
        return next
    }
}

/**
 * 手表同步所需的数据源抽象，由 [com.Badnng.moe.viewmodel.OrderViewModel] 实现，
 * 避免 manager 直接依赖 Compose ViewModel。SDK 逻辑不进 ViewModel（Plan §5.3）。
 */
interface WearableSyncSource {
    /** 未完成订单流（变化时触发推送）。 */
    val incompleteOrders: kotlinx.coroutines.flow.StateFlow<List<OrderEntity>>

    /** 已完成订单流（用于手表端“已取”历史页）。 */
    val completedOrders: kotlinx.coroutines.flow.StateFlow<List<OrderEntity>>

    /**
     * 按订单 ID 幂等标记完成并返回数据库处理结果，供手表端显示成功/失败。
     * 未找到订单返回 false，已完成订单返回 true。
     */
    suspend fun markAsCompletedFromWearable(orderId: String): Boolean
}
