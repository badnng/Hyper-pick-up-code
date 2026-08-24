package com.Badnng.moe.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Badnng.moe.data.db.OrderDao
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.data.db.OrderGroup
import com.Badnng.moe.data.db.OrderGroupDao
import com.Badnng.moe.data.repository.OrderGroupRepository
import com.Badnng.moe.data.repository.OrderRepository
import com.Badnng.moe.helper.NotificationHelper
import com.Badnng.moe.helper.NotificationScheduler
import com.Badnng.moe.helper.DailyExpressGroupingHelper
import com.Badnng.moe.wearable.WearableSyncManager
import com.Badnng.moe.wearable.WearableSyncSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OrderViewModel(application: Application) : AndroidViewModel(application), WearableSyncSource {

    private val orderDao: OrderDao
    private val orderGroupDao: OrderGroupDao
    private val repository: OrderRepository
    private val groupRepository: OrderGroupRepository
    private val notificationHelper = NotificationHelper(application)

    private val _orders = MutableStateFlow<List<OrderEntity>>(emptyList())
    val orders: StateFlow<List<OrderEntity>> = _orders.asStateFlow()

    private val _incompleteOrders = MutableStateFlow<List<OrderEntity>>(emptyList())
    override val incompleteOrders: StateFlow<List<OrderEntity>> = _incompleteOrders.asStateFlow()

    private val _completedOrders = MutableStateFlow<List<OrderEntity>>(emptyList())
    override val completedOrders: StateFlow<List<OrderEntity>> = _completedOrders.asStateFlow()

    private val _ruleCorrectionDrafts = MutableStateFlow<List<OrderEntity>>(emptyList())
    val ruleCorrectionDrafts: StateFlow<List<OrderEntity>> = _ruleCorrectionDrafts.asStateFlow()

    private val _orderGroups = MutableStateFlow<List<OrderGroup>>(emptyList())
    val orderGroups: StateFlow<List<OrderGroup>> = _orderGroups.asStateFlow()

    private val _incompleteGroups = MutableStateFlow<List<OrderGroup>>(emptyList())
    val incompleteGroups: StateFlow<List<OrderGroup>> = _incompleteGroups.asStateFlow()

    private val _completedGroups = MutableStateFlow<List<OrderGroup>>(emptyList())
    val completedGroups: StateFlow<List<OrderGroup>> = _completedGroups.asStateFlow()

    init {
        val database = OrderDatabase.getDatabase(application)
        orderDao = database.orderDao()
        orderGroupDao = database.orderGroupDao()
        repository = OrderRepository(orderDao)
        groupRepository = OrderGroupRepository(orderGroupDao)

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

        viewModelScope.launch {
            orderDao.getRuleCorrectionDrafts().collect { drafts ->
                _ruleCorrectionDrafts.value = drafts
            }
        }

        viewModelScope.launch {
            groupRepository.getAllGroups().collect { groups ->
                _orderGroups.value = groups
            }
        }

        viewModelScope.launch {
            groupRepository.getIncompleteGroups().collect { groups ->
                _incompleteGroups.value = groups
            }
        }

        viewModelScope.launch {
            groupRepository.getCompletedGroups().collect { groups ->
                _completedGroups.value = groups
            }
        }

        // ★ 手表同步：向 Application 级 manager 注入数据源并启动
        WearableSyncManager.getInstance(application).attachSource(this)
    }

    fun addOrder(order: OrderEntity) {
        viewModelScope.launch {
            repository.insertOrder(order)
            notificationHelper.showPromotedLiveUpdate(order, order.brandName)
        }
    }

    fun markAsCompleted(orderId: String) {
        viewModelScope.launch {
            markAsCompletedFromWearable(orderId)
        }
    }

    override suspend fun markAsCompletedFromWearable(orderId: String): Boolean {
        val order = orderDao.getOrderById(orderId) ?: return false
        // 手表端完成操作不经过 NotificationReceiver，先清理手机端通知和对应闹钟。
        runCatching { notificationHelper.cancelNotification(orderId) }
        runCatching {
            NotificationScheduler.cancel(getApplication(), NotificationScheduler.getOrderRequestCode(orderId))
        }
        if (order.isCompleted) return true

        val groupId = order.groupId
        repository.markAsCompleted(orderId)
        if (groupId != null) {
            try {
                checkAndDissolveGroup(groupId)
            } catch (_: Throwable) {
                // 订单完成状态已经写入，分组整理失败不回滚主操作。
            }
        }
        return orderDao.getOrderById(orderId)?.isCompleted == true
    }

    /**
     * ViewModel 被销毁时解绑手表同步数据源，避免 Application 级单例
     * [WearableSyncManager] 永久持有已清理的 OrderViewModel（泄漏）。
     */
    override fun onCleared() {
        WearableSyncManager.getInstance(getApplication<Application>()).detachSource()
        super.onCleared()
    }

    fun deleteOrder(order: OrderEntity) {
        viewModelScope.launch {
            val groupId = order.groupId
            repository.deleteOrder(order)
            notificationHelper.cancelNotification(order.id)
            if (groupId != null) checkAndDissolveGroup(groupId)
        }
    }

    fun deleteCompletedOrders() {
        viewModelScope.launch {
            repository.deleteCompletedOrders()
        }
    }

    fun applyRuleCorrection(
        draft: OrderEntity,
        correctedOrders: List<OrderEntity>,
        keepDraft: Boolean,
    ) {
        viewModelScope.launch {
            for (order in correctedOrders) {
                repository.insertOrder(order)
            }
            if (!keepDraft) {
                repository.deleteOrder(draft)
            }
            DailyExpressGroupingHelper.regroupPendingExpressByDay(
                orderDao,
                orderGroupDao,
                getApplication(),
            )
            for (order in correctedOrders) {
                val refreshed = orderDao.getOrderById(order.id) ?: order
                if (refreshed.groupId == null) {
                    notificationHelper.showPromotedLiveUpdate(refreshed, refreshed.brandName)
                }
            }
        }
    }

    fun resolveRuleCorrection(order: OrderEntity) {
        viewModelScope.launch {
            repository.updateOrder(order.copy(needsRuleCorrection = false))
            DailyExpressGroupingHelper.regroupPendingExpressByDay(
                orderDao,
                orderGroupDao,
                getApplication(),
            )
            val refreshed = orderDao.getOrderById(order.id) ?: order
            if (refreshed.groupId == null) {
                notificationHelper.showPromotedLiveUpdate(refreshed, refreshed.brandName)
            }
        }
    }
    fun updateOrder(order: OrderEntity) {
        viewModelScope.launch {
            repository.updateOrder(order)
        }
    }

    suspend fun insertGroup(group: OrderGroup): Long {
        return groupRepository.insertGroup(group)
    }

    fun getOrdersByGroupId(groupId: Long): StateFlow<List<OrderEntity>> {
        val result = MutableStateFlow<List<OrderEntity>>(emptyList())
        viewModelScope.launch {
            groupRepository.getOrdersByGroupId(groupId).collect { orders ->
                result.value = orders
            }
        }
        return result.asStateFlow()
    }

    fun markGroupAsCompleted(groupId: Long) {
        viewModelScope.launch {
            groupRepository.markGroupAsCompleted(groupId)
            groupRepository.markAllOrdersInGroupCompleted(groupId)
            notificationHelper.cancelGroupNotification(groupId)
        }
    }

    fun markAllOrdersInGroupCompleted(groupId: Long) {
        viewModelScope.launch {
            groupRepository.markAllOrdersInGroupCompleted(groupId)
        }
    }

    fun deleteGroup(group: OrderGroup) {
        viewModelScope.launch {
            groupRepository.deleteGroup(group)
            notificationHelper.cancelGroupNotification(group.id)
        }
    }

    fun deleteCompletedGroups() {
        viewModelScope.launch {
            groupRepository.deleteCompletedGroups()
        }
    }

    suspend fun checkAndDissolveGroup(groupId: Long) {
        val group = orderGroupDao.getGroupById(groupId) ?: return
        if (group.isCompleted) return
        val incompleteCount = orderDao.getAllOrdersList().count { it.groupId == groupId && !it.isCompleted }
        if (incompleteCount < 2) {
            val remaining = orderDao.getAllOrdersList().filter { it.groupId == groupId }
            remaining.forEach { order ->
                orderDao.update(order.copy(groupId = null))
            }
            orderGroupDao.deleteGroup(group)
            runCatching { notificationHelper.cancelGroupNotification(groupId) }
            runCatching {
                NotificationScheduler.cancel(getApplication(), NotificationScheduler.getGroupRequestCode(groupId))
            }
        } else {
            orderGroupDao.updateOrderCount(groupId, incompleteCount)
            // 手表完成组内一单后，重绘组通知，避免通知仍展示已完成取餐码。
            val pendingOrders = orderDao.getAllOrdersList().filter {
                it.groupId == groupId && !it.isCompleted
            }
            runCatching {
                notificationHelper.showGroupNotification(
                    group.copy(orderCount = incompleteCount),
                    pendingOrders,
                )
            }
        }
    }
}
