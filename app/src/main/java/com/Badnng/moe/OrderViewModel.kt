package com.Badnng.moe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OrderViewModel(application: Application) : AndroidViewModel(application) {

    private val orderDao: OrderDao
    private val repository: OrderRepository
    private val notificationHelper = NotificationHelper(application)

    private val _orders = MutableStateFlow<List<OrderEntity>>(emptyList())
    val orders: StateFlow<List<OrderEntity>> = _orders.asStateFlow()

    private val _incompleteOrders = MutableStateFlow<List<OrderEntity>>(emptyList())
    val incompleteOrders: StateFlow<List<OrderEntity>> = _incompleteOrders.asStateFlow()

    private val _completedOrders = MutableStateFlow<List<OrderEntity>>(emptyList())
    val completedOrders: StateFlow<List<OrderEntity>> = _completedOrders.asStateFlow()

    init {
        val database = OrderDatabase.getDatabase(application)
        orderDao = database.orderDao()
        repository = OrderRepository(orderDao)

        // 观察订单列表变化
        viewModelScope.launch {
            repository.getAllOrders().collect { orders ->
                _orders.value = orders
            }
        }

        viewModelScope.launch {
            repository.getIncompleteOrders().collect { orders ->
                _incompleteOrders.value = orders
            }
        }

        viewModelScope.launch {
            repository.getCompletedOrders().collect { orders ->
                _completedOrders.value = orders
            }
        }
    }

    // 添加订单并触发动态更新
    fun addOrder(order: OrderEntity) {
        viewModelScope.launch {
            repository.insertOrder(order)

            // 发送实况窗通知
            notificationHelper.showPromotedLiveUpdate(order.id, order.takeoutCode)
        }
    }

    // 标记为已完成
    fun markAsCompleted(orderId: String) {
        viewModelScope.launch {
            repository.markAsCompleted(orderId)
            // 当订单完成时，取消并移除实况窗
            notificationHelper.cancelNotification()
        }
    }

    fun deleteOrder(order: OrderEntity) {
        viewModelScope.launch {
            repository.deleteOrder(order)
        }
    }

    fun deleteCompletedOrders() {
        viewModelScope.launch {
            repository.deleteCompletedOrders()
        }
    }

    fun updateOrder(order: OrderEntity) {
        viewModelScope.launch {
            repository.updateOrder(order)
        }
    }
}