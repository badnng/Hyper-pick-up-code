package com.Badnng.moe.recognition

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionDiagnosticRedactorTest {
    @Test
    fun redactsKnownKeysBearerTokensAndNamedSecretFields() {
        val knownKey = "custom-secret-value"
        val source = """
            Authorization: Bearer sk-example-secret-123456
            {"api_key":"$knownKey","access_token":"token-value-123"}
        """.trimIndent()

        val result = RecognitionDiagnosticRedactor.redact(source, listOf(knownKey)).orEmpty()

        assertFalse(result.contains(knownKey))
        assertFalse(result.contains("sk-example-secret-123456"))
        assertFalse(result.contains("token-value-123"))
        assertTrue(result.contains("[REDACTED]"))
    }
}
