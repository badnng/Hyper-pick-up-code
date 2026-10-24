package com.Badnng.moe.recognition

import android.content.Context
import com.Badnng.moe.R
import com.Badnng.moe.privacy.PrivacyConsent

enum class OnlineRecognitionProvider(
    val key: String,
    val displayName: String,
    val iconRes: Int,
) {
    MIMO("mimo", "小米 MiMo", R.drawable.provider_mimo),
    ZHIPU("zhipu", "智谱开放平台", R.drawable.provider_zhipu_hd),
    OPENAI("openai", "OpenAI", R.drawable.provider_openai),
    MOONSHOT("moonshot", "月之暗面", R.drawable.provider_kimi_hd),
    MINIMAX("minimax", "MiniMax", R.drawable.provider_minimax_hd),
    OPENCODE_GO("opencode_go", "OpenCode Go", R.drawable.provider_opencode),
    DEEPSEEK("deepseek", "DeepSeek", R.drawable.provider_deepseek),
    CUSTOM("custom", "自定义", R.drawable.provider_custom);

    companion object {
        fun fromKey(key: String?): OnlineRecognitionProvider =
            entries.firstOrNull { it.key == key } ?: MIMO
    }
}

enum class CustomRequestMode(val key: String, val displayName: String, val endpoint: String) {
    RESPONSES("responses", "Responses API", "responses"),
    CHAT_COMPLETIONS("chat_completions", "Chat Completions", "chat/completions");

    companion object {
        fun fromKey(key: String?): CustomRequestMode =
            entries.firstOrNull { it.key == key } ?: RESPONSES
    }
}

enum class MimoBillingMode(val key: String, val displayName: String) {
    PAY_AS_YOU_GO("pay_as_you_go", "按量计费"),
    TOKEN_PLAN("token_plan", "Token Plan");

    companion object {
        fun fromKey(key: String?): MimoBillingMode =
            entries.firstOrNull { it.key == key } ?: PAY_AS_YOU_GO
    }
}

enum class ReasoningControl {
    NONE,
    THINKING_DISABLED,
    OPENAI_EFFORT_NONE,
    MINIMAX_EFFORT_NONE,
    OPENCODE_COMBINED,
    ANTHROPIC_DISABLED,
}

enum class OnlineRecognitionApiKind {
    RESPONSES,
    CHAT,
    ANTHROPIC,
}

data class OnlineRecognitionModel(
    val id: String,
    val displayName: String,
    val reasoningControl: ReasoningControl,
    val apiKind: OnlineRecognitionApiKind? = null,
    val supportsImages: Boolean? = null,
    val supportsTextOutput: Boolean? = null,
    val unsupportedProtocol: Boolean = false,
)

object OnlineRecognitionCatalog {
    fun resolveModel(
        @Suppress("UNUSED_PARAMETER") provider: OnlineRecognitionProvider,
        modelId: String?,
    ): OnlineRecognitionModel {
        val id = modelId?.trim().orEmpty()
        return OnlineRecognitionModel(id, id, ReasoningControl.NONE)
    }
}

object OnlineRecognitionPreferences {
    const val PROMPT_ASSET_NAME = "online_recognition_prompt.txt"
    const val MODE_KEY = "recognition_mode"
    const val MODE_OFFLINE = "offline"
    const val MODE_ONLINE = "online"
    const val PROVIDER_KEY = "online_recognition_provider"
    const val MIMO_BILLING_KEY = "mimo_billing_mode"
    const val CUSTOM_REQUEST_MODE_KEY = "custom_recognition_request_mode"
    const val CUSTOM_BASE_URL_KEY = "custom_recognition_base_url"
    const val CUSTOM_PROMPT_KEY = "online_recognition_custom_prompt"
    private fun modelKey(provider: OnlineRecognitionProvider) =
        "online_recognition_model_${provider.key}"

    fun savedMode(context: Context): String? = settings(context).getString(MODE_KEY, null)

    fun isOnline(context: Context): Boolean {
        val preferences = settings(context)
        return PrivacyConsent.isCurrentPolicyAccepted(preferences) &&
            preferences.getString(MODE_KEY, MODE_OFFLINE) == MODE_ONLINE
    }

    fun provider(context: Context): OnlineRecognitionProvider = OnlineRecognitionProvider.fromKey(
        settings(context).getString(PROVIDER_KEY, OnlineRecognitionProvider.MIMO.key)
    )

    fun mimoBillingMode(context: Context): MimoBillingMode = MimoBillingMode.fromKey(
        settings(context).getString(MIMO_BILLING_KEY, MimoBillingMode.PAY_AS_YOU_GO.key)
    )

    fun customRequestMode(context: Context): CustomRequestMode = CustomRequestMode.fromKey(
        settings(context).getString(CUSTOM_REQUEST_MODE_KEY, CustomRequestMode.RESPONSES.key)
    )

    fun customBaseUrl(context: Context): String =
        settings(context).getString(CUSTOM_BASE_URL_KEY, "").orEmpty().trim()

    fun defaultPrompt(context: Context): String =
        context.applicationContext.assets.open(PROMPT_ASSET_NAME)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
            .trim()

    fun customPrompt(context: Context): String? =
        settings(context).getString(CUSTOM_PROMPT_KEY, null)?.takeIf { it.isNotBlank() }

    fun effectivePrompt(context: Context, builtInPrompt: String = defaultPrompt(context)): String =
        RecognitionPromptPolicy.resolve(builtInPrompt, customPrompt(context))

    fun saveCustomPrompt(context: Context, prompt: String, defaultPrompt: String) {
        settings(context).edit().apply {
            if (RecognitionPromptPolicy.shouldPersist(prompt, defaultPrompt)) {
                putString(CUSTOM_PROMPT_KEY, prompt)
            } else {
                remove(CUSTOM_PROMPT_KEY)
            }
        }.apply()
    }

    fun clearCustomPrompt(context: Context) {
        settings(context).edit().remove(CUSTOM_PROMPT_KEY).apply()
    }

    fun model(context: Context, provider: OnlineRecognitionProvider): OnlineRecognitionModel {
        val savedModelId = settings(context).getString(modelKey(provider), null)?.trim()
        return enrichModel(context, provider, OnlineRecognitionCatalog.resolveModel(provider, savedModelId))
    }

    fun enrichModel(
        context: Context,
        provider: OnlineRecognitionProvider,
        selected: OnlineRecognitionModel,
        allowMetadataDiskRead: Boolean = false,
        mimoBillingMode: MimoBillingMode = mimoBillingMode(context),
        customBaseUrl: String = customBaseUrl(context),
    ): OnlineRecognitionModel {
        if (selected.id.isBlank()) return selected
        cachedModels(context, provider, mimoBillingMode, customBaseUrl)
            .firstOrNull { it.id == selected.id }?.let { return it }
        // UI 仅使用内存索引；后台识别可读本地磁盘，两个入口均不会发起网络请求。
        val repository = ModelsDevMetadataRepository(context)
        val index = if (allowMetadataDiskRead) repository.cached() else repository.memoryCached()
        val metadata = index?.find(provider, selected.id, mimoBillingMode, customBaseUrl) ?: return selected
        return selected.copy(
            displayName = if (selected.displayName.isBlank() || selected.displayName == selected.id) {
                metadata.displayName ?: selected.displayName
            } else selected.displayName,
            reasoningControl = when {
                provider == OnlineRecognitionProvider.CUSTOM -> ReasoningControl.NONE
                selected.reasoningControl != ReasoningControl.NONE -> selected.reasoningControl
                else -> metadata.reasoningControl
            },
            apiKind = if (provider == OnlineRecognitionProvider.CUSTOM) null else selected.apiKind ?: metadata.apiKind,
            supportsImages = selected.supportsImages ?: metadata.supportsImages,
            supportsTextOutput = selected.supportsTextOutput ?: metadata.supportsTextOutput,
            unsupportedProtocol = provider == OnlineRecognitionProvider.OPENCODE_GO &&
                (selected.unsupportedProtocol || (selected.apiKind == null && metadata.unsupportedProtocol)),
        )
    }

    fun cachedModels(
        context: Context,
        provider: OnlineRecognitionProvider,
        mimoBillingMode: MimoBillingMode = mimoBillingMode(context),
        customBaseUrl: String = customBaseUrl(context),
    ): List<OnlineRecognitionModel> {
        val identity = modelCacheIdentity(context, provider, mimoBillingMode, customBaseUrl)
            ?: return emptyList()
        val cached = modelCache(context).getString(identity, null) ?: return emptyList()
        return runCatching { OnlineRecognitionModelDirectory.decodeCache(cached) }
            .getOrDefault(emptyList())
    }

    fun saveCachedModels(
        context: Context,
        provider: OnlineRecognitionProvider,
        models: List<OnlineRecognitionModel>,
        mimoBillingMode: MimoBillingMode = mimoBillingMode(context),
        customBaseUrl: String = customBaseUrl(context),
    ) {
        val identity = modelCacheIdentity(context, provider, mimoBillingMode, customBaseUrl) ?: return
        saveCachedModelsForIdentity(context, identity, models)
    }

    internal fun modelCacheIdentity(
        context: Context,
        provider: OnlineRecognitionProvider,
        mimoBillingMode: MimoBillingMode,
        customBaseUrl: String,
        apiKey: String? = SecureApiKeyStore(context).get(provider),
    ): String? = apiKey?.takeIf { it.isNotBlank() }?.let {
        OnlineRecognitionModelDirectory.cacheIdentity(provider, mimoBillingMode, customBaseUrl, it)
    }

    internal fun saveCachedModelsForIdentity(
        context: Context,
        identity: String,
        models: List<OnlineRecognitionModel>,
    ) {
        // 失败/空结果不会破坏上一次成功目录，当前已选模型也不在这里更改。
        if (models.isEmpty()) return
        modelCache(context).edit()
            .putString(identity, OnlineRecognitionModelDirectory.encodeCache(models))
            .apply()
    }

    private fun modelCache(context: Context) = context.applicationContext.getSharedPreferences(
        "online_recognition_model_cache",
        Context.MODE_PRIVATE,
    )

    fun saveModel(context: Context, provider: OnlineRecognitionProvider, modelId: String) {
        settings(context).edit().putString(modelKey(provider), modelId).apply()
    }

    private fun settings(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).also { preferences ->
            if (preferences.getString(PROVIDER_KEY, null) == "opencode_zen") {
                // 已移除的供应商回退到离线模式，避免升级后自动改用另一家在线服务。
                preferences.edit()
                    .putString(MODE_KEY, MODE_OFFLINE)
                    .remove(PROVIDER_KEY)
                    .remove("online_recognition_model_opencode_zen")
                    .apply()
            }
        }
}
