package com.Badnng.moe.wearable

import com.Badnng.moe.data.db.OrderEntity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearableSyncProtocolTest {

    private fun sampleOrder(
        id: String = "order-1",
        takeoutCode: String = "A-123456",
        qrCodeData: String? = "https://qr.example/x",
        brandName: String? = "蜜雪冰城",
        pickupLocation: String? = "A区3号柜",
        createdAt: Long? = null,
    ) = OrderEntity(
        id = id,
        takeoutCode = takeoutCode,
        qrCodeData = qrCodeData,
        screenshotPath = "/tmp/x.png",
        recognizedText = "取餐码",
        brandName = brandName,
        pickupLocation = pickupLocation,
        createdAt = createdAt ?: System.currentTimeMillis(),
    )

    private fun parseJson(bytes: ByteArray): JSONObject =
        JSONObject(String(bytes, Charsets.UTF_8))

    // ═══════════ encodeCodes ═══════════

    @Test
    fun encodeCodesCarriesEnvelopeFields() {
        val json = parseJson(WearableSyncProtocol.encodeCodes(listOf(sampleOrder()), snapshotVersion = 99L))

        assertEquals(WearableSyncProtocol.MsgType.CODES, json.optString("type"))
        assertEquals(2, json.optInt("schemaVersion"))
        assertEquals(99L, json.optLong("snapshotVersion"))
        assertTrue(json.optJSONArray("payload") is JSONArray)
    }

    @Test
    fun encodeCodesMapsOrderFieldsCorrectly() {
        val order = sampleOrder()
        val json = parseJson(WearableSyncProtocol.encodeCodes(listOf(order), snapshotVersion = 1L))
        val item = json.optJSONArray("payload").getJSONObject(0)

        assertEquals(order.id, item.optString("orderId"))          // orderId ← id
        assertEquals(order.takeoutCode, item.optString("code"))     // code ← takeoutCode
        assertEquals(order.orderType, item.optString("orderType"))
        assertEquals(order.brandName, item.optString("brandName"))
        assertEquals(order.pickupLocation, item.optString("pickupLocation"))
        assertEquals(order.qrCodeData, item.optString("qrCodeData"))
        assertEquals(order.createdAt, item.optLong("createdAt"))   // createdAt ← createdAt
    }

    @Test
    fun encodeCodesCarriesCreatedAtWhenNonNull() {
        // 显式指定创建时间（如 2026-08-15 12:00 UTC+8），序列化必须原样输出
        val createdAt = 1786766400000L
        val item = parseJson(WearableSyncProtocol.encodeCodes(listOf(sampleOrder(createdAt = createdAt)), snapshotVersion = 1L))
            .optJSONArray("payload").getJSONObject(0)

        assertEquals(true, item.has("createdAt"))
        assertEquals(createdAt, item.optLong("createdAt"))
    }

    @Test
    fun encodeCodesOmitsCreatedAtWhenNull() {
        // OrderEntity.createdAt 非空，encodeCodes 路径下 createdAt 恒非 null；
        // 此处直接构造 SnapshotOrder(createdAt = null) 验证可空字段序列化逻辑（null 不输出字段，与 brandName 等一致）
        val order = WearableSyncProtocol.SnapshotOrder(
            orderId = "order-1",
            code = "A-123456",
            orderType = "餐食",
            brandName = null,
            pickupLocation = null,
            qrCodeData = null,
            createdAt = null,
        )
        val toJson = WearableSyncProtocol::class.java.declaredMethods
            .single {
                it.name == "toJson" &&
                    it.parameterTypes.contentEquals(arrayOf(WearableSyncProtocol.SnapshotOrder::class.java))
            }
        toJson.isAccessible = true
        val item = toJson.invoke(WearableSyncProtocol, order) as JSONObject

        assertEquals(false, item.has("createdAt"))
    }

    @Test
    fun encodeCodesOmitsQrFieldWhenNull() {
        val order = sampleOrder(qrCodeData = null)
        val item = parseJson(WearableSyncProtocol.encodeCodes(listOf(order), snapshotVersion = 1L))
            .optJSONArray("payload").getJSONObject(0)

        assertEquals(false, item.has("qrCodeData"))
    }

    @Test
    fun encodeCodesSkipsOverlongQrData() {
        // 超长 qrCodeData 不得截断，应整段跳过（qrCodeData 字段不存在），保留订单其余字段
        val overlong = "x".repeat(2000)
        val order = sampleOrder(qrCodeData = overlong)
        val item = parseJson(WearableSyncProtocol.encodeCodes(listOf(order), snapshotVersion = 1L))
            .optJSONArray("payload").getJSONObject(0)

        assertEquals(false, item.has("qrCodeData"))
        assertEquals(order.id, item.optString("orderId"))          // 其余字段仍保留
        assertEquals(order.takeoutCode, item.optString("code"))
        assertEquals(order.brandName, item.optString("brandName"))
    }

    @Test
    fun encodeCodesKeepsQrDataAtMaxBoundary() {
        val boundary = "y".repeat(WearableSyncProtocol.MAX_QR_CODE_LENGTH)
        val item = parseJson(WearableSyncProtocol.encodeCodes(listOf(sampleOrder(qrCodeData = boundary)), snapshotVersion = 1L))
            .optJSONArray("payload").getJSONObject(0)

        assertEquals(true, item.has("qrCodeData"))
        assertEquals(boundary, item.optString("qrCodeData"))
    }

    @Test
    fun encodeCodesHandlesEmptyOrders() {
        val json = parseJson(WearableSyncProtocol.encodeCodes(emptyList(), snapshotVersion = 0L))
        assertEquals(0, json.optJSONArray("payload").length())
    }

    // ═══════════ parseIncoming ═══════════

    @Test
    fun parseIncomingParsesHello() {
        val msg = WearableSyncProtocol.parseIncoming("""{"type":"hello"}""".toByteArray())
        assertTrue(msg is WearableSyncProtocol.IncomingMessage.Hello)
    }

    @Test
    fun parseIncomingParsesDone() {
        val msg = WearableSyncProtocol.parseIncoming(
            """{"type":"done","orderId":"order-9","requestId":"req-1"}""".toByteArray(),
        )
        assertTrue(msg is WearableSyncProtocol.IncomingMessage.Done)
        msg as WearableSyncProtocol.IncomingMessage.Done
        assertEquals("order-9", msg.orderId)
        assertEquals("req-1", msg.requestId)
    }

    @Test
    fun parseIncomingParsesDoneFromInterconnectDataEnvelope() {
        val msg = WearableSyncProtocol.parseIncoming(
            """{"data":"{\"type\":\"done\",\"orderId\":\"order-9\",\"requestId\":\"req-2\"}"}""".toByteArray(),
        )
        assertTrue(msg is WearableSyncProtocol.IncomingMessage.Done)
        msg as WearableSyncProtocol.IncomingMessage.Done
        assertEquals("order-9", msg.orderId)
        assertEquals("req-2", msg.requestId)
    }

    @Test
    fun parseIncomingDoneWithoutOrderIdIsInvalid() {
        val msg = WearableSyncProtocol.parseIncoming("""{"type":"done"}""".toByteArray())
        assertEquals(WearableSyncProtocol.IncomingMessage.Invalid, msg)
    }

    @Test
    fun parseIncomingMalformedJsonIsInvalid() {
        val msg = WearableSyncProtocol.parseIncoming("not-json{{{".toByteArray())
        assertEquals(WearableSyncProtocol.IncomingMessage.Invalid, msg)
    }

    @Test
    fun parseIncomingUnknownTypeIsUnsupported() {
        val msg = WearableSyncProtocol.parseIncoming("""{"type":"unknown"}""".toByteArray())
        assertTrue(msg is WearableSyncProtocol.IncomingMessage.Unsupported)
        msg as WearableSyncProtocol.IncomingMessage.Unsupported
        assertEquals("unknown", msg.type)
    }

    @Test
    fun parseIncomingEmptyPayloadIsInvalid() {
        val msg = WearableSyncProtocol.parseIncoming(ByteArray(0))
        assertEquals(WearableSyncProtocol.IncomingMessage.Invalid, msg)
    }
}
