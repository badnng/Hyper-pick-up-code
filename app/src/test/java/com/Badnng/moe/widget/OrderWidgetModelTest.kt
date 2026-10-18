package com.Badnng.moe.widget

import com.Badnng.moe.data.db.OrderEntity
import org.junit.Assert.*
import org.junit.Test

class OrderWidgetModelTest {
    @Test
    fun filtersPendingFoodDrinksAndExpressAndSortsNewestFirst() {
        val orders = listOf(
            order("food", "餐食", 10),
            order("express", "快递", 30),
            order("drink", "饮品", 20),
            order("done", "快递", 40).copy(isCompleted = true),
        )
        assertEquals(listOf("express", "drink", "food"), widgetOrders(orders, OrderWidgetFilter.ALL).map { it.id })
        assertEquals(listOf("drink", "food"), widgetOrders(orders, OrderWidgetFilter.FOOD).map { it.id })
        assertEquals(listOf("express"), widgetOrders(orders, OrderWidgetFilter.EXPRESS).map { it.id })
    }

    @Test
    fun groupMembersRemainIndividualAndEqualTimestampsHaveStableOrder() {
        val orders = listOf(order("b", "快递", 20), order("a", "快递", 20))
            .map { it.copy(groupId = 12L) }
        assertEquals(listOf("a", "b"), widgetOrders(orders, OrderWidgetFilter.ALL).map { it.id })
    }

    @Test
    fun missingBrandUsesPickupTypeAndUnknownFilterUsesAll() {
        assertEquals("取件码", widgetOrderLabel(order("a", "快递", 1).copy(brandName = "  ")))
        assertEquals("取餐码", widgetOrderLabel(order("b", "饮品", 1)))
        assertEquals("品牌", widgetOrderLabel(order("b", "饮品", 1).copy(brandName = "品牌")))
        assertEquals(OrderWidgetFilter.ALL, OrderWidgetFilter.fromValue(null))
        assertEquals(OrderWidgetFilter.ALL, OrderWidgetFilter.fromValue("old_filter"))
    }

    @Test
    fun stableIdsAvoidReservedRangeAndCommonStringHashCollisions() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertNotEquals(widgetItemId("Aa"), widgetItemId("BB"))
        val ids = (0..1000).map { widgetItemId("order-$it") }
        assertTrue(ids.all { it >= 0L })
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(widgetItemId("persisted-order"), widgetItemId("persisted-order"))
    }

    private fun order(id: String, type: String, time: Long) = OrderEntity(
        id = id, takeoutCode = id, orderType = type, createdAt = time,
        screenshotPath = "", recognizedText = "",
    )
}
