package com.Badnng.moe

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProcessTextActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置空视图，避免 WindowInsets 警告
        val rootView = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // 禁用 WindowInsets 分发
            setOnApplyWindowInsetsListener { v, insets ->
                WindowInsets.Builder().build()
            }
        }
        setContentView(rootView)

        // 获取用户选中的文字
        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()

        Log.d("ProcessTextActivity", "Received text: $selectedText")

        if (!selectedText.isNullOrBlank()) {
            lifecycleScope.launch {
                try {
                    // 解析文字
                    val helper = TextRecognitionHelper()
                    val result = helper.recognizeFromText(selectedText)
                    helper.close()

                    // 如果识别到了取件码，保存订单
                    if (result.code != null) {
                        val order = OrderEntity(
                            takeoutCode = result.code,
                            qrCodeData = result.qr,
                            screenshotPath = "", // 纯文字模式无截图
                            recognizedText = selectedText,
                            orderType = result.type,
                            brandName = result.brand,
                            fullText = result.fullText,
                            pickupLocation = result.pickupLocation,
                            sourceApp = "文字选择"
                        )

                        // 保存到数据库
                        val database = OrderDatabase.getDatabase(this@ProcessTextActivity)
                        database.orderDao().insert(order)

                        // 显示通知
                        val notificationHelper = NotificationHelper(this@ProcessTextActivity)
                        notificationHelper.showPromotedLiveUpdate(order, result.brand)

                        Log.d("ProcessTextActivity", "Order saved: ${result.code}")
                    } else {
                        Log.d("ProcessTextActivity", "No code extracted from text")
                    }
                } catch (e: Exception) {
                    Log.e("ProcessTextActivity", "Error processing text", e)
                }

                // 关闭 Activity
                withContext(Dispatchers.Main) {
                    finish()
                    overridePendingTransition(0, 0)
                }
            }
        } else {
            // 没有文字，直接关闭
            finish()
            overridePendingTransition(0, 0)
        }
    }
}
