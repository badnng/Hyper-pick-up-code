package com.Badnng.moe

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderGroupDao {
    @Insert
    suspend fun insertGroup(group: OrderGroup): Long

    @Update
    suspend fun updateGroup(group: OrderGroup)

    @Delete
    suspend fun deleteGroup(group: OrderGroup)

    @Query("SELECT * FROM order_groups WHERE id = :id")
    suspend fun getGroupById(id: Long): OrderGroup?

    @Query("SELECT * FROM order_groups ORDER BY createdAt DESC")
    fun getAllGroups(): Flow<List<OrderGroup>>

    @Query("SELECT * FROM order_groups WHERE isCompleted = 0 ORDER BY createdAt DESC")
    fun getIncompleteGroups(): Flow<List<OrderGroup>>

    @Query("SELECT * FROM order_groups WHERE isCompleted = 1 ORDER BY completedAt DESC")
    fun getCompletedGroups(): Flow<List<OrderGroup>>

    @Query("SELECT * FROM orders WHERE groupId = :groupId ORDER BY createdAt DESC")
    fun getOrdersByGroupId(groupId: Long): Flow<List<OrderEntity>>

    @Query("SELECT COUNT(*) FROM orders WHERE groupId = :groupId")
    suspend fun getOrderCountInGroup(groupId: Long): Int

    @Query("SELECT COUNT(*) FROM orders WHERE groupId = :groupId AND isCompleted = 1")
    suspend fun getCompletedOrderCountInGroup(groupId: Long): Int

    @Query("UPDATE order_groups SET isCompleted = 1, completedAt = :completedTime WHERE id = :groupId")
    suspend fun markGroupAsCompleted(groupId: Long, completedTime: Long)

    @Query("UPDATE orders SET isCompleted = 1, completedAt = :completedTime WHERE groupId = :groupId")
    suspend fun markAllOrdersInGroupCompleted(groupId: Long, completedTime: Long)

    @Query("DELETE FROM order_groups WHERE isCompleted = 1")
    suspend fun deleteCompletedGroups()

    @Query("SELECT * FROM order_groups ORDER BY createdAt DESC")
    suspend fun getAllGroupsList(): List<OrderGroup>

    @Query("UPDATE order_groups SET orderCount = :count WHERE id = :groupId")
    suspend fun updateOrderCount(groupId: Long, count: Int)
}
