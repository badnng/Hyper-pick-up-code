package com.Badnng.moe

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity

class ProcessTextActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 获取用户选中的文字
        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()

        Log.d("ProcessTextActivity", "Received text: $selectedText")

        if (!selectedText.isNullOrBlank()) {
            // 启动后台服务处理识别
            val serviceIntent = Intent(this, ProcessTextRecognitionService::class.java).apply {
                putExtra("selectedText", selectedText)
            }
            startForegroundService(serviceIntent)
        }

        // 立即关闭 Activity，不阻塞用户操作
        finish()
        overridePendingTransition(0, 0)
    }
}