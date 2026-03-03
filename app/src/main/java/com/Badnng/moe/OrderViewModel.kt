package com.Badnng.moe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    fun addOrder(order: OrderEntity) {
        viewModelScope.launch {
            repository.insertOrder(order)
            // 关键：手动创建时也传入品牌名（如果有的话），确保通知高亮逻辑打通
            notificationHelper.showPromotedLiveUpdate(order, order.brandName)
        }
    }

    fun markAsCompleted(orderId: String) {
        viewModelScope.launch {
            repository.markAsCompleted(orderId)
            notificationHelper.cancelNotification(orderId)
        }
    }

    fun deleteOrder(order: OrderEntity) {
        viewModelScope.launch {
            repository.deleteOrder(order)
            notificationHelper.cancelNotification(order.id)
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
