package com.Badnng.moe.recognition

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.Badnng.moe.ocr.MultiRecognitionResult
import com.Badnng.moe.ocr.OcrDiagnosticSnapshotCodec
import com.Badnng.moe.ocr.OcrDiagnosticsPreferences
import com.Badnng.moe.ocr.RecognitionResult
import com.Badnng.moe.ocr.TextRecognitionHelper
import com.Badnng.moe.privacy.PrivacyConsent
import com.Badnng.moe.rules.RecognitionRuleEngine
import com.Badnng.moe.rules.LuckinQrRule
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class RoutedRecognitionResult(
    val orders: List<RecognitionResult>,
    val metadata: RecognitionExecutionMetadata,
) {
    val usedOfflineFallback: Boolean get() = metadata.usedOfflineFallback
    val onlineError: String? get() = metadata.errorSummary
}

private data class OfflineImageRecognition(
    val orders: List<RecognitionResult>,
    val ocrDiagnosticData: String?,
)

class RecognitionRouter(context: Context) {
    private val appContext = context.applicationContext
    private val settings = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    suspend fun recognizeImage(
        bitmap: Bitmap,
        sourceApp: String? = null,
        sourcePackage: String? = null,
        trigger: RecognitionTrigger = RecognitionTrigger.IMPORTED_IMAGE,
    ): RoutedRecognitionResult {
        val startedAt = SystemClock.elapsedRealtime()
        if (!OnlineRecognitionPreferences.isOnline(appContext)) {
            val offlineResult = offlineRecognizeImage(bitmap, sourceApp, sourcePackage, trigger)
            return RoutedRecognitionResult(
                orders = offlineResult.orders,
                metadata = RecognitionExecutionMetadata(
                    mode = RecognitionMode.OFFLINE,
                    inputType = RecognitionInputType.IMAGE,
                    trigger = trigger,
                    durationMs = SystemClock.elapsedRealtime() - startedAt,
                    ocrDiagnosticData = offlineResult.ocrDiagnosticData,
                ),
            )
        }

        var provider: OnlineRecognitionProvider? = null
        var model: OnlineRecognitionModel? = null
        return try {
            requirePrivacyConsent()
            val selectedProvider = OnlineRecognitionPreferences.provider(appContext)
            val selectedModel = OnlineRecognitionPreferences.model(appContext, selectedProvider)
            provider = selectedProvider
            model = selectedModel
            val barcodeMatch = scanBarcode(bitmap)
            val qrCode = barcodeMatch?.value
            val qrBrand = barcodeMatch?.brand
            val orders = OnlineRecognitionClient(appContext).recognizeImage(
                bitmap = bitmap,
                provider = selectedProvider,
                model = selectedModel,
                mimoBillingMode = OnlineRecognitionPreferences.mimoBillingMode(appContext),
                barcodeBrandHint = qrBrand?.name,
            )
            if (orders.isEmpty()) {
                val message = qrBrand?.let { "在线识别未返回${it.name}二维码订单" } ?: "在线识别未返回有效订单"
                throw OnlineRecognitionException(message)
            }
            RoutedRecognitionResult(
                orders = mergeBarcodeResult(orders, qrCode, qrBrand),
                metadata = RecognitionExecutionMetadata(
                    mode = RecognitionMode.ONLINE,
                    inputType = RecognitionInputType.IMAGE,
                    trigger = trigger,
                    providerKey = selectedProvider.key,
                    modelId = selectedModel.id,
                    durationMs = SystemClock.elapsedRealtime() - startedAt,
                ),
            )
        } catch (error: Exception) {
            val secrets = diagnosticSecrets(provider)
            val message = RecognitionDiagnosticRedactor.redact(error.message ?: "在线识别失败", secrets)
                ?: "在线识别失败"
            notifyOfflineFallback()
            val offlineResult = offlineRecognizeImage(bitmap, sourceApp, sourcePackage, trigger)
            RoutedRecognitionResult(
                orders = offlineResult.orders,
                metadata = RecognitionExecutionMetadata(
                    mode = RecognitionMode.ONLINE,
                    inputType = RecognitionInputType.IMAGE,
                    trigger = trigger,
                    providerKey = provider?.key,
                    modelId = model?.id,
                    usedOfflineFallback = true,
                    errorSummary = message,
                    errorDetail = RecognitionDiagnosticRedactor.redact(
                        (error as? OnlineRecognitionException)?.diagnosticDetail,
                        secrets,
                    ),
                    durationMs = SystemClock.elapsedRealtime() - startedAt,
                    ocrDiagnosticData = offlineResult.ocrDiagnosticData,
                ),
            )
        }
    }

    suspend fun recognizeText(
        text: String,
        source: RecognitionTextSource = RecognitionTextSource.General,
        trigger: RecognitionTrigger = source.defaultTrigger(),
    ): RoutedRecognitionResult {
        val startedAt = SystemClock.elapsedRealtime()
        if (shouldBlockText(text, source)) {
            return RoutedRecognitionResult(
                orders = emptyList(),
                metadata = RecognitionExecutionMetadata(
                    mode = RecognitionMode.OFFLINE,
                    inputType = RecognitionInputType.TEXT,
                    trigger = trigger,
                    durationMs = SystemClock.elapsedRealtime() - startedAt,
                ),
            )
        }

        if (!OnlineRecognitionPreferences.isOnline(appContext)) {
            return RoutedRecognitionResult(
                orders = offlineRecognizeText(text, source),
                metadata = RecognitionExecutionMetadata(
                    mode = RecognitionMode.OFFLINE,
                    inputType = RecognitionInputType.TEXT,
                    trigger = trigger,
                    durationMs = SystemClock.elapsedRealtime() - startedAt,
                ),
            )
        }

        var provider: OnlineRecognitionProvider? = null
        var model: OnlineRecognitionModel? = null
        return try {
            requirePrivacyConsent()
            val selectedProvider = OnlineRecognitionPreferences.provider(appContext)
            val selectedModel = OnlineRecognitionPreferences.model(appContext, selectedProvider)
            provider = selectedProvider
            model = selectedModel
            RoutedRecognitionResult(
                orders = OnlineRecognitionClient(appContext).recognizeText(
                    text = text,
                    provider = selectedProvider,
                    model = selectedModel,
                    mimoBillingMode = OnlineRecognitionPreferences.mimoBillingMode(appContext),
                ),
                metadata = RecognitionExecutionMetadata(
                    mode = RecognitionMode.ONLINE,
                    inputType = RecognitionInputType.TEXT,
                    trigger = trigger,
                    providerKey = selectedProvider.key,
                    modelId = selectedModel.id,
                    durationMs = SystemClock.elapsedRealtime() - startedAt,
                ),
            )
        } catch (error: Exception) {
            val secrets = diagnosticSecrets(provider)
            val message = RecognitionDiagnosticRedactor.redact(error.message ?: "在线识别失败", secrets)
                ?: "在线识别失败"
            notifyOfflineFallback()
            RoutedRecognitionResult(
                orders = offlineRecognizeText(text, source),
                metadata = RecognitionExecutionMetadata(
                    mode = RecognitionMode.ONLINE,
                    inputType = RecognitionInputType.TEXT,
                    trigger = trigger,
                    providerKey = provider?.key,
                    modelId = model?.id,
                    usedOfflineFallback = true,
                    errorSummary = message,
                    errorDetail = RecognitionDiagnosticRedactor.redact(
                        (error as? OnlineRecognitionException)?.diagnosticDetail,
                        secrets,
                    ),
                    durationMs = SystemClock.elapsedRealtime() - startedAt,
                ),
            )
        }
    }

    suspend fun recognizeTextOffline(
        text: String,
        source: RecognitionTextSource = RecognitionTextSource.General,
        trigger: RecognitionTrigger = source.defaultTrigger(),
    ): RoutedRecognitionResult {
        val startedAt = SystemClock.elapsedRealtime()
        val orders = if (shouldBlockText(text, source)) {
            emptyList()
        } else {
            offlineRecognizeText(text, source)
        }
        return RoutedRecognitionResult(
            orders = orders,
            metadata = RecognitionExecutionMetadata(
                mode = RecognitionMode.OFFLINE,
                inputType = RecognitionInputType.TEXT,
                trigger = trigger,
                durationMs = SystemClock.elapsedRealtime() - startedAt,
            ),
        )
    }

    private fun shouldBlockText(text: String, source: RecognitionTextSource): Boolean {
        if (!source.supportsCustomBlocking) return false
        val blocked = RecognitionBlockedWordsPreferences.firstMatch(appContext, text) != null
        if (blocked) {
            Log.i(TAG, "Text recognition skipped by custom block word: source=$source")
        }
        return blocked
    }

    private fun requirePrivacyConsent() {
        if (!PrivacyConsent.isCurrentPolicyAccepted(settings)) {
            throw OnlineRecognitionException("尚未同意《澎湃记用户协议与隐私说明》")
        }
    }

    private suspend fun offlineRecognizeImage(
        bitmap: Bitmap,
        sourceApp: String?,
        sourcePackage: String?,
        trigger: RecognitionTrigger,
    ): OfflineImageRecognition {
        val recognitionBitmap = cropForOfflineRecognition(bitmap, trigger)
        if (!RecognitionRuleEngine.isInitialized) {
            RecognitionRuleEngine.initialize(appContext)
        }
        val helper = TextRecognitionHelper(appContext)
        return try {
            if (!helper.paddleOcr.isInitialized) helper.initOcr()
            val (singleResult, ocrResult) = helper.recognizeAll(recognitionBitmap, sourceApp, sourcePackage)
            val hasExpressKeyword = singleResult.fullText.contains("取件") ||
                singleResult.fullText.contains("取货") ||
                singleResult.fullText.contains("快递") ||
                singleResult.fullText.contains("驿站") ||
                singleResult.fullText.contains("菜鸟")
            val usesSimpleRulePack = com.Badnng.moe.rules.SimpleRuleRuntime.current().schemaVersion ==
                com.Badnng.moe.rules.SimpleRulePack.SCHEMA_VERSION
            val multiResult = if (usesSimpleRulePack || hasExpressKeyword || singleResult.type == "快递") {
                helper.recognizeMultipleCodesFromResult(
                    rawFullText = ocrResult.rawFullText,
                    textBlocks = ocrResult.textBlocks,
                    mergedText = ocrResult.mergedText,
                    sourceApp = sourceApp,
                    sourcePkg = sourcePackage,
                    qrData = singleResult.qr,
                    simpleRuleMatches = ocrResult.simpleRuleMatches,
                )
            } else {
                MultiRecognitionResult(emptyList(), false)
            }
            val orders = when {
                multiResult.orders.isNotEmpty() -> multiResult.orders
                singleResult.code != null -> listOf(singleResult)
                else -> listOf(singleResult)
            }
            val diagnosticData = if (OcrDiagnosticsPreferences.shouldCapture(appContext)) {
                ocrResult.diagnosticResult?.let(OcrDiagnosticSnapshotCodec::encode)
            } else {
                null
            }
            OfflineImageRecognition(orders, diagnosticData)
        } finally {
            helper.close()
            if (recognitionBitmap !== bitmap && !recognitionBitmap.isRecycled) {
                recognitionBitmap.recycle()
            }
        }
    }

    private fun cropForOfflineRecognition(bitmap: Bitmap, trigger: RecognitionTrigger): Bitmap {
        // 分享/导入的图片不一定是完整系统截图，顶部和底部都可能紧贴有效内容。
        // 旧逻辑固定裁掉顶部 150px，并且只保留 81% 高度，会直接丢失列表首尾项目。
        // 离线识别仍保留水平方向的轻量裁剪，用于排除截图两侧黑边，但不再裁掉纵向内容。
        val sideRatio = when (trigger) {
            RecognitionTrigger.IMPORTED_IMAGE -> 0.02f
            RecognitionTrigger.SHARED_IMAGE -> 0.02f
            RecognitionTrigger.SCREEN_CAPTURE -> 0.04f
            else -> 0f
        }
        val sideMargin = (bitmap.width * sideRatio).toInt()
        val targetWidth = bitmap.width - sideMargin * 2
        return if (sideMargin > 0 && targetWidth > 0 && targetWidth < bitmap.width) {
            Bitmap.createBitmap(bitmap, sideMargin, 0, targetWidth, bitmap.height)
        } else {
            bitmap
        }
    }

    private suspend fun offlineRecognizeText(text: String, source: RecognitionTextSource): List<RecognitionResult> {
        if (!RecognitionRuleEngine.isInitialized) {
            RecognitionRuleEngine.initialize(appContext)
        }
        val helper = TextRecognitionHelper(appContext)
        return try {
            helper.recognizeFromText(text, when (source) {
                RecognitionTextSource.Sms -> com.Badnng.moe.rules.SimpleRuleSource.SMS
                RecognitionTextSource.Notification -> com.Badnng.moe.rules.SimpleRuleSource.NOTIFICATION
                RecognitionTextSource.General -> com.Badnng.moe.rules.SimpleRuleSource.TEXT
            })
        } finally {
            helper.close()
        }
    }

    private suspend fun scanBarcode(bitmap: Bitmap): BarcodeMatch? {
        val scanner = runCatching { BarcodeScanning.getClient() }.getOrNull() ?: return null
        val values = try {
            val barcodes = withContext(Dispatchers.Main) {
                scanner.process(InputImage.fromBitmap(bitmap, 0)).await()
            }
            barcodes.mapNotNull { barcode ->
                barcode.rawValue?.trim()?.takeIf { value ->
                    value.isNotEmpty() &&
                        !value.startsWith("http://", ignoreCase = true) &&
                        !value.startsWith("https://", ignoreCase = true)
                }
            }.distinct()
        } catch (_: Exception) {
            emptyList()
        } finally {
            scanner.close()
        }
        if (values.isEmpty()) return null

        // 图片中可能同时存在多个二维码。规则命中的品牌二维码优先于普通二维码，
        // 避免 ML Kit 返回顺序导致瑞幸二维码没有参与在线结果校正。
        for (value in values) {
            val brand = findBarcodeBrand(value)
            if (brand != null) return BarcodeMatch(value, brand)
        }
        return BarcodeMatch(values.first(), null)
    }

    private fun diagnosticSecrets(provider: OnlineRecognitionProvider?): List<String> =
        provider?.let { selectedProvider ->
            SecureApiKeyStore(appContext).get(selectedProvider)?.let { secret -> listOf(secret) }
        }.orEmpty()

    private suspend fun findBarcodeBrand(value: String): BarcodeBrandMatch? {
        if (LuckinQrRule.matches(value)) {
            return BarcodeBrandMatch(LuckinQrRule.BRAND_NAME, LuckinQrRule.CATEGORY)
        }
        if (!RecognitionRuleEngine.isInitialized) {
            RecognitionRuleEngine.initialize(appContext)
        }
        return RecognitionRuleEngine.getAllBrands().firstNotNullOfOrNull { brand ->
            val pattern = brand.qrPattern ?: return@firstNotNullOfOrNull null
            if (runCatching { Regex(pattern).matches(value) }.getOrDefault(false)) {
                BarcodeBrandMatch(brand.name, brand.category)
            } else {
                null
            }
        }
    }

    private fun mergeBarcodeResult(
        orders: List<RecognitionResult>,
        qrCode: String?,
        qrBrand: BarcodeBrandMatch?,
    ): List<RecognitionResult> {
        if (orders.isEmpty() || qrCode == null) return orders
        val targetIndex = if (qrBrand == null) {
            0
        } else {
            orders.indexOfFirst { result ->
                result.brand?.let { brand ->
                    brand == qrBrand.name ||
                        RecognitionRuleEngine.getBrandByName(brand)?.name == qrBrand.name
                } == true
            }.takeIf { it >= 0 } ?: 0
        }
        return orders.mapIndexed { index, result ->
            if (index == targetIndex) {
                result.copy(
                    qr = qrCode,
                    type = qrBrand?.category ?: result.type,
                    brand = qrBrand?.name ?: result.brand,
                )
            } else {
                result
            }
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
        const val TAG = "RecognitionRouter"
        const val FALLBACK_TOAST_INTERVAL_MS = 30_000L
        var lastFallbackToastAt = 0L
    }

    private data class BarcodeBrandMatch(
        val name: String,
        val category: String,
    )

    private data class BarcodeMatch(
        val value: String,
        val brand: BarcodeBrandMatch?,
    )
}

private fun RecognitionTextSource.defaultTrigger(): RecognitionTrigger = when (this) {
    RecognitionTextSource.General -> RecognitionTrigger.PROCESS_TEXT
    RecognitionTextSource.Sms -> RecognitionTrigger.SMS
    RecognitionTextSource.Notification -> RecognitionTrigger.NOTIFICATION
}
