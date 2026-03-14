package com.Badnng.moe

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("ShareReceiver", "Share received")

        // 获取分享的图片
        val imageUri: Uri? = when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("image/") == true) {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                } else null
            }
            else -> null
        }

        if (imageUri != null) {
            lifecycleScope.launch {
                try {
                    // 读取图片
                    val inputStream = contentResolver.openInputStream(imageUri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (bitmap != null) {
                        // 裁剪图片（与快捷设置识别相同的区域）
                        val croppedBitmap = cropStatusBar(bitmap)
                        
                        // 识别图片
                        val helper = TextRecognitionHelper()
                        val result = helper.recognizeAll(croppedBitmap)
                        helper.close()

                        Log.d("ShareReceiver", "Recognition result: code=${result.code}, type=${result.type}, brand=${result.brand}")

                        // 如果识别到了取件码，保存订单
                        if (result.code != null) {
                            // 保存截图到本地
                            val screenshotFile = java.io.File(filesDir, "screenshots/shared_${System.currentTimeMillis()}.png")
                            screenshotFile.parentFile?.mkdirs()
                            val outputStream = java.io.FileOutputStream(screenshotFile)
                            croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                            outputStream.close()

                            val order = OrderEntity(
                                takeoutCode = result.code,
                                qrCodeData = result.qr,
                                screenshotPath = screenshotFile.absolutePath,
                                recognizedText = "分享识别",
                                orderType = result.type,
                                brandName = result.brand,
                                fullText = result.fullText,
                                pickupLocation = result.pickupLocation,
                                sourceApp = "分享识别"
                            )

                            // 保存到数据库
                            val database = OrderDatabase.getDatabase(this@ShareReceiverActivity)
                            database.orderDao().insert(order)

                            // 显示通知
                            val notificationHelper = NotificationHelper(this@ShareReceiverActivity)
                            notificationHelper.showPromotedLiveUpdate(order, result.brand)

                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ShareReceiverActivity, "识别成功：${result.code}", Toast.LENGTH_SHORT).show()
                            }

                            Log.d("ShareReceiver", "Order saved: ${result.code}")
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ShareReceiverActivity, "未识别到取件码", Toast.LENGTH_SHORT).show()
                            }
                            Log.d("ShareReceiver", "No code extracted from shared image")
                        }

                        bitmap.recycle()
                        if (croppedBitmap != bitmap) croppedBitmap.recycle()
                    }
                } catch (e: Exception) {
                    Log.e("ShareReceiver", "Error processing shared image", e)
                }

                // 关闭 Activity
                withContext(Dispatchers.Main) {
                    finish()
                    overridePendingTransition(0, 0)
                }
            }
        } else {
            // 没有图片，直接关闭
            finish()
            overridePendingTransition(0, 0)
        }
    }

    // 与 ScreenCaptureService 相同的裁剪逻辑
    private fun cropStatusBar(src: Bitmap): Bitmap {
        val statusBarHeight = 150
        val sideMargin = (src.width * 0.05).toInt()
        val targetWidth = (src.width * 0.9).toInt()
        val targetHeight = (src.height * 0.81).toInt()
        return if (src.height > statusBarHeight + targetHeight && src.width > sideMargin + targetWidth) {
            Bitmap.createBitmap(
                src,
                sideMargin,       // x: 从左边 5% 处开始
                statusBarHeight,  // y: 从状态栏高度处开始
                targetWidth,      // width: 宽度为原图的 90%
                targetHeight      // height: 高度为原图的 81%
            )
        } else {
            src
        }
    }
}