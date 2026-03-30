package com.Badnng.moe

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
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            if (!RootHelper.hasRootAccess()) {
                Toast.makeText(applicationContext, "Root 不可用，无法执行截图", Toast.LENGTH_SHORT).show()
                stopSelf()
                return START_NOT_STICKY
            }
            startRootCaptureSingleTry()
        } else if (useShizuku) {
            startShizukuCaptureSingleTry()
        } else {
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
                    val cropped = cropStatusBar(bitmap)
                    recognizeAndStop(cropped, appName, pkg, triggeredByAccessibilityShortcut)
                } else {
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e("CaptureLog", "Shizuku capture failed", e)
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
                    val cropped = cropStatusBar(bitmap)
                    recognizeAndStop(cropped, appName, pkg, triggeredByAccessibilityShortcut)
                } else {
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
                val cropped = cropStatusBar(cleanBitmap)
                recognizeAndStop(cropped, appName, pkg, triggeredByAccessibilityShortcut)
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

    private fun recognizeAndStop(bitmap: Bitmap, sourceApp: String?, sourcePkg: String?, triggeredByAccessibilityShortcut: Boolean) {
        scope.launch {
            var helper: TextRecognitionHelper? = null

            try {
                helper = TextRecognitionHelper(applicationContext)
                if (!helper.paddleOcr.isInitialized) {
                    helper.initOcr()
                }

                val singleResult = helper.recognizeAll(bitmap, sourceApp, sourcePkg)
                val hasExpressKeyword = singleResult.fullText.contains("\u53d6\u4ef6") ||
                    singleResult.fullText.contains("\u53d6\u8d27") ||
                    singleResult.fullText.contains("\u5feb\u9012") ||
                    singleResult.fullText.contains("\u9a7f\u7ad9") ||
                    singleResult.fullText.contains("\u83dc\u9e1f")
                val multiResult = if (hasExpressKeyword || singleResult.type == "\u5feb\u9012") {
                    helper.recognizeMultipleCodes(bitmap, sourceApp, sourcePkg)
                } else {
                    MultiRecognitionResult(emptyList(), false)
                }
                val recognizedOrders = when {
                    multiResult.hasMultipleCodes && multiResult.orders.size > 1 -> multiResult.orders
                    singleResult.code != null -> listOf(singleResult)
                    multiResult.orders.isNotEmpty() -> multiResult.orders
                    else -> emptyList()
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

                if (multiResult.hasMultipleCodes && recognizedOrders.size > 1) {
                    val existingGroups = orderGroupDao.getAllGroupsList()
                    val maxGroupNumber = existingGroups
                        .map { it.name }
                        .filter { it.startsWith("\u7ec4") }
                        .mapNotNull { it.removePrefix("\u7ec4").toIntOrNull() }
                        .maxOrNull() ?: 0
                    val groupName = "\u7ec4${maxGroupNumber + 1}"

                    val group = OrderGroup(
                        name = groupName,
                        orderType = recognizedOrders.first().type,
                        brandName = recognizedOrders.first().brand,
                        screenshotPath = screenshotFile.absolutePath,
                        recognizedText = recognizedOrders.joinToString("\n") { it.fullText },
                        sourceApp = sourceApp,
                        sourcePackage = sourcePkg,
                        createdAt = System.currentTimeMillis(),
                        isCompleted = false,
                        orderCount = recognizedOrders.size
                    )
                    val groupId = orderGroupDao.insertGroup(group)

                    val insertedOrders = mutableListOf<OrderEntity>()
                    for (result in recognizedOrders) {
                        val code = result.code ?: continue
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
                            pickupLocation = result.pickupLocation,
                            groupId = groupId
                        )
                        orderDao.insert(order)
                        insertedOrders.add(order)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext,
                            "\u8bc6\u522b\u5230${recognizedOrders.size}\u4e2a\u53d6\u4ef6\u7801",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    NotificationHelper(applicationContext)
                        .showGroupNotification(group.copy(id = groupId), insertedOrders)
                } else {
                    val result = recognizedOrders.first()
                    val code = result.code ?: return@launch
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

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext,
                            "\u8bc6\u522b\u6210\u529f: $code",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    NotificationHelper(applicationContext).showPromotedLiveUpdate(order, order.brandName)
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
                try {
                    helper?.close()
                } catch (e: Exception) {
                    Log.e("CaptureLog", "Failed to close helper", e)
                }

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
        scope.cancel()
        virtualDisplay?.release()
        mediaProjection?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
