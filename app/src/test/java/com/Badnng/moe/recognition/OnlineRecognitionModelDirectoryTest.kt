package com.Badnng.moe.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineRecognitionModelDirectoryTest {
    @Test
    fun unknownSavedModelIsPreservedRatherThanReplacedByDefault() {
        val model = OnlineRecognitionCatalog.resolveModel(OnlineRecognitionProvider.OPENAI, " future-vision ")
        assertEquals("future-vision", model.id)
        assertEquals(ReasoningControl.NONE, model.reasoningControl)
        assertNull(model.apiKind)
        assertEquals("", OnlineRecognitionCatalog.resolveModel(OnlineRecognitionProvider.OPENAI, " ").id)
        assertEquals("", OnlineRecognitionCatalog.resolveModel(OnlineRecognitionProvider.MIMO, null).id)
    }

    @Test
    fun discoveryUsesReturnedNamesAndDeduplicatesIdsWithoutBuiltInSettings() {
        val models = OnlineRecognitionModelDirectory.parse(OnlineRecognitionProvider.MIMO, """
            {"data":[{"id":"vision-b","name":"Server vision","supports_image_in":true},
              {"id":"new-vision","name":"A new vision","supports_image_in":true},
              {"id":"vision-b","supports_image_in":true},{"id":""},{"created":123}]}
        """.trimIndent())
        assertEquals(listOf("new-vision", "vision-b"), models.map { it.id })
        assertEquals("Server vision", models.last().displayName)
        assertEquals(ReasoningControl.NONE, models.last().reasoningControl)
        assertEquals(ReasoningControl.NONE, models.first().reasoningControl)
        assertEquals(true, models.first().supportsImages)
    }

    @Test
    fun onlyConfirmedVisionModelsAppearInDirectory() {
        val models = OnlineRecognitionModelDirectory.parse(OnlineRecognitionProvider.CUSTOM, """
            {"data":[
              {"id":"unknown"},
              {"id":"vision","input_modalities":["text","image"],"output_modalities":["text"]},
              {"id":"no-kimi-image","supports_image_in":false},
              {"id":"text-only","modalities":{"input":["text"],"output":["text"]}},
              {"id":"no-image","capabilities":{"vision":false}},
              {"id":"audio-output","modalities":{"input":["text","image"],"output":["audio"]}},
              {"id":"embedding","task":"embeddings"},
              {"id":"no-text","supports_text_out":false}
            ]}
        """.trimIndent())
        assertEquals(setOf("vision"), models.map { it.id }.toSet())
    }

    @Test
    fun compatibleEndpointTakesPrecedenceOverUnsupportedNativeProtocol() {
        val models = OnlineRecognitionModelDirectory.parse(OnlineRecognitionProvider.OPENCODE_GO, """
            {"data":[
              {"id":"google-only","supports_image_in":true,"provider":{"npm":"@ai-sdk/google"}},
              {"id":"google-compatible","supports_image_in":true,"provider":{"npm":"@ai-sdk/google"},"supported_endpoints":["generateContent","chat/completions"]},
              {"id":"future-model","supports_image_in":true,"provider":{"npm":"@ai-sdk/future"}},
              {"id":"future-responses","provider":{"shape":"responses","npm":"@ai-sdk/openai-compatible"},"supports_image_in":true}
            ]}
        """.trimIndent())
        assertEquals(setOf("google-compatible", "future-responses"), models.map { it.id }.toSet())
        assertEquals(OnlineRecognitionApiKind.CHAT, models.first { it.id == "google-compatible" }.apiKind)
        assertEquals(OnlineRecognitionApiKind.RESPONSES, models.first { it.id == "future-responses" }.apiKind)
    }

    @Test
    fun unknownReasoningModelGetsNoDisableParameterUnlessMetadataAllowsNone() {
        val models = OnlineRecognitionModelDirectory.parse(OnlineRecognitionProvider.OPENAI, """
            {"data":[
              {"id":"unknown-gpt","supports_image_in":true},
              {"id":"reasoning-low","supports_image_in":true,"api_type":"responses","reasoning_options":[{"type":"effort","values":["low","high"]}]},
              {"id":"reasoning-none","supports_image_in":true,"api_type":"responses","reasoning_options":[{"type":"effort","values":["none","low"]}]}
            ]}
        """.trimIndent())
        assertEquals(ReasoningControl.NONE, models.first { it.id == "unknown-gpt" }.reasoningControl)
        assertEquals(ReasoningControl.NONE, models.first { it.id == "reasoning-low" }.reasoningControl)
        assertEquals(ReasoningControl.OPENAI_EFFORT_NONE, models.first { it.id == "reasoning-none" }.reasoningControl)
    }

    @Test
    fun modelsDevCompletesCapabilitiesButNeverInventsProviderModelIds() {
        val metadata = ModelsDevMetadata.parse("""
            {"openai":{"npm":"@ai-sdk/openai","models":{
              "remote-vision":{"id":"remote-vision","name":"Remote vision","modalities":{"input":["text","image"],"output":["text"]},"reasoning_options":[{"type":"effort","values":["none","low"]}]},
              "pure-text":{"id":"pure-text","modalities":{"input":["text"],"output":["text"]}},
              "provider-optout":{"id":"provider-optout","modalities":{"input":["text","image"],"output":["text"]}},
              "provider-optin":{"id":"provider-optin","modalities":{"input":["text"],"output":["text"]}},
              "empty-input":{"id":"empty-input","modalities":{"input":["text","image"],"output":["text"]}},
              "empty-output":{"id":"empty-output","modalities":{"input":["text","image"],"output":["text"]}},
              "not-in-provider-list":{"id":"not-in-provider-list","modalities":{"input":["text","image"],"output":["text"]}}
            }}}
        """.trimIndent())
        val models = OnlineRecognitionModelDirectory.parse(OnlineRecognitionProvider.OPENAI, """
            {"data":[{"id":"remote-vision"},{"id":"pure-text"},{"id":"unknown"},
              {"id":"provider-optout","supports_image_in":false},
              {"id":"empty-input","input_modalities":[]},
              {"id":"empty-output","output_modalities":[]},
              {"id":"provider-optin","supports_image_in":true}]}
        """.trimIndent(), metadata)
        assertEquals(setOf("remote-vision", "provider-optin"), models.map { it.id }.toSet())
        val remote = models.first { it.id == "remote-vision" }
        assertEquals("Remote vision", remote.displayName)
        assertEquals(OnlineRecognitionApiKind.RESPONSES, remote.apiKind)
        assertEquals(ReasoningControl.OPENAI_EFFORT_NONE, remote.reasoningControl)
        assertEquals(true, remote.supportsImages)
        assertEquals(true, remote.supportsTextOutput)
    }

    @Test
    fun customRequestModeIsNotOverriddenByMetadataSdkProtocol() {
        val metadata = ModelsDevMetadata.parse("""
            {"native-google":{"npm":"@ai-sdk/google","api":"https://compatible.example/v1","models":{
              "vision":{"id":"vision","name":"Vision","modalities":{"input":["text","image"],"output":["text"]}}
            }}}
        """.trimIndent())
        val model = OnlineRecognitionModelDirectory.parse(
            OnlineRecognitionProvider.CUSTOM, """{"data":[{"id":"vision"}]}""", metadata,
            customBaseUrl = "https://compatible.example/v1",
        ).single()
        assertNull(model.apiKind)
        assertFalse(model.unsupportedProtocol)
        assertEquals(ReasoningControl.NONE, model.reasoningControl)
        OnlineRecognitionModelDirectory.validateForRequest(model, hasImage = true)
    }

    @Test
    fun savedModelWithUnknownCapabilitiesRemainsUsableAndTextDoesNotRequireImages() {
        OnlineRecognitionModelDirectory.validateForRequest(
            OnlineRecognitionModel("unknown", "Unknown", ReasoningControl.NONE), hasImage = true,
        )
        OnlineRecognitionModelDirectory.validateForRequest(
            OnlineRecognitionModel("text", "Text", ReasoningControl.NONE, supportsImages = false), hasImage = false,
        )
    }

    @Test(expected = OnlineRecognitionException::class)
    fun blankSelectionNeverSendsAnEmptyModelId() {
        OnlineRecognitionModelDirectory.validateForRequest(
            OnlineRecognitionCatalog.resolveModel(OnlineRecognitionProvider.OPENAI, null), hasImage = false,
        )
    }

    @Test(expected = OnlineRecognitionException::class)
    fun explicitTextOnlyModelCannotReceiveImages() {
        OnlineRecognitionModelDirectory.validateForRequest(
            OnlineRecognitionModel("text", "Text", ReasoningControl.NONE, supportsImages = false), hasImage = true,
        )
    }

    @Test(expected = OnlineRecognitionException::class)
    fun modelWithoutTextOutputCannotReturnTextRecognitionResults() {
        OnlineRecognitionModelDirectory.validateForRequest(
            OnlineRecognitionModel("audio", "Audio", ReasoningControl.NONE, supportsTextOutput = false), hasImage = false,
        )
    }

    @Test(expected = OnlineRecognitionException::class)
    fun explicitUnsupportedProtocolDoesNotSendAnyRequest() {
        OnlineRecognitionModelDirectory.validateForRequest(
            OnlineRecognitionModel("native", "Native", ReasoningControl.NONE, unsupportedProtocol = true), hasImage = false,
        )
    }

    @Test(expected = OnlineRecognitionException::class)
    fun metadataUnavailableDoesNotPresentUnknownIdsAsVisionModels() {
        OnlineRecognitionModelDirectory.parse(OnlineRecognitionProvider.OPENAI, """{"data":[{"id":"unknown"}]}""")
    }

    @Test
    fun cacheIdentityIsScopedByProviderBillingEndpointAndKeyWithoutExposingKey() {
        fun identity(
            provider: OnlineRecognitionProvider,
            mode: MimoBillingMode = MimoBillingMode.PAY_AS_YOU_GO,
            base: String = "https://example.com/v1",
            key: String = "secret-test-key",
        ) = OnlineRecognitionModelDirectory.cacheIdentity(provider, mode, base, key)

        assertNotEquals(identity(OnlineRecognitionProvider.OPENAI), identity(OnlineRecognitionProvider.MOONSHOT))
        assertNotEquals(identity(OnlineRecognitionProvider.MIMO), identity(OnlineRecognitionProvider.MIMO, MimoBillingMode.TOKEN_PLAN))
        assertNotEquals(identity(OnlineRecognitionProvider.CUSTOM), identity(OnlineRecognitionProvider.CUSTOM, base = "https://other.example/v1"))
        assertNotEquals(identity(OnlineRecognitionProvider.CUSTOM), identity(OnlineRecognitionProvider.CUSTOM, base = "https://example.com/v2"))
        assertNotEquals(identity(OnlineRecognitionProvider.CUSTOM), identity(OnlineRecognitionProvider.CUSTOM, key = "other-secret-key"))
        assertEquals(identity(OnlineRecognitionProvider.CUSTOM), identity(OnlineRecognitionProvider.CUSTOM, base = " https://EXAMPLE.COM:443/v1/ "))
        assertEquals(identity(OnlineRecognitionProvider.OPENAI), identity(OnlineRecognitionProvider.OPENAI, MimoBillingMode.TOKEN_PLAN, "unrelated"))
        assertFalse(identity(OnlineRecognitionProvider.CUSTOM).orEmpty().contains("secret-test-key"))
        assertFalse(identity(OnlineRecognitionProvider.CUSTOM).orEmpty().contains("example.com"))
        assertTrue(identity(OnlineRecognitionProvider.CUSTOM).orEmpty().startsWith("v2_"))
        assertNull(identity(OnlineRecognitionProvider.CUSTOM, base = ""))
    }

    @Test
    fun cacheRoundTripRetainsAllRequestMetadata() {
        val models = listOf(
            OnlineRecognitionModel("known", "Known vision", ReasoningControl.ANTHROPIC_DISABLED, OnlineRecognitionApiKind.ANTHROPIC, true, true),
            OnlineRecognitionModel("unknown", "Unknown", ReasoningControl.NONE),
            OnlineRecognitionModel("unsupported", "Native protocol", ReasoningControl.NONE, unsupportedProtocol = true),
        )
        assertEquals(models, OnlineRecognitionModelDirectory.decodeCache(OnlineRecognitionModelDirectory.encodeCache(models)))
        val futureCache = """[{"id":"future","reasoningControl":"FUTURE","apiKind":"FUTURE"}]"""
        val decoded = OnlineRecognitionModelDirectory.decodeCache(futureCache).single()
        assertEquals(ReasoningControl.NONE, decoded.reasoningControl)
        assertNull(decoded.apiKind)
    }

    @Test
    fun openCodeUsesOnlyExplicitProtocolMetadata() {
        fun model(id: String, kind: OnlineRecognitionApiKind? = null) =
            OnlineRecognitionModel(id, id, ReasoningControl.NONE, kind)
        assertEquals(OnlineRecognitionApiKind.RESPONSES, OnlineRecognitionModelDirectory.openCodeApiKind(model("qwen-next", OnlineRecognitionApiKind.RESPONSES)))
        assertEquals(OnlineRecognitionApiKind.CHAT, OnlineRecognitionModelDirectory.openCodeApiKind(model("gemini-compatible", OnlineRecognitionApiKind.CHAT)))
    }

    @Test(expected = OnlineRecognitionException::class)
    fun savedGoModelWithoutProtocolDoesNotSilentlyUseChatRequest() {
        OnlineRecognitionModelDirectory.openCodeApiKind(
            OnlineRecognitionModel("gpt-next", "New model", ReasoningControl.NONE),
        )
    }

    @Test
    fun providerDirectoriesUseTheirOwnHostsAndVersionedPaths() {
        fun url(provider: OnlineRecognitionProvider, mode: MimoBillingMode = MimoBillingMode.PAY_AS_YOU_GO) =
            OnlineRecognitionModelDirectory.modelsUrl(provider, mode, "https://example.com/proxy/v1/")
        assertEquals("https://api.minimax.cn/v1/models", url(OnlineRecognitionProvider.MINIMAX))
        assertEquals("https://api.deepseek.com/models", url(OnlineRecognitionProvider.DEEPSEEK))
        assertEquals("https://token-plan-cn.xiaomimimo.com/v1/models", url(OnlineRecognitionProvider.MIMO, MimoBillingMode.TOKEN_PLAN))
        assertEquals("https://example.com/proxy/v1/models", url(OnlineRecognitionProvider.CUSTOM))
        assertTrue(url(OnlineRecognitionProvider.OPENCODE_GO).endsWith("/inference/go/v1/models"))
    }

    @Test(expected = OnlineRecognitionException::class)
    fun invalidDirectoryDoesNotBecomeAnEmptySuccessfulCache() {
        OnlineRecognitionModelDirectory.parse(OnlineRecognitionProvider.CUSTOM, "{\"error\":\"unavailable\"}")
    }
}
