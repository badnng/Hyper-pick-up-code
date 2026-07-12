package com.Badnng.moe.recognition

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.widget.Toast
import com.Badnng.moe.ocr.MultiRecognitionResult
import com.Badnng.moe.ocr.RecognitionResult
import com.Badnng.moe.ocr.TextRecognitionHelper
import com.Badnng.moe.privacy.PrivacyConsent
import com.Badnng.moe.rules.RecognitionRuleEngine
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class RoutedRecognitionResult(
    val orders: List<RecognitionResult>,
    val usedOfflineFallback: Boolean = false,
    val onlineError: String? = null,
)

class RecognitionRouter(context: Context) {
    private val appContext = context.applicationContext
    private val settings = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    suspend fun recognizeImage(
        bitmap: Bitmap,
        sourceApp: String? = null,
        sourcePackage: String? = null,
    ): RoutedRecognitionResult {
        if (!OnlineRecognitionPreferences.isOnline(appContext)) {
            return RoutedRecognitionResult(offlineRecognizeImage(bitmap, sourceApp, sourcePackage))
        }

        return try {
            requirePrivacyConsent()
            val provider = OnlineRecognitionPreferences.provider(appContext)
            val model = OnlineRecognitionPreferences.model(appContext, provider)
            val orders = OnlineRecognitionClient(appContext).recognizeImage(
                bitmap = bitmap,
                provider = provider,
                model = model,
                mimoBillingMode = OnlineRecognitionPreferences.mimoBillingMode(appContext),
            )
            val qrCode = if (orders.isNotEmpty()) scanBarcode(bitmap) else null
            RoutedRecognitionResult(
                orders = if (qrCode != null) {
                    orders.mapIndexed { index, result ->
                        if (index == 0) result.copy(qr = qrCode) else result
                    }
                } else {
                    orders
                }
            )
        } catch (error: Exception) {
            val message = error.message ?: "在线识别失败"
            notifyOfflineFallback()
            RoutedRecognitionResult(
                orders = offlineRecognizeImage(bitmap, sourceApp, sourcePackage),
                usedOfflineFallback = true,
                onlineError = message,
            )
        }
    }

    suspend fun recognizeText(text: String): RoutedRecognitionResult {
        if (!OnlineRecognitionPreferences.isOnline(appContext)) {
            return RoutedRecognitionResult(offlineRecognizeText(text))
        }

        return try {
            requirePrivacyConsent()
            val provider = OnlineRecognitionPreferences.provider(appContext)
            val model = OnlineRecognitionPreferences.model(appContext, provider)
            RoutedRecognitionResult(
                orders = OnlineRecognitionClient(appContext).recognizeText(
                    text = text,
                    provider = provider,
                    model = model,
                    mimoBillingMode = OnlineRecognitionPreferences.mimoBillingMode(appContext),
                )
            )
        } catch (error: Exception) {
            val message = error.message ?: "在线识别失败"
            notifyOfflineFallback()
            RoutedRecognitionResult(
                orders = offlineRecognizeText(text),
                usedOfflineFallback = true,
                onlineError = message,
            )
        }
    }

    suspend fun recognizeTextOffline(text: String): List<RecognitionResult> =
        offlineRecognizeText(text)

    private fun requirePrivacyConsent() {
        if (!PrivacyConsent.isAccepted(settings)) {
            throw OnlineRecognitionException("尚未同意《澎湃记用户协议与隐私说明》")
        }
    }

    private suspend fun offlineRecognizeImage(
        bitmap: Bitmap,
        sourceApp: String?,
        sourcePackage: String?,
    ): List<RecognitionResult> {
        if (!RecognitionRuleEngine.isInitialized) {
            RecognitionRuleEngine.initialize(appContext)
        }
        val helper = TextRecognitionHelper(appContext)
        return try {
            if (!helper.paddleOcr.isInitialized) helper.initOcr()
            val (singleResult, ocrResult) = helper.recognizeAll(bitmap, sourceApp, sourcePackage)
            val hasExpressKeyword = singleResult.fullText.contains("取件") ||
                singleResult.fullText.contains("取货") ||
                singleResult.fullText.contains("快递") ||
                singleResult.fullText.contains("驿站") ||
                singleResult.fullText.contains("菜鸟")
            val multiResult = if (hasExpressKeyword || singleResult.type == "快递") {
                helper.recognizeMultipleCodesFromResult(
                    ocrResult.rawFullText,
                    ocrResult.textBlocks,
                    ocrResult.mergedText,
                    sourceApp,
                    sourcePackage,
                )
            } else {
                MultiRecognitionResult(emptyList(), false)
            }
            when {
                multiResult.hasMultipleCodes && multiResult.orders.size > 1 -> multiResult.orders
                singleResult.code != null -> listOf(singleResult)
                multiResult.orders.isNotEmpty() -> multiResult.orders
                else -> emptyList()
            }
        } finally {
            helper.close()
        }
    }

    private suspend fun offlineRecognizeText(text: String): List<RecognitionResult> {
        if (!RecognitionRuleEngine.isInitialized) {
            RecognitionRuleEngine.initialize(appContext)
        }
        val helper = TextRecognitionHelper(appContext)
        return try {
            helper.recognizeFromText(text)
        } finally {
            helper.close()
        }
    }

    private suspend fun scanBarcode(bitmap: Bitmap): String? {
        val scanner = runCatching { BarcodeScanning.getClient() }.getOrNull() ?: return null
        return try {
            val barcodes = withContext(Dispatchers.Main) {
                scanner.process(InputImage.fromBitmap(bitmap, 0)).await()
            }
            barcodes.firstNotNullOfOrNull { barcode ->
                barcode.rawValue?.takeUnless {
                    it.startsWith("http://", ignoreCase = true) ||
                        it.startsWith("https://", ignoreCase = true)
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            scanner.close()
        }
    }

    private suspend fun notifyOfflineFallback() {
        val now = SystemClock.elapsedRealtime()
        val shouldNotify = synchronized(RecognitionRouter::class.java) {
            if (now - lastFallbackToastAt < FALLBACK_TOAST_INTERVAL_MS) {
                false
            } else {
                lastFallbackToastAt = now
                true
            }
        }
        if (shouldNotify) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    appContext,
                    "在线识别失败，已切换为离线识别",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private companion object {
        const val FALLBACK_TOAST_INTERVAL_MS = 30_000L
        var lastFallbackToastAt = 0L
    }
}
