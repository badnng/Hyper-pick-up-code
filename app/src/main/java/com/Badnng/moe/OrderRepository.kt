package com.Badnng.moe

import kotlinx.coroutines.flow.Flow

class OrderRepository(private val orderDao: OrderDao) {

    // 插入订单
    suspend fun insertOrder(order: OrderEntity) {
        orderDao.insert(order)
    }

    // 更新订单
    suspend fun updateOrder(order: OrderEntity) {
        orderDao.update(order)
    }

    // 删除订单
    suspend fun deleteOrder(order: OrderEntity) {
        orderDao.delete(order)
    }

    // 获取单个订单
    suspend fun getOrderById(id: String): OrderEntity? {
        return orderDao.getOrderById(id)
    }

    // 获取所有订单（Flow 实时更新）
    fun getAllOrders(): Flow<List<OrderEntity>> {
        return orderDao.getAllOrders()
    }

    // 获取未完成的订单
    fun getIncompleteOrders(): Flow<List<OrderEntity>> {
        return orderDao.getIncompleteOrders()
    }

    // 获取已完成的订单
    fun getCompletedOrders(): Flow<List<OrderEntity>> {
        return orderDao.getCompletedOrders()
    }

    // 标记订单为已完成
    suspend fun markAsCompleted(orderId: String, completedTime: Long = System.currentTimeMillis()) {
        orderDao.markAsCompleted(orderId, completedTime)
    }

    // 删除所有已完成的订单
    suspend fun deleteCompletedOrders() {
        orderDao.deleteCompletedOrders()
    }
}