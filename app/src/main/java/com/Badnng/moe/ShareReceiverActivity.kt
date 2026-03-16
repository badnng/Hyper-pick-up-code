package com.Badnng.moe

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity

class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("ShareReceiver", "Share received")

        // 获取分享的图片 URI
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
            // 启动后台服务处理识别
            val serviceIntent = Intent(this, ShareRecognitionService::class.java).apply {
                putExtra("imageUri", imageUri)
            }
            startForegroundService(serviceIntent)
        }

        // 立即关闭 Activity，不阻塞用户操作
        finish()
        overridePendingTransition(0, 0)
    }
}