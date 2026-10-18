package com.Badnng.moe.widget

import android.content.Context
import android.content.SharedPreferences
import com.Badnng.moe.data.db.OrderEntity

internal enum class OrderWidgetFilter(val label: String, val emptyLabel: String) {
    ALL("全部", "暂无待取订单"),
    FOOD("取餐", "暂无待取餐食"),
    EXPRESS("取件", "暂无待取快递");

    companion object {
        fun fromValue(value: String?): OrderWidgetFilter = entries.find { it.name == value } ?: ALL
    }
}

internal fun widgetOrders(orders: List<OrderEntity>, filter: OrderWidgetFilter): List<OrderEntity> =
    orders.asSequence()
        .filterNot { it.isCompleted }
        .filter {
            when (filter) {
                OrderWidgetFilter.ALL -> true
                OrderWidgetFilter.FOOD -> it.orderType != "快递"
                OrderWidgetFilter.EXPRESS -> it.orderType == "快递"
            }
        }
        .sortedWith(compareByDescending<OrderEntity> { it.createdAt }.thenBy { it.id })
        .toList()

internal fun widgetOrderLabel(order: OrderEntity): String =
    order.brandName?.takeIf { it.isNotBlank() }
        ?: if (order.orderType == "快递") "取件码" else "取餐码"

/** 不用列表索引或 32 位 String.hashCode，避免刷新后滚动锚点错位。 */
internal fun widgetItemId(orderId: String): Long {
    var hash = -3750763034362895579L
    orderId.forEach { hash = (hash xor it.code.toLong()) * 1099511628211L }
    return hash and Long.MAX_VALUE // Glance 的负数保留区不能用作 itemId。
}

internal class OrderWidgetPreferences(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(
        context.getSharedPreferences("order_widget_instances", Context.MODE_PRIVATE),
    )

    fun filter(id: Int): OrderWidgetFilter = OrderWidgetFilter.fromValue(prefs.getString(key(id), null))

    fun setFilter(id: Int, filter: OrderWidgetFilter) {
        check(prefs.edit().putString(key(id), filter.name).commit()) { "无法保存小组件分类" }
    }

    fun remove(ids: IntArray) {
        val editor = prefs.edit()
        ids.forEach { editor.remove(key(it)) }
        editor.apply()
    }

    fun restore(oldIds: IntArray, newIds: IntArray) {
        // 先拍快照，再写新 ID，兼容新旧 ID 交叉的恢复映射。
        val filters = oldIds.zip(newIds).map { (old, new) -> new to filter(old) }
        val editor = prefs.edit()
        oldIds.forEach { editor.remove(key(it)) }
        filters.forEach { (id, filter) -> editor.putString(key(id), filter.name) }
        editor.apply()
    }

    private fun key(id: Int) = "filter_$id"
}
