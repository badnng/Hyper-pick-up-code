package com.Badnng.moe

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 订单组 - 用于将一次识别的多个取件码分组
 */
@Entity(tableName = "order_groups")
data class OrderGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,  // 组名，如"快递取件码"或"取餐码"
    val orderType: String,  // 类型：餐食、饮品、快递
    val brandName: String? = null,  // 品牌名
    val screenshotPath: String,  // 截图路径
    val sourceApp: String? = null,  // 来源应用
    val sourcePackage: String? = null,  // 来源包名
    val recognizedText: String,  // 识别文本
    val createdAt: Long = System.currentTimeMillis(),  // 创建时间
    val isCompleted: Boolean = false,  // 是否已完成
    val completedAt: Long? = null,  // 完成时间
    val orderCount: Int = 0  // 包含的订单数量
)
