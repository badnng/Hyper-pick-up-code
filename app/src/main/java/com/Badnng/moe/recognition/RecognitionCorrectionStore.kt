package com.Badnng.moe.recognition

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.helper.ScreenshotStorage
import com.Badnng.moe.ocr.RecognitionResult

/** 保存由用户主动发起、但尚未提取到码值的图片识别结果。 */
object RecognitionCorrectionStore {
    private const val TAG = "RecognitionMonitor"

    suspend fun saveImageDraft(
        context: Context,
        bitmap: Bitmap,
        result: RecognitionResult,
        metadata: RecognitionExecutionMetadata,
        recognizedText: String,
        sourceApp: String? = null,
        sourcePackage: String? = null,
        screenshotPrefix: String = "待纠正识别",
        existingScreenshotPath: String? = null,
    ): Boolean {
        if (result.code != null || result.fullText.isBlank()) return false
        if (metadata.trigger !in USER_IMAGE_TRIGGERS) return false

        val appContext = context.applicationContext
        val orderDao = OrderDatabase.getDatabase(appContext).orderDao()
        val duplicate = orderDao.findPendingCorrectionByText(result.fullText)
        if (duplicate != null) {
            Log.d(TAG, "待纠正识别已存在: id=${duplicate.id}, trigger=${metadata.trigger.key}")
            return true
        }

        val screenshotPath = existingScreenshotPath ?: ScreenshotStorage.saveBitmap(
            appContext,
            bitmap,
            namePrefix = screenshotPrefix,
        )
        val draft = RecognizedOrderFactory.correctionDraft(
            result = result,
            metadata = metadata,
            screenshotPath = screenshotPath,
            recognizedText = recognizedText,
            sourceApp = sourceApp,
            sourcePackage = sourcePackage,
        )
        orderDao.insert(draft)
        Log.d(TAG, "已保存待纠正识别: id=${draft.id}, trigger=${metadata.trigger.key}, textLength=${result.fullText.length}")
        return true
    }


    /** 保存由用户主动发起、但尚未提取到码值的文本识别结果。 */
    suspend fun saveTextDraft(
        context: Context,
        result: RecognitionResult,
        metadata: RecognitionExecutionMetadata,
        recognizedText: String,
        sourceApp: String? = null,
        sourcePackage: String? = null,
    ): Boolean {
        if (result.code != null || result.fullText.isBlank()) return false
        if (metadata.trigger !in USER_TEXT_TRIGGERS) return false

        val appContext = context.applicationContext
        val orderDao = OrderDatabase.getDatabase(appContext).orderDao()
        val duplicate = orderDao.findPendingCorrectionByText(result.fullText)
        if (duplicate != null) {
            Log.d(TAG, "待纠正识别已存在: id=${duplicate.id}, trigger=${metadata.trigger.key}")
            return true
        }

        val draft = RecognizedOrderFactory.correctionDraft(
            result = result,
            metadata = metadata,
            screenshotPath = "",
            recognizedText = recognizedText,
            sourceApp = sourceApp,
            sourcePackage = sourcePackage,
        )
        orderDao.insert(draft)
        Log.d(TAG, "已保存文本待纠正识别: id=${draft.id}, trigger=${metadata.trigger.key}, textLength=${result.fullText.length}")
        return true
    }

    private val USER_IMAGE_TRIGGERS = setOf(
        RecognitionTrigger.SCREEN_CAPTURE,
        RecognitionTrigger.SHARED_IMAGE,
        RecognitionTrigger.IMPORTED_IMAGE,
    )

    private val USER_TEXT_TRIGGERS = setOf(
        RecognitionTrigger.PROCESS_TEXT,
    )
}
