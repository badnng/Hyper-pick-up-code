package com.Badnng.moe.helper

import android.content.Context
import com.Badnng.moe.data.db.OrderDao
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.data.db.OrderGroup
import com.Badnng.moe.data.db.OrderGroupDao
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailyGroupingResult(
    val group: OrderGroup?,
    val groupOrders: List<OrderEntity>,
    val singleOrdersToNotify: List<OrderEntity>
)

object DailyExpressGroupingHelper {
    suspend fun regroupPendingExpressByDay(
        orderDao: OrderDao,
        groupDao: OrderGroupDao,
        context: Context? = null
    ) {
        // 检查自动合并开关
        if (context != null) {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("auto_group_enabled", true)) return
        }

        val allOrders = orderDao.getAllOrdersList()
        val pendingExpress = allOrders.filter { it.orderType == "快递" && !it.isCompleted }
        if (pendingExpress.isEmpty()) return

        val dayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val groupedByDay = pendingExpress.groupBy { dayFormatter.format(Date(it.createdAt)) }
        val allGroups = groupDao.getAllGroupsList()

        groupedByDay.forEach { (dayName, ordersOfDay) ->
            if (ordersOfDay.size < 2) return@forEach

            val latest = ordersOfDay.maxByOrNull { it.createdAt } ?: return@forEach
            val existingGroup = allGroups.firstOrNull {
                !it.isCompleted && it.orderType == "快递" && it.name == dayName
            }

            val groupId = if (existingGroup != null) {
                groupDao.updateGroup(
                    existingGroup.copy(
                        screenshotPath = latest.screenshotPath,
                        recognizedText = latest.fullText ?: existingGroup.recognizedText,
                        sourceApp = latest.sourceApp ?: existingGroup.sourceApp,
                        sourcePackage = latest.sourcePackage ?: existingGroup.sourcePackage,
                        brandName = latest.brandName ?: existingGroup.brandName
                    )
                )
                existingGroup.id
            } else {
                groupDao.insertGroup(
                    OrderGroup(
                        name = dayName,
                        orderType = "快递",
                        brandName = latest.brandName,
                        screenshotPath = latest.screenshotPath,
                        sourceApp = latest.sourceApp,
                        sourcePackage = latest.sourcePackage,
                        recognizedText = latest.fullText ?: "",
                        createdAt = latest.createdAt,
                        isCompleted = false,
                        orderCount = ordersOfDay.size
                    )
                )
            }

            ordersOfDay.forEach { order ->
                if (order.groupId != groupId) {
                    orderDao.update(order.copy(groupId = groupId))
                }
            }
            groupDao.updateOrderCount(groupId, ordersOfDay.size)
        }
    }

    suspend fun applyForToday(
        orderDao: OrderDao,
        groupDao: OrderGroupDao,
        newlyInsertedOrders: List<OrderEntity>,
        screenshotPath: String,
        recognizedText: String,
        sourceApp: String?,
        sourcePackage: String?,
        defaultBrand: String?
    ): DailyGroupingResult {
        if (newlyInsertedOrders.isEmpty()) {
            return DailyGroupingResult(null, emptyList(), emptyList())
        }

        val newExpressOrders = newlyInsertedOrders.filter { it.orderType == "快递" && !it.isCompleted }
        if (newExpressOrders.isEmpty()) {
            return DailyGroupingResult(null, emptyList(), newlyInsertedOrders)
        }

        val now = System.currentTimeMillis()
        val (dayStart, dayEnd) = getDayRange(now)
        val dayName = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))

        val allOrders = orderDao.getAllOrdersList()
        val todayPendingExpressOrders = allOrders.filter {
            it.orderType == "快递" &&
                !it.isCompleted &&
                it.createdAt in dayStart until dayEnd
        }

        if (todayPendingExpressOrders.size < 2) {
            return DailyGroupingResult(null, emptyList(), newlyInsertedOrders)
        }

        val allGroups = groupDao.getAllGroupsList()
        val existingTodayGroup = allGroups.firstOrNull {
            !it.isCompleted && it.orderType == "快递" && it.name == dayName
        }

        val groupId = if (existingTodayGroup != null) {
            groupDao.updateGroup(
                existingTodayGroup.copy(
                    screenshotPath = screenshotPath,
                    recognizedText = recognizedText,
                    sourceApp = sourceApp ?: existingTodayGroup.sourceApp,
                    sourcePackage = sourcePackage ?: existingTodayGroup.sourcePackage,
                    brandName = defaultBrand ?: existingTodayGroup.brandName
                )
            )
            existingTodayGroup.id
        } else {
            groupDao.insertGroup(
                OrderGroup(
                    name = dayName,
                    orderType = "快递",
                    brandName = defaultBrand,
                    screenshotPath = screenshotPath,
                    sourceApp = sourceApp,
                    sourcePackage = sourcePackage,
                    recognizedText = recognizedText,
                    createdAt = now,
                    isCompleted = false,
                    orderCount = todayPendingExpressOrders.size
                )
            )
        }

        todayPendingExpressOrders.forEach { order ->
            if (order.groupId != groupId) {
                orderDao.update(order.copy(groupId = groupId))
            }
        }

        val refreshedOrders = orderDao.getAllOrdersList()
        val groupedOrders = refreshedOrders
            .filter { it.groupId == groupId }
            .sortedByDescending { it.createdAt }

        groupDao.updateOrderCount(groupId, groupedOrders.size)
        val group = groupDao.getGroupById(groupId)

        val groupedIds = groupedOrders.map { it.id }.toSet()
        val singleOrdersToNotify = newlyInsertedOrders.filterNot { it.id in groupedIds }

        return DailyGroupingResult(group, groupedOrders, singleOrdersToNotify)
    }

    private fun getDayRange(timeMillis: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return start to cal.timeInMillis
    }
}
