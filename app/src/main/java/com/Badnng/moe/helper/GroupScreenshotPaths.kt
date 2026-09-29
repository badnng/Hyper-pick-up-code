package com.Badnng.moe.helper

import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.data.db.OrderGroup
import org.json.JSONArray

/** 组本身保留来源图；读取时也汇入旧版本仅保存在子订单上的图片。 */
object GroupScreenshotPaths {
    fun decode(json: String): List<String> = runCatching {
        val array = JSONArray(json)
        buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }.distinct()
    }.getOrDefault(emptyList())

    fun encode(paths: Iterable<String>): String = JSONArray(
        paths.filter(String::isNotBlank).distinct(),
    ).toString()

    fun all(group: OrderGroup, orders: List<OrderEntity> = emptyList()): List<String> = buildList {
        addAll(decode(group.screenshotPathsJson))
        orders.sortedBy(OrderEntity::createdAt).forEach { add(it.screenshotPath) }
        add(group.screenshotPath)
    }.filter(String::isNotBlank).distinct()
}
