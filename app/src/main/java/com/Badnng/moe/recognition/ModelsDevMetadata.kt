package com.Badnng.moe.recognition

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** models.dev 只补充能力与协议，不决定供应商实际可调用的模型 ID。 */
internal class ModelsDevMetadata private constructor(private val records: List<Record>) {
    data class Entry(
        val displayName: String?,
        val supportsImages: Boolean?,
        val supportsTextOutput: Boolean?,
        val apiKind: OnlineRecognitionApiKind?,
        val reasoningControl: ReasoningControl,
        val unsupportedProtocol: Boolean = false,
    )

    private data class Record(
        val providerIds: Set<String>,
        val ids: Set<String>,
        val aliases: Set<String>,
        val endpoint: String?,
        val displayName: String?,
        val supportsImages: Boolean?,
        val supportsTextOutput: Boolean?,
        val apiKind: OnlineRecognitionApiKind?,
        val unsupportedProtocol: Boolean,
        val reasoningToggle: Boolean,
        val reasoningEffortNone: Boolean,
    ) {
        fun entry(provider: OnlineRecognitionProvider): Entry {
            val control = when {
                provider == OnlineRecognitionProvider.CUSTOM -> ReasoningControl.NONE
                provider == OnlineRecognitionProvider.MINIMAX -> if (
                    apiKind == OnlineRecognitionApiKind.RESPONSES && reasoningEffortNone
                ) ReasoningControl.MINIMAX_EFFORT_NONE else ReasoningControl.NONE
                apiKind == OnlineRecognitionApiKind.RESPONSES && reasoningEffortNone && provider in setOf(
                    OnlineRecognitionProvider.OPENAI, OnlineRecognitionProvider.OPENCODE_GO,
                ) ->
                    ReasoningControl.OPENAI_EFFORT_NONE
                provider == OnlineRecognitionProvider.OPENCODE_GO &&
                    apiKind == OnlineRecognitionApiKind.ANTHROPIC && reasoningToggle ->
                    ReasoningControl.ANTHROPIC_DISABLED
                apiKind == OnlineRecognitionApiKind.CHAT && reasoningToggle && provider in setOf(
                    OnlineRecognitionProvider.MIMO, OnlineRecognitionProvider.ZHIPU,
                    OnlineRecognitionProvider.MOONSHOT, OnlineRecognitionProvider.DEEPSEEK,
                ) -> ReasoningControl.THINKING_DISABLED
                else -> ReasoningControl.NONE
            }
            return Entry(
                displayName, supportsImages, supportsTextOutput, apiKind, control,
                unsupportedProtocol && provider == OnlineRecognitionProvider.OPENCODE_GO,
            )
        }
    }

    // 索引只持有不可变值，不保留 JSONObject 或调用方可修改的集合。
    private val exactIds = records.indexBy { it.ids }
    private val aliases = records.indexBy { it.aliases }
    private val providers = records.indexBy { it.providerIds }
    private val endpoints = records.filter { it.endpoint != null }.groupBy { it.endpoint!! }

    fun find(
        provider: OnlineRecognitionProvider,
        modelId: String,
        mimoBillingMode: MimoBillingMode = MimoBillingMode.PAY_AS_YOU_GO,
        customBaseUrl: String = "",
    ): Entry? {
        val id = modelId.trim().takeIf { it.isNotEmpty() } ?: return null
        val ownRecords = if (provider == OnlineRecognitionProvider.CUSTOM) {
            normalizedEndpoint(customBaseUrl)?.let { endpoints[it] }.orEmpty()
        } else {
            providers[providerId(provider, mimoBillingMode)].orEmpty()
        }
        val ownMatches = ownRecords.filter { id in it.ids }.ifEmpty {
            ownRecords.filter { id in it.aliases }
        }
        if (ownMatches.isNotEmpty()) return merge(
            ownMatches.map { it.entry(provider) },
            // 自定义请求协议由用户选择；公共数据只补名称与模态。
            trustedProtocol = provider != OnlineRecognitionProvider.CUSTOM,
        )

        // 未知网关只能借用一致的能力。别家的 npm、URL、推理控制不代表本网关支持。
        val matches = exactIds[id].orEmpty().ifEmpty { aliases[id].orEmpty() }
        if (matches.isEmpty()) return null
        return merge(matches.map { it.entry(provider) }, trustedProtocol = false)
    }

    companion object {
        fun parse(json: String): ModelsDevMetadata {
            val root = JSONObject(json)
            val records = buildList {
                for (providerKey in root.keys()) {
                    val provider = root.optJSONObject(providerKey) ?: continue
                    val models = provider.optJSONObject("models") ?: continue
                    val providerIds = setOfNotNull(providerKey.nonBlank(), provider.string("id"))
                    for (modelKey in models.keys()) {
                        val model = models.optJSONObject(modelKey) ?: continue
                        val ids = setOfNotNull(modelKey.nonBlank(), model.string("id"))
                        if (ids.isEmpty()) continue
                        val modelProvider = model.optJSONObject("provider")
                        // 覆盖按字段继承；仅覆盖 api 时仍使用供应商原来的 npm。
                        val npm = modelProvider?.string("npm") ?: provider.string("npm")
                        val api = modelProvider?.string("api") ?: provider.string("api")
                        val shape = modelProvider?.string("shape") ?: provider.string("shape")
                        val protocol = protocol(shape, npm)
                        val modalities = model.optJSONObject("modalities")
                        val canonical = model.string("canonical_model_id")
                        val aliases = buildSet {
                            addAll(model.optJSONArray("aliases").strings())
                            model.string("alias")?.let(::add)
                            canonical?.let {
                                add(it)
                                // canonical_model_id 是明确关联，不能猜测日期或最新版本。
                                if ('/' in it) add(it.substringAfter('/'))
                            }
                        }.minus(ids)
                        val options = model.optJSONArray("reasoning_options")
                        var toggle = false
                        var effortNone = false
                        if (options != null) for (index in 0 until options.length()) {
                            val option = options.optJSONObject(index) ?: continue
                            if (option.string("type") == "toggle") toggle = true
                            if (option.string("type") == "effort" &&
                                "none" in option.optJSONArray("values").strings()
                            ) effortNone = true
                        }
                        add(Record(
                            providerIds = providerIds,
                            ids = ids,
                            aliases = aliases,
                            endpoint = api?.let(::normalizedEndpoint),
                            displayName = model.string("name"),
                            // attachment 可以只是 PDF/文件，不能据此判为图片模型。
                            supportsImages = modalities?.optJSONArray("input").supports("image"),
                            supportsTextOutput = modalities?.optJSONArray("output").supports("text"),
                            apiKind = protocol.first,
                            unsupportedProtocol = protocol.second,
                            reasoningToggle = toggle,
                            reasoningEffortNone = effortNone,
                        ))
                    }
                }
            }
            require(records.isNotEmpty()) { "models.dev 未返回有效的模型元数据" }
            return ModelsDevMetadata(records)
        }

        private fun providerId(provider: OnlineRecognitionProvider, billing: MimoBillingMode): String =
            when (provider) {
                OnlineRecognitionProvider.MIMO -> if (billing == MimoBillingMode.TOKEN_PLAN) {
                    "xiaomi-token-plan-cn"
                } else "xiaomi"
                OnlineRecognitionProvider.ZHIPU -> "zhipuai"
                OnlineRecognitionProvider.OPENAI -> "openai"
                OnlineRecognitionProvider.MOONSHOT -> "moonshotai-cn"
                OnlineRecognitionProvider.MINIMAX -> "minimax-cn"
                OnlineRecognitionProvider.OPENCODE_GO -> "opencode-go"
                OnlineRecognitionProvider.DEEPSEEK -> "deepseek"
                OnlineRecognitionProvider.CUSTOM -> ""
            }

        private fun protocol(shape: String?, npm: String?): Pair<OnlineRecognitionApiKind?, Boolean> {
            when (shape?.lowercase(Locale.ROOT)) {
                "responses" -> return OnlineRecognitionApiKind.RESPONSES to false
                "completions", "chat", "chat_completions", "chat/completions" ->
                    return OnlineRecognitionApiKind.CHAT to false
                "anthropic", "messages" -> return OnlineRecognitionApiKind.ANTHROPIC to false
            }
            return when (npm?.lowercase(Locale.ROOT)) {
                "@ai-sdk/openai" -> OnlineRecognitionApiKind.RESPONSES to false
                "@ai-sdk/openai-compatible", "@ai-sdk/mistral" -> OnlineRecognitionApiKind.CHAT to false
                "@ai-sdk/anthropic" -> OnlineRecognitionApiKind.ANTHROPIC to false
                "@ai-sdk/google", "@ai-sdk/google-vertex", "@ai-sdk/amazon-bedrock",
                "@ai-sdk/cohere" -> null to true
                else -> null to false
            }
        }

        private fun merge(entries: List<Entry>, trustedProtocol: Boolean): Entry = Entry(
            displayName = entries.map { it.displayName }.consistent(),
            supportsImages = entries.map { it.supportsImages }.consistent(),
            supportsTextOutput = entries.map { it.supportsTextOutput }.consistent(),
            apiKind = if (trustedProtocol) entries.map { it.apiKind }.consistent() else null,
            reasoningControl = if (trustedProtocol) {
                entries.map { it.reasoningControl }.consistent() ?: ReasoningControl.NONE
            } else ReasoningControl.NONE,
            unsupportedProtocol = trustedProtocol && entries.all { it.unsupportedProtocol },
        )

        private fun normalizedEndpoint(value: String): String? {
            val url = value.trim().toHttpUrlOrNull() ?: return null
            if (url.query != null || url.fragment != null || url.username.isNotEmpty() || url.password.isNotEmpty()) {
                return null
            }
            return url.toString().trimEnd('/')
        }

        private fun List<Record>.indexBy(keys: (Record) -> Set<String>): Map<String, List<Record>> =
            flatMap { record -> keys(record).map { it to record } }.groupBy({ it.first }, { it.second })

        private fun <T> List<T>.consistent(): T? = distinct().singleOrNull()
        private fun String.nonBlank(): String? = trim().takeIf { it.isNotEmpty() }
        private fun JSONObject.string(key: String): String? = (opt(key) as? String)?.nonBlank()
        private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else {
            (0 until length()).mapNotNull { (opt(it) as? String)?.nonBlank() }
        }
        private fun JSONArray?.supports(modality: String): Boolean? {
            if (this == null) return null
            val values = strings()
            // 显式空列表表示没有该输出/输入；格式不合法则保留未知。
            if (values.size != length()) return null
            return values.any { it.equals(modality, ignoreCase = true) }
        }
    }
}

/** 公共目录使用独立客户端，永远不会携带用户的供应商密钥。 */
internal class ModelsDevMetadataRepository(context: Context) {
    private val cacheFile = File(context.applicationContext.filesDir, "online_recognition/models-dev.json")

    /** UI 读取只看进程内快照，不触碰磁盘，也不等待正在解析的目录。 */
    fun memoryCached(): ModelsDevMetadata? =
        snapshot?.takeIf { it.path == cacheFile.absolutePath }?.metadata

    /** 识别时可只读上一次元数据；调用方应在已有 IO 线程中使用。 */
    fun cached(): ModelsDevMetadata? {
        snapshot?.takeIf { it.path == cacheFile.absolutePath }?.let { return it.metadata }
        return synchronized(diskReadLock) {
            snapshot?.takeIf { it.path == cacheFile.absolutePath }?.let { return@synchronized it.metadata }
            val metadata = runCatching {
                AtomicFile(cacheFile).openRead().use { input -> ModelsDevMetadata.parse(input.readBoundedUtf8()) }
            }.getOrNull()
            val diskSnapshot = Snapshot(cacheFile.absolutePath, metadata, cacheFile.lastModified())
            synchronized(memoryLock) {
                // 解析期间可能已经完成网络刷新；不要用旧磁盘覆盖较新的内存索引。
                val current = snapshot?.takeIf { it.path == cacheFile.absolutePath }
                if (current != null) current.metadata else {
                    snapshot = diskSnapshot
                    metadata
                }
            }
        }
    }

    suspend fun load(): ModelsDevMetadata? = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val previous = cached()
            val now = System.currentTimeMillis()
            val savedAt = snapshot?.takeIf { it.path == cacheFile.absolutePath }?.savedAt ?: 0L
            if (previous != null && now - savedAt in 0 until MAX_AGE_MS) return@withLock previous
            if (lastAttemptPath == cacheFile.absolutePath && now - lastAttemptAt in 0 until RETRY_DELAY_MS) {
                return@withLock previous
            }
            lastAttemptPath = cacheFile.absolutePath
            lastAttemptAt = now
            try {
                val json = HTTP_CLIENT.newCall(Request.Builder().url(METADATA_URL).get().build()).awaitText()
                currentCoroutineContext().ensureActive()
                val metadata = ModelsDevMetadata.parse(json)
                currentCoroutineContext().ensureActive()
                // 设备存储暂时不可写时，本进程仍可使用已校验的公共元数据。
                runCatching { persist(json) }
                synchronized(memoryLock) {
                    snapshot = Snapshot(cacheFile.absolutePath, metadata, System.currentTimeMillis())
                }
                metadata
            } catch (cancelled: CancellationException) {
                // 取消不是请求失败，下一次有效请求应当能立即重试。
                lastAttemptAt = 0L
                throw cancelled
            } catch (_: Exception) {
                previous
            }
        }
    }

    private fun persist(json: String) {
        if (!cacheFile.parentFile!!.isDirectory && !cacheFile.parentFile!!.mkdirs()) {
            throw IOException("无法创建模型元数据缓存目录")
        }
        val atomic = AtomicFile(cacheFile)
        val output = atomic.startWrite()
        try {
            output.write(json.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
    }

    private suspend fun Call.awaitText(): String = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val text = response.use {
                        if (!it.isSuccessful) throw IOException("models.dev HTTP ${it.code}")
                        val body = it.body ?: throw IOException("models.dev 返回空响应")
                        if (body.contentLength() > MAX_METADATA_BYTES) throw IOException("模型元数据过大")
                        body.byteStream().use { input -> input.readBoundedUtf8() }
                    }
                    if (continuation.isActive) continuation.resume(text)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }

    private data class Snapshot(val path: String, val metadata: ModelsDevMetadata?, val savedAt: Long)

    companion object {
        private const val METADATA_URL = "https://models.dev/api.json"
        private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
        private const val RETRY_DELAY_MS = 60 * 1000L
        private const val MAX_METADATA_BYTES = 16 * 1024 * 1024
        private val memoryLock = Any()
        private val diskReadLock = Any()
        private val refreshMutex = Mutex()
        @Volatile private var snapshot: Snapshot? = null
        private var lastAttemptPath: String? = null
        private var lastAttemptAt = 0L
        private val HTTP_CLIENT = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()

        private fun InputStream.readBoundedUtf8(): String {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = read(buffer)
                if (count < 0) break
                if (output.size().toLong() + count > MAX_METADATA_BYTES) throw IOException("模型元数据过大")
                output.write(buffer, 0, count)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }
}
