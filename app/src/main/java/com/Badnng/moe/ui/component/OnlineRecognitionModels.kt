package com.Badnng.moe.ui.component

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.Badnng.moe.recognition.MimoBillingMode
import com.Badnng.moe.recognition.OnlineRecognitionClient
import com.Badnng.moe.recognition.OnlineRecognitionModel
import com.Badnng.moe.recognition.OnlineRecognitionPreferences
import com.Badnng.moe.recognition.OnlineRecognitionProvider
import com.Badnng.moe.recognition.SecureApiKeyStore
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

/** 为现有模型下拉框提供后台目录状态，不渲染界面或更改当前选择。 */
@Stable
class OnlineRecognitionModelsState internal constructor(initialModels: List<OnlineRecognitionModel>) {
    var models by mutableStateOf(initialModels.filter(::isImageRecognitionModel))
        internal set
    var loading by mutableStateOf(false)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set

    internal var attempts = 0
    internal var retryNotBeforeAt = 0L
    internal var lastSuccessAt: Long? = null
    internal var refreshRequired = true
}

private object OnlineRecognitionModelsMemory {
    const val REFRESH_INTERVAL_MS = 5 * 60 * 1000L
    private val states = linkedMapOf<String, OnlineRecognitionModelsState>()
    private var activeIdentity: String? = null

    fun state(identity: String, initialModels: List<OnlineRecognitionModel>): OnlineRecognitionModelsState =
        states.getOrPut(identity) {
            OnlineRecognitionModelsState(initialModels)
        }.also {
            // 内存中仅保存哈希身份和目录，限制历史连接数量。
            if (states.size > 32) states.remove(states.keys.first())
        }

    fun activate(identity: String): Boolean {
        val changed = activeIdentity != identity
        activeIdentity = identity
        return changed
    }
}

@Composable
fun rememberOnlineRecognitionModels(
    provider: OnlineRecognitionProvider,
    mimoBillingMode: MimoBillingMode,
    customBaseUrl: String,
    apiKey: String,
    enabled: Boolean = true,
): OnlineRecognitionModelsState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val client = remember(context) { OnlineRecognitionClient(context) }
    val keyStore = remember(context) { SecureApiKeyStore(context) }
    val baseUrl = customBaseUrl.trim()
    val key = apiKey.trim()
    val identity = remember(provider, mimoBillingMode, baseUrl, key) {
        modelsIdentity(provider, mimoBillingMode, baseUrl, key)
    }
    val state = remember(context, identity) {
        OnlineRecognitionModelsMemory.state(
            identity,
            OnlineRecognitionPreferences.cachedModels(context, provider, mimoBillingMode, baseUrl),
        )
    }
    val canFetch = enabled && key.isNotBlank() &&
        (provider != OnlineRecognitionProvider.CUSTOM || baseUrl.isNotBlank())
    val isCurrentConnection = {
        OnlineRecognitionPreferences.provider(context) == provider &&
            modelsIdentity(
                provider,
                OnlineRecognitionPreferences.mimoBillingMode(context),
                OnlineRecognitionPreferences.customBaseUrl(context),
                keyStore.get(provider).orEmpty(),
            ) == identity
    }

    LaunchedEffect(state, lifecycleOwner, enabled) {
        if (OnlineRecognitionModelsMemory.activate(identity)) {
            state.attempts = 0
            state.retryNotBeforeAt = 0L
            state.refreshRequired = true
        }
        if (!canFetch) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            delay(700)
            val retryDelays = longArrayOf(5_000L, 15_000L, 60_000L)
            while (isActive) {
                if (!isCurrentConnection()) return@repeatOnLifecycle
                val now = SystemClock.elapsedRealtime()
                val cacheExpiresAt = if (state.refreshRequired) 0L else state.lastSuccessAt?.let {
                    it + OnlineRecognitionModelsMemory.REFRESH_INTERVAL_MS
                } ?: 0L
                val nextAttemptAt = maxOf(state.retryNotBeforeAt, cacheExpiresAt)
                if (nextAttemptAt > now) {
                    delay(nextAttemptAt - now)
                    continue
                }
                // 四次失败后冷却五分钟，前后台切换沿用截止时间。
                if (state.attempts >= 4) state.attempts = 0
                state.loading = true
                state.error = null
                state.attempts++
                try {
                    val fetchedModels = client.fetchModels(provider, mimoBillingMode, baseUrl)
                    currentCoroutineContext().ensureActive()
                    if (!isCurrentConnection()) return@repeatOnLifecycle
                    state.models = fetchedModels.filter(::isImageRecognitionModel)
                    state.lastSuccessAt = SystemClock.elapsedRealtime()
                    state.attempts = 0
                    state.retryNotBeforeAt = 0L
                    state.refreshRequired = false
                } catch (cancelled: CancellationException) {
                    state.attempts = (state.attempts - 1).coerceAtLeast(0)
                    throw cancelled
                } catch (exception: Exception) {
                    state.error = exception.message?.takeIf { it.isNotBlank() } ?: "暂时无法获取模型目录"
                    val retryDelay = retryDelays.getOrNull(state.attempts - 1)
                        ?: OnlineRecognitionModelsMemory.REFRESH_INTERVAL_MS
                    state.retryNotBeforeAt = SystemClock.elapsedRealtime() + retryDelay
                } finally {
                    state.loading = false
                }
            }
        }
    }
    return state
}

private fun isImageRecognitionModel(model: OnlineRecognitionModel): Boolean =
    model.supportsImages == true && model.supportsTextOutput != false && !model.unsupportedProtocol

private fun modelsIdentity(
    provider: OnlineRecognitionProvider,
    mimoBillingMode: MimoBillingMode,
    baseUrl: String,
    apiKey: String,
): String = MessageDigest.getInstance("SHA-256")
    .digest("${provider.key}\n${mimoBillingMode.key}\n${baseUrl.trim().trimEnd('/')}\n${apiKey.trim()}".toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
