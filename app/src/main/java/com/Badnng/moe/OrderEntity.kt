package com.Badnng.moe

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "orders",
    foreignKeys = [
        ForeignKey(
            entity = OrderGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["groupId"])]
)
data class OrderEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val takeoutCode: String,
    val qrCodeData: String? = null,
    val screenshotPath: String,
    val recognizedText: String,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val orderType: String = "餐食",
    val brandName: String? = null,
    val sourceApp: String? = null,
    val sourcePackage: String? = null,
    val fullText: String? = null,
    val pickupLocation: String? = null,
    val groupId: Long? = null  // 关联到 OrderGroup 的 id
)
