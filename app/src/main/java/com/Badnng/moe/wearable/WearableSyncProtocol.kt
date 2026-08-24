package com.Badnng.moe.wearable

import com.Badnng.moe.data.db.OrderEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * 澎湃记 × 小米手表 取餐码同步数据协议（v2，见 PLAN §5.4）。
 *
 * 本层与具体穿戴 SDK 完全解耦（仅依赖 org.json 与 OrderEntity），
 * 负责两端消息的统一编解码与兜底解析，保证坏 JSON / 未知 type / 缺字段不崩溃。
 *
 * 消息方向：
 *  - 手机→手表  `codes`：未完成与已完成订单全量快照（订单变化推送；收到 hello 时补发）
 *  - 手表→手机  `done`：完成请求，手机端按 orderId 幂等处理并回传确认
 *  - 手机→手表  `done_result`：完成结果，手表端按 orderId 成功移除或失败恢复
 *  - 手表→手机  `hello`：打开即同步请求，手机端立即补发最新全量快照
 *  - 手表→手机  `ping`：闲时心跳（证明「检测到 App 在线」），手机端回复 `pong`
 *  - 手机→手表  `pong`：心跳应答，手表端据此维持「已连接」状态
 */
object WearableSyncProtocol {

    /** 协议 schema 版本，用于未来不兼容升级时区分解析策略。 */
    const val SCHEMA_VERSION = 2

    /** 协议字段名（与手表端约定一致）。 */
    object Key {
        const val TYPE = "type"
        const val SCHEMA_VERSION = "schemaVersion"
        const val SNAPSHOT_VERSION = "snapshotVersion"
        const val PAYLOAD = "payload"
        const val COMPLETED_PAYLOAD = "completedPayload"
        const val ORDER_ID = "orderId"
        const val CODE = "code"
        const val ORDER_TYPE = "orderType"
        const val BRAND_NAME = "brandName"
        const val PICKUP_LOCATION = "pickupLocation"
        const val QR_CODE_DATA = "qrCodeData"
        const val CREATED_AT = "createdAt"
        const val COMPLETED_AT = "completedAt"
        const val GROUP_ID = "groupId"
        const val REQUEST_ID = "requestId"
        const val SUCCESS = "success"
        const val MESSAGE = "message"
    }

    object MsgType {
        const val CODES = "codes"   // 手机→手表 全量快照
        const val DONE = "done"     // 手表→手机 完成请求
        const val DONE_RESULT = "done_result" // 手机→手表 完成结果
        const val HELLO = "hello"   // 手表→手机 打开即同步
        const val PING = "ping"     // 手表→手机 闲时心跳
        const val PONG = "pong"     // 手机→手表 心跳应答
        const val READY = "ready"   // 手机→手表 业务监听已就绪
    }

    const val HANDSHAKE_TAG = "__hs__"

    /** 单条待同步订单的协议表示。 */
    data class SnapshotOrder(
        val orderId: String,
        val code: String,
        val orderType: String,
        val brandName: String?,
        val pickupLocation: String?,
        val qrCodeData: String?,
        /** 订单创建时间（毫秒时间戳），来自 OrderEntity.createdAt，供手表端卡片显示时间。 */
        val createdAt: Long? = null,
        /** 完成时间（毫秒时间戳），供手表端已取列表排序/显示。 */
        val completedAt: Long? = null,
        /** 同一组识别结果的分组 ID；为空表示独立订单。 */
        val groupId: Long? = null,
    )

    /** 手机→手表 全量快照消息。 */
    data class CodesMessage(
        val snapshotVersion: Long,
        val orders: List<SnapshotOrder>,
    )

    /** 手表→手机 回执消息。 */
    data class DoneMessage(
        val orderId: String,
        val requestId: String?,
    )

    // ═══════════ 手机端：推送编码 ═══════════

    /** 将未完成订单列表编码为 `codes` 全量快照 JSON 字节。qrCodeData 超长则置空跳过（不截断）。 */
    fun encodeCodes(orders: List<OrderEntity>, snapshotVersion: Long): ByteArray =
        encodeCodes(orders, emptyList(), snapshotVersion)

    /** 将未完成与已完成订单编码为同一份全量快照。 */
    fun encodeCodes(
        orders: List<OrderEntity>,
        completedOrders: List<OrderEntity>,
        snapshotVersion: Long,
    ): ByteArray =
        JSONObject()
            .put(Key.TYPE, MsgType.CODES)
            .put(Key.SCHEMA_VERSION, SCHEMA_VERSION)
            .put(Key.SNAPSHOT_VERSION, snapshotVersion)
            .put(Key.PAYLOAD, JSONArray().apply {
                orders.forEach { order -> put(toSnapshotOrder(order).toJson()) }
            })
            .put(Key.COMPLETED_PAYLOAD, JSONArray().apply {
                completedOrders.forEach { order -> put(toSnapshotOrder(order, includeQrCode = false).toJson()) }
            })
            .toString()
            .toByteArray(Charsets.UTF_8)

    /**
     * 将未完成订单列表编码为「官方 demo 式分片」全量快照帧序列。
     *
     * 帧协议与 open-vela/packages_apps `interconnect_image_demo` 对齐：
     *  - header: {"type":"header","totalChunks":N,"chunkSize":N,"totalSize":N}
     *  - data:   {"type":"data","index":N,"chunk":"<原 JSON 文本分片>"}
     *  - end:    {"type":"end"}
     */
    fun encodeCodesFrames(orders: List<OrderEntity>, snapshotVersion: Long): List<ByteArray> =
        encodeCodesFrames(orders, emptyList(), snapshotVersion)

    /** 将未完成与已完成订单编码为官方 demo 式分片全量快照。 */
    fun encodeCodesFrames(
        orders: List<OrderEntity>,
        completedOrders: List<OrderEntity>,
        snapshotVersion: Long,
    ): List<ByteArray> {
        val fullJson = JSONObject()
            .put(Key.TYPE, MsgType.CODES)
            .put(Key.SCHEMA_VERSION, SCHEMA_VERSION)
            .put(Key.SNAPSHOT_VERSION, snapshotVersion)
            .put(Key.PAYLOAD, JSONArray().apply {
                orders.forEach { order -> put(toSnapshotOrder(order).toJson()) }
            })
            .put(Key.COMPLETED_PAYLOAD, JSONArray().apply {
                completedOrders.forEach { order -> put(toSnapshotOrder(order, includeQrCode = false).toJson()) }
            })
            .toString()
        val totalSize = fullJson.length
        val totalChunks = ((totalSize + CHUNK_SIZE - 1) / CHUNK_SIZE).coerceAtLeast(1)

        val frames = ArrayList<ByteArray>()
        frames += JSONObject()
            .put("type", "header")
            .put("totalChunks", totalChunks)
            .put("chunkSize", CHUNK_SIZE)
            .put("totalSize", totalSize)
            .toString()
            .toByteArray(Charsets.UTF_8)

        for (i in 0 until totalChunks) {
            val start = i * CHUNK_SIZE
            val end = minOf(start + CHUNK_SIZE, totalSize)
            val chunk = fullJson.substring(start, end)
            frames += JSONObject()
                .put("type", "data")
                .put("index", i)
                .put("chunk", chunk)
                .toString()
                .toByteArray(Charsets.UTF_8)
        }

        frames += JSONObject()
            .put("type", "end")
            .toString()
            .toByteArray(Charsets.UTF_8)

        return frames
    }

    /** 单帧 JSON 文本切分长度（字符数）。与手表端 FRAME_CHUNK_SIZE 对应。 */
    const val CHUNK_SIZE = 3000

    /** 将 `pong` 心跳应答编码为 JSON 字节（回复手表端 `ping`，证明手机端 App 在线）。 */
    fun encodePong(): ByteArray =
        JSONObject()
            .put(Key.TYPE, MsgType.PONG)
            .toString()
            .toByteArray(Charsets.UTF_8)

    /** 手机端消息监听与订单处理器已就绪，作为业务层握手回执。 */
    fun encodeReady(): ByteArray =
        JSONObject()
            .put(Key.TYPE, MsgType.READY)
            .put(Key.SCHEMA_VERSION, SCHEMA_VERSION)
            .toString()
            .toByteArray(Charsets.UTF_8)

    /** 对齐 Snapnotes 的手机主动握手：count=0 首包，手表逐级回复到 count=2。 */
    fun encodeHandshake(count: Int, version: Int = SCHEMA_VERSION): ByteArray =
        JSONObject()
            .put("tag", HANDSHAKE_TAG)
            .put("count", count)
            .put("version", version)
            .toString()
            .toByteArray(Charsets.UTF_8)

    /** 将手机端已处理的完成状态回传给手表。 */
    fun encodeDone(orderId: String, requestId: String? = null): ByteArray =
        JSONObject()
            .put(Key.TYPE, MsgType.DONE)
            .put(Key.ORDER_ID, orderId)
            .apply { requestId?.let { put(Key.REQUEST_ID, it) } }
            .toString()
            .toByteArray(Charsets.UTF_8)

    /** 返回手机端按订单 ID 处理完成请求的结果。 */
    fun encodeDoneResult(
        orderId: String,
        success: Boolean,
        message: String? = null,
        requestId: String? = null,
    ): ByteArray = JSONObject()
        .put(Key.TYPE, MsgType.DONE_RESULT)
        .put(Key.ORDER_ID, orderId)
        .put(Key.SUCCESS, success)
        .apply {
            message?.takeIf { it.isNotBlank() }?.let { put(Key.MESSAGE, it) }
            requestId?.let { put(Key.REQUEST_ID, it) }
        }
        .toString()
        .toByteArray(Charsets.UTF_8)

    private fun SnapshotOrder.toJson(): JSONObject {
        val obj = JSONObject()
            .put(Key.ORDER_ID, orderId)
            .put(Key.CODE, code)
            .put(Key.ORDER_TYPE, orderType)
        brandName?.let { obj.put(Key.BRAND_NAME, it) }
        pickupLocation?.let { obj.put(Key.PICKUP_LOCATION, it) }
        qrCodeData?.let { obj.put(Key.QR_CODE_DATA, it) }
        createdAt?.let { obj.put(Key.CREATED_AT, it) }
        completedAt?.let { obj.put(Key.COMPLETED_AT, it) }
        groupId?.let { obj.put(Key.GROUP_ID, it) }
        return obj
    }

    private fun toSnapshotOrder(
        order: OrderEntity,
        includeQrCode: Boolean = true,
    ): SnapshotOrder = SnapshotOrder(
        orderId = order.id,
        code = order.takeoutCode,
        orderType = order.orderType,
        brandName = order.brandName,
        pickupLocation = order.pickupLocation,
        // 已取页只显示历史码，不需要二维码；省掉长字符串可显著降低手表解析和渲染压力。
        qrCodeData = if (includeQrCode) sanitizeQrCodeData(order.qrCodeData) else null,
        createdAt = order.createdAt,
        completedAt = order.completedAt,
        groupId = order.groupId,
    )

    /**
     * 手机端识别时已过滤纯 URL 二维码；此处对超长/异常数据（如异常 URL）做兜底：
     * 返回 `null` 即**跳过该字段不发送**（手表端该订单不展示二维码入口）。
     *
     * ⚠️ 绝不截断二维码数据——截断会破坏二维码内容使其无法扫码，还会让手表端拿到
     * 一份「看似可用实则错误」的数据。超限一律置空跳过，保留订单其余字段。
     */
    private fun sanitizeQrCodeData(data: String?): String? {
        if (data == null) return null
        return if (data.length <= MAX_QR_CODE_LENGTH) data else null
    }

    /** 二维码数据最大长度。超过则整段置空跳过（不截断，避免破坏二维码）。 */
    const val MAX_QR_CODE_LENGTH = 512

    // ═══════════ 手机端：接收解析（hello / done）═══════════

    /**
     * 解析手表端发来的任意字节消息。
     * @return 解析结果；坏 JSON / 未知 type / 缺关键字段时返回不支持类型，绝不抛出。
     */
    fun parseIncoming(raw: ByteArray): IncomingMessage {
        var json = runCatching {
            JSONObject(String(raw, Charsets.UTF_8))
        }.getOrNull() ?: return IncomingMessage.Invalid

        // 兼容部分 Vela 版本/旧快应用使用的外层 data 信封：
        // {"data":"{\"type\":\"hello\"}"} 或 {"data":{"type":"hello"}}。
        if (json.optString(Key.TYPE).isBlank()) {
            val nested = json.opt("data")
            if (nested is JSONObject) {
                json = nested
            } else if (nested is String) {
                json = runCatching { JSONObject(nested) }.getOrNull() ?: json
            }
        }

        val type = json.optString(Key.TYPE)
        if (json.optString("tag") == HANDSHAKE_TAG) {
            return IncomingMessage.Handshake(
                count = json.optInt("count", -1),
                version = json.optInt("version", 0),
            )
        }
        return when (type) {
            MsgType.HELLO -> IncomingMessage.Hello
            MsgType.PING -> IncomingMessage.Ping
            MsgType.DONE -> {
                val orderId = json.optString(Key.ORDER_ID)
                if (orderId.isBlank()) {
                    IncomingMessage.Invalid
                } else {
                    IncomingMessage.Done(
                        orderId = orderId,
                        requestId = json.optString(Key.REQUEST_ID).ifEmpty { null },
                    )
                }
            }
            else -> IncomingMessage.Unsupported(type)
        }
    }

    sealed interface IncomingMessage {
        /** 检测到的不受支持消息类型（预留扩展，当前忽略式兜底）。 */
        data class Unsupported(val type: String) : IncomingMessage
        /** 坏 JSON / 未知 type / 缺关键字段。 */
        data object Invalid : IncomingMessage
        /** 手表请求最新全量快照（打开即同步）。 */
        data object Hello : IncomingMessage
        /** 手表闲时心跳（手机端应回复 `pong`）。 */
        data object Ping : IncomingMessage
        data class Handshake(val count: Int, val version: Int) : IncomingMessage
        /** 取餐完成回执。 */
        data class Done(val orderId: String, val requestId: String?) : IncomingMessage
    }
}
