package com.Badnng.moe

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val takeoutCode: String,           // 取餐码（数字）
    val qrCodeData: String? = null,    // 二维码数据（如果有的话）
    val screenshotPath: String,        // 截图文件路径
    val recognizedText: String,        // OCR 识别出的所有文本
    val isCompleted: Boolean = false,  // 是否已完成
    val createdAt: Long = System.currentTimeMillis(),  // 创建时间
    val completedAt: Long? = null      // 完成时间
)