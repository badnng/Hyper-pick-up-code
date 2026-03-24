package com.Badnng.moe

import kotlinx.coroutines.flow.Flow

class OrderGroupRepository(private val orderGroupDao: OrderGroupDao) {

    // 插入订单组
    suspend fun insertGroup(group: OrderGroup): Long {
        return orderGroupDao.insertGroup(group)
    }

    // 更新订单组
    suspend fun updateGroup(group: OrderGroup) {
        orderGroupDao.updateGroup(group)
    }

    // 删除订单组
    suspend fun deleteGroup(group: OrderGroup) {
        orderGroupDao.deleteGroup(group)
    }

    // 获取所有订单组（Flow 实时更新）
    fun getAllGroups(): Flow<List<OrderGroup>> {
        return orderGroupDao.getAllGroups()
    }

    // 获取未完成的订单组
    fun getIncompleteGroups(): Flow<List<OrderGroup>> {
        return orderGroupDao.getIncompleteGroups()
    }

    // 获取已完成的订单组
    fun getCompletedGroups(): Flow<List<OrderGroup>> {
        return orderGroupDao.getCompletedGroups()
    }

    // 获取订单组中的订单
    fun getOrdersByGroupId(groupId: Long): Flow<List<OrderEntity>> {
        return orderGroupDao.getOrdersByGroupId(groupId)
    }

    // 获取组中的订单数量
    suspend fun getOrderCountInGroup(groupId: Long): Int {
        return orderGroupDao.getOrderCountInGroup(groupId)
    }

    // 获取组中已完成的订单数量
    suspend fun getCompletedOrderCountInGroup(groupId: Long): Int {
        return orderGroupDao.getCompletedOrderCountInGroup(groupId)
    }

    // 标记订单组为已完成
    suspend fun markGroupAsCompleted(groupId: Long, completedTime: Long = System.currentTimeMillis()) {
        orderGroupDao.markGroupAsCompleted(groupId, completedTime)
    }

    // 标记组中所有订单为已完成
    suspend fun markAllOrdersInGroupCompleted(groupId: Long, completedTime: Long = System.currentTimeMillis()) {
        orderGroupDao.markAllOrdersInGroupCompleted(groupId, completedTime)
    }

    // 删除所有已完成的订单组
    suspend fun deleteCompletedGroups() {
        orderGroupDao.deleteCompletedGroups()
    }

    // 根据ID获取订单组
    suspend fun getGroupById(groupId: Long): OrderGroup? {
        return orderGroupDao.getGroupById(groupId)
    }
}