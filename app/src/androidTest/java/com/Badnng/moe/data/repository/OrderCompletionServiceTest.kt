package com.Badnng.moe.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.data.db.OrderGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrderCompletionServiceTest {
    private lateinit var database: OrderDatabase
    private lateinit var effects: FakeEffects
    private lateinit var service: OrderCompletionService
    private var clock = 100L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, OrderDatabase::class.java,
        ).build()
        effects = FakeEffects()
        service = OrderCompletionService(database, effects) { ++clock }
    }

    @After fun close() { database.close() }

    @Test
    fun groupCountUpdatesThenDissolvesWithoutDeletingAnyMember() = runBlocking {
        val groupId = insertGroup(3)
        assertTrue(service.completeOrder("order-0"))
        assertEquals(2, database.orderGroupDao().getGroupById(groupId)!!.orderCount)
        assertEquals(listOf("order-1", "order-2"), effects.updatedOrders.map { it.id }.sorted())

        assertTrue(service.completeOrder("order-1"))
        assertNull(database.orderGroupDao().getGroupById(groupId))
        val remaining = database.orderDao().getAllOrdersList()
        assertEquals(3, remaining.size)
        assertTrue(remaining.all { it.groupId == null })
        assertFalse(database.orderDao().getOrderById("order-2")!!.isCompleted)
        assertEquals(listOf(groupId), effects.cancelledGroups)
        assertEquals(listOf("order-0", "order-1"), effects.cancelledOrders)
    }

    @Test
    fun concurrentRepeatedCompletionKeepsFirstTimestampAndMissingOrderIsHarmless() = runBlocking {
        database.orderDao().insert(order("single"))
        val results = listOf(
            async(Dispatchers.IO) { service.completeOrder("single") },
            async(Dispatchers.IO) { service.completeOrder("single") },
        ).awaitAll()
        assertTrue(results.all { it })
        assertEquals(101L, database.orderDao().getOrderById("single")!!.completedAt)
        assertFalse(service.completeOrder("deleted"))
    }

    @Test
    fun groupCleanupFailureRollsBackCompletionAndLeavesNotificationsUntouched() = runBlocking {
        val groupId = insertGroup(2)
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_group_delete BEFORE DELETE ON order_groups BEGIN SELECT RAISE(ABORT, 'test failure'); END",
        )
        val result = runCatching { service.completeOrder("order-0") }
        assertTrue(result.isFailure)
        assertFalse(database.orderDao().getOrderById("order-0")!!.isCompleted)
        assertTrue(database.orderDao().getAllOrdersList().all { it.groupId == groupId })
        assertEquals(2, database.orderGroupDao().getGroupById(groupId)!!.orderCount)
        assertTrue(effects.cancelledOrders.isEmpty())
        assertTrue(effects.cancelledGroups.isEmpty())
    }

    @Test
    fun notificationFailureDoesNotUndoCommittedOrder() = runBlocking {
        database.orderDao().insert(order("single"))
        effects.fail = true
        assertTrue(service.completeOrder("single"))
        assertTrue(database.orderDao().getOrderById("single")!!.isCompleted)
    }

    private suspend fun insertGroup(count: Int): Long {
        val id = database.orderGroupDao().insertGroup(OrderGroup(
            name = "取件码", orderType = "快递", screenshotPath = "", recognizedText = "", orderCount = count,
        ))
        repeat(count) { database.orderDao().insert(order("order-$it").copy(groupId = id)) }
        return id
    }

    private fun order(id: String) = OrderEntity(id = id, takeoutCode = id, screenshotPath = "", recognizedText = "")

    private class FakeEffects : CompletionEffects {
        val cancelledOrders = mutableListOf<String>()
        val cancelledGroups = mutableListOf<Long>()
        var updatedOrders = emptyList<OrderEntity>()
        var fail = false
        override fun cancelOrder(orderId: String) {
            if (fail) error("notification unavailable")
            cancelledOrders += orderId
        }
        override fun cancelGroup(groupId: Long) { cancelledGroups += groupId }
        override fun updateGroup(group: OrderGroup, orders: List<OrderEntity>) { updatedOrders = orders }
    }
}
