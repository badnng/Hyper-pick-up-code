package com.Badnng.moe

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Insert
    suspend fun insert(order: OrderEntity)

    @Update
    suspend fun update(order: OrderEntity)

    @Delete
    suspend fun delete(order: OrderEntity)

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getOrderById(id: String): OrderEntity?

    @Query("SELECT * FROM orders ORDER BY createdAt DESC")
    fun getAllOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE isCompleted = 0 ORDER BY createdAt DESC")
    fun getIncompleteOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE isCompleted = 1 ORDER BY completedAt DESC")
    fun getCompletedOrders(): Flow<List<OrderEntity>>

    @Query("UPDATE orders SET isCompleted = 1, completedAt = :completedTime WHERE id = :orderId")
    suspend fun markAsCompleted(orderId: String, completedTime: Long)

    @Query("DELETE FROM orders WHERE isCompleted = 1")
    suspend fun deleteCompletedOrders()
}