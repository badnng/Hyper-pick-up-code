package com.Badnng.moe.helper

import android.content.Context
import android.net.Uri
import android.util.Log
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.rules.RecognitionRuleEngine
import com.Badnng.moe.rules.RuleValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Deprecated("旧备份实现已停用，请使用流式 BackupManager", level = DeprecationLevel.ERROR)
internal object BackupHelper {
    private const val TAG = "BackupHelper"

    data class BackupData(
        val orders: List<OrderEntity>,
        val settings: Map<String, Any?>,
        val hasRules: Boolean = false
    )

    suspend fun createBackup(
        context: Context,
        orders: List<OrderEntity>,
        settings: Map<String, Any?>
    ): ByteArray = withContext(Dispatchers.IO) {
        try {
            val byteArrayOutputStream = ByteArrayOutputStream()
            ZipOutputStream(byteArrayOutputStream).use { zos ->
                // 添加订单数据
                val ordersJson = JSONArray()
                orders.forEach { order ->
                    val orderJson = JSONObject().apply {
                        put("id", order.id)
                        put("takeoutCode", order.takeoutCode)
                        put("qrCodeData", order.qrCodeData ?: JSONObject.NULL)
                        put("screenshotPath", order.screenshotPath)
                        put("recognizedText", order.recognizedText)
                        put("isCompleted", order.isCompleted)
                        put("createdAt", order.createdAt)
                        put("completedAt", order.completedAt ?: JSONObject.NULL)
                        put("orderType", order.orderType)
                        put("brandName", order.brandName ?: JSONObject.NULL)
                        put("sourceApp", order.sourceApp ?: JSONObject.NULL)
                        put("sourcePackage", order.sourcePackage ?: JSONObject.NULL)
                        put("fullText", order.fullText ?: JSONObject.NULL)
                        put("pickupLocation", order.pickupLocation ?: JSONObject.NULL)
                        put("ocrDiagnosticData", order.ocrDiagnosticData ?: JSONObject.NULL)
                    }
                    ordersJson.put(orderJson)
                }
                
                zos.putNextEntry(ZipEntry("orders.json"))
                zos.write(ordersJson.toString(2).toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 添加设置数据
                val settingsJson = JSONObject()
                settings.forEach { (key, value) ->
                    if (value != null) {
                        settingsJson.put(key, value)
                    }
                }
                
                zos.putNextEntry(ZipEntry("settings.json"))
                zos.write(settingsJson.toString(2).toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 添加识别规则
                val rulesFile = File(context.filesDir, "rules/rules.json")
                if (rulesFile.exists()) {
                    zos.putNextEntry(ZipEntry("rules.json"))
                    zos.write(rulesFile.readBytes())
                    zos.closeEntry()
                    Log.d(TAG, "已添加识别规则到备份")
                }
            }

            Log.d(TAG, "备份创建成功，订单数量: ${orders.size}")
            byteArrayOutputStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "创建备份失败", e)
            throw e
        }
    }

    suspend fun restoreBackup(
        context: Context,
        backupData: ByteArray
    ): BackupData = withContext(Dispatchers.IO) {
        try {
            var orders = listOf<OrderEntity>()
            var settings = mapOf<String, Any?>()
            var hasRules = false

            ByteArrayInputStream(backupData).use { bais ->
                ZipInputStream(bais).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        when (entry.name) {
                            "orders.json" -> {
                                val json = zis.readBytes().toString(Charsets.UTF_8)
                                orders = parseOrders(json)
                                Log.d(TAG, "恢复订单数量: ${orders.size}")
                            }
                            "settings.json" -> {
                                val json = zis.readBytes().toString(Charsets.UTF_8)
                                settings = parseSettings(json)
                                Log.d(TAG, "恢复设置数量: ${settings.size}")
                            }
                            "rules.json" -> {
                                val json = zis.readBytes().toString(Charsets.UTF_8)
                                val validation = RuleValidator.validateJson(json)
                                if (validation.isValid) {
                                    val rulesDir = File(context.filesDir, "rules")
                                    rulesDir.mkdirs()
                                    File(rulesDir, "rules.json").writeText(json)
                                    hasRules = true
                                    Log.d(TAG, "恢复识别规则成功")
                                } else {
                                    Log.w(TAG, "备份中的识别规则无效: ${validation.errors}")
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            // 如果恢复了规则，重新加载规则引擎
            if (hasRules) {
                RecognitionRuleEngine.reload(context)
                Log.d(TAG, "规则引擎已重新加载")
            }

            BackupData(orders, settings, hasRules)
        } catch (e: Exception) {
            Log.e(TAG, "恢复备份失败", e)
            throw e
        }
    }

    private fun parseOrders(json: String): List<OrderEntity> {
        val jsonArray = JSONArray(json)
        val orders = mutableListOf<OrderEntity>()
        
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val order = OrderEntity(
                id = obj.getString("id"),
                takeoutCode = obj.getString("takeoutCode"),
                qrCodeData = if (obj.isNull("qrCodeData")) null else obj.getString("qrCodeData"),
                screenshotPath = obj.getString("screenshotPath"),
                recognizedText = obj.getString("recognizedText"),
                isCompleted = obj.getBoolean("isCompleted"),
                createdAt = obj.getLong("createdAt"),
                completedAt = if (obj.isNull("completedAt")) null else obj.getLong("completedAt"),
                orderType = obj.getString("orderType"),
                brandName = if (obj.isNull("brandName")) null else obj.getString("brandName"),
                sourceApp = if (obj.isNull("sourceApp")) null else obj.getString("sourceApp"),
                sourcePackage = if (obj.isNull("sourcePackage")) null else obj.getString("sourcePackage"),
                fullText = if (obj.isNull("fullText")) null else obj.getString("fullText"),
                pickupLocation = if (obj.isNull("pickupLocation")) null else obj.getString("pickupLocation"),
                ocrDiagnosticData = if (!obj.has("ocrDiagnosticData") || obj.isNull("ocrDiagnosticData")) {
                    null
                } else {
                    obj.getString("ocrDiagnosticData")
                },
            )
            orders.add(order)
        }
        
        return orders
    }

    private fun parseSettings(json: String): Map<String, Any?> {
        val jsonObject = JSONObject(json)
        val settings = mutableMapOf<String, Any?>()
        
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = jsonObject.get(key)
            settings[key] = if (value == JSONObject.NULL) null else value
        }
        
        return settings
    }

    fun generateBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault())
        val dateTime = dateFormat.format(Date())
        return "澎湃记备份-$dateTime.backup"
    }
}
