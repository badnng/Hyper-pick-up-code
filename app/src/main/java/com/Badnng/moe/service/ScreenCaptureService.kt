package com.Badnng.moe.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.Badnng.moe.helper.AppLogger
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.Badnng.moe.R
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.helper.DailyExpressGroupingHelper
import com.Badnng.moe.helper.NotificationHelper
import com.Badnng.moe.helper.RootHelper
import com.Badnng.moe.recognition.RecognitionRouter
import com.Badnng.moe.recognition.OnlineRecognitionPreferences
import rikka.shizuku.Shizuku
import java.io.File
import java.io.FileOutputStream

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var triggeredByAccessibilityShortcut = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val normalCaptureDelayMs = 800L
    private val accessibilityCaptureDelayMs = 950L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.service("ScreenCaptureService onStartCommand, triggeredByAccessibilityShortcut=${intent?.getBooleanExtra("triggered_by_accessibility_shortcut", false)}")
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val currentMode = prefs.getString("capture_mode", "media_projection") ?: "media_projection"
        val intentUseShizuku = intent?.getBooleanExtra("use_shizuku", false) ?: false
        val intentUseRoot = intent?.getBooleanExtra("use_root", false) ?: false
        val useRoot = when {
            intentUseRoot -> true
            intentUseShizuku -> false
            currentMode == "root" -> true
            else -> false
        }
        val useShizuku = when {
            intentUseShizuku -> true
            intentUseRoot -> false
            currentMode == "shizuku" -> true
            else -> false
        }
        triggeredByAccessibilityShortcut = intent?.getBooleanExtra("triggered_by_accessibility_shortcut", false) ?: false

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val type = if (useShizuku || useRoot) {
                    if (Build.VERSION.SDK_INT >= 34) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    } else {
                        0
                    }
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                }
                startForeground(1001, createNotification(), type)
            } else {
                startForeground(1001, createNotification())
            }
        } catch (e: Exception) {
            Log.e("CaptureLog", "Failed to start foreground service", e)
            stopSelf()
            return START_NOT_STICKY
        }

        if (useRoot) {
            AppLogger.service("ScreenCaptureService using ROOT capture")
            startRootCaptureSingleTry()
        } else if (useShizuku) {
            AppLogger.service("ScreenCaptureService using SHIZUKU capture")
            startShizukuCaptureSingleTry()
        } else {
            AppLogger.service("ScreenCaptureService using MEDIA_PROJECTION capture")
            val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED)
                ?: Activity.RESULT_CANCELED
            val data = if (Build.VERSION.SDK_INT >= 33) {
                intent?.getParcelableExtra("data", Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra<Intent>("data")
            }

            if (resultCode == Activity.RESULT_OK && data != null) {
                val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = projectionManager.getMediaProjection(resultCode, data)
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        stopSelf()
                    }
                }, android.os.Handler(android.os.Looper.getMainLooper()))
                startMediaProjectionCaptureSingleTry()
            } else {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startShizukuCaptureSingleTry() {
        scope.launch {
            val captureDelay = if (triggeredByAccessibilityShortcut) {
                accessibilityCaptureDelayMs
            } else {
                normalCaptureDelayMs
            }
            delay(captureDelay)
            val pkg = getForegroundPackageName(applicationContext)
            val appName = pkg?.let { getAppName(applicationContext, it) }

            var bitmap: Bitmap? = null

            try {
                bitmap = captureShizukuScreenshot()
                if (bitmap != null) {
                    val recognitionBitmap = prepareRecognitionBitmap(bitmap)
                    recognizeAndStop(recognitionBitmap, appName, pkg, triggeredByAccessibilityShortcut)
                } else {
                    AppLogger.service("Shizuku capture returned null bitmap")
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e("CaptureLog", "Shizuku capture failed", e)
                AppLogger.service("Shizuku capture failed: ${e.message}")
                stopSelf()
            } finally {
                bitmap?.recycle()
            }
        }
    }

    private fun startRootCaptureSingleTry() {
        scope.launch {
            val captureDelay = if (triggeredByAccessibilityShortcut) {
                accessibilityCaptureDelayMs
            } else {
                normalCaptureDelayMs
            }
            delay(captureDelay)
            val pkg = getForegroundPackageName(applicationContext)
            val appName = pkg?.let { getAppName(applicationContext, it) }

            var bitmap: Bitmap? = null

            try {
                bitmap = RootHelper.captureScreenshot()
                if (bitmap != null) {
                    val recognitionBitmap = prepareRecognitionBitmap(bitmap)
                    recognizeAndStop(recognitionBitmap, appName, pkg, triggeredByAccessibilityShortcut)
                } else {
                    AppLogger.service("Root capture returned null bitmap")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext,
                            "Root 截图失败，请确认 Root 权限可用",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e("CaptureLog", "Root capture failed", e)
                AppLogger.service("Root capture failed: ${e.message}")
                stopSelf()
            } finally {
                bitmap?.recycle()
            }
        }
    }

    private fun captureShizukuScreenshot(): Bitmap? {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(
                null,
                arrayOf("screencap", "-p"),
                null,
                null
            ) as rikka.shizuku.ShizukuRemoteProcess
            val bitmap = BitmapFactory.decodeStream(process.inputStream)
            process.waitFor()
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun getForegroundPackageName(context: Context): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 10,
            time
        )
        return stats
            ?.filter {
                it.lastTimeUsed > 0 &&
                    it.packageName != packageName &&
                    it.packageName != "com.android.systemui"
            }
            ?.maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }

    private fun getAppName(context: Context, packageName: String): String {
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun startMediaProjectionCaptureSingleTry() {
        val metrics = resources.displayMetrics
        imageReader = ImageReader.newInstance(
            metrics.widthPixels,
            metrics.heightPixels,
            PixelFormat.RGBA_8888,
            2
        )

        try {
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )
        } catch (e: Exception) {
            Log.e("CaptureLog", "Failed to create virtual display", e)
            stopSelf()
            return
        }

        scope.launch {
            val captureDelay = if (triggeredByAccessibilityShortcut) {
                accessibilityCaptureDelayMs
            } else {
                normalCaptureDelayMs
            }
            delay(captureDelay)
            val pkg = getForegroundPackageName(applicationContext)
            val appName = pkg?.let { getAppName(applicationContext, it) }

            var image: android.media.Image? = null
            var bitmap: Bitmap? = null
            var cleanBitmap: Bitmap? = null

            try {
                image = imageReader?.acquireLatestImage()
                if (image == null) {
                    stopSelf()
                    return@launch
                }

                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * image.width

                bitmap = Bitmap.createBitmap(
                    image.width + rowPadding / pixelStride,
                    image.height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                cleanBitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                val recognitionBitmap = prepareRecognitionBitmap(cleanBitmap)
                recognizeAndStop(recognitionBitmap, appName, pkg, triggeredByAccessibilityShortcut)
            } catch (e: Exception) {
                Log.e("CaptureLog", "MediaProjection capture failed", e)
                stopSelf()
            } finally {
                image?.close()
                bitmap?.recycle()
                cleanBitmap?.recycle()
            }
        }
    }

    private fun cropStatusBar(src: Bitmap): Bitmap {
        val statusBarHeight = 150
        val sideMargin = (src.width * 0.02).toInt()
        val targetWidth = (src.width * 0.92).toInt()
        val targetHeight = (src.height * 0.81).toInt()
        return if (src.height > statusBarHeight + targetHeight && src.width > sideMargin + targetWidth) {
            Bitmap.createBitmap(src, sideMargin, statusBarHeight, targetWidth, targetHeight)
        } else {
            src
        }
    }

    private fun prepareRecognitionBitmap(src: Bitmap): Bitmap {
        if (OnlineRecognitionPreferences.isOnline(applicationContext)) {
            return src.copy(Bitmap.Config.ARGB_8888, false)
        }
        val cropped = cropStatusBar(src)
        return if (cropped === src) {
            src.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            cropped
        }
    }

    private fun recognizeAndStop(bitmap: Bitmap, sourceApp: String?, sourcePkg: String?, triggeredByAccessibilityShortcut: Boolean) {
        scope.launch {
            try {
                val routedResult = RecognitionRouter(applicationContext)
                    .recognizeImage(bitmap, sourceApp, sourcePkg)
                val recognizedOrders = routedResult.orders
                routedResult.onlineError?.let {
                    Log.w("CaptureLog", "Online recognition fallback: $it")
                }

                if (recognizedOrders.isEmpty()) {
                    Log.d("CaptureLog", "No code recognized")
                    return@launch
                }

                val screenshotFile = File(filesDir, "screenshots/${System.currentTimeMillis()}.png")
                screenshotFile.parentFile?.mkdirs()
                FileOutputStream(screenshotFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }

                val database = OrderDatabase.getDatabase(applicationContext)
                val orderGroupDao = database.orderGroupDao()
                val orderDao = database.orderDao()
                val insertedOrders = mutableListOf<OrderEntity>()
                for (result in recognizedOrders) {
                    val code = result.code ?: continue
                    AppLogger.recognition("code=$code, type=${result.type}, brand=${result.brand}, pickup=${result.pickupLocation}")
                    val order = OrderEntity(
                        takeoutCode = code,
                        qrCodeData = result.qr,
                        screenshotPath = screenshotFile.absolutePath,
                        recognizedText = "\u81ea\u52a8\u8bc6\u522b",
                        orderType = result.type,
                        brandName = result.brand,
                        fullText = result.fullText,
                        sourceApp = sourceApp,
                        sourcePackage = sourcePkg,
                        pickupLocation = result.pickupLocation
                    )
                    orderDao.insert(order)
                    insertedOrders.add(order)
                }
                if (insertedOrders.isEmpty()) return@launch

                // 每次新识别后立即重整分组：不依赖打开 App。
                DailyExpressGroupingHelper.regroupPendingExpressByDay(orderDao, orderGroupDao, applicationContext)
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
            } catch (e: Exception) {
                Log.e("CaptureLog", "Recognition failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "\u8bc6\u522b\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
                stopSelf()
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = "capture_service"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "\u6b63\u5728\u8bc6\u522b",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("\u6b63\u5728\u626b\u63cf\u5c4f\u5e55")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        AppLogger.service("ScreenCaptureService onDestroy")
        scope.cancel()
        virtualDisplay?.release()
        mediaProjection?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
