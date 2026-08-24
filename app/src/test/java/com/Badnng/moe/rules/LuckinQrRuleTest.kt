package com.Badnng.moe.rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuckinQrRuleTest {
    @Test
    fun matchesLegacyLuckinQrShape() {
        assertTrue(LuckinQrRule.matches("a1234567AA.="))
    }

    @Test
    fun rejectsUrlsAndSimilarInvalidValues() {
        assertFalse(LuckinQrRule.matches("https://example.com"))
        assertFalse(LuckinQrRule.matches("a123456AA.="))
        assertFalse(LuckinQrRule.matches(null))
    }
}
