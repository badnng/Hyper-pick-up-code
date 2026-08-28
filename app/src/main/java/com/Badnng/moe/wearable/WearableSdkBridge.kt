package com.Badnng.moe.wearable

import android.content.Context
import android.util.Log

/**
 * 小米穿戴官方 XMS Wearable SDK 的抽象隔离层。
 *
 * `app/libs/xms-wearable-lib_1.4_release.aar` 提供官方 API；具体 AAR 调用集中在
 * [XiaomiWearableBridge]，上层同步逻辑只依赖这里的最小接口，便于生命周期和后台策略
 * 与 SDK 解耦。这里不使用反射或手写 AIDL 替代官方 SDK。
 *
 * 对应官方 API（文档 v1.4）：
 *   - NodeApi.getConnectedNodes() -> List<Node>（无权限）
 *   - AuthApi.requestPermission(nodeId, ...) / checkPermissions(...)
 *   - MessageApi.sendMessage(nodeId, bytes) -> Task<Void>
 *   - MessageApi.addListener(nodeId, OnMessageReceivedListener) / removeListener(...)
 *   - ServiceApi（可选）监听与 Mi Fitness 服务连接
 */
fun interface MessageListener {
    fun onMessage(bytes: ByteArray)
}

/** 官方 NodeApi.subscribe(ITEM_CONNECTION) 的轻量回调。 */
fun interface ConnectionListener {
    fun onConnectionChanged(nodeId: String, connected: Boolean)
}

interface WearableSdkBridge {

    /** 官方 AAR 已加载且 Mi Fitness XMS service 可解析。 */
    val isSdkAvailable: Boolean

    /** 通知权限（NOTIFY）是否已授予。不依赖手表端快应用，优先申请。 */
    val isNotifyPermissionGranted: Boolean
        get() = false

    /** 设备管理权限（DEVICE_MANAGER：状态查询/订阅 + 消息通道）是否已授予。依赖手表端快应用已安装。 */
    val isDeviceManagerPermissionGranted: Boolean
        get() = false

    /** 是否已授权所需权限（通知 + 设备管理）。 */
    val isPermissionGranted: Boolean
        get() = isNotifyPermissionGranted && isDeviceManagerPermissionGranted

    /**
     * 消息监听是否已注册。供上层「穿戴通道自愈轮询」判断监听是否丢失；
     * 实现不跟踪监听状态时默认视为已注册（默认 true，避免占位桥触发无意义自愈）。
     */
    val isListenerRegistered: Boolean
        get() = true

    /** 连接状态订阅是否已注册。 */
    val isConnectionListenerRegistered: Boolean
        get() = true

    /** 最近一次连接/权限/消息操作的可读错误；兼容旧 fake 实现的默认值。 */
    val lastError: String?
        get() = null

    /**
     * 发现已连接的手表节点 id。
     * 一次只能连接一个设备；断线后需重新发现，不缓存 nodeId。
     * @return 节点 id，无则返回 null。
     */
    fun findNodeId(context: Context): String?

    /**
     * 请求通知权限（NOTIFY）。
     * 该权限不依赖手表端快应用，应在设备管理权限之前申请。
     * @return 是否授予成功。
     */
    fun requestNotifyPermission(context: Context): Boolean

    /**
     * 请求设备管理权限（DEVICE_MANAGER）。
     * 该权限依赖手表端快应用（rpk）已安装，未安装时 Mi Fitness 会返回 “app not installed”。
     * @return 是否授予成功。
     */
    fun requestDeviceManagerPermission(context: Context): Boolean

    /**
     * 查询手表端对应快应用（rpk）是否已安装。
     * @return true/false；null 表示服务未返回有效结果。
     */
    fun isWatchAppInstalled(context: Context, nodeId: String): Boolean?

    /**
     * 向手表发送字节消息。
     * 官方返回 Task<Void>，表示消息已提交给穿戴服务；
     * 因此不将其当作业务 ACK。
     * @return 是否投递成功（提交成功），供上层决定最近同步时间是否更新。
     */
    fun sendMessage(context: Context, bytes: ByteArray): Boolean

    /**
     * 向手表发送一条系统通知（NotifyApi.sendNotify）。
     * 注意：手表端快应用无感知，点击通知不会自动跳转到 App 页面。
     * @return 是否提交成功。
     */
    fun sendSystemNotify(context: Context, title: String, message: String): Boolean

    /**
     * 注册消息监听。返回的监听器对象供 [removeMessageListener] 解绑。
     * 必须在 manager 生命周期内注册，结束时解绑。
     */
    fun setMessageListener(context: Context, listener: MessageListener)

    /** 注销消息监听。 */
    fun removeMessageListener(context: Context)

    /** 查询官方 ITEM_CONNECTION 状态；null 表示服务未返回有效结果。 */
    fun queryConnection(context: Context, nodeId: String): Boolean?

    /** 注册官方 ITEM_CONNECTION 状态订阅。 */
    fun setConnectionListener(context: Context, nodeId: String, listener: ConnectionListener): Boolean

    /** 注销官方 ITEM_CONNECTION 状态订阅。 */
    fun removeConnectionListener(context: Context)

    /** 销毁资源，结束连接相关管理。 */
    fun release()
}

/**
 * SDK 未集成时的明确占位桥。
 *
 * 没有真实 SDK 依赖时，所有能力判为「不可用/未获授权」，操作安全空转并记录日志，
 * **绝不伪造包名/方法或反射猜测调用**。这样上层逻辑可统一走接口，无需感知 SDK 缺失，
 * 且不会在 SDK 缺失时产生任何连接/发现/监听的副作用。
 */
class UnavailableWearableSdkBridge : WearableSdkBridge {

    override val isSdkAvailable: Boolean = false

    override val isNotifyPermissionGranted: Boolean = false

    override val isDeviceManagerPermissionGranted: Boolean = false

    override val isConnectionListenerRegistered: Boolean = false

    override fun findNodeId(context: Context): String? {
        log("Mi Fitness 服务未集成，跳过节点发现")
        return null
    }

    override fun requestNotifyPermission(context: Context): Boolean {
        log("Mi Fitness 服务未集成，无法请求通知权限")
        return false
    }

    override fun requestDeviceManagerPermission(context: Context): Boolean {
        log("Mi Fitness 服务未集成，无法请求设备管理权限")
        return false
    }

    override fun isWatchAppInstalled(context: Context, nodeId: String): Boolean? = null

    override fun sendMessage(context: Context, bytes: ByteArray): Boolean {
        log("Mi Fitness 服务未集成，跳过消息发送")
        return false
    }

    override fun sendSystemNotify(context: Context, title: String, message: String): Boolean {
        log("Mi Fitness 服务未集成，跳过系统通知发送")
        return false
    }

    override fun setMessageListener(context: Context, listener: MessageListener) {
        log("Mi Fitness 服务未集成，未注册消息监听")
    }

    override fun removeMessageListener(context: Context) {
        // 无监听可移除，安全空转
    }

    override fun queryConnection(context: Context, nodeId: String): Boolean? = null

    override fun setConnectionListener(
        context: Context,
        nodeId: String,
        listener: ConnectionListener,
    ): Boolean = false

    override fun removeConnectionListener(context: Context) {
        // 无监听可移除，安全空转
    }

    override fun release() {
        // 无可释放资源
    }

    private fun log(msg: String) {
        Log.w(TAG, msg)
    }

    companion object {
        private const val TAG = "WearableSdkBridge"
    }
}
