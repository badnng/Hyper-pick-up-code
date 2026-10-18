package com.Badnng.moe.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.data.db.OrderGroup
import com.Badnng.moe.helper.NotificationHelper
import com.Badnng.moe.helper.NotificationScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 手机、手表与桌面共用的完成操作；不依赖 Activity 或 ViewModel 的生命周期。 */
class OrderCompletionService internal constructor(
    private val database: OrderDatabase,
    private val effects: CompletionEffects,
    private val now: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this(
        OrderDatabase.getDatabase(context.applicationContext),
        AndroidCompletionEffects(context.applicationContext),
    )

    suspend fun completeOrder(orderId: String): Boolean = completionMutex.withLock {
        var groupChange: GroupChange? = null
        val found = database.withTransaction {
            val order = database.orderDao().getOrderById(orderId) ?: return@withTransaction false
            if (!order.isCompleted) {
                database.orderDao().markAsCompleted(orderId, now())
                groupChange = order.groupId?.let { reconcileGroupInTransaction(it) }
            }
            true
        }
        // 写入失败时不会撤掉通知。通知系统失败也不能回滚已经提交的订单状态。
        bestEffort { effects.cancelOrder(orderId) }
        publishGroupChange(groupChange)
        found
    }

    suspend fun reconcileGroup(groupId: Long) = completionMutex.withLock {
        val change = database.withTransaction { reconcileGroupInTransaction(groupId) }
        publishGroupChange(change)
    }

    private suspend fun reconcileGroupInTransaction(groupId: Long): GroupChange? {
        val group = database.orderGroupDao().getGroupById(groupId) ?: return null
        if (group.isCompleted) return null
        val pending = database.orderDao().getOrdersInGroup(groupId).filterNot { it.isCompleted }
        return if (pending.size < 2) {
            // 包括已经完成的成员也要先解除关联，避免 ON DELETE CASCADE 删除历史记录。
            database.orderDao().detachGroupMembers(groupId)
            database.orderGroupDao().deleteGroup(group)
            GroupChange.Dissolved(groupId)
        } else {
            database.orderGroupDao().updateOrderCount(groupId, pending.size)
            GroupChange.Updated(group.copy(orderCount = pending.size), pending)
        }
    }

    private fun publishGroupChange(change: GroupChange?) {
        when (change) {
            is GroupChange.Dissolved -> bestEffort { effects.cancelGroup(change.id) }
            is GroupChange.Updated -> bestEffort { effects.updateGroup(change.group, change.orders) }
            null -> Unit
        }
    }

    private inline fun bestEffort(block: () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 系统通知/闹钟不可用时，数据库仍是订单状态的唯一依据。
        }
    }

    private sealed interface GroupChange {
        data class Dissolved(val id: Long) : GroupChange
        data class Updated(val group: OrderGroup, val orders: List<OrderEntity>) : GroupChange
    }

    companion object {
        // 序列化多个实例的通知副作用，避免快速完成同组多单后旧通知覆盖新通知。
        private val completionMutex = Mutex()
    }
}

internal interface CompletionEffects {
    fun cancelOrder(orderId: String)
    fun cancelGroup(groupId: Long)
    fun updateGroup(group: OrderGroup, orders: List<OrderEntity>)
}

private class AndroidCompletionEffects(private val context: Context) : CompletionEffects {
    private val notifications = NotificationHelper(context)

    override fun cancelOrder(orderId: String) {
        try {
            notifications.cancelNotification(orderId)
        } finally {
            NotificationScheduler.cancel(context, NotificationScheduler.getOrderRequestCode(orderId))
        }
    }

    override fun cancelGroup(groupId: Long) {
        try {
            notifications.cancelGroupNotification(groupId)
        } finally {
            NotificationScheduler.cancel(context, NotificationScheduler.getGroupRequestCode(groupId))
        }
    }

    override fun updateGroup(group: OrderGroup, orders: List<OrderEntity>) {
        notifications.showGroupNotification(group, orders)
    }
}
