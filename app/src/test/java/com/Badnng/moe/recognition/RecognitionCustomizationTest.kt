package com.Badnng.moe.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionCustomizationTest {
    @Test
    fun normalizeBlockedWordsTrimsDeduplicatesAndPreservesOrder() {
        val result = RecognitionBlockedWordsPolicy.normalize(
            listOf("  广告  ", "", "ADVERTISEMENT", "advertisement", "推广"),
        )

        assertEquals(listOf("广告", "ADVERTISEMENT", "推广"), result)
    }

    @Test
    fun normalizeBlockedWordsStopsAtConfiguredLimit() {
        val result = RecognitionBlockedWordsPolicy.normalize(
            (0 until RecognitionBlockedWordsPolicy.MAX_WORDS + 20).map { "word-$it" },
        )

        assertEquals(RecognitionBlockedWordsPolicy.MAX_WORDS, result.size)
        assertEquals("word-99", result.last())
    }

    @Test
    fun blockedWordMatchingIgnoresCaseAndUsesContains() {
        val match = RecognitionBlockedWordsPolicy.firstMatch(
            text = "This is an AdVerTiseMent notification",
            words = listOf("advertisement"),
        )

        assertEquals("advertisement", match)
        assertNull(RecognitionBlockedWordsPolicy.firstMatch("正常取件通知", listOf("广告")))
    }

    @Test
    fun promptFallsBackForMissingOrBlankCustomValue() {
        assertEquals("default", RecognitionPromptPolicy.resolve("default", null))
        assertEquals("default", RecognitionPromptPolicy.resolve("default", "   "))
        assertEquals("custom", RecognitionPromptPolicy.resolve("default", "custom"))
    }

    @Test
    fun promptOnlyPersistsMeaningfulOverrides() {
        assertFalse(RecognitionPromptPolicy.shouldPersist("", "default"))
        assertFalse(RecognitionPromptPolicy.shouldPersist("default", "default"))
        assertTrue(RecognitionPromptPolicy.shouldPersist("custom", "default"))
    }

    @Test
    fun onlySmsAndNotificationSupportCustomBlocking() {
        assertFalse(RecognitionTextSource.General.supportsCustomBlocking)
        assertTrue(RecognitionTextSource.Sms.supportsCustomBlocking)
        assertTrue(RecognitionTextSource.Notification.supportsCustomBlocking)
    }
}
