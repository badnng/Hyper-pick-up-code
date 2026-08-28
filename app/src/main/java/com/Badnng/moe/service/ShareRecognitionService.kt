package com.Badnng.moe.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.Badnng.moe.R
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.helper.AppLogger
import com.Badnng.moe.helper.DailyExpressGroupingHelper
import com.Badnng.moe.helper.ImageSourceMetadataResolver
import com.Badnng.moe.helper.NotificationHelper
import com.Badnng.moe.helper.ScreenshotStorage
import com.Badnng.moe.recognition.RecognizedOrderFactory
import com.Badnng.moe.recognition.RecognitionCorrectionDetector
import com.Badnng.moe.recognition.RecognitionCorrectionStore
import com.Badnng.moe.recognition.RecognitionRouter
import com.Badnng.moe.recognition.RecognitionTrigger

class ShareRecognitionService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.service("ShareRecognitionService onStartCommand")
        startForeground(NOTIFICATION_ID, createNotification())

        val imageUri = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("imageUri", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("imageUri")
        }

        if (imageUri != null) {
            AppLogger.service("ShareRecognitionService processing image: $imageUri")
            scope.launch {
                try {
                    processImage(imageUri)
                } catch (e: Exception) {
                    Log.e("ShareRecognition", "Error processing image", e)
                    AppLogger.service("ShareRecognitionService error: ${e.message}")
                }
                AppLogger.service("ShareRecognitionService stopping")
                stopSelf()
            }
        } else {
            AppLogger.service("ShareRecognitionService no imageUri, stopping")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private suspend fun processImage(imageUri: Uri) {
        val imageSource = ImageSourceMetadataResolver.resolve(applicationContext, imageUri)
        val resolvedSourceApp = imageSource.appName ?: imageSource.packageName
        AppLogger.recognition(
            "ShareRecognition source: file=${imageSource.displayName}, " +
                "package=${imageSource.packageName}, app=$resolvedSourceApp"
        )
        val inputStream = contentResolver.openInputStream(imageUri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        if (bitmap == null) {
            return
        }

        try {
            val routedResult = RecognitionRouter(applicationContext).recognizeImage(
                bitmap,
                resolvedSourceApp,
                imageSource.packageName,
                RecognitionTrigger.SHARED_IMAGE,
            )
            val recognizedOrders = routedResult.orders
            routedResult.onlineError?.let {
                Log.w("ShareRecognition", "Online recognition fallback: $it")
            }
            val successfulResults = recognizedOrders
                .filter { it.code != null }
                .distinctBy { it.code }
            val unrecognizedExplicitCodes = RecognitionCorrectionDetector.findUnrecognizedCodes(
                fullText = recognizedOrders.firstOrNull()?.fullText.orEmpty(),
                recognizedCodes = successfulResults.mapNotNull { it.code },
            )
            if (successfulResults.isEmpty()) {
                val draftSaved = recognizedOrders.firstOrNull()?.let { result ->
                    RecognitionCorrectionStore.saveImageDraft(
                        context = applicationContext,
                        bitmap = bitmap,
                        result = result,
                        metadata = routedResult.metadata,
                        recognizedText = "分享识别（待纠正）",
                        sourceApp = resolvedSourceApp ?: "分享识别",
                        sourcePackage = imageSource.packageName,
                        screenshotPrefix = "分享待纠正",
                    )
                } == true
                AppLogger.recognition("ShareRecognition: no codes found, correctionDraftSaved=$draftSaved")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        if (draftSaved) "识别失败，已加入纠正识别" else "未识别到取件码",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }

            val screenshotPath = ScreenshotStorage.saveBitmap(
                applicationContext,
                bitmap,
                namePrefix = "分享识别",
            )

            val partialDraftSaved = if (unrecognizedExplicitCodes.isNotEmpty()) {
                recognizedOrders.firstOrNull()?.let { result ->
                    RecognitionCorrectionStore.saveImageDraft(
                        context = applicationContext,
                        bitmap = bitmap,
                        result = result.copy(code = null, brand = null, pickupLocation = null),
                        metadata = routedResult.metadata,
                        recognizedText = "分享识别（部分待纠正）",
                        sourceApp = resolvedSourceApp ?: "分享识别",
                        sourcePackage = imageSource.packageName,
                        screenshotPrefix = "分享待纠正",
                        existingScreenshotPath = screenshotPath,
                    )
                } == true
            } else {
                false
            }
            if (partialDraftSaved) {
                AppLogger.recognition("ShareRecognition: partial correction saved, missing=$unrecognizedExplicitCodes")
            }

            val database = OrderDatabase.getDatabase(applicationContext)
            val orderDao = database.orderDao()
            val orderGroupDao = database.orderGroupDao()
            val insertedOrders = mutableListOf<OrderEntity>()
            for (result in successfulResults) {
                val code = result.code ?: continue
                AppLogger.recognition("ShareRecognition code=$code, type=${result.type}, brand=${result.brand}, pickup=${result.pickupLocation}")
                val order = RecognizedOrderFactory.fromRecognition(
                    result = result,
                    metadata = routedResult.metadata,
                    screenshotPath = screenshotPath,
                    recognizedText = "\u5206\u4eab\u8bc6\u522b",
                    sourceApp = resolvedSourceApp ?: "\u5206\u4eab\u8bc6\u522b",
                    sourcePackage = imageSource.packageName,
                ) ?: continue
                orderDao.insert(order)
                insertedOrders.add(order)
                com.Badnng.moe.wearable.WearableSyncManager.notifyOrderSaved(this, order)
            }
            if (insertedOrders.isEmpty()) return

            // 每次新识别后立即重整分组：不依赖打开 App。
            DailyExpressGroupingHelper.regroupPendingExpressByDay(orderDao, orderGroupDao, this)
            val notificationHelper = NotificationHelper(applicationContext)
            val refreshedInsertedOrders = insertedOrders.mapNotNull { orderDao.getOrderById(it.id) }
            val groupedIds = refreshedInsertedOrders.mapNotNull { it.groupId }.toSet()
            val allOrders = orderDao.getAllOrdersList()

            if (groupedIds.isNotEmpty()) {
                groupedIds.forEach { groupId ->
                    val group = orderGroupDao.getGroupById(groupId) ?: return@forEach
                    val groupOrders = allOrders
                        .filter { it.groupId == groupId && !it.isCompleted }
                        .sortedByDescending { it.createdAt }
                    if (groupOrders.size >= 2) {
                        groupOrders.forEach { notificationHelper.cancelNotification(it.id) }
                        orderGroupDao.updateOrderCount(groupId, groupOrders.size)
                        notificationHelper.showGroupNotification(
                            group.copy(orderCount = groupOrders.size),
                            groupOrders
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "\u65b0\u8bc6\u522b\u53d6\u4ef6\u7801\u5df2\u81ea\u52a8\u6574\u7406",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            refreshedInsertedOrders
                .filter { it.groupId == null }
                .forEach { order ->
                    notificationHelper.showPromotedLiveUpdate(order, order.brandName)
                }

            if (groupedIds.isEmpty()) {
                val firstCode = refreshedInsertedOrders.firstOrNull()?.takeoutCode
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        if (firstCode != null) "\u8bc6\u522b\u6210\u529f: $firstCode" else "\u8bc6\u522b\u6210\u529f",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            val refreshIntent = Intent("com.Badnng.moe.REFRESH_ORDERS")
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(refreshIntent)
        } finally {
            bitmap.recycle()
        }
    }

    private fun createNotification(): Notification {
        val channelId = "share_recognition"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "\u5206\u4eab\u8bc6\u522b",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("\u6b63\u5728\u8bc6\u522b\u622a\u56fe")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1002
    }
}
