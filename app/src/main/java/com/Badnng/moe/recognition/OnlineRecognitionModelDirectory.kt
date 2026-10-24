package com.Badnng.moe.recognition

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

/** 模型发现、缓存使用同一套纯数据规则；未知能力不等于不支持。 */
internal object OnlineRecognitionModelDirectory {
    fun modelsUrl(
        provider: OnlineRecognitionProvider,
        mimoBillingMode: MimoBillingMode,
        customBaseUrl: String,
    ): String = when (provider) {
        OnlineRecognitionProvider.MIMO -> when (mimoBillingMode) {
            MimoBillingMode.PAY_AS_YOU_GO -> "https://api.xiaomimimo.com/v1/models"
            MimoBillingMode.TOKEN_PLAN -> "https://token-plan-cn.xiaomimimo.com/v1/models"
        }
        OnlineRecognitionProvider.ZHIPU -> "https://open.bigmodel.cn/api/paas/v4/models"
        OnlineRecognitionProvider.OPENAI -> "https://api.openai.com/v1/models"
        OnlineRecognitionProvider.MOONSHOT -> "https://api.moonshot.cn/v1/models"
        OnlineRecognitionProvider.MINIMAX -> "https://api.minimax.cn/v1/models"
        OnlineRecognitionProvider.OPENCODE_GO -> "https://opencode.ai/inference/go/v1/models"
        OnlineRecognitionProvider.DEEPSEEK -> "https://api.deepseek.com/models"
        OnlineRecognitionProvider.CUSTOM -> "${normalizeCustomBaseUrl(customBaseUrl)}/models"
    }

    fun normalizeCustomBaseUrl(value: String): String {
        val url = value.trim().toHttpUrlOrNull()
            ?: throw OnlineRecognitionException("API 请求地址必须是有效的 http:// 或 https:// 地址")
        if (url.query != null || url.fragment != null || url.username.isNotEmpty() || url.password.isNotEmpty()) {
            throw OnlineRecognitionException("API 请求地址请勿包含查询参数、片段或登录信息")
        }
        val normalized = url.toString().trimEnd('/')
        if (normalized.endsWith("/responses", ignoreCase = true) ||
            normalized.endsWith("/chat/completions", ignoreCase = true)
        ) {
            throw OnlineRecognitionException("API 请求地址请填写到 /responses 或 /chat/completions 之前")
        }
        return normalized
    }

    fun cacheIdentity(
        provider: OnlineRecognitionProvider,
        mimoBillingMode: MimoBillingMode,
        customBaseUrl: String,
        apiKey: String,
    ): String? {
        val endpoint = runCatching { modelsUrl(provider, mimoBillingMode, customBaseUrl) }.getOrNull()
            ?: return null
        // Key 与地址仅参与哈希；缓存键和值都不保存明文密钥或自定义地址。
        return "v2_${sha256("${provider.key}\n$endpoint\n${sha256(apiKey.trim())}")}"
    }

    fun parse(
        provider: OnlineRecognitionProvider,
        responseText: String,
        metadata: ModelsDevMetadata? = null,
        mimoBillingMode: MimoBillingMode = MimoBillingMode.PAY_AS_YOU_GO,
        customBaseUrl: String = "",
    ): List<OnlineRecognitionModel> {
        val root = runCatching { JSONObject(responseText) }
            .getOrElse { throw OnlineRecognitionException("模型列表格式不正确") }
        val data = root.optJSONArray("data") ?: root.optJSONArray("models")
            ?: root.optJSONObject("data")?.optJSONArray("models")
            ?: throw OnlineRecognitionException("供应商没有返回模型列表")
        val models = buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val id = item.nonBlankString("id") ?: continue
                val matched = metadata?.find(provider, id, mimoBillingMode, customBaseUrl)
                val modalities = item.optJSONObject("modalities")
                val architecture = item.optJSONObject("architecture")
                val capabilities = item.optJSONObject("capabilities")
                val inputs = item.optJSONArray("input_modalities")
                    ?: modalities?.optJSONArray("input")
                    ?: architecture?.optJSONArray("input_modalities")
                val outputs = item.optJSONArray("output_modalities")
                    ?: modalities?.optJSONArray("output")
                    ?: architecture?.optJSONArray("output_modalities")
                val providerImages = item.optionalBoolean(
                    "supports_image_in", "supports_image_input", "supports_images", "supports_vision", "vision",
                )
                    ?: capabilities?.optionalBoolean("vision", "image", "images")
                    ?: inputs?.let { "image" in it.strings() }
                val providerText = item.optionalBoolean("supports_text_out", "supports_text_output")
                    ?: outputs?.let { "text" in it.strings() }
                val supportsImages = providerImages ?: matched?.supportsImages
                val supportsTextOutput = providerText ?: matched?.supportsTextOutput
                if (supportsImages != true || supportsTextOutput == false || explicitlyNonConversational(item)) continue

                val protocol = protocol(item)
                val kind = if (provider == OnlineRecognitionProvider.CUSTOM) null else protocol.kind ?: matched?.apiKind
                val unsupportedProtocol = provider == OnlineRecognitionProvider.OPENCODE_GO && when {
                    protocol.kind != null -> false
                    protocol.unsupported -> true
                    else -> matched?.unsupportedProtocol == true
                }
                if (unsupportedProtocol || (provider == OnlineRecognitionProvider.OPENCODE_GO && kind == null)) continue
                val reasoning = when {
                    provider == OnlineRecognitionProvider.CUSTOM -> ReasoningControl.NONE
                    item.has("reasoning_options") -> metadataReasoningControl(item, kind)
                    else -> matched?.reasoningControl ?: ReasoningControl.NONE
                }
                add(OnlineRecognitionModel(
                    id = id,
                    displayName = item.nonBlankString("name") ?: item.nonBlankString("display_name")
                        ?: matched?.displayName ?: id,
                    reasoningControl = reasoning,
                    apiKind = kind,
                    supportsImages = supportsImages,
                    supportsTextOutput = supportsTextOutput,
                    unsupportedProtocol = unsupportedProtocol,
                ))
            }
        }.distinctBy { it.id }.sortedBy { it.displayName.lowercase(Locale.ROOT) }
        if (models.isEmpty()) throw OnlineRecognitionException("未找到已确认支持图片识别且请求协议可用的模型")
        return models
    }

    /** Go 必须依赖公开元数据明确选择协议，不根据型号或家族猜测。 */
    fun openCodeApiKind(model: OnlineRecognitionModel): OnlineRecognitionApiKind {
        if (model.unsupportedProtocol) {
            throw OnlineRecognitionException("此 OpenCode Go 模型使用当前尚未支持的协议，请改用兼容接口")
        }
        return model.apiKind ?: throw OnlineRecognitionException(
            "尚未确认此 OpenCode Go 模型的请求协议，请等待模型元数据更新或改用提供兼容接口的供应商",
        )
    }

    fun validateForRequest(model: OnlineRecognitionModel, hasImage: Boolean) {
        if (model.id.isBlank()) {
            throw OnlineRecognitionException("尚未选择在线识别模型，请等待模型列表获取完成后选择")
        }
        if (model.unsupportedProtocol) {
            throw OnlineRecognitionException("当前尚未支持此模型的请求协议，请改用提供兼容接口的供应商")
        }
        if (model.supportsTextOutput == false) {
            throw OnlineRecognitionException("所选模型不支持文本输出，无法返回识别结果")
        }
        if (hasImage && model.supportsImages == false) {
            throw OnlineRecognitionException("所选模型不支持图片输入，请选择支持图片识别的模型")
        }
    }

    fun encodeCache(models: List<OnlineRecognitionModel>): String = JSONArray().apply {
        models.forEach { model ->
            put(JSONObject().apply {
                put("id", model.id)
                put("name", model.displayName)
                put("reasoningControl", model.reasoningControl.name)
                model.apiKind?.let { put("apiKind", it.name) }
                model.supportsImages?.let { put("supportsImages", it) }
                model.supportsTextOutput?.let { put("supportsTextOutput", it) }
                put("unsupportedProtocol", model.unsupportedProtocol)
            })
        }
    }.toString()

    fun decodeCache(value: String): List<OnlineRecognitionModel> {
        val data = JSONArray(value)
        return buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val id = item.nonBlankString("id") ?: continue
                add(OnlineRecognitionModel(
                    id = id,
                    displayName = item.nonBlankString("name") ?: id,
                    reasoningControl = enumValueOrNull<ReasoningControl>(item.optString("reasoningControl"))
                        ?: ReasoningControl.NONE,
                    apiKind = enumValueOrNull<OnlineRecognitionApiKind>(item.optString("apiKind")),
                    supportsImages = item.optionalBoolean("supportsImages"),
                    supportsTextOutput = item.optionalBoolean("supportsTextOutput"),
                    unsupportedProtocol = item.optionalBoolean("unsupportedProtocol") == true,
                ))
            }
        }.distinctBy { it.id }
    }

    private data class Protocol(val kind: OnlineRecognitionApiKind?, val unsupported: Boolean = false)

    private fun protocol(item: JSONObject): Protocol {
        val provider = item.optJSONObject("provider")
        val descriptions = listOfNotNull(
            item.nonBlankString("api_type"), item.nonBlankString("api_kind"),
            item.nonBlankString("protocol"), item.nonBlankString("endpoint"),
            item.nonBlankString("shape"), provider?.nonBlankString("shape"),
            provider?.nonBlankString("npm"), provider?.nonBlankString("api"),
        )
        var unsupported = false
        for (description in descriptions) {
            val value = description.lowercase(Locale.ROOT)
            when {
                "responses" in value || value == "@ai-sdk/openai" -> return Protocol(OnlineRecognitionApiKind.RESPONSES)
                "anthropic" in value || value == "messages" -> return Protocol(OnlineRecognitionApiKind.ANTHROPIC)
                "chat/completions" in value || value == "chat_completions" ||
                    value == "chat" || value == "completions" ||
                    value == "@ai-sdk/openai-compatible" -> return Protocol(OnlineRecognitionApiKind.CHAT)
                "google" in value || "generative" in value || "generatecontent" in value -> unsupported = true
            }
        }
        val endpoints = item.optJSONArray("supported_endpoints") ?: item.optJSONArray("supported_endpoint_types")
        if (endpoints != null) {
            for (endpoint in endpoints.strings()) {
                val kind = protocol(JSONObject().put("endpoint", endpoint))
                if (kind.kind != null) return kind
            }
            if (endpoints.strings().any { "generatecontent" in it || "google" in it }) unsupported = true
        }
        return Protocol(null, unsupported)
    }

    private fun explicitlyNonConversational(item: JSONObject): Boolean {
        val task = (item.nonBlankString("task") ?: item.nonBlankString("type"))
            ?.lowercase(Locale.ROOT) ?: return false
        return task in setOf("embedding", "embeddings", "rerank", "reranking", "tts", "asr",
            "image-generation", "image_generation", "text-to-image", "text-to-speech", "speech-to-text")
    }

    private fun metadataReasoningControl(item: JSONObject, kind: OnlineRecognitionApiKind?): ReasoningControl {
        val options = item.optJSONArray("reasoning_options") ?: return ReasoningControl.NONE
        for (index in 0 until options.length()) {
            val option = options.optJSONObject(index) ?: continue
            if (option.optString("type") == "effort" &&
                option.optJSONArray("values")?.strings()?.contains("none") == true &&
                kind == OnlineRecognitionApiKind.RESPONSES
            ) return ReasoningControl.OPENAI_EFFORT_NONE
        }
        return ReasoningControl.NONE
    }

    private fun JSONObject.nonBlankString(key: String): String? =
        (opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun JSONObject.optionalBoolean(vararg names: String): Boolean? =
        names.firstNotNullOfOrNull { opt(it) as? Boolean }

    private fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull {
        (opt(it) as? String)?.lowercase(Locale.ROOT)
    }

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
        enumValues<T>().firstOrNull { it.name == value }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
