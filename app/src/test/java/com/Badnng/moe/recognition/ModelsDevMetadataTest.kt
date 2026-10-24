package com.Badnng.moe.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsDevMetadataTest {
    @Test
    fun exactProviderWinsOverOtherGatewaysWithTheSameModelId() {
        val metadata = ModelsDevMetadata.parse("""
            {"openai":{"npm":"@ai-sdk/openai","models":{"shared":{"name":"Own model",
              "modalities":{"input":["text","image"],"output":["text"]}}}},
             "gateway":{"npm":"@ai-sdk/anthropic","models":{"shared":{"name":"Other model",
              "modalities":{"input":["text"],"output":["text"]},"reasoning_options":[{"type":"toggle"}]}}}}
        """.trimIndent())
        val entry = metadata.find(OnlineRecognitionProvider.OPENAI, "shared")!!
        assertEquals("Own model", entry.displayName)
        assertEquals(true, entry.supportsImages)
        assertEquals(OnlineRecognitionApiKind.RESPONSES, entry.apiKind)
        assertEquals(ReasoningControl.NONE, entry.reasoningControl)
    }

    @Test
    fun chinaProvidersAndMimoBillingPlansUseTheirOwnEntries() {
        val metadata = ModelsDevMetadata.parse("""
            {"xiaomi":{"models":{"shared":{"name":"Usage","modalities":{"input":["text","image"]}}}},
             "xiaomi-token-plan-cn":{"models":{"shared":{"name":"Plan","modalities":{"input":["text"]}}}},
             "moonshotai":{"models":{"shared":{"name":"Global","modalities":{"input":["text"]}}}},
             "moonshotai-cn":{"models":{"shared":{"name":"China","modalities":{"input":["text","image"]}}}}}
        """.trimIndent())
        assertEquals("Usage", metadata.find(OnlineRecognitionProvider.MIMO, "shared")?.displayName)
        val plan = metadata.find(OnlineRecognitionProvider.MIMO, "shared", MimoBillingMode.TOKEN_PLAN)!!
        assertEquals("Plan", plan.displayName)
        assertEquals(false, plan.supportsImages)
        assertEquals("China", metadata.find(OnlineRecognitionProvider.MOONSHOT, "shared")?.displayName)
    }

    @Test
    fun modelKeysIdsAndExplicitCanonicalAliasesMatchWithoutGuessingNewVersions() {
        val metadata = ModelsDevMetadata.parse("""
            {"provider-key":{"id":"openai","npm":"@ai-sdk/openai","models":{
              "map-key":{"id":"request-id","name":"Aliased","canonical_model_id":"origin/canonical-id",
                "aliases":["documented-alias"],"modalities":{"input":["image"],"output":["text"]}},
              "model-2026-10":{"name":"Snapshot","modalities":{"input":["text"]}}}}}
        """.trimIndent())
        for (id in listOf("map-key", "request-id", "origin/canonical-id", "canonical-id", "documented-alias")) {
            assertEquals("Aliased", metadata.find(OnlineRecognitionProvider.OPENAI, id)?.displayName)
        }
        assertNull(metadata.find(OnlineRecognitionProvider.OPENAI, "model-latest"))
        assertNull(metadata.find(OnlineRecognitionProvider.OPENAI, "model-2026-11"))
        assertNull(metadata.find(OnlineRecognitionProvider.OPENAI, "REQUEST-ID"))
    }

    @Test
    fun realModelIdHasPriorityOverAnotherModelsAlias() {
        val metadata = ModelsDevMetadata.parse("""
            {"openai":{"models":{
              "alias-owner":{"name":"Alias owner","aliases":["exact-id"],"modalities":{"input":["text"]}},
              "exact-id":{"name":"Exact","modalities":{"input":["image"]}}}}}
        """.trimIndent())
        assertEquals("Exact", metadata.find(OnlineRecognitionProvider.OPENAI, "exact-id")?.displayName)
    }

    @Test
    fun unknownGatewayOnlyMergesCapabilitiesWhenAllEntriesAgree() {
        val metadata = ModelsDevMetadata.parse("""
            {"one":{"npm":"@ai-sdk/openai","models":{
              "conflict":{"name":"One","modalities":{"input":["image"],"output":["text"]},
                "reasoning_options":[{"type":"effort","values":["none","low"]}]},
              "agree":{"modalities":{"input":["image"],"output":["text"]}},
              "partial":{"modalities":{"input":["image"]}}}},
             "two":{"npm":"@ai-sdk/google","models":{
              "conflict":{"name":"Two","modalities":{"input":["text"],"output":["text"]}},
              "agree":{"modalities":{"input":["text","image"],"output":["text"]}},
              "partial":{}}}}
        """.trimIndent())
        val conflict = metadata.find(OnlineRecognitionProvider.CUSTOM, "conflict")!!
        assertNull(conflict.supportsImages)
        assertEquals(true, conflict.supportsTextOutput)
        assertNull(conflict.displayName)
        assertNull(conflict.apiKind)
        assertEquals(ReasoningControl.NONE, conflict.reasoningControl)
        assertFalse(conflict.unsupportedProtocol)
        assertEquals(true, metadata.find(OnlineRecognitionProvider.CUSTOM, "agree")?.supportsImages)
        assertNull(metadata.find(OnlineRecognitionProvider.CUSTOM, "partial")?.supportsImages)
    }

    @Test
    fun missingGoEntryNeverBorrowsTheProtocolOfAnotherGateway() {
        val metadata = ModelsDevMetadata.parse("""
            {"opencode":{"npm":"@ai-sdk/anthropic","models":{"shared":{
              "modalities":{"input":["image"],"output":["text"]},"reasoning_options":[{"type":"toggle"}]}}}}
        """.trimIndent())
        val entry = metadata.find(OnlineRecognitionProvider.OPENCODE_GO, "shared")!!
        assertEquals(true, entry.supportsImages)
        assertNull(entry.apiKind)
        assertEquals(ReasoningControl.NONE, entry.reasoningControl)
    }

    @Test
    fun customProviderEndpointMustMatchTheMetadataHostAndPath() {
        val metadata = ModelsDevMetadata.parse("""
            {"known":{"api":"https://known.example/v1","npm":"@ai-sdk/openai","models":{"shared":{
              "name":"Matched provider","modalities":{"input":["image"]},
              "reasoning_options":[{"type":"effort","values":["none"]}]}}},
             "other":{"api":"https://other.example/v1","npm":"@ai-sdk/google","models":{"shared":{
              "name":"Other provider","modalities":{"input":["text"]}}}}}
        """.trimIndent())
        val matched = metadata.find(OnlineRecognitionProvider.CUSTOM, "shared", customBaseUrl = " https://KNOWN.example:443/v1/ ")!!
        assertEquals(true, matched.supportsImages)
        assertEquals("Matched provider", matched.displayName)
        assertNull(matched.apiKind)
        assertEquals(ReasoningControl.NONE, matched.reasoningControl)
        assertFalse(matched.unsupportedProtocol)
        val otherPath = metadata.find(OnlineRecognitionProvider.CUSTOM, "shared", customBaseUrl = "https://known.example/proxy/v1")!!
        assertNull(otherPath.apiKind)
        assertNull(otherPath.supportsImages)
        val otherHost = metadata.find(OnlineRecognitionProvider.CUSTOM, "shared", customBaseUrl = "https://unrelated.example/v1")!!
        assertNull(otherHost.apiKind)
        assertNull(otherHost.supportsImages)
    }

    @Test
    fun imageInputAndTextOutputAreIndependentAndAttachmentIsInsufficient() {
        val metadata = ModelsDevMetadata.parse("""
            {"openai":{"models":{
              "voice":{"modalities":{"input":["text"],"output":["audio"]}},
              "image-generator":{"modalities":{"input":["text","image"],"output":["image"]}},
              "file-only":{"attachment":true},
              "empty":{"modalities":{"input":[],"output":[]}},
              "invalid":{"modalities":{"input":[42],"output":[null]}},
              "vision":{"attachment":false,"modalities":{"input":["image"],"output":["text"]}}}}}
        """.trimIndent())
        assertEquals(false, metadata.find(OnlineRecognitionProvider.OPENAI, "voice")?.supportsImages)
        assertEquals(false, metadata.find(OnlineRecognitionProvider.OPENAI, "voice")?.supportsTextOutput)
        assertEquals(true, metadata.find(OnlineRecognitionProvider.OPENAI, "image-generator")?.supportsImages)
        assertEquals(false, metadata.find(OnlineRecognitionProvider.OPENAI, "image-generator")?.supportsTextOutput)
        assertNull(metadata.find(OnlineRecognitionProvider.OPENAI, "file-only")?.supportsImages)
        assertEquals(false, metadata.find(OnlineRecognitionProvider.OPENAI, "empty")?.supportsImages)
        assertEquals(false, metadata.find(OnlineRecognitionProvider.OPENAI, "empty")?.supportsTextOutput)
        assertNull(metadata.find(OnlineRecognitionProvider.OPENAI, "invalid")?.supportsImages)
        assertNull(metadata.find(OnlineRecognitionProvider.OPENAI, "invalid")?.supportsTextOutput)
        assertEquals(true, metadata.find(OnlineRecognitionProvider.OPENAI, "vision")?.supportsImages)
    }

    @Test
    fun modelProviderNpmOverridesAndApiOnlyOverridesInheritNpm() {
        val metadata = ModelsDevMetadata.parse("""
            {"opencode-go":{"npm":"@ai-sdk/openai-compatible","api":"https://go.example/openai/v1","models":{
              "responses":{"provider":{"npm":"@ai-sdk/openai"}},
              "anthropic":{"provider":{"npm":"@ai-sdk/anthropic"}},
              "google":{"provider":{"npm":"@ai-sdk/google"}},
              "api-only":{"provider":{"api":"https://go.example/other/v1"}}}}}
        """.trimIndent())
        assertEquals(OnlineRecognitionApiKind.RESPONSES, metadata.find(OnlineRecognitionProvider.OPENCODE_GO, "responses")?.apiKind)
        assertEquals(OnlineRecognitionApiKind.ANTHROPIC, metadata.find(OnlineRecognitionProvider.OPENCODE_GO, "anthropic")?.apiKind)
        assertTrue(metadata.find(OnlineRecognitionProvider.OPENCODE_GO, "google")!!.unsupportedProtocol)
        assertEquals(OnlineRecognitionApiKind.CHAT, metadata.find(OnlineRecognitionProvider.OPENCODE_GO, "api-only")?.apiKind)
    }

    @Test
    fun explicitShapeTakesPriorityOverNpmAndIsInheritedPerField() {
        val metadata = ModelsDevMetadata.parse("""
            {"opencode-go":{"npm":"@ai-sdk/google","shape":"completions","models":{
              "chat":{"provider":{"api":"https://go.example/other/v1"}},
              "responses":{"provider":{"shape":"responses"}}}}}
        """.trimIndent())
        val chat = metadata.find(OnlineRecognitionProvider.OPENCODE_GO, "chat")!!
        assertEquals(OnlineRecognitionApiKind.CHAT, chat.apiKind)
        assertFalse(chat.unsupportedProtocol)
        assertEquals(OnlineRecognitionApiKind.RESPONSES, metadata.find(OnlineRecognitionProvider.OPENCODE_GO, "responses")?.apiKind)
    }

    @Test
    fun reasoningCapabilityAloneNeverAddsDisableParameters() {
        val metadata = ModelsDevMetadata.parse("""
            {"opencode-go":{"npm":"@ai-sdk/openai","models":{
              "reasoning":{"reasoning":true,"interleaved":{"field":"reasoning_content"}},
              "low-only":{"reasoning_options":[{"type":"effort","values":["low","high"]}]},
              "none":{"reasoning_options":[{"type":"effort","values":["none","low"]}]},
              "toggle":{"provider":{"npm":"@ai-sdk/anthropic"},"reasoning_options":[{"type":"toggle"}]},
              "chat-toggle":{"provider":{"npm":"@ai-sdk/openai-compatible"},"reasoning_options":[{"type":"toggle"}]}}},
             "xiaomi":{"npm":"@ai-sdk/openai-compatible","models":{"toggle":{"reasoning_options":[{"type":"toggle"}]}}},
             "minimax-cn":{"npm":"@ai-sdk/anthropic","models":{"toggle":{"reasoning_options":[{"type":"toggle"}]}}}}
        """.trimIndent())
        assertEquals(ReasoningControl.NONE, metadata.find(OnlineRecognitionProvider.OPENCODE_GO, "reasoning")?.reasoningControl)
        assertEquals(ReasoningControl.NONE, metadata.find(OnlineRecognitionProvider.OPENCODE_GO, "low-only")?.reasoningControl)
        assertEquals(ReasoningControl.OPENAI_EFFORT_NONE, metadata.find(OnlineRecognitionProvider.OPENCODE_GO, "none")?.reasoningControl)
        assertEquals(ReasoningControl.ANTHROPIC_DISABLED, metadata.find(OnlineRecognitionProvider.OPENCODE_GO, "toggle")?.reasoningControl)
        assertEquals(ReasoningControl.NONE, metadata.find(OnlineRecognitionProvider.OPENCODE_GO, "chat-toggle")?.reasoningControl)
        assertEquals(ReasoningControl.THINKING_DISABLED, metadata.find(OnlineRecognitionProvider.MIMO, "toggle")?.reasoningControl)
        // MiniMax 在本应用使用 Responses，不能把其 Anthropic 元数据的 toggle 套入该协议。
        assertEquals(ReasoningControl.NONE, metadata.find(OnlineRecognitionProvider.MINIMAX, "toggle")?.reasoningControl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyMetadataCannotReplaceSuccessfulCache() {
        ModelsDevMetadata.parse("{}")
    }
}
