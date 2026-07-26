package com.Badnng.moe.recognition

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.Badnng.moe.ocr.RecognitionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class OnlineRecognitionClient(context: Context) {
    private val appContext = context.applicationContext
    private val apiKeyStore = SecureApiKeyStore(appContext)
    private val isDebuggable =
        appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val systemPrompt by lazy { OnlineRecognitionPreferences.effectivePrompt(appContext) }

    suspend fun recognizeImage(
        bitmap: Bitmap,
        provider: OnlineRecognitionProvider,
        model: OnlineRecognitionModel,
        mimoBillingMode: MimoBillingMode,
        barcodeBrandHint: String? = null,
    ): List<RecognitionResult> {
        val imageDataUrl = bitmap.toImageDataUrl()
        return request(
            provider = provider,
            model = model,
            mimoBillingMode = mimoBillingMode,
            imageDataUrl = imageDataUrl,
            sourceText = null,
            imageHint = barcodeBrandHint?.let(::buildBarcodeBrandHint),
        )
    }

    suspend fun recognizeText(
        text: String,
        provider: OnlineRecognitionProvider,
        model: OnlineRecognitionModel,
        mimoBillingMode: MimoBillingMode,
    ): List<RecognitionResult> = request(
        provider = provider,
        model = model,
        mimoBillingMode = mimoBillingMode,
        imageDataUrl = null,
        sourceText = text,
        imageHint = null,
    )

    suspend fun fetchCustomModels(baseUrl: String): List<OnlineRecognitionModel> =
        withContext(Dispatchers.IO) {
            val apiKey = apiKeyStore.get(OnlineRecognitionProvider.CUSTOM)
                ?.takeIf { it.isNotBlank() }
                ?: throw OnlineRecognitionException("请先填写自定义供应商 API 密钥")
            val normalizedBaseUrl = normalizeCustomBaseUrl(baseUrl)
            val request = Request.Builder()
                .url("$normalizedBaseUrl/models")
                .get()
                .header("Authorization", "Bearer $apiKey")
                .build()

            HTTP_CLIENT.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw OnlineRecognitionException(
                        "获取模型失败（HTTP ${response.code}）"
                    )
                }
                val root = runCatching { JSONObject(responseText) }
                    .getOrElse { throw OnlineRecognitionException("模型列表格式不正确") }
                val data = root.optJSONArray("data")
                    ?: throw OnlineRecognitionException("供应商没有返回模型列表")
                buildList {
                    for (index in 0 until data.length()) {
                        val id = data.optJSONObject(index)
                            ?.optString("id")
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?: continue
                        add(OnlineRecognitionModel(id, id, ReasoningControl.NONE))
                    }
                }.distinctBy { it.id }.sortedBy { it.displayName.lowercase() }
                    .takeIf { it.isNotEmpty() }
                    ?: throw OnlineRecognitionException("供应商没有返回可用模型")
            }
        }

    private suspend fun request(
        provider: OnlineRecognitionProvider,
        model: OnlineRecognitionModel,
        mimoBillingMode: MimoBillingMode,
        imageDataUrl: String?,
        sourceText: String?,
        imageHint: String?,
    ): List<RecognitionResult> = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        val inputType = if (imageDataUrl != null) "image" else "text"
        Log.i(
            TAG,
            "request start: provider=${provider.displayName}, model=${model.id}, " +
                "input=$inputType, textLength=${sourceText?.length ?: 0}",
        )

        try {
            val apiKey = apiKeyStore.get(provider)
                ?.takeIf { it.isNotBlank() }
                ?: throw OnlineRecognitionException("尚未配置 ${provider.displayName} API 密钥")
            if (provider == OnlineRecognitionProvider.CUSTOM && model.id.isBlank()) {
                throw OnlineRecognitionException("尚未获取或选择自定义供应商模型")
            }

            val isResponsesApi = provider == OnlineRecognitionProvider.OPENAI ||
                provider == OnlineRecognitionProvider.MINIMAX ||
                (provider == OnlineRecognitionProvider.CUSTOM &&
                    OnlineRecognitionPreferences.customRequestMode(appContext) ==
                    CustomRequestMode.RESPONSES)
            val requestUrl = endpoint(provider, mimoBillingMode, isResponsesApi)
            val body = if (isResponsesApi) {
                createResponsesBody(model, imageDataUrl, sourceText, imageHint, provider)
            } else {
                createChatCompletionsBody(model, imageDataUrl, sourceText, imageHint)
            }
            logFullPayload("request url", requestUrl)
            logFullPayload("request body", body.toString())
            val request = Request.Builder()
                .url(requestUrl)
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json")
                .apply {
                    if (provider == OnlineRecognitionProvider.MIMO) {
                        header("api-key", apiKey)
                    } else {
                        header("Authorization", "Bearer $apiKey")
                    }
                }
                .build()

            HTTP_CLIENT.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                logFullPayload("response body", responseText)
                val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                Log.i(
                    TAG,
                    "response: provider=${provider.displayName}, model=${model.id}, " +
                        "http=${response.code}, elapsedMs=$elapsedMs, bodyLength=${responseText.length}",
                )
                if (!response.isSuccessful) {
                    Log.w(TAG, "error response: ${responseText.logSnippet()}")
                    throw OnlineRecognitionException(
                        message = httpErrorMessage(response.code, provider),
                        diagnosticDetail = responseText,
                        httpStatus = response.code,
                    )
                }
                try {
                    val content = if (isResponsesApi) {
                        extractResponsesContent(responseText)
                    } else {
                        extractChatContent(responseText)
                    }
                    Log.d(TAG, "model content: ${content.logSnippet()}")
                    parseRecognitionContent(content, sourceText).also(::logRecognitionResults)
                } catch (error: OnlineRecognitionException) {
                    if (error.diagnosticDetail != null) throw error
                    throw OnlineRecognitionException(
                        message = error.message ?: "在线识别响应处理失败",
                        diagnosticDetail = responseText,
                        httpStatus = response.code,
                    )
                }
            }
        } catch (error: Exception) {
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            Log.e(
                TAG,
                "request failed: provider=${provider.displayName}, model=${model.id}, " +
                    "input=$inputType, elapsedMs=$elapsedMs, error=${error.message}",
                error,
            )
            throw error
        }
    }

    private fun logRecognitionResults(results: List<RecognitionResult>) {
        Log.i(TAG, "parsed results: count=${results.size}, codes=${results.map { it.code }}")
        results.forEachIndexed { index, result ->
            Log.d(
                TAG,
                "result[$index]: code=${result.code}, type=${result.type}, brand=${result.brand}, " +
                    "pickupLocation=${result.pickupLocation}, fullText=${result.fullText.logSnippet()}",
            )
        }
    }

    private fun String.logSnippet(): String =
        if (length <= MAX_LOG_TEXT_LENGTH) this else take(MAX_LOG_TEXT_LENGTH) + "...[truncated]"

    private fun logFullPayload(label: String, value: String) {
        if (!isDebuggable) return
        val chunks = value.chunked(MAX_LOG_CHUNK_LENGTH).ifEmpty { listOf("") }
        chunks.forEachIndexed { index, chunk ->
            Log.d(TAG, "$label [${index + 1}/${chunks.size}]: $chunk")
        }
    }

    private fun createChatCompletionsBody(
        model: OnlineRecognitionModel,
        imageDataUrl: String?,
        sourceText: String?,
        imageHint: String?,
    ): JSONObject {
        val userContent = if (imageDataUrl != null) {
            JSONArray()
                .put(
                    JSONObject()
                        .put("type", "image_url")
                        .put("image_url", JSONObject().put("url", imageDataUrl))
                )
                .put(JSONObject().put("type", "text").put("text", imageRequestPrompt(imageHint)))
        } else {
            TEXT_REQUEST_PROMPT + sourceText.orEmpty()
        }
        return JSONObject()
            .put("model", model.id)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userContent))
            )
            .put("stream", false)
            .apply {
                when {
                    model.id.startsWith("mimo-") -> put("max_completion_tokens", 1200)
                    else -> put("max_tokens", 1200)
                }
                if (model.reasoningControl == ReasoningControl.THINKING_DISABLED) {
                    put("thinking", JSONObject().put("type", "disabled"))
                }
            }
    }

    private fun createResponsesBody(
        model: OnlineRecognitionModel,
        imageDataUrl: String?,
        sourceText: String?,
        imageHint: String?,
        provider: OnlineRecognitionProvider,
    ): JSONObject {
        val content = JSONArray()
        if (imageDataUrl != null) {
            content.put(
                JSONObject()
                    .put("type", "input_image")
                    .put("image_url", imageDataUrl)
                    .apply {
                        if (provider == OnlineRecognitionProvider.OPENAI) put("detail", "high")
                    }
            )
            content.put(
                JSONObject()
                    .put("type", "input_text")
                    .put("text", imageRequestPrompt(imageHint))
            )
        } else {
            content.put(
                JSONObject()
                    .put("type", "input_text")
                    .put("text", TEXT_REQUEST_PROMPT + sourceText.orEmpty())
            )
        }

        return JSONObject()
            .put("model", model.id)
            .put("instructions", systemPrompt)
            .put(
                "input",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", content)
                )
            )
            .put("stream", false)
            .apply {
                if (provider != OnlineRecognitionProvider.CUSTOM) {
                    put("max_output_tokens", 1200)
                    put("store", false)
                }
                when (model.reasoningControl) {
                    ReasoningControl.OPENAI_EFFORT_NONE,
                    ReasoningControl.MINIMAX_EFFORT_NONE -> {
                        put("reasoning", JSONObject().put("effort", "none"))
                    }
                    else -> Unit
                }
            }
    }

    private fun endpoint(
        provider: OnlineRecognitionProvider,
        mimoBillingMode: MimoBillingMode,
        responsesApi: Boolean,
    ): String = when (provider) {
        OnlineRecognitionProvider.MIMO -> when (mimoBillingMode) {
            MimoBillingMode.PAY_AS_YOU_GO -> "https://api.xiaomimimo.com/v1/chat/completions"
            MimoBillingMode.TOKEN_PLAN -> "https://token-plan-cn.xiaomimimo.com/v1/chat/completions"
        }
        OnlineRecognitionProvider.ZHIPU -> "https://open.bigmodel.cn/api/paas/v4/chat/completions"
        OnlineRecognitionProvider.OPENAI -> "https://api.openai.com/v1/responses"
        OnlineRecognitionProvider.MOONSHOT -> "https://api.moonshot.cn/v1/chat/completions"
        OnlineRecognitionProvider.MINIMAX -> if (responsesApi) {
            "https://api.minimaxi.com/v1/responses"
        } else {
            "https://api.minimaxi.com/v1/chat/completions"
        }
        OnlineRecognitionProvider.CUSTOM -> {
            val baseUrl = normalizeCustomBaseUrl(
                OnlineRecognitionPreferences.customBaseUrl(appContext)
            )
            val mode = OnlineRecognitionPreferences.customRequestMode(appContext)
            "$baseUrl/${mode.endpoint}"
        }
    }

    private fun normalizeCustomBaseUrl(value: String): String {
        val normalized = value.trim().trimEnd('/')
        if (!normalized.startsWith("https://", ignoreCase = true) &&
            !normalized.startsWith("http://", ignoreCase = true)
        ) {
            throw OnlineRecognitionException("API 请求地址必须以 http:// 或 https:// 开头")
        }
        if (normalized.endsWith("/responses", ignoreCase = true) ||
            normalized.endsWith("/chat/completions", ignoreCase = true)
        ) {
            throw OnlineRecognitionException(
                "API 请求地址请填写到 /responses 或 /chat/completions 之前"
            )
        }
        return normalized
    }

    private fun extractChatContent(responseText: String): String {
        val root = runCatching { JSONObject(responseText) }
            .getOrElse { throw OnlineRecognitionException("供应商返回了无法解析的数据") }
        return root.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.takeIf { it.isNotBlank() }
            ?: throw OnlineRecognitionException("供应商没有返回识别内容")
    }

    private fun extractResponsesContent(responseText: String): String {
        if (responseText.lineSequence().any { it.trimStart().startsWith("data:") }) {
            return extractResponsesSseContent(responseText)
        }
        val root = runCatching { JSONObject(responseText) }
            .getOrElse { throw OnlineRecognitionException("供应商返回了无法解析的数据") }
        return extractResponsesJsonContent(root)
            ?: throw OnlineRecognitionException("供应商没有返回识别内容")
    }

    private fun extractResponsesSseContent(responseText: String): String {
        val deltas = StringBuilder()
        var completedText: String? = null
        var completedResponse: JSONObject? = null

        responseText.lineSequence().forEach { rawLine ->
            val line = rawLine.trimStart()
            if (!line.startsWith("data:")) return@forEach
            val data = line.removePrefix("data:").trim()
            if (data.isEmpty() || data == "[DONE]") return@forEach
            val event = runCatching { JSONObject(data) }.getOrNull() ?: return@forEach
            when (event.optString("type")) {
                "response.output_text.delta" -> deltas.append(event.optString("delta"))
                "response.output_text.done" -> event.optString("text")
                    .takeIf { it.isNotBlank() }
                    ?.let { completedText = it }
                "response.completed" -> completedResponse = event.optJSONObject("response")
            }
        }

        completedText?.takeIf { it.isNotBlank() }?.let { return it }
        completedResponse?.let(::extractResponsesJsonContent)?.let { return it }
        return deltas.toString().takeIf { it.isNotBlank() }
            ?: throw OnlineRecognitionException("供应商的流式响应中没有识别内容")
    }

    private fun extractResponsesJsonContent(root: JSONObject): String? {
        root.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }

        val text = buildString {
            val output = root.optJSONArray("output") ?: return@buildString
            for (index in 0 until output.length()) {
                val content = output.optJSONObject(index)?.optJSONArray("content") ?: continue
                for (contentIndex in 0 until content.length()) {
                    content.optJSONObject(contentIndex)
                        ?.optString("text")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { append(it) }
                }
            }
        }
        return text.takeIf { it.isNotBlank() }
    }

    private fun parseRecognitionContent(content: String, originalText: String?): List<RecognitionResult> {
        val jsonText = content
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
            .let { cleaned ->
                val objectStart = cleaned.indexOf('{')
                val objectEnd = cleaned.lastIndexOf('}')
                if (objectStart >= 0 && objectEnd > objectStart) {
                    cleaned.substring(objectStart, objectEnd + 1)
                } else {
                    cleaned
                }
            }
        val root = runCatching { JSONObject(jsonText) }
            .getOrElse { throw OnlineRecognitionException("在线识别结果格式不正确") }
        val orders = root.optJSONArray("orders")
            ?: throw OnlineRecognitionException("在线识别结果缺少订单列表")
        val sourceText = root.optNullableString("source_text") ?: originalText.orEmpty()
        val seenCodes = mutableSetOf<String>()

        return buildList {
            for (index in 0 until orders.length()) {
                val item = orders.optJSONObject(index) ?: continue
                val code = item.optNullableString("code") ?: continue
                if (!isPlausibleCode(code) || !seenCodes.add(code)) continue
                add(
                    RecognitionResult(
                        code = code,
                        qr = null,
                        type = normalizeType(item.optNullableString("type").orEmpty()),
                        brand = item.optNullableString("brand"),
                        fullText = item.optNullableString("fullText", "full_text") ?: sourceText,
                        pickupLocation = item.optNullableString(
                            "pickupLocation",
                            "pickup_location",
                        ),
                    )
                )
            }
        }
    }

    private fun JSONObject.optNullableString(vararg names: String): String? {
        for (name in names) {
            if (!has(name) || isNull(name)) continue
            val value = optString(name).trim()
            if (value.isNotBlank() && !value.equals("null", ignoreCase = true)) return value
        }
        return null
    }

    private fun normalizeType(type: String): String = when {
        type.contains("快递") || type.contains("包裹") -> "快递"
        type.contains("饮") || type.contains("奶茶") || type.contains("咖啡") -> "饮品"
        else -> "餐食"
    }

    private fun isPlausibleCode(code: String): Boolean {
        if (code.length !in 2..32 || code.contains("http", ignoreCase = true)) return false
        return code.any { it.isLetterOrDigit() } && code.none { it == '\n' || it == '\r' }
    }

    private fun imageRequestPrompt(imageHint: String?): String = buildString {
        append(IMAGE_REQUEST_PROMPT)
        imageHint?.takeIf { it.isNotBlank() }?.let {
            append('\n')
            append(it)
        }
    }

    private fun buildBarcodeBrandHint(brand: String): String {
        val safeBrand = brand
            .filterNot { it == '\n' || it == '\r' || it == '\u0000' }
            .take(MAX_BARCODE_BRAND_HINT_LENGTH)
        return "设备端二维码规则已确认该图片包含「$safeBrand」二维码。" +
            "请仍以图片中明确可见的信息提取取餐码，并将品牌填写为「$safeBrand」；" +
            "不要把二维码原始数据直接当作取餐码。"
    }

    private fun Bitmap.toImageDataUrl(): String {
        val scale = (MAX_LONG_EDGE.toFloat() / maxOf(width, height)).coerceAtMost(1f)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                this,
                (width * scale).toInt().coerceAtLeast(1),
                (height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            this
        }
        val jpeg = ByteArrayOutputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            output.toByteArray()
        }
        if (scaled !== this) scaled.recycle()
        return "data:image/jpeg;base64,${Base64.encodeToString(jpeg, Base64.NO_WRAP)}"
    }

    private fun httpErrorMessage(code: Int, provider: OnlineRecognitionProvider): String = when (code) {
        401, 403 -> "${provider.displayName} API 密钥无效或无权访问该模型"
        402 -> "${provider.displayName} 账户余额不足"
        404 -> "${provider.displayName} 当前无法使用所选模型"
        408 -> "${provider.displayName} 请求超时"
        429 -> "${provider.displayName} 请求过于频繁或额度已用完"
        in 500..599 -> "${provider.displayName} 服务暂时不可用"
        else -> "${provider.displayName} 请求失败（HTTP $code）"
    }

    companion object {
        private const val TAG = "OnlineRecognition"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_LOG_TEXT_LENGTH = 2_000
        private const val MAX_LOG_CHUNK_LENGTH = 3_000
        private const val MAX_LONG_EDGE = 2048
        private const val JPEG_QUALITY = 85
        private const val MAX_BARCODE_BRAND_HINT_LENGTH = 40
        private val HTTP_CLIENT = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

        private const val IMAGE_REQUEST_PROMPT =
            "识别图片中的全部有效取餐码或快递取件码，严格按照指定 JSON 返回。"
        private const val TEXT_REQUEST_PROMPT =
            "识别以下文本中的全部有效取餐码或快递取件码，严格按照指定 JSON 返回。文本：\n"
    }
}

class OnlineRecognitionException(
    message: String,
    val diagnosticDetail: String? = null,
    val httpStatus: Int? = null,
) : Exception(message)
