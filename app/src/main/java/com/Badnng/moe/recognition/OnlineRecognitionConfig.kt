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
}

data class OnlineRecognitionModel(
    val id: String,
    val displayName: String,
    val reasoningControl: ReasoningControl,
)

object OnlineRecognitionCatalog {
    private val models = mapOf(
        OnlineRecognitionProvider.MIMO to listOf(
            OnlineRecognitionModel("mimo-v2.5", "MiMo-V2.5", ReasoningControl.THINKING_DISABLED),
        ),
        OnlineRecognitionProvider.ZHIPU to listOf(
            OnlineRecognitionModel("glm-4.6v", "GLM-4.6V", ReasoningControl.THINKING_DISABLED),
            OnlineRecognitionModel("glm-4.6v-flashx", "GLM-4.6V-FlashX", ReasoningControl.THINKING_DISABLED),
            OnlineRecognitionModel("glm-4.6v-flash", "GLM-4.6V-Flash", ReasoningControl.THINKING_DISABLED),
            OnlineRecognitionModel("glm-4v-flash", "GLM-4V-Flash", ReasoningControl.NONE),
        ),
        OnlineRecognitionProvider.OPENAI to listOf(
            OnlineRecognitionModel("gpt-5.6-sol", "GPT-5.6 Sol", ReasoningControl.OPENAI_EFFORT_NONE),
            OnlineRecognitionModel("gpt-5.6-terra", "GPT-5.6 Terra", ReasoningControl.OPENAI_EFFORT_NONE),
            OnlineRecognitionModel("gpt-5.6-luna", "GPT-5.6 Luna", ReasoningControl.OPENAI_EFFORT_NONE),
            OnlineRecognitionModel("gpt-5.5", "GPT-5.5", ReasoningControl.OPENAI_EFFORT_NONE),
            OnlineRecognitionModel("gpt-5.4", "GPT-5.4", ReasoningControl.OPENAI_EFFORT_NONE),
            OnlineRecognitionModel("gpt-5.4-mini", "GPT-5.4 mini", ReasoningControl.OPENAI_EFFORT_NONE),
            OnlineRecognitionModel("gpt-5.4-nano", "GPT-5.4 nano", ReasoningControl.OPENAI_EFFORT_NONE),
            OnlineRecognitionModel("gpt-5.2", "GPT-5.2", ReasoningControl.OPENAI_EFFORT_NONE),
            OnlineRecognitionModel("gpt-5.1", "GPT-5.1", ReasoningControl.OPENAI_EFFORT_NONE),
            OnlineRecognitionModel("gpt-5", "GPT-5", ReasoningControl.OPENAI_EFFORT_NONE),
            OnlineRecognitionModel("gpt-5-mini", "GPT-5 mini", ReasoningControl.OPENAI_EFFORT_NONE),
            OnlineRecognitionModel("gpt-5-nano", "GPT-5 nano", ReasoningControl.OPENAI_EFFORT_NONE),
            OnlineRecognitionModel("gpt-4.1", "GPT-4.1", ReasoningControl.NONE),
            OnlineRecognitionModel("gpt-4.1-mini", "GPT-4.1 mini", ReasoningControl.NONE),
            OnlineRecognitionModel("gpt-4.1-nano", "GPT-4.1 nano", ReasoningControl.NONE),
            OnlineRecognitionModel("gpt-4o", "GPT-4o", ReasoningControl.NONE),
            OnlineRecognitionModel("gpt-4o-mini", "GPT-4o mini", ReasoningControl.NONE),
        ),
        OnlineRecognitionProvider.MOONSHOT to listOf(
            OnlineRecognitionModel("kimi-k2.6", "Kimi K2.6", ReasoningControl.THINKING_DISABLED),
            OnlineRecognitionModel("kimi-k2.5", "Kimi K2.5", ReasoningControl.THINKING_DISABLED),
            OnlineRecognitionModel("moonshot-v1-8k-vision-preview", "Moonshot V1 8K Vision Preview", ReasoningControl.NONE),
            OnlineRecognitionModel("moonshot-v1-32k-vision-preview", "Moonshot V1 32K Vision Preview", ReasoningControl.NONE),
            OnlineRecognitionModel("moonshot-v1-128k-vision-preview", "Moonshot V1 128K Vision Preview", ReasoningControl.NONE),
        ),
        OnlineRecognitionProvider.MINIMAX to listOf(
            OnlineRecognitionModel("MiniMax-M3", "MiniMax M3", ReasoningControl.MINIMAX_EFFORT_NONE),
        ),
        OnlineRecognitionProvider.CUSTOM to listOf(
            OnlineRecognitionModel("", "等待获取模型", ReasoningControl.NONE),
        ),
    )

    fun modelsFor(provider: OnlineRecognitionProvider): List<OnlineRecognitionModel> =
        models.getValue(provider)

    fun defaultModel(provider: OnlineRecognitionProvider): OnlineRecognitionModel =
        modelsFor(provider).first()

    fun resolveModel(provider: OnlineRecognitionProvider, modelId: String?): OnlineRecognitionModel =
        modelsFor(provider).firstOrNull { it.id == modelId } ?: defaultModel(provider)
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

    fun isOnline(context: Context): Boolean {
        val preferences = settings(context)
        return PrivacyConsent.isAccepted(preferences) &&
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
        val savedModelId = settings(context).getString(modelKey(provider), null)
        if (provider == OnlineRecognitionProvider.CUSTOM && !savedModelId.isNullOrBlank()) {
            return OnlineRecognitionModel(savedModelId, savedModelId, ReasoningControl.NONE)
        }
        return OnlineRecognitionCatalog.resolveModel(provider, savedModelId)
    }

    fun saveModel(context: Context, provider: OnlineRecognitionProvider, modelId: String) {
        settings(context).edit().putString(modelKey(provider), modelId).apply()
    }

    private fun settings(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
}
